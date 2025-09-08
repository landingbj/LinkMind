package ai.servlet.passenger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.json.JSONArray;
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
import java.util.*;
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
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerService.class);

    private final JedisPool jedisPool = new JedisPool(Config.REDIS_HOST, Config.REDIS_PORT);
    private KafkaConsumer<String, String> consumer;
    private ExecutorService executorService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private java.util.concurrent.ScheduledExecutorService overdueScanner;

    // 试点线路
    private static final String[] PILOT_ROUTES = {
            "1001000021",   // 8路
            "1001000055",   // 36路
            "1001000248",   // 316路
            "1001000721",    // 55路
            "3301000100116310"    // 522M路
    };

    // 开关门白名单车辆 - 已注释，只保留试点线路
    /*
    private static final String[] DOOR_SIGNAL_WHITELIST = {
            "2-6764", "2-8087", "2-8110", "2-8091", "2-8089", "2-6796", "2-9181", "2-8198", "2-8119", "2-8118",
            "2-8117", "2-8116", "2-8115", "2-6769", "2-6761", "2-6766", "2-6763", "2-6765", "2-6713", "2-9049",
            "2-9050", "2-8241sy", "2-8249sy", "2-9059", "2-9058", "2-9057", "2-8113", "2-8114", "2-8107", "2-8112",
            "8-9116", "8-9117", "8-6161", "8-6162", "8-6163", "8-6164", "8-9118", "8-6178", "8-6177", "8-6176",
            "8-6175", "8-6174", "8-6173", "8-6172", "8-6171", "8-6170", "8-6169", "8-6168", "8-6062", "8-9081",
            "8-6053", "8-9070", "8-8065", "8-8062", "8-8060", "8-6195", "8-6194", "8-6193", "8-6192", "8-6191"
    };
    */

    // 站点GPS映射
    private final Map<String, double[]> stationGpsMap = new HashMap<>();
    // 判门未触发原因日志的节流：每辆车每分钟最多打印一次
    private static final Map<String, Long> lastDoorSkipLogMsByBus = new ConcurrentHashMap<>();

    // 本地乘客流处理器：用于在判定开/关门后直接触发处理，无需依赖CV回推
    private final PassengerFlowProcessor passengerFlowProcessor = new PassengerFlowProcessor();

    // 异步数据库服务管理器（替换原有的同步数据库服务）
    private final AsyncDbServiceManager asyncDbServiceManager = AsyncDbServiceManager.getInstance();

    // 性能统计相关
    private long lastPerformanceLogTime = System.currentTimeMillis();

    /**
     * 安全地序列化JSON对象，避免循环引用问题
     */
    private String safeJsonToString(JSONObject jsonObject) {
        try {
            // 先尝试直接toString
            return jsonObject.toString();
        } catch (StackOverflowError soe) {
            if (Config.LOG_ERROR) {
                logger.error("[KafkaConsumerService] 检测到JSON循环引用，尝试清理: " + soe.getMessage());
            }

            try {
                // 如果检测到循环引用，尝试手动构建安全的消息
                JSONObject safeMessage = new JSONObject();
                safeMessage.put("event", jsonObject.optString("event", "unknown"));

                if (jsonObject.has("data")) {
                    try {
                        JSONObject data = jsonObject.getJSONObject("data");
                        JSONObject safeData = new JSONObject();
                        // 保留所有对CV至关重要的字段，避免兜底时丢失
                        safeData.put("sqe_no", data.optString("sqe_no", ""));
                        safeData.put("bus_id", data.optString("bus_id", ""));
                        safeData.put("bus_no", data.optString("bus_no", ""));
                        safeData.put("camera_no", data.optString("camera_no", ""));
                        safeData.put("action", data.optString("action", ""));
                        safeData.put("timestamp", data.optString("timestamp", ""));
                        safeData.put("stationId", data.optString("stationId", ""));
                        safeData.put("stationName", data.optString("stationName", ""));
                        safeMessage.put("data", safeData);
                    } catch (Exception dataEx) {
                        if (Config.LOG_ERROR) {
                            logger.error("[KafkaConsumerService] 清理data字段失败: " + dataEx.getMessage());
                        }
                        // 如果data字段有问题，创建一个空的data
                        safeMessage.put("data", new JSONObject());
                    }
                }

                return safeMessage.toString();
            } catch (Exception cleanEx) {
                if (Config.LOG_ERROR) {
                    logger.error("[KafkaConsumerService] 清理JSON失败: " + cleanEx.getMessage());
                }
                // 最后返回一个基本的错误消息
                return "{\"error\":\"JSON循环引用\",\"event\":\"unknown\"}";
            }
        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                logger.error("[KafkaConsumerService] JSON序列化失败，尝试使用Jackson: " + e.getMessage());
            }

            try {
                // 如果JSONObject.toString失败，使用Jackson作为备选方案
                return objectMapper.writeValueAsString(jsonObject.toMap());
            } catch (Exception jacksonError) {
                if (Config.LOG_ERROR) {
                    logger.error("[KafkaConsumerService] Jackson序列化也失败: " + jacksonError.getMessage());
                }
                // 最后尝试手动构建字符串
                return "{\"error\":\"序列化失败\",\"message\":\"" + e.getMessage() + "\"}";
            }
        }
    }

    public KafkaConsumerService() {
        logger.info("[KafkaConsumerService] 构造函数开始执行");
        logger.info("[KafkaConsumerService] 试点线路配置: " + Arrays.toString(PILOT_ROUTES));
        // System.out.println("[KafkaConsumerService] 开关门白名单车辆: " + Arrays.toString(DOOR_SIGNAL_WHITELIST)); // 已注释，只保留试点线路
        logger.info("[KafkaConsumerService] 正在加载站点GPS数据...");
        loadStationGpsFromDb();

        // 映射关系已删除，现在直接使用bus_id
        logger.info("[KafkaConsumerService] 映射关系已删除，现在直接使用bus_id");

        logger.info("[KafkaConsumerService] 构造函数执行完成");
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
                    logger.info("[KafkaConsumerService] Loaded " + stationGpsMap.size() + " stations from Redis cache");
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
                    logger.info("[KafkaConsumerService] Loaded " + stationGpsMap.size() + " stations from database");
                    logger.info("[KafkaConsumerService] 试点线路站点GPS数据加载完成，共加载 " + stationGpsMap.size() + " 个站点");
                }
            }
        } catch (SQLException e) {
            if (Config.LOG_ERROR) {
                logger.error("[KafkaConsumerService] Failed to load station GPS: " + e.getMessage());
            }
        }
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            if (Config.LOG_INFO) {
                logger.info("[KafkaConsumerService] Starting Kafka consumer service, topics=[" +
                        String.join(", ", KafkaConfig.BUS_GPS_TOPIC, KafkaConfig.TICKET_TOPIC) + "]");
                logger.info("[KafkaConsumerService] 试点线路配置: " + Arrays.toString(PILOT_ROUTES));
                logger.info("[KafkaConsumerService] 试点线路说明: 8路(1001000021), 36路(1001000055), 316路(1001000248), 55路(1001000721), 522M路(3301000100116310)");
                // System.out.println("[KafkaConsumerService] 开关门白名单车辆: " + Arrays.toString(DOOR_SIGNAL_WHITELIST)); // 已注释，只保留试点线路
            }
            Properties props = KafkaConfig.getConsumerProperties();
            consumer = new KafkaConsumer<>(props);
            consumer.subscribe(Arrays.asList(
                    KafkaConfig.BUS_GPS_TOPIC,
                    KafkaConfig.TICKET_TOPIC
            ));
            executorService = Executors.newSingleThreadExecutor();
            executorService.submit(this::consumeLoop);

            // 启动超时关门巡检器：每5秒扫描一次
            overdueScanner = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
            overdueScanner.scheduleAtFixedRate(this::scanAndForceCloseOverdueDoors, 5, 5, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (Config.LOG_INFO) {
                logger.info("[KafkaConsumerService] Stopping Kafka consumer service");
            }

            // 关闭Kafka消费者
            if (consumer != null) {
                try {
                    consumer.close();
                    if (Config.LOG_INFO) {
                        logger.info("[KafkaConsumerService] Kafka consumer closed");
                    }
                } catch (Exception e) {
                    if (Config.LOG_ERROR) {
                        logger.error("[KafkaConsumerService] Error closing Kafka consumer: " + e.getMessage());
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
                            logger.info("[KafkaConsumerService] Executor service did not terminate gracefully, forcing shutdown");
                        }
                        // 如果30秒内没有结束，强制关闭
                        executorService.shutdownNow();

                        // 再等待最多10秒
                        if (!executorService.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                            if (Config.LOG_ERROR) {
                                logger.error("[KafkaConsumerService] Executor service did not terminate");
                            }
                        }
                    }

                    if (Config.LOG_INFO) {
                        logger.info("[KafkaConsumerService] Executor service stopped");
                    }
                } catch (InterruptedException e) {
                    if (Config.LOG_ERROR) {
                        logger.error("[KafkaConsumerService] Interrupted while waiting for executor service to terminate: " + e.getMessage());
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
                        logger.info("[KafkaConsumerService] Redis connection pool closed");
                    }
                } catch (Exception e) {
                    if (Config.LOG_ERROR) {
                        logger.error("[KafkaConsumerService] Error closing Redis connection pool: " + e.getMessage());
                    }
                }
            }

            // 关闭异步数据库服务管理器
            if (asyncDbServiceManager != null) {
                try {
                    asyncDbServiceManager.shutdown();
                    if (Config.LOG_INFO) {
                        logger.info("[KafkaConsumerService] Async database service manager shutdown completed");
                    }
                } catch (Exception e) {
                    if (Config.LOG_ERROR) {
                        logger.error("[KafkaConsumerService] Error shutting down async database service manager: " + e.getMessage());
                    }
                }
            }

            if (Config.LOG_INFO) {
                logger.info("[KafkaConsumerService] Kafka consumer service stopped completely");
            }

            // 关闭超时关门巡检器
            if (overdueScanner != null) {
                try {
                    overdueScanner.shutdown();
                    overdueScanner.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } finally {
                    overdueScanner.shutdownNow();
                }
            }
        }
    }

    private void consumeLoop() {
        if (Config.LOG_INFO) {
            logger.info("[KafkaConsumerService] Enter consume loop");
        }
        while (running.get()) {
            try {
                // 减少poll超时时间，确保能够快速响应停止信号
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));

                // 定期打印异步数据库性能统计（每5分钟一次）
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastPerformanceLogTime > 300000) { // 5分钟
                    asyncDbServiceManager.printPerformanceStats();
                    lastPerformanceLogTime = currentTime;
                }
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        JSONObject message = new JSONObject(record.value());
                        String topic = record.topic();
                        // 修复：统一使用busNo作为核心车辆标识
                        // 到离站信号使用busNo，刷卡数据使用busSelfNo，但都映射到busNo
                        String busNo = message.optString("busNo", message.optString("busSelfNo"));
                        if (busNo.isEmpty()) continue;

                        // 设置当前处理的topic
                        setCurrentTopic(topic);

        // 第一时间（无条件保存监听到的msg，确保数据完整性）
        String sqeNo = getCurrentSqeNoFromRedis(busNo);
        saveMessage(topic, message, busNo, sqeNo);

                        // 票务数据处理：第一时间保存到数据库
                        if (topic.equals(KafkaConfig.TICKET_TOPIC)) {
                            logger.info("[票务数据接收] 收到票务Kafka原始数据:");
                            logger.info("   topic=" + topic);
                            logger.info("   busNo=" + busNo);
                            logger.info("   完整消息: " + message.toString());
                            logger.info("   =============================================================================");

                            // 第一时间保存刷卡数据到数据库（无条件保存，确保数据完整性）
                            handleCardSwipeDataImmediate(message, busNo);

                            // 继续原有的票务处理逻辑
                            processMessage(topic, message, busNo);
                            continue;
                        }

                        // 过滤试点线路（仅对GPS和到离站数据）
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
                                    // direction映射逻辑：4=上行，5=下行，6=上行，其他=原始trafficType值
                                    String direction2 = "4".equals(trafficType2) || "6".equals(trafficType2) ? "up" :
                                                       "5".equals(trafficType2) ? "down" : trafficType2;
                                    String routeNo = message.optString("routeNo");

                                    logger.info("[车辆到离站信号-非白名单] pktType=4 的Kafka原始数据:");
                                    logger.info("   busNo=" + busNo);
                                    logger.info("   routeId=" + routeId);
                                    logger.info("   isArriveOrLeft=" + isArriveOrLeft);
                                    logger.info("   stationId=" + stationId);
                                    logger.info("   stationName=" + stationName);
                                    logger.info("   nextStationSeqNum=" + nextStationSeqNum);
                                    logger.info("   trafficType=" + trafficType2);
                                    logger.info("   direction=" + direction2);
                                    logger.info("   routeNo=" + routeNo);
                                    logger.info("   完整消息: " + message.toString());
                                    logger.info("   =============================================================================");
                                }
                            }
                            continue;
                        }

                        // 试点线路匹配成功，但不打印日志，避免刷屏
                        if (Config.PILOT_ROUTE_LOG_ENABLED) {
                            logger.info("[试点线路匹配] 车辆 " + busNo + " 匹配试点线路 " + routeId + "，开始处理消息");
                        }

                        processMessage(topic, message, busNo);
                    } catch (Exception e) {
                        if (Config.LOG_ERROR) {
                            logger.error("[KafkaConsumerService] Process message error: " + e.getMessage());
                            // 如果是JSON序列化错误，打印更多调试信息
                            if (e.getMessage() != null && e.getMessage().contains("non-finite numbers")) {
                                logger.error("[KafkaConsumerService] JSON序列化错误详情:");
                                logger.error("  Topic: " + record.topic());
                                logger.error("  Message: " + record.value());
                                e.printStackTrace();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (running.get()) { // 只有在服务运行时才记录错误
                    if (Config.LOG_ERROR) {
                        logger.error("[KafkaConsumerService] Error in consume loop: " + e.getMessage());
                    }
                }
            }
        }
        if (Config.LOG_INFO) {
            logger.info("[KafkaConsumerService] Exit consume loop");
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
     * 检查车辆是否在开关门白名单中 - 已注释，只保留试点线路
     * @param busNo 车辆编号
     * @return 是否在白名单中
     */
    /*
    private boolean isDoorSignalWhitelisted(String busNo) {
        for (String whitelistedBus : DOOR_SIGNAL_WHITELIST) {
            if (whitelistedBus.equals(busNo)) {
                return true;
            }
        }
        return false;
    }
    */

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
                    // 票务数据不进行试点线路过滤，直接处理
                    logger.info("[票务数据处理] 收到票务Kafka数据:");
                    logger.info("   topic=" + topic);
                    logger.info("   busNo=" + busNo);
                    logger.info("   完整消息: " + message.toString());
                    logger.info("   =============================================================================");
                    handleTicket(message, busNo, jedis);
                    break;
            }

            // 判断开门/关门
            judgeAndSendDoorSignal(topic, message, busNo, jedis);
        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                logger.error("[KafkaConsumerService] Process message error: " + e.getMessage());
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
                logger.error("[KafkaConsumerService] 无效GPS坐标，跳过处理: busNo=" + busNo + ", lat=" + lat + ", lng=" + lng);
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
        // direction映射逻辑：4=上行，5=下行，6=上行，其他=原始trafficType值
        String direction2 = "4".equals(trafficType2) || "6".equals(trafficType2) ? "up" :
                           "5".equals(trafficType2) ? "down" : trafficType2;
        String routeNo = message.optString("routeNo");

        // 第一时间保存到离站数据到数据库（无条件保存，确保数据完整性）
        handleArriveLeaveDataImmediate(message, busNo, routeNo, isArriveOrLeft, stationName);

        // 收集原始Kafka数据用于校验
        collectBusGpsMsg(busNo, message, jedis);

        // 处理到离站数据：检查是否为试点线路车辆并保存到数据库
        //handleArriveLeaveData(message, busNo, routeNo, isArriveOrLeft, stationName);

        // 对白名单中的车辆打印完整的到离站Kafka原始数据 - 已注释，只保留试点线路
        /*
        if (isDoorSignalWhitelisted(busNo)) {
            logger.info("[白名单车辆到离站信号] pktType=4 的Kafka原始数据:");
            logger.info("   busNo=" + busNo);
            logger.info("   isArriveOrLeft=" + isArriveOrLeft);
            logger.info("   stationId=" + stationId);
            logger.info("   stationName=" + stationName);
            logger.info("   nextStationSeqNum=" + nextStationSeqNum);
            logger.info("   trafficType=" + trafficType2);
            logger.info("   direction=" + direction2);
            logger.info("   routeNo=" + routeNo);
            logger.info("   完整消息: " + message.toString());
            logger.info("   =============================================================================");
        }
        */

        // 缓存到离站，设置过期时间
        JSONObject arriveLeave = new JSONObject();
        arriveLeave.put("isArriveOrLeft", isArriveOrLeft);
        arriveLeave.put("stationId", stationId);
        arriveLeave.put("stationName", stationName);
        arriveLeave.put("nextStationSeqNum", nextStationSeqNum);
        arriveLeave.put("direction", direction2);
        // 增加时间戳信息，便于调试和时序分析
        arriveLeave.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        arriveLeave.put("updateTime", System.currentTimeMillis());
        arriveLeave.put("trafficType", trafficType2);
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

        // 增加到离站信号调试日志
        if (Config.PILOT_ROUTE_LOG_ENABLED) {
            logger.info("[到离站信号] 收到信号: busNo=" + busNo +
                ", isArriveOrLeft=" + isArriveOrLeft +
                ", stationId=" + stationId +
                ", stationName=" + stationName +
                ", trafficType=" + trafficType2 +
                ", direction=" + direction2 +
                ", timestamp=" + arriveLeave.optString("timestamp"));
        }
    }

    private void handleTicket(JSONObject message, String busNo, Jedis jedis) {
        // 刷卡数据按窗口累计
        String busSelfNo = message.optString("busSelfNo", busNo);
        String tradeTime = message.optString("tradeTime");
        String cardNo = message.optString("cardNo");
        String cardType = message.optString("cardType");
        String childCardType = message.optString("childCardType");
        String onOff = message.optString("onOff");

        logger.info("[票务数据处理] 开始处理票务数据:");
        logger.info("   busNo=" + busNo);
        logger.info("   busSelfNo=" + busSelfNo);
        logger.info("   tradeTime=" + tradeTime);
        logger.info("   cardNo=" + cardNo);
        logger.info("   cardType=" + cardType);
        logger.info("   childCardType=" + childCardType);
        logger.info("   onOff=" + onOff);

        // 只在存在已开启窗口时累计
        String windowId = jedis.get("open_time:" + busNo);
        logger.info("   检查开门窗口: open_time:" + busNo + " = " + windowId);

        if (windowId != null && !windowId.isEmpty()) {
            // 判断上下车方向
            String direction = "up"; // 默认为上车
            if (onOff != null && ("down".equalsIgnoreCase(onOff) || "DOWN".equalsIgnoreCase(onOff))) {
                direction = "down";
            } else if (onOff == null) {
                // onOff为null时，根据业务规则默认为上车
                logger.info("   [上下车判断] onOff为null，默认为上车");
            }

            // 创建刷卡记录详情
            JSONObject ticketDetail = new JSONObject();
            ticketDetail.put("busSelfNo", busSelfNo);
            ticketDetail.put("cardNo", cardNo);
            ticketDetail.put("cardType", cardType);
            ticketDetail.put("childCardType", childCardType);
            ticketDetail.put("tradeTime", tradeTime);
            // 处理onOff字段：null值转换为"unknown"，便于下游系统处理
            ticketDetail.put("onOff", onOff != null ? onOff : "unknown");
            ticketDetail.put("direction", direction.equals("up") ? "上车" : "下车");

            // 存储到对应的上下车集合中
            String detailKey = "ticket_detail:" + busNo + ":" + windowId + ":" + direction;
            jedis.sadd(detailKey, ticketDetail.toString());
            jedis.expire(detailKey, Config.REDIS_TTL_OPEN_TIME);

            // 更新上下车计数
            String countKey = "ticket_count:" + busNo + ":" + windowId + ":" + direction;
            long count = jedis.incr(countKey);
            jedis.expire(countKey, Config.REDIS_TTL_OPEN_TIME);

            logger.info("   [票务计数] " + (direction.equals("up") ? "上车" : "下车") + "刷卡计数已更新: " + countKey + " = " + count);
            logger.info("   [票务详情] 刷卡详情已存储: " + detailKey);
        } else {
            // 🔥 新增：无窗口时写入等待队列
            logger.info("   [票务计数] 未找到开门窗口，写入等待队列");
            writeToWaitQueue(message, busNo);
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
            logger.info("   [到离站信息] 已更新到离站信息: " + arriveLeaveKey);
        }

        logger.info("   [票务数据处理] 处理完成");
        logger.info("   ================================================================================");
    }

    private void judgeAndSendDoorSignal(String topic, JSONObject message, String busNo, Jedis jedis) throws JsonProcessingException {
        // 白名单检查：只有白名单内的车辆才能触发开关门信号 - 已注释，只保留试点线路
        /*
        if (!isDoorSignalWhitelisted(busNo)) {
            // 移除日志，避免刷屏
            return;
        }
        */

        // 第一时间（无条件保存监听到的msg，确保数据完整性）
        // saveMessage(topic, message, busNo);

        // 获取缓存数据（仅到离站）
        String arriveLeaveStr = jedis.get("arrive_leave:" + busNo);

        if (arriveLeaveStr == null) {
            logDoorSkipThrottled(busNo, "缺少arrive_leave");
            return;
        }

        JSONObject arriveLeave = new JSONObject(arriveLeaveStr);
        String stationId = arriveLeave.optString("stationId");

        // 检查是否处理过相同的到离站信号（基于seqNum和timestamp）
        String isArriveOrLeft = arriveLeave.optString("isArriveOrLeft");
        String seqNum = arriveLeave.optString("seqNum");
        String timestamp = arriveLeave.optString("timestamp");

        if (seqNum != null && !seqNum.isEmpty() && timestamp != null && !timestamp.isEmpty()) {
            String processedKey = "processed_signal:" + busNo + ":" + seqNum + ":" + timestamp;
            if (jedis.get(processedKey) != null) {
                logDoorSkipThrottled(busNo, "已处理过相同信号，跳过: seqNum=" + seqNum + ", timestamp=" + timestamp);
                return;
            }
            // 标记该信号已处理，设置较短的过期时间（避免Redis内存过多）
            jedis.set(processedKey, "1");
            jedis.expire(processedKey, 300); // 5分钟过期
        }

        // 移除判门输入调试日志

        // 仅依赖到离站数据进行判定
        double distance = -1.0;
        double speed = -1.0;

        LocalDateTime now = LocalDateTime.now();

        // 判断开门（优先报站 > GPS）
        boolean shouldOpen = false;
        String openReason = "";

        if ("1".equals(arriveLeave.optString("isArriveOrLeft"))) {
            // 到站信号：直接开门
            shouldOpen = true;
            openReason = "报站到站信号";
        }

        // 判断关门（加入去抖与最小开门时长约束）
        // 放宽一致性校验：只做基本的数据完整性检查，不过于严格
        boolean closeCondition = false;
        String closeReason = "";
        boolean isArriveLeaveClose = "2".equals(arriveLeave.optString("isArriveOrLeft"));
        if (isArriveLeaveClose) {
            closeCondition = true; // 报站离站
            closeReason = "报站离站信号";
        }

        // 增加关门超时机制：如果开门时间超过最大允许时长，强制关门
        boolean shouldClose = false;
        String openTimeStrForClose = jedis.get("open_time:" + busNo);
        if (openTimeStrForClose != null) {
            try {
                LocalDateTime openTimeParsed;
                // 兼容两种时间格式：yyyy-MM-dd HH:mm:ss 和 yyyy-MM-ddTHH:mm:ss
                if (openTimeStrForClose.contains("T")) {
                    openTimeParsed = LocalDateTime.parse(openTimeStrForClose);
                } else {
                    openTimeParsed = LocalDateTime.parse(openTimeStrForClose, formatter);
                }
                long openMs = java.time.Duration.between(openTimeParsed, now).toMillis();

                // 关门条件判断
                if (closeCondition && openMs >= Config.MIN_DOOR_OPEN_MS) {
                    // 报站离站=2：直接允许关门
                    shouldClose = true;
                } else if (openMs >= Config.MAX_DOOR_OPEN_MS) {
                    // 超时强制关门：防止车门长时间不关闭
                    shouldClose = true;
                    closeReason = "超时强制关门(" + (openMs / 1000) + "秒)";
                    if (Config.LOG_INFO) {
                        logger.info("[KafkaConsumerService] 车辆 " + busNo + " 开门超时，强制关门: " + openMs + "ms");
                    }
                } else {
                    // 条件不满足或开门时间不足
                }
            } catch (Exception e) {
                // 时间解析异常：保守放行（视为已满足最小开门时长）
                if (closeCondition) {
                    shouldClose = true;
                }
                if (Config.LOG_ERROR) {
                    logger.error("[KafkaConsumerService] 时间解析异常，车辆 " + busNo + ": " + e.getMessage());
                }
            }
        }

        // 移除判门结果调试日志

        if (shouldOpen) {
            String openTimeKey = "open_time:" + busNo;
            String lastOpenStr = jedis.get(openTimeKey);
            // 开门防抖：同一车辆在指定秒内不重复开门且不重置窗口
            if (lastOpenStr != null && !lastOpenStr.isEmpty()) {
                try {
                    LocalDateTime lastOpen = lastOpenStr.contains("T") ? LocalDateTime.parse(lastOpenStr) : LocalDateTime.parse(lastOpenStr, formatter);
                    long sinceLastOpenSec = java.time.Duration.between(lastOpen, now).getSeconds();
                    if (sinceLastOpenSec < Config.OPEN_DEBOUNCE_SECONDS) {
                        logDoorSkipThrottled(busNo, "开门防抖(" + sinceLastOpenSec + "s<" + Config.OPEN_DEBOUNCE_SECONDS + ")，忽略重复");
                        // 仅刷新到离站态标记有效期
                        jedis.expire("arrive_leave:" + busNo, Config.REDIS_TTL_ARRIVE_LEAVE);
                        return;
                    }
                    // 若同一站内重复到站，保持首次窗口，不重置
                    String openedStation = jedis.get("open_station:" + busNo);
                    if (openedStation != null && openedStation.equals(stationId)) {
                        logDoorSkipThrottled(busNo, "同站重复到站，保持首开门窗口，不重置");
                        jedis.expire(openTimeKey, Config.REDIS_TTL_OPEN_TIME);
                        jedis.expire("open_station:" + busNo, Config.REDIS_TTL_OPEN_TIME);
                        return;
                    }
                } catch (Exception ignore) {}
            }

            String ticketCountKey = "ticket_count_window:" + busNo;
            jedis.set(openTimeKey, now.format(formatter));
            jedis.expire(openTimeKey, Config.REDIS_TTL_OPEN_TIME);
            // 将车辆加入开门集合，供巡检器扫描
            try { jedis.sadd("open_buses", busNo); jedis.expire("open_buses", Config.REDIS_TTL_OPEN_TIME); } catch (Exception ignore) {}
            // 记录开门站点，便于同站重复开门忽略
            if (stationId != null && !stationId.isEmpty()) {
                jedis.set("open_station:" + busNo, stationId);
                jedis.expire("open_station:" + busNo, Config.REDIS_TTL_OPEN_TIME);
            }

            // 映射关系已删除，现在直接使用bus_id，不再需要维护车牌映射

            // 试点线路开门流程日志（可通过配置控制）
            if (Config.PILOT_ROUTE_LOG_ENABLED) {
                logger.info("[试点线路开门流程] 开始发送开门信号到CV系统:");
                logger.info("   busNo=" + busNo);
                logger.info("   原因=" + openReason);
                logger.info("   时间=" + now.format(formatter));
                logger.info("   站点ID=" + stationId);
                logger.info("   站点名称=" + arriveLeave.optString("stationName"));
                logger.info("   线路ID=" + arriveLeave.optString("routeNo", "UNKNOWN"));
                logger.info("   ================================================================================");
            }

            if (Config.LOG_INFO) {
                logger.info("[KafkaConsumerService] 发送开门信号到CV系统: busNo=" + busNo +
                    ", 原因=" + openReason + ", 时间=" + now.format(formatter));
            }

            // 发送开门信号到CV（bus_no为车牌号由sendDoorSignalToCV内部映射）
            sendDoorSignalToCV(busNo, "open", now);

            if (Config.LOG_INFO) {
                logger.info("[KafkaConsumerService] 开门信号处理完成: busNo=" + busNo +
                    ", open_time=" + now.format(formatter) + ", Redis缓存已设置");
            }
            // 记录最近一次到离站标志
            jedis.set("last_arrive_leave_flag:" + busNo, arriveLeave.optString("isArriveOrLeft", ""));
            jedis.expire("last_arrive_leave_flag:" + busNo, Config.REDIS_TTL_ARRIVE_LEAVE);
        } else if (shouldClose) {
            String openTimeStr = jedis.get("open_time:" + busNo);
            if (openTimeStr != null) {
                // 幂等：该开门窗口是否已发过关门
                String closeSentKey = "close_sent:" + busNo + ":" + (openTimeStr.contains("T") ? openTimeStr.replace("T", " ") : openTimeStr);
                if (jedis.get(closeSentKey) != null) {
                    logDoorSkipThrottled(busNo, "该开门窗口已发送过关门，忽略重复");
                    return;
                }
                // 试点线路关门流程日志（可通过配置控制）
                if (Config.PILOT_ROUTE_LOG_ENABLED) {
                    logger.info("[试点线路关门流程] 开始发送关门信号到CV系统:");
                    logger.info("   busNo=" + busNo);
                    logger.info("   原因=" + closeReason);
                    logger.info("   时间=" + now.format(formatter));
                    logger.info("   上次开门时间=" + openTimeStr);
                    logger.info("   站点ID=" + stationId);
                    logger.info("   站点名称=" + arriveLeave.optString("stationName"));
                    logger.info("   线路ID=" + arriveLeave.optString("routeNo", "UNKNOWN"));
                    logger.info("   ================================================================================");
                }

                if (Config.LOG_INFO) {
                    logger.info("[KafkaConsumerService] 发送关门信号到CV系统: busNo=" + busNo +
                        ", 原因=" + closeReason + ", 时间=" + now.format(formatter) +
                        ", 上次开门时间=" + openTimeStr);
                }

                // 发送关门信号到CV
                sendDoorSignalToCV(busNo, "close", now);

                if (Config.LOG_INFO) {
                    logger.info("[KafkaConsumerService] 关门信号处理完成: busNo=" + busNo +
                        ", 已发送关门信号到CV系统，并已触发本地OD处理流程");
                }

                // 注意：不再立即清理Redis缓存，让CV系统处理完OD数据后再清理
                // jedis.del("open_time:" + busNo);
                // jedis.del("ticket_count_window:" + busNo);
                // 设置关门幂等标记
                jedis.set(closeSentKey, "1");
                jedis.expire(closeSentKey, Config.REDIS_TTL_OPEN_TIME);
                // 从开门集合移除
                try { jedis.srem("open_buses", busNo); } catch (Exception ignore) {}
                // 记录最近一次到离站标志
                jedis.set("last_arrive_leave_flag:" + busNo, arriveLeave.optString("isArriveOrLeft", ""));
                jedis.expire("last_arrive_leave_flag:" + busNo, Config.REDIS_TTL_ARRIVE_LEAVE);
            } else {
                logDoorSkipThrottled(busNo, "未找到open_time窗口");
            }
        } else {
            // 数据齐全但条件未触发，低频提示原因
            String arriveFlag = arriveLeave.optString("isArriveOrLeft");
            logDoorSkipThrottled(busNo, "条件未满足: arriveLeave=" + arriveFlag);
        }
    }

    private void logDoorSkipThrottled(String busNo, String reason) {
        long now = System.currentTimeMillis();
        long prev = lastDoorSkipLogMsByBus.getOrDefault(busNo, 0L);
        if (now - prev > 60_000) { // 每车每分钟最多一次
            if (Config.LOG_INFO) {
                logger.info("[KafkaConsumerService] 未触发开关门: busNo=" + busNo + ", 原因=" + reason);

                // 关闭状态诊断日志，避免刷屏
                // 如需调试，可临时启用以下代码
                /*
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.auth(Config.REDIS_PASSWORD);

                    // 检查关键状态
                    String openTime = jedis.get("open_time:" + busNo);
                    String arriveLeave = jedis.get("arrive_leave:" + busNo);
                    String gps = jedis.get("gps:" + busNo);

                    logger.info("[KafkaConsumerService] 车辆 " + busNo + " 状态诊断:");
                    logger.info("  open_time: " + (openTime != null ? openTime : "NULL"));
                    logger.info("  arrive_leave: " + (arriveLeave != null ? "EXISTS" : "NULL"));
                    logger.info("  gps: " + (gps != null ? "EXISTS" : "NULL"));

                    // 检查相关数据
                    Set<String> featuresKeys = jedis.keys("features_set:" + busNo + ":*");
                    Set<String> imageKeys = jedis.keys("image_urls:" + busNo + ":*");
                    Set<String> countKeys = jedis.keys("cv_*_count:" + busNo + ":*");

                    logger.info("  特征数据: " + (featuresKeys != null ? featuresKeys.size() : 0) + " 个");
                    logger.info("  图片数据: " + (imageKeys != null ? imageKeys.size() : 0) + " 个");
                    logger.info("  计数数据: " + (countKeys != null ? countKeys.size() : 0) + " 个");

                } catch (Exception e) {
                    logger.error("[KafkaConsumerService] 状态诊断失败: " + e.getMessage());
                }
                */
            }
            lastDoorSkipLogMsByBus.put(busNo, now);
        }
    }

    /**
     * 发送开关门信号到CV
     * 严格按照与CV约定的WebSocket数据格式发送
     */
    private void sendDoorSignalToCV(String busNo, String action, LocalDateTime timestamp) {
        try {
            // 现在直接使用busNo作为bus_id，不再需要映射
            String busId = busNo;

            // 获取当前站点信息
            String stationId = "UNKNOWN";
            String stationName = "Unknown Station";
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.auth(Config.REDIS_PASSWORD);
                String arriveLeaveStr = jedis.get("arrive_leave:" + busNo);
                if (arriveLeaveStr != null) {
                    JSONObject arriveLeave = new JSONObject(arriveLeaveStr);
                    stationId = arriveLeave.optString("stationId", "UNKNOWN");
                    stationName = arriveLeave.optString("stationName", "Unknown Station");
                }
            } catch (Exception e) {
                if (Config.LOG_ERROR) {
                    logger.error("[KafkaConsumerService] 获取站点信息失败: " + e.getMessage());
                }
            }

            //  获取/生成开关门唯一批次号：开门生成，关门复用
            String sqeNo;
            if ("close".equals(action)) {
                // 关门：优先复用开门时记录的current_sqe_no
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.auth(Config.REDIS_PASSWORD);
                    String canonicalKey = "current_sqe_no:" + busId;
                    String plateKey = "current_sqe_no:" + busNo;
                    String reused = jedis.get(canonicalKey);
                    if (reused == null || reused.isEmpty()) {
                        reused = jedis.get(plateKey);
                    }
                    if (reused != null && !reused.isEmpty()) {
                        sqeNo = reused;
                        if (Config.LOG_INFO) {
                            logger.info("[SqeNo复用] 关门复用开门sqe_no: " + sqeNo + ", busNo=" + busNo);
                        }
                    } else {
                        // 兜底：极端情况下未取到则生成，但同时打印告警
                        sqeNo = generateSqeNo(busNo, timestamp, action);
                        if (Config.LOG_ERROR) {
                            logger.error("[SqeNo复用告警] 未找到当前开门批次号，关门将临时生成新的sqe_no: " + sqeNo + ", busNo=" + busNo);
                        }
                    }
                }
            } else {
                // 开门：生成新的sqe_no
                sqeNo = generateSqeNo(busNo, timestamp, action);
            }

            // 严格按照约定格式构建消息
            JSONObject doorSignal = new JSONObject();
            doorSignal.put("event", "open_close_door");

            JSONObject data = new JSONObject();
            data.put("bus_no", "default"); // 车牌号，没有地方获取，传default
            data.put("bus_id", busId); // 车辆ID（到离站中的busNo）
            data.put("camera_no", "default"); // 摄像头编号，没有地方获取，传default
            data.put("action", action); // open 或 close
            data.put("sqe_no", sqeNo); //  新增：开关门唯一批次号
            data.put("timestamp", timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            data.put("stationId", stationId); // 站点ID
            data.put("stationName", stationName); // 站点名称

            doorSignal.put("data", data);

            //  调试：打印发送给CV的完整消息，确认sqe_no是否正确
            if (Config.LOG_DEBUG) {
                logger.info("[KafkaConsumerService]  发送给CV的WebSocket消息:");
                logger.info("   sqe_no: " + sqeNo);
                logger.info("   完整消息: " + doorSignal.toString());
                logger.info("   ================================================================================");
            }

            // 使用安全的JSON序列化方法
            String messageJson = safeJsonToString(doorSignal);

            //  二次确认：检查序列化后的消息是否包含sqe_no
            if (Config.LOG_DEBUG) {
                logger.info("[KafkaConsumerService]  序列化后的消息:");
                logger.info("   包含sqe_no: " + messageJson.contains("sqe_no"));
                logger.info("   sqe_no值: " + (messageJson.contains(sqeNo) ? sqeNo : "NOT_FOUND"));
                logger.info("   ================================================================================");
            }

            // 通过WebSocket发送给CV
            WebSocketEndpoint.sendToAll(messageJson);

            // 关键日志：发送开/关门时打印sqe_no
            if (Config.LOG_INFO) {
                logger.info("[KafkaConsumerService] 发送" + (action.equals("open") ? "开门" : "关门") +
                    "信号到CV系统: busNo=" + busNo + ", busId=" + busId + ", sqe_no=" + sqeNo);
            }

            // 保存WebSocket消息到数据库
            saveOpenCloseDoorMessage(data.optString("bus_no"), data.optString("bus_id"),
                data.optString("camera_no"), action, data.optString("timestamp"),
                stationId, stationName, data);

            // 本地自回推：直接触发 PassengerFlowProcessor 处理开/关门事件
            // 这样即使CV不回发 open_close_door，也能继续OD流程
            try {
                passengerFlowProcessor.processEvent(doorSignal);
            } catch (Exception e) {
                if (Config.LOG_ERROR) {
                    logger.error("[KafkaConsumerService] Failed to process local door event: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                logger.error("[KafkaConsumerService] Failed to send door signal to CV: " + e.getMessage());
            }
        }
    }

    /**
     * 扫描并强制关闭超时未关门的车辆
     * 不依赖新的到离站消息驱动，保证MAX_DOOR_OPEN_MS生效
     */
    private void scanAndForceCloseOverdueDoors() {
        if (!running.get()) return;
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.auth(Config.REDIS_PASSWORD);
            Set<String> openBuses = jedis.smembers("open_buses");
            if (openBuses == null || openBuses.isEmpty()) return;
            LocalDateTime now = LocalDateTime.now();
            for (String busNo : openBuses) {
                try {
                    String openTimeStr = jedis.get("open_time:" + busNo);
                    if (openTimeStr == null || openTimeStr.isEmpty()) {
                        // 键不存在则从集合移除，避免脏数据
                        jedis.srem("open_buses", busNo);
                        continue;
                    }
                    LocalDateTime openTime = openTimeStr.contains("T") ? LocalDateTime.parse(openTimeStr) : LocalDateTime.parse(openTimeStr, formatter);
                    long openMs = java.time.Duration.between(openTime, now).toMillis();
                    if (openMs >= Config.MAX_DOOR_OPEN_MS) {
                        // 幂等：避免重复对同一窗口强制关门
                        String closeSentKey = "close_sent:" + busNo + ":" + (openTimeStr.contains("T") ? openTimeStr.replace("T", " ") : openTimeStr);
                        if (jedis.get(closeSentKey) != null) {
                            jedis.srem("open_buses", busNo);
                            continue;
                        }
                        if (Config.LOG_INFO) {
                            logger.info("[超时巡检] 车辆 " + busNo + " 开门超时(" + (openMs/1000) + "s) ，触发强制关门");
                        }
                        // 触发关门
                        sendDoorSignalToCV(busNo, "close", now);
                        // 设置关门幂等标记
                        jedis.set(closeSentKey, "1");
                        jedis.expire(closeSentKey, Config.REDIS_TTL_OPEN_TIME);
                        // 从集合移除
                        jedis.srem("open_buses", busNo);
                    }
                } catch (Exception perBusEx) {
                    if (Config.LOG_ERROR) {
                        logger.error("[超时巡检] 处理车辆 " + busNo + " 失败: " + perBusEx.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                logger.error("[超时巡检] 扫描失败: " + e.getMessage());
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

    /**
     * 收集车辆到离站信号的原始Kafka数据
     * @param busNo 车辆编号
     * @param message 原始Kafka消息
     * @param jedis Redis连接
     */
    private void collectBusGpsMsg(String busNo, JSONObject message, Jedis jedis) {
        try {
            String isArriveOrLeft = String.valueOf(message.opt("isArriveOrLeft"));
            String eventType = "1".equals(isArriveOrLeft) ? "door_open" : "door_close";
            String stationId = message.optString("stationId");
            String stationName = message.optString("stationName");

            // 获取当前车辆的sqeNo
            String sqeNo = getCurrentSqeNoFromRedis(busNo);

            // 构建包含事件类型和原始Kafka数据的JSON对象
            JSONObject gpsMsg = new JSONObject();
            gpsMsg.put("eventType", eventType);
            gpsMsg.put("kafkaData", message);
            gpsMsg.put("stationId", stationId);
            gpsMsg.put("stationName", stationName);
            gpsMsg.put("timestamp", message.optString("gmtTime"));
            gpsMsg.put("sqeNo", sqeNo); // 添加sqeNo字段

            // 增强存储策略：同时使用多种key存储，提高检索成功率
            List<String> keys = new ArrayList<>();

            // 方式1：按站点分组存储（原有逻辑）
            if (stationId != null && !stationId.isEmpty()) {
                keys.add("bus_gps_msg:" + busNo + ":" + stationId);
            }

            // 方式2：按sqeNo存储（新增逻辑）
            if (sqeNo != null && !sqeNo.isEmpty()) {
                keys.add("bus_gps_msg:" + sqeNo);
            }

            // 方式3：按车辆+sqeNo存储（兜底逻辑）
            if (sqeNo != null && !sqeNo.isEmpty()) {
                keys.add("bus_gps_msg:" + busNo + ":" + sqeNo);
            }

            // 方式4：按车辆编号存储（最后兜底）
            keys.add("bus_gps_msg:" + busNo);

            // 为每个key存储数据
            for (String key : keys) {
                // 获取现有数据数组
                String existingDataStr = jedis.get(key);
                JSONArray gpsMsgArray;
                if (existingDataStr != null && !existingDataStr.isEmpty()) {
                    gpsMsgArray = new JSONArray(existingDataStr);
                } else {
                    gpsMsgArray = new JSONArray();
                }

                // 检查是否已存在相同的数据（避免重复）
                boolean exists = false;
                for (int i = 0; i < gpsMsgArray.length(); i++) {
                    JSONObject existingMsg = gpsMsgArray.getJSONObject(i);
                    if (existingMsg.optString("timestamp").equals(gpsMsg.optString("timestamp")) &&
                        existingMsg.optString("eventType").equals(gpsMsg.optString("eventType"))) {
                        exists = true;
                        break;
                    }
                }

                // 如果不存在，则添加新数据
                if (!exists) {
                    gpsMsgArray.put(gpsMsg);
                }

                // 存储到Redis，设置过期时间
                jedis.set(key, gpsMsgArray.toString());
                jedis.expire(key, Config.REDIS_TTL_OPEN_TIME);
            }

            if (Config.LOG_DEBUG) {
                logger.info("[KafkaConsumerService] 增强收集到离站信号原始数据: busNo=" + busNo + ", stationId=" + stationId + ", sqeNo=" + sqeNo + ", 存储keys=" + keys.size() + ", eventType=" + eventType);
            }
        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                logger.error("[KafkaConsumerService] 收集到离站信号原始数据失败: " + e.getMessage());
            }
        }
    }

    /**
     * 保存开关门WebSocket消息到数据库
     */
    private void saveOpenCloseDoorMessage(String busNo, String busId, String cameraNo, String action,
            String timestamp, String stationId, String stationName, JSONObject originalData) {
        try {
            // 创建完整的消息对象，包含event字段（open_close_door消息不包含image字段）
            JSONObject fullMessage = new JSONObject();
            fullMessage.put("event", "open_close_door");
            fullMessage.put("data", originalData);

            // 创建开关门消息对象
            OpenCloseDoorMsg doorMsg = new OpenCloseDoorMsg();
            doorMsg.setBusNo(busNo);
            doorMsg.setBusId(busId); // 设置bus_id字段
            doorMsg.setCameraNo(cameraNo);
            doorMsg.setAction(action);
            doorMsg.setTimestamp(timestamp);
            // 传递sqe_no用于数据库存档
            if (originalData != null) {
                doorMsg.setSqeNo(originalData.optString("sqe_no"));
            }

            // 解析时间戳
            if (timestamp != null && !timestamp.trim().isEmpty()) {
                try {
                    LocalDateTime parsedTime = LocalDateTime.parse(timestamp.trim(),
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    doorMsg.setParsedTimestamp(parsedTime);
                } catch (Exception e) {
                    if (Config.LOG_ERROR) {
                        logger.error(String.format("[WebSocket消息保存] 解析时间戳失败: %s, 错误: %s", timestamp, e.getMessage()));
                    }
                    // 时间戳解析失败时使用当前时间
                    doorMsg.setParsedTimestamp(LocalDateTime.now());
                }
            } else {
                // 时间戳为空时使用当前时间
                doorMsg.setParsedTimestamp(LocalDateTime.now());
            }

            doorMsg.setStationId(stationId);
            doorMsg.setStationName(stationName);
            //  提取并设置sqe_no字段
            String sqeNo = originalData.optString("sqe_no");
            doorMsg.setSqeNo(sqeNo);
            doorMsg.setEvent("open_close_door");
            doorMsg.setOriginalMessage(fullMessage.toString());

            if (Config.LOG_INFO) {
                String actionDesc = "open".equals(action) ? "开门" : "关门";
                logger.info(String.format("[WebSocket消息保存]  开始保存%s消息: 车辆=%s, 车辆ID=%s, sqe_no=%s, 站点=%s",
                    actionDesc, busNo, busId, sqeNo, stationName));
            }

            // 异步保存到数据库
            asyncDbServiceManager.saveOpenCloseDoorMsgAsync(doorMsg);

            if (Config.LOG_INFO) {
                String actionDesc = "open".equals(action) ? "开门" : "关门";
                logger.info(String.format("[WebSocket消息保存]  %s消息记录完成: 车辆=%s, 车辆ID=%s, sqe_no=%s, 站点=%s, 时间=%s",
                    actionDesc, busNo, busId, sqeNo, stationName, timestamp));
            }

        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                logger.error(String.format("[WebSocket消息保存] 保存车辆 %s 开关门消息时发生错误: %s", busNo, e.getMessage()));
                e.printStackTrace();
            }
        }
    }

    /**
     * 第一时间保存刷卡数据到数据库（无条件保存）
     */
    private void handleCardSwipeDataImmediate(JSONObject message, String busNo) {
        try {
            // 解析刷卡数据
            BusCardSwipeData cardData = new BusCardSwipeData();
            cardData.setBusSelfNo(message.optString("busSelfNo", busNo)); // 如果busSelfNo为空，使用busNo作为fallback
            cardData.setCardNo(message.optString("cardNo"));
            cardData.setCardType(message.optString("cardType"));
            cardData.setChildCardType(message.optString("childCardType"));
            cardData.setOnOff(message.optString("onOff"));
            cardData.setTradeNo(message.optString("tradeNo"));
            cardData.setTradeTime(message.optString("tradeTime"));

            //  获取当前车辆的sqe_no（从Redis中获取当前开门批次）
            String sqeNo = getCurrentSqeNoFromRedis(busNo);
            cardData.setSqeNo(sqeNo);

            if (Config.LOG_INFO) {
                logger.info(String.format("[第一时间保存] 刷卡数据: 车辆=%s, 卡号=%s, 交易时间=%s, sqeNo=%s",
                    busNo, cardData.getCardNo(), cardData.getTradeTime(), sqeNo));
            }

            // 异步保存到数据库
            asyncDbServiceManager.saveCardSwipeDataAsync(cardData);

        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                logger.error(String.format("[第一时间保存] 保存车辆 %s 刷卡数据时发生错误: %s", busNo, e.getMessage()));
                e.printStackTrace();
            }
        }
    }

    /**
     * 第一时间保存到离站数据到数据库（无条件保存）
     */
    private void handleArriveLeaveDataImmediate(JSONObject message, String busNo, String routeNo, String isArriveOrLeft, String stationName) {
        try {
            // 解析到离站数据
            BusArriveLeaveData arriveLeaveData = new BusArriveLeaveData();
            arriveLeaveData.setBusNo(busNo);
            arriveLeaveData.setBusSelfNo(message.optString("busSelfNo", busNo)); // 设置车辆自编号
            arriveLeaveData.setBusId(message.optLong("busId"));
            arriveLeaveData.setSrcAddr(message.optString("srcAddr"));
            arriveLeaveData.setSeqNum(message.optLong("seqNum"));
            arriveLeaveData.setPacketTime(message.optLong("packetTime"));
            arriveLeaveData.setIsArriveOrLeft(isArriveOrLeft);
            arriveLeaveData.setStationId(message.optString("stationId"));
            arriveLeaveData.setStationName(stationName);
            arriveLeaveData.setNextStationSeqNum(message.optString("nextStationSeqNum"));
            arriveLeaveData.setTrafficType(message.optString("trafficType"));
            arriveLeaveData.setRouteNo(routeNo); // 设置线路编号
            arriveLeaveData.setPktType(message.optInt("pktType", 4)); // 设置包类型，到离站消息默认为4

            // direction映射逻辑
            String trafficType = message.optString("trafficType");
            String direction = "4".equals(trafficType) || "6".equals(trafficType) ? "up" :
                              "5".equals(trafficType) ? "down" : trafficType;
            arriveLeaveData.setDirection(direction);

            // 保存原始消息
            arriveLeaveData.setOriginalMessage(message.toString());

            //  获取当前车辆的sqe_no（从Redis中获取当前开门批次）
            String sqeNo = getCurrentSqeNoFromRedis(busNo);
            arriveLeaveData.setSqeNo(sqeNo);

            // 异步保存到数据库
            asyncDbServiceManager.saveArriveLeaveDataAsync(arriveLeaveData);

            if (Config.LOG_INFO) {
                String actionDesc = "1".equals(isArriveOrLeft) ? "到站" : "2".equals(isArriveOrLeft) ? "离站" : "其他";
                logger.info(String.format("[第一时间保存] 到离站数据: 车辆=%s, %s, 站点=%s, 线路=%s, sqeNo=%s",
                    busNo, actionDesc, stationName, routeNo, sqeNo));
            }

        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                logger.error(String.format("[第一时间保存] 保存车辆 %s 到离站数据时发生错误: %s", busNo, e.getMessage()));
                e.printStackTrace();
            }
        }
    }

    /**
     * 处理刷卡数据
     */
    private void handleCardSwipeData(JSONObject message, String busNo) {
        try {
            // 检查是否为试点线路车辆
            if (!isPilotVehicle(busNo)) {
                // if (Config.LOG_DEBUG) {
                //     System.out.println(String.format("[刷卡数据过滤] 车辆 %s 不在试点线路中，跳过保存", busNo));
                // }
                return;
            }

            // 解析刷卡数据
            BusCardSwipeData cardData = new BusCardSwipeData();
            cardData.setBusSelfNo(message.optString("busSelfNo", busNo)); // 如果busSelfNo为空，使用busNo作为fallback
            cardData.setCardNo(message.optString("cardNo"));
            cardData.setCardType(message.optString("cardType"));
            cardData.setChildCardType(message.optString("childCardType"));
            cardData.setOnOff(message.optString("onOff"));
            cardData.setTradeNo(message.optString("tradeNo"));
            cardData.setTradeTime(message.optString("tradeTime"));

            if (Config.LOG_INFO) {
                logger.info(String.format("[刷卡数据处理] 试点线路车辆 %s 刷卡数据，开始保存到数据库", busNo));
            }

            // 异步保存到数据库
            asyncDbServiceManager.saveCardSwipeDataAsync(cardData);

            if (Config.LOG_INFO) {
                logger.info(String.format("[刷卡数据保存] 车辆 %s 刷卡数据已提交异步保存: 卡号=%s, 交易时间=%s",
                    busNo, cardData.getCardNo(), cardData.getTradeTime()));
            }

        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                logger.error(String.format("[刷卡数据处理] 处理车辆 %s 刷卡数据时发生错误: %s", busNo, e.getMessage()));
                e.printStackTrace();
            }
        }
    }

    /**
     * 处理到离站数据
     */
    private void handleArriveLeaveData(JSONObject message, String busNo, String routeNo, String isArriveOrLeft, String stationName) {
        try {
            // 检查是否为试点线路
            if (!isPilotRoute(routeNo)) {
                // if (Config.LOG_DEBUG) {
                //     System.out.println(String.format("[到离站数据过滤] 线路 %s 不在试点线路中，跳过保存", routeNo));
                // }
                return;
            }

            // 解析到离站数据
            BusArriveLeaveData arriveLeaveData = new BusArriveLeaveData();
            arriveLeaveData.setBusNo(busNo);
            arriveLeaveData.setBusSelfNo(message.optString("busSelfNo", busNo)); // 设置车辆自编号
            arriveLeaveData.setBusId(message.optLong("busId"));
            arriveLeaveData.setSrcAddr(message.optString("srcAddr"));
            arriveLeaveData.setSeqNum(message.optLong("seqNum"));
            arriveLeaveData.setPacketTime(message.optLong("packetTime"));
            arriveLeaveData.setIsArriveOrLeft(isArriveOrLeft);
            arriveLeaveData.setStationId(message.optString("stationId"));
            arriveLeaveData.setStationName(stationName);
            arriveLeaveData.setNextStationSeqNum(message.optString("nextStationSeqNum"));
            arriveLeaveData.setTrafficType(message.optString("trafficType"));
            arriveLeaveData.setRouteNo(routeNo); // 设置线路编号
            arriveLeaveData.setPktType(message.optInt("pktType", 4)); // 设置包类型，到离站消息默认为4

            // direction映射逻辑
            String trafficType = message.optString("trafficType");
            String direction = "4".equals(trafficType) || "6".equals(trafficType) ? "up" :
                              "5".equals(trafficType) ? "down" : trafficType;
            arriveLeaveData.setDirection(direction);

            // 保存原始消息
            arriveLeaveData.setOriginalMessage(message.toString());

            // 异步保存到数据库
            asyncDbServiceManager.saveArriveLeaveDataAsync(arriveLeaveData);

            if (Config.LOG_INFO) {
                String actionDesc = "1".equals(isArriveOrLeft) ? "到站" : "2".equals(isArriveOrLeft) ? "离站" : "其他";
                logger.info(String.format("[到离站数据保存] 车辆 %s %s数据已提交异步保存: 站点=%s, 线路=%s",
                    busNo, actionDesc, stationName, routeNo));
            }

        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                logger.error(String.format("[到离站数据处理] 处理车辆 %s 到离站数据时发生错误: %s", busNo, e.getMessage()));
                e.printStackTrace();
            }
        }
    }

    /**
     * 检查车辆是否为试点车辆
     */
    private boolean isPilotVehicle(String busNo) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.auth(Config.REDIS_PASSWORD);

            // 先尝试从GPS数据获取线路信息
            String gpsData = jedis.get("gps:" + busNo);
            if (gpsData != null) {
                JSONObject gpsJson = new JSONObject(gpsData);
                String routeNo = gpsJson.optString("routeNo");
                if (routeNo != null && !routeNo.isEmpty() && isPilotRoute(routeNo)) {
                    return true;
                }
            }

            // 再尝试从到离站数据获取线路信息
            String arriveLeaveData = jedis.get("arrive_leave:" + busNo);
            if (arriveLeaveData != null) {
                JSONObject arriveLeaveJson = new JSONObject(arriveLeaveData);
                String routeNo = arriveLeaveJson.optString("routeNo");
                if (routeNo != null && !routeNo.isEmpty() && isPilotRoute(routeNo)) {
                    return true;
                }
            }

        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                logger.error(String.format("[试点车辆检查] 检查车辆 %s 时发生错误: %s", busNo, e.getMessage()));
            }
        }
        return false;
    }

    /**
     * 无条件保存所有收到的消息到数据库
     * 确保数据完整性，第一时间保存原始消息
     */
    private void saveMessage(String topic, JSONObject message, String busNo, String sqeNo) {
        try {
            switch (topic) {
                case KafkaConfig.BUS_GPS_TOPIC:
                    String routeNo = message.optString("routeNo");
                    if (routeNo == null || routeNo.isEmpty()) {
                        return;
                    }

                    // 检查是否为试点线路
                    if (routeNo != null && !routeNo.isEmpty() && !isPilotRoute(routeNo)) {
                        if (Config.LOG_DEBUG) {
                        // System.out.println(String.format("[GPS数据过滤] 线路 %s 不在试点线路中，跳过保存", routeNo));
                    }

                    return;
                }

                case KafkaConfig.TICKET_TOPIC:
                    break;
                default:
                    break;
            }


            // 创建消息记录对象
            RetrieveAllMsg allMsg = new RetrieveAllMsg();

            // 基本信息
            allMsg.setBusNo(busNo);
            allMsg.setSource("kafka");
            allMsg.setRawMessage(message.toString());
            allMsg.setReceivedAt(LocalDateTime.now());
            allMsg.setTopic(topic);

            // 根据topic确定消息类型
            if (KafkaConfig.TICKET_TOPIC.equals(topic)) {
                allMsg.setMessageType("kafka_ticket");
                // 提取票务相关字段
                parseTicketMessage(message, allMsg);
            } else if (KafkaConfig.BUS_GPS_TOPIC.equals(topic)) {
                allMsg.setMessageType("kafka_gps");
                // 提取GPS相关字段
                parseGpsMessage(message, allMsg);
            } else {
                allMsg.setMessageType("kafka_unknown");
            }

            // 通用字段提取
            parseCommonFields(message, allMsg);

            //  设置sqe_no
            allMsg.setSqeNo(sqeNo);

            if (Config.LOG_DEBUG) {
                logger.info(String.format("[第一时间保存] 消息类型=%s, 车辆=%s, 来源=%s, sqeNo=%s",
                    allMsg.getMessageType(), busNo, allMsg.getSource(), sqeNo));
            }

            // 异步保存到数据库
            asyncDbServiceManager.saveAllMessageAsync(allMsg);

        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                logger.error(String.format("[第一时间保存] 保存车辆 %s 消息时发生错误: %s", busNo, e.getMessage()));
                e.printStackTrace();
            }
        }
    }

    /**
     * 解析票务消息的特定字段
     */
    private void parseTicketMessage(JSONObject message, RetrieveAllMsg allMsg) {
        try {
            // 票务消息特有字段
            allMsg.setBusId(message.optString("busSelfNo"));

            // 尝试解析时间戳
            String tradeTime = message.optString("tradeTime");
            if (tradeTime != null && !tradeTime.trim().isEmpty()) {
                try {
                    LocalDateTime timestamp = LocalDateTime.parse(tradeTime.trim(),
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    allMsg.setMessageTimestamp(timestamp);
                } catch (Exception e) {
                    // 解析失败使用当前时间
                    allMsg.setMessageTimestamp(LocalDateTime.now());
                }
            }
        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                logger.error("[第一时间保存] 解析票务消息字段失败: " + e.getMessage());
            }
        }
    }

    /**
     * 解析GPS消息的特定字段
     */
    private void parseGpsMessage(JSONObject message, RetrieveAllMsg allMsg) {
        try {
            // GPS消息特有字段
            allMsg.setBusId(String.valueOf(message.optLong("busId")));
            allMsg.setRouteNo(message.optString("routeNo"));
            allMsg.setStationId(message.optString("stationId"));
            allMsg.setStationName(message.optString("stationName"));

            // 尝试解析时间戳
            String gmtTime = message.optString("gmtTime");
            if (gmtTime != null && !gmtTime.trim().isEmpty()) {
                try {
                    LocalDateTime timestamp = LocalDateTime.parse(gmtTime.trim(),
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    allMsg.setMessageTimestamp(timestamp);
                } catch (Exception e) {
                    // 解析失败使用当前时间
                    allMsg.setMessageTimestamp(LocalDateTime.now());
                }
            }
        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                logger.error("[第一时间保存] 解析GPS消息字段失败: " + e.getMessage());
            }
        }
    }

    /**
     * 解析通用字段
     */
    private void parseCommonFields(JSONObject message, RetrieveAllMsg allMsg) {
        try {
            // 如果bus_id为空，尝试从其他字段获取
            if (allMsg.getBusId() == null || allMsg.getBusId().trim().isEmpty()) {
                allMsg.setBusId(message.optString("busSelfNo"));
            }

            // 如果仍然为空，使用busNo
            if (allMsg.getBusId() == null || allMsg.getBusId().trim().isEmpty()) {
                allMsg.setBusId(allMsg.getBusNo());
            }

            // 如果消息时间戳为空，使用当前时间
            if (allMsg.getMessageTimestamp() == null) {
                allMsg.setMessageTimestamp(LocalDateTime.now());
            }
        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                logger.error("[第一时间保存] 解析通用字段失败: " + e.getMessage());
            }
        }
    }

    // 用于跟踪当前处理的topic的变量
    private String currentTopic = null;

    /**
     * 获取当前处理的topic
     */
    private String getCurrentTopic() {
        return currentTopic;
    }

    /**
     * 设置当前处理的topic
     */
    private void setCurrentTopic(String topic) {
        this.currentTopic = topic;
    }

    /**
     * 生成开关门唯一批次号（sqe_no）
     * 格式: {busId}_{timestamp}_{uuid}
     * 示例: 8-203_20250115143025_abc12345
     */
    private String generateSqeNo(String busNo, LocalDateTime timestamp, String action) {
        try {
            // 格式化时间戳：yyyyMMddHHmmss
            String timeStr = timestamp.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

            // 生成短UUID（取前8位）
            String shortUuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

            // 组合：busNo_timestamp_uuid (去掉action)
            String sqeNo = String.format("%s_%s_%s", busNo, timeStr, shortUuid);

            if (Config.LOG_DEBUG) {
                logger.info("[SqeNo生成] busNo=" + busNo + ", action=" + action + ", sqeNo=" + sqeNo);
            }

            return sqeNo;
        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                logger.error("[SqeNo生成] 生成sqe_no失败: " + e.getMessage());
            }
            // 兜底：使用简单的时间戳+随机数 (也去掉action)
            long timestamp_ms = System.currentTimeMillis();
            return busNo + "_" + timestamp_ms + "_" + (int)(Math.random() * 10000);
        }
    }

    /**
     * 写入等待队列（无窗口时的刷卡数据）
     * 使用Redis List实现队列
     */
    private void writeToWaitQueue(JSONObject message, String busNo) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.auth(Config.REDIS_PASSWORD);

            // 构建等待队列消息
            JSONObject waitMessage = new JSONObject();
            waitMessage.put("busNo", busNo);
            waitMessage.put("cardData", message);
            waitMessage.put("timestamp", LocalDateTime.now().toString());
            waitMessage.put("retryCount", 0);

            // 写入Redis等待队列
            String queueKey = "wait_queue_card_swipe:" + busNo;
            jedis.lpush(queueKey, waitMessage.toString());

            // 设置队列过期时间（24小时）
            jedis.expire(queueKey, 86400);

            logger.info("[等待队列] 刷卡数据写入Redis等待队列: busNo=" + busNo +
                              ", cardNo=" + message.optString("cardNo") +
                              ", tradeTime=" + message.optString("tradeTime") +
                              ", queueKey=" + queueKey);

        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                logger.error("[等待队列] 写入Redis失败: busNo=" + busNo + ", 错误=" + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * 从Redis获取当前车辆的sqe_no（用于Kafka消息关联）
     */
    private String getCurrentSqeNoFromRedis(String busNo) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.auth(Config.REDIS_PASSWORD);

            // 首先尝试通过windowId获取sqe_no
            String windowId = jedis.get("open_time:" + busNo);
            if (windowId != null && !windowId.isEmpty()) {
                String sqeNo = jedis.get("open_time_index:" + windowId);
                if (sqeNo != null && !sqeNo.isEmpty()) {
                    return sqeNo;
                }
            }

            // 如果没有找到，返回null（表示当前没有开门批次）
            return null;
        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                logger.error("[sqe_no获取] 获取车辆 " + busNo + " 的sqe_no失败: " + e.getMessage());
            }
            return null;
        }
    }
}
