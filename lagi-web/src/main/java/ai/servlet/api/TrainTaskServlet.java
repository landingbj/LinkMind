package ai.servlet.api;

import ai.database.impl.MysqlAdapter;
import ai.dto.TaskStatus;
import ai.dto.TrainingLogs;
import ai.dto.TrainingTasks;
import ai.servlet.BaseServlet;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;


import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;


@Slf4j
public class TrainTaskServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();
    private static final DateTimeFormatter UTC_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);
    private static String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

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
        } else {
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
     * 启动训练任务（POST）
     */
    private void handleStartTask(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            String jsonBody = requestToJson(req);
            JsonObject jsonNode = gson.fromJson(jsonBody, JsonObject.class);

            // 验证必填参数
            if (!jsonNode.has("dataset_path") || !jsonNode.has("model_path")) {
                resp.setStatus(400);
                result.put("error", "缺少必填参数：dataset_path或model_path");
                responsePrint(resp, toJson(result));
                return;
            }

            // 构建训练任务对象
            TrainingTasks task = new TrainingTasks();
            task.setDatasetPath(jsonNode.get("dataset_path").getAsString());
            task.setModelPath(jsonNode.get("model_path").getAsString());
            // 处理可选参数（使用默认值）
            task.setEpochs(jsonNode.has("epochs") ? jsonNode.get("epochs").getAsInt() : 10);
            task.setBatchSize(jsonNode.has("batch") ? jsonNode.get("batch").getAsInt() : 8);
            task.setImageSize(jsonNode.has("imgsz") ? jsonNode.get("imgsz").getAsInt() : 640);
            task.setUseGpu(jsonNode.has("use_gpu") ? jsonNode.get("use_gpu").getAsBoolean() : true);
            task.setStatusEnum(TaskStatus.RUNNING); // 任务启动状态
            task.setProgress("0%");
            task.setCurrentEpoch(0);
            task.setStartTime(currentTime);
            task.setCreatedAt(currentTime);

            // 保存任务到数据库并获取任务ID
            String taskId = saveStartTrainTaskToDB(task);


            // 构建返回结果
            result.put("status", TaskStatus.TASKRUNNING);
            result.put("task_id", taskId);
            result.put("message", "使用返回的task_id可执行进度查询、停止/继续训练、查看日志");

            Map<String, Object> trainConfig = new HashMap<>();
            trainConfig.put("dataset_path", task.getDatasetPath());
            trainConfig.put("model_path", task.getModelPath());
            trainConfig.put("epochs", task.getEpochs());
            trainConfig.put("batch", task.getBatchSize());
            trainConfig.put("imgsz", task.getImageSize());
            trainConfig.put("use_gpu", task.getUseGpu());
            result.put("train_config", trainConfig);
            result.put("created_at", currentTime);

            // 返回成功响应
            resp.setStatus(200);
            responsePrint(resp, toJson(result));
        } catch (Exception e) {
            resp.setStatus(500);
            result.put("error", "服务器内部错误：" + e.getMessage());
            responsePrint(resp, toJson(result));

        }
    }

    private String saveStartTrainTaskToDB(TrainingTasks task) {
        MysqlAdapter mysqlAdapter = new MysqlAdapter("mysql");
        String taskId = UUID.randomUUID().toString();
        task.setTaskId(taskId);
        String sql = "INSERT INTO training_tasks ( task_id, dataset_path, model_path, epochs, batch_size, image_size, use_gpu, status, progress, current_epoch, start_time, created_at) VALUES ( ?,?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?)";
        try {
            mysqlAdapter.executeUpdate(
                    sql,
                    task.getTaskId(),
                    task.getDatasetPath(),
                    task.getModelPath(),
                    task.getEpochs(),
                    task.getBatchSize(),
                    task.getImageSize(),
                    task.getUseGpu(),
                    task.getStatus(),
                    task.getProgress(),
                    task.getCurrentEpoch(),
                    currentTime,
                    currentTime
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

            // 构建响应
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
        } catch (Exception e) {
            resp.setStatus(500);
            response.put("error", "服务器内部错误：" + e.getMessage());
            resp.getWriter().print(gson.toJson(response));
            resp.getWriter().flush();
        }
    }


    private TrainingTasks getTrainTaskByTaskId(String taskId) {
        MysqlAdapter mysqlAdapter = new MysqlAdapter("mysql");
        String sql = "SELECT * FROM training_tasks WHERE task_id = ?";
        try {
            List<Map<String, Object>> list = mysqlAdapter.select(sql, taskId);
            if (list == null || list.isEmpty()) {
                return null;
            }

            Map<String, Object> map = list.get(0);
            TrainingTasks task = new TrainingTasks();

            task.setTaskId((String) map.get("task_id"));
            task.setStatus((String) map.get("status"));
            task.setProgress((String) map.get("progress"));
            task.setCurrentEpoch((Integer) map.get("current_epoch"));
            task.setTotalEpochs((Integer) map.get("total_epochs"));
            task.setStartTime((String) map.get("start_time"));
            task.setEndTime((String) map.get("end_time"));
            task.setUpdatedAt((String) map.get("updated_at"));
            task.setTrainDir((String) map.get("train_dir"));
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
            if (task == null) {
                resp.setStatus(404);
                response.put("error", "任务不存在");
                responsePrint(resp, toJson(response));
                return;
            }

            if (task.getStatus().equals("stop")) {
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

            // 刷新任务数据
            task = getTrainTaskByTaskId(taskId);

            // 构建响应
            response.put("task_id", taskId);
            response.put("status", TaskStatus.TASKSTOPPED);
            response.put("stopped_at", task.getEndTime());
            response.put("current_progress", task.getProgress());
            response.put("message", "可调用/resume接口继续训练");

            resp.setStatus(200);
            resp.getWriter().print(gson.toJson(response));
            resp.getWriter().flush();
        } catch (Exception e) {
            resp.setStatus(500);
            response.put("error", "服务器内部错误：" + e.getMessage());
            responsePrint(resp, toJson(response));
        }
    }


    private Boolean stopTrainTaskByTaskId(String taskId) {
        TrainingTasks task = getTrainTaskByTaskId(taskId);
        if (task == null || !TaskStatus.RUNNING.getValue().equals(task.getStatus())) {
            return false;
        }
        String sql = "UPDATE training_tasks SET status = ?, end_time = ?, updated_at = ? WHERE task_id = ?";
        MysqlAdapter mysqlAdapter = new MysqlAdapter("mysql");
        try {
            mysqlAdapter.executeUpdate(
                    sql,
                    TaskStatus.STOPPED.getValue(),
                    currentTime,
                    currentTime,
                    taskId
            );
            return true;
        } catch (Exception e) {
            throw new RuntimeException("停止任务失败：" + e.getMessage(), e);
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
                resp.getWriter().print(gson.toJson(response));
                resp.getWriter().flush();
                return;
            }

            TrainingTasks task = getTrainTaskByTaskId(taskId);
            if (task == null) {
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


            task = getTrainTaskByTaskId(taskId);
            // 构建响应
            response.put("task_id", taskId);
            response.put("status",TaskStatus.TASKRESUME);
            response.put("resumed_at", task.getUpdatedAt());
            response.put("resume_progress", task.getProgress());
            response.put("message", "可调用/progress接口查询最新进度");

            resp.setStatus(200);
            resp.getWriter().print(gson.toJson(response));
            resp.getWriter().flush();
        } catch (Exception e) {
            resp.setStatus(500);
            response.put("error", "服务器内部错误：" + e.getMessage());
            responsePrint(resp, toJson(response));
        }
    }

    private boolean resumeTrainTaskByTaskId(String taskId) {
        TrainingTasks task = getTrainTaskByTaskId(taskId);
        if (task == null || !TaskStatus.STOPPED.getValue().equals(task.getStatus())) {
            return false;
        }

        String sql = "UPDATE training_tasks " +
                "SET status = ?, updated_at = ? " +
                "WHERE task_id = ?";
        MysqlAdapter mysqlAdapter = new MysqlAdapter("mysql");
        try {
            mysqlAdapter.executeUpdate(
                    sql,
                    TaskStatus.RUNNING.getValue(),
                    currentTime,
                    taskId
            );
            return true;
        } catch (Exception e) {
            throw new RuntimeException("继续任务失败：" + e.getMessage(), e);
        }
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
        MysqlAdapter mysqlAdapter = new MysqlAdapter("mysql");
        String sql = "SELECT COUNT(*) AS count FROM training_tasks WHERE task_id = ?";
        try {
            List<Map<String, Object>> result = mysqlAdapter.select(sql, taskId);
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
        MysqlAdapter mysqlAdapter = new MysqlAdapter("mysql");

        String sql = "SELECT log_level, log_message, created_at, log_file_path " +
                "FROM training_logs " +
                "WHERE task_id = ? " +
                "ORDER BY created_at DESC " +
                "LIMIT ?";

        try {
            // 先查询出Map列表
            List<Map<String, Object>> mapList = mysqlAdapter.select(sql, taskId, logLines);
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
        MysqlAdapter mysqlAdapter = new MysqlAdapter("mysql");
        String sql = "SELECT COUNT(*) AS total FROM training_logs WHERE task_id = ?";
        try {
            List<Map<String, Object>> result = mysqlAdapter.select(sql, taskId);
            if (result != null && !result.isEmpty()) {
                return ((Number) result.get(0).get("total")).intValue();
            }
            return 0;
        } catch (Exception e) {
            throw new RuntimeException("查询日志总行数失败：" + e.getMessage(), e);
        }
    }
}
