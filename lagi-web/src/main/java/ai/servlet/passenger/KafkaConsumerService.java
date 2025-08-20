package ai.servlet.passenger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.json.JSONObject;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kafka消费者服务，统一消费多个主题，判断开门/关门，发送信号到CV
 * Kafka消费 → 判断信号 → 发送WebSocket到CV → CV推送 → 处理OD/大模型 → 保存DB/Kafka。
 */
public class KafkaConsumerService {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final JedisPool jedisPool = new JedisPool(Config.REDIS_HOST, Config.REDIS_PORT);
    private KafkaConsumer<String, String> consumer;
    private ExecutorService executorService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // 试点线路
    private static final String[] PILOT_ROUTES = {
            "1001000041",   // 8路
            "1001000109",   // 36路
            "1001000496",   // 316路
            "1001001437"    // 55路
    };

    // 站点GPS映射
    private final Map<String, double[]> stationGpsMap = new HashMap<>();

    public KafkaConsumerService() {
        loadStationGpsFromDb();
    }

    private void loadStationGpsFromDb() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.auth(Config.REDIS_PASSWORD);
            // 检查 Redis 缓存
            if (jedis.exists("station_gps_map")) {
                Map<String, String> cached = jedis.hgetAll("station_gps_map");
                for (Map.Entry<String, String> entry : cached.entrySet()) {
                    String[] coords = entry.getValue().split(",");
                    stationGpsMap.put(entry.getKey(), new double[]{Double.parseDouble(coords[0]), Double.parseDouble(coords[1])});
                }
                System.out.println("[KafkaConsumerService] Loaded " + stationGpsMap.size() + " stations from Redis cache");
                return;
            }

            // 从数据库加载
            String sql = "SELECT stop_id, stop_coord_wgs84_lat, stop_coord_wgs84_lng " +
                    "FROM ods.route_stop " +
                    "WHERE route_id IN (?,?,?,?,?) AND biz_date = (SELECT MAX(biz_date) FROM ods.route_stop) " +
                    "AND stop_coord_wgs84_lat IS NOT NULL AND stop_coord_wgs84_lng IS NOT NULL";
            try (Connection conn = DriverManager.getConnection(Config.getDbUrl(), Config.getDbUser(), Config.getDbPassword());
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                // 设置试点线路参数
                for (int i = 0; i < PILOT_ROUTES.length; i++) {
                    pstmt.setString(i + 1, PILOT_ROUTES[i]);
                }
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        String stopId = rs.getString("stop_id");
                        double lat = rs.getDouble("stop_coord_wgs84_lat");
                        double lng = rs.getDouble("stop_coord_wgs84_lng");
                        stationGpsMap.put(stopId, new double[]{lat, lng});
                        jedis.hset("station_gps_map", stopId, lat + "," + lng);
                    }
                }
                System.out.println("[KafkaConsumerService] Loaded " + stationGpsMap.size() + " stations from database");
            }
        } catch (SQLException e) {
            System.err.println("[KafkaConsumerService] Failed to load station GPS: " + e.getMessage());
        }
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            System.out.println("[KafkaConsumerService] Starting Kafka consumer service, topics=[" +
                    String.join(", ", KafkaConfig.DOOR_STATUS_TOPIC, KafkaConfig.BUS_GPS_TOPIC, KafkaConfig.TICKET_TOPIC) + "]");
            Properties props = KafkaConfig.getConsumerProperties();
            consumer = new KafkaConsumer<>(props);
            consumer.subscribe(Arrays.asList(
                    KafkaConfig.DOOR_STATUS_TOPIC,
                    KafkaConfig.BUS_GPS_TOPIC,
                    KafkaConfig.TICKET_TOPIC
            ));
            executorService = Executors.newSingleThreadExecutor();
            executorService.submit(this::consumeLoop);
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            System.out.println("[KafkaConsumerService] Stopping Kafka consumer service");
            if (consumer != null) consumer.close();
            if (executorService != null) executorService.shutdown();
        }
    }

    private void consumeLoop() {
        System.out.println("[KafkaConsumerService] Enter consume loop");
        while (running.get()) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
            for (ConsumerRecord<String, String> record : records) {
                try {
                    JSONObject message = new JSONObject(record.value());
                    String topic = record.topic();
                    String busNo = message.optString("busSelfNo", message.optString("busNo"));
                    System.out.println("[KafkaConsumerService] Received from topic=" + topic + ", busNo=" + busNo + ", payload=" + record.value());
                    if (busNo.isEmpty()) continue;

                    // 过滤试点线路
                    String routeId = extractRouteId(message, topic);
                    if (!isPilotRoute(routeId)) {
                        System.out.println("[KafkaConsumerService] Skip non-pilot routeId=" + routeId + ", busNo=" + busNo);
                        continue;
                    }

                    processMessage(topic, message, busNo);
                } catch (Exception e) {
                    System.err.println("[KafkaConsumerService] Error processing Kafka message: " + e.getMessage());
                }
            }
        }
        System.out.println("[KafkaConsumerService] Exit consume loop");
    }

    private String extractRouteId(JSONObject message, String topic) {
        if (topic.equals(KafkaConfig.BUS_GPS_TOPIC)) {
            String v = message.optString("srcAddrOrg");
            if (v != null && !v.isEmpty()) return v;
            return message.optString("routeNo");
        }
        return "";
    }

    private boolean isPilotRoute(String routeId) {
        for (String pilot : PILOT_ROUTES) {
            if (pilot.equals(routeId)) return true;
        }
        return false;
    }

    private void processMessage(String topic, JSONObject message, String busNo) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.auth(Config.REDIS_PASSWORD);

            switch (topic) {
                case KafkaConfig.DOOR_STATUS_TOPIC:
                    System.out.println("[KafkaConsumerService] Handling door status, busNo=" + busNo);
                    handleDoorStatus(message, busNo, jedis);
                    break;
                case KafkaConfig.BUS_GPS_TOPIC:
                    int pktType = message.optInt("pktType", 0);
                    System.out.println("[KafkaConsumerService] Handling bus_gps pktType=" + pktType + ", busNo=" + busNo);
                    if (pktType == 3) {
                        handleGps(message, busNo, jedis);
                    } else if (pktType == 4) {
                        handleArriveLeave(message, busNo, jedis);
                    } else {
                        if (message.has("lat") && message.has("lng")) {
                            handleGps(message, busNo, jedis);
                        } else if (message.has("isArriveOrLeft")) {
                            handleArriveLeave(message, busNo, jedis);
                        } else {
                            System.out.println("[KafkaConsumerService] Unknown bus_gps message schema, busNo=" + busNo);
                        }
                    }
                    break;
                case KafkaConfig.TICKET_TOPIC:
                    System.out.println("[KafkaConsumerService] Handling ticket, busNo=" + busNo);
                    handleTicket(message, busNo, jedis);
                    break;
            }

            // 判断开门/关门
            judgeAndSendDoorSignal(busNo, jedis);
        } catch (Exception e) {
            System.err.println("[KafkaConsumerService] Process message error: " + e.getMessage());
        }
    }

    private void handleDoorStatus(JSONObject message, String busNo, Jedis jedis) throws Exception {
        DoorStatusMessage doorStatus = new DoorStatusMessage();
        doorStatus.setBusId(message.optLong("busId"));
        doorStatus.setBusSelfNo(busNo);
        doorStatus.setDoor1OpenSts(message.optInt("door1OpenSts"));
        doorStatus.setDoor3OpenSts(message.optInt("door3OpenSts"));
        doorStatus.setDoor5LockSts(message.optInt("door5LockSts"));
        String timeStr = message.optString("time");
        if (timeStr != null && !timeStr.isEmpty()) {
            doorStatus.setTime(LocalDateTime.parse(timeStr.replace(" ", "T")));
        }

        // 缓存门状态
        jedis.set("door_status:" + busNo, objectMapper.writeValueAsString(doorStatus));
        System.out.println("[KafkaConsumerService] Cached door_status for busNo=" + busNo + ", payload=" + objectMapper.writeValueAsString(doorStatus));

        if (doorStatus.hasOpenDoor()) {
            System.out.println("[KafkaConsumerService] Door open detected for bus=" + busNo);
        } else if (doorStatus.hasErrorDoor()) {
            System.err.println("[KafkaConsumerService] Door error for bus=" + busNo);
        }
    }

    private void handleGps(JSONObject message, String busNo, Jedis jedis) {
        double lat = message.optDouble("lat");
        double lng = message.optDouble("lng");
        double speed = message.optDouble("speed");
        String trafficType = String.valueOf(message.opt("trafficType"));
        String direction = "4".equals(trafficType) ? "up" : "down";

        // 缓存GPS
        JSONObject gpsJson = new JSONObject();
        gpsJson.put("lat", lat);
        gpsJson.put("lng", lng);
        gpsJson.put("speed", speed);
        gpsJson.put("direction", direction);
        if (message.has("busId")) {
            gpsJson.put("busId", message.optLong("busId"));
            jedis.set("bus_id:" + busNo, String.valueOf(message.optLong("busId")));
        }
        jedis.set("gps:" + busNo, gpsJson.toString());
        System.out.println("[KafkaConsumerService] Cached gps for busNo=" + busNo + ", lat=" + lat + ", lng=" + lng + ", speed=" + speed + ", direction=" + direction);
    }

    private void handleArriveLeave(JSONObject message, String busNo, Jedis jedis) {
        String isArriveOrLeft = String.valueOf(message.opt("isArriveOrLeft"));
        String stationId = message.optString("stationId");
        String stationName = message.optString("stationName");
        String nextStationSeqNum = message.optString("nextStationSeqNum");
        String trafficType2 = String.valueOf(message.opt("trafficType"));
        String direction2 = "4".equals(trafficType2) ? "up" : "down";
        String srcAddrOrg = message.optString("srcAddrOrg");

        // 缓存到离站
        JSONObject arriveLeave = new JSONObject();
        arriveLeave.put("isArriveOrLeft", isArriveOrLeft);
        arriveLeave.put("stationId", stationId);
        arriveLeave.put("stationName", stationName);
        arriveLeave.put("nextStationSeqNum", nextStationSeqNum);
        arriveLeave.put("direction", direction2);
        if (srcAddrOrg != null && !srcAddrOrg.isEmpty()) {
            arriveLeave.put("srcAddrOrg", srcAddrOrg);
        }
        jedis.set("arrive_leave:" + busNo, arriveLeave.toString());
        System.out.println("[KafkaConsumerService] Cached arrive_leave for busNo=" + busNo + ", isArriveOrLeft=" + isArriveOrLeft + ", stationId=" + stationId + ", stationName=" + stationName + ", direction=" + direction2);

        if ("1".equals(isArriveOrLeft)) {
            System.out.println("[KafkaConsumerService] Bus " + busNo + " arrived at station " + stationName);
        } else if ("2".equals(isArriveOrLeft)) {
            System.out.println("[KafkaConsumerService] Bus " + busNo + " left station " + stationName);
        }
    }

    private void handleTicket(JSONObject message, String busNo, Jedis jedis) {
        String onOff = message.optString("onOff", "");
        boolean isUpSwipe = !("down".equalsIgnoreCase(onOff));
        if (isUpSwipe) {
            jedis.incr("ticket_count_total:" + busNo);
            String windowId = jedis.get("open_time:" + busNo);
            if (windowId != null) {
                jedis.incr("ticket_count_window:" + busNo);
                System.out.println("[KafkaConsumerService] Ticket up-swipe counted in window, busNo=" + busNo + ", windowId=" + windowId);
            } else {
                System.out.println("[KafkaConsumerService] Ticket up-swipe counted (no open window), busNo=" + busNo);
            }
        } else {
            System.out.println("[KafkaConsumerService] Ticket down-swipe ignored in up-count, busNo=" + busNo);
        }
    }

    private void judgeAndSendDoorSignal(String busNo, Jedis jedis) throws JsonProcessingException {
        // 获取缓存数据
        String arriveLeaveStr = jedis.get("arrive_leave:" + busNo);
        String gpsStr = jedis.get("gps:" + busNo);
        String doorStatusStr = jedis.get("door_status:" + busNo);

        if (arriveLeaveStr == null || gpsStr == null) return;

        JSONObject arriveLeave = new JSONObject(arriveLeaveStr);
        JSONObject gps = new JSONObject(gpsStr);
        DoorStatusMessage doorStatus = doorStatusStr != null ? objectMapper.readValue(doorStatusStr, DoorStatusMessage.class) : null;

        String stationId = arriveLeave.optString("stationId");
        double busLat = gps.optDouble("lat");
        double busLng = gps.optDouble("lng");
        double speed = gps.optDouble("speed");

        // 获取站点GPS
        double[] stationGps = stationGpsMap.getOrDefault(stationId, null);
        if (stationGps == null) {
            System.err.println("[KafkaConsumerService] No GPS data for station: " + stationId);
            return;
        }

        double distance = calculateDistance(busLat, busLng, stationGps[0], stationGps[1]);

        // 判断开门（优先报站 > GPS）
        boolean shouldOpen = false;
        if ("1".equals(arriveLeave.optString("isArriveOrLeft"))) {
            shouldOpen = true; // 报站到站
        } else if (distance < 50 && speed < 1) { // GPS电子围栏 <50米且速度<1m/s
            shouldOpen = true;
        } else if (doorStatus != null && doorStatus.hasOpenDoor()) {
            shouldOpen = true; // 门状态打开
        }

        // 判断关门
        boolean shouldClose = false;
        if ("2".equals(arriveLeave.optString("isArriveOrLeft"))) {
            shouldClose = true; // 报站离站
        } else if (distance > 30 || speed > 10 / 3.6) { // >30米或速度>10km/h (m/s)
            shouldClose = true;
        } else if (doorStatus != null && !doorStatus.hasOpenDoor()) {
            shouldClose = true; // 门状态关闭
        }

        LocalDateTime now = LocalDateTime.now();
        System.out.println("[KafkaConsumerService] Door judge busNo=" + busNo + ", stationId=" + stationId + ", distance=" + distance + ", speed=" + speed + ", shouldOpen=" + shouldOpen + ", shouldClose=" + shouldClose);

        if (shouldOpen) {
            jedis.set("open_time:" + busNo, now.format(formatter));
            jedis.set("ticket_count_window:" + busNo, "0");
            System.out.println("[KafkaConsumerService] Mark OPEN window for busNo=" + busNo + ", open_time=" + now.format(formatter));
        } else if (shouldClose) {
            String openTimeStr = jedis.get("open_time:" + busNo);
            if (openTimeStr != null) {
                jedis.del("open_time:" + busNo);
                jedis.del("ticket_count_window:" + busNo);
                System.out.println("[KafkaConsumerService] Mark CLOSE window for busNo=" + busNo + ", prev_open_time=" + openTimeStr);
            }
        }
    }

    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        // 简化距离计算（Haversine公式）
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double a = radLat1 - radLat2;
        double b = Math.toRadians(lng1) - Math.toRadians(lng2);
        double s = 2 * Math.asin(Math.sqrt(Math.pow(Math.sin(a / 2), 2) + Math.cos(radLat1) * Math.cos(radLat2) * Math.pow(Math.sin(b / 2), 2)));
        s = s * 6378137.0; // 地球半径
        return Math.round(s * 10000) / 10000;
    }
}
