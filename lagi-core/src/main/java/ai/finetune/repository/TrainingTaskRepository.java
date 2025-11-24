package ai.finetune.repository;

import ai.database.impl.MysqlAdapter;
import ai.finetune.dto.TrainingTaskDTO;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 训练任务数据库操作类
 * 统一处理所有任务类型的数据库操作，消除代码冗余
 */
@Slf4j
public class TrainingTaskRepository {
    
    private final MysqlAdapter mysqlAdapter;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public TrainingTaskRepository(MysqlAdapter mysqlAdapter) {
        this.mysqlAdapter = mysqlAdapter;
    }
    
    /**
     * 保存任务到数据库（通用方法）
     * 根据不同的任务类型自动选择需要保存的字段
     */
    public boolean saveTask(TrainingTaskDTO task) {
        try {
            String currentTime = getCurrentTime();
            
            // 根据任务类型选择不同的保存方法
            switch (task.getTaskType()) {
                case "train":
                    return saveTrainTask(task, currentTime);
                case "evaluate":
                    return saveEvaluateTask(task, currentTime);
                case "predict":
                    return savePredictTask(task, currentTime);
                case "export":
                    return saveExportTask(task, currentTime);
                default:
                    log.warn("未知的任务类型: {}", task.getTaskType());
                    return false;
            }
        } catch (Exception e) {
            log.error("保存任务到数据库失败: taskId={}, type={}, error={}", 
                     task.getTaskId(), task.getTaskType(), e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 保存训练任务（包含最完整的字段）
     */
    private boolean saveTrainTask(TrainingTaskDTO task, String currentTime) {
        String sql = "INSERT INTO ai_training_tasks " +
                "(task_id, track_id, model_name, model_category, model_framework, task_type, " +
                "container_name, container_id, docker_image, gpu_ids, use_gpu, " +
                "dataset_path, model_path, epochs, batch_size, image_size, optimizer, " +
                "status, progress, current_epoch, start_time, created_at, is_deleted, config_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        int result = mysqlAdapter.executeUpdate(sql,
                task.getTaskId(),
                task.getTrackId(),
                task.getModelName(),
                task.getModelCategory(),
                task.getModelFramework(),
                "train",
                task.getContainerName(),
                task.getContainerId() != null ? task.getContainerId() : "",
                task.getDockerImage(),
                task.getGpuIds(),
                task.getUseGpu() ? 1 : 0,
                task.getDatasetPath(),
                task.getModelPath(),
                task.getEpochs(),
                task.getBatchSize(),
                task.getImageSize(),
                task.getOptimizer(),
                task.getStatus(),
                task.getProgress(),
                task.getCurrentEpoch(),
                currentTime,
                currentTime,
                0,  // is_deleted
                task.getConfigJson() != null ? task.getConfigJson().toString() : "{}");
        
        log.info("训练任务已保存: taskId={}, model={}, category={}, framework={}", 
                task.getTaskId(), task.getModelName(), task.getModelCategory(), task.getModelFramework());
        return result > 0;
    }
    
    /**
     * 保存评估任务
     */
    private boolean saveEvaluateTask(TrainingTaskDTO task, String currentTime) {
        String sql = "INSERT INTO ai_training_tasks " +
                "(task_id, track_id, model_name, model_category, model_framework, task_type, " +
                "container_name, dataset_path, model_path, image_size, optimizer, " +
                "status, progress, current_epoch, start_time, created_at, is_deleted, config_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        int result = mysqlAdapter.executeUpdate(sql,
                task.getTaskId(),
                task.getTrackId() != null ? task.getTrackId() : "",
                task.getModelName(),
                task.getModelCategory(),
                task.getModelFramework(),
                "evaluate",
                task.getContainerName() != null ? task.getContainerName() : "",
                task.getDatasetPath(),
                task.getModelPath(),
                task.getImageSize(),
                task.getOptimizer(),
                task.getStatus(),
                task.getProgress(),
                task.getCurrentEpoch(),
                currentTime,
                currentTime,
                0,
                task.getConfigJson() != null ? task.getConfigJson().toString() : "{}");
        
        log.info("评估任务已保存: taskId={}, model={}", task.getTaskId(), task.getModelName());
        return result > 0;
    }
    
    /**
     * 保存预测任务
     */
    private boolean savePredictTask(TrainingTaskDTO task, String currentTime) {
        String sql = "INSERT INTO ai_training_tasks " +
                "(task_id, track_id, model_name, model_category, model_framework, task_type, " +
                "container_name, model_path, gpu_ids, use_gpu, " +
                "status, progress, current_epoch, start_time, created_at, is_deleted, config_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        int result = mysqlAdapter.executeUpdate(sql,
                task.getTaskId(),
                task.getTrackId() != null ? task.getTrackId() : "",
                task.getModelName(),
                task.getModelCategory(),
                task.getModelFramework(),
                "predict",
                task.getContainerName() != null ? task.getContainerName() : "",
                task.getModelPath(),
                task.getGpuIds(),
                task.getUseGpu() ? 1 : 0,
                task.getStatus(),
                task.getProgress(),
                task.getCurrentEpoch(),
                currentTime,
                currentTime,
                0,
                task.getConfigJson() != null ? task.getConfigJson().toString() : "{}");
        
        log.info("预测任务已保存: taskId={}, model={}", task.getTaskId(), task.getModelName());
        return result > 0;
    }
    
    /**
     * 保存导出任务
     */
    private boolean saveExportTask(TrainingTaskDTO task, String currentTime) {
        String sql = "INSERT INTO ai_training_tasks " +
                "(task_id, track_id, model_name, model_category, model_framework, task_type, " +
                "container_name, model_path, gpu_ids, use_gpu, " +
                "status, progress, current_epoch, start_time, created_at, is_deleted, config_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        int result = mysqlAdapter.executeUpdate(sql,
                task.getTaskId(),
                task.getTrackId() != null ? task.getTrackId() : "",
                task.getModelName(),
                task.getModelCategory(),
                task.getModelFramework(),
                "export",
                task.getContainerName() != null ? task.getContainerName() : "",
                task.getModelPath(),
                task.getGpuIds(),
                task.getUseGpu() ? 1 : 0,
                task.getStatus(),
                task.getProgress(),
                task.getCurrentEpoch(),
                currentTime,
                currentTime,
                0,
                task.getConfigJson() != null ? task.getConfigJson().toString() : "{}");
        
        log.info("导出任务已保存: taskId={}, model={}", task.getTaskId(), task.getModelName());
        return result > 0;
    }
    
    /**
     * 更新任务状态
     */
    public boolean updateTaskStatus(String taskId, String status, String message) {
        String sql = "UPDATE ai_training_tasks SET status = ?, error_message = ?, updated_at = ? WHERE task_id = ?";
        try {
            int result = mysqlAdapter.executeUpdate(sql, status, message, getCurrentTime(), taskId);
            log.info("任务状态已更新: taskId={}, status={}", taskId, status);
            return result > 0;
        } catch (Exception e) {
            log.error("更新任务状态失败: taskId={}, status={}", taskId, status, e);
            return false;
        }
    }
    
    /**
     * 更新任务停止状态（包含结束时间）
     */
    public boolean updateTaskStopStatus(String taskId, String endTime) {
        String sql = "UPDATE ai_training_tasks SET status = ?, end_time = ?, updated_at = ? WHERE task_id = ?";
        try {
            int result = mysqlAdapter.executeUpdate(sql, "stopped", endTime, getCurrentTime(), taskId);
            log.info("任务已停止: taskId={}, endTime={}", taskId, endTime);
            return result > 0;
        } catch (Exception e) {
            log.error("更新任务停止状态失败: taskId={}", taskId, e);
            return false;
        }
    }
    
    /**
     * 更新训练进度
     */
    public boolean updateTaskProgress(String taskId, int currentEpoch, String progress) {
        String sql = "UPDATE ai_training_tasks SET current_epoch = ?, progress = ?, updated_at = ? WHERE task_id = ?";
        try {
            int result = mysqlAdapter.executeUpdate(sql, currentEpoch, progress, getCurrentTime(), taskId);
            return result > 0;
        } catch (Exception e) {
            log.error("更新任务进度失败: taskId={}", taskId, e);
            return false;
        }
    }
    
    /**
     * 删除任务（软删除）
     */
    public boolean deleteTask(String taskId) {
        String sql = "UPDATE ai_training_tasks SET is_deleted = 1, deleted_at = ?, updated_at = ? WHERE task_id = ?";
        try {
            String currentTime = getCurrentTime();
            int result = mysqlAdapter.executeUpdate(sql, currentTime, currentTime, taskId);
            log.info("任务已删除: taskId={}", taskId);
            return result > 0;
        } catch (Exception e) {
            log.error("删除任务失败: taskId={}", taskId, e);
            return false;
        }
    }
    
    /**
     * 添加训练日志
     */
    public boolean addTrainingLog(String taskId, String logLevel, String logContent) {
        String sql = "INSERT INTO ai_training_logs (task_id, log_level, log_content, log_time, created_at) " +
                    "VALUES (?, ?, ?, ?, ?)";
        try {
            String currentTime = getCurrentTime();
            int result = mysqlAdapter.executeUpdate(sql, taskId, logLevel, logContent, currentTime, currentTime);
            return result > 0;
        } catch (Exception e) {
            log.error("添加训练日志失败: taskId={}", taskId, e);
            return false;
        }
    }
    
    /**
     * 添加训练指标到 ai_training_metrics 表
     * 
     * @param taskId 任务ID
     * @param epoch 当前轮次
     * @param step 当前步数
     * @param metricName 指标名称（如：train_loss, val_loss, train_acc, val_acc, mAP等）
     * @param metricValue 指标值
     * @param metricType 指标类型（loss, accuracy, map, f1等）
     * @return 是否成功
     */
    public boolean addTrainingMetric(String taskId, Integer epoch, Integer step, 
                                     String metricName, Double metricValue, String metricType) {
        String sql = "INSERT INTO ai_training_metrics " +
                    "(task_id, epoch, step, metric_name, metric_value, metric_type, recorded_at, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            String currentTime = getCurrentTime();
            int result = mysqlAdapter.executeUpdate(sql, 
                taskId, epoch, step, metricName, metricValue, metricType, currentTime, currentTime);
            return result > 0;
        } catch (Exception e) {
            log.error("添加训练指标失败: taskId={}, metric={}", taskId, metricName, e);
            return false;
        }
    }
    
    /**
     * 批量添加训练指标
     * 
     * @param taskId 任务ID
     * @param epoch 当前轮次
     * @param step 当前步数
     * @param metrics 指标映射（指标名称 -> 指标值）
     * @return 成功添加的指标数量
     */
    public int addTrainingMetrics(String taskId, Integer epoch, Integer step, 
                                   java.util.Map<String, Double> metrics) {
        int successCount = 0;
        for (java.util.Map.Entry<String, Double> entry : metrics.entrySet()) {
            String metricName = entry.getKey();
            Double metricValue = entry.getValue();
            
            // 根据指标名称推断类型
            String metricType = inferMetricType(metricName);
            
            if (addTrainingMetric(taskId, epoch, step, metricName, metricValue, metricType)) {
                successCount++;
            }
        }
        return successCount;
    }
    
    /**
     * 根据指标名称推断指标类型
     */
    private String inferMetricType(String metricName) {
        String lowerName = metricName.toLowerCase();
        if (lowerName.contains("loss")) {
            return "loss";
        } else if (lowerName.contains("acc") || lowerName.contains("accuracy")) {
            return "accuracy";
        } else if (lowerName.contains("map") || lowerName.contains("ap")) {
            return "map";
        } else if (lowerName.contains("f1") || lowerName.contains("precision") || lowerName.contains("recall")) {
            return "f1";
        } else if (lowerName.contains("iou")) {
            return "iou";
        } else if (lowerName.contains("lr") || lowerName.contains("learning_rate")) {
            return "lr";
        }
        return "other";
    }
    
    /**
     * 更新任务的最佳指标
     */
    public boolean updateBestMetric(String taskId, String metricName, Double metricValue) {
        String sql = "UPDATE ai_training_tasks " +
                    "SET best_metric = ?, best_metric_name = ?, updated_at = ? " +
                    "WHERE task_id = ?";
        try {
            int result = mysqlAdapter.executeUpdate(sql, metricValue, metricName, getCurrentTime(), taskId);
            log.info("更新最佳指标: taskId={}, metric={}={}", taskId, metricName, metricValue);
            return result > 0;
        } catch (Exception e) {
            log.error("更新最佳指标失败: taskId={}", taskId, e);
            return false;
        }
    }
    
    /**
     * 更新任务的训练和验证损失/准确率
     */
    public boolean updateTaskMetrics(String taskId, Double trainLoss, Double valLoss, 
                                     Double trainAcc, Double valAcc) {
        String sql = "UPDATE ai_training_tasks " +
                    "SET train_loss = ?, val_loss = ?, train_acc = ?, val_acc = ?, updated_at = ? " +
                    "WHERE task_id = ?";
        try {
            int result = mysqlAdapter.executeUpdate(sql, 
                trainLoss, valLoss, trainAcc, valAcc, getCurrentTime(), taskId);
            return result > 0;
        } catch (Exception e) {
            log.error("更新任务指标失败: taskId={}", taskId, e);
            return false;
        }
    }
    
    /**
     * 获取当前时间字符串
     */
    private String getCurrentTime() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }
}

