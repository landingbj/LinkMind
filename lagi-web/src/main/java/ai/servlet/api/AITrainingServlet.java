package ai.servlet.api;

import ai.common.utils.ObservableList;
import ai.config.ContextLoader;
import ai.config.pojo.DiscriminativeModelsConfig;
import ai.dto.TrainingLogs;
import ai.dto.TrainingTasks;
import ai.finetune.YoloTrainerAdapter;
import ai.finetune.DeeplabAdapter;
import ai.finetune.TrackNetV3Adapter;
import ai.finetune.SSHConnectionManager;
import ai.finetune.repository.TrainingTaskRepository;
import ai.servlet.BaseServlet;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.google.gson.Gson;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * AI 模型训练任务管理 Servlet（通用版）
 * 支持任意 AI 模型的训练、评估、预测和导出
 * 包括但不限于：YOLOv8, YOLOv11, CenterNet, CRNN, HRNet, PIDNet, ResNet, OSNet等
 * 提供训练任务的完整生命周期管理和流式输出
 *
 * 扩展性：
 * - 通过 trainerMap 注册新模型的 Trainer
 * - 支持动态模型类别和框架推断
 * - 无法推断的模型自动归为 "custom" 类别
 */
@Slf4j
public class AITrainingServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    // 存储不同模型的 Trainer 实例
    private static final Map<String, Object> trainerMap = new ConcurrentHashMap<>();

    // 存储 YoloTrainer 实例（单例，向后兼容）
    public static YoloTrainerAdapter yoloTrainer;

    // 存储 DeeplabAdapter 实例
    public static DeeplabAdapter deeplabAdapter;

    // 存储任务 ID 到容器名称的映射
    private static final Map<String, String> taskContainerMap = new ConcurrentHashMap<>();

    // 存储任务ID到流式输出的映射
    private static final Map<String, ObservableList<String>> taskStreamMap = new ConcurrentHashMap<>();

    /**
     * 检查训练服务是否可用
     */
    private boolean isTrainerAvailable() {
        return yoloTrainer != null || deeplabAdapter != null || !trainerMap.isEmpty();
    }

    /**
     * 返回服务不可用的错误响应
     */
    private void sendServiceUnavailableError(HttpServletResponse response, String modelName) throws IOException {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType("application/json;charset=UTF-8");

        JSONObject error = new JSONObject();
        error.put("status", "error");
        error.put("code", "SERVICE_UNAVAILABLE");
        error.put("message", modelName != null
                ? "模型 " + modelName + " 训练服务未启用或配置不正确"
                : "AI 训练服务未启用或配置不正确");
        error.put("detail", "请检查 lagi.yml 中的 discriminative_models 配置");

        response.getWriter().write(error.toString());
    }

    @Override
    public void init() throws ServletException {
        super.init();

        // 初始化 YoloTrainer - 使用安全的初始化方式
        if (yoloTrainer == null) {
            try {
                ContextLoader.loadContext();
                // 从 lagi.yml 配置文件读取配置
                DiscriminativeModelsConfig discriminativeConfig = ContextLoader.configuration
                        .getModelPlatformConfig()
                        .getDiscriminativeModelsConfig();

                if (discriminativeConfig == null) {
                    log.warn("判别式模型配置(discriminative_models)不存在，AI训练模块将不可用");
                    return;
                }

                // 初始化 YOLO 模型训练器
                initYoloTrainer(discriminativeConfig);

                // 初始化 DeepLab 模型训练器
                initDeeplabTrainer(discriminativeConfig);

                // 初始化 TrackNetV3 模型训练器
                initTrackNetV3Trainer(discriminativeConfig);

                // TODO: 在这里初始化其他模型的训练器
                // initCenterNetTrainer(discriminativeConfig);
                // initCRNNTrainer(discriminativeConfig);
                // ...

                log.info("========================================");
                log.info("✓ AI 训练模块初始化完成");
                log.info("  - 可用模型: {}", String.join(", ", trainerMap.keySet()));
                log.info("========================================");

            } catch (Exception e) {
                log.error("========================================");
                log.error("✗ AI 训练模块初始化失败: {}", e.getMessage(), e);
                log.error("✗ AI 训练功能将不可用，但不影响其他模块");
                log.error("========================================");
                yoloTrainer = null;
            }
        }
    }

    /**
     * 初始化 YOLO 训练器
     */
    private void initYoloTrainer(DiscriminativeModelsConfig discriminativeConfig) {
        try {
            DiscriminativeModelsConfig.YoloConfig yoloConfig = discriminativeConfig.getYolo();

            if (yoloConfig == null) {
                log.warn("YOLO 配置(discriminative_models.yolo)不存在");
                return;
            }

            if (!Boolean.TRUE.equals(yoloConfig.getEnable())) {
                log.info("YOLO 训练模块未启用(enable=false)");
                return;
            }

            // 获取有效的 SSH 配置
            DiscriminativeModelsConfig.SshConfig ssh = discriminativeConfig.getEffectiveSshConfig(yoloConfig);
            DiscriminativeModelsConfig.DockerConfig docker = yoloConfig.getDocker();

            // 验证配置
            if (ssh == null || !ssh.isValid()) {
                log.error("YOLO SSH 配置不完整或无效");
                return;
            }

            if (docker == null || !docker.isValid()) {
                log.error("YOLO Docker 配置不完整或无效");
                return;
            }

            // 创建 YoloTrainer 实例
            yoloTrainer = new YoloTrainerAdapter();
            yoloTrainer.setRemoteServer(ssh.getHost(), ssh.getPort(), ssh.getUsername(), ssh.getPassword());

            if (docker.getImage() != null) {
                yoloTrainer.setDockerImage(docker.getImage());
            }
            if (docker.getVolumeMount() != null) {
                yoloTrainer.setVolumeMount(docker.getVolumeMount());
            }

            // 注册到 trainerMap
            trainerMap.put("yolov8", yoloTrainer);
            trainerMap.put("yolov11", yoloTrainer); // YOLOv11 也使用同一个 trainer

            log.info("✓ YOLO 训练器初始化成功: {}:{}", ssh.getHost(), ssh.getPort());

        } catch (Exception e) {
            log.error("YOLO 训练器初始化失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 初始化 DeepLab 训练器
     */
    private void initDeeplabTrainer(DiscriminativeModelsConfig discriminativeConfig) {
        try {
            DiscriminativeModelsConfig.DeeplabConfig deeplabConfig = discriminativeConfig.getDeeplab();

            if (deeplabConfig == null) {
                log.warn("DeepLab 配置(discriminative_models.deeplab)不存在");
                return;
            }

            if (!Boolean.TRUE.equals(deeplabConfig.getEnable())) {
                log.info("DeepLab 训练模块未启用(enable=false)");
                return;
            }

            // 获取有效的 SSH 配置
            DiscriminativeModelsConfig.SshConfig ssh = discriminativeConfig.getEffectiveSshConfig(deeplabConfig);
            DiscriminativeModelsConfig.DockerConfig docker = deeplabConfig.getDocker();

            // 验证配置
            if (ssh == null || !ssh.isValid()) {
                log.error("DeepLab SSH 配置不完整或无效");
                return;
            }

            if (docker == null || !docker.isValid()) {
                log.error("DeepLab Docker 配置不完整或无效");
                return;
            }

            // 创建 DeeplabAdapter 实例
            deeplabAdapter = new DeeplabAdapter();
            deeplabAdapter.setRemoteServer(ssh.getHost(), ssh.getPort(), ssh.getUsername(), ssh.getPassword());

            if (docker.getImage() != null) {
                deeplabAdapter.setDockerImage(docker.getImage());
            }
            if (docker.getVolumeMount() != null) {
                deeplabAdapter.setVolumeMount(docker.getVolumeMount());
            }

            // 注册到 trainerMap
            trainerMap.put("deeplab", deeplabAdapter);
            trainerMap.put("deeplabv3", deeplabAdapter); // deeplabv3 也使用同一个 adapter

            log.info("✓ DeepLab 训练器初始化成功: {}:{}", ssh.getHost(), ssh.getPort());

        } catch (Exception e) {
            log.error("DeepLab 训练器初始化失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 初始化 TrackNetV3 训练器
     */
    private void initTrackNetV3Trainer(DiscriminativeModelsConfig discriminativeConfig) {
        try {
            DiscriminativeModelsConfig.TrackNetV3Config tracknetv3Config = discriminativeConfig.getTracknetv3();

            if (tracknetv3Config == null) {
                log.warn("TrackNetV3 配置(discriminative_models.tracknetv3)不存在");
                return;
            }

            if (!Boolean.TRUE.equals(tracknetv3Config.getEnable())) {
                log.info("TrackNetV3 训练模块未启用(enable=false)");
                return;
            }

            // 获取有效的 SSH 配置
            DiscriminativeModelsConfig.SshConfig ssh = discriminativeConfig.getEffectiveSshConfig(tracknetv3Config);
            DiscriminativeModelsConfig.DockerConfig docker = tracknetv3Config.getDocker();

            // 验证配置
            if (ssh == null || !ssh.isValid()) {
                log.error("TrackNetV3 SSH 配置不完整或无效");
                return;
            }

            if (docker == null || !docker.isValid()) {
                log.error("TrackNetV3 Docker 配置不完整或无效");
                return;
            }

            // 创建 TrackNetV3Adapter 实例
            TrackNetV3Adapter trackNetV3Adapter = new TrackNetV3Adapter();
            trackNetV3Adapter.setRemoteServer(ssh.getHost(), ssh.getPort(), ssh.getUsername(), ssh.getPassword());

            if (docker.getImage() != null) {
                trackNetV3Adapter.setDockerImage(docker.getImage());
            }
            if (docker.getVolumeMount() != null) {
                trackNetV3Adapter.setVolumeMount(docker.getVolumeMount());
            }

            // 注册到 trainerMap
            trainerMap.put("tracknetv3", trackNetV3Adapter);
            trainerMap.put("tracknet", trackNetV3Adapter); // tracknet 也使用同一个 adapter
            trainerMap.put("TrackNetV2", trackNetV3Adapter);

            log.info("✓ TrackNetV3 训练器初始化成功: {}:{}", ssh.getHost(), ssh.getPort());

        } catch (Exception e) {
            log.error("TrackNetV3 训练器初始化失败: {}", e.getMessage(), e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!isTrainerAvailable()) {
            sendServiceUnavailableError(resp, null);
            return;
        }

        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);

        switch (method) {
            case "start":
                handleStartTraining(req, resp);
                break;
            case "pause":
                handlePauseContainer(req, resp);
                break;
            case "resume":
                handleResumeContainer(req, resp);
                break;
            case "stop":
                handleStopContainer(req, resp);
                break;
            case "remove":
                handleRemoveContainer(req, resp);
                break;
            case "deleted":
                handleDeletedContainer(req, resp);
                break;
            case "status":
                handleGetStatus(req, resp);
                break;
            case "logs":
                handleGetLogs(req, resp);
                break;
            case "stream":
                handleStreamLogs(req, resp);
                break;
            case "evaluate":
                // TODO: 评估（也是推理验证）
                handleEvaluate(req, resp);
                break;
            case "predict":
                // TODO: 预测
                handlePredict(req, resp);
                break;
            case "export":
                // TODO: 导出模型
                handleExportModel(req, resp);
                break;
            case "upload":
                handleUploadFile(req, resp);
                break;
            case "download":
                handleDownloadFile(req, resp);
                break;
            case "commit":
                handleCommitImage(req, resp);
                break;
            case "list":
                handleListContainers(req, resp);
                break;
            case "models":
                handleListModels(req, resp);
                break;
            case "detail":
                handledetail(req, resp);
                break;
            case "resources":
                handleGetResourceUsage(req, resp);
                break;
            case "updateData":
                handleUpdateData(req, resp);
                break;

            case "monitor":
                handleMonitor(req, resp);
                break;
            default:
                resp.setStatus(404);
                Map<String, String> error = new HashMap<>();
                error.put("error", "接口不存在: " + method);
                responsePrint(resp, toJson(error));
        }
    }

    private void handleMonitor(HttpServletRequest req, HttpServletResponse resp) {
        // TODO: 添加监控功能
    }


    private void handleUpdateData(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> response = new HashMap<>();

        try {
            String jsonBody = requestToJson(req);
            JSONObject requestJson = JSONUtil.parseObj(jsonBody);

            // 必须提供 task_id
            String taskId = requestJson.getStr("task_id");
            if (taskId == null || taskId.isEmpty()) {
                resp.setStatus(400);
                response.put("code", "400");
                response.put("message", "缺少参数: task_id");
                responsePrint(resp, toJson(response));
                return;
            }

            // 构建更新数据
            Map<String, Object> updateData = new HashMap<>();

            // 只有当字段存在时才添加到更新数据中
            if (requestJson.containsKey("track_id")) {
                updateData.put("track_id", requestJson.getStr("track_id"));
            }
            if (requestJson.containsKey("model_name")) {
                updateData.put("model_name", requestJson.getStr("model_name"));
            }
            if (requestJson.containsKey("model_version")) {
                updateData.put("model_version", requestJson.getStr("model_version"));
            }
            if (requestJson.containsKey("model_framework")) {
                updateData.put("model_framework", requestJson.getStr("model_framework"));
            }
            if (requestJson.containsKey("model_category")) {
                updateData.put("model_category", requestJson.getStr("model_category"));
            }
            if (requestJson.containsKey("task_type")) {
                updateData.put("task_type", requestJson.getStr("task_type"));
            }
            if (requestJson.containsKey("container_name")) {
                updateData.put("container_name", requestJson.getStr("container_name"));
            }
            if (requestJson.containsKey("container_id")) {
                updateData.put("container_id", requestJson.getStr("container_id"));
            }
            if (requestJson.containsKey("docker_image")) {
                updateData.put("docker_image", requestJson.getStr("docker_image"));
            }
            if (requestJson.containsKey("gpu_ids")) {
                updateData.put("gpu_ids", requestJson.getStr("gpu_ids"));
            }
            if (requestJson.containsKey("use_gpu")) {
                updateData.put("use_gpu", requestJson.getInt("use_gpu"));
            }
            if (requestJson.containsKey("dataset_path")) {
                updateData.put("dataset_path", requestJson.getStr("dataset_path"));
            }
            if (requestJson.containsKey("dataset_name")) {
                updateData.put("dataset_name", requestJson.getStr("dataset_name"));
            }
            if (requestJson.containsKey("dataset_type")) {
                updateData.put("dataset_type", requestJson.getStr("dataset_type"));
            }
            if (requestJson.containsKey("num_classes")) {
                updateData.put("num_classes", requestJson.getInt("num_classes"));
            }
            if (requestJson.containsKey("model_path")) {
                updateData.put("model_path", requestJson.getStr("model_path"));
            }
            if (requestJson.containsKey("checkpoint_path")) {
                updateData.put("checkpoint_path", requestJson.getStr("checkpoint_path"));
            }
            if (requestJson.containsKey("output_path")) {
                updateData.put("output_path", requestJson.getStr("output_path"));
            }
            if (requestJson.containsKey("epochs")) {
                updateData.put("epochs", requestJson.getInt("epochs"));
            }
            if (requestJson.containsKey("batch_size")) {
                updateData.put("batch_size", requestJson.getInt("batch_size"));
            }
            if (requestJson.containsKey("learning_rate")) {
                updateData.put("learning_rate", requestJson.getBigDecimal("learning_rate"));
            }
            if (requestJson.containsKey("image_size")) {
                updateData.put("image_size", requestJson.getStr("image_size"));
            }
            if (requestJson.containsKey("optimizer")) {
                updateData.put("optimizer", requestJson.getStr("optimizer"));
            }
            if (requestJson.containsKey("status")) {
                updateData.put("status", requestJson.getStr("status"));
            }
            if (requestJson.containsKey("progress")) {
                updateData.put("progress", requestJson.getStr("progress"));
            }
            if (requestJson.containsKey("current_epoch")) {
                updateData.put("current_epoch", requestJson.getInt("current_epoch"));
            }
            if (requestJson.containsKey("current_step")) {
                updateData.put("current_step", requestJson.getInt("current_step"));
            }
            if (requestJson.containsKey("total_steps")) {
                updateData.put("total_steps", requestJson.getInt("total_steps"));
            }
            if (requestJson.containsKey("train_loss")) {
                updateData.put("train_loss", requestJson.getBigDecimal("train_loss"));
            }
            if (requestJson.containsKey("val_loss")) {
                updateData.put("val_loss", requestJson.getBigDecimal("val_loss"));
            }
            if (requestJson.containsKey("train_acc")) {
                updateData.put("train_acc", requestJson.getBigDecimal("train_acc"));
            }
            if (requestJson.containsKey("val_acc")) {
                updateData.put("val_acc", requestJson.getBigDecimal("val_acc"));
            }
            if (requestJson.containsKey("best_metric")) {
                updateData.put("best_metric", requestJson.getBigDecimal("best_metric"));
            }
            if (requestJson.containsKey("best_metric_name")) {
                updateData.put("best_metric_name", requestJson.getStr("best_metric_name"));
            }
            if (requestJson.containsKey("train_dir")) {
                updateData.put("train_dir", requestJson.getStr("train_dir"));
            }
            if (requestJson.containsKey("weights_path")) {
                updateData.put("weights_path", requestJson.getStr("weights_path"));
            }
            if (requestJson.containsKey("best_weights_path")) {
                updateData.put("best_weights_path", requestJson.getStr("best_weights_path"));
            }
            if (requestJson.containsKey("log_file_path")) {
                updateData.put("log_file_path", requestJson.getStr("log_file_path"));
            }
            if (requestJson.containsKey("error_message")) {
                updateData.put("error_message", requestJson.getStr("error_message"));
            }
            if (requestJson.containsKey("start_time")) {
                updateData.put("start_time", requestJson.getStr("start_time"));
            }
            if (requestJson.containsKey("end_time")) {
                updateData.put("end_time", requestJson.getStr("end_time"));
            }
            if (requestJson.containsKey("estimated_time")) {
                updateData.put("estimated_time", requestJson.getLong("estimated_time"));
            }
            if (requestJson.containsKey("created_at")) {
                updateData.put("created_at", requestJson.getStr("created_at"));
            }
            if (requestJson.containsKey("updated_at")) {
                updateData.put("updated_at", requestJson.getStr("updated_at"));
            }
            if (requestJson.containsKey("deleted_at")) {
                updateData.put("deleted_at", requestJson.getStr("deleted_at"));
            }
            if (requestJson.containsKey("is_deleted")) {
                updateData.put("is_deleted", requestJson.getInt("is_deleted"));
            }
            if (requestJson.containsKey("user_id")) {
                updateData.put("user_id", requestJson.getStr("user_id"));
            }
            if (requestJson.containsKey("project_id")) {
                updateData.put("project_id", requestJson.getStr("project_id"));
            }
            if (requestJson.containsKey("template_id")) {
                updateData.put("template_id", requestJson.getInt("template_id"));
            }
            if (requestJson.containsKey("priority")) {
                updateData.put("priority", requestJson.getInt("priority"));
            }
            if (requestJson.containsKey("tags")) {
                updateData.put("tags", requestJson.getStr("tags"));
            }
            if (requestJson.containsKey("remark")) {
                updateData.put("remark", requestJson.getStr("remark"));
            }
            if (requestJson.containsKey("config_json")) {
                updateData.put("config_json", requestJson.getStr("config_json"));
            }

            // 执行更新操作
            TrainingTaskRepository repository = yoloTrainer.getRepository();
            boolean success = repository.updateTaskByTaskId(taskId, updateData);

            if (success) {
                response.put("code", "200");
                response.put("message", "更新成功");
            } else {
                resp.setStatus(500);
                response.put("code", "500");
                response.put("message", "更新失败");
            }

            responsePrint(resp, toJson(response));

        } catch (Exception e) {
            log.error("更新训练任务数据失败", e);
            resp.setStatus(500);
            response.put("code", "500");
            response.put("message", "更新训练任务数据失败: " + e.getMessage());
            responsePrint(resp, toJson(response));
        }
    }

    private void handledetail(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> response = new HashMap<>();

        try {
            String jsonBody = requestToJson(req);
            JSONObject requestJson = JSONUtil.parseObj(jsonBody);
            String taskId = requestJson.getStr("task_id");

            if (taskId == null || taskId.isEmpty()) {
                resp.setStatus(400);
                response.put("code", "400");
                response.put("message", "缺少参数: task_id");
                responsePrint(resp, toJson(response));
                return;
            }

            // 从数据库查询任务详情
            TrainingTaskRepository repository = yoloTrainer.getRepository();
            Map<String, Object> taskDetail = repository.getTaskDetailByTaskId(taskId);

            if (taskDetail == null) {
                resp.setStatus(404);
                response.put("code", "404");
                response.put("message", "未找到任务: " + taskId);
                responsePrint(resp, toJson(response));
                return;
            }

            // 构建返回数据
            Map<String, Object> result = new HashMap<>();

            //这里获取的是template_info的主键id
            Integer tempId = (Integer) taskDetail.get("template_id");

            if (tempId != null && tempId > 0) {
                Map<String, Object> templateInfo = repository.getTemplateInfoById(tempId);
                if (templateInfo != null) {
                    //这里的templateId是template_info表中的template_id
                    String templateId = (String) templateInfo.get("template_id");
                    // 查询模板字段
                    List<Map<String, Object>> templateFields = repository.getTemplateFieldsByTemplateId(templateId);
                    templateInfo.put("fields", templateFields);
                }
                result.put("template", templateInfo);
            }

            // 计算训练时长
            //Object startTimeObj = taskDetail.get("start_time");
            //Object endTimeObj = taskDetail.get("end_time");
            //String trainingDuration = calculateTrainingDuration(startTimeObj, endTimeObj);
           // result.put("training_duration", trainingDuration);
            result.put("task", taskDetail);

            response.put("code", "200");
            response.put("data", result);
            responsePrint(resp, toJson(response));

        } catch (Exception e) {
            log.error("查询任务详情失败", e);
            resp.setStatus(500);
            response.put("code", "500");
            response.put("message", "查询任务详情失败: " + e.getMessage());
            responsePrint(resp, toJson(response));
        }
    }

    /**
     * 计算训练时长
     */
    private String calculateTrainingDuration(Object startTimeObj, Object endTimeObj) {
        try {
            if (startTimeObj == null) {
                return "0s";
            }

            LocalDateTime startTime = null;
            if (startTimeObj instanceof String) {
                startTime = LocalDateTime.parse((String) startTimeObj, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } else if (startTimeObj instanceof Timestamp) {
                startTime = ((Timestamp) startTimeObj).toLocalDateTime();
            }

            LocalDateTime endTime = LocalDateTime.now();
            if (endTimeObj != null) {
                if (endTimeObj instanceof String) {
                    endTime = LocalDateTime.parse((String) endTimeObj, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                } else if (endTimeObj instanceof Timestamp) {
                    endTime = ((Timestamp) endTimeObj).toLocalDateTime();
                }
            }

            if (startTime != null) {
                long durationSeconds = java.time.Duration.between(startTime, endTime).getSeconds();
                long hours = durationSeconds / 3600;
                long minutes = (durationSeconds % 3600) / 60;
                long seconds = durationSeconds % 60;

                if (hours > 0) {
                    return String.format("%dh%dm%ds", hours, minutes, seconds);
                } else if (minutes > 0) {
                    return String.format("%dm%ds", minutes, seconds);
                } else {
                    return String.format("%ds", seconds);
                }
            }
        } catch (Exception e) {
            log.warn("计算训练时长失败", e);
        }
        return "未知";
    }

    /**
     * 将对象转换为布尔值
     */
    private boolean convertToBoolean(Object value) {
        if (value == null) {
            return false;
        }

        if (value instanceof Boolean) {
            return (Boolean) value;
        }

        if (value instanceof Number) {
            return ((Number) value).intValue() == 1;
        }

        if (value instanceof String) {
            String str = (String) value;
            return "1".equals(str) || "true".equalsIgnoreCase(str);
        }

        return false;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doGet(req, resp);
    }

    /**
     * 启动训练任务（通用版，支持所有模型）
     */
    private void handleStartTraining(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            String jsonBody = requestToJson(req);
            JSONObject config = JSONUtil.parseObj(jsonBody);

            // 获取用户ID（可选，但建议提供）
            String userId = config.getStr("user_id");

            // 获取模型名称（必填）
            String modelName = config.getStr("model_name");
            if (modelName == null || modelName.isEmpty()) {
                resp.setStatus(400);
                Map<String, String> error = new HashMap<>();
                error.put("error", "缺少必填参数: model_name");
                error.put("available_models", String.join(", ", trainerMap.keySet()));
                responsePrint(resp, toJson(error));
                return;
            }

            // 检查模型是否支持（忽略大小写和空格）
            String normalizedModelName = modelName.toLowerCase().trim();
            Object trainer = trainerMap.get(normalizedModelName);
            if (trainer == null) {
                // 如果通过标准方式没找到，尝试忽略大小写查找
                for (Map.Entry<String, Object> entry : trainerMap.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(normalizedModelName)) {
                        trainer = entry.getValue();
                        break;
                    }
                }
            }
            if (trainer == null) {
                resp.setStatus(400);
                Map<String, String> error = new HashMap<>();
                error.put("error", "不支持的模型: " + modelName);
                error.put("available_models", String.join(", ", trainerMap.keySet()));
                responsePrint(resp, toJson(error));
                return;
            }

            // 生成或获取任务ID
            String taskId = config.getStr("task_id");
            if (taskId == null || taskId.isEmpty()) {
                taskId = YoloTrainerAdapter.generateTaskId();
            }

            String trackId = config.getStr("track_id");
            if (trackId == null || trackId.isEmpty()) {
                trackId = YoloTrainerAdapter.generateTrackId();
            }

            final String finalTaskId = taskId;
            final String finalTrackId = trackId;
            final String finalUserId = userId;
            final String finalModelName = modelName;
            final JSONObject finalConfig = config;
            final Object finalTrainer = trainer;

            asyncTaskExecutor.submit(() -> {
                try {
                    log.info("开始异步执行训练任务: taskId={}, model={}", finalTaskId, finalModelName);

                    // 根据模型类型调用对应的训练器
                    String result;
                    if (finalTrainer instanceof YoloTrainerAdapter) {
                        result = startYoloTraining((YoloTrainerAdapter) finalTrainer, finalTaskId, finalTrackId, finalUserId, finalConfig);
                    } else if (finalTrainer instanceof DeeplabAdapter) {
                        result = startDeeplabTraining((DeeplabAdapter) finalTrainer, finalTaskId, finalTrackId, finalUserId, finalConfig);
                    } else if (finalTrainer instanceof TrackNetV3Adapter) {
                        result = startTrackNetV3Training((TrackNetV3Adapter) finalTrainer, finalTaskId, finalTrackId, finalUserId, finalConfig);
                    } else {
                        log.error("不支持的训练器类型: taskId={}, trainerClass={}", finalTaskId, finalTrainer.getClass().getName());
                        return;
                    }

                    // 保存任务到容器的映射
                    if (YoloTrainerAdapter.isSuccess(result)) {
                        JSONObject resultJson = JSONUtil.parseObj(result);
                        String containerName = resultJson.getStr("containerName");
                        String containerId = resultJson.getStr("containerId");

                        if (containerName != null) {
                            // 使用同步方法更新共享的Map
                            synchronized (taskContainerMap) {
                                taskContainerMap.put(finalTaskId, containerName);
                            }
                        }

                        log.info("训练任务启动成功: taskId={}, container={}", finalTaskId, containerName);

                        // 如果需要返回更详细的信息，可以在这里记录到数据库或日志
                        if (containerId != null) {
                            log.info("训练任务容器信息: taskId={}, containerId={}", finalTaskId, containerId);
                        }
                    } else {
                        // 训练启动失败
                        log.error("训练任务启动失败: taskId={}, result={}", finalTaskId, result);

                        // 尝试解析原始错误信息
                        String errorMsg = "训练任务启动失败";
                        try {
                            JSONObject resultJson = JSONUtil.parseObj(result);
                            String message = resultJson.getStr("message");
                            if (message != null && !message.isEmpty()) {
                                errorMsg = message;
                            }
                            if (resultJson.containsKey("error")) {
                                errorMsg += ": " + resultJson.getStr("error");
                            }
                        } catch (Exception e) {
                            // 如果解析失败，使用原始结果作为错误信息
                            if (result != null && !result.isEmpty()) {
                                errorMsg += ": " + result;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("异步训练任务执行异常: taskId={}", finalTaskId, e);
                }
            });

            // 立即返回响应，告知任务已提交
            JSONObject response = new JSONObject();
            response.put("status", "success");
            response.put("msg", "训练任务已提交");
            response.put("task_id", taskId);
            response.put("track_id", trackId);
            response.put("model_name", config.getStr("model_name", modelName));
            response.put("user_id", userId != null ? userId : "");

            String timestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            response.put("submit_time", timestamp);
            response.put("queue_status", "PENDING");

            // 添加创建时间（ISO 8601格式）
            String createdAt = java.time.ZonedDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ISO_INSTANT);
            response.put("created_at", createdAt);

            responsePrint(resp, response.toString());

        } catch (Exception e) {
            log.error("启动训练任务失败", e);
            resp.setStatus(500);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            responsePrint(resp, toJson(error));
        }
    }

    /**
     * 启动 YOLO 训练任务
     */
    private String startYoloTraining(YoloTrainerAdapter trainer, String taskId, String trackId, String userId, JSONObject config) {
        // 从配置文件获取默认配置
        ai.config.ContextLoader.loadContext();
        ai.config.pojo.DiscriminativeModelsConfig discriminativeConfig = ai.config.ContextLoader.configuration
                .getModelPlatformConfig()
                .getDiscriminativeModelsConfig();

        Map<String, Object> defaultConfig = null;
        if (discriminativeConfig != null && discriminativeConfig.getYolo() != null) {
            defaultConfig = discriminativeConfig.getYolo().getDefaultConfig();
        }

        // 构建训练配置
        JSONObject trainConfig = new JSONObject();
        trainConfig.put("the_train_type", "train");
        trainConfig.put("task_id", taskId);
        trainConfig.put("track_id", trackId);
        trainConfig.put("model_name", config.getStr("model_name", "yolov8")); // 传递模型名称
        // 如果用户没有指定 train_log_file，不设置默认值，让 YoloTrainerAdapter 根据 taskId 自动生成
        String userTrainLogFile = config.getStr("train_log_file");
        if (userTrainLogFile != null && !userTrainLogFile.isEmpty()) {
            trainConfig.put("train_log_file", userTrainLogFile);
        }
        // 如果没有指定，不设置 train_log_file，让适配器根据 taskId 自动生成到 /app/data/log/train/{taskId}.log

        // 添加用户ID到配置中
        if (userId != null && !userId.isEmpty()) {
            trainConfig.put("user_id", userId);
        }

        // 添加模板ID到配置中
        Integer templateId = config.getInt("template_id");
        if (templateId != null) {
            trainConfig.put("template_id", templateId);
        }

        // 使用配置文件的默认值
        if (defaultConfig != null && !defaultConfig.isEmpty()) {
            trainConfig.put("model_path", config.getStr("model_path", (String) defaultConfig.get("model_path")));
            trainConfig.put("data", config.getStr("data", (String) defaultConfig.get("data")));
            trainConfig.put("epochs", config.getInt("epochs", (Integer) defaultConfig.get("epochs")));
            trainConfig.put("batch", config.getInt("batch", (Integer) defaultConfig.get("batch")));
            trainConfig.put("imgsz", config.getInt("imgsz", (Integer) defaultConfig.get("imgsz")));
            trainConfig.put("device", config.getStr("device", (String) defaultConfig.get("device")));
            trainConfig.put("project", config.getStr("project", (String) defaultConfig.get("project")));
            trainConfig.put("runs_dir", config.getStr("runs_dir", (String) defaultConfig.get("runs_dir")));
        } else {
            trainConfig.put("model_path", config.getStr("model_path", "/app/data/models/yolo11n.pt"));
            trainConfig.put("data", config.getStr("data", "/app/data/datasets/YoloV8/data.yaml"));
            trainConfig.put("epochs", config.getInt("epochs", 10));
            trainConfig.put("batch", config.getInt("batch", 2));
            trainConfig.put("imgsz", config.getInt("imgsz", 640));
            trainConfig.put("device", config.getStr("device", "0"));
            trainConfig.put("project", config.getStr("project", "/app/data/project"));
            trainConfig.put("runs_dir", config.getStr("runs_dir", "/app/data"));
        }

        trainConfig.put("exist_ok", config.getBool("exist_ok", true));
        trainConfig.put("name", config.getStr("name", "yolo_experiment_" + System.currentTimeMillis()));

        return trainer.startTraining(taskId, trackId, trainConfig);
    }

    /**
     * 启动 DeepLab 训练任务
     */
    private String startDeeplabTraining(DeeplabAdapter trainer, String taskId, String trackId, String userId, JSONObject config) {
        // 从配置文件获取默认配置
        ai.config.ContextLoader.loadContext();
        ai.config.pojo.DiscriminativeModelsConfig discriminativeConfig = ai.config.ContextLoader.configuration
                .getModelPlatformConfig()
                .getDiscriminativeModelsConfig();

        Map<String, Object> defaultConfig = null;
        if (discriminativeConfig != null && discriminativeConfig.getDeeplab() != null) {
            defaultConfig = discriminativeConfig.getDeeplab().getDefaultConfig();
        }

        // 构建训练配置
        JSONObject trainConfig = new JSONObject();
        trainConfig.put("the_train_type", "train");
        trainConfig.put("task_id", taskId);
        trainConfig.put("track_id", trackId);
        trainConfig.put("model_name", config.getStr("model_name", "deeplabv3")); // 传递模型名称
        // 如果用户没有指定 train_log_file，不设置默认值，让 DeeplabAdapter 根据 taskId 自动生成
        String userTrainLogFile = config.getStr("train_log_file");
        if (userTrainLogFile != null && !userTrainLogFile.isEmpty()) {
            trainConfig.put("train_log_file", userTrainLogFile);
        }
        // 如果没有指定，不设置 train_log_file，让适配器根据 taskId 自动生成到 /app/data/log/train/{taskId}.log

        // 添加用户ID到配置中
        if (userId != null && !userId.isEmpty()) {
            trainConfig.put("user_id", userId);
        }

        // 添加模板ID到配置中
        Integer templateId = config.getInt("template_id");
        if (templateId != null) {
            trainConfig.put("template_id", templateId);
        }

        // 使用配置文件的默认值
        if (defaultConfig != null && !defaultConfig.isEmpty()) {
            trainConfig.put("model_path", config.getStr("model_path", (String) defaultConfig.get("model_path")));
            trainConfig.put("dataset_path", config.getStr("dataset_path", (String) defaultConfig.get("dataset_path")));
            trainConfig.put("num_classes", config.getInt("num_classes", (Integer) defaultConfig.get("num_classes")));
            trainConfig.put("backbone", config.getStr("backbone", (String) defaultConfig.get("backbone")));
            trainConfig.put("input_shape", config.getInt("input_shape", (Integer) defaultConfig.get("input_shape")));
            trainConfig.put("freeze_train", config.getBool("freeze_train", (Boolean) defaultConfig.get("freeze_train")));
            trainConfig.put("freeze_epoch", config.getInt("freeze_epoch", (Integer) defaultConfig.get("freeze_epoch")));
            trainConfig.put("freeze_batch_size", config.getInt("freeze_batch_size", (Integer) defaultConfig.get("freeze_batch_size")));
            trainConfig.put("epochs", config.getInt("epochs", (Integer) defaultConfig.get("epochs")));
            trainConfig.put("un_freeze_batch_size", config.getInt("un_freeze_batch_size", (Integer) defaultConfig.get("un_freeze_batch_size")));
            trainConfig.put("cuda", config.getBool("cuda", (Boolean) defaultConfig.get("cuda")));
            trainConfig.put("distributed", config.getBool("distributed", (Boolean) defaultConfig.get("distributed")));
            trainConfig.put("fp16", config.getBool("fp16", (Boolean) defaultConfig.get("fp16")));
            trainConfig.put("save_dir", config.getStr("save_dir", (String) defaultConfig.get("save_dir")));
            trainConfig.put("save_period", config.getInt("save_period", (Integer) defaultConfig.get("save_period")));
            trainConfig.put("eval_flag", config.getBool("eval_flag", (Boolean) defaultConfig.get("eval_flag")));
            trainConfig.put("eval_period", config.getInt("eval_period", (Integer) defaultConfig.get("eval_period")));
            trainConfig.put("focal_loss", config.getBool("focal_loss", (Boolean) defaultConfig.get("focal_loss")));
            trainConfig.put("num_workers", config.getInt("num_workers", (Integer) defaultConfig.get("num_workers")));
            trainConfig.put("optimizer_type", config.getStr("optimizer_type", (String) defaultConfig.get("optimizer_type")));
            trainConfig.put("momentum", config.getDouble("momentum", (Double) defaultConfig.get("momentum")));
            trainConfig.put("weight_decay", config.getDouble("weight_decay", (Double) defaultConfig.get("weight_decay")));
            trainConfig.put("init_lr", config.getDouble("init_lr", (Double) defaultConfig.get("init_lr")));
            trainConfig.put("min_lr", config.getDouble("min_lr", (Double) defaultConfig.get("min_lr")));
        } else {
            // 使用默认值
            trainConfig.put("model_path", config.getStr("model_path", "/app/data/models/deeplab_mobilenetv2.pth"));
            trainConfig.put("dataset_path", config.getStr("dataset_path", "/app/data/datasets/deeplabv3/VOCdevkit"));
            trainConfig.put("num_classes", config.getInt("num_classes", 21));
            trainConfig.put("backbone", config.getStr("backbone", "mobilenet"));
            trainConfig.put("input_shape", config.getInt("input_shape", 512));
            trainConfig.put("freeze_train", config.getBool("freeze_train", true));
            trainConfig.put("freeze_epoch", config.getInt("freeze_epoch", 50));
            trainConfig.put("freeze_batch_size", config.getInt("freeze_batch_size", 8));
            trainConfig.put("epochs", config.getInt("epochs", 100));
            trainConfig.put("un_freeze_batch_size", config.getInt("un_freeze_batch_size", 4));
            trainConfig.put("cuda", config.getBool("cuda", true));
            trainConfig.put("distributed", config.getBool("distributed", false));
            trainConfig.put("fp16", config.getBool("fp16", false));
            trainConfig.put("save_dir", config.getStr("save_dir", "/app/data/save_dir"));
            trainConfig.put("save_period", config.getInt("save_period", 5));
            trainConfig.put("eval_flag", config.getBool("eval_flag", true));
            trainConfig.put("eval_period", config.getInt("eval_period", 5));
            trainConfig.put("focal_loss", config.getBool("focal_loss", false));
            trainConfig.put("num_workers", config.getInt("num_workers", 4));
            trainConfig.put("optimizer_type", config.getStr("optimizer_type", "sgd"));
            trainConfig.put("momentum", config.getDouble("momentum", 0.9));
            trainConfig.put("weight_decay", config.getDouble("weight_decay", 1e-4));
            trainConfig.put("init_lr", config.getDouble("init_lr", 7e-3));
            trainConfig.put("min_lr", config.getDouble("min_lr", 7e-5));
        }

        return trainer.startTraining(taskId, trackId, trainConfig);
    }

    /**
     * 启动 TrackNetV3 训练任务
     */
    private String startTrackNetV3Training(TrackNetV3Adapter trainer, String taskId, String trackId, String userId, JSONObject config) {
        // 从配置文件获取默认配置
        ai.config.ContextLoader.loadContext();
        ai.config.pojo.DiscriminativeModelsConfig discriminativeConfig = ai.config.ContextLoader.configuration
                .getModelPlatformConfig()
                .getDiscriminativeModelsConfig();

        Map<String, Object> defaultConfig = null;
        if (discriminativeConfig != null && discriminativeConfig.getTracknetv3() != null) {
            defaultConfig = discriminativeConfig.getTracknetv3().getDefaultConfig();
        }

        // 构建训练配置
        JSONObject trainConfig = new JSONObject();
        trainConfig.put("the_train_type", "train");
        trainConfig.put("task_id", taskId);
        trainConfig.put("track_id", trackId);
        trainConfig.put("model_name", config.getStr("model_name", "tracknetv3")); // 传递模型名称
        // 如果用户没有指定 train_log_file，不设置默认值，让 TrackNetV3Adapter 根据 taskId 自动生成
        String userTrainLogFile = config.getStr("train_log_file");
        if (userTrainLogFile != null && !userTrainLogFile.isEmpty()) {
            trainConfig.put("train_log_file", userTrainLogFile);
        }

        // 添加用户ID到配置中
        if (userId != null && !userId.isEmpty()) {
            trainConfig.put("user_id", userId);
        }

        // 添加模板ID到配置中
        Integer templateId = config.getInt("template_id");
        if (templateId != null) {
            trainConfig.put("template_id", templateId);
        }

        // 使用配置文件的默认值或用户传入的值
        if (defaultConfig != null && !defaultConfig.isEmpty()) {
            trainConfig.put("model_name", config.getStr("model_name", (String) defaultConfig.get("model_name")));
            trainConfig.put("num_frame", config.getInt("num_frame", (Integer) defaultConfig.get("num_frame")));
            trainConfig.put("input_type", config.getStr("input_type", (String) defaultConfig.get("input_type")));
            trainConfig.put("epochs", config.getInt("epochs", (Integer) defaultConfig.get("epochs")));
            trainConfig.put("batch_size", config.getInt("batch_size", (Integer) defaultConfig.get("batch_size")));
            trainConfig.put("learning_rate", config.getDouble("learning_rate", (Double) defaultConfig.get("learning_rate")));
            trainConfig.put("tolerance", config.getDouble("tolerance", (Double) defaultConfig.get("tolerance")));
            trainConfig.put("save_dir", config.getStr("save_dir", (String) defaultConfig.get("save_dir")));
            trainConfig.put("use_gpu", config.getBool("use_gpu", (Boolean) defaultConfig.get("use_gpu")));
            trainConfig.put("data_dir", config.getStr("data_dir", (String) defaultConfig.get("data_dir")));
        } else {
            // 使用用户传入的值，如果没有则不设置（让适配器处理）
            if (config.containsKey("model_name")) {
                trainConfig.put("model_name", config.getStr("model_name"));
            }
            if (config.containsKey("num_frame")) {
                trainConfig.put("num_frame", config.getInt("num_frame"));
            }
            if (config.containsKey("input_type")) {
                trainConfig.put("input_type", config.getStr("input_type"));
            }
            if (config.containsKey("epochs")) {
                trainConfig.put("epochs", config.getInt("epochs"));
            }
            if (config.containsKey("batch_size")) {
                trainConfig.put("batch_size", config.getInt("batch_size"));
            }
            if (config.containsKey("learning_rate")) {
                trainConfig.put("learning_rate", config.getDouble("learning_rate"));
            }
            if (config.containsKey("tolerance")) {
                trainConfig.put("tolerance", config.getDouble("tolerance"));
            }
            if (config.containsKey("save_dir")) {
                trainConfig.put("save_dir", config.getStr("save_dir"));
            }
            if (config.containsKey("use_gpu")) {
                trainConfig.put("use_gpu", config.getBool("use_gpu"));
            }
            if (config.containsKey("data_dir")) {
                trainConfig.put("data_dir", config.getStr("data_dir"));
            }
        }

        return trainer.startTraining(taskId, trackId, trainConfig);
    }

    /**
     * 暂停容器
     * POST ?taskId=xxx 或 ?containerId=xxx
     */
    private void handlePauseContainer(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            String containerId = getContainerIdFromRequest(req);
            if (containerId == null) {
                resp.setStatus(400);
                Map<String, String> error = new HashMap<>();
                error.put("error", "缺少 taskId 或 containerId 参数");
                responsePrint(resp, toJson(error));
                return;
            }

            // 目前所有模型都使用 Docker 容器，可以统一处理
            String result = yoloTrainer.pauseContainer(containerId);
            responsePrint(resp, result);

        } catch (Exception e) {
            log.error("暂停容器失败", e);
            resp.setStatus(500);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            responsePrint(resp, toJson(error));
        }
    }

    /**
     * 恢复容器
     */
    private void handleResumeContainer(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            String containerId = getContainerIdFromRequest(req);
            if (containerId == null) {
                resp.setStatus(400);
                Map<String, String> error = new HashMap<>();
                error.put("error", "缺少 taskId 或 containerId 参数");
                responsePrint(resp, toJson(error));
                return;
            }

            String result = yoloTrainer.resumeContainer(containerId);
            responsePrint(resp, result);

        } catch (Exception e) {
            log.error("恢复容器失败", e);
            resp.setStatus(500);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            responsePrint(resp, toJson(error));
        }
    }

    /**
     * 停止容器
     */
    private void handleStopContainer(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            String containerId = getContainerIdFromRequest(req);
            if (containerId == null) {
                resp.setStatus(400);
                Map<String, String> error = new HashMap<>();
                error.put("error", "缺少 taskId 或 containerId 参数");
                responsePrint(resp, toJson(error));
                return;
            }

            String result = yoloTrainer.stopContainer(containerId);
            responsePrint(resp, result);

        } catch (Exception e) {
            log.error("停止容器失败", e);
            resp.setStatus(500);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            responsePrint(resp, toJson(error));
        }
    }

    /**
     * 删除训练任务，不包训练容器
     */
    private void handleDeletedContainer(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            String taskId = req.getParameter("taskId");
            if (taskId == null) {
                resp.setStatus(400);
                Map<String, String> error = new HashMap<>();
                error.put("error", "缺少 taskId");
                responsePrint(resp, toJson(error));
                return;
            }
            String result = yoloTrainer.removeContainer(null,taskId);
            if (taskId != null) {
                taskContainerMap.remove(taskId);
                taskStreamMap.remove(taskId);
            }

            responsePrint(resp, result);

        } catch (Exception e) {
            log.error("删除容器失败", e);
            resp.setStatus(500);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            responsePrint(resp, toJson(error));
        }
    }

    /**
     * 删除容器
     */
    private void handleRemoveContainer(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            String containerId = getContainerIdFromRequest(req);
            if (containerId == null) {
                resp.setStatus(400);
                Map<String, String> error = new HashMap<>();
                error.put("error", "缺少 taskId 或 containerId 参数");
                responsePrint(resp, toJson(error));
                return;
            }

            String result = yoloTrainer.removeContainer(containerId);

            // 清理任务映射
            String taskId = req.getParameter("taskId");
            if (taskId != null) {
                taskContainerMap.remove(taskId);
                taskStreamMap.remove(taskId);
            }

            responsePrint(resp, result);

        } catch (Exception e) {
            log.error("删除容器失败", e);
            resp.setStatus(500);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            responsePrint(resp, toJson(error));
        }
    }

    /**
     * 查看容器状态
     */
    private void handleGetStatus(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            String containerId = getContainerIdFromRequest(req);
            if (containerId == null) {
                resp.setStatus(400);
                Map<String, String> error = new HashMap<>();
                error.put("error", "缺少 taskId 或 containerId 参数");
                responsePrint(resp, toJson(error));
                return;
            }

            String result = yoloTrainer.getContainerStatus(containerId);
            responsePrint(resp, result);

        } catch (Exception e) {
            log.error("查询容器状态失败", e);
            resp.setStatus(500);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            responsePrint(resp, toJson(error));
        }
    }

    /**
     * 查看容器日志
     */
    private void handleGetLogs(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            String containerId = getContainerIdFromRequest(req);
            if (containerId == null) {
                // 如果通过 taskId 无法找到 containerId，则直接使用 taskId 作为 containerId
                String taskId = req.getParameter("taskId");
                if (taskId == null || taskId.isEmpty()) {
                    resp.setStatus(400);
                    Map<String, String> error = new HashMap<>();
                    error.put("error", "缺少 taskId 或 containerId 参数");
                    responsePrint(resp, toJson(error));
                    return;
                }
                containerId = taskId;
            }

            String linesStr = req.getParameter("lines");
            int lines = linesStr != null ? Integer.parseInt(linesStr) : 100;

            String result = yoloTrainer.getContainerLogs(containerId, lines);
            responsePrint(resp, result);

        } catch (Exception e) {
            log.error("查询容器日志失败", e);
            resp.setStatus(500);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            responsePrint(resp, toJson(error));
        }
    }

    /**
     * 流式获取容器日志（SSE）
     */
    private void handleStreamLogs(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String containerId = getContainerIdFromRequest(req);
        if (containerId == null) {
            resp.setStatus(400);
            resp.setContentType("application/json;charset=utf-8");
            Map<String, String> error = new HashMap<>();
            error.put("error", "缺少 taskId 或 containerId 参数");
            responsePrint(resp, toJson(error));
            return;
        }

        // 设置 SSE 响应头
        resp.setContentType("text/event-stream");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-cache");
        resp.setHeader("Connection", "keep-alive");

        PrintWriter writer = resp.getWriter();

        try {
            ObservableList<String> logStream = yoloTrainer.getContainerLogsStream(containerId);

            logStream.getObservable().subscribe(
                    line -> {
                        try {
                            writer.write("data: " + line + "\n\n");
                            writer.flush();
                        } catch (Exception e) {
                            log.error("发送日志流失败", e);
                        }
                    },
                    error -> {
                        log.error("日志流错误", error);
                        writer.write("event: error\ndata: " + error.getMessage() + "\n\n");
                        writer.flush();
                    },
                    () -> {
                        writer.write("event: complete\ndata: 日志流结束\n\n");
                        writer.flush();
                    }
            );

        } catch (Exception e) {
            log.error("创建日志流失败", e);
            writer.write("event: error\ndata: " + e.getMessage() + "\n\n");
            writer.flush();
        }
    }

    /**
     * 执行评估任务
     */
    private void handleEvaluate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            String jsonBody = requestToJson(req);
            JSONObject config = JSONUtil.parseObj(jsonBody);

            // 根据模型名称选择对应的训练器
            String modelName = config.getStr("model_name", "");
            Object trainer = trainerMap.get(modelName.toLowerCase());

            String result;
            if (trainer instanceof DeeplabAdapter) {
                result = ((DeeplabAdapter) trainer).evaluate(config);
            } else if (trainer instanceof TrackNetV3Adapter) {
                result = ((TrackNetV3Adapter) trainer).evaluate(config);
            } else if (trainer instanceof YoloTrainerAdapter || trainer == null) {
                // 默认使用 yoloTrainer（向后兼容）
                result = yoloTrainer.evaluate(config);
            } else {
                resp.setStatus(501);
                Map<String, String> error = new HashMap<>();
                error.put("error", "模型 " + modelName + " 的评估功能尚未实现");
                responsePrint(resp, toJson(error));
                return;
            }

            responsePrint(resp, result);

        } catch (Exception e) {
            log.error("执行评估失败", e);
            resp.setStatus(500);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            responsePrint(resp, toJson(error));
        }
    }

    //用于异步执行任务
    private ExecutorService asyncTaskExecutor = new ThreadPoolExecutor(
            2, // 核心线程数
            5, // 最大线程数
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(10), // 任务队列
            Executors.defaultThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy() // 任务满时的拒绝策略
    );

    /**
     * 执行预测任务
     */
    private void handlePredict(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            String jsonBody = requestToJson(req);
            JSONObject config = JSONUtil.parseObj(jsonBody);

            // 根据模型名称选择对应的训练器
            String modelName = config.getStr("model_name", "");
            Object trainer = trainerMap.get(modelName.toLowerCase());

            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
            String timestamp = sdf.format(new Date());
            String uuidPart = UUID.randomUUID().toString().substring(0, 8);
            String taskId = "task_" + timestamp + "_" + uuidPart;
            config.put("task_id", taskId);
            String trackId = config.getStr("track_id");
            if (trackId == null || trackId.isEmpty()) {
                trackId = YoloTrainerAdapter.generateTrackId();
                config.put("track_id", trackId);
            }

            asyncTaskExecutor.submit(() -> {
                try {
                    if (trainer instanceof DeeplabAdapter) {
                        ((DeeplabAdapter) trainer).predict(config);
                    } else if (trainer instanceof TrackNetV3Adapter) {
                        ((TrackNetV3Adapter) trainer).predict(config);
                    } else if (trainer instanceof YoloTrainerAdapter || trainer == null) {
                        // 默认使用 yoloTrainer（向后兼容）
                        yoloTrainer.predict(config);
                    } else {
                        resp.setStatus(501);
                        Map<String, String> error = new HashMap<>();
                        error.put("error", "模型 " + modelName + " 的预测功能尚未实现");
                        responsePrint(resp, toJson(error));
                        return;
                    }

                    log.info("异步预测完成：taskId={}, model={}", taskId, modelName);
                } catch (Exception e) {
                    log.error("异步预测失败：taskId={}", taskId, e);
                }
            });

            Map<String, Object> respMap = new HashMap<>();
            respMap.put("code", 200);
            respMap.put("msg", "预测任务已提交");
            respMap.put("taskId", taskId);
            responsePrint(resp, toJson(respMap));

        } catch (Exception e) {
            log.error("执行预测失败", e);
            resp.setStatus(500);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            responsePrint(resp, toJson(error));
        }
    }

    /**
     * 导出模型
     */
    private void handleExportModel(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            String jsonBody = requestToJson(req);
            JSONObject config = JSONUtil.parseObj(jsonBody);

            // 根据模型名称选择对应的训练器
            String modelName = config.getStr("model_name", "");
            Object trainer = trainerMap.get(modelName.toLowerCase());

            String result;
            if (trainer instanceof DeeplabAdapter) {
                result = ((DeeplabAdapter) trainer).exportModel(config);
            } else if (trainer instanceof TrackNetV3Adapter) {
                result = ((TrackNetV3Adapter) trainer).exportModel(config);
            } else if (trainer instanceof YoloTrainerAdapter || trainer == null) {
                // 默认使用 yoloTrainer（向后兼容）
                result = yoloTrainer.exportModel(config);
            } else {
                resp.setStatus(501);
                Map<String, String> error = new HashMap<>();
                error.put("error", "模型 " + modelName + " 的导出功能尚未实现");
                responsePrint(resp, toJson(error));
                return;
            }

            responsePrint(resp, result);

        } catch (Exception e) {
            log.error("导出模型失败", e);
            resp.setStatus(500);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            responsePrint(resp, toJson(error));
        }
    }

    /**
     * 上传文件到容器
     */
    private void handleUploadFile(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            String jsonBody = requestToJson(req);
            JSONObject params = JSONUtil.parseObj(jsonBody);

            String containerId = params.getStr("containerId");
            if (containerId == null) {
                String taskId = params.getStr("taskId");
                if (taskId != null) {
                    containerId = taskContainerMap.get(taskId);
                }
            }

            if (containerId == null) {
                resp.setStatus(400);
                Map<String, String> error = new HashMap<>();
                error.put("error", "缺少 taskId 或 containerId 参数");
                responsePrint(resp, toJson(error));
                return;
            }

            String localPath = params.getStr("localPath");
            String containerPath = params.getStr("containerPath");

            if (localPath == null || containerPath == null) {
                resp.setStatus(400);
                Map<String, String> error = new HashMap<>();
                error.put("error", "缺少 localPath 或 containerPath 参数");
                responsePrint(resp, toJson(error));
                return;
            }

            String result = yoloTrainer.uploadToContainer(containerId, localPath, containerPath);
            responsePrint(resp, result);

        } catch (Exception e) {
            log.error("上传文件失败", e);
            resp.setStatus(500);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            responsePrint(resp, toJson(error));
        }
    }

    /**
     * 从容器下载文件
     */
    private void handleDownloadFile(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            String jsonBody = requestToJson(req);
            JSONObject params = JSONUtil.parseObj(jsonBody);

            String containerId = params.getStr("containerId");
            if (containerId == null) {
                String taskId = params.getStr("taskId");
                if (taskId != null) {
                    containerId = taskContainerMap.get(taskId);
                }
            }

            if (containerId == null) {
                resp.setStatus(400);
                Map<String, String> error = new HashMap<>();
                error.put("error", "缺少 taskId 或 containerId 参数");
                responsePrint(resp, toJson(error));
                return;
            }

            String containerPath = params.getStr("containerPath");
            String localPath = params.getStr("localPath");

            if (containerPath == null || localPath == null) {
                resp.setStatus(400);
                Map<String, String> error = new HashMap<>();
                error.put("error", "缺少 containerPath 或 localPath 参数");
                responsePrint(resp, toJson(error));
                return;
            }

            String result = yoloTrainer.downloadFromContainer(containerId, containerPath, localPath);
            responsePrint(resp, result);

        } catch (Exception e) {
            log.error("下载文件失败", e);
            resp.setStatus(500);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            responsePrint(resp, toJson(error));
        }
    }

    /**
     * 提交容器为镜像
     */
    private void handleCommitImage(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            String jsonBody = requestToJson(req);
            JSONObject params = JSONUtil.parseObj(jsonBody);

            String containerId = params.getStr("containerId");
            if (containerId == null) {
                String taskId = params.getStr("taskId");
                if (taskId != null) {
                    containerId = taskContainerMap.get(taskId);
                }
            }

            if (containerId == null) {
                resp.setStatus(400);
                Map<String, String> error = new HashMap<>();
                error.put("error", "缺少 taskId 或 containerId 参数");
                responsePrint(resp, toJson(error));
                return;
            }

            String imageName = params.getStr("imageName");
            String imageTag = params.getStr("imageTag", "latest");

            if (imageName == null) {
                resp.setStatus(400);
                Map<String, String> error = new HashMap<>();
                error.put("error", "缺少 imageName 参数");
                responsePrint(resp, toJson(error));
                return;
            }

            String result = yoloTrainer.commitContainerAsImage(containerId, imageName, imageTag);
            responsePrint(resp, result);

        } catch (Exception e) {
            log.error("提交镜像失败", e);
            resp.setStatus(500);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            responsePrint(resp, toJson(error));
        }
    }

    /**
     * 列出所有训练容器
     */
    private void handleListContainers(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            String result = yoloTrainer.listTrainingContainers();
            responsePrint(resp, result);

        } catch (Exception e) {
            log.error("列出容器失败", e);
            resp.setStatus(500);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            responsePrint(resp, toJson(error));
        }
    }

    /**
     * 列出所有支持的模型
     * GET /ai/training/models
     */
    private void handleListModels(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("available_models", trainerMap.keySet());
            result.put("total", trainerMap.size());

            Map<String, String> modelInfo = new HashMap<>();
            modelInfo.put("yolov8", "目标检测 - YOLOv8");
            modelInfo.put("yolov11", "目标检测 - YOLOv11");
            modelInfo.put("deeplab", "语义分割 - DeepLabV3");
            modelInfo.put("deeplabv3", "语义分割 - DeepLabV3");
            modelInfo.put("tracknetv3", "轨迹跟踪 - TrackNetV3");
            modelInfo.put("tracknet", "轨迹跟踪 - TrackNetV3");
            // TODO: 添加其他模型信息

            result.put("model_descriptions", modelInfo);

            responsePrint(resp, result.toString());

        } catch (Exception e) {
            log.error("列出模型失败", e);
            resp.setStatus(500);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            responsePrint(resp, toJson(error));
        }
    }

    /**
     * 从请求中获取容器ID
     */
    private String getContainerIdFromRequest(HttpServletRequest req) {
        String containerId = req.getParameter("containerId");
        if (containerId == null || containerId.isEmpty()) {
            String taskId = req.getParameter("taskId");
            if (taskId != null && !taskId.isEmpty()) {
                containerId = taskContainerMap.get(taskId);
            }
        }
        return containerId;
    }


    /**
     * 批量获取训练任务状态
     * 支持通过逗号拼接的容器ID或训练任务ID查询
     *
     * @param containerIds 逗号拼接的容器ID，例如: "id1,id2,id3"
     * @param taskIds 逗号拼接的训练任务ID，例如: "id1,id2,id3"
     * @return 包含训练任务状态信息的Map列表
     */
    public static List<Map<String, Object>> batchGetTaskStatus(String containerIds, String taskIds) {
        List<Map<String, Object>> resultList = new ArrayList<>();

        // 参数检查
        if ((containerIds == null || containerIds.trim().isEmpty()) &&
                (taskIds == null || taskIds.trim().isEmpty())) {
            return resultList;
        }

        try {
            TrainingTaskRepository repository = yoloTrainer.getRepository();

            // 获取任务列表
            List<Map<String, Object>> tasks = new ArrayList<>();

            // 根据任务ID查询
            if (taskIds != null && !taskIds.trim().isEmpty()) {
                List<String> taskIdList = Arrays.stream(taskIds.split(","))
                        .map(String::trim)
                        .filter(id -> !id.isEmpty())
                        .collect(Collectors.toList());
                if (!taskIdList.isEmpty()) {
                    tasks.addAll(repository.getTasksByTaskIds(taskIdList));
                }
            }

            // 根据容器ID查询
            if (containerIds != null && !containerIds.trim().isEmpty()) {
                List<String> containerIdList = Arrays.stream(containerIds.split(","))
                        .map(String::trim)
                        .filter(id -> !id.isEmpty())
                        .collect(Collectors.toList());
                if (!containerIdList.isEmpty()) {
                    // 先尝试按容器ID查询
                    List<Map<String, Object>> tasksByContainerId = repository.getTasksByContainerIds(containerIdList);
                    tasks.addAll(tasksByContainerId);

                    // 如果按容器ID查不到，尝试按容器名称查询
                    List<Map<String, Object>> tasksByContainerName = repository.getTasksByContainerNames(containerIdList);
                    tasks.addAll(tasksByContainerName);
                }
            }

            if (tasks.isEmpty()) {
                return resultList;
            }

            // 去重（基于task_id）
            Map<String, Map<String, Object>> uniqueTasks = new LinkedHashMap<>();
            for (Map<String, Object> task : tasks) {
                String taskId = (String) task.get("task_id");
                if (taskId != null && !uniqueTasks.containsKey(taskId)) {
                    uniqueTasks.put(taskId, task);
                }
            }

            // 收集所有任务ID，用于后续重新查询
            List<String> allTaskIds = new ArrayList<>(uniqueTasks.keySet());

            // 先批量从服务器获取容器状态
            Map<String, String> containerStatusMap = new HashMap<>();
            Map<String, String> taskStatusMap = new HashMap<>();

            for (Map<String, Object> task : uniqueTasks.values()) {
                String taskId = (String) task.get("task_id");
                String containerId = (String) task.get("container_id");
                String containerName = (String) task.get("container_name");

                // 从服务器获取容器状态
                if (yoloTrainer != null) {
                    try {
                        // 优先使用容器ID，如果没有则使用容器名称
                        String containerIdentifier = containerId;
                        if (containerIdentifier == null || containerIdentifier.isEmpty()) {
                            containerIdentifier = containerName;
                        }

                        if (containerIdentifier != null && !containerIdentifier.isEmpty()) {
                            String statusResult = yoloTrainer.getContainerStatus(containerIdentifier);
                            JSONObject statusJson = JSONUtil.parseObj(statusResult);

                            // 检查是否获取成功
                            String resultStatus = statusJson.getStr("status");
                            String exitCode = "";
                            String isStatus ="";
                            // 尝试从output字段提取exitCode
                            String output = statusJson.getStr("output");
                            if (output != null && output.contains(";")) {
                                String[] parts = output.split(";");
                                if (parts.length > 1) {
                                    exitCode = parts[1].trim();
                                    isStatus = parts[0].trim();
                                }
                            }
                            // 如果exitCode大于0，则将containerStatus设置为failed
                            if (exitCode != null && !exitCode.isEmpty()) {
                                try {
                                    int code = Integer.parseInt(exitCode);
                                    if (code > 0) {
                                        statusJson.put("containerStatus", "failed");
                                    }else {
                                        statusJson.put("containerStatus", isStatus);
                                    }
                                } catch (NumberFormatException e) {
                                    log.warn("Invalid exitCode format: {}", exitCode);
                                }
                            }
                            if (!"error".equals(resultStatus)) {
                                // 从接口返回的JSON中获取containerStatus字段作为任务状态
                                String containerStatus = statusJson.getStr("containerStatus");

                                // 如果containerStatus为空，尝试从output字段获取（去掉换行符）
                                if (containerStatus == null || containerStatus.isEmpty()) {
                                    if (output != null && !output.isEmpty()) {
                                        containerStatus = output.trim().replace("\n", "");
                                    }
                                }

                                // 如果还是为空，尝试从status字段获取
                                if (containerStatus == null || containerStatus.isEmpty()) {
                                    containerStatus = resultStatus;
                                }

                                // 直接使用containerStatus作为任务状态
                                if (containerStatus != null && !containerStatus.isEmpty()) {
                                    // 保存容器状态和任务状态（使用containerStatus作为任务状态）
                                    containerStatusMap.put(taskId, containerStatus);
                                    taskStatusMap.put(taskId, containerStatus);
                                }
                            } else {
                                // 获取状态失败，可能是容器不存在
                                String errorMsg = statusJson.getStr("message", "获取容器状态失败");
                                log.warn("获取容器状态失败: taskId={}, containerId={}, error={}", taskId, containerId, errorMsg);
                                containerStatusMap.put(taskId, "unknown");
                            }
                        } else {
                            containerStatusMap.put(taskId, "unknown");
                        }
                    } catch (Exception e) {
                        log.error("获取容器状态失败: taskId={}, containerId={}", taskId, containerId, e);
                        containerStatusMap.put(taskId, "unknown");
                    }
                } else {
                    containerStatusMap.put(taskId, "unknown");
                }
            }

            // 批量更新数据库中的任务状态
            for (Map.Entry<String, String> entry : taskStatusMap.entrySet()) {
                String taskId = entry.getKey();
                String taskStatus = entry.getValue();
                repository.updateTaskStatus(taskId, taskStatus, null);
            }

            // 重新查询数据库获取最新数据
            List<Map<String, Object>> latestTasks = repository.getTasksByTaskIds(allTaskIds);

            // 构建返回结果，使用从服务器获取的实时状态
            for (Map<String, Object> task : latestTasks) {
                String taskId = (String) task.get("task_id");
                Map<String, Object> taskResult = new HashMap<>(task);

                // 使用从服务器获取的实时容器状态
                String containerStatus = containerStatusMap.get(taskId);
                if (containerStatus != null) {
                    taskResult.put("containerStatus", containerStatus);
                    // 同时更新status字段为containerStatus的值
                    taskResult.put("status", containerStatus);
                }

                // 使用从服务器获取的实时任务状态（覆盖数据库中的旧状态）
                String realTimeTaskStatus = taskStatusMap.get(taskId);
                if (realTimeTaskStatus != null) {
                    taskResult.put("status", realTimeTaskStatus);
                }

                resultList.add(taskResult);
            }

            return resultList;

        } catch (Exception e) {
            log.error("批量获取训练任务状态失败", e);
        }
        return resultList;
    }

    /**
     * 获取训练任务对应的容器资源利用率（CPU、内存、GPU、显存）
     * GET /ai/training/resources?taskId=xxx
     */
    private void handleGetResourceUsage(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            String taskId = req.getParameter("taskId");
            if (taskId == null || taskId.isEmpty()) {
                resp.setStatus(400);
                Map<String, String> error = new HashMap<>();
                error.put("error", "缺少 taskId 参数");
                responsePrint(resp, toJson(error));
                return;
            }

            // 获取容器ID或容器名称
            String containerId = getContainerIdFromTaskId(taskId);
            if (containerId == null || containerId.isEmpty()) {
                resp.setStatus(404);
                Map<String, String> error = new HashMap<>();
                error.put("error", "未找到任务对应的容器: " + taskId);
                responsePrint(resp, toJson(error));
                return;
            }

            // 获取资源使用率
            String result = getContainerResourceUsage(containerId, taskId);
            responsePrint(resp, result);

        } catch (Exception e) {
            log.error("获取容器资源使用率失败", e);
            resp.setStatus(500);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            responsePrint(resp, toJson(error));
        }
    }

    /**
     * 根据任务ID获取容器ID或容器名称
     */
    private String getContainerIdFromTaskId(String taskId) {
        // 1. 先从 taskContainerMap 中查找
        String containerName = taskContainerMap.get(taskId);
        if (containerName != null && !containerName.isEmpty()) {
            return containerName;
        }

        // 2. 从数据库查询
        try {
            TrainingTaskRepository repository = yoloTrainer.getRepository();
            Map<String, Object> taskDetail = repository.getTaskDetailByTaskId(taskId);
            if (taskDetail != null) {
                String containerId = (String) taskDetail.get("container_id");
                String containerNameFromDb = (String) taskDetail.get("container_name");

                // 优先使用 container_id，如果没有则使用 container_name
                if (containerId != null && !containerId.isEmpty()) {
                    return containerId;
                }
                if (containerNameFromDb != null && !containerNameFromDb.isEmpty()) {
                    return containerNameFromDb;
                }
            }
        } catch (Exception e) {
            log.warn("从数据库查询容器信息失败: taskId={}, error={}", taskId, e.getMessage());
        }

        return null;
    }

    /**
     * 获取容器资源利用率（CPU、内存、GPU、显存）
     */
    private String getContainerResourceUsage(String containerId, String taskId) {
        try {
            JSONObject result = new JSONObject();
            result.put("taskId", taskId);
            result.put("containerId", containerId);
            result.put("timestamp", System.currentTimeMillis());

            // 获取可用的训练器来执行命令（复用SSH连接池）
            // 优先使用 yoloTrainer，如果为 null 则使用 deeplabAdapter
            ai.finetune.DockerTrainerAbstract trainer = yoloTrainer != null ? yoloTrainer : deeplabAdapter;
            if (trainer == null) {
                throw new RuntimeException("训练器未初始化");
            }

            // 1. 获取 CPU 和内存使用率
            JSONObject cpuMemory = getContainerCpuMemoryUsage(containerId, trainer);
            result.put("cpu", cpuMemory.getObj("cpu"));
            result.put("memory", cpuMemory.getObj("memory"));

            // 2. 获取 GPU 和显存使用率
            JSONObject gpuUsage = getContainerGpuUsage(containerId, trainer);
            result.put("gpu", gpuUsage.getObj("gpu"));
            result.put("gpuMemory", gpuUsage.getObj("gpuMemory"));

            result.put("status", "success");
            return result.toString();

        } catch (Exception e) {
            log.error("获取容器资源利用率失败: containerId={}, taskId={}", containerId, taskId, e);
            JSONObject error = new JSONObject();
            error.put("status", "error");
            error.put("message", "获取容器资源利用率失败");
            error.put("error", e.getMessage());
            error.put("containerId", containerId);
            error.put("taskId", taskId);
            return error.toString();
        }
    }

    /**
     * 获取容器的 CPU 和内存使用率
     * 使用 docker stats 命令
     */
    private JSONObject getContainerCpuMemoryUsage(String containerId, ai.finetune.DockerTrainerAbstract trainer) {
        JSONObject result = new JSONObject();

        try {
            // 使用 docker stats --no-stream 获取一次性统计数据
            String command = "docker stats --no-stream --format " +
                    "\"{{.Container}},{{.CPUPerc}},{{.MemUsage}},{{.MemPerc}}\" " + containerId;

            String commandResult = trainer.executeRemoteCommand(command);
            JSONObject resultJson = JSONUtil.parseObj(commandResult);

            if ("success".equals(resultJson.getStr("status"))) {
                String output = resultJson.getStr("output", "").trim();
                if (!output.isEmpty()) {
                    // 解析输出：containerID,15.23%,1.5GiB / 8GiB,18.75%
                    String[] parts = output.trim().split(",");

                    if (parts.length >= 4) {
                        // CPU 使用率
                        String cpuPerc = parts[1].replace("%", "").trim();
                        JSONObject cpu = new JSONObject();
                        cpu.put("percent", Double.parseDouble(cpuPerc));
                        cpu.put("usage", parts[1].trim());
                        result.put("cpu", cpu);

                        // 内存使用率
                        String memUsage = parts[2].trim(); // "1.5GiB / 8GiB"
                        String memPerc = parts[3].replace("%", "").trim();

                        JSONObject memory = new JSONObject();
                        memory.put("percent", Double.parseDouble(memPerc));
                        memory.put("usage", memUsage);

                        // 解析具体的内存使用量
                        if (memUsage.contains("/")) {
                            String[] memParts = memUsage.split("/");
                            memory.put("used", memParts[0].trim());
                            memory.put("total", memParts[1].trim());
                        }

                        result.put("memory", memory);
                    }
                }
            }

        } catch (Exception e) {
            log.error("获取容器 CPU/内存使用率失败: containerId={}", containerId, e);
            result.put("cpu", createErrorObject("无法获取"));
            result.put("memory", createErrorObject("无法获取"));
        }

        return result;
    }

    /**
     * 获取容器的 GPU 和显存使用率
     * 通过在容器内执行 nvidia-smi 命令
     */
    private JSONObject getContainerGpuUsage(String containerId, ai.finetune.DockerTrainerAbstract trainer) {
        JSONObject result = new JSONObject();

        try {
            // 在容器内执行 nvidia-smi 命令获取 GPU 信息
            String command = "docker exec " + containerId +
                    " nvidia-smi --query-gpu=utilization.gpu,memory.used,memory.total," +
                    "temperature.gpu,power.draw --format=csv,noheader,nounits";

            String commandResult = trainer.executeRemoteCommand(command);
            JSONObject resultJson = JSONUtil.parseObj(commandResult);

            if ("success".equals(resultJson.getStr("status"))) {
                String output = resultJson.getStr("output", "").trim();
                if (!output.isEmpty()) {
                    // 解析输出：15, 1024, 8192, 65, 120.5
                    String[] parts = output.trim().split(",");

                    if (parts.length >= 3) {
                        // GPU 利用率
                        double gpuUtil = Double.parseDouble(parts[0].trim());
                        JSONObject gpu = new JSONObject();
                        gpu.put("percent", gpuUtil);
                        gpu.put("usage", gpuUtil + "%");

                        if (parts.length >= 4) {
                            gpu.put("temperature", Double.parseDouble(parts[3].trim()));
                        }
                        if (parts.length >= 5) {
                            gpu.put("powerDraw", Double.parseDouble(parts[4].trim()));
                        }

                        result.put("gpu", gpu);

                        // 显存使用率
                        double memUsed = Double.parseDouble(parts[1].trim()); // MB
                        double memTotal = Double.parseDouble(parts[2].trim()); // MB
                        double memPercent = (memUsed / memTotal) * 100;

                        JSONObject gpuMemory = new JSONObject();
                        gpuMemory.put("percent", Math.round(memPercent * 100.0) / 100.0);
                        gpuMemory.put("used", Math.round(memUsed) + " MiB");
                        gpuMemory.put("total", Math.round(memTotal) + " MiB");
                        gpuMemory.put("usedMB", Math.round(memUsed));
                        gpuMemory.put("totalMB", Math.round(memTotal));
                        gpuMemory.put("usage", String.format("%.1f MiB / %.1f MiB", memUsed, memTotal));

                        result.put("gpuMemory", gpuMemory);
                    }
                } else {
                    // 如果没有 GPU 或无法获取
                    result.put("gpu", createErrorObject("无 GPU 或无法访问"));
                    result.put("gpuMemory", createErrorObject("无 GPU 或无法访问"));
                }
            } else {
                // 命令执行失败，可能没有 GPU
                result.put("gpu", createErrorObject("无 GPU 或无法访问"));
                result.put("gpuMemory", createErrorObject("无 GPU 或无法访问"));
            }

        } catch (Exception e) {
            log.warn("获取容器 GPU 使用率失败（可能无 GPU）: containerId={}", containerId, e);
            result.put("gpu", createErrorObject("无 GPU 或无法访问"));
            result.put("gpuMemory", createErrorObject("无 GPU 或无法访问"));
        }

        return result;
    }

    /**
     * 创建错误对象
     */
    private JSONObject createErrorObject(String message) {
        JSONObject error = new JSONObject();
        error.put("error", message);
        error.put("percent", 0);
        return error;
    }

}
