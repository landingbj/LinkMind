package ai.servlet.api;

import ai.database.impl.MysqlAdapter;
import ai.dto.DictOptionDto;
import ai.dto.VersionManagementDto;
import ai.servlet.BaseServlet;
import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型版本管理与子目录管理
 * 对应接口：
 * - POST   /api/model/versionManagement/save     新增 / 编辑版本
 * - GET    /api/model/versionManagement/list     版本列表
 * - GET    /api/model/versionManagement/detail   版本详情
 * - POST   /api/model/versionManagement/deleted  删除版本
 */
@Slf4j
public class VersionManagementServlet extends BaseServlet {

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

        if (method.equals("save")) {
            handleSaveVersionManagement(req, resp);
        } else if (method.equals("list")) {
            handleListVersionManagement(req, resp);
        } else if (method.equals("detail")){
            handleDetailVersionManagement(req, resp);
        }else if (method.equals("deleted")) {
            handleDeletedVersionManagement(req, resp);
        }else if (method.equals("queryModel")){
            handleQueryModel(req, resp);
        } else if (method.equals("queryFramework")) {
            handleQueryFramework(req, resp);
        } else {
            resp.setStatus(404);
            Map<String, Object> result = new HashMap<>();
            result.put("code", 404);
            result.put("message", "接口不存在");
            responsePrint(resp, toJson(result));
        }
    }


    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doGet(req, resp);
    }


    private void handleQueryFramework(HttpServletRequest req, HttpServletResponse resp) throws  IOException{
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

    private void handleQueryModel(HttpServletRequest req, HttpServletResponse resp) throws  IOException{
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            List<DictOptionDto> options = new ArrayList<>();
            String sql = "SELECT id, model_name FROM model_introduction";
            List<Map<String, Object>> modellist = getMysqlAdapter().select(sql);

            for (Map<String, Object> map : modellist) {
                DictOptionDto option = new DictOptionDto();
                Object idObj = map.get("id");
                Long id = null;
                if (idObj != null) {
                    // 通用转换方式：先转成Number，再获取long值（兼容Integer/BigInteger/Long等数值类型）
                    id = ((Number) idObj).longValue();
                }
                option.setId(id);
                option.setName((String) map.get("model_name"));
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


    private void handleDeletedVersionManagement(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            String body = requestToJson(req);
            JsonObject jsonObject = gson.fromJson(body, JsonObject.class);

            if (jsonObject == null || !jsonObject.has("versionIds") || jsonObject.get("versionIds").isJsonNull()) {
                resp.setStatus(400);
                result.put("code", 400);
                result.put("message", "versionIds不能为空"); // 根据文档返回 message
                responsePrint(resp, toJson(result));
                return;
            }

            JsonArray versionIdsArray = jsonObject.getAsJsonArray("versionIds");
            if (versionIdsArray == null || versionIdsArray.size() == 0) {
                resp.setStatus(400);
                result.put("code", 400);
                result.put("message", "versionIds不能为空数组");
                responsePrint(resp, toJson(result));
                return;
            }
            // 检查所有版本是否存在
            List<String> notExistIds = new ArrayList<>();
            for (int i = 0; i < versionIdsArray.size(); i++) {
                String versionId = versionIdsArray.get(i).getAsString().trim();
                Map<String, Object> dbRecord = getVersionManagementFromDB(versionId);
                if (dbRecord == null) {
                    notExistIds.add(versionId);
                }
            }

            // 如果有不存在的版本，直接返回错误
            if (!notExistIds.isEmpty()) {
                resp.setStatus(404);
                result.put("code", 404);
                result.put("message", "以下版本不存在: " + String.join(", ", notExistIds));
                responsePrint(resp, toJson(result));
                return;
            }

            // 构建批量删除的SQL
            StringBuilder sqlBuilder = new StringBuilder("UPDATE model_version SET is_deleted = 1, update_at = ? WHERE version_id IN (");

            // 构建占位符
            for (int i = 0; i < versionIdsArray.size(); i++) {
                sqlBuilder.append("?");
                if (i < versionIdsArray.size() - 1) {
                    sqlBuilder.append(", ");
                }
            }
            sqlBuilder.append(")");

            String sql = sqlBuilder.toString();
            currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            // 构建参数列表：update_at + 所有versionId
            List<Object> params = new ArrayList<>();
            params.add(currentTime);
            for (int i = 0; i < versionIdsArray.size(); i++) {
                params.add(versionIdsArray.get(i).getAsString().trim());
            }

            // 执行批量删除
            int rowsAffected = getMysqlAdapter().executeUpdate(sql, params.toArray());

            if (rowsAffected > 0) {
                result.put("code", 200);
                result.put("message", "删除成功");
                Map<String, Object> data = new HashMap<>();
                data.put("deleted_count", rowsAffected);
                result.put("data", data);
            } else {
                resp.setStatus(500);
                result.put("code", 500);
                result.put("message", "版本删除失败");
            }

            responsePrint(resp, toJson(result));

        } catch (JsonSyntaxException e) {
            log.error("JSON解析异常: ", e);
            resp.setStatus(400);
            result.put("code", 400);
            result.put("message", "请求参数格式错误");
            responsePrint(resp, toJson(result));
        } catch (Exception e) {
            log.error("删除版本异常: ", e);
            resp.setStatus(500);
            result.put("code", 500);
            result.put("message", "服务器内部错误");
            responsePrint(resp, toJson(result));
        }
    }

    private void handleDetailVersionManagement(HttpServletRequest req, HttpServletResponse resp) {


    }

    private void handleListVersionManagement(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            // 获取分页参数
            int page = 1;
            String pageParam = req.getParameter("page");
            if (pageParam != null && !pageParam.trim().isEmpty()) {
                try {
                    page = Integer.parseInt(pageParam.trim());
                    if (page <= 0) {
                        page = 1;
                    }
                } catch (NumberFormatException e) {
                    log.warn("page参数解析失败，使用默认值1", e);
                    page = 1;
                }
            }

            int pageSize = 10;
            String pageSizeParam = req.getParameter("page_size");
            if (pageSizeParam != null && !pageSizeParam.trim().isEmpty()) {
                try {
                    pageSize = Integer.parseInt(pageSizeParam.trim());
                    if (pageSize <= 0 || pageSize > 100) {
                        pageSize = 10;
                    }
                } catch (NumberFormatException e) {
                    log.warn("page_size参数解析失败，使用默认值10", e);
                    pageSize = 10;
                }
            }

            // 查询统计信息
            Map<String, Object> stat = queryVersionStatistics();

            // 查询版本列表
            int offset = (page - 1) * pageSize;
            List<Map<String, Object>> versionList = queryVersionList(offset, pageSize);

            // 构建返回数据
            Map<String, Object> data = new HashMap<>();
            data.put("stat", stat);
            data.put("list", versionList);

            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", data);
            resp.setStatus(200);
            responsePrint(resp, toJson(result));

        } catch (Exception e) {
            log.error("查询版本列表异常: ", e);
            resp.setStatus(500);
            result.put("code", 500);
            result.put("message", "服务器内部错误");
            responsePrint(resp, toJson(result));
        }
    }

    /**
     * 查询版本统计信息
     */
    private Map<String, Object> queryVersionStatistics() {
        Map<String, Object> stat = new HashMap<>();
        try {
            // 总版本数（未删除的）
            String totalVersionSql = "SELECT COUNT(*) AS cnt FROM model_version WHERE is_deleted = 0";
            List<Map<String, Object>> totalVersionResult = getMysqlAdapter().select(totalVersionSql);
            long totalVersion = 0;
            if (totalVersionResult != null && !totalVersionResult.isEmpty() && totalVersionResult.get(0).get("cnt") != null) {
                totalVersion = ((Number) totalVersionResult.get(0).get("cnt")).longValue();
            }
            stat.put("totalVersion", totalVersion);

            // 活跃版本数（status = 'enabled' 或 is_deleted = 0 且 status 不为 'disabled'）
            String activeVersionSql = "SELECT COUNT(*) AS cnt FROM model_version WHERE is_deleted = 0 AND (status = 'enabled' OR status IS NULL OR status = '')";
            List<Map<String, Object>> activeVersionResult = getMysqlAdapter().select(activeVersionSql);
            long activeVersion = 0;
            if (activeVersionResult != null && !activeVersionResult.isEmpty() && activeVersionResult.get(0).get("cnt") != null) {
                activeVersion = ((Number) activeVersionResult.get(0).get("cnt")).longValue();
            }
            stat.put("activeVersion", activeVersion);

            // TODO 文件总数


            // 平均准确率
            String avgAccuracySql = "SELECT AVG(accuracy_rate) AS avg_acc FROM model_version WHERE is_deleted = 0 AND accuracy_rate IS NOT NULL";
            List<Map<String, Object>> avgAccuracyResult = getMysqlAdapter().select(avgAccuracySql);
            String avgAccuracy = "0%";
            if (avgAccuracyResult != null && !avgAccuracyResult.isEmpty() && avgAccuracyResult.get(0).get("avg_acc") != null) {
                Object avgAccObj = avgAccuracyResult.get(0).get("avg_acc");
                if (avgAccObj != null) {
                    double avgAcc = ((Number) avgAccObj).doubleValue();
                    avgAccuracy = String.format("%.2f%%", avgAcc * 100);
                }
            }
            stat.put("avgAccuracy", avgAccuracy);

        } catch (Exception e) {
            log.error("查询版本统计信息异常: ", e);
            stat.put("totalVersion", 0);
            stat.put("activeVersion", 0);
            stat.put("totalFile", 0);
            stat.put("avgAccuracy", "0%");
        }
        return stat;
    }

    /**
     * 查询版本列表（分页）
     */
    private List<Map<String, Object>> queryVersionList(int offset, int pageSize) {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            // 关联查询版本信息、模型信息、分类信息、框架信息
            // 使用 COALESCE 处理可能为 NULL 的字段
            String sql = "SELECT " +
                    "mv.version_id, " +
                    "mv.version_name, " +
                    "mv.version_number, " +
                    "COALESCE(mi.model_name, '') AS model_name, " +
                    "COALESCE(mc.category_name, '') AS category_name, " +
                    "COALESCE(mfd.framework_name, '') AS framework_name, " +
                    "COALESCE(mv.status, 'enabled') AS status, " +
                    "mv.accuracy_rate, " +
                    "mv.create_at, " +
                    "COALESCE(mv.creator, '') AS creator " +
                    "FROM model_version mv " +
                    "LEFT JOIN model_introduction mi ON mv.model_introduction_id = mi.id " +
                    "LEFT JOIN model_category mc ON mi.category_id = mc.id " +
                    "LEFT JOIN model_framework_dict mfd ON mv.framework_id = mfd.id " +
                    "WHERE mv.is_deleted = 0 " +
                    "ORDER BY mv.create_at DESC " +
                    "LIMIT ? OFFSET ?";

            List<Map<String, Object>> rows = getMysqlAdapter().select(sql, pageSize, offset);

            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("versionId", row.get("version_id"));
                    item.put("versionName", row.get("version_name"));
                    item.put("versionNumber", row.get("version_number"));
                    item.put("modelName", row.get("model_name") != null ? row.get("model_name") : "");
                    item.put("category", row.get("category_name") != null ? row.get("category_name") : "");
                    item.put("framework", row.get("framework_name") != null ? row.get("framework_name") : "");

                    // 处理状态：如果为 null 或空，默认为 enabled
                    Object statusObj = row.get("status");
                    String status = "enabled";
                    if (statusObj != null && !statusObj.toString().trim().isEmpty()) {
                        status = statusObj.toString().trim();
                    }
                    item.put("status", status);

                    // 处理准确率：转换为百分比字符串
                    Object accuracyRateObj = row.get("accuracy_rate");
                    String accuracy = "0%";
                    if (accuracyRateObj != null) {
                        double accuracyRate = ((Number) accuracyRateObj).doubleValue();
                        accuracy = String.format("%.0f%%", accuracyRate * 100);
                    }
                    item.put("accuracy", accuracy);

                    // TODO 文件大小和子目录数暂时返回默认值
                    //item.put("fileSize", );
                    //item.put("subDirCount", 0);

                    // 创建者
                    item.put("creator", row.get("creator") != null ? row.get("creator") : "");

                    // 处理创建时间：格式化为 yyyy/MM/dd HH:mm:ss
                    Object createAtObj = row.get("create_at");
                    String createTime = "";
                    if (createAtObj != null) {
                        String createAtStr = createAtObj.toString().trim();
                        // 如果已经是正确格式（包含/），直接使用
                        if (createAtStr.contains("/")) {
                            createTime = createAtStr;
                        } else {
                            // 尝试从数据库格式（yyyy-MM-dd HH:mm:ss）转换为目标格式
                            try {
                                // 标准化输入字符串：移除T，截取前19个字符
                                String normalized = createAtStr.replace("T", " ");
                                if (normalized.length() > 19) {
                                    normalized = normalized.substring(0, 19);
                                }
                                LocalDateTime dateTime = LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                                createTime = dateTime.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"));
                            } catch (Exception e) {
                                // 如果解析失败，尝试简单替换
                                createTime = createAtStr.replace("-", "/");
                            }
                        }
                    }
                    item.put("createTime", createTime);

                    list.add(item);
                }
            }
        } catch (Exception e) {
            log.error("查询版本列表异常: ", e);
        }
        return list;
    }

    private void handleSaveVersionManagement(HttpServletRequest req, HttpServletResponse resp) {

        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            String body = requestToJson(req);
            JsonObject jsonNode = gson.fromJson(body, JsonObject.class);
            if (jsonNode == null) {
                resp.setStatus(400);
                result.put("code", 400);
                result.put("message", "请求体不能为空");
                responsePrint(resp, toJson(result));
                return;
            }
            boolean isUpdate = jsonNode.has("version_id") && !jsonNode.get("version_id").isJsonNull()
                    && !jsonNode.get("version_id").getAsString().trim().isEmpty();
            if (isUpdate){
                // 修改
                String versionId = jsonNode.get("version_id").getAsString().trim();
                Map<String, Object> dbRecord = getVersionManagementFromDB(versionId);

                if (dbRecord == null) {
                    resp.setStatus(404);
                    result.put("code", 404);
                    result.put("msg", "版本不存在");
                    responsePrint(resp, toJson(result));
                    return;
                }
                // 1. 收集需要更新的字段和参数
                StringBuilder sqlBuilder = new StringBuilder();
                List<Object> params = new ArrayList<>();
                // 初始化SQL前缀
                sqlBuilder.append("update model_version set update_at = ?");
                params.add(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))); // 先加更新时间

                // 2. 逐个判断字段是否传入，动态拼接SQL和参数
                if (jsonNode.has("version_name") && !jsonNode.get("version_name").isJsonNull()) {
                    sqlBuilder.append(", version_name = ?");
                    params.add(jsonNode.get("version_name").getAsString().trim());
                }
                if (jsonNode.has("version_number") && !jsonNode.get("version_number").isJsonNull()) {
                    sqlBuilder.append(", version_number = ?");
                    params.add(jsonNode.get("version_number").getAsString().trim());
                }
                if (jsonNode.has("model_introduction_id") && !jsonNode.get("model_introduction_id").isJsonNull()) {
                    sqlBuilder.append(", model_introduction_id = ?");
                    params.add(jsonNode.get("model_introduction_id").getAsInt());
                }
                if (jsonNode.has("framework_id") && !jsonNode.get("framework_id").isJsonNull()) {
                    sqlBuilder.append(", framework_id = ?");
                    params.add(jsonNode.get("framework_id").getAsInt());
                }
                if (jsonNode.has("accuracy_rate") && !jsonNode.get("accuracy_rate").isJsonNull()) {
                    sqlBuilder.append(", accuracy_rate = ?");
                    params.add(jsonNode.get("accuracy_rate").getAsFloat());
                }
                if (jsonNode.has("version_description") && !jsonNode.get("version_description").isJsonNull()) {
                    sqlBuilder.append(", version_description = ?");
                    params.add(jsonNode.get("version_description").getAsString().trim());
                }
                if (jsonNode.has("tags") && !jsonNode.get("tags").isJsonNull()) {
                    sqlBuilder.append(", tags = ?");
                    params.add(jsonNode.get("tags").getAsString().trim());
                }

                // 3. 拼接where条件，添加version_id参数
                sqlBuilder.append(" where version_id = ?");
                params.add(versionId);


                // 执行数据库更新
                int rowsAffected = updateVersionManagementInDB(sqlBuilder.toString(), params);

                // 返回更新结果
                if (rowsAffected > 0) {
                    result.put("code", 200);
                    result.put("msg", "版本更新成功");
                    Map<String, Object> data = new HashMap<>();
                    data.put("version_id", versionId);
                    result.put("data", data);
                } else {
                    resp.setStatus(500);
                    result.put("code", 500);
                    result.put("msg", "版本更新失败");
                }
                responsePrint(resp, toJson(result));
            }else {
                // 新增
                String versionId = UUID.randomUUID().toString();
                if (!jsonNode.has("version_name") || jsonNode.get("version_name").isJsonNull() ||
                        jsonNode.get("version_name").getAsString().trim().isEmpty()) {
                    resp.setStatus(400);
                    result.put("code", 400);
                    result.put("msg", "版本名称不能为空");
                    responsePrint(resp, toJson(result));
                    return;
                }
                if (!jsonNode.has("version_number") || jsonNode.get("version_number").isJsonNull() ||
                        jsonNode.get("version_number").getAsString().trim().isEmpty()) {
                    resp.setStatus(400);
                    result.put("code", 400);
                    result.put("msg", "版本号不能为空");
                    responsePrint(resp, toJson(result));
                    return;
                }
                if (!jsonNode.has("model_introduction_id") || jsonNode.get("model_introduction_id").isJsonNull() ||
                        jsonNode.get("model_introduction_id").getAsString().trim().isEmpty()) {
                    resp.setStatus(400);
                    result.put("code", 400);
                    result.put("msg", "所属模型不能为空");
                    responsePrint(resp, toJson(result));
                    return;
                }
                if (!jsonNode.has("framework_id") || jsonNode.get("framework_id").isJsonNull() ||
                        jsonNode.get("framework_id").getAsString().trim().isEmpty()) {
                    resp.setStatus(400);
                    result.put("code", 400);
                    result.put("msg", "所属模型不能为空");
                    responsePrint(resp, toJson(result));
                    return;
                }
                if (!jsonNode.has("version_description") || jsonNode.get("version_description").isJsonNull() ||
                        jsonNode.get("version_description").getAsString().trim().isEmpty()) {
                    resp.setStatus(400);
                    result.put("code", 400);
                    result.put("msg", "版本描述不能为空");
                    responsePrint(resp, toJson(result));
                    return;
                }
                VersionManagementDto versionManagementDto = new VersionManagementDto();
                versionManagementDto.setVersionId(versionId);
                versionManagementDto.setVersionName(jsonNode.get("version_name").getAsString());
                versionManagementDto.setVersionNumber(jsonNode.get("version_number").getAsString());
                versionManagementDto.setModelIntroductionId(jsonNode.get("model_introduction_id").getAsInt());
                versionManagementDto.setFrameworkId(jsonNode.get("framework_id").getAsInt());
               versionManagementDto.setAccuracyRate(jsonNode.has("accuracy_rate") && !jsonNode.get("accuracy_rate").isJsonNull() ?jsonNode.get("accuracy_rate").getAsFloat():0.0f);
                versionManagementDto.setVersionDescription(jsonNode.get("version_description").getAsString());
                versionManagementDto.setTags(jsonNode.has("tags") && !jsonNode.get("tags").isJsonNull() ?
                        jsonNode.get("tags").getAsString() : "");
                int rowsAffected = saveVersionManagementToDB(versionManagementDto);

                if (rowsAffected > 0) {
                    result.put("code", 200);
                    result.put("msg", "版本保存成功");
                    Map<String, Object> data = new HashMap<>();
                    data.put("version_id", versionId);
                    result.put("data", data);
                    responsePrint(resp, toJson(result));
                } else {
                    resp.setStatus(500);
                    result.put("code", 500);
                    result.put("msg", "版本保存失败");
                    responsePrint(resp, toJson(result));
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int updateVersionManagementInDB(String dynamicSql, List<Object> params) {
        try {
            // 执行动态SQL，参数列表已按顺序组装
            int rowsAffected = getMysqlAdapter().executeUpdate(dynamicSql, params.toArray());
            return rowsAffected; // 返回实际影响行数
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    private Map<String, Object> getVersionManagementFromDB(String versionId) {
        String sql = "select * from model_version where version_id = ?";
        List<Map<String, Object>> modelVersionList = getMysqlAdapter().select(sql, versionId);
        if (modelVersionList == null || modelVersionList.isEmpty()) {
            return null;
        }
        Map<String, Object> recordMap = modelVersionList.get(0);
        return recordMap;
    }

    private int saveVersionManagementToDB(VersionManagementDto versionManagementDto) {
        currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        try {
            String sql = "insert into model_version(version_id, version_name, version_number, model_introduction_id, framework_id, accuracy_rate, version_description, tags, create_at, is_deleted) values(?,?,?,?,?,?,?,?,?,?)";
            int rowsAffected =getMysqlAdapter().executeUpdate(sql,
                    versionManagementDto.getVersionId(),
                    versionManagementDto.getVersionName(),
                    versionManagementDto.getVersionNumber(),
                    versionManagementDto.getModelIntroductionId(),
                    versionManagementDto.getFrameworkId(),
                    versionManagementDto.getAccuracyRate(),
                    versionManagementDto.getVersionDescription(),
                    versionManagementDto.getTags(),
                    currentTime,
                    0
                    );
            return rowsAffected;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }



}
