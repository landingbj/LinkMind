package ai.config;

import cn.hutool.core.util.StrUtil;
import cn.hutool.setting.yaml.YamlUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.Map;

/**
 * 模型与数据集存储配置加载器
 * 支持环境变量注入，便于容器化部署
 */
@Slf4j
@Data
public class ModelStorageConfig {
    
    private String basePath;
    private String modelsPath;
    private String datasetsPath;
    private String trainingOutputPath;
    private String projectPath;
    
    private static ModelStorageConfig instance;
    
    private ModelStorageConfig() {
        loadConfig();
    }
    
    public static ModelStorageConfig getInstance() {
        if (instance == null) {
            synchronized (ModelStorageConfig.class) {
                if (instance == null) {
                    instance = new ModelStorageConfig();
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
            InputStream inputStream = this.getClass().getResourceAsStream("/model-storage.yaml");
            if (inputStream == null) {
                log.warn("未找到 model-storage.yaml 配置文件，使用默认路径");
                setDefaultPaths();
                return;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> config = (Map<String, Object>) YamlUtil.load(inputStream, Map.class);
            if (config == null || !config.containsKey("storage")) {
                log.warn("model-storage.yaml 配置格式错误，使用默认路径");
                setDefaultPaths();
                return;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> storage = (Map<String, Object>) config.get("storage");
            
            // 解析路径，支持环境变量替换
            this.basePath = resolvePath((String) storage.get("base_path"), "/app/data");
            this.modelsPath = resolvePath((String) storage.get("models"), this.basePath + "/models");
            this.datasetsPath = resolvePath((String) storage.get("datasets"), this.basePath + "/datasets");
            this.trainingOutputPath = resolvePath((String) storage.get("training_output"), this.basePath + "/training_output");
            this.projectPath = resolvePath((String) storage.get("project"), this.basePath + "/project");
            
            log.info("模型存储配置加载成功: models={}, datasets={}", this.modelsPath, this.datasetsPath);
            
        } catch (Exception e) {
            log.error("加载 model-storage.yaml 配置失败，使用默认路径", e);
            setDefaultPaths();
        }
    }
    
    /**
     * 解析路径，支持环境变量替换
     * 格式: ${ENV_VAR:default_value}
     */
    private String resolvePath(String path, String defaultValue) {
        if (StrUtil.isBlank(path)) {
            return defaultValue;
        }
        
        // 替换环境变量 ${VAR:default}
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
     * 设置默认路径
     */
    private void setDefaultPaths() {
        String base = System.getenv("STORAGE_BASE_PATH");
        if (StrUtil.isBlank(base)) {
            base = "/app/data";
        }
        
        this.basePath = base;
        this.modelsPath = base + "/models";
        this.datasetsPath = base + "/datasets";
        this.trainingOutputPath = base + "/training_output";
        this.projectPath = base + "/project";
    }
}
