package ai.servlet.passenger;

import com.google.gson.Gson;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * CV downup消息测试工具类
 */
public class RetrieveDownUpMsgTestUtil {

    public static void main(String[] args) {
        // 测试downup消息
        String downupMessage = createTestDownUpMessage();

        System.out.println("=== 测试CV downup消息解析 ===");
        testMessage(downupMessage);

        // 测试数据库服务
        System.out.println("\n=== 测试数据库连接 ===");
        RetrieveDownUpMsgDbService dbService = new RetrieveDownUpMsgDbService();
        boolean testConnection = dbService.testConnection();
        System.out.println("数据库连接测试: " + (testConnection ? "成功" : "失败"));

        dbService.close();
        System.out.println("\n测试完成！");
    }

    private static String createTestDownUpMessage() {
        // 创建测试的downup消息
        JSONObject message = new JSONObject();
        message.put("event", "downup");

        JSONObject data = new JSONObject();
        data.put("bus_no", "6-6445");
        data.put("bus_id", "8-203");
        data.put("camera_no", "camera_01");
        data.put("timestamp", "2025-01-06 15:30:00");

        JSONArray events = new JSONArray();

        // 上车事件
        JSONObject upEvent = new JSONObject();
        upEvent.put("direction", "up");
        upEvent.put("feature", "feature_data_up_123");
        upEvent.put("image", "image_data_up_456");
        upEvent.put("box_x", 100);
        upEvent.put("box_y", 150);
        upEvent.put("box_w", 80);
        upEvent.put("box_h", 120);
        events.put(upEvent);

        // 下车事件
        JSONObject downEvent = new JSONObject();
        downEvent.put("direction", "down");
        downEvent.put("feature", "feature_data_down_789");
        downEvent.put("image", "image_data_down_012");
        downEvent.put("box_x", 200);
        downEvent.put("box_y", 180);
        downEvent.put("box_w", 75);
        downEvent.put("box_h", 110);
        events.put(downEvent);

        data.put("events", events);
        message.put("data", data);

        return message.toString();
    }

    private static void testMessage(String messageJson) {
        try {
            // 解析消息
            JSONObject jsonMessage = new JSONObject(messageJson);
            JSONObject data = jsonMessage.getJSONObject("data");

            // 创建消息对象
            RetrieveDownUpMsg downUpMsg = new RetrieveDownUpMsg();
            downUpMsg.setBusNo(data.optString("bus_no"));
            downUpMsg.setBusId(data.optString("bus_id"));
            downUpMsg.setCameraNo(data.optString("camera_no"));
            downUpMsg.setTimestamp(data.optString("timestamp"));
            downUpMsg.setEvent("downup");
            downUpMsg.setOriginalMessage(messageJson);

            // 处理events数组
            JSONArray events = data.optJSONArray("events");
            if (events != null) {
                List<DownUpEvent> eventList = new ArrayList<>();
                int upCount = 0, downCount = 0;

                for (int i = 0; i < events.length(); i++) {
                    JSONObject eventObj = events.getJSONObject(i);
                    DownUpEvent event = new DownUpEvent();
                    event.setDirection(eventObj.optString("direction"));
                    event.setFeature(eventObj.optString("feature"));
                    event.setImage(eventObj.optString("image"));
                    event.setBoxX(eventObj.optInt("box_x"));
                    event.setBoxY(eventObj.optInt("box_y"));
                    event.setBoxW(eventObj.optInt("box_w"));
                    event.setBoxH(eventObj.optInt("box_h"));

                    eventList.add(event);

                    if ("up".equals(event.getDirection())) {
                        upCount++;
                    } else if ("down".equals(event.getDirection())) {
                        downCount++;
                    }
                }

                downUpMsg.setEvents(eventList);
                downUpMsg.setUpCount(upCount);
                downUpMsg.setDownCount(downCount);
                downUpMsg.setEventsJson(events.toString());
            }

            System.out.println("车辆编号: " + downUpMsg.getBusNo());
            System.out.println("车辆ID: " + downUpMsg.getBusId());
            System.out.println("摄像头编号: " + downUpMsg.getCameraNo());
            System.out.println("时间戳: " + downUpMsg.getTimestamp());
            System.out.println("解析后时间: " + downUpMsg.getParsedTimestamp());
            System.out.println("上车事件数: " + downUpMsg.getUpCount());
            System.out.println("下车事件数: " + downUpMsg.getDownCount());

            // 显示image和feature优化信息
            JSONArray originalEvents = data.optJSONArray("events");
            if (originalEvents != null) {
                int imageCount = 0;
                int featureCount = 0;
                for (int i = 0; i < originalEvents.length(); i++) {
                    JSONObject event = originalEvents.getJSONObject(i);
                    String image = event.optString("image");
                    String feature = event.optString("feature");
                    if (image != null && !image.trim().isEmpty() && !"null".equals(image)) {
                        imageCount++;
                    }
                    if (feature != null && !feature.trim().isEmpty() && !"null".equals(feature)) {
                        featureCount++;
                    }
                }
                System.out.println("包含图像的事件数: " + imageCount + " (已优化为'有')");
                System.out.println("包含特征的事件数: " + featureCount + " (已优化为'有')");
            }
            System.out.println("总事件数: " + (downUpMsg.getEvents() != null ? downUpMsg.getEvents().size() : 0));

            // 测试数据库保存
            RetrieveDownUpMsgDbService dbService = new RetrieveDownUpMsgDbService();
            if (dbService.testConnection()) {
                boolean saved = dbService.saveDownUpMsg(downUpMsg);
                System.out.println("数据保存测试: " + (saved ? "成功" : "失败"));
            }
            dbService.close();

        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
