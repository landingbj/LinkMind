package ai.servlet.passenger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import org.json.JSONObject;
import org.json.JSONArray;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 刷卡数据等待队列消费者
 * 处理无窗口时的刷卡数据，将其关联到对应的bus_od_record
 */
@Component
public class CardSwipeWaitQueueConsumer {
    private static final Logger logger = LoggerFactory.getLogger(CardSwipeWaitQueueConsumer.class);

    @Autowired
    private BusOdRecordDbService busOdRecordDbService;

    @Autowired
    private JedisPool jedisPool;

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 定时处理等待队列消息
     * 每30秒执行一次
     */
    @Scheduled(fixedRate = 30000)
    public void processWaitQueueMessages() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.auth(Config.REDIS_PASSWORD);

            // 查找所有等待队列
            Set<String> queueKeys = jedis.keys("wait_queue_card_swipe:*");

            for (String queueKey : queueKeys) {
                processQueueMessages(jedis, queueKey);
            }

        } catch (Exception e) {
            logger.error("[等待队列处理] 定时任务执行失败: " + e.getMessage());
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
            return busOdRecordDbService.findLatestByBusNoAndTime(busNo, tradeTime);
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
        if (retryCount < 3) {
            // 重试机制：延迟后重新发送到队列
            waitMessage.put("retryCount", retryCount + 1);
            waitMessage.put("retryTime", LocalDateTime.now().plusMinutes(2).toString());

            logger.info("[等待队列处理] 查不到记录，准备重试: retryCount=" + (retryCount + 1) +
                             ", busNo=" + waitMessage.getString("busNo"));

            // 重新发送到Redis队列
            scheduleRetry(waitMessage, jedis, queueKey);
        } else {
            // 超过重试次数，记录到错误日志
            logger.error("[等待队列处理] 超过重试次数，丢弃消息: " + waitMessage.toString());
        }
    }

    /**
     * 延迟重试（使用Redis队列）
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
}
