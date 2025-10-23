package ai.servlet.api;

import ai.config.ContextLoader;
import ai.config.pojo.ApiConfig;
import ai.database.impl.MysqlAdapter;
import ai.servlet.BaseServlet;
import ai.servlet.dto.DatasetUploadResponse;
import com.google.gson.Gson;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DatasetApiServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;
    protected Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);

        if (method.equals("list")) {
            this.getDatasetList(req, resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);

        if (method.equals("upload")) {
            this.uploadDataset(req, resp);
        }
    }

    public void uploadDataset(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (!ServletFileUpload.isMultipartContent(req)) {
                result.put("status", "failed");
                result.put("message", "请求必须是multipart/form-data格式");
                responsePrint(resp, toJson(result));
                return;
            }

            DiskFileItemFactory factory = new DiskFileItemFactory();
            ServletFileUpload upload = new ServletFileUpload(factory);
            @SuppressWarnings("unchecked")
            List<FileItem> items = upload.parseRequest(req);

            String datasetName = null;
            String description = null;
            String userId = null;
            FileItem fileItem = null;

            for (FileItem item : items) {
                if (item.isFormField()) {
                    String fieldName = item.getFieldName();
                    String fieldValue = item.getString("UTF-8");
                    
                    switch (fieldName) {
                        case "dataset_name":
                            datasetName = fieldValue;
                            break;
                        case "description":
                            description = fieldValue;
                            break;
                        case "user_id":
                            userId = fieldValue;
                            break;
                    }
                } else {
                    fileItem = item;
                }
            }

            if (datasetName == null || datasetName.trim().isEmpty()) {
                result.put("status", "failed");
                result.put("message", "数据集名称不能为空");
                responsePrint(resp, toJson(result));
                return;
            }

            if (fileItem == null || fileItem.getSize() == 0) {
                result.put("status", "failed");
                result.put("message", "请选择要上传的文件");
                responsePrint(resp, toJson(result));
                return;
            }

            String fileName = fileItem.getName();
            if (!fileName.toLowerCase().endsWith(".zip")) {
                result.put("status", "failed");
                result.put("message", "仅支持zip格式的压缩包");
                responsePrint(resp, toJson(result));
                return;
            }

            String baseUrl = getTrainPlatformBaseUrl();
            if (baseUrl == null) {
                result.put("status", "failed");
                result.put("message", "训练平台配置未找到");
                responsePrint(resp, toJson(result));
                return;
            }

            String uploadUrl = baseUrl + "/api/dataset/upload";
            
            String uploadResponseStr = callTrainPlatformUpload(uploadUrl, datasetName, fileItem);
            System.out.println("训练平台响应: " + uploadResponseStr);
            
            try {
                DatasetUploadResponse uploadResponse = gson.fromJson(uploadResponseStr, DatasetUploadResponse.class);
                
                if ("上传成功".equals(uploadResponse.getStatus()) || "可使用该路径进行训练".equals(uploadResponse.getMessage())) {
                    saveDatasetRecord(datasetName, uploadResponse.getDataset_path(), description, userId, fileItem.getSize());
                    
                    result.put("status", "success");
                    result.put("data", uploadResponse);
                } else {
                    result.put("status", "failed");
                    result.put("message", uploadResponse.getMessage());
                }
            } catch (Exception e) {
                result.put("status", "failed");
                result.put("message", "解析训练平台响应失败: " + uploadResponseStr);
            }
            
        } catch (Exception e) {
            result.put("status", "failed");
            result.put("message", "上传失败: " + e.getMessage());
        }
        
        responsePrint(resp, toJson(result));
    }

    public void getDatasetList(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            String baseUrl = getTrainPlatformBaseUrl();
            if (baseUrl == null) {
                result.put("status", "failed");
                result.put("message", "训练平台配置未找到");
                responsePrint(resp, toJson(result));
                return;
            }

            String listUrl = baseUrl + "/api/dataset/list";
            String response = callTrainPlatformList(listUrl);
            
            result.put("status", "success");
            result.put("data", gson.fromJson(response, Object.class));
            
        } catch (Exception e) {
            result.put("status", "failed");
            result.put("message", "获取数据集列表失败: " + e.getMessage());
        }
        
        responsePrint(resp, toJson(result));
    }

    private String getTrainPlatformBaseUrl() {
        try {
            List<ApiConfig> apis = ContextLoader.configuration.getApis();
            if (apis != null) {
                for (ApiConfig api : apis) {
                    if ("train_platform".equals(api.getName())) {
                        return api.getBase_url();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private String callTrainPlatformUpload(String url, String datasetName, FileItem fileItem) throws Exception {
        String boundary = "----WebKitFormBoundary" + UUID.randomUUID().toString().replace("-", "");
        
        StringBuilder requestBody = new StringBuilder();
        requestBody.append("--").append(boundary).append("\r\n");
        requestBody.append("Content-Disposition: form-data; name=\"dataset_name\"\r\n\r\n");
        requestBody.append(datasetName).append("\r\n");
        
        requestBody.append("--").append(boundary).append("\r\n");
        requestBody.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(fileItem.getName()).append("\"\r\n");
        requestBody.append("Content-Type: application/zip\r\n\r\n");
        
        String requestBodyStr = requestBody.toString();
        
        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setDoOutput(true);
        
        try (OutputStream os = connection.getOutputStream()) {
            os.write(requestBodyStr.getBytes("UTF-8"));
            try (InputStream is = fileItem.getInputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }
            os.write(("\r\n--" + boundary + "--\r\n").getBytes("UTF-8"));
        }
        
        int responseCode = connection.getResponseCode();
        if (responseCode == 200) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                return response.toString();
            }
        } else {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), "UTF-8"))) {
                StringBuilder errorResponse = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    errorResponse.append(line);
                }
                return errorResponse.toString();
            }
        }
    }

    private String callTrainPlatformList(String url) throws Exception {
        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        connection.setRequestMethod("GET");
        
        int responseCode = connection.getResponseCode();
        if (responseCode == 200) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                return response.toString();
            }
        } else {
            throw new Exception("HTTP错误: " + responseCode);
        }
    }

    private void saveDatasetRecord(String datasetName, String datasetPath, String description, String userId, long fileSize) {
        try {
            MysqlAdapter mysqlAdapter = new MysqlAdapter("mysql");
            String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            
            String sql = "INSERT INTO dataset_records (dataset_name, dataset_path, created_at, file_size, description, user_id, status) VALUES (?, ?, ?, ?, ?, ?, 'active')";
            mysqlAdapter.executeUpdate(sql, datasetName, datasetPath, currentTime, fileSize, description, userId);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
