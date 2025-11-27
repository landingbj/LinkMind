package ai.finetune;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SSH连接管理器
 * 用于管理和复用SSH连接，避免频繁创建和断开连接
 * 支持连接池、健康检查、自动重连、定期清理等功能
 */
@Slf4j
public class SSHConnectionManager {
    
    private static final SSHConnectionManager INSTANCE = new SSHConnectionManager();
    
    /**
     * 连接信息包装类
     */
    private static class ConnectionInfo {
        Session session;
        long lastUsedTime;  // 最后使用时间
        long createTime;    // 创建时间
        int useCount;       // 使用次数
        String password;    // 密码（用于重连）
        
        ConnectionInfo(Session session, String password) {
            this.session = session;
            this.password = password;
            this.lastUsedTime = System.currentTimeMillis();
            this.createTime = System.currentTimeMillis();
            this.useCount = 0;
        }
        
        void updateLastUsedTime() {
            this.lastUsedTime = System.currentTimeMillis();
            this.useCount++;
        }
    }
    
    // 连接池：key为 "host:port:username"，value为ConnectionInfo
    private final ConcurrentHashMap<String, ConnectionInfo> connectionPool = new ConcurrentHashMap<>();
    
    // 连接锁：确保同一连接只被创建一次
    private final ConcurrentHashMap<String, ReentrantLock> connectionLocks = new ConcurrentHashMap<>();
    
    // JSch实例（可以复用）
    private final JSch jsch = new JSch();
    
    // 连接超时时间（毫秒）
    private static final int CONNECTION_TIMEOUT = 300000; // 5分钟
    
    // Session保活时间（毫秒）
    private static final int KEEP_ALIVE_INTERVAL = 30000; // 30秒
    
    // 连接空闲超时时间（毫秒）- 30分钟未使用则关闭
    private static final long IDLE_TIMEOUT = 30 * 60 * 1000;
    
    // 连接健康检查间隔（毫秒）- 10分钟（降低检查频率，减少开销）
    private static final long HEALTH_CHECK_INTERVAL = 10 * 60 * 1000;
    
    // 定期清理任务的调度器
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r, "SSH-Connection-Cleanup");
        t.setDaemon(true);
        return t;
    });
    
    private SSHConnectionManager() {
        // 单例模式
        // 启动定期清理任务
        startCleanupTask();
    }
    
    public static SSHConnectionManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * 获取SSH Session（复用或创建新连接）
     * @param host SSH主机地址
     * @param port SSH端口
     * @param username SSH用户名
     * @param password SSH密码
     * @return SSH Session
     */
    public Session getSession(String host, int port, String username, String password) {
        String connectionKey = buildConnectionKey(host, port, username);
        
        // 尝试从连接池获取现有连接
        ConnectionInfo connInfo = connectionPool.get(connectionKey);
        if (connInfo != null) {
            Session session = connInfo.session;
            // 只检查连接是否还连接着，不进行完整的验证（避免频繁验证）
            if (session != null && session.isConnected()) {
                // 直接复用，不进行额外验证（连接有保活机制）
                connInfo.updateLastUsedTime();
                if (log.isTraceEnabled()) {
                    long timeSinceLastValidation = System.currentTimeMillis() - connInfo.lastUsedTime;
                    log.trace("复用现有SSH连接: {} (使用次数: {}, 距上次使用: {}ms)", 
                        connectionKey, connInfo.useCount, timeSinceLastValidation);
                }
                return session;
            } else {
                // 连接已断开，清理
                log.debug("SSH连接已断开，将重建: {}", connectionKey);
                removeInvalidConnection(connectionKey, connInfo);
            }
        }
        
        // 如果连接不存在或已断开，创建新连接
        ReentrantLock lock = connectionLocks.computeIfAbsent(connectionKey, k -> new ReentrantLock());
        lock.lock();
        try {
            // 双重检查，防止并发创建
            connInfo = connectionPool.get(connectionKey);
            if (connInfo != null) {
                Session session = connInfo.session;
                if (session != null && session.isConnected()) {
                    connInfo.updateLastUsedTime();
                    log.debug("并发检查：复用现有SSH连接: {}", connectionKey);
                    return session;
                }
            }
            
            // 创建新连接
            log.info("创建新的SSH连接: {}@{}:{} (连接池大小: {})", username, host, port, connectionPool.size());
            Session session = createNewSession(host, port, username, password);
            
            // 创建连接信息并放入连接池
            connInfo = new ConnectionInfo(session, password);
            connectionPool.put(connectionKey, connInfo);
            log.info("SSH连接已添加到连接池: {} (当前连接池大小: {})", connectionKey, connectionPool.size());
            
            return session;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 验证SSH连接是否真的可用（仅在需要时调用，避免频繁验证）
     * @param session SSH Session
     * @return 是否可用
     */
    private boolean validateConnection(Session session) {
        if (session == null || !session.isConnected()) {
            return false;
        }
        
        // 由于连接有保活机制，如果连接状态显示已连接，则认为是有效的
        // 只有在清理任务中需要验证时才进行实际测试
        return true;
    }
    
    /**
     * 实际验证SSH连接（执行测试命令，仅在必要时使用）
     * @param session SSH Session
     * @return 是否可用
     */
    private boolean validateConnectionWithTest(Session session) {
        if (session == null || !session.isConnected()) {
            return false;
        }
        
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand("echo 'test'");
            channel.setInputStream(null);
            channel.connect(3000); // 3秒超时
            
            // 读取输出
            InputStream in = channel.getInputStream();
            byte[] buffer = new byte[1024];
            while (in.available() > 0) {
                in.read(buffer);
            }
            
            int exitStatus = channel.getExitStatus();
            return exitStatus == 0;
        } catch (Exception e) {
            log.debug("SSH连接验证失败: {}", e.getMessage());
            return false;
        } finally {
            if (channel != null) {
                try {
                    channel.disconnect();
                } catch (Exception e) {
                    // 忽略
                }
            }
        }
    }
    
    /**
     * 移除无效连接
     */
    private void removeInvalidConnection(String connectionKey, ConnectionInfo connInfo) {
        if (connInfo != null && connInfo.session != null) {
            try {
                if (connInfo.session.isConnected()) {
                    connInfo.session.disconnect();
                }
            } catch (Exception e) {
                log.warn("关闭无效SSH连接时出错: {}", e.getMessage());
            }
        }
        connectionPool.remove(connectionKey);
    }
    
    /**
     * 创建新的SSH Session
     */
    private Session createNewSession(String host, int port, String username, String password) {
        try {
            Session session = jsch.getSession(username, host, port);
            session.setPassword(password);
            
            // 配置SSH连接
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            config.put("server_host_key", "rsa-sha2-512,rsa-sha2-256,ssh-ed25519,ssh-rsa,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521");
            config.put("PubkeyAcceptedAlgorithms", "rsa-sha2-512,rsa-sha2-256,ssh-ed25519,ssh-rsa,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521");
            // 设置保活机制
            config.put("ServerAliveInterval", String.valueOf(KEEP_ALIVE_INTERVAL / 1000));
            config.put("ServerAliveCountMax", "3");
            
            session.setConfig(config);
            session.setTimeout(CONNECTION_TIMEOUT);
            
            // 连接
            session.connect();
            log.info("SSH连接成功: {}@{}:{}", username, host, port);
            
            return session;
        } catch (Exception e) {
            log.error("创建SSH连接失败: {}@{}:{}", username, host, port, e);
            throw new RuntimeException("创建SSH连接失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 移除并关闭指定连接
     * @param host SSH主机地址
     * @param port SSH端口
     * @param username SSH用户名
     */
    public void removeSession(String host, int port, String username) {
        String connectionKey = buildConnectionKey(host, port, username);
        ConnectionInfo connInfo = connectionPool.remove(connectionKey);
        if (connInfo != null && connInfo.session != null) {
            try {
                if (connInfo.session.isConnected()) {
                    connInfo.session.disconnect();
                }
                log.info("SSH连接已关闭并从连接池移除: {}", connectionKey);
            } catch (Exception e) {
                log.warn("关闭SSH连接时出错: {}", e.getMessage());
            }
        }
        connectionLocks.remove(connectionKey);
    }
    
    /**
     * 检查连接是否有效（快速检查，不执行测试命令）
     * @param session SSH Session
     * @return 是否有效
     */
    public boolean isSessionValid(Session session) {
        return session != null && session.isConnected();
    }
    
    /**
     * 检查连接是否有效（执行测试命令进行完整验证）
     * @param session SSH Session
     * @return 是否有效
     */
    public boolean isSessionValidWithTest(Session session) {
        return session != null && session.isConnected() && validateConnectionWithTest(session);
    }
    
    /**
     * 清理所有连接
     */
    public void closeAllSessions() {
        log.info("开始清理所有SSH连接，连接数: {}", connectionPool.size());
        for (String key : connectionPool.keySet()) {
            ConnectionInfo connInfo = connectionPool.remove(key);
            if (connInfo != null && connInfo.session != null) {
                try {
                    if (connInfo.session.isConnected()) {
                        connInfo.session.disconnect();
                    }
                } catch (Exception e) {
                    log.warn("关闭SSH连接时出错: {}", e.getMessage());
                }
            }
        }
        connectionLocks.clear();
        log.info("所有SSH连接已清理");
    }
    
    /**
     * 清理无效连接（已断开的连接和空闲超时的连接）
     */
    public void cleanupInvalidSessions() {
        long currentTime = System.currentTimeMillis();
        int cleanedCount = 0;
        int idleClosedCount = 0;
        int invalidClosedCount = 0;
        
        // 使用迭代器安全地遍历和移除
        java.util.Iterator<String> iterator = connectionPool.keySet().iterator();
        while (iterator.hasNext()) {
            String key = iterator.next();
            ConnectionInfo connInfo = connectionPool.get(key);
            if (connInfo == null || connInfo.session == null) {
                iterator.remove();
                connectionLocks.remove(key);
                cleanedCount++;
                continue;
            }
            
            Session session = connInfo.session;
            boolean isInvalid = !session.isConnected();
            boolean isIdleTimeout = (currentTime - connInfo.lastUsedTime) > IDLE_TIMEOUT;
            
            if (isInvalid || isIdleTimeout) {
                iterator.remove();
                connectionLocks.remove(key);
                try {
                    if (session.isConnected()) {
                        session.disconnect();
                    }
                } catch (Exception e) {
                    log.debug("关闭SSH连接时出错: {}", e.getMessage());
                }
                cleanedCount++;
                if (isIdleTimeout) {
                    idleClosedCount++;
                    log.debug("关闭空闲超时的SSH连接: {} (空闲时间: {}分钟)", 
                        key, (currentTime - connInfo.lastUsedTime) / 60000);
                } else if (isInvalid) {
                    invalidClosedCount++;
                    log.debug("关闭已断开的SSH连接: {}", key);
                }
            }
        }
        
        if (cleanedCount > 0) {
            log.info("清理了 {} 个SSH连接 (已断开: {}, 空闲超时: {}, 当前连接池大小: {})", 
                cleanedCount, invalidClosedCount, idleClosedCount, connectionPool.size());
        }
    }
    
    /**
     * 启动定期清理任务
     */
    private void startCleanupTask() {
        // 每5分钟执行一次清理任务
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                cleanupInvalidSessions();
            } catch (Exception e) {
                log.error("定期清理SSH连接时出错", e);
            }
        }, HEALTH_CHECK_INTERVAL, HEALTH_CHECK_INTERVAL, TimeUnit.MILLISECONDS);
        
        log.info("SSH连接管理器定期清理任务已启动 (间隔: {}分钟)", HEALTH_CHECK_INTERVAL / 60000);
    }
    
    /**
     * 关闭连接管理器（清理所有连接和停止定时任务）
     */
    public void shutdown() {
        log.info("正在关闭SSH连接管理器...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        closeAllSessions();
        log.info("SSH连接管理器已关闭");
    }
    
    /**
     * 构建连接键
     */
    private String buildConnectionKey(String host, int port, String username) {
        return host + ":" + port + ":" + username;
    }
    
    /**
     * 获取连接池大小
     */
    public int getPoolSize() {
        return connectionPool.size();
    }
    
    /**
     * 获取连接统计信息
     */
    public String getConnectionStats() {
        StringBuilder stats = new StringBuilder();
        stats.append("SSH连接池统计:\n");
        stats.append("  总连接数: ").append(connectionPool.size()).append("\n");
        
        long currentTime = System.currentTimeMillis();
        for (String key : connectionPool.keySet()) {
            ConnectionInfo connInfo = connectionPool.get(key);
            if (connInfo != null) {
                long idleTime = currentTime - connInfo.lastUsedTime;
                stats.append(String.format("  %s: 使用次数=%d, 空闲时间=%d分钟, 创建时间=%d分钟前\n",
                    key, connInfo.useCount, idleTime / 60000, (currentTime - connInfo.createTime) / 60000));
            }
        }
        return stats.toString();
    }
}

