package ai.servlet.passenger;

import org.json.JSONObject;

import java.math.BigDecimal;

/**
 * CV满载率消息测试工具类
 */
public class RetrieveLoadFactorMsgTestUtil {

    public static void main(String[] args) {
        // 测试满载率消息
        String loadFactorMessage = createTestLoadFactorMessage();

        System.out.println("=== 测试CV满载率消息解析 ===");
        testMessage(loadFactorMessage);

        // 测试数据库服务
        System.out.println("\n=== 测试数据库连接 ===");
        RetrieveLoadFactorMsgDbService dbService = new RetrieveLoadFactorMsgDbService();
        boolean testConnection = dbService.testConnection();
        System.out.println("数据库连接测试: " + (testConnection ? "成功" : "失败"));

        dbService.close();
        System.out.println("\n测试完成！");
    }

    private static String createTestLoadFactorMessage() {
        // 创建测试的load_factor消息
        JSONObject message = new JSONObject();
        message.put("event", "load_factor");

        JSONObject data = new JSONObject();
        data.put("bus_no", "6-6445");
        data.put("camera_no", "camera_01");
        data.put("timestamp", "2025-01-06 15:30:00");
        data.put("count", 200);
        data.put("factor", 0.9);

        message.put("data", data);

        return message.toString();
    }

    private static void testMessage(String messageJson) {
        try {
            // 解析消息
            JSONObject jsonMessage = new JSONObject(messageJson);
            JSONObject data = jsonMessage.getJSONObject("data");

            // 创建消息对象
            RetrieveLoadFactorMsg loadFactorMsg = new RetrieveLoadFactorMsg();
            loadFactorMsg.setBusNo(data.optString("bus_no"));
            loadFactorMsg.setCameraNo(data.optString("camera_no"));
            loadFactorMsg.setTimestamp(data.optString("timestamp"));
            loadFactorMsg.setEvent("load_factor");
            loadFactorMsg.setCount(data.optInt("count"));

            // 处理满载率
            double factorValue = data.optDouble("factor", 0.0);
            loadFactorMsg.setFactor(BigDecimal.valueOf(factorValue));

            loadFactorMsg.setOriginalMessage(messageJson);

            System.out.println("车辆编号: " + loadFactorMsg.getBusNo());
            System.out.println("摄像头编号: " + loadFactorMsg.getCameraNo());
            System.out.println("时间戳: " + loadFactorMsg.getTimestamp());
            System.out.println("解析后时间: " + loadFactorMsg.getParsedTimestamp());
            System.out.println("车辆总人数: " + loadFactorMsg.getCount());
            System.out.println("满载率: " + loadFactorMsg.getFactor() + " (" + loadFactorMsg.getFactorPercentage() + ")");

            // 测试数据库保存
            RetrieveLoadFactorMsgDbService dbService = new RetrieveLoadFactorMsgDbService();
            if (dbService.testConnection()) {
                boolean saved = dbService.saveLoadFactorMsg(loadFactorMsg);
                System.out.println("数据保存测试: " + (saved ? "成功" : "失败"));
            }
            dbService.close();

        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
