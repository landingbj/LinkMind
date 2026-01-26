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
import java.util.ArrayList;
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
        this.mysqlAdapter = MysqlAdapter.getInstance();
    }
    
    /**
     * 保存数据集到数据库
     */
    public Long saveDataset(String name, String path, String description, String userId, 
                           Long fileSize, String fileType) {
        try {
            // 使用Java时间确保时区正确（东八区）
            String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String sql = "INSERT INTO datasets (name, path, description, user_id, file_size, file_type, " +
                        "status, created_at, updated_at, is_deleted) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 'active', ?, ?, 0)";
            
            int result = mysqlAdapter.executeUpdate(sql, name, path, description, userId, 
                                                    fileSize, fileType, currentTime, currentTime);
            
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
     * 保存模型到数据库（基础版本，用于文件上传）
     * 
     * @deprecated introductionId 参数已废弃（model_introduction 表已合并到 models 表），传入 null 即可
     */
    @Deprecated
    public Long saveModel(String name, String path, String version, Long datasetId, 
                         Long introductionId, String modelType, String framework,
                         Long fileSize, String fileType, String description, String userId) {
        // 为了向后兼容，保留此方法签名，但 introductionId 参数已废弃（不再使用）
        // model_introduction 表已合并到 models 表，所有简介字段已直接包含在 models 表中
        return saveModelWithDetails(name, path, version, datasetId, modelType, framework,
                                   fileSize, fileType, description, userId, null, null, null,
                                   null, null, null, null, null, null, null, null, null, null,
                                   null, null, null, null, null, null, null, "active");
    }
    
    /**
     * 保存模型到数据库（完整版本，包含所有简介字段）
     * @param status 模型状态，默认为 'active'。训练后自动入库的模型应设置为 'archived'
     */
    public Long saveModelWithDetails(String name, String path, String version, Long datasetId,
                                     String modelType, String framework, Long fileSize, String fileType,
                                     String description, String userId,
                                     String title, String detailContent, Long categoryId,
                                     Long modelTypeId, Long frameworkId, String algorithm,
                                     String inputShape, String outputShape, Integer totalParams,
                                     Integer trainableParams, Integer nonTrainableParams,
                                     Float accuracy, Float precision, Float recall, Float f1Score,
                                     String tags, Long viewCount, String author, String docLink, String iconLink,
                                     String status) {
        // 如果状态为空，默认为 'active'
        if (status == null || status.trim().isEmpty()) {
            status = "active";
        }
        
        try {
            // 使用Java时间确保时区正确（东八区）
            String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String sql = "INSERT INTO models (name, path, version, dataset_id, " +
                        "model_type, framework, file_size, file_type, description, user_id, " +
                        "title, detail_content, category_id, model_type_id, framework_id, " +
                        "algorithm, input_shape, output_shape, total_params, trainable_params, " +
                        "non_trainable_params, accuracy, `precision`, `recall`, f1_score, " +
                        "tags, view_count, author, doc_link, icon_link, " +
                        "status, created_at, updated_at, is_deleted) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)";
            
            int result = mysqlAdapter.executeUpdate(sql, 
                    name, path, version, datasetId,
                    modelType, framework, fileSize, fileType, description, userId,
                    title, detailContent, categoryId, modelTypeId, frameworkId,
                    algorithm, inputShape, outputShape, totalParams, trainableParams,
                    nonTrainableParams, accuracy, precision, recall, f1Score,
                    tags, viewCount != null ? viewCount : 0, author, docLink, iconLink, status,
                    currentTime, currentTime);
            
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
     * 注意：实际使用的是 dataset_upload 表，不是 datasets 表
     */
    public Map<String, Object> getDatasetById(Long datasetId) {
        try {
            // 使用 dataset_upload 表，字段映射：storage_path -> path
            String sql = "SELECT id, sample_id, name, storage_path AS path, description, uploader AS user_id, " +
                        "file_size, storage_type, original_url, create_time AS upload_time, " +
                        "update_time, is_deleted " +
                        "FROM dataset_upload WHERE id = ? AND (is_deleted = 0 OR is_deleted IS NULL)";
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
     * 查询所有模型列表（基础版本，用于训练页面下拉框）
     */
    public List<Map<String, Object>> listModels(String userId) {
        try {
            String sql = "SELECT id, name, path, version, model_type, framework, " +
                        "file_size, status, description, user_id, created_at " +
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
     * 查询模型列表（完整版本，包含所有简介字段）
     */
    public List<Map<String, Object>> listModelsWithDetails(String userId, String keyword, 
                                                           String status, Long categoryId,
                                                           int page, int pageSize) {
        try {
            StringBuilder sql = new StringBuilder(
                "SELECT m.id, m.name, m.path, m.version, m.title, m.description, m.detail_content, " +
                "m.model_type, m.framework, m.file_size, m.status, m.created_at, " +
                "m.category_id, m.model_type_id, m.framework_id, m.algorithm, " +
                "m.input_shape, m.output_shape, m.total_params, m.trainable_params, " +
                "m.non_trainable_params, m.accuracy, m.`precision`, m.`recall`, m.f1_score, " +
                "m.tags, m.view_count, m.author, m.doc_link, m.icon_link, m.user_id " +
                "FROM models m WHERE m.is_deleted = 0"
            );
            
            List<Object> params = new ArrayList<>();
            
            if (userId != null && !userId.isEmpty()) {
                sql.append(" AND m.user_id = ?");
                params.add(userId);
            }
            
            if (keyword != null && !keyword.isEmpty()) {
                sql.append(" AND (m.name LIKE ? OR m.description LIKE ? OR m.title LIKE ?)");
                String likeValue = "%" + keyword + "%";
                params.add(likeValue);
                params.add(likeValue);
                params.add(likeValue);
            }
            
            if (status != null && !status.isEmpty()) {
                sql.append(" AND m.status = ?");
                params.add(status);
            }
            
            if (categoryId != null) {
                sql.append(" AND m.category_id = ?");
                params.add(categoryId);
            }
            
            sql.append(" ORDER BY m.created_at DESC");
            
            if (page > 0 && pageSize > 0) {
                int offset = (page - 1) * pageSize;
                sql.append(" LIMIT ?, ?");
                params.add(offset);
                params.add(pageSize);
            }
            
            return mysqlAdapter.select(sql.toString(), params.toArray());
        } catch (Exception e) {
            log.error("查询模型列表（完整版）失败", e);
            return null;
        }
    }
    
    /**
     * 查询模型总数（用于分页）
     */
    public long countModels(String userId, String keyword, String status, Long categoryId) {
        try {
            StringBuilder sql = new StringBuilder("SELECT COUNT(*) AS cnt FROM models m WHERE m.is_deleted = 0");
            List<Object> params = new ArrayList<>();
            
            if (userId != null && !userId.isEmpty()) {
                sql.append(" AND m.user_id = ?");
                params.add(userId);
            }
            
            if (keyword != null && !keyword.isEmpty()) {
                sql.append(" AND (m.name LIKE ? OR m.description LIKE ? OR m.title LIKE ?)");
                String likeValue = "%" + keyword + "%";
                params.add(likeValue);
                params.add(likeValue);
                params.add(likeValue);
            }
            
            if (status != null && !status.isEmpty()) {
                sql.append(" AND m.status = ?");
                params.add(status);
            }
            
            if (categoryId != null) {
                sql.append(" AND m.category_id = ?");
                params.add(categoryId);
            }
            
            List<Map<String, Object>> results = mysqlAdapter.select(sql.toString(), params.toArray());
            if (results != null && !results.isEmpty() && results.get(0).get("cnt") instanceof Number) {
                return ((Number) results.get(0).get("cnt")).longValue();
            }
            return 0;
        } catch (Exception e) {
            log.error("查询模型总数失败", e);
            return 0;
        }
    }
    
    /**
     * 查询所有数据集列表
     * 注意：实际使用的是 dataset_upload 表，不是 datasets 表
     */
    public List<Map<String, Object>> listDatasets(String userId) {
        try {
            // 使用 dataset_upload 表，字段映射：storage_path -> path, uploader -> user_id
            String sql = "SELECT id, sample_id, name, storage_path AS path, description, uploader AS user_id, " +
                        "file_size, storage_type, original_url, create_time AS upload_time, " +
                        "update_time AS created_at " +
                        "FROM dataset_upload WHERE (is_deleted = 0 OR is_deleted IS NULL)";
            
            if (userId != null && !userId.isEmpty()) {
                sql += " AND uploader = ?";
                sql += " ORDER BY create_time DESC";
                return mysqlAdapter.select(sql, userId);
            } else {
                sql += " ORDER BY create_time DESC";
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
            
            // 获取训练任务信息
            Map<String, Object> taskInfo = null;
            if (taskId != null && !taskId.isEmpty()) {
                try {
                    String taskSql = "SELECT * FROM ai_training_tasks WHERE task_id = ? AND is_deleted = 0 LIMIT 1";
                    List<Map<String, Object>> taskResults = mysqlAdapter.select(taskSql, taskId);
                    if (taskResults != null && !taskResults.isEmpty()) {
                        taskInfo = taskResults.get(0);
                    }
                } catch (Exception e) {
                    log.warn("获取训练任务信息失败: taskId={}", taskId, e);
                }
            }
            
            // 获取新版本号（使用智能版本号生成，确保唯一性）
            String originalModelName = (String) originalModel.get("name");
            String currentVersion = (String) originalModel.get("version");
            String newVersion = ModelVersionManager.generateNewVersion(originalModelName, currentVersion, taskId);
            
            // 生成新模型名称：提取基础名称，然后替换版本和时间戳
            // 例如：yolo11-1.0.0-202601221038 -> yolo11-1.1.0-202601221043
            // 例如：YOLOv11-1.2.0-202601231024 -> YOLOv11-1.3.0-202601231056
            String baseName = extractBaseModelName(originalModelName);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
            String versionWithoutV = newVersion.startsWith("V") ? newVersion.substring(1) : newVersion;
            String newModelName = baseName + "-" + versionWithoutV + "-" + timestamp;
            
            // 复制模型信息
            String name = newModelName;
            Long datasetId = originalModel.get("dataset_id") != null ? 
                            ((Number) originalModel.get("dataset_id")).longValue() : null;
            String modelType = (String) originalModel.get("model_type");
            String framework = (String) originalModel.get("framework");
            
            // 生成详细的模型描述
            String description = generateTrainingDescription(originalModel, taskInfo, taskId);
            
            String userId = (String) originalModel.get("user_id");
            
            // 复制简介相关字段
            String title = (String) originalModel.get("title");
            String detailContent = (String) originalModel.get("detail_content");
            Long categoryId = originalModel.get("category_id") != null ?
                             ((Number) originalModel.get("category_id")).longValue() : null;
            Long modelTypeId = originalModel.get("model_type_id") != null ?
                              ((Number) originalModel.get("model_type_id")).longValue() : null;
            Long frameworkId = originalModel.get("framework_id") != null ?
                              ((Number) originalModel.get("framework_id")).longValue() : null;
            String algorithm = (String) originalModel.get("algorithm");
            String inputShape = (String) originalModel.get("input_shape");
            String outputShape = (String) originalModel.get("output_shape");
            Integer totalParams = originalModel.get("total_params") != null ?
                                ((Number) originalModel.get("total_params")).intValue() : null;
            Integer trainableParams = originalModel.get("trainable_params") != null ?
                                     ((Number) originalModel.get("trainable_params")).intValue() : null;
            Integer nonTrainableParams = originalModel.get("non_trainable_params") != null ?
                                        ((Number) originalModel.get("non_trainable_params")).intValue() : null;
            Float accuracy = originalModel.get("accuracy") != null ?
                           ((Number) originalModel.get("accuracy")).floatValue() : null;
            Float precision = originalModel.get("precision") != null ?
                            ((Number) originalModel.get("precision")).floatValue() : null;
            Float recall = originalModel.get("recall") != null ?
                         ((Number) originalModel.get("recall")).floatValue() : null;
            Float f1Score = originalModel.get("f1_score") != null ?
                          ((Number) originalModel.get("f1_score")).floatValue() : null;
            String tags = (String) originalModel.get("tags");
            Long viewCount = originalModel.get("view_count") != null ?
                           ((Number) originalModel.get("view_count")).longValue() : 0L;
            String author = (String) originalModel.get("author");
            String docLink = (String) originalModel.get("doc_link");
            String iconLink = (String) originalModel.get("icon_link");
            
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
            
            // 保存新模型（包含所有简介字段）
            // 训练后自动入库的模型状态设置为 'archived'（已归档）
            Long newModelId = saveModelWithDetails(name, newModelPath, newVersion, datasetId,
                                                  modelType, framework, fileSize, fileType,
                                                  description, userId,
                                                  title, detailContent, categoryId,
                                                  modelTypeId, frameworkId, algorithm,
                                                  inputShape, outputShape, totalParams,
                                                  trainableParams, nonTrainableParams,
                                                  accuracy, precision, recall, f1Score,
                                                  tags, viewCount, author, docLink, iconLink,
                                                  "archived");
            
            if (newModelId != null) {
                log.info("训练后模型已自动入库: newModelId={}, originalModelName={}, newModelName={}, version={}, path={}", 
                        newModelId, originalModelName, newModelName, newVersion, newModelPath);
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
    
    /**
     * 生成训练后的模型描述信息
     * 包含：原模型信息、数据集信息、训练时间等
     * 使用HTML格式，便于页面友好显示
     */
    private String generateTrainingDescription(Map<String, Object> originalModel, 
                                                 Map<String, Object> taskInfo, 
                                                 String taskId) {
        StringBuilder desc = new StringBuilder();
        
        // 使用HTML格式，便于页面显示
        desc.append("<div style='line-height: 1.8;'>");
        desc.append("<p style='margin: 0 0 8px 0; color: #666;'>训练后自动生成</p>");
        
        // 原模型信息
        if (originalModel != null) {
            String originalModelName = (String) originalModel.get("name");
            Object originalModelIdObj = originalModel.get("id");
            String originalModelId = originalModelIdObj != null ? originalModelIdObj.toString() : "未知";
            String originalVersion = (String) originalModel.get("version");
            
            desc.append("<p style='margin: 0 0 8px 0;'>");
            desc.append("<strong>基于模型：</strong>");
            if (originalModelName != null && !originalModelName.isEmpty()) {
                desc.append(originalModelName);
            } else {
                desc.append("未知模型");
            }
            desc.append(" <span style='color: #999;'>(ID: ").append(originalModelId);
            if (originalVersion != null && !originalVersion.isEmpty()) {
                desc.append(", 版本: ").append(originalVersion);
            }
            desc.append(")</span>");
            desc.append("</p>");
        } else {
            desc.append("<p style='margin: 0 0 8px 0;'><strong>基于模型：</strong>未知模型</p>");
        }
        
        // 数据集信息
        if (taskInfo != null) {
            Object datasetIdObj = taskInfo.get("dataset_id");
            String datasetName = (String) taskInfo.get("dataset_name");
            
            if (datasetIdObj != null || (datasetName != null && !datasetName.isEmpty())) {
                desc.append("<p style='margin: 0 0 8px 0;'>");
                desc.append("<strong>训练数据集：</strong>");
                if (datasetName != null && !datasetName.isEmpty()) {
                    desc.append(datasetName);
                } else {
                    // 如果任务中没有数据集名称，尝试从数据集ID查询
                    if (datasetIdObj != null) {
                        try {
                            Long datasetId = ((Number) datasetIdObj).longValue();
                            Map<String, Object> datasetInfo = getDatasetById(datasetId);
                            if (datasetInfo != null) {
                                String dsName = (String) datasetInfo.get("name");
                                if (dsName != null && !dsName.isEmpty()) {
                                    desc.append(dsName);
                                } else {
                                    desc.append("数据集ID: ").append(datasetId);
                                }
                            } else {
                                desc.append("数据集ID: ").append(datasetId);
                            }
                        } catch (Exception e) {
                            desc.append("数据集ID: ").append(datasetIdObj);
                        }
                    } else {
                        desc.append("未知数据集");
                    }
                }
                
                if (datasetIdObj != null) {
                    desc.append(" <span style='color: #999;'>(ID: ").append(datasetIdObj).append(")</span>");
                }
                desc.append("</p>");
            }
        }
        
        // 训练时间信息
        if (taskInfo != null) {
            String startTime = convertToString(taskInfo.get("start_time"));
            String endTime = convertToString(taskInfo.get("end_time"));
            String createdAt = convertToString(taskInfo.get("created_at"));
            
            if (startTime != null && !startTime.isEmpty() || endTime != null && !endTime.isEmpty()) {
                desc.append("<p style='margin: 0 0 8px 0;'>");
                desc.append("<strong>训练时间：</strong>");
                if (startTime != null && !startTime.isEmpty()) {
                    desc.append(startTime);
                }
                if (endTime != null && !endTime.isEmpty()) {
                    if (startTime != null && !startTime.isEmpty()) {
                        desc.append(" ~ ");
                    }
                    desc.append(endTime);
                } else if (createdAt != null && !createdAt.isEmpty()) {
                    if (startTime != null && !startTime.isEmpty()) {
                        desc.append(" ~ ");
                    }
                    desc.append(createdAt);
                }
                desc.append("</p>");
            }
        }
        
        // 训练参数信息
        if (taskInfo != null) {
            Object epochs = taskInfo.get("epochs");
            Object batchSize = taskInfo.get("batch_size");
            Object imageSize = taskInfo.get("image_size");
            Object learningRate = taskInfo.get("learning_rate");
            
            boolean hasParams = false;
            StringBuilder paramsDesc = new StringBuilder();
            
            if (epochs != null) {
                paramsDesc.append("训练轮次: ").append(epochs);
                hasParams = true;
            }
            if (batchSize != null) {
                if (hasParams) paramsDesc.append(" | ");
                paramsDesc.append("批次大小: ").append(batchSize);
                hasParams = true;
            }
            if (imageSize != null) {
                if (hasParams) paramsDesc.append(" | ");
                paramsDesc.append("图片尺寸: ").append(imageSize);
                hasParams = true;
            }
            if (learningRate != null) {
                if (hasParams) paramsDesc.append(" | ");
                paramsDesc.append("学习率: ").append(learningRate);
                hasParams = true;
            }
            
            if (hasParams) {
                desc.append("<p style='margin: 0 0 8px 0;'>");
                desc.append("<strong>训练参数：</strong>").append(paramsDesc);
                desc.append("</p>");
            }
        }
        
        // 训练任务ID
        if (taskId != null && !taskId.isEmpty()) {
            desc.append("<p style='margin: 0; color: #999; font-size: 12px;'>");
            desc.append("任务ID: ").append(taskId);
            desc.append("</p>");
        }
        
        desc.append("</div>");
        return desc.toString();
    }
    
    /**
     * 从模型名称中提取基础名称（去掉版本号和时间戳部分）
     * 例如：
     * - "yolo11-1.0.0-202601221038" -> "yolo11"
     * - "YOLOv11-1.2.0-202601231024" -> "YOLOv11"
     * - "yolo11" -> "yolo11" (如果没有版本和时间戳，返回原名称)
     */
    private String extractBaseModelName(String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            return "model";
        }
        
        // 匹配模式：{baseName}-{version}-{timestamp}
        // 版本号格式：数字.数字.数字 (如 1.0.0, 1.2.0)
        // 时间戳格式：12位数字 (如 202601221038)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^(.+?)-\\d+\\.\\d+\\.\\d+-\\d{12}$");
        java.util.regex.Matcher matcher = pattern.matcher(modelName);
        
        if (matcher.matches()) {
            // 如果匹配到模式，提取基础名称
            return matcher.group(1);
        }
        
        // 如果没有匹配到模式，尝试匹配只有版本号的情况：{baseName}-{version}
        pattern = java.util.regex.Pattern.compile("^(.+?)-\\d+\\.\\d+\\.\\d+$");
        matcher = pattern.matcher(modelName);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        
        // 如果都不匹配，返回原名称
        return modelName;
    }
    
    /**
     * 将对象转换为字符串，支持 LocalDateTime、Timestamp 等类型
     */
    private String convertToString(Object obj) {
        if (obj == null) {
            return null;
        }
        
        if (obj instanceof String) {
            return (String) obj;
        }
        
        if (obj instanceof java.time.LocalDateTime) {
            return ((java.time.LocalDateTime) obj).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        
        if (obj instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) obj).toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        
        if (obj instanceof java.util.Date) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return sdf.format((java.util.Date) obj);
        }
        
        // 其他类型直接转换为字符串
        return obj.toString();
    }
}
