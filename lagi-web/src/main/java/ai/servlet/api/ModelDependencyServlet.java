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
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.text.SimpleDateFormat;

@Slf4j
public class ModelDependencyServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();
    private String currentTime;

    private static volatile MysqlAdapter mysqlAdapter = null;
    private static MysqlAdapter getMysqlAdapter() {
        if (mysqlAdapter == null) {
            synchronized (ModelDependencyServlet.class) {
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

        currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        log.info("ModelDependencyServlet method: {}", method);

        if (method.equals("statistics")) {
            getStatistics(req, resp);
        } else if (method.equals("modelDependencies")) {
            getModelDependencies(req, resp);
        } else if (method.equals("dependencyFiles")) {
            getDependencyFiles(req, resp);
        } else if (method.equals("dependencyPackages")) {
            getDependencyPackages(req, resp);
        } else if (method.equals("searchPackages")) {
            searchDependencyPackages(req, resp);
        } else if (method.equals("uploadFile")) {
            uploadDependencyFile(req, resp);
        } else if (method.equals("deleteFile")) {
            deleteDependencyFile(req, resp);
        } else if (method.equals("updatePackageStatus")) {
            updatePackageStatus(req, resp);
        } else {
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

    /**
     * 获取统计概览数据
     * 返回：模型总数、依赖文件总数、依赖包总数、待更新依赖包数量
     */
    private void getStatistics(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> statistics = queryStatistics();
            result.put("code", 200);
            result.put("message", "success");
            result.put("data", statistics);
        } catch (Exception e) {
            log.error("获取统计数据失败", e);
            result.put("code", 500);
            result.put("message", "获取统计数据失败: " + e.getMessage());
        }
        responsePrint(resp, toJson(result));
    }

    /**
     * 获取指定模型的所有依赖文件
     */
    private void getModelDependencies(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            String modelIdStr = req.getParameter("modelId");
            if (modelIdStr == null || modelIdStr.trim().isEmpty()) {
                result.put("code", 400);
                result.put("message", "缺少modelId参数");
                responsePrint(resp, toJson(result));
                return;
            }

            long modelId = Long.parseLong(modelIdStr);

            // 分页参数
            int page = 1;
            int pageSize = 20;
            String pageParam = req.getParameter("page");
            String pageSizeParam = req.getParameter("pageSize");

            if (pageParam != null && !pageParam.trim().isEmpty()) {
                try {
                    page = Integer.parseInt(pageParam.trim());
                    if (page < 1) page = 1;
                } catch (NumberFormatException e) {
                    page = 1;
                }
            }

            if (pageSizeParam != null && !pageSizeParam.trim().isEmpty()) {
                try {
                    pageSize = Integer.parseInt(pageSizeParam.trim());
                    if (pageSize < 1) pageSize = 20;
                    if (pageSize > 100) pageSize = 100; // 最大100条
                } catch (NumberFormatException e) {
                    pageSize = 20;
                }
            }

            Map<String, Object> pageResult = queryModelDependenciesPaged(modelId, page, pageSize);
            result.put("code", 200);
            result.put("message", "success");
            result.put("data", pageResult);
        } catch (NumberFormatException e) {
            result.put("code", 400);
            result.put("message", "modelId参数格式错误");
        } catch (Exception e) {
            log.error("获取模型依赖失败", e);
            result.put("code", 500);
            result.put("message", "获取模型依赖失败: " + e.getMessage());
        }
        responsePrint(resp, toJson(result));
    }

    /**
     * 获取依赖文件列表
     */
    private void getDependencyFiles(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            String modelIdStr = req.getParameter("modelId");
            if (modelIdStr == null || modelIdStr.trim().isEmpty()) {
                result.put("code", 400);
                result.put("message", "缺少modelId参数");
                responsePrint(resp, toJson(result));
                return;
            }

            long modelId = Long.parseLong(modelIdStr);

            // 分页参数
            int page = 1;
            int pageSize = 20;
            String pageParam = req.getParameter("page");
            String pageSizeParam = req.getParameter("pageSize");

            if (pageParam != null && !pageParam.trim().isEmpty()) {
                try {
                    page = Integer.parseInt(pageParam.trim());
                    if (page < 1) page = 1;
                } catch (NumberFormatException e) {
                    page = 1;
                }
            }

            if (pageSizeParam != null && !pageSizeParam.trim().isEmpty()) {
                try {
                    pageSize = Integer.parseInt(pageSizeParam.trim());
                    if (pageSize < 1) pageSize = 20;
                    if (pageSize > 100) pageSize = 100; // 最大100条
                } catch (NumberFormatException e) {
                    pageSize = 20;
                }
            }

            Map<String, Object> pageResult = queryDependencyFilesPaged(modelId, page, pageSize);
            result.put("code", 200);
            result.put("message", "success");
            result.put("data", pageResult);
        } catch (NumberFormatException e) {
            result.put("code", 400);
            result.put("message", "modelId参数格式错误");
        } catch (Exception e) {
            log.error("获取依赖文件列表失败", e);
            result.put("code", 500);
            result.put("message", "获取依赖文件列表失败: " + e.getMessage());
        }
        responsePrint(resp, toJson(result));
    }

    /**
     * 获取依赖包列表
     */
    private void getDependencyPackages(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            String fileIdStr = req.getParameter("fileId");
            if (fileIdStr == null || fileIdStr.trim().isEmpty()) {
                result.put("code", 400);
                result.put("message", "缺少fileId参数");
                responsePrint(resp, toJson(result));
                return;
            }

            long fileId = Long.parseLong(fileIdStr);

            // 分页参数
            int page = 1;
            int pageSize = 20;
            String pageParam = req.getParameter("page");
            String pageSizeParam = req.getParameter("pageSize");

            if (pageParam != null && !pageParam.trim().isEmpty()) {
                try {
                    page = Integer.parseInt(pageParam.trim());
                    if (page < 1) page = 1;
                } catch (NumberFormatException e) {
                    page = 1;
                }
            }

            if (pageSizeParam != null && !pageSizeParam.trim().isEmpty()) {
                try {
                    pageSize = Integer.parseInt(pageSizeParam.trim());
                    if (pageSize < 1) pageSize = 20;
                    if (pageSize > 100) pageSize = 100; // 最大100条
                } catch (NumberFormatException e) {
                    pageSize = 20;
                }
            }

            Map<String, Object> pageResult = queryDependencyPackagesPaged(fileId, page, pageSize);
            result.put("code", 200);
            result.put("message", "success");
            result.put("data", pageResult);
        } catch (NumberFormatException e) {
            result.put("code", 400);
            result.put("message", "fileId参数格式错误");
        } catch (Exception e) {
            log.error("获取依赖包列表失败", e);
            result.put("code", 500);
            result.put("message", "获取依赖包列表失败: " + e.getMessage());
        }
        responsePrint(resp, toJson(result));
    }

    /**
     * 搜索依赖包
     */
    private void searchDependencyPackages(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            String keyword = req.getParameter("keyword");
            String modelIdStr = req.getParameter("modelId");

            if (keyword == null || keyword.trim().isEmpty()) {
                result.put("code", 400);
                result.put("message", "缺少keyword参数");
                responsePrint(resp, toJson(result));
                return;
            }

            Long modelId = null;
            if (modelIdStr != null && !modelIdStr.trim().isEmpty()) {
                modelId = Long.parseLong(modelIdStr);
            }

            // 分页参数
            int page = 1;
            int pageSize = 20;
            String pageParam = req.getParameter("page");
            String pageSizeParam = req.getParameter("pageSize");

            if (pageParam != null && !pageParam.trim().isEmpty()) {
                try {
                    page = Integer.parseInt(pageParam.trim());
                    if (page < 1) page = 1;
                } catch (NumberFormatException e) {
                    page = 1;
                }
            }

            if (pageSizeParam != null && !pageSizeParam.trim().isEmpty()) {
                try {
                    pageSize = Integer.parseInt(pageSizeParam.trim());
                    if (pageSize < 1) pageSize = 20;
                    if (pageSize > 100) pageSize = 100; // 最大100条
                } catch (NumberFormatException e) {
                    pageSize = 20;
                }
            }

            Map<String, Object> pageResult = searchPackagesPaged(keyword, modelId, page, pageSize);
            result.put("code", 200);
            result.put("message", "success");
            result.put("data", pageResult);
        } catch (NumberFormatException e) {
            result.put("code", 400);
            result.put("message", "modelId参数格式错误");
        } catch (Exception e) {
            log.error("搜索依赖包失败", e);
            result.put("code", 500);
            result.put("message", "搜索依赖包失败: " + e.getMessage());
        }
        responsePrint(resp, toJson(result));
    }

    /**
     * 上传依赖文件
     * 支持通过JSON指定文件位置信息
     */
    private void uploadDependencyFile(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            // 从请求体获取JSON参数
            String body = getRequestBody(req);
            JsonObject jsonObject = gson.fromJson(body, JsonObject.class);

            // 必填参数校验
            if (!jsonObject.has("modelId") || jsonObject.get("modelId").isJsonNull()) {
                result.put("code", 400);
                result.put("message", "缺少modelId参数");
                responsePrint(resp, toJson(result));
                return;
            }

            if (!jsonObject.has("fileName") || jsonObject.get("fileName").isJsonNull()) {
                result.put("code", 400);
                result.put("message", "缺少fileName参数");
                responsePrint(resp, toJson(result));
                return;
            }

            // 可选参数处理
            String fileType = "txt"; // 默认文件类型
            if (jsonObject.has("fileType") && !jsonObject.get("fileType").isJsonNull()) {
                fileType = jsonObject.get("fileType").getAsString();
            }

            String filePath = null; // 文件路径可以为空，由系统生成
            if (jsonObject.has("filePath") && !jsonObject.get("filePath").isJsonNull()) {
                filePath = jsonObject.get("filePath").getAsString();
            }

            // 如果没有指定filePath，则根据modelId和fileName生成默认路径
            if (filePath == null || filePath.trim().isEmpty()) {
                long modelId = jsonObject.get("modelId").getAsLong();
                String fileName = jsonObject.get("fileName").getAsString();
                filePath = generateDefaultFilePath(modelId, fileName);
            }

            long modelId = jsonObject.get("modelId").getAsLong();
            String fileName = jsonObject.get("fileName").getAsString();

            // 保存到数据库
            long fileId = saveDependencyFile(modelId, fileName, fileType, filePath);

            result.put("code", 200);
            result.put("message", "success");
            Map<String, Object> data = new HashMap<>();
            data.put("fileId", fileId);
            data.put("filePath", filePath);
            result.put("data", data);
        } catch (Exception e) {
            log.error("上传依赖文件失败", e);
            result.put("code", 500);
            result.put("message", "上传依赖文件失败: " + e.getMessage());
        }
        responsePrint(resp, toJson(result));
    }

    /**
     * 删除依赖文件
     */
    private void deleteDependencyFile(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            String fileIdStr = req.getParameter("fileId");
            if (fileIdStr == null || fileIdStr.trim().isEmpty()) {
                result.put("code", 400);
                result.put("message", "缺少fileId参数");
                responsePrint(resp, toJson(result));
                return;
            }

            long fileId = Long.parseLong(fileIdStr);

            // 删除依赖文件（级联删除依赖包）
            boolean success = deleteDependencyFile(fileId);

            if (success) {
                result.put("code", 200);
                result.put("message", "success");
            } else {
                result.put("code", 404);
                result.put("message", "依赖文件不存在或已删除");
            }
        } catch (NumberFormatException e) {
            result.put("code", 400);
            result.put("message", "fileId参数格式错误");
        } catch (Exception e) {
            log.error("删除依赖文件失败", e);
            result.put("code", 500);
            result.put("message", "删除依赖文件失败: " + e.getMessage());
        }
        responsePrint(resp, toJson(result));
    }

    /**
     * 更新依赖包状态
     */
    private void updatePackageStatus(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            String body = getRequestBody(req);
            JsonObject jsonObject = gson.fromJson(body, JsonObject.class);

            if (!jsonObject.has("packageId") || jsonObject.get("packageId").isJsonNull()) {
                result.put("code", 400);
                result.put("message", "缺少packageId参数");
                responsePrint(resp, toJson(result));
                return;
            }

            if (!jsonObject.has("hasUpdate") || jsonObject.get("hasUpdate").isJsonNull()) {
                result.put("code", 400);
                result.put("message", "缺少hasUpdate参数");
                responsePrint(resp, toJson(result));
                return;
            }

            long packageId = jsonObject.get("packageId").getAsLong();
            String hasUpdate = jsonObject.get("hasUpdate").getAsString();

            // 更新依赖包状态
            boolean success = updateDependencyPackageStatus(packageId, hasUpdate);

            if (success) {
                result.put("code", 200);
                result.put("message", "success");
            } else {
                result.put("code", 404);
                result.put("message", "依赖包不存在");
            }
        } catch (Exception e) {
            log.error("更新依赖包状态失败", e);
            result.put("code", 500);
            result.put("message", "更新依赖包状态失败: " + e.getMessage());
        }
        responsePrint(resp, toJson(result));
    }

    // ========== 数据查询方法 ==========

    /**
     * 查询统计概览数据
     */
    private Map<String, Object> queryStatistics() {
        String sql = "SELECT " +
                "COUNT(DISTINCT mi.id) as model_count, " +
                "COUNT(DISTINCT df.id) as file_count, " +
                "COUNT(DISTINCT dp.id) as package_count, " +
                "COUNT(DISTINCT CASE WHEN dp.has_update = '是' THEN dp.id END) as pending_update_count " +
                "FROM model_introduction mi " +
                "LEFT JOIN dependency_file df ON mi.id = df.model_id " +
                "LEFT JOIN dependency_package dp ON df.id = dp.file_id " +
                "WHERE mi.is_deleted = 0";

        List<Map<String, Object>> rows = getMysqlAdapter().select(sql);
        Map<String, Object> result = new HashMap<>();
        if (rows.isEmpty()) {
            result.put("modelCount", 0);
            result.put("fileCount", 0);
            result.put("packageCount", 0);
            result.put("pendingUpdateCount", 0);
        } else {
            Map<String, Object> row = rows.get(0);
            result.put("modelCount", row.get("model_count"));
            result.put("fileCount", row.get("file_count"));
            result.put("packageCount", row.get("package_count"));
            result.put("pendingUpdateCount", row.get("pending_update_count"));
        }
        return result;
    }

    /**
     * 查询模型的依赖文件列表（分页）
     */
    private Map<String, Object> queryModelDependenciesPaged(long modelId, int page, int pageSize) {
        // 计算偏移量
        int offset = (page - 1) * pageSize;

        // 查询总数
        String countSql = "SELECT COUNT(*) as total FROM dependency_file WHERE model_id = ?";
        List<Map<String, Object>> countResult = getMysqlAdapter().select(countSql, modelId);
        long total = 0;
        if (!countResult.isEmpty()) {
            total = ((Number) countResult.get(0).get("total")).longValue();
        }

        // 查询分页数据
        String sql = "SELECT df.id, df.file_name, df.file_type, df.file_path, df.upload_time, " +
                "COUNT(dp.id) as package_count " +
                "FROM dependency_file df " +
                "LEFT JOIN dependency_package dp ON df.id = dp.file_id " +
                "WHERE df.model_id = ? " +
                "GROUP BY df.id, df.file_name, df.file_type, df.file_path, df.upload_time " +
                "ORDER BY df.upload_time DESC " +
                "LIMIT ? OFFSET ?";

        List<Map<String, Object>> data = getMysqlAdapter().select(sql, modelId, pageSize, offset);

        // 格式化时间字段
        formatTimeFields(data);

        // 计算总页数
        int totalPages = (int) Math.ceil((double) total / pageSize);

        Map<String, Object> result = new HashMap<>();
        result.put("list", data);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("totalPages", totalPages);

        return result;
    }

    /**
     * 查询模型的依赖文件列表
     */
    private List<Map<String, Object>> queryModelDependencies(long modelId) {
        String sql = "SELECT df.id, df.file_name, df.file_type, df.file_path, df.upload_time, " +
                "COUNT(dp.id) as package_count " +
                "FROM dependency_file df " +
                "LEFT JOIN dependency_package dp ON df.id = dp.file_id " +
                "WHERE df.model_id = ? " +
                "GROUP BY df.id, df.file_name, df.file_type, df.file_path, df.upload_time " +
                "ORDER BY df.upload_time DESC";

        return getMysqlAdapter().select(sql, modelId);
    }

    /**
     * 查询依赖文件列表（详细，分页）
     */
    private Map<String, Object> queryDependencyFilesPaged(long modelId, int page, int pageSize) {
        // 计算偏移量
        int offset = (page - 1) * pageSize;

        // 查询总数
        String countSql = "SELECT COUNT(*) as total FROM dependency_file df " +
                "JOIN model_introduction mi ON df.model_id = mi.id " +
                "WHERE df.model_id = ? AND mi.is_deleted = 0";
        List<Map<String, Object>> countResult = getMysqlAdapter().select(countSql, modelId);
        long total = 0;
        if (!countResult.isEmpty()) {
            total = ((Number) countResult.get(0).get("total")).longValue();
        }

        // 查询分页数据
        String sql = "SELECT df.id, df.file_name, df.file_type, df.file_path, df.upload_time, " +
                "mi.model_name, mi.version " +
                "FROM dependency_file df " +
                "JOIN model_introduction mi ON df.model_id = mi.id " +
                "WHERE df.model_id = ? AND mi.is_deleted = 0 " +
                "ORDER BY df.upload_time DESC " +
                "LIMIT ? OFFSET ?";

        List<Map<String, Object>> data = getMysqlAdapter().select(sql, modelId, pageSize, offset);

        // 格式化时间字段
        formatTimeFields(data);

        // 计算总页数
        int totalPages = (int) Math.ceil((double) total / pageSize);

        Map<String, Object> result = new HashMap<>();
        result.put("list", data);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("totalPages", totalPages);

        return result;
    }

    /**
     * 查询依赖文件列表（详细）
     */
    private List<Map<String, Object>> queryDependencyFiles(long modelId) {
        String sql = "SELECT df.id, df.file_name, df.file_type, df.file_path, df.upload_time, " +
                "mi.model_name, mi.version " +
                "FROM dependency_file df " +
                "JOIN model_introduction mi ON df.model_id = mi.id " +
                "WHERE df.model_id = ? AND mi.is_deleted = 0 " +
                "ORDER BY df.upload_time DESC";

        return getMysqlAdapter().select(sql, modelId);
    }

    /**
     * 查询依赖包列表（分页）
     */
    private Map<String, Object> queryDependencyPackagesPaged(long fileId, int page, int pageSize) {
        // 计算偏移量
        int offset = (page - 1) * pageSize;

        // 查询总数
        String countSql = "SELECT COUNT(*) as total FROM dependency_package dp " +
                "JOIN dependency_file df ON dp.file_id = df.id " +
                "JOIN model_introduction mi ON df.model_id = mi.id " +
                "WHERE dp.file_id = ? AND mi.is_deleted = 0";
        List<Map<String, Object>> countResult = getMysqlAdapter().select(countSql, fileId);
        long total = 0;
        if (!countResult.isEmpty()) {
            total = ((Number) countResult.get(0).get("total")).longValue();
        }

        // 查询分页数据
        String sql = "SELECT dp.id, dp.package_name, dp.version, dp.source_file, dp.description, " +
                "dp.size, dp.has_update, dp.update_time, " +
                "df.file_name, mi.model_name " +
                "FROM dependency_package dp " +
                "JOIN dependency_file df ON dp.file_id = df.id " +
                "JOIN model_introduction mi ON df.model_id = mi.id " +
                "WHERE dp.file_id = ? AND mi.is_deleted = 0 " +
                "ORDER BY dp.package_name ASC " +
                "LIMIT ? OFFSET ?";

        List<Map<String, Object>> data = getMysqlAdapter().select(sql, fileId, pageSize, offset);

        // 格式化时间字段
        formatTimeFields(data);

        // 计算总页数
        int totalPages = (int) Math.ceil((double) total / pageSize);

        Map<String, Object> result = new HashMap<>();
        result.put("list", data);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("totalPages", totalPages);

        return result;
    }

    /**
     * 查询依赖包列表
     */
    private List<Map<String, Object>> queryDependencyPackages(long fileId) {
        String sql = "SELECT dp.id, dp.package_name, dp.version, dp.source_file, dp.description, " +
                "dp.size, dp.has_update, dp.update_time, " +
                "df.file_name, mi.model_name " +
                "FROM dependency_package dp " +
                "JOIN dependency_file df ON dp.file_id = df.id " +
                "JOIN model_introduction mi ON df.model_id = mi.id " +
                "WHERE dp.file_id = ? AND mi.is_deleted = 0 " +
                "ORDER BY dp.package_name ASC";

        return getMysqlAdapter().select(sql, fileId);
    }

    /**
     * 搜索依赖包（分页）
     */
    private Map<String, Object> searchPackagesPaged(String keyword, Long modelId, int page, int pageSize) {
        // 计算偏移量
        int offset = (page - 1) * pageSize;

        // 构建查询条件
        String whereClause = "WHERE dp.package_name LIKE CONCAT('%', ?, '%') AND mi.is_deleted = 0 ";
        List<Object> params = new ArrayList<>();
        params.add(keyword);

        if (modelId != null) {
            whereClause += "AND mi.id = ? ";
            params.add(modelId);
        }

        // 查询总数
        String countSql = "SELECT COUNT(*) as total FROM dependency_package dp " +
                "JOIN dependency_file df ON dp.file_id = df.id " +
                "JOIN model_introduction mi ON df.model_id = mi.id " +
                whereClause;

        List<Map<String, Object>> countResult = getMysqlAdapter().select(countSql, params.toArray());
        long total = 0;
        if (!countResult.isEmpty()) {
            total = ((Number) countResult.get(0).get("total")).longValue();
        }

        // 查询分页数据
        String sql = "SELECT dp.id, dp.package_name, dp.version, dp.source_file, dp.description, " +
                "dp.size, dp.has_update, dp.update_time, " +
                "df.file_name, mi.model_name, mi.version as model_version " +
                "FROM dependency_package dp " +
                "JOIN dependency_file df ON dp.file_id = df.id " +
                "JOIN model_introduction mi ON df.model_id = mi.id " +
                whereClause +
                "ORDER BY dp.package_name ASC " +
                "LIMIT ? OFFSET ?";

        // 添加分页参数
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(pageSize);
        queryParams.add(offset);

        List<Map<String, Object>> data = getMysqlAdapter().select(sql, queryParams.toArray());

        // 格式化时间字段
        formatTimeFields(data);

        // 计算总页数
        int totalPages = (int) Math.ceil((double) total / pageSize);

        Map<String, Object> result = new HashMap<>();
        result.put("list", data);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("totalPages", totalPages);

        return result;
    }

    /**
     * 搜索依赖包
     */
    private List<Map<String, Object>> searchPackages(String keyword, Long modelId) {
        String sql = "SELECT dp.id, dp.package_name, dp.version, dp.source_file, dp.description, " +
                "dp.size, dp.has_update, dp.update_time, " +
                "df.file_name, mi.model_name, mi.version as model_version " +
                "FROM dependency_package dp " +
                "JOIN dependency_file df ON dp.file_id = df.id " +
                "JOIN model_introduction mi ON df.model_id = mi.id " +
                "WHERE dp.package_name LIKE CONCAT('%', ?, '%') AND mi.is_deleted = 0 ";

        List<Object> params = new ArrayList<>();
        params.add(keyword);

        if (modelId != null) {
            sql += "AND mi.id = ? ";
            params.add(modelId);
        }

        sql += "ORDER BY dp.package_name ASC";

        return getMysqlAdapter().select(sql, params.toArray());
    }

    // ========== 数据操作方法 ==========

    /**
     * 保存依赖文件
     */
    private long saveDependencyFile(long modelId, String fileName, String fileType, String filePath) {
        String sql = "INSERT INTO dependency_file (model_id, file_name, file_type, file_path, upload_time) " +
                "VALUES (?, ?, ?, ?, ?)";

        // 执行插入并获取生成的ID
        int generatedId = getMysqlAdapter().executeUpdateGeneratedKeys(sql, modelId, fileName, fileType, filePath, currentTime);

        if (generatedId > 0) {
            return generatedId;
        }

        throw new RuntimeException("保存依赖文件失败");
    }

    /**
     * 删除依赖文件
     */
    private boolean deleteDependencyFile(long fileId) {
        String sql = "DELETE FROM dependency_file WHERE id = ?";
        int rowsAffected = getMysqlAdapter().executeUpdate(sql, fileId);
        return rowsAffected > 0;
    }

    /**
     * 更新依赖包状态
     */
    private boolean updateDependencyPackageStatus(long packageId, String hasUpdate) {
        String sql = "UPDATE dependency_package SET has_update = ?, update_time = ? WHERE id = ?";
        int rowsAffected = getMysqlAdapter().executeUpdate(sql, hasUpdate, currentTime, packageId);
        return rowsAffected > 0;
    }

    /**
     * 获取请求体内容
     */
    private String getRequestBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = req.getReader().readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }

    /**
     * 响应输出
     */
    public void responsePrint(HttpServletResponse resp, String content) throws IOException {
        resp.getWriter().print(content);
    }

    /**
     * 对象转JSON
     */
    public String toJson(Object obj) {
        return gson.toJson(obj);
    }

    /**
     * 生成默认的文件路径
     */
    private String generateDefaultFilePath(long modelId, String fileName) {
        // 格式: /uploads/models/{modelId}/{timestamp}_{fileName}
        String timestamp = String.valueOf(System.currentTimeMillis());
        return String.format("/uploads/models/%d/%s_%s", modelId, timestamp, fileName);
    }

    /**
     * 格式化时间字段，将数据库时间对象转换为字符串
     */

    private void formatTimeFields(List<Map<String, Object>> data) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (Map<String, Object> item : data) {
            if (item.containsKey("upload_time") && item.get("upload_time") != null) {
                Object timeObj = item.get("upload_time");
                String timeStr = timeObj.toString();
                        // 处理 ISO 格式的时间字符串 "2024-01-20T14:20"
                if (timeStr.contains("T")) {
                    try {
                      LocalDateTime localDateTime = LocalDateTime.parse(timeStr.toString());
                      item.put("upload_time", localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                       } catch (Exception e) {
                        item.put("upload_time", timeStr); // 如果解析失败，保持原值
                       }

                }
            }
        }
    }
}
