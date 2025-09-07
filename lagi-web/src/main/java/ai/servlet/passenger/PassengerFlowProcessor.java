package ai.servlet.passenger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.json.JSONArray;
import org.json.JSONObject;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Transaction;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Base64;

/**
 * 乘客流量处理器，处理CV WebSocket推送的事件
 */
public class PassengerFlowProcessor {

	private static final ObjectMapper objectMapper = new ObjectMapper();
	private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private final JedisPool jedisPool = new JedisPool(Config.REDIS_HOST, Config.REDIS_PORT);
	private KafkaProducer<String, String> producer;

	// 异步数据库服务管理器
	private final AsyncDbServiceManager asyncDbServiceManager = AsyncDbServiceManager.getInstance();

	public PassengerFlowProcessor() {
		Properties props = KafkaConfig.getProducerProperties();
		producer = new KafkaProducer<>(props);
		// 修复Java 8 时间类型序列化（LocalDate/LocalDateTime）
		objectMapper.registerModule(new JavaTimeModule());
		objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	}

	public void processEvent(JSONObject eventJson) {
		String event = eventJson.optString("event");
		JSONObject data = eventJson.optJSONObject("data");

		// 第一时间保存WebSocket消息到数据库
		saveWebSocketMessage(eventJson, event, data);

		// 关闭CV事件详细日志，避免大payload(如base64)刷屏
		if (Config.PILOT_ROUTE_LOG_ENABLED) {
			System.out.println("[流程] 收到CV事件: event=" + event + ", 字段: " + (data != null ? data.keySet() : java.util.Collections.emptySet()));
		}

		if (data == null) {
			if (Config.LOG_ERROR) {
				System.err.println("[流程中断] CV事件data为空，跳过。event=" + event);
			}
			return;
		}

		String busNo = data.optString("bus_no");
		String busId = data.optString("bus_id");
		String cameraNo = data.optString("camera_no");

		// 降低参数解析日志噪音

		try (Jedis jedis = jedisPool.getResource()) {
			jedis.auth(Config.REDIS_PASSWORD);

			switch (event) {
				case "downup":
					// 第一时间保存downup消息到数据库
					saveDownUpMessage(data, busNo, busId, cameraNo);
					handleDownUpEvent(data, busNo, busId, cameraNo, jedis);
					break;
				case "load_factor":
					// 第一时间保存load_factor消息到数据库
					saveLoadFactorMessage(data, busNo, cameraNo);
					// 高频事件，移除过程性日志
					handleLoadFactorEvent(data, busNo, busId, jedis);
					break;
				case "open_close_door":
					// 关键事件在KafkaConsumerService侧已有明确日志
					// 只处理open，开始缓存
					handleOpenDoorEvent(data, busNo, busId, cameraNo, jedis);
					break;
				case "notify_complete":
					// 关键事件在KafkaConsumerService侧已有明确日志
					// 收到cv的公交分析业务处理结束，开始发kafa落库
					handleCloseDoorAndCVComplateEvent(data, busNo, busId, cameraNo, jedis);
					break;
				default:
					if (Config.LOG_ERROR) {
						System.err.println("[流程中断] 未知CV事件类型，跳过。event=" + event);
					}
			}
		} catch (Exception e) {
			if (Config.LOG_ERROR) {
				System.err.println("[流程异常] 处理CV事件失败: " + e.getMessage());
			}
		}
	}

	private void handleDownUpEvent(JSONObject data, String busNo, String busId, String cameraNo, Jedis jedis) throws IOException, SQLException {
		String sqeNo = data.optString("sqe_no");  // 新增：获取开关门唯一批次号
		LocalDateTime eventTime = LocalDateTime.parse(data.optString("timestamp").replace(" ", "T"));
		JSONArray events = data.optJSONArray("events");

		if (events == null || events.length() == 0) {
			return;
		}

		// 收集原始downup事件数据用于校验
		if (Config.LOG_DEBUG) {
			System.out.println("[PassengerFlowProcessor] 开始收集downup事件: busNo=" + busNo + ", busId=" + busId + ", sqeNo=" + sqeNo + ", stationId=" + data.optString("stationId") + ", events=" + (events != null ? events.length() : 0));
		}
		collectDownupMsg(busNo, data, jedis);


		List<BusOdRecord> odRecords = new ArrayList<>();
		int upCount = 0, downCount = 0;

		// 获取新的数据结构中的字段
		String stationId = data.optString("stationId");
		String stationName = data.optString("stationName");

		// 精简CV数据接收日志，避免重复输出
		if (Config.PILOT_ROUTE_LOG_ENABLED) {
			System.out.println("[CV数据接收] downup事件: bus_id=" + busId + ", bus_no=" + busNo + ", sqe_no=" + sqeNo + ", stationId=" + stationId + ", stationName=" + stationName + ", 事件数=" + (events != null ? events.length() : 0));
		}

		if (Config.PILOT_ROUTE_LOG_ENABLED) {
			System.out.println("[流程] downup事件开始: busId=" + busId + ", busNo=" + busNo + ", sqe_no=" + sqeNo + ", 事件数=" + (events != null ? events.length() : 0));
		}

		// 现在直接使用bus_id作为canonicalBusNo，不再需要映射
		String canonicalBusNo = busId != null && !busId.isEmpty() ? busId : busNo;

		for (int i = 0; i < events.length(); i++) {
			JSONObject ev = events.getJSONObject(i);
			String direction = ev.optString("direction");
			String feature = ev.optString("feature");
			String image = ev.optString("image");

			// 添加feature字段调试日志
			if (Config.PILOT_ROUTE_LOG_ENABLED) {
				System.out.println("[特征调试] 收到feature字段: " + (feature != null ? "长度=" + feature.length() + ", 前100字符=" + feature.substring(0, Math.min(100, feature.length())) : "null"));
			}
			int boxX = ev.optInt("box_x");
			int boxY = ev.optInt("box_y");
			int boxW = ev.optInt("box_w");
			int boxH = ev.optInt("box_h");

			// 处理图片：支持直接URL与base64两种形式
			String imageUrl = null;
			// 添加图片字段调试日志
			if (Config.PILOT_ROUTE_LOG_ENABLED) {
				System.out.println("[图片调试] 收到image字段: " + (image != null ? "长度=" + image.length() + ", 前100字符=" + image.substring(0, Math.min(100, image.length())) : "null"));
			}
			if (image != null && !image.isEmpty()) {
				if (image.startsWith("http://") || image.startsWith("https://")) {
					// 直接使用URL并缓存
					imageUrl = image;
				} else if (Config.ENABLE_IMAGE_PROCESSING) {
					try {
						if (Config.PILOT_ROUTE_LOG_ENABLED) {
							System.out.println("[流程] 开始处理图片(base64->文件->OSS): busNo=" + busNo + ", cameraNo=" + cameraNo);
						}
						imageUrl = processBase64Image(image, canonicalBusNo, cameraNo, eventTime);
						if (Config.PILOT_ROUTE_LOG_ENABLED) {
							System.out.println("[流程] 图片上传完成，得到URL");
						}
					} catch (Exception e) {
						if (Config.LOG_ERROR) {
							System.err.println("[PassengerFlowProcessor] Error processing base64 image: " + e.getMessage());
						}
					}
				}
			}

			// 添加图片处理结果调试日志
			if (Config.PILOT_ROUTE_LOG_ENABLED) {
				System.out.println("[图片调试] 图片处理结果: imageUrl=" + (imageUrl != null ? "长度=" + imageUrl.length() : "null"));
			}

			// 🔥 获取当前开门时间窗口ID - 优先使用sqe_no匹配
			String windowId = null;

			// 1. 优先通过sqe_no匹配（新的主要匹配方式）
			if (sqeNo != null && !sqeNo.isEmpty()) {
				windowId = jedis.get("open_time:" + sqeNo);
				canonicalBusNo = jedis.get("canonical_bus:" + sqeNo);
				if (windowId != null && canonicalBusNo != null) {
					if (Config.PILOT_ROUTE_LOG_ENABLED) {
						System.out.println("[CV数据匹配] 🔥 通过sqe_no找到时间窗口: " + windowId + " for bus: " + canonicalBusNo);
					}
				}
			}

			// 2. 如果sqe_no匹配失败，尝试通过stationId、stationName、bus_id匹配（兼容性）
			if (windowId == null && stationId != null && !stationId.isEmpty() && stationName != null && !stationName.isEmpty() && busId != null && !busId.isEmpty()) {
				windowId = jedis.get("open_time_by_station:" + stationId + ":" + stationName + ":" + busId);
				if (windowId != null) {
					if (Config.PILOT_ROUTE_LOG_ENABLED) {
						System.out.println("[CV数据匹配] 通过stationId、stationName、bus_id找到时间窗口: " + windowId);
					}
				}
			}

			// 3. 如果上述匹配失败，尝试通过canonicalBusNo匹配（兼容性）
			if (windowId == null) {
				windowId = jedis.get("open_time:" + canonicalBusNo);
				if (windowId != null) {
					if (Config.PILOT_ROUTE_LOG_ENABLED) {
						System.out.println("[CV数据匹配] 通过canonicalBusNo找到时间窗口: " + windowId);
					}
				}
			}

			// 3. 兜底方案：通过时间窗口匹配（保持原有逻辑作为兜底）
			if (windowId == null) {
				for (int delta = 0; delta <= 10 && windowId == null; delta++) {
					LocalDateTime t0 = eventTime.minusSeconds(delta);
					LocalDateTime t1 = delta == 0 ? null : eventTime.plusSeconds(delta);
					String k0 = t0.format(formatter);
					String bus0 = jedis.get("open_time_index:" + k0);
					if (bus0 != null && !bus0.isEmpty()) {
						windowId = k0;
						canonicalBusNo = bus0;
						if (Config.PILOT_ROUTE_LOG_ENABLED) {
							System.out.println("[CV数据匹配] 通过时间窗口兜底找到: " + windowId + " for bus: " + canonicalBusNo);
						}
						break;
					}
					if (t1 != null) {
						String k1 = t1.format(formatter);
						String bus1 = jedis.get("open_time_index:" + k1);
						if (bus1 != null && !bus1.isEmpty()) {
							windowId = k1;
							canonicalBusNo = bus1;
							if (Config.PILOT_ROUTE_LOG_ENABLED) {
								System.out.println("[CV数据匹配] 通过时间窗口兜底找到: " + windowId + " for bus: " + canonicalBusNo);
							}
							break;
						}
					}
				}
			}

			if (windowId == null) {
				if (Config.PILOT_ROUTE_LOG_ENABLED) {
					System.out.println("[CV数据匹配] 未找到时间窗口，跳过处理: busId=" + busId + ", stationId=" + stationId + ", stationName=" + stationName);
				}
				continue;
			}

			if (Config.PILOT_ROUTE_LOG_ENABLED) {
				System.out.println("[CV数据匹配] 找到时间窗口: " + windowId + " for bus: " + canonicalBusNo);
			}

			if ("up".equals(direction)) {
				upCount++;

				// 缓存上车特征和站点信息 - 使用CV推送的stationId和stationName
				cacheFeatureStationMapping(jedis, feature, stationId, stationName, "up");

				// 🔥 使用Redis事务保证上车计数和特征缓存的原子性
				Transaction txUp = jedis.multi();
				try {
					// 更新上车计数：优先使用sqe_no
					if (sqeNo != null && !sqeNo.isEmpty()) {
						String cvUpCountKey = "cv_up_count:" + sqeNo;
						txUp.incr(cvUpCountKey);
						txUp.expire(cvUpCountKey, Config.REDIS_TTL_OPEN_TIME);
					}

					// 兼容性计数：保持原有逻辑
					String cvUpCountKeyLegacy = "cv_up_count:" + canonicalBusNo + ":" + windowId;
					txUp.incr(cvUpCountKeyLegacy);
					txUp.expire(cvUpCountKeyLegacy, Config.REDIS_TTL_OPEN_TIME);

					// 缓存特征集合：优先使用sqe_no
					String featuresKey = sqeNo != null && !sqeNo.isEmpty() ?
						"features_set:" + sqeNo :
						"features_set:" + canonicalBusNo + ":" + windowId;
					JSONObject featureInfo = new JSONObject();
					featureInfo.put("feature", feature);
					featureInfo.put("direction", "up");
					featureInfo.put("timestamp", eventTime.format(formatter));
					featureInfo.put("image", imageUrl);
					// 使用CV推送的站点信息
					featureInfo.put("stationId", stationId);
					featureInfo.put("stationName", stationName);
					JSONObject position = new JSONObject();
					position.put("xLeftUp", boxX);
					position.put("yLeftUp", boxY);
					position.put("xRightBottom", boxX + boxW);
					position.put("yRightBottom", boxY + boxH);
					featureInfo.put("position", position);
					txUp.sadd(featuresKey, featureInfo.toString());
					txUp.expire(featuresKey, Config.REDIS_TTL_FEATURES);

					// 执行上车事务
					txUp.exec();
				} catch (Exception e) {
					txUp.discard();
					if (Config.LOG_ERROR) {
						System.err.println("[Redis事务] 上车数据事务执行失败: " + e.getMessage());
					}
					throw e;
				}

				// 🔥 缓存图片URL：传递sqeNo
				if (imageUrl != null) {
					cacheImageUrl(jedis, canonicalBusNo, windowId, imageUrl, "up", sqeNo);
				}

				// 缓存乘客位置信息（特征向量 -> 位置信息的映射）
				String positionKey = "feature_position:" + canonicalBusNo + ":" + windowId + ":" + feature;
				JSONObject positionInfo = new JSONObject();
				positionInfo.put("xLeftUp", boxX);
				positionInfo.put("yLeftUp", boxY);
				positionInfo.put("xRightBottom", boxX + boxW);
				positionInfo.put("yRightBottom", boxY + boxH);
				// 移除循环引用，不再包含position字段
				jedis.set(positionKey, positionInfo.toString());
				jedis.expire(positionKey, Config.REDIS_TTL_FEATURES);

			} else if ("down".equals(direction)) {
				downCount++;

				// 🔥 尝试匹配上车特征，计算区间客流 - 传递sqeNo参数
				processPassengerMatching(feature, canonicalBusNo, jedis, eventTime, stationId, stationName, sqeNo, windowId);

				// 🔥 使用Redis事务保证下车计数和特征缓存的原子性
				Transaction txDown = jedis.multi();
				try {
					// 更新下车计数：优先使用sqe_no
					if (sqeNo != null && !sqeNo.isEmpty()) {
						String cvDownCountKey = "cv_down_count:" + sqeNo;
						txDown.incr(cvDownCountKey);
						txDown.expire(cvDownCountKey, Config.REDIS_TTL_OPEN_TIME);
					}

					// 兼容性计数：保持原有逻辑
					String cvDownCountKeyLegacy = "cv_down_count:" + canonicalBusNo + ":" + windowId;
					txDown.incr(cvDownCountKeyLegacy);
					txDown.expire(cvDownCountKeyLegacy, Config.REDIS_TTL_OPEN_TIME);

					// 缓存下车特征到特征集合：优先使用sqe_no
					String featuresKey = sqeNo != null && !sqeNo.isEmpty() ?
						"features_set:" + sqeNo :
						"features_set:" + canonicalBusNo + ":" + windowId;
					JSONObject featureInfo = new JSONObject();
					featureInfo.put("feature", feature);
					featureInfo.put("direction", "down");
					featureInfo.put("timestamp", eventTime.format(formatter));
					featureInfo.put("image", imageUrl);
					// 使用CV推送的站点信息（下车时刻）
					featureInfo.put("stationId", stationId);
					featureInfo.put("stationName", stationName);
					JSONObject positionInfo = new JSONObject();
					positionInfo.put("xLeftUp", boxX);
					positionInfo.put("yLeftUp", boxY);
					positionInfo.put("xRightBottom", boxX + boxW);
					positionInfo.put("yRightBottom", boxY + boxH);
					featureInfo.put("position", positionInfo);

					// 限制特征数据大小，避免Redis存储过大
					String featureStr = featureInfo.toString();
					if (featureStr.length() > Config.MAX_FEATURE_SIZE_BYTES) {
						if (Config.LOG_DEBUG) {
							System.out.println("[PassengerFlowProcessor] 特征数据过大，智能截断处理: " + featureStr.length() + " bytes");
						}

						// 智能截断：确保截断后的特征向量仍能正确解码
						String truncatedFeature = smartTruncateFeature(feature);
						featureInfo.put("feature", truncatedFeature);
						featureStr = featureInfo.toString();

						if (Config.LOG_DEBUG) {
							System.out.println("[PassengerFlowProcessor] 截断后大小: " + featureStr.length() + " bytes");
						}
					}

					txDown.sadd(featuresKey, featureStr);
					txDown.expire(featuresKey, Config.REDIS_TTL_FEATURES);

					// 执行下车事务
					txDown.exec();
				} catch (Exception e) {
					txDown.discard();
					if (Config.LOG_ERROR) {
						System.err.println("[Redis事务] 下车数据事务执行失败: " + e.getMessage());
					}
					throw e;
				}

				// 限制每个时间窗口的特征数量，避免数据过大
				String featuresKey = sqeNo != null && !sqeNo.isEmpty() ?
					"features_set:" + sqeNo :
					"features_set:" + canonicalBusNo + ":" + windowId;
				long featureCount = jedis.scard(featuresKey);
				if (featureCount > Config.MAX_FEATURES_PER_WINDOW) {
					if (Config.LOG_DEBUG) {
						System.out.println("[PassengerFlowProcessor] 特征数量过多，清理旧特征: " + featureCount);
					}
					// 随机删除一些旧特征，保留最新的
					Set<String> allFeatures = jedis.smembers(featuresKey);
					if (allFeatures != null && allFeatures.size() > Config.FEATURE_CLEANUP_THRESHOLD) {
						List<String> featureList = new ArrayList<>(allFeatures);
						// 按时间戳排序，删除最旧的
						featureList.sort((a, b) -> {
							try {
								JSONObject objA = new JSONObject(a);
								JSONObject objB = new JSONObject(b);
								String timeA = objA.optString("timestamp", "");
								String timeB = objB.optString("timestamp", "");
								return timeA.compareTo(timeB);
							} catch (Exception e) {
								return 0;
							}
						});
						// 删除最旧的20个特征
						for (int j = 0; j < 20 && j < featureList.size(); j++) {
							jedis.srem(featuresKey, featureList.get(j));
						}
					}
				}

				// 🔥 缓存图片URL：传递sqeNo
				if (imageUrl != null) {
					cacheImageUrl(jedis, canonicalBusNo, windowId, imageUrl, "down", sqeNo);
				}

				// 缓存乘客位置信息（特征向量 -> 位置信息的映射）
				String positionKey = "feature_position:" + canonicalBusNo + ":" + windowId + ":" + feature;
				JSONObject position = new JSONObject();
				position.put("xLeftUp", boxX);
				position.put("yLeftUp", boxY);
				position.put("xRightBottom", boxX + boxW);
				position.put("yRightBottom", boxY + boxH);
				position.put("direction", "down");
				jedis.set(positionKey, position.toString());
				jedis.expire(positionKey, Config.REDIS_TTL_FEATURES);
			}
		}

		// 不再在downup事件中自算总人数，统一以CV推送的vehicle_total_count为准

		// 汇总日志可按需开启，默认关闭
		if (Config.PILOT_ROUTE_LOG_ENABLED) {
			System.out.println("[CV客流数据] 收到车牌号" + busNo + "的客流信息推送数据，开始收集");
		}
	}

	/**
	 * 处理乘客特征向量匹配，计算区间客流
	 * @param downFeature 下车特征向量
	 * @param busNo 公交车编号
	 * @param jedis Redis连接
	 * @param eventTime 事件时间
	 * @param currentStationId 当前站点ID（下车站点）
	 * @param currentStationName 当前站点名称（下车站点）
	 * @param sqeNo 开关门唯一批次号
	 * @param windowId 时间窗口ID
	 */
	private void processPassengerMatching(String downFeature, String busNo, Jedis jedis, LocalDateTime eventTime, String currentStationId, String currentStationName, String sqeNo, String windowId) {
		try {
			// 🔥 windowId现在已经从调用方传入，无需再查询Redis
			if (windowId == null) {
				if (Config.PILOT_ROUTE_LOG_ENABLED) {
					System.out.println("[乘客匹配] 时间窗口为空，跳过匹配: busNo=" + busNo + ", sqeNo=" + sqeNo);
				}
				return;
			}

			// 🔥 获取上车特征集合：优先使用sqeNo
			String featuresKey = sqeNo != null && !sqeNo.isEmpty() ?
				"features_set:" + sqeNo :
				"features_set:" + busNo + ":" + windowId;
			Set<String> features = fetchFeaturesWithRetry(jedis, featuresKey);
			if (features == null || features.isEmpty()) {
				String nearestWindow = findNearestFeatureWindow(jedis, busNo, normalizeWindowId(windowId), Config.FEATURE_FALLBACK_WINDOW_MINUTES);
				if (nearestWindow != null) {
					String fallbackKey = "features_set:" + busNo + ":" + nearestWindow;
					features = fetchFeaturesWithRetry(jedis, fallbackKey);
					if (Config.PILOT_ROUTE_LOG_ENABLED) {
						System.out.println("[乘客匹配][回退] 使用最近窗口特征: from=" + windowId + " -> " + nearestWindow + ", size=" + (features != null ? features.size() : 0));
					}
				}
			}

			if (features == null || features.isEmpty()) {
				if (Config.PILOT_ROUTE_LOG_ENABLED) {
					System.out.println("[乘客匹配] 未找到上车特征，跳过匹配: busNo=" + busNo + ", windowId=" + windowId);
				}
				return;
			}

			if (Config.PILOT_ROUTE_LOG_ENABLED) {
				System.out.println("[乘客匹配] 开始匹配: busNo=" + busNo + ", windowId=" + windowId +
					", 上车特征数=" + features.size());
				System.out.println("[乘客匹配] 下车特征向量长度: " + (downFeature != null ? downFeature.length() : 0));
			}

			float[] downFeatureVec = CosineSimilarity.parseFeatureVector(downFeature);
			if (downFeatureVec.length == 0) return;

			// 🔥 去重：同一上车特征仅允许匹配一次，优先使用sqeNo
			String matchedUpKey = sqeNo != null && !sqeNo.isEmpty() ?
				"matched_up_features:" + sqeNo :
				"matched_up_features:" + busNo + ":" + windowId;

			// 遍历上车特征，寻找匹配
			for (String featureStr : features) {
				try {
					JSONObject featureObj = new JSONObject(featureStr);
					String direction = featureObj.optString("direction");

					// 只处理上车特征
					if (!"up".equals(direction)) continue;

					String upFeature = featureObj.optString("feature");

					// 已匹配过的上车特征跳过，避免重复计数
					try {
						if (upFeature != null && !upFeature.isEmpty() && jedis.sismember(matchedUpKey, upFeature)) {
							continue;
						}
					} catch (Exception ignore) {}

					// 时间顺序校验：上车时间需早于当前下车事件，且至少间隔1秒
					LocalDateTime upTime = null;
					try {
						String upTs = featureObj.optString("timestamp");
						if (upTs != null && !upTs.isEmpty()) {
							String normalized = upTs.contains("T") ? upTs.replace("T", " ") : upTs;
							upTime = LocalDateTime.parse(normalized, formatter);
						}
					} catch (Exception ignore) {}
					if (upTime != null) {
						if (!upTime.isBefore(eventTime.minusSeconds(1))) {
							continue;
						}
					}

					float[] upFeatureVec = CosineSimilarity.parseFeatureVector(upFeature);

					if (upFeatureVec.length > 0) {
						// 使用余弦相似度计算匹配度
						double similarity = CosineSimilarity.cosine(downFeatureVec, upFeatureVec);

						// 相似度大于0.5认为是同一乘客
						if (similarity > 0.5) {
							if (Config.LOG_DEBUG || Config.PILOT_ROUTE_LOG_ENABLED) {
								System.out.println("[PassengerFlowProcessor] 找到匹配乘客，相似度: " + similarity);
							}
							// 获取上车特征对象内的站点信息
							String stationIdOn2 = featureObj.optString("stationId");
							String stationNameOn2 = featureObj.optString("stationName");

							if (Config.PILOT_ROUTE_LOG_ENABLED) {
								System.out.println("[乘客匹配] 检查上车站点信息:");
								System.out.println("  featureObj中的stationId: " + stationIdOn2);
								System.out.println("  featureObj中的stationName: " + stationNameOn2);
								System.out.println("  upFeature: " + (upFeature != null ? upFeature.substring(0, Math.min(20, upFeature.length())) + "..." : "null"));
							}

							// 如果特征对象中没有站点信息，尝试从缓存获取
							if (stationIdOn2 == null || stationIdOn2.isEmpty() || "UNKNOWN".equals(stationIdOn2)
								|| stationNameOn2 == null || stationNameOn2.isEmpty() || "Unknown Station".equals(stationNameOn2)) {

								if (Config.PILOT_ROUTE_LOG_ENABLED) {
									System.out.println("[乘客匹配] 特征对象中站点信息无效，尝试从缓存获取");
								}

								JSONObject onStation = getOnStationFromCache(jedis, upFeature);
								if (onStation != null) {
									stationIdOn2 = onStation.optString("stationId");
									stationNameOn2 = onStation.optString("stationName");
									if (Config.PILOT_ROUTE_LOG_ENABLED) {
										System.out.println("[乘客匹配] 从缓存获取到站点信息: " + stationNameOn2 + "(" + stationIdOn2 + ")");
									}
								} else {
									if (Config.PILOT_ROUTE_LOG_ENABLED) {
										System.out.println("[乘客匹配] 缓存中也没有找到站点信息");
									}
								}
							}

							if (stationIdOn2 != null && !stationIdOn2.isEmpty()) {
								if (Config.PILOT_ROUTE_LOG_ENABLED) {
									System.out.println("[乘客匹配] 站点信息: 上车站点=" + stationNameOn2 +
										"(" + stationIdOn2 + "), 下车站点=" + currentStationName +
										"(" + currentStationId + ")");
								}

								// 同站过滤：同站上/下视为无效区间，跳过
								if (stationIdOn2.equals(currentStationId)) {
									if (Config.LOG_INFO) {
										System.out.println("[PassengerFlowProcessor] 跳过同站OD: station=" + currentStationName +
											", featureHashLen=" + (upFeature != null ? upFeature.length() : 0));
									}
									continue;
								}

								// 组装乘客明细：写入解码后的向量数组与上下车站名
								JSONObject passengerDetail = new JSONObject();
								passengerDetail.put("featureVector", toJsonArraySafe(upFeatureVec.length > 0 ? upFeatureVec : downFeatureVec));
								passengerDetail.put("stationIdOn", stationIdOn2);
								passengerDetail.put("stationNameOn", stationNameOn2);
								passengerDetail.put("stationIdOff", currentStationId);
								passengerDetail.put("stationNameOff", currentStationName);

								// 🔥 更新区间客流统计并追加明细，传递sqeNo
								updateSectionPassengerFlow(jedis, busNo, windowId,
									stationIdOn2,
									stationNameOn2,
									currentStationId,
									currentStationName,
									passengerDetail,
									sqeNo);

								// 记录已匹配的上车特征，避免后续重复匹配
								try {
									if (upFeature != null && !upFeature.isEmpty()) {
										jedis.sadd(matchedUpKey, upFeature);
										jedis.expire(matchedUpKey, Config.REDIS_TTL_OPEN_TIME);
									}
								} catch (Exception ignore) {}
							} else {
								if (Config.PILOT_ROUTE_LOG_ENABLED) {
									System.out.println("[乘客匹配] 上车站点信息为空，无法匹配: upFeature=" +
										(upFeature != null ? upFeature.substring(0, Math.min(20, upFeature.length())) + "..." : "null"));
								}
							}
							break; // 找到匹配后跳出循环
						}
					}
				} catch (Exception e) {
					if (Config.LOG_DEBUG) {
						System.out.println("[PassengerFlowProcessor] Failed to parse feature JSON: " + featureStr);
					}
				}
			}
		} catch (Exception e) {
			if (Config.LOG_ERROR) {
				System.err.println("[PassengerFlowProcessor] Error in processPassengerMatching: " + e.getMessage());
			}
		}
	}

	/**
	 * 更新区间客流统计
	 * @param jedis Redis连接
	 * @param busNo 公交车编号
	 * @param windowId 时间窗口ID
	 * @param stationIdOn 上车站点ID
	 * @param stationNameOn 上车站点名称
	 * @param stationIdOff 下车站点ID
	 * @param stationNameOff 下车站点名称
	 * @param passengerDetail 乘客详情
	 * @param sqeNo 开关门唯一批次号
	 */
	private void updateSectionPassengerFlow(Jedis jedis, String busNo, String windowId,
										  String stationIdOn, String stationNameOn,
										  String stationIdOff, String stationNameOff,
										  JSONObject passengerDetail, String sqeNo) {
		try {
			if (Config.LOG_DEBUG || Config.PILOT_ROUTE_LOG_ENABLED) {
				System.out.println("[PassengerFlowProcessor] 更新区间客流: " + stationNameOn + "(" + stationIdOn + ") -> " + stationNameOff + "(" + stationIdOff + ")");
				System.out.println("[PassengerFlowProcessor] 区间客流更新 - busNo=" + busNo + ", windowId=" + windowId + ", sqeNo=" + sqeNo);
			}
			// 🔥 优先使用sqeNo作为区间客流的key
			String flowKey = sqeNo != null && !sqeNo.isEmpty() ?
				"section_flow:" + sqeNo :
				"section_flow:" + busNo + ":" + windowId;

			// 构建区间标识
			String sectionKey = stationIdOn + "_" + stationIdOff;

			// 获取现有区间客流数据
			String existingFlowJson = jedis.hget(flowKey, sectionKey);
			JSONObject sectionFlow;

			if (existingFlowJson != null) {
				sectionFlow = new JSONObject(existingFlowJson);
			} else {
				sectionFlow = new JSONObject();
				sectionFlow.put("stationIdOn", stationIdOn);
				sectionFlow.put("stationNameOn", stationNameOn);
				sectionFlow.put("stationIdOff", stationIdOff);
				sectionFlow.put("stationNameOff", stationNameOff);
				sectionFlow.put("passengerFlowCount", 0);
				sectionFlow.put("detail", new JSONArray());
			}

			// 增加客流数
			int currentCount = sectionFlow.optInt("passengerFlowCount", 0);
			sectionFlow.put("passengerFlowCount", currentCount + 1);

			// 追加乘客明细
			try {
				JSONArray detailArray = sectionFlow.optJSONArray("detail");
				if (detailArray == null) {
					detailArray = new JSONArray();
				}
				if (passengerDetail != null) {
					detailArray.put(passengerDetail);
				}
				sectionFlow.put("detail", detailArray);
			} catch (Exception ignore) {}

			// 更新Redis
			jedis.hset(flowKey, sectionKey, sectionFlow.toString());
			jedis.expire(flowKey, Config.REDIS_TTL_OPEN_TIME);

			if (Config.LOG_DEBUG || Config.PILOT_ROUTE_LOG_ENABLED) {
				System.out.println("[PassengerFlowProcessor] 区间客流更新完成，当前客流数: " + sectionFlow.optInt("passengerFlowCount", 0));
			}

		} catch (Exception e) {
			if (Config.LOG_ERROR) {
				System.err.println("[PassengerFlowProcessor] Error updating section passenger flow: " + e.getMessage());
			}
		}
	}

	private void handleLoadFactorEvent(JSONObject data, String busNo, String busId, Jedis jedis) {
		String sqeNo = data.optString("sqe_no");  // 新增：获取开关门唯一批次号
		int count = data.optInt("count");
		double factor = data.optDouble("factor");
		String cameraNo = data.optString("camera_no");

		// 打印CV推送的满载率数据，用于开关门timestamp校验
		if (Config.PILOT_ROUTE_LOG_ENABLED) {
			System.out.println("[CV满载率数据] 收到车牌号" + busNo + "的满载率数据，sqe_no=" + sqeNo + "，开始收集");
		}


		// 现在直接使用bus_id作为canonicalBusNo，不再需要映射
		String canonicalBusNo = busId != null && !busId.isEmpty() ? busId : busNo;

		// 缓存 camera 与 bus 的映射，便于反查
		if (cameraNo != null && !cameraNo.isEmpty() && !"default".equalsIgnoreCase(cameraNo)) {
			jedis.set("bus_alias_by_camera:" + cameraNo, canonicalBusNo);
			jedis.expire("bus_alias_by_camera:" + cameraNo, Config.REDIS_TTL_OPEN_TIME);
		}

		// 🔥 缓存满载率和车辆总人数：优先使用sqe_no
		if (sqeNo != null && !sqeNo.isEmpty()) {
			String loadFactorKey = "load_factor:" + sqeNo;
			String vehicleTotalCountKey = "vehicle_total_count:" + sqeNo;
			jedis.set(loadFactorKey, String.valueOf(factor));
			jedis.set(vehicleTotalCountKey, String.valueOf(count));
			jedis.expire(loadFactorKey, Config.REDIS_TTL_COUNTS);
			jedis.expire(vehicleTotalCountKey, Config.REDIS_TTL_COUNTS);
		}

		// 兼容性存储：保持原有逻辑
		String loadFactorKeyLegacy = "load_factor:" + canonicalBusNo;
		String vehicleTotalCountKeyLegacy = "vehicle_total_count:" + canonicalBusNo;
		jedis.set(loadFactorKeyLegacy, String.valueOf(factor));
		jedis.set(vehicleTotalCountKeyLegacy, String.valueOf(count));  // 存储CV系统的车辆总人数
		jedis.expire(loadFactorKeyLegacy, Config.REDIS_TTL_COUNTS);
		jedis.expire(vehicleTotalCountKeyLegacy, Config.REDIS_TTL_COUNTS);

		System.out.println("[CV数据映射] 最终使用的bus_no: " + canonicalBusNo + ", 已缓存满载率数据");
	}

	private void handleOpenDoorEvent(JSONObject data, String busNo, String busId, String cameraNo, Jedis jedis) throws IOException, SQLException {
		String action = data.optString("action");
		String sqeNo = data.optString("sqe_no");  // 新增：获取开关门唯一批次号
		LocalDateTime eventTime = LocalDateTime.parse(data.optString("timestamp").replace(" ", "T"));
		String stationId = data.optString("stationId", "UNKNOWN");
		String stationName = data.optString("stationName", "Unknown Station");

		// 🔥 调试：检查CV回推的开门事件是否包含sqe_no
		if (Config.LOG_DEBUG) {
			System.out.println("[PassengerFlowProcessor] 🔥 CV回推开门事件:");
			System.out.println("   sqe_no: " + (sqeNo != null && !sqeNo.isEmpty() ? sqeNo : "NULL或空"));
			System.out.println("   完整data: " + data.toString());
			System.out.println("   ================================================================================");
		}

		// 打印本地生成的开关门事件数据，用于timestamp校验
		System.out.println("[本地开关门事件] open_close_door事件数据详情:");
		System.out.println("   bus_no: " + busNo);
		System.out.println("   bus_id: " + busId);
		System.out.println("   camera_no: " + cameraNo);
		System.out.println("   action: " + action);
		System.out.println("   sqe_no: " + sqeNo);  // 新增：打印sqe_no
		System.out.println("   timestamp: " + data.optString("timestamp"));
		System.out.println("   stationId: " + stationId);
		System.out.println("   stationName: " + stationName);
		System.out.println("   ================================================================================");

		// 现在直接使用bus_id作为canonicalBusNo，不再需要映射
		String canonicalBusNo = busId != null && !busId.isEmpty() ? busId : busNo;

		if ("open".equals(action)) {
			// 验证sqe_no必须存在
			if (sqeNo == null || sqeNo.isEmpty()) {
				if (Config.LOG_ERROR) {
					System.err.println("[开门事件] sqe_no为空，无法处理开门事件: busNo=" + busNo);
				}
				return;
			}

			// 试点线路本地开门流程日志（可通过配置控制）
			if (Config.PILOT_ROUTE_LOG_ENABLED) {
				System.out.println("[本地开门流程] 生成车牌号" + busNo + "的开门信号，sqe_no=" + sqeNo + "，开始收集");
			}

			// 开门时创建记录并缓存（不再设置单独的站点字段，使用区间客流统计）
			BusOdRecord record = createBaseRecord(canonicalBusNo, cameraNo, eventTime, jedis, sqeNo);
			record.setTimestampBegin(eventTime);

			// 生成开门时间窗口ID = 开门时间字符串（与Kafka侧一致）
			String windowId = eventTime.format(formatter);

			// 🔥 使用Redis事务保证所有映射关系和计数器的原子性操作
			Transaction tx = jedis.multi();
			try {
				// 主要存储：基于sqe_no的新映射关系
				tx.set("open_time:" + sqeNo, windowId);
				tx.set("open_time_index:" + windowId, sqeNo);
				tx.set("canonical_bus:" + sqeNo, canonicalBusNo);
				tx.expire("open_time:" + sqeNo, Config.REDIS_TTL_OPEN_TIME);
				tx.expire("open_time_index:" + windowId, Config.REDIS_TTL_OPEN_TIME);
				tx.expire("canonical_bus:" + sqeNo, Config.REDIS_TTL_OPEN_TIME);

				// 兼容性存储：保持原有逻辑作为兜底
				tx.set("open_time:" + canonicalBusNo, windowId);
				tx.expire("open_time:" + canonicalBusNo, Config.REDIS_TTL_OPEN_TIME);

				// 记录时间到bus的索引，便于downup仅凭timestamp反查（兼容性）
				tx.set("open_time_index:" + windowId + ":legacy", canonicalBusNo);
				tx.expire("open_time_index:" + windowId + ":legacy", Config.REDIS_TTL_OPEN_TIME);

				// 建立新的映射关系：优先使用stationId、stationName、bus_id三个值做匹配
				if (stationId != null && !stationId.isEmpty() && stationName != null && !stationName.isEmpty() && busId != null && !busId.isEmpty()) {
					tx.set("open_time_by_station:" + stationId + ":" + stationName + ":" + busId, windowId);
					tx.expire("open_time_by_station:" + stationId + ":" + stationName + ":" + busId, Config.REDIS_TTL_OPEN_TIME);
				}

				// 建立camera与bus/window的映射，便于CV用车牌号推送时反查
				if (cameraNo != null && !cameraNo.isEmpty()) {
					tx.set("open_time_by_camera:" + cameraNo, windowId);
					tx.expire("open_time_by_camera:" + cameraNo, Config.REDIS_TTL_OPEN_TIME);
					tx.set("bus_alias_by_camera:" + cameraNo, canonicalBusNo);
					tx.expire("bus_alias_by_camera:" + cameraNo, Config.REDIS_TTL_OPEN_TIME);
				}

				// 建立车牌号到bus_no的映射，便于后续CV数据反查
				tx.set("plate_to_bus:" + busNo, canonicalBusNo);
				tx.expire("plate_to_bus:" + busNo, Config.REDIS_TTL_OPEN_TIME);

				// 主要计数：基于sqe_no
				tx.set("cv_up_count:" + sqeNo, "0");
				tx.set("cv_down_count:" + sqeNo, "0");
				tx.expire("cv_up_count:" + sqeNo, Config.REDIS_TTL_OPEN_TIME);
				tx.expire("cv_down_count:" + sqeNo, Config.REDIS_TTL_OPEN_TIME);

				// 兼容性计数：保持原有逻辑作为兜底
				tx.set("cv_up_count:" + canonicalBusNo + ":" + windowId, "0");
				tx.set("cv_down_count:" + canonicalBusNo + ":" + windowId, "0");
				tx.expire("cv_up_count:" + canonicalBusNo + ":" + windowId, Config.REDIS_TTL_OPEN_TIME);
				tx.expire("cv_down_count:" + canonicalBusNo + ":" + windowId, Config.REDIS_TTL_OPEN_TIME);

				// 执行事务
				tx.exec();

				if (Config.PILOT_ROUTE_LOG_ENABLED) {
					System.out.println("[本地开门流程] 建立站点映射: stationId=" + stationId + ", stationName=" + stationName + ", busId=" + busId + ", windowId=" + windowId);
					System.out.println("[Redis事务] 开门映射关系和计数器原子性操作完成: sqeNo=" + sqeNo);
				}
			} catch (Exception e) {
				// 事务失败，回滚
				tx.discard();
				if (Config.LOG_ERROR) {
					System.err.println("[Redis事务] 开门事务执行失败: " + e.getMessage());
				}
				throw e;
			}

			if (Config.PILOT_ROUTE_LOG_ENABLED) {
				System.out.println("[试点线路本地开门流程] 开门时间窗口已创建:");
				System.out.println("   windowId=" + windowId);
				System.out.println("   上车计数已初始化");
				System.out.println("   下车计数已初始化");
				System.out.println("   ================================================================================");
			}

			if (Config.LOG_INFO) {
				System.out.println("[PassengerFlowProcessor] Door OPEN event processed for plate=" + busNo + ", busNo=" + canonicalBusNo + ", windowId=" + windowId);
			}
		}
	}

	private void handleCloseDoorAndCVComplateEvent(JSONObject data, String busNo, String busId, String cameraNo, Jedis jedis) throws IOException, SQLException {
		String sqeNo = data.optString("sqe_no");  // 新增：获取开关门唯一批次号
		LocalDateTime eventTime = LocalDateTime.parse(data.optString("timestamp").replace(" ", "T"));

		// 🔥 调试：检查CV回推的notify_complete事件是否包含sqe_no
		if (Config.LOG_DEBUG) {
			System.out.println("[PassengerFlowProcessor] 🔥 CV回推notify_complete事件:");
			System.out.println("   sqe_no: " + (sqeNo != null && !sqeNo.isEmpty() ? sqeNo : "NULL或空"));
			System.out.println("   完整data: " + data.toString());
			System.out.println("   ================================================================================");
		}

		// notify_complete事件处理 - 收到CV的公交分析业务处理结束信号，开始发Kafka落库
		if (Config.PILOT_ROUTE_LOG_ENABLED) {
			System.out.println("[CV业务完成] 收到notify_complete事件:");
			System.out.println("   busNo=" + busNo);
			System.out.println("   busId=" + busId);
			System.out.println("   sqe_no=" + sqeNo);  // 新增：打印sqe_no
			System.out.println("   cameraNo=" + cameraNo);
			System.out.println("   完成时间=" + eventTime.format(formatter));
			System.out.println("   stationId=" + data.optString("stationId"));
			System.out.println("   stationName=" + data.optString("stationName"));
			System.out.println("   ================================================================================");
		}

		// 🔥 修复关键漏洞：优先通过sqe_no获取时间窗口
		String windowId = null;
		String canonicalBusNo = null;

		if (sqeNo != null && !sqeNo.isEmpty()) {
			windowId = jedis.get("open_time:" + sqeNo);
			canonicalBusNo = jedis.get("canonical_bus:" + sqeNo);
			if (Config.PILOT_ROUTE_LOG_ENABLED) {
				System.out.println("[CV业务完成] 🔥 通过sqe_no找到: windowId=" + windowId + ", canonicalBusNo=" + canonicalBusNo);
			}
		}

		// 兜底：如果sqe_no匹配失败，使用canonicalBusNo逻辑（修复原有漏洞）
		if (windowId == null || canonicalBusNo == null) {
			canonicalBusNo = busId != null && !busId.isEmpty() ? busId : busNo;
			windowId = jedis.get("open_time:" + canonicalBusNo);
			if (Config.PILOT_ROUTE_LOG_ENABLED) {
				System.out.println("[CV业务完成] 兜底匹配: canonicalBusNo=" + canonicalBusNo + ", windowId=" + windowId);
			}
		}
		// 标准化windowId格式，统一为空格分隔，避免后续解析和Redis Key不一致
		String normalizedWindowId = windowId;
		if (normalizedWindowId != null && normalizedWindowId.contains("T")) {
			normalizedWindowId = normalizedWindowId.replace("T", " ");
		}
		if (windowId != null) {
			// 🔥 幂等性检查：优先使用sqe_no
			String odSentKey = sqeNo != null && !sqeNo.isEmpty() ?
				"od_sent:" + sqeNo :
				"od_sent:" + canonicalBusNo + ":" + normalizedWindowId;
			if (jedis.get(odSentKey) != null) {
				if (Config.PILOT_ROUTE_LOG_ENABLED) {
					System.out.println("[CV业务完成] 已检测到OD已发送标记，跳过重复发送。key=" + odSentKey);
				}
				return;
			}
			if (Config.PILOT_ROUTE_LOG_ENABLED) {
				System.out.println("[CV业务完成] 找到开门时间窗口:");
				System.out.println("   windowId=" + normalizedWindowId);
				System.out.println("   ================================================================================");
			}

			// 🔥 获取CV计数：优先使用sqe_no
			int[] cvCounts = waitForCvResultsStable(jedis, canonicalBusNo, normalizedWindowId, sqeNo);
			int cvUpCount = cvCounts[0];
			int cvDownCount = cvCounts[1];

			if (Config.PILOT_ROUTE_LOG_ENABLED) {
				System.out.println("[CV业务完成] CV计数统计完成:");
				System.out.println("   上车人数=" + cvUpCount);
				System.out.println("   下车人数=" + cvDownCount);
				System.out.println("   ==============================================================================");
			}

			// 创建关门记录
			BusOdRecord record = createBaseRecord(canonicalBusNo, cameraNo, eventTime, jedis, sqeNo);
			// 从windowId恢复开门时间，避免begin与end相同
			LocalDateTime beginTime = null;
			try {
				beginTime = LocalDateTime.parse(normalizedWindowId, formatter);
				record.setTimestampBegin(beginTime);
			} catch (Exception e) {
				// 兜底：如果解析失败，保持空
			}
			record.setTimestampEnd(eventTime);
			record.setUpCount(cvUpCount);
			record.setDownCount(cvDownCount);

			// 🔥 设置区间客流统计：传递sqeNo
			setSectionPassengerFlowCount(record, jedis, canonicalBusNo, normalizedWindowId, sqeNo);

			// 🔥 设置乘客特征集合：传递sqeNo
			setPassengerFeatures(record, jedis, busNo, normalizedWindowId, sqeNo);

			// 🔥 并行处理图片：使用容忍时间窗口 [open-30s, close+30s]，传递sqeNo
			try {
				List<String> rangedImages = getImagesByTimeRange(jedis, busNo, beginTime, eventTime,
					Config.IMAGE_TIME_TOLERANCE_BEFORE_SECONDS, Config.IMAGE_TIME_TOLERANCE_AFTER_SECONDS, sqeNo);
				processImagesParallelWithList(record, jedis, busNo, normalizedWindowId, eventTime, rangedImages, sqeNo);
			} catch (Exception e) {
				if (Config.LOG_ERROR) {
					System.err.println("[PassengerFlowProcessor] Error in parallel image processing: " + e.getMessage());
				}
			}

			// 🔥 设置车辆总人数（从CV系统获取）：优先使用sqe_no
			record.setVehicleTotalCount(getVehicleTotalCountFromRedis(jedis, canonicalBusNo, sqeNo));

			// 设置原始数据字段用于校验
			record.setRetrieveBusGpsMsg(getBusGpsMsgFromRedis(jedis, busNo));
			record.setRetrieveDownupMsg(getDownupMsgFromRedis(jedis, busNo));

			if (Config.PILOT_ROUTE_LOG_ENABLED) {
				System.out.println("[CV业务完成] 准备落库，发送kafka:busNo=" + busNo);
			}
			sendToKafka(record);
			// 设置OD发送幂等标记
			jedis.set(odSentKey, "1");
			jedis.expire(odSentKey, Config.REDIS_TTL_OPEN_TIME);

			// 注意：不再手动清理Redis缓存，让Redis的TTL机制和RedisCleanupUtil自动管理
			// 这样可以确保乘客特征向量、区间客流数据等关键信息在需要时仍然可用
			if (Config.PILOT_ROUTE_LOG_ENABLED) {
				System.out.println("[CV业务完成] 车牌号" + busNo + "的OD数据处理完成，已发送至Kafka");
			}
		}
	}

	/**
	 * 设置区间客流统计信息
	 * @param record BusOdRecord记录
	 * @param jedis Redis连接
	 * @param busNo 公交车编号
	 * @param windowId 时间窗口ID
	 */
	private void setSectionPassengerFlowCount(BusOdRecord record, Jedis jedis, String busNo, String windowId, String sqeNo) {
		try {
			// 🔥 优先使用sqeNo获取区间客流数据
			String flowKey = sqeNo != null && !sqeNo.isEmpty() ?
				"section_flow:" + sqeNo :
				"section_flow:" + busNo + ":" + windowId;
			Map<String, String> sectionFlows = jedis.hgetAll(flowKey);

			if (Config.PILOT_ROUTE_LOG_ENABLED) {
				System.out.println("[流程] 开始设置区间客流统计: busNo=" + busNo + ", windowId=" + windowId + ", sqeNo=" + sqeNo);
				System.out.println("[流程] Redis键: " + flowKey);
				System.out.println("[流程] 获取到的区间数据数量: " + (sectionFlows != null ? sectionFlows.size() : 0));
			}

			if (sectionFlows != null && !sectionFlows.isEmpty()) {
				JSONArray sectionFlowArray = new JSONArray();

				for (String sectionKey : sectionFlows.keySet()) {
					String flowJson = sectionFlows.get(sectionKey);
					JSONObject flowObj = new JSONObject(flowJson);
					sectionFlowArray.put(flowObj);

					if (Config.PILOT_ROUTE_LOG_ENABLED) {
						System.out.println("[流程] 处理区间: " + sectionKey + " -> " + flowObj.optString("stationNameOn") + " -> " + flowObj.optString("stationNameOff") +
							", 客流数: " + flowObj.optInt("passengerFlowCount", 0));
					}
				}

				record.setSectionPassengerFlowCount(sectionFlowArray.toString());

				if (Config.PILOT_ROUTE_LOG_ENABLED) {
					System.out.println("[流程] 区间客流统计设置完成，区间数: " + sectionFlowArray.length());
					System.out.println("[流程] 最终JSON长度: " + sectionFlowArray.toString().length());
				}
			} else {
				if (Config.PILOT_ROUTE_LOG_ENABLED) {
					System.out.println("[流程] 警告：未找到区间客流数据，sectionPassengerFlowCount将保持为null");
				}
			}
		} catch (Exception e) {
			if (Config.LOG_ERROR) {
				System.err.println("[PassengerFlowProcessor] Error setting section passenger flow count: " + e.getMessage());
			}
		}
	}

	/**
	 * 并行处理图片：AI分析和视频转换
	 * @param record BusOdRecord记录
	 * @param jedis Redis连接
	 * @param busNo 公交车编号
	 * @param windowId 时间窗口ID
	 * @param eventTime 事件时间
	 */
	private void processImagesParallel(BusOdRecord record, Jedis jedis, String busNo, String windowId, LocalDateTime eventTime) throws IOException, SQLException {
		System.out.println("[并行处理] 开始为车辆 " + busNo + " 并行处理图片，时间窗口: " + windowId);

		// 1. 收集图片URL
		List<String> imageUrls = getAllImageUrls(jedis, busNo, windowId);

		if (imageUrls == null || imageUrls.isEmpty()) {
			System.out.println("[并行处理] 没有图片需要处理，跳过");
			return;
		}

		System.out.println("[并行处理] 收集到 " + imageUrls.size() + " 张图片，开始并行处理");

		// 2. 设置图片URL集合到记录中
		JSONArray imageArray = new JSONArray();
		for (String imageUrl : imageUrls) {
			imageArray.put(imageUrl);
		}
		record.setPassengerImages(imageArray.toString());

		// 3. 并行处理：AI分析和视频转换
		try {
			// 3.1 AI分析（同步执行，因为需要结果）
			System.out.println("[并行处理] 开始AI图片分析");
			analyzeImagesWithAI(jedis, busNo, eventTime, record, imageUrls);

			// 3.2 视频转换（同步执行，因为需要结果）
			System.out.println("[并行处理] 开始分别按方向图片转视频");
			LocalDateTime begin = record.getTimestampBegin();
			LocalDateTime end = record.getTimestampEnd();
			if (begin != null && end != null) {
				Map<String, List<String>> imagesByDir = getImagesByTimeRangeSeparated(jedis, busNo, begin, end,
					Config.IMAGE_TIME_TOLERANCE_BEFORE_SECONDS, Config.IMAGE_TIME_TOLERANCE_AFTER_SECONDS, null);
				List<String> upImages = imagesByDir.getOrDefault("up", new ArrayList<>());
				List<String> downImages = imagesByDir.getOrDefault("down", new ArrayList<>());
				processImagesToVideoByDirection(record, jedis, busNo, windowId, upImages, downImages);
			} else {
				Map<String, List<String>> imagesByDir = getImagesByExactWindowSeparated(jedis, busNo, windowId, null);
				List<String> upImages = imagesByDir.getOrDefault("up", new ArrayList<>());
				List<String> downImages = imagesByDir.getOrDefault("down", new ArrayList<>());
				processImagesToVideoByDirection(record, jedis, busNo, windowId, upImages, downImages);
			}

			System.out.println("[并行处理] 并行处理完成，AI分析和视频转换都已成功");

		} catch (Exception e) {
			System.err.println("[并行处理] 并行处理过程中发生异常: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * 处理图片转视频功能
	 * @param record BusOdRecord记录
	 * @param jedis Redis连接
	 * @param busNo 公交车编号
	 * @param windowId 时间窗口ID
	 * @param imageUrls 图片URL列表（已收集）
	 */
	private void processImagesToVideo(BusOdRecord record, Jedis jedis, String busNo, String windowId, List<String> imageUrls) {
		System.out.println("[图片转视频] 开始为车辆 " + busNo + " 处理图片转视频，时间窗口: " + windowId);

		try {
			// 使用传入的图片URL列表
			if (imageUrls != null && !imageUrls.isEmpty()) {
				System.out.println("[图片转视频] 收集到 " + imageUrls.size() + " 张图片，开始转换视频");

				// 设置图片URL集合
				JSONArray imageArray = new JSONArray();
				for (String imageUrl : imageUrls) {
					imageArray.put(imageUrl);
				}
				record.setPassengerImages(imageArray.toString());

				// 转换为视频 - 与AI分析并行处理，用于存储和展示
				try {
					System.out.println("[图片转视频] 开始调用FFmpeg转换图片为视频，临时目录: " + System.getProperty("java.io.tmpdir"));

					String tempDir = System.getProperty("java.io.tmpdir");
					File videoFile = ImageToVideoConverter.convertImagesToVideo(imageUrls, tempDir);

					System.out.println("[图片转视频] FFmpeg转换完成，生成视频文件: " + videoFile.getAbsolutePath() + ", 大小: " + videoFile.length() + " 字节");

					// 生成动态目录名（基于开关门事件）
					String dynamicDir = "PassengerFlowRecognition/" + windowId;
					System.out.println("[图片转视频] 准备上传视频到OSS，目录: " + dynamicDir);

					// 上传视频到OSS（使用视频配置）
					String videoUrl = OssUtil.uploadVideoFile(videoFile, UUID.randomUUID().toString() + ".mp4", dynamicDir);
					record.setPassengerVideoUrl(videoUrl);

					System.out.println("[图片转视频] 视频上传OSS成功，URL: " + videoUrl);

					// 删除临时视频文件
					videoFile.delete();
					System.out.println("[图片转视频] 临时视频文件已清理");

				} catch (Exception e) {
					System.err.println("[图片转视频] 转换失败: " + e.getMessage());
					e.printStackTrace();

					// 设置默认值，避免字段为null
					record.setPassengerVideoUrl("");
					System.out.println("[图片转视频] 转换失败，已设置默认值");
				}
			} else {
				// 没有图片时设置默认值
				record.setPassengerImages("[]");
				record.setPassengerVideoUrl("");
				System.out.println("[图片转视频] 没有图片需要处理，已设置默认值");
			}
		} catch (Exception e) {
			System.err.println("[图片转视频] 处理过程发生异常: " + e.getMessage());
			e.printStackTrace();

			// 异常情况下设置默认值
			record.setPassengerImages("[]");
			record.setPassengerVideoUrl("");
			System.out.println("[图片转视频] 异常处理，已设置默认值");
		}
	}

	/**
	 * 🔥 增强图片收集：多种方式尝试收集图片URL
	 * @param jedis Redis连接
	 * @param busNo 公交车编号
	 * @param windowId 时间窗口ID
	 * @param sqeNo 开关门唯一批次号
	 * @param beginTime 开始时间
	 * @param endTime 结束时间
	 * @return 图片URL列表
	 */
	private List<String> enhancedImageCollection(Jedis jedis, String busNo, String windowId, String sqeNo,
			LocalDateTime beginTime, LocalDateTime endTime) {
		List<String> imageUrls = new ArrayList<>();

		System.out.println("[增强图片收集] 开始多种方式收集图片: busNo=" + busNo + ", windowId=" + windowId + ", sqeNo=" + sqeNo);

		try {
			// 方式1：基于sqe_no收集
			if (sqeNo != null && !sqeNo.isEmpty()) {
				Set<String> upImages = jedis.smembers("image_urls:" + sqeNo + ":up");
				Set<String> downImages = jedis.smembers("image_urls:" + sqeNo + ":down");
				if (upImages != null) imageUrls.addAll(upImages);
				if (downImages != null) imageUrls.addAll(downImages);
				System.out.println("[增强图片收集] 方式1(sqe_no): 收集到 " + imageUrls.size() + " 张图片");
			}

			// 方式2：基于时间窗口收集
			if (imageUrls.isEmpty() && windowId != null) {
				List<String> windowImages = getImagesByExactWindow(jedis, busNo, windowId);
				imageUrls.addAll(windowImages);
				System.out.println("[增强图片收集] 方式2(时间窗口): 收集到 " + windowImages.size() + " 张图片");
			}

			// 方式3：基于时间范围收集
			if (imageUrls.isEmpty() && beginTime != null && endTime != null) {
				List<String> rangeImages = getImagesByTimeRange(jedis, busNo, beginTime, endTime,
					Config.IMAGE_TIME_TOLERANCE_BEFORE_SECONDS, Config.IMAGE_TIME_TOLERANCE_AFTER_SECONDS, sqeNo);
				imageUrls.addAll(rangeImages);
				System.out.println("[增强图片收集] 方式3(时间范围): 收集到 " + rangeImages.size() + " 张图片");
			}

			// 方式4：模糊匹配收集
			if (imageUrls.isEmpty() && windowId != null) {
				List<String> fuzzyImages = getImagesByFuzzyWindow(jedis, busNo, windowId);
				imageUrls.addAll(fuzzyImages);
				System.out.println("[增强图片收集] 方式4(模糊匹配): 收集到 " + fuzzyImages.size() + " 张图片");
			}

			// 方式5：扫描所有相关Redis键
			if (imageUrls.isEmpty()) {
				Set<String> allImageKeys = jedis.keys("image_urls:*" + busNo + "*");
				if (allImageKeys != null) {
					for (String key : allImageKeys) {
						Set<String> images = jedis.smembers(key);
						if (images != null) imageUrls.addAll(images);
					}
					System.out.println("[增强图片收集] 方式5(全扫描): 扫描到 " + allImageKeys.size() + " 个键，收集到 " + imageUrls.size() + " 张图片");
				}
			}

			// 去重
			imageUrls = new ArrayList<>(new HashSet<>(imageUrls));
			System.out.println("[增强图片收集] 最终收集到 " + imageUrls.size() + " 张不重复图片");

		} catch (Exception e) {
			System.err.println("[增强图片收集] 收集过程异常: " + e.getMessage());
		}

		return imageUrls;
	}

	// 已合并：模糊匹配方法仅保留基于前后5分钟搜索的实现，避免重复定义

	/**
	 * 获取所有图片URL
	 * @param jedis Redis连接
	 * @param busNo 公交车编号
	 * @param windowId 时间窗口ID
	 * @return 图片URL列表
	 */
	private List<String> getAllImageUrls(Jedis jedis, String busNo, String windowId) {
		List<String> imageUrls = new ArrayList<>();

		System.out.println("[图片收集] 开始收集车辆 " + busNo + " 在时间窗口 " + windowId + " 的图片URL");

		try {
			// 首先尝试精确匹配
			List<String> exactMatchImages = getImagesByExactWindow(jedis, busNo, windowId);
			if (!exactMatchImages.isEmpty()) {
				imageUrls.addAll(exactMatchImages);
				System.out.println("[图片收集] 精确匹配收集到图片 " + exactMatchImages.size() + " 张");
			} else {
				// 如果精确匹配失败，尝试模糊匹配（前后5分钟）
				System.out.println("[图片收集] 精确匹配未找到图片，尝试模糊匹配...");
				List<String> fuzzyMatchImages = getImagesByFuzzyWindow(jedis, busNo, windowId);
				if (!fuzzyMatchImages.isEmpty()) {
					imageUrls.addAll(fuzzyMatchImages);
					System.out.println("[图片收集] 模糊匹配收集到图片 " + fuzzyMatchImages.size() + " 张");
				}
			}

			System.out.println("[图片收集] 总共收集到图片 " + imageUrls.size() + " 张");

		} catch (Exception e) {
			System.err.println("[图片收集] 收集图片URL时发生异常: " + e.getMessage());
			e.printStackTrace();
		}

		return imageUrls;
	}

	/**
	 * 基于时间区间的图片收集：从 [openTime - beforeSec, closeTime + afterSec] 聚合
	 * @param sqeNo 开关门唯一批次号
	 */
	private List<String> getImagesByTimeRange(Jedis jedis, String busNo, LocalDateTime openTime,
			LocalDateTime closeTime, int beforeSec, int afterSec, String sqeNo) {
		List<String> imageUrls = new ArrayList<>();
		if (openTime == null || closeTime == null) return imageUrls;
		try {
			LocalDateTime from = openTime.minusSeconds(Math.max(0, beforeSec));
			LocalDateTime to = closeTime.plusSeconds(Math.max(0, afterSec));
			System.out.println("[图片收集] 区间聚合: bus=" + busNo + ", from=" + from.format(formatter) + ", to=" + to.format(formatter) + ", sqeNo=" + sqeNo);

			// 🔥 优先尝试基于sqeNo的图片收集
			if (sqeNo != null && !sqeNo.isEmpty()) {
				// 上车图片
				Set<String> upImagesBySqe = jedis.smembers("image_urls:" + sqeNo + ":up");
				if (upImagesBySqe != null && !upImagesBySqe.isEmpty()) {
					imageUrls.addAll(upImagesBySqe);
					System.out.println("[图片收集] 基于sqeNo收集到上车图片 " + upImagesBySqe.size() + " 张");
				}
				// 下车图片
				Set<String> downImagesBySqe = jedis.smembers("image_urls:" + sqeNo + ":down");
				if (downImagesBySqe != null && !downImagesBySqe.isEmpty()) {
					imageUrls.addAll(downImagesBySqe);
					System.out.println("[图片收集] 基于sqeNo收集到下车图片 " + downImagesBySqe.size() + " 张");
				}
			}

			// 🔥 如果基于sqeNo没有找到图片，或作为兜底，按时间范围收集
			if (imageUrls.isEmpty()) {
				LocalDateTime cursor = from;
				while (!cursor.isAfter(to)) {
					String win = cursor.format(formatter);
					// 上车
					Set<String> up = jedis.smembers("image_urls:" + busNo + ":" + win + ":up");
					if (up != null && !up.isEmpty()) imageUrls.addAll(up);
					// 下车
					Set<String> down = jedis.smembers("image_urls:" + busNo + ":" + win + ":down");
					if (down != null && !down.isEmpty()) imageUrls.addAll(down);
					cursor = cursor.plusSeconds(1); // 秒级扫描
				}
				System.out.println("[图片收集] 兜底按时间范围收集到图片 " + imageUrls.size() + " 张");
			}
			System.out.println("[图片收集] 区间聚合共收集到图片 " + imageUrls.size() + " 张");
		} catch (Exception e) {
			System.err.println("[图片收集] 区间聚合异常: " + e.getMessage());
		}
		return imageUrls;
	}

	/**
	 * 使用外部提供的图片列表执行并行处理
	 * @param sqeNo 开关门唯一批次号
	 */
	private void processImagesParallelWithList(BusOdRecord record, Jedis jedis, String busNo, String windowId,
			LocalDateTime eventTime, List<String> imageUrls, String sqeNo) throws IOException, SQLException {
		System.out.println("[并行处理] 开始为车辆 " + busNo + " 并行处理图片(区间聚合)，时间窗口: " + windowId);

		// 🔥 增强图片收集：如果传入的图片列表为空，尝试多种方式收集
		if (imageUrls == null || imageUrls.isEmpty()) {
			System.out.println("[并行处理] 传入图片列表为空，尝试增强收集...");
			imageUrls = enhancedImageCollection(jedis, busNo, windowId, sqeNo, record.getTimestampBegin(), record.getTimestampEnd());
		}

		if (imageUrls == null || imageUrls.isEmpty()) {
			System.out.println("[并行处理] 增强收集后仍无图片，设置默认值");
			record.setPassengerImages("[]");
			return;
		}

		JSONArray imageArray = new JSONArray();
		for (String imageUrl : imageUrls) imageArray.put(imageUrl);
		record.setPassengerImages(imageArray.toString());

		System.out.println("[并行处理] 成功设置passengerImages字段，图片数量: " + imageUrls.size());
		try {
			System.out.println("[并行处理] 开始AI图片分析");
			analyzeImagesWithAI(jedis, busNo, eventTime, record, imageUrls);
			System.out.println("[并行处理] 开始分别按方向图片转视频");
			LocalDateTime begin = record.getTimestampBegin();
			LocalDateTime end = record.getTimestampEnd();
			if (begin != null && end != null) {
				// 🔥 传递sqeNo参数
				Map<String, List<String>> imagesByDir = getImagesByTimeRangeSeparated(jedis, busNo, begin, end,
					Config.IMAGE_TIME_TOLERANCE_BEFORE_SECONDS, Config.IMAGE_TIME_TOLERANCE_AFTER_SECONDS, sqeNo);
				List<String> upImages = imagesByDir.getOrDefault("up", new ArrayList<>());
				List<String> downImages = imagesByDir.getOrDefault("down", new ArrayList<>());
				processImagesToVideoByDirection(record, jedis, busNo, windowId, upImages, downImages);
			} else {
				// 🔥 传递sqeNo参数
				Map<String, List<String>> imagesByDir = getImagesByExactWindowSeparated(jedis, busNo, windowId, sqeNo);
				List<String> upImages = imagesByDir.getOrDefault("up", new ArrayList<>());
				List<String> downImages = imagesByDir.getOrDefault("down", new ArrayList<>());
				processImagesToVideoByDirection(record, jedis, busNo, windowId, upImages, downImages);
			}
			System.out.println("[并行处理] 并行处理完成");
		} catch (Exception e) {
			System.err.println("[并行处理] 并行处理异常: " + e.getMessage());
		}
	}

	/**
	 * 精确匹配时间窗口的图片
	 */
	private List<String> getImagesByExactWindow(Jedis jedis, String busNo, String windowId) {
		List<String> imageUrls = new ArrayList<>();

		try {
			// 获取上车图片URL
			String upImagesKey = "image_urls:" + busNo + ":" + windowId + ":up";
			Set<String> upImages = jedis.smembers(upImagesKey);
			if (upImages != null && !upImages.isEmpty()) {
				imageUrls.addAll(upImages);
				System.out.println("[图片收集] 收集到上车图片 " + upImages.size() + " 张");
			} else {
				System.out.println("[图片收集] 未找到上车图片");
			}

			// 获取下车图片URL
			String downImagesKey = "image_urls:" + busNo + ":" + windowId + ":down";
			Set<String> downImages = jedis.smembers(downImagesKey);
			if (downImages != null && !downImages.isEmpty()) {
				imageUrls.addAll(downImages);
				System.out.println("[图片收集] 收集到下车图片 " + downImages.size() + " 张");
			} else {
				System.out.println("[图片收集] 未找到下车图片");
			}
		} catch (Exception e) {
			System.err.println("[图片收集] 精确匹配收集图片时发生异常: " + e.getMessage());
		}

		return imageUrls;
	}

	/**
	 * 模糊匹配时间窗口的图片（前后5分钟）
	 */
	private List<String> getImagesByFuzzyWindow(Jedis jedis, String busNo, String windowId) {
		List<String> imageUrls = new ArrayList<>();

		try {
			// 解析时间窗口
			LocalDateTime windowTime;
			String normalized = windowId;
			if (normalized != null && normalized.contains("T")) {
				normalized = normalized.replace("T", " ");
			}
			try {
				windowTime = LocalDateTime.parse(normalized, formatter);
			} catch (Exception e) {
				// 兜底：尝试直接按ISO解析
				windowTime = LocalDateTime.parse(windowId);
			}

			// 搜索前后5分钟的时间窗口
			for (int delta = -5; delta <= 5; delta++) {
				LocalDateTime searchTime = windowTime.plusMinutes(delta);
				String searchWindowId = searchTime.format(formatter);

				// 获取上车图片URL
				String upImagesKey = "image_urls:" + busNo + ":" + searchWindowId + ":up";
				Set<String> upImages = jedis.smembers(upImagesKey);
				if (upImages != null && !upImages.isEmpty()) {
					imageUrls.addAll(upImages);
					System.out.println("[图片收集] 模糊匹配找到上车图片 " + upImages.size() + " 张，时间窗口: " + searchWindowId);
				}

				// 获取下车图片URL
				String downImagesKey = "image_urls:" + busNo + ":" + searchWindowId + ":down";
				Set<String> downImages = jedis.smembers(downImagesKey);
				if (downImages != null && !downImages.isEmpty()) {
					imageUrls.addAll(downImages);
					System.out.println("[图片收集] 模糊匹配找到下车图片 " + downImages.size() + " 张，时间窗口: " + searchWindowId);
				}
			}
		} catch (Exception e) {
			System.err.println("[图片收集] 模糊匹配收集图片时发生异常: " + e.getMessage());
		}

		return imageUrls;
	}

	private BusOdRecord createBaseRecord(String busNo, String cameraNo, LocalDateTime time, Jedis jedis, String sqeNo) {
		System.out.println("[OD记录创建] 开始创建车辆 " + busNo + " 的OD记录");

		BusOdRecord record = new BusOdRecord();
		record.setDate(time != null ? time.toLocalDate() : LocalDate.now());
		record.setBusNo(busNo);
		// 🔥 设置开关门唯一批次号
		record.setSqeNo(sqeNo);
		// 优先使用CV传入的cameraNo；若为空或为default，则尝试从到离站/GPS中推导
		record.setCameraNo(resolveCameraNo(cameraNo, busNo, jedis));
		record.setLineId(getLineIdFromBusNo(busNo, jedis));
		record.setRouteDirection(getRouteDirectionFromBusNo(busNo, jedis));
		record.setGpsLat(getGpsLat(busNo, jedis));
		record.setGpsLng(getGpsLng(busNo, jedis));
		record.setFullLoadRate(getFullLoadRateFromRedis(jedis, busNo, sqeNo));

		// 设置刷卡人数（JSON格式）
		String ticketCountJson = getTicketCountWindowFromRedis(jedis, busNo);
		record.setTicketJson(ticketCountJson);

		// 从JSON中提取上下车刷卡人数并设置到独立字段
		try {
			JSONObject ticketCountObj = new JSONObject(ticketCountJson);
			int upCount = ticketCountObj.optInt("upCount", 0);
			int downCount = ticketCountObj.optInt("downCount", 0);
			record.setTicketUpCount(upCount);
			record.setTicketDownCount(downCount);
			System.out.println("[OD记录创建] 设置ticketUpCount: " + upCount + ", ticketDownCount: " + downCount);
		} catch (Exception e) {
			// 如果JSON解析失败，设置默认值
			record.setTicketUpCount(0);
			record.setTicketDownCount(0);
			System.err.println("[OD记录创建] 解析ticketJson JSON失败，设置默认值: " + e.getMessage());
		}

		System.out.println("[OD记录创建] 设置ticketJson: " + ticketCountJson);

		record.setCurrentStationName(getCurrentStationName(busNo, jedis));
		// 设置车辆总人数（来自CV系统满载率推送）
		record.setVehicleTotalCount(getVehicleTotalCountFromRedis(jedis, busNo, sqeNo));
		Long busId = getBusIdFromRedis(jedis, busNo);
		if (busId != null) record.setBusId(busId);

		// 设置原始数据字段用于校验
		record.setRetrieveBusGpsMsg(getBusGpsMsgFromRedis(jedis, busNo));
		record.setRetrieveDownupMsg(getDownupMsgFromRedis(jedis, busNo));

		System.out.println("[OD记录创建] OD记录创建完成:");
		System.out.println("   sqeNo=" + record.getSqeNo());
		System.out.println("   ticketJson=" + ticketCountJson);
		System.out.println("   ticketUpCount=" + record.getTicketUpCount());
		System.out.println("   ticketDownCount=" + record.getTicketDownCount());
		System.out.println("   ================================================================================");

		return record;
	}

	// 根据到离站/GPS缓存推导cameraNo（设备编号），当CV回推为默认值时兜底
	private String resolveCameraNo(String cameraNo, String busNo, Jedis jedis) {
		// 只信任CV推送的camera_no；当为空或default时返回原值以便观测
		if (cameraNo != null && !cameraNo.isEmpty() && !"default".equalsIgnoreCase(cameraNo)) {
			return cameraNo;
		}
		return cameraNo;
	}

	private String getLineIdFromBusNo(String busNo, Jedis jedis) {
		String arriveLeaveStr = jedis.get("arrive_leave:" + busNo);
		if (arriveLeaveStr != null) {
			JSONObject arriveLeave = new JSONObject(arriveLeaveStr);
			// 使用routeNo作为线路ID
			String routeNo = arriveLeave.optString("routeNo");
			if (routeNo != null && !routeNo.isEmpty()) {
				return routeNo;
			}
		}
		return "UNKNOWN";
	}

	private String getRouteDirectionFromBusNo(String busNo, Jedis jedis) {
		// 优先从到离站缓存中直接读取direction；若无则按trafficType映射
		try {
			String arriveLeaveStr = jedis.get("arrive_leave:" + busNo);
			if (arriveLeaveStr != null) {
				JSONObject arriveLeave = new JSONObject(arriveLeaveStr);
				String direct = arriveLeave.optString("direction");
				if (direct != null && !direct.isEmpty()) return direct;
				String t = arriveLeave.optString("trafficType");
				String v = mapTrafficTypeToDirection(t);
				if (v != null && !v.isEmpty()) return v;
			}
			// 次选从gps:trafficType判定
			String gpsStr = jedis.get("gps:" + busNo);
			if (gpsStr != null) {
				String t = new JSONObject(gpsStr).optString("trafficType");
				String v = mapTrafficTypeToDirection(t);
				if (v != null && !v.isEmpty()) return v;
			}
		} catch (Exception ignore) {}
		// 默认回退为上行
		return "up";
	}

	private String mapTrafficTypeToDirection(String trafficType) {
		if (trafficType == null) return "unknown";
		switch (trafficType) {
			case "4": return "up";
			case "5": return "down";
			case "6": return "up";
			default: return trafficType; // 返回原始trafficType值
		}
	}

	private String getCurrentStationId(String busNo, Jedis jedis) {
		String arriveLeaveStr = jedis.get("arrive_leave:" + busNo);
		if (arriveLeaveStr != null) {
			JSONObject arriveLeave = new JSONObject(arriveLeaveStr);
			String stationId = arriveLeave.optString("stationId");

			if (Config.PILOT_ROUTE_LOG_ENABLED) {
				System.out.println("[站点信息] 获取站点ID: busNo=" + busNo +
					", stationId=" + stationId +
					", arriveLeave数据=" + arriveLeaveStr);
			}

			if ("UNKNOWN".equals(stationId)) {
				if (Config.PILOT_ROUTE_LOG_ENABLED) {
					System.out.println("[站点信息] 警告：获取到UNKNOWN站点ID: busNo=" + busNo + ", arriveLeave=" + arriveLeaveStr);
				}
			}
			return stationId;
		}
		if (Config.PILOT_ROUTE_LOG_ENABLED) {
			System.out.println("[站点信息] 未找到到离站信息: busNo=" + busNo);
		}
		return "UNKNOWN";
	}

	private String getCurrentStationName(String busNo, Jedis jedis) {
		String arriveLeaveStr = jedis.get("arrive_leave:" + busNo);
		if (arriveLeaveStr != null) {
			JSONObject arriveLeave = new JSONObject(arriveLeaveStr);
			String stationName = arriveLeave.optString("stationName");

			if (Config.PILOT_ROUTE_LOG_ENABLED) {
				System.out.println("[站点信息] 获取站点名称: busNo=" + busNo +
					", stationName=" + stationName +
					", arriveLeave数据=" + arriveLeaveStr);
			}

			if ("Unknown Station".equals(stationName)) {
				if (Config.PILOT_ROUTE_LOG_ENABLED) {
					System.out.println("[站点信息] 警告：获取到Unknown Station: busNo=" + busNo + ", arriveLeave=" + arriveLeaveStr);
				}
			}
			return stationName;
		}
		if (Config.PILOT_ROUTE_LOG_ENABLED) {
			System.out.println("[站点信息] 未找到到离站信息: busNo=" + busNo);
		}
		return "Unknown Station";
	}

	private BigDecimal getGpsLat(String busNo, Jedis jedis) {
		String gpsStr = jedis.get("gps:" + busNo);
		if (gpsStr != null) {
			return new BigDecimal(new JSONObject(gpsStr).optDouble("lat"));
		}
		return BigDecimal.ZERO;
	}

	private BigDecimal getGpsLng(String busNo, Jedis jedis) {
		String gpsStr = jedis.get("gps:" + busNo);
		if (gpsStr != null) {
			return new BigDecimal(new JSONObject(gpsStr).optDouble("lng"));
		}
		return BigDecimal.ZERO;
	}

	private String getTicketCountWindowFromRedis(Jedis jedis, String busNo) {
		String windowId = jedis.get("open_time:" + busNo);
		System.out.println("[票务计数获取] 获取车辆 " + busNo + " 的刷卡计数:");
		System.out.println("   开门窗口ID: " + windowId);

		if (windowId == null) {
			System.out.println("   [票务计数获取] 未找到开门窗口，返回空JSON");
			return "{\"upCount\":0,\"downCount\":0,\"totalCount\":0,\"detail\":[]}";
		}

		// 获取上下车计数
		String upCountKey = "ticket_count:" + busNo + ":" + windowId + ":up";
		String downCountKey = "ticket_count:" + busNo + ":" + windowId + ":down";
		String upCountStr = jedis.get(upCountKey);
		String downCountStr = jedis.get(downCountKey);

		int upCount = upCountStr != null ? Integer.parseInt(upCountStr) : 0;
		int downCount = downCountStr != null ? Integer.parseInt(downCountStr) : 0;
		int totalCount = upCount + downCount;

		// 获取上下车详情
		JSONArray detailArray = new JSONArray();

		// 获取上车详情
		String upDetailKey = "ticket_detail:" + busNo + ":" + windowId + ":up";
		Set<String> upDetails = jedis.smembers(upDetailKey);
		if (upDetails != null) {
			for (String detail : upDetails) {
				try {
					detailArray.put(new JSONObject(detail));
				} catch (Exception e) {
					System.err.println("[票务计数获取] 解析上车详情失败: " + detail);
				}
			}
		}

		// 获取下车详情
		String downDetailKey = "ticket_detail:" + busNo + ":" + windowId + ":down";
		Set<String> downDetails = jedis.smembers(downDetailKey);
		if (downDetails != null) {
			for (String detail : downDetails) {
				try {
					detailArray.put(new JSONObject(detail));
				} catch (Exception e) {
					System.err.println("[票务计数获取] 解析下车详情失败: " + detail);
				}
			}
		}

		// 构建JSON结果
		JSONObject result = new JSONObject();
		result.put("upCount", upCount);
		result.put("downCount", downCount);
		result.put("totalCount", totalCount);
		result.put("detail", detailArray);

		String resultJson = result.toString();

		System.out.println("   [票务计数获取] 上车计数: " + upCount + " (Redis键: " + upCountKey + ")");
		System.out.println("   [票务计数获取] 下车计数: " + downCount + " (Redis键: " + downCountKey + ")");
		System.out.println("   [票务计数获取] 总计数: " + totalCount);
		System.out.println("   [票务计数获取] 详情数量: " + detailArray.length());
		System.out.println("   [票务计数获取] JSON结果: " + resultJson);
		System.out.println("   ================================================================================");

		return resultJson;
	}

	private int getVehicleTotalCountFromRedis(Jedis jedis, String busNo, String sqeNo) {
		// 🔥 优先使用sqe_no获取车辆总人数
		String count = null;

		if (sqeNo != null && !sqeNo.isEmpty()) {
			count = jedis.get("vehicle_total_count:" + sqeNo);
		}

		// 兜底：使用原有key格式
		if (count == null) {
			count = jedis.get("vehicle_total_count:" + busNo);
		}

		return count != null ? Integer.parseInt(count) : 0;
	}



	private BigDecimal getFullLoadRateFromRedis(Jedis jedis, String busNo, String sqeNo) {
		// 🔥 优先使用sqe_no获取满载率
		String factor = null;

		if (sqeNo != null && !sqeNo.isEmpty()) {
			factor = jedis.get("load_factor:" + sqeNo);
		}

		// 兜底：使用原有key格式
		if (factor == null) {
			factor = jedis.get("load_factor:" + busNo);
		}

		return factor != null ? new BigDecimal(factor) : BigDecimal.ZERO;
	}

	private float matchPassengerFeature(String feature, String busNo) {
		try (Jedis jedis = jedisPool.getResource()) {
			jedis.auth(Config.REDIS_PASSWORD);
			String windowId = jedis.get("open_time:" + busNo);
			if (windowId == null) return 0.0f;
			String key = "features_set:" + busNo + ":" + windowId;
			Set<String> features = jedis.smembers(key);
			float[] probe = CosineSimilarity.parseFeatureVector(feature);
			double best = 0.0;
			for (String cand : features) {
				try {
					// 从JSON字符串中提取feature字段
					JSONObject featureObj = new JSONObject(cand);
					String featureValue = featureObj.optString("feature");
					if (featureValue != null && !featureValue.isEmpty()) {
						float[] vec = CosineSimilarity.parseFeatureVector(featureValue);
						if (probe.length > 0 && vec.length == probe.length) {
							double sim = CosineSimilarity.cosine(probe, vec);
							if (sim > best) best = sim;
						}
					}
				} catch (Exception e) {
					// 如果解析失败，跳过该特征
					if (Config.LOG_DEBUG) {
						System.out.println("[PassengerFlowProcessor] Failed to parse feature JSON: " + cand);
					}
				}
			}
			return (float) best;
		}
	}

	private JSONObject callMediaApi(List<String> imageList, String prompt) throws IOException {
		System.out.println("[大模型API] 开始调用大模型API: " + Config.MEDIA_API);
		System.out.println("[大模型API] 请求参数 - 图片数量: " + (imageList != null ? imageList.size() : 0) +
			", 提示词: " + prompt);

		// 打印图片URL列表用于调试
		if (imageList != null && !imageList.isEmpty()) {
			System.out.println("[大模型API] 图片URL列表:");
			for (int i = 0; i < imageList.size(); i++) {
				System.out.println("  [" + (i + 1) + "] " + imageList.get(i));
			}
		}

		try (CloseableHttpClient client = HttpClients.createDefault()) {
			HttpPost post = new HttpPost(Config.MEDIA_API);
			post.setHeader("Content-Type", "application/json");
			JSONObject payload = new JSONObject();

			// 仅使用图片列表参数
			if (imageList != null && !imageList.isEmpty()) {
				payload.put("image_path_list", new JSONArray(imageList));
				System.out.println("[大模型API] 使用图片列表参数，图片数量: " + imageList.size());
			} else {
				System.err.println("[大模型API] 错误：图片列表为空");
				throw new IllegalArgumentException("必须提供非空的图片列表");
			}

			payload.put("system_prompt", prompt);
			StringEntity entity = new StringEntity(payload.toString(), "UTF-8");
			post.setEntity(entity);

			System.out.println("[大模型API] 发送HTTP请求，payload大小: " + payload.toString().length());

			try (CloseableHttpResponse response = client.execute(post)) {
				String responseString = EntityUtils.toString(response.getEntity());
				int statusCode = response.getStatusLine().getStatusCode();
				System.out.println("[大模型API] 收到响应，状态码: " + statusCode + ", 响应大小: " + responseString.length());

				// 检查HTTP状态码
				if (statusCode != 200) {
					System.err.println("[大模型API] HTTP错误，状态码: " + statusCode + ", 响应内容: " + responseString);
					System.err.println("[大模型API] 请求的图片URL列表:");
					if (imageList != null) {
						for (int i = 0; i < imageList.size(); i++) {
							System.err.println("  [" + (i + 1) + "] " + imageList.get(i));
						}
					}
					throw new IOException("大模型API返回HTTP错误: " + statusCode);
				}

				// 解析响应JSON
				JSONObject responseJson = new JSONObject(responseString);

				// 检查API响应格式
				boolean success = responseJson.optBoolean("success", false);
				String error = responseJson.optString("error", null);

				if (!success) {
					System.err.println("[大模型API] API调用失败，success=false, error=" + error);
					System.err.println("[大模型API] 完整响应: " + responseString);
					System.err.println("[大模型API] 请求的图片URL列表:");
					if (imageList != null) {
						for (int i = 0; i < imageList.size(); i++) {
							System.err.println("  [" + (i + 1) + "] " + imageList.get(i));
						}
					}
					throw new IOException("大模型API调用失败: " + (error != null ? error : "未知错误"));
				}

				// 检查response字段
				if (!responseJson.has("response")) {
					System.err.println("[大模型API] 响应格式异常，缺少response字段");
					System.err.println("[大模型API] 完整响应: " + responseString);
					System.err.println("[大模型API] 请求的图片URL列表:");
					if (imageList != null) {
						for (int i = 0; i < imageList.size(); i++) {
							System.err.println("  [" + (i + 1) + "] " + imageList.get(i));
						}
					}
					throw new IOException("大模型API响应格式异常，缺少response字段");
				}

				JSONObject responseObj = responseJson.getJSONObject("response");
				JSONArray passengerFeatures = responseObj.optJSONArray("passenger_features");
				int totalCount = responseObj.optInt("total_count", 0);

				System.out.println("[大模型API] 解析成功 - success=true, 特征数量: " +
					(passengerFeatures != null ? passengerFeatures.length() : 0) +
					", 总人数: " + totalCount);

				return responseJson;
			}
		} catch (Exception e) {
			// 在异常时也打印URL列表用于调试
			System.err.println("[大模型API] 调用异常: " + e.getMessage());
			System.err.println("[大模型API] 请求的图片URL列表:");
			if (imageList != null) {
				for (int i = 0; i < imageList.size(); i++) {
					System.err.println("  [" + (i + 1) + "] " + imageList.get(i));
				}
			}
			throw e;
		}
	}

	private File downloadFile(String fileUrl) throws IOException {
		URL url = new URL(fileUrl);
		ReadableByteChannel rbc = Channels.newChannel(url.openStream());
		File file = new File("/tmp/" + UUID.randomUUID() + ".mp4");
		FileOutputStream fos = new FileOutputStream(file);
		fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
		fos.close();
		rbc.close();
		return file;
	}



	private void cacheFeatureStationMapping(Jedis jedis, String feature, String stationId, String stationName, String direction) {
		// 检查站点信息有效性
		if ("UNKNOWN".equals(stationId) || "Unknown Station".equals(stationName)) {
			if (Config.PILOT_ROUTE_LOG_ENABLED) {
				System.out.println("[站点缓存] 警告：缓存无效站点信息: stationId=" + stationId +
					", stationName=" + stationName + ", direction=" + direction);
			}
		}

		JSONObject mapping = new JSONObject();
		mapping.put("stationId", stationId);
		mapping.put("stationName", stationName);
		mapping.put("direction", direction); // 添加方向信息
		mapping.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
		String key = "feature_station:" + feature;
		jedis.set(key, mapping.toString());
		jedis.expire(key, Config.REDIS_TTL_FEATURES);

		if (Config.PILOT_ROUTE_LOG_ENABLED) {
			System.out.println("[站点缓存] 缓存特征站点映射: feature=" + feature.substring(0, Math.min(20, feature.length())) +
				"..., stationId=" + stationId + ", stationName=" + stationName + ", direction=" + direction);
		}
	}

	private JSONObject getOnStationFromCache(Jedis jedis, String feature) {
		String mappingJson = jedis.get("feature_station:" + feature);
		if (mappingJson != null) {
			return new JSONObject(mappingJson);
		}
		return null;
	}

	private int getCachedDownCount(Jedis jedis, String busNo, String windowId, String sqeNo) {
		if (windowId == null) return 0;

		// 🔥 优先使用sqe_no获取计数
		String v = null;
		String keyUsed = "";

		if (sqeNo != null && !sqeNo.isEmpty()) {
			keyUsed = "cv_down_count:" + sqeNo;
			v = jedis.get(keyUsed);
		}

		// 兜底：使用原有key格式
		if (v == null) {
			keyUsed = "cv_down_count:" + busNo + ":" + windowId;
			v = jedis.get(keyUsed);
		}

		int count = v != null ? Integer.parseInt(v) : 0;
		if (count == 0) {
			// 回退：基于特征方向进行统计
			try {
				String featuresKey = sqeNo != null && !sqeNo.isEmpty() ?
					"features_set:" + sqeNo :
					"features_set:" + busNo + ":" + windowId;
				Set<String> features = jedis.smembers(featuresKey);
				if (features != null) {
					for (String f : features) {
						try { if ("down".equals(new JSONObject(f).optString("direction"))) count++; } catch (Exception ignore) {}
					}
				}
			} catch (Exception ignore) {}
		}
		if (Config.LOG_DEBUG) {
			System.out.println("[CV计数获取] 下车计数(含回退): " + keyUsed + " = " + count);
		}
		return count;
	}

	private int getCachedUpCount(Jedis jedis, String busNo, String windowId, String sqeNo) {
		// 🔥 优先使用sqe_no获取计数
		String v = null;
		String keyUsed = "";

		if (sqeNo != null && !sqeNo.isEmpty()) {
			keyUsed = "cv_up_count:" + sqeNo;
			v = jedis.get(keyUsed);
		}

		// 兜底：使用原有key格式
		if (v == null) {
			keyUsed = "cv_up_count:" + busNo + ":" + windowId;
			v = jedis.get(keyUsed);
		}

		int count = v != null ? Integer.parseInt(v) : 0;
		if (count == 0) {
			// 回退：基于特征方向进行统计
			try {
				String featuresKey = sqeNo != null && !sqeNo.isEmpty() ?
					"features_set:" + sqeNo :
					"features_set:" + busNo + ":" + windowId;
				Set<String> features = jedis.smembers(featuresKey);
				if (features != null) {
					for (String f : features) {
						try { if ("up".equals(new JSONObject(f).optString("direction"))) count++; } catch (Exception ignore) {}
					}
				}
			} catch (Exception ignore) {}
		}
		if (Config.LOG_DEBUG) {
			System.out.println("[CV计数获取] 上车计数(含回退): " + keyUsed + " = " + count);
		}
		return count;
	}

	private Long getBusIdFromRedis(Jedis jedis, String busNo) {
		String v = jedis.get("bus_id:" + busNo);
		if (v == null) return null;
		try { return Long.parseLong(v); } catch (Exception e) { return null; }
	}

	private void sendToKafka(Object data) {
		try {
			String json = objectMapper.writeValueAsString(data);

			// 试点线路最终流程日志 - 准备发送到Kafka（隐藏payload，仅打印主题与大小）
			if (Config.PILOT_ROUTE_LOG_ENABLED) {
				System.out.println("[流程] 准备发送Kafka: topic=" + KafkaConfig.PASSENGER_FLOW_TOPIC + ", size=" + json.length());
			}

			if (Config.FLOW_LOG_ENABLED && data instanceof BusOdRecord) {
				System.out.println("[发送BusOdRecord] topic=" + KafkaConfig.PASSENGER_FLOW_TOPIC + ", size=" + json.length());
			}

			if (Config.LOG_DEBUG) {
				System.out.println("[PassengerFlowProcessor] Send to Kafka topic=" + KafkaConfig.PASSENGER_FLOW_TOPIC + ", size=" + json.length());
			}

			// 使用回调来确认发送状态
			producer.send(new ProducerRecord<>(KafkaConfig.PASSENGER_FLOW_TOPIC, json),
				(metadata, exception) -> {
					if (exception != null) {
						// 试点线路最终流程日志 - Kafka发送失败（隐藏payload）
						if (Config.PILOT_ROUTE_LOG_ENABLED) {
							System.out.println("[流程] Kafka发送失败: error=" + exception.getMessage());
						}

						if (Config.LOG_ERROR) {
							System.err.println("[发送失败] BusOdRecord发送Kafka失败: " + exception.getMessage());
						}
						// 可以在这里添加重试逻辑或告警机制
						handleKafkaSendFailure(data, exception);
					} else {
						// 试点线路最终流程日志 - Kafka发送成功（打印元数据）
						if (Config.PILOT_ROUTE_LOG_ENABLED) {
							System.out.println("[流程] Kafka发送成功: topic=" + metadata.topic() + ", partition=" + metadata.partition() + ", offset=" + metadata.offset());
						}

						if (Config.FLOW_LOG_ENABLED && data instanceof BusOdRecord) {
							System.out.println("[发送成功] BusOdRecord已发送 topic=" + metadata.topic() + ", partition=" + metadata.partition() + ", offset=" + metadata.offset());
						}
						// 可以在这里添加发送成功的统计或监控
						handleKafkaSendSuccess(data, metadata);
					}
				});

		} catch (Exception e) {
			// 试点线路最终流程日志 - 数据序列化失败（可通过配置控制）
			if (Config.PILOT_ROUTE_LOG_ENABLED) {
				System.out.println("[试点线路最终流程] 数据序列化失败:");
				System.out.println("   错误信息: " + e.getMessage());
				System.out.println("   错误数据: " + data);
				System.out.println("   ================================================================================");
			}

			if (Config.LOG_ERROR) {
				System.err.println("[流程异常] 序列化发送数据失败: " + e.getMessage());
			}
		}
	}

	/**
	 * 处理Kafka发送失败的情况
	 */
	private void handleKafkaSendFailure(Object data, Exception exception) {
		try {
			// 记录失败的数据到Redis，用于后续重试
			String failureKey = "kafka_failure:" + System.currentTimeMillis() + ":" + UUID.randomUUID().toString().substring(0, 8);
			try (Jedis jedis = jedisPool.getResource()) {
				jedis.auth(Config.REDIS_PASSWORD);
				jedis.set(failureKey, objectMapper.writeValueAsString(data));
				jedis.expire(failureKey, Config.REDIS_TTL_OPEN_TIME); // 设置过期时间

				if (Config.LOG_ERROR) {
					System.err.println("[PassengerFlowProcessor] Cached failed data to Redis, key=" + failureKey + ", error=" + exception.getMessage());
				}
			}
		} catch (Exception e) {
			if (Config.LOG_ERROR) {
				System.err.println("[PassengerFlowProcessor] Failed to cache failed data: " + e.getMessage());
			}
		}
	}

	/**
	 * 处理Kafka发送成功的情况
	 */
	private void handleKafkaSendSuccess(Object data, org.apache.kafka.clients.producer.RecordMetadata metadata) {
		try {
			// 可以在这里添加发送成功的统计信息
			if (Config.LOG_DEBUG) {
				System.out.println("[PassengerFlowProcessor] Successfully sent data to Kafka: " +
					"topic=" + metadata.topic() +
					", partition=" + metadata.partition() +
					", offset=" + metadata.offset() +
					", timestamp=" + metadata.timestamp());
			}

			// 可以在这里添加成功发送的监控指标
			// 例如：发送成功计数、延迟统计等

		} catch (Exception e) {
			if (Config.LOG_ERROR) {
				System.err.println("[PassengerFlowProcessor] Error handling success callback: " + e.getMessage());
			}
		}
	}

	public void close() {
		if (producer != null) producer.close();
	}

	/**
	 * 收集CV推送的原始downup事件数据
	 * @param busNo 车辆编号
	 * @param data 原始downup事件数据
	 * @param jedis Redis连接
	 */
	private void collectDownupMsg(String busNo, JSONObject data, Jedis jedis) {
		try {
			String stationId = data.optString("stationId");
			String stationName = data.optString("stationName");
			String sqeNo = data.optString("sqe_no"); // 🔥 获取sqe_no

			// 构建完整的downup事件JSON对象
			JSONObject downupEvent = new JSONObject();
			downupEvent.put("event", "downup");
			downupEvent.put("data", data);
			downupEvent.put("stationId", stationId);
			downupEvent.put("stationName", stationName);
			downupEvent.put("timestamp", data.optString("timestamp"));
			downupEvent.put("sqe_no", sqeNo); // 🔥 添加sqe_no字段

			// 🔥 增强存储策略：同时使用多种key存储，提高检索成功率
			List<String> keys = new ArrayList<>();

			// 方式1：按站点分组存储（原有逻辑）
			if (stationId != null && !stationId.isEmpty()) {
				keys.add("downup_msg:" + busNo + ":" + stationId);
			}

			// 方式2：按sqe_no存储（新增逻辑）
			if (sqeNo != null && !sqeNo.isEmpty()) {
				keys.add("downup_msg:" + sqeNo);
			}

			// 方式3：按车辆+时间窗口存储（兜底逻辑）
			String windowId = jedis.get("open_time:" + busNo);
			if (windowId != null && !windowId.isEmpty()) {
				keys.add("downup_msg:" + busNo + ":" + windowId);
			}

			// 为每个key存储数据
			for (String key : keys) {
				// 获取现有数据数组
				String existingDataStr = jedis.get(key);
				JSONArray downupMsgArray;
				if (existingDataStr != null && !existingDataStr.isEmpty()) {
					downupMsgArray = new JSONArray(existingDataStr);
				} else {
					downupMsgArray = new JSONArray();
				}

				// 检查是否已存在相同的数据（避免重复）
				boolean exists = false;
				for (int i = 0; i < downupMsgArray.length(); i++) {
					JSONObject existingEvent = downupMsgArray.getJSONObject(i);
					if (existingEvent.optString("timestamp").equals(downupEvent.optString("timestamp")) &&
						existingEvent.optString("stationId").equals(downupEvent.optString("stationId"))) {
						exists = true;
						break;
					}
				}

				// 如果不存在，则添加新数据
				if (!exists) {
					downupMsgArray.put(downupEvent);
				}

				// 存储到Redis，设置过期时间
				jedis.set(key, downupMsgArray.toString());
				jedis.expire(key, Config.REDIS_TTL_OPEN_TIME);
			}

			if (Config.LOG_DEBUG) {
				System.out.println("[PassengerFlowProcessor] 🔥 增强收集downup事件: busNo=" + busNo + ", stationId=" + stationId + ", sqeNo=" + sqeNo + ", 存储keys=" + keys.size() + ", events=" + data.optJSONArray("events").length());
			}
		} catch (Exception e) {
			if (Config.LOG_ERROR) {
				System.err.println("[PassengerFlowProcessor] 收集downup事件原始数据失败: " + e.getMessage());
			}
		}
	}

	/**
	 * 从Redis获取车辆到离站信号原始数据
	 * @param jedis Redis连接
	 * @param busNo 车辆编号
	 * @return JSON字符串
	 */
	private String getBusGpsMsgFromRedis(Jedis jedis, String busNo) {
		try {
			// 获取当前站点的开关门数据
			String currentStationId = getCurrentStationId(busNo, jedis);
			if (currentStationId != null && !currentStationId.isEmpty()) {
				String data = jedis.get("bus_gps_msg:" + busNo + ":" + currentStationId);
				if (data != null && !data.isEmpty()) {
					return data;
				}
			}

			// 如果当前站点没有数据，尝试获取所有站点的数据（兜底方案）
			Set<String> keys = jedis.keys("bus_gps_msg:" + busNo + ":*");
			JSONArray allData = new JSONArray();
			for (String key : keys) {
				String data = jedis.get(key);
				if (data != null && !data.isEmpty()) {
					JSONArray stationData = new JSONArray(data);
					for (int i = 0; i < stationData.length(); i++) {
						allData.put(stationData.get(i));
					}
				}
			}
			return allData.length() > 0 ? allData.toString() : "[]";
		} catch (Exception e) {
			if (Config.LOG_ERROR) {
				System.err.println("[PassengerFlowProcessor] 获取车辆到离站信号原始数据失败: " + e.getMessage());
			}
			return "[]";
		}
	}


	/**
	 * 从Redis获取downup事件原始数据
	 * @param jedis Redis连接
	 * @param busNo 车辆编号
	 * @return JSON字符串
	 */
	private String getDownupMsgFromRedis(Jedis jedis, String busNo) {
		try {
			// 🔥 增强检索策略：多种方式尝试获取downup数据
			JSONArray allData = new JSONArray();

			// 方式1：🔥 优先通过sqe_no检索（新增逻辑）
			String sqeNo = getCurrentSqeNo(busNo, jedis);
			if (sqeNo != null && !sqeNo.isEmpty()) {
				String sqeKey = "downup_msg:" + sqeNo;
				String sqeData = jedis.get(sqeKey);
				if (sqeData != null && !sqeData.isEmpty()) {
					JSONArray sqeDataArray = new JSONArray(sqeData);
					for (int i = 0; i < sqeDataArray.length(); i++) {
						allData.put(sqeDataArray.get(i));
					}
					if (Config.LOG_DEBUG) {
						System.out.println("[PassengerFlowProcessor] 🔥 通过sqe_no匹配到downup数据: sqeNo=" + sqeNo + ", 数据量=" + sqeDataArray.length());
					}
				}
			}

			// 方式2：通过站点信息匹配（原有逻辑）
			if (allData.length() == 0) {
				String arriveLeaveStr = jedis.get("arrive_leave:" + busNo);
				if (arriveLeaveStr != null) {
					JSONObject arriveLeave = new JSONObject(arriveLeaveStr);
					String stationId = arriveLeave.optString("stationId");
					String stationName = arriveLeave.optString("stationName");
					String busId = arriveLeave.optString("busId");

					if (Config.LOG_DEBUG) {
						System.out.println("[PassengerFlowProcessor] 获取站点信息: busNo=" + busNo + ", stationId=" + stationId + ", stationName=" + stationName + ", busId=" + busId);
					}

					if (stationId != null && !stationId.isEmpty()) {
						String key = "downup_msg:" + busNo + ":" + stationId;
						String data = jedis.get(key);
						if (data != null && !data.isEmpty()) {
							JSONArray stationData = new JSONArray(data);
							for (int i = 0; i < stationData.length(); i++) {
								allData.put(stationData.get(i));
							}
							if (Config.LOG_DEBUG) {
								System.out.println("[PassengerFlowProcessor] 通过stationId匹配到downup数据: key=" + key + ", 数据量=" + stationData.length());
							}
						}
					}
				}
			}

			// 方式3：🔥 通过车辆+时间窗口匹配（增强逻辑）
			if (allData.length() == 0) {
				String windowId = jedis.get("open_time:" + busNo);
				if (windowId != null && !windowId.isEmpty()) {
					String windowKey = "downup_msg:" + busNo + ":" + windowId;
					String windowData = jedis.get(windowKey);
					if (windowData != null && !windowData.isEmpty()) {
						JSONArray windowDataArray = new JSONArray(windowData);
						for (int i = 0; i < windowDataArray.length(); i++) {
							allData.put(windowDataArray.get(i));
						}
						if (Config.LOG_DEBUG) {
							System.out.println("[PassengerFlowProcessor] 🔥 通过时间窗口匹配到downup数据: windowId=" + windowId + ", 数据量=" + windowDataArray.length());
						}
					}
				}
			}

			// 方式4：🔥 全扫描匹配（兜底逻辑）
			if (allData.length() == 0) {
				Set<String> allKeys = jedis.keys("downup_msg:" + busNo + ":*");
				for (String key : allKeys) {
					String data = jedis.get(key);
					if (data != null && !data.isEmpty()) {
						JSONArray stationData = new JSONArray(data);
						for (int i = 0; i < stationData.length(); i++) {
							allData.put(stationData.get(i));
						}
					}
				}
				if (Config.LOG_DEBUG) {
					System.out.println("[PassengerFlowProcessor] 🔥 通过全扫描匹配到downup数据: 扫描keys=" + allKeys.size() + ", 数据量=" + allData.length());
				}
			}

			// 方式5：🔥 时间范围兜底匹配（原有逻辑增强）
			if (allData.length() == 0) {
				String windowId = jedis.get("open_time:" + busNo);
				if (windowId != null && !windowId.isEmpty()) {
					// 解析时间窗口，搜索前后时间范围的数据
					LocalDateTime windowTime;
					String normalized = windowId;
					if (normalized != null && normalized.contains("T")) {
						normalized = normalized.replace("T", " ");
					}
					try {
						windowTime = LocalDateTime.parse(normalized, formatter);
					} catch (Exception e) {
						windowTime = LocalDateTime.parse(windowId);
					}

					// 搜索前后5分钟的时间窗口
					for (int delta = -5; delta <= 5; delta++) {
						LocalDateTime searchTime = windowTime.plusMinutes(delta);

						// 查找该时间窗口的downup数据
						Set<String> keys = jedis.keys("downup_msg:" + busNo + ":*");
						for (String key : keys) {
							String data = jedis.get(key);
							if (data != null && !data.isEmpty()) {
								JSONArray stationData = new JSONArray(data);
								for (int i = 0; i < stationData.length(); i++) {
									JSONObject downupEvent = stationData.getJSONObject(i);
									String eventTimestamp = downupEvent.optString("timestamp");

									// 检查时间是否在搜索范围内
									if (isTimeInRange(eventTimestamp, searchTime, 60)) { // 前后1分钟容差
										allData.put(downupEvent);
									}
								}
							}
						}
					}
					if (Config.LOG_DEBUG) {
						System.out.println("[PassengerFlowProcessor] 🔥 通过时间范围兜底匹配到downup数据: 数据量=" + allData.length());
					}
				}
			}

			String result = allData.length() > 0 ? allData.toString() : "[]";
			if (Config.LOG_DEBUG) {
				System.out.println("[PassengerFlowProcessor] 🔥 返回downup数据: 总数据量=" + allData.length() + ", 结果长度=" + result.length());
			}
			return result;
		} catch (Exception e) {
			if (Config.LOG_ERROR) {
				System.err.println("[PassengerFlowProcessor] 获取downup事件原始数据失败: " + e.getMessage());
			}
			return "[]";
		}
	}

	/**
	 * 检查时间是否在指定范围内
	 * @param eventTimestamp 事件时间戳
	 * @param targetTime 目标时间
	 * @param toleranceSeconds 容差秒数
	 * @return 是否在范围内
	 */
	private boolean isTimeInRange(String eventTimestamp, LocalDateTime targetTime, int toleranceSeconds) {
		try {
			if (eventTimestamp == null || eventTimestamp.isEmpty()) {
				return false;
			}

			// 解析事件时间戳
			LocalDateTime eventTime;
			if (eventTimestamp.contains("T")) {
				eventTime = LocalDateTime.parse(eventTimestamp.replace(" ", "T"));
			} else {
				eventTime = LocalDateTime.parse(eventTimestamp, formatter);
			}

			// 检查是否在容差范围内
			LocalDateTime start = targetTime.minusSeconds(toleranceSeconds);
			LocalDateTime end = targetTime.plusSeconds(toleranceSeconds);

			return !eventTime.isBefore(start) && !eventTime.isAfter(end);
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * 处理base64图片：解码为文件并上传到OSS
	 */
	private String processBase64Image(String base64Image, String busNo, String cameraNo, LocalDateTime eventTime) throws IOException {
		// 1. 将base64图片解码为文件
		File imageFile = decodeBase64ToFile(base64Image);

		// 2. 生成动态目录名（基于开关门事件）
		String windowId = getCurrentWindowId(busNo);
		String dynamicDir = "PassengerFlowRecognition/" + (windowId != null ? windowId : "default");

		// 3. 上传到OSS获取URL（使用图片配置）
		String fileName = String.format("cv_%s_%s_%s_%s.jpg",
			busNo, cameraNo, eventTime.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")), UUID.randomUUID().toString().substring(0, 8));

		String imageUrl = OssUtil.uploadImageFile(imageFile, fileName, dynamicDir);

		// 清理临时文件
		imageFile.delete();

		return imageUrl;
	}

	/**
	 * 🔥 数据修复工具：为缺失passengerImages的记录补充图片数据
	 * @param record BusOdRecord记录
	 * @param jedis Redis连接
	 */
	public void repairPassengerImages(BusOdRecord record, Jedis jedis) {
		if (record == null || jedis == null) return;

		String passengerImages = record.getPassengerImages();
		if (passengerImages != null && !passengerImages.isEmpty() && !passengerImages.equals("[]")) {
			System.out.println("[数据修复] 记录已有passengerImages数据，跳过修复");
			return;
		}

		System.out.println("[数据修复] 开始修复记录ID=" + record.getId() + " 的passengerImages字段");

		try {
			String busNo = record.getBusNo();
			String sqeNo = record.getSqeNo();
			LocalDateTime beginTime = record.getTimestampBegin();
			LocalDateTime endTime = record.getTimestampEnd();

			// 尝试多种方式收集图片
			List<String> imageUrls = enhancedImageCollection(jedis, busNo, null, sqeNo, beginTime, endTime);

			if (!imageUrls.isEmpty()) {
				JSONArray imageArray = new JSONArray();
				for (String imageUrl : imageUrls) {
					imageArray.put(imageUrl);
				}
				record.setPassengerImages(imageArray.toString());
				System.out.println("[数据修复] 成功修复passengerImages字段，图片数量: " + imageUrls.size());
			} else {
				System.out.println("[数据修复] 未找到相关图片，保持原状");
			}

		} catch (Exception e) {
			System.err.println("[数据修复] 修复过程异常: " + e.getMessage());
		}
	}

	/**
	 * 🔥 数据修复工具：为缺失retrieveDownupMsg的记录补充downup数据
	 * @param record BusOdRecord记录
	 * @param jedis Redis连接
	 */
	public void repairRetrieveDownupMsg(BusOdRecord record, Jedis jedis) {
		if (record == null || jedis == null) return;

		String retrieveDownupMsg = record.getRetrieveDownupMsg();
		if (retrieveDownupMsg != null && !retrieveDownupMsg.isEmpty() && !retrieveDownupMsg.equals("[]")) {
			System.out.println("[数据修复] 记录已有retrieveDownupMsg数据，跳过修复");
			return;
		}

		System.out.println("[数据修复] 开始修复记录ID=" + record.getId() + " 的retrieveDownupMsg字段");

		try {
			String busNo = record.getBusNo();
			String sqeNo = record.getSqeNo();

			// 🔥 使用增强的downup数据收集逻辑
			String downupData = getDownupMsgFromRedis(jedis, busNo);

			if (downupData != null && !downupData.isEmpty() && !downupData.equals("[]")) {
				record.setRetrieveDownupMsg(downupData);
				System.out.println("[数据修复] 成功修复retrieveDownupMsg字段，数据长度: " + downupData.length());

				// 解析并显示downup事件数量
				try {
					JSONArray downupArray = new JSONArray(downupData);
					System.out.println("[数据修复] downup事件数量: " + downupArray.length());
				} catch (Exception e) {
					System.out.println("[数据修复] 无法解析downup数据格式");
				}
			} else {
				System.out.println("[数据修复] 未找到相关downup数据，保持原状");
			}

		} catch (Exception e) {
			System.err.println("[数据修复] 修复retrieveDownupMsg过程异常: " + e.getMessage());
		}
	}

	/**
	 * 🔥 获取当前开关门唯一批次号
	 * @param busNo 公交车编号
	 * @param jedis Redis连接
	 * @return sqe_no
	 */
	private String getCurrentSqeNo(String busNo, Jedis jedis) {
		try {
			// 方式1：从开门时间缓存中获取
			String sqeNo = jedis.get("sqe_no:" + busNo);
			if (sqeNo != null && !sqeNo.isEmpty()) {
				return sqeNo;
			}

			// 方式2：从开关门消息中获取
			String doorMsg = jedis.get("open_close_door_msg:" + busNo);
			if (doorMsg != null && !doorMsg.isEmpty()) {
				JSONObject doorData = new JSONObject(doorMsg);
				sqeNo = doorData.optString("sqe_no");
				if (sqeNo != null && !sqeNo.isEmpty()) {
					return sqeNo;
				}
			}

			// 方式3：从最近的downup事件中获取
			Set<String> downupKeys = jedis.keys("downup_msg:" + busNo + ":*");
			for (String key : downupKeys) {
				String data = jedis.get(key);
				if (data != null && !data.isEmpty()) {
					JSONArray downupArray = new JSONArray(data);
					for (int i = 0; i < downupArray.length(); i++) {
						JSONObject downupEvent = downupArray.getJSONObject(i);
						sqeNo = downupEvent.optString("sqe_no");
						if (sqeNo != null && !sqeNo.isEmpty()) {
							return sqeNo;
						}
					}
				}
			}

			return null;
		} catch (Exception e) {
			if (Config.LOG_ERROR) {
				System.err.println("[PassengerFlowProcessor] 获取当前sqe_no失败: " + e.getMessage());
			}
			return null;
		}
	}

	/**
	 * 获取当前开门时间窗口ID
	 * @param busNo 公交车编号
	 * @return 时间窗口ID
	 */
	private String getCurrentWindowId(String busNo) {
		try (Jedis jedis = jedisPool.getResource()) {
			jedis.auth(Config.REDIS_PASSWORD);
			return jedis.get("open_time:" + busNo);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * 将base64字符串解码为文件
	 */
	private File decodeBase64ToFile(String base64Image) throws IOException {
		// 移除base64前缀（如果有的话）
		String base64Data = base64Image;
		if (base64Image.contains(",")) {
			base64Data = base64Image.substring(base64Image.indexOf(",") + 1);
		}

		// 解码base64
		byte[] imageBytes = java.util.Base64.getDecoder().decode(base64Data);

		// 创建临时文件
		File tempFile = File.createTempFile("cv_image_", ".jpg");
		try (FileOutputStream fos = new FileOutputStream(tempFile)) {
			fos.write(imageBytes);
		}

		return tempFile;
	}

	    /**
     * 缓存图片URL到Redis，用于后续AI分析
     * @param sqeNo 开关门唯一批次号
     */
    private void cacheImageUrl(Jedis jedis, String busNo, String windowId, String imageUrl, String direction, String sqeNo) {
        if (windowId != null) {
            // 🔥 优先使用sqeNo作为图片缓存key
            String imageUrlsKey = sqeNo != null && !sqeNo.isEmpty() ?
                "image_urls:" + sqeNo + ":" + direction :
                "image_urls:" + busNo + ":" + windowId + ":" + direction;
            jedis.sadd(imageUrlsKey, imageUrl);
            jedis.expire(imageUrlsKey, Config.REDIS_TTL_OPEN_TIME);

            System.out.println("[图片缓存] 成功缓存图片URL: 车辆=" + busNo + ", 方向=" + direction + ", 时间窗口=" + windowId + ", sqeNo=" + sqeNo + ", URL长度=" + imageUrl.length());
        }
    }

	    /**
     * 使用图片列表调用AI模型进行分析，增强现有的OD记录
     */
    /**
     * 使用大模型分析图片列表 - 与图片转视频并行处理
     * 图片转视频：用于存储和展示
     * 大模型分析：直接使用图片列表进行AI分析，无需等待视频转换
     */
    private void analyzeImagesWithAI(Jedis jedis, String busNo, LocalDateTime timeWindow, BusOdRecord record, List<String> imageUrls) throws IOException, SQLException {
        // 检查是否启用AI图片分析
        if (!Config.ENABLE_AI_IMAGE_ANALYSIS) {
            System.out.println("[大模型分析] AI图片分析功能已禁用，跳过分析");
            // 兜底：用图片数量作为AI总人数的保守估计
            try {
                int size = imageUrls != null ? imageUrls.size() : 0;
                Integer cur = record.getAiTotalCount();
                record.setAiTotalCount(Math.max(cur == null ? 0 : cur, size));
            } catch (Exception ignore) {}
            return;
        }

        System.out.println("[大模型分析] 开始为车辆 " + busNo + " 进行AI图片分析");

        // 获取当前开门时间窗口ID
        String windowId = jedis.get("open_time:" + busNo);
        if (windowId == null) {
            System.out.println("[大模型分析] 未找到车辆 " + busNo + " 的开门时间窗口，跳过AI分析");
            return;
        }

        System.out.println("[大模型分析] 找到时间窗口: " + windowId);

        // 使用传入的图片URL列表，不再从特征数据中收集
        if (imageUrls == null || imageUrls.isEmpty()) {
            System.out.println("[大模型分析] 传入的图片URL列表为空，跳过AI分析");
            return;
        }

        if (imageUrls.isEmpty()) {
            System.out.println("[大模型分析] 车辆 " + busNo + " 没有图片需要分析，跳过AI分析");
            return;
        }

        System.out.println("[大模型分析] 收集到 " + imageUrls.size() + " 张图片，准备调用大模型分析");

        // 限制图片数量，避免AI模型处理过多图片
        if (imageUrls.size() > Config.MAX_IMAGES_PER_ANALYSIS) {
            System.out.println("[大模型分析] 图片数量过多，从 " + imageUrls.size() + " 张限制到 " + Config.MAX_IMAGES_PER_ANALYSIS + " 张");
            imageUrls = imageUrls.subList(0, Config.MAX_IMAGES_PER_ANALYSIS);
        }

        System.out.println("[大模型分析] 开始调用大模型API，图片数量: " + imageUrls.size() + "，提示词: " + Config.PASSENGER_PROMPT);

        // 调用大模型分析图片 - 直接传入图片列表，不使用视频路径
        JSONObject modelResponse;
        JSONArray passengerFeatures = new JSONArray();
        int aiTotalCount = 0;
        int attempts = 0;
        int maxRetry = Math.max(0, Config.MEDIA_MAX_RETRY);
        while (true) {
            try {
                attempts++;
                System.out.println("[大模型分析] 开始第" + attempts + "次调用大模型API...");
                modelResponse = callMediaApi(imageUrls, Config.PASSENGER_PROMPT);

                // 解析响应
                JSONObject responseObj = modelResponse.optJSONObject("response");
                passengerFeatures = responseObj != null ? responseObj.optJSONArray("passenger_features") : new JSONArray();
                aiTotalCount = responseObj != null ? responseObj.optInt("total_count", 0) : 0;

                System.out.println("[大模型分析] 第" + attempts + "次调用完成 - 特征数量: " +
                    (passengerFeatures != null ? passengerFeatures.length() : 0) +
                    ", 总人数: " + aiTotalCount);

                // 检查是否成功获取到特征
                if (passengerFeatures != null && passengerFeatures.length() > 0) {
                    System.out.println("[大模型分析] 成功获取到乘客特征，停止重试");
                    break; // 成功拿到非空特征
                }

                // 检查是否达到最大重试次数
                if (attempts >= maxRetry) {
                    System.out.println("[大模型分析] 特征仍为空且已达最大重试次数(" + maxRetry + ")，停止重试");
                    break;
                }

                // 等待后重试
                int backoffMs = Config.MEDIA_RETRY_BACKOFF_MS * attempts;
                System.out.println("[大模型分析] 特征为空，等待 " + backoffMs + "ms 后进行第" + (attempts + 1) + "次重试...");
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    System.out.println("[大模型分析] 重试被中断，停止重试");
                    break;
                }

            } catch (Exception e) {
                System.err.println("[大模型分析] 第" + attempts + "次调用失败: " + e.getMessage());
                e.printStackTrace(); // 打印完整堆栈信息

                // 检查是否达到最大重试次数
                if (attempts >= maxRetry) {
                    System.err.println("[大模型分析] 已达最大重试次数(" + maxRetry + ")，停止重试");
                    // 兜底：用图片数量作为AI总人数的保守估计
                    int size = imageUrls != null ? imageUrls.size() : 0;
                    Integer cur = record.getAiTotalCount();
                    record.setAiTotalCount(Math.max(cur == null ? 0 : cur, size));
                    record.setFeatureDescription("[]"); // 设置空的特征描述
                    System.out.println("[大模型分析] 设置兜底值 - AI总人数: " + record.getAiTotalCount() + ", 特征描述: []");
                    return;
                }

                // 等待后重试
                int backoffMs = Config.MEDIA_RETRY_BACKOFF_MS * attempts;
                System.out.println("[大模型分析] 等待 " + backoffMs + "ms 后进行第" + (attempts + 1) + "次重试...");
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    System.out.println("[大模型分析] 重试被中断，停止重试");
                    return;
                }
            }
        }


        System.out.println("[大模型分析] AI分析结果 - 总人数: " + aiTotalCount +
            ", 特征数量: " + (passengerFeatures != null ? passengerFeatures.length() : 0));

        // 增强现有记录，设置大模型识别的总人数
        String featureDescription = (passengerFeatures != null && passengerFeatures.length() > 0) ?
            passengerFeatures.toString() : "[]";
        record.setFeatureDescription(featureDescription);
        record.setAiTotalCount(aiTotalCount);

        System.out.println("[大模型分析] 成功增强OD记录，车辆: " + busNo +
            "，AI总人数: " + aiTotalCount +
            "，特征描述: " + (featureDescription.length() > 100 ?
                featureDescription.substring(0, 100) + "..." : featureDescription));

        // 注意：不再在这里发送到Kafka，由调用方统一处理
    }

	/**
	 * 标准化时间窗口ID格式，确保与存储时一致
	 * @param windowId 原始时间窗口ID
	 * @return 标准化后的时间窗口ID
	 */
	private String normalizeWindowId(String windowId) {
		if (windowId == null) return null;
		// 统一时间格式，将T替换为空格
		return windowId.replace("T", " ");
	}

	/**
	 * 智能截断特征向量，确保截断后仍能正确解码
	 * @param feature 原始特征向量字符串
	 * @return 截断后的特征向量字符串
	 */
	private String smartTruncateFeature(String feature) {
		if (feature == null || feature.isEmpty()) {
			return feature;
		}

		try {
			// 先尝试解码原始特征向量，获取维度数
			float[] originalFeatures = CosineSimilarity.parseFeatureVector(feature);
			if (originalFeatures.length == 0) {
				// 如果解码失败，直接截断到安全长度
				return feature.substring(0, Math.min(Config.MAX_FEATURE_SIZE_BYTES / 2, feature.length()));
			}

			// 计算目标维度数（基于配置的最大字节数）
			// 每个float 4字节，Base64编码后约5.33字节，加上JSON开销，按6字节计算
			int maxDimensions = Config.MAX_FEATURE_SIZE_BYTES / 6;
			maxDimensions = Math.min(maxDimensions, Config.MAX_FEATURE_VECTOR_DIMENSIONS);

			if (originalFeatures.length <= maxDimensions) {
				return feature; // 不需要截断
			}

			// 截断到目标维度数
			float[] truncatedFeatures = new float[maxDimensions];
			System.arraycopy(originalFeatures, 0, truncatedFeatures, 0, maxDimensions);

			// 重新编码为Base64
			ByteBuffer buffer = ByteBuffer.allocate(maxDimensions * 4);
			buffer.order(ByteOrder.LITTLE_ENDIAN);
			for (float f : truncatedFeatures) {
				buffer.putFloat(f);
			}

			return Base64.getEncoder().encodeToString(buffer.array());

		} catch (Exception e) {
			if (Config.LOG_DEBUG) {
				System.out.println("[PassengerFlowProcessor] 智能截断失败，使用简单截断: " + e.getMessage());
			}
			// 如果智能截断失败，使用简单截断
			return feature.substring(0, Math.min(Config.MAX_FEATURE_SIZE_BYTES / 2, feature.length()));
		}
	}

	/**
	 * 在时间范围内查找特征数据
	 * @param jedis Redis连接
	 * @param busNo 公交车编号
	 * @param begin 开始时间
	 * @param end 结束时间
	 * @return 特征数据集合
	 */
	private Set<String> findFeaturesInTimeRange(Jedis jedis, String busNo, LocalDateTime begin, LocalDateTime end) {
		Set<String> allFeatures = new HashSet<>();
		if (begin == null || end == null) return allFeatures;

		try {
			LocalDateTime cursor = begin.minusSeconds(Math.max(0, Config.IMAGE_TIME_TOLERANCE_BEFORE_SECONDS));
			LocalDateTime to = end.plusSeconds(Math.max(0, Config.IMAGE_TIME_TOLERANCE_AFTER_SECONDS));

			while (!cursor.isAfter(to)) {
				String win = cursor.format(formatter);
				Set<String> fset = jedis.smembers("features_set:" + busNo + ":" + win);
				if (fset != null && !fset.isEmpty()) {
					allFeatures.addAll(fset);
				}
				cursor = cursor.plusSeconds(1);
			}
		} catch (Exception e) {
			if (Config.LOG_DEBUG) {
				System.out.println("[PassengerFlowProcessor] Error finding features in time range: " + e.getMessage());
			}
		}

		return allFeatures;
	}

	/**
	 * 设置乘客特征集合信息
	 * @param record BusOdRecord记录
	 * @param jedis Redis连接
	 * @param busNo 公交车编号
	 * @param windowId 时间窗口ID
	 * @param sqeNo 开关门唯一批次号
	 */
	private void setPassengerFeatures(BusOdRecord record, Jedis jedis, String busNo, String windowId, String sqeNo) {
		try {
			// 🔥 优先使用sqeNo获取特征集合
			String featuresKey = sqeNo != null && !sqeNo.isEmpty() ?
				"features_set:" + sqeNo :
				"features_set:" + busNo + ":" + normalizeWindowId(windowId);
			Set<String> features = fetchFeaturesWithRetry(jedis, featuresKey);

			if (features != null && !features.isEmpty()) {
				JSONArray featuresArray = new JSONArray();
				JSONArray positionArray = new JSONArray();

				for (String featureStr : features) {
					try {
						JSONObject featureObj = new JSONObject(featureStr);
						featuresArray.put(featureObj);

						// 提取位置信息到单独的数组
						if (featureObj.has("position")) {
							JSONObject position = featureObj.getJSONObject("position");
							positionArray.put(position);
						}
					} catch (Exception e) {
						if (Config.LOG_DEBUG) {
							System.out.println("[PassengerFlowProcessor] Failed to parse feature JSON: " + featureStr);
						}
					}
				}

				record.setPassengerFeatures(featuresArray.toString());

				// 设置乘客图像坐标
				if (positionArray.length() > 0) {
					record.setPassengerPosition(positionArray.toString());
				}

				if (Config.PILOT_ROUTE_LOG_ENABLED) {
					System.out.println("[流程] 乘客特征集合设置完成，特征数: " + featuresArray.length() + ", 位置数: " + positionArray.length());
				}
			} else {
				String normalizedWindowId = normalizeWindowId(windowId);
				if (Config.LOG_DEBUG) {
					System.out.println("[PassengerFlowProcessor] No features found for busNo=" + busNo + ", windowId=" + normalizedWindowId);
					System.out.println("[PassengerFlowProcessor] Redis key: " + featuresKey);
				}

				// 最近窗口回退：在±N分钟内搜索最近存在特征的窗口
				String nearestWindow = findNearestFeatureWindow(jedis, busNo, normalizedWindowId, Config.FEATURE_FALLBACK_WINDOW_MINUTES);
				if (nearestWindow != null && !nearestWindow.equals(normalizedWindowId)) {
					String fallbackKey = "features_set:" + busNo + ":" + nearestWindow;
					features = fetchFeaturesWithRetry(jedis, fallbackKey);
					if (features != null && !features.isEmpty()) {
						if (Config.PILOT_ROUTE_LOG_ENABLED) {
							System.out.println("[流程][回退] 使用最近窗口特征: from=" + normalizedWindowId + " -> " + nearestWindow + ", size=" + features.size());
						}
						// 更新record中的窗口特征来源信息（不改变windowId字段，用于溯源）
					}
				}

				// 回退：按开关门时间区间聚合 features 与 position
				features = findFeaturesInTimeRange(jedis, busNo, record.getTimestampBegin(), record.getTimestampEnd());
				if (features != null && !features.isEmpty()) {
					JSONArray featuresArray = new JSONArray();
					JSONArray positionArray = new JSONArray();

					for (String featureStr : features) {
						try {
							JSONObject featureObj = new JSONObject(featureStr);
							featuresArray.put(featureObj);
							if (featureObj.has("position")) {
								JSONObject position = featureObj.getJSONObject("position");
								positionArray.put(position);
							}
						} catch (Exception e) {
							if (Config.LOG_DEBUG) {
								System.out.println("[PassengerFlowProcessor] Failed to parse feature JSON in time range: " + featureStr);
							}
						}
					}

					if (featuresArray.length() > 0) {
						record.setPassengerFeatures(featuresArray.toString());
						if (positionArray.length() > 0) {
							record.setPassengerPosition(positionArray.toString());
						}
						if (Config.PILOT_ROUTE_LOG_ENABLED) {
							System.out.println("[流程][回退] 乘客特征集合按时间区间聚合成功，特征数: " + featuresArray.length());
						}
					} else {
						// 设置默认值，避免字段为null
						record.setPassengerFeatures("[]");
						record.setPassengerPosition("[]");
					}
				} else {
					// 设置默认值，避免字段为null
					record.setPassengerFeatures("[]");
					record.setPassengerPosition("[]");
				}
			}
		} catch (Exception e) {
			if (Config.LOG_ERROR) {
				System.err.println("[PassengerFlowProcessor] Error setting passenger features: " + e.getMessage());
			}

			// 异常情况下设置默认值
			record.setPassengerFeatures("[]");
			record.setPassengerPosition("[]");
		}
	}

	/**
	 * 带重试地获取特征集合
	 */
	private Set<String> fetchFeaturesWithRetry(Jedis jedis, String featuresKey) {
		Set<String> features = null;
		int attempts = 0;
		int maxRetry = Math.max(0, Config.REDIS_FEATURE_FETCH_RETRY);
		while (true) {
			attempts++;
			features = jedis.smembers(featuresKey);
			if (features != null && !features.isEmpty()) return features;
			if (attempts >= maxRetry) return features;
			int backoff = Config.REDIS_FEATURE_FETCH_BACKOFF_MS * attempts;
			try { Thread.sleep(backoff); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return features; }
		}
	}

	/**
	 * 在±minutes范围内，按秒查找最近存在features_set的窗口，返回最近的windowId
	 */
	private String findNearestFeatureWindow(Jedis jedis, String busNo, String baseWindowId, int minutes) {
		try {
			LocalDateTime base = LocalDateTime.parse(baseWindowId, formatter);
			int maxSec = Math.max(0, minutes) * 60;
			String bestWin = null;
			long bestDist = Long.MAX_VALUE;
			for (int delta = 1; delta <= maxSec; delta++) {
				LocalDateTime before = base.minusSeconds(delta);
				LocalDateTime after = base.plusSeconds(delta);
				String wb = before.format(formatter);
				String wa = after.format(formatter);
				if (jedis.exists("features_set:" + busNo + ":" + wb)) {
					bestWin = wb; bestDist = delta; break;
				}
				if (jedis.exists("features_set:" + busNo + ":" + wa)) {
					bestWin = wa; bestDist = delta; break;
				}
			}
			if (bestWin != null && Config.LOG_DEBUG) {
				System.out.println("[PassengerFlowProcessor] 最近特征窗口: base=" + baseWindowId + ", nearest=" + bestWin + ", |Δ|秒=" + bestDist);
			}
			return bestWin;
		} catch (Exception e) {
			if (Config.LOG_DEBUG) {
				System.out.println("[PassengerFlowProcessor] findNearestFeatureWindow异常: " + e.getMessage());
			}
			return null;
		}
	}

    private int[] waitForCvResultsStable(Jedis jedis, String busNo, String windowId, String sqeNo) {
        long start = System.currentTimeMillis();
        long lastChange = start;
        int lastUp = getCachedUpCount(jedis, busNo, windowId, sqeNo);
        int lastDown = getCachedDownCount(jedis, busNo, windowId, sqeNo);
        int lastImageCount = getAllImageUrls(jedis, busNo, windowId).size();

        if (Config.LOG_DEBUG) {
            System.out.println("[CV结果等待] 初始状态 - 上车: " + lastUp + ", 下车: " + lastDown + ", 图片: " + lastImageCount);
            System.out.println("[CV结果等待] 查询的Redis键:");
            if (sqeNo != null && !sqeNo.isEmpty()) {
                System.out.println("  🔥 cv_up_count:" + sqeNo);
                System.out.println("  🔥 cv_down_count:" + sqeNo);
            } else {
                System.out.println("  cv_up_count:" + busNo + ":" + windowId);
                System.out.println("  cv_down_count:" + busNo + ":" + windowId);
            }
        }

        while (System.currentTimeMillis() - start < Config.CV_RESULT_GRACE_MS) {
            try {
                Thread.sleep(Config.CV_RESULT_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            int up = getCachedUpCount(jedis, busNo, windowId, sqeNo);
            int down = getCachedDownCount(jedis, busNo, windowId, sqeNo);
            int img = getAllImageUrls(jedis, busNo, windowId).size();

            if (up != lastUp || down != lastDown || img != lastImageCount) {
                if (Config.LOG_DEBUG) {
                    System.out.println("[CV结果等待] 检测到变化 - 上车: " + lastUp + "->" + up +
                        ", 下车: " + lastDown + "->" + down + ", 图片: " + lastImageCount + "->" + img);
                }
                lastUp = up;
                lastDown = down;
                lastImageCount = img;
                lastChange = System.currentTimeMillis();
            }
            if (System.currentTimeMillis() - lastChange >= Config.CV_RESULT_STABLE_MS) {
                if (Config.LOG_DEBUG) {
                    System.out.println("[CV结果等待] 结果稳定，停止等待");
                }
                break; // 在稳定窗口内无变化
            }
        }

        if (Config.LOG_DEBUG) {
            System.out.println("[CV结果等待] 最终结果 - 上车: " + lastUp + ", 下车: " + lastDown +
                ", 等待时间: " + (System.currentTimeMillis() - start) + "ms");
        }

        return new int[]{lastUp, lastDown};
    }

	/**
	 * 区间分方向图片
	 * @param sqeNo 开关门唯一批次号
	 */
	private Map<String, List<String>> getImagesByTimeRangeSeparated(Jedis jedis, String busNo, LocalDateTime openTime,
			LocalDateTime closeTime, int beforeSec, int afterSec, String sqeNo) {
		Map<String, List<String>> result = new HashMap<>();
		result.put("up", new ArrayList<>());
		result.put("down", new ArrayList<>());
		if (openTime == null || closeTime == null) return result;
		try {
			LocalDateTime from = openTime.minusSeconds(Math.max(0, beforeSec));
			LocalDateTime to = closeTime.plusSeconds(Math.max(0, afterSec));
			System.out.println("[图片收集] (按方向) 区间聚合: bus=" + busNo + ", from=" + from.format(formatter) + ", to=" + to.format(formatter) + ", sqeNo=" + sqeNo);

			// 🔥 优先尝试基于sqeNo的图片收集
			if (sqeNo != null && !sqeNo.isEmpty()) {
				Set<String> upImagesBySqe = jedis.smembers("image_urls:" + sqeNo + ":up");
				if (upImagesBySqe != null && !upImagesBySqe.isEmpty()) {
					result.get("up").addAll(upImagesBySqe);
					System.out.println("[图片收集] (按方向) 基于sqeNo收集到上车图片 " + upImagesBySqe.size() + " 张");
				}
				Set<String> downImagesBySqe = jedis.smembers("image_urls:" + sqeNo + ":down");
				if (downImagesBySqe != null && !downImagesBySqe.isEmpty()) {
					result.get("down").addAll(downImagesBySqe);
					System.out.println("[图片收集] (按方向) 基于sqeNo收集到下车图片 " + downImagesBySqe.size() + " 张");
				}
			}

			// 🔥 如果基于sqeNo没有找到图片，按时间范围兜底收集
			if (result.get("up").isEmpty() && result.get("down").isEmpty()) {
				LocalDateTime cursor = from;
				while (!cursor.isAfter(to)) {
					String win = cursor.format(formatter);
					Set<String> up = jedis.smembers("image_urls:" + busNo + ":" + win + ":up");
					if (up != null && !up.isEmpty()) result.get("up").addAll(up);
					Set<String> down = jedis.smembers("image_urls:" + busNo + ":" + win + ":down");
					if (down != null && !down.isEmpty()) result.get("down").addAll(down);
					cursor = cursor.plusSeconds(1);
				}
				System.out.println("[图片收集] (按方向) 兜底按时间范围收集完成");
			}
			System.out.println("[图片收集] (按方向) 区间聚合共收集到 上车=" + result.get("up").size() + ", 下车=" + result.get("down").size());
		} catch (Exception e) {
			System.err.println("[图片收集] (按方向) 区间聚合异常: " + e.getMessage());
		}
		return result;
	}

	/**
	 * 按方向生成视频，设置JSON数组到 passengerVideoUrl
	 */
	private void processImagesToVideoByDirection(BusOdRecord record, Jedis jedis, String busNo, String windowId,
			List<String> upImages, List<String> downImages) {
		System.out.println("[图片转视频-按方向] 开始处理，bus=" + busNo + ", windowId=" + windowId);
		JSONArray results = new JSONArray();
		try {
			String dynamicDir = "PassengerFlowRecognition/" + windowId;
			String tempDir = System.getProperty("java.io.tmpdir");
			if (upImages != null && !upImages.isEmpty()) {
				try {
					File upVideo = ImageToVideoConverter.convertImagesToVideo(upImages, tempDir);
					String upUrl = OssUtil.uploadVideoFile(upVideo, UUID.randomUUID().toString() + ".mp4", dynamicDir);
					JSONObject upObj = new JSONObject();
					upObj.put("location", "up");
					upObj.put("videoUrl", upUrl);
					results.put(upObj);
					upVideo.delete();
					System.out.println("[图片转视频-按方向] 上车视频上传成功: " + upUrl);
				} catch (Exception e) {
					System.err.println("[图片转视频-按方向] 上车转换失败: " + e.getMessage());
				}
			}
			if (downImages != null && !downImages.isEmpty()) {
				try {
					File downVideo = ImageToVideoConverter.convertImagesToVideo(downImages, tempDir);
					String downUrl = OssUtil.uploadVideoFile(downVideo, UUID.randomUUID().toString() + ".mp4", dynamicDir);
					JSONObject downObj = new JSONObject();
					downObj.put("location", "down");
					downObj.put("videoUrl", downUrl);
					results.put(downObj);
					downVideo.delete();
					System.out.println("[图片转视频-按方向] 下车视频上传成功: " + downUrl);
				} catch (Exception e) {
					System.err.println("[图片转视频-按方向] 下车转换失败: " + e.getMessage());
				}
			}
		} catch (Exception e) {
			System.err.println("[图片转视频-按方向] 处理过程异常: " + e.getMessage());
		}
		record.setPassengerVideoUrl(results.toString());
	}

	/**
	 * 精确窗口分方向图片
	 * @param sqeNo 开关门唯一批次号
	 */
	private Map<String, List<String>> getImagesByExactWindowSeparated(Jedis jedis, String busNo, String windowId, String sqeNo) {
		Map<String, List<String>> result = new HashMap<>();
		result.put("up", new ArrayList<>());
		result.put("down", new ArrayList<>());
		try {
			// 🔥 优先使用sqeNo获取图片
			if (sqeNo != null && !sqeNo.isEmpty()) {
				Set<String> upImagesBySqe = jedis.smembers("image_urls:" + sqeNo + ":up");
				if (upImagesBySqe != null && !upImagesBySqe.isEmpty()) {
					result.get("up").addAll(upImagesBySqe);
					System.out.println("[图片收集] (按方向) 基于sqeNo收集到上车图片 " + upImagesBySqe.size() + " 张");
				}
				Set<String> downImagesBySqe = jedis.smembers("image_urls:" + sqeNo + ":down");
				if (downImagesBySqe != null && !downImagesBySqe.isEmpty()) {
					result.get("down").addAll(downImagesBySqe);
					System.out.println("[图片收集] (按方向) 基于sqeNo收集到下车图片 " + downImagesBySqe.size() + " 张");
				}
			}

			// 🔥 如果基于sqeNo没有找到图片，兜底使用原有逻辑
			if (result.get("up").isEmpty() && result.get("down").isEmpty()) {
				String upImagesKey = "image_urls:" + busNo + ":" + windowId + ":up";
				Set<String> upImages = jedis.smembers(upImagesKey);
				if (upImages != null && !upImages.isEmpty()) {
					result.get("up").addAll(upImages);
					System.out.println("[图片收集] (按方向) 兜底收集到上车图片 " + upImages.size() + " 张");
				}
				String downImagesKey = "image_urls:" + busNo + ":" + windowId + ":down";
				Set<String> downImages = jedis.smembers(downImagesKey);
				if (downImages != null && !downImages.isEmpty()) {
					result.get("down").addAll(downImages);
					System.out.println("[图片收集] (按方向) 兜底收集到下车图片 " + downImages.size() + " 张");
				}
			}
		} catch (Exception e) {
			System.err.println("[图片收集] (按方向) 精确匹配异常: " + e.getMessage());
		}
		return result;
	}

	/**
     * 将float数组转为JSONArray（安全）
     */
    private static org.json.JSONArray toJsonArraySafe(float[] vec) {
        org.json.JSONArray arr = new org.json.JSONArray();
        if (vec == null) return arr;
        for (float v : vec) arr.put(v);
        return arr;
    }

    /**
     * 保存downup消息到数据库
     */
    private void saveDownUpMessage(JSONObject data, String busNo, String busId, String cameraNo) {
        try {
            // 创建完整的消息对象
            JSONObject fullMessage = new JSONObject();
            fullMessage.put("event", "downup");
            fullMessage.put("data", data);

            // 优化events数组中的image和feature字段
            JSONObject optimizedData = new JSONObject(data.toString());
            JSONArray events = optimizedData.optJSONArray("events");
            if (events != null) {
                for (int i = 0; i < events.length(); i++) {
                    JSONObject event = events.getJSONObject(i);
                    optimizeEventsImageFields(event);
                }
                optimizedData.put("events", events);
            }

            // 创建优化后的完整消息
            JSONObject optimizedFullMessage = new JSONObject();
            optimizedFullMessage.put("event", "downup");
            optimizedFullMessage.put("data", optimizedData);

            // 创建downup消息对象
            RetrieveDownUpMsg downUpMsg = new RetrieveDownUpMsg();
            downUpMsg.setBusNo(busNo);
            downUpMsg.setBusId(busId); // 设置bus_id字段
            downUpMsg.setCameraNo(cameraNo);
            downUpMsg.setTimestamp(data.optString("timestamp"));
            // 🔥 提取并设置sqe_no字段
            String sqeNo = data.optString("sqe_no");
            downUpMsg.setSqeNo(sqeNo);
            // downUpMsg.setStationId(data.optString("stationId"));
            // downUpMsg.setStationName(data.optString("stationName"));
            downUpMsg.setEvent("downup");

            // 解析events数组
            JSONArray eventsArray = data.optJSONArray("events");
            if (eventsArray != null) {
                StringBuilder eventsJson = new StringBuilder("[");
                for (int i = 0; i < eventsArray.length(); i++) {
                    if (i > 0) eventsJson.append(",");
                    JSONObject event = eventsArray.getJSONObject(i);

                    DownUpEvent downUpEvent = new DownUpEvent();
                    downUpEvent.setDirection(event.optString("direction"));
                    downUpEvent.setFeature(event.has("feature") && !event.isNull("feature") ? "有" : null);
                    downUpEvent.setImage(event.has("image") && !event.isNull("image") ? "有" : null);
                    downUpEvent.setBoxX(event.optInt("box_x"));
                    downUpEvent.setBoxY(event.optInt("box_y"));
                    downUpEvent.setBoxW(event.optInt("box_w"));
                    downUpEvent.setBoxH(event.optInt("box_h"));

                    JSONObject eventJson = new JSONObject();
                    eventJson.put("direction", downUpEvent.getDirection());
                    eventJson.put("feature", downUpEvent.getFeature());
                    eventJson.put("image", downUpEvent.getImage());
                    eventJson.put("box_x", downUpEvent.getBoxX());
                    eventJson.put("box_y", downUpEvent.getBoxY());
                    eventJson.put("box_w", downUpEvent.getBoxW());
                    eventJson.put("box_h", downUpEvent.getBoxH());

                    eventsJson.append(eventJson.toString());
                }
                eventsJson.append("]");
                downUpMsg.setEventsJson(eventsJson.toString());
            }

            downUpMsg.setOriginalMessage(optimizedFullMessage.toString());

            if (Config.LOG_INFO) {
                System.out.println(String.format("[WebSocket消息保存] 🔥 开始保存downup消息: 车辆=%s, 车辆ID=%s, sqe_no=%s, 事件数=%d",
                    busNo, busId, sqeNo, eventsArray != null ? eventsArray.length() : 0));
            }

            // 异步保存到数据库
            asyncDbServiceManager.saveDownUpMsgAsync(downUpMsg);

            if (Config.LOG_INFO) {
                System.out.println(String.format("[WebSocket消息保存] 🔥 downup消息记录完成: 车辆=%s, 车辆ID=%s, sqe_no=%s, 时间=%s",
                    busNo, busId, sqeNo, downUpMsg.getTimestamp()));
            }

        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                System.err.println(String.format("[WebSocket消息保存] 保存车辆 %s downup消息时发生错误: %s", busNo, e.getMessage()));
                e.printStackTrace();
            }
        }
    }

    /**
     * 保存load_factor消息到数据库
     */
    private void saveLoadFactorMessage(JSONObject data, String busNo, String cameraNo) {
        try {
            // 创建完整的消息对象
            JSONObject fullMessage = new JSONObject();
            fullMessage.put("event", "load_factor");
            fullMessage.put("data", data);

            // 创建load_factor消息对象
            RetrieveLoadFactorMsg loadFactorMsg = new RetrieveLoadFactorMsg();
            loadFactorMsg.setBusNo(busNo);
            loadFactorMsg.setCameraNo(cameraNo);
            loadFactorMsg.setTimestamp(data.optString("timestamp"));
            loadFactorMsg.setCount(data.optInt("count"));
            // 处理满载率，确保转换为BigDecimal
            double factorValue = data.optDouble("factor", 0.0);
            loadFactorMsg.setFactor(java.math.BigDecimal.valueOf(factorValue));
            // 🔥 提取并设置sqe_no字段
            String sqeNo = data.optString("sqe_no");
            loadFactorMsg.setSqeNo(sqeNo);
            loadFactorMsg.setEvent("load_factor");
            loadFactorMsg.setOriginalMessage(fullMessage.toString());

            if (Config.LOG_INFO) {
                System.out.println(String.format("[WebSocket消息保存] 🔥 开始保存load_factor消息: 车辆=%s, sqe_no=%s, 人数=%d, 满载率=%.2f",
                    busNo, sqeNo, loadFactorMsg.getCount(), loadFactorMsg.getFactor()));
            }

            // 异步保存到数据库
            asyncDbServiceManager.saveLoadFactorMsgAsync(loadFactorMsg);

            if (Config.LOG_INFO) {
                System.out.println(String.format("[WebSocket消息保存] 🔥 load_factor消息记录完成: 车辆=%s, sqe_no=%s, 时间=%s",
                    busNo, sqeNo, loadFactorMsg.getTimestamp()));
            }

        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                System.err.println(String.format("[WebSocket消息保存] 保存车辆 %s load_factor消息时发生错误: %s", busNo, e.getMessage()));
                e.printStackTrace();
            }
        }
    }

    /**
     * 优化事件对象中的image和feature字段
     */
    private void optimizeEventsImageFields(JSONObject event) {
        // 优化image字段
        if (event.has("image") && !event.isNull("image")) {
            String imageValue = event.optString("image");
            if (imageValue != null && !imageValue.trim().isEmpty()) {
                event.put("image", "有");
            }
        }

        // 优化feature字段
        if (event.has("feature") && !event.isNull("feature")) {
            String featureValue = event.optString("feature");
            if (featureValue != null && !featureValue.trim().isEmpty()) {
                event.put("feature", "有");
            }
        }
    }

    /**
     * 保存WebSocket消息到数据库
     * 第一时间无条件保存所有WebSocket消息到retrieve_all_ws表
     */
    private void saveWebSocketMessage(JSONObject eventJson, String event, JSONObject data) {
        try {
            if (data == null) return;

            // 创建WebSocket消息记录对象
            RetrieveAllWs allWs = new RetrieveAllWs();

            // 基本信息
            String busNo = data.optString("bus_no");
            if (busNo == null || busNo.trim().isEmpty()) {
                busNo = "UNKNOWN";
            }

            allWs.setBusNo(busNo);
            allWs.setEvent(event);
            allWs.setRawMessage(eventJson.toString());
            allWs.setReceivedAt(LocalDateTime.now());

            // 提取关键字段
            allWs.setBusId(data.optString("bus_id"));
            allWs.setCameraNo(data.optString("camera_no"));
            allWs.setStationId(data.optString("stationId"));
            allWs.setStationName(data.optString("stationName"));
            // 🔥 提取并设置sqe_no字段
            String sqeNo = data.optString("sqe_no");
            allWs.setSqeNo(sqeNo);

            // 解析时间戳
            String timestamp = data.optString("timestamp");
            if (timestamp != null && !timestamp.trim().isEmpty()) {
                try {
                    LocalDateTime parsedTime = LocalDateTime.parse(timestamp.trim(),
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    allWs.setMessageTimestamp(parsedTime);
                } catch (Exception e) {
                    // 解析失败使用当前时间
                    allWs.setMessageTimestamp(LocalDateTime.now());
                }
            } else {
                allWs.setMessageTimestamp(LocalDateTime.now());
            }

            // 确保bus_id不为空
            if (allWs.getBusId() == null || allWs.getBusId().trim().isEmpty()) {
                allWs.setBusId(busNo);
            }

            if (Config.LOG_DEBUG) {
                System.out.println(String.format("[第一时间保存] 🔥 WebSocket消息到retrieve_all_ws: 事件=%s, 车辆=%s, sqe_no=%s",
                    event, busNo, sqeNo));
            }

            // 异步保存到retrieve_all_ws表
            asyncDbServiceManager.saveAllWebSocketMessageAsync(allWs);

        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                System.err.println(String.format("[第一时间保存] 保存WebSocket消息时发生错误: 事件=%s, 错误=%s", event, e.getMessage()));
                e.printStackTrace();
            }
        }
    }

}
