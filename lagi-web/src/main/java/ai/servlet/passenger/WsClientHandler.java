package ai.servlet.passenger;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

import java.net.URI;
import java.time.LocalDateTime;

/**
 * WebSocket客户端（当前不使用，CV会主动连接本服务端）。
 * 如需作为客户端主动连接第三方WS，再按需启用。
 */
public class WsClientHandler extends WebSocketClient {

	private final PassengerFlowProcessor processor = new PassengerFlowProcessor();

	public WsClientHandler() throws Exception {
		// 保留占位，如需启用，替换为有效URI
		super(new URI("ws://invalid.local/unused"));
	}

	@Override
	public void onOpen(ServerHandshake handshakedata) {
		System.out.println("WebSocket connected");
	}

	@Override
	public void onMessage(String message) {
		System.out.println("Received message: " + message);
		try {
			JSONObject json = new JSONObject(message);
			processor.processEvent(json);
		} catch (Exception e) {
			System.err.println("Process WS message error: " + e.getMessage());
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