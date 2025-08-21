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
		
		if (Config.LOG_INFO) {
			System.out.println("📥 [PassengerFlowProcessor] 收到CV系统事件:");
			System.out.println("   事件类型: " + event);
			System.out.println("   事件数据: " + eventJson.toString());
			System.out.println("   时间: " + LocalDateTime.now().format(formatter));
		}
		
		if (Config.LOG_DEBUG) {
			System.out.println("[PassengerFlowProcessor] Receive event=" + event + ", payload=" + eventJson);
		}
		
		if (data == null) {
			if (Config.LOG_ERROR) {
				System.err.println("❌ [PassengerFlowProcessor] 事件数据为空，无法处理事件: " + event);
			}
			return;
		}

		String busNo = data.optString("bus_no");
		String cameraNo = data.optString("camera_no");

		if (Config.LOG_DEBUG) {
			System.out.println("[PassengerFlowProcessor] 解析事件参数: busNo=" + busNo + ", cameraNo=" + cameraNo);
		}

		try (Jedis jedis = jedisPool.getResource()) {
			jedis.auth(Config.REDIS_PASSWORD);

			switch (event) {
				case "downup":
					if (Config.LOG_INFO) {
						System.out.println("👥 [PassengerFlowProcessor] 处理上下车事件: busNo=" + busNo + ", cameraNo=" + cameraNo);
					}
					if (Config.LOG_DEBUG) {
						System.out.println("[PassengerFlowProcessor] Handle downup, busNo=" + busNo);
					}
					handleDownUpEvent(data, busNo, cameraNo, jedis);
					break;
				case "load_factor":
					if (Config.LOG_INFO) {
						System.out.println("📊 [PassengerFlowProcessor] 处理载客率事件: busNo=" + busNo + ", cameraNo=" + cameraNo);
					}
					if (Config.LOG_DEBUG) {
						System.out.println("[PassengerFlowProcessor] Handle load_factor, busNo=" + busNo);
					}
					handleLoadFactorEvent(data, busNo, jedis);
					break;
				case "open_close_door":
					if (Config.LOG_INFO) {
						System.out.println("🚪 [PassengerFlowProcessor] 处理开关门事件: busNo=" + busNo + ", cameraNo=" + cameraNo);
					}
					if (Config.LOG_DEBUG) {
						System.out.println("[PassengerFlowProcessor] Handle open_close_door, busNo=" + busNo);
					}
					handleOpenCloseDoorEvent(data, busNo, cameraNo, jedis);
					break;
				case "notify_pull_file":
					if (Config.LOG_INFO) {
						System.out.println("📁 [PassengerFlowProcessor] 处理文件拉取通知: busNo=" + busNo + ", cameraNo=" + cameraNo);
					}
					if (Config.LOG_DEBUG) {
						System.out.println("[PassengerFlowProcessor] Handle notify_pull_file, busNo=" + busNo);
					}
					handleNotifyPullFileEvent(data, busNo, cameraNo, jedis);
					break;
				default:
					if (Config.LOG_ERROR) {
						System.err.println("❌ [PassengerFlowProcessor] 未知事件类型: " + event);
					}
			}
		} catch (Exception e) {
			if (Config.LOG_ERROR) {
				System.err.println("❌ [PassengerFlowProcessor] 处理事件时发生错误: " + e.getMessage());
				System.err.println("   事件类型: " + event);
				System.err.println("   事件数据: " + eventJson.toString());
			}
		}
	}

	private void handleDownUpEvent(JSONObject data, String busNo, String cameraNo, Jedis jedis) throws IOException, SQLException {
		LocalDateTime eventTime = LocalDateTime.parse(data.optString("timestamp").replace(" ", "T"));
		JSONArray events = data.optJSONArray("events");

		if (events == null || events.length() == 0) {
			if (Config.LOG_DEBUG) {
				System.out.println("[PassengerFlowProcessor] No events in downup data for busNo=" + busNo);
			}
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
					if (Config.LOG_DEBUG) {
						System.out.println("[PassengerFlowProcessor] Processed base64 image to URL: " + imageUrl);
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
				if (Config.LOG_DEBUG) {
					System.out.println("[PassengerFlowProcessor] No open window found for busNo=" + busNo + ", skipping event");
				}
				continue;
			}

			if ("up".equals(direction)) {
				upCount++;

				// 缓存上车特征和站点信息
				cacheFeatureStationMapping(jedis, feature, getCurrentStationId(busNo, jedis), getCurrentStationName(busNo, jedis));

				// 更新上车计数
				String cvUpCountKey = "cv_up_count:" + busNo + ":" + windowId;
				jedis.incr(cvUpCountKey);
				jedis.expire(cvUpCountKey, Config.REDIS_TTL_OPEN_TIME);

				// 缓存特征集合
				String featuresKey = "features_set:" + busNo + ":" + windowId;
				jedis.sadd(featuresKey, feature);
				jedis.expire(featuresKey, Config.REDIS_TTL_FEATURES);

				// 缓存图片URL
				if (imageUrl != null) {
					cacheImageUrl(jedis, busNo, windowId, imageUrl, "up");
				}

				if (Config.LOG_DEBUG) {
					System.out.println("[PassengerFlowProcessor] Processed UP event for busNo=" + busNo + ", windowId=" + windowId);
				}
			} else if ("down".equals(direction)) {
				downCount++;

				// 尝试匹配上车特征
				float similarity = matchPassengerFeature(feature, busNo);
				if (Config.LOG_DEBUG) {
					System.out.println("[PassengerFlowProcessor] DOWN similarity=" + similarity + ", busNo=" + busNo);
				}

				if (similarity > 0.8f) {
					JSONObject onStation = getOnStationFromCache(jedis, feature);
					if (onStation != null) {
						if (Config.LOG_DEBUG) {
							System.out.println("[PassengerFlowProcessor] Matched passenger feature, onStation=" + onStation.optString("stationName"));
						}
					}
				}

				// 更新下车计数
				String cvDownCountKey = "cv_down_count:" + busNo + ":" + windowId;
				jedis.incr(cvDownCountKey);
				jedis.expire(cvDownCountKey, Config.REDIS_TTL_OPEN_TIME);

				// 缓存图片URL
				if (imageUrl != null) {
					cacheImageUrl(jedis, busNo, windowId, imageUrl, "down");
				}

				if (Config.LOG_DEBUG) {
					System.out.println("[PassengerFlowProcessor] Processed DOWN event for busNo=" + busNo + ", windowId=" + windowId);
				}
			}
		}

		// 更新总计数
		int totalCount = getTotalCountFromRedis(jedis, busNo) + upCount - downCount;
		String totalCountKey = "total_count:" + busNo;
		jedis.set(totalCountKey, String.valueOf(totalCount));
		jedis.expire(totalCountKey, Config.REDIS_TTL_COUNTS);

		if (Config.LOG_INFO) {
			System.out.println("[PassengerFlowProcessor] Processed downup events for busNo=" + busNo +
				", upCount=" + upCount + ", downCount=" + downCount + ", totalCount=" + totalCount);
		}
	}

	private void handleLoadFactorEvent(JSONObject data, String busNo, Jedis jedis) {
		int count = data.optInt("count");
		double factor = data.optDouble("factor");

		// 缓存满载率，设置过期时间
		String loadFactorKey = "load_factor:" + busNo;
		String totalCountKey = "total_count:" + busNo;
		jedis.set(loadFactorKey, String.valueOf(factor));
		jedis.set(totalCountKey, String.valueOf(count));
		jedis.expire(loadFactorKey, Config.REDIS_TTL_COUNTS);
		jedis.expire(totalCountKey, Config.REDIS_TTL_COUNTS);
		if (Config.LOG_DEBUG) {
			System.out.println("[PassengerFlowProcessor] Cache load_factor=" + factor + ", total_count=" + count + ", busNo=" + busNo);
		}
	}

	private void handleOpenCloseDoorEvent(JSONObject data, String busNo, String cameraNo, Jedis jedis) throws IOException, SQLException {
		String action = data.optString("action");
		LocalDateTime eventTime = LocalDateTime.parse(data.optString("timestamp").replace(" ", "T"));

		if ("open".equals(action)) {
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

			if (Config.LOG_INFO) {
				System.out.println("[PassengerFlowProcessor] Door OPEN event processed for busNo=" + busNo + ", windowId=" + windowId);
			}
		} else if ("close".equals(action)) {
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
		if (Config.LOG_INFO) {
			System.out.println("[PassengerFlowProcessor] Video downloaded and uploaded to OSS, url=" + ossUrl);
		}

		// 调用多模态模型（视频）
		JSONObject modelResponse = callMediaApi(null, ossUrl, Config.PASSENGER_PROMPT);
		JSONObject responseObj = modelResponse.optJSONObject("response");
		JSONArray passengerFeatures = responseObj != null ? responseObj.optJSONArray("passenger_features") : new JSONArray();
		int modelTotalCount = responseObj != null ? responseObj.optInt("total_count") : 0;
		if (Config.LOG_DEBUG) {
			System.out.println("[PassengerFlowProcessor] Model total_count=" + modelTotalCount + ", features_len=" + passengerFeatures.length());
		}

		BusOdRecord record = createBaseRecord(busNo, cameraNo, begin, jedis);
		record.setTimestampEnd(end);
		record.setFeatureDescription(passengerFeatures.toString());
		record.setTotalCount(modelTotalCount);

		// 校验CV结果 - 这里暂时使用模型结果，因为notify_pull_file事件可能不在开门时间窗口内
		record.setDownCount(modelTotalCount);
		if (Config.LOG_DEBUG) {
			System.out.println("[PassengerFlowProcessor] Using model down_count for notify_pull_file event: " + modelTotalCount);
		}

		if (Config.LOG_INFO) {
			System.out.println("[PassengerFlowProcessor] Created MODEL OD record for busNo=" + busNo);
		}
		sendToKafka(record);
	}

	private void handleCloseDoorEvent(JSONObject data, String busNo, String cameraNo, Jedis jedis) throws IOException, SQLException {
		LocalDateTime eventTime = LocalDateTime.parse(data.optString("timestamp").replace(" ", "T"));
		String action = data.optString("action");

		if ("close".equals(action)) {
			// 获取开门时间窗口ID
			String windowId = jedis.get("open_time:" + busNo);
			if (windowId != null) {
				// 获取CV计数
				int cvUpCount = getCachedUpCount(jedis, busNo, windowId);
				int cvDownCount = getCachedDownCount(jedis, busNo, windowId);

				// 创建关门记录
				BusOdRecord record = createBaseRecord(busNo, cameraNo, eventTime, jedis);
				record.setTimestampEnd(eventTime);
				record.setUpCount(cvUpCount);
				record.setDownCount(cvDownCount);

				// 设置站点信息
				record.setStationIdOff(getCurrentStationId(busNo, jedis));
				record.setStationNameOff(getCurrentStationName(busNo, jedis));

				if (Config.LOG_INFO) {
					System.out.println("[PassengerFlowProcessor] Created CLOSE DOOR OD record for busNo=" + busNo +
						", upCount=" + cvUpCount + ", downCount=" + cvDownCount);
				}

				// 在关门时触发AI图片分析（但不发送到Kafka）
				if (Config.ENABLE_AI_IMAGE_ANALYSIS) {
					try {
						analyzeImagesWithAI(jedis, busNo, eventTime, record);
					} catch (Exception e) {
						if (Config.LOG_ERROR) {
							System.err.println("[PassengerFlowProcessor] Error during AI image analysis: " + e.getMessage());
						}
						// AI分析失败时，仍然发送CV数据到Kafka
						sendToKafka(record);
					}
				} else {
					// 如果没有启用AI分析，直接发送CV数据到Kafka
					sendToKafka(record);
				}

				// 清理开门时间窗口
				jedis.del("open_time:" + busNo);
				jedis.del("cv_up_count:" + busNo + ":" + windowId);
				jedis.del("cv_down_count:" + busNo + ":" + windowId);

				if (Config.LOG_DEBUG) {
					System.out.println("[PassengerFlowProcessor] Cleaned up open_time window for busNo=" + busNo);
				}
			}
		}

		if (Config.LOG_DEBUG) {
			System.out.println("[PassengerFlowProcessor] Close door event processed, busNo=" + busNo + ", action=" + action);
		}
	}

	private BusOdRecord createBaseRecord(String busNo, String cameraNo, LocalDateTime time, Jedis jedis) {
		BusOdRecord record = new BusOdRecord();
		record.setDate(LocalDate.now());
		record.setTimestampBegin(time);
		record.setBusNo(busNo);
		record.setCameraNo(cameraNo);
		record.setLineId(getLineIdFromBusNo(busNo, jedis));
		record.setDirection(getDirectionFromBusNo(busNo, jedis));
		record.setGpsLat(getGpsLat(busNo, jedis));
		record.setGpsLng(getGpsLng(busNo, jedis));
		record.setFullLoadRate(getFullLoadRateFromRedis(jedis, busNo));
		record.setTicketCount(getTicketCountWindowFromRedis(jedis, busNo));
		record.setCurrentStationName(getCurrentStationName(busNo, jedis));
		Long busId = getBusIdFromRedis(jedis, busNo);
		if (busId != null) record.setBusId(busId);
		return record;
	}

	private String getLineIdFromBusNo(String busNo, Jedis jedis) {
		String arriveLeaveStr = jedis.get("arrive_leave:" + busNo);
		if (arriveLeaveStr != null) {
			return new JSONObject(arriveLeaveStr).optString("srcAddrOrg", "UNKNOWN");
		}
		return "UNKNOWN";
	}

	private String getDirectionFromBusNo(String busNo, Jedis jedis) {
		String gpsStr = jedis.get("gps:" + busNo);
		if (gpsStr != null) {
			return new JSONObject(gpsStr).optString("direction");
		}
		return "up";
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

	private int getTotalCountFromRedis(Jedis jedis, String busNo) {
		String count = jedis.get("total_count:" + busNo);
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
				double[] vec = parseFeatureVector(cand);
				if (probe.length > 0 && vec.length == probe.length) {
					double sim = CosineSimilarity.cosine(probe, vec);
					if (sim > best) best = sim;
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
				System.out.println("[PassengerFlowProcessor] Media API response: " + responseString);
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



	private void cacheFeatureStationMapping(Jedis jedis, String feature, String stationId, String stationName) {
		JSONObject mapping = new JSONObject();
		mapping.put("stationId", stationId);
		mapping.put("stationName", stationName);
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
			
			if (Config.LOG_INFO) {
				System.out.println("📤 [PassengerFlowProcessor] 准备发送数据到Kafka:");
				System.out.println("   主题: " + KafkaConfig.PASSENGER_FLOW_TOPIC);
				System.out.println("   数据大小: " + json.length() + " 字符");
				System.out.println("   数据内容: " + json);
			}
			
			if (Config.LOG_DEBUG) {
				System.out.println("[PassengerFlowProcessor] Send to Kafka topic=" + KafkaConfig.PASSENGER_FLOW_TOPIC + ", size=" + json.length());
			}

			// 使用回调来确认发送状态
			producer.send(new ProducerRecord<>(KafkaConfig.PASSENGER_FLOW_TOPIC, json),
				(metadata, exception) -> {
					if (exception != null) {
						if (Config.LOG_ERROR) {
							System.err.println("❌ [PassengerFlowProcessor] Kafka发送失败: " + exception.getMessage());
							System.err.println("   失败数据: " + json);
						}
						// 可以在这里添加重试逻辑或告警机制
						handleKafkaSendFailure(data, exception);
					} else {
						if (Config.LOG_INFO) {
							System.out.println("✅ [PassengerFlowProcessor] Kafka发送成功:");
							System.out.println("   主题: " + metadata.topic());
							System.out.println("   分区: " + metadata.partition());
							System.out.println("   偏移量: " + metadata.offset());
							System.out.println("   时间戳: " + metadata.timestamp());
						}
						if (Config.LOG_DEBUG) {
							System.out.println("[PassengerFlowProcessor] Kafka send success: topic=" +
								metadata.topic() + ", partition=" + metadata.partition() +
								", offset=" + metadata.offset());
						}
						// 可以在这里添加发送成功的统计或监控
						handleKafkaSendSuccess(data, metadata);
					}
				});

		} catch (Exception e) {
			if (Config.LOG_ERROR) {
				System.err.println("❌ [PassengerFlowProcessor] 发送到Kafka时发生错误: " + e.getMessage());
				System.err.println("   错误数据: " + data);
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
     * 收集指定时间窗口内的图片URL列表
     */
    private List<String> collectImageUrlsInTimeWindow(Jedis jedis, String busNo, String windowId) {
        List<String> imageUrls = new ArrayList<>();

        if (windowId != null) {
            // 收集上车图片
            String upImagesKey = "image_urls:" + busNo + ":" + windowId + ":up";
            Set<String> upUrls = jedis.smembers(upImagesKey);
            if (upUrls != null) {
                imageUrls.addAll(upUrls);
            }

            // 收集下车图片
            String downImagesKey = "image_urls:" + busNo + ":" + windowId + ":down";
            Set<String> downUrls = jedis.smembers(downImagesKey);
            if (downUrls != null) {
                imageUrls.addAll(downUrls);
            }

            if (Config.LOG_DEBUG) {
                System.out.println("[PassengerFlowProcessor] Collected " + imageUrls.size() + " image URLs for busNo=" + busNo + ", windowId=" + windowId);
            }
        }

        return imageUrls;
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

        // 收集图片URL列表
        List<String> imageUrls = collectImageUrlsInTimeWindow(jedis, busNo, windowId);

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
        int modelTotalCount = responseObj != null ? responseObj.optInt("total_count") : 0;

        if (Config.LOG_DEBUG) {
            System.out.println("[PassengerFlowProcessor] AI analysis result - total_count=" + modelTotalCount + ", features_len=" + passengerFeatures.length());
        }

        // 增强现有记录，而不是创建新记录
        record.setFeatureDescription(passengerFeatures.toString());

        if (Config.LOG_INFO) {
            System.out.println("[PassengerFlowProcessor] Enhanced OD record with AI analysis for busNo=" + busNo);
        }

        // 发送增强后的记录到Kafka（只发送一次）
        sendToKafka(record);
    }
}
