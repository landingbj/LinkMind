package ai.finetune.utils;

import ai.database.impl.MysqlAdapter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 模型与数据集管理工具类
 * 提供上传、入库、版本控制等功能
 */
@Slf4j
public class ModelDatasetManager {
    
    private final MysqlAdapter mysqlAdapter;
    
    public ModelDatasetManager() {
        this.mysqlAdapter = new MysqlAdapter("mysql");
    }
    
    /**
     * 保存数据集到数据库
     */
    public Long saveDataset(String name, String path, String description, String userId, 
                           Long fileSize, String fileType) {
        try {
            String sql = "INSERT INTO datasets (name, path, description, user_id, file_size, file_type, " +
                        "status, created_at, updated_at, is_deleted) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 'active', NOW(), NOW(), 0)";
            
            int result = mysqlAdapter.executeUpdate(sql, name, path, description, userId, 
                                                    fileSize, fileType);
            
            if (result > 0) {
                // 获取插入的ID
                String selectSql = "SELECT id FROM datasets WHERE name = ? AND path = ? ORDER BY id DESC LIMIT 1";
                List<Map<String, Object>> results = mysqlAdapter.select(selectSql, name, path);
                if (results != null && !results.isEmpty()) {
                    Object idObj = results.get(0).get("id");
                    if (idObj instanceof Number) {
                        return ((Number) idObj).longValue();
                    }
                }
            }
            
            log.error("保存数据集失败: name={}, path={}", name, path);
            return null;
            
        } catch (Exception e) {
            log.error("保存数据集到数据库失败: name={}, path={}", name, path, e);
            return null;
        }
    }
    
    /**
     * 保存模型到数据库
     */
    public Long saveModel(String name, String path, String version, Long datasetId, 
                         Long introductionId, String modelType, String framework,
                         Long fileSize, String fileType, String description, String userId) {
        try {
            String sql = "INSERT INTO models (name, path, version, dataset_id, introduction_id, " +
                        "model_type, framework, file_size, file_type, description, user_id, " +
                        "status, created_at, updated_at, is_deleted) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'active', NOW(), NOW(), 0)";
            
            int result = mysqlAdapter.executeUpdate(sql, name, path, version, datasetId, introductionId,
                                                    modelType, framework, fileSize, fileType, 
                                                    description, userId);
            
            if (result > 0) {
                // 获取插入的ID
                String selectSql = "SELECT id FROM models WHERE name = ? AND path = ? ORDER BY id DESC LIMIT 1";
                List<Map<String, Object>> results = mysqlAdapter.select(selectSql, name, path);
                if (results != null && !results.isEmpty()) {
                    Object idObj = results.get(0).get("id");
                    if (idObj instanceof Number) {
                        return ((Number) idObj).longValue();
                    }
                }
            }
            
            log.error("保存模型失败: name={}, path={}", name, path);
            return null;
            
        } catch (Exception e) {
            log.error("保存模型到数据库失败: name={}, path={}", name, path, e);
            return null;
        }
    }
    
    /**
     * 根据模型ID获取模型信息
     */
    public Map<String, Object> getModelById(Long modelId) {
        try {
            String sql = "SELECT * FROM models WHERE id = ? AND is_deleted = 0";
            List<Map<String, Object>> results = mysqlAdapter.select(sql, modelId);
            if (results != null && !results.isEmpty()) {
                return results.get(0);
            }
        } catch (Exception e) {
            log.error("查询模型信息失败: modelId={}", modelId, e);
        }
        return null;
    }
    
    /**
     * 根据数据集ID获取数据集信息
     */
    public Map<String, Object> getDatasetById(Long datasetId) {
        try {
            String sql = "SELECT * FROM datasets WHERE id = ? AND is_deleted = 0";
            List<Map<String, Object>> results = mysqlAdapter.select(sql, datasetId);
            if (results != null && !results.isEmpty()) {
                return results.get(0);
            }
        } catch (Exception e) {
            log.error("查询数据集信息失败: datasetId={}", datasetId, e);
        }
        return null;
    }
    
    /**
     * 查询所有模型列表
     */
    public List<Map<String, Object>> listModels(String userId) {
        try {
            String sql = "SELECT id, name, path, version, model_type, framework, " +
                        "file_size, status, description, created_at " +
                        "FROM models WHERE is_deleted = 0";
            
            if (userId != null && !userId.isEmpty()) {
                sql += " AND user_id = ?";
                return mysqlAdapter.select(sql, userId);
            } else {
                sql += " ORDER BY created_at DESC";
                return mysqlAdapter.select(sql);
            }
        } catch (Exception e) {
            log.error("查询模型列表失败", e);
            return null;
        }
    }
    
    /**
     * 查询所有数据集列表
     */
    public List<Map<String, Object>> listDatasets(String userId) {
        try {
            String sql = "SELECT id, name, path, file_size, file_type, status, " +
                        "description, created_at " +
                        "FROM datasets WHERE is_deleted = 0";
            
            if (userId != null && !userId.isEmpty()) {
                sql += " AND user_id = ?";
                return mysqlAdapter.select(sql, userId);
            } else {
                sql += " ORDER BY created_at DESC";
                return mysqlAdapter.select(sql);
            }
        } catch (Exception e) {
            log.error("查询数据集列表失败", e);
            return null;
        }
    }
    
    /**
     * 训练完成后自动入库新模型
     * 复制原模型信息，更新路径和版本号
     */
    public Long saveTrainedModel(Long originalModelId, String newModelPath, String taskId) {
        try {
            // 获取原模型信息
            Map<String, Object> originalModel = getModelById(originalModelId);
            if (originalModel == null) {
                log.warn("原模型不存在: modelId={}", originalModelId);
                return null;
            }
            
            // 获取新版本号
            String currentVersion = (String) originalModel.get("version");
            String newVersion = ModelVersionManager.incrementVersion(currentVersion);
            
            // 复制模型信息
            String name = (String) originalModel.get("name");
            Long datasetId = originalModel.get("dataset_id") != null ? 
                            ((Number) originalModel.get("dataset_id")).longValue() : null;
            Long introductionId = originalModel.get("introduction_id") != null ?
                                 ((Number) originalModel.get("introduction_id")).longValue() : null;
            String modelType = (String) originalModel.get("model_type");
            String framework = (String) originalModel.get("framework");
            String description = "训练后自动生成，原模型ID: " + originalModelId + 
                               ", 训练任务ID: " + taskId;
            String userId = (String) originalModel.get("user_id");
            
            // 获取文件大小
            Long fileSize = null;
            try {
                Path path = Paths.get(newModelPath);
                if (Files.exists(path)) {
                    fileSize = Files.size(path);
                }
            } catch (IOException e) {
                log.warn("获取模型文件大小失败: path={}", newModelPath, e);
            }
            
            // 获取文件类型
            String fileType = getFileExtension(newModelPath);
            
            // 保存新模型
            Long newModelId = saveModel(name, newModelPath, newVersion, datasetId, introductionId,
                                      modelType, framework, fileSize, fileType, description, userId);
            
            if (newModelId != null) {
                log.info("训练后模型已自动入库: modelId={}, version={}, path={}", 
                        newModelId, newVersion, newModelPath);
            }
            
            return newModelId;
            
        } catch (Exception e) {
            log.error("训练后自动入库模型失败: originalModelId={}, newModelPath={}", 
                     originalModelId, newModelPath, e);
            return null;
        }
    }
    
    /**
     * 如果训练时未传递模型ID，直接将训练完成的模型文件放置在指定目录
     */
    public String saveUntrackedModel(String modelName, String sourcePath, String taskId) {
        try {
            ai.config.TrainingConfig trainingConfig = ai.config.TrainingConfig.getInstance();
            String outputDir = trainingConfig.getDefaultOutputDir();
            
            // 确保目录存在
            Path outputPath = Paths.get(outputDir);
            if (!Files.exists(outputPath)) {
                Files.createDirectories(outputPath);
            }
            
            // 生成文件名
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String version = ModelVersionManager.getInitialVersion();
            String fileName = trainingConfig.getModelFilePattern()
                    .replace("{model_name}", modelName)
                    .replace("{version}", version)
                    .replace("{timestamp}", timestamp);
            
            // 复制文件
            Path source = Paths.get(sourcePath);
            Path target = outputPath.resolve(fileName);
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            
            String finalPath = target.toString();
            log.info("未跟踪模型已保存: path={}, taskId={}", finalPath, taskId);
            
            return finalPath;
            
        } catch (Exception e) {
            log.error("保存未跟踪模型失败: modelName={}, sourcePath={}", modelName, sourcePath, e);
            return null;
        }
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return "";
        }
        int lastDot = filePath.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filePath.length() - 1) {
            return filePath.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }
}
