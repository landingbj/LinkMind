package ai.finetune.dto;

import ai.finetune.config.ModelConfigManager;
import cn.hutool.json.JSONObject;
import lombok.Data;

/**
 * 训练任务数据传输对象
 * 统一封装任务信息，简化数据库操作
 */
@Data
public class TrainingTaskDTO {
    // 基本信息
    private String taskId;
    private String trackId;
    private String taskType;  // train, evaluate, predict, export
    
    // 模型信息
    private String modelName;
    private String modelCategory;
    private String modelFramework;
    private String modelVersion;
    
    // Docker 信息
    private String containerName;
    private String containerId;
    private String dockerImage;
    
    // 训练参数
    private String datasetPath;
    private String datasetName;
    private String datasetType;
    private String modelPath;
    private String checkpointPath;
    private String outputPath;
    
    // 训练配置
    private Integer epochs;
    private Integer batchSize;
    private Double learningRate;
    private String imageSize;
    private String optimizer;
    
    // GPU 配置
    private String gpuIds;
    private Boolean useGpu;
    
    // 状态信息
    private String status;          // pending, starting, running, paused, stopped, completed, failed
    private String progress;
    private Integer currentEpoch;
    private Integer currentStep;
    private Integer totalSteps;
    
    // 指标信息
    private Double trainLoss;
    private Double valLoss;
    private Double trainAcc;
    private Double valAcc;
    private Double bestMetric;
    private String bestMetricName;
    
    // 文件路径
    private String trainDir;
    private String weightsPath;
    private String bestWeightsPath;
    private String logFilePath;
    
    // 其他信息
    private String errorMessage;
    private String startTime;
    private String endTime;
    private Integer estimatedTime;
    private String userId;
    private String projectId;
    private Integer templateId;
    private Integer priority;
    private String tags;
    private String remark;
    
    // 完整配置 JSON
    private JSONObject configJson;
    
    /**
     * 从配置创建 DTO（推荐使用 Builder）
     */
    public static TrainingTaskDTOBuilder builder() {
        return new TrainingTaskDTOBuilder();
    }
    
    /**
     * Builder 模式，支持链式调用
     */
    public static class TrainingTaskDTOBuilder {
        private final TrainingTaskDTO dto = new TrainingTaskDTO();
        
        public TrainingTaskDTOBuilder taskId(String taskId) {
            dto.taskId = taskId;
            return this;
        }
        
        public TrainingTaskDTOBuilder trackId(String trackId) {
            dto.trackId = trackId;
            return this;
        }
        
        public TrainingTaskDTOBuilder taskType(String taskType) {
            dto.taskType = taskType;
            return this;
        }
        
        public TrainingTaskDTOBuilder modelName(String modelName) {
            dto.modelName = modelName;
            return this;
        }
        
        public TrainingTaskDTOBuilder modelCategory(String modelCategory) {
            dto.modelCategory = modelCategory;
            return this;
        }
        
        public TrainingTaskDTOBuilder modelFramework(String modelFramework) {
            dto.modelFramework = modelFramework;
            return this;
        }
        
        public TrainingTaskDTOBuilder containerName(String containerName) {
            dto.containerName = containerName;
            return this;
        }
        
        public TrainingTaskDTOBuilder dockerImage(String dockerImage) {
            dto.dockerImage = dockerImage;
            return this;
        }
        
        public TrainingTaskDTOBuilder datasetPath(String datasetPath) {
            dto.datasetPath = datasetPath;
            return this;
        }
        
        public TrainingTaskDTOBuilder modelPath(String modelPath) {
            dto.modelPath = modelPath;
            return this;
        }
        
        public TrainingTaskDTOBuilder epochs(Integer epochs) {
            dto.epochs = epochs;
            return this;
        }
        
        public TrainingTaskDTOBuilder batchSize(Integer batchSize) {
            dto.batchSize = batchSize;
            return this;
        }
        
        public TrainingTaskDTOBuilder imageSize(String imageSize) {
            dto.imageSize = imageSize;
            return this;
        }
        
        public TrainingTaskDTOBuilder gpuIds(String gpuIds) {
            dto.gpuIds = gpuIds;
            dto.useGpu = gpuIds != null && !gpuIds.equals("cpu");
            return this;
        }
        
        public TrainingTaskDTOBuilder optimizer(String optimizer) {
            dto.optimizer = optimizer;
            return this;
        }
        
        public TrainingTaskDTOBuilder status(String status) {
            dto.status = status;
            return this;
        }
        
        public TrainingTaskDTOBuilder progress(String progress) {
            dto.progress = progress;
            return this;
        }
        
        public TrainingTaskDTOBuilder configJson(JSONObject configJson) {
            dto.configJson = configJson;
            return this;
        }
        
        /**
         * 从 JSONObject 配置填充 DTO
         */
        public TrainingTaskDTOBuilder fromConfig(JSONObject config, String taskId, String taskType) {
            dto.taskId = taskId;
            dto.taskType = taskType;
            dto.configJson = config;
            
            // 基本信息
            dto.trackId = config.getStr("track_id", "");
            dto.modelName = config.getStr("model_name", "custom_model");
            
            // 容器信息
            dto.containerName = config.getStr("_container_name", "");
            dto.dockerImage = config.getStr("_docker_image", "");
            
            // 训练参数
            dto.datasetPath = config.getStr("data", "");
            dto.modelPath = config.getStr("model_path", "");
            dto.epochs = config.getInt("epochs", null);
            dto.batchSize = config.getInt("batch", null);
            dto.imageSize = config.getInt("imgsz") != null ? String.valueOf(config.getInt("imgsz")) : null;
            dto.optimizer = config.getStr("optimizer", "sgd");
            
            // GPU 配置
            String device = config.getStr("device", "0");
            dto.gpuIds = device;
            dto.useGpu = !device.equals("cpu");
            
            // 状态
            dto.status = config.getStr("_status", "running");
            dto.progress = "0%";
            dto.currentEpoch = 0;
            
            return this;
        }
        
        /**
         * 使用 ModelConfigManager 获取模型配置
         */
        public TrainingTaskDTOBuilder withModelConfig(ModelConfigManager.ModelConfig modelConfig) {
            dto.modelCategory = modelConfig.getModelCategory();
            dto.modelFramework = modelConfig.getModelFramework();
            return this;
        }
        
        public TrainingTaskDTO build() {
            // 设置默认值
            if (dto.status == null) dto.status = "pending";
            if (dto.progress == null) dto.progress = "0%";
            if (dto.currentEpoch == null) dto.currentEpoch = 0;
            
            return dto;
        }
    }
}

