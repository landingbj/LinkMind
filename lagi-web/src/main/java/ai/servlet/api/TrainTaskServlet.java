package ai.servlet.api;

import ai.config.ContextLoader;
import ai.config.pojo.ApiConfig;
import ai.database.impl.MysqlAdapter;
import ai.dto.*;
import ai.servlet.BaseServlet;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.*;


@Slf4j
public class TrainTaskServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();
    private String currentTime;

    // 用于存放所有任务的实时进度  key : taskId   值: 进度
    private static final Map<String, TrainingTasks> PROGRESS_STORAGE = new ConcurrentHashMap<>();

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

        if (method.equals("progress")) {
            handleGetProgress(req, resp);
        } else if (method.equals("log")) {
            handleGetLog(req, resp);
        } else if (method.equals("start")) {
            handleStartTask(req, resp);
        } else if (method.equals("stop")) {
            handleStopTask(req, resp);
        } else if (method.equals("resume")) {
            handleResumeTask(req, resp);
        } else if (method.equals("detail")){
            handleDetailTask(req,resp);
        }else if(method.equals("monitor")){
            handleMonitorTask(req,resp);
        }else if (method.equals("batch_stop")){
            handleBatchStopTask(req,resp);
        }else if (method.equals("deleted")){
            handleDeleteTask(req,resp);
        }else if (method.equals("batch_deleted")){
            handleBatchDeleteTask(req,resp);
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


    private ExecutorService trainingExecutor = new ThreadPoolExecutor(
            2, // 核心线程数
            5, // 最大线程数
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(10), // 任务队列
            Executors.defaultThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy() // 任务满时的拒绝策略
    );

    // 用于定期轮询训练进度的调度器
    private ScheduledExecutorService progressPollingExecutor = Executors.newScheduledThreadPool(5);

    // 存储任务ID到训练平台任务ID的映射关系
    private static final Map<String, String> TASK_ID_MAPPING = new ConcurrentHashMap<>();

    // 存储正在轮询的任务ID集合
    private static final Set<String> POLLING_TASKS = ConcurrentHashMap.newKeySet();

    // 存储任务ID到轮询任务的映射（核心新增）
    private static final Map<String, ScheduledFuture<?>> TASK_POLLING_FUTURE = new ConcurrentHashMap<>();

    /**
     * 启动训练任务（POST）
     */
    private void handleStartTask(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            String jsonBody = requestToJson(req);
            JsonObject jsonNode = gson.fromJson(jsonBody, JsonObject.class);
            if (!jsonNode.has("dataset_path") || !jsonNode.has("model_path")) {
                resp.setStatus(400);
                result.put("error", "缺少必填参数：dataset_path或model_path");
                responsePrint(resp, toJson(result));
                return;
            }

            TrainingTasks task = new TrainingTasks();

            String datasetPath = jsonNode.get("dataset_path").getAsString();
            task.setDatasetPath(datasetPath);

            //根据数据集路径获取数据集名称
            String datasetName = getDatasetNameByDatasetPath(datasetPath);
            task.setDatasetName(datasetName);

            task.setModelPath(jsonNode.get("model_path").getAsString());
            // 处理可选参数（使用默认值）
            task.setEpochs(jsonNode.has("epochs") ? jsonNode.get("epochs").getAsInt() : 10);
            task.setBatchSize(jsonNode.has("batch") ? jsonNode.get("batch").getAsInt() : 8);
            task.setImageSize(jsonNode.has("imgsz") ? jsonNode.get("imgsz").getAsInt() : 640);
            task.setUseGpu(jsonNode.has("use_gpu") ? jsonNode.get("use_gpu").getAsBoolean() : true);
            task.setTemplateId(jsonNode.has("template_id") ? jsonNode.get("template_id").getAsInt() : 0);

            task.setStatusEnum(TaskStatus.RUNNING); // 任务启动状态
            task.setProgress("0%"); //训练进度
            task.setCurrentEpoch(0);  //当前训练轮次

            task.setStartTime(currentTime);
            task.setCreatedAt(currentTime);

            //保存启动任务到数据库
            String taskId = saveStartTrainTaskToDB(task);

            //保存配置模版信息到training_task_field表中
            //saveTrainingTaskField(taskId, task.getTemplateId());
            //添加启动训练任务日志
            addTrainingTaskLog(taskId, "INFO", "训练任务已启动");

            //提交训练任务到线程池（异步执行）
            trainingExecutor.submit(() -> {
                try {
                    // 执行实际训练，并实时更新进度
                    executeTraining(task);
                } catch (Exception e) {
                    log.error("训练任务执行失败: taskId={}, 错误={}", taskId, e.getMessage(), e);
                    // 训练失败时更新状态
                    updateTaskStatus(taskId, TaskStatus.FAILED, "训练失败：" + e.getMessage());
                }
            });

            result.put("status", TaskStatus.TASKRUNNING.getDesc());
            result.put("task_id", taskId);
            result.put("message", "使用返回的task_id可执行进度查询、停止/继续训练、查看日志");

            Map<String, Object> trainConfig = new HashMap<>();
            trainConfig.put("dataset_name", task.getDatasetName());
            trainConfig.put("dataset_path", task.getDatasetPath());
            trainConfig.put("model_path", task.getModelPath());
            trainConfig.put("epochs", task.getEpochs());
            trainConfig.put("batch", task.getBatchSize());
            trainConfig.put("imgsz", task.getImageSize());
            trainConfig.put("use_gpu", task.getUseGpu());
            result.put("train_config", trainConfig);
            result.put("created_at", task.getCreatedAt());

            resp.setStatus(200);
            responsePrint(resp, toJson(result));
        } catch (Exception e) {
            resp.setStatus(500);
            result.put("error", "服务器内部错误：" + e.getMessage());
            responsePrint(resp, toJson(result));
        }
    }

//    private void saveTrainingTaskField(String taskId, Integer templateId) {
//        for (FieldDefinitionDto field : queryFieldDefinition(templateId)) {
//            TrainingTaskField taskField = new TrainingTaskField();
//            taskField.setTaskId(taskId);
//            taskField.setFieldName(field.getFieldName());
//            taskField.setFieldColumnName(field.getFieldColumnName());
//            taskField.setFieldType(field.getFieldType());
//            taskField.setFieldDescription(field.getFieldDescription());
//            taskField.setFieldScope(field.getFieldScope());
//            taskField.setIsRequired(field.getIsRequired());
//            taskField.setDefaultValue(field.getDefaultValue());
//            taskField.setRemark(field.getRemark());
//            taskField.setTemplateId(templateId);
//
//            // 保存到数据库
//            String sql = "INSERT INTO training_task_field " +
//                    "(task_id, field_name, field_column_name, field_type, field_description, field_scope, is_required, default_value, remark, template_id) " +
//                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
//            try {
//                getMysqlAdapter().executeUpdate(
//                        sql,
//                        taskField.getTaskId(),
//                        taskField.getFieldName(),
//                        taskField.getFieldColumnName(),
//                        taskField.getFieldType(),
//                        taskField.getFieldDescription(),
//                        taskField.getFieldScope(),
//                        taskField.getIsRequired(),
//                        taskField.getDefaultValue(),
//                        taskField.getRemark(),
//                        taskField.getTemplateId()
//                );
//            } catch (Exception e) {
//                log.error("保存训练任务字段信息失败: taskId={}, fieldName={}, error={}",
//                        taskId, taskField.getFieldName(), e.getMessage(), e);
//            }
//        }
//    }

    /**异步执行训练任务
     */
    private void executeTraining(TrainingTasks task) {
        String taskId = task.getTaskId();
        try {
            // 1. 调用训练平台API启动训练任务
            String platformTaskId = startTrainingOnPlatform(task);
            if (platformTaskId == null || platformTaskId.isEmpty()) {
                log.error("训练平台返回的任务ID为空: taskId={}", taskId);
                updateTaskStatus(taskId, TaskStatus.FAILED, "训练平台启动失败：未返回任务ID");
                return;
            }

            // 2. 保存本地任务ID与训练平台任务ID的映射关系
            TASK_ID_MAPPING.put(taskId, platformTaskId);
            log.info("训练任务已提交到训练平台: taskId={}, platformTaskId={}", taskId, platformTaskId);

            // 3. 开始定期轮询训练进度
            startProgressPolling(taskId, platformTaskId);

        } catch (Exception e) {
            log.error("训练任务执行失败: taskId={}, 错误={}", taskId, e.getMessage(), e);
            updateTaskStatus(taskId, TaskStatus.FAILED, "训练失败：" + e.getMessage());
        }
    }

    /**
     * 启动训练平台训练任务
     */
    private String startTrainingOnPlatform(TrainingTasks task) throws Exception {
        String baseUrl = getTrainPlatformBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new RuntimeException("训练平台配置未找到，请检查配置文件中的train_platform配置");
        }

        String startUrl = baseUrl + "/api/yolo/train/start";

        Map<String, Object> trainRequest = new HashMap<>();
        trainRequest.put("dataset_path", task.getDatasetPath());
        trainRequest.put("model_path", task.getModelPath());
        trainRequest.put("epochs", task.getEpochs());
        trainRequest.put("batch", task.getBatchSize());
        trainRequest.put("imgsz", task.getImageSize());
        trainRequest.put("use_gpu", task.getUseGpu());
        trainRequest.put("task_id", task.getTaskId()); // 传递本地任务ID

        String requestBody = gson.toJson(trainRequest);
        log.info("调用训练平台启动接口: url={}, request={}", startUrl, requestBody);

        String responseStr = callTrainPlatformApi(startUrl, requestBody, "POST");
        log.info("训练平台启动响应: {}", responseStr);

        // 解析响应获取训练平台返回的任务ID
        JsonObject responseJson = gson.fromJson(responseStr, JsonObject.class);

        if (responseJson.has("task_id")) {
            return responseJson.get("task_id").getAsString();
        } else if (responseJson.has("platform_task_id")) {
            return responseJson.get("platform_task_id").getAsString();
        } else if (responseJson.has("id")) {
            return responseJson.get("id").getAsString();
        } else {
            // 如果响应中没有标准字段，尝试使用本地taskId
            log.warn("训练平台响应中未找到任务ID字段，使用本地taskId: taskId={}", task.getTaskId());
            return task.getTaskId();
        }
    }

    /**
     * 开始定期轮询训练进度
     */
    private void startProgressPolling(String taskId, String platformTaskId) {
        if (POLLING_TASKS.contains(taskId)) {
            log.warn("任务已在轮询中: taskId={}", taskId);
            return;
        }

        POLLING_TASKS.add(taskId);

        // 每5秒轮询一次训练进度
        ScheduledFuture<?> pollingFuture = progressPollingExecutor.scheduleWithFixedDelay(() -> {
            try {
                pollTrainingProgress(taskId, platformTaskId);
            } catch (Exception e) {
                log.error("轮询训练进度失败: taskId={}, error={}", taskId, e.getMessage(), e);
            }
        }, 2, 5, TimeUnit.SECONDS); // 延迟2秒开始，之后每5秒轮询一次

        // 保存任务与轮询实例的映射
        TASK_POLLING_FUTURE.put(taskId, pollingFuture);
        log.info("轮询任务启动: taskId={}, platformTaskId={}", taskId, platformTaskId);
    }

    /**
     * 停止指定任务的轮询
     * @param taskId 本地任务ID
     */
    private void stopPolling(String taskId) {
        // 1. 取消轮询任务（不中断正在执行的轮询，避免数据更新不完整）
        ScheduledFuture<?> future = TASK_POLLING_FUTURE.remove(taskId);
        if (future != null && !future.isCancelled() && !future.isDone()) {
            future.cancel(false);
            log.info("轮询任务已取消: taskId={}", taskId);
        }
        // 2. 清理缓存（避免内存泄漏）
        POLLING_TASKS.remove(taskId);
        TASK_ID_MAPPING.remove(taskId);

        log.info("任务轮询资源清理完成: taskId={}", taskId);
    }

    /**
     * 轮询训练平台获取训练进度
     */
    private void pollTrainingProgress(String taskId, String platformTaskId) {
        try {
            String baseUrl = getTrainPlatformBaseUrl();
            if (baseUrl == null || baseUrl.isEmpty()) {
                log.error("训练平台配置未找到: taskId={}", taskId);
                return;
            }

            // 查询训练任务进度
            String progressUrl = baseUrl + "/api/yolo/train/progress?task_id=" + platformTaskId;
            String responseStr = callTrainPlatformApi(progressUrl, null, "GET");

            if (responseStr == null || responseStr.isEmpty()) {
                log.warn("训练平台返回的进度信息为空: taskId={}", taskId);
                return;
            }

            JsonObject progressJson = gson.fromJson(responseStr, JsonObject.class);

            // 检查任务状态
            String status = null;
            if (progressJson.has("status")) {
                status = progressJson.get("status").getAsString();
            }

            // 获取当前轮次
            int currentEpoch = 0;
            if (progressJson.has("current_epoch")) {
                currentEpoch = progressJson.get("current_epoch").getAsInt();
            }

            // 获取进度百分比
            String progress = "0%";
            if (progressJson.has("progress")) {
                JsonElement progressObj = progressJson.get("progress");
                if (progressObj.isJsonPrimitive()) {
                    if (progressObj.getAsJsonPrimitive().isNumber()) {
                        double progressValue = progressObj.getAsDouble();
                        progress = (int)progressValue + "%";
                    } else {
                        progress = progressObj.getAsString();
                    }
                }
            }

            //获取结束时间
            String endTime = null;
            if (progressJson.has("end_time") && !progressJson.get("end_time").isJsonNull()) {
                String originalEndTime = progressJson.get("end_time").getAsString();
                try {
                    // 解析ISO格式时间（如 "2025-11-11T20:42:24.158487"）
                    LocalDateTime dateTime = LocalDateTime.parse(
                            originalEndTime,
                            DateTimeFormatter.ISO_LOCAL_DATE_TIME // 适配ISO格式（自动忽略多余毫秒）
                    );
                    // 格式化为目标字符串（yyyy-MM-dd HH:mm:ss）
                    endTime = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                } catch (DateTimeParseException e) {
                    // 异常处理：解析失败时保留原始值，记录日志
                    log.warn("end_time格式解析失败，保留原始值: taskId={}, originalEndTime={}, error={}",
                            taskId, originalEndTime, e.getMessage());
                    endTime = originalEndTime; // 解析失败时使用原始值，避免丢失数据
                }
            }

            //获取输出目录
            String trainDir = "";
            if (progressJson.has("train_dir")){
                trainDir = progressJson.get("train_dir").getAsString();
            }

            // 转换为本地任务状态
            TaskStatus localStatus = TaskStatus.fromPlatformStatus(status);

            updateFinishTaskToDB(taskId,localStatus.getValue(), currentEpoch, progress, endTime, trainDir);

            // 任务终止时停止轮询（核心触发逻辑）
            if (localStatus.isTerminal()) {
                log.info("任务已终止，触发轮询停止: taskId={}, status={}", taskId, localStatus.getDesc());
                addTrainingTaskLog(taskId,"INFO", "训练任务已完成");
                stopPolling(taskId);
            }

        } catch (Exception e) {
            log.error("查询训练进度异常: taskId={}, error={}", taskId, e.getMessage(), e);
        }
    }

    private void updateFinishTaskToDB(String taskId, String status, int currentEpoch, String progress, String endTime, String trainDir) {
        String sql = "UPDATE training_tasks SET status = ?, current_epoch = ?, progress = ?, end_time = ?, train_dir = ? WHERE task_id = ?";
        try {
            getMysqlAdapter().executeUpdate(
                    sql,
                    status,
                    currentEpoch,
                    progress,
                    endTime,
                    trainDir,
                    taskId
            );
            log.info("任务信息更新成功");
        } catch (Exception e) {
            log.error("更新任务完成信息失败:  错误={}",  e.getMessage(), e);
        }
    }

    /**
     * 获取训练平台基础URL
     */
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
            log.error("获取训练平台配置失败: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * 调用训练平台API
     */
    private String callTrainPlatformApi(String url, String requestBody, String method) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new java.net.URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
        connection.setConnectTimeout(10000); // 连接超时10秒
        connection.setReadTimeout(30000); // 读取超时30秒

        if ("POST".equals(method) && requestBody != null) {
            connection.setDoOutput(true);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        }

        int responseCode = connection.getResponseCode();
        StringBuilder response = new StringBuilder();
        if (responseCode == 200) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                return response.toString();
            }
        } else {
            String errorMessage = "HTTP错误码: " + responseCode;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                StringBuilder errorResponse = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    errorResponse.append(line);
                }
                if (errorResponse.length() > 0) {
                    errorMessage += ", 错误信息: " + errorResponse.toString();
                }
            } catch (Exception e) {
            }
            throw new RuntimeException(errorMessage);
        }
    }

    /**
     * 更新任务状态（运行中/已完成/失败等）
     */
    private void updateTaskStatus(String taskId, TaskStatus status, String message) {
        currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String sql = "UPDATE training_tasks " +
                "SET status = ?, error_message = ?, updated_at = ? " +
                "WHERE task_id = ?";
        try {
            getMysqlAdapter().executeUpdate(
                    sql,
                    status.getValue(),
                    message,
                    currentTime,
                    taskId
            );

            // 同时更新内存中的状态
            TrainingTasks task = PROGRESS_STORAGE.get(taskId);
            if (task != null) {
                task.setStatusEnum(status);
                task.setErrorMsg(message);
                task.setUpdatedAt(currentTime);
                PROGRESS_STORAGE.put(taskId, task);
            }

            // 根据状态添加失败/完成等日志（可按需扩展）
            if (status == TaskStatus.FAILED) {
                addTrainingTaskLog(taskId, "ERROR", "训练任务失败：" + message);
            } else if (status == TaskStatus.COMPLETED) {
                addTrainingTaskLog(taskId, "INFO", "训练任务已完成");
            }
        } catch (Exception e) {
            log.error("更新任务状态失败: taskId={}, 状态={}, 错误={}", taskId, status, e.getMessage(), e);
        }
    }

    private String getDatasetNameByDatasetPath(String datasetPath) {
        String sql = "SELECT dataset_name FROM dataset_records WHERE dataset_path = ?";
        try {
            List<Map<String, Object>> result = getMysqlAdapter().select(sql, datasetPath);
            if (result != null && !result.isEmpty()) {
                return (String) result.get(0).get("dataset_name");
            }
            return null;
        } catch (Exception e) {
            log.error("根据数据集路径获取数据集名称失败: datasetPath={}, error={}", datasetPath, e.getMessage(), e);
            return null;
        }
    }

    private String saveStartTrainTaskToDB(TrainingTasks task) {
        String taskId = UUID.randomUUID().toString();
        task.setTaskId(taskId);
        String sql = "INSERT INTO training_tasks " +
                "( task_id, dataset_name, dataset_path, model_path, epochs, batch_size, image_size, use_gpu, status, " +
                "progress, current_epoch, start_time, created_at, is_deleted, template_id) " +
                "VALUES ( ?,?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?,?,?,?)";
        try {
            getMysqlAdapter().executeUpdate(
                    sql,
                    task.getTaskId(),
                    task.getDatasetName(),
                    task.getDatasetPath(),
                    task.getModelPath(),
                    task.getEpochs(),
                    task.getBatchSize(),
                    task.getImageSize(),
                    task.getUseGpu(),
                    task.getStatus(),
                    task.getProgress(),
                    task.getCurrentEpoch(),
                    task.getStartTime(),
                    task.getCreatedAt(),
                    0,
                    task.getTemplateId()
            );
            return taskId;
        } catch (Exception e) {
            throw new RuntimeException("启动训练任务失败：" + e.getMessage(), e);
        }
    }

    /**
     * 查询训练进度（GET）
     */
    private void handleGetProgress(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> response = new HashMap<>();
        try {
            String taskId = req.getParameter("task_id");
            if (taskId == null || taskId.trim().isEmpty()) {
                resp.setStatus(400);
                response.put( "error", "缺少参数：task_id");
                return;
            }

            // 直接从内存获取最新进度
            TrainingTasks progress = PROGRESS_STORAGE.get(taskId);
            if (progress == null) {
                TrainingTasks task = getTrainTaskByTaskId(taskId);
                if (task == null) {
                    resp.setStatus(404);
                    response.put("error", "任务不存在");
                    responsePrint(resp, toJson(response));
                    return;
                }
                response.put("task_id", task.getTaskId());
                response.put("status", task.getStatus());
                response.put("progress", task.getProgress());
                response.put("current_epoch", task.getCurrentEpoch());
                response.put("total_epochs", task.getEpochs());
                response.put("start_time", task.getStartTime());
                response.put("end_time", task.getEndTime());
                response.put("resumed_at", task.getUpdatedAt());
                response.put("train_dir", task.getTrainDir());
                response.put("error", task.getErrorMsg());
                resp.setStatus(200);
                responsePrint(resp, toJson(response));
                return;
            }

            response.put("task_id", progress.getTaskId());
            response.put("status", progress.getStatus());
            response.put("progress", progress.getProgress());
            response.put("current_epoch", progress.getCurrentEpoch());
            response.put("total_epochs", progress.getEpochs());
            response.put("updated_time", progress.getUpdatedAt());
            response.put("error", progress.getErrorMsg());

            resp.setStatus(200);
            responsePrint(resp, toJson(response));
        } catch (Exception e) {
            resp.setStatus(500);
            response.put("error", "服务器内部错误：" + e.getMessage());
            responsePrint(resp,toJson(response));
        }
    }


    private TrainingTasks getTrainTaskByTaskId(String taskId) {
        String sql = "SELECT * FROM training_tasks WHERE task_id = ?";
        try {
            List<Map<String, Object>> list = getMysqlAdapter().select(sql, taskId);
            if (list == null || list.isEmpty()) {
                return null;
            }

            Map<String, Object> map = list.get(0);
            TrainingTasks task = new TrainingTasks();
            task.setTaskId((String) map.get("task_id"));
            task.setDatasetName((String) map.get("dataset_name"));
            task.setDatasetPath((String) map.get("dataset_path"));
            task.setModelPath((String) map.get("model_path"));
            task.setEpochs((Integer) map.get("epochs"));
            task.setBatchSize((Integer) map.get("batch_size"));
            task.setImageSize((Integer) map.get("image_size"));
            task.setUseGpu((Boolean) map.get("use_gpu"));
            task.setStatus((String) map.get("status"));
            task.setProgress((String) map.get("progress"));
            task.setCurrentEpoch((Integer) map.get("current_epoch"));
            task.setStartTime((String) map.get("start_time"));
            task.setEndTime((String) map.get("end_time"));
            task.setTrainDir((String) map.get("train_dir"));
            task.setErrorMsg((String) map.get("error_message"));
            task.setCreatedAt((String) map.get("created_at"));
            task.setUpdatedAt((String) map.get("updated_at"));
            task.setUserId((String) map.get("user_id"));
            task.setIsDeleted(((Long) map.get("is_deleted")).intValue());
            task.setDeletedAt((String) map.get("deleted_at"));
            task.setTemplateId((Integer) map.get("template_id"));
            return task;
        } catch (Exception e) {
            throw new RuntimeException("查询任务失败：" + e.getMessage(), e);
        }
    }

    /**
     * 停止训练任务（POST）
     */
    private void handleStopTask(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> response = new HashMap<>();
        try {
            String taskId = req.getParameter("task_id");
            if (taskId == null || taskId.isEmpty()) {
                resp.setStatus(400);
                response.put("error", "缺少参数：task_id");
                responsePrint(resp, toJson(response));
                return;
            }

            TrainingTasks task = getTrainTaskByTaskId(taskId);
            if (task == null || task.getIsDeleted() == 1) {
                resp.setStatus(404);
                response.put("error", "任务不存在");
                responsePrint(resp, toJson(response));
                return;
            }

            if (task.getStatus().equals("stopped")) {
                resp.setStatus(400);
                response.put("error", "当前任务已处于停止状态（当前状态：" + task.getStatus() + "）");
                responsePrint(resp, toJson(response));
                return;
            }
            boolean success = stopTrainTaskByTaskId(taskId);
            if (!success) {
                resp.setStatus(400);
                response.put("error", "任务状态不允许停止（当前状态：" + task.getStatus() + "）");
                responsePrint(resp, toJson(response));
                return;
            }


            // 1. 调用训练平台API停止训练任务
            String platformTaskId = TASK_ID_MAPPING.get(taskId);
            if (platformTaskId != null && !platformTaskId.isEmpty()) {
                try {
                    stopTrainingOnPlatform(platformTaskId);
                    // 停止轮询进度
                    POLLING_TASKS.remove(taskId);
                } catch (Exception e) {
                    log.error("调用训练平台停止接口失败: taskId={}, platformTaskId={}, error={}",
                            taskId, platformTaskId, e.getMessage(), e);
                    // 即使训练平台停止失败，也继续更新本地状态
                }
            } else {
                // 如果没有平台任务ID，停止轮询
                POLLING_TASKS.remove(taskId);
            }

            // 刷新任务数据
            task = getTrainTaskByTaskId(taskId);

            // 构建响应
            response.put("task_id", taskId);
            response.put("status", TaskStatus.TASKSTOPPED);
            response.put("stopped_at", task.getEndTime());
            response.put("current_progress", task.getProgress());
            response.put("message", "可调用/resume接口继续训练");

            resp.setStatus(200);
            responsePrint(resp,toJson(response));
        } catch (Exception e) {
            resp.setStatus(500);
            response.put("error", "服务器内部错误：" + e.getMessage());
            responsePrint(resp, toJson(response));
        }
    }


    private Boolean stopTrainTaskByTaskId(String taskId) {
        currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        TrainingTasks task = getTrainTaskByTaskId(taskId);
        if (task == null || !TaskStatus.RUNNING.getValue().equals(task.getStatus())) {
            return false;

        }

        String sql = "UPDATE training_tasks SET status = ?, end_time = ?, updated_at = ? WHERE task_id = ?";
        try {
            getMysqlAdapter().executeUpdate(
                    sql,
                    TaskStatus.STOPPED.getValue(),
                    currentTime,
                    currentTime,
                    taskId
            );

            // 更新内存中的状态
            if (task != null) {
                task.setStatusEnum(TaskStatus.STOPPED);
                task.setEndTime(currentTime);
                task.setUpdatedAt(currentTime);
                PROGRESS_STORAGE.put(taskId, task);
            }

            // 添加停止训练任务日志
            addTrainingTaskLog(taskId, "INFO", "训练任务已停止");
            return true;
        } catch (Exception e) {
            throw new RuntimeException("停止任务失败：" + e.getMessage(), e);
        }
    }

    /**
     * 调用训练平台停止训练任务
     */
    private void stopTrainingOnPlatform(String platformTaskId) throws Exception {
        String baseUrl = getTrainPlatformBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new RuntimeException("训练平台配置未找到");
        }

        String stopUrl = baseUrl + "/api/yolo/train/stop";

        // 构建停止请求参数
        Map<String, Object> stopRequest = new HashMap<>();
        stopRequest.put("task_id", platformTaskId);

        String requestBody = gson.toJson(stopRequest);
        log.info("调用训练平台停止接口: url={}, request={}", stopUrl, requestBody);

        String responseStr = callTrainPlatformApi(stopUrl, requestBody, "POST");
        log.info("训练平台停止响应: {}", responseStr);
    }

    /**
     * 批量停止训练任务
     */
    private void handleBatchStopTask(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> response = new HashMap<>();
        try {
            String jsonBody = requestToJson(req);
            JsonObject jsonObject = gson.fromJson(jsonBody, JsonObject.class);

            if (!jsonObject.has("task_id") || !jsonObject.get("task_id").isJsonArray()) {
                resp.setStatus(400);
                response.put("error", "缺少参数：task_id 或 task_id 不是数组");
                responsePrint(resp, toJson(response));
                return;
            }

            // 获取任务ID列表
            List<String> taskIds = new Gson().fromJson(jsonObject.get("task_id"), List.class);

            if (taskIds.isEmpty()) {
                resp.setStatus(400);
                response.put("error", "任务ID列表不能为空");
                responsePrint(resp, toJson(response));
                return;
            }

            List<String> successIds = new ArrayList<>();
            Map<String, String> failIds = new HashMap<>();

            for (String taskId : taskIds) {
                try {
                    TrainingTasks task = getTrainTaskByTaskId(taskId);
                    if (task == null || task.getIsDeleted() == 1) {
                        failIds.put(taskId, "任务不存在");
                        continue;
                    }

                    if (task.getStatus().equals("stopped")) {
                        failIds.put(taskId, "当前任务已处于停止状态（当前状态：" + task.getStatus() + "）");
                        continue;
                    }

                    boolean success = stopTrainTaskByTaskId(taskId);
                    if (success) {
                        successIds.add(taskId);
                    } else {
                        failIds.put(taskId, "任务状态不允许停止（当前状态：" + task.getStatus() + "）");
                    }
                } catch (Exception e) {
                    failIds.put(taskId, "停止任务时发生异常：" + e.getMessage());
                }
            }

            response.put("task_ids", taskIds);
            response.put("success_ids", successIds);
            response.put("fail_ids", failIds);

            if (successIds.size() == taskIds.size()) {
                response.put("message", "所有任务已停止");
            } else if (successIds.size() > 0) {
                response.put("message", "部分任务已停止");
            } else {
                response.put("message", "没有任务被成功停止");
            }

            resp.setStatus(200);
            responsePrint(resp, toJson(response));
        } catch (Exception e) {
            resp.setStatus(500);
            response.put("error", "服务器内部错误：" + e.getMessage());
            responsePrint(resp, toJson(response));
        }
    }

    /**
     * 继续训练任务（POST）
     */
    private void handleResumeTask(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> response = new HashMap<>();
        try {
            String taskId = req.getParameter("task_id");
            if (taskId == null || taskId.isEmpty()) {
                resp.setStatus(400);
                response.put("error", "缺少参数：task_id");
                responsePrint(resp,toJson(response));
                return;
            }

            TrainingTasks task = getTrainTaskByTaskId(taskId);
            if (task == null || task.getIsDeleted() == 1) {
                resp.setStatus(404);
                response.put("error", "任务不存在");
                responsePrint(resp, toJson(response));
                return;
            }

            boolean success = resumeTrainTaskByTaskId(taskId);
            if (!success) {
                resp.setStatus(400);
                response.put("error", "任务状态不允许继续（当前状态：" + task.getStatus() + "）");
                responsePrint(resp, toJson(response));
                return;
            }

            // 1. 调用训练平台API恢复训练任务
            String platformTaskId = TASK_ID_MAPPING.get(taskId);
            if (platformTaskId != null && !platformTaskId.isEmpty()) {
                try {
                    resumeTrainingOnPlatform(platformTaskId);
                    // 重新开始轮询进度
                    startProgressPolling(taskId, platformTaskId);
                } catch (Exception e) {
                    log.error("调用训练平台恢复接口失败: taskId={}, platformTaskId={}, error={}",
                            taskId, platformTaskId, e.getMessage(), e);
                    // 即使训练平台恢复失败，也继续更新本地状态
                }
            } else {
                log.warn("未找到训练平台任务ID，无法恢复训练: taskId={}", taskId);
            }

            //task = getTrainTaskByTaskId(taskId);
            // 构建响应
            response.put("task_id", taskId);
            response.put("status",TaskStatus.TASKRESUME);
            response.put("resumed_at", task.getUpdatedAt());
            response.put("resume_progress", task.getProgress());
            response.put("message", "可调用/progress接口查询最新进度");

            resp.setStatus(200);
            responsePrint(resp,toJson(response));
        } catch (Exception e) {
            resp.setStatus(500);
            response.put("error", "服务器内部错误：" + e.getMessage());
            responsePrint(resp, toJson(response));
        }
    }

    private boolean resumeTrainTaskByTaskId(String taskId) {
        currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        TrainingTasks task = getTrainTaskByTaskId(taskId);
//        if (task == null || !TaskStatus.STOPPED.getValue().equals(task.getStatus())) {
//            return false;
//        }


        // 2. 更新本地数据库状态
        String sql = "UPDATE training_tasks " +
                "SET status = ?, updated_at = ? " +
                "WHERE task_id = ?";
        try {
            getMysqlAdapter().executeUpdate(
                    sql,
                    TaskStatus.RUNNING.getValue(),
                    currentTime,
                    taskId
            );

            // 更新内存中的状态
            if (task != null) {
                task.setStatusEnum(TaskStatus.RUNNING);
                task.setUpdatedAt(currentTime);
                PROGRESS_STORAGE.put(taskId, task);
            }

            // 添加继续训练任务日志
            addTrainingTaskLog(taskId, "INFO", "训练任务已继续");


            return true;
        } catch (Exception e) {
            throw new RuntimeException("继续任务失败：" + e.getMessage(), e);
        }
    }

    /**
     * 调用训练平台恢复训练任务
     */
    private void resumeTrainingOnPlatform(String platformTaskId) throws Exception {
        String baseUrl = getTrainPlatformBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new RuntimeException("训练平台配置未找到");
        }

        String resumeUrl = baseUrl + "/api/yolo/train/resume";

        // 构建恢复请求参数
        Map<String, Object> resumeRequest = new HashMap<>();
        resumeRequest.put("task_id", platformTaskId);

        String requestBody = gson.toJson(resumeRequest);
        log.info("调用训练平台恢复接口: url={}, request={}", resumeUrl, requestBody);

        String responseStr = callTrainPlatformApi(resumeUrl, requestBody, "POST");
        log.info("训练平台恢复响应: {}", responseStr);
    }

    /**
     * 查看训练日志
     */
    private void handleGetLog(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> response = new HashMap<>();
        try {

            String taskId = req.getParameter("task_id");
            int logLines = 100; // 默认返回100行

            if (taskId == null || taskId.trim().isEmpty()) {
                resp.setStatus(400);
                response.put("error", "缺少参数：task_id");
                responsePrint(resp, toJson(response));
                return;
            }

            String logLinesParam = req.getParameter("log_lines");
            if (logLinesParam != null && !logLinesParam.trim().isEmpty()) {
                try {
                    logLines = Integer.parseInt(logLinesParam.trim());
                    if (logLines <= 0) {
                        resp.setStatus(400);
                        response.put("error", "参数log_lines必须为正整数");
                        responsePrint(resp, toJson(response));
                        return;
                    }
                } catch (NumberFormatException e) {
                    resp.setStatus(400);
                    response.put("error", "参数log_lines必须是整数");
                    responsePrint(resp, toJson(response));
                    return;
                }
            }

            if (!isTaskExists(taskId)) {
                resp.setStatus(404);
                response.put("error", "任务ID错误或不存在");
                responsePrint(resp, toJson(response));
                return;
            }

            List<TrainingLogs> logList = queryTrainingLogsFromDB(taskId, logLines);
            int totalLines = queryTotalLogLinesFromDB(taskId);

            if (logList.isEmpty() || totalLines == 0) {
                resp.setStatus(404);
                response.put("error", "日志文件未生成");
                responsePrint(resp, toJson(response));
                return;
            }

            StringBuilder logContent = new StringBuilder();

            for (TrainingLogs log : logList) {
                String createdAt = log.getCreatedAt() != null ? log.getCreatedAt() : "";
                String logLevel = log.getLogLevel() != null ? log.getLogLevel() : "";
                String logMessage = log.getLogMessage() != null ? log.getLogMessage() : "";
                logContent.append(String.format("%s - %s - %s%n", createdAt, logLevel, logMessage));
            }

            response.put("task_id", taskId);
            // 取第一条日志的文件路径
            response.put("log_file", logList.get(0).getLogFilePath());
            response.put("return_lines", logList.size());
            response.put("total_lines", totalLines);
            response.put("log_content", logContent.toString().trim());

            resp.setStatus(200);
            responsePrint(resp, toJson(response));

        } catch (Exception e) {

            resp.setStatus(500);
            response.put("error", "服务器内部错误：" + e.getMessage());
            responsePrint(resp, toJson(response));
        }
    }

    /**
     * 验证任务ID是否存在
     */
    private boolean isTaskExists(String taskId) {
        String sql = "SELECT COUNT(*) AS count FROM training_tasks WHERE task_id = ?";
        try {
            List<Map<String, Object>> result = getMysqlAdapter().select(sql, taskId);
            if (result != null && !result.isEmpty()) {
                long count = ((Number) result.get(0).get("count")).longValue();
                return count > 0; // 存在至少一条记录则任务有效
            }
            return false;
        } catch (Exception e) {
            throw new RuntimeException("验证任务存在性失败：" + e.getMessage(), e);
        }
    }

    /**
     * 从数据库training_logs表查询指定任务的日志
     */
    private List<TrainingLogs> queryTrainingLogsFromDB(String taskId, int logLines) {
        String sql = "SELECT log_level, log_message, created_at, log_file_path " +
                "FROM training_logs " +
                "WHERE task_id = ? " +
                "ORDER BY created_at DESC " +
                "LIMIT ?";

        try {
            // 先查询出Map列表
            List<Map<String, Object>> mapList = getMysqlAdapter().select(sql, taskId, logLines);
            // 转换为TrainingLogs实体类列表
            List<TrainingLogs> logsList = new ArrayList<>();
            for (Map<String, Object> map : mapList) {
                TrainingLogs log = new TrainingLogs();
                // 映射字段
                log.setTaskId(taskId);
                log.setLogLevel(map.get("log_level") != null ? map.get("log_level").toString() : null);
                log.setLogMessage(map.get("log_message") != null ? map.get("log_message").toString() : null);
                log.setCreatedAt(map.get("created_at") != null ? map.get("created_at").toString() : null);
                log.setLogFilePath(map.get("log_file_path") != null ? map.get("log_file_path").toString() : null);
                logsList.add(log);
            }
            return logsList;
        } catch (Exception e) {
            throw new RuntimeException("查询训练日志失败：" + e.getMessage(), e);
        }
    }

    /**
     * 查询指定任务的日志总行数
     */
    private int queryTotalLogLinesFromDB(String taskId) {
        String sql = "SELECT COUNT(*) AS total FROM training_logs WHERE task_id = ?";
        try {
            List<Map<String, Object>> result = getMysqlAdapter().select(sql, taskId);
            if (result != null && !result.isEmpty()) {
                return ((Number) result.get(0).get("total")).intValue();
            }
            return 0;
        } catch (Exception e) {
            throw new RuntimeException("查询日志总行数失败：" + e.getMessage(), e);
        }
    }

    /**
     * 获取训练任务详情（GET）
     */
    private void handleDetailTask(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> response = new HashMap<>();
        try {
            String taskId = req.getParameter("task_id");
            if (taskId == null || taskId.isEmpty()) {
                resp.setStatus(400);
                response.put("error", "缺少参数：task_id");
                responsePrint(resp, toJson(response));
                return;
            }

            TrainingTasks task = getTrainTaskByTaskId(taskId);

            if (task == null || task.getIsDeleted() == 1) {
                resp.setStatus(404);
                response.put("error", "任务不存在");
                responsePrint(resp, toJson(response));
                return;
            }

            // 构建响应数据
            Map<String, Object> data = new HashMap<>();
            data.put("task_id", task.getTaskId());
            data.put("status", task.getStatus());
            data.put("progress", task.getProgress());
            data.put("current_epoch", task.getCurrentEpoch());
            data.put("created_at", task.getCreatedAt());
            data.put("start_time", task.getStartTime());
            data.put("end_time", task.getEndTime());
            data.put("train_dir", task.getTrainDir());
            data.put("dataset_path", task.getDatasetPath());
            data.put("model_path", task.getModelPath());
            data.put("epochs", task.getEpochs());
            data.put("batch_size", task.getBatchSize());
            data.put("image_size", task.getImageSize() + "px");
            data.put("use_gpu", task.getUseGpu());

            String startTimeStr = task.getStartTime();
            String endTimeStr = task.getEndTime();
            String trainingDuration = calculateDuration(startTimeStr, endTimeStr, formatter);
            data.put("training_duration",trainingDuration);

            //返回模版相关信息
            //data.put("template", getTemplateInfo(task.getTemplateId()));

            response.put("code", "200");
            response.put("data", data);
            response.put("validate_time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            resp.setStatus(200);
            responsePrint(resp, toJson(response));
        } catch (Exception e) {
            resp.setStatus(500);
            response.put("error", "服务器内部错误：" + e.getMessage());
            responsePrint(resp, toJson(response));
        }
    }

//    /**
//     * 获取模板信息
//     * @param templateId
//     * @return
//     */
//    private FieldTemplateDto getTemplateInfo(Integer templateId) {
//        String sql = "SELECT * FROM field_template WHERE id = ?";
//        List<Map<String, Object>> result = getMysqlAdapter().select(sql, templateId);
//        Map<String, Object> map = result.get(0);
//        FieldTemplateDto fieldTemplate = new FieldTemplateDto();
//        fieldTemplate.setTemplateName(map.get("template_name").toString());
//        fieldTemplate.setTemplateDesc(map.get("template_desc").toString());
//
//        List<TrainingTaskField> fields = queryTrainingTaskField(templateId);
//        fieldTemplate.setTtf(fields);
//        return fieldTemplate;
//    }
//
//    private List<TrainingTaskField> queryTrainingTaskField(Integer templateId) {
//        String fieldsSql = "SELECT * FROM training_task_field WHERE template_id = ?";
//        try {
//            List<Map<String, Object>> fields = getMysqlAdapter().select(fieldsSql, templateId);
//            List<TrainingTaskField> ttf = new ArrayList<>();
//
//            for (Map<String, Object> field : fields) {
//                TrainingTaskField dto = new TrainingTaskField();
//                dto.setFieldName((String) field.get("field_name"));
//                dto.setFieldColumnName((String) field.get("field_column_name"));
//                dto.setFieldType((String) field.get("field_type"));
//                dto.setFieldDescription((String) field.get("field_description"));
//                dto.setFieldScope((String) field.get("field_scope"));
//                dto.setIsRequired((Integer) field.get("is_required"));
//                dto.setDefaultValue((Integer) field.get("default_value"));
//                dto.setRemark((String) field.get("remark"));
//                dto.setTemplateId((Integer) field.get("template_id"));
//                ttf.add(dto);
//            }
//
//            return ttf;
//        } catch (Exception e) {
//            log.error("查询字段定义失败: templateId={}, error={}", templateId, e.getMessage(), e);
//            return Collections.emptyList();
//        }
//
//    }
//
//    private List<FieldDefinitionDto> queryFieldDefinition(Integer templateId) {
//        String fieldsSql = "SELECT * FROM field_definition WHERE template_id = ? ORDER BY id ASC";
//        try {
//            List<Map<String, Object>> fields = getMysqlAdapter().select(fieldsSql, templateId);
//            List<FieldDefinitionDto> fieldDefinitions = new ArrayList<>();
//
//            for (Map<String, Object> field : fields) {
//                FieldDefinitionDto dto = new FieldDefinitionDto();
//                dto.setId(((Integer) field.get("id")));
//                dto.setFieldName((String) field.get("field_name"));
//                dto.setFieldColumnName((String) field.get("field_column_name"));
//                dto.setFieldType((String) field.get("field_type"));
//                dto.setFieldDescription((String) field.get("field_description"));
//                dto.setFieldScope((String) field.get("field_scope"));
//                dto.setIsRequired((Integer) field.get("is_required"));
//                dto.setDefaultValue((Integer) field.get("default_value"));
//                dto.setRemark((String) field.get("remark"));
//                dto.setTemplateId(((Integer) field.get("template_id")));
//                fieldDefinitions.add(dto);
//            }
//
//            return fieldDefinitions;
//        } catch (Exception e) {
//            log.error("查询字段定义失败: templateId={}, error={}", templateId, e.getMessage(), e);
//            return Collections.emptyList();
//        }
//    }


    /**
     * 训练任务实时监控
     */
    private void handleMonitorTask(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> response = new HashMap<>();
        try {
            String taskId = req.getParameter("task_id");
            if (taskId == null || taskId.isEmpty()) {
                resp.setStatus(400);
                response.put("error", "缺少参数：task_id");
                responsePrint(resp, toJson(response));
                return;
            }

            TrainingTasks task = getTrainTaskByTaskId(taskId);

            if (task == null || task.getIsDeleted() == 1) {
                resp.setStatus(404);
                response.put("error", "任务不存在");
                responsePrint(resp, toJson(response));
                return;
            }

            // 构建响应数据
            Map<String, Object> data = new HashMap<>();
            data.put("status",task.getStatus());
            data.put("progress", task.getProgress());
            data.put("current_epoch", task.getCurrentEpoch());
            data.put("epoch", task.getEpochs());

            String startTimeStr = task.getStartTime();
            String endTimeStr = task.getEndTime();
            String trainingDuration = calculateDuration(startTimeStr, endTimeStr, formatter);
            data.put("training_duration",trainingDuration);

            //获取日志
            TrainingLogs trainingLogs= getLogByTaskId(taskId);
            data.put("log", trainingLogs.getLogMessage());


            response.put("code", "200");
            response.put("data", data);


            resp.setStatus(200);
            responsePrint(resp, toJson(response));
        } catch (Exception e) {
            resp.setStatus(500);
            response.put("error", "服务器内部错误：" + e.getMessage());
            responsePrint(resp, toJson(response));
        }
    }

    private TrainingLogs getLogByTaskId(String taskId) {
        String sql = "SELECT * FROM training_logs WHERE task_id = ?";
        try {
            List<Map<String, Object>> result = getMysqlAdapter().select(sql, taskId);
            if (result != null && !result.isEmpty()) {
                Map<String, Object> log = result.get(0);
                TrainingLogs trainingLogs = new TrainingLogs();
                trainingLogs.setLogLevel((String) log.get("log_level"));
                trainingLogs.setLogMessage((String) log.get("log_message"));
                trainingLogs.setCreatedAt((String) log.get("created_at"));
                trainingLogs.setLogFilePath((String) log.get("log_file_path"));
                return trainingLogs;
            }
        } catch (Exception e) {
            log.error("获取训练日志失败: taskId={}, error={}", taskId, e.getMessage(), e);
        }
        return null;
    }

    // 时长计算方法
    private String calculateDuration(String startTimeStr, String endTimeStr, DateTimeFormatter formatter) {
        // 开始时间为空，直接返回未知
        if (startTimeStr == null || startTimeStr.trim().isEmpty()) {
            return "未知时长";
        }

        try {
            LocalDateTime startTime = LocalDateTime.parse(startTimeStr, formatter);
            LocalDateTime endTime;

            // 若结束时间为空（任务未完成），用当前时间作为结束时间
            if (endTimeStr == null || endTimeStr.trim().isEmpty()) {
                endTime = LocalDateTime.now();
            } else {
                endTime = LocalDateTime.parse(endTimeStr, formatter);
            }

            // 防止结束时间早于开始时间（数据异常）
            if (endTime.isBefore(startTime)) {
                return "0秒";
            }

            // 计算时差（小时、分钟、秒）
            Duration duration = Duration.between(startTime, endTime);
            long hours = duration.toHours();
            long minutes = duration.toMinutes() % 60;
            long seconds = duration.getSeconds() % 60;

            // 拼接时长字符串（忽略0值单位，如"0小时25分钟38秒"→"25分钟38秒"）
            StringBuilder sb = new StringBuilder();
            if (hours > 0) sb.append(hours).append("h");
            if (minutes > 0) sb.append(minutes).append("m");
            sb.append(seconds).append("s");

            return sb.toString();
        } catch (DateTimeParseException e) {
            // 时间格式错误（如数据库存储格式不符）
            return "时间格式错误";
        }
    }

    /**
     * 删除训练任务（POST）
     */
    private void handleDeleteTask(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> response = new HashMap<>();
        try {
            String taskId = req.getParameter("task_id");
            if (taskId == null || taskId.isEmpty()) {
                resp.setStatus(400);
                response.put("error", "缺少参数：task_id");
                responsePrint(resp, toJson(response));
                return;
            }

            TrainingTasks task = getTrainTaskByTaskId(taskId);
            if (task == null) {
                resp.setStatus(404);
                response.put("error", "任务不存在");
                responsePrint(resp, toJson(response));
                return;
            }

            if (task.getIsDeleted() == 1) {
                resp.setStatus(404);
                response.put("error", "任务已被删除");
                responsePrint(resp, toJson(response));
                return;
            }

            String result = deleteTrainTask(taskId, task);
            if (!"success".equals(result)) {
                resp.setStatus(400);
                response.put("error", result);
                responsePrint(resp, toJson(response));
                return;
            }

            response.put("task_id", taskId);
            response.put("msg", "任务已删除");
            response.put("status", "SUCCESS");

            resp.setStatus(200);
            responsePrint(resp, toJson(response));
        } catch (Exception e) {
            resp.setStatus(500);
            response.put("error", "服务器内部错误：" + e.getMessage());
            responsePrint(resp, toJson(response));
        }
    }

    /**
     * 批量删除训练任务
     */
    private void handleBatchDeleteTask(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> response = new HashMap<>();
        try {
            String jsonBody = requestToJson(req);
            JsonObject jsonObject = gson.fromJson(jsonBody, JsonObject.class);

            if (!jsonObject.has("task_id") || !jsonObject.get("task_id").isJsonArray()) {
                resp.setStatus(400);
                response.put("error", "缺少参数：task_id 或 task_id 不是数组");
                responsePrint(resp, toJson(response));
                return;
            }

            // 获取任务ID列表
            List<String> taskIds = new Gson().fromJson(jsonObject.get("task_id"), List.class);

            if (taskIds.isEmpty()) {
                resp.setStatus(400);
                response.put("error", "任务ID列表不能为空");
                responsePrint(resp, toJson(response));
                return;
            }

            List<String> successIds = new ArrayList<>();
            Map<String, String> failIds = new HashMap<>();

            for (String taskId : taskIds) {
                try {
                    TrainingTasks task = getTrainTaskByTaskId(taskId);
                    if (task == null) {
                        failIds.put(taskId, "任务不存在");
                        continue;
                    }

                    if (task.getIsDeleted() == 1) {
                        failIds.put(taskId, "任务已被删除");
                        continue;
                    }

                    String result = deleteTrainTask(taskId, task);
                    if ("success".equals(result)) {
                        successIds.add(taskId);
                    } else {
                        failIds.put(taskId, result);
                    }
                } catch (Exception e) {
                    failIds.put(taskId, "删除任务时发生异常：" + e.getMessage());
                }
            }

            response.put("task_ids", taskIds);
            response.put("success_ids", successIds);
            response.put("fail_ids", failIds);

            if (successIds.size() == taskIds.size()) {
                response.put("message", "所有任务已删除");
            } else if (successIds.size() > 0) {
                response.put("message", "部分任务已删除");
            } else {
                response.put("message", "没有任务被成功删除");
            }

            resp.setStatus(200);
            responsePrint(resp, toJson(response));
        } catch (Exception e) {
            resp.setStatus(500);
            response.put("error", "服务器内部错误：" + e.getMessage());
            responsePrint(resp, toJson(response));
        }
    }

    /**
     * 删除训练任务
     */
    private String deleteTrainTask(String taskId, TrainingTasks task) {
        currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        // 检查任务状态是否允许删除
        if (!TaskStatus.COMPLETED.getValue().equals(task.getStatus()) &&
                !TaskStatus.STOPPED.getValue().equals(task.getStatus()) &&
                !TaskStatus.FAILED.getValue().equals(task.getStatus())) {
            return "当前任务状态不允许删除(当前状态:" + task.getStatus() + ")";
        }

        String sql = "UPDATE training_tasks SET is_deleted = 1, deleted_at = ? WHERE task_id = ?";
        try {
            getMysqlAdapter().executeUpdate(
                    sql,
                    currentTime,
                    taskId
            );
            return "success";
        } catch (Exception e) {
            throw new RuntimeException("删除任务失败：" + e.getMessage(), e);
        }
    }

    /**
     * 通用训练任务日志记录
     */
    private void addTrainingTaskLog(String taskId, String logLevel, String logMessage) {
        currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String logFilePath = "/root/ai/train_logs/" + taskId + ".log"; // 日志文件路径

        // 构造日志条目：时间 + 日志级别 + 日志信息 + 换行（使用\n适配多数场景，或用System.lineSeparator()跨平台）
        String logEntry = currentTime + " " + logLevel + " " + logMessage + "\n";

        // 1. 检查该任务是否已存在日志记录
        String checkSql = "SELECT COUNT(*) AS cnt FROM training_logs WHERE task_id = ?";

        try {
            long logCount = 0;
            List<Map<String, Object>> result = getMysqlAdapter().select(checkSql, taskId);
            if (result != null && !result.isEmpty() && result.get(0).get("cnt") != null) {
                logCount = ((Number) result.get(0).get("cnt")).longValue();
            }

            if (logCount > 0) {
                // 2. 若存在日志，追加内容（拼接原有日志和新内容）
                String updateSql = "UPDATE training_logs " +
                        "SET log_message = CONCAT(IFNULL(log_message, ''), '; ', ?), " +
                        "log_level = ?, " +
                        "created_at = ? " +
                        "WHERE task_id = ?";
                getMysqlAdapter().executeUpdate(updateSql, logEntry, logLevel, currentTime, taskId);
            } else {
                // 3. 若不存在日志，直接插入新记录
                String insertSql = "INSERT INTO training_logs " +
                        "(task_id, log_level, log_message, created_at, log_file_path) " +
                        "VALUES (?, ?, ?, ?, ?)";
                getMysqlAdapter().executeUpdate(insertSql,
                        taskId,
                        logLevel,
                        logEntry,
                        currentTime,
                        logFilePath);
            }
        } catch (Exception e) {
            log.error("处理训练任务日志失败: taskId={}, error={}", taskId, e.getMessage(), e);
        }
    }

}
