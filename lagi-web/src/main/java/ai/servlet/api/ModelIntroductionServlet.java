package ai.servlet.api;

import ai.database.impl.MysqlAdapter;
import ai.dto.DictOptionDto;
import ai.dto.ModelIntroductionDto;
import ai.servlet.BaseServlet;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Slf4j
public class ModelIntroductionServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();
    private String currentTime;

    private static volatile MysqlAdapter mysqlAdapter = null;
    private static MysqlAdapter getMysqlAdapter() {
        if (mysqlAdapter == null) {
            synchronized (ModelIntroductionServlet.class) {
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

        if (method.equals("create")) {
            createModelIntroduction(req, resp);
        } else if (method.equals("list")) {
            listModelIntroduction(req, resp);
        } else if(method.equals("detail")){
            detailModelIntroduction(req, resp);
        } else if(method.equals("update")){
            updateModelIntroduction(req, resp);
        }else if(method.equals("deleted")){
            deletedModelIntroduction(req, resp);
        }else if (method.equals("queryModelCategory")){
            queryModelCategoryList(req, resp);
        }else if (method.equals("queryModelType")){
            queryModelType(req, resp);
        }else if (method.equals("queryFramework")){
            queryFramework(req, resp);
        }else {
            {
                resp.setStatus(404);
                Map<String, String> error = new HashMap<>();
                error.put("error", "接口不存在");
                responsePrint(resp, toJson(error));
            }
        }
    }



    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doGet(req, resp);
    }


    // ========== 新增：查询模型分类下拉框 ==========
    private void queryModelCategoryList(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            List<DictOptionDto> options = new ArrayList<>();
            String sql = "SELECT id, category_name FROM model_category";
            List<Map<String, Object>> categorylist = getMysqlAdapter().select(sql);

            for (Map<String, Object> map : categorylist) {
                DictOptionDto option = new DictOptionDto();
                option.setId((Long) map.get("id"));
                option.setName((String) map.get("category_name"));
                options.add(option);
            }

            result.put("code", 200);
            result.put("msg", "查询模型分类成功");
            result.put("data", options);
            responsePrint(resp, toJson(result));
        } catch (Exception e) {
            log.error("查询模型分类异常: ", e);
            resp.setStatus(500);
            result.put("code", 500);
            result.put("msg", "服务器内部错误");
            responsePrint(resp, toJson(result));
        }
    }


    // ========== 新增：查询模型类型下拉框 ==========
    private void queryModelType(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            List<DictOptionDto> options = new ArrayList<>();
            String sql = "SELECT id, type_name FROM model_type_dict";
            List<Map<String, Object>> typelist = getMysqlAdapter().select(sql);

            for (Map<String, Object> map : typelist) {
                DictOptionDto option = new DictOptionDto();
                option.setId((Long) map.get("id"));
                option.setName((String) map.get("type_name"));
                options.add(option);
            }

            result.put("code", 200);
            result.put("msg", "查询模型类型成功");
            result.put("data", options);
            responsePrint(resp, toJson(result));
        } catch (Exception e) {
            log.error("查询模型类型异常: ", e);
            resp.setStatus(500);
            result.put("code", 500);
            result.put("msg", "服务器内部错误");
            responsePrint(resp, toJson(result));
        }
    }

    // ========== 新增：查询框架下拉框 ==========
    private void queryFramework(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            List<DictOptionDto> options = new ArrayList<>();
            String sql = "SELECT id, framework_name FROM model_framework_dict";
            List<Map<String, Object>> frameworklist = getMysqlAdapter().select(sql);

            for (Map<String, Object> map : frameworklist) {
                DictOptionDto option = new DictOptionDto();
                option.setId((Long) map.get("id"));
                option.setName((String) map.get("framework_name"));
                options.add(option);
            }

            result.put("code", 200);
            result.put("msg", "查询框架成功");
            result.put("data", options);
            responsePrint(resp, toJson(result));
        } catch (Exception e) {
            log.error("查询框架异常: ", e);
            resp.setStatus(500);
            result.put("code", 500);
            result.put("msg", "服务器内部错误");
            responsePrint(resp, toJson(result));
        }
    }

    /**
     * 创建模型简介
     */
    private void createModelIntroduction(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();

        try {
            String jsonBody = requestToJson(req);
            JsonObject jsonNode = gson.fromJson(jsonBody, JsonObject.class);

            // 验证必填参数 modelName
            if (!jsonNode.has("modelName") || jsonNode.get("modelName").isJsonNull() ||
                    jsonNode.get("modelName").getAsString().trim().isEmpty()) {
                resp.setStatus(400);
                result.put("code", 400);
                result.put("msg", "模型名称不能为空");
                responsePrint(resp, toJson(result));
                return;
            }

            // 验证版本号是否为空 version
            if (!jsonNode.has("version") || jsonNode.get("version").isJsonNull() ||
                    jsonNode.get("version").getAsString().trim().isEmpty()) {
                resp.setStatus(400);
                result.put("code", 400);
                result.put("msg", "版本号不能为空");
                responsePrint(resp, toJson(result));
                return;
            }

            // 验证必填参数 title
            if (!jsonNode.has("title") || jsonNode.get("title").isJsonNull() ||
                    jsonNode.get("title").getAsString().trim().isEmpty()) {
                resp.setStatus(400);
                result.put("code", 400);
                result.put("msg", "模型简介标题不能为空");
                responsePrint(resp, toJson(result));
                return;
            }

            // 验证必填参数 description
            if (!jsonNode.has("description") || jsonNode.get("description").isJsonNull() ||
                    jsonNode.get("description").getAsString().trim().isEmpty()) {
                resp.setStatus(400);
                result.put("code", 400);
                result.put("msg", "模型描述不能为空");
                responsePrint(resp, toJson(result));
                return;
            }

            // 验证必填参数 detailContent
            if (!jsonNode.has("detailContent") || jsonNode.get("detailContent").isJsonNull() ||
                    jsonNode.get("detailContent").getAsString().trim().isEmpty()) {
                resp.setStatus(400);
                result.put("code", 400);
                result.put("msg", "模型详细内容介绍不能为空");
                responsePrint(resp, toJson(result));
                return;
            }

            // 验证必填参数 categoryId
            if (!jsonNode.has("categoryId") || jsonNode.get("categoryId").isJsonNull()) {
                resp.setStatus(400);
                result.put("code", 400);
                result.put("msg", "模型分类ID不能为空");
                responsePrint(resp, toJson(result));
                return;
            }

            // 验证必填参数 modelTypeId
            if (!jsonNode.has("modelTypeId") || jsonNode.get("modelTypeId").isJsonNull()) {
                resp.setStatus(400);
                result.put("code", 400);
                result.put("msg", "模型类型ID不能为空");
                responsePrint(resp, toJson(result));
                return;
            }

            // 验证必填参数 framework
            if (!jsonNode.has("frameworkId") || jsonNode.get("frameworkId").isJsonNull()) {
                resp.setStatus(400);
                result.put("code", 400);
                result.put("msg", "框架ID不能为空");
                responsePrint(resp, toJson(result));
                return;
            }

            // 验证必填参数 algorithm
            if (!jsonNode.has("algorithm") || jsonNode.get("algorithm").isJsonNull() ||
                    jsonNode.get("algorithm").getAsString().trim().isEmpty()) {
                resp.setStatus(400);
                result.put("code", 400);
                result.put("msg", "算法名称不能为空");
                responsePrint(resp, toJson(result));
                return;
            }

            // 验证必填参数 inputShape
            if (!jsonNode.has("inputShape") || jsonNode.get("inputShape").isJsonNull() ||
                    jsonNode.get("inputShape").getAsString().trim().isEmpty()) {
                resp.setStatus(400);
                result.put("code", 400);
                result.put("msg", "输入形状不能为空");
                responsePrint(resp, toJson(result));
                return;
            }

            // 验证必填参数 outputShape
            if (!jsonNode.has("outputShape") || jsonNode.get("outputShape").isJsonNull() ||
                    jsonNode.get("outputShape").getAsString().trim().isEmpty()) {
                resp.setStatus(400);
                result.put("code", 400);
                result.put("msg", "输出形状不能为空");
                responsePrint(resp, toJson(result));
                return;
            }

            // 验证必填参数 status
            if (!jsonNode.has("status") || jsonNode.get("status").isJsonNull() ||
                    jsonNode.get("status").getAsString().trim().isEmpty()) {
                resp.setStatus(400);
                result.put("code", 400);
                result.put("msg", "状态不能为空");
                responsePrint(resp, toJson(result));
                return;
            }

            // 验证必填参数 author
            if (!jsonNode.has("author") || jsonNode.get("author").isJsonNull() ||
                    jsonNode.get("author").getAsString().trim().isEmpty()) {
                resp.setStatus(400);
                result.put("code", 400);
                result.put("msg", "作者不能为空");
                responsePrint(resp, toJson(result));
                return;
            }
            ModelIntroductionDto modelIntroductionDto = new ModelIntroductionDto();
            modelIntroductionDto.setModelName(jsonNode.get("modelName").getAsString());
            modelIntroductionDto.setVersion(jsonNode.get("version").getAsString());
            modelIntroductionDto.setTitle(jsonNode.get("title").getAsString());
            modelIntroductionDto.setDescription(jsonNode.get("description").getAsString());
            modelIntroductionDto.setDetailContent(jsonNode.get("detailContent").getAsString());
            modelIntroductionDto.setCategoryId(jsonNode.get("categoryId").getAsInt());
            modelIntroductionDto.setModelTypeId(jsonNode.get("modelTypeId").getAsInt());
            modelIntroductionDto.setFrameworkId(jsonNode.get("frameworkId").getAsInt());
            //modelIntroductionDto.setModelType(jsonNode.get("modelType").getAsString());
            //modelIntroductionDto.setFramework(jsonNode.get("framework").getAsString());
            modelIntroductionDto.setAlgorithm(jsonNode.get("algorithm").getAsString());
            modelIntroductionDto.setInputShape(jsonNode.get("inputShape").getAsString());
            modelIntroductionDto.setOutputShape(jsonNode.get("outputShape").getAsString());
            modelIntroductionDto.setTotalParams(jsonNode.get("totalParams").getAsInt());
            modelIntroductionDto.setTrainableParams(jsonNode.get("trainableParams").getAsInt());
            modelIntroductionDto.setNonTrainableParams(jsonNode.get("nonTrainableParams").getAsInt());
            modelIntroductionDto.setAccuracy(jsonNode.get("accuracy").getAsFloat());
            modelIntroductionDto.setPrecision(jsonNode.get("precision").getAsFloat());
            modelIntroductionDto.setRecall(jsonNode.get("recall").getAsFloat());
            modelIntroductionDto.setF1Score(jsonNode.get("f1Score").getAsFloat());
            modelIntroductionDto.setTags(jsonNode.get("tags").getAsString());
            modelIntroductionDto.setStatus(jsonNode.get("status").getAsString());
            modelIntroductionDto.setAuthor(jsonNode.get("author").getAsString());
            modelIntroductionDto.setDocLink(jsonNode.get("docLink").getAsString());
            modelIntroductionDto.setIconLink(jsonNode.get("iconLink").getAsString());
            modelIntroductionDto.setCreatedAt(currentTime);
            modelIntroductionDto.setIsDeleted(0);
            //保存到数据库
            int rowsAffected = saveModelIntroductionToDB(modelIntroductionDto);
            if (rowsAffected > 0){
                result.put("code", 200);
                result.put("msg", "模型简介创建成功");
                responsePrint(resp, toJson(result));
            }else{
                result.put("code", 400);
                result.put("msg", "模型简介创建失败");
                responsePrint(resp, toJson(result));
            }

        } catch (Exception e) {
            log.error("创建模型简介时发生异常: ", e);
            resp.setStatus(500);
            result.put("code", 500);
            result.put("msg", "服务器内部错误");
            responsePrint(resp, toJson(result));
        }
    }

    private int saveModelIntroductionToDB(ModelIntroductionDto modelIntroductionDto) {
        String sql = "INSERT INTO model_introduction " +
                "(model_name, version, title, description, detail_content, category_id, model_type_id, framework_id, algorithm, " +
                "input_shape, output_shape, total_params, trainable_params, non_trainable_params, accuracy, `precision`, " +
                "recall, f1_score, tags, status, author, doc_link, icon_link, created_at, is_deleted) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try {
            int rowsAffected =getMysqlAdapter().executeUpdate(sql,
                    modelIntroductionDto.getModelName(),
                    modelIntroductionDto.getVersion(),
                    modelIntroductionDto.getTitle(),
                    modelIntroductionDto.getDescription(),
                    modelIntroductionDto.getDetailContent(),
                    modelIntroductionDto.getCategoryId(),
                    modelIntroductionDto.getModelTypeId(),
                    modelIntroductionDto.getFrameworkId(),
                    modelIntroductionDto.getAlgorithm(),
                    modelIntroductionDto.getInputShape(),
                    modelIntroductionDto.getOutputShape(),
                    modelIntroductionDto.getTotalParams(),
                    modelIntroductionDto.getTrainableParams(),
                    modelIntroductionDto.getNonTrainableParams(),
                    modelIntroductionDto.getAccuracy(),
                    modelIntroductionDto.getPrecision(),
                    modelIntroductionDto.getRecall(),
                    modelIntroductionDto.getF1Score(),
                    modelIntroductionDto.getTags(),
                    modelIntroductionDto.getStatus(),
                    modelIntroductionDto.getAuthor(),
                    modelIntroductionDto.getDocLink(),
                    modelIntroductionDto.getIconLink(),
                    modelIntroductionDto.getCreatedAt(),
                    modelIntroductionDto.getIsDeleted()
            );
            return rowsAffected;
        } catch (Exception e) {
            throw new RuntimeException("系统错误：" + e.getMessage(), e);
        }
    }

    private void listModelIntroduction(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            // 解析请求参数
            int page = 1;
            String pageParam = req.getParameter("page");
            if (pageParam != null && !pageParam.trim().isEmpty()) {
                try {
                    page = Integer.parseInt(pageParam.trim());
                    if (page <= 0) page = 1;
                } catch (NumberFormatException e) {
                    page = 1;
                }
            }

            int pageSize = 10;
            String pageSizeParam = req.getParameter("page_size");
            if (pageSizeParam != null && !pageSizeParam.trim().isEmpty()) {
                try {
                    pageSize = Integer.parseInt(pageSizeParam.trim());
                    if (pageSize <= 0) pageSize = 10;
                } catch (NumberFormatException e) {
                    pageSize = 10;
                }
            }

            String keyword = req.getParameter("keyword");
            if (keyword != null) keyword = keyword.trim();
            if (keyword != null && keyword.isEmpty()) keyword = null;

            String status = req.getParameter("status");
            if (status != null) status = status.trim();
            if (status != null && status.isEmpty()) status = null;

            Long categoryId = null;
            String categoryParam = req.getParameter("category_id");
            if (categoryParam != null && !categoryParam.trim().isEmpty()) {
                try {
                    categoryId = Long.parseLong(categoryParam.trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("参数 category_id 必须为数字");
                }
            }

            String startTime = null;
            String endTime = null;
            String createdRange = req.getParameter("created_at");
            if (createdRange != null && !createdRange.trim().isEmpty()) {
                String[] parts = createdRange.split(",");
                if (parts.length == 2) {
                    startTime = parts[0].trim();
                    endTime = parts[1].trim();
                }
            }

            // 查询数据
            long total = queryModelIntroductionCount(keyword, status, categoryId, startTime, endTime);
            Map<String, Object> statusCount = queryModelIntroductionStatusCount(keyword, status, categoryId, startTime, endTime);
            List<Map<String, Object>> list = queryModelIntroductionList(keyword, status, categoryId, startTime, endTime, page, pageSize);

            // 构建返回结果
            Map<String, Object> data = new HashMap<>();
            data.put("total", total);
            data.put("statusCount", statusCount);
            data.put("list", list);

            result.put("code", 200);
            result.put("msg", "查询成功");
            result.put("data", data);
            resp.setStatus(200);
            responsePrint(resp, toJson(result));
        } catch (IllegalArgumentException e) {
            resp.setStatus(400);
            result.put("code", 400);
            result.put("msg", e.getMessage());
            responsePrint(resp, toJson(result));
        } catch (Exception e) {
            log.error("查询模型简介列表失败: {}", e.getMessage(), e);
            resp.setStatus(500);
            result.put("code", 500);
            result.put("msg", "服务器内部错误：" + e.getMessage());
            responsePrint(resp, toJson(result));
        }
    }

    /**
     * 查询模型简介总数
     */
    private long queryModelIntroductionCount(String keyword, String status, Long categoryId, String startTime, String endTime) {
        StringBuilder where = new StringBuilder(" WHERE mi.is_deleted = 0 ");
        List<Object> params = new ArrayList<>();

        if (keyword != null && !keyword.isEmpty()) {
            where.append(" AND (mi.model_name LIKE ? OR mi.description LIKE ? OR mi.title LIKE ?)");
            String likeValue = "%" + keyword + "%";
            params.add(likeValue);
            params.add(likeValue);
            params.add(likeValue);
        }
        if (status != null && !status.isEmpty()) {
            where.append(" AND mi.status = ?");
            params.add(status);
        }
        if (categoryId != null) {
            where.append(" AND mi.category_id = ?");
            params.add(categoryId);
        }
        if (startTime != null) {
            where.append(" AND mi.created_at >= ?");
            params.add(startTime);
        }
        if (endTime != null) {
            where.append(" AND mi.created_at <= ?");
            params.add(endTime);
        }

        String sql = "SELECT COUNT(*) AS cnt FROM model_introduction mi" + where;
        List<Map<String, Object>> result = getMysqlAdapter().select(sql, params.toArray());
        if (!result.isEmpty() && result.get(0).get("cnt") instanceof Number) {
            return ((Number) result.get(0).get("cnt")).longValue();
        }
        return 0;
    }

    /**
     * 查询模型简介状态统计
     */
    private Map<String, Object> queryModelIntroductionStatusCount(String keyword, String status, Long categoryId, String startTime, String endTime) {
        StringBuilder where = new StringBuilder(" WHERE mi.is_deleted = 0 ");
        List<Object> params = new ArrayList<>();

        if (keyword != null && !keyword.isEmpty()) {
            where.append(" AND (mi.model_name LIKE ? OR mi.description LIKE ? OR mi.title LIKE ?)");
            String likeValue = "%" + keyword + "%";
            params.add(likeValue);
            params.add(likeValue);
            params.add(likeValue);
        }
        if (status != null && !status.isEmpty()) {
            where.append(" AND mi.status = ?");
            params.add(status);
        }
        if (categoryId != null) {
            where.append(" AND mi.category_id = ?");
            params.add(categoryId);
        }
        if (startTime != null) {
            where.append(" AND mi.created_at >= ?");
            params.add(startTime);
        }
        if (endTime != null) {
            where.append(" AND mi.created_at <= ?");
            params.add(endTime);
        }

        String sql = "SELECT mi.status AS status, COUNT(*) AS cnt FROM model_introduction mi" + where + " GROUP BY mi.status";
        List<Map<String, Object>> rows = getMysqlAdapter().select(sql, params.toArray());
        Map<String, Object> statusCount = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object statusKey = row.get("status");
            Object cntValue = row.get("cnt");
            if (statusKey != null && cntValue instanceof Number) {
                statusCount.put(statusKey.toString(), ((Number) cntValue).longValue());
            }
        }
        return statusCount;
    }

    /**
     * 查询模型简介列表
     */
    private List<Map<String, Object>> queryModelIntroductionList(String keyword, String status, Long categoryId, String startTime, String endTime, int page, int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE mi.is_deleted = 0 ");
        List<Object> params = new ArrayList<>();

        if (keyword != null && !keyword.isEmpty()) {
            where.append(" AND (mi.model_name LIKE ? OR mi.description LIKE ? OR mi.title LIKE ?)");
            String likeValue = "%" + keyword + "%";
            params.add(likeValue);
            params.add(likeValue);
            params.add(likeValue);
        }
        if (status != null && !status.isEmpty()) {
            where.append(" AND mi.status = ?");
            params.add(status);
        }
        if (categoryId != null) {
            where.append(" AND mi.category_id = ?");
            params.add(categoryId);
        }
        if (startTime != null) {
            where.append(" AND mi.created_at >= ?");
            params.add(startTime);
        }
        if (endTime != null) {
            where.append(" AND mi.created_at <= ?");
            params.add(endTime);
        }

        int offset = (page - 1) * pageSize;
//        String sql = "SELECT * FROM model_introduction mi LEFT JOIN model_category mc ON mi.category_id = mc.id" +
//                where + " ORDER BY mi.created_at DESC LIMIT ?, ?";
        String sql = "SELECT mi.id AS model_id, mi.model_name, mi.version, mc.category_name, " +
                "mi.title, mi.author, mi.status, mi.view_count, mi.created_at " +
                "FROM model_introduction mi LEFT JOIN model_category mc ON mi.category_id = mc.id" +
                where + " ORDER BY mi.created_at DESC LIMIT ?, ?";

        Object[] sqlParams = new Object[params.size() + 2];
        System.arraycopy(params.toArray(), 0, sqlParams, 0, params.size());
        sqlParams[sqlParams.length - 2] = offset;
        sqlParams[sqlParams.length - 1] = pageSize;

        List<Map<String, Object>> rows = getMysqlAdapter().select(sql, sqlParams);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", row.get("model_id"));
            item.put("modelName", row.get("model_name"));
            item.put("version", row.get("version"));
            item.put("category", row.get("category_name"));
            item.put("title", row.get("title"));
            item.put("author", row.get("author"));
            item.put("status", row.get("status"));
            item.put("viewCount", row.get("view_count"));
            item.put("createTime", row.get("created_at"));
            list.add(item);
        }
        return list;
    }


    private void detailModelIntroduction(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();

        try {
            String jsonBody = requestToJson(req);
            JsonObject jsonObject = gson.fromJson(jsonBody, JsonObject.class);
            String paramId = req.getParameter("id");
            if (paramId == null){
                resp.setStatus(404);
                result.put("code", 404);
                result.put("msg", "模型简介ID不能为空");
                responsePrint(resp, toJson(result));
                return;
            }

            long id = Long.parseLong(paramId);

            //long id = jsonObject.get("id").getAsLong();

            // 查询模型详情
            Map<String, Object> modelDetail = queryModelIntroductionDetail(id);
            if (modelDetail == null) {
                resp.setStatus(404);
                result.put("code", 404);
                result.put("msg", "模型简介不存在");
                responsePrint(resp, toJson(result));
                return;
            }
            BigInteger bigCategoryId = (BigInteger) modelDetail.get("category_id");
            Integer categoryId = bigCategoryId != null ? bigCategoryId.intValue() : null;
            String selectCategorySql = "SELECT category_name FROM model_category WHERE id = ?";
            String categoryName = getMysqlAdapter().select(selectCategorySql, categoryId).get(0).get("category_name").toString();
            modelDetail.put("category", categoryName);

            Long frameworkId = (Long) modelDetail.get("framework_id");
            String frameworkSql = "SELECT framework_name FROM model_framework_dict WHERE id = ?";
            String frameworkName = getMysqlAdapter().select(frameworkSql, frameworkId).get(0).get("framework_name").toString();
            modelDetail.put("framework", frameworkName);

            Long modelTypeId = (Long) modelDetail.get("model_type_id");
            String modelTypeSql = "SELECT type_name FROM model_type_dict WHERE id = ?";
            String modelTypeName = getMysqlAdapter().select(modelTypeSql, modelTypeId).get(0).get("type_name").toString();
            modelDetail.put("modelType", modelTypeName);


            result.put("code", 200);
            result.put("msg", "查询成功");
            result.put("data", modelDetail);
            resp.setStatus(200);
            responsePrint(resp, toJson(result));

        } catch (Exception e) {
            log.error("查询模型简介详情失败: {}", e.getMessage(), e);
            resp.setStatus(500);
            result.put("code", 500);
            result.put("msg", "服务器内部错误：" + e.getMessage());
            responsePrint(resp, toJson(result));
        }
    }

    private Map<String, Object> queryModelIntroductionDetail(long id) {
        String sql = "SELECT * FROM model_introduction WHERE id = ?";
        List<Map<String, Object>> rows = getMysqlAdapter().select(sql, id);
        if (rows.isEmpty()) {
            return null;
        }
        return rows.get(0);
    }

    /**
     * 编辑模型简介
     */
    private void updateModelIntroduction(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();

        try {
            String jsonBody = requestToJson(req);
            JsonObject jsonNode = gson.fromJson(jsonBody, JsonObject.class);

            // 验证必填参数 id
            if (!jsonNode.has("id") || jsonNode.get("id").isJsonNull()) {
                resp.setStatus(400);
                result.put("code", 400);
                result.put("msg", "模型简介ID不能为空");
                responsePrint(resp, toJson(result));
                return;
            }

            long id = jsonNode.get("id").getAsLong();

            // 检查记录是否存在
            Map<String, Object> existingRecord = queryModelIntroductionDetail(id);
            if (existingRecord == null) {
                resp.setStatus(404);
                result.put("code", 404);
                result.put("msg", "模型简介不存在");
                responsePrint(resp, toJson(result));
                return;
            }

            // 构建更新SQL，只更新提供的字段
            StringBuilder sql = new StringBuilder("UPDATE model_introduction SET ");
            List<Object> params = new ArrayList<>();
            List<String> setParts = new ArrayList<>();

            if (jsonNode.has("modelName") && !jsonNode.get("modelName").isJsonNull()) {
                setParts.add("model_name = ?");
                params.add(jsonNode.get("modelName").getAsString());
            }
            if (jsonNode.has("version") && !jsonNode.get("version").isJsonNull()) {
                setParts.add("version = ?");
                params.add(jsonNode.get("version").getAsString());
            }
            if (jsonNode.has("title") && !jsonNode.get("title").isJsonNull()) {
                setParts.add("title = ?");
                params.add(jsonNode.get("title").getAsString());
            }
            if (jsonNode.has("description") && !jsonNode.get("description").isJsonNull()) {
                setParts.add("description = ?");
                params.add(jsonNode.get("description").getAsString());
            }
            if (jsonNode.has("detailContent") && !jsonNode.get("detailContent").isJsonNull()) {
                setParts.add("detail_content = ?");
                params.add(jsonNode.get("detailContent").getAsString());
            }
            if (jsonNode.has("categoryId") && !jsonNode.get("categoryId").isJsonNull()) {
                setParts.add("category_id = ?");
                params.add(jsonNode.get("categoryId").getAsInt());
            }
            if (jsonNode.has("modelTypeId") && !jsonNode.get("modelTypeId").isJsonNull()) {
                setParts.add("model_type_id = ?");
                params.add(jsonNode.get("modelTypeId").getAsString());
            }
            if (jsonNode.has("frameworkId") && !jsonNode.get("frameworkId").isJsonNull()) {
                setParts.add("framework_id = ?");
                params.add(jsonNode.get("frameworkId").getAsString());
            }
            if (jsonNode.has("algorithm") && !jsonNode.get("algorithm").isJsonNull()) {
                setParts.add("algorithm = ?");
                params.add(jsonNode.get("algorithm").getAsString());
            }
            if (jsonNode.has("inputShape") && !jsonNode.get("inputShape").isJsonNull()) {
                setParts.add("input_shape = ?");
                params.add(jsonNode.get("inputShape").getAsString());
            }
            if (jsonNode.has("outputShape") && !jsonNode.get("outputShape").isJsonNull()) {
                setParts.add("output_shape = ?");
                params.add(jsonNode.get("outputShape").getAsString());
            }
            if (jsonNode.has("totalParams") && !jsonNode.get("totalParams").isJsonNull()) {
                setParts.add("total_params = ?");
                params.add(jsonNode.get("totalParams").getAsInt());
            }
            if (jsonNode.has("trainableParams") && !jsonNode.get("trainableParams").isJsonNull()) {
                setParts.add("trainable_params = ?");
                params.add(jsonNode.get("trainableParams").getAsInt());
            }
            if (jsonNode.has("nonTrainableParams") && !jsonNode.get("nonTrainableParams").isJsonNull()) {
                setParts.add("non_trainable_params = ?");
                params.add(jsonNode.get("nonTrainableParams").getAsInt());
            }
            if (jsonNode.has("accuracy") && !jsonNode.get("accuracy").isJsonNull()) {
                setParts.add("accuracy = ?");
                params.add(jsonNode.get("accuracy").getAsFloat());
            }
            if (jsonNode.has("precision") && !jsonNode.get("precision").isJsonNull()) {
                setParts.add("`precision` = ?");
                params.add(jsonNode.get("precision").getAsFloat());
            }
            if (jsonNode.has("recall") && !jsonNode.get("recall").isJsonNull()) {
                setParts.add("recall = ?");
                params.add(jsonNode.get("recall").getAsFloat());
            }
            if (jsonNode.has("f1Score") && !jsonNode.get("f1Score").isJsonNull()) {
                setParts.add("f1_score = ?");
                params.add(jsonNode.get("f1Score").getAsFloat());
            }
            if (jsonNode.has("tags") && !jsonNode.get("tags").isJsonNull()) {
                setParts.add("tags = ?");
                params.add(jsonNode.get("tags").getAsString());
            }
            if (jsonNode.has("status") && !jsonNode.get("status").isJsonNull()) {
                setParts.add("status = ?");
                params.add(jsonNode.get("status").getAsString());
            }
            if (jsonNode.has("author") && !jsonNode.get("author").isJsonNull()) {
                setParts.add("author = ?");
                params.add(jsonNode.get("author").getAsString());
            }
            if (jsonNode.has("docLink") && !jsonNode.get("docLink").isJsonNull()) {
                setParts.add("doc_link = ?");
                params.add(jsonNode.get("docLink").getAsString());
            }
            if (jsonNode.has("iconLink") && !jsonNode.get("iconLink").isJsonNull()) {
                setParts.add("icon_link = ?");
                params.add(jsonNode.get("iconLink").getAsString());
            }

            // 如果没有提供任何要更新的字段
            if (setParts.isEmpty()) {
                resp.setStatus(400);
                result.put("code", 400);
                result.put("msg", "至少需要提供一个要更新的字段");
                responsePrint(resp, toJson(result));
                return;
            }

            // 添加更新时间
            setParts.add("updated_at = ?");
            params.add(currentTime);

            sql.append(String.join(", ", setParts));
            sql.append(" WHERE id = ? AND is_deleted = 0");
            params.add(id);

            // 执行更新
            int rowsAffected = getMysqlAdapter().executeUpdate(sql.toString(), params.toArray());
            if (rowsAffected > 0) {
                result.put("code", 200);
                result.put("msg", "编辑成功");
                resp.setStatus(200);
                responsePrint(resp, toJson(result));
            } else {
                result.put("code", 400);
                result.put("msg", "编辑失败，记录可能不存在或已被删除");
                resp.setStatus(400);
                responsePrint(resp, toJson(result));
            }

        } catch (Exception e) {
            log.error("编辑模型简介时发生异常: ", e);
            resp.setStatus(500);
            result.put("code", 500);
            result.put("msg", "服务器内部错误");
            responsePrint(resp, toJson(result));
        }
    }

    /**
     * 删除模型简介（软删除）
     */
    private void deletedModelIntroduction(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();

        try {
            String paramId = req.getParameter("id");
            if (paramId == null){
                resp.setStatus(404);
                result.put("msg", "模型简介ID不能为空");
                responsePrint(resp, toJson(result));
                return;
            }
            long id = Long.parseLong(paramId);
//            String jsonBody = requestToJson(req);
//            JsonObject jsonObject = gson.fromJson(jsonBody, JsonObject.class);
//
//            // 验证必填参数 id
//            if (!jsonObject.has("id") || jsonObject.get("id").isJsonNull()) {
//                resp.setStatus(400);
//                result.put("code", 400);
//                result.put("msg", "模型简介ID不能为空");
//                responsePrint(resp, toJson(result));
//                return;
//            }
//
//            long id = jsonObject.get("id").getAsLong();

            // 检查记录是否存在
            Map<String, Object> existingRecord = queryModelIntroductionDetail(id);
            if (existingRecord == null) {
                resp.setStatus(404);
                result.put("code", 404);
                result.put("msg", "模型简介不存在");
                responsePrint(resp, toJson(result));
                return;
            }

            // 执行软删除：将is_deleted设置为1
            String sql = "UPDATE model_introduction SET is_deleted = 1 WHERE id = ? AND is_deleted = 0";
            int rowsAffected = getMysqlAdapter().executeUpdate(sql, id);

            if (rowsAffected > 0) {
                result.put("code", 200);
                result.put("msg", "模型简介删除成功");
                resp.setStatus(200);
                responsePrint(resp, toJson(result));
            } else {
                result.put("code", 400);
                result.put("msg", "删除失败，记录可能不存在或已被删除");
                resp.setStatus(400);
                responsePrint(resp, toJson(result));
            }

        } catch (Exception e) {
            log.error("删除模型简介时发生异常: ", e);
            resp.setStatus(500);
            result.put("code", 500);
            result.put("msg", "服务器内部错误");
            responsePrint(resp, toJson(result));
        }
    }

}
