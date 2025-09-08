package ai.servlet.passenger;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 使用标准 Java WebSocket API 的端点实现
 * 这样可以直接在 Tomcat 中运行，无需独立的 WebSocket 服务器
 */
@ServerEndpoint(value = "/passengerflow")
public class WebSocketEndpoint {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketEndpoint.class);

	// 存储所有连接的会话
	private static Set<Session> sessions = Collections.synchronizedSet(new HashSet<>());
	private static final PassengerFlowProcessor PROCESSOR = new PassengerFlowProcessor();

	// 异步事件处理执行器，避免在WebSocket线程中执行耗时任务
	private static final ExecutorService EVENT_EXECUTOR = new ThreadPoolExecutor(
		Config.WS_CORE_POOL_SIZE, Config.WS_MAX_POOL_SIZE,
		Config.WS_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
		new LinkedBlockingQueue<>(Config.WS_QUEUE_CAPACITY),
		new ThreadFactory() {
			private int threadNumber = 1;
			@Override
			public Thread newThread(Runnable r) {
				Thread t = new Thread(r, "WS-Event-" + threadNumber++);
				t.setDaemon(true);
				return t;
			}
		},
		new ThreadPoolExecutor.CallerRunsPolicy() {
			@Override
			public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
				if (Config.LOG_ERROR) {
					logger.error("[WebSocket] 任务被拒绝: poolSize={}, active={}, core={}, max={}, queueSize={}, taskCount={}, completed={}",
						e.getPoolSize(), e.getActiveCount(), e.getCorePoolSize(), e.getMaximumPoolSize(),
						e.getQueue() != null ? e.getQueue().size() : -1,
						e.getTaskCount(), e.getCompletedTaskCount());
				}
				super.rejectedExecution(r, e);
			}
		}
	);

	// 定时打印线程池运行指标
	private static final ScheduledExecutorService POOL_MONITOR = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
		@Override
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r, "WS-Event-Monitor");
			t.setDaemon(true);
			return t;
		}
	});

	static {
		POOL_MONITOR.scheduleAtFixedRate(() -> {
			if (Config.LOG_INFO) {
				try {
					logPoolStats();
				} catch (Throwable ignore) {}
			}
		}, Config.WS_MONITOR_SECONDS, Config.WS_MONITOR_SECONDS, TimeUnit.SECONDS);

		if (Config.LOG_INFO) {
			logger.info("[WebSocket] 线程池配置: core={}, max={}, keepAliveSeconds={}, queueCapacity={}, monitorSeconds={}",
					Config.WS_CORE_POOL_SIZE, Config.WS_MAX_POOL_SIZE, Config.WS_KEEP_ALIVE_SECONDS,
					Config.WS_QUEUE_CAPACITY, Config.WS_MONITOR_SECONDS);
		}
	}

	private static void logPoolStats() {
		ThreadPoolExecutor e = (ThreadPoolExecutor) EVENT_EXECUTOR;
		int queueSize = e.getQueue() != null ? e.getQueue().size() : -1;
		int queueRemaining = e.getQueue() != null ? e.getQueue().remainingCapacity() : -1;
		logger.info("[WebSocket] 线程池: poolSize={}, active={}, core={}, max={}, largest={}, queueSize={}, queueRemain={}, taskCount={}, completed={}, isShutdown={}, isTerminated={}",
			e.getPoolSize(), e.getActiveCount(), e.getCorePoolSize(), e.getMaximumPoolSize(),
			e.getLargestPoolSize(), queueSize, queueRemaining, e.getTaskCount(), e.getCompletedTaskCount(),
			e.isShutdown(), e.isTerminated());
	}

	@OnOpen
	public void onOpen(Session session) {
		sessions.add(session);
		// 放大文本/二进制消息缓冲区（50MB，支持大型特征向量和图片数据）
		session.setMaxTextMessageBufferSize(50 * 1024 * 1024);
		session.setMaxBinaryMessageBufferSize(50 * 1024 * 1024);
		if (Config.LOG_INFO) {
			logger.info("新的WebSocket客户端连接: {}", session.getId());
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
				logger.error("发送欢迎消息失败: {}", e.getMessage(), e);
			}
		}
	}

	@OnMessage
	public void onMessage(String message, Session session) {
		// 关闭原始消息内容打印，避免base64刷屏
		if (Config.LOG_INFO) {
			logger.info("[WebSocket] 收到消息，会话ID: {}", session.getId());
		}

		try {
			// 验证JSON格式正确性
			JSONObject jsonMessage;
			try {
				jsonMessage = new JSONObject(message);
			} catch (Exception e) {
				if (Config.LOG_ERROR) {
					logger.error("[WebSocket] JSON格式错误，会话ID: {}, 错误: {}", session.getId(), e.getMessage(), e);
					logger.error("  原始消息: {}...", message.substring(0, Math.min(message.length(), 200)));
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
						logger.error("[WebSocket] 发送错误响应失败: {}", sendError.getMessage(), sendError);
					}
				}
				return;
			}

			String type = jsonMessage.optString("type", "unknown");

			// 移除消息类型解析日志

			// 兼容CV协议：存在event字段则转处理器
			if (jsonMessage.has("event")) {
				String eventType = jsonMessage.optString("event");
				// 提取并打印sqe_no
				if (Config.LOG_INFO) {
					try {
						JSONObject data = jsonMessage.optJSONObject("data");
						String sqeNo = data != null ? data.optString("sqe_no", "") : "";
						logger.info("[WebSocket] 收到事件: event={}, sqe_no={}, sessionId={}", eventType, sqeNo, session.getId());
					} catch (Exception ignore) {}
				}
				// 移除CV事件转发日志

				// 添加JSON循环引用检查
				try {
					// 尝试序列化一次，检查是否有循环引用
					jsonMessage.toString();
				} catch (StackOverflowError soe) {
					if (Config.LOG_ERROR) {
						logger.error("[WebSocket] 检测到JSON循环引用，会话ID: {}, event: {}", session.getId(), eventType);
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
							logger.error("[WebSocket] 发送错误响应失败: {}", sendError.getMessage(), sendError);
						}
					}
					return;
				}

				// 先快速ACK，随后异步处理
				JSONObject ack = new JSONObject();
				ack.put("type", "ack");
				ack.put("event", eventType);
				ack.put("timestamp", LocalDateTime.now().toString());
				session.getBasicRemote().sendText(ack.toString());

				EVENT_EXECUTOR.submit(() -> {
					try {
						PROCESSOR.processEvent(jsonMessage);
					} catch (Throwable t) {
						if (Config.LOG_ERROR) {
							logger.error("[WebSocket] 异步处理事件失败: {}", t.getMessage(), t);
						}
					}
				});
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
				logger.error("[WebSocket] 处理消息出错: {}, 会话ID: {}", e.getMessage(), session.getId(), e);
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
					logger.error("[WebSocket] 发送错误响应失败: {}", ioException.getMessage(), ioException);
				}
			}
		}
	}

	@OnClose
	public void onClose(Session session, CloseReason closeReason) {
		sessions.remove(session);
		if (Config.LOG_INFO) {
			logger.info("WebSocket客户端断开连接: {}, 原因: {}", session.getId(), closeReason.getReasonPhrase());
		}
	}

	@OnError
	public void onError(Session session, Throwable error) {
		if (Config.LOG_ERROR) {
			logger.error("WebSocket错误 (session: {}): {}", session.getId(), error.getMessage(), error);
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
							logger.error("广播消息失败: {}", e.getMessage(), e);
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
							logger.error("广播消息失败: {}", e.getMessage(), e);
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
		// 打印下发消息的event与sqe_no，便于链路核查
		if (Config.LOG_INFO) {
			try {
				JSONObject obj = new JSONObject(message);
				String event = obj.optString("event");
				JSONObject data = obj.optJSONObject("data");
				String sqeNo = data != null ? data.optString("sqe_no", "") : "";
				String action = data != null ? data.optString("action", "") : "";
				logger.info("[WebSocket] 下发消息: event={}, action={}, sqe_no={}, 连接数={}", event, action, sqeNo, sessions.size());
			} catch (Exception ignore) {}
		}
		synchronized (sessions) {
			for (Session session : sessions) {
				if (session.isOpen()) {
					try {
						session.getBasicRemote().sendText(message);
					} catch (IOException e) {
						if (Config.LOG_ERROR) {
							logger.error("发送消息失败: {}", e.getMessage(), e);
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
