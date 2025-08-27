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
import java.util.Set;

/**
 * Kafka消费者服务，统一消费多个主题，判断开门/关门，发送信号到CV
 * Kafka消费 → 判断信号 → CV发送WebSocket到系统 → CV推送 → 处理OD/大模型 → 发送Kafka。
 */
public class KafkaConsumerService {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JedisPool jedisPool = new JedisPool(Config.REDIS_HOST, Config.REDIS_PORT);
    private KafkaConsumer<String, String> consumer;
    private ExecutorService executorService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // 试点线路
    private static final String[] PILOT_ROUTES = {
            "1001000021",   // 8路
            "1001000055",   // 36路
            "1001000248",   // 316路
            "1001000721",    // 55路
            "3301000100116310"    // 522M路
    };

    // 开关门白名单车辆
    private static final String[] DOOR_SIGNAL_WHITELIST = {
            "2-8091", "2-8089", "2-8117", "2-8116", "2-9050", "2-9059",
            "8-6161", "8-6162", "8-6173", "8-6172", "8-8065", "8-8062"
    };

    // 站点GPS映射
    private final Map<String, double[]> stationGpsMap = new HashMap<>();
    // 判门未触发原因日志的节流：每辆车每分钟最多打印一次
    private static final Map<String, Long> lastDoorSkipLogMsByBus = new ConcurrentHashMap<>();

    // 本地乘客流处理器：用于在判定开/关门后直接触发处理，无需依赖CV回推
    private final PassengerFlowProcessor passengerFlowProcessor = new PassengerFlowProcessor();

    public KafkaConsumerService() {
        System.out.println("[KafkaConsumerService] 构造函数开始执行");
        System.out.println("[KafkaConsumerService] 试点线路配置: " + Arrays.toString(PILOT_ROUTES));
        System.out.println("[KafkaConsumerService] 开关门白名单车辆: " + Arrays.toString(DOOR_SIGNAL_WHITELIST));
        System.out.println("[KafkaConsumerService] 正在加载站点GPS数据...");
        loadStationGpsFromDb();
        
        // 打印bus_no到车牌号的映射关系
        System.out.println("[KafkaConsumerService] 车辆编号与车牌号映射关系:");
        BusPlateMappingUtil.printAllMappings();
        
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

            // 从数据库加载（修正为5个占位符，匹配PILOT_ROUTES数组长度）
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
                // 设置站点GPS缓存过期时间
                jedis.expire("station_gps_map", Config.REDIS_TTL_STATION_GPS);
                if (Config.LOG_INFO) {
                    System.out.println("[KafkaConsumerService] Loaded " + stationGpsMap.size() + " stations from database");
                    System.out.println("[KafkaConsumerService] 试点线路站点GPS数据加载完成，共加载 " + stationGpsMap.size() + " 个站点");
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
                System.out.println("[KafkaConsumerService] 试点线路配置: " + Arrays.toString(PILOT_ROUTES));
                System.out.println("[KafkaConsumerService] 试点线路说明: 8路(1001000021), 36路(1001000055), 316路(1001000248), 55路(1001000721), 522M路(3301000100116310)");
                System.out.println("[KafkaConsumerService] 开关门白名单车辆: " + Arrays.toString(DOOR_SIGNAL_WHITELIST));
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
                        JSONObject message = new JSONObject(record.value());
                        String topic = record.topic();
                        String busNo = message.optString("busSelfNo", message.optString("busNo"));
                        if (busNo.isEmpty()) continue;

                        // 过滤试点线路
                        String routeId = extractRouteId(message, topic);
                        // 对未命中白名单但为到/离站的消息，按开关打印原始Kafka数据
                        if (!isPilotRoute(routeId)) {
                            if (topic.equals(KafkaConfig.BUS_GPS_TOPIC)) {
                                int nonPilotPktType = message.optInt("pktType", 0);
                                if (nonPilotPktType == 4 && Config.ARRIVE_LEAVE_LOG_NON_PILOT_ENABLED && Config.ARRIVE_LEAVE_LOG_ENABLED) {
                                    String isArriveOrLeft = String.valueOf(message.opt("isArriveOrLeft"));
                                    String stationId = message.optString("stationId");
                                    String stationName = message.optString("stationName");
                                    String nextStationSeqNum = message.optString("nextStationSeqNum");
                                    String trafficType2 = String.valueOf(message.opt("trafficType"));
                                    String direction2 = "4".equals(trafficType2) ? "up" : "down";
                                    String routeNo = message.optString("routeNo");

                                    System.out.println("[车辆到离站信号-非白名单] pktType=4 的Kafka原始数据:");
                                    System.out.println("   busNo=" + busNo);
                                    System.out.println("   routeId=" + routeId);
                                    System.out.println("   isArriveOrLeft=" + isArriveOrLeft);
                                    System.out.println("   stationId=" + stationId);
                                    System.out.println("   stationName=" + stationName);
                                    System.out.println("   nextStationSeqNum=" + nextStationSeqNum);
                                    System.out.println("   trafficType=" + trafficType2);
                                    System.out.println("   direction=" + direction2);
                                    System.out.println("   routeNo=" + routeNo);
                                    System.out.println("   完整消息: " + message.toString());
                                    System.out.println("   =============================================================================");
                                }
                            }
                            continue;
                        }
                        
                        // 试点线路匹配成功，但不打印日志，避免刷屏

                        processMessage(topic, message, busNo);
                    } catch (Exception e) {
                        if (Config.LOG_ERROR) {
                            System.err.println("[KafkaConsumerService] Process message error: " + e.getMessage());
                            // 如果是JSON序列化错误，打印更多调试信息
                            if (e.getMessage() != null && e.getMessage().contains("non-finite numbers")) {
                                System.err.println("[KafkaConsumerService] JSON序列化错误详情:");
                                System.err.println("  Topic: " + record.topic());
                                System.err.println("  BusNo: " + busNo);
                                System.err.println("  Message: " + record.value());
                                e.printStackTrace();
                            }
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

    private String extractRouteId(JSONObject message, String topic) {
        if (topic.equals(KafkaConfig.BUS_GPS_TOPIC)) {
            String routeNo = message.optString("routeNo");
            if (routeNo != null && !routeNo.isEmpty()) {
                return routeNo;
            }
        }
        return "";
    }

    private boolean isPilotRoute(String routeId) {
        for (String pilot : PILOT_ROUTES) {
            if (pilot.equals(routeId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查车辆是否在开关门白名单中
     * @param busNo 车辆编号
     * @return 是否在白名单中
     */
    private boolean isDoorSignalWhitelisted(String busNo) {
        for (String whitelistedBus : DOOR_SIGNAL_WHITELIST) {
            if (whitelistedBus.equals(busNo)) {
                return true;
            }
        }
        return false;
    }

    private void processMessage(String topic, JSONObject message, String busNo) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.auth(Config.REDIS_PASSWORD);

            switch (topic) {
                case KafkaConfig.BUS_GPS_TOPIC:
                    int pktType = message.optInt("pktType", 0);
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

                        }
                    }
                    break;
                case KafkaConfig.TICKET_TOPIC:
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

        // 验证GPS坐标有效性，防止JSON序列化问题
        if (!isValidGpsCoordinate(lat, lng)) {
            if (Config.LOG_ERROR) {
                System.err.println("[KafkaConsumerService] 无效GPS坐标，跳过处理: busNo=" + busNo + ", lat=" + lat + ", lng=" + lng);
            }
            return;
        }

        // 验证速度值有效性
        if (Double.isNaN(speed) || Double.isInfinite(speed)) {
            speed = 0.0; // 使用默认值
        }

        // 移除高频GPS处理日志

        // 缓存GPS，设置过期时间
        JSONObject gpsJson = new JSONObject();
        gpsJson.put("lat", lat);
        gpsJson.put("lng", lng);
        gpsJson.put("speed", speed);
        gpsJson.put("direction", direction);
        // 缓存线路信息
        String routeNo = message.optString("routeNo");
        if (routeNo != null && !routeNo.isEmpty()) {
            gpsJson.put("routeNo", routeNo);
        }
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
        String routeNo = message.optString("routeNo");

        // 专门打印车辆到离站信号的Kafka原始数据（可通过配置控制）
        if (Config.ARRIVE_LEAVE_LOG_ENABLED) {
            System.out.println("[车辆到离站信号] pktType=4 的Kafka原始数据:");
            System.out.println("   busNo=" + busNo);
            System.out.println("   isArriveOrLeft=" + isArriveOrLeft);
            System.out.println("   stationId=" + stationId);
            System.out.println("   stationName=" + stationName);
            System.out.println("   nextStationSeqNum=" + nextStationSeqNum);
            System.out.println("   trafficType=" + trafficType2);
            System.out.println("   direction=" + direction2);
            System.out.println("   routeNo=" + routeNo);
            System.out.println("   完整消息: " + message.toString());
            System.out.println("   ================================================================================");
        }

        // 缓存到离站，设置过期时间
        JSONObject arriveLeave = new JSONObject();
        arriveLeave.put("isArriveOrLeft", isArriveOrLeft);
        arriveLeave.put("stationId", stationId);
        arriveLeave.put("stationName", stationName);
        arriveLeave.put("nextStationSeqNum", nextStationSeqNum);
        arriveLeave.put("direction", direction2);
        // 使用routeNo作为线路ID
        if (routeNo != null && !routeNo.isEmpty()) {
            arriveLeave.put("routeNo", routeNo);
        }
        String arriveLeaveKey = "arrive_leave:" + busNo;
        // 附加一致性字段，便于后续判门校验
        if (message.has("busId")) {
            arriveLeave.put("busId", message.optLong("busId"));
        }
        if (message.has("srcAddr")) {
            arriveLeave.put("srcAddr", message.optString("srcAddr"));
        }
        if (message.has("seqNum")) {
            arriveLeave.put("seqNum", message.optLong("seqNum"));
        }
        if (message.has("packetTime")) {
            arriveLeave.put("packetTime", message.optLong("packetTime"));
        }
        jedis.set(arriveLeaveKey, arriveLeave.toString());
        jedis.expire(arriveLeaveKey, Config.REDIS_TTL_ARRIVE_LEAVE);

        // 移除到离站缓存调试日志

        // 移除到站/离站信息日志
    }

    private void handleTicket(JSONObject message, String busNo, Jedis jedis) {
        // 刷卡数据按窗口累计
        String busSelfNo = message.optString("busSelfNo", busNo);
        String tradeTime = message.optString("tradeTime");

        // 只在存在已开启窗口时累计
        String windowId = jedis.get("open_time:" + busNo);
        if (windowId != null && !windowId.isEmpty()) {
            String key = "ticket_count_window:" + busNo + ":" + windowId;
            jedis.incr(key);
            jedis.expire(key, Config.REDIS_TTL_OPEN_TIME);
        }

        // 为兼容原有逻辑，仍维护到离站最近信息（若字段提供）
        String stationId = message.optString("stationId");
        String stationName = message.optString("stationName");
        String routeNo = message.optString("routeNo");
        if (!stationId.isEmpty() || !stationName.isEmpty() || !routeNo.isEmpty()) {
            JSONObject arriveLeaveJson = new JSONObject();
            if (!stationId.isEmpty()) arriveLeaveJson.put("stationId", stationId);
            if (!stationName.isEmpty()) arriveLeaveJson.put("stationName", stationName);
            if (!routeNo.isEmpty()) arriveLeaveJson.put("routeNo", routeNo);
            arriveLeaveJson.put("timestamp", LocalDateTime.now().format(formatter));
            String arriveLeaveKey = "arrive_leave:" + busNo;
            jedis.set(arriveLeaveKey, arriveLeaveJson.toString());
            jedis.expire(arriveLeaveKey, Config.REDIS_TTL_ARRIVE_LEAVE);
        }
    }

    private void judgeAndSendDoorSignal(String busNo, Jedis jedis) throws JsonProcessingException {
        // 白名单检查：只有白名单内的车辆才能触发开关门信号
        if (!isDoorSignalWhitelisted(busNo)) {
            // 移除日志，避免刷屏
            return;
        }

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
        double distance = -1.0; // 使用-1表示无效距离，避免JSON序列化问题
        if (hasStationGps) {
            // 验证GPS坐标的有效性
            if (isValidGpsCoordinate(stationGps[0], stationGps[1]) && isValidGpsCoordinate(busLat, busLng)) {
                distance = calculateDistance(busLat, busLng, stationGps[0], stationGps[1]);
            }
        }
        
        LocalDateTime now = LocalDateTime.now();

        // 判断开门（优先报站 > GPS）
        boolean shouldOpen = false;
        String openReason = "";
        if ("1".equals(arriveLeave.optString("isArriveOrLeft"))) {
            shouldOpen = true; // 报站到站
            openReason = "报站到站信号";
        } else if (hasStationGps && distance >= 0 && distance < Config.OPEN_DISTANCE_THRESHOLD_M && speed < Config.OPEN_SPEED_THRESHOLD_MS) {
            shouldOpen = true; // GPS阈值触发
            openReason = "GPS电子围栏触发(距离" + distance + "m, 速度" + speed + "m/s)";
        }

        // 判断关门（加入去抖与最小开门时长约束）
        // 一致性校验：到/离站的route/busId需与当前缓存一致
        String gpsRouteNo = gps.optString("routeNo", "");
        String arriveRouteNo = arriveLeave.optString("routeNo", "");
        if (!gpsRouteNo.isEmpty() && !arriveRouteNo.isEmpty() && !gpsRouteNo.equals(arriveRouteNo)) {
            logDoorSkipThrottled(busNo, "routeNo不一致，忽略本次到/离站: gps=" + gpsRouteNo + ", arrive=" + arriveRouteNo);
            return;
        }
        String cachedBusId = jedis.get("bus_id:" + busNo);
        String arriveBusId = arriveLeave.has("busId") ? String.valueOf(arriveLeave.optLong("busId")) : "";
        if (cachedBusId != null && !cachedBusId.isEmpty() && !arriveBusId.isEmpty() && !cachedBusId.equals(arriveBusId)) {
            logDoorSkipThrottled(busNo, "busId不一致，忽略本次到/离站: cached=" + cachedBusId + ", arrive=" + arriveBusId);
            return;
        }

        boolean closeCondition = false;
        String closeReason = "";
        boolean isArriveLeaveClose = "2".equals(arriveLeave.optString("isArriveOrLeft"));
        if (isArriveLeaveClose) {
            closeCondition = true; // 报站离站
            closeReason = "报站离站信号";
        } else if (hasStationGps && distance >= 0 && (distance > Config.CLOSE_DISTANCE_THRESHOLD_M || speed > Config.CLOSE_SPEED_THRESHOLD_MS)) {
            closeCondition = true; // GPS阈值触发
            closeReason = "GPS电子围栏触发(距离" + distance + "m, 速度" + speed + "m/s)";
        }

        boolean shouldClose = false;
        String openTimeStrForClose = jedis.get("open_time:" + busNo);
        if (openTimeStrForClose != null) {
            // 最小开门时长约束
            try {
                LocalDateTime openTimeParsed = LocalDateTime.parse(openTimeStrForClose, formatter);
                long openMs = java.time.Duration.between(openTimeParsed, now).toMillis();
                if (closeCondition && openMs >= Config.MIN_DOOR_OPEN_MS) {
                    if (isArriveLeaveClose) {
                        // 报站离站=2：直接允许关门，不做连续计数与顺序标志校验
                        shouldClose = true;
                    } else {
                        // GPS分支：保留连续计数，过滤抖动
                        String counterKey = "door_close_candidate:" + busNo;
                        long c = jedis.incr(counterKey);
                        jedis.expire(counterKey, 10);
                        if (c >= Config.CLOSE_CONSECUTIVE_REQUIRED) {
                            shouldClose = true;
                            jedis.del(counterKey);
                        }
                    }
                } else {
                    // 条件不满足或开门时间不足，重置计数
                    jedis.del("door_close_candidate:" + busNo);
                }
            } catch (Exception e) {
                // 时间解析异常：保守放行（视为已满足最小开门时长）
                if (closeCondition) {
                    shouldClose = true;
                }
            }
        }

        // 移除判门结果调试日志

        if (shouldOpen) {
            String openTimeKey = "open_time:" + busNo;
            String ticketCountKey = "ticket_count_window:" + busNo;
            jedis.set(openTimeKey, now.format(formatter));
            // 改为按窗口计数，不在bus维度单独维护
            jedis.expire(openTimeKey, Config.REDIS_TTL_OPEN_TIME);

            // 试点线路开门流程日志（可通过配置控制）
            if (Config.PILOT_ROUTE_LOG_ENABLED) {
                System.out.println("[试点线路开门流程] 开始发送开门信号到CV系统:");
                System.out.println("   busNo=" + busNo);
                System.out.println("   原因=" + openReason);
                System.out.println("   时间=" + now.format(formatter));
                System.out.println("   站点ID=" + stationId);
                System.out.println("   站点名称=" + arriveLeave.optString("stationName"));
                System.out.println("   线路ID=" + arriveLeave.optString("routeNo", "UNKNOWN"));
                System.out.println("   ================================================================================");
            }

            if (Config.LOG_INFO) {
                System.out.println("[KafkaConsumerService] 发送开门信号到CV系统: busNo=" + busNo +
                    ", 原因=" + openReason + ", 时间=" + now.format(formatter));
            }

            // 发送开门信号到CV
            sendDoorSignalToCV(busNo, "open", now);

            if (Config.LOG_INFO) {
                System.out.println("[KafkaConsumerService] 开门信号处理完成: busNo=" + busNo +
                    ", open_time=" + now.format(formatter) + ", Redis缓存已设置");
            }
            // 记录最近一次到离站标志
            jedis.set("last_arrive_leave_flag:" + busNo, arriveLeave.optString("isArriveOrLeft", ""));
            jedis.expire("last_arrive_leave_flag:" + busNo, Config.REDIS_TTL_ARRIVE_LEAVE);
        } else if (shouldClose) {
            String openTimeStr = jedis.get("open_time:" + busNo);
            if (openTimeStr != null) {
                // 试点线路关门流程日志（可通过配置控制）
                if (Config.PILOT_ROUTE_LOG_ENABLED) {
                    System.out.println("[试点线路关门流程] 开始发送关门信号到CV系统:");
                    System.out.println("   busNo=" + busNo);
                    System.out.println("   原因=" + closeReason);
                    System.out.println("   时间=" + now.format(formatter));
                    System.out.println("   上次开门时间=" + openTimeStr);
                    System.out.println("   站点ID=" + stationId);
                    System.out.println("   站点名称=" + arriveLeave.optString("stationName"));
                    System.out.println("   线路ID=" + arriveLeave.optString("routeNo", "UNKNOWN"));
                    System.out.println("   ================================================================================");
                }

                if (Config.LOG_INFO) {
                    System.out.println("[KafkaConsumerService] 发送关门信号到CV系统: busNo=" + busNo +
                        ", 原因=" + closeReason + ", 时间=" + now.format(formatter) +
                        ", 上次开门时间=" + openTimeStr);
                }

                // 发送关门信号到CV
                sendDoorSignalToCV(busNo, "close", now);

                if (Config.LOG_INFO) {
                    System.out.println("[KafkaConsumerService] 关门信号处理完成: busNo=" + busNo +
                        ", 已发送关门信号到CV系统，并已触发本地OD处理流程");
                }

                // 注意：不再立即清理Redis缓存，让CV系统处理完OD数据后再清理
                // jedis.del("open_time:" + busNo);
                // jedis.del("ticket_count_window:" + busNo);
                // 记录最近一次到离站标志
                jedis.set("last_arrive_leave_flag:" + busNo, arriveLeave.optString("isArriveOrLeft", ""));
                jedis.expire("last_arrive_leave_flag:" + busNo, Config.REDIS_TTL_ARRIVE_LEAVE);
            } else {
                logDoorSkipThrottled(busNo, "未找到open_time窗口");
            }
        } else {
            // 数据齐全但条件未触发，低频提示原因
            String arriveFlag = arriveLeave.optString("isArriveOrLeft");
            String distanceStr = distance >= 0 ? distance + "m" : "无效";
            logDoorSkipThrottled(busNo, "条件未满足: distance=" + distanceStr + ", speed=" + speed + "m/s, arriveLeave=" + arriveFlag);
        }
    }

    private void logDoorSkipThrottled(String busNo, String reason) {
        long now = System.currentTimeMillis();
        long prev = lastDoorSkipLogMsByBus.getOrDefault(busNo, 0L);
        if (now - prev > 60_000) { // 每车每分钟最多一次
            if (Config.LOG_INFO) {
                System.out.println("[KafkaConsumerService] 未触发开关门: busNo=" + busNo + ", 原因=" + reason);
                
                // 关闭状态诊断日志，避免刷屏
                // 如需调试，可临时启用以下代码
                /*
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.auth(Config.REDIS_PASSWORD);
                    
                    // 检查关键状态
                    String openTime = jedis.get("open_time:" + busNo);
                    String arriveLeave = jedis.get("arrive_leave:" + busNo);
                    String gps = jedis.get("gps:" + busNo);
                    
                    System.out.println("[KafkaConsumerService] 车辆 " + busNo + " 状态诊断:");
                    System.out.println("  open_time: " + (openTime != null ? openTime : "NULL"));
                    System.out.println("  arrive_leave: " + (arriveLeave != null ? "EXISTS" : "NULL"));
                    System.out.println("  gps: " + (gps != null ? "EXISTS" : "NULL"));
                    
                    // 检查相关数据
                    Set<String> featuresKeys = jedis.keys("features_set:" + busNo + ":*");
                    Set<String> imageKeys = jedis.keys("image_urls:" + busNo + ":*");
                    Set<String> countKeys = jedis.keys("cv_*_count:" + busNo + ":*");
                    
                    System.out.println("  特征数据: " + (featuresKeys != null ? featuresKeys.size() : 0) + " 个");
                    System.out.println("  图片数据: " + (imageKeys != null ? imageKeys.size() : 0) + " 个");
                    System.out.println("  计数数据: " + (countKeys != null ? countKeys.size() : 0) + " 个");
                    
                } catch (Exception e) {
                    System.err.println("[KafkaConsumerService] 状态诊断失败: " + e.getMessage());
                }
                */
            }
            lastDoorSkipLogMsByBus.put(busNo, now);
        }
    }

    /**
     * 发送开关门信号到CV
     * 现在推送车牌号而不是bus_no，确保与CV系统数据一致
     */
    private void sendDoorSignalToCV(String busNo, String action, LocalDateTime timestamp) {
        try {
            // 获取对应的车牌号
            String plateNumber = BusPlateMappingUtil.getPlateNumber(busNo);
            
            JSONObject doorSignal = new JSONObject();
            doorSignal.put("event", "open_close_door");

            JSONObject data = new JSONObject();
            data.put("bus_no", plateNumber); // 推送车牌号而不是bus_no
            data.put("original_bus_no", busNo); // 保留原始bus_no用于内部处理
            data.put("camera_no", "default"); // 默认摄像头编号
            data.put("action", action);
            data.put("timestamp", timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            doorSignal.put("data", data);

            // 通过WebSocket发送给CV
            WebSocketEndpoint.sendToAll(doorSignal.toString());

            if (Config.LOG_INFO) {
                System.out.println("[KafkaConsumerService] 发送开关门信号到CV系统:");
                System.out.println("   原始bus_no: " + busNo);
                System.out.println("   推送车牌号: " + plateNumber);
                System.out.println("   动作: " + action);
                System.out.println("   时间: " + timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                System.out.println("   完整消息: " + doorSignal.toString());
            }

            // 本地自回推：直接触发 PassengerFlowProcessor 处理开/关门事件
            // 这样即使CV不回发 open_close_door，也能继续OD流程
            try {
                passengerFlowProcessor.processEvent(doorSignal);
            } catch (Exception e) {
                if (Config.LOG_ERROR) {
                    System.err.println("[KafkaConsumerService] Failed to process local door event: " + e.getMessage());
                }
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

    private boolean isValidGpsCoordinate(double lat, double lng) {
        return lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }
}
