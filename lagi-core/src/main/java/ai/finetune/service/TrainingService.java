package ai.finetune.service;

import ai.database.impl.MysqlAdapter;
import ai.finetune.config.ModelConfigManager;
import ai.finetune.dto.TrainingTaskDTO;
import ai.finetune.repository.TrainingTaskRepository;
import cn.hutool.json.JSONObject;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * 训练服务统一入口
 * 整合 ModelConfigManager、TrainingTaskRepository 和数据库表
 * 
 * 使用的数据表：
 * - ai_model_templates    模型配置模板
 * - ai_training_tasks     训练任务主表
 * - ai_training_logs      训练日志表
 * - ai_training_metrics   训练指标表
 */
@Slf4j
public class TrainingService {
    
    private final MysqlAdapter mysqlAdapter;
    private final ModelConfigManager configManager;
    private final TrainingTaskRepository taskRepository;
    
    /**
     * 构造函数
     */
    public TrainingService(MysqlAdapter mysqlAdapter) {
        this.mysqlAdapter = mysqlAdapter;
        this.configManager = new ModelConfigManager(mysqlAdapter);
        this.taskRepository = new TrainingTaskRepository(mysqlAdapter);
        
        // 加载模型配置
        this.configManager.loadConfigsFromDatabase();
        log.info("训练服务初始化完成，加载了 {} 个模型配置", 
                configManager.getAllConfigs().size());
    }
    
    /**
     * 创建训练任务
     * 
     * @param config 训练配置（包含 model_name 等参数）
     * @param taskType 任务类型：train, evaluate, predict, export
     * @return 任务 DTO
     */
    public TrainingTaskDTO createTask(JSONObject config, String taskType) {
        // 生成任务ID
        String taskId = config.getStr("task_id");
        if (taskId == null || taskId.isEmpty()) {
            taskId = generateTaskId();
            config.put("task_id", taskId);
        }
        
        // 获取模型配置（从数据库或智能推断）
        String modelName = config.getStr("model_name", "custom_model");
        ModelConfigManager.ModelConfig modelConfig = configManager.getModelConfig(modelName, config);
        
        // 构建任务 DTO
        TrainingTaskDTO task = TrainingTaskDTO.builder()
                .fromConfig(config, taskId, taskType)
                .withModelConfig(modelConfig)
                .build();
        
        log.info("创建任务: taskId={}, type={}, model={}, category={}, framework={}", 
                taskId, taskType, modelName, modelConfig.getModelCategory(), modelConfig.getModelFramework());
        
        return task;
    }
    
    /**
     * 保存训练任务到数据库
     * 
     * @param task 任务 DTO
     * @return 是否成功
     */
    public boolean saveTask(TrainingTaskDTO task) {
        return taskRepository.saveTask(task);
    }
    
    /**
     * 创建并保存训练任务（一步完成）
     * 
     * @param config 训练配置
     * @param taskType 任务类型
     * @return 任务 DTO
     */
    public TrainingTaskDTO createAndSaveTask(JSONObject config, String taskType) {
        TrainingTaskDTO task = createTask(config, taskType);
        boolean success = saveTask(task);
        
        if (success) {
            log.info("任务已保存到数据库: taskId={}", task.getTaskId());
        } else {
            log.error("任务保存失败: taskId={}", task.getTaskId());
        }
        
        return task;
    }
    
    /**
     * 更新任务状态
     */
    public boolean updateTaskStatus(String taskId, String status, String message) {
        return taskRepository.updateTaskStatus(taskId, status, message);
    }
    
    /**
     * 更新任务进度
     */
    public boolean updateTaskProgress(String taskId, int currentEpoch, String progress) {
        return taskRepository.updateTaskProgress(taskId, currentEpoch, progress);
    }
    
    /**
     * 停止任务
     */
    public boolean stopTask(String taskId, String endTime) {
        return taskRepository.updateTaskStopStatus(taskId, endTime);
    }
    
    /**
     * 删除任务（软删除）
     */
    public boolean deleteTask(String taskId) {
        return taskRepository.deleteTask(taskId);
    }
    
    /**
     * 添加训练日志到 ai_training_logs 表
     */
    public boolean addLog(String taskId, String logLevel, String logContent) {
        return taskRepository.addTrainingLog(taskId, logLevel, logContent);
    }
    
    /**
     * 添加训练指标到 ai_training_metrics 表
     */
    public boolean addMetric(String taskId, int epoch, String metricName, double metricValue) {
        try {
            String sql = "INSERT INTO ai_training_metrics " +
                        "(task_id, epoch, metric_name, metric_value, metric_type, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, NOW())";
            
            int result = mysqlAdapter.executeUpdate(sql, taskId, epoch, metricName, metricValue, "training");
            
            if (result > 0) {
                log.debug("添加训练指标: taskId={}, epoch={}, {}={}", 
                         taskId, epoch, metricName, metricValue);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("添加训练指标失败: taskId={}", taskId, e);
            return false;
        }
    }
    
    /**
     * 批量添加训练指标
     */
    public boolean addMetrics(String taskId, int epoch, JSONObject metrics) {
        boolean allSuccess = true;
        
        for (String metricName : metrics.keySet()) {
            Object value = metrics.get(metricName);
            if (value instanceof Number) {
                double metricValue = ((Number) value).doubleValue();
                boolean success = addMetric(taskId, epoch, metricName, metricValue);
                allSuccess = allSuccess && success;
            }
        }
        
        return allSuccess;
    }
    
    /**
     * 注册新模型配置（运行时动态添加）
     */
    public void registerModelConfig(String modelName, String modelCategory, String modelFramework) {
        configManager.registerModelConfig(modelName, modelCategory, modelFramework);
        log.info("注册新模型配置: model={}, category={}, framework={}", 
                modelName, modelCategory, modelFramework);
    }
    
    /**
     * 重新加载模型配置（从数据库）
     */
    public void reloadModelConfigs() {
        configManager.reload();
        log.info("重新加载模型配置，共 {} 个", configManager.getAllConfigs().size());
    }
    
    /**
     * 获取模型配置
     */
    public ModelConfigManager.ModelConfig getModelConfig(String modelName, JSONObject requestConfig) {
        return configManager.getModelConfig(modelName, requestConfig);
    }
    
    /**
     * 获取所有模型配置
     */
    public java.util.Map<String, ModelConfigManager.ModelConfig> getAllModelConfigs() {
        return configManager.getAllConfigs();
    }
    
    /**
     * 生成任务ID
     */
    private String generateTaskId() {
        return "task_" + UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * 生成追踪ID
     */
    public static String generateTrackId() {
        return "track_" + UUID.randomUUID().toString().replace("-", "");
    }
}

