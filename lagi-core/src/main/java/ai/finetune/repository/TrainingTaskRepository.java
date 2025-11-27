package ai.finetune.repository;

import ai.database.impl.MysqlAdapter;
import ai.finetune.dto.TrainingTaskDTO;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                "status, progress, current_epoch, start_time, created_at, is_deleted, user_id, template_id, config_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

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
                task.getUseGpu(),
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
                task.getUserId(),  // 添加 user_id
                task.getTemplateId(), // 添加 template_id
                task.getConfigJson() != null ? task.getConfigJson().toString() : "{}");

        log.info("训练任务已保存: taskId={}, model={}, category={}, framework={}, userId={}",
                task.getTaskId(), task.getModelName(), task.getModelCategory(), task.getModelFramework(), task.getUserId());
        return result > 0;
    }

    /**
     * 保存评估任务
     */
    private boolean saveEvaluateTask(TrainingTaskDTO task, String currentTime) {
        String sql = "INSERT INTO ai_training_tasks " +
                "(task_id, track_id, model_name, model_category, model_framework, task_type, " +
                "container_name, dataset_path, model_path, image_size, optimizer, " +
                "status, progress, current_epoch, start_time, created_at, is_deleted, user_id, config_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

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
                task.getUserId(),
                task.getConfigJson() != null ? task.getConfigJson().toString() : "{}");

        log.info("评估任务已保存: taskId={}, model={}, userId={}", task.getTaskId(), task.getModelName(), task.getUserId());
        return result > 0;
    }

    /**
     * 保存预测任务
     */
    private boolean savePredictTask(TrainingTaskDTO task, String currentTime) {
        String sql = "INSERT INTO ai_training_tasks " +
                "(task_id, track_id, model_name, model_category, model_framework, task_type, " +
                "container_name, model_path, gpu_ids, use_gpu, " +
                "status, progress, current_epoch, start_time, created_at, is_deleted, user_id, config_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

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
                task.getUseGpu(),
                task.getStatus(),
                task.getProgress(),
                task.getCurrentEpoch(),
                currentTime,
                currentTime,
                0,
                task.getUserId(),
                task.getConfigJson() != null ? task.getConfigJson().toString() : "{}");

        log.info("预测任务已保存: taskId={}, model={}, userId={}", task.getTaskId(), task.getModelName(), task.getUserId());
        return result > 0;
    }

    /**
     * 保存导出任务
     */
    private boolean saveExportTask(TrainingTaskDTO task, String currentTime) {
        String sql = "INSERT INTO ai_training_tasks " +
                "(task_id, track_id, model_name, model_category, model_framework, task_type, " +
                "container_name, model_path, gpu_ids, use_gpu, " +
                "status, progress, current_epoch, start_time, created_at, is_deleted, user_id, config_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

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
                task.getUseGpu(),
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
     * 查询训练任务列表（分页）
     *
     * @param page 页码（从1开始）
     * @param pageSize 每页数量
     * @return 任务列表和统计信息
     */
    public Map<String, Object> getTaskList(int page, int pageSize) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 计算偏移量
            int offset = (page - 1) * pageSize;

            // 查询任务列表（仅查询训练类型的任务）
            String listSql = "SELECT task_id, dataset_path, epochs, status, progress, " +
                           "current_epoch, start_time, created_at, template_id " +
                           "FROM ai_training_tasks " +
                           "WHERE is_deleted = 0 AND task_type = 'train' " +
                           "ORDER BY created_at DESC " +
                           "LIMIT ? OFFSET ?";

            List<Map<String, Object>> tasks = mysqlAdapter.select(listSql, pageSize, offset);

            // 处理任务列表
            List<Map<String, Object>> taskList = new ArrayList<>();
            List<String> taskIdsToUpdate = new ArrayList<>();

            for (Map<String, Object> task : tasks) {
                Map<String, Object> taskMap = new HashMap<>();
                String taskId = (String) task.get("task_id");
                taskMap.put("taskId", taskId);

                // 从 dataset_path 提取数据集名称
                String datasetPath = (String) task.get("dataset_path");
                String datasetName = extractDatasetName(datasetPath);
                taskMap.put("datasetName", datasetName);

                taskMap.put("epochs", task.get("epochs"));

                String status = (String) task.get("status");
                if (status != null && status.contains(";")) {
                    status = status.split(";")[0];
                }
                taskMap.put("status", status);
                taskMap.put("progress", task.get("progress"));
                taskMap.put("currentEpoch", task.get("current_epoch"));
                taskMap.put("startTime", task.get("start_time").toString());
                taskMap.put("createdAt", task.get("created_at").toString());

                taskList.add(taskMap);

                // 收集需要更新状态的任务ID
                if ("pending".equals(status) || "starting".equals(status) ||
                    "running".equals(status) || "paused".equals(status) ) {
                    taskIdsToUpdate.add(taskId);
                }
            }

            // 更新任务状态
            updateTaskStatuses(taskList, taskIdsToUpdate);

            // 查询总数和状态统计
            long total = getTaskCount();
            Map<String, Integer> statusCountMap = getStatusCount();

            result.put("data", taskList);
            result.put("total", total);
            result.put("statusCount", statusCountMap);
            result.put("status", "SUCCESS");
        } catch (Exception e) {
            log.error("查询任务列表失败: page={}, pageSize={}", page, pageSize, e);
            result.put("status", "ERROR");
            result.put("message", "查询任务列表失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 更新任务状态
     */
    private void updateTaskStatuses(List<Map<String, Object>> taskList, List<String> taskIdsToUpdate) {
        if (taskIdsToUpdate.isEmpty()) {
            return;
        }

        try {
            // 将任务ID列表转换为逗号分隔的字符串
            String taskIds = String.join(",", taskIdsToUpdate);

            // 使用反射调用AITrainingServlet.batchGetTaskStatus方法
            Class<?> servletClass = Class.forName("ai.servlet.api.AITrainingServlet");
            java.lang.reflect.Method method = servletClass.getMethod("batchGetTaskStatus", String.class, String.class);
            Object resultObj = method.invoke(null, null, taskIds);

            if (resultObj instanceof List) {
                List<Map<String, Object>> updatedTasks = (List<Map<String, Object>>) resultObj;
                // 更新taskList中的任务状态
                for (Map<String, Object> updatedTask : updatedTasks) {
                    String updatedTaskId = (String) updatedTask.get("task_id");
                    String updatedStatus = (String) updatedTask.get("status");

                    // 在taskList中找到对应的任务并更新状态
                    for (Map<String, Object> taskMap : taskList) {
                        if (updatedTaskId.equals(taskMap.get("taskId"))) {
                            taskMap.put("status", updatedStatus);
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("更新任务状态失败，使用数据库中的状态: {}", e.getMessage());
        }
    }

    /**
     * 获取任务总数
     */
    private long getTaskCount() {
        try {
            String countSql = "SELECT COUNT(*) as total FROM ai_training_tasks " +
                            "WHERE is_deleted = 0 AND task_type = 'train'";
            List<Map<String, Object>> countResult = mysqlAdapter.select(countSql);
            if (!countResult.isEmpty()) {
                Object totalObj = countResult.get(0).get("total");
                if (totalObj instanceof Long) {
                    return (Long) totalObj;
                } else if (totalObj instanceof Integer) {
                    return ((Integer) totalObj).longValue();
                }
            }
        } catch (Exception e) {
            log.error("查询任务总数失败", e);
        }
        return 0;
    }

    /**
     * 获取各状态的任务数量统计
     */
    private Map<String, Integer> getStatusCount() {
        Map<String, Integer> statusCountMap = new HashMap<>();
        statusCountMap.put("running", 0);
        statusCountMap.put("stopped", 0);
        statusCountMap.put("waiting", 0);
        statusCountMap.put("completed", 0);
        statusCountMap.put("failed", 0);

        try {
            String statusCountSql = "SELECT status, COUNT(*) as count FROM ai_training_tasks " +
                                  "WHERE is_deleted = 0 AND task_type = 'train' " +
                                  "GROUP BY status";
            List<Map<String, Object>> statusCounts = mysqlAdapter.select(statusCountSql);

            for (Map<String, Object> statusCount : statusCounts) {
                String status = (String) statusCount.get("status");
                Object countObj = statusCount.get("count");
                int count = 0;
                if (countObj instanceof Long) {
                    count = ((Long) countObj).intValue();
                } else if (countObj instanceof Integer) {
                    count = (Integer) countObj;
                }

                // 映射状态
                if ("running".equalsIgnoreCase(status) || "training".equalsIgnoreCase(status)) {
                    statusCountMap.put("running", statusCountMap.get("running") + count);
                } else if ("stopped".equalsIgnoreCase(status) || "paused".equalsIgnoreCase(status)) {
                    statusCountMap.put("stopped", statusCountMap.get("stopped") + count);
                } else if ("waiting".equalsIgnoreCase(status)|| "paused".equalsIgnoreCase(status) || "pending".equalsIgnoreCase(status) || "starting".equalsIgnoreCase(status)) {
                    statusCountMap.put("waiting", statusCountMap.get("waiting") + count);
                } else if ("completed".equalsIgnoreCase(status) || "finished".equalsIgnoreCase(status)||"exited".equalsIgnoreCase(status)) {
                    statusCountMap.put("completed", statusCountMap.get("completed") + count);
                } else if ("failed".equalsIgnoreCase(status) || "error".equalsIgnoreCase(status)) {
                    statusCountMap.put("failed", statusCountMap.get("failed") + count);
                }
            }
        } catch (Exception e) {
            log.error("查询任务状态统计失败", e);
        }

        return statusCountMap;
    }

    /**
     * 从数据集路径中提取数据集名称
     * 例如: /app/data/datasets/greybrick-yolo/data.yaml -> greybrick-yolo
     */
    private String extractDatasetName(String datasetPath) {
        if (datasetPath == null || datasetPath.isEmpty()) {
            return "未知数据集";
        }

        // 移除文件名
        String path = datasetPath;
        if (path.contains("/")) {
            int lastSlash = path.lastIndexOf("/");
            if (lastSlash > 0) {
                path = path.substring(0, lastSlash);
            }
        }

        // 提取最后一级目录名
        if (path.contains("/")) {
            int lastSlash = path.lastIndexOf("/");
            return path.substring(lastSlash + 1);
        }

        return path;
    }

    /**
     * 获取当前时间字符串
     */
    private String getCurrentTime() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }

    /**
     * 根据模板ID获取模板信息
     * @param templateId 模板ID
     * @return 模板信息
     */
    public Map<String, Object> getTemplateInfoById(String templateId) {
        if (templateId == null || templateId.isEmpty()) {
            return null;
        }
        try {
            String sql = "SELECT * FROM template_info WHERE template_id = ?";
            List<Map<String, Object>> result = mysqlAdapter.select(sql, templateId);
            if (result != null && !result.isEmpty()) {
                return result.get(0);
            }
        } catch (Exception e) {
            log.error("根据模板ID查询模板信息失败: templateId={}", templateId, e);
        }
        return null;
    }

    /**
     * 根据模板ID获取模板字段列表
     * @param templateId 模板ID
     * @return 模板字段列表
     */
    public List<Map<String, Object>> getTemplateFieldsByTemplateId(String templateId) {
        if (templateId == null || templateId.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            String sql = "SELECT * FROM template_field WHERE template_id = ? ORDER BY sequence ASC";
            return mysqlAdapter.select(sql, templateId);
        } catch (Exception e) {
            log.error("根据模板ID查询模板字段列表失败: templateId={}", templateId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 根据任务ID查询训练任务详情
     */
    public Map<String, Object> getTaskDetailByTaskId(String taskId) {
        if (taskId == null || taskId.isEmpty()) {
            return null;
        }
        try {
            String sql = "SELECT *, template_id as template_id FROM ai_training_tasks WHERE task_id = ? AND is_deleted = 0";
            List<Map<String, Object>> result = mysqlAdapter.select(sql, taskId);
            if (result != null && !result.isEmpty()) {
                return result.get(0);
            }
        } catch (Exception e) {
            log.error("根据任务ID查询训练任务详情失败: taskId={}", taskId, e);
        }
        return null;
    }

    /**
     * 根据任务ID列表批量查询训练任务
     */
    public List<Map<String, Object>> getTasksByTaskIds(List<String> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            String placeholders = String.join(",", java.util.Collections.nCopies(taskIds.size(), "?"));
            String sql = "SELECT task_id, container_id, container_name, status, progress, current_epoch, " +
                        "start_time, end_time, created_at, updated_at " +
                        "FROM ai_training_tasks " +
                        "WHERE task_id IN (" + placeholders + ") AND is_deleted = 0";
            return mysqlAdapter.select(sql, taskIds.toArray());
        } catch (Exception e) {
            log.error("根据任务ID列表查询训练任务失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 根据容器ID列表批量查询训练任务
     */
    public List<Map<String, Object>> getTasksByContainerIds(List<String> containerIds) {
        if (containerIds == null || containerIds.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            String placeholders = String.join(",", java.util.Collections.nCopies(containerIds.size(), "?"));
            String sql = "SELECT task_id, container_id, container_name, status, progress, current_epoch, " +
                        "start_time, end_time, created_at, updated_at " +
                        "FROM ai_training_tasks " +
                        "WHERE container_id IN (" + placeholders + ") AND is_deleted = 0";
            return mysqlAdapter.select(sql, containerIds.toArray());
        } catch (Exception e) {
            log.error("根据容器ID列表查询训练任务失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 根据容器名称列表批量查询训练任务
     */
    public List<Map<String, Object>> getTasksByContainerNames(List<String> containerNames) {
        if (containerNames == null || containerNames.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            String placeholders = String.join(",", java.util.Collections.nCopies(containerNames.size(), "?"));
            String sql = "SELECT task_id, container_id, container_name, status, progress, current_epoch, " +
                        "start_time, end_time, created_at, updated_at " +
                        "FROM ai_training_tasks " +
                        "WHERE container_name IN (" + placeholders + ") AND is_deleted = 0";
            return mysqlAdapter.select(sql, containerNames.toArray());
        } catch (Exception e) {
            log.error("根据容器名称列表查询训练任务失败", e);
            return new ArrayList<>();
        }
    }

}















