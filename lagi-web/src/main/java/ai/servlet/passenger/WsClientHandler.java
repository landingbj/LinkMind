package ai.servlet.passenger;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

import java.net.URI;
import java.time.LocalDateTime;

/**
 * WebSocket客户端，连接CV系统，接收推送并处理
 */
public class WsClientHandler extends WebSocketClient {

    private final PassengerFlowProcessor processor = new PassengerFlowProcessor();

    public WsClientHandler() throws Exception {
        super(new URI(Config.CV_WEBSOCKET_URI));
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        System.out.println("WebSocket connected to CV system");
    }

    @Override
    public void onMessage(String message) {
        System.out.println("Received message from CV: " + message);
        try {
            JSONObject json = new JSONObject(message);
            processor.processEvent(json);
        } catch (Exception e) {
            System.err.println("Process CV message error: " + e.getMessage());
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("WebSocket closed: " + reason);
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }

    public void sendOpenDoorSignal(String busNo, String cameraNo, LocalDateTime timestamp) {
        JSONObject message = new JSONObject();
        message.put("event", "open_door");
        JSONObject data = new JSONObject();
        data.put("bus_no", busNo);
        data.put("camera_no", cameraNo);
        data.put("action", "open");
        data.put("timestamp", timestamp.toString().replace("T", " "));
        message.put("data", data);
        send(message.toString());
    }

    public void sendCloseDoorSignal(String busNo, String cameraNo, LocalDateTime begin, LocalDateTime end) {
        JSONObject message = new JSONObject();
        message.put("event", "close_door");
        JSONObject data = new JSONObject();
        data.put("bus_no", busNo);
        data.put("camera_no", cameraNo);
        data.put("action", "close");
        data.put("timestamp_begin", begin.toString().replace("T", " "));
        data.put("timestamp_end", end.toString().replace("T", " "));
        message.put("data", data);
        send(message.toString());
    }
}