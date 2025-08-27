package ai.servlet.passenger;

import org.json.JSONObject;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 使用标准 Java WebSocket API 的端点实现
 * 这样可以直接在 Tomcat 中运行，无需独立的 WebSocket 服务器
 */
@ServerEndpoint(value = "/passengerflow")
public class WebSocketEndpoint {

	// 存储所有连接的会话
	private static Set<Session> sessions = Collections.synchronizedSet(new HashSet<>());
	private static final PassengerFlowProcessor PROCESSOR = new PassengerFlowProcessor();

	@OnOpen
	public void onOpen(Session session) {
		sessions.add(session);
		// 放大文本/二进制消息缓冲区（10MB）
		session.setMaxTextMessageBufferSize(10 * 1024 * 1024);
		session.setMaxBinaryMessageBufferSize(10 * 1024 * 1024);
		if (Config.LOG_INFO) {
			System.out.println("新的WebSocket客户端连接: " + session.getId());
		}

		// 发送欢迎消息
		JSONObject welcomeMsg = new JSONObject();
		welcomeMsg.put("type", "welcome");
		welcomeMsg.put("message", "WebSocket服务端连接成功");
		welcomeMsg.put("timestamp", LocalDateTime.now().toString());
		welcomeMsg.put("sessionId", session.getId());

		try {
			session.getBasicRemote().sendText(welcomeMsg.toString());
		} catch (IOException e) {
			if (Config.LOG_ERROR) {
				System.err.println("发送欢迎消息失败: " + e.getMessage());
			}
		}
	}

	@OnMessage
	public void onMessage(String message, Session session) {
		// 关闭原始消息内容打印，避免base64刷屏
		if (Config.LOG_INFO) {
			System.out.println("[WebSocket] 收到消息，会话ID: " + session.getId());
		}

		try {
			// 验证JSON格式正确性
			JSONObject jsonMessage;
			try {
				jsonMessage = new JSONObject(message);
			} catch (Exception e) {
				if (Config.LOG_ERROR) {
					System.err.println("[WebSocket] JSON格式错误，会话ID: " + session.getId() + ", 错误: " + e.getMessage());
					System.err.println("  原始消息: " + message.substring(0, Math.min(message.length(), 200)) + "...");
				}

				// 发送错误响应
				JSONObject errorResponse = new JSONObject();
				errorResponse.put("type", "error");
				errorResponse.put("message", "JSON格式错误: " + e.getMessage());
				errorResponse.put("timestamp", LocalDateTime.now().toString());

				try {
					session.getBasicRemote().sendText(errorResponse.toString());
				} catch (IOException sendError) {
					if (Config.LOG_ERROR) {
						System.err.println("[WebSocket] 发送错误响应失败: " + sendError.getMessage());
					}
				}
				return;
			}

			String type = jsonMessage.optString("type", "unknown");

			// 移除消息类型解析日志

			// 兼容CV协议：存在event字段则转处理器
			if (jsonMessage.has("event")) {
				String eventType = jsonMessage.optString("event");
				// 移除CV事件转发日志

				// 添加JSON循环引用检查
				try {
					// 尝试序列化一次，检查是否有循环引用
					jsonMessage.toString();
				} catch (StackOverflowError soe) {
					if (Config.LOG_ERROR) {
						System.err.println("[WebSocket] 检测到JSON循环引用，会话ID: " + session.getId() + ", event: " + eventType);
					}

					// 发送错误响应
					JSONObject errorResponse = new JSONObject();
					errorResponse.put("type", "error");
					errorResponse.put("message", "JSON循环引用错误");
					errorResponse.put("timestamp", LocalDateTime.now().toString());

					try {
						session.getBasicRemote().sendText(errorResponse.toString());
					} catch (IOException sendError) {
						if (Config.LOG_ERROR) {
							System.err.println("[WebSocket] 发送错误响应失败: " + sendError.getMessage());
						}
					}
					return;
				}

				PROCESSOR.processEvent(jsonMessage);

				JSONObject ack = new JSONObject();
				ack.put("type", "ack");
				ack.put("event", eventType);
				ack.put("timestamp", LocalDateTime.now().toString());

				// 移除确认响应调试日志

				session.getBasicRemote().sendText(ack.toString());
				return;
			}

			switch (type) {
				case "passenger_count":
					// 移除过程性日志
					handlePassengerCount(session, jsonMessage);
					break;
				case "bus_status":
					// 移除过程性日志
					handleBusStatus(session, jsonMessage);
					break;
				case "heartbeat":
					// 移除过程性日志
					handleHeartbeat(session, jsonMessage);
					break;
				case "door_status":
					// 移除过程性日志
					handleDoorStatus(session, jsonMessage);
					break;
				default:
					// 移除广播过程性日志
					// 广播消息给所有客户端
					broadcastMessage(jsonMessage);
			}
		} catch (Exception e) {
			if (Config.LOG_ERROR) {
				System.err.println("[WebSocket] 处理消息出错: " + e.getMessage() + ", 会话ID: " + session.getId());
				e.printStackTrace();
			}

			// 发送错误响应
			JSONObject errorResponse = new JSONObject();
			errorResponse.put("type", "error");
			errorResponse.put("message", "消息格式错误: " + e.getMessage());

			try {
				session.getBasicRemote().sendText(errorResponse.toString());
				// 移除错误响应发送调试日志
			} catch (IOException ioException) {
				if (Config.LOG_ERROR) {
					System.err.println("[WebSocket] 发送错误响应失败: " + ioException.getMessage());
				}
			}
		}
	}

	@OnClose
	public void onClose(Session session, CloseReason closeReason) {
		sessions.remove(session);
		if (Config.LOG_INFO) {
			System.out.println("WebSocket客户端断开连接: " + session.getId() +
					", 原因: " + closeReason.getReasonPhrase());
		}
	}

	@OnError
	public void onError(Session session, Throwable error) {
		if (Config.LOG_ERROR) {
			System.err.println("WebSocket错误 (session: " + session.getId() + "): " + error.getMessage());
			error.printStackTrace();
		}
	}

	private void handlePassengerCount(Session session, JSONObject message) throws IOException {
		// 处理乘客计数消息
		JSONObject response = new JSONObject();
		response.put("type", "passenger_count_response");
		response.put("status", "success");
		response.put("timestamp", LocalDateTime.now().toString());
		response.put("data", message.opt("data"));

		session.getBasicRemote().sendText(response.toString());

		// 广播给其他客户端
		broadcastToOthers(session, message);
	}

	private void handleBusStatus(Session session, JSONObject message) throws IOException {
		// 处理公交车状态消息
		JSONObject response = new JSONObject();
		response.put("type", "bus_status_response");
		response.put("status", "success");
		response.put("timestamp", LocalDateTime.now().toString());
		response.put("data", message.opt("data"));

		session.getBasicRemote().sendText(response.toString());

		// 广播给其他客户端
		broadcastToOthers(session, message);
	}

	private void handleHeartbeat(Session session, JSONObject message) throws IOException {
		// 处理心跳消息
		JSONObject response = new JSONObject();
		response.put("type", "heartbeat_response");
		response.put("timestamp", LocalDateTime.now().toString());
		response.put("status", "alive");

		session.getBasicRemote().sendText(response.toString());
	}

	private void handleDoorStatus(Session session, JSONObject message) throws IOException {
		// 处理门状态消息
		JSONObject response = new JSONObject();
		response.put("type", "door_status_response");
		response.put("status", "success");
		response.put("timestamp", LocalDateTime.now().toString());
		response.put("data", message.opt("data"));

		session.getBasicRemote().sendText(response.toString());

		// 广播给其他客户端
		broadcastToOthers(session, message);
	}

	private void broadcastMessage(JSONObject message) {
		// 广播消息给所有连接的客户端
		String messageStr = message.toString();
		synchronized (sessions) {
			for (Session session : sessions) {
				if (session.isOpen()) {
					try {
						session.getBasicRemote().sendText(messageStr);
					} catch (IOException e) {
						if (Config.LOG_ERROR) {
							System.err.println("广播消息失败: " + e.getMessage());
						}
					}
				}
			}
		}
	}

	private void broadcastToOthers(Session sender, JSONObject message) {
		// 广播消息给除了发送者之外的所有客户端
		String messageStr = message.toString();
		synchronized (sessions) {
			for (Session session : sessions) {
				if (session.isOpen() && !session.equals(sender)) {
					try {
						session.getBasicRemote().sendText(messageStr);
					} catch (IOException e) {
						if (Config.LOG_ERROR) {
							System.err.println("广播消息失败: " + e.getMessage());
						}
					}
				}
			}
		}
	}

	/**
	 * 发送消息给所有客户端（供其他类调用）
	 */
	public static void sendToAll(String message) {
		synchronized (sessions) {
			for (Session session : sessions) {
				if (session.isOpen()) {
					try {
						session.getBasicRemote().sendText(message);
					} catch (IOException e) {
						if (Config.LOG_ERROR) {
							System.err.println("发送消息失败: " + e.getMessage());
						}
					}
				}
			}
		}
	}

	/**
	 * 获取当前连接数
	 */
	public static int getClientCount() {
		return sessions.size();
	}

	/**
	 * 获取所有会话
	 */
	public static Set<Session> getSessions() {
		return new HashSet<>(sessions);
	}
}
