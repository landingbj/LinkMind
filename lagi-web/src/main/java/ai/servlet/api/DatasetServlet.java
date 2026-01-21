  package ai.servlet.api;


import ai.config.ContextLoader;
import ai.config.UploadConfig;
import ai.database.impl.MysqlAdapter;
import ai.servlet.BaseServlet;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;


import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
public class DatasetServlet extends BaseServlet {

    // 配置对象
    private static final UploadConfig uploadConfig = ContextLoader.getBean(UploadConfig.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();


    private static volatile MysqlAdapter mysqlAdapter = null;
    private static MysqlAdapter getMysqlAdapter() {
        if (mysqlAdapter == null) {
            synchronized (DatasetServlet.class) {
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


        if (method.equals("lists")) {
            getDatasetList(req,resp);
        }else if (method.equals("upload")){
            uploadDataset(req, resp);
        }else if (method.equals("update")){
            updateDataset(req, resp);
        }else if (method.equals("delete")){
            deleteDataset(req, resp);
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
     * 获取数据集列表
     * @param req
     * @param resp
     * @throws IOException
     * 支持参数：
     * - page: 页码，默认1
     * - page_size: 每页条数，默认10，最大100
     * - name: 数据集名称，模糊查询
     * - category: 分类筛选
     * - access_level: 访问级别筛选（1=私有，2=公开，3=团队）
     * - uploader: 上传人筛选
     */
    private void getDatasetList(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 解析分页参数
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
            
            // 解析筛选参数
            String name = req.getParameter("name");
            if (name != null) {
                name = name.trim();
                if (name.isEmpty()) {
                    name = null;
                }
            }
            
            String category = req.getParameter("category");
            if (category != null) {
                category = category.trim();
                if (category.isEmpty()) {
                    category = null;
                }
            }
            
            Integer accessLevel = null;
            String accessLevelParam = req.getParameter("access_level");
            if (accessLevelParam != null && !accessLevelParam.trim().isEmpty()) {
                try {
                    accessLevel = Integer.parseInt(accessLevelParam.trim());
                    if (accessLevel < 1 || accessLevel > 3) {
                        accessLevel = null;
                    }
                } catch (NumberFormatException e) {
                    log.warn("access_level参数解析失败", e);
                    accessLevel = null;
                }
            }
            
            String uploader = req.getParameter("uploader");
            if (uploader != null) {
                uploader = uploader.trim();
                if (uploader.isEmpty()) {
                    uploader = null;
                }
            }
            
            // 构建查询SQL（只查询未删除的数据集）
            StringBuilder sqlBuilder = new StringBuilder("SELECT * FROM dataset_upload WHERE is_deleted = 0");
            List<Object> params = new ArrayList<>();
            
            if (name != null) {
                sqlBuilder.append(" AND name LIKE ?");
                params.add("%" + name + "%");
            }
            
            if (category != null) {
                sqlBuilder.append(" AND category = ?");
                params.add(category);
            }
            
            if (accessLevel != null) {
                sqlBuilder.append(" AND access_level = ?");
                params.add(accessLevel);
            }
            
            if (uploader != null) {
                sqlBuilder.append(" AND uploader = ?");
                params.add(uploader);
            }
            
            // 排序：按创建时间倒序（假设有created_at字段，如果没有则按sample_id）
            sqlBuilder.append(" ORDER BY sample_id DESC");
            
            // 查询总数（只统计未删除的数据集）
            String countSql = "SELECT COUNT(*) as total FROM dataset_upload WHERE is_deleted = 0";
            List<Object> countParams = new ArrayList<>();
            
            if (name != null) {
                countSql += " AND name LIKE ?";
                countParams.add("%" + name + "%");
            }
            if (category != null) {
                countSql += " AND category = ?";
                countParams.add(category);
            }
            if (accessLevel != null) {
                countSql += " AND access_level = ?";
                countParams.add(accessLevel);
            }
            if (uploader != null) {
                countSql += " AND uploader = ?";
                countParams.add(uploader);
            }
            
            List<Map<String, Object>> countResult = getMysqlAdapter().select(countSql, countParams.toArray());
            long total = 0;
            if (countResult != null && !countResult.isEmpty()) {
                Object totalObj = countResult.get(0).get("total");
                if (totalObj != null) {
                    total = totalObj instanceof Number ? ((Number) totalObj).longValue() : Long.parseLong(totalObj.toString());
                }
            }
            
            // 分页查询
            int offset = (page - 1) * pageSize;
            sqlBuilder.append(" LIMIT ? OFFSET ?");
            params.add(pageSize);
            params.add(offset);
            
            List<Map<String, Object>> datasetList = getMysqlAdapter().select(sqlBuilder.toString(), params.toArray());
            
            // 处理返回数据，格式化字段
            List<Map<String, Object>> dataList = new ArrayList<>();
            if (datasetList != null) {
                for (Map<String, Object> row : datasetList) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("sample_id", row.get("sample_id"));
                    item.put("name", row.get("name"));
                    item.put("description", row.get("description"));
                    item.put("label", row.get("label"));
                    item.put("category", row.get("category"));
                    item.put("access_level", row.get("access_level"));
                    item.put("uploader", row.get("uploader"));
                    item.put("data_source", row.get("data_source"));
                    item.put("data_processing_status", row.get("data_processing_status"));
                    item.put("missing_value_mark", row.get("missing_value_mark"));
                    item.put("weight", row.get("weight"));
                    item.put("remark", row.get("remark"));
                    item.put("storage_path", row.get("storage_path"));
                    item.put("storage_type", row.get("storage_type"));
                    item.put("original_url", row.get("original_url"));
                    item.put("file_size", row.get("file_size"));
                    
                    // 处理training_params JSON字段
                    Object trainingParams = row.get("training_params");
                    if (trainingParams != null) {
                        try {
                            if (trainingParams instanceof String) {
                                item.put("training_params", objectMapper.readTree((String) trainingParams));
                            } else {
                                item.put("training_params", trainingParams);
                            }
                        } catch (Exception e) {
                            log.warn("解析training_params失败", e);
                            item.put("training_params", null);
                        }
                    } else {
                        item.put("training_params", null);
                    }
                    
                    dataList.add(item);
                }
            }
            
            // 构建返回结果
            Map<String, Object> data = new HashMap<>();
            data.put("list", dataList);
            data.put("total", total);
            data.put("page", page);
            data.put("page_size", pageSize);
            data.put("total_pages", (int) Math.ceil((double) total / pageSize));
            
            result.put("code", 200);
            result.put("msg", "查询成功");
            result.put("data", data);
            
            responsePrint(resp, objectMapper.writeValueAsString(result));
            
        } catch (Exception e) {
            log.error("获取数据集列表失败", e);
            result.put("code", 500);
            result.put("msg", "查询失败：" + e.getMessage());
            responsePrint(resp, objectMapper.writeValueAsString(result));
        }
    }

    /**
     * 上传预训练数据集
     * @param req
     * @param resp
     * @throws IOException
     * 核心：处理三种方式的数据集上传，支持multipart/form-data格式，自动解压ZIP并校验data.yaml
     */
    public void uploadDataset(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        
        Map<String, Object> paramMap = new HashMap<>();
        File uploadedFile = null;
        String contentType = req.getContentType();
        
        try {
            // 1. 解析请求参数（支持multipart/form-data和application/json）
            if (contentType != null && contentType.contains("multipart/form-data")) {
                // 处理multipart/form-data格式（文件上传专用）
                if (!ServletFileUpload.isMultipartContent(req)) {
                    responseError(resp, 400, "请求必须是multipart/form-data格式");
                    return;
                }
                
                DiskFileItemFactory factory = new DiskFileItemFactory();
                ServletFileUpload upload = new ServletFileUpload(factory);
                List<FileItem> items = upload.parseRequest(req);
                
                for (FileItem item : items) {
                    if (item.isFormField()) {
                        // 普通表单字段
                        String fieldName = item.getFieldName();
                        String fieldValue = item.getString("UTF-8");
                        
                        // 特殊处理JSON类型参数和数值类型
                        if ("training_params".equals(fieldName) && fieldValue != null && !fieldValue.isEmpty()) {
                            try {
                                paramMap.put(fieldName, objectMapper.readTree(fieldValue));
                            } catch (Exception e) {
                                log.warn("解析training_params失败，将作为字符串处理", e);
                                paramMap.put(fieldName, fieldValue);
                            }
                        } else if ("access_level".equals(fieldName) && fieldValue != null && !fieldValue.isEmpty()) {
                            try {
                                paramMap.put(fieldName, Integer.parseInt(fieldValue));
                            } catch (NumberFormatException e) {
                                responseError(resp, 400, "access_level必须是整数");
                                return;
                            }
                        } else if ("weight".equals(fieldName) && fieldValue != null && !fieldValue.isEmpty()) {
                            try {
                                paramMap.put(fieldName, Double.parseDouble(fieldValue));
                            } catch (NumberFormatException e) {
                                responseError(resp, 400, "weight必须是数字");
                                return;
                            }
                        } else {
                            paramMap.put(fieldName, fieldValue);
                        }
                    } else {
                        // 文件字段
                        if ("file".equals(item.getFieldName())) {
                            // 创建临时文件保存上传的文件
                            String tempFileName = "dataset_upload_" + System.currentTimeMillis() + "_" + item.getName();
                            File tempFile = new File(uploadConfig.getDataset().getStorage().getContainer_temp_path(), tempFileName);
                            // 确保临时目录存在
                            tempFile.getParentFile().mkdirs();
                            item.write(tempFile);
                            uploadedFile = tempFile;
                        }
                    }
                }
            } else {
                // 处理JSON格式请求（绝对路径/URL上传）
                String jsonBody = requestToJson(req);
                if (jsonBody != null && !jsonBody.trim().isEmpty()) {
                    JSONObject jsonObj = JSONUtil.parseObj(jsonBody);
                    for (String key : jsonObj.keySet()) {
                        paramMap.put(key, jsonObj.get(key));
                    }
                }
            }

            // 2. 核心必填参数校验
            String name = paramMap.get("name") == null ? null : paramMap.get("name").toString();
            Integer accessLevel = paramMap.get("access_level") == null ? null : 
                    (paramMap.get("access_level") instanceof Integer ? (Integer) paramMap.get("access_level") : 
                     Integer.parseInt(paramMap.get("access_level").toString()));
            // 上传人：建议从登录态/Token获取，此处先默认值
            String uploader = req.getHeader("X-Uploader") == null ? "admin" : req.getHeader("X-Uploader");

            if (name == null || name.trim().isEmpty()) {
                responseError(resp, 400, "数据集名称（name）不能为空");
                return;
            }
            if (accessLevel == null || accessLevel < 1 || accessLevel > 3) {
                responseError(resp, 400, "访问级别（access_level）必须为1(私有)/2(公开)/3(团队)");
                return;
            }

            // 3. 生成唯一样本ID
            String sampleId = generateSampleId();

            // 4. 区分三种上传方式处理
            String storagePath = null;
            Integer storageType = null;
            String originalUrl = null;
            Long fileSize = 0L;

            // 方式1：服务器绝对路径上传
            if (paramMap.containsKey("absolute_path") && paramMap.get("absolute_path") != null) {
                String absolutePath = paramMap.get("absolute_path").toString();
                File absoluteFile = new File(absolutePath);
                // 验证文件存在且可读
                if (!absoluteFile.exists() || !absoluteFile.canRead()) {
                    responseError(resp, 400, "绝对路径文件不存在或无读取权限：" + absolutePath);
                    return;
                }
                
                // 如果是ZIP文件，需要解压
                if (absolutePath.toLowerCase().endsWith(".zip")) {
                    File extractDir = handleZipFile(absoluteFile, sampleId);
                    if (extractDir == null) {
                        responseError(resp, 400, "ZIP文件解压失败");
                        return;
                    }
                    storagePath = extractDir.getAbsolutePath();
                } else {
                    storagePath = absolutePath;
                }
                storageType = uploadConfig.getDataset().getStorage_type().getAbsolute_path();
                fileSize = absoluteFile.length();

            // 方式2：URL下载上传
            } else if (paramMap.containsKey("dataset_url") && paramMap.get("dataset_url") != null) {
                originalUrl = paramMap.get("dataset_url").toString();
                // 下载文件到容器临时目录
                String tempFileName = sampleId + "_" + System.currentTimeMillis() + getFileSuffix(originalUrl);
                File tempFile = new File(uploadConfig.getDataset().getStorage().getContainer_temp_path(), tempFileName);
                tempFile.getParentFile().mkdirs();
                
                // 执行URL下载
                try {
                    downloadFileFromUrl(originalUrl, tempFile);
                } catch (Exception e) {
                    responseError(resp, 500, "URL下载失败：" + e.getMessage());
                    return;
                }
                
                // 如果是ZIP文件，需要解压
                if (tempFile.getName().toLowerCase().endsWith(".zip")) {
                    File extractDir = handleZipFile(tempFile, sampleId);
                    if (extractDir == null) {
                        responseError(resp, 400, "ZIP文件解压失败");
                        return;
                    }
                    storagePath = extractDir.getAbsolutePath();
                    // 删除临时ZIP文件
                    tempFile.delete();
                } else {
                    // 移动到正式存储目录
                    String targetFileName = sampleId + "_" + System.currentTimeMillis() + getFileSuffix(originalUrl);
                    File targetFile = new File(uploadConfig.getDataset().getStorage().getContainer_base_path(), targetFileName);
                    targetFile.getParentFile().mkdirs();
                    Files.move(tempFile.toPath(), targetFile.toPath());
                    storagePath = targetFile.getAbsolutePath();
                }
                storageType = uploadConfig.getDataset().getStorage_type().getUrl_download();
                fileSize = new File(storagePath).isDirectory() ? 
                    calculateDirectorySize(new File(storagePath)) : new File(storagePath).length();

            // 方式3：本地文件上传（multipart/form-data）
            } else if (uploadedFile != null && uploadedFile.exists()) {
                // 验证文件格式（必须是ZIP）
                String fileName = uploadedFile.getName();
                if (!fileName.toLowerCase().endsWith(".zip")) {
                    responseError(resp, 400, "仅支持ZIP格式的数据集压缩包");
                    uploadedFile.delete();
                    return;
                }
                
                // 验证文件大小
                long fileSizeBytes = uploadedFile.length();
                if (fileSizeBytes == 0) {
                    responseError(resp, 400, "上传的ZIP文件为空");
                    uploadedFile.delete();
                    return;
                }
                log.info("上传的ZIP文件：{}，大小：{} 字节", uploadedFile.getAbsolutePath(), fileSizeBytes);
                
                // 解压ZIP文件
                File extractDir = handleZipFile(uploadedFile, sampleId);
                if (extractDir == null) {
                    responseError(resp, 400, "ZIP文件解压失败，请检查ZIP文件是否损坏或格式不正确");
                    uploadedFile.delete();
                    return;
                }
                
                // 验证解压后的目录是否有内容
                File[] extractedFiles = extractDir.listFiles();
                if (extractedFiles == null || extractedFiles.length == 0) {
                    log.warn("解压后的目录为空：{}", extractDir.getAbsolutePath());
                    responseError(resp, 400, "ZIP文件解压后目录为空，请检查ZIP文件内容");
                    deleteDirectory(extractDir);
                    uploadedFile.delete();
                    return;
                }
                
                log.info("解压成功，目录中有 {} 个文件/目录", extractedFiles.length);
                
                storagePath = extractDir.getAbsolutePath();
                storageType = uploadConfig.getDataset().getStorage_type().getFile_upload();
                fileSize = calculateDirectorySize(extractDir);
                
                // 删除临时上传文件
                uploadedFile.delete();

            // 无有效上传方式
            } else {
                responseError(resp, 400, "必须选择一种上传方式：absolute_path/dataset_url/file");
                return;
            }

            // 5. 插入数据库
            boolean insertSuccess = insertDatasetToDb(paramMap, sampleId, storagePath, storageType, originalUrl, fileSize, uploader);
            if (insertSuccess) {
                Map<String, Object> success = new HashMap<>();
                success.put("code", 200);
                success.put("msg", "数据集上传成功");
                Map<String, Object> data = new HashMap<>();
                data.put("sample_id", sampleId);
                data.put("storage_path", storagePath);
                data.put("file_size", fileSize);
                data.put("storage_type", storageType);
                success.put("data", data);
                responsePrint(resp, objectMapper.writeValueAsString(success));
            } else {
                responseError(resp, 500, "数据集入库失败");
            }
        } catch (Exception e) {
            log.error("数据集上传处理异常", e);
            responseError(resp, 500, "数据集上传失败：" + e.getMessage());
        }
    }

    /**
     * 处理ZIP文件：解压ZIP文件到指定目录
     * @param zipFile ZIP文件
     * @param sampleId 样本ID
     * @return 解压后的目录，如果解压失败则返回null
     */
    private File handleZipFile(File zipFile, String sampleId) {
        try {
            // 验证ZIP文件是否存在且可读
            if (zipFile == null || !zipFile.exists() || !zipFile.canRead()) {
                log.error("ZIP文件不存在或不可读：{}", zipFile != null ? zipFile.getAbsolutePath() : "null");
                return null;
            }
            
            log.info("开始解压ZIP文件：{}，大小：{} 字节", zipFile.getAbsolutePath(), zipFile.length());
            
            // 创建解压目标目录
            // 使用宿主机路径存储
            String hostPath = uploadConfig.getDataset().getStorage().getHost_mount_path();
            File extractDir = new File(hostPath, "datasets/" + sampleId);
            //File extractDir = new File(uploadConfig.getDataset().getStorage().getContainer_base_path(), sampleId);
            if (!extractDir.exists()) {
                boolean created = extractDir.mkdirs();
                if (!created) {
                    log.error("创建解压目录失败：{}", extractDir.getAbsolutePath());
                    return null;
                }
            }
            
            log.info("解压目标目录：{}", extractDir.getAbsolutePath());
            
            // 解压ZIP文件
            int extractedCount = unzipFile(zipFile, extractDir);
            
            if (extractedCount == 0) {
                log.warn("ZIP文件解压后没有提取到任何文件");
            } else {
                log.info("ZIP文件解压成功，共解压 {} 个文件/目录，解压目录：{}", extractedCount, extractDir.getAbsolutePath());
            }
            
            return extractDir;
        } catch (Exception e) {
            log.error("处理ZIP文件失败", e);
            return null;
        }
    }
    
    /**
     * 解压ZIP文件到指定目录
     * @return 解压的文件/目录数量
     */
    private int unzipFile(File zipFile, File targetDir) throws IOException {
        int count = 0;
        try (FileInputStream fis = new FileInputStream(zipFile);
             ZipInputStream zis = new ZipInputStream(fis)) {
            
            ZipEntry zipEntry;
            byte[] buffer = new byte[8192];
            
            while ((zipEntry = zis.getNextEntry()) != null) {
                String entryName = zipEntry.getName();
                log.debug("处理ZIP条目：{}", entryName);
                
                Path targetPath = targetDir.toPath().resolve(entryName).normalize();
                
                // 安全检查：确保解压的文件在目标目录内
                Path targetDirPath = targetDir.toPath().normalize();
                if (!targetPath.startsWith(targetDirPath)) {
                    log.warn("跳过不安全的ZIP条目: {}", entryName);
                    zis.closeEntry();
                    continue;
                }
                
                if (zipEntry.isDirectory()) {
                    Files.createDirectories(targetPath);
                    log.debug("创建目录：{}", targetPath);
                    count++;
                } else {
                    // 确保父目录存在
                    Files.createDirectories(targetPath.getParent());
                    
                    try (FileOutputStream fos = new FileOutputStream(targetPath.toFile())) {
                        long totalBytes = 0;
                        int bytesRead;
                        while ((bytesRead = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                            totalBytes += bytesRead;
                        }
                        log.debug("解压文件：{}，大小：{} 字节", targetPath, totalBytes);
                        count++;
                    } catch (IOException e) {
                        log.error("解压文件失败：{}", targetPath, e);
                        throw e;
                    }
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            log.error("解压ZIP文件时发生IO异常：{}", zipFile.getAbsolutePath(), e);
            throw e;
        }
        
        return count;
    }
    
    /**
     * 计算目录大小
     */
    private long calculateDirectorySize(File directory) {
        long size = 0;
        if (directory.isFile()) {
            return directory.length();
        }
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                size += calculateDirectorySize(file);
            }
        }
        return size;
    }
    
    /**
     * 递归删除目录
     */
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }

    /**
     * 生成唯一样本ID
     */
    private String generateSampleId() {
        return "DS_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 从URL下载文件
     */
    private void downloadFileFromUrl(String url, File targetFile) throws Exception {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(uploadConfig.getDataset().getUrl_download().getTimeout()))
                .setResponseTimeout(Timeout.ofMilliseconds(uploadConfig.getDataset().getUrl_download().getTimeout()))
                .build();

        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build()) {
            HttpGet httpGet = new HttpGet(url);
            int retryCount = 0;
            while (retryCount < uploadConfig.getDataset().getUrl_download().getRetry_count()) {
                try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                    if (response.getCode() == 200) {
                        // 写入文件
                        try (OutputStream out = new FileOutputStream(targetFile)) {
                            response.getEntity().writeTo(out);
                        }
                        return;
                    } else {
                        retryCount++;
                        log.warn("URL下载失败，状态码：{}，重试次数：{}", response.getCode(), retryCount);
                        Thread.sleep(1000); // 重试间隔1秒
                    }
                } catch (Exception e) {
                    retryCount++;
                    log.warn("URL下载异常，重试次数：{}", retryCount, e);
                    Thread.sleep(1000);
                }
            }
            throw new RuntimeException("URL下载失败，已重试" + uploadConfig.getDataset().getUrl_download().getRetry_count() + "次");
        } finally {
            // 下载失败清理临时文件
            if (uploadConfig.getDataset().getUrl_download().isClean_failed_temp_file() && !targetFile.exists()) {
                if (targetFile.exists()) {
                    targetFile.delete();
                }
            }
        }
    }

    /**
     * 获取文件后缀
     */
    private String getFileSuffix(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return ".dat";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }

    /**
     * 插入数据集信息到数据库
     */
    private boolean insertDatasetToDb(Map<String, Object> paramMap, String sampleId, String storagePath,
                                      Integer storageType, String originalUrl, Long fileSize, String uploader) throws SQLException {
        String sql = "INSERT INTO dataset_upload (" +
                "sample_id, name, description, label, category, access_level, uploader, " +
                "data_source, data_processing_status, missing_value_mark, weight, remark, " +
                "training_params, storage_path, storage_type, original_url, file_size, is_deleted" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        // 处理权重（默认1.0）
        Object weight = paramMap.get("weight");
        if (weight == null) {
            weight = new java.math.BigDecimal("1.0000");
        } else {
            weight = new java.math.BigDecimal(weight.toString());
        }
        
        // 处理JSON类型的训练参数
        Object trainingParams = paramMap.get("training_params");
        String trainingParamsStr = null;
        if (trainingParams != null) {
            try {
                trainingParamsStr = objectMapper.writeValueAsString(trainingParams);
            } catch (JsonProcessingException e) {
                log.warn("训练参数JSON序列化失败，设置为null", e);
                trainingParamsStr = null;
            }
        }
        
        int affectedRows = getMysqlAdapter().executeUpdate(sql,
                sampleId,
                getStringValue(paramMap.get("name")),
                getStringValue(paramMap.get("description")),
                getStringValue(paramMap.get("label")),
                getStringValue(paramMap.get("category")),
                paramMap.get("access_level"),
                uploader,
                getStringValue(paramMap.get("data_source")),
                getStringValue(paramMap.get("data_processing_status")),
                getStringValue(paramMap.get("missing_value_mark")),
                weight,
                getStringValue(paramMap.get("remark")),
                trainingParamsStr,
                storagePath,
                storageType,
                originalUrl,
                fileSize,
                0  // is_deleted 默认值为 0
                );
        
        return affectedRows > 0;
    }
    
    /**
     * 安全获取字符串值，处理null情况
     */
    private String getStringValue(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }


    /**
     * 修改数据集
     * @param req
     * @param resp
     * @throws IOException
     * 请求格式：JSON
     * 必填参数：sample_id
     * 可选参数：name, description, label, category, access_level, data_source, 
     *          data_processing_status, missing_value_mark, weight, remark, training_params
     */
    private void updateDataset(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 解析JSON请求
            String jsonBody = requestToJson(req);
            if (jsonBody == null || jsonBody.trim().isEmpty()) {
                responseError(resp, 400, "请求体不能为空");
                return;
            }
            
            JSONObject jsonObj = JSONUtil.parseObj(jsonBody);
            
            // 必填参数校验
            String sampleId = jsonObj.getStr("sample_id");
            if (sampleId == null || sampleId.trim().isEmpty()) {
                responseError(resp, 400, "sample_id参数不能为空");
                return;
            }
            
            // 检查数据集是否存在
            String checkSql = "SELECT sample_id FROM dataset_upload WHERE sample_id = ? AND is_deleted = 0";
            List<Map<String, Object>> checkResult = getMysqlAdapter().select(checkSql, sampleId);
            if (checkResult == null || checkResult.isEmpty()) {
                responseError(resp, 404, "数据集不存在或已被删除");
                return;
            }
            
            // 构建更新SQL（只更新提供的字段）
            List<String> updateFields = new ArrayList<>();
            List<Object> updateParams = new ArrayList<>();
            
            // 可更新的字段列表
            String[] updatableFields = {
                "name", "description", "label", "category", "access_level",
                "data_source", "data_processing_status", "missing_value_mark",
                "weight", "remark", "training_params"
            };
            
            for (String field : updatableFields) {
                if (jsonObj.containsKey(field)) {
                    Object value = jsonObj.get(field);
                    
                    if (value == null) {
                        updateFields.add(field + " = ?");
                        updateParams.add(null);
                    } else if ("access_level".equals(field)) {
                        try {
                            Integer accessLevel = jsonObj.getInt(field);
                            if (accessLevel < 1 || accessLevel > 3) {
                                responseError(resp, 400, "access_level必须是1、2或3");
                                return;
                            }
                            updateFields.add(field + " = ?");
                            updateParams.add(accessLevel);
                        } catch (Exception e) {
                            responseError(resp, 400, "access_level必须是整数");
                            return;
                        }
                    } else if ("weight".equals(field)) {
                        try {
                            java.math.BigDecimal weight = new java.math.BigDecimal(value.toString());
                            updateFields.add(field + " = ?");
                            updateParams.add(weight);
                        } catch (Exception e) {
                            responseError(resp, 400, "weight必须是数字");
                            return;
                        }
                    } else if ("training_params".equals(field)) {
                        try {
                            String trainingParamsStr = objectMapper.writeValueAsString(value);
                            updateFields.add(field + " = ?");
                            updateParams.add(trainingParamsStr);
                        } catch (Exception e) {
                            responseError(resp, 400, "training_params格式错误");
                            return;
                        }
                    } else {
                        updateFields.add(field + " = ?");
                        updateParams.add(value.toString());
                    }
                }
            }
            
            // 检查是否有要更新的字段
            if (updateFields.isEmpty()) {
                responseError(resp, 400, "至少需要提供一个可更新的字段");
                return;
            }
            
            // 构建UPDATE SQL
            StringBuilder sqlBuilder = new StringBuilder("UPDATE dataset_upload SET ");
            sqlBuilder.append(String.join(", ", updateFields));
            sqlBuilder.append(" WHERE sample_id = ? AND is_deleted = 0");
            updateParams.add(sampleId);
            
            // 执行更新
            int affectedRows = getMysqlAdapter().executeUpdate(sqlBuilder.toString(), updateParams.toArray());
            
            if (affectedRows > 0) {
                result.put("code", 200);
                result.put("msg", "数据集更新成功");
                Map<String, Object> data = new HashMap<>();
                data.put("sample_id", sampleId);
                result.put("data", data);
                responsePrint(resp, objectMapper.writeValueAsString(result));
            } else {
                responseError(resp, 500, "数据集更新失败");
            }
            
        } catch (Exception e) {
            log.error("修改数据集失败", e);
            responseError(resp, 500, "修改数据集失败：" + e.getMessage());
        }
    }
    
    /**
     * 删除数据集（软删除，支持批量删除）
     * @param req
     * @param resp
     * @throws IOException
     * 请求格式：JSON
     * 参数：sample_id（String）或 sample_ids（List<String>），支持单个或批量删除
     */
    private void deleteDataset(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 解析JSON请求
            String jsonBody = requestToJson(req);
            if (jsonBody == null || jsonBody.trim().isEmpty()) {
                responseError(resp, 400, "请求体不能为空");
                return;
            }
            
            JSONObject jsonObj = JSONUtil.parseObj(jsonBody);
            
            // 获取sample_id列表（支持单个或批量）
            List<String> sampleIds = new ArrayList<>();
            
            if (jsonObj.containsKey("sample_ids")) {
                // 批量删除
                Object sampleIdsObj = jsonObj.get("sample_ids");
                if (sampleIdsObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> idsList = (List<Object>) sampleIdsObj;
                    for (Object id : idsList) {
                        if (id != null) {
                            sampleIds.add(id.toString().trim());
                        }
                    }
                } else if (sampleIdsObj instanceof String) {
                    // 如果是字符串，尝试按逗号分割
                    String[] ids = sampleIdsObj.toString().split(",");
                    for (String id : ids) {
                        if (id != null && !id.trim().isEmpty()) {
                            sampleIds.add(id.trim());
                        }
                    }
                }
            } else if (jsonObj.containsKey("sample_id")) {
                // 单个删除
                String sampleId = jsonObj.getStr("sample_id");
                if (sampleId != null && !sampleId.trim().isEmpty()) {
                    sampleIds.add(sampleId.trim());
                }
            }
            
            if (sampleIds.isEmpty()) {
                responseError(resp, 400, "sample_id或sample_ids参数不能为空");
                return;
            }
            
            // 过滤掉空值
            sampleIds.removeIf(String::isEmpty);
            if (sampleIds.isEmpty()) {
                responseError(resp, 400, "sample_id列表不能为空");
                return;
            }
            
            // 构建批量更新的SQL（使用IN子句）
            StringBuilder sqlBuilder = new StringBuilder("UPDATE dataset_upload SET is_deleted = 1 WHERE sample_id IN (");
            for (int i = 0; i < sampleIds.size(); i++) {
                if (i > 0) {
                    sqlBuilder.append(", ");
                }
                sqlBuilder.append("?");
            }
            sqlBuilder.append(") AND is_deleted = 0");
            
            // 执行批量软删除
            int affectedRows = getMysqlAdapter().executeUpdate(sqlBuilder.toString(), sampleIds.toArray());
            
            if (affectedRows > 0) {
                result.put("code", 200);
                result.put("msg", "数据集删除成功");
                Map<String, Object> data = new HashMap<>();
                data.put("deleted_count", affectedRows);
                data.put("sample_ids", sampleIds);
                result.put("data", data);
                responsePrint(resp, objectMapper.writeValueAsString(result));
            } else {
                responseError(resp, 404, "未找到要删除的数据集或数据集已被删除");
            }
            
        } catch (Exception e) {
            log.error("删除数据集失败", e);
            responseError(resp, 500, "删除数据集失败：" + e.getMessage());
        }
    }

    /**
     * 响应错误信息
     */
    private void responseError(HttpServletResponse resp, int code, String message) throws IOException {
        resp.setStatus(code);
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> error = new HashMap<>();
        error.put("code", code);
        error.put("msg", message);
        responsePrint(resp, objectMapper.writeValueAsString(error));
    }

}
