package ai.servlet.passenger;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import org.json.JSONObject;
import org.json.JSONArray;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 刷卡数据等待队列消费者
 * 处理无窗口时的刷卡数据，将其关联到对应的bus_od_record
 */
public class CardSwipeWaitQueueConsumer {
    private static final Logger logger = LoggerFactory.getLogger(CardSwipeWaitQueueConsumer.class);

    private BusOdRecordDbService busOdRecordDbService;
    private JedisPool jedisPool;
    private ScheduledExecutorService scheduler;

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public CardSwipeWaitQueueConsumer() {
        this.busOdRecordDbService = new BusOdRecordDbService();
        this.jedisPool = new JedisPool(Config.REDIS_HOST, Config.REDIS_PORT);
        this.scheduler = Executors.newSingleThreadScheduledExecutor();

        logger.info("[等待队列消费者] CardSwipeWaitQueueConsumer 初始化完成，定时任务将每30秒执行一次");

        // 启动定时任务
        scheduler.scheduleAtFixedRate(this::processWaitQueueMessages, 30, 30, TimeUnit.SECONDS);
        logger.info("[等待队列消费者] 定时任务已启动，每30秒执行一次");
    }

    /**
     * 定时处理等待队列消息
     * 每30秒执行一次
     */
    public void processWaitQueueMessages() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.auth(Config.REDIS_PASSWORD);

            // 1. 处理延迟重试消息
            processDelayedRetryMessages(jedis);

            // 2. 查找所有等待队列
            Set<String> queueKeys = jedis.keys("wait_queue_card_swipe:*");

            if (queueKeys != null && !queueKeys.isEmpty()) {
                logger.info("[等待队列处理] 发现 {} 个等待队列", queueKeys.size());

                for (String queueKey : queueKeys) {
                    long queueLength = jedis.llen(queueKey);
                    if (queueLength > 0) {
                        logger.info("[等待队列处理] 处理队列: {}, 长度: {}", queueKey, queueLength);
                        processQueueMessages(jedis, queueKey);
                    }
                }
            } else {
                logger.debug("[等待队列处理] 未发现等待队列");
            }

        } catch (Exception e) {
            logger.error("[等待队列处理] 定时任务执行失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 处理延迟重试消息
     * 检查Redis中是否有到期的延迟重试消息
     */
    private void processDelayedRetryMessages(Jedis jedis) {
        try {
            // 查找所有延迟重试键
            Set<String> retryKeys = jedis.keys("wait_queue_retry:*");

            if (retryKeys != null && !retryKeys.isEmpty()) {
                logger.info("[延迟重试处理] 发现 {} 个延迟重试键", retryKeys.size());

                for (String retryKey : retryKeys) {
                    String message = jedis.get(retryKey);
                    if (message != null) {
                        // 检查是否到了重试时间
                        try {
                            JSONObject waitMessage = new JSONObject(message);
                            String retryTimeStr = waitMessage.getString("retryTime");
                            LocalDateTime retryTime = LocalDateTime.parse(retryTimeStr);

                            if (LocalDateTime.now().isAfter(retryTime)) {
                                // 到了重试时间，重新发送到等待队列
                                String busNo = waitMessage.getString("busNo");
                                String queueKey = "wait_queue_card_swipe:" + busNo;

                                jedis.lpush(queueKey, message);
                                jedis.del(retryKey); // 删除延迟重试键

                                logger.info("[延迟重试处理] 重新发送到等待队列: queueKey={}, retryCount={}",
                                           queueKey, waitMessage.getInt("retryCount"));
                            } else {
                                logger.debug("[延迟重试处理] 消息未到期: {}, 剩余时间: {}分钟",
                                           retryKey, java.time.Duration.between(LocalDateTime.now(), retryTime).toMinutes());
                            }
                        } catch (Exception e) {
                            logger.error("[延迟重试处理] 解析消息失败: " + retryKey + ", 错误=" + e.getMessage());
                            jedis.del(retryKey); // 删除有问题的键
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("[延迟重试处理] 处理延迟重试消息失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 处理单个队列的消息
     */
    private void processQueueMessages(Jedis jedis, String queueKey) {
        try {
            // 从队列中取出消息（非阻塞）
            String message = jedis.rpop(queueKey);

            while (message != null) {
                handleWaitQueueMessage(message, jedis, queueKey);
                message = jedis.rpop(queueKey);
            }

        } catch (Exception e) {
            logger.error("[等待队列处理] 处理队列失败: queueKey=" + queueKey + ", 错误=" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 处理单个等待队列消息
     */
    private void handleWaitQueueMessage(String message, Jedis jedis, String queueKey) {
        try {
            JSONObject waitMessage = new JSONObject(message);
            String busNo = waitMessage.getString("busNo");
            JSONObject cardData = waitMessage.getJSONObject("cardData");
            int retryCount = waitMessage.getInt("retryCount");

            logger.info("[等待队列处理] 收到消息: busNo=" + busNo +
                             ", cardNo=" + cardData.getString("cardNo") +
                             ", retryCount=" + retryCount + ", queueKey=" + queueKey);

            // 查询bus_od_record
            BusOdRecord record = queryBusOdRecord(busNo, cardData.getString("tradeTime"));

            if (record == null) {
                // 查不到记录，处理重试
                handleMessageNotFound(waitMessage, retryCount, jedis, queueKey);
            } else {
                // 找到记录，更新ticket_json
                updateTicketJson(record, cardData);
                logger.info("[等待队列处理] 成功更新bus_od_record: id=" + record.getId() +
                                 ", busNo=" + busNo + ", cardNo=" + cardData.getString("cardNo"));
            }

        } catch (Exception e) {
            logger.error("[等待队列处理] 处理消息失败: message=" + message + ", 错误=" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 查询bus_od_record
     * 条件：bus_no匹配且时间差小于1分钟
     */
    private BusOdRecord queryBusOdRecord(String busNo, String tradeTime) {
        try {
            BusOdRecord record = busOdRecordDbService.findLatestByBusNoAndTime(busNo, tradeTime);
            if (record == null) {
                // 查询失败时进行诊断
                logger.warn("[等待队列处理] 未找到匹配记录，进行诊断查询: busNo={}, tradeTime={}", busNo, tradeTime);
                busOdRecordDbService.findLatestByBusNoOnly(busNo);
            }
            return record;
        } catch (Exception e) {
            logger.error("[等待队列处理] 查询bus_od_record失败: busNo=" + busNo +
                             ", tradeTime=" + tradeTime + ", 错误=" + e.getMessage());
            return null;
        }
    }

    /**
     * 更新ticket_json字段
     */
    private void updateTicketJson(BusOdRecord record, JSONObject cardData) {
        try {
            // 解析现有ticket_json
            String ticketJsonStr = record.getTicketJson();
            JSONObject ticketJson = ticketJsonStr != null ? new JSONObject(ticketJsonStr) : new JSONObject();

            // 初始化detail数组
            JSONArray detailArray = ticketJson.optJSONArray("detail");
            if (detailArray == null) {
                detailArray = new JSONArray();
            }

            // 添加新的刷卡记录
            JSONObject newCardRecord = new JSONObject();
            newCardRecord.put("busSelfNo", cardData.getString("busSelfNo"));
            newCardRecord.put("childCardType", cardData.getString("childCardType"));
            newCardRecord.put("tradeTime", cardData.getString("tradeTime"));
            newCardRecord.put("cardType", cardData.getString("cardType"));
            newCardRecord.put("cardNo", cardData.getString("cardNo"));
            newCardRecord.put("onOff", cardData.getString("onOff"));
            newCardRecord.put("direction", determineDirection(cardData));

            detailArray.put(newCardRecord);

            // 更新计数
            int upCount = ticketJson.optInt("upCount", 0);
            int downCount = ticketJson.optInt("downCount", 0);

            String onOff = cardData.getString("onOff");
            if ("down".equals(onOff)) {
                downCount++;
            } else {
                upCount++; // 包括onOff为空或"up"的情况
            }

            // 更新ticket_json
            ticketJson.put("downCount", downCount);
            ticketJson.put("upCount", upCount);
            ticketJson.put("detail", detailArray);
            ticketJson.put("totalCount", upCount + downCount);

            // 更新数据库
            boolean updateResult = busOdRecordDbService.updateTicketJson(record.getId(), ticketJson.toString(), upCount, downCount);

            if (updateResult) {
                logger.info("[等待队列处理] 🔥 更新ticket_json成功: id=" + record.getId() +
                                 ", busNo=" + record.getBusNo() +
                                 ", cardNo=" + cardData.getString("cardNo") +
                                 ", upCount=" + upCount + ", downCount=" + downCount +
                                 ", totalCount=" + (upCount + downCount) +
                                 ", 更新时间=" + LocalDateTime.now().format(formatter));
            } else {
                logger.error("[等待队列处理] ❌ 更新ticket_json失败: id=" + record.getId() +
                                 ", busNo=" + record.getBusNo() +
                                 ", cardNo=" + cardData.getString("cardNo"));
            }

        } catch (Exception e) {
            logger.error("[等待队列处理] 更新ticket_json失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 判断上下车方向
     */
    private String determineDirection(JSONObject cardData) {
        String onOff = cardData.getString("onOff");
        if ("down".equals(onOff)) {
            return "下车";
        } else {
            return "上车"; // 包括onOff为空或"up"的情况
        }
    }

    /**
     * 处理查不到记录的情况
     */
    private void handleMessageNotFound(JSONObject waitMessage, int retryCount, Jedis jedis, String queueKey) {
        if (retryCount < 5) { // 增加重试次数到5次
            // 重试机制：延迟10分钟后重新发送到队列
            waitMessage.put("retryCount", retryCount + 1);
            waitMessage.put("retryTime", LocalDateTime.now().plusMinutes(10).toString());

            logger.info("[等待队列处理] 查不到记录，10分钟后重试: retryCount=" + (retryCount + 1) +
                             ", busNo=" + waitMessage.getString("busNo") +
                             ", retryTime=" + waitMessage.getString("retryTime"));

            // 发送到延迟队列，10分钟后重试
            scheduleDelayedRetry(waitMessage, jedis, queueKey);
        } else {
            // 超过重试次数，记录到错误日志
            logger.error("[等待队列处理] 超过重试次数(5次)，丢弃消息: " + waitMessage.toString());
        }
    }

    /**
     * 延迟重试（使用Redis延迟队列）
     * 将消息存储到Redis中，不设置过期时间，通过时间戳比较来控制重试
     */
    private void scheduleDelayedRetry(JSONObject waitMessage, Jedis jedis, String queueKey) {
        try {
            String busNo = waitMessage.getString("busNo");
            String retryKey = "wait_queue_retry:" + busNo + ":" + System.currentTimeMillis();

            // 将消息存储到Redis，不设置过期时间
            jedis.set(retryKey, waitMessage.toString());

            logger.info("[等待队列处理] 延迟重试已安排: retryKey=" + retryKey +
                             ", retryCount=" + waitMessage.getInt("retryCount") +
                             ", busNo=" + busNo + ", retryTime=" + waitMessage.getString("retryTime"));

        } catch (Exception e) {
            logger.error("[等待队列处理] 延迟重试安排失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 延迟重试（使用Redis队列）- 保留原方法用于兼容
     */
    private void scheduleRetry(JSONObject waitMessage, Jedis jedis, String queueKey) {
        try {
            // 重新发送到Redis队列
            jedis.lpush(queueKey, waitMessage.toString());

            logger.info("[等待队列处理] 延迟重试: queueKey=" + queueKey +
                             ", retryCount=" + waitMessage.getInt("retryCount") +
                             ", busNo=" + waitMessage.getString("busNo"));

        } catch (Exception e) {
            logger.error("[等待队列处理] 延迟重试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 关闭等待队列消费者
     */
    public void shutdown() {
        try {
            logger.info("[等待队列消费者] 正在关闭等待队列消费者...");

            if (scheduler != null) {
                scheduler.shutdown();
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
                logger.info("[等待队列消费者] 定时任务已关闭");
            }

            if (jedisPool != null) {
                jedisPool.close();
                logger.info("[等待队列消费者] Redis连接池已关闭");
            }

            logger.info("[等待队列消费者] 等待队列消费者关闭完成");

        } catch (Exception e) {
            logger.error("[等待队列消费者] 关闭时发生错误: {}", e.getMessage(), e);
        }
    }
}
