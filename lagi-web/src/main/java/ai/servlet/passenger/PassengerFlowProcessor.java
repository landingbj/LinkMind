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
	private final BusOdRecordDao busOdRecordDao = new BusOdRecordDao();
	private KafkaProducer<String, String> producer;

	public PassengerFlowProcessor() {
		Properties props = KafkaConfig.getProducerProperties();
		producer = new KafkaProducer<>(props);
	}

	public void processEvent(JSONObject eventJson) {
		String event = eventJson.optString("event");
		JSONObject data = eventJson.optJSONObject("data");
		System.out.println("[PassengerFlowProcessor] Receive event=" + event + ", payload=" + eventJson);
		if (data == null) return;

		String busNo = data.optString("bus_no");
		String cameraNo = data.optString("camera_no");

		try (Jedis jedis = jedisPool.getResource()) {
			jedis.auth(Config.REDIS_PASSWORD);

			switch (event) {
				case "downup":
					System.out.println("[PassengerFlowProcessor] Handle downup, busNo=" + busNo);
					handleDownUpEvent(data, busNo, cameraNo, jedis);
					break;
				case "load_factor":
					System.out.println("[PassengerFlowProcessor] Handle load_factor, busNo=" + busNo);
					handleLoadFactorEvent(data, busNo, jedis);
					break;
				case "open_close_door":
					System.out.println("[PassengerFlowProcessor] Handle open_close_door, busNo=" + busNo);
					handleOpenCloseDoorEvent(data, busNo, cameraNo, jedis);
					break;
				case "notify_pull_file":
					System.out.println("[PassengerFlowProcessor] Handle notify_pull_file, busNo=" + busNo);
					handleNotifyPullFileEvent(data, busNo, cameraNo, jedis);
					break;
				default:
					System.err.println("[PassengerFlowProcessor] Unknown event: " + event);
			}
		} catch (Exception e) {
			System.err.println("[PassengerFlowProcessor] Process event error: " + e.getMessage());
		}
	}

	private void handleDownUpEvent(JSONObject data, String busNo, String cameraNo, Jedis jedis) throws IOException, SQLException {
		LocalDateTime eventTime = LocalDateTime.parse(data.optString("timestamp").replace(" ", "T"));
		JSONArray events = data.optJSONArray("events");

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

			BusOdRecord record = createBaseRecord(busNo, cameraNo, eventTime, jedis);
			record.setPassengerVector(feature);
			record.setCountingImage(image);
			record.setPassengerPosition(String.format("[{\"xLeftUp\":%d,\"yLeftUp\":%d,\"xRightBottom\":%d,\"yRightBottom\":%d}]",
					boxX, boxY, boxX + boxW, boxY + boxH));
			record.setDataSource("CV");

			if ("up".equals(direction)) {
				upCount++;
				record.setStationIdOn(getCurrentStationId(busNo, jedis));
				record.setStationNameOn(getCurrentStationName(busNo, jedis));
				record.setUpCount(1);
				cacheFeatureStationMapping(jedis, feature, record.getStationIdOn(), record.getStationNameOn());
				String windowId = jedis.get("open_time:" + busNo);
				if (windowId != null) {
					jedis.sadd("features_set:" + busNo + ":" + windowId, feature);
					jedis.incr("cv_up_count:" + busNo + ":" + windowId);
					System.out.println("[PassengerFlowProcessor] Cache UP feature and count, busNo=" + busNo + ", windowId=" + windowId);
				}
			} else {
				downCount++;
				record.setStationIdOff(getCurrentStationId(busNo, jedis));
				record.setStationNameOff(getCurrentStationName(busNo, jedis));
				record.setDownCount(1);

				float similarity = matchPassengerFeature(feature, busNo);
				System.out.println("[PassengerFlowProcessor] DOWN similarity=" + similarity + ", busNo=" + busNo);
				if (similarity > 0.8f) {
					JSONObject onStation = getOnStationFromCache(jedis, feature);
					if (onStation != null) {
						record.setStationIdOn(onStation.optString("stationId"));
						record.setStationNameOn(onStation.optString("stationName"));
					}
				}
				String windowId = jedis.get("open_time:" + busNo);
				if (windowId != null) {
					jedis.incr("cv_down_count:" + busNo + ":" + windowId);
					System.out.println("[PassengerFlowProcessor] Cache DOWN count, busNo=" + busNo + ", windowId=" + windowId);
				}
			}

			odRecords.add(record);
			busOdRecordDao.save(record);
			System.out.println("[PassengerFlowProcessor] Saved OD record to DB, busNo=" + busNo + ", direction=" + direction);
		}

		// 更新总计数
		int totalCount = getTotalCountFromRedis(jedis, busNo) + upCount - downCount;
		jedis.set("total_count:" + busNo, String.valueOf(totalCount));
		System.out.println("[PassengerFlowProcessor] Update total_count to " + totalCount + ", busNo=" + busNo);

		// 发送到Kafka
		sendToKafka(odRecords);
	}

	private void handleLoadFactorEvent(JSONObject data, String busNo, Jedis jedis) {
		int count = data.optInt("count");
		double factor = data.optDouble("factor");

		// 缓存满载率
		jedis.set("load_factor:" + busNo, String.valueOf(factor));
		jedis.set("total_count:" + busNo, String.valueOf(count));
		System.out.println("[PassengerFlowProcessor] Cache load_factor=" + factor + ", total_count=" + count + ", busNo=" + busNo);
	}

	private void handleOpenCloseDoorEvent(JSONObject data, String busNo, String cameraNo, Jedis jedis) throws IOException {
		String action = data.optString("action");
		LocalDateTime eventTime = LocalDateTime.parse(data.optString("timestamp").replace(" ", "T"));

		BusOdRecord record = createBaseRecord(busNo, cameraNo, eventTime, jedis);

		if ("open".equals(action)) {
			record.setTimestampBegin(eventTime);
			record.setStationIdOn(getCurrentStationId(busNo, jedis));
			record.setStationNameOn(getCurrentStationName(busNo, jedis));
		} else {
			record.setTimestampEnd(eventTime);
			record.setStationIdOff(getCurrentStationId(busNo, jedis));
			record.setStationNameOff(getCurrentStationName(busNo, jedis));
		}

		cacheOdRecord(jedis, record);
		System.out.println("[PassengerFlowProcessor] Cache OD window record, busNo=" + busNo + ", action=" + action);
	}

	private void handleNotifyPullFileEvent(JSONObject data, String busNo, String cameraNo, Jedis jedis) throws IOException, SQLException {
		LocalDateTime begin = LocalDateTime.parse(data.optString("timestamp_begin").replace(" ", "T"));
		LocalDateTime end = LocalDateTime.parse(data.optString("timestamp_end").replace(" ", "T"));
		String fileUrl = data.optString("fileurl");

		// 下载视频
		File videoFile = downloadFile(fileUrl);
		String ossUrl = OssUtil.uploadFile(videoFile, UUID.randomUUID().toString() + ".mp4");
		System.out.println("[PassengerFlowProcessor] Video downloaded and uploaded to OSS, url=" + ossUrl);

		// 调用多模态模型（视频）
		JSONObject modelResponse = callMediaApi(null, ossUrl, Config.PASSENGER_PROMPT);
		JSONObject responseObj = modelResponse.optJSONObject("response");
		JSONArray passengerFeatures = responseObj != null ? responseObj.optJSONArray("passenger_features") : new JSONArray();
		int modelTotalCount = responseObj != null ? responseObj.optInt("total_count") : 0;
		System.out.println("[PassengerFlowProcessor] Model total_count=" + modelTotalCount + ", features_len=" + passengerFeatures.length());

		BusOdRecord record = createBaseRecord(busNo, cameraNo, begin, jedis);
		record.setTimestampEnd(end);
		record.setFeatureDescription(passengerFeatures.toString());
		record.setTotalCount(modelTotalCount);
		record.setDataSource("MODEL");

		// 校验CV结果
		int cvDownCount = getCachedDownCount(jedis, busNo, begin);
		System.out.println("[PassengerFlowProcessor] CV down_count in window=" + cvDownCount);
		if (Math.abs(modelTotalCount - cvDownCount) > 2) {
			record.setDownCount(modelTotalCount);
			System.out.println("[PassengerFlowProcessor] Use model down_count due to deviation");
		} else {
			record.setDownCount(cvDownCount);
		}

		busOdRecordDao.save(record);
		System.out.println("[PassengerFlowProcessor] Saved MODEL OD record to DB, busNo=" + busNo);
		sendToKafka(record);
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

	private void cacheOdRecord(Jedis jedis, BusOdRecord record) throws IOException {
		String key = "od_record:" + record.getBusNo() + ":" + record.getTimestampBegin();
		jedis.set(key, objectMapper.writeValueAsString(record));
		System.out.println("[PassengerFlowProcessor] Cache od_record key=" + key);
	}

	private void cacheFeatureStationMapping(Jedis jedis, String feature, String stationId, String stationName) {
		JSONObject mapping = new JSONObject();
		mapping.put("stationId", stationId);
		mapping.put("stationName", stationName);
		jedis.set("feature_station:" + feature, mapping.toString());
		System.out.println("[PassengerFlowProcessor] Cache feature_station for feature hashLen=" + feature.length());
	}

	private JSONObject getOnStationFromCache(Jedis jedis, String feature) {
		String mappingJson = jedis.get("feature_station:" + feature);
		if (mappingJson != null) {
			return new JSONObject(mappingJson);
		}
		return null;
	}

	private int getCachedDownCount(Jedis jedis, String busNo, LocalDateTime begin) {
		String windowId = jedis.get("open_time:" + busNo);
		if (windowId == null) return 0;
		String v = jedis.get("cv_down_count:" + busNo + ":" + windowId);
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
			System.out.println("[PassengerFlowProcessor] Send to Kafka topic=" + KafkaConfig.PASSENGER_FLOW_TOPIC + ", size=" + json.length());
			producer.send(new ProducerRecord<>(KafkaConfig.PASSENGER_FLOW_TOPIC, json));
		} catch (Exception e) {
			System.err.println("[PassengerFlowProcessor] Send to Kafka error: " + e.getMessage());
		}
	}

	public void close() {
		if (producer != null) producer.close();
	}
}