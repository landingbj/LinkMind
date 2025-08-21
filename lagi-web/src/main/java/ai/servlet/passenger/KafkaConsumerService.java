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
 * Kafka消费 → 判断信号 → CV发送WebSocket到系统 → CV推送 → 处理OD/大模型 → 发送Kafka。
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
                if (Config.LOG_INFO) {
                    System.out.println("[KafkaConsumerService] Loaded " + stationGpsMap.size() + " stations from Redis cache");
                }
                return;
            }

            // 从数据库加载（修正为4个占位符）
            String sql = "SELECT stop_id, stop_coord_wgs84_lat, stop_coord_wgs84_lng " +
                    "FROM ods.route_stop " +
                    "WHERE route_id IN (?,?,?,?) AND biz_date = (SELECT MAX(biz_date) FROM ods.route_stop) " +
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
                // 设置站点GPS缓存过期时间
                jedis.expire("station_gps_map", Config.REDIS_TTL_STATION_GPS);
                if (Config.LOG_INFO) {
                    System.out.println("[KafkaConsumerService] Loaded " + stationGpsMap.size() + " stations from database");
                }
            }
        } catch (SQLException e) {
            if (Config.LOG_ERROR) {
                System.err.println("[KafkaConsumerService] Failed to load station GPS: " + e.getMessage());
            }
        }
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            if (Config.LOG_INFO) {
                System.out.println("[KafkaConsumerService] Starting Kafka consumer service, topics=[" +
                        String.join(", ", KafkaConfig.BUS_GPS_TOPIC, KafkaConfig.TICKET_TOPIC) + "]");
            }
            Properties props = KafkaConfig.getConsumerProperties();
            consumer = new KafkaConsumer<>(props);
            consumer.subscribe(Arrays.asList(
                    KafkaConfig.BUS_GPS_TOPIC,
                    KafkaConfig.TICKET_TOPIC
            ));
            executorService = Executors.newSingleThreadExecutor();
            executorService.submit(this::consumeLoop);
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (Config.LOG_INFO) {
                System.out.println("[KafkaConsumerService] Stopping Kafka consumer service");
            }
            if (consumer != null) consumer.close();
            if (executorService != null) executorService.shutdown();
        }
    }

    private void consumeLoop() {
        if (Config.LOG_INFO) {
            System.out.println("[KafkaConsumerService] Enter consume loop");
        }
        while (running.get()) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
            for (ConsumerRecord<String, String> record : records) {
                try {
                    JSONObject message = new JSONObject(record.value());
                    String topic = record.topic();
                    String busNo = message.optString("busSelfNo", message.optString("busNo"));
                    if (Config.LOG_DEBUG) {
                        System.out.println("[KafkaConsumerService] Received from topic=" + topic + ", busNo=" + busNo + ", payload=" + record.value());
                    }
                    if (busNo.isEmpty()) continue;

                    // 过滤试点线路
                    String routeId = extractRouteId(message, topic);
                    if (!isPilotRoute(routeId)) {
                        if (Config.LOG_DEBUG) {
                            System.out.println("[KafkaConsumerService] Skip non-pilot routeId=" + routeId + ", busNo=" + busNo);
                        }
                        continue;
                    }

                    processMessage(topic, message, busNo);
                } catch (Exception e) {
                    if (Config.LOG_ERROR) {
                        System.err.println("[KafkaConsumerService] Error processing Kafka message: " + e.getMessage());
                    }
                }
            }
        }
        if (Config.LOG_INFO) {
            System.out.println("[KafkaConsumerService] Exit consume loop");
        }
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
                case KafkaConfig.BUS_GPS_TOPIC:
                    int pktType = message.optInt("pktType", 0);
                    if (Config.LOG_DEBUG) {
                        System.out.println("[KafkaConsumerService] Handling bus_gps pktType=" + pktType + ", busNo=" + busNo);
                    }
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
                            if (Config.LOG_DEBUG) {
                                System.out.println("[KafkaConsumerService] Unknown bus_gps message schema, busNo=" + busNo);
                            }
                        }
                    }
                    break;
                case KafkaConfig.TICKET_TOPIC:
                    if (Config.LOG_DEBUG) {
                        System.out.println("[KafkaConsumerService] Handling ticket, busNo=" + busNo);
                    }
                    handleTicket(message, busNo, jedis);
                    break;
            }

            // 判断开门/关门
            judgeAndSendDoorSignal(busNo, jedis);
        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                System.err.println("[KafkaConsumerService] Process message error: " + e.getMessage());
            }
        }
    }



    private void handleGps(JSONObject message, String busNo, Jedis jedis) {
        double lat = message.optDouble("lat");
        double lng = message.optDouble("lng");
        double speed = message.optDouble("speed");
        String trafficType = String.valueOf(message.opt("trafficType"));
        String direction = "4".equals(trafficType) ? "up" : "down";

        if (Config.LOG_DEBUG) {
            System.out.println("[KafkaConsumerService] 📍 处理GPS数据: busNo=" + busNo + 
                ", lat=" + lat + ", lng=" + lng + ", speed=" + speed + "m/s (" + (speed * 3.6) + "km/h)" +
                ", trafficType=" + trafficType + ", direction=" + direction);
        }

        // 缓存GPS，设置过期时间
        JSONObject gpsJson = new JSONObject();
        gpsJson.put("lat", lat);
        gpsJson.put("lng", lng);
        gpsJson.put("speed", speed);
        gpsJson.put("direction", direction);
        if (message.has("busId")) {
            gpsJson.put("busId", message.optLong("busId"));
            String busIdKey = "bus_id:" + busNo;
            jedis.set(busIdKey, String.valueOf(message.optLong("busId")));
            jedis.expire(busIdKey, Config.REDIS_TTL_COUNTS);
            
            if (Config.LOG_DEBUG) {
                System.out.println("[KafkaConsumerService] 缓存车辆ID: busNo=" + busNo + ", busId=" + message.optLong("busId"));
            }
        }
        String gpsKey = "gps:" + busNo;
        jedis.set(gpsKey, gpsJson.toString());
        jedis.expire(gpsKey, Config.REDIS_TTL_GPS);

        if (Config.LOG_INFO) {
            System.out.println("📍 [KafkaConsumerService] GPS数据已缓存: busNo=" + busNo + 
                ", 坐标(" + lat + ", " + lng + "), 速度" + speed + "m/s, 方向" + direction);
        }
        
        if (Config.LOG_DEBUG) {
            System.out.println("[KafkaConsumerService] Cached gps for busNo=" + busNo + ", lat=" + lat + ", lng=" + lng + ", speed=" + speed + ", direction=" + direction);
        }
    }

    private void handleArriveLeave(JSONObject message, String busNo, Jedis jedis) {
        String isArriveOrLeft = String.valueOf(message.opt("isArriveOrLeft"));
        String stationId = message.optString("stationId");
        String stationName = message.optString("stationName");
        String nextStationSeqNum = message.optString("nextStationSeqNum");
        String trafficType2 = String.valueOf(message.opt("trafficType"));
        String direction2 = "4".equals(trafficType2) ? "up" : "down";
        String srcAddrOrg = message.optString("srcAddrOrg");

        // 缓存到离站，设置过期时间
        JSONObject arriveLeave = new JSONObject();
        arriveLeave.put("isArriveOrLeft", isArriveOrLeft);
        arriveLeave.put("stationId", stationId);
        arriveLeave.put("stationName", stationName);
        arriveLeave.put("nextStationSeqNum", nextStationSeqNum);
        arriveLeave.put("direction", direction2);
        if (srcAddrOrg != null && !srcAddrOrg.isEmpty()) {
            arriveLeave.put("srcAddrOrg", srcAddrOrg);
        }
        String arriveLeaveKey = "arrive_leave:" + busNo;
        jedis.set(arriveLeaveKey, arriveLeave.toString());
        jedis.expire(arriveLeaveKey, Config.REDIS_TTL_ARRIVE_LEAVE);

        if (Config.LOG_DEBUG) {
            System.out.println("[KafkaConsumerService] Cached arrive_leave for busNo=" + busNo + ", isArriveOrLeft=" + isArriveOrLeft + ", stationId=" + stationId + ", stationName=" + stationName + ", direction=" + direction2);
        }

        if ("1".equals(isArriveOrLeft)) {
            if (Config.LOG_INFO) {
                System.out.println("[KafkaConsumerService] Bus " + busNo + " arrived at station " + stationName);
            }
        } else if ("2".equals(isArriveOrLeft)) {
            if (Config.LOG_INFO) {
                System.out.println("[KafkaConsumerService] Bus " + busNo + " left station " + stationName);
            }
        }
    }

    private void handleTicket(JSONObject message, String busNo, Jedis jedis) {
        String cardNo = message.optString("cardNo");
        String stationId = message.optString("stationId");
        String stationName = message.optString("stationName");
        String trafficType = String.valueOf(message.opt("trafficType"));
        String direction = "4".equals(trafficType) ? "up" : "down";
        double amount = message.optDouble("amount", 0.0);

        if (Config.LOG_DEBUG) {
            System.out.println("[KafkaConsumerService] 🎫 处理票务数据: busNo=" + busNo + 
                ", cardNo=" + cardNo + ", stationId=" + stationId + ", stationName=" + stationName +
                ", trafficType=" + trafficType + ", direction=" + direction + ", amount=" + amount);
        }

        // 缓存到离站信息
        JSONObject arriveLeaveJson = new JSONObject();
        arriveLeaveJson.put("stationId", stationId);
        arriveLeaveJson.put("stationName", stationName);
        arriveLeaveJson.put("isArriveOrLeft", trafficType);
        arriveLeaveJson.put("timestamp", LocalDateTime.now().format(formatter));
        arriveLeaveJson.put("cardNo", cardNo);
        arriveLeaveJson.put("amount", amount);

        String arriveLeaveKey = "arrive_leave:" + busNo;
        jedis.set(arriveLeaveKey, arriveLeaveJson.toString());
        jedis.expire(arriveLeaveKey, Config.REDIS_TTL_ARRIVE_LEAVE);

        if (Config.LOG_INFO) {
            System.out.println("🎫 [KafkaConsumerService] 票务数据已缓存: busNo=" + busNo + 
                ", 站点=" + stationName + "(" + stationId + "), 方向=" + direction + 
                ", 金额=" + amount + "元, 时间=" + LocalDateTime.now().format(formatter));
        }

        // 更新站点GPS缓存
        if (message.has("lat") && message.has("lng")) {
            double lat = message.optDouble("lat");
            double lng = message.optDouble("lng");
            double[] stationGps = {lat, lng};
            stationGpsMap.put(stationId, stationGps);
            
            if (Config.LOG_DEBUG) {
                System.out.println("[KafkaConsumerService] 更新站点GPS缓存: stationId=" + stationId + 
                    ", lat=" + lat + ", lng=" + lng);
            }
        }

        if (Config.LOG_DEBUG) {
            System.out.println("[KafkaConsumerService] Cached arrive/leave for busNo=" + busNo + ", stationId=" + stationId + ", trafficType=" + trafficType);
        }
    }

    private void judgeAndSendDoorSignal(String busNo, Jedis jedis) throws JsonProcessingException {
        // 获取缓存数据
        String arriveLeaveStr = jedis.get("arrive_leave:" + busNo);
        String gpsStr = jedis.get("gps:" + busNo);

        if (arriveLeaveStr == null || gpsStr == null) {
            if (Config.LOG_DEBUG) {
                System.out.println("[KafkaConsumerService] Missing data for door signal judgment: busNo=" + busNo + 
                    ", arriveLeave=" + (arriveLeaveStr != null ? "exists" : "null") + 
                    ", gps=" + (gpsStr != null ? "exists" : "null"));
            }
            return;
        }

        JSONObject arriveLeave = new JSONObject(arriveLeaveStr);
        JSONObject gps = new JSONObject(gpsStr);

        String stationId = arriveLeave.optString("stationId");
        double busLat = gps.optDouble("lat");
        double busLng = gps.optDouble("lng");
        double speed = gps.optDouble("speed");

        if (Config.LOG_DEBUG) {
            System.out.println("[KafkaConsumerService] Door signal judgment input: busNo=" + busNo + 
                ", stationId=" + stationId + ", busLat=" + busLat + ", busLng=" + busLng + 
                ", speed=" + speed + "m/s (" + (speed * 3.6) + "km/h)");
        }

        // 获取站点GPS
        double[] stationGps = stationGpsMap.getOrDefault(stationId, null);
        boolean hasStationGps = stationGps != null;
        double distance = Double.MAX_VALUE;
        if (hasStationGps) {
            distance = calculateDistance(busLat, busLng, stationGps[0], stationGps[1]);
            if (Config.LOG_DEBUG) {
                System.out.println("[KafkaConsumerService] Station GPS found: stationId=" + stationId + 
                    ", stationLat=" + stationGps[0] + ", stationLng=" + stationGps[1] + 
                    ", distance=" + distance + "m");
            }
        } else {
            if (Config.LOG_DEBUG) {
                System.err.println("[KafkaConsumerService] No GPS data for station: " + stationId + ", will judge door only by arrive/leave");
            }
        }

        // 判断开门（优先报站 > GPS）
        boolean shouldOpen = false;
        String openReason = "";
        if ("1".equals(arriveLeave.optString("isArriveOrLeft"))) {
            shouldOpen = true; // 报站到站
            openReason = "报站到站信号";
        } else if (hasStationGps && distance < 50 && speed < 1) { // GPS电子围栏 <50米且速度<1m/s
            shouldOpen = true;
            openReason = "GPS电子围栏触发(距离" + distance + "m, 速度" + speed + "m/s)";
        }

        // 判断关门
        boolean shouldClose = false;
        String closeReason = "";
        if ("2".equals(arriveLeave.optString("isArriveOrLeft"))) {
            shouldClose = true; // 报站离站
            closeReason = "报站离站信号";
        } else if (hasStationGps && (distance > 30 || speed > 10 / 3.6)) { // >30米或速度>10km/h (m/s)
            shouldClose = true;
            closeReason = "GPS电子围栏触发(距离" + distance + "m, 速度" + speed + "m/s)";
        }

        LocalDateTime now = LocalDateTime.now();
        if (Config.LOG_DEBUG) {
            System.out.println("[KafkaConsumerService] Door judgment result: busNo=" + busNo + 
                ", stationId=" + stationId + ", distance=" + distance + "m, speed=" + speed + "m/s" +
                ", shouldOpen=" + shouldOpen + " (" + openReason + ")" +
                ", shouldClose=" + shouldClose + " (" + closeReason + ")");
        }

        if (shouldOpen) {
            String openTimeKey = "open_time:" + busNo;
            String ticketCountKey = "ticket_count_window:" + busNo;
            jedis.set(openTimeKey, now.format(formatter));
            jedis.set(ticketCountKey, "0");
            jedis.expire(openTimeKey, Config.REDIS_TTL_OPEN_TIME);
            jedis.expire(ticketCountKey, Config.REDIS_TTL_OPEN_TIME);
            
            if (Config.LOG_INFO) {
                System.out.println("[KafkaConsumerService] 🚪 发送开门信号到CV系统: busNo=" + busNo + 
                    ", 原因=" + openReason + ", 时间=" + now.format(formatter));
            }
            
            // 发送开门信号到CV
            sendDoorSignalToCV(busNo, "open", now);
            
            if (Config.LOG_INFO) {
                System.out.println("[KafkaConsumerService] ✅ 开门信号处理完成: busNo=" + busNo + 
                    ", open_time=" + now.format(formatter) + ", Redis缓存已设置");
            }
        } else if (shouldClose) {
            String openTimeStr = jedis.get("open_time:" + busNo);
            if (openTimeStr != null) {
                if (Config.LOG_INFO) {
                    System.out.println("[KafkaConsumerService] 🚪 发送关门信号到CV系统: busNo=" + busNo + 
                        ", 原因=" + closeReason + ", 时间=" + now.format(formatter) + 
                        ", 上次开门时间=" + openTimeStr);
                }
                
                // 发送关门信号到CV
                sendDoorSignalToCV(busNo, "close", now);
                
                if (Config.LOG_INFO) {
                    System.out.println("[KafkaConsumerService] ✅ 关门信号处理完成: busNo=" + busNo + 
                        ", 清理Redis缓存, 准备处理OD数据");
                }
                
                jedis.del("open_time:" + busNo);
                jedis.del("ticket_count_window:" + busNo);
            } else {
                if (Config.LOG_DEBUG) {
                    System.out.println("[KafkaConsumerService] 关门信号触发但未找到开门记录: busNo=" + busNo);
                }
            }
        }
    }

    /**
     * 发送开关门信号到CV
     */
    private void sendDoorSignalToCV(String busNo, String action, LocalDateTime timestamp) {
        try {
            JSONObject doorSignal = new JSONObject();
            doorSignal.put("event", "open_close_door");
            
            JSONObject data = new JSONObject();
            data.put("bus_no", busNo);
            data.put("camera_no", "default"); // 默认摄像头编号
            data.put("action", action);
            data.put("timestamp", timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            
            doorSignal.put("data", data);
            
            // 通过WebSocket发送给CV
            WebSocketEndpoint.sendToAll(doorSignal.toString());
            
            if (Config.LOG_INFO) {
                System.out.println("[KafkaConsumerService] Sent door signal to CV: " + doorSignal.toString());
            }
        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                System.err.println("[KafkaConsumerService] Failed to send door signal to CV: " + e.getMessage());
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
