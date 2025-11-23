package ai.servlet.api;



import ai.config.ContextLoader;
import ai.config.pojo.ApiConfig;
import ai.database.impl.MysqlAdapter;
import ai.dto.TrainingTasks;
import ai.servlet.BaseServlet;
import ai.servlet.dto.ModelValidateRequest;
import ai.servlet.dto.ModelValidateResponse;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ModelTrainingServlet extends BaseServlet {

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
            getTrainList(req, resp);
        } else if (method.equals("pretrain")){
            getPretrainModelList(req,resp);
        }else if (method.equals("validate")){
            validateModel(req,resp);
        }else{
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
     *模型训练列表查询
     */
    private void getTrainList(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> response = new HashMap<>();
        Gson gson = new Gson();
        try {
            String pageStr = req.getParameter("page");
            String pageSizeStr = req.getParameter("page_size");
            // 页码默认1
            int page = 1;
            if (pageStr != null && !pageStr.isEmpty()) {
                try {
                    page = Integer.parseInt(pageStr);
                    if (page <= 0) {
                        page = 1; // 非法页码重置为默认值
                    }
                } catch (NumberFormatException e) {
                    page = 1; // 格式错误用默认值
                }
            }

            // 每页条数默认10，确保为正整数
            int pageSize = 10;
            if (pageSizeStr != null && !pageSizeStr.isEmpty()) {
                try {
                    pageSize = Integer.parseInt(pageSizeStr);
                    if (pageSize <= 0 || pageSize > 100) {
                        pageSize = 10; // 非法条数重置为默认值
                    }
                } catch (NumberFormatException e) {
                    pageSize = 10; // 格式错误用默认值
                }
            }
            // 计算分页偏移量
            int offset = (page - 1) * pageSize;

            List<TrainingTasks> taskList = getTrainTaskList(pageSize, offset);
            int total = getTotalTaskCount(); // 总任务数
            Map<String, Integer> statusCount = getTaskStatusCount(); // 各状态数量

            response.put("status", "success");
            response.put("data", taskList);
            response.put("total", total);
            response.put("statusCount", statusCount);

            resp.setStatus(200);
            responsePrint(resp, gson.toJson(response));

        } catch (Exception e) {
            resp.setStatus(500);
            response.put("error", "服务器内部错误：" + e.getMessage());
            responsePrint(resp, gson.toJson(response));
        }
    }

    // 查询未删除的总任务数
    private int getTotalTaskCount() {
        String sql = "SELECT COUNT(*) as total FROM training_tasks WHERE (is_deleted = 0 OR is_deleted IS NULL)";
        try {
            List<Map<String, Object>> result = getMysqlAdapter().select(sql);
            if (result != null && !result.isEmpty()) {
                return ((Number) result.get(0).get("total")).intValue();
            }
            return 0;
        } catch (Exception e) {
            throw new RuntimeException("查询总任务数失败：" + e.getMessage(), e);
        }
    }

    // 按状态分组查询任务数（返回状态->数量的映射）
    private Map<String, Integer> getTaskStatusCount() {
        String sql = "SELECT status, COUNT(*) as count FROM training_tasks " +
                "WHERE (is_deleted = 0 OR is_deleted IS NULL) " +
                "GROUP BY status";
        try {
            List<Map<String, Object>> result = getMysqlAdapter().select(sql);
            Map<String, Integer> statusCount = new HashMap<>();
            // 初始化所有可能的状态（避免前端无数据时显示undefined）
            statusCount.put("running", 0);      // 运行中
            statusCount.put("completed", 0);    // 已完成
            statusCount.put("failed", 0);       // 失败
            statusCount.put("stopped", 0);      // 已停止
            statusCount.put("waiting", 0);      // 等待中

            // 填充数据库查询到的状态数量
            for (Map<String, Object> row : result) {
                String status = (String) row.get("status");
                int count = ((Number) row.get("count")).intValue();
                if (statusCount.containsKey(status)) {
                    statusCount.put(status, count);
                }
            }
            return statusCount;
        } catch (Exception e) {
            throw new RuntimeException("查询状态任务数失败：" + e.getMessage(), e);
        }
    }


    private List<TrainingTasks> getTrainTaskList(int pageSize, int offset) {
        String sql = "SELECT * " +
                "FROM training_tasks " +
                "WHERE (is_deleted = 0 OR is_deleted IS NULL) " +
                "ORDER BY created_at DESC " +
                "LIMIT ?, ?";

        try {
            // 执行分页查询（参数：offset, pageSize）
            List<Map<String, Object>> dbList = getMysqlAdapter().select(sql, offset, pageSize);
            List<TrainingTasks> taskList = new ArrayList<>();

            // 转换数据库结果为TrainTask对象列表
            for (Map<String, Object> dbMap : dbList) {
                TrainingTasks task = new TrainingTasks();
                String taskId = (String) dbMap.get("task_id");
                String status = (String) dbMap.get("status");
                String progress = (String) dbMap.get("progress");
                
                task.setTaskId(taskId);
                task.setStatus(status);
                task.setProgress(progress);
                task.setCurrentEpoch((Integer) dbMap.get("current_epoch"));
                task.setEpochs((Integer) dbMap.get("epochs"));
                task.setDatasetName((String) dbMap.get("dataset_name"));
                task.setCreatedAt((String) dbMap.get("created_at"));
                task.setStartTime((String) dbMap.get("start_time"));
                
                // 如果进度为100%，且状态不是已完成，则更新状态为已完成
                if (progress != null && progress.equals("100%")) {
                    if (!"completed".equals(status)) {
                        // 更新数据库中的状态
                        updateTaskStatusToCompleted(taskId);
                        // 更新返回对象的状态
                        task.setStatus("completed");
                    }
                }
                
                taskList.add(task);
            }
            return taskList;
        } catch (Exception e) {
            throw new RuntimeException("查询训练任务列表失败：" + e.getMessage(), e);
        }
    }
    
    /**
     * 更新任务状态为已完成
     */
    private void updateTaskStatusToCompleted(String taskId) {
        try {
            String updateSql = "UPDATE training_tasks SET status = 'completed' WHERE task_id = ?";
            getMysqlAdapter().executeUpdate(updateSql, taskId);
        } catch (Exception e) {
            log.error("更新任务状态为已完成失败，taskId: {}", taskId, e);
        }
    }

    /**
     * 可用预训练模型列表查询
     */
    private void getPretrainModelList(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> response = new HashMap<>();
        try {
            String userId = req.getParameter("user_id");

            // 查询预训练模型列表
            List<Map<String, Object>> modelList = getPretrainModelsFromDB(userId);

            response.put("status", "SUCCESS");
            response.put("data", modelList);
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

    private List<Map<String, Object>> getPretrainModelsFromDB(String userId) {

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT model_id, model_name, model_path FROM model_downloads ") ;
        
        List<Object> params = new ArrayList<>();
        
        if (userId != null && !userId.isEmpty()) {
            sql.append("WHERE user_id = ? ");
            params.add(userId);
        }
        
        sql.append("ORDER BY model_id");
        
        try {
            return getMysqlAdapter().select(sql.toString(), params.toArray());
        } catch (Exception e) {
            throw new RuntimeException("查询预训练模型列表失败：" + e.getMessage(), e);
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

}
