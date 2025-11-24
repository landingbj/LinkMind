package ai.finetune.repository;

import ai.database.impl.MysqlAdapter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 训练指标数据库操作类
 * 记录训练过程中的各种指标（loss, accuracy, mAP等）
 */
@Slf4j
public class TrainingMetricsRepository {
    
    private final MysqlAdapter mysqlAdapter;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public TrainingMetricsRepository(MysqlAdapter mysqlAdapter) {
        this.mysqlAdapter = mysqlAdapter;
    }
    
    /**
     * 添加训练指标
     * 
     * @param taskId 任务ID
     * @param epoch 当前轮次
     * @param step 当前步数
     * @param metricName 指标名称（如：train_loss, val_loss, train_acc, val_acc, mAP等）
     * @param metricValue 指标值
     * @return 是否成功
     */
    public boolean addMetric(String taskId, Integer epoch, Integer step, 
                            String metricName, Double metricValue) {
        String sql = "INSERT INTO ai_training_metrics " +
                    "(task_id, epoch, step, metric_name, metric_value, recorded_at, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try {
            String currentTime = getCurrentTime();
            int result = mysqlAdapter.executeUpdate(sql, 
                taskId, epoch, step, metricName, metricValue, currentTime, currentTime);
            
            if (result > 0) {
                log.debug("添加训练指标: taskId={}, epoch={}, {}={}", 
                         taskId, epoch, metricName, metricValue);
                return true;
            }
        } catch (Exception e) {
            log.error("添加训练指标失败: taskId={}, metricName={}", taskId, metricName, e);
        }
        return false;
    }
    
    /**
     * 批量添加指标（提高性能）
     * 
     * @param taskId 任务ID
     * @param epoch 当前轮次
     * @param step 当前步数
     * @param metrics 指标Map（指标名 -> 指标值）
     * @return 成功添加的数量
     */
    public int addMetricsBatch(String taskId, Integer epoch, Integer step, 
                               Map<String, Double> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return 0;
        }
        
        int successCount = 0;
        String currentTime = getCurrentTime();
        
        for (Map.Entry<String, Double> entry : metrics.entrySet()) {
            if (addMetric(taskId, epoch, step, entry.getKey(), entry.getValue())) {
                successCount++;
            }
        }
        
        log.info("批量添加训练指标: taskId={}, epoch={}, 成功添加 {}/{} 个指标", 
                taskId, epoch, successCount, metrics.size());
        return successCount;
    }
    
    /**
     * 获取任务的所有指标
     */
    public List<Map<String, Object>> getTaskMetrics(String taskId) {
        String sql = "SELECT * FROM ai_training_metrics " +
                    "WHERE task_id = ? ORDER BY epoch, step";
        try {
            return mysqlAdapter.select(sql, taskId);
        } catch (Exception e) {
            log.error("获取任务指标失败: taskId={}", taskId, e);
            return null;
        }
    }
    
    /**
     * 获取指定轮次的指标
     */
    public List<Map<String, Object>> getEpochMetrics(String taskId, Integer epoch) {
        String sql = "SELECT * FROM ai_training_metrics " +
                    "WHERE task_id = ? AND epoch = ? ORDER BY step";
        try {
            return mysqlAdapter.select(sql, taskId, epoch);
        } catch (Exception e) {
            log.error("获取轮次指标失败: taskId={}, epoch={}", taskId, epoch, e);
            return null;
        }
    }
    
    /**
     * 获取特定指标的历史数据
     */
    public List<Map<String, Object>> getMetricHistory(String taskId, String metricName) {
        String sql = "SELECT epoch, step, metric_value, recorded_at " +
                    "FROM ai_training_metrics " +
                    "WHERE task_id = ? AND metric_name = ? " +
                    "ORDER BY epoch, step";
        try {
            return mysqlAdapter.select(sql, taskId, metricName);
        } catch (Exception e) {
            log.error("获取指标历史失败: taskId={}, metricName={}", taskId, metricName, e);
            return null;
        }
    }
    
    /**
     * 获取最佳指标值
     * 
     * @param taskId 任务ID
     * @param metricName 指标名称
     * @param maxOrMin "max" 或 "min"（是否越大越好）
     * @return 最佳指标值
     */
    public Map<String, Object> getBestMetric(String taskId, String metricName, String maxOrMin) {
        String orderBy = "max".equalsIgnoreCase(maxOrMin) ? "DESC" : "ASC";
        String sql = "SELECT epoch, step, metric_value, recorded_at " +
                    "FROM ai_training_metrics " +
                    "WHERE task_id = ? AND metric_name = ? " +
                    "ORDER BY metric_value " + orderBy + " LIMIT 1";
        try {
            List<Map<String, Object>> results = mysqlAdapter.select(sql, taskId, metricName);
            if (results != null && !results.isEmpty()) {
                return results.get(0);
            }
        } catch (Exception e) {
            log.error("获取最佳指标失败: taskId={}, metricName={}", taskId, metricName, e);
        }
        return null;
    }
    
    /**
     * 获取最新指标值
     */
    public Map<String, Object> getLatestMetric(String taskId, String metricName) {
        String sql = "SELECT epoch, step, metric_value, recorded_at " +
                    "FROM ai_training_metrics " +
                    "WHERE task_id = ? AND metric_name = ? " +
                    "ORDER BY epoch DESC, step DESC LIMIT 1";
        try {
            List<Map<String, Object>> results = mysqlAdapter.select(sql, taskId, metricName);
            if (results != null && !results.isEmpty()) {
                return results.get(0);
            }
        } catch (Exception e) {
            log.error("获取最新指标失败: taskId={}, metricName={}", taskId, metricName, e);
        }
        return null;
    }
    
    /**
     * 删除任务的所有指标
     */
    public boolean deleteTaskMetrics(String taskId) {
        String sql = "DELETE FROM ai_training_metrics WHERE task_id = ?";
        try {
            int result = mysqlAdapter.executeUpdate(sql, taskId);
            log.info("删除任务指标: taskId={}, 删除了 {} 条记录", taskId, result);
            return result > 0;
        } catch (Exception e) {
            log.error("删除任务指标失败: taskId={}", taskId, e);
            return false;
        }
    }
    
    /**
     * 统计任务的指标数量
     */
    public int countTaskMetrics(String taskId) {
        String sql = "SELECT COUNT(*) FROM ai_training_metrics WHERE task_id = ?";
        try {
            return mysqlAdapter.selectCount(sql, taskId);
        } catch (Exception e) {
            log.error("统计任务指标失败: taskId={}", taskId, e);
            return 0;
        }
    }
    
    /**
     * 获取当前时间字符串
     */
    private String getCurrentTime() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }
}

