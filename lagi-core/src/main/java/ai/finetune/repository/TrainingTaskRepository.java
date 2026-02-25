package ai.finetune.repository;

import ai.config.pojo.ModelMapper;
import ai.database.impl.MysqlAdapter;
import ai.finetune.config.ModelConfigManager;
import ai.finetune.dto.TrainingTaskDTO;
import ai.finetune.util.ParameterUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
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
    private static final Gson gson = new Gson();

    private final ModelConfigManager modelConfigManager;

    public TrainingTaskRepository(MysqlAdapter mysqlAdapter) {
        this.mysqlAdapter = mysqlAdapter;
        this.modelConfigManager = new ModelConfigManager(mysqlAdapter);
        this.modelConfigManager.loadConfigsFromDatabase();
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
     * 保存训练任务到数据库
     */
    public void saveTrainingTaskToDB(String taskId, String trackId, String containerName, JSONObject config, ModelMapper modelMapper) {
        try {
            String modelName = config.getStr("model_name", "custom_model");
            ModelConfigManager.ModelConfig modelConfig = modelConfigManager.getModelConfig(modelName, config);

            // 从配置中读取数据集名称
            String datasetName = config.getStr("dataset_name", "外部数据集");
            String datasetPath = config.getStr("data", "");
            if (StrUtil.isNotBlank(datasetPath)) {
                // 通过 DAO 查询数据集名称
                String queriedName = getDatasetNameByStoragePath(datasetPath);
                if (StrUtil.isNotBlank(queriedName)) {
                    datasetName = queriedName;
                    config.set("dataset_name", datasetName);
                }
            }

            // 构建 TrainingTaskDTO
            TrainingTaskDTO task = TrainingTaskDTO.builder()
                    .taskId(taskId)
                    .trackId(trackId)
                    .taskType("train")
                    .modelName(modelName)
                    .modelCategory(modelConfig.getModelCategory())
                    .modelFramework(modelConfig.getModelFramework())
                    .containerName(containerName)
                    .dockerImage(config.getStr("_docker_image", ""))
                    .datasetPath(datasetPath)
                    .modelPath(config.getStr("model_path", ""))
                    .epochs(config.getInt("epochs", null))
                    .batchSize(config.getInt("batch", null))
                    .imageSize(config.getInt("imgsz") != null ? String.valueOf(config.getInt("imgsz")) : null)
                    .optimizer(config.getStr("optimizer", "sgd"))
                    .gpuIds(config.getStr("device", "0"))
                    .useGpu(config.getStr("device", "0").equals("cpu") ? "0" : "1")
                    .status("starting")
                    .progress("0%")
                    .configJson(config)
                    .build();

            ParameterUtil.attrMapping(modelMapper.getAttrMapping(), config, task);
            // 设置额外的字段（如果 Builder 不支持）
            TrainingTaskDTO taskDTO = task;
            if (StrUtil.isNotBlank(datasetName)) {
                taskDTO.setDatasetName(datasetName);
            }
            if (StrUtil.isNotBlank(config.getStr("user_id"))) {
                taskDTO.setUserId(config.getStr("user_id"));
            }

            if (config.getStr("template_id") != null) {
                TrainingTaskRepository repository = new TrainingTaskRepository(MysqlAdapter.getInstance());

                List<Map<String, Object>> templateFields = repository.getTemplateFieldsByTemplateId(config.getStr("template_id"));
                if (templateFields != null && templateFields.size()>0){
                    Integer template_int_id = ((Number) templateFields.get(0).get("id")).intValue();
                    taskDTO.setTemplateId(template_int_id);
                }
            }

            saveTask(taskDTO);
            log.info("训练任务已保存到数据库: taskId={}, model={}, category={}, framework={}",
                    taskId, modelName, modelConfig.getModelCategory(), modelConfig.getModelFramework());
        } catch (Exception e) {
            log.error("保存训练任务到数据库失败: taskId={}, error={}", taskId, e.getMessage(), e);
            throw new RuntimeException("保存训练任务到数据库失败: " + e.getMessage(), e);
        }
    }

    /**
     * 保存评估任务到数据库
     */
    public void saveEvaluationTaskToDB(String taskId, JSONObject config) {
        try {
            String modelName = config.getStr("model_name", "custom_model");
            ModelConfigManager.ModelConfig modelConfig = modelConfigManager.getModelConfig(modelName, config);

            TrainingTaskDTO task = TrainingTaskDTO.builder()
                    .taskId(taskId)
                    .taskType("evaluate")
                    .modelName(modelName)
                    .modelCategory(modelConfig.getModelCategory())
                    .modelFramework(modelConfig.getModelFramework())
                    .datasetPath(config.getStr("data", ""))
                    .modelPath(config.getStr("model_path", ""))
                    .imageSize(config.getInt("imgsz") != null ? String.valueOf(config.getInt("imgsz")) : null)
                    .optimizer(config.getStr("optimizer", "sgd"))
                    .status("running")
                    .progress("0%")
                    .configJson(config)
                    .build();

            // 设置额外的字段
            if (StrUtil.isNotBlank(config.getStr("user_id"))) {
                task.setUserId(config.getStr("user_id"));
            }

            saveTask(task);
            log.info("评估任务已保存到数据库: taskId={}, model={}", taskId, modelName);
        } catch (Exception e) {
            log.error("保存评估任务到数据库失败: taskId={}, error={}", taskId, e.getMessage(), e);
            throw new RuntimeException("保存评估任务到数据库失败: " + e.getMessage(), e);
        }
    }

    /**
     * 保存预测任务到数据库
     */
    public void savePredictionTaskToDB(String taskId, JSONObject config) {
        try {
            String modelName = config.getStr("model_name", "custom_model");
            ModelConfigManager.ModelConfig modelConfig = modelConfigManager.getModelConfig(modelName, config);

            TrainingTaskDTO task = TrainingTaskDTO.builder()
                    .taskId(taskId)
                    .taskType("predict")
                    .modelName(modelName)
                    .modelCategory(modelConfig.getModelCategory())
                    .modelFramework(modelConfig.getModelFramework())
                    .modelPath(config.getStr("model_path", ""))
                    .gpuIds(config.getStr("device", "cpu"))
                    .useGpu(config.getStr("device", "cpu").equals("cpu") ? "0" : "1")
                    .status("running")
                    .progress("0%")
                    .configJson(config)
                    .build();

            // 设置额外的字段
            if (StrUtil.isNotBlank(config.getStr("user_id"))) {
                task.setUserId(config.getStr("user_id"));
            }

            saveTask(task);
            log.info("预测任务已保存到数据库: taskId={}, model={}", taskId, modelName);
        } catch (Exception e) {
            log.error("保存预测任务到数据库失败: taskId={}, error={}", taskId, e.getMessage(), e);
            throw new RuntimeException("保存预测任务到数据库失败: " + e.getMessage(), e);
        }
    }


    /**
     * 保存转换任务到数据库
     */
    public void saveConvertTaskToDB(String taskId, JSONObject config) {
        try {
            String modelName = config.getStr("model_name", "custom_model");
            ModelConfigManager.ModelConfig modelConfig = modelConfigManager.getModelConfig(modelName, config);

            TrainingTaskDTO task = TrainingTaskDTO.builder()
                    .taskId(taskId)
                    .taskType("export")
                    .modelName(modelName)
                    .modelCategory(modelConfig.getModelCategory())
                    .modelFramework(modelConfig.getModelFramework())
                    .modelPath(config.getStr("model_path", ""))
                    .gpuIds(config.getStr("device", "cpu"))
                    .useGpu(config.getStr("device", "cpu").equals("cpu") ? "0" : "1")
                    .status("running")
                    .progress("0%")
                    .configJson(config)
                    .build();

            // 设置额外的字段
            if (StrUtil.isNotBlank(config.getStr("user_id"))) {
                task.setUserId(config.getStr("user_id"));
            }

            saveTask(task);
            log.info("转换任务已保存到数据库: taskId={}, model={}", taskId, modelName);
        } catch (Exception e) {
            log.error("保存转换任务到数据库失败: taskId={}, error={}", taskId, e.getMessage(), e);
            throw new RuntimeException("保存转换任务到数据库失败: " + e.getMessage(), e);
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
                "status, progress, current_epoch, start_time, created_at, is_deleted, user_id, template_id, config_json, output_path) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

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
                task.getConfigJson() != null ? task.getConfigJson().toString() : "{}",
                task.getOutputPath()
        );

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
                task.getUserId(),
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
            // 先查旧状态 + 任务类型，避免重复触发
            String oldStatus = null;
            String taskType = null;
            try {
                List<Map<String, Object>> taskRows = mysqlAdapter.select(
                        "SELECT status, task_type FROM ai_training_tasks WHERE task_id = ? AND is_deleted = 0 LIMIT 1",
                        taskId
                );
                if (taskRows != null && !taskRows.isEmpty()) {
                    oldStatus = (String) taskRows.get(0).get("status");
                    taskType = (String) taskRows.get(0).get("task_type");
                }
            } catch (Exception e) {
                log.debug("查询旧状态失败（不影响更新）: taskId={}", taskId, e);
            }

            int result = mysqlAdapter.executeUpdate(sql, status, message, getCurrentTime(), taskId);
            log.info("任务状态已更新: taskId={}, status={}", taskId, status);

            // 训练完成后自动入库（Docker 轮询常见终态为 exited/finished）
            // 仅对 train 任务触发，且仅在从非终态切到终态时触发一次
            boolean isTrainTask = "train".equalsIgnoreCase(taskType);
            boolean isDoneStatus = "completed".equalsIgnoreCase(status)
                    || "exited".equalsIgnoreCase(status)
                    || "finished".equalsIgnoreCase(status);
            boolean wasDoneStatus = "completed".equalsIgnoreCase(oldStatus)
                    || "exited".equalsIgnoreCase(oldStatus)
                    || "finished".equalsIgnoreCase(oldStatus);
            if (result > 0 && isTrainTask && isDoneStatus && !wasDoneStatus) {
                try {
                    log.info("检测到训练任务进入终态，触发训练后自动入库: taskId={}, oldStatus={}, newStatus={}", taskId, oldStatus, status);
                    ai.finetune.utils.TrainingPostProcessor postProcessor = new ai.finetune.utils.TrainingPostProcessor();
                    postProcessor.processTrainingCompletion(taskId);
                } catch (Exception e) {
                    log.warn("训练后自动入库处理失败: taskId={}", taskId, e);
                }
            }
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
        String sql = "INSERT INTO ai_training_logs (task_id, log_level, log_message, created_at) " +
                "VALUES (?, ?, ?, ?)";
        try {
            String currentTime = getCurrentTime();
            int result = mysqlAdapter.executeUpdate(sql, taskId, logLevel, logContent, currentTime);
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
     * 查询任务列表（分页，支持指定任务类型）
     *
     * @param taskType 任务类型：train, predict
     * @param page 页码（从1开始）
     * @param pageSize 每页数量
     * @return 任务列表和统计信息
     */
    public Map<String, Object> getTaskList(String taskType, int page, int pageSize) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 计算偏移量
            int offset = (page - 1) * pageSize;

            // 如果是 export 类型，查询特定字段
            String listSql;
            if ("export".equals(taskType)) {
                listSql = "SELECT task_id, model_name, model_framework, created_at, updated_at, remark, status " +
                        "FROM ai_training_tasks WHERE is_deleted = 0 AND task_type = ? " +
                        "ORDER BY created_at DESC LIMIT ? OFFSET ?";
            } else {
                // 查询任务列表（根据任务类型过滤）
                listSql = "SELECT * FROM ai_training_tasks WHERE is_deleted = 0 AND task_type = ? " +
                        "ORDER BY created_at DESC LIMIT ? OFFSET ?";
            }

            List<Map<String, Object>> tasks = mysqlAdapter.select(listSql, taskType, pageSize, offset);

            // 处理任务列表
            List<Map<String, Object>> taskList = new ArrayList<>();
            List<String> taskIdsToUpdate = new ArrayList<>();

            for (Map<String, Object> task : tasks) {
                Map<String, Object> taskMap = new HashMap<>();

                // 如果是 export 类型，使用特殊处理
                if ("export".equals(taskType)) {
                    // 导出任务特定字段处理
                    taskMap = convertTask2Export(task);
                    taskList.add(taskMap);
                } else {
                    // 其他任务类型的原有处理逻辑
                    String taskId = (String) task.get("task_id");
                    taskMap.put("taskId", taskId);

                    // 从 dataset_path 提取数据集名称
                    //String datasetPath = (String) task.get("dataset_path");
                    taskMap.put("datasetName", task.get("dataset_name"));
                    taskMap.put("modelName", task.get("model_name"));

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
            }

            // 查询总数和状态统计
            long total = getTaskCount(taskType);
            Map<String, Integer> statusCountMap = getStatusCount(taskType);

            result.put("data", taskList);
            result.put("total", total);
            result.put("statusCount", statusCountMap);
            result.put("status", "SUCCESS");


            // 异步更新任务状态（非 export 类型）
            if (!"export".equals(taskType) && !taskIdsToUpdate.isEmpty()) {
                new Thread(() -> {
                    try {
                        updateTaskStatuses(taskList, taskIdsToUpdate);
                    } catch (Exception e) {
                        log.warn("异步更新任务状态失败: {}", e.getMessage());
                    }
                }).start();
            }
        } catch (Exception e) {
            log.error("查询任务列表失败: page={}, pageSize={}", page, pageSize, e);
            result.put("status", "ERROR");
            result.put("message", "查询任务列表失败: " + e.getMessage());
        }

        return result;
    }


    private Map<String, Object> convertTask2Export(Map<String, Object> task) {
        Map<String, Object> taskMap = new HashMap<>();

        // 1. 基础字段映射（安全类型转换）
        taskMap.put("taskId", getStringValue(task, "task_id"));
        taskMap.put("modelName", task.get("model_name"));
        taskMap.put("modelFramework", task.get("model_framework"));
        taskMap.put("status", task.get("status"));

        // 2. 时间字段处理（转换为字符串）
        taskMap.put("createTime", task.get("created_at") != null ? task.get("created_at").toString() : null);
        taskMap.put("updateTime", task.get("updated_at") != null ? task.get("updated_at").toString() : null);

        // 3. 初始化remark相关字段为null（统一空值基准）
        taskMap.put("modelFileSize", null);
        taskMap.put("exportFileSize", null);
        taskMap.put("exportFormat", null);

        // 4. 解析remark JSON字段（提取通用方法减少重复）
        String taskId = getStringValue(task, "task_id");
        String remark = getStringValue(task, "remark");
        if (remark != null && !remark.trim().isEmpty()) {
            try {
                JsonObject remarkJson = gson.fromJson(remark, JsonObject.class);
                if (remarkJson != null) {
                    // 调用通用解析方法处理各字段
                    taskMap.put("modelFileSize", getJsonFieldAsString(remarkJson, "model_file_size"));
                    taskMap.put("exportFileSize", getJsonFieldAsString(remarkJson, "export_file_size"));
                    taskMap.put("exportFormat", getJsonFieldAsString(remarkJson, "export_format"));
                }
            } catch (Exception e) {
                log.warn("解析 remark JSON 失败: taskId={}, remark={}, error={}",
                        taskId, remark, e.getMessage());
                // 异常时保持初始的null值即可，无需重复赋值
            }
        }

        return taskMap;
    }

    /**
     * 安全获取Map中的字符串值（避免ClassCastException）
     * @param map 源Map
     * @param key 键名
     * @return 字符串值，类型不匹配/为空时返回null
     */
    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 从JsonObject中安全提取字段值并转为字符串（支持所有JSON类型）
     * @param jsonObject JSON对象
     * @param fieldName 字段名
     * @return 字符串值，字段不存在/为null时返回null
     */
    private String getJsonFieldAsString(JsonObject jsonObject, String fieldName) {
        // 检查字段是否存在且非null
        if (!jsonObject.has(fieldName) || jsonObject.get(fieldName).isJsonNull()) {
            return null;
        }

        JsonElement element = jsonObject.get(fieldName);
        // 处理基本类型（数字/字符串）
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            // 数字类型转为字符串，保持兼容性
            if (primitive.isNumber()) {
                return primitive.getAsNumber().toString();
            } else {
                return primitive.getAsString();
            }
        }
        // 非基本类型（如对象/数组）直接转字符串
        return element.toString();
    }

    /**
     * 获取任务总数（支持指定任务类型）
     */
    private long getTaskCount(String taskType) {
        try {
            String countSql = "SELECT COUNT(*) as total FROM ai_training_tasks " +
                    "WHERE is_deleted = 0 AND task_type = ?";
            List<Map<String, Object>> countResult = mysqlAdapter.select(countSql, taskType);
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
     * 获取各状态的任务数量统计（支持指定任务类型）
     */
    private Map<String, Integer> getStatusCount(String taskType) {
        Map<String, Integer> statusCountMap = new HashMap<>();
        statusCountMap.put("running", 0);
        statusCountMap.put("stopped", 0);
        statusCountMap.put("waiting", 0);
        statusCountMap.put("completed", 0);
        statusCountMap.put("failed", 0);

        try {
            String statusCountSql = "SELECT status, COUNT(*) as count FROM ai_training_tasks " +
                    "WHERE is_deleted = 0 AND task_type = ? " +
                    "GROUP BY status";
            List<Map<String, Object>> statusCounts = mysqlAdapter.select(statusCountSql, taskType);

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

            // 查询总数和状态统计
            long total = getTaskCount();
            Map<String, Integer> statusCountMap = getStatusCount();

            result.put("data", taskList);
            result.put("total", total);
            result.put("statusCount", statusCountMap);
            result.put("status", "SUCCESS");


            // 异步更新任务状态
            if (!taskIdsToUpdate.isEmpty()) {
                new Thread(() -> {
                    try {
                        updateTaskStatuses(taskList, taskIdsToUpdate);
                    } catch (Exception e) {
                        log.warn("异步更新任务状态失败: {}", e.getMessage());
                    }
                }).start();
            }
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
    public Map<String, Object> getTemplateInfoById(Integer templateId) {
        if (templateId == null) {
            return null;
        }
        try {
            String sql = "SELECT * FROM template_info WHERE id = ?";
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
        if (templateId == null) {
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
            String sql = "SELECT * FROM ai_training_tasks WHERE task_id = ? AND is_deleted = 0";
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

    public boolean updateTaskByTaskId(String taskId, Map<String, Object> updateData) {
        if (taskId == null || taskId.isEmpty() || updateData == null || updateData.isEmpty()) {
            log.warn("更新任务参数无效: taskId={}, updateData={}", taskId, updateData);
            return false;
        }

        try {
            // 构建动态SQL
            StringBuilder sqlBuilder = new StringBuilder("UPDATE ai_training_tasks SET ");
            List<Object> params = new ArrayList<>();

            // 添加需要更新的字段
            boolean first = true;
            for (Map.Entry<String, Object> entry : updateData.entrySet()) {
                if (!first) {
                    sqlBuilder.append(", ");
                }
                sqlBuilder.append(entry.getKey()).append(" = ?");
                params.add(entry.getValue());
                first = false;
            }

            // 添加更新时间和where条件
            sqlBuilder.append(", updated_at = ? WHERE task_id = ?");
            params.add(getCurrentTime());
            params.add(taskId);

            String sql = sqlBuilder.toString();

            int result = mysqlAdapter.executeUpdate(sql, params.toArray());
            log.info("任务信息已更新: taskId={}, affectedRows={}", taskId, result);

            // 如果更新了 train_dir 且任务状态是终态，触发自动入库
            if (result > 0 && updateData.containsKey("train_dir")) {
                try {
                    // 查询当前任务状态和类型
                    String checkSql = "SELECT status, task_type FROM ai_training_tasks WHERE task_id = ? AND is_deleted = 0 LIMIT 1";
                    List<Map<String, Object>> taskRows = mysqlAdapter.select(checkSql, taskId);
                    if (taskRows != null && !taskRows.isEmpty()) {
                        String status = (String) taskRows.get(0).get("status");
                        String taskType = (String) taskRows.get(0).get("task_type");

                        // 如果是训练任务且状态是终态，触发自动入库
                        boolean isTrainTask = "train".equalsIgnoreCase(taskType);
                        boolean isDoneStatus = "completed".equalsIgnoreCase(status)
                                || "exited".equalsIgnoreCase(status)
                                || "finished".equalsIgnoreCase(status);

                        if (isTrainTask && isDoneStatus) {
                            // 检查是否已处理过
                            String checkModelSql = "SELECT id FROM models WHERE description LIKE ? AND is_deleted = 0 LIMIT 1";
                            List<Map<String, Object>> existingModels = mysqlAdapter.select(checkModelSql, "%训练任务ID: " + taskId + "%");
                            if (existingModels == null || existingModels.isEmpty()) {
                                log.info("检测到 train_dir 更新且任务处于终态，触发训练后自动入库: taskId={}, status={}", taskId, status);
                                try {
                                    ai.finetune.utils.TrainingPostProcessor postProcessor = new ai.finetune.utils.TrainingPostProcessor();
                                    postProcessor.processTrainingCompletion(taskId);
                                } catch (Exception e) {
                                    log.warn("训练后自动入库处理失败: taskId={}", taskId, e);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("检查任务状态失败（不影响更新）: taskId={}", taskId, e);
                }
            }

            return result > 0;
        } catch (Exception e) {
            log.error("根据任务ID更新任务信息失败: taskId={}", taskId, e);
            return false;
        }
    }

    /**
     * 根据存储路径查询数据集名称
     * @param storagePath 数据集存储路径
     * @return 数据集名称，如果未找到则返回 null
     */
    public String getDatasetNameByStoragePath(String storagePath) {
        if (storagePath == null || storagePath.isEmpty()) {
            return null;
        }
        try {
            String sql = "SELECT name FROM dataset_upload WHERE storage_path = ? AND is_deleted = 0 LIMIT 1";
            List<Map<String, Object>> result = mysqlAdapter.select(sql, storagePath);
            if (result != null && !result.isEmpty()) {
                Object name = result.get(0).get("name");
                return name != null ? name.toString() : null;
            }
        } catch (Exception e) {
            log.error("根据存储路径查询数据集名称失败: storagePath={}, error={}", storagePath, e.getMessage(), e);
        }
        return null;
    }
}















