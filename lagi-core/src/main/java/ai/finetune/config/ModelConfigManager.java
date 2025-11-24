package ai.finetune.config;

import ai.database.impl.MysqlAdapter;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型配置管理器 - 从数据库加载模型配置
 * 支持动态配置模型的类别和框架
 * 
 * 优先级：
 * 1. 请求参数中明确指定
 * 2. 数据库 ai_model_templates 表配置
 * 3. 关键词智能推断
 * 4. 默认值
 */
@Slf4j
public class ModelConfigManager {
    
    private final MysqlAdapter mysqlAdapter;
    private final Map<String, ModelConfig> configCache = new ConcurrentHashMap<>();
    private final Map<String, String> categoryKeywords = new ConcurrentHashMap<>();
    private final Map<String, String> frameworkKeywords = new ConcurrentHashMap<>();
    
    /**
     * 模型配置类
     */
    @Data
    public static class ModelConfig {
        private String modelName;
        private String modelCategory;
        private String modelFramework;
        private String description;
        private JSONObject defaultConfig;
        
        public ModelConfig(String modelName, String modelCategory, String modelFramework) {
            this.modelName = modelName;
            this.modelCategory = modelCategory;
            this.modelFramework = modelFramework;
        }
        
        public ModelConfig(String modelName, String modelCategory, String modelFramework, 
                          String description, String defaultConfigJson) {
            this.modelName = modelName;
            this.modelCategory = modelCategory;
            this.modelFramework = modelFramework;
            this.description = description;
            if (defaultConfigJson != null && !defaultConfigJson.isEmpty()) {
                try {
                    this.defaultConfig = JSONUtil.parseObj(defaultConfigJson);
                } catch (Exception e) {
                    log.warn("解析模型默认配置失败: {}", e.getMessage());
                    this.defaultConfig = new JSONObject();
                }
            }
        }
    }
    
    public ModelConfigManager(MysqlAdapter mysqlAdapter) {
        this.mysqlAdapter = mysqlAdapter;
        initDefaultKeywords();
    }
    
    /**
     * 初始化默认关键词映射
     */
    private void initDefaultKeywords() {
        // 类别关键词
        categoryKeywords.put("yolo,ssd,rcnn,centernet,tracknet,retinanet,efficientdet,detector", "detection");
        categoryKeywords.put("unet,fcn,pidnet,deeplab,segnet,maskrcnn,pspnet,segmentation", "segmentation");
        categoryKeywords.put("crnn,ocr,recogn,tesseract,paddleocr,easyocr", "recognition");
        categoryKeywords.put("reid,clip,triplet,metric", "reid");
        categoryKeywords.put("hrnet,keypoint,openpose,alphapose", "keypoint");
        categoryKeywords.put("resnet,osnet,vgg,mobilenet,efficientnet,densenet,inception,feature", "feature_extraction");
        categoryKeywords.put("tdeed,event,action", "event_detection");
        categoryKeywords.put("transnet,video,temporal", "video_segmentation");
        categoryKeywords.put("classif,alexnet,squeezenet", "classification");
        categoryKeywords.put("track,sort,deepsort,bytetrack", "tracking");
        categoryKeywords.put("pose,posenet", "pose_estimation");
        categoryKeywords.put("gan,generator,diffusion,vae", "gan");
        
        // 框架关键词
        frameworkKeywords.put("paddle,paddleocr", "paddle");
        frameworkKeywords.put("transnet,tf,tensorflow", "tensorflow");
        frameworkKeywords.put("keras", "keras");
        frameworkKeywords.put("mxnet", "mxnet");
        frameworkKeywords.put("caffe", "caffe");
    }
    
    /**
     * 从数据库加载模型配置
     */
    public void loadConfigsFromDatabase() {
        try {
            String sql = "SELECT model_name, model_category, model_framework, description, default_config " +
                        "FROM ai_model_templates WHERE is_active = 1";
            
            List<Map<String, Object>> results = mysqlAdapter.select(sql);
            
            for (Map<String, Object> row : results) {
                String modelName = (String) row.get("model_name");
                String modelCategory = (String) row.get("model_category");
                String modelFramework = (String) row.get("model_framework");
                String description = (String) row.get("description");
                String defaultConfig = (String) row.get("default_config");
                
                ModelConfig config = new ModelConfig(
                    modelName, 
                    modelCategory, 
                    modelFramework, 
                    description, 
                    defaultConfig
                );
                
                configCache.put(modelName.toLowerCase(), config);
                log.debug("加载模型配置: {}, category={}, framework={}", 
                         modelName, modelCategory, modelFramework);
            }
            
            log.info("从数据库加载了 {} 个模型配置", configCache.size());
            
        } catch (Exception e) {
            log.error("从数据库加载模型配置失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 获取模型配置（核心方法）
     * 
     * @param modelName 模型名称
     * @param requestConfig 请求配置（可能包含用户指定的 category 和 framework）
     * @return 模型配置
     */
    public ModelConfig getModelConfig(String modelName, JSONObject requestConfig) {
        String lowerModelName = modelName.toLowerCase();
        
        // 1. 优先使用请求参数中明确指定的值
        String requestCategory = requestConfig.getStr("model_category");
        String requestFramework = requestConfig.getStr("model_framework");
        
        if (requestCategory != null && requestFramework != null) {
            log.debug("使用请求中指定的模型配置: model={}, category={}, framework={}", 
                     modelName, requestCategory, requestFramework);
            return new ModelConfig(modelName, requestCategory, requestFramework);
        }
        
        // 2. 从数据库缓存中查找
        ModelConfig cachedConfig = configCache.get(lowerModelName);
        if (cachedConfig != null) {
            // 如果请求中部分指定，则覆盖
            String finalCategory = requestCategory != null ? requestCategory : cachedConfig.getModelCategory();
            String finalFramework = requestFramework != null ? requestFramework : cachedConfig.getModelFramework();
            
            log.debug("使用数据库配置: model={}, category={}, framework={}", 
                     modelName, finalCategory, finalFramework);
            return new ModelConfig(modelName, finalCategory, finalFramework);
        }
        
        // 3. 根据关键词智能推断
        String inferredCategory = requestCategory != null ? requestCategory : inferCategoryFromKeywords(lowerModelName);
        String inferredFramework = requestFramework != null ? requestFramework : inferFrameworkFromKeywords(lowerModelName);
        
        log.info("未找到模型配置，使用智能推断: model={}, category={}, framework={}", 
                modelName, inferredCategory, inferredFramework);
        
        return new ModelConfig(modelName, inferredCategory, inferredFramework);
    }
    
    /**
     * 根据关键词推断模型类别
     */
    private String inferCategoryFromKeywords(String lowerModelName) {
        for (Map.Entry<String, String> entry : categoryKeywords.entrySet()) {
            String[] keywords = entry.getKey().split(",");
            for (String keyword : keywords) {
                if (lowerModelName.contains(keyword.trim())) {
                    return entry.getValue();
                }
            }
        }
        return "custom"; // 默认值
    }
    
    /**
     * 根据关键词推断模型框架
     */
    private String inferFrameworkFromKeywords(String lowerModelName) {
        for (Map.Entry<String, String> entry : frameworkKeywords.entrySet()) {
            String[] keywords = entry.getKey().split(",");
            for (String keyword : keywords) {
                if (lowerModelName.contains(keyword.trim())) {
                    return entry.getValue();
                }
            }
        }
        return "pytorch"; // 默认值（大多数模型使用 PyTorch）
    }
    
    /**
     * 注册模型配置（运行时动态添加）
     */
    public void registerModelConfig(String modelName, String modelCategory, String modelFramework) {
        ModelConfig config = new ModelConfig(modelName, modelCategory, modelFramework);
        configCache.put(modelName.toLowerCase(), config);
        log.info("注册模型配置: model={}, category={}, framework={}", 
                modelName, modelCategory, modelFramework);
    }
    
    /**
     * 获取所有已缓存的配置
     */
    public Map<String, ModelConfig> getAllConfigs() {
        return new ConcurrentHashMap<>(configCache);
    }
    
    /**
     * 重新加载配置
     */
    public void reload() {
        configCache.clear();
        loadConfigsFromDatabase();
    }
}

