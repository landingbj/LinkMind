package ai.servlet;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import ai.common.pojo.*;
import ai.dto.ProgressTrackerEntity;
import ai.medusa.MedusaService;
import ai.medusa.pojo.InstructionData;
import ai.medusa.pojo.InstructionPairRequest;
import ai.migrate.service.UploadFileService;
import ai.utils.ExcelSqlUtil;
import ai.utils.LRUCacheUtil;
import ai.vector.*;
import ai.vector.pojo.UpsertRecord;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import ai.utils.MigrateGlobal;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

@Slf4j
public class UploadFileServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();
    private final FileService fileService = new FileService();
    private static final Configuration config = MigrateGlobal.config;
    private final VectorDbService vectorDbService = new VectorDbService(config);
    private final UploadFileService uploadFileService = new UploadFileService();
    private final VectorStoreService vectorStoreService = new VectorStoreService();
    private final MedusaService medusaService = new MedusaService();
    private static final String UPLOAD_DIR = "/upload";

    private static final ExecutorService uploadExecutorService = Executors.newFixedThreadPool(5);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);

        // 下载文件接口不需要设置默认Content-Type，由downloadFile方法自己设置
        if (method.equals("downloadFile")) {
            this.downloadFile(req, resp);
        } else {
            // 其他接口设置默认Content-Type
            resp.setHeader("Content-Type", "text/html;charset=utf-8");
            
            if (method.equals("uploadLearningFile") || method.equals("upload")) {
                this.uploadLearningFile(req, resp);
            } else if (method.equals("uploadImageFile")) {
                this.uploadImageFile(req, resp);
            } else if (method.equals("uploadVideoFile")) {
                this.uploadVideoFile(req, resp);
            } else if (method.equals("deleteFile")) {
                this.deleteFile(req, resp);
            } else if (method.equals("getUploadFileList")) {
                this.getUploadFileList(req, resp);
            } else if (method.equals("pairing")) {
                this.pairing(req, resp);
            } else if (method.equals("asynchronousUpload")) {
                this.asynchronousUpload(req, resp);
            } else if (method.equals("getProgress")) {
                this.getProgress(req, resp);
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doGet(req, resp);
    }

    private void pairing(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        InstructionPairRequest instructionPairRequest = mapper.readValue(requestToJson(req), InstructionPairRequest.class);
        long timestamp = Instant.now().toEpochMilli();
        new Thread(() -> {
            List<InstructionData> instructionDataList = instructionPairRequest.getData();
            String category = instructionPairRequest.getCategory();
            String level = Optional.ofNullable(instructionPairRequest.getLevel()).orElse("user");
            Map<String, String> qaMap = new HashMap<>();
            for (InstructionData data : instructionDataList) {
                for (String instruction : data.getInstruction()) {
                    instruction = instruction.trim();
                    String output = data.getOutput().trim();
                    qaMap.put(instruction, output);
                    Map<String, String> metadata = new HashMap<>();
                    metadata.put("category", category);
                    metadata.put("level", level);
                    metadata.put("filename", "");
                    metadata.put("seq", Long.toString(timestamp));
                    metadata.put("source", VectorStoreConstant.FileChunkSource.FILE_CHUNK_SOURCE_QA);
                    List<UpsertRecord> upsertRecords = new ArrayList<>();
                    upsertRecords.add(UpsertRecord.newBuilder()
                            .withMetadata(metadata)
                            .withDocument(instruction)
                            .build());
                    upsertRecords.add(UpsertRecord.newBuilder()
                            .withMetadata(new HashMap<>(metadata))
                            .withDocument(output)
                            .build());
                    String s = instruction.replaceAll("\n","");
                    VectorCacheLoader.put2L2(s, timestamp, output);
                    vectorStoreService.upsertCustomVectors(upsertRecords, category, true);
                }
            }
            medusaService.load(qaMap, category);
        }).start();

        PrintWriter out = resp.getWriter();
        Map<String, Object> map = new HashMap<>();
        map.put("status", "success");
        out.print(gson.toJson(map));
        out.flush();
        out.close();
    }

    private void deleteFile(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        req.setCharacterEncoding("utf-8");
        resp.setContentType("application/json;charset=utf-8");
        String category = req.getParameter("category");
        List<String> idList = gson.fromJson(requestToJson(req), new TypeToken<List<String>>() {
        }.getType());
        if(StrUtil.isBlank(category)) {
            vectorDbService.deleteDoc(idList);
        } else {
            vectorDbService.deleteDoc(idList, category);
        }
        uploadFileService.deleteUploadFile(idList);
        if (ExcelSqlUtil.isConnect()||ExcelSqlUtil.isSqlietConnect()){
            ExcelSqlUtil.deleteListSql(idList);
        }
        Map<String, Object> map = new HashMap<>();
        map.put("status", "success");
        PrintWriter out = resp.getWriter();
        out.print(gson.toJson(map));
        out.flush();
        out.close();
    }

    private void getUploadFileList(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setHeader("Content-Type", "application/json;charset=utf-8");
        int pageSize = Integer.MAX_VALUE;
        int pageNumber = 1;

        String category = req.getParameter("category");
        String userId = req.getParameter("lagiUserId");

        if (req.getParameter("pageNumber") != null) {
            pageSize = Integer.parseInt(req.getParameter("pageSize"));
            pageNumber = Integer.parseInt(req.getParameter("pageNumber"));
        }

        Map<String, Object> map = new HashMap<>();
        List<UploadFile> result = null;
        int totalRow = 0;
        try {
            result = uploadFileService.getUploadFileList(pageNumber, pageSize, category, userId);
            totalRow = uploadFileService.getTotalRow(category, userId);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        if (result != null) {
            map.put("status", "success");
            int totalPage = (int) Math.ceil((double) totalRow / pageSize);
            map.put("totalRow", totalRow);
            map.put("totalPage", totalPage);
            map.put("pageNumber", pageNumber);
            map.put("pageSize", pageSize);
            map.put("data", result);
        } else {
            map.put("status", "failed");
        }
        PrintWriter out = resp.getWriter();
        out.print(gson.toJson(map));
        out.flush();
        out.close();
    }

    // 下载文件
    private void downloadFile(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String filePath = req.getParameter("filePath");
        String fileName = req.getParameter("fileName");
        
        log.info("下载文件请求 - filePath: {}, fileName: {}", filePath, fileName);
        
        // 参数验证
        if (filePath == null || filePath.trim().isEmpty()) {
            log.error("filePath参数为空");
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "filePath参数不能为空");
            return;
        }
        if (fileName == null || fileName.trim().isEmpty()) {
            // 从filePath中提取文件名
            int lastSeparator = Math.max(filePath.lastIndexOf(File.separator), filePath.lastIndexOf("/"));
            if (lastSeparator >= 0) {
                fileName = filePath.substring(lastSeparator + 1);
            } else {
                fileName = filePath;
            }
        }
        
        // 获取上传目录 - 尝试多种方式
        String uploadDir = null;
        try {
            uploadDir = getServletContext().getRealPath(UPLOAD_DIR);
            log.info("getRealPath({}) 返回: {}", UPLOAD_DIR, uploadDir);
        } catch (Exception e) {
            log.warn("getRealPath失败", e);
        }
        
        if (uploadDir == null || uploadDir.trim().isEmpty()) {
            // 尝试使用Web应用根目录
            try {
                String webRoot = getServletContext().getRealPath("/");
                if (webRoot != null) {
                    uploadDir = webRoot + UPLOAD_DIR.substring(1);
                    log.info("使用备用路径: {}", uploadDir);
                }
            } catch (Exception e) {
                log.warn("获取备用路径失败", e);
            }
        }
        
        if (uploadDir == null || uploadDir.trim().isEmpty()) {
            log.error("无法获取上传目录路径");
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "无法获取上传目录路径");
            return;
        }
        
        // 构建完整文件路径 - 处理filePath可能包含路径分隔符的情况
        String fullPath;
        if (filePath.contains(File.separator) || filePath.contains("/")) {
            // filePath已经包含路径，直接拼接
            fullPath = uploadDir + File.separator + filePath;
        } else {
            // filePath只是文件名
            fullPath = uploadDir + File.separator + filePath;
        }
        
        // 标准化路径（处理路径分隔符不一致的问题）
        fullPath = fullPath.replace("/", File.separator).replace("\\", File.separator);
        
        File file = new File(fullPath);
        log.info("尝试访问文件: {}", file.getAbsolutePath());
        log.info("文件是否存在: {}, 是否为文件: {}, 文件大小: {} bytes", 
                file.exists(), file.isFile(), file.exists() ? file.length() : 0);
        
        // 检查文件是否存在
        if (!file.exists()) {
            log.error("文件不存在: {}", file.getAbsolutePath());
            // 列出目录内容以便调试
            File dir = file.getParentFile();
            if (dir != null && dir.exists() && dir.isDirectory()) {
                String[] files = dir.list();
                log.info("目录 {} 中的文件: {}", dir.getAbsolutePath(), 
                        files != null ? String.join(", ", files) : "无");
            }
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "文件不存在: " + filePath);
            return;
        }
        
        if (!file.isFile()) {
            log.error("路径不是文件: {}", file.getAbsolutePath());
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "路径不是文件: " + filePath);
            return;
        }
        
        long fileSize = file.length();
        if (fileSize == 0) {
            log.warn("文件大小为0: {}", file.getAbsolutePath());
        }
        
        // 根据文件扩展名设置Content-Type
        String contentType = getContentTypeByFileName(fileName);
        resp.setContentType(contentType);
        
        // 设置文件下载响应头
        String encodedFileName = URLEncoder.encode(fileName, "UTF-8");
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"");
        resp.setHeader("Content-Length", String.valueOf(fileSize));
        
        log.info("开始下载文件: {}, 大小: {} bytes, Content-Type: {}", fileName, fileSize, contentType);
        
        // 读取文件并写入响应流
        FileInputStream fileInputStream = null;
        OutputStream outputStream = null;
        long bytesWritten = 0;
        try {
            fileInputStream = new FileInputStream(file);
            outputStream = resp.getOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                bytesWritten += bytesRead;
            }
            outputStream.flush();
            log.info("文件下载完成: {}, 已写入: {} bytes", fileName, bytesWritten);
        } catch (IOException e) {
            log.error("文件下载失败: {}, 已写入: {} bytes", file.getAbsolutePath(), bytesWritten, e);
            if (!resp.isCommitted()) {
                resp.reset(); // 重置响应
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "文件下载失败: " + e.getMessage());
            }
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException e) {
                    log.warn("关闭文件输入流失败", e);
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    log.warn("关闭响应输出流失败", e);
                }
            }
        }
    }
    
    /**
     * 根据文件名获取Content-Type
     */
    private String getContentTypeByFileName(String fileName) {
        if (fileName == null) {
            return "application/octet-stream";
        }
        
        String lowerFileName = fileName.toLowerCase();
        if (lowerFileName.endsWith(".pdf")) {
            return "application/pdf";
        } else if (lowerFileName.endsWith(".doc")) {
            return "application/msword";
        } else if (lowerFileName.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else if (lowerFileName.endsWith(".xls")) {
            return "application/vnd.ms-excel";
        } else if (lowerFileName.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else if (lowerFileName.endsWith(".ppt")) {
            return "application/vnd.ms-powerpoint";
        } else if (lowerFileName.endsWith(".pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        } else if (lowerFileName.endsWith(".txt")) {
            return "text/plain;charset=utf-8";
        } else if (lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerFileName.endsWith(".png")) {
            return "image/png";
        } else if (lowerFileName.endsWith(".gif")) {
            return "image/gif";
        } else if (lowerFileName.endsWith(".zip")) {
            return "application/zip";
        } else if (lowerFileName.endsWith(".rar")) {
            return "application/x-rar-compressed";
        } else {
            return "application/octet-stream";
        }
    }

    private void uploadVideoFile(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession();

        JsonObject jsonResult = new JsonObject();
        jsonResult.addProperty("status", "failed");
        DiskFileItemFactory factory = new DiskFileItemFactory();
        ServletFileUpload upload = new ServletFileUpload(factory);
        upload.setFileSizeMax(MigrateGlobal.VIDEO_FILE_SIZE_LIMIT);
        upload.setSizeMax(MigrateGlobal.VIDEO_FILE_SIZE_LIMIT);
        String uploadDir = getServletContext().getRealPath(UPLOAD_DIR);
        if (!new File(uploadDir).isDirectory()) {
            new File(uploadDir).mkdirs();
        }

        String lastFilePath = "";

        try {
            // 存储文件
            List<?> fileItems = upload.parseRequest(req);
            Iterator<?> it = fileItems.iterator();

            while (it.hasNext()) {
                FileItem fi = (FileItem) it.next();
                if (!fi.isFormField()) {
                    String fileName = fi.getName();
                    File file;
                    String newName;
                    do {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
                        newName = sdf.format(new Date()) + ("" + Math.random()).substring(2, 6);
                        newName = newName + fileName.substring(fileName.lastIndexOf("."));
                        file = new File(uploadDir + File.separator + newName);
                        lastFilePath = uploadDir + File.separator + newName;
                        session.setAttribute("last_video_file", lastFilePath);
                        jsonResult.addProperty("status", "success");
                    } while (file.exists());
                    fi.write(file);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        PrintWriter out = resp.getWriter();
        out.write(gson.toJson(jsonResult));
        out.flush();
        out.close();
    }

    private void uploadImageFile(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession();

        JsonObject jsonResult = new JsonObject();
        jsonResult.addProperty("status", "failed");
        DiskFileItemFactory factory = new DiskFileItemFactory();
        ServletFileUpload upload = new ServletFileUpload(factory);
        upload.setFileSizeMax(MigrateGlobal.IMAGE_FILE_SIZE_LIMIT);
        upload.setSizeMax(MigrateGlobal.IMAGE_FILE_SIZE_LIMIT);
        String uploadDir = getServletContext().getRealPath(UPLOAD_DIR);
        if (!new File(uploadDir).isDirectory()) {
            new File(uploadDir).mkdirs();
        }

        String lastFilePath = "";

        try {
            // 存储文件
            List<?> fileItems = upload.parseRequest(req);
            Iterator<?> it = fileItems.iterator();

            while (it.hasNext()) {
                FileItem fi = (FileItem) it.next();
                if (!fi.isFormField()) {
                    String fileName = fi.getName();
                    File file = null;
                    String newName = null;
                    do {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
                        newName = sdf.format(new Date()) + ("" + Math.random()).substring(2, 6);
                        newName = newName + fileName.substring(fileName.lastIndexOf("."));
                        file = new File(uploadDir + File.separator + newName);
                        lastFilePath = uploadDir + File.separator + newName;
                        session.setAttribute("last_image_file", lastFilePath);
                        jsonResult.addProperty("status", "success");
                    } while (file.exists());
                    fi.write(file);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        PrintWriter out = resp.getWriter();
        out.write(gson.toJson(jsonResult));
        out.flush();
        out.close();
    }

    private void uploadLearningFile(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession();
        String category = null;
        String level = null;
        String description = null;
        String userId = null;
        JsonObject jsonResult = new JsonObject();
        jsonResult.addProperty("status", "success");
        DiskFileItemFactory factory = new DiskFileItemFactory();
        ServletFileUpload upload = new ServletFileUpload(factory);
        upload.setFileSizeMax(MigrateGlobal.DOC_FILE_SIZE_LIMIT);
        upload.setSizeMax(MigrateGlobal.DOC_FILE_SIZE_LIMIT);
        String uploadDir = getServletContext().getRealPath(UPLOAD_DIR);
        if (!new File(uploadDir).isDirectory()) {
            new File(uploadDir).mkdirs();
        }

        List<File> files = new ArrayList<>();
        Map<String, String> realNameMap = new HashMap<>();

        try {
            List<?> fileItems = upload.parseRequest(req);
            for (Object fileItem : fileItems) {
                FileItem fi = (FileItem) fileItem;
                if (fi.isFormField()) {
                    String fieldName = fi.getFieldName();
                    String fieldValue = fi.getString("UTF-8");
                    if ("category".equals(fieldName)) {
                        category = fieldValue;
                    } else if ("level".equals(fieldName)) {
                        level = fieldValue;
                    } else if ("description".equals(fieldName)) {
                        description = fieldValue;
                    } else if ("userId".equals(fieldName)) {
                        userId = fieldValue;
                    }
                } else if (!fi.isFormField()) {
                    String fileName = fi.getName();
                    File file;
                    String newName;
                    do {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
                        newName = sdf.format(new Date()) + ("" + Math.random()).substring(2, 6);
                        newName = newName + fileName.substring(fileName.lastIndexOf("."));
                        String lastFilePath = uploadDir + File.separator + newName;
                        file = new File(lastFilePath);
                        session.setAttribute(newName, file.toString());
                        session.setAttribute("lastFilePath", lastFilePath);
                    } while (file.exists());
                    fi.write(file);
                    files.add(file);
                    realNameMap.put(file.getName(), fileName);
                }
            }
        } catch (Exception ex) {
            jsonResult.addProperty("msg", "解析文件出现错误");
            ex.printStackTrace();
        }
        String taskId = UUID.randomUUID().toString();
        ProgressTrackerEntity tracker = new ProgressTrackerEntity(taskId);
        LRUCacheUtil.put(taskId, tracker);
        List<Future<?>> futures = new ArrayList<>();
        JsonArray fileList = new JsonArray();
        if (!files.isEmpty()) {


            for (File file : files) {
                if (file.exists() && file.isFile()) {
                    String filename = realNameMap.get(file.getName());
                    if (category == null || category.trim().isEmpty()) {
                        category = "default";
                    }
//                    Future<?> future =uploadExecutorService.submit(new AddDocIndex(file, category, filename, level ,taskId));
//                    futures.add(future);
                    AddDocIndex addDocIndex = new AddDocIndex(file, category, filename, level, taskId);
                    JsonObject fileInfo = addDocIndex.handleAddDocIndexes();
                    JsonObject jsonObject = new JsonObject();
                    jsonObject.addProperty("filename", filename);
                    jsonObject.addProperty("filepath", file.getName());
                    jsonObject.addProperty("fileId", fileInfo.get("fileId").getAsString());
                    jsonObject.add("vectorIds", fileInfo.get("vectorIds").getAsJsonArray());
                    fileList.add(jsonObject);
                }
            }
        }
        String status ="success";
        // 等待所有任务完成
//            for (int i = 0; i< futures.size(); i++ ) {
//                try {
//                    JsonObject fileInfo = (JsonObject)futures.get(i).get();
//                    fileList.get(i).getAsJsonObject().addProperty("fileId", fileInfo.get("fileId").getAsString());
//                    fileList.get(i).getAsJsonObject().add("vectorIds", fileInfo.get("vectorIds").getAsJsonArray());
//                } catch (InterruptedException e) {
//                    //任务终断
//                    status = "failed";
//                    Thread.currentThread().interrupt();
//                } catch (ExecutionException e) {
//                    //执行中异常
//                    status = "failed";
//                    e.printStackTrace();
//                }
//            }
            jsonResult.addProperty("data", fileList.toString());
            if (!jsonResult.has("msg")) {
                jsonResult.addProperty("status", status);
            }
            jsonResult.addProperty("task_id", taskId);
            PrintWriter out = resp.getWriter();
            out.write(gson.toJson(jsonResult));
            out.flush();
            out.close();

    }
    private void asynchronousUpload(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession();
        String category = req.getParameter("category");
        String level = req.getParameter("level");
        String userId = req.getParameter("userId");
        JsonObject jsonResult = new JsonObject();
        jsonResult.addProperty("status", "success");
        DiskFileItemFactory factory = new DiskFileItemFactory();
        ServletFileUpload upload = new ServletFileUpload(factory);
        upload.setFileSizeMax(MigrateGlobal.DOC_FILE_SIZE_LIMIT);
        upload.setSizeMax(MigrateGlobal.DOC_FILE_SIZE_LIMIT);
        String uploadDir = getServletContext().getRealPath(UPLOAD_DIR);
        if (!new File(uploadDir).isDirectory()) {
            new File(uploadDir).mkdirs();
        }

        List<File> files = new ArrayList<>();
        Map<String, String> realNameMap = new HashMap<>();

        try {
            List<?> fileItems = upload.parseRequest(req);
            for (Object fileItem : fileItems) {
                FileItem fi = (FileItem) fileItem;
                if (!fi.isFormField()) {
                    String fileName = fi.getName();
                    File file;
                    String newName;
                    do {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
                        newName = sdf.format(new Date()) + ("" + Math.random()).substring(2, 6);
                        newName = newName + fileName.substring(fileName.lastIndexOf("."));
                        String lastFilePath = uploadDir + File.separator + newName;
                        file = new File(lastFilePath);
                        session.setAttribute(newName, file.toString());
                        session.setAttribute("lastFilePath", lastFilePath);
                    } while (file.exists());
                    fi.write(file);
                    files.add(file);
                    realNameMap.put(file.getName(), fileName);
                }
            }
        } catch (Exception ex) {
            jsonResult.addProperty("msg", "解析文件出现错误");
            ex.printStackTrace();
        }

        String taskId = UUID.randomUUID().toString();
        ProgressTrackerEntity tracker = new ProgressTrackerEntity(taskId);
        LRUCacheUtil.put(taskId, tracker);

        List<Future<?>> futures = new ArrayList<>();
        if (!files.isEmpty()) {
            JsonArray fileList = new JsonArray();
            for (File file : files) {
                if (file.exists() && file.isFile()) {
                    String filename = realNameMap.get(file.getName());
                    Future<?> future = uploadExecutorService.submit(new AddDocIndex(file, category, filename, level, taskId));
                    futures.add(future);
                    JsonObject jsonObject = new JsonObject();
                    jsonObject.addProperty("filename", filename);
                    jsonObject.addProperty("filepath", file.getName());
                    fileList.add(jsonObject);
                }
            }
            jsonResult.add("data", fileList);
        }

        jsonResult.addProperty("task_id", taskId);
        PrintWriter out = resp.getWriter();
        out.write(gson.toJson(jsonResult));
        out.flush();
        out.close();
    }

    private void getProgress(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setHeader("Content-Type", "application/json;charset=utf-8");
        String taskId = req.getParameter("task_id");
        ProgressTrackerEntity tracker = LRUCacheUtil.get(taskId);
        Map<String, Object> map = new HashMap<>();
        if (tracker != null) {
            map.put("status", "success");
            map.put("progress", tracker.getProgress());
            map.put("files", tracker.getFilesSnapshot());
            if (tracker.getProgress() == 100){
                map.put("msg", "上传完毕！");
            }else if(0 < tracker.getProgress()) {
                map.put("msg", "上传中...");
            }else if(0 > tracker.getProgress()) {
                map.put("status", "failed");
                map.put("msg", "上传失败！");
            }
        } else {
            map.put("status", "failed");
            map.put("msg", "未找到该taskId记录！！！");
        }
        PrintWriter out = resp.getWriter();
        out.print(gson.toJson(map));
        out.flush();
        out.close();
    }


    public class AddDocIndex implements Callable<JsonObject> {
        private final VectorDbService vectorDbService = new VectorDbService(config);
        private final File file;
        private final String category;
        private final String filename;
        private final String level;
        private final String taskId;

        public AddDocIndex(File file, String category, String filename, String level,String taskId) {
            this.file = file;
            this.category = category;
            this.filename = filename;
            this.level = level;
            this.taskId = taskId;
        }

        @Override
        public JsonObject call() throws Exception {
            // 1) 先生成 fileId，后面直接返回
            String fileId = UUID.randomUUID().toString().replace("-", "");
//            List<List<String>> vectorIds = addDocIndexes(fileId);
            // 将文件名和vectorIds转成json返回
//            if (vectorIds == null || vectorIds.isEmpty()) {
//                throw new IOException("Failed to add document indexes for file: " + file.getName());
//            }
            CompletableFuture<List<List<String>>> future = addDocIndexesAsync(fileId);
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("fileId", fileId);
//            jsonObject.add("vectorIds", gson.toJsonTree(vectorIds));
            return jsonObject;            // 2) 把结果抛给上层
        }

        public JsonObject handleAddDocIndexes() throws IOException{
            String fileId = UUID.randomUUID().toString().replace("-", "");
            try {
                List<List<String>> vectorIds = addDocIndexes(fileId);
                if (vectorIds == null) {
                    log.error("addDocIndexes returned null for file: {}, category: {}", file.getName(), category);
                    throw new IOException("Failed to add document indexes for file: " + file.getName() + ", category: " + category);
                }
                JsonObject jsonObject = new JsonObject();
                jsonObject.addProperty("fileId", fileId);
                jsonObject.add("vectorIds", gson.toJsonTree(vectorIds));
                return jsonObject;
            } catch (Exception e) {
                log.error("Error in handleAddDocIndexes for file: {}, category: {}", file.getName(), category, e);
                throw new IOException("Failed to add document indexes for file: " + file.getName() + ", category: " + category + ", error: " + e.getMessage(), e);
            }
        }
        private List<List<String>> addDocIndexes(String fileId) throws IOException {
            Map<String, Object> metadatas = new HashMap<>();
            String filepath = file.getName();

            metadatas.put("filename", filename);
            metadatas.put("category", category);
            metadatas.put("filepath", filepath);
            metadatas.put("file_id", fileId);
            if (level == null) {
                metadatas.put("level", "user");
            } else {
                metadatas.put("level", level);
            }
            ProgressTrackerEntity tracker = LRUCacheUtil.get(taskId);
            try {
                if (tracker != null) {
                    tracker.setProgress(30);
                    LRUCacheUtil.put(taskId, tracker);
                }

                if (category == null || category.trim().isEmpty()) {
                    log.error("Category is null or empty for file: {}", file.getName());
                    throw new IOException("Category is required for file upload");
                }

                log.info("Adding file vectors for file: {}, category: {}", file.getName(), category);
                List<List<String>> vectorIds = vectorDbService.addFileVectors(this.file, metadatas, category);
                
                if (vectorIds == null) {
                    log.error("vectorDbService.addFileVectors returned null for file: {}, category: {}", file.getName(), category);
                    throw new IOException("Failed to add file vectors, returned null");
                }

                try {
                    UploadFile entity = new UploadFile();
                    entity.setCategory(category);
                    entity.setFilename(filename);
                    entity.setFilepath(filepath);
                    entity.setFileId(fileId);
                    entity.setCreateTime(new Date().getTime());
                    uploadFileService.addUploadFile(entity);
                } catch (SQLException e) {
                    log.warn("Failed to save upload file record to database: {}", e.getMessage());
                }

                if (tracker != null) {
                    tracker.setProgress(100);
                    LRUCacheUtil.put(taskId, tracker);
                }
                return vectorIds;
            } catch (IOException e) {
                if (tracker != null) {
                    tracker.setProgress(-1);
                    LRUCacheUtil.put(taskId, tracker);
                }
                log.error("IOException in addDocIndexes for file: {}, category: {}", file.getName(), category, e);
                throw e;
            } catch (Exception e) {
                if (tracker != null) {
                    tracker.setProgress(-1);
                    LRUCacheUtil.put(taskId, tracker);
                }
                log.error("Unexpected error in addDocIndexes for file: {}, category: {}", file.getName(), category, e);
                throw new IOException("Failed to add document indexes: " + e.getMessage(), e);
            }
        }

        private CompletableFuture<List<List<String>>> addDocIndexesAsync(String fileId) {
            Map<String, Object> metadatas = new HashMap<>();
            String filepath = file.getName();

            metadatas.put("filename", filename);
            metadatas.put("category", category);
            metadatas.put("filepath", filepath);
            metadatas.put("file_id", fileId);
            if (level == null) {
                metadatas.put("level", "user");
            } else {
                metadatas.put("level", level);
            }
            ProgressTrackerEntity tracker = LRUCacheUtil.get(taskId);
            tracker.updateFileInfo(filepath, fileId, filename);
            IngestListener listener = new IngestListener() {
                @Override
                public void onSplitReady(String fid, List<List<FileChunkResponse.Document>> docs) {
                    ProgressTrackerEntity t = LRUCacheUtil.get(taskId);
                    if (t != null) {
                        t.saveSplitResult(file.getName(), docs); // 你实现：按文件保存切片数据
                        // 也可以顺便更新阶段进度：比如 30%
                        t.updateFileStage(file.getName(), 30, "文件分片完成", "组数=" + docs.size());
                        LRUCacheUtil.put(taskId, t);
                    }
                }

                @Override
                public void onQaGroupReady(String fid, int groupIndex, List<FileChunkResponse.Document> qaDocs) {
                    ProgressTrackerEntity t = LRUCacheUtil.get(taskId);
                    if (t != null) {
                        t.saveQaGroup(file.getName(), groupIndex, qaDocs); // 你实现：按组保存问答抽取结果
                        t.updateFileStage(file.getName(), 70, "问答抽取进行中", "完成组=" + (groupIndex + 1));
                        LRUCacheUtil.put(taskId, t);
                    }
                }

                @Override
                public void onQaChunk(String fileId, int groupIndex,int docIndex, List<FileChunkResponse.Document> qaDocs) {
                    ProgressTrackerEntity t = LRUCacheUtil.get(taskId);
                    t.saveQaChunk(file.getName(), groupIndex, docIndex, qaDocs);
                }

                @Override
                public void onComplete(String fileId) {
                    ProgressTrackerEntity t = LRUCacheUtil.get(taskId);
                    t.setProgress(100);
                }

                @Override
                public void onError(String fid, Throwable ex) {
                    ProgressTrackerEntity t = LRUCacheUtil.get(taskId);
                    if (t != null) {
                        t.markFailed(file.getName(), ex.getMessage());
                        LRUCacheUtil.put(taskId, t);
                    }
                }
            };
            if (tracker != null) {
                tracker.setProgress(30);
                LRUCacheUtil.put(taskId, tracker);
            }
            return vectorDbService.addFileVectorsAsync(this.file, metadatas, category, listener).whenComplete((allVectorIds,ex)-> {
                ProgressTrackerEntity t = LRUCacheUtil.get(taskId);
                if (t != null) {
                    if (ex == null) {
                        System.out.println("All vectors added for file: " + file.getName() + ", total vectors: " + (allVectorIds == null ? 0 : allVectorIds.size()));
                        for (List<String> vecIds : allVectorIds) {
                            System.out.println("  Chunk with " + (vecIds == null ? 0 : vecIds.size()) + " vectors.");
                            for (String vid : vecIds) {
                                System.out.println("  vectorId: " + vid);
                            }
                        }
                        t.markSuccess(file.getName(), fileId, allVectorIds);
                    }
                    else {
                        t.markFailed(file.getName(), ex.getMessage());
                    }
                }
            });
        }
    }



    protected String requestToJson(HttpServletRequest request) throws IOException {
        InputStream in = request.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = in.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }
        out.close();
        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
