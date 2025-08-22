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
import java.util.concurrent.ConcurrentHashMap;
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
            "8路",   // 8路
            "36路",   // 36路
            "316路",   // 316路
            "55路",    // 55路
            "522M路"    // 522M路
    };

    // 站点GPS映射
    private final Map<String, double[]> stationGpsMap = new HashMap<>();
    // 判门未触发原因日志的节流：每辆车每分钟最多打印一次
    private static final Map<String, Long> lastDoorSkipLogMsByBus = new ConcurrentHashMap<>();

    public KafkaConsumerService() {
        System.out.println("[KafkaConsumerService] 构造函数开始执行");
        System.out.println("[KafkaConsumerService] 试点线路配置: " + Arrays.toString(PILOT_ROUTES));
        System.out.println("[KafkaConsumerService] 正在加载站点GPS数据...");
        loadStationGpsFromDb();
        System.out.println("[KafkaConsumerService] 构造函数执行完成");
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

            // 从数据库加载试点线路的站点GPS数据
            // 使用route_name字段匹配试点线路
            String sql = "SELECT stop_id, stop_coord_wgs84_lat, stop_coord_wgs84_lng, stop_name " +
                    "FROM ods.route_stop " +
                    "WHERE route_name IN (?,?,?,?,?) AND biz_date = (SELECT MAX(biz_date) FROM ods.route_stop) " +
                    "AND stop_coord_wgs84_lat IS NOT NULL AND stop_coord_wgs84_lng IS NOT NULL " +
                    "AND stop_coord_wgs84_lat != '' AND stop_coord_wgs84_lng != ''";
            
            try (Connection conn = DriverManager.getConnection(Config.getDbUrl(), Config.getDbUser(), Config.getDbPassword());
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                // 设置试点线路名称参数
                for (int i = 0; i < PILOT_ROUTES.length; i++) {
                    pstmt.setString(i + 1, PILOT_ROUTES[i]);
                }
                
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        String stopId = rs.getString("stop_id");
                        String stopName = rs.getString("stop_name");
                        String latStr = rs.getString("stop_coord_wgs84_lat");
                        String lngStr = rs.getString("stop_coord_wgs84_lng");
                        
                        try {
                            double lat = Double.parseDouble(latStr);
                            double lng = Double.parseDouble(lngStr);
                            
                            // 使用stopId作为key存储GPS数据
                            stationGpsMap.put(stopId, new double[]{lat, lng});
                            jedis.hset("station_gps_map", stopId, lat + "," + lng);
                            
                            if (Config.LOG_DEBUG) {
                                System.out.println("[KafkaConsumerService] 加载站点GPS: stopId=" + stopId + 
                                    ", stopName=" + stopName + ", lat=" + lat + ", lng=" + lng);
                            }
                        } catch (NumberFormatException e) {
                            if (Config.LOG_ERROR) {
                                System.err.println("[KafkaConsumerService] 站点GPS坐标格式错误: stopId=" + stopId + 
                                    ", lat=" + latStr + ", lng=" + lngStr + ", error=" + e.getMessage());
                            }
                        }
                    }
                }
                
                // 设置站点GPS缓存过期时间
                jedis.expire("station_gps_map", Config.REDIS_TTL_STATION_GPS);
                if (Config.LOG_INFO) {
                    System.out.println("[KafkaConsumerService] Loaded " + stationGpsMap.size() + " stations from database");
                    System.out.println("[KafkaConsumerService] 试点线路站点GPS数据加载完成，共加载 " + stationGpsMap.size() + " 个站点");
                }
            } catch (SQLException e) {
                if (Config.LOG_ERROR) {
                    System.err.println("[KafkaConsumerService] Failed to load station GPS from database: " + e.getMessage());
                }
                // 数据库查询失败时，记录错误但不影响系统启动
            }
        } catch (Exception e) {
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
                System.out.println("[KafkaConsumerService] 试点线路配置: " + Arrays.toString(PILOT_ROUTES));
                System.out.println("[KafkaConsumerService] 试点线路说明: 8路、36路、316路、55路、522M路 (通过站点名匹配 + 数据库查询站点GPS)");
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
            
            // 关闭Kafka消费者
            if (consumer != null) {
                try {
                    consumer.close();
                    if (Config.LOG_INFO) {
                        System.out.println("[KafkaConsumerService] Kafka consumer closed");
                    }
                } catch (Exception e) {
                    if (Config.LOG_ERROR) {
                        System.err.println("[KafkaConsumerService] Error closing Kafka consumer: " + e.getMessage());
                    }
                }
            }
            
            // 优雅关闭线程池
            if (executorService != null) {
                try {
                    // 先尝试优雅关闭
                    executorService.shutdown();
                    
                    // 等待最多30秒让线程自然结束
                    if (!executorService.awaitTermination(Config.KAFKA_SHUTDOWN_TIMEOUT_MS / 1000, java.util.concurrent.TimeUnit.SECONDS)) {
                        if (Config.LOG_INFO) {
                            System.out.println("[KafkaConsumerService] Executor service did not terminate gracefully, forcing shutdown");
                        }
                        // 如果30秒内没有结束，强制关闭
                        executorService.shutdownNow();
                        
                        // 再等待最多10秒
                        if (!executorService.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                            if (Config.LOG_ERROR) {
                                System.err.println("[KafkaConsumerService] Executor service did not terminate");
                            }
                        }
                    }
                    
                    if (Config.LOG_INFO) {
                        System.out.println("[KafkaConsumerService] Executor service stopped");
                    }
                } catch (InterruptedException e) {
                    if (Config.LOG_ERROR) {
                        System.err.println("[KafkaConsumerService] Interrupted while waiting for executor service to terminate: " + e.getMessage());
                    }
                    // 恢复中断状态
                    Thread.currentThread().interrupt();
                    // 强制关闭
                    executorService.shutdownNow();
                }
            }
            
            // 关闭Redis连接池
            if (jedisPool != null) {
                try {
                    jedisPool.close();
                    if (Config.LOG_INFO) {
                        System.out.println("[KafkaConsumerService] Redis connection pool closed");
                    }
                } catch (Exception e) {
                    if (Config.LOG_ERROR) {
                        System.err.println("[KafkaConsumerService] Error closing Redis connection pool: " + e.getMessage());
                    }
                }
            }
            
            if (Config.LOG_INFO) {
                System.out.println("[KafkaConsumerService] Kafka consumer service stopped completely");
            }
        }
    }

    private void consumeLoop() {
        if (Config.LOG_INFO) {
            System.out.println("[KafkaConsumerService] Enter consume loop");
        }
        while (running.get()) {
            try {
                // 减少poll超时时间，确保能够快速响应停止信号
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        // 打印原始Kafka消息的完整内容
                        if (Config.LOG_INFO) {
                            System.out.println("[KafkaConsumerService] 📨 收到Kafka消息:");
                            System.out.println("   Topic: " + record.topic());
                            System.out.println("   Partition: " + record.partition());
                            System.out.println("   Offset: " + record.offset());
                            System.out.println("   Key: " + record.key());
                            System.out.println("   Value: " + record.value());
                            System.out.println("   Timestamp: " + record.timestamp());
                            System.out.println("   Headers: " + (record.headers() != null ? record.headers().toString() : "null"));
                        }
                        
                        JSONObject message = new JSONObject(record.value());
                        String topic = record.topic();
                        String busNo = message.optString("busSelfNo", message.optString("busNo"));
                        if (busNo.isEmpty()) continue;

                        // 过滤试点线路
                        String stationName = extractStationName(message, topic);
                        if (Config.LOG_INFO) {
                            System.out.println("[KafkaConsumerService] 试点线路过滤检查 - busNo=" + busNo + ", stationName=" + stationName + ", topic=" + topic);
                        }
                        if (!isPilotRoute(stationName)) {
                            if (Config.LOG_INFO) {
                                System.out.println("[KafkaConsumerService] 非试点线路，跳过处理 - busNo=" + busNo + ", stationName=" + stationName);
                            }
                            continue;
                        }
                        if (Config.LOG_INFO) {
                            System.out.println("[KafkaConsumerService] 试点线路匹配成功，继续处理 - busNo=" + busNo + ", stationName=" + stationName);
                        }

                        processMessage(topic, message, busNo);
                    } catch (Exception e) {
                        if (Config.LOG_ERROR) {
                            System.err.println("[KafkaConsumerService] Error processing Kafka message: " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                if (running.get()) { // 只有在服务运行时才记录错误
                    if (Config.LOG_ERROR) {
                        System.err.println("[KafkaConsumerService] Error in consume loop: " + e.getMessage());
                    }
                }
            }
        }
        if (Config.LOG_INFO) {
            System.out.println("[KafkaConsumerService] Exit consume loop");
        }
    }

    private String extractStationName(JSONObject message, String topic) {
        if (topic.equals(KafkaConfig.BUS_GPS_TOPIC)) {
            String stationName = message.optString("stationName");
            if (Config.LOG_DEBUG) {
                System.out.println("[KafkaConsumerService] 提取站点名 - topic=" + topic + ", stationName=" + stationName);
            }
            if (stationName != null && !stationName.isEmpty()) {
                if (Config.LOG_DEBUG) {
                    System.out.println("[KafkaConsumerService] 使用stationName作为匹配字段: " + stationName);
                }
                return stationName;
            }
            if (Config.LOG_DEBUG) {
                System.out.println("[KafkaConsumerService] stationName为空，无法匹配试点线路");
            }
            return "";
        }
        if (Config.LOG_DEBUG) {
            System.out.println("[KafkaConsumerService] 非GPS主题，无法提取站点名 - topic=" + topic);
        }
        return "";
    }

    private boolean isPilotRoute(String stationName) {
        if (Config.LOG_DEBUG) {
            System.out.println("[KafkaConsumerService] 检查是否为试点线路 - stationName=" + stationName + ", 试点线路列表=" + Arrays.toString(PILOT_ROUTES));
        }
        if (stationName == null || stationName.isEmpty()) {
            if (Config.LOG_DEBUG) {
                System.out.println("[KafkaConsumerService] 站点名为空，无法匹配试点线路");
            }
            return false;
        }
        
        for (String pilot : PILOT_ROUTES) {
            if (stationName.contains(pilot)) {
                if (Config.LOG_DEBUG) {
                    System.out.println("[KafkaConsumerService] 试点线路匹配成功 - stationName=" + stationName + " 包含 " + pilot);
                }
                return true;
            }
        }
        if (Config.LOG_DEBUG) {
            System.out.println("[KafkaConsumerService] 试点线路匹配失败 - stationName=" + stationName + " 不包含任何试点线路名称");
        }
        return false;
    }

    private void processMessage(String topic, JSONObject message, String busNo) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.auth(Config.REDIS_PASSWORD);

            // 打印消息处理开始日志
            if (Config.LOG_INFO) {
                System.out.println("[KafkaConsumerService] 🔄 开始处理消息 - Topic: " + topic + ", BusNo: " + busNo);
                System.out.println("[KafkaConsumerService] 📋 消息内容摘要: " + message.toString());
            }

            switch (topic) {
                case KafkaConfig.BUS_GPS_TOPIC:
                    int pktType = message.optInt("pktType", 0);
                    if (Config.LOG_INFO) {
                        System.out.println("[KafkaConsumerService] 🚌 GPS主题消息 - pktType: " + pktType + ", busNo: " + busNo);
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
                            if (Config.LOG_INFO) {
                                System.out.println("[KafkaConsumerService] ⚠️ GPS主题消息但无法识别类型 - pktType: " + pktType);
                            }
                        }
                    }
                    break;
                case KafkaConfig.TICKET_TOPIC:
                    if (Config.LOG_INFO) {
                        System.out.println("[KafkaConsumerService] 🎫 票务主题消息 - busNo: " + busNo);
                    }
                    handleTicket(message, busNo, jedis);
                    break;
            }

            // 判断开门/关门
            judgeAndSendDoorSignal(busNo, jedis);
            
            // 打印消息处理完成日志
            if (Config.LOG_INFO) {
                System.out.println("[KafkaConsumerService] ✅ 消息处理完成 - Topic: " + topic + ", BusNo: " + busNo);
            }
        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                System.err.println("[KafkaConsumerService] ❌ 消息处理错误: " + e.getMessage());
                System.err.println("[KafkaConsumerService] 📋 错误消息内容: " + message.toString());
            }
        }
    }



    private void handleGps(JSONObject message, String busNo, Jedis jedis) {
        double lat = message.optDouble("lat");
        double lng = message.optDouble("lng");
        double speed = message.optDouble("speed");
        String trafficType = String.valueOf(message.opt("trafficType"));
        String direction = "4".equals(trafficType) ? "up" : "down";

        // 打印GPS消息处理详情
        if (Config.LOG_INFO) {
            System.out.println("[KafkaConsumerService] 📍 GPS消息处理 - busNo: " + busNo + 
                ", lat: " + lat + ", lng: " + lng + ", speed: " + speed + 
                ", trafficType: " + trafficType + ", direction: " + direction);
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

            // 移除缓存车辆ID高频日志
        }
        String gpsKey = "gps:" + busNo;
        jedis.set(gpsKey, gpsJson.toString());
        jedis.expire(gpsKey, Config.REDIS_TTL_GPS);

        // 移除GPS缓存信息日志

        // 移除GPS缓存调试日志
    }

    private void handleArriveLeave(JSONObject message, String busNo, Jedis jedis) {
        String isArriveOrLeft = String.valueOf(message.opt("isArriveOrLeft"));
        String stationId = message.optString("stationId");
        String stationName = message.optString("stationName");
        String nextStationSeqNum = message.optString("nextStationSeqNum");
        String trafficType2 = String.valueOf(message.opt("trafficType"));
        String direction2 = "4".equals(trafficType2) ? "up" : "down";
        String srcAddrOrg = message.optString("srcAddrOrg");

        // 打印到离站消息处理详情
        if (Config.LOG_INFO) {
            System.out.println("[KafkaConsumerService] 🚏 到离站消息处理 - busNo: " + busNo + 
                ", isArriveOrLeft: " + isArriveOrLeft + ", stationId: " + stationId + 
                ", stationName: " + stationName + ", direction: " + direction2);
        }

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

        // 移除到离站缓存调试日志

        // 移除到站/离站信息日志
    }

    private void handleTicket(JSONObject message, String busNo, Jedis jedis) {
        String cardNo = message.optString("cardNo");
        String stationId = message.optString("stationId");
        String stationName = message.optString("stationName");
        String trafficType = String.valueOf(message.opt("trafficType"));
        String direction = "4".equals(trafficType) ? "up" : "down";
        double amount = message.optDouble("amount", 0.0);

        // 打印票务消息处理详情
        if (Config.LOG_INFO) {
            System.out.println("[KafkaConsumerService] 🎫 票务消息处理 - busNo: " + busNo + 
                ", cardNo: " + cardNo + ", stationId: " + stationId + 
                ", stationName: " + stationName + ", amount: " + amount + 
                ", direction: " + direction);
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

        // 移除票务缓存信息日志

        // 更新站点GPS缓存
        if (message.has("lat") && message.has("lng")) {
            double lat = message.optDouble("lat");
            double lng = message.optDouble("lng");
            double[] stationGps = {lat, lng};
            stationGpsMap.put(stationId, stationGps);

            // 移除更新站点GPS缓存调试日志
        }

        // 移除到离站缓存调试日志
    }

    private void judgeAndSendDoorSignal(String busNo, Jedis jedis) throws JsonProcessingException {
        // 获取缓存数据
        String arriveLeaveStr = jedis.get("arrive_leave:" + busNo);
        String gpsStr = jedis.get("gps:" + busNo);

        if (arriveLeaveStr == null || gpsStr == null) {
            String reason;
            if (arriveLeaveStr == null && gpsStr == null) {
                reason = "缺少arrive_leave与gps";
            } else if (arriveLeaveStr == null) {
                reason = "缺少arrive_leave";
            } else {
                reason = "缺少gps";
            }
            logDoorSkipThrottled(busNo, reason);
            return;
        }

        JSONObject arriveLeave = new JSONObject(arriveLeaveStr);
        JSONObject gps = new JSONObject(gpsStr);

        String stationId = arriveLeave.optString("stationId");
        double busLat = gps.optDouble("lat");
        double busLng = gps.optDouble("lng");
        double speed = gps.optDouble("speed");

        // 移除判门输入调试日志

        // 获取站点GPS
        double[] stationGps = stationGpsMap.getOrDefault(stationId, null);
        boolean hasStationGps = stationGps != null;
        double distance = Double.MAX_VALUE;
        if (hasStationGps) {
            distance = calculateDistance(busLat, busLng, stationGps[0], stationGps[1]);
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
        // 移除判门结果调试日志

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
                logDoorSkipThrottled(busNo, "未找到open_time窗口");
            }
        } else {
            // 数据齐全但条件未触发，低频提示原因
            String arriveFlag = arriveLeave.optString("isArriveOrLeft");
            logDoorSkipThrottled(busNo, "条件未满足: distance=" + distance + "m, speed=" + speed + "m/s, arriveLeave=" + arriveFlag);
        }
    }

    private void logDoorSkipThrottled(String busNo, String reason) {
        long now = System.currentTimeMillis();
        long prev = lastDoorSkipLogMsByBus.getOrDefault(busNo, 0L);
        if (now - prev > 60_000) { // 每车每分钟最多一次
            if (Config.LOG_INFO) {
                System.out.println("[KafkaConsumerService] ⏭️ 未触发开关门: busNo=" + busNo + ", 原因=" + reason);
            }
            lastDoorSkipLogMsByBus.put(busNo, now);
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
