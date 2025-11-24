package ai.config.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * 判别式模型训练平台配置
 * 支持多种视觉模型的训练
 */
@Data
public class DiscriminativeModelsConfig {
    
    /**
     * 通用 SSH 配置（可选）
     * 如果某个模型没有单独配置 SSH，则使用此通用配置
     */
    @JsonProperty("common_ssh")
    private SshConfig commonSsh;
    
    /**
     * YOLO 目标检测模型配置
     */
    @JsonProperty("yolo")
    private YoloConfig yolo;
    
    /**
     * 图像分类模型配置（预留）
     */
    @JsonProperty("image_classification")
    private ModelTrainingConfig imageClassification;
    
    /**
     * 语义分割模型配置（预留）
     */
    @JsonProperty("semantic_segmentation")
    private ModelTrainingConfig semanticSegmentation;
    
    /**
     * Stable Diffusion 模型配置（预留）
     */
    @JsonProperty("stable_diffusion")
    private ModelTrainingConfig stableDiffusion;
    
    /**
     * YOLO 模型特定配置（为了类型明确性）
     */
    @Data
    public static class YoloConfig extends ModelTrainingConfig {
        // 继承所有 ModelTrainingConfig 的字段
        // 可以在此添加 YOLO 特有的配置项
    }
    
    /**
     * 单个模型的训练配置
     */
    @Data
    public static class ModelTrainingConfig {
        /**
         * 是否启用该模型训练
         * false 时该模块不可用但不影响其他模块和项目启动
         */
        private Boolean enable;
        
        /**
         * SSH 连接配置（可选，不配置则使用 commonSsh）
         */
        private SshConfig ssh;
        
        /**
         * Docker 配置
         */
        private DockerConfig docker;
        
        /**
         * 默认训练配置（具体字段根据模型不同而不同）
         */
        @JsonProperty("default_config")
        private Map<String, Object> defaultConfig;
    }
    
    /**
     * SSH 配置
     */
    @Data
    public static class SshConfig {
        private String host;
        private Integer port;
        private String username;
        private String password;
        
        /**
         * 检查配置是否完整
         */
        public boolean isValid() {
            return host != null && !host.isEmpty() 
                    && port != null 
                    && username != null && !username.isEmpty()
                    && password != null && !password.isEmpty();
        }
    }
    
    /**
     * Docker 配置
     */
    @Data
    public static class DockerConfig {
        private String image;
        @JsonProperty("volume_mount")
        private String volumeMount;
        
        /**
         * 检查配置是否完整
         */
        public boolean isValid() {
            return image != null && !image.isEmpty() 
                    && volumeMount != null && !volumeMount.isEmpty();
        }
    }
    
    /**
     * 获取指定模型的有效 SSH 配置
     * 优先使用模型自己的 SSH 配置，如果没有则使用通用配置
     */
    public SshConfig getEffectiveSshConfig(ModelTrainingConfig modelConfig) {
        if (modelConfig == null) {
            return null;
        }
        
        // 优先使用模型自己的配置
        if (modelConfig.getSsh() != null && modelConfig.getSsh().isValid()) {
            return modelConfig.getSsh();
        }
        
        // 否则使用通用配置
        if (commonSsh != null && commonSsh.isValid()) {
            return commonSsh;
        }
        
        return null;
    }
}
