package ai.servlet.passenger;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

/**
 * 使用标准 Java WebSocket API 的端点实现
 * 这样可以直接在 Tomcat 中运行，无需独立的 WebSocket 服务器
 */
@ServerEndpoint(value = "/passengerflow")
public class WebSocketEndpoint {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketEndpoint.class);

	// 存储所有连接的会话 - 使用CopyOnWriteArraySet避免锁竞争
	private static Set<Session> sessions = new CopyOnWriteArraySet<>();
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

		// 添加定时清理无效连接的任务
		POOL_MONITOR.scheduleAtFixedRate(() -> {
			cleanupInvalidSessions();
		}, 30, 30, TimeUnit.SECONDS); // 每30秒清理一次

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
		// 记录消息处理开始时间
		long startTime = System.currentTimeMillis();
		String messageId = session.getId() + "_" + startTime;

		// 关闭原始消息内容打印，避免base64刷屏
		if (Config.LOG_INFO) {
			logger.info("[WebSocket] 收到消息，会话ID: {}, 消息ID: {}", session.getId(), messageId);
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
					long asyncStartTime = System.currentTimeMillis();
					try {
						PROCESSOR.processEvent(jsonMessage);

						// 记录异步处理完成时间
						long asyncEndTime = System.currentTimeMillis();
						long totalTime = asyncEndTime - startTime;
						long asyncTime = asyncEndTime - asyncStartTime;

						if (Config.LOG_INFO) {
							logger.info("[WebSocket] 消息处理完成 - 消息ID: {}, 总耗时: {}ms, 异步处理耗时: {}ms",
								messageId, totalTime, asyncTime);
						}
					} catch (Throwable t) {
						long asyncEndTime = System.currentTimeMillis();
						long totalTime = asyncEndTime - startTime;

						if (Config.LOG_ERROR) {
							logger.error("[WebSocket] 异步处理事件失败 - 消息ID: {}, 总耗时: {}ms, 错误: {}",
								messageId, totalTime, t.getMessage(), t);
						}
					}
				});
				return;
			}

			// 处理同步消息类型
			long syncStartTime = System.currentTimeMillis();
			try {
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

				// 记录同步处理完成时间
				long syncEndTime = System.currentTimeMillis();
				long totalTime = syncEndTime - startTime;
				long syncTime = syncEndTime - syncStartTime;

				if (Config.LOG_INFO) {
					logger.info("[WebSocket] 同步消息处理完成 - 消息ID: {}, 类型: {}, 总耗时: {}ms, 同步处理耗时: {}ms",
						messageId, type, totalTime, syncTime);
				}
			} catch (Exception e) {
				long syncEndTime = System.currentTimeMillis();
				long totalTime = syncEndTime - startTime;

				if (Config.LOG_ERROR) {
					logger.error("[WebSocket] 同步消息处理失败 - 消息ID: {}, 类型: {}, 总耗时: {}ms, 错误: {}",
						messageId, type, totalTime, e.getMessage(), e);
				}
				throw e; // 重新抛出异常，让外层catch处理
			}
		} catch (Exception e) {
			long errorTime = System.currentTimeMillis();
			long totalTime = errorTime - startTime;

			if (Config.LOG_ERROR) {
				logger.error("[WebSocket] 处理消息出错 - 消息ID: {}, 会话ID: {}, 总耗时: {}ms, 错误: {}",
					messageId, session.getId(), totalTime, e.getMessage(), e);
			}

			// 发送错误响应
			JSONObject errorResponse = new JSONObject();
			errorResponse.put("type", "error");
			errorResponse.put("message", "消息格式错误: " + e.getMessage());
			errorResponse.put("messageId", messageId);
			errorResponse.put("processingTime", totalTime);

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
			logger.info("[WebSocket连接移除] WebSocket客户端断开连接: {}, 原因: {}, 时间: {}",
				session.getId(), closeReason.getReasonPhrase(),
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")));
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
		// 广播消息给所有连接的客户端 - 使用CopyOnWriteArraySet，无需锁
		String messageStr = message.toString();
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

	private void broadcastToOthers(Session sender, JSONObject message) {
		// 广播消息给除了发送者之外的所有客户端
		String messageStr = message.toString();

		// 使用线程池异步广播，避免阻塞主线程
		EVENT_EXECUTOR.submit(() -> {
			// CopyOnWriteArraySet支持并发遍历，无需锁
			for (Session session : sessions) {
				if (session.isOpen() && !session.equals(sender)) {
					try {
						// 直接发送，无需超时保护
						session.getBasicRemote().sendText(messageStr);
					} catch (IOException e) {
						if (Config.LOG_ERROR) {
							logger.error("广播消息失败: {}", e.getMessage(), e);
						}
						// 移除失效的session
						sessions.remove(session);
					}
				}
			}
		});
	}

	/**
	 * 发送消息给所有客户端（供其他类调用）
	 */
	public static void sendToAll(String message) {
		// 🔥 增强日志：WebSocket发送状态跟踪
		if (Config.LOG_INFO) {
			logger.info("[WebSocket发送跟踪] ========== 开始WebSocket消息发送 ==========");
			logger.info("[WebSocket发送跟踪] 当前总连接数: {}", sessions.size());

			// 检查连接状态
			int activeConnections = 0;
			for (Session session : sessions) {
				if (session.isOpen()) {
					activeConnections++;
				}
			}
			logger.info("[WebSocket发送跟踪] 有效连接数: {}", activeConnections);

			try {
				JSONObject obj = new JSONObject(message);
				String event = obj.optString("event");
				JSONObject data = obj.optJSONObject("data");
				String sqeNo = data != null ? data.optString("sqe_no", "") : "";
				String action = data != null ? data.optString("action", "") : "";
				String busId = data != null ? data.optString("bus_id", "") : "";
				logger.info("[WebSocket发送跟踪] 消息详情: event={}, action={}, bus_id={}, sqe_no={}", event, action, busId, sqeNo);
			} catch (Exception e) {
				logger.warn("[WebSocket发送跟踪] 解析消息失败: {}", e.getMessage());
			}
		}

		// 先清理无效连接，再检查是否有活跃连接
		cleanupInvalidSessions();

		// 检查是否有活跃连接
		if (sessions.isEmpty()) {
			if (Config.LOG_ERROR) {
				logger.error("[WebSocket发送跟踪] 没有活跃的WebSocket连接，无法发送消息");
			}
			return;
		}

		// 使用线程池异步发送，避免阻塞主线程
		EVENT_EXECUTOR.submit(() -> {
			// 创建sessions的副本，避免并发修改
			Set<Session> sessionsCopy = new HashSet<>(sessions);
			int successCount = 0;
			int failCount = 0;

			for (Session session : sessionsCopy) {
				if (session.isOpen()) {
					try {
						// 直接发送，无需超时保护
						session.getBasicRemote().sendText(message);
						successCount++;
						if (Config.LOG_DEBUG) {
							logger.debug("[WebSocket发送跟踪] 消息发送成功到会话: {}", session.getId());
						}
					} catch (IOException e) {
						failCount++;
						if (Config.LOG_ERROR) {
							logger.error("[WebSocket发送跟踪] 发送失败到会话 {}: {}", session.getId(), e.getMessage());
						}
						// 移除失败的session
						sessions.remove(session);
						if (Config.LOG_INFO) {
							logger.info("[WebSocket连接移除] 移除发送失败会话: {}, 时间: {}",
								session.getId(), LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")));
						}
					}
				} else {
					failCount++;
					if (Config.LOG_DEBUG) {
						logger.debug("[WebSocket发送跟踪] 移除已关闭的会话: {}", session.getId());
					}
					// 移除已关闭的session
					sessions.remove(session);
					if (Config.LOG_INFO) {
						logger.info("[WebSocket连接移除] 移除已关闭会话: {}, 时间: {}",
							session.getId(), LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")));
					}
				}
			}

			// 🔥 增强日志：发送结果统计
			if (Config.LOG_INFO) {
				logger.info("[WebSocket发送跟踪] 消息发送完成: 成功={}, 失败={}, 剩余连接数={}",
					successCount, failCount, sessions.size());
				logger.info("[WebSocket发送跟踪] ========== WebSocket消息发送结束 ==========");
			}
		});
	}

	/**
	 * 获取当前连接数
	 */
	public static int getClientCount() {
		return sessions.size();
	}

	/**
	 * 获取有效连接数（已打开的连接）
	 */
	public static int getActiveConnectionCount() {
		int count = 0;
		for (Session session : sessions) {
			if (session.isOpen()) {
				count++;
			}
		}
		return count;
	}

	/**
	 * 检查是否有活跃的WebSocket连接
	 */
	public static boolean hasActiveConnections() {
		return getActiveConnectionCount() > 0;
	}

	/**
	 * 打印WebSocket连接状态（供调试使用）
	 */
	public static void printConnectionStatus() {
		if (Config.LOG_INFO) {
			int totalConnections = sessions.size();
			int activeConnections = getActiveConnectionCount();
			logger.info("[WebSocket状态] 总连接数: {}, 活跃连接数: {}, 是否有活跃连接: {}",
				totalConnections, activeConnections, hasActiveConnections());
		}
	}


	/**
	 * 获取所有会话
	 */
	public static Set<Session> getSessions() {
		return new HashSet<>(sessions);
	}

	/**
	 * 清理无效的WebSocket连接
	 * 移除已关闭或无效的session
	 */
	private static void cleanupInvalidSessions() {
		if (sessions.isEmpty()) {
			return;
		}

		Set<Session> invalidSessions = new HashSet<>();
		for (Session session : sessions) {
			if (!session.isOpen()) {
				invalidSessions.add(session);
			}
		}

		if (!invalidSessions.isEmpty()) {
			sessions.removeAll(invalidSessions);
			if (Config.LOG_INFO) {
				logger.info("[WebSocket连接清理] 清理了 {} 个无效连接，剩余连接数: {}, 时间: {}",
					invalidSessions.size(), sessions.size(),
					LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")));
			}
		}
	}

	/**
	 * 强制清理所有连接（用于调试）
	 */
	public static void forceCleanupAllSessions() {
		int oldSize = sessions.size();
		sessions.clear();
		if (Config.LOG_INFO) {
			logger.info("[WebSocket强制清理] 清理了 {} 个连接, 时间: {}",
				oldSize, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")));
		}
	}
}
