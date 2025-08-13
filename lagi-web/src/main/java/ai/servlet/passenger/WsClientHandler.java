package ai.servlet.passenger;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

import java.net.URI;
import java.time.LocalDateTime;

public class WsClientHandler extends WebSocketClient {

    public WsClientHandler() throws Exception {
        super(new URI(Config.CV_WEBSOCKET_URI));
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        System.out.println("WebSocket connected");
    }

    @Override
    public void onMessage(String message) {
        // 接收 CV 系统推送的消息，交由 Servlet 处理
        System.out.println("Received message: " + message);
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

    public void sendCloseDoorSignal(String busNo, String cameraNo, LocalDateTime timestamp) {
        JSONObject message = new JSONObject();
        message.put("event", "close_door");
        JSONObject data = new JSONObject();
        data.put("bus_no", busNo);
        data.put("camera_no", cameraNo);
        data.put("action", "close");
        data.put("timestamp", timestamp.toString().replace("T", " "));
        message.put("data", data);
        send(message.toString());
    }
}