package ai.servlet.api;

import ai.common.exception.RRException;
import ai.config.ContextLoader;
import ai.database.impl.MysqlAdapter;
import ai.finetune.repository.TrainingTaskRepository;
import ai.finetune.service.TrainerService;
import ai.finetune.utils.ModelDatasetManager;
import ai.servlet.RestfulServlet;
import ai.servlet.annotation.Body;
import ai.servlet.annotation.Get;
import ai.servlet.annotation.Param;
import ai.servlet.annotation.Post;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import lombok.extern.slf4j.Slf4j;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * AI 模型训练任务管理 Servlet（通用版）
 * 支持任意 AI 模型的训练、评估、预测和导出
 * 包括但不限于：YOLOv8, YOLOv11, CenterNet, CRNN, HRNet, PIDNet, ResNet, OSNet等
 * 提供训练任务的完整生命周期管理和流式输出
 * 扩展性：
 * - 通过 trainerMap 注册新模型的 Trainer
 * - 支持动态模型类别和框架推断
 * - 无法推断的模型自动归为 "custom" 类别
 */
@Slf4j
public class AITrainingServlet1 extends RestfulServlet {

    private final TrainerService trainerService = ContextLoader.getBean(TrainerService.class);

    @Post("start")
    public String start(@Body JSONObject config) {
        config.set("the_train_type", "train");
        if (trainerService != null) {
            trainerService.startTrainingTask(config);
            return "训练任务已提交";
        }
        throw new RRException("为找到对应的训练服务");
    }

    @Post("pause")
    public String pause(@Body String taskId) {
        if (trainerService != null) {
            return trainerService.pauseTask(taskId);
        }
        throw new RRException("为找到对应的训练服务");
    }

    @Post("resume")
    public String resume(@Param("taskId") String taskId, @Param("containerId") String containerId) {
        if (trainerService != null) {
            trainerService.resumeTask(StrUtil.isBlank(taskId)?containerId:taskId);
            return "任务已恢复";
        }
        throw new RRException("为找到对应的训练服务");
    }


    @Post("stop")
    public String stop(@Param("taskId") String taskId, @Param("containerId") String containerId) {
        if (trainerService != null) {
            return trainerService.stopTask(StrUtil.isBlank(taskId)?containerId:taskId);
        }
        throw new RRException("为找到对应的训练服务");
    }

    @Post("remove")
    public String remove(@Param("taskId") String taskId, @Param("containerId") String containerId) {
        if (trainerService != null) {
            return trainerService.removeTask(StrUtil.isBlank(taskId)?containerId:taskId);
        }
        throw new RRException("为找到对应的训练服务");
    }

    @Post("deleted")
    public String deleted(@Param("taskId") String taskId, @Param("containerId") String containerId) {
        if (trainerService != null) {
            return trainerService.removeTask(StrUtil.isBlank(taskId)?containerId:taskId);
        }
        throw new RRException("为找到对应的训练服务");
    }

    @Get("status")
    public String status(@Param("taskId") String taskId, @Param("containerId") String containerId) {
        if (trainerService != null) {
            return trainerService.getTaskStatus(StrUtil.isBlank(taskId)?containerId:taskId);
        }
        throw new RRException("为找到对应的训练服务");
    }

    @Get("logs")
    public String getLogs(@Param("taskId") String taskId, @Param("containerId") String containerId, @Param("lines") Integer lastLines) {
        if (trainerService != null) {
            return trainerService.getTaskLogs(StrUtil.isBlank(taskId)?containerId:taskId, lastLines);
        }
        throw new RRException("为找到对应的训练服务");
    }


    @Post("evaluate")
    public String evaluate(@Body JSONObject config) {
        config.set("the_train_type", "valuate");
        if (trainerService != null) {
            trainerService.startEvaluationTask(config);
            return "评估任务已提交";
        }
        throw new RRException("为找到对应的训练服务");
    }

    @Post("predict")
    public String predict(@Body JSONObject config) {
        config.set("the_train_type", "predict");
        if (trainerService != null) {
            trainerService.startPredictionTask(config);
            return "推理任务已提交";
        }
        throw new RRException("为找到对应的训练服务");
    }

    @Post("export")
    public String export(@Body JSONObject config) {
        config.set("the_train_type", "export");
        if (trainerService != null) {
            try {
                Future<String> stringFuture = trainerService.startConvertTask(config);
                return stringFuture.get();
            } catch (Exception e) {
                throw new RRException(e.getMessage());
            }
        }
        throw new RRException("为找到对应的训练服务");
    }


    @Get("list")
    public String list() {
        if (trainerService != null) {
            try {
                return trainerService.getRunningTaskInfo();
            } catch (Exception e) {
                throw new RRException(e.getMessage());
            }
        }
        throw new RRException("为找到对应的训练服务");
    }

    @Get("resources")
    public JSONObject resources(@Param("taskId") String taskId, @Param("containerId") String containerId) {
        if (trainerService != null) {
            try {
                return trainerService.getResourceInfo(StrUtil.isBlank(taskId)?containerId:taskId);
            } catch (Exception e) {
                throw new RRException(e.getMessage());
            }
        }
        throw new RRException("为找到对应的服务");
    }
    @Post("detail")
    public Map<String, Object> detail(@Body JSONObject object) {
        String taskId = object.get("task_id") != null ? object.get("task_id").toString() : null;
        if (StrUtil.isBlank(taskId)) {
            throw new RRException("task_id不能为空");
        }
        TrainingTaskRepository repository = new TrainingTaskRepository(MysqlAdapter.getInstance());
        Map<String, Object> taskDetail = repository.getTaskDetailByTaskId(taskId);
        if (taskDetail == null) {
            throw new RRException("未找到对应的训练任务");
        }
        Map<String, Object> result = new HashMap<>();
        Integer tempId = (Integer) taskDetail.get("template_id");
        // 构建返回数据
        if (tempId != null && tempId > 0) {
            Map<String, Object> templateInfo = repository.getTemplateInfoById(tempId);
            if (templateInfo != null) {
                //这里的templateId是template_info表中的template_id
                String templateId = (String) templateInfo.get("template_id");
                    List<Map<String, Object>> templateFields = repository.getTemplateFieldsByTemplateId(templateId);
                    templateInfo.put("fields", templateFields);
            }
            result.put("template",  templateInfo);
        } else {
            log.warn("模板信息中 template_id 为空，taskId={}, tempId={}", taskId, tempId);
        }
        result.put("task", taskDetail);
        return result;
    }
    @Get("queryFramework")
    public List<Map<String, Object>> queryFramework() {
            List<Map<String, Object>> options = new ArrayList<>();
            ai.database.impl.MysqlAdapter mysqlAdapter = MysqlAdapter.getInstance();
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
    @Get("listModelsWithDetails")
    public Map<String, Object> listModelsWithDetails(
            @Param("page") Integer page,
            @Param("page_size") Integer pageSize,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("category_id") Long categoryId) {
        try {
            // 设置默认值
            if (page == null || page <= 0) page = 1;
            if (pageSize == null || pageSize <= 0) pageSize = 10;
            if (keyword != null && keyword.trim().isEmpty()) keyword = null;
            if (status != null && status.trim().isEmpty()) status = null;

            // 查询数据
            ai.finetune.utils.ModelDatasetManager manager = new ai.finetune.utils.ModelDatasetManager();
            ai.database.impl.MysqlAdapter mysqlAdapter = MysqlAdapter.getInstance();

            // 查询总数
            long total = manager.countModels(null, keyword, status, categoryId);

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
            List<Map<String, Object>> rows = manager.listModelsWithDetails(null, keyword, status, categoryId, page, pageSize);
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
            throw new RRException("查询模型列表失败");
        }
    }
    @Get("queryModelCategory")
    public List<Map<String, Object>> queryModelCategory() {
        List<Map<String, Object>> options = new ArrayList<>();
        ai.database.impl.MysqlAdapter mysqlAdapter = MysqlAdapter.getInstance();
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
    @Get("queryModelType")
    public List<Map<String, Object>> queryModelType() {
        List<Map<String, Object>> options = new ArrayList<>();
        ai.database.impl.MysqlAdapter mysqlAdapter = MysqlAdapter.getInstance();
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

    @Get("getModelDetail")
    public Map<String, Object> getModelDetail(@Param("id") Long modelId) {
        // 参数校验
        if (modelId == null) {
            throw new RRException("模型ID不能为空");
        }

        try {
            // 查询模型详情
            ModelDatasetManager manager = new ModelDatasetManager();
            Map<String, Object> modelDetail = manager.getModelById(modelId);

            // 检查模型是否存在
            if (modelDetail == null) {
                throw new RRException("模型不存在");
            }

            // 获取数据库连接实例
            MysqlAdapter mysqlAdapter = MysqlAdapter.getInstance();

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
            throw new RRException("查询模型详情失败: " + e.getMessage());
        }
    }
    @Post("deleteModel")
    public String deleteModel(@Param("id") Long modelId) {
        if (modelId == null) {
            throw new RRException("模型ID不能为空");
        }

        try {
            // 检查模型是否存在
            ModelDatasetManager manager = new ModelDatasetManager();
            Map<String, Object> existingModel = manager.getModelById(modelId);
            if (existingModel == null) {
                throw new RRException("模型不存在");
            }

            // 执行软删除
            String currentTime = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            MysqlAdapter mysqlAdapter = MysqlAdapter.getInstance();
            String sql = "UPDATE models SET is_deleted = 1, updated_at = ? WHERE id = ? AND is_deleted = 0";
            int rowsAffected = mysqlAdapter.executeUpdate(sql, currentTime, modelId);

            if (rowsAffected > 0) {
                return "模型删除成功";
            } else {
                throw new RRException("删除失败，记录可能不存在或已被删除");
            }
        } catch (Exception e) {
            log.error("删除模型失败", e);
            throw new RRException("服务器内部错误: " + e.getMessage());
        }
    }
    @Post("updateModel")
    public String updateModel(@Param("id") Long modelId, @Body("model") JSONObject model) {
        // 验证必填参数 id
        if (modelId == null) {
            throw new RRException("模型ID不能为空");
        }

        try {
            // 检查模型是否存在
            ModelDatasetManager manager = new ModelDatasetManager();
            Map<String, Object> existingModel = manager.getModelById(modelId);
            if (existingModel == null) {
                throw new RRException("模型不存在");
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
                throw new RRException("至少需要提供一个要更新的字段");
            }

            // 添加更新时间和WHERE条件
            String currentTime = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            setParts.add("updated_at = ?");
            params.add(currentTime);
            sql.append(String.join(", ", setParts));
            sql.append(" WHERE id = ? AND is_deleted = 0");
            params.add(modelId);

            // 执行更新
            MysqlAdapter mysqlAdapter = MysqlAdapter.getInstance();
            int rowsAffected = mysqlAdapter.executeUpdate(sql.toString(), params.toArray());

            if (rowsAffected > 0) {
                return "模型更新成功";
            } else {
                throw new RRException("模型更新失败，记录可能不存在或已被删除");
            }
        } catch (Exception e) {
            log.error("更新模型失败", e);
            throw new RRException("服务器内部错误: " + e.getMessage());
        }
    }
    @Post("createModel")
    public String createModel(@Body("model") JSONObject model) {
        // 验证必填参数
        if (!model.containsKey("modelName") || model.getStr("modelName") == null ||
            model.getStr("modelName").trim().isEmpty()) {
            throw new RRException("模型名称不能为空");
        }

        if (!model.containsKey("version") || model.getStr("version") == null ||
            model.getStr("version").trim().isEmpty()) {
            throw new RRException("版本号不能为空");
        }

        // 构建模型数据
        String modelName = model.getStr("modelName");
        String version = model.getStr("version");
        String path = model.getStr("path"); // 可选，如果没有则使用占位符
        if (path == null || path.isEmpty()) {
            path = "/placeholder/model_" + System.currentTimeMillis() + ".pt";
        }

        // 调用 ModelDatasetManager 保存
        ModelDatasetManager manager = new ModelDatasetManager();

        // 获取状态，如果未提供则默认为 'active'（用户手动创建的模型默认为活跃状态）
        String status = model.getStr("status");
        if (status == null || status.trim().isEmpty()) {
            status = "active";
        }

        Long modelId = manager.saveModelWithDetails(
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

        if (modelId != null) {
            return "模型创建成功";
        } else {
            throw new RRException("模型创建失败");
        }
    }
}
