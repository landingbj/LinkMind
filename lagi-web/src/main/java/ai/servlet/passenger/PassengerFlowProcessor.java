package ai.servlet.passenger;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * 乘客流量处理器，处理CV WebSocket推送的事件
 */
public class PassengerFlowProcessor {

	private static final ObjectMapper objectMapper = new ObjectMapper();
	private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private final JedisPool jedisPool = new JedisPool(Config.REDIS_HOST, Config.REDIS_PORT);
	private KafkaProducer<String, String> producer;

	public PassengerFlowProcessor() {
		Properties props = KafkaConfig.getProducerProperties();
		producer = new KafkaProducer<>(props);
	}

	public void processEvent(JSONObject eventJson) {
		String event = eventJson.optString("event");
		JSONObject data = eventJson.optJSONObject("data");

		// 关闭CV事件详细日志，避免大payload(如base64)刷屏

		if (data == null) {
			if (Config.LOG_ERROR) {
				System.err.println("[流程中断] CV事件data为空，跳过。event=" + event);
			}
			return;
		}

		String busNo = data.optString("bus_no");
		String cameraNo = data.optString("camera_no");

		// 降低参数解析日志噪音

		try (Jedis jedis = jedisPool.getResource()) {
			jedis.auth(Config.REDIS_PASSWORD);

			switch (event) {
				case "downup":
					handleDownUpEvent(data, busNo, cameraNo, jedis);
					break;
				case "load_factor":
					// 高频事件，移除过程性日志
					handleLoadFactorEvent(data, busNo, jedis);
					break;
				case "open_close_door":
					// 关键事件在KafkaConsumerService侧已有明确日志
					handleOpenCloseDoorEvent(data, busNo, cameraNo, jedis);
					break;
				case "notify_pull_file":
					// 此事件通常量不大，必要时可单行打印
					handleNotifyPullFileEvent(data, busNo, cameraNo, jedis);
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

	private void handleDownUpEvent(JSONObject data, String busNo, String cameraNo, Jedis jedis) throws IOException, SQLException {
		LocalDateTime eventTime = LocalDateTime.parse(data.optString("timestamp").replace(" ", "T"));
		JSONArray events = data.optJSONArray("events");

		if (events == null || events.length() == 0) {
			return;
		}

		List<BusOdRecord> odRecords = new ArrayList<>();
		int upCount = 0, downCount = 0;

		for (int i = 0; i < events.length(); i++) {
			JSONObject ev = events.getJSONObject(i);
			String direction = ev.optString("direction");
			String feature = ev.optString("feature");
			String image = ev.optString("image");
			int boxX = ev.optInt("box_x");
			int boxY = ev.optInt("box_y");
			int boxW = ev.optInt("box_w");
			int boxH = ev.optInt("box_h");

			// 处理base64图片：解码并上传到OSS
			String imageUrl = null;
			if (image != null && !image.isEmpty() && Config.ENABLE_IMAGE_PROCESSING) {
				try {
					imageUrl = processBase64Image(image, busNo, cameraNo, eventTime);
					// 屏蔽图片URL日志
				} catch (Exception e) {
					if (Config.LOG_ERROR) {
						System.err.println("[PassengerFlowProcessor] Error processing base64 image: " + e.getMessage());
					}
				}
			}

			// 获取当前开门时间窗口ID
			String windowId = jedis.get("open_time:" + busNo);
			if (windowId == null) {
				continue;
			}

			if ("up".equals(direction)) {
				upCount++;

				// 缓存上车特征和站点信息
				cacheFeatureStationMapping(jedis, feature, getCurrentStationId(busNo, jedis), getCurrentStationName(busNo, jedis), "up");

				// 更新上车计数
				String cvUpCountKey = "cv_up_count:" + busNo + ":" + windowId;
				jedis.incr(cvUpCountKey);
				jedis.expire(cvUpCountKey, Config.REDIS_TTL_OPEN_TIME);

				// 缓存特征集合（包含方向信息）
				String featuresKey = "features_set:" + busNo + ":" + windowId;
				JSONObject featureInfo = new JSONObject();
				featureInfo.put("feature", feature);
				featureInfo.put("direction", "up");
				featureInfo.put("timestamp", eventTime.format(formatter));
				featureInfo.put("image", imageUrl);
				JSONObject position = new JSONObject();
				position.put("xLeftUp", boxX);
				position.put("yLeftUp", boxY);
				position.put("xRightBottom", boxX + boxW);
				position.put("yRightBottom", boxY + boxH);
				featureInfo.put("position", position);
				jedis.sadd(featuresKey, featureInfo.toString());
				jedis.expire(featuresKey, Config.REDIS_TTL_FEATURES);

				// 缓存图片URL
				if (imageUrl != null) {
					cacheImageUrl(jedis, busNo, windowId, imageUrl, "up");
				}

				// 缓存乘客位置信息（特征向量 -> 位置信息的映射）
				String positionKey = "feature_position:" + busNo + ":" + windowId + ":" + feature;
				JSONObject positionInfo = new JSONObject();
				positionInfo.put("xLeftUp", boxX);
				positionInfo.put("yLeftUp", boxY);
				positionInfo.put("xRightBottom", boxX + boxW);
				positionInfo.put("yRightBottom", boxY + boxH);
				positionInfo.put("direction", "up");
				jedis.set(positionKey, positionInfo.toString());
				jedis.expire(positionKey, Config.REDIS_TTL_FEATURES);

				// 移除逐条UP处理完成日志
			} else if ("down".equals(direction)) {
				downCount++;

				// 尝试匹配上车特征
				float similarity = matchPassengerFeature(feature, busNo);
				// 屏蔽相似度调试日志

				if (similarity > 0.8f) {
					JSONObject onStation = getOnStationFromCache(jedis, feature);
					if (onStation != null) {
						// 屏蔽命中特征调试日志
					}
				}

				// 更新下车计数
				String cvDownCountKey = "cv_down_count:" + busNo + ":" + windowId;
				jedis.incr(cvDownCountKey);
				jedis.expire(cvDownCountKey, Config.REDIS_TTL_OPEN_TIME);

				// 缓存下车特征到特征集合（包含方向信息）
				String featuresKey = "features_set:" + busNo + ":" + windowId;
				JSONObject featureInfo = new JSONObject();
				featureInfo.put("feature", feature);
				featureInfo.put("direction", "down");
				featureInfo.put("timestamp", eventTime.format(formatter));
				featureInfo.put("image", imageUrl);
				JSONObject positionInfo = new JSONObject();
				positionInfo.put("xLeftUp", boxX);
				positionInfo.put("yLeftUp", boxY);
				positionInfo.put("xRightBottom", boxX + boxW);
				positionInfo.put("yRightBottom", boxY + boxH);
				featureInfo.put("position", positionInfo);
				jedis.sadd(featuresKey, featureInfo.toString());
				jedis.expire(featuresKey, Config.REDIS_TTL_FEATURES);

				// 缓存图片URL
				if (imageUrl != null) {
					cacheImageUrl(jedis, busNo, windowId, imageUrl, "down");
				}

				// 缓存乘客位置信息（特征向量 -> 位置信息的映射）
				String positionKey = "feature_position:" + busNo + ":" + windowId + ":" + feature;
				JSONObject position = new JSONObject();
				position.put("xLeftUp", boxX);
				position.put("yLeftUp", boxY);
				position.put("xRightBottom", boxX + boxW);
				position.put("yRightBottom", boxY + boxH);
				position.put("direction", "down");
				jedis.set(positionKey, position.toString());
				jedis.expire(positionKey, Config.REDIS_TTL_FEATURES);

				// 移除逐条DOWN处理完成日志
			}
		}

		// 不再在downup事件中自算总人数，统一以CV推送的vehicle_total_count为准

		// 汇总日志可按需开启，默认关闭
	}

	private void handleLoadFactorEvent(JSONObject data, String busNo, Jedis jedis) {
		int count = data.optInt("count");
		double factor = data.optDouble("factor");

		// 缓存满载率和车辆总人数，设置过期时间
		String loadFactorKey = "load_factor:" + busNo;
		String vehicleTotalCountKey = "vehicle_total_count:" + busNo;
		jedis.set(loadFactorKey, String.valueOf(factor));
		jedis.set(vehicleTotalCountKey, String.valueOf(count));  // 存储CV系统的车辆总人数
		jedis.expire(loadFactorKey, Config.REDIS_TTL_COUNTS);
		jedis.expire(vehicleTotalCountKey, Config.REDIS_TTL_COUNTS);
		// 移除高频缓存日志
	}

	private void handleOpenCloseDoorEvent(JSONObject data, String busNo, String cameraNo, Jedis jedis) throws IOException, SQLException {
		String action = data.optString("action");
		LocalDateTime eventTime = LocalDateTime.parse(data.optString("timestamp").replace(" ", "T"));

		if ("open".equals(action)) {
			// 试点线路CV开门流程日志（可通过配置控制）
			if (Config.PILOT_ROUTE_LOG_ENABLED) {
				System.out.println("[试点线路CV开门流程] CV系统接收到开门信号:");
				System.out.println("   busNo=" + busNo);
				System.out.println("   cameraNo=" + cameraNo);
				System.out.println("   时间=" + eventTime.format(formatter));
				System.out.println("   ================================================================================");
			}

			// 开门时创建记录并缓存
			BusOdRecord record = createBaseRecord(busNo, cameraNo, eventTime, jedis);
			record.setTimestampBegin(eventTime);
			record.setStationIdOn(getCurrentStationId(busNo, jedis));
			record.setStationNameOn(getCurrentStationName(busNo, jedis));

			// 生成开门时间窗口ID
			String windowId = System.currentTimeMillis() + "_" + busNo;
			jedis.set("open_time:" + busNo, windowId);
			jedis.expire("open_time:" + busNo, Config.REDIS_TTL_OPEN_TIME);

			// 初始化计数
			jedis.set("cv_up_count:" + busNo + ":" + windowId, "0");
			jedis.set("cv_down_count:" + busNo + ":" + windowId, "0");
			jedis.expire("cv_up_count:" + busNo + ":" + windowId, Config.REDIS_TTL_OPEN_TIME);
			jedis.expire("cv_down_count:" + busNo + ":" + windowId, Config.REDIS_TTL_OPEN_TIME);

			if (Config.PILOT_ROUTE_LOG_ENABLED) {
				System.out.println("[试点线路CV开门流程] 开门时间窗口已创建:");
				System.out.println("   windowId=" + windowId);
				System.out.println("   上车计数已初始化");
				System.out.println("   下车计数已初始化");
				System.out.println("   ================================================================================");
			}

			if (Config.LOG_INFO) {
				System.out.println("[PassengerFlowProcessor] Door OPEN event processed for busNo=" + busNo + ", windowId=" + windowId);
			}
		} else if ("close".equals(action)) {
			// 试点线路CV关门流程日志（可通过配置控制）
			if (Config.PILOT_ROUTE_LOG_ENABLED) {
				System.out.println("[试点线路CV关门流程] CV系统接收到关门信号:");
				System.out.println("   busNo=" + busNo);
				System.out.println("   cameraNo=" + cameraNo);
				System.out.println("   时间=" + eventTime.format(formatter));
				System.out.println("   ================================================================================");
			}

			// 关门时处理OD数据并发送到Kafka
			handleCloseDoorEvent(data, busNo, cameraNo, jedis);
		}
	}

	private void handleNotifyPullFileEvent(JSONObject data, String busNo, String cameraNo, Jedis jedis) throws IOException, SQLException {
		LocalDateTime begin = LocalDateTime.parse(data.optString("timestamp_begin").replace(" ", "T"));
		LocalDateTime end = LocalDateTime.parse(data.optString("timestamp_end").replace(" ", "T"));
		String fileUrl = data.optString("fileurl");

		// 下载视频
		File videoFile = downloadFile(fileUrl);
		String ossUrl = OssUtil.uploadFile(videoFile, UUID.randomUUID().toString() + ".mp4");
		// 移除视频上传信息日志

		// 调用多模态模型（视频）
		JSONObject modelResponse = callMediaApi(null, ossUrl, Config.PASSENGER_PROMPT);
		JSONObject responseObj = modelResponse.optJSONObject("response");
		JSONArray passengerFeatures = responseObj != null ? responseObj.optJSONArray("passenger_features") : new JSONArray();

		// 解析大模型识别的上下车人数
		int aiUpCount = 0;
		int aiDownCount = 0;
		if (responseObj != null) {
			// 假设大模型返回的是总人数，这里需要根据实际API响应格式调整
			int modelTotalCount = responseObj.optInt("total_count", 0);
			// 如果大模型能区分上下车，则分别获取；否则全部作为下车人数
			aiUpCount = responseObj.optInt("up_count", 0);
			aiDownCount = responseObj.optInt("down_count", modelTotalCount);
		}

		BusOdRecord record = createBaseRecord(busNo, cameraNo, begin, jedis);
		record.setTimestampEnd(end);
		record.setFeatureDescription(passengerFeatures.toString());

		// 设置大模型识别的上下车人数
		record.setAiUpCount(aiUpCount);
		record.setAiDownCount(aiDownCount);

		// 设置车辆总人数（从CV系统获取）
		record.setVehicleTotalCount(getVehicleTotalCountFromRedis(jedis, busNo));

		// 不再设置tripTotalCount

		// 移除创建模型OD记录信息日志
		sendToKafka(record);
	}

	private void handleCloseDoorEvent(JSONObject data, String busNo, String cameraNo, Jedis jedis) throws IOException, SQLException {
		LocalDateTime eventTime = LocalDateTime.parse(data.optString("timestamp").replace(" ", "T"));
		String action = data.optString("action");

		if ("close".equals(action)) {
			if (Config.PILOT_ROUTE_LOG_ENABLED) {
				System.out.println("[试点线路CV关门流程] 开始处理关门事件:");
				System.out.println("   busNo=" + busNo);
				System.out.println("   cameraNo=" + cameraNo);
				System.out.println("   关门时间=" + eventTime.format(formatter));
				System.out.println("   ================================================================================");
			}

			// 获取开门时间窗口ID
			String windowId = jedis.get("open_time:" + busNo);
			if (windowId != null) {
				if (Config.PILOT_ROUTE_LOG_ENABLED) {
					System.out.println("[试点线路CV关门流程] 找到开门时间窗口:");
				 System.out.println("   windowId=" + windowId);
					System.out.println("   ================================================================================");
				}

				// 获取CV计数
				int cvUpCount = getCachedUpCount(jedis, busNo, windowId);
				int cvDownCount = getCachedDownCount(jedis, busNo, windowId);

				if (Config.PILOT_ROUTE_LOG_ENABLED) {
					System.out.println("[试点线路CV关门流程] CV计数统计完成:");
					System.out.println("   上车人数=" + cvUpCount);
					System.out.println("   下车人数=" + cvDownCount);
					System.out.println("   ================================================================================");
				}

				// 创建关门记录
				BusOdRecord record = createBaseRecord(busNo, cameraNo, eventTime, jedis);
				record.setTimestampEnd(eventTime);
				record.setUpCount(cvUpCount);
				record.setDownCount(cvDownCount);

				// 不再设置tripTotalCount

				// 设置站点信息
				record.setStationIdOff(getCurrentStationId(busNo, jedis));
				record.setStationNameOff(getCurrentStationName(busNo, jedis));

				if (Config.PILOT_ROUTE_LOG_ENABLED) {
					System.out.println("[试点线路CV关门流程] OD记录已创建:");
					System.out.println("   上车站点=" + record.getStationNameOn());
					System.out.println("   下车站点=" + record.getStationNameOff());
					System.out.println("   线路ID=" + record.getLineId());
					System.out.println("   方向=" + record.getRouteDirection());
					System.out.println("   ================================================================================");
				}

				// 收集该趟次该站点的所有乘客特征向量
				Set<String> features = jedis.smembers("features_set:" + busNo + ":" + windowId);
				if (features != null && !features.isEmpty()) {
					// 直接使用Redis中存储的完整特征信息（已包含方向、时间戳、图片、位置等）
					String passengerFeatures = new JSONArray(features).toString();
					record.setPassengerFeatures(passengerFeatures);
					if (Config.PILOT_ROUTE_LOG_ENABLED) {
						System.out.println("[试点线路CV关门流程] 乘客特征收集完成:");
						System.out.println("   特征数量=" + features.size());
						System.out.println("   ================================================================================");
					}
					if (Config.LOG_DEBUG) {
						System.out.println("[PassengerFlowProcessor] Collected " + features.size() + " passenger features for busNo=" + busNo);
					}
				}

				// 收集该趟次该站点的所有图片URL（从特征数据中提取）
				List<String> imageUrls = new ArrayList<>();
				if (features != null && !features.isEmpty()) {
					for (String featureStr : features) {
						try {
							JSONObject featureObj = new JSONObject(featureStr);
							String imageUrl = featureObj.optString("image");
							if (imageUrl != null && !imageUrl.isEmpty()) {
								imageUrls.add(imageUrl);
							}
						} catch (Exception e) {
							// 如果解析失败，跳过该特征
							if (Config.LOG_DEBUG) {
								System.out.println("[PassengerFlowProcessor] Failed to parse feature JSON for image extraction: " + featureStr);
							}
						}
					}
				}
				if (!imageUrls.isEmpty()) {
					String passengerImages = new JSONArray(imageUrls).toString();
					record.setPassengerImages(passengerImages);
					if (Config.PILOT_ROUTE_LOG_ENABLED) {
						System.out.println("[试点线路CV关门流程] 乘客图片收集完成:");
						System.out.println("   图片数量=" + imageUrls.size());
						System.out.println("   ================================================================================");
					}
					if (Config.LOG_DEBUG) {
						System.out.println("[PassengerFlowProcessor] Collected " + imageUrls.size() + " passenger images for busNo=" + busNo);
					}
				}

				// 收集该趟次该站点的所有乘客位置信息（从特征数据中提取）
				List<JSONObject> positions = new ArrayList<>();
				if (features != null && !features.isEmpty()) {
					for (String featureStr : features) {
						try {
							JSONObject featureObj = new JSONObject(featureStr);
							JSONObject position = featureObj.optJSONObject("position");
							if (position != null) {
								positions.add(position);
							}
						} catch (Exception e) {
							// 如果解析失败，跳过该特征
							if (Config.LOG_DEBUG) {
								System.out.println("[PassengerFlowProcessor] Failed to parse feature JSON for position extraction: " + featureStr);
							}
						}
					}
				}
				if (!positions.isEmpty()) {
					String passengerPosition = new JSONArray(positions).toString();
					record.setPassengerPosition(passengerPosition);
					if (Config.PILOT_ROUTE_LOG_ENABLED) {
						System.out.println("[试点线路CV关门流程] 乘客位置信息收集完成:");
						System.out.println("   位置信息数量=" + positions.size());
						System.out.println("   ================================================================================");
					}
					if (Config.LOG_DEBUG) {
						System.out.println("[PassengerFlowProcessor] Collected " + positions.size() + " passenger positions for busNo=" + busNo);
					}
				}

				if (Config.LOG_INFO) {
					System.out.println("[PassengerFlowProcessor] Created CLOSE DOOR OD record for busNo=" + busNo +
						", upCount=" + cvUpCount + ", downCount=" + cvDownCount +
						", features=" + (features != null ? features.size() : 0) +
						", images=" + imageUrls.size());
				}

				// 在关门时触发AI图片分析（但不发送到Kafka）
				if (Config.ENABLE_AI_IMAGE_ANALYSIS) {
					if (Config.PILOT_ROUTE_LOG_ENABLED) {
						System.out.println("[试点线路CV关门流程] 开始AI图片分析:");
						System.out.println("   启用AI分析=" + Config.ENABLE_AI_IMAGE_ANALYSIS);
						System.out.println("   最大图片数量=" + Config.MAX_IMAGES_PER_ANALYSIS);
						System.out.println("   ================================================================================");
					}

					try {
						analyzeImagesWithAI(jedis, busNo, eventTime, record);
					} catch (Exception e) {
						if (Config.LOG_ERROR) {
							System.err.println("[PassengerFlowProcessor] Error during AI image analysis: " + e.getMessage());
						}
						// AI分析失败时，仍然发送CV数据到Kafka
						if (Config.PILOT_ROUTE_LOG_ENABLED) {
							System.out.println("[试点线路CV关门流程] AI分析失败，发送CV数据到Kafka:");
							System.out.println("   错误信息=" + e.getMessage());
							System.out.println("   ================================================================================");
						}
						sendToKafka(record);
					}
				} else {
					// 如果没有启用AI分析，直接发送CV数据到Kafka
					if (Config.PILOT_ROUTE_LOG_ENABLED) {
						System.out.println("[试点线路CV关门流程] 未启用AI分析，直接发送CV数据到Kafka:");
						System.out.println("   ================================================================================");
					}
					sendToKafka(record);
				}

				// 清理开门时间窗口
				jedis.del("open_time:" + busNo);
				jedis.del("cv_up_count:" + busNo + ":" + windowId);
				jedis.del("cv_down_count:" + busNo + ":" + windowId);

				if (Config.PILOT_ROUTE_LOG_ENABLED) {
					System.out.println("[试点线路CV关门流程] 开门时间窗口已清理:");
					System.out.println("   ================================================================================");
				}

				// 移除清理窗口调试日志
			}
		}

		// 移除关门事件处理完成日志
	}

	private BusOdRecord createBaseRecord(String busNo, String cameraNo, LocalDateTime time, Jedis jedis) {
		BusOdRecord record = new BusOdRecord();
		record.setDate(LocalDate.now());
		record.setTimestampBegin(time);
		record.setBusNo(busNo);
		record.setCameraNo(cameraNo);
		record.setLineId(getLineIdFromBusNo(busNo, jedis));
		record.setRouteDirection(getRouteDirectionFromBusNo(busNo, jedis));
		record.setGpsLat(getGpsLat(busNo, jedis));
		record.setGpsLng(getGpsLng(busNo, jedis));
		record.setFullLoadRate(getFullLoadRateFromRedis(jedis, busNo));
		record.setTicketCount(getTicketCountWindowFromRedis(jedis, busNo));
		record.setCurrentStationName(getCurrentStationName(busNo, jedis));

		// 设置车辆总人数（来自CV系统满载率推送）
		record.setVehicleTotalCount(getVehicleTotalCountFromRedis(jedis, busNo));

		// 不再设置tripTotalCount

		Long busId = getBusIdFromRedis(jedis, busNo);
		if (busId != null) record.setBusId(busId);
		return record;
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
		String gpsStr = jedis.get("gps:" + busNo);
		if (gpsStr != null) {
			String trafficType = new JSONObject(gpsStr).optString("trafficType");
			switch (trafficType) {
				case "4": return "up";      // 上行
				case "5": return "down";    // 下行
				case "6": return "circular"; // 环形
				default: return "unknown";
			}
		}
		return "unknown";
	}

	private String getCurrentStationId(String busNo, Jedis jedis) {
		String arriveLeaveStr = jedis.get("arrive_leave:" + busNo);
		if (arriveLeaveStr != null) {
			return new JSONObject(arriveLeaveStr).optString("stationId");
		}
		return "UNKNOWN";
	}

	private String getCurrentStationName(String busNo, Jedis jedis) {
		String arriveLeaveStr = jedis.get("arrive_leave:" + busNo);
		if (arriveLeaveStr != null) {
			return new JSONObject(arriveLeaveStr).optString("stationName");
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

	private int getTicketCountWindowFromRedis(Jedis jedis, String busNo) {
		String count = jedis.get("ticket_count_window:" + busNo);
		return count != null ? Integer.parseInt(count) : 0;
	}

	private int getVehicleTotalCountFromRedis(Jedis jedis, String busNo) {
		String count = jedis.get("vehicle_total_count:" + busNo);
		return count != null ? Integer.parseInt(count) : 0;
	}



	private BigDecimal getFullLoadRateFromRedis(Jedis jedis, String busNo) {
		String factor = jedis.get("load_factor:" + busNo);
		return factor != null ? new BigDecimal(factor) : BigDecimal.ZERO;
	}

	private float matchPassengerFeature(String feature, String busNo) {
		try (Jedis jedis = jedisPool.getResource()) {
			jedis.auth(Config.REDIS_PASSWORD);
			String windowId = jedis.get("open_time:" + busNo);
			if (windowId == null) return 0.0f;
			String key = "features_set:" + busNo + ":" + windowId;
			Set<String> features = jedis.smembers(key);
			double[] probe = parseFeatureVector(feature);
			double best = 0.0;
			for (String cand : features) {
				try {
					// 从JSON字符串中提取feature字段
					JSONObject featureObj = new JSONObject(cand);
					String featureValue = featureObj.optString("feature");
					if (featureValue != null && !featureValue.isEmpty()) {
						double[] vec = parseFeatureVector(featureValue);
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

	private JSONObject callMediaApi(List<String> imageList, String videoPath, String prompt) throws IOException {
		try (CloseableHttpClient client = HttpClients.createDefault()) {
			HttpPost post = new HttpPost(Config.MEDIA_API);
			post.setHeader("Content-Type", "application/json");
			JSONObject payload = new JSONObject();
			if (imageList != null && !imageList.isEmpty()) {
				payload.put("image_path_list", new JSONArray(imageList));
			} else if (videoPath != null && !videoPath.isEmpty()) {
				payload.put("video_path", videoPath);
			}
			payload.put("system_prompt", prompt);
			StringEntity entity = new StringEntity(payload.toString(), "UTF-8");
			post.setEntity(entity);

			try (CloseableHttpResponse response = client.execute(post)) {
				String responseString = EntityUtils.toString(response.getEntity());
				// 屏蔽模型API原始响应日志
				return new JSONObject(responseString);
			}
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
		JSONObject mapping = new JSONObject();
		mapping.put("stationId", stationId);
		mapping.put("stationName", stationName);
		mapping.put("direction", direction); // 添加方向信息
		String key = "feature_station:" + feature;
		jedis.set(key, mapping.toString());
		jedis.expire(key, Config.REDIS_TTL_FEATURES);
		if (Config.LOG_DEBUG) {
			System.out.println("[PassengerFlowProcessor] Cache feature_station for feature hashLen=" + feature.length());
		}
	}

	private JSONObject getOnStationFromCache(Jedis jedis, String feature) {
		String mappingJson = jedis.get("feature_station:" + feature);
		if (mappingJson != null) {
			return new JSONObject(mappingJson);
		}
		return null;
	}

	private int getCachedDownCount(Jedis jedis, String busNo, String windowId) {
		if (windowId == null) return 0;
		String v = jedis.get("cv_down_count:" + busNo + ":" + windowId);
		return v != null ? Integer.parseInt(v) : 0;
	}

	private int getCachedUpCount(Jedis jedis, String busNo, String windowId) {
		String v = jedis.get("cv_up_count:" + busNo + ":" + windowId);
		return v != null ? Integer.parseInt(v) : 0;
	}

	private Long getBusIdFromRedis(Jedis jedis, String busNo) {
		String v = jedis.get("bus_id:" + busNo);
		if (v == null) return null;
		try { return Long.parseLong(v); } catch (Exception e) { return null; }
	}

	private double[] parseFeatureVector(String feature) {
		if (feature == null || feature.isEmpty()) return new double[0];
		try {
			JSONArray arr = new JSONArray(feature);
			double[] vec = new double[arr.length()];
			for (int i = 0; i < arr.length(); i++) vec[i] = arr.getDouble(i);
			return vec;
		} catch (Exception ignore) {}
		try {
			String[] parts = feature.split(",");
			double[] vec = new double[parts.length];
			for (int i = 0; i < parts.length; i++) vec[i] = Double.parseDouble(parts[i].trim());
			return vec;
		} catch (Exception ignore) {}
		return new double[0];
	}

	private void sendToKafka(Object data) {
		try {
			String json = objectMapper.writeValueAsString(data);

			// 试点线路最终流程日志 - 准备发送到Kafka（可通过配置控制）
			if (Config.PILOT_ROUTE_LOG_ENABLED) {
				System.out.println("[试点线路最终流程] 准备发送数据到Kafka:");
				System.out.println("   主题: " + KafkaConfig.PASSENGER_FLOW_TOPIC);
				System.out.println("   数据大小: " + json.length() + " 字符");
				System.out.println("   数据内容: " + json);
				System.out.println("   ================================================================================");
			}

			if (Config.FLOW_LOG_ENABLED && data instanceof BusOdRecord) {
				System.out.println("[发送BusOdRecord] topic=" + KafkaConfig.PASSENGER_FLOW_TOPIC + ", data=" + json);
			}

			if (Config.LOG_DEBUG) {
				System.out.println("[PassengerFlowProcessor] Send to Kafka topic=" + KafkaConfig.PASSENGER_FLOW_TOPIC + ", size=" + json.length());
			}

			// 使用回调来确认发送状态
			producer.send(new ProducerRecord<>(KafkaConfig.PASSENGER_FLOW_TOPIC, json),
				(metadata, exception) -> {
					if (exception != null) {
						// 试点线路最终流程日志 - Kafka发送失败（可通过配置控制）
						if (Config.PILOT_ROUTE_LOG_ENABLED) {
							System.out.println("[试点线路最终流程] Kafka发送失败:");
							System.out.println("   错误信息: " + exception.getMessage());
							System.out.println("   失败数据: " + json);
							System.out.println("   ================================================================================");
						}

						if (Config.LOG_ERROR) {
							System.err.println("[发送失败] BusOdRecord发送Kafka失败: " + exception.getMessage());
						}
						// 可以在这里添加重试逻辑或告警机制
						handleKafkaSendFailure(data, exception);
					} else {
						// 试点线路最终流程日志 - Kafka发送成功（可通过配置控制）
						if (Config.PILOT_ROUTE_LOG_ENABLED) {
							System.out.println("[试点线路最终流程] Kafka发送成功:");
							System.out.println("   主题: " + metadata.topic());
							System.out.println("   分区: " + metadata.partition());
							System.out.println("   偏移量: " + metadata.offset());
							System.out.println("   时间戳: " + metadata.timestamp());
							System.out.println("   ================================================================================");
							System.out.println("[试点线路完整流程] 从开关门信号到Kafka发送的整个流程已完成!");
							System.out.println("   ================================================================================");
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
	 * 处理base64图片：解码为文件并上传到OSS
	 */
	private String processBase64Image(String base64Image, String busNo, String cameraNo, LocalDateTime eventTime) throws IOException {
		// 1. 将base64图片解码为文件
		File imageFile = decodeBase64ToFile(base64Image);

		// 2. 上传到OSS获取URL
		String fileName = String.format("cv_%s_%s_%s_%s.jpg",
			busNo, cameraNo, eventTime.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")), UUID.randomUUID().toString().substring(0, 8));

		String imageUrl = OssUtil.uploadFile(imageFile, fileName);

		// 清理临时文件
		imageFile.delete();

		return imageUrl;
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
     */
    private void cacheImageUrl(Jedis jedis, String busNo, String windowId, String imageUrl, String direction) {
        if (windowId != null) {
            String imageUrlsKey = "image_urls:" + busNo + ":" + windowId + ":" + direction;
            jedis.sadd(imageUrlsKey, imageUrl);
            jedis.expire(imageUrlsKey, Config.REDIS_TTL_OPEN_TIME);

            if (Config.LOG_DEBUG) {
                System.out.println("[PassengerFlowProcessor] Cached image URL for " + direction + ", busNo=" + busNo + ", windowId=" + windowId);
            }
        }
    }

	    /**
     * 使用图片列表调用AI模型进行分析，增强现有的OD记录
     */
    private void analyzeImagesWithAI(Jedis jedis, String busNo, LocalDateTime timeWindow, BusOdRecord record) throws IOException, SQLException {
        // 检查是否启用AI图片分析
        if (!Config.ENABLE_AI_IMAGE_ANALYSIS) {
            if (Config.LOG_DEBUG) {
                System.out.println("[PassengerFlowProcessor] AI image analysis is disabled");
            }
            return;
        }

        // 获取当前开门时间窗口ID
        String windowId = jedis.get("open_time:" + busNo);
        if (windowId == null) {
            if (Config.LOG_DEBUG) {
                System.out.println("[PassengerFlowProcessor] No open window found for busNo=" + busNo + ", skipping AI analysis");
            }
            return;
        }

        // 从特征数据中收集图片URL列表
        List<String> imageUrls = new ArrayList<>();
        Set<String> features = jedis.smembers("features_set:" + busNo + ":" + windowId);
        if (features != null && !features.isEmpty()) {
            for (String featureStr : features) {
                try {
                    JSONObject featureObj = new JSONObject(featureStr);
                    String imageUrl = featureObj.optString("image");
                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        imageUrls.add(imageUrl);
                    }
                } catch (Exception e) {
                    // 如果解析失败，跳过该特征
                    if (Config.LOG_DEBUG) {
                        System.out.println("[PassengerFlowProcessor] Failed to parse feature JSON for AI analysis: " + featureStr);
                    }
                }
            }
        }

        if (imageUrls.isEmpty()) {
            if (Config.LOG_DEBUG) {
                System.out.println("[PassengerFlowProcessor] No images to analyze for busNo=" + busNo);
            }
            return;
        }

        // 限制图片数量，避免AI模型处理过多图片
        if (imageUrls.size() > Config.MAX_IMAGES_PER_ANALYSIS) {
            if (Config.LOG_DEBUG) {
                System.out.println("[PassengerFlowProcessor] Limiting images from " + imageUrls.size() + " to " + Config.MAX_IMAGES_PER_ANALYSIS);
            }
            imageUrls = imageUrls.subList(0, Config.MAX_IMAGES_PER_ANALYSIS);
        }

        		// 调用大模型分析图片
		JSONObject modelResponse = callMediaApi(imageUrls, null, Config.PASSENGER_PROMPT);
		JSONObject responseObj = modelResponse.optJSONObject("response");
		JSONArray passengerFeatures = responseObj != null ? responseObj.optJSONArray("passenger_features") : new JSONArray();

		// 解析大模型识别的上下车人数
		int aiUpCount = 0;
		int aiDownCount = 0;
		if (responseObj != null) {
			// 如果大模型能区分上下车，则分别获取；否则根据当前上下车事件类型设置
			aiUpCount = responseObj.optInt("up_count", 0);
			aiDownCount = responseObj.optInt("down_count", 0);
		}

		if (Config.LOG_DEBUG) {
			System.out.println("[PassengerFlowProcessor] AI analysis result - ai_up_count=" + aiUpCount +
				", ai_down_count=" + aiDownCount + ", features_len=" + passengerFeatures.length());
		}

		// 增强现有记录，设置大模型识别的上下车人数
		record.setFeatureDescription(passengerFeatures.toString());
		record.setAiUpCount(aiUpCount);
		record.setAiDownCount(aiDownCount);

        if (Config.LOG_INFO) {
            System.out.println("[PassengerFlowProcessor] Enhanced OD record with AI analysis for busNo=" + busNo);
        }

        // 发送增强后的记录到Kafka（只发送一次）
        sendToKafka(record);
    }
}
