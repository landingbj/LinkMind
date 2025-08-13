package ai.servlet.passenger;

import org.json.JSONObject;
import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

@ClientEndpoint
public class WsClientHandler {
    private static final AtomicReference<Session> sessionRef = new AtomicReference<>();
    private final BusFlowProcessor processorServlet;

    public WsClientHandler(BusFlowProcessor processorServlet) {
        this.processorServlet = processorServlet;
    }

    @OnOpen
    public void onOpen(Session session) {
        System.out.println("WebSocket connection opened with CV system.");
        sessionRef.set(session);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        System.out.println("Received message from CV system: " + message);
        try {
            JSONObject jsonMessage = new JSONObject(message);
            String event = jsonMessage.getString("event");

            switch (event) {
                case "downup":
                    processorServlet.handleDownUpEvent(jsonMessage.getJSONObject("data"));
                    break;
                case "notify_pull_file":
                    JSONObject data = jsonMessage.getJSONObject("data");
                    String busNo = data.getString("bus_no");
                    String cameraNo = data.getString("camera_no");
                    String timestampBeginStr = data.getString("timestamp_begin");
                    String timestampEndStr = data.getString("timestamp_end");
                    String fileUrl = data.getString("fileurl");
                    LocalDateTime timestampBegin = LocalDateTime.parse(timestampBeginStr);
                    LocalDateTime timestampEnd = LocalDateTime.parse(timestampEndStr);
                    processorServlet.processFinalData(busNo, cameraNo, timestampBegin, timestampEnd, fileUrl);
                    break;
                default:
                    System.out.println("Unknown event type: " + event);
            }
        } catch (Exception e) {
            System.err.println("Error processing WebSocket message: " + e.getMessage());
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        System.out.println("WebSocket connection closed. Reason: " + closeReason.getReasonPhrase());
        sessionRef.set(null);
    }

    public static Session getSession() {
        return sessionRef.get();
    }

    public void sendMessage(String message) throws IOException {
        Session session = getSession();
        if (session != null && session.isOpen()) {
            session.getBasicRemote().sendText(message);
        } else {
            throw new IOException("WebSocket session is not open. Message not sent.");
        }
    }
}