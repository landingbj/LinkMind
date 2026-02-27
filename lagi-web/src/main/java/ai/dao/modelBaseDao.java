package ai.dao;

import ai.database.impl.MysqlAdapter;
import ai.finetune.repository.TrainingTaskRepository;
import ai.finetune.utils.ModelDatasetManager;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
public class modelBaseDao {

    private final MysqlAdapter mysqlAdapter;
    private final TrainingTaskRepository trainingTaskRepository;
    private final ModelDatasetManager modelDatasetManager;
    private static final DateTimeFormatter DATE_TIME_FORMATTER =  DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public modelBaseDao() {
        this.mysqlAdapter = MysqlAdapter.getInstance();
        this.trainingTaskRepository = new TrainingTaskRepository(mysqlAdapter);
        this.modelDatasetManager = new ModelDatasetManager();
    }

    /**
     * 根据任务ID获取训练任务详情及相关模板信息
     */
    public Map<String, Object> getTaskDetailByTaskId(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            throw new IllegalArgumentException("task_id不能为空");
        }

        Map<String, Object> taskDetail = trainingTaskRepository.getTaskDetailByTaskId(taskId);
        if (taskDetail == null) {
            return null;
        }

        Map<String, Object> result = new HashMap<>();
        Integer tempId = (Integer) taskDetail.get("template_id");

        // 构建返回数据
        if (tempId != null && tempId > 0) {
            Map<String, Object> templateInfo = trainingTaskRepository.getTemplateInfoById(tempId);
            if (templateInfo != null) {
                //这里的templateId是template_info表中的template_id
                String templateId = (String) templateInfo.get("template_id");
                List<Map<String, Object>> templateFields = trainingTaskRepository.getTemplateFieldsByTemplateId(templateId);
                templateInfo.put("fields", templateFields);
            }
            result.put("template", templateInfo);
        } else {
            log.warn("模板信息中 template_id 为空，taskId={}, tempId={}", taskId, tempId);
        }

        result.put("task", taskDetail);
        return result;
    }

    /**
     * 查询模型框架字典
     */
    public List<Map<String, Object>> queryFramework() {
        List<Map<String, Object>> options = new ArrayList<>();
        String sql = "SELECT id, framework_name AS name FROM model_framework_dict";
        List<Map<String, Object>> frameworkList = mysqlAdapter.select(sql);

        for (Map<String, Object> map : frameworkList) {
            Map<String, Object> option = new HashMap<>();
            option.put("id", map.get("id"));
            option.put("name", map.get("name"));
            options.add(option);
        }
        return options;
    }

    /**
     * 查询模型列表及详情
     */
    public Map<String, Object> listModelsWithDetails(Integer page, Integer pageSize,
                                                     String keyword, String status, Long categoryId) {
        try {
            // 设置默认值
            if (page == null || page <= 0) page = 1;
            if (pageSize == null || pageSize <= 0) pageSize = 10;
            if (keyword != null && keyword.trim().isEmpty()) keyword = null;
            if (status != null && status.trim().isEmpty()) status = null;

            // 查询总数
            long total = modelDatasetManager.countModels(null, keyword, status, categoryId);

            // 查询状态统计
            StringBuilder statusCountWhere = new StringBuilder(" WHERE m.is_deleted = 0 ");
            List<Object> statusParams = new ArrayList<>();
            if (keyword != null && !keyword.isEmpty()) {
                statusCountWhere.append(" AND (m.name LIKE ? OR m.description LIKE ? OR m.title LIKE ?)");
                String likeValue = "%" + keyword + "%";
                statusParams.add(likeValue);
                statusParams.add(likeValue);
                statusParams.add(likeValue);
            }
            if (status != null && !status.isEmpty()) {
                statusCountWhere.append(" AND m.status = ?");
                statusParams.add(status);
            }
            if (categoryId != null) {
                statusCountWhere.append(" AND m.category_id = ?");
                statusParams.add(categoryId);
            }

            String statusCountSql = "SELECT m.status AS status, COUNT(*) AS cnt FROM models m" +
                    statusCountWhere + " GROUP BY m.status";
            List<Map<String, Object>> statusCountResult = mysqlAdapter.select(statusCountSql, statusParams.toArray());
            Map<String, Object> statusCount = new HashMap<>();
            for (Map<String, Object> row : statusCountResult) {
                Object statusKey = row.get("status");
                Object cntValue = row.get("cnt");
                if (statusKey != null && cntValue instanceof Number) {
                    statusCount.put(statusKey.toString(), ((Number) cntValue).longValue());
                }
            }

            // 查询列表
            List<Map<String, Object>> rows = modelDatasetManager.listModelsWithDetails(null, keyword, status, categoryId, page, pageSize);
            List<Map<String, Object>> list = new ArrayList<>();

            // 查询分类名称
            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", row.get("id"));
                item.put("modelName", row.get("name"));
                item.put("version", row.get("version"));

                // 查询分类名称
                if (row.get("category_id") != null) {
                    Long catId = ((Number) row.get("category_id")).longValue();
                    String categorySql = "SELECT category_name FROM model_category WHERE id = ?";
                    List<Map<String, Object>> catResult = mysqlAdapter.select(categorySql, catId);
                    if (catResult != null && !catResult.isEmpty()) {
                        item.put("category", catResult.get(0).get("category_name"));
                    }
                }

                item.put("title", row.get("title"));
                item.put("author", row.get("author"));
                item.put("status", row.get("status"));
                item.put("viewCount", row.get("view_count"));
                item.put("createTime", row.get("created_at"));
                list.add(item);
            }

            // 构建返回结果
            Map<String, Object> data = new HashMap<>();
            data.put("total", total);
            data.put("statusCount", statusCount);
            data.put("list", list);
            return data;
        } catch (Exception e) {
            log.error("查询模型列表失败", e);
            throw new RuntimeException("查询模型列表失败");
        }
    }

    /**
     * 查询模型分类
     */
    public List<Map<String, Object>> queryModelCategory() {
        List<Map<String, Object>> options = new ArrayList<>();
        String sql = "SELECT id, category_name AS name FROM model_category";
        List<Map<String, Object>> categoryList = mysqlAdapter.select(sql);

        for (Map<String, Object> map : categoryList) {
            Map<String, Object> option = new HashMap<>();
            option.put("id", map.get("id"));
            option.put("name", map.get("name"));
            options.add(option);
        }

        return options;
    }

    /**
     * 查询模型类型
     */
    public List<Map<String, Object>> queryModelType() {
        List<Map<String, Object>> options = new ArrayList<>();
        String sql = "SELECT id, type_name AS name FROM model_type";
        List<Map<String, Object>> typeList = mysqlAdapter.select(sql);

        for (Map<String, Object> map : typeList) {
            Map<String, Object> option = new HashMap<>();
            option.put("id", map.get("id"));
            option.put("name", map.get("name"));
            options.add(option);
        }
        return options;
    }

    /**
     * 根据ID获取模型详细信息
     */
    public Map<String, Object> getModelDetail(Long modelId) {
        // 参数校验
        if (modelId == null) {
            throw new IllegalArgumentException("模型ID不能为空");
        }

        try {
            // 查询模型详情
            Map<String, Object> modelDetail = modelDatasetManager.getModelById(modelId);

            // 检查模型是否存在
            if (modelDetail == null) {
                return null;
            }

            // 查询并填充分类名称
            if (modelDetail.containsKey("category_id") && modelDetail.get("category_id") != null) {
                Long categoryId = ((Number) modelDetail.get("category_id")).longValue();
                String categorySql = "SELECT category_name FROM model_category WHERE id = ?";
                List<Map<String, Object>> categoryResult = mysqlAdapter.select(categorySql, categoryId);
                if (categoryResult != null && !categoryResult.isEmpty()) {
                    modelDetail.put("category_name", categoryResult.get(0).get("category_name"));
                }
            }

            // 查询并填充框架名称
            if (modelDetail.containsKey("framework_id") && modelDetail.get("framework_id") != null) {
                Long frameworkId = ((Number) modelDetail.get("framework_id")).longValue();
                String frameworkSql = "SELECT framework_name FROM model_framework_dict WHERE id = ?";
                List<Map<String, Object>> frameworkResult = mysqlAdapter.select(frameworkSql, frameworkId);
                if (frameworkResult != null && !frameworkResult.isEmpty()) {
                    modelDetail.put("framework_name", frameworkResult.get(0).get("framework_name"));
                }
            }

            // 查询并填充模型类型名称
            if (modelDetail.containsKey("model_type_id") && modelDetail.get("model_type_id") != null) {
                Long modelTypeId = ((Number) modelDetail.get("model_type_id")).longValue();
                String modelTypeSql = "SELECT type_name FROM model_type_dict WHERE id = ?";
                List<Map<String, Object>> modelTypeResult = mysqlAdapter.select(modelTypeSql, modelTypeId);
                if (modelTypeResult != null && !modelTypeResult.isEmpty()) {
                    modelDetail.put("type_name", modelTypeResult.get(0).get("type_name"));
                }
            }

            return modelDetail;
        } catch (Exception e) {
            log.error("查询模型详情失败", e);
            throw new RuntimeException("查询模型详情失败: " + e.getMessage());
        }
    }

    /**
     * 软删除模型
     */
    public boolean deleteModel(Long modelId) {
        if (modelId == null) {
            throw new IllegalArgumentException("模型ID不能为空");
        }

        try {
            // 检查模型是否存在
            Map<String, Object> existingModel = modelDatasetManager.getModelById(modelId);
            if (existingModel == null) {
                return false;
            }

            // 执行软删除
            String currentTime = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String sql = "UPDATE models SET is_deleted = 1, updated_at = ? WHERE id = ? AND is_deleted = 0";
            int rowsAffected = mysqlAdapter.executeUpdate(sql, currentTime, modelId);

            return rowsAffected > 0;
        } catch (Exception e) {
            log.error("删除模型失败", e);
            throw new RuntimeException("服务器内部错误: " + e.getMessage());
        }
    }

    /**
     * 更新模型信息
     */
    public boolean updateModel(Long modelId, JSONObject model) {
        // 验证必填参数 id
        if (modelId == null) {
            throw new IllegalArgumentException("模型ID不能为空");
        }

        try {
            // 检查模型是否存在
            Map<String, Object> existingModel = modelDatasetManager.getModelById(modelId);
            if (existingModel == null) {
                return false;
            }

            // 构建更新SQL，只更新提供的字段
            StringBuilder sql = new StringBuilder("UPDATE models SET ");
            List<Object> params = new ArrayList<>();
            List<String> setParts = new ArrayList<>();

            // 定义字段映射关系
            Map<String, String> fieldMapping = new HashMap<String, String>() {{
                put("modelName", "name");
                put("version", "version");
                put("path", "path");
                put("description", "description");
                put("title", "title");
                put("detailContent", "detail_content");
                put("categoryId", "category_id");
                put("modelTypeId", "model_type_id");
                put("frameworkId", "framework_id");
                put("modelType", "model_type");
                put("framework", "framework");
                put("algorithm", "algorithm");
                put("inputShape", "input_shape");
                put("outputShape", "output_shape");
                put("totalParams", "total_params");
                put("trainableParams", "trainable_params");
                put("nonTrainableParams", "non_trainable_params");
                put("accuracy", "accuracy");
                put("precision", "`precision`");
                put("recall", "`recall`");
                put("f1Score", "f1_score");
                put("tags", "tags");
                put("status", "status");
                put("author", "author");
                put("docLink", "doc_link");
                put("iconLink", "icon_link");
            }};

            // 遍历字段映射，动态构建SQL
            for (Map.Entry<String, String> entry : fieldMapping.entrySet()) {
                String key = entry.getKey();
                String column = entry.getValue();
                if (model.containsKey(key) && model.get(key) != null) {
                    setParts.add(column + " = ?");
                    params.add(model.get(key));
                }
            }

            if (setParts.isEmpty()) {
                throw new IllegalArgumentException("至少需要提供一个要更新的字段");
            }

            // 添加更新时间和WHERE条件
            String currentTime = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            setParts.add("updated_at = ?");
            params.add(currentTime);
            sql.append(String.join(", ", setParts));
            sql.append(" WHERE id = ? AND is_deleted = 0");
            params.add(modelId);

            // 执行更新
            int rowsAffected = mysqlAdapter.executeUpdate(sql.toString(), params.toArray());

            return rowsAffected > 0;
        } catch (Exception e) {
            log.error("更新模型失败", e);
            throw new RuntimeException("服务器内部错误: " + e.getMessage());
        }
    }

    /**
     * 创建新模型
     */
    public Long createModel(JSONObject model) {
        // 验证必填参数
        if (!model.containsKey("modelName") || model.getStr("modelName") == null ||
            model.getStr("modelName").trim().isEmpty()) {
            throw new IllegalArgumentException("模型名称不能为空");
        }

        if (!model.containsKey("version") || model.getStr("version") == null ||
            model.getStr("version").trim().isEmpty()) {
            throw new IllegalArgumentException("版本号不能为空");
        }

        // 构建模型数据
        String modelName = model.getStr("modelName");
        String version = model.getStr("version");
        String path = model.getStr("path"); // 可选，如果没有则使用占位符
        if (path == null || path.isEmpty()) {
            path = "/placeholder/model_" + System.currentTimeMillis() + ".pt";
        }

        // 获取状态，如果未提供则默认为 'active'（用户手动创建的模型默认为活跃状态）
        String status = model.getStr("status");
        if (status == null || status.trim().isEmpty()) {
            status = "active";
        }

        Long modelId = modelDatasetManager.saveModelWithDetails(
            modelName, path, version,
            model.getLong("dataset_id", null),
            model.getStr("model_type"), model.getStr("framework"),
            model.getLong("file_size", null),
            model.getStr("file_type"),
            model.getStr("description"),
            model.getStr("user_id"),
            model.getStr("title"),
            model.getStr("detailContent"),
            model.getLong("category_id", null),
            model.getLong("model_type_id", null),
            model.getLong("framework_id", null),
            model.getStr("algorithm"),
            model.getStr("inputShape"),
            model.getStr("outputShape"),
            model.getInt("total_params", null),
            model.getInt("trainable_params", null),
            model.getInt("non_trainable_params", null),
            model.getFloat("accuracy", null),
            model.getFloat("precision", null),
            model.getFloat("recall", null),
            model.getFloat("f1_score", null),
            model.getStr("tags"),
            model.getLong("view_count", 0L),
            model.getStr("author"),
            model.getStr("doc_link"),
            model.getStr("icon_link"),
            status  // 第31个参数：模型状态
        );

        return modelId;
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

            int result = mysqlAdapter.executeUpdate(sql, status, message, LocalDateTime.now().format(DATE_TIME_FORMATTER), taskId);
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
}
