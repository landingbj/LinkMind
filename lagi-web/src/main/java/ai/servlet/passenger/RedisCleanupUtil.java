package ai.servlet.passenger;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.ScanResult;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Redis清理工具类，用于定期清理过期数据和监控内存使用情况
 */
public class RedisCleanupUtil {
    
    private final JedisPool jedisPool;
    private final ScheduledExecutorService scheduler;
    private static final long CLEANUP_INTERVAL_MINUTES = 30; // 每30分钟清理一次
    
    public RedisCleanupUtil() {
        this.jedisPool = new JedisPool(Config.REDIS_HOST, Config.REDIS_PORT);
        this.scheduler = Executors.newScheduledThreadPool(1);
        startCleanupTask();
    }
    
    /**
     * 启动定期清理任务
     */
    private void startCleanupTask() {
        scheduler.scheduleAtFixedRate(this::cleanupExpiredData, 
            CLEANUP_INTERVAL_MINUTES, CLEANUP_INTERVAL_MINUTES, TimeUnit.MINUTES);
        
        if (Config.LOG_INFO) {
            System.out.println("[RedisCleanupUtil] Started cleanup task, interval: " + CLEANUP_INTERVAL_MINUTES + " minutes");
        }
    }
    
    /**
     * 清理过期数据
     */
    private void cleanupExpiredData() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.auth(Config.REDIS_PASSWORD);
            
            // 清理过期的门状态数据
            cleanupExpiredKeys(jedis, "door_status:*", "门状态数据");
            
            // 清理过期的GPS数据
            cleanupExpiredKeys(jedis, "gps:*", "GPS数据");
            
            // 清理过期的到离站数据
            cleanupExpiredKeys(jedis, "arrive_leave:*", "到离站数据");
            
            // 清理过期的特征向量数据（只清理已过期的）
            cleanupExpiredKeys(jedis, "features_set:*", "特征向量数据");
            
            // 清理过期的乘客位置信息数据（只清理已过期的）
            cleanupExpiredKeys(jedis, "feature_position:*", "乘客位置信息数据");
            
            // 清理过期的图像URL数据（只清理已过期的）
            cleanupExpiredKeys(jedis, "image_urls:*", "图像URL数据");
            
            // 清理过期的计数数据（只清理已过期的）
            cleanupExpiredKeys(jedis, "ticket_count_*", "票务计数数据");
            cleanupExpiredKeys(jedis, "cv_*_count:*", "CV计数数据");
            
            // 清理过期的OD记录缓存
            cleanupExpiredKeys(jedis, "od_record:*", "OD记录缓存");
            
            // 清理过期的特征站点映射（只清理已过期的）
            cleanupExpiredKeys(jedis, "feature_station:*", "特征站点映射");
            
            // 清理过期的区间客流数据
            cleanupExpiredKeys(jedis, "section_flow:*", "区间客流数据");
            
            // 监控内存使用情况
            monitorMemoryUsage(jedis);
            
        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                System.err.println("[RedisCleanupUtil] Cleanup error: " + e.getMessage());
            }
        }
    }
    
    /**
     * 清理指定模式的过期键
     */
    private void cleanupExpiredKeys(Jedis jedis, String pattern, String dataType) {
        try {
            Set<String> keys = jedis.keys(pattern);
            int expiredCount = 0;
            int skippedCount = 0;
            
            for (String key : keys) {
                long ttl = jedis.ttl(key);
                
                if (ttl == -1) { // 没有设置过期时间的键
                    // 根据键名判断应该设置什么过期时间
                    int newTtl = getTTLForKey(key);
                    if (newTtl > 0) {
                        jedis.expire(key, newTtl);
                        expiredCount++;
                        
                        if (Config.LOG_DEBUG) {
                            System.out.println("[RedisCleanupUtil] Set TTL for key: " + key + ", TTL: " + newTtl + "s");
                        }
                    }
                } else if (ttl == -2) { // 键不存在（已被删除）
                    // 跳过已删除的键
                    continue;
                } else if (ttl > 0) { // 键还有剩余时间
                    // 跳过还有剩余时间的键，不进行清理
                    skippedCount++;
                    continue;
                } else if (ttl == 0) { // 键已过期
                    // 只删除已过期的键
                    jedis.del(key);
                    expiredCount++;
                    
                    if (Config.LOG_DEBUG) {
                        System.out.println("[RedisCleanupUtil] Deleted expired key: " + key);
                    }
                }
            }
            
            if (expiredCount > 0 || skippedCount > 0) {
                if (Config.LOG_INFO) {
                    System.out.println("[RedisCleanupUtil] " + dataType + " cleanup: " + 
                        expiredCount + " processed, " + skippedCount + " skipped (still valid)");
                }
            }
            
        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                System.err.println("[RedisCleanupUtil] Error cleaning " + dataType + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * 根据键名获取对应的TTL
     */
    private int getTTLForKey(String key) {
        if (key.startsWith("door_status:")) {
            return Config.REDIS_TTL_DOOR_STATUS;
        } else if (key.startsWith("gps:")) {
            return Config.REDIS_TTL_GPS;
        } else if (key.startsWith("arrive_leave:")) {
            return Config.REDIS_TTL_ARRIVE_LEAVE;
        } else if (key.startsWith("features_set:")) {
            return Config.REDIS_TTL_FEATURES;
        } else if (key.startsWith("feature_position:")) {
            return Config.REDIS_TTL_FEATURES; // 假设乘客位置信息数据也与特征向量数据同TTL
        } else if (key.startsWith("image_urls:")) {
            return Config.REDIS_TTL_OPEN_TIME; // 假设图像URL数据也与开放时间同TTL
        } else if (key.startsWith("open_time:") || key.startsWith("ticket_count_window:") || 
                   key.startsWith("cv_up_count:") || key.startsWith("cv_down_count:")) {
            return Config.REDIS_TTL_OPEN_TIME;
        } else if (key.startsWith("ticket_count_total:") || key.startsWith("total_count:") || 
                   key.startsWith("load_factor:")) {
            return Config.REDIS_TTL_COUNTS;
        } else if (key.startsWith("od_record:")) {
            return Config.REDIS_TTL_OPEN_TIME;
        } else if (key.startsWith("feature_station:")) {
            return Config.REDIS_TTL_FEATURES;
        } else if (key.startsWith("section_flow:")) {
            return Config.REDIS_TTL_OPEN_TIME; // 假设区间客流数据也与开放时间同TTL
        }
        return 0; // 不设置过期时间
    }
    
    /**
     * 监控Redis内存使用情况
     */
    private void monitorMemoryUsage(Jedis jedis) {
        try {
            String info = jedis.info("memory");
            String[] lines = info.split("\r\n");
            
            long usedMemory = 0;
            long maxMemory = 0;
            
            for (String line : lines) {
                if (line.startsWith("used_memory:")) {
                    usedMemory = Long.parseLong(line.split(":")[1]);
                } else if (line.startsWith("maxmemory:")) {
                    maxMemory = Long.parseLong(line.split(":")[1]);
                }
            }
            
            if (maxMemory > 0) {
                double usagePercent = (double) usedMemory / maxMemory * 100;
                
                if (usagePercent > 80) {
                    if (Config.LOG_ERROR) {
                        System.err.println("[RedisCleanupUtil] WARNING: Redis memory usage is " + 
                            String.format("%.2f", usagePercent) + "% (" + 
                            formatBytes(usedMemory) + "/" + formatBytes(maxMemory) + ")");
                    }
                } else if (Config.LOG_INFO) {
                    System.out.println("[RedisCleanupUtil] Redis memory usage: " + 
                        String.format("%.2f", usagePercent) + "% (" + 
                        formatBytes(usedMemory) + "/" + formatBytes(maxMemory) + ")");
                }
            }
            
        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                System.err.println("[RedisCleanupUtil] Error monitoring memory: " + e.getMessage());
            }
        }
    }
    
    /**
     * 格式化字节数
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
    
    /**
     * 手动触发清理
     */
    public void manualCleanup() {
        if (Config.LOG_INFO) {
            System.out.println("[RedisCleanupUtil] Manual cleanup triggered");
        }
        cleanupExpiredData();
    }
    
    /**
     * 停止清理任务
     */
    public void shutdown() {
        if (Config.LOG_INFO) {
            System.out.println("[RedisCleanupUtil] Shutting down cleanup task");
        }
        
        try {
            // 优雅关闭调度器
            scheduler.shutdown();
            
            // 等待最多30秒让任务自然结束
            if (!scheduler.awaitTermination(Config.REDIS_CLEANUP_SHUTDOWN_TIMEOUT_MS / 1000, TimeUnit.SECONDS)) {
                if (Config.LOG_INFO) {
                    System.out.println("[RedisCleanupUtil] Scheduler did not terminate gracefully, forcing shutdown");
                }
                // 如果30秒内没有结束，强制关闭
                scheduler.shutdownNow();
                
                // 再等待最多10秒
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    if (Config.LOG_ERROR) {
                        System.err.println("[RedisCleanupUtil] Scheduler did not terminate");
                    }
                }
            }
            
            if (Config.LOG_INFO) {
                System.out.println("[RedisCleanupUtil] Scheduler stopped");
            }
            
        } catch (InterruptedException e) {
            if (Config.LOG_ERROR) {
                System.err.println("[RedisCleanupUtil] Interrupted while waiting for scheduler to terminate: " + e.getMessage());
            }
            // 恢复中断状态
            Thread.currentThread().interrupt();
            // 强制关闭
            scheduler.shutdownNow();
        }
        
        // 关闭Redis连接池
        try {
            if (jedisPool != null) {
                jedisPool.close();
                if (Config.LOG_INFO) {
                    System.out.println("[RedisCleanupUtil] Redis connection pool closed");
                }
            }
        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                System.err.println("[RedisCleanupUtil] Error closing Redis connection pool: " + e.getMessage());
            }
        }
        
        if (Config.LOG_INFO) {
            System.out.println("[RedisCleanupUtil] Cleanup task shutdown complete");
        }
    }
    
    /**
     * 获取Redis连接池
     */
    public JedisPool getJedisPool() {
        return jedisPool;
    }
}
