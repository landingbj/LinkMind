package ai.servlet.passenger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kafka Door Status Consumer
 * 更新版本：支持使用标准 WebSocket 端点进行广播
 */
public class KafkaDoorStatusConsumer {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final String topic;
    private final String bootstrapServers;
    private final String groupId;

    private ExecutorService consumerThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public KafkaDoorStatusConsumer(String topic, String bootstrapServers, String groupId) {
        this.topic = topic;
        this.bootstrapServers = bootstrapServers;
        this.groupId = groupId;
    }

    /**
     * 启动消费者
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            System.out.println("Starting Kafka consumer, topic: " + topic);
            consumerThread = Executors.newSingleThreadExecutor();
            consumerThread.submit(this::consumeMessages);
        }
    }

    /**
     * 停止消费者
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            System.out.println("Stopping Kafka consumer");
            if (consumerThread != null) {
                consumerThread.shutdown();
            }
        }
    }

    /**
     * 消费消息的主循环
     */
    private void consumeMessages() {
        try {
            // 模拟Kafka消息消费
            // 在实际环境中，这里应该使用KafkaConsumer来消费消息
            System.out.println("Kafka consumer started, waiting for messages...");

            while (running.get()) {
                try {
                    // Simulate message receiving
                    Thread.sleep(5000); // Check every 5 seconds

                    // Should receive actual messages from Kafka here
                    // Currently simulating a message
                    simulateDoorStatusMessage();

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("Error processing Kafka message: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.err.println("Error running Kafka consumer: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 模拟开关门状态消息（用于测试）
     */
    private void simulateDoorStatusMessage() {
        try {
            // 创建模拟的开关门状态消息
            JSONObject message = new JSONObject();
            message.put("door1OpenSts", 0);
            message.put("door3OpenSts", 0);
            message.put("door5LockSts", 0);
            message.put("busId", 3301000100144492L);
            message.put("busSelfNo", "2-6235");
            message.put("time", LocalDateTime.now().format(formatter));

            // 处理消息
            processDoorStatusMessage(message.toString());

        } catch (Exception e) {
            System.err.println("模拟消息处理失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 处理开关门状态消息
     */
    public void processDoorStatusMessage(String messageJson) {
        try {
            System.out.println("收到开关门状态消息: " + messageJson);

            // 解析JSON消息
            JSONObject message = new JSONObject(messageJson);

            // 创建DoorStatusMessage对象
            DoorStatusMessage doorStatus = new DoorStatusMessage();
            doorStatus.setBusSelfNo(message.getString("busSelfNo"));
            doorStatus.setBusId(message.getLong("busId"));
            doorStatus.setDoor1OpenSts(message.getInt("door1OpenSts"));
            doorStatus.setDoor3OpenSts(message.getInt("door3OpenSts"));
            doorStatus.setDoor5LockSts(message.getInt("door5LockSts"));

            // 解析时间
            String timeStr = message.getString("time");
            LocalDateTime time = LocalDateTime.parse(timeStr, formatter);
            doorStatus.setTime(time);

            // 处理门状态
            handleDoorStatus(doorStatus);

            // 通过WebSocket广播给客户端
            broadcastDoorStatus(doorStatus);

        } catch (Exception e) {
            System.err.println("处理开关门状态消息失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 处理门状态逻辑
     */
    private void handleDoorStatus(DoorStatusMessage doorStatus) {
        System.out.println("处理门状态: " + doorStatus);

        // 检查是否有门打开
        if (doorStatus.hasOpenDoor()) {
            System.out.println("警告: 车辆 " + doorStatus.getBusSelfNo() + " 有门处于打开状态");
            // 这里可以添加业务逻辑，比如记录日志、发送告警等
        }

        // 检查是否有门处于错误状态
        if (doorStatus.hasErrorDoor()) {
            System.out.println("错误: 车辆 " + doorStatus.getBusSelfNo() + " 有门处于错误状态");
            // 这里可以添加错误处理逻辑
        }

        // 记录到数据库或缓存
        saveDoorStatusToStorage(doorStatus);
    }

    /**
     * 保存门状态到存储
     */
    private void saveDoorStatusToStorage(DoorStatusMessage doorStatus) {
        try {
            // TODO: 实现保存到数据库或Redis的逻辑
            System.out.println("保存门状态到存储: " + doorStatus.getBusSelfNo());
        } catch (Exception e) {
            System.err.println("保存门状态失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 通过WebSocket广播门状态
     * 使用标准的 WebSocket 端点进行广播
     */
    private void broadcastDoorStatus(DoorStatusMessage doorStatus) {
        try {
            JSONObject wsMessage = new JSONObject();
            wsMessage.put("type", "door_status");
            wsMessage.put("timestamp", LocalDateTime.now().toString());
            wsMessage.put("data", new JSONObject()
                    .put("busSelfNo", doorStatus.getBusSelfNo())
                    .put("busId", doorStatus.getBusId())
                    .put("door1OpenSts", doorStatus.getDoor1OpenSts())
                    .put("door3OpenSts", doorStatus.getDoor3OpenSts())
                    .put("door5LockSts", doorStatus.getDoor5LockSts())
                    .put("time", doorStatus.getTime().format(formatter))
            );

            // 使用标准 WebSocket 端点广播给所有客户端
            WebSocketEndpoint.sendToAll(wsMessage.toString());
            System.out.println("已通过WebSocket广播门状态消息");

        } catch (Exception e) {
            System.err.println("WebSocket广播失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 检查消费者是否正在运行
     */
    public boolean isRunning() {
        return running.get();
    }
}