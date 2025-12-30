package ai.servlet.api;

import ai.servlet.BaseServlet;
import lombok.extern.slf4j.Slf4j;
import lombok.var;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024, // 1MB
    maxFileSize = 1024 * 1024 * 10, // 10MB
    maxRequestSize = 1024 * 1024 * 50 // 50MB
)
public class FileLocalhostServlet extends BaseServlet {

    // 默认本地文件存储根目录
    private static final String DEFAULT_FILE_ROOT_PATH = System.getProperty("user.home") + "/LagiMarket/uploads/";

    /**
     * 获取安全的文件路径
     * 支持绝对路径和相对路径（相对于默认目录）
     */
    private String getSafeFilePath(String pathParam) {
        if (pathParam == null || pathParam.trim().isEmpty()) {
            return DEFAULT_FILE_ROOT_PATH;
        }

        String path = pathParam.trim();

        // 如果是绝对路径，直接使用
        if (Paths.get(path).isAbsolute()) {
            return path.endsWith("/") ? path : path + "/";
        }

        // 如果是相对路径，相对于默认目录
        Path fullPath = Paths.get(DEFAULT_FILE_ROOT_PATH, path).normalize();
        return fullPath.toString().endsWith("/") ? fullPath.toString() : fullPath.toString() + "/";
    }

    /**
     * 验证路径是否安全（防止路径遍历攻击）
     */
    private boolean isPathSafe(String basePath, String filePath) {
        try {
            Path base = Paths.get(basePath).normalize();
            Path file = Paths.get(filePath).normalize();
            return file.startsWith(base);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);

        if (method.equals("download")) {
            handleDownload(req, resp);
        } else if (method.equals("upload")) {
            handleUpload(req, resp);
        } else if (method.equals("directory")) {
            handleDirectory(req, resp);
        } else if (method.equals("createFolder")) {
            handleCreateFolder(req, resp);
        } else if (method.equals("delete")) {
            handleDelete(req, resp);
        } else if (method.equals("rename")) {
            handleRename(req, resp);
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
     * 处理文件下载
     */
    private void handleDownload(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String filePath = req.getParameter("path");
        String fileName = req.getParameter("filename");

        if (fileName == null || fileName.trim().isEmpty()) {
            sendError(resp, 400, "文件名不能为空");
            return;
        }

        String basePath = getSafeFilePath(filePath);
        Path file = Paths.get(basePath, fileName).normalize();

        // 安全检查：确保文件在允许的目录内
        if (!isPathSafe(basePath, file.toString())) {
            sendError(resp, 403, "访问被拒绝：路径不安全");
            return;
        }

        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            sendError(resp, 404, "文件不存在");
            return;
        }

        try {
            // 设置响应头
            resp.setContentType("application/octet-stream");
            resp.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            resp.setContentLengthLong(Files.size(file));

            // 传输文件
            try (InputStream inputStream = Files.newInputStream(file);
                 OutputStream outputStream = resp.getOutputStream()) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
            }

            log.info("文件下载成功: {}", file.toString());

        } catch (Exception e) {
            log.error("文件下载失败: {}", file.toString(), e);
            sendError(resp, 500, "文件下载失败: " + e.getMessage());
        }
    }

    /**
     * 发送错误响应
     */
    private void sendError(HttpServletResponse resp, int statusCode, String message) throws IOException {
        resp.setStatus(statusCode);
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> error = new HashMap<>();
        error.put("code", statusCode);
        error.put("msg", message);
        error.put("data", null);
        responsePrint(resp, toJson(error));
    }

    /**
     * 处理文件上传
     */
    private void handleUpload(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        String path = req.getParameter("path");

        // 获取上传的文件
        Part filePart = req.getPart("file");
        if (filePart == null) {
            sendError(resp, 400, "没有找到上传的文件");
            return;
        }

        String fileName = filePart.getSubmittedFileName();
        if (fileName == null || fileName.trim().isEmpty()) {
            sendError(resp, 400, "文件名不能为空");
            return;
        }

        String basePath = getSafeFilePath(path);
        Path targetDir = Paths.get(basePath);

        // 确保目标目录存在
        try {
            Files.createDirectories(targetDir);
        } catch (Exception e) {
            log.error("创建目录失败: {}", targetDir.toString(), e);
            sendError(resp, 500, "创建目录失败: " + e.getMessage());
            return;
        }

        Path targetFile = targetDir.resolve(fileName).normalize();

        // 安全检查：确保文件在允许的目录内
        if (!isPathSafe(basePath, targetFile.toString())) {
            sendError(resp, 403, "访问被拒绝：路径不安全");
            return;
        }

        try (InputStream inputStream = filePart.getInputStream();
             OutputStream outputStream = Files.newOutputStream(targetFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
        } catch (Exception e) {
            log.error("文件上传失败: {}", targetFile.toString(), e);
            sendError(resp, 500, "文件上传失败: " + e.getMessage());
            return;
        }

        // 返回上传结果
        Map<String, Object> result = new HashMap<>();
        result.put("fileName", fileName);
        result.put("fileSize", Files.size(targetFile));
        result.put("filePath", targetFile.toString());
        result.put("uploadTime", System.currentTimeMillis());

        log.info("文件上传成功: {}", targetFile.toString());
        sendSuccess(resp, result);
    }

    /**
     * 处理目录浏览
     */
    private void handleDirectory(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getParameter("path");

        String basePath = getSafeFilePath(path);
        Path directory = Paths.get(basePath);

        if (!Files.exists(directory)) {
            try {
                Files.createDirectories(directory);
            } catch (Exception e) {
                log.error("创建目录失败: {}", directory.toString(), e);
                sendError(resp, 500, "创建目录失败: " + e.getMessage());
                return;
            }
        }

        if (!Files.isDirectory(directory)) {
            sendError(resp, 400, "指定的路径不是目录");
            return;
        }

        try {
            List<Map<String, Object>> fileList = new ArrayList<>();

            try (var stream = Files.list(directory)) {
                stream.forEach(filePath -> {
                    try {
                        Map<String, Object> fileInfo = new HashMap<>();
                        fileInfo.put("name", filePath.getFileName().toString());
                        fileInfo.put("path", filePath.toString());
                        fileInfo.put("isDirectory", Files.isDirectory(filePath));
                        fileInfo.put("size", Files.isRegularFile(filePath) ? Files.size(filePath) : 0);
                        fileInfo.put("lastModified", Files.getLastModifiedTime(filePath).toMillis());
                        fileList.add(fileInfo);
                    } catch (Exception e) {
                        log.warn("获取文件信息失败: {}", filePath.toString(), e);
                    }
                });
            }

            // 按名称排序，目录在前
            fileList.sort((a, b) -> {
                boolean aIsDir = (Boolean) a.get("isDirectory");
                boolean bIsDir = (Boolean) b.get("isDirectory");
                if (aIsDir && !bIsDir) return -1;
                if (!aIsDir && bIsDir) return 1;
                return ((String) a.get("name")).compareToIgnoreCase((String) b.get("name"));
            });

            Map<String, Object> result = new HashMap<>();
            result.put("currentPath", directory.toString());
            result.put("files", fileList);
            result.put("totalCount", fileList.size());

            sendSuccess(resp, result);

        } catch (Exception e) {
            log.error("浏览目录失败: {}", directory.toString(), e);
            sendError(resp, 500, "浏览目录失败: " + e.getMessage());
        }
    }

    /**
     * 处理创建文件夹
     */
    private void handleCreateFolder(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getParameter("path");
        String folderName = req.getParameter("folderName");

        if (folderName == null || folderName.trim().isEmpty()) {
            sendError(resp, 400, "文件夹名称不能为空");
            return;
        }

        String basePath = getSafeFilePath(path);
        Path newFolder = Paths.get(basePath, folderName).normalize();

        // 安全检查：确保文件夹在允许的目录内
        if (!isPathSafe(basePath, newFolder.toString())) {
            sendError(resp, 403, "访问被拒绝：路径不安全");
            return;
        }

        if (Files.exists(newFolder)) {
            sendError(resp, 409, "文件夹已存在");
            return;
        }

        try {
            Files.createDirectories(newFolder);

            Map<String, Object> result = new HashMap<>();
            result.put("folderName", folderName);
            result.put("folderPath", newFolder.toString());
            result.put("createTime", System.currentTimeMillis());

            log.info("文件夹创建成功: {}", newFolder.toString());
            sendSuccess(resp, result);

        } catch (Exception e) {
            log.error("创建文件夹失败: {}", newFolder.toString(), e);
            sendError(resp, 500, "创建文件夹失败: " + e.getMessage());
        }
    }

    /**
     * 处理文件/文件夹删除
     */
    private void handleDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getParameter("path");
        String fileName = req.getParameter("filename");

        if (fileName == null || fileName.trim().isEmpty()) {
            sendError(resp, 400, "文件名不能为空");
            return;
        }

        String basePath = getSafeFilePath(path);
        Path targetFile = Paths.get(basePath, fileName).normalize();

        // 安全检查：确保文件在允许的目录内
        if (!isPathSafe(basePath, targetFile.toString())) {
            sendError(resp, 403, "访问被拒绝：路径不安全");
            return;
        }

        if (!Files.exists(targetFile)) {
            sendError(resp, 404, "文件或文件夹不存在");
            return;
        }

        try {
            // 递归删除目录及其内容
            if (Files.isDirectory(targetFile)) {
                deleteDirectoryRecursively(targetFile);
            } else {
                Files.delete(targetFile);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("deletedPath", targetFile.toString());
            result.put("isDirectory", Files.isDirectory(targetFile));
            result.put("deleteTime", System.currentTimeMillis());

            log.info("删除成功: {}", targetFile.toString());
            sendSuccess(resp, result);

        } catch (Exception e) {
            log.error("删除失败: {}", targetFile.toString(), e);
            sendError(resp, 500, "删除失败: " + e.getMessage());
        }
    }

    /**
     * 处理文件/文件夹重命名
     */
    private void handleRename(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getParameter("path");
        String oldName = req.getParameter("oldName");
        String newName = req.getParameter("newName");

        if (oldName == null || oldName.trim().isEmpty()) {
            sendError(resp, 400, "原文件名不能为空");
            return;
        }

        if (newName == null || newName.trim().isEmpty()) {
            sendError(resp, 400, "新文件名不能为空");
            return;
        }

        String basePath = getSafeFilePath(path);
        Path oldFile = Paths.get(basePath, oldName).normalize();
        Path newFile = Paths.get(basePath, newName).normalize();

        // 安全检查：确保文件在允许的目录内
        if (!isPathSafe(basePath, oldFile.toString()) || !isPathSafe(basePath, newFile.toString())) {
            sendError(resp, 403, "访问被拒绝：路径不安全");
            return;
        }

        if (!Files.exists(oldFile)) {
            sendError(resp, 404, "原文件或文件夹不存在");
            return;
        }

        if (Files.exists(newFile)) {
            sendError(resp, 409, "新文件名已存在");
            return;
        }

        try {
            Files.move(oldFile, newFile);

            Map<String, Object> result = new HashMap<>();
            result.put("oldPath", oldFile.toString());
            result.put("newPath", newFile.toString());
            result.put("isDirectory", Files.isDirectory(newFile));
            result.put("renameTime", System.currentTimeMillis());

            log.info("重命名成功: {} -> {}", oldFile.toString(), newFile.toString());
            sendSuccess(resp, result);

        } catch (Exception e) {
            log.error("重命名失败: {} -> {}", oldFile.toString(), newFile.toString(), e);
            sendError(resp, 500, "重命名失败: " + e.getMessage());
        }
    }

    /**
     * 递归删除目录
     */
    private void deleteDirectoryRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                stream.forEach(child -> {
                    try {
                        deleteDirectoryRecursively(child);
                    } catch (IOException e) {
                        log.warn("删除子项失败: {}", child.toString(), e);
                    }
                });
            }
        }
        Files.delete(path);
    }

    /**
     * 发送成功响应
     */
    private void sendSuccess(HttpServletResponse resp, Object data) throws IOException {
        resp.setStatus(200);
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("data", data);
        responsePrint(resp, toJson(result));
    }
}
