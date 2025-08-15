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
        if (data == null) return;

        String busNo = data.optString("bus_no");
        String cameraNo = data.optString("camera_no");

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.auth(Config.REDIS_PASSWORD);

            switch (event) {
                case "downup":
                    handleDownUpEvent(data, busNo, cameraNo, jedis);
                    break;
                case "load_factor":
                    handleLoadFactorEvent(data, busNo, jedis);
                    break;
                case "open_close_door":
                    handleOpenCloseDoorEvent(data, busNo, cameraNo, jedis);
                    break;
                case "notify_pull_file":
                    handleNotifyPullFileEvent(data, busNo, cameraNo, jedis);
                    break;
                default:
                    System.err.println("Unknown event: " + event);
            }
        } catch (Exception e) {
            System.err.println("Process event error: " + e.getMessage());
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
            } else {
                downCount++;
                record.setStationIdOff(getCurrentStationId(busNo, jedis));
                record.setStationNameOff(getCurrentStationName(busNo, jedis));
                record.setDownCount(1);

                float similarity = matchPassengerFeature(feature, busNo);
                if (similarity > 0.8f) {
                    JSONObject onStation = getOnStationFromCache(jedis, feature);
                    if (onStation != null) {
                        record.setStationIdOn(onStation.optString("stationId"));
                        record.setStationNameOn(onStation.optString("stationName"));
                    }
                }
            }

            odRecords.add(record);
            busOdRecordDao.save(record);
        }

        // 更新总计数
        int totalCount = getTotalCountFromRedis(jedis, busNo) + upCount - downCount;
        jedis.set("total_count:" + busNo, String.valueOf(totalCount));

        // 发送到Kafka
        sendToKafka(odRecords);
    }

    private void handleLoadFactorEvent(JSONObject data, String busNo, Jedis jedis) {
        int count = data.optInt("count");
        double factor = data.optDouble("factor");

        // 缓存满载率
        jedis.set("load_factor:" + busNo, String.valueOf(factor));
        jedis.set("total_count:" + busNo, String.valueOf(count));
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
    }

    private void handleNotifyPullFileEvent(JSONObject data, String busNo, String cameraNo, Jedis jedis) throws IOException, SQLException {
        LocalDateTime begin = LocalDateTime.parse(data.optString("timestamp_begin").replace(" ", "T"));
        LocalDateTime end = LocalDateTime.parse(data.optString("timestamp_end").replace(" ", "T"));
        String fileUrl = data.optString("fileurl");

        // 下载视频
        File videoFile = downloadFile(fileUrl);
        String ossUrl = OssUtil.uploadFile(videoFile, UUID.randomUUID().toString() + ".mp4");

        // 调用大模型
        JSONObject modelResponse = callVideoUnderstandApi(ossUrl);
        JSONArray passengerFeatures = modelResponse.optJSONArray("passenger_features");
        int modelTotalCount = modelResponse.optInt("total_count");

        BusOdRecord record = createBaseRecord(busNo, cameraNo, begin, jedis);
        record.setTimestampEnd(end);
        record.setFeatureDescription(passengerFeatures.toString());
        record.setTotalCount(modelTotalCount);
        record.setDataSource("MODEL");

        // 校验CV结果
        int cvDownCount = getCachedDownCount(jedis, busNo, begin); // 从缓存获取CV下车数
        if (Math.abs(modelTotalCount - cvDownCount) > 2) { // 范围校验
            record.setDownCount(modelTotalCount); // 使用大模型修正
        } else {
            record.setDownCount(cvDownCount);
        }

        busOdRecordDao.save(record);
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
        record.setTripNo(getTripNoFromBusNo(busNo, jedis));
        record.setGpsLat(getGpsLat(busNo, jedis));
        record.setGpsLng(getGpsLng(busNo, jedis));
        record.setFullLoadRate(getFullLoadRateFromRedis(jedis, busNo));
        record.setTicketCount(getTicketCountFromRedis(jedis, busNo));
        return record;
    }

    private String getLineIdFromBusNo(String busNo, Jedis jedis) {
        String roadSheetStr = jedis.get("road_sheet:" + busNo);
        if (roadSheetStr != null) {
            return new JSONObject(roadSheetStr).optString("lineId");
        }
        String arriveLeaveStr = jedis.get("arrive_leave:" + busNo);
        if (arriveLeaveStr != null) {
            return new JSONObject(arriveLeaveStr).optString("srcAddrOrg");
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

    private String getTripNoFromBusNo(String busNo, Jedis jedis) {
        String roadSheetStr = jedis.get("road_sheet:" + busNo);
        if (roadSheetStr != null) {
            return new JSONObject(roadSheetStr).optString("tripNo");
        }
        return "UNKNOWN";
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

    private int getTicketCountFromRedis(Jedis jedis, String busNo) {
        String count = jedis.get("ticket_count:" + busNo);
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

    // TODO: CV团队实现特征向量匹配接口
    private float matchPassengerFeature(String feature, String busNo) {
        // 预留接口，返回0-1相似度
        // 未来接入向量数据库匹配
        return 0.0f; // 占位
    }

    private JSONObject callVideoUnderstandApi(String videoUrl) throws IOException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(Config.VIDEO_UNDERSTAND_API);
            post.setHeader("Content-Type", "application/json");
            StringEntity entity = new StringEntity("{\"video_path\":\"" + videoUrl + "\"}");
            post.setEntity(entity);

            try (CloseableHttpResponse response = client.execute(post)) {
                String responseString = EntityUtils.toString(response.getEntity());
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
    }

    private void cacheFeatureStationMapping(Jedis jedis, String feature, String stationId, String stationName) {
        JSONObject mapping = new JSONObject();
        mapping.put("stationId", stationId);
        mapping.put("stationName", stationName);
        jedis.set("feature_station:" + feature, mapping.toString());
    }

    private JSONObject getOnStationFromCache(Jedis jedis, String feature) {
        String mappingJson = jedis.get("feature_station:" + feature);
        if (mappingJson != null) {
            return new JSONObject(mappingJson);
        }
        return null;
    }

    private int getCachedDownCount(Jedis jedis, String busNo, LocalDateTime begin) {
        // 从缓存获取本次开门期间的下车数（简化假设，从total_count计算差异）
        return 0; // 实际实现从缓存累加
    }

    private void sendToKafka(Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            producer.send(new ProducerRecord<>(KafkaConfig.PASSENGER_FLOW_TOPIC, json));
        } catch (Exception e) {
            System.err.println("Send to Kafka error: " + e.getMessage());
        }
    }

    public void close() {
        if (producer != null) producer.close();
    }
}