package ai.servlet.api;

import ai.database.impl.MysqlAdapter;
import ai.finetune.SSHConnectionManager;
import ai.servlet.BaseServlet;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.net.URLEncoder;

/**
 * - K8s挂载目录通过系统属性或环境变量配置，默认值为 /app/mounted/files
 */
@Slf4j
public class FileManagementServlet extends BaseServlet {

    /**
     * K8s挂载目录常量
     * 优先级：系统属性 > 环境变量 > 默认值
     * 可通过以下方式配置：
     * 1. 系统属性：-DK8S_MOUNTED_DIR=/app/mounted/files
     * 2. 环境变量：export K8S_MOUNTED_DIR=/app/mounted/files
     */
    private static final String K8S_MOUNTED_DIR = getK8sMountedDir();

    /**
     * 获取K8s挂载目录路径
     * 优先读取系统属性，其次环境变量，最后使用默认值
     */
    private static String getK8sMountedDir() {
        String dir = System.getProperty("K8S_MOUNTED_DIR");
        if (dir == null || dir.trim().isEmpty()) {
            dir = System.getenv("K8S_MOUNTED_DIR");
        }
        if (dir == null || dir.trim().isEmpty()) {
            dir = "/app/mounted/files";
        }
        return dir.trim();
    }

    /**
     * 将业务路径（full_path）映射为容器内物理路径
     * 例如：/project/test.txt -> /app/mounted/files/project/test.txt
     *
     * @param fullPath 业务层级路径，如：/project/test.txt
     * @return 容器内物理路径
     */
    private String mapToPhysicalPath(String fullPath) {
        if (fullPath == null || fullPath.trim().isEmpty()) {
            throw new IllegalArgumentException("fullPath不能为空");
        }

        // 标准化路径：确保以/开头
        String normalizedPath = fullPath.trim();
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }

        // 拼接K8s挂载目录
        String baseDir = K8S_MOUNTED_DIR.endsWith("/")
                ? K8S_MOUNTED_DIR.substring(0, K8S_MOUNTED_DIR.length() - 1)
                : K8S_MOUNTED_DIR;

        return baseDir + normalizedPath;
    }

    private static volatile MysqlAdapter mysqlAdapter = null;

    private static MysqlAdapter getMysqlAdapter() {
        if (mysqlAdapter == null) {
            synchronized (FileManagementServlet.class) {
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

        if (method.equals("download")) {
            downloadFile(req, resp);
        } else if (method.equals("upload")) {
            uploadFile(req, resp);
        } else if (method.equals("directory")) {
            directoryFile( req, resp);
        } else if (method.equals("createFolder")) {
            createFolder(req, resp);
        } else {
            resp.setStatus(404);
            resp.setContentType("application/json;charset=utf-8");
            Map<String, Object> error = new HashMap<>();
            error.put("code", 404);
            error.put("msg", "接口不存在");
            error.put("data", null);
            responsePrint(resp, toJson(error));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doGet(req, resp);
    }

    /**
     * 文件上传 - K8s容器挂载目录版本
     * 所有文件直接写入K8s挂载的持久化目录
     *
     * @param req
     * @param resp
     * @throws IOException
     */
    private void uploadFile(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();

        try {
            // 检查请求是否为multipart/form-data格式
            if (!ServletFileUpload.isMultipartContent(req)) {
                result.put("code", 400);
                result.put("message", "请求必须是multipart/form-data格式");
                result.put("data", null);
                responsePrint(resp, toJson(result));
                return;
            }

            // 解析multipart请求
            DiskFileItemFactory factory = new DiskFileItemFactory();
            ServletFileUpload upload = new ServletFileUpload(factory);
            // 设置最大文件大小 100MB
            upload.setFileSizeMax(100 * 1024 * 1024);
            upload.setSizeMax(100 * 1024 * 1024);

            @SuppressWarnings("unchecked")
            List<FileItem> items = upload.parseRequest(req);

            String parentIdStr = null;
            String path = null; // 文件路径参数（用于根据路径创建层级关系）
            FileItem fileItem = null;

            // 解析表单字段
            for (FileItem item : items) {
                if (item.isFormField()) {
                    String fieldName = item.getFieldName();
                    String fieldValue = item.getString("UTF-8");

                    if ("parent_id".equals(fieldName)) {
                        parentIdStr = fieldValue;
                    } else if ("path".equals(fieldName)) {
                        path = fieldValue; // 获取文件路径参数
                    }
                } else {
                    // 文件字段
                    if (item.getName() != null && !item.getName().isEmpty()) {
                        fileItem = item;
                    }
                }
            }

            // 验证文件是否存在
            if (fileItem == null) {
                result.put("code", 400);
                result.put("message", "未找到上传的文件");
                result.put("data", null);
                responsePrint(resp, toJson(result));
                return;
            }

            // 获取文件名
            String fileName = fileItem.getName();
            // 处理文件名（去除路径，只保留文件名）
            if (fileName.contains(File.separator) || fileName.contains("/")) {
                fileName = fileName.substring(fileName.lastIndexOf(File.separator) + 1);
                if (fileName.contains("/")) {
                    fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
                }
            }

            long fileSize = fileItem.getSize();

            MysqlAdapter adapter = getMysqlAdapter();

            String fullPath;
            int parentId;

            // 如果提供了path参数，根据路径创建层级关系
            if (path != null && !path.trim().isEmpty()) {
                path = path.trim();
                // 标准化路径：确保以/开头，去除末尾的/
                if (!path.startsWith("/")) {
                    path = "/" + path;
                }
                // 如果path以/结尾，说明是目录路径，需要追加文件名
                if (path.endsWith("/")) {
                    fullPath = path + fileName;
                } else {
                    // path是完整路径，包含文件名
                    fullPath = path;
                }

                // 根据路径创建目录层级，并获取最终的parentId
                parentId = createDirectoryHierarchyByPath(adapter, fullPath);
                if (parentId < 0) {
                    result.put("code", 500);
                    result.put("message", "创建目录层级失败");
                    result.put("data", null);
                    responsePrint(resp, toJson(result));
                    return;
                }
            } else {
                // 使用parent_id方式（兼容旧逻辑）
                // 解析parent_id，默认为0（根目录）
                parentId = 0;
                if (parentIdStr != null && !parentIdStr.trim().isEmpty()) {
                    try {
                        parentId = Integer.parseInt(parentIdStr);
                    } catch (NumberFormatException e) {
                        result.put("code", 400);
                        result.put("message", "parent_id参数格式错误");
                        result.put("data", null);
                        responsePrint(resp, toJson(result));
                        return;
                    }
                }

                // 获取父目录信息（用于构建full_path）
                String parentFullPath = "/";
                if (parentId > 0) {
                    String parentSql = "SELECT full_path FROM file_manager WHERE id = ? AND is_deleted = 0";
                    List<Map<String, Object>> parentList = adapter.select(parentSql, parentId);
                    if (parentList.isEmpty() || parentList.get(0).containsKey("error")) {
                        result.put("code", 404);
                        result.put("message", "父目录不存在");
                        result.put("data", null);
                        responsePrint(resp, toJson(result));
                        return;
                    }
                    parentFullPath = (String) parentList.get(0).get("full_path");
                    if (parentFullPath == null) {
                        parentFullPath = "/";
                    }
                }

                // 构建full_path
                if ("/".equals(parentFullPath)) {
                    fullPath = "/" + fileName;
                } else {
                    fullPath = parentFullPath + "/" + fileName;
                }
            }

            // 检查同名文件是否已存在
            String checkSql = "SELECT id FROM file_manager WHERE parent_id = ? AND name = ? AND is_deleted = 0";
            List<Map<String, Object>> existList = adapter.select(checkSql, parentId, fileName);
            if (!existList.isEmpty() && !existList.get(0).containsKey("error")) {
                result.put("code", 409);
                result.put("message", "文件已存在: " + fileName);
                result.put("data", null);
                responsePrint(resp, toJson(result));
                return;
            }

            // 获取文件扩展名和MIME类型
            String extension = "";
            int lastDotIndex = fileName.lastIndexOf(".");
            if (lastDotIndex > 0) {
                extension = fileName.substring(lastDotIndex);
            }

            String mimeType = getMimeType(fileName);

            // 使用K8s挂载目录路径映射，将full_path转换为容器内物理路径
            String physicalFilePath = mapToPhysicalPath(fullPath);
            log.info("K8s挂载目录文件上传 - full_path: {}, 物理路径: {}", fullPath, physicalFilePath);

            // 创建目标文件对象
            File targetFile = new File(physicalFilePath);
            File parentDir = targetFile.getParentFile();

            // 创建父目录（如果不存在）
            if (parentDir != null && !parentDir.exists()) {
                boolean created = parentDir.mkdirs();
                if (!created) {
                    log.error("创建目录失败: {}", parentDir.getAbsolutePath());
                    result.put("code", 500);
                    result.put("message", "创建目录失败，请检查K8s挂载目录权限: " + parentDir.getAbsolutePath());
                    result.put("data", null);
                    responsePrint(resp, toJson(result));
                    return;
                }
            }

            // 写入文件到K8s挂载目录
            try {
                fileItem.write(targetFile);
            } catch (Exception e) {
                log.error("文件写入失败: {}", physicalFilePath, e);
                result.put("code", 500);
                result.put("message", "文件写入失败，请检查K8s挂载目录权限: " + e.getMessage());
                result.put("data", null);
                responsePrint(resp, toJson(result));
                return;
            }

            // 获取文件大小
            fileSize = targetFile.length();

            // 生成时间戳字符串
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String currentTime = sdf.format(new Date());

            // file_path存储挂载目录下的完整路径（用于数据库记录）
            String dbFilePath = physicalFilePath;

            // 插入数据库（storage_type固定为local）
            String insertSql = "INSERT INTO file_manager (name, type, parent_id, full_path, file_path, " +
                    "file_size, mime_type, extension, storage_type, create_time, update_time, is_deleted) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)";

            int fileId = adapter.executeUpdateGeneratedKeys(insertSql,
                    fileName, "file", parentId, fullPath, dbFilePath,
                    fileSize, mimeType, extension, "local", // 存储类型固定为local
                    currentTime, currentTime);

            if (fileId <= 0) {
                // 如果插入失败，删除已上传的文件
                targetFile.delete();
                result.put("code", 500);
                result.put("message", "文件上传失败：数据库插入错误");
                result.put("data", null);
                responsePrint(resp, toJson(result));
                return;
            }

            // 构建返回数据
            Map<String, Object> data = new HashMap<>();
            data.put("id", fileId);
            data.put("name", fileName);
            data.put("type", "file");
            data.put("parent_id", parentId);
            data.put("full_path", fullPath);
            data.put("file_path", dbFilePath);
            data.put("storage_type", "local");
            data.put("file_size", fileSize);
            data.put("mime_type", mimeType);
            data.put("extension", extension);
            data.put("create_time", currentTime);
            data.put("update_time", currentTime);

            result.put("code", 200);
            result.put("message", "success");
            result.put("data", data);

        } catch (Exception e) {
            log.error("文件上传失败", e);
            result.put("code", 500);
            result.put("message", "服务器内部错误: " + e.getMessage());
            result.put("data", null);
        }

        responsePrint(resp, toJson(result));
    }

    /**
     * 根据文件名获取MIME类型
     */
    private String getMimeType(String fileName) {
        try {
            String mimeType = Files.probeContentType(Paths.get(fileName));
            if (mimeType != null) {
                return mimeType;
            }
        } catch (Exception e) {
            // 忽略异常，使用默认值
        }

        // 根据扩展名返回常见的MIME类型
        String extension = "";
        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex > 0) {
            extension = fileName.substring(lastDotIndex).toLowerCase();
        }

        Map<String, String> mimeTypes = new HashMap<>();
        mimeTypes.put(".txt", "text/plain");
        mimeTypes.put(".pdf", "application/pdf");
        mimeTypes.put(".doc", "application/msword");
        mimeTypes.put(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        mimeTypes.put(".xls", "application/vnd.ms-excel");
        mimeTypes.put(".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        mimeTypes.put(".jpg", "image/jpeg");
        mimeTypes.put(".jpeg", "image/jpeg");
        mimeTypes.put(".png", "image/png");
        mimeTypes.put(".gif", "image/gif");
        mimeTypes.put(".zip", "application/zip");
        mimeTypes.put(".json", "application/json");
        mimeTypes.put(".yaml", "application/x-yaml");
        mimeTypes.put(".yml", "application/x-yaml");
        mimeTypes.put(".csv", "text/csv");

        return mimeTypes.getOrDefault(extension, "application/octet-stream");
    }

    /**
     * 文件下载 - K8s容器挂载目录版本
     * 从K8s挂载的持久化目录直接读取文件
     *
     * @param req
     * @param resp
     * @throws IOException
     */
    private void downloadFile(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> result = new HashMap<>();

        try {
            // 获取请求参数
            Map<String, String> params = getQueryData(req);
            String idStr = params.get("id");
            String path = params.get("path");

            // 参数校验：id或path至少需要提供一个
            if ((idStr == null || idStr.trim().isEmpty()) && (path == null || path.trim().isEmpty())) {
                resp.setStatus(400);
                resp.setContentType("application/json;charset=utf-8");
                result.put("code", 400);
                result.put("msg", "参数错误：id或path至少需要提供一个");
                result.put("data", null);
                responsePrint(resp, toJson(result));
                return;
            }

            MysqlAdapter adapter = getMysqlAdapter();
            List<Map<String, Object>> fileList;

            // 根据id或path查询文件
            if (idStr != null && !idStr.trim().isEmpty()) {
                try {
                    int id = Integer.parseInt(idStr);
                    String sql = "SELECT id, name, type, file_path, mime_type FROM file_manager WHERE id = ? AND is_deleted = 0";
                    fileList = adapter.select(sql, id);
                } catch (NumberFormatException e) {
                    resp.setStatus(400);
                    resp.setContentType("application/json;charset=utf-8");
                    result.put("code", 400);
                    result.put("msg", "id参数格式错误");
                    result.put("data", null);
                    responsePrint(resp, toJson(result));
                    return;
                }
            } else {
                // 根据path查询
                String sql = "SELECT id, name, type, file_path, mime_type FROM file_manager WHERE full_path = ? AND is_deleted = 0";
                fileList = adapter.select(sql, path);
            }

            // 检查查询结果
            if (fileList.isEmpty() || fileList.get(0).containsKey("error")) {
                resp.setStatus(404);
                resp.setContentType("application/json;charset=utf-8");
                result.put("code", 404);
                result.put("msg", "文件不存在");
                result.put("data", null);
                responsePrint(resp, toJson(result));
                return;
            }

            Map<String, Object> fileInfo = fileList.get(0);
            String type = (String) fileInfo.get("type");

            // 检查是否为文件
            if (!"file".equals(type)) {
                resp.setStatus(400);
                resp.setContentType("application/json;charset=utf-8");
                result.put("code", 400);
                result.put("msg", "指定的记录不是文件");
                result.put("data", null);
                responsePrint(resp, toJson(result));
                return;
            }

            String filePath = (String) fileInfo.get("file_path");
            String fileName = (String) fileInfo.get("name");
            String mimeType = (String) fileInfo.get("mime_type");

            if (filePath == null || filePath.trim().isEmpty()) {
                resp.setStatus(404);
                resp.setContentType("application/json;charset=utf-8");
                result.put("code", 404);
                result.put("msg", "文件路径不存在");
                result.put("data", null);
                responsePrint(resp, toJson(result));
                return;
            }

            // file_path已经是K8s挂载目录的完整物理路径，直接使用
            String actualFilePath = filePath;
            log.info("K8s挂载目录文件下载 - file_path: {}", actualFilePath);

            // 检查文件是否存在
            File file = new File(actualFilePath);
            if (!file.exists() || !file.isFile()) {
                resp.setStatus(404);
                resp.setContentType("application/json;charset=utf-8");
                result.put("code", 404);
                result.put("msg", "文件不存在: " + actualFilePath);
                result.put("data", null);
                responsePrint(resp, toJson(result));
                return;
            }

            // 检查文件是否可读
            if (!file.canRead()) {
                resp.setStatus(500);
                resp.setContentType("application/json;charset=utf-8");
                result.put("code", 500);
                result.put("msg", "文件无读取权限: " + actualFilePath);
                result.put("data", null);
                responsePrint(resp, toJson(result));
                return;
            }

            // 设置响应头
            if (mimeType != null && !mimeType.isEmpty()) {
                resp.setContentType(mimeType);
            } else {
                resp.setContentType("application/octet-stream");
            }

            // 设置文件名（URL编码）
            String encodedFileName = URLEncoder.encode(fileName, "UTF-8");
            resp.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"");
            resp.setHeader("Content-Length", String.valueOf(file.length()));

            // 读取文件并写入响应流（使用try-with-resources确保资源关闭）
            try (FileInputStream fileInputStream = new FileInputStream(file);
                 OutputStream outputStream = resp.getOutputStream()) {

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
            }

            // 文件下载成功，不需要返回JSON（已经返回了文件流）

        } catch (Exception e) {
            log.error("文件下载失败", e);
            resp.setStatus(500);
            resp.setContentType("application/json;charset=utf-8");
            result.put("code", 500);
            result.put("msg", "服务器内部错误: " + e.getMessage());
            result.put("data", null);
            responsePrint(resp, toJson(result));
        }
    }

    /**
     * 文件查看 - 查看当前文件夹下的子文件夹及子文件
     * @param req
     * @param resp
     * @throws IOException
     */
    private void directoryFile(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();

        try {
            // 获取请求参数
            Map<String, String> params = getQueryData(req);
            String parentIdStr = params.get("parent_id");

            // 解析parent_id，默认为0（根目录）
            int parentId = 0;
            if (parentIdStr != null && !parentIdStr.trim().isEmpty()) {
                try {
                    parentId = Integer.parseInt(parentIdStr);
                } catch (NumberFormatException e) {
                    result.put("code", 400);
                    result.put("msg", "parent_id参数格式错误");
                    result.put("data", null);
                    responsePrint(resp, toJson(result));
                    return;
                }
            }

            MysqlAdapter adapter = getMysqlAdapter();

            // 构建查询SQL - 查询当前目录下的所有文件和文件夹（未删除的）
            // 根据实际表结构：is_deleted字段，create_time和update_time是varchar类型
            StringBuilder sqlBuilder = new StringBuilder();
            sqlBuilder.append("SELECT id, name, type, parent_id, full_path, file_path, ");
            sqlBuilder.append("file_size, mime_type, extension, create_time, update_time ");
            sqlBuilder.append("FROM file_manager WHERE parent_id = ? AND is_deleted = 0 ");

            List<Object> paramsList = new ArrayList<>();
            paramsList.add(parentId);

            // 排序：文件夹在前，文件在后，然后按名称排序
            sqlBuilder.append("ORDER BY type DESC, name ASC");

            // 执行查询
            List<Map<String, Object>> fileList = adapter.select(
                    sqlBuilder.toString(),
                    paramsList.toArray()
            );

            // 检查是否有错误
            if (!fileList.isEmpty() && fileList.get(0).containsKey("error")) {
                result.put("code", 500);
                result.put("msg", "数据库查询错误: " + fileList.get(0).get("error"));
                result.put("data", null);
                responsePrint(resp, toJson(result));
                return;
            }

            // 格式化返回数据
            List<Map<String, Object>> formattedList = new ArrayList<>();
            for (Map<String, Object> item : fileList) {
                Map<String, Object> formattedItem = new HashMap<>();
                formattedItem.put("id", item.get("id"));
                formattedItem.put("name", item.get("name"));
                formattedItem.put("type", item.get("type"));
                formattedItem.put("parent_id", item.get("parent_id"));
                formattedItem.put("full_path", item.get("full_path"));

                // 如果是文件，添加文件相关字段
                if ("file".equals(item.get("type"))) {
                    formattedItem.put("file_path", item.get("file_path"));
                    formattedItem.put("file_size", item.get("file_size"));
                    formattedItem.put("mime_type", item.get("mime_type"));
                    formattedItem.put("extension", item.get("extension"));
                } else {
                    // 文件夹的file_size为0
                    formattedItem.put("file_size", 0);
                }

                // create_time和update_time是varchar类型，直接返回字符串
                formattedItem.put("create_time", item.get("create_time"));
                formattedItem.put("update_time", item.get("update_time"));

                formattedList.add(formattedItem);
            }

            // 构建响应
            Map<String, Object> data = new HashMap<>();
            data.put("list", formattedList);

            result.put("code", 200);
            result.put("message", "success");
            result.put("data", data);

        } catch (Exception e) {
            log.error("查看文件列表失败", e);
            result.put("code", 500);
            result.put("msg", "服务器内部错误: " + e.getMessage());
            result.put("data", null);
        }

        responsePrint(resp, toJson(result));
    }

    /**
     * 根据路径获取或创建目录，返回目录ID
     * 例如：/project/subfolder
     * 会创建：/project 和 /project/subfolder 目录（如果不存在）
     *
     * @param adapter 数据库适配器
     * @param dirPath 目录路径，如：/project/subfolder
     * @return 目录ID，如果失败返回-1
     */
    private int getOrCreateDirectoryByPath(MysqlAdapter adapter, String dirPath) {
        try {
            // 标准化路径
            String normalizedPath = dirPath.trim();
            if (!normalizedPath.startsWith("/")) {
                normalizedPath = "/" + normalizedPath;
            }
            // 去除末尾的/
            if (normalizedPath.length() > 1 && normalizedPath.endsWith("/")) {
                normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 1);
            }

            if ("/".equals(normalizedPath)) {
                return 0; // 根目录
            }

            // 拆分路径层级
            String[] pathParts = normalizedPath.substring(1).split("/");

            int currentParentId = 0;
            String currentPath = "/";

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String currentTime = sdf.format(new Date());

            // 逐级创建或获取目录
            for (String dirName : pathParts) {
                if (dirName == null || dirName.trim().isEmpty()) {
                    continue;
                }

                dirName = dirName.trim();
                currentPath = currentPath.equals("/") ? "/" + dirName : currentPath + "/" + dirName;

                // 检查目录是否已存在
                String checkSql = "SELECT id FROM file_manager WHERE parent_id = ? AND name = ? AND type = 'folder' AND is_deleted = 0";
                List<Map<String, Object>> existList = adapter.select(checkSql, currentParentId, dirName);

                if (!existList.isEmpty() && !existList.get(0).containsKey("error")) {
                    // 目录已存在，使用其ID
                    currentParentId = (Integer) existList.get(0).get("id");
                } else {
                    // 目录不存在，创建新目录
                    String insertSql = "INSERT INTO file_manager (name, type, parent_id, full_path, file_size, " +
                            "create_time, update_time, storage_type, is_deleted) " +
                            "VALUES (?, 'folder', ?, ?, 0, ?, ?, 'local', 0)";

                    int dirId = adapter.executeUpdateGeneratedKeys(insertSql,
                            dirName, currentParentId, currentPath, currentTime, currentTime);

                    if (dirId <= 0) {
                        log.error("创建目录失败: {}", currentPath);
                        return -1;
                    }

                    currentParentId = dirId;
                    log.info("创建目录成功: {} (id: {})", currentPath, dirId);
                }
            }

            return currentParentId;

        } catch (Exception e) {
            log.error("获取或创建目录失败: {}", dirPath, e);
            return -1;
        }
    }

    /**
     * 根据路径创建目录层级关系
     * 例如：/project/subfolder/file.txt
     * 会创建：/project 和 /project/subfolder 目录（如果不存在）
     *
     * @param adapter 数据库适配器
     * @param fullPath 完整路径，如：/project/subfolder/file.txt
     * @return 最终目录的parentId，如果失败返回-1
     */
    private int createDirectoryHierarchyByPath(MysqlAdapter adapter, String fullPath) {
        try {
            // 标准化路径
            String normalizedPath = fullPath.trim();
            if (!normalizedPath.startsWith("/")) {
                normalizedPath = "/" + normalizedPath;
            }

            // 分离目录路径和文件名
            int lastSlashIndex = normalizedPath.lastIndexOf("/");
            if (lastSlashIndex < 0) {
                return 0; // 根目录
            }

            String dirPath = normalizedPath.substring(0, lastSlashIndex);
            if (dirPath.isEmpty() || "/".equals(dirPath)) {
                return 0; // 根目录
            }

            // 拆分路径层级，例如：/project/subfolder -> [project, subfolder]
            String[] pathParts = dirPath.substring(1).split("/");

            int currentParentId = 0; // 从根目录开始
            String currentPath = "/";

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String currentTime = sdf.format(new Date());

            // 逐级创建目录
            for (String dirName : pathParts) {
                if (dirName == null || dirName.trim().isEmpty()) {
                    continue;
                }

                dirName = dirName.trim();
                currentPath = currentPath.equals("/") ? "/" + dirName : currentPath + "/" + dirName;

                // 检查目录是否已存在
                String checkSql = "SELECT id FROM file_manager WHERE parent_id = ? AND name = ? AND type = 'folder' AND is_deleted = 0";
                List<Map<String, Object>> existList = adapter.select(checkSql, currentParentId, dirName);

                if (!existList.isEmpty() && !existList.get(0).containsKey("error")) {
                    // 目录已存在，使用其ID
                    currentParentId = (Integer) existList.get(0).get("id");
                } else {
                    // 目录不存在，创建新目录
                    String insertSql = "INSERT INTO file_manager (name, type, parent_id, full_path, file_size, " +
                            "create_time, update_time, storage_type, is_deleted) " +
                            "VALUES (?, 'folder', ?, ?, 0, ?, ?, 'local', 0)";

                    int dirId = adapter.executeUpdateGeneratedKeys(insertSql,
                            dirName, currentParentId, currentPath, currentTime, currentTime);

                    if (dirId <= 0) {
                        log.error("创建目录失败: {}", currentPath);
                        return -1;
                    }

                    currentParentId = dirId;
                    log.info("创建目录成功: {} (id: {})", currentPath, dirId);
                }
            }

            return currentParentId;

        } catch (Exception e) {
            log.error("创建目录层级失败: {}", fullPath, e);
            return -1;
        }
    }

    /**
     * 创建文件夹
     * 支持通过parent_id或path参数创建文件夹
     *
     * @param req
     * @param resp
     * @throws IOException
     */
    private void createFolder(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();

        try {
            // 获取请求参数
            Map<String, String> params = getQueryData(req);
            String folderName = params.get("name");
            String parentIdStr = params.get("parent_id");
            String path = params.get("path"); // 文件夹路径参数（用于根据路径创建层级关系）

            // 验证文件夹名称
            if (folderName == null || folderName.trim().isEmpty()) {
                result.put("code", 400);
                result.put("message", "文件夹名称不能为空");
                result.put("data", null);
                responsePrint(resp, toJson(result));
                return;
            }

            folderName = folderName.trim();

            // 验证文件夹名称合法性（不能包含路径分隔符）
            if (folderName.contains("/") || folderName.contains("\\")) {
                result.put("code", 400);
                result.put("message", "文件夹名称不能包含路径分隔符");
                result.put("data", null);
                responsePrint(resp, toJson(result));
                return;
            }

            MysqlAdapter adapter = getMysqlAdapter();
            String fullPath;
            int parentId;

            // 如果提供了path参数，根据路径创建层级关系
            if (path != null && !path.trim().isEmpty()) {
                path = path.trim();
                // 标准化路径：确保以/开头，去除末尾的/
                if (!path.startsWith("/")) {
                    path = "/" + path;
                }
                // 去除末尾的/（如果有）
                if (path.length() > 1 && path.endsWith("/")) {
                    path = path.substring(0, path.length() - 1);
                }

                // 构建完整路径：父路径 + 文件夹名
                fullPath = "/".equals(path) ? "/" + folderName : path + "/" + folderName;

                // 根据父路径获取或创建目录，并获取最终的parentId
                parentId = getOrCreateDirectoryByPath(adapter, path);
                if (parentId < 0) {
                    result.put("code", 500);
                    result.put("message", "创建父目录层级失败");
                    result.put("data", null);
                    responsePrint(resp, toJson(result));
                    return;
                }
            } else {
                // 使用parent_id方式
                parentId = 0;
                if (parentIdStr != null && !parentIdStr.trim().isEmpty()) {
                    try {
                        parentId = Integer.parseInt(parentIdStr);
                    } catch (NumberFormatException e) {
                        result.put("code", 400);
                        result.put("message", "parent_id参数格式错误");
                        result.put("data", null);
                        responsePrint(resp, toJson(result));
                        return;
                    }
                }

                // 获取父目录信息（用于构建full_path）
                String parentFullPath = "/";
                if (parentId > 0) {
                    String parentSql = "SELECT full_path FROM file_manager WHERE id = ? AND is_deleted = 0";
                    List<Map<String, Object>> parentList = adapter.select(parentSql, parentId);
                    if (parentList.isEmpty() || parentList.get(0).containsKey("error")) {
                        result.put("code", 404);
                        result.put("message", "父目录不存在");
                        result.put("data", null);
                        responsePrint(resp, toJson(result));
                        return;
                    }
                    parentFullPath = (String) parentList.get(0).get("full_path");
                    if (parentFullPath == null) {
                        parentFullPath = "/";
                    }
                }

                // 构建full_path
                if ("/".equals(parentFullPath)) {
                    fullPath = "/" + folderName;
                } else {
                    fullPath = parentFullPath + "/" + folderName;
                }
            }

            // 检查同名文件夹是否已存在
            String checkSql = "SELECT id FROM file_manager WHERE parent_id = ? AND name = ? AND type = 'folder' AND is_deleted = 0";
            List<Map<String, Object>> existList = adapter.select(checkSql, parentId, folderName);
            if (!existList.isEmpty() && !existList.get(0).containsKey("error")) {
                result.put("code", 409);
                result.put("message", "文件夹已存在: " + folderName);
                result.put("data", null);
                responsePrint(resp, toJson(result));
                return;
            }

            // 生成时间戳字符串
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String currentTime = sdf.format(new Date());

            // 文件夹的物理路径（在K8s挂载目录中创建对应的物理目录）
            String physicalDirPath = mapToPhysicalPath(fullPath);
            log.info("创建文件夹 - full_path: {}, 物理路径: {}", fullPath, physicalDirPath);

            // 创建物理目录
            File dir = new File(physicalDirPath);
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                if (!created) {
                    log.error("创建物理目录失败: {}", physicalDirPath);
                    result.put("code", 500);
                    result.put("message", "创建物理目录失败，请检查K8s挂载目录权限: " + physicalDirPath);
                    result.put("data", null);
                    responsePrint(resp, toJson(result));
                    return;
                }
            }

            // file_path存储挂载目录下的完整路径（用于数据库记录）
            String dbDirPath = physicalDirPath;

            // 插入数据库（storage_type固定为local，type为folder）
            String insertSql = "INSERT INTO file_manager (name, type, parent_id, full_path, file_path, " +
                    "file_size, storage_type, create_time, update_time, is_deleted) " +
                    "VALUES (?, 'folder', ?, ?, ?, 0, 'local', ?, ?, 0)";

            int folderId = adapter.executeUpdateGeneratedKeys(insertSql,
                    folderName, parentId, fullPath, dbDirPath, currentTime, currentTime);

            if (folderId <= 0) {
                // 如果插入失败，删除已创建的物理目录
                if (dir.exists()) {
                    dir.delete();
                }
                result.put("code", 500);
                result.put("message", "创建文件夹失败：数据库插入错误");
                result.put("data", null);
                responsePrint(resp, toJson(result));
                return;
            }

            // 构建返回数据
            Map<String, Object> data = new HashMap<>();
            data.put("id", folderId);
            data.put("name", folderName);
            data.put("type", "folder");
            data.put("parent_id", parentId);
            data.put("full_path", fullPath);
            data.put("file_path", dbDirPath);
            data.put("storage_type", "local");
            data.put("file_size", 0);
            data.put("create_time", currentTime);
            data.put("update_time", currentTime);

            result.put("code", 200);
            result.put("message", "success");
            result.put("data", data);

        } catch (Exception e) {
            log.error("创建文件夹失败", e);
            result.put("code", 500);
            result.put("message", "服务器内部错误: " + e.getMessage());
            result.put("data", null);
        }

        responsePrint(resp, toJson(result));
    }

}
