package ai.servlet.api;

import ai.config.ContextLoader;
import ai.config.pojo.ApiConfig;
import ai.database.impl.MysqlAdapter;
import ai.servlet.BaseServlet;
import ai.servlet.dto.*;
import com.google.gson.Gson;
import org.apache.commons.io.IOUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModelApiServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;
    protected Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);

        if (method.equals("download")) {
            this.downloadModel(req, resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);

        if (method.equals("validate")) {
            this.validateModel(req, resp);
        } else if (method.equals("predict")) {
            this.predictModel(req, resp);
        } else if (method.equals("export")) {
            this.exportModel(req, resp);
        }
    }

    public void validateModel(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            String jsonString = IOUtils.toString(req.getInputStream(), StandardCharsets.UTF_8);
            ModelValidateRequest request = gson.fromJson(jsonString, ModelValidateRequest.class);

            String baseUrl = getTrainPlatformBaseUrl();
            if (baseUrl == null) {
                result.put("status", "failed");
                result.put("message", "训练平台配置未找到");
                responsePrint(resp, toJson(result));
                return;
            }

            String validateUrl = baseUrl + "/api/model/validate";
            String responseStr = callTrainPlatformApi(validateUrl, gson.toJson(request));
            
            try {
                ModelValidateResponse response = gson.fromJson(responseStr, ModelValidateResponse.class);
                
                if ("验证成功".equals(response.getStatus())) {
                    saveValidationRecord(request, response);
                    
                    result.put("status", "success");
                    result.put("data", response);
                } else {
                    result.put("status", "failed");
                    result.put("message", response.getStatus());
                }
            } catch (Exception e) {
                result.put("status", "failed");
                result.put("message", "解析训练平台响应失败: " + responseStr);
            }
            
        } catch (Exception e) {
            result.put("status", "failed");
            result.put("message", "模型验证失败: " + e.getMessage());
        }
        
        responsePrint(resp, toJson(result));
    }

    public void predictModel(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            String jsonString = IOUtils.toString(req.getInputStream(), StandardCharsets.UTF_8);
            ModelPredictRequest request = gson.fromJson(jsonString, ModelPredictRequest.class);

            String baseUrl = getTrainPlatformBaseUrl();
            if (baseUrl == null) {
                result.put("status", "failed");
                result.put("message", "训练平台配置未找到");
                responsePrint(resp, toJson(result));
                return;
            }

            String predictUrl = baseUrl + "/api/model/predict";
            String responseStr = callTrainPlatformApi(predictUrl, gson.toJson(request));
            System.out.println("训练平台预测响应: " + responseStr);
            
            try {
                ModelPredictResponse response = gson.fromJson(responseStr, ModelPredictResponse.class);
                
                if ("预测成功".equals(response.getStatus())) {
                    savePredictionRecord(request, response);
                    
                    result.put("status", "success");
                    result.put("data", response);
                } else {
                    result.put("status", "failed");
                    result.put("message", response.getStatus());
                }
            } catch (Exception e) {
                result.put("status", "failed");
                result.put("message", "解析训练平台响应失败: " + responseStr);
            }
            
        } catch (Exception e) {
            result.put("status", "failed");
            result.put("message", "模型预测失败: " + e.getMessage());
        }
        
        responsePrint(resp, toJson(result));
    }

    public void exportModel(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            String jsonString = IOUtils.toString(req.getInputStream(), StandardCharsets.UTF_8);
            ModelExportRequest request = gson.fromJson(jsonString, ModelExportRequest.class);

            String baseUrl = getTrainPlatformBaseUrl();
            if (baseUrl == null) {
                result.put("status", "failed");
                result.put("message", "训练平台配置未找到");
                responsePrint(resp, toJson(result));
                return;
            }

            String exportUrl = baseUrl + "/api/model/export";
            String responseStr = callTrainPlatformApi(exportUrl, gson.toJson(request));
            System.out.println("训练平台导出响应: " + responseStr);
            
            try {
                ModelExportResponse response = gson.fromJson(responseStr, ModelExportResponse.class);
                
                if ("导出成功".equals(response.getStatus())) {
                    saveExportRecord(request, response);
                    
                    result.put("status", "success");
                    result.put("data", response);
                } else {
                    result.put("status", "failed");
                    result.put("message", response.getStatus());
                }
            } catch (Exception e) {
                result.put("status", "failed");
                result.put("message", "解析训练平台响应失败: " + responseStr);
            }
            
        } catch (Exception e) {
            result.put("status", "failed");
            result.put("message", "模型导出失败: " + e.getMessage());
        }
        
        responsePrint(resp, toJson(result));
    }

    public void downloadModel(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String filePath = req.getParameter("file_path");
        
        if (filePath == null || filePath.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\": \"file_path参数不能为空\"}");
            return;
        }

        try {
            String baseUrl = getTrainPlatformBaseUrl();
            if (baseUrl == null) {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.getWriter().write("{\"error\": \"训练平台配置未找到\"}");
                return;
            }

            String downloadUrl = baseUrl + "/api/model/download?file_path=" + java.net.URLEncoder.encode(filePath, "UTF-8");
            System.out.println("下载URL: " + downloadUrl);
            
            HttpURLConnection connection = (HttpURLConnection) new java.net.URL(downloadUrl).openConnection();
            connection.setRequestMethod("GET");
            
            int responseCode = connection.getResponseCode();
            System.out.println("训练平台响应码: " + responseCode);
            
            if (responseCode == 200) {
                String fileName = filePath.substring(filePath.lastIndexOf("/") + 1);
                resp.setContentType("application/octet-stream");
                resp.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
                
                try (InputStream inputStream = connection.getInputStream();
                     OutputStream outputStream = resp.getOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }
                
                saveDownloadRecord(filePath, fileName);
            } else {
                String errorMessage = "文件不存在或无法访问";
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    if (errorResponse.length() > 0) {
                        errorMessage = errorResponse.toString();
                    }
                } catch (Exception e) {
                    System.out.println("读取错误响应失败: " + e.getMessage());
                }
                System.out.println("训练平台错误响应: " + errorMessage);
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"error\": \"" + errorMessage + "\"}");
            }
            
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\": \"下载失败: " + e.getMessage() + "\"}");
        }
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

    private String callTrainPlatformApi(String url, String requestBody) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new java.net.URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        
        try (OutputStream os = connection.getOutputStream()) {
            os.write(requestBody.getBytes(StandardCharsets.UTF_8));
        }
        
        int responseCode = connection.getResponseCode();
        if (responseCode == 200) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                return response.toString();
            }
        } else {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                StringBuilder errorResponse = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    errorResponse.append(line);
                }
                return errorResponse.toString();
            }
        }
    }

    private void saveValidationRecord(ModelValidateRequest request, ModelValidateResponse response) {
        try {
            MysqlAdapter mysqlAdapter = new MysqlAdapter("mysql");
            String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            
            String sql = "INSERT INTO model_validations (model_path, dataset_path, use_gpu, imgsz, mAP50, mAP50_95, `precision`, recall, box_loss, cls_loss, obj_loss, status, created_at, user_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'completed', ?, ?)";
            
            ModelValidateResponse.Metrics metrics = response.getMetrics();
            
            System.out.println("开始保存模型验证记录...");
            System.out.println("metrics: " + gson.toJson(metrics));
            
            // 从实际响应中提取数据
            double mAP50 = metrics.getMAP50();
            double mAP50_95 = metrics.getMAP50_95();
            double precision = metrics.getPrecision();
            double recall = metrics.getRecall();
            
            // 从嵌套的metrics中提取loss数据
            Double boxLoss = null;
            Double clsLoss = null;
            Double objLoss = null;
            
            if (metrics.getLoss_metrics() != null) {
                boxLoss = metrics.getLoss_metrics().getBox_loss();
                clsLoss = metrics.getLoss_metrics().getCls_loss();
                objLoss = metrics.getLoss_metrics().getObj_loss();
            }
            
            int result = mysqlAdapter.executeUpdate(sql, 
                request.getModel_path(), 
                request.getDataset_path(), 
                request.isUse_gpu(), 
                request.getImgsz(),
                mAP50,
                mAP50_95,
                precision,
                recall,
                boxLoss,
                clsLoss,
                objLoss,
                currentTime,
                "system"
            );
            
            System.out.println("模型验证记录保存结果: " + result);
            
        } catch (Exception e) {
            System.out.println("保存模型验证记录失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void savePredictionRecord(ModelPredictRequest request, ModelPredictResponse response) {
        try {
            MysqlAdapter mysqlAdapter = new MysqlAdapter("mysql");
            String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            
            String sql = "INSERT INTO model_predictions (model_path, image_path, use_gpu, conf_threshold, iou_threshold, save_result, result_save_path, predict_time, detection_count, status, created_at, user_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'completed', ?, ?)";
            
            System.out.println("开始保存模型预测记录...");
            int result = mysqlAdapter.executeUpdate(sql, 
                request.getModel_path(), 
                request.getImage_path(), 
                request.isUse_gpu(), 
                request.getConf_threshold(),
                request.getIou_threshold(),
                request.isSave_result(),
                response.getResult_save_path(),
                response.getPredict_time(),
                response.getPredictions() != null ? response.getPredictions().size() : 0,
                currentTime,
                "system"
            );
            
            System.out.println("模型预测记录保存结果: " + result);
            
        } catch (Exception e) {
            System.out.println("保存模型预测记录失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void saveExportRecord(ModelExportRequest request, ModelExportResponse response) {
        try {
            MysqlAdapter mysqlAdapter = new MysqlAdapter("mysql");
            String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            
            String sql = "INSERT INTO model_exports (model_path, export_format, use_gpu, imgsz, simplify, export_path, model_size, status, created_at, user_id) VALUES (?, ?, ?, ?, ?, ?, ?, 'completed', ?, ?)";
            
            mysqlAdapter.executeUpdate(sql, 
                request.getModel_path(), 
                request.getExport_format(), 
                request.isUse_gpu(), 
                request.getImgsz(),
                request.isSimplify(),
                response.getExport_path(),
                response.getModel_size(),
                currentTime,
                "system"
            );
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveDownloadRecord(String filePath, String fileName) {
        try {
            MysqlAdapter mysqlAdapter = new MysqlAdapter("mysql");
            String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            
            String sql = "INSERT INTO model_downloads (file_path, file_name, download_count, last_download_at, created_at, user_id) VALUES (?, ?, 1, ?, ?, ?) ON DUPLICATE KEY UPDATE download_count = download_count + 1, last_download_at = ?";
            
            mysqlAdapter.executeUpdate(sql, 
                filePath, 
                fileName, 
                currentTime,
                currentTime,
                "system",
                currentTime
            );
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
