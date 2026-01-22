package ai.config;

import cn.hutool.core.util.StrUtil;
import cn.hutool.setting.yaml.YamlUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.Map;

/**
 * 训练相关配置加载器
 */
@Slf4j
@Data
public class TrainingConfig {
    
    /**
     * 版本递增类型
     */
    public enum VersionIncrementType {
        MAJOR,  // 主版本：V1.0.0 -> V2.0.0
        MINOR,  // 次版本：V1.0.0 -> V1.1.0
        PATCH   // 补丁版本：V1.0.0 -> V1.0.1
    }
    
    /**
     * 版本号生成策略
     */
    public enum VersionStrategy {
        LATEST,     // 基于最新版本递增（推荐）：查询该模型的所有版本，找到最新版本号，然后递增
        TIMESTAMP,  // 版本号+时间戳：V1.1.0-20260121-143022
        SEQUENCE,   // 版本号+序号：V1.1.0-1, V1.1.0-2
        TASKID      // 版本号+任务ID：V1.1.0-task_xxx
    }
    
    private VersionIncrementType versionIncrement = VersionIncrementType.MINOR;
    private VersionStrategy versionStrategy = VersionStrategy.LATEST;
    private String defaultOutputDir;
    private boolean autoSaveAfterTraining = true;
    private boolean autoCreateVersion = true;
    private String modelFilePattern = "{model_name}_{version}_{timestamp}.pt";
    
    private static TrainingConfig instance;
    
    private TrainingConfig() {
        loadConfig();
    }
    
    public static TrainingConfig getInstance() {
        if (instance == null) {
            synchronized (TrainingConfig.class) {
                if (instance == null) {
                    instance = new TrainingConfig();
                }
            }
        }
        return instance;
    }
    
    /**
     * 加载配置文件
     */
    private void loadConfig() {
        try {
            InputStream inputStream = this.getClass().getResourceAsStream("/training-config.yaml");
            if (inputStream == null) {
                log.warn("未找到 training-config.yaml 配置文件，使用默认配置");
                setDefaultConfig();
                return;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> config = (Map<String, Object>) YamlUtil.load(inputStream, Map.class);
            if (config == null || !config.containsKey("training")) {
                log.warn("training-config.yaml 配置格式错误，使用默认配置");
                setDefaultConfig();
                return;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> training = (Map<String, Object>) config.get("training");
            
            // 解析版本递增类型
            String incrementType = (String) training.get("version_increment");
            if (StrUtil.isNotBlank(incrementType)) {
                try {
                    this.versionIncrement = VersionIncrementType.valueOf(incrementType.toUpperCase());
                } catch (IllegalArgumentException e) {
                    log.warn("无效的版本递增类型: {}, 使用默认值 MINOR", incrementType);
                }
            }
            
            // 解析默认输出目录（支持环境变量）
            String outputDir = (String) training.get("default_output_dir");
            if (StrUtil.isNotBlank(outputDir)) {
                this.defaultOutputDir = resolvePath(outputDir, "/app/data/trained_models");
            } else {
                this.defaultOutputDir = "/app/data/trained_models";
            }
            
            // 解析自动保存配置
            Object autoSave = training.get("auto_save_after_training");
            if (autoSave != null) {
                this.autoSaveAfterTraining = Boolean.parseBoolean(autoSave.toString());
            }
            
            // 解析自动创建版本配置
            Object autoCreate = training.get("auto_create_version");
            if (autoCreate != null) {
                this.autoCreateVersion = Boolean.parseBoolean(autoCreate.toString());
            }
            
            // 解析模型文件命名规则
            String pattern = (String) training.get("model_file_pattern");
            if (StrUtil.isNotBlank(pattern)) {
                this.modelFilePattern = pattern;
            }
            
            // 解析版本号生成策略
            String strategy = (String) training.get("version_strategy");
            if (StrUtil.isNotBlank(strategy)) {
                try {
                    this.versionStrategy = VersionStrategy.valueOf(strategy.toUpperCase());
                } catch (IllegalArgumentException e) {
                    log.warn("无效的版本号生成策略: {}, 使用默认值 LATEST", strategy);
                }
            }
            
            log.info("训练配置加载成功: versionIncrement={}, versionStrategy={}, defaultOutputDir={}", 
                    this.versionIncrement, this.versionStrategy, this.defaultOutputDir);
            
        } catch (Exception e) {
            log.error("加载 training-config.yaml 配置失败，使用默认配置", e);
            setDefaultConfig();
        }
    }
    
    /**
     * 解析路径，支持环境变量替换
     */
    private String resolvePath(String path, String defaultValue) {
        if (StrUtil.isBlank(path)) {
            return defaultValue;
        }
        
        String resolved = path;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\$\\{([^:}]+)(?::([^}]+))?\\}");
        java.util.regex.Matcher matcher = pattern.matcher(path);
        
        while (matcher.find()) {
            String envVar = matcher.group(1);
            String defaultVal = matcher.group(2);
            String envValue = System.getenv(envVar);
            
            if (StrUtil.isNotBlank(envValue)) {
                resolved = resolved.replace(matcher.group(0), envValue);
            } else if (StrUtil.isNotBlank(defaultVal)) {
                resolved = resolved.replace(matcher.group(0), defaultVal);
            } else {
                resolved = resolved.replace(matcher.group(0), "");
            }
        }
        
        return resolved;
    }
    
    /**
     * 设置默认配置
     */
    private void setDefaultConfig() {
        this.versionIncrement = VersionIncrementType.MINOR;
        this.versionStrategy = VersionStrategy.LATEST;
        String base = System.getenv("STORAGE_BASE_PATH");
        if (StrUtil.isBlank(base)) {
            base = "/app/data";
        }
        this.defaultOutputDir = base + "/trained_models";
        this.autoSaveAfterTraining = true;
        this.autoCreateVersion = true;
        this.modelFilePattern = "{model_name}_{version}_{timestamp}.pt";
    }
}
