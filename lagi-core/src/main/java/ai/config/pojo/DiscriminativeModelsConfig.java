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


    @JsonProperty("execution_mode")
    private String executionMode;  //接收 docker / k8s

    @JsonProperty("k8s")
    private K8sConfig k8s;   // 新增

    /**
     * K8s 整体配置
     * 包含集群配置和各模型的K8s专属配置
     */
    @Data
    public static class K8sConfig {
        /**
         * K8s集群基础配置
         */
        @JsonProperty("cluster_config")
        private ClusterConfig clusterConfig;

        /**
         * YOLO模型的K8s专属配置
         */
        @JsonProperty("yolo")
        private YoloK8sConfig yolo;

        /**
         * deeplab模型K8s运行配置
         */
        @JsonProperty("deeplab")
        private DeeplabK8sConfig deeplab;

        /**
         * K8s集群连接配置
         */
        @Data
        public static class ClusterConfig {
            @JsonProperty("apiServer")
            private String apiServer;
            private String token;
            private String namespace;
            @JsonProperty("verifyTls")
            private Boolean verifyTls;
        }

        /**
         * YOLO模型的K8s运行配置
         */
        @Data
        public static class YoloK8sConfig {
            private Boolean enable;  // 独立启用开关

            /**
             * YOLO模型K8s Pod专属配置
             */
            @JsonProperty("k8s_config")
            private YoloK8sPodConfig k8sConfig;

            /**
             * YOLO Pod的具体配置
             */
            @Data
            public static class YoloK8sPodConfig {
                @JsonProperty("dockerImage")
                private String dockerImage;
            }
        }

        /**
         * YOLO模型的K8s运行配置
         */
        @Data
        public static class DeeplabK8sConfig {
            private Boolean enable;  // 独立启用开关

            /**
             * YOLO模型K8s Pod专属配置
             */
            @JsonProperty("k8s_config")
            private Deeplab8sPodConfig k8sConfig;

            /**
             * YOLO Pod的具体配置
             */
            @Data
            public static class Deeplab8sPodConfig {
                @JsonProperty("dockerImage")
                private String dockerImage;
            }
        }
    }
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
     * DeepLab 语义分割模型配置
     */
    @JsonProperty("deeplab")
    private DeeplabConfig deeplab;
    
    /**
     * TrackNetV3 轨迹跟踪模型配置
     */
    @JsonProperty("tracknetv3")
    private TrackNetV3Config tracknetv3;
    
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
     * 通用训练器配置
     * 用于支持动态配置的模型训练
     */
    @JsonProperty("universal_trainer")
    private ModelTrainingConfig universalTrainer;
    
    /**
     * YOLO 模型特定配置（为了类型明确性）
     */
    @Data
    public static class YoloConfig extends ModelTrainingConfig {
        // 继承所有 ModelTrainingConfig 的字段
        // 可以在此添加 YOLO 特有的配置项
    }
    
    /**
     * DeepLab 模型特定配置
     */
    @Data
    public static class DeeplabConfig extends ModelTrainingConfig {
        // 继承所有 ModelTrainingConfig 的字段
        // 可以在此添加 DeepLab 特有的配置项
    }
    
    /**
     * TrackNetV3 模型特定配置
     */
    @Data
    public static class TrackNetV3Config extends ModelTrainingConfig {
        // 继承所有 ModelTrainingConfig 的字段
        // 可以在此添加 TrackNetV3 特有的配置项
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
        @JsonProperty("shm_size")
        private String shmSize;
        private String gpu;
        private Boolean rm;
        @JsonProperty("image_name")
        private String imageName;
        @JsonProperty("log_path_prefix")
        private String logPathPrefix;
        
        /**
         * 检查配置是否完整（仅检查必需字段）
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
