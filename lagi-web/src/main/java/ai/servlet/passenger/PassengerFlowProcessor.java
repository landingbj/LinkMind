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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

		if (Config.PILOT_ROUTE_LOG_ENABLED) {
			System.out.println("[流程] downup事件开始: busNo=" + busNo + ", 事件数=" + (events != null ? events.length() : 0));
		}

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
					if (Config.PILOT_ROUTE_LOG_ENABLED) {
						System.out.println("[流程] 开始处理图片(base64->文件->OSS): busNo=" + busNo + ", cameraNo=" + cameraNo);
					}
					imageUrl = processBase64Image(image, busNo, cameraNo, eventTime);
					if (Config.PILOT_ROUTE_LOG_ENABLED) {
						System.out.println("[流程] 图片上传完成，得到URL");
					}
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
				positionInfo.put("position", positionInfo);
				jedis.set(positionKey, positionInfo.toString());
				jedis.expire(positionKey, Config.REDIS_TTL_FEATURES);

			} else if ("down".equals(direction)) {
				downCount++;

				// 尝试匹配上车特征，计算区间客流
				processPassengerMatching(feature, busNo, jedis, eventTime);

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
			}
		}

		// 不再在downup事件中自算总人数，统一以CV推送的vehicle_total_count为准

		// 汇总日志可按需开启，默认关闭
		if (Config.PILOT_ROUTE_LOG_ENABLED) {
			System.out.println("[流程] downup事件结束: busNo=" + busNo + ", upCount=" + upCount + ", downCount=" + downCount);
		}
	}

	/**
	 * 处理乘客特征向量匹配，计算区间客流
	 * @param downFeature 下车特征向量
	 * @param busNo 公交车编号
	 * @param jedis Redis连接
	 * @param eventTime 事件时间
	 */
	private void processPassengerMatching(String downFeature, String busNo, Jedis jedis, LocalDateTime eventTime) {
		try {
			String windowId = jedis.get("open_time:" + busNo);
			if (windowId == null) return;

			// 获取上车特征集合
			String featuresKey = "features_set:" + busNo + ":" + windowId;
			Set<String> features = jedis.smembers(featuresKey);
			
			if (features == null || features.isEmpty()) return;

			double[] downFeatureVec = CosineSimilarity.parseFeatureVector(downFeature);
			if (downFeatureVec.length == 0) return;

			// 遍历上车特征，寻找匹配
			for (String featureStr : features) {
				try {
					JSONObject featureObj = new JSONObject(featureStr);
					String direction = featureObj.optString("direction");
					
					// 只处理上车特征
					if (!"up".equals(direction)) continue;
					
					String upFeature = featureObj.optString("feature");
					double[] upFeatureVec = CosineSimilarity.parseFeatureVector(upFeature);
					
					if (upFeatureVec.length > 0) {
						// 使用余弦相似度计算匹配度
						double similarity = CosineSimilarity.cosine(downFeatureVec, upFeatureVec);
						
						// 相似度大于0.5认为是同一乘客
						if (similarity > 0.5) {
							// 获取上车站点信息
							JSONObject onStation = getOnStationFromCache(jedis, upFeature);
							if (onStation != null) {
								// 获取当前下车站点信息
								String currentStationId = getCurrentStationId(busNo, jedis);
								String currentStationName = getCurrentStationName(busNo, jedis);
								
								// 更新区间客流统计
								updateSectionPassengerFlow(jedis, busNo, windowId, 
									onStation.optString("stationId"), 
									onStation.optString("stationName"),
									currentStationId, 
									currentStationName);
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
	 */
	private void updateSectionPassengerFlow(Jedis jedis, String busNo, String windowId, 
										  String stationIdOn, String stationNameOn,
										  String stationIdOff, String stationNameOff) {
		try {
			String flowKey = "section_flow:" + busNo + ":" + windowId;
			
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
			}
			
			// 增加客流数
			int currentCount = sectionFlow.optInt("passengerFlowCount", 0);
			sectionFlow.put("passengerFlowCount", currentCount + 1);
			
			// 更新Redis
			jedis.hset(flowKey, sectionKey, sectionFlow.toString());
			jedis.expire(flowKey, Config.REDIS_TTL_OPEN_TIME);
			
		} catch (Exception e) {
			if (Config.LOG_ERROR) {
				System.err.println("[PassengerFlowProcessor] Error updating section passenger flow: " + e.getMessage());
			}
		}
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

			// 开门时创建记录并缓存（不再设置单独的站点字段，使用区间客流统计）
			BusOdRecord record = createBaseRecord(busNo, cameraNo, eventTime, jedis);
			record.setTimestampBegin(eventTime);

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
			if (Config.PILOT_ROUTE_LOG_ENABLED) {
				System.out.println("[流程] 准备处理关门->生成并发送OD: busNo=" + busNo);
			}
			handleCloseDoorEvent(data, busNo, cameraNo, jedis);
		}
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

				// 设置区间客流统计
				setSectionPassengerFlowCount(record, jedis, busNo, windowId);

				// 处理图片转视频
				processImagesToVideo(record, jedis, busNo, windowId);

				// 设置乘客特征集合
				setPassengerFeatures(record, jedis, busNo, windowId);

				// 设置车辆总人数（从CV系统获取）
				record.setVehicleTotalCount(getVehicleTotalCountFromRedis(jedis, busNo));

				if (Config.PILOT_ROUTE_LOG_ENABLED) {
					System.out.println("[流程] 关门事件OD记录构建完成，准备发送Kafka");
				}
				sendToKafka(record);
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
	private void setSectionPassengerFlowCount(BusOdRecord record, Jedis jedis, String busNo, String windowId) {
		try {
			String flowKey = "section_flow:" + busNo + ":" + windowId;
			Map<String, String> sectionFlows = jedis.hgetAll(flowKey);
			
			if (sectionFlows != null && !sectionFlows.isEmpty()) {
				JSONArray sectionFlowArray = new JSONArray();
				
				for (String sectionKey : sectionFlows.keySet()) {
					String flowJson = sectionFlows.get(sectionKey);
					JSONObject flowObj = new JSONObject(flowJson);
					sectionFlowArray.put(flowObj);
				}
				
				record.setSectionPassengerFlowCount(sectionFlowArray.toString());
				
				if (Config.PILOT_ROUTE_LOG_ENABLED) {
					System.out.println("[流程] 区间客流统计设置完成，区间数: " + sectionFlowArray.length());
				}
			}
		} catch (Exception e) {
			if (Config.LOG_ERROR) {
				System.err.println("[PassengerFlowProcessor] Error setting section passenger flow count: " + e.getMessage());
			}
		}
	}

	/**
	 * 处理图片转视频功能
	 * @param record BusOdRecord记录
	 * @param jedis Redis连接
	 * @param busNo 公交车编号
	 * @param windowId 时间窗口ID
	 */
	private void processImagesToVideo(BusOdRecord record, Jedis jedis, String busNo, String windowId) {
		try {
			// 获取所有图片URL
			List<String> imageUrls = getAllImageUrls(jedis, busNo, windowId);
			
			if (imageUrls != null && !imageUrls.isEmpty()) {
				if (Config.PILOT_ROUTE_LOG_ENABLED) {
					System.out.println("[流程] 开始处理图片转视频，图片数量: " + imageUrls.size());
				}
				
				// 设置图片URL集合
				JSONArray imageArray = new JSONArray();
				for (String imageUrl : imageUrls) {
					imageArray.put(imageUrl);
				}
				record.setPassengerImages(imageArray.toString());
				
				// 转换为视频
				try {
					String tempDir = System.getProperty("java.io.tmpdir");
					File videoFile = ImageToVideoConverter.convertImagesToVideo(imageUrls, tempDir);
					
					// 生成动态目录名（基于开关门事件）
					String dynamicDir = "PassengerFlowRecognition/" + windowId;
					
					// 上传视频到OSS（使用视频配置）
					String videoUrl = OssUtil.uploadVideoFile(videoFile, UUID.randomUUID().toString() + ".mp4", dynamicDir);
					record.setPassengerVideoUrl(videoUrl);
					
					// 删除临时视频文件
					videoFile.delete();
					
					if (Config.PILOT_ROUTE_LOG_ENABLED) {
						System.out.println("[流程] 图片转视频完成，视频URL: " + videoUrl);
					}
				} catch (Exception e) {
					if (Config.LOG_ERROR) {
						System.err.println("[PassengerFlowProcessor] Error converting images to video: " + e.getMessage());
					}
				}
			}
		} catch (Exception e) {
			if (Config.LOG_ERROR) {
				System.err.println("[PassengerFlowProcessor] Error processing images to video: " + e.getMessage());
			}
		}
	}

	/**
	 * 获取所有图片URL
	 * @param jedis Redis连接
	 * @param busNo 公交车编号
	 * @param windowId 时间窗口ID
	 * @return 图片URL列表
	 */
	private List<String> getAllImageUrls(Jedis jedis, String busNo, String windowId) {
		List<String> imageUrls = new ArrayList<>();
		
		try {
			// 获取上车图片URL
			String upImagesKey = "image_urls:" + busNo + ":" + windowId + ":up";
			Set<String> upImages = jedis.smembers(upImagesKey);
			if (upImages != null) {
				imageUrls.addAll(upImages);
			}
			
			// 获取下车图片URL
			String downImagesKey = "image_urls:" + busNo + ":" + windowId + ":down";
			Set<String> downImages = jedis.smembers(downImagesKey);
			if (downImages != null) {
				imageUrls.addAll(downImages);
			}
		} catch (Exception e) {
			if (Config.LOG_ERROR) {
				System.err.println("[PassengerFlowProcessor] Error getting image URLs: " + e.getMessage());
			}
		}
		
		return imageUrls;
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
			double[] probe = CosineSimilarity.parseFeatureVector(feature);
			double best = 0.0;
			for (String cand : features) {
				try {
					// 从JSON字符串中提取feature字段
					JSONObject featureObj = new JSONObject(cand);
					String featureValue = featureObj.optString("feature");
					if (featureValue != null && !featureValue.isEmpty()) {
						double[] vec = CosineSimilarity.parseFeatureVector(featureValue);
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

	/**
	 * 设置乘客特征集合信息
	 * @param record BusOdRecord记录
	 * @param jedis Redis连接
	 * @param busNo 公交车编号
	 * @param windowId 时间窗口ID
	 */
	private void setPassengerFeatures(BusOdRecord record, Jedis jedis, String busNo, String windowId) {
		try {
			String featuresKey = "features_set:" + busNo + ":" + windowId;
			Set<String> features = jedis.smembers(featuresKey);
			
			if (features != null && !features.isEmpty()) {
				JSONArray featuresArray = new JSONArray();
				for (String featureStr : features) {
					featuresArray.put(new JSONObject(featureStr));
				}
				
				record.setPassengerFeatures(featuresArray.toString());
				
				if (Config.PILOT_ROUTE_LOG_ENABLED) {
					System.out.println("[流程] 乘客特征集合设置完成，特征数: " + featuresArray.length());
				}
			}
		} catch (Exception e) {
			if (Config.LOG_ERROR) {
				System.err.println("[PassengerFlowProcessor] Error setting passenger features: " + e.getMessage());
			}
		}
	}
}
