package ai.servlet.api;

import ai.database.impl.MysqlAdapter;
import ai.servlet.BaseServlet;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Slf4j
public class ModelCategoryServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();
    private String currentTime;

    private static volatile MysqlAdapter mysqlAdapter = null;
    private static MysqlAdapter getMysqlAdapter() {
        if (mysqlAdapter == null) {
            synchronized (ModelCategoryServlet.class) {
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
            createModelCategory(req, resp);
        } else if (method.equals("list")) {
            listModelCategory(req, resp);
        } else if(method.equals("detail")){
            detailModelCategory(req, resp);
        } else if(method.equals("update")){
            updateModelCategory(req, resp);
        }else if(method.equals("deleted")){
            deletedModelCategory(req, resp);
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

    /**
     * 创建模型分类
     * @param req
     * @param resp
     */
    private void createModelCategory(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            String jsonBody = requestToJson(req);
            JsonObject jsonNode = gson.fromJson(jsonBody, JsonObject.class);

            // 验证必填参数：categoryName
            if (!jsonNode.has("categoryName") || jsonNode.get("categoryName").isJsonNull() ||
                    jsonNode.get("categoryName").getAsString().trim().isEmpty()) {
                resp.setStatus(400);
                result.put("code", 400);
                result.put("msg", "分类名称不能为空");
                responsePrint(resp, toJson(result));
                return;
            }

            // 验证必填参数：creator
            if (!jsonNode.has("creator") || jsonNode.get("creator").isJsonNull() ||
                    jsonNode.get("creator").getAsString().trim().isEmpty()) {
                resp.setStatus(400);
                result.put("code", 400);
                result.put("msg", "创建人不能为空");
                responsePrint(resp, toJson(result));
                return;
            }

            String categoryName = jsonNode.get("categoryName").getAsString().trim();
            String creator = jsonNode.get("creator").getAsString().trim();

            // 验证参数长度
            if (categoryName.length() > 50) {
                resp.setStatus(400);
                result.put("code", 400);
                result.put("msg", "分类名称长度不能超过50字符");
                responsePrint(resp, toJson(result));
                return;
            }

            if (creator.length() > 50) {
                resp.setStatus(400);
                result.put("code", 400);
                result.put("msg", "创建人用户名长度不能超过50字符");
                responsePrint(resp, toJson(result));
                return;
            }

            // 处理可选参数
            int parentId = 0;
            if (jsonNode.has("parentId") && !jsonNode.get("parentId").isJsonNull()) {
                parentId = jsonNode.get("parentId").getAsInt();
            }

            String description = null;
            if (jsonNode.has("description") && !jsonNode.get("description").isJsonNull()) {
                description = jsonNode.get("description").getAsString().trim();
                if (description.length() > 200) {
                    resp.setStatus(400);
                    result.put("code", 400);
                    result.put("msg", "分类描述长度不能超过200字符");
                    responsePrint(resp, toJson(result));
                    return;
                }
            }

            int sort = 0;
            if (jsonNode.has("sort") && !jsonNode.get("sort").isJsonNull()) {
                sort = jsonNode.get("sort").getAsInt();
            }

            String status = "active";
            if (jsonNode.has("status") && !jsonNode.get("status").isJsonNull()) {
                status = jsonNode.get("status").getAsString().trim();
                // 验证状态值
                if (!status.equals("active") && !status.equals("inactive") && !status.equals("archived")) {
                    resp.setStatus(400);
                    result.put("code", 400);
                    result.put("msg", "分类状态值无效，可选值：active、inactive、archived");
                    responsePrint(resp, toJson(result));
                    return;
                }
            }

            // 如果parentId不为0，验证父级分类是否存在
            if (parentId != 0) {
                if (!isParentCategoryExists(parentId)) {
                    resp.setStatus(404);
                    result.put("code", 404);
                    result.put("msg", "父级分类ID不存在");
                    responsePrint(resp, toJson(result));
                    return;
                }
            }

            // 检查同一父级分类下名称是否已存在
            if (isCategoryNameExists(categoryName, parentId)) {
                resp.setStatus(409);
                result.put("code", 409);
                result.put("msg", "同一父级分类下名称已存在");
                responsePrint(resp, toJson(result));
                return;
            }

          saveModelCategoryToDB(categoryName, parentId, description, sort, status, creator);

            resp.setStatus(200);
            result.put("code", 200);
            result.put("msg", "分类创建成功");
            responsePrint(resp, toJson(result));

        } catch (Exception e) {
            log.error("创建模型分类失败: error={}", e.getMessage(), e);
            resp.setStatus(500);
            result.put("code", 500);
            result.put("msg", "服务器内部错误：" + e.getMessage());
            responsePrint(resp, toJson(result));
        }
    }


    /**
     * 保存模型分类到数据库
     * @param categoryName 分类名称
     * @param parentId 父级分类ID
     * @param description 分类描述
     * @param sort 排序值
     * @param status 分类状态
     * @param creator 创建人
     * @return 新创建的分类ID
     */
    private void saveModelCategoryToDB(String categoryName, Integer parentId, String description, Integer sort, String status, String creator) {
        currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        int isDeleted = 0;//逻辑删除字段
        String insertSql = "INSERT INTO model_category (category_name, parent_id, description, sort, status, creator, created_at, is_deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            getMysqlAdapter().executeUpdate(insertSql,
                    categoryName,
                    parentId,
                    description,
                    sort,
                    status,
                    creator,
                    currentTime,
                    isDeleted
            );

        } catch (Exception e) {
            log.error("保存模型分类失败: categoryName={}, parentId={}, error={}", categoryName, parentId, e.getMessage(), e);
            throw new RuntimeException("保存模型分类失败：" + e.getMessage(), e);
        }
    }

    /**
     * 检查父级分类是否存在
     * @param parentId 父级分类ID
     * @return true-存在，false-不存在
     */
    private boolean isParentCategoryExists(Integer parentId) {
        String checkParentSql = "SELECT COUNT(*) AS cnt FROM model_category WHERE id = ?";
        try {
            List<Map<String, Object>> parentResult = getMysqlAdapter().select(checkParentSql, parentId);
            if (parentResult != null && !parentResult.isEmpty()) {
                Integer parentCount = ((Number) parentResult.get(0).get("cnt")).intValue();
                return parentCount > 0;
            }
            return false;
        } catch (Exception e) {
            log.error("检查父级分类是否存在失败: parentId={}, error={}", parentId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 检查同一父级分类下名称是否已存在
     * @param categoryName 分类名称
     * @param parentId 父级分类ID
     * @return true-已存在，false-不存在
     */
    private boolean isCategoryNameExists(String categoryName, Integer parentId) {
        String checkDuplicateSql = "SELECT COUNT(*) AS cnt FROM model_category WHERE category_name = ? AND parent_id = ?";
        try {
            List<Map<String, Object>> duplicateResult = getMysqlAdapter().select(checkDuplicateSql, categoryName, parentId);
            if (duplicateResult != null && !duplicateResult.isEmpty()) {
                Integer duplicateCount = ((Number) duplicateResult.get(0).get("cnt")).intValue();
                return duplicateCount > 0;
            }
            return false;
        } catch (Exception e) {
            log.error("检查分类名称是否存在失败: categoryName={}, parentId={}, error={}", categoryName, parentId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 更新分类时检查名称是否重复
     */
    private boolean isCategoryNameExists(String categoryName, Integer parentId, long excludeId) {
        String checkDuplicateSql = "SELECT COUNT(*) AS cnt FROM model_category WHERE category_name = ? AND parent_id = ? AND id <> ? AND is_deleted = 0";
        try {
            List<Map<String, Object>> duplicateResult = getMysqlAdapter().select(checkDuplicateSql, categoryName, parentId, excludeId);
            if (duplicateResult != null && !duplicateResult.isEmpty()) {
                Integer duplicateCount = ((Number) duplicateResult.get(0).get("cnt")).intValue();
                return duplicateCount > 0;
            }
            return false;
        } catch (Exception e) {
            log.error("检查分类名称是否存在失败: categoryName={}, parentId={}, excludeId={}, error={}", categoryName, parentId, excludeId, e.getMessage(), e);
            return false;
        }
    }


    /**
     * 模型分类列表查询
     * 对应接口：GET /api/model/category/list
     *
     * 支持参数：
     * - page        页码，默认 1
     * - page_size   每页条数，默认 10
     * - parentId    父级分类ID
     * - status      分类状态：active、inactive、archived
     * - category_name 分类名称，模糊查询
     */
    private void listModelCategory(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            // 解析分页参数
            int page = 1;
            int pageSize = 10;
            String pageParam = req.getParameter("page");
            String pageSizeParam = req.getParameter("page_size");

            if (pageParam != null && !pageParam.trim().isEmpty()) {
                try {
                    page = Integer.parseInt(pageParam.trim());
                    if (page <= 0) {
                        page = 1;
                    }
                } catch (NumberFormatException e) {
                    resp.setStatus(400);
                    result.put("code", 400);
                    result.put("msg", "参数 page 必须为整数");
                    responsePrint(resp, toJson(result));
                    return;
                }
            }

            if (pageSizeParam != null && !pageSizeParam.trim().isEmpty()) {
                try {
                    pageSize = Integer.parseInt(pageSizeParam.trim());
                    if (pageSize <= 0) {
                        pageSize = 10;
                    }
                } catch (NumberFormatException e) {
                    resp.setStatus(400);
                    result.put("code", 400);
                    result.put("msg", "参数 page_size 必须为整数");
                    responsePrint(resp, toJson(result));
                    return;
                }
            }

            // 解析筛选参数
            Long parentId = null;
            String parentIdParam = req.getParameter("parentId");
            if (parentIdParam != null && !parentIdParam.trim().isEmpty()) {
                try {
                    parentId = Long.parseLong(parentIdParam.trim());
                } catch (NumberFormatException e) {
                    resp.setStatus(400);
                    result.put("code", 400);
                    result.put("msg", "参数 parentId 必须为数字");
                    responsePrint(resp, toJson(result));
                    return;
                }
            }

            String status = req.getParameter("status");
            if (status != null) {
                status = status.trim();
                if (!status.isEmpty()) {
                    if (!status.equals("active") && !status.equals("inactive") && !status.equals("archived")) {
                        resp.setStatus(400);
                        result.put("code", 400);
                        result.put("msg", "分类状态值无效，可选值：active、inactive、archived");
                        responsePrint(resp, toJson(result));
                        return;
                    }
                } else {
                    status = null;
                }
            }

            String categoryName = req.getParameter("category_name");
            if (categoryName != null) {
                categoryName = categoryName.trim();
                if (categoryName.isEmpty()) {
                    categoryName = null;
                }
            }

            // 1. 查询总条数（用于分页）
            long total = getModelCategoryTotal(parentId, status, categoryName);

            // 2. 查询状态统计
            Map<String, Object> statusCount = getModelCategoryStatusCount(parentId, categoryName);

            // 3. 查询列表数据
            List<Map<String, Object>> list = getModelCategoryList(page, pageSize, parentId, status, categoryName);

            Map<String, Object> data = new HashMap<>();
            data.put("total", total);
            data.put("statusCount", statusCount);
            data.put("list", list);

            result.put("code", 200);
            result.put("msg", "查询成功");
            result.put("data", data);

            resp.setStatus(200);
            responsePrint(resp, toJson(result));

        } catch (Exception e) {
            log.error("查询模型分类列表失败: error={}", e.getMessage(), e);
            resp.setStatus(500);
            result.put("code", 500);
            result.put("msg", "服务器内部错误：" + e.getMessage());
            responsePrint(resp, toJson(result));
        }
    }
    /**
     * 查询模型分类总数
     * @param parentId 父级分类ID
     * @param status 分类状态
     * @param categoryName 分类名称（模糊）
     * @return 总条数
     */
    private long getModelCategoryTotal(Long parentId, String status, String categoryName) {
        StringBuilder whereSql = new StringBuilder(" WHERE is_deleted = 0");
        List<Object> params = new java.util.ArrayList<>();

        if (parentId != null) {
            whereSql.append(" AND parent_id = ?");
            params.add(parentId);
        }
        if (status != null) {
            whereSql.append(" AND status = ?");
            params.add(status);
        }
        if (categoryName != null) {
            whereSql.append(" AND category_name LIKE ?");
            params.add("%" + categoryName + "%");
        }

        String countSql = "SELECT COUNT(*) AS cnt FROM model_category" + whereSql;
        try {
            List<Map<String, Object>> countResult = getMysqlAdapter().select(countSql, params.toArray());
            if (countResult != null && !countResult.isEmpty() && countResult.get(0).get("cnt") != null) {
                return ((Number) countResult.get(0).get("cnt")).longValue();
            }
            return 0;
        } catch (Exception e) {
            log.error("查询模型分类总数失败: parentId={}, status={}, categoryName={}, error={}", parentId, status, categoryName, e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 查询模型分类状态统计
     * @param parentId 父级分类ID
     * @param categoryName 分类名称（模糊）
     * @return 各状态数量
     */
    private Map<String, Object> getModelCategoryStatusCount(Long parentId, String categoryName) {
        StringBuilder whereSql = new StringBuilder(" WHERE is_deleted = 0");
        List<Object> params = new java.util.ArrayList<>();
        if (parentId != null) {
            whereSql.append(" AND parent_id = ?");
            params.add(parentId);
        }
        if (categoryName != null) {
            whereSql.append(" AND category_name LIKE ?");
            params.add("%" + categoryName + "%");
        }

        String statusCountSql = "SELECT status, COUNT(*) AS cnt FROM model_category" + whereSql + " GROUP BY status";
        Map<String, Object> statusCount = new HashMap<>();
        statusCount.put("active", 0);
        statusCount.put("inactive", 0);
        statusCount.put("archived", 0);

        try {
            List<Map<String, Object>> statusCountResult = getMysqlAdapter().select(statusCountSql, params.toArray());
            if (statusCountResult != null) {
                for (Map<String, Object> row : statusCountResult) {
                    Object statusVal = row.get("status");
                    Object cntVal = row.get("cnt");
                    if (statusVal != null && cntVal != null) {
                        String s = statusVal.toString();
                        long cnt = ((Number) cntVal).longValue();
                        statusCount.put(s, cnt);
                    }
                }
            }
        } catch (Exception e) {
            log.error("查询模型分类状态统计失败: parentId={}, categoryName={}, error={}", parentId, categoryName, e.getMessage(), e);
        }

        return statusCount;
    }

    /**
     * 查询模型分类列表
     * @param page 页码
     * @param pageSize 每页条数
     * @param parentId 父级分类ID
     * @param status 分类状态
     * @param categoryName 分类名称（模糊）
     * @return 列表数据
     */
    private List<Map<String, Object>> getModelCategoryList(int page, int pageSize, Long parentId, String status, String categoryName) {
        StringBuilder whereSql = new StringBuilder(" WHERE is_deleted = 0");
        List<Object> params = new java.util.ArrayList<>();

        if (parentId != null) {
            whereSql.append(" AND parent_id = ?");
            params.add(parentId);
        }
        if (status != null) {
            whereSql.append(" AND status = ?");
            params.add(status);
        }
        if (categoryName != null) {
            whereSql.append(" AND category_name LIKE ?");
            params.add("%" + categoryName + "%");
        }

        int offset = (page - 1) * pageSize;
        String listSql = "SELECT id, category_name, parent_id, description, sort, status, creator, created_at " +
                "FROM model_category" +
                whereSql +
                " ORDER BY sort ASC, id DESC LIMIT ? OFFSET ?";

        params.add(pageSize);
        params.add(offset);

        List<Map<String, Object>> list = new java.util.ArrayList<>();
        try {
            List<Map<String, Object>> queryResult = getMysqlAdapter().select(listSql, params.toArray());
            if (queryResult != null) {
                for (Map<String, Object> row : queryResult) {
                    list.add(convertToCategoryItem(row));
                }
            }
        } catch (Exception e) {
            log.error("查询模型分类列表失败: parentId={}, status={}, categoryName={}, error={}", parentId, status, categoryName, e.getMessage(), e);
        }
        return list;
    }

    /**
     * 将数据库行转换为返回给前端的分类对象
     * @param row 查询结果行
     * @return 分类对象
     */
    private Map<String, Object> convertToCategoryItem(Map<String, Object> row) {
        Map<String, Object> item = new HashMap<>();
        if (row == null) {
            return item;
        }
        item.put("id", row.get("id"));
        item.put("categoryName", row.get("category_name"));
        item.put("parentId", row.get("parent_id"));
        item.put("description", row.get("description"));
        item.put("sort", row.get("sort"));
        item.put("status", row.get("status"));
        item.put("createTime", row.get("created_at"));
        item.put("creator", row.get("creator"));
        return item;
    }


    /**
     * 查询分类详情
     * 对应接口：GET /api/model/category/detail?id=xxx
     */
    private void detailModelCategory(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            String idParam = req.getParameter("id");
            if (idParam == null || idParam.trim().isEmpty()) {
                resp.setStatus(400);
                result.put("code", 400);
                result.put("msg", "分类ID不能为空");
                responsePrint(resp, toJson(result));
                return;
            }

            long categoryId;
            try {
                categoryId = Long.parseLong(idParam.trim());
            } catch (NumberFormatException e) {
                resp.setStatus(400);
                result.put("code", 400);
                result.put("msg", "分类ID必须为数字");
                responsePrint(resp, toJson(result));
                return;
            }

            Map<String, Object> categoryRow = getCategoryById(categoryId);
            if (categoryRow == null || categoryRow.isEmpty()) {
                resp.setStatus(404);
                result.put("code", 404);
                result.put("msg", "分类不存在或已删除");
                responsePrint(resp, toJson(result));
                return;
            }

            Map<String, Object> basicInfo = buildCategoryBasicInfo(categoryRow);
            List<Map<String, Object>> subCategoryList = getSubCategoryList(categoryId);

            Map<String, Object> statInfo = new HashMap<>();
            Object modelCountObj = basicInfo.get("modelCount");
            long modelCount = modelCountObj instanceof Number ? ((Number) modelCountObj).longValue() : 0L;
            statInfo.put("totalModel", modelCount);
            statInfo.put("subCategoryCount", subCategoryList.size());

            List<Map<String, Object>> operationLogs = getCategoryOperationLogs(categoryId, categoryRow);

            Map<String, Object> data = new HashMap<>();
            data.put("basicInfo", basicInfo);
            data.put("statInfo", statInfo);
            data.put("subCategoryList", subCategoryList);
            data.put("operationLogs", operationLogs);

            result.put("code", 200);
            result.put("msg", "查询成功");
            result.put("data", data);

            resp.setStatus(200);
            responsePrint(resp, toJson(result));

        } catch (Exception e) {
            log.error("查询模型分类详情失败: error={}", e.getMessage(), e);
            resp.setStatus(500);
            result.put("code", 500);
            result.put("msg", "服务器内部错误：" + e.getMessage());
            responsePrint(resp, toJson(result));
        }
    }

    /**
     * 根据ID获取分类数据
     */
    private Map<String, Object> getCategoryById(long categoryId) {
        String sql = "SELECT * FROM model_category WHERE id = ? AND is_deleted = 0 LIMIT 1";
        try {
            List<Map<String, Object>> queryResult = getMysqlAdapter().select(sql, categoryId);
            if (queryResult != null && !queryResult.isEmpty()) {
                return queryResult.get(0);
            }
        } catch (Exception e) {
            log.error("根据ID查询分类失败: categoryId={}, error={}", categoryId, e.getMessage(), e);
        }
        return null;
    }


    /**
     * 组装基础信息
     */
    private Map<String, Object> buildCategoryBasicInfo(Map<String, Object> categoryRow) {
        Map<String, Object> basicInfo = new HashMap<>();
        if (categoryRow == null) {
            return basicInfo;
        }
        long categoryId = ((Number) categoryRow.get("id")).longValue();
        long parentId = categoryRow.get("parent_id") == null ? 0L : ((Number) categoryRow.get("parent_id")).longValue();

        basicInfo.put("categoryName", categoryRow.get("category_name"));
        basicInfo.put("categoryId", String.valueOf(categoryId));
        basicInfo.put("parentCategory", parentId == 0 ? "无" : getParentCategoryName(parentId));
        basicInfo.put("level", parentId == 0 ? "一级分类" : "二级分类");
        basicInfo.put("status", translateStatus(categoryRow.get("status")));
        basicInfo.put("sort", categoryRow.get("sort"));
        basicInfo.put("modelCount", getCategoryModelCount(categoryId));
        basicInfo.put("description", categoryRow.get("description"));
        return basicInfo;
    }

    /**
     * 查询子分类列表
     */
    private List<Map<String, Object>> getSubCategoryList(long parentId) {
        String sql = "SELECT id, category_name, status, created_at FROM model_category WHERE parent_id = ? AND is_deleted = 0 ORDER BY sort ASC, id DESC";
        List<Map<String, Object>> list = new java.util.ArrayList<>();
        try {
            List<Map<String, Object>> queryResult = getMysqlAdapter().select(sql, parentId);
            if (queryResult != null) {
                for (Map<String, Object> row : queryResult) {
                    Map<String, Object> item = new HashMap<>();
                    long categoryId = row.get("id") == null ? 0L : ((Number) row.get("id")).longValue();
                    item.put("categoryName", row.get("category_name"));
                    item.put("modelCount", getCategoryModelCount(categoryId));
                    item.put("status", translateStatus(row.get("status")));
                    item.put("createTime", row.get("created_at"));
                    list.add(item);
                }
            }
        } catch (Exception e) {
            log.error("查询子分类失败: parentId={}, error={}", parentId, e.getMessage(), e);
        }
        return list;
    }

    /**
     * 统计分类下模型数量
     */
    private long getCategoryModelCount(long categoryId) {
        String sql = "SELECT COUNT(*) AS cnt FROM model_info WHERE category_id = ?";
        try {
            List<Map<String, Object>> queryResult = getMysqlAdapter().select(sql, categoryId);
            if (queryResult != null && !queryResult.isEmpty() && queryResult.get(0).get("cnt") != null) {
                return ((Number) queryResult.get(0).get("cnt")).longValue();
            }
        } catch (Exception e) {
            log.warn("统计分类下模型数量失败: categoryId={}, error={}", categoryId, e.getMessage());
        }
        return 0L;
    }

    /**
     * 将状态转换为前端文案
     */
    private String translateStatus(Object statusObj) {
        if (statusObj == null) {
            return "未知";
        }
        String status = statusObj.toString();
        switch (status) {
            case "active":
                return "活跃";
            case "inactive":
                return "非活跃";
            case "archived":
                return "已归档";
            default:
                return status;
        }
    }

    /**
     * 查询分类操作日志
     */
    private List<Map<String, Object>> getCategoryOperationLogs(long categoryId, Map<String, Object> categoryRow) {
        List<Map<String, Object>> logs = new java.util.ArrayList<>();
        String sql = "SELECT operation_type, operator, created_at FROM model_category_operation_log WHERE category_id = ? ORDER BY created_at DESC LIMIT 20";
        try {
            List<Map<String, Object>> queryResult = getMysqlAdapter().select(sql, categoryId);
            if (queryResult != null && !queryResult.isEmpty()) {
                for (Map<String, Object> row : queryResult) {
                    Map<String, Object> logItem = new HashMap<>();
                    logItem.put("time", row.get("created_at"));
                    logItem.put("operationType", row.get("operation_type"));
                    logItem.put("operator", row.get("operator"));
                    logs.add(logItem);
                }
            }
        } catch (Exception e) {
            log.warn("查询分类操作日志失败: categoryId={}, error={}", categoryId, e.getMessage());
        }

        if (logs.isEmpty() && categoryRow != null) {
            Map<String, Object> defaultLog = new HashMap<>();
            defaultLog.put("time", categoryRow.get("created_at"));
            defaultLog.put("operationType", "创建分类");
            defaultLog.put("operator", categoryRow.get("creator"));
            logs.add(defaultLog);
        }
        return logs;
    }

    /**
     * 查询父级分类名称
     */
    private String getParentCategoryName(long parentId) {
        String sql = "SELECT category_name FROM model_category WHERE id = ? AND is_deleted = 0";
        try {
            List<Map<String, Object>> queryResult = getMysqlAdapter().select(sql, parentId);
            if (queryResult != null && !queryResult.isEmpty()) {
                Object name = queryResult.get(0).get("category_name");
                if (name != null) {
                    return name.toString();
                }
            }
        } catch (Exception e) {
            log.error("查询父级分类名称失败: parentId={}, error={}", parentId, e.getMessage(), e);
        }
        return "未知";
    }


    /**
     * 修改模型分类
     * @param req
     * @param resp
     * @throws IOException
     */
    private void updateModelCategory(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            String jsonBody = requestToJson(req);
            if (jsonBody == null || jsonBody.trim().isEmpty()) {
                resp.setStatus(400);
                result.put("code", 400);
                result.put("msg", "请求体不能为空");
                responsePrint(resp, toJson(result));
                return;
            }

            String id = req.getParameter("id");

            JsonObject jsonNode = gson.fromJson(jsonBody, JsonObject.class);
//            if (jsonNode == null || !jsonNode.has("id") || jsonNode.get("id").isJsonNull()) {
//                resp.setStatus(400);
//                result.put("code", 400);
//                result.put("msg", "分类ID不能为空");
//                responsePrint(resp, toJson(result));
//                return;
//            }

            long categoryId = Long.parseLong(id);
//            try {
//                categoryId =  Long.parseLong(id);
//            } catch (Exception e) {
//                resp.setStatus(400);
//                result.put("code", 400);
//                result.put("msg", "分类ID必须为数字");
//                responsePrint(resp, toJson(result));
//                return;
//            }

            Map<String, Object> categoryRow = getCategoryById(categoryId);
            if (categoryRow == null || categoryRow.isEmpty()) {
                resp.setStatus(404);
                result.put("code", 404);
                result.put("msg", "分类不存在或已删除");
                responsePrint(resp, toJson(result));
                return;
            }

            java.util.LinkedHashMap<String, Object> updateFields = new java.util.LinkedHashMap<>();
            Integer parentId = categoryRow.get("parent_id") == null ? 0 : ((Number) categoryRow.get("parent_id")).intValue();

            if (jsonNode.has("parentId") && !jsonNode.get("parentId").isJsonNull()) {
                int newParentId;
                try {
                    newParentId = jsonNode.get("parentId").getAsInt();
                } catch (Exception e) {
                    resp.setStatus(400);
                    result.put("code", 400);
                    result.put("msg", "parentId必须为数字");
                    responsePrint(resp, toJson(result));
                    return;
                }
                if (newParentId < 0) {
                    resp.setStatus(400);
                    result.put("code", 400);
                    result.put("msg", "父级分类ID不能小于0");
                    responsePrint(resp, toJson(result));
                    return;
                }
                if (newParentId == categoryId) {
                    resp.setStatus(400);
                    result.put("code", 400);
                    result.put("msg", "父级分类不能为自身");
                    responsePrint(resp, toJson(result));
                    return;
                }
                if (newParentId != 0 && getCategoryById(newParentId) == null) {
                    resp.setStatus(404);
                    result.put("code", 404);
                    result.put("msg", "父级分类ID不存在");
                    responsePrint(resp, toJson(result));
                    return;
                }
                parentId = newParentId;
                updateFields.put("parent_id", newParentId);
            }

            String categoryName = categoryRow.get("category_name") == null ? null : categoryRow.get("category_name").toString();
            if (jsonNode.has("categoryName") && !jsonNode.get("categoryName").isJsonNull()) {
                String newCategoryName = jsonNode.get("categoryName").getAsString().trim();
                if (newCategoryName.isEmpty()) {
                    resp.setStatus(400);
                    result.put("code", 400);
                    result.put("msg", "分类名称不能为空");
                    responsePrint(resp, toJson(result));
                    return;
                }
                if (newCategoryName.length() > 50) {
                    resp.setStatus(400);
                    result.put("code", 400);
                    result.put("msg", "分类名称长度不能超过50字符");
                    responsePrint(resp, toJson(result));
                    return;
                }
                categoryName = newCategoryName;
                updateFields.put("category_name", newCategoryName);
            }

            if (jsonNode.has("description") && !jsonNode.get("description").isJsonNull()) {
                String description = jsonNode.get("description").getAsString().trim();
                if (description.length() > 200) {
                    resp.setStatus(400);
                    result.put("code", 400);
                    result.put("msg", "分类描述长度不能超过200字符");
                    responsePrint(resp, toJson(result));
                    return;
                }
                updateFields.put("description", description.isEmpty() ? null : description);
            }

            if (jsonNode.has("sort") && !jsonNode.get("sort").isJsonNull()) {
                try {
                    int sort = jsonNode.get("sort").getAsInt();
                    updateFields.put("sort", sort);
                } catch (Exception e) {
                    resp.setStatus(400);
                    result.put("code", 400);
                    result.put("msg", "排序值必须为整数");
                    responsePrint(resp, toJson(result));
                    return;
                }
            }

            if (jsonNode.has("status") && !jsonNode.get("status").isJsonNull()) {
                String status = jsonNode.get("status").getAsString().trim();
                if (!status.isEmpty()) {
                    if (!status.equals("active") && !status.equals("inactive") && !status.equals("archived")) {
                        resp.setStatus(400);
                        result.put("code", 400);
                        result.put("msg", "分类状态值无效，可选值：active、inactive、archived");
                        responsePrint(resp, toJson(result));
                        return;
                    }
                    updateFields.put("status", status);
                }
            }

            if (updateFields.isEmpty()) {
                resp.setStatus(400);
                result.put("code", 400);
                result.put("msg", "没有需要更新的字段");
                responsePrint(resp, toJson(result));
                return;
            }

            if (categoryName != null && isCategoryNameExists(categoryName, parentId, categoryId)) {
                resp.setStatus(409);
                result.put("code", 409);
                result.put("msg", "同一父级分类下名称已存在");
                responsePrint(resp, toJson(result));
                return;
            }

            StringBuilder updateSql = new StringBuilder("UPDATE model_category SET ");
            List<Object> params = new java.util.ArrayList<>();
            int index = 0;
            for (Map.Entry<String, Object> entry : updateFields.entrySet()) {
                if (index > 0) {
                    updateSql.append(", ");
                }
                updateSql.append(entry.getKey()).append(" = ?");
                params.add(entry.getValue());
                index++;
            }
            updateSql.append(" WHERE id = ?");
            params.add(categoryId);

            getMysqlAdapter().executeUpdate(updateSql.toString(), params.toArray());

            resp.setStatus(200);
            result.put("code", 200);
            result.put("msg", "分类修改成功");
            responsePrint(resp, toJson(result));
        } catch (Exception e) {
            log.error("更新模型分类失败: error={}", e.getMessage(), e);
            resp.setStatus(500);
            result.put("code", 500);
            result.put("msg", "服务器内部错误：" + e.getMessage());
            responsePrint(resp, toJson(result));
        }
    }
    private void deletedModelCategory(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            Long categoryId = null;

            String jsonBody = requestToJson(req);
            if (jsonBody != null && !jsonBody.trim().isEmpty()) {
                JsonObject jsonNode = gson.fromJson(jsonBody, JsonObject.class);
                if (jsonNode != null && jsonNode.has("id") && !jsonNode.get("id").isJsonNull()) {
                    try {
                        categoryId = jsonNode.get("id").getAsLong();
                    } catch (Exception e) {
                        resp.setStatus(400);
                        result.put("code", 400);
                        result.put("msg", "分类ID必须为数字");
                        responsePrint(resp, toJson(result));
                        return;
                    }
                }
            }

            if (categoryId == null) {
                String idParam = req.getParameter("id");
                if (idParam == null || idParam.trim().isEmpty()) {
                    resp.setStatus(400);
                    result.put("code", 400);
                    result.put("msg", "分类ID不能为空");
                    responsePrint(resp, toJson(result));
                    return;
                }
                try {
                    categoryId = Long.parseLong(idParam.trim());
                } catch (NumberFormatException e) {
                    resp.setStatus(400);
                    result.put("code", 400);
                    result.put("msg", "分类ID必须为数字");
                    responsePrint(resp, toJson(result));
                    return;
                }
            }

            Map<String, Object> categoryRow = getCategoryById(categoryId);
            if (categoryRow == null || categoryRow.isEmpty()) {
                resp.setStatus(404);
                result.put("code", 404);
                result.put("msg", "分类不存在或已删除");
                responsePrint(resp, toJson(result));
                return;
            }

            String deletedSql = "UPDATE model_category SET is_deleted = 1 WHERE id = ?";
            getMysqlAdapter().executeUpdate(deletedSql, categoryId);

            resp.setStatus(200);
            result.put("code", 200);
            result.put("msg", "分类删除成功");
            responsePrint(resp, toJson(result));
        } catch (Exception e) {
            log.error("删除模型分类失败: error={}", e.getMessage(), e);
            resp.setStatus(500);
            result.put("code", 500);
            result.put("msg", "服务器内部错误：" + e.getMessage());
            responsePrint(resp, toJson(result));
        }
    }


}
