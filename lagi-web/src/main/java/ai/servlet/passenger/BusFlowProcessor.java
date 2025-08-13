package ai.servlet.passenger;

import ai.servlet.BaseServlet;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import redis.clients.jedis.Jedis;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.WebSocketContainer;
import java.io.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@WebServlet(urlPatterns = "/processor", loadOnStartup = 1)
public class BusFlowProcessor extends BaseServlet {
    private static final long serialVersionUID = 1L;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private WebSocketContainer container;
    private WsClientHandler clientHandler;
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final BusOdRecordDao busOdRecordDao = new BusOdRecordDao();
    // 缓存车辆开门期间的实时数据，key为busNo_cameraNo_timestampBegin
    private final Map<String, List<JSONObject>> realTimeDataCache = new ConcurrentHashMap<>();
    
    @Override
    public void init() throws ServletException {
        super.init();
        System.out.println("BusFlowProcessor initializing...");
        container = ContainerProvider.getWebSocketContainer();
        clientHandler = new WsClientHandler(this);

        try {
            // 连接CV系统的WebSocket服务端
            container.connectToServer(clientHandler, new URI(Config.CV_WEBSOCKET_URI));
        } catch (DeploymentException | IOException | URISyntaxException e) {
            System.err.println("Failed to connect to CV WebSocket server: " + e.getMessage());
            throw new ServletException("WebSocket connection failed", e);
        }

        // TODO: 初始化Kafka Consumer，监听公交云推送的数据
        // 替换现有模拟定时任务为真实Kafka监听逻辑
        // scheduler.scheduleAtFixedRate(this::processKafkaMessages, 0, 5, TimeUnit.SECONDS);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            String event = request.getParameter("event");
            String busNo = request.getParameter("busNo");
            String cameraNo = request.getParameter("cameraNo");

            if ("open_door_signal".equals(event)) {
                openDoor(busNo, cameraNo);
                response.setStatus(HttpServletResponse.SC_OK);
                out.write("{\"message\":\"Open door signal sent to CV system.\"}");
            } else if ("close_door_signal".equals(event)) {
                closeDoor(busNo, cameraNo);
                response.setStatus(HttpServletResponse.SC_OK);
                out.write("{\"message\":\"Close door signal sent and data processing started.\"}");
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.write("{\"message\":\"Invalid event type.\"}");
            }
        }
    }

    private void openDoor(String busNo, String cameraNo) {
        try {
            String timestamp = LocalDateTime.now().toString();
            String openDoorMessage = String.format(
                "{\"event\":\"open_door\",\"data\":{\"bus_no\":\"%s\",\"camera_no\":\"%s\",\"action\":\"open\",\"timestamp\":\"%s\"}}",
                busNo, cameraNo, timestamp);
            clientHandler.sendMessage(openDoorMessage);
            System.out.println("Sent open_door signal to CV system for bus: " + busNo);
            // 初始化缓存
            realTimeDataCache.put(getCacheKey(busNo, cameraNo, timestamp), new ArrayList<>());
        } catch (IOException e) {
            System.err.println("Failed to send open_door signal: " + e.getMessage());
            throw new RuntimeException("Failed to send open_door signal", e);
        }
    }

    private void closeDoor(String busNo, String cameraNo) {
        try {
            LocalDateTime now = LocalDateTime.now();
            String timestampBegin = now.minusSeconds(5).toString(); // 模拟开门持续时间
            String timestampEnd = now.toString();
            String closeDoorMessage = String.format(
                "{\"event\":\"close_door\",\"data\":{\"bus_no\":\"%s\",\"camera_no\":\"%s\",\"timestamp_begin\":\"%s\",\"timestamp_end\":\"%s\"}}",
                busNo, cameraNo, timestampBegin, timestampEnd);
            clientHandler.sendMessage(closeDoorMessage);
            System.out.println("Sent close_door signal to CV system for bus: " + busNo);
        } catch (IOException e) {
            System.err.println("Failed to send close_door signal: " + e.getMessage());
            throw new RuntimeException("Failed to send close_door signal", e);
        }
    }

    // 处理WebSocket接收到的实时上下车数据
    public void handleDownUpEvent(JSONObject data) {
        String busNo = data.getString("bus_no");
        String cameraNo = data.getString("camera_no");
        String timestamp = data.getString("timestamp");
        String cacheKey = getCacheKey(busNo, cameraNo, timestamp);

        // 缓存实时数据到Redis和内存
        try (Jedis jedis = new Jedis(Config.REDIS_HOST, Config.REDIS_PORT)) {
            jedis.auth(Config.REDIS_PASSWORD);
            jedis.lpush("bus_od_data:" + cacheKey, data.toString());
            realTimeDataCache.computeIfAbsent(cacheKey, k -> new ArrayList<>()).add(data);
            System.out.println("Cached downup data for bus: " + busNo + ", cacheKey: " + cacheKey);
        } catch (Exception e) {
            System.err.println("Failed to cache downup data: " + e.getMessage());
            throw new RuntimeException("Redis cache error", e);
        }
    }

    // 处理关门后的文件拉取通知
    public void processFinalData(String busNo, String cameraNo, LocalDateTime timestampBegin, LocalDateTime timestampEnd, String videoFileUrl) {
        System.out.println("Processing final data for bus: " + busNo);
        try {
            // 1. 下载视频文件并上传至OSS
            String ossVideoUrl = downloadAndUploadToOss(videoFileUrl);
            if (ossVideoUrl == null) {
                System.err.println("Failed to download or upload video file. Aborting data processing.");
                return;
            }

            // 2. 调用大模型API获取乘客特征和预估人数
            String largeModelResponse = callLargeModelApi(ossVideoUrl);
            JSONObject largeModelJson = new JSONObject(largeModelResponse);
            String featureDescription = largeModelJson.getJSONArray("passenger_features").toString();
            int totalCount = largeModelJson.getInt("total_count");

            // 3. 从Redis和内存缓存中获取实时数据
            String cacheKey = getCacheKey(busNo, cameraNo, timestampBegin.toString());
            List<JSONObject> cachedData = realTimeDataCache.getOrDefault(cacheKey, new ArrayList<>());
            if (cachedData.isEmpty()) {
                try (Jedis jedis = new Jedis(Config.REDIS_HOST, Config.REDIS_PORT)) {
                    jedis.auth(Config.REDIS_PASSWORD);
                    List<String> cachedStrings = jedis.lrange("bus_od_data:" + cacheKey, 0, -1);
                    for (String str : cachedStrings) {
                        cachedData.add(new JSONObject(str));
                    }
                }
            }

            // 4. 计算OD矩阵和客流统计
            BusOdRecord record = buildBusOdRecord(busNo, cameraNo, timestampBegin, timestampEnd, cachedData, featureDescription, totalCount);

            // 5. 持久化到PolarDB
            busOdRecordDao.save(record);
            System.out.println("Bus OD record saved successfully for bus: " + busNo);

            // 6. 发送结果到Kafka供数仓存储
            // TODO: 实现Kafka Producer逻辑
            // KafkaUtil.send(record);

            // 7. 清理缓存
            realTimeDataCache.remove(cacheKey);
            try (Jedis jedis = new Jedis(Config.REDIS_HOST, Config.REDIS_PORT)) {
                jedis.auth(Config.REDIS_PASSWORD);
                jedis.del("bus_od_data:" + cacheKey);
            }

        } catch (SQLException e) {
            System.err.println("Failed to save Bus OD record: " + e.getMessage());
            throw new RuntimeException("Database error", e);
        } catch (Exception e) {
            System.err.println("Error processing final data: " + e.getMessage());
            throw new RuntimeException("Data processing error", e);
        }
    }

    private String getCacheKey(String busNo, String cameraNo, String timestamp) {
        return busNo + "_" + cameraNo + "_" + timestamp.replaceAll("[:\\-]", "");
    }

    private BusOdRecord buildBusOdRecord(String busNo, String cameraNo, LocalDateTime timestampBegin, LocalDateTime timestampEnd, 
                                        List<JSONObject> cachedData, String featureDescription, int totalCount) {
        BusOdRecord record = new BusOdRecord();
        record.setId(UUID.randomUUID().getMostSignificantBits());
        record.setDate(timestampBegin.toLocalDate());
        record.setTimestampBegin(timestampBegin);
        record.setTimestampEnd(timestampEnd);
        record.setBusNo(busNo);
        record.setCameraNo(cameraNo);

        // TODO: 从公交云获取线路、班次、站点等信息
        record.setLineId("L102"); // 模拟数据，需替换
        record.setDirection("up"); // 模拟数据，需替换
        record.setTripNo("T001"); // 模拟数据，需替换
        record.setStationIdOn("S01"); // 模拟数据，需替换
        record.setStationNameOn("起点站"); // 模拟数据，需替换
        record.setStationIdOff("S02"); // 模拟数据，需替换
        record.setStationNameOff("第二站"); // 模拟数据，需替换

        // 处理缓存数据，计算上下车人数和OD
        int upCount = 0;
        int downCount = 0;
        List<String> passengerVectors = new ArrayList<>();
        List<String> passengerPositions = new ArrayList<>();
        for (JSONObject data : cachedData) {
            JSONArray events = data.getJSONArray("events");
            for (int i = 0; i < events.length(); i++) {
                JSONObject event = events.getJSONObject(i);
                String direction = event.getString("direction");
                if ("up".equals(direction)) {
                    upCount++;
                } else if ("down".equals(direction)) {
                    downCount++;
                    passengerVectors.add(event.getString("feature"));
                    JSONObject position = new JSONObject();
                    position.put("xLeftUp", event.getInt("box_x"));
                    position.put("yLeftUp", event.getInt("box_y"));
                    position.put("xRightBottom", event.getInt("box_x") + event.getInt("box_w"));
                    position.put("yRightBottom", event.getInt("box_y") + event.getInt("box_h"));
                    passengerPositions.add(position.toString());
                }
            }
        }

        record.setUpCount(upCount);
        record.setDownCount(downCount);
        record.setPassengerVector(String.join(",", passengerVectors));
        record.setPassengerPosition("[" + String.join(",", passengerPositions) + "]");
        record.setCountingImage(cachedData.isEmpty() ? "" : cachedData.get(0).getJSONArray("events").getJSONObject(0).getString("image"));
        
        // TODO: 从CV接口获取满载率
        record.setFullLoadRate(new BigDecimal("50.00")); // 模拟数据，需替换

        // 设置GPS坐标（模拟数据，需替换为公交云提供的实时GPS）
        record.setGpsLat(new BigDecimal("30.25"));
        record.setGpsLng(new BigDecimal("120.15"));

        record.setFeatureDescription(featureDescription);
        record.setTotalCount(totalCount);

        // TODO: 从公交云获取票务数据
        record.setTicketCount(0); // 模拟数据，需替换
        record.setDataSource("CV_AI_修正");
        record.setCreatedAt(LocalDateTime.now());

        return record;
    }

    private String downloadAndUploadToOss(String videoFileUrl) throws IOException {
        System.out.println("Downloading video from: " + videoFileUrl);
        URL url = new URL(videoFileUrl);
        String tempFileName = UUID.randomUUID().toString() + ".mp4";
        File tempFile = new File(System.getProperty("java.io.tmpdir"), tempFileName);

        try (InputStream in = url.openStream();
             FileOutputStream out = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
        System.out.println("Download complete. File saved to: " + tempFile.getAbsolutePath());

        String ossUrl = OssUtil.uploadFile(tempFile, tempFile.getName());
        tempFile.delete();
        if (ossUrl != null) {
            System.out.println("Video uploaded to OSS: " + ossUrl);
            return ossUrl;
        }
        throw new IOException("Failed to upload video to OSS");
    }

    private String callLargeModelApi(String videoPath) throws IOException {
        System.out.println("Calling large model API with video path: " + videoPath);
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpPost httppost = new HttpPost(Config.VIDEO_UNDERSTAND_API);
            JSONObject jsonRequest = new JSONObject();
            jsonRequest.put("video_path", videoPath);
            StringEntity entity = new StringEntity(jsonRequest.toString(), "UTF-8");
            entity.setContentType("application/json");
            httppost.setEntity(entity);

            try (CloseableHttpResponse response = httpclient.execute(httppost)) {
                HttpEntity resEntity = response.getEntity();
                if (resEntity != null) {
                    String responseString = EntityUtils.toString(resEntity);
                    System.out.println("Large model API response: " + responseString);
                    return responseString;
                }
            }
        }
        throw new IOException("Failed to get response from large model API.");
    }

    @Override
    public void destroy() {
        super.destroy();
        System.out.println("BusFlowProcessor destroying...");
        if (scheduler != null) {
            scheduler.shutdown();
        }
        if (clientHandler != null && WsClientHandler.getSession() != null) {
            try {
                WsClientHandler.getSession().close();
            } catch (IOException e) {
                System.err.println("Failed to close WebSocket session: " + e.getMessage());
            }
        }
    }
}