package ai.servlet.passenger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import redis.clients.jedis.Jedis;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.json.JSONObject;
import org.json.JSONArray;

public class BusFlowProcessor extends HttpServlet {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private WsClientHandler wsClientHandler;
    private BusOdRecordDao busOdRecordDao;

    @Override
    public void init() throws ServletException {
        super.init();
        busOdRecordDao = new BusOdRecordDao();
        try {
            wsClientHandler = new WsClientHandler();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try {
            wsClientHandler.connect();
        } catch (Exception e) {
            throw new ServletException("Failed to initialize WebSocket client", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String event = req.getParameter("event");
        String dataJson = req.getReader().lines().reduce("", (accumulator, actual) -> accumulator + actual);
        JSONObject jsonData = new JSONObject(dataJson).getJSONObject("data");

        switch (event) {
            case "downup":
                handleDownUpEvent(jsonData);
                break;
            case "load_factor":
                handleLoadFactorEvent(jsonData);
                break;
            case "open_close_door":
                handleOpenCloseDoorEvent(jsonData);
                break;
            case "notify_pull_file":
                handleNotifyPullFileEvent(jsonData);
                break;
            default:
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("Unknown event: " + event);
        }
    }

    private void handleDownUpEvent(JSONObject data) throws IOException {
        String busNo = data.getString("bus_no");
        String cameraNo = data.getString("camera_no");
        String timestamp = data.getString("timestamp");
        LocalDateTime eventTime = LocalDateTime.parse(timestamp.replace(" ", "T"));
        JSONArray events = data.getJSONArray("events");

        try (Jedis jedis = new Jedis(Config.REDIS_HOST, Config.REDIS_PORT)) {
            jedis.auth(Config.REDIS_PASSWORD);
            List<BusOdRecord> odRecords = new ArrayList<>();
            int upCount = 0, downCount = 0;

            for (int i = 0; i < events.length(); i++) {
                JSONObject event = events.getJSONObject(i);
                String direction = event.getString("direction");
                String feature = event.getString("feature");
                String image = event.getString("image");
                int boxX = event.getInt("box_x");
                int boxY = event.getInt("box_y");
                int boxW = event.getInt("box_w");
                int boxH = event.getInt("box_h");

                BusOdRecord record = new BusOdRecord();
                record.setDate(LocalDate.now());
                record.setTimestampBegin(eventTime);
                record.setTimestampEnd(eventTime);
                record.setBusNo(busNo);
                record.setCameraNo(cameraNo);
                record.setLineId(getLineIdFromBusNo(busNo)); // TODO: 从公交云获取线路编号
                record.setDirection(getDirectionFromBusNo(busNo)); // TODO: 从公交云获取上下行
                record.setTripNo(getTripNoFromBusNo(busNo)); // TODO: 从公交云获取班次号
                record.setPassengerVector(feature);
                record.setCountingImage(image);
                record.setPassengerPosition(String.format("[{\"xLeftUp\":%d,\"yLeftUp\":%d,\"xRightBottom\":%d,\"yRightBottom\":%d}]",
                        boxX, boxY, boxX + boxW, boxY + boxH));
                record.setDataSource("CV");

                if ("up".equals(direction)) {
                    upCount++;
                    record.setStationIdOn(getCurrentStationId(busNo)); // TODO: 从公交云获取上车站点ID
                    record.setStationNameOn(getCurrentStationName(busNo)); // TODO: 从公交云获取上车站点名称
                    record.setUpCount(1);
                } else {
                    downCount++;
                    record.setStationIdOff(getCurrentStationId(busNo)); // TODO: 从公交云获取下车站点ID
                    record.setStationNameOff(getCurrentStationName(busNo)); // TODO: 从公交云获取下车站点名称
                    record.setDownCount(1);

                    // TODO: CV 团队实现特征向量匹配接口
                    float similarity = matchPassengerFeature(feature, busNo);
                    if (similarity > 0.8) { // 假设相似度阈值为 0.8
                        String onStation = getOnStationFromFeature(feature); // TODO: 从向量数据库查询上车站点
                        record.setStationIdOn(onStation);
                        record.setStationNameOn(getStationNameById(onStation));
                    }
                }

                odRecords.add(record);
                cacheOdRecord(jedis, record);
            }

            // 更新总人数和满载率
            BusOdRecord summaryRecord = new BusOdRecord();
            summaryRecord.setDate(LocalDate.now());
            summaryRecord.setTimestampBegin(eventTime);
            summaryRecord.setTimestampEnd(eventTime);
            summaryRecord.setBusNo(busNo);
            summaryRecord.setCameraNo(cameraNo);
            summaryRecord.setLineId(getLineIdFromBusNo(busNo));
            summaryRecord.setDirection(getDirectionFromBusNo(busNo));
            summaryRecord.setTripNo(getTripNoFromBusNo(busNo));
            summaryRecord.setUpCount(upCount);
            summaryRecord.setDownCount(downCount);
            summaryRecord.setTotalCount(getTotalCountFromRedis(jedis, busNo));
            summaryRecord.setFullLoadRate(getFullLoadRateFromRedis(jedis, busNo));
            summaryRecord.setDataSource("CV");
            odRecords.add(summaryRecord);

            // 保存到 PolarDB
            for (BusOdRecord record : odRecords) {
                try {
                    busOdRecordDao.save(record);
                } catch (SQLException e) {
                    throw new IOException("Failed to save OD record", e);
                }
            }

            // TODO: 发送结果到 Kafka 供数据侧数仓存储
            sendToKafka(odRecords);
        }
    }

    private void handleLoadFactorEvent(JSONObject data) throws IOException {
        String busNo = data.getString("bus_no");
        String cameraNo = data.getString("camera_no");
        String timestamp = data.getString("timestamp");
        int count = data.getInt("count");
        float factor = data.getFloat("factor");

        try (Jedis jedis = new Jedis(Config.REDIS_HOST, Config.REDIS_PORT)) {
            jedis.auth(Config.REDIS_PASSWORD);
            jedis.set("load_factor:" + busNo, String.valueOf(factor));
            jedis.set("total_count:" + busNo, String.valueOf(count));

            BusOdRecord record = new BusOdRecord();
            record.setDate(LocalDate.now());
            record.setTimestampBegin(LocalDateTime.parse(timestamp.replace(" ", "T")));
            record.setBusNo(busNo);
            record.setCameraNo(cameraNo);
            record.setLineId(getLineIdFromBusNo(busNo));
            record.setDirection(getDirectionFromBusNo(busNo));
            record.setTripNo(getTripNoFromBusNo(busNo));
            record.setTotalCount(count);
            record.setFullLoadRate(new BigDecimal(factor));
            record.setDataSource("CV");

            try {
                busOdRecordDao.save(record);
            } catch (SQLException e) {
                throw new IOException("Failed to save load factor record", e);
            }
        }
    }

    private void handleOpenCloseDoorEvent(JSONObject data) throws IOException {
        String busNo = data.getString("bus_no");
        String cameraNo = data.getString("camera_no");
        String action = data.getString("action");
        String timestamp = data.getString("timestamp");
        LocalDateTime eventTime = LocalDateTime.parse(timestamp.replace(" ", "T"));

        BusOdRecord record = new BusOdRecord();
        record.setDate(LocalDate.now());
        record.setTimestampBegin(eventTime);
        record.setBusNo(busNo);
        record.setCameraNo(cameraNo);
        record.setLineId(getLineIdFromBusNo(busNo));
        record.setDirection(getDirectionFromBusNo(busNo));
        record.setTripNo(getTripNoFromBusNo(busNo));
        record.setDataSource("CV");

        try (Jedis jedis = new Jedis(Config.REDIS_HOST, Config.REDIS_PORT)) {
            jedis.auth(Config.REDIS_PASSWORD);
            if ("open".equals(action)) {
                record.setStationIdOn(getCurrentStationId(busNo));
                record.setStationNameOn(getCurrentStationName(busNo));
                wsClientHandler.sendOpenDoorSignal(busNo, cameraNo, eventTime);
            } else {
                record.setStationIdOff(getCurrentStationId(busNo));
                record.setStationNameOff(getCurrentStationName(busNo));
                wsClientHandler.sendCloseDoorSignal(busNo, cameraNo, eventTime);
            }
            cacheOdRecord(jedis, record);
        }
    }

    private void handleNotifyPullFileEvent(JSONObject data) throws IOException {
        String busNo = data.getString("bus_no");
        String cameraNo = data.getString("camera_no");
        String timestampBegin = data.getString("timestamp_begin");
        String timestampEnd = data.getString("timestamp_end");
        String fileUrl = data.getString("fileurl");

        // 下载视频文件
        File videoFile = downloadFile(fileUrl);
        String ossUrl = OssUtil.uploadFile(videoFile, UUID.randomUUID().toString() + ".mp4");

        // 调用大模型 API
        JSONObject modelResponse = callVideoUnderstandApi(ossUrl);
        JSONArray passengerFeatures = modelResponse.getJSONArray("passenger_features");
        int totalCount = modelResponse.getInt("total_count");

        BusOdRecord record = new BusOdRecord();
        record.setDate(LocalDate.now());
        record.setTimestampBegin(LocalDateTime.parse(timestampBegin.replace(" ", "T")));
        record.setTimestampEnd(LocalDateTime.parse(timestampEnd.replace(" ", "T")));
        record.setBusNo(busNo);
        record.setCameraNo(cameraNo);
        record.setLineId(getLineIdFromBusNo(busNo));
        record.setDirection(getDirectionFromBusNo(busNo));
        record.setTripNo(getTripNoFromBusNo(busNo));
        record.setFeatureDescription(passengerFeatures.toString());
        record.setTotalCount(totalCount);
        record.setDataSource("MODEL");

        try {
            busOdRecordDao.save(record);
        } catch (SQLException e) {
            throw new IOException("Failed to save video analysis record", e);
        }

        // TODO: 发送结果到 Kafka 供数据侧数仓存储
        sendToKafka(record);
    }

    // TODO: CV 团队实现特征向量匹配接口
    private float matchPassengerFeature(String feature, String busNo) {
        // 预留接口，返回 0-1 的相似度值
        // CV 团队需实现：从向量数据库中匹配 feature，确定是否为同一乘客
        return 0.0f; // 占位返回值
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

    private File downloadFile(String fileUrl) {
        // TODO: 实现文件下载逻辑
        return new File("/tmp/placeholder.mp4"); // 占位文件
    }

    private void cacheOdRecord(Jedis jedis, BusOdRecord record) throws IOException {
        String key = "od_record:" + record.getBusNo() + ":" + record.getTimestampBegin();
        jedis.set(key, objectMapper.writeValueAsString(record));
    }

    private int getTotalCountFromRedis(Jedis jedis, String busNo) {
        String count = jedis.get("total_count:" + busNo);
        return count != null ? Integer.parseInt(count) : 0;
    }

    private BigDecimal getFullLoadRateFromRedis(Jedis jedis, String busNo) {
        String factor = jedis.get("load_factor:" + busNo);
        return factor != null ? new BigDecimal(factor) : BigDecimal.ZERO;
    }

    private String getLineIdFromBusNo(String busNo) {
        // TODO: 从公交云获取线路编号
        return "LINE_001";
    }

    private String getDirectionFromBusNo(String busNo) {
        // TODO: 从公交云获取上下行方向
        return "up";
    }

    private String getTripNoFromBusNo(String busNo) {
        // TODO: 从公交云获取班次号
        return "TRIP_001";
    }

    private String getCurrentStationId(String busNo) {
        // TODO: 从公交云获取当前站点ID
        return "STATION_001";
    }

    private String getCurrentStationName(String busNo) {
        // TODO: 从公交云获取当前站点名称
        return "Station Name";
    }

    private String getOnStationFromFeature(String feature) {
        // TODO: 从向量数据库查询上车站点
        return "STATION_001";
    }

    private String getStationNameById(String stationId) {
        // TODO: 根据站点ID获取站点名称
        return "Station Name";
    }

    private void sendToKafka(Object data) {
        // TODO: 实现 Kafka 发送逻辑
    }

    @Override
    public void destroy() {
        wsClientHandler.close();
        super.destroy();
    }
}