package ai.servlet.api;

import ai.database.impl.MysqlAdapter;
import ai.dto.TemplateInfoDto;
import ai.servlet.BaseServlet;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import lombok.var;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Slf4j
public class CustomTrainingTasksServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();
    private String currentTime;

    private static volatile MysqlAdapter mysqlAdapter = null;
    private static MysqlAdapter getMysqlAdapter() {
        if (mysqlAdapter == null) {
            synchronized (CustomTrainingTasksServlet.class) {
                if (mysqlAdapter == null) {
                    mysqlAdapter = new MysqlAdapter("mysql");
                }
            }
        }
        return mysqlAdapter;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);
        if (method.equals("saveTemplate")) {
            saveTemplate(req, resp);
        }else if (method.equals("queryTemplate")) {
            queryTemplate(req, resp);
        }else if (method.equals("TemplateList")) {
            templateList(req, resp);
        }else if (method.equals("deletedTemplate")){
            deletedTemplate(req, resp);
        }else {
            resp.setStatus(404);
            Map<String, String> error = new HashMap<>();
            error.put("error", "接口不存在");
            responsePrint(resp, toJson(error));
        }
    }



    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doGet(req, resp);
    }


private void saveTemplate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();

        try {
            String jsonBody = requestToJson(req);
            JsonObject jsonNode = gson.fromJson(jsonBody, JsonObject.class);


            // 1. 解析模板主信息
            if (!jsonNode.has("template")) {
                resp.setStatus(400);
                result.put("error", "缺少必填参数：template");
                responsePrint(resp, toJson(result));
                return;
            }

            JsonObject templateObj = jsonNode.getAsJsonObject("template");
            TemplateInfoDto templateInfoDto = new TemplateInfoDto();

            // 获取传入的templateId（可能为null或空字符串）
            JsonElement templateIdElement = templateObj.get("template_id");
            String templateId = templateIdElement != null && !templateIdElement.isJsonNull() ? templateIdElement.getAsString() : null;
            
            if (templateId == null || templateId.isEmpty()){
                //新增
                // 生成默认UUID（如果未传入templateId）
                templateId = java.util.UUID.randomUUID().toString();
                
                templateInfoDto.setTemplateId(templateId);
                templateInfoDto.setName(templateObj.get("name").getAsString());
                templateInfoDto.setDescription(templateObj.get("description").getAsString());
                templateInfoDto.setType(templateObj.get("type").getAsString());
                templateInfoDto.setCategory(templateObj.get("category").getAsString());
                templateInfoDto.setDifficulty(templateObj.get("difficulty").getAsInt());
                templateInfoDto.setEstimatedTime(templateObj.get("estimatedTime").getAsString());
                List<String> templateTags = new ArrayList<>();
                for (int i = 0; i < templateObj.get("tags").getAsJsonArray().size(); i++) {
                    templateTags.add(templateObj.get("tags").getAsJsonArray().get(i).getAsString());
                }
                templateInfoDto.setTags(templateTags);
                templateInfoDto.setIsBuiltIn(templateObj.get("isBuiltIn").getAsBoolean());
                templateInfoDto.setUsageCount(templateObj.get("usageCount").getAsInt());
                templateInfoDto.setRating(templateObj.get("rating").getAsFloat());
                templateInfoDto.setIsPublic(templateObj.get("isPublic").getAsBoolean());
                String tagsStr = templateInfoDto.getTags() == null ? "" : templateInfoDto.getTags().toString();
                //保存到数据库
                String insertTemplateSql = "INSERT INTO template_info (template_id, template_name, description, type, category, difficulty," +
                        "estimated_time, tags, is_built_in, usage_count, rating, is_public, created_at, updated_at, is_deleted) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
                getMysqlAdapter().executeUpdate(insertTemplateSql,
                        templateInfoDto.getTemplateId(),
                        templateInfoDto.getName(),
                        templateInfoDto.getDescription(),
                        templateInfoDto.getType(),
                        templateInfoDto.getCategory(),
                        templateInfoDto.getDifficulty(),
                        templateInfoDto.getEstimatedTime(),
                        tagsStr,
                        templateInfoDto.getIsBuiltIn(),
                        templateInfoDto.getUsageCount(),
                        templateInfoDto.getRating(),
                        templateInfoDto.getIsPublic(),
                        currentTime,
                        currentTime,
                        0
                );

                // 2. 解析并保存字段配置
                if (jsonNode.has("fields") && !jsonNode.get("fields").isJsonNull()) {
                    for (var elem : jsonNode.getAsJsonArray("fields")) {
                        JsonObject f = elem.getAsJsonObject();

                        String fieldId = java.util.UUID.randomUUID().toString();
                        Integer sequence = f.has("sequence") && !f.get("sequence").isJsonNull()
                                ? f.get("sequence").getAsInt() : 0;
                        String description = f.get("description").getAsString();
                        String columnName = f.has("columnName") && !f.get("columnName").isJsonNull()
                                ? f.get("columnName").getAsString() : null;
                        String physicalType = f.has("physicalType") && !f.get("physicalType").isJsonNull()
                                ? f.get("physicalType").getAsString() : null;
                        String javaType = f.has("javaType") && !f.get("javaType").isJsonNull()
                                ? f.get("javaType").getAsString() : null;
                        String javaProperty = f.has("javaProperty") && !f.get("javaProperty").isJsonNull()
                                ? f.get("javaProperty").getAsString() : null;
                        String queryMethod = f.has("queryMethod") && !f.get("queryMethod").isJsonNull()
                                ? f.get("queryMethod").getAsString() : null;
                        String displayType = f.get("displayType").getAsString();
                        String dictType = f.has("dictType") && !f.get("dictType").isJsonNull()
                                ? f.get("dictType").getAsString() : null;
                        Integer required = f.get("required").getAsBoolean() ? 1 : 0;
                        String placeholder = f.has("placeholder") && !f.get("placeholder").isJsonNull()
                                ? f.get("placeholder").getAsString() : null;
                        String defaultValueJson = f.has("defaultValue") && !f.get("defaultValue").isJsonNull()
                                ? gson.toJson(f.get("defaultValue")) : null;
                        String optionsJson = f.has("options") && !f.get("options").isJsonNull()
                                ? gson.toJson(f.get("options")) : null;
                        String validationJson = f.has("validation") && !f.get("validation").isJsonNull()
                                ? gson.toJson(f.get("validation")) : null;

                        String insertFieldSql = "INSERT INTO template_field (template_id, field_id, sequence, " +
                                "description, column_name, physical_type, java_type, java_property, query_method, " +
                                "display_type, dict_type, required, placeholder, default_value, options, validation) " +
                                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

                        getMysqlAdapter().executeUpdate(insertFieldSql,
                                templateId, fieldId, sequence, description, columnName, physicalType, javaType,
                                javaProperty, queryMethod, displayType, dictType, required, placeholder,
                                defaultValueJson, optionsJson, validationJson);
                    }
                }
            }else{
                //修改
                templateInfoDto.setTemplateId(templateId);
                templateInfoDto.setName(templateObj.get("name").getAsString());
                templateInfoDto.setDescription(templateObj.get("description").getAsString());
                templateInfoDto.setType(templateObj.get("type").getAsString());
                templateInfoDto.setCategory(templateObj.get("category").getAsString());
                templateInfoDto.setDifficulty(templateObj.get("difficulty").getAsInt());
                templateInfoDto.setEstimatedTime(templateObj.get("estimatedTime").getAsString());
                List<String> templateTags = new ArrayList<>();
                for (int i = 0; i < templateObj.get("tags").getAsJsonArray().size(); i++) {
                    templateTags.add(templateObj.get("tags").getAsJsonArray().get(i).getAsString());
                }
                templateInfoDto.setTags(templateTags);
                templateInfoDto.setIsBuiltIn(templateObj.get("isBuiltIn").getAsBoolean());
                templateInfoDto.setUsageCount(templateObj.get("usageCount").getAsInt());
                templateInfoDto.setRating(templateObj.get("rating").getAsFloat());
                templateInfoDto.setIsPublic(templateObj.get("isPublic").getAsBoolean());
                String tagsStr = templateInfoDto.getTags() == null ? "" : templateInfoDto.getTags().toString();
                
                //更新数据库
                String updateTemplateSql = "UPDATE template_info SET template_name=?, description=?, type=?, category=?, difficulty=?, " +
                        "estimated_time=?, tags=?, is_built_in=?, usage_count=?, rating=?, is_public=?, updated_at=? WHERE template_id=?";
                getMysqlAdapter().executeUpdate(updateTemplateSql,
                        templateInfoDto.getName(),
                        templateInfoDto.getDescription(),
                        templateInfoDto.getType(),
                        templateInfoDto.getCategory(),
                        templateInfoDto.getDifficulty(),
                        templateInfoDto.getEstimatedTime(),
                        tagsStr,
                        templateInfoDto.getIsBuiltIn(),
                        templateInfoDto.getUsageCount(),
                        templateInfoDto.getRating(),
                        templateInfoDto.getIsPublic(),
                        currentTime,
                        templateId
                );

                // 先删除原有字段配置
                String deleteFieldsSql = "DELETE FROM template_field WHERE template_id=?";
                getMysqlAdapter().executeUpdate(deleteFieldsSql, templateId);

                // 重新插入字段配置
                if (jsonNode.has("fields") && !jsonNode.get("fields").isJsonNull()) {
                    for (var elem : jsonNode.getAsJsonArray("fields")) {
                        JsonObject f = elem.getAsJsonObject();

                        String fieldId = java.util.UUID.randomUUID().toString();
                        Integer sequence = f.has("sequence") && !f.get("sequence").isJsonNull()
                                ? f.get("sequence").getAsInt() : 0;
                        String description = f.get("description").getAsString();
                        String columnName = f.has("columnName") && !f.get("columnName").isJsonNull()
                                ? f.get("columnName").getAsString() : null;
                        String physicalType = f.has("physicalType") && !f.get("physicalType").isJsonNull()
                                ? f.get("physicalType").getAsString() : null;
                        String javaType = f.has("javaType") && !f.get("javaType").isJsonNull()
                                ? f.get("javaType").getAsString() : null;
                        String javaProperty = f.has("javaProperty") && !f.get("javaProperty").isJsonNull()
                                ? f.get("javaProperty").getAsString() : null;
                        String queryMethod = f.has("queryMethod") && !f.get("queryMethod").isJsonNull()
                                ? f.get("queryMethod").getAsString() : null;
                        String displayType = f.get("displayType").getAsString();
                        String dictType = f.has("dictType") && !f.get("dictType").isJsonNull()
                                ? f.get("dictType").getAsString() : null;
                        Integer required = f.get("required").getAsBoolean() ? 1 : 0;
                        String placeholder = f.has("placeholder") && !f.get("placeholder").isJsonNull()
                                ? f.get("placeholder").getAsString() : null;
                        String defaultValueJson = f.has("defaultValue") && !f.get("defaultValue").isJsonNull()
                                ? gson.toJson(f.get("defaultValue")) : null;
                        String optionsJson = f.has("options") && !f.get("options").isJsonNull()
                                ? gson.toJson(f.get("options")) : null;
                        String validationJson = f.has("validation") && !f.get("validation").isJsonNull()
                                ? gson.toJson(f.get("validation")) : null;

                        String insertFieldSql = "INSERT INTO template_field (template_id, field_id, sequence, " +
                                "description, column_name, physical_type, java_type, java_property, query_method, " +
                                "display_type, dict_type, required, placeholder, default_value, options, validation) " +
                                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

                        getMysqlAdapter().executeUpdate(insertFieldSql,
                                templateId, fieldId, sequence, description, columnName, physicalType, javaType,
                                javaProperty, queryMethod, displayType, dictType, required, placeholder,
                                defaultValueJson, optionsJson, validationJson);
                    }
                }
            }

            // 3. 构建返回结果
            String templateIdResponse = templateId;
            Map<String, Object> data = new HashMap<>();
            data.put("templateId", templateIdResponse);
            data.put("name", templateObj.get("name").getAsString());
            // 查询创建时间
            String selectCreatedAtSql = "SELECT created_at FROM template_info WHERE template_id = ?";
            List<Map<String, Object>> createdAtRows = getMysqlAdapter().select(selectCreatedAtSql, templateId);
            String createdAt = createdAtRows != null && !createdAtRows.isEmpty() && createdAtRows.get(0).get("created_at") != null
                    ? String.valueOf(createdAtRows.get(0).get("created_at")) : currentTime;
            data.put("createdAt", createdAt);

            result.put("code", 200);
            result.put("msg", "模板保存成功");
            result.put("data", data);
            result.put("timestamp", currentTime);

            resp.setStatus(200);
            responsePrint(resp, toJson(result));

        } catch (Exception e) {
            log.error("保存模板失败: error={}", e.getMessage(), e);
            resp.setStatus(500);
            result.put("error", "保存模板失败：" + e.getMessage());
            responsePrint(resp, toJson(result));
        }
    }

    private void queryTemplate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();

        String templateIdParam = req.getParameter("template_id");
        if (templateIdParam == null || templateIdParam.trim().isEmpty()) {
            resp.setStatus(400);
            result.put("error", "缺少必填参数：template_id");
            responsePrint(resp, toJson(result));
            return;
        }

        try {
            String fieldsSql = "SELECT * FROM template_field WHERE template_id = ? ORDER BY sequence ASC";
            List<Map<String, Object>> fieldRows = getMysqlAdapter().select(fieldsSql, templateIdParam);

            String templateSql = "SELECT * FROM template_info WHERE template_id = ?";
            List<Map<String, Object>> templateRow = getMysqlAdapter().select(templateSql, templateIdParam);

            result.put("template", templateRow != null && !templateRow.isEmpty() ? templateRow.get(0) : Collections.emptyMap());
            result.put("fields", fieldRows != null ? fieldRows : Collections.emptyList());

            resp.setStatus(200);
            responsePrint(resp, toJson(result));
        } catch (Exception e) {
            log.error("查询模板失败: templateId={}, error={}", templateIdParam, e.getMessage(), e);
            resp.setStatus(500);
            result.put("error", "服务器内部错误：" + e.getMessage());
            responsePrint(resp, toJson(result));
        }
    }


    private void templateList(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();

        try {

            // 获取分页参数
            String pageStr = req.getParameter("page");
            String pageSizeStr = req.getParameter("page_size");

            // 页码默认1
            int page = 1;
            if (pageStr != null && !pageStr.isEmpty()) {
                try {
                    page = Integer.parseInt(pageStr);
                    if (page <= 0) {
                        page = 1;
                    }
                } catch (NumberFormatException e) {
                    page = 1;
                }
            }

            // 每页条数默认10
            int pageSize = 10;
            if (pageSizeStr != null && !pageSizeStr.isEmpty()) {
                try {
                    pageSize = Integer.parseInt(pageSizeStr);
                    if (pageSize <= 0 || pageSize > 100) {
                        pageSize = 10;
                    }
                } catch (NumberFormatException e) {
                    pageSize = 10;
                }
            }

            // 计算分页偏移量
            int offset = (page - 1) * pageSize;

            String jsonBody = requestToJson(req);
            JsonObject jsonNode = null;
            if (jsonBody != null && !jsonBody.trim().isEmpty()) {
                try {
                    jsonNode = gson.fromJson(jsonBody, JsonObject.class);
                } catch (Exception e) {
                    log.warn("JSON请求体解析失败，使用默认type=train", e);
                    jsonNode = null; // 解析失败也设为null，避免后续报错
                }
            } else {
                log.debug("请求体为空，使用默认type=train");
            }

            String type = "train";
            if (jsonNode != null && jsonNode.has("type") && !jsonNode.get("type").isJsonNull()) {
                // 字段存在且不为null时，获取值并去除首尾空格
                type = jsonNode.get("type").getAsString().trim();
                // 若trim后为空字符串，仍使用默认值"train"
                if (type.isEmpty()) {
                    type = "train";
                }
            }

            // 查询模板列表
            String sql = "SELECT * FROM template_info WHERE is_deleted = 0 AND type = ? ORDER BY template_id ASC LIMIT ? OFFSET ?";
            List<Map<String, Object>> templateRows = getMysqlAdapter().select(sql, type, pageSize, offset);

            // 构建返回数据
            List<Map<String, Object>> data = new ArrayList<>();
            if (templateRows != null) {
                for (Map<String, Object> row : templateRows) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("template_id", String.valueOf(row.get("template_id")));
                    item.put("template_name", row.get("template_name"));
                    item.put("template_desc", row.get("template_desc"));
                    data.add(item);
                }
            }

            result.put("code", "200");
            result.put("data", data);

            resp.setStatus(200);
            responsePrint(resp, toJson(result));
        } catch (Exception e) {
            log.error("查询模板列表失败: error={}", e.getMessage(), e);
            resp.setStatus(500);
            result.put("code", "500");
            result.put("error", "服务器内部错误：" + e.getMessage());
            responsePrint(resp, toJson(result));
        }
    }

    private void deletedTemplate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        try {
            String templateId = req.getParameter("template_id");
            if (templateId == null || templateId.trim().isEmpty()){
                resp.setStatus(400);
                result.put("error", "缺少必填参数：template_id");
                responsePrint(resp, toJson(result));
                return;
            }
            String jsonBody = requestToJson(req);

            // 更新template_info表中的is_deleted字段为1
            String updateSql = "UPDATE template_info SET is_deleted = 1, updated_at = ? WHERE template_id = ?";
            int rowsAffected = getMysqlAdapter().executeUpdate(updateSql, currentTime, templateId);

            if (rowsAffected > 0) {
                result.put("code", 200);
                result.put("msg", "模板删除成功");
                result.put("timestamp", currentTime);
                resp.setStatus(200);
            } else {
                result.put("code", 404);
                result.put("msg", "未找到指定的模板");
                resp.setStatus(404);
            }

            responsePrint(resp, toJson(result));

        } catch (Exception e) {
            log.error("删除模板失败: error={}", e.getMessage(), e);
            resp.setStatus(500);
            result.put("error", "删除模板失败：" + e.getMessage());
            responsePrint(resp, toJson(result));
        }
    }
}
