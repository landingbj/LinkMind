package ai.servlet.passenger;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 开关门WebSocket消息测试工具类
 */
public class OpenCloseDoorMsgTestUtil {

    private static final Logger logger = LoggerFactory.getLogger(OpenCloseDoorMsgTestUtil.class);

    public static void main(String[] args) {
        // 测试开门消息（包含bus_id字段）
        String openMessage = "{\"event\":\"open_close_door\",\"data\":{\"bus_no\":\"6-6445\",\"bus_id\":\"8-203\",\"camera_no\":\"camera_01\",\"action\":\"open\",\"timestamp\":\"2025-01-06 15:30:00\"}}";

        // 测试关门消息（包含bus_id，无image字段）
        String closeMessage = "{\"event\":\"open_close_door\",\"data\":{\"bus_no\":\"6-6445\",\"bus_id\":\"8-203\",\"camera_no\":\"camera_01\",\"action\":\"close\",\"timestamp\":\"2025-01-06 15:32:30\"}}";

        logger.info("=== 测试开门消息解析 ===");
        testMessage(openMessage, "清河坊站", "3301000101243477");

        logger.info("\n=== 测试关门消息解析 ===");
        testMessage(closeMessage, "清河坊站", "3301000101243477");

        // 测试数据库服务
        logger.info("\n=== 测试数据库连接 ===");
        OpenCloseDoorMsgDbService dbService = new OpenCloseDoorMsgDbService();
        boolean testConnection = dbService.testConnection();
        logger.info("数据库连接测试: {}", (testConnection ? "成功" : "失败"));

        dbService.close();
        logger.info("\n测试完成！");
    }

    private static void testMessage(String messageJson, String stationName, String stationId) {
        try {
            // 解析消息
            Gson gson = new Gson();
            org.json.JSONObject jsonObj = new org.json.JSONObject(messageJson);
            org.json.JSONObject data = jsonObj.getJSONObject("data");

            // 创建消息对象
            OpenCloseDoorMsg doorMsg = new OpenCloseDoorMsg();
            doorMsg.setBusNo(data.optString("bus_no"));
            doorMsg.setBusId(data.optString("bus_id"));
            doorMsg.setCameraNo(data.optString("camera_no"));
            doorMsg.setAction(data.optString("action"));
            doorMsg.setTimestamp(data.optString("timestamp"));
            doorMsg.setStationId(stationId);
            doorMsg.setStationName(stationName);
            doorMsg.setOriginalMessage(messageJson);

            logger.info("车辆编号: {}", doorMsg.getBusNo());
            logger.info("车辆ID: {}", doorMsg.getBusId());
            logger.info("摄像头编号: {}", doorMsg.getCameraNo());
            logger.info("动作: {} ({})", doorMsg.getAction(),
                ("open".equals(doorMsg.getAction()) ? "开门" : "关门"));
            logger.info("时间戳: {}", doorMsg.getTimestamp());
            logger.info("解析后时间: {}", doorMsg.getParsedTimestamp());
            logger.info("站点: {} ({})", doorMsg.getStationName(), doorMsg.getStationId());

            // 测试数据库保存
            OpenCloseDoorMsgDbService dbService = new OpenCloseDoorMsgDbService();
            if (dbService.testConnection()) {
                boolean saved = dbService.saveOpenCloseDoorMsg(doorMsg);
                logger.info("数据保存测试: {}", (saved ? "成功" : "失败"));
            }
            dbService.close();

        } catch (Exception e) {
            logger.error("测试失败: {}", e.getMessage(), e);
        }
    }
}
