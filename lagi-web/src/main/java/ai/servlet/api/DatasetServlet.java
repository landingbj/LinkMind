package ai.servlet.api;

import ai.config.ContextLoader;
import ai.config.pojo.ApiConfig;
import ai.database.impl.MysqlAdapter;
import ai.servlet.BaseServlet;
import ai.servlet.dto.DatasetUploadResponse;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class DatasetServlet extends BaseServlet {

    private static volatile MysqlAdapter mysqlAdapter = null;
    private static MysqlAdapter getMysqlAdapter() {
        if (mysqlAdapter == null) {
            synchronized (TrainTaskServlet.class) {
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
     */
    private void getDatasetList(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> response = new HashMap<>();
        try {
            String userId = req.getParameter("user_id");

            // 查询数据集列表
            List<Map<String, Object>> datasetList = getDatasetsFromDB(userId);

            response.put("status", "SUCCESS");
            response.put("data", datasetList);
            response.put("code", 200);

            resp.setStatus(200);
            responsePrint(resp, toJson(response));

        } catch (Exception e) {
            resp.setStatus(500);
            response.put("error", "服务器内部错误：" + e.getMessage());
            response.put("code", 500);
            responsePrint(resp, toJson(response));
        }
    }

    private List<Map<String, Object>> getDatasetsFromDB(String userId) {

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM dataset_records ");

        List<Object> params = new ArrayList<>();

        if (userId != null && !userId.isEmpty()) {
            sql.append("WHERE user_id = ? ");
            params.add(userId);
        }
        try {
            List<Map<String, Object>> datasetsRecordsList = getMysqlAdapter().select(sql.toString(), params.toArray());
            return datasetsRecordsList;
        } catch (Exception e) {
            throw new RuntimeException("查询数据集列表失败：" + e.getMessage(), e);
        }
    }

    /**
     * 上传预训练数据集
     * @param req
     * @param resp
     * @throws IOException
     */
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
