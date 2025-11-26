package ai.servlet.api;

import ai.common.utils.ObservableList;
import ai.config.ContextLoader;
import ai.config.pojo.DiscriminativeModelsConfig;
import ai.finetune.YoloTrainerAdapter;
import ai.finetune.DeeplabAdapter;
import ai.finetune.repository.TrainingTaskRepository;
import ai.servlet.BaseServlet;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
                handleEvaluate(req, resp);
                break;
            case "predict":
                handlePredict(req, resp);
                break;
            case "export":
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
            default:
                resp.setStatus(404);
                Map<String, String> error = new HashMap<>();
                error.put("error", "接口不存在: " + method);
                responsePrint(resp, toJson(error));
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
            Map<String, Object> data = new HashMap<>();

            // 添加模板信息（这里从数据库查询模板表）
            Map<String, Object> template = new HashMap<>();
            String templateId = (String) taskDetail.get("template_id");
            if (templateId != null && !templateId.isEmpty()) {
                Map<String, Object> templateInfo = repository.getTemplateInfoById(templateId);
                if (templateInfo != null) {
                    template.put("templateName", templateInfo.get("template_name"));
                    template.put("templateDesc", templateInfo.get("description"));

                    // 查询模板字段
                    List<Map<String, Object>> templateFields = repository.getTemplateFieldsByTemplateId(templateId);
                    List<Map<String, Object>> fields = new ArrayList<>();
                    for (Map<String, Object> fieldInfo : templateFields) {
                        Map<String, Object> field = new HashMap<>();
                        field.put("fieldName", fieldInfo.get("field_id"));
                        field.put("fieldDesc", fieldInfo.get("description"));
                        field.put("fieldType", fieldInfo.get("display_type"));
                        field.put("isRequired", fieldInfo.get("required"));
                        fields.add(field);
                    }
                    template.put("fields", fields);
                } else {
                    // 如果未找到模板信息，使用默认值
                    template.put("templateName", "自定义模版名称");
                    template.put("templateDesc", "模版描述");
                    template.put("fields", new ArrayList<>());
                }
            } else {
                // 如果没有模板ID，使用默认值
                template.put("templateName", "自定义模版名称");
                template.put("templateDesc", "模版描述");
                template.put("fields", new ArrayList<>());
            }
            data.put("template", template);

            // 添加任务信息
            data.put("task_id", taskDetail.get("task_id"));
            data.put("status", taskDetail.get("status"));
            data.put("progress", taskDetail.get("progress"));
            data.put("current_epoch", taskDetail.get("current_epoch"));
            data.put("created_at", taskDetail.get("created_at"));
            data.put("start_time", taskDetail.get("start_time"));
            data.put("end_time", taskDetail.get("end_time"));
            data.put("train_dir", taskDetail.get("train_dir"));
            data.put("dataset_path", taskDetail.get("dataset_path"));
            data.put("model_path", taskDetail.get("model_path"));
            data.put("epochs", taskDetail.get("epochs"));
            data.put("batch_size", taskDetail.get("batch_size"));
            data.put("image_size", taskDetail.get("image_size"));
            data.put("use_gpu", convertToBoolean(taskDetail.get("use_gpu")));

            // 计算训练时长
            Object startTimeObj = taskDetail.get("start_time");
            Object endTimeObj = taskDetail.get("end_time");
            String trainingDuration = calculateTrainingDuration(startTimeObj, endTimeObj);
            data.put("training_duration", trainingDuration);

            response.put("code", "200");
            response.put("data", data);
            response.put("validate_time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")));

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

            // 检查模型是否支持
            Object trainer = trainerMap.get(modelName.toLowerCase());
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

            // 根据模型类型调用对应的训练器
            String result;
            if (trainer instanceof YoloTrainerAdapter) {
                result = startYoloTraining((YoloTrainerAdapter) trainer, taskId, trackId, userId, config);
            } else if (trainer instanceof DeeplabAdapter) {
                result = startDeeplabTraining((DeeplabAdapter) trainer, taskId, trackId, userId, config);
            } else {
                // TODO: 支持其他模型类型
                resp.setStatus(501);
                Map<String, String> error = new HashMap<>();
                error.put("error", "模型 " + modelName + " 的训练功能尚未实现");
                responsePrint(resp, toJson(error));
                return;
            }

            // 保存任务到容器的映射并构建返回结果
            if (YoloTrainerAdapter.isSuccess(result)) {
                JSONObject resultJson = JSONUtil.parseObj(result);
                String containerName = resultJson.getStr("containerName");
                String containerId = resultJson.getStr("containerId");

                if (containerName != null) {
                    taskContainerMap.put(taskId, containerName);
                }

                // 构建标准化的返回结果（保留所有原始字段）
                JSONObject response = new JSONObject();
                response.put("status", "success");
                response.put("msg", "训练任务已启动");
                response.put("task_id", taskId);
                response.put("track_id", trackId);
                response.put("model_name", config.getStr("model_name", "yolov8"));

                // 添加用户ID（总是返回，即使为空）
                response.put("user_id", userId != null ? userId : "");

                // 添加容器信息
                if (containerName != null) {
                    response.put("containerName", containerName);
                }
                if (containerId != null) {
                    response.put("containerId", containerId);
                }

                // 添加时间戳（当前时间，格式：yyyy-MM-dd HH:mm:ss）
                String timestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                response.put("timestamp", timestamp);

                // 提取训练配置信息（根据模型类型使用不同的参数名）
                JSONObject trainConfig = new JSONObject();
                if (modelName.contains("deeplab")) {
                    // Deeplab 使用 dataset_path
                    trainConfig.put("dataset_path", config.getStr("dataset_path", ""));
                    trainConfig.put("model_path", config.getStr("model_path", ""));
                    trainConfig.put("epochs", config.getInt("epochs", 0));
                    trainConfig.put("freeze_batch_size", config.getInt("freeze_batch_size", 0));
                    trainConfig.put("input_shape", config.getInt("input_shape", 512));
                } else {
                    // YOLO 使用 data
                    trainConfig.put("dataset_path", config.getStr("data", ""));
                    trainConfig.put("model_path", config.getStr("model_path", ""));
                    trainConfig.put("epochs", config.getInt("epochs", 0));
                    trainConfig.put("batch", config.getInt("batch", 0));
                    trainConfig.put("imgsz", config.getInt("imgsz", 640));
                }

                // 判断是否使用GPU
                String device = config.getStr("device", "cpu");
                boolean useGpu = !device.equalsIgnoreCase("cpu");
                trainConfig.put("use_gpu", useGpu);

                response.put("train_config", trainConfig);

                // 添加创建时间（ISO 8601格式）
                String createdAt = java.time.ZonedDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ISO_INSTANT);
                response.put("created_at", createdAt);

                responsePrint(resp, response.toString());
            } else {
                // 训练启动失败，返回错误信息
                JSONObject errorResponse = new JSONObject();
                errorResponse.put("status", "ERROR");

                // 尝试解析原始错误信息
                try {
                    JSONObject resultJson = JSONUtil.parseObj(result);
                    errorResponse.put("msg", resultJson.getStr("message", "训练任务启动失败"));
                    if (resultJson.containsKey("error")) {
                        errorResponse.put("error", resultJson.getStr("error"));
                    }
                } catch (Exception e) {
                    errorResponse.put("msg", "训练任务启动失败");
                    errorResponse.put("error", result);
                }

                resp.setStatus(500);
                responsePrint(resp, errorResponse.toString());
            }

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
        trainConfig.put("train_log_file", config.getStr("train_log_file", "/app/data/train.log"));

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
        trainConfig.put("train_log_file", config.getStr("train_log_file", "/app/data/train_" + taskId + ".log"));

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
                resp.setStatus(400);
                Map<String, String> error = new HashMap<>();
                error.put("error", "缺少 taskId 或 containerId 参数");
                responsePrint(resp, toJson(error));
                return;
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

            String result;
            if (trainer instanceof DeeplabAdapter) {
                result = ((DeeplabAdapter) trainer).predict(config);
            } else if (trainer instanceof YoloTrainerAdapter || trainer == null) {
                // 默认使用 yoloTrainer（向后兼容）
                result = yoloTrainer.predict(config);
            } else {
                resp.setStatus(501);
                Map<String, String> error = new HashMap<>();
                error.put("error", "模型 " + modelName + " 的预测功能尚未实现");
                responsePrint(resp, toJson(error));
                return;
            }

            responsePrint(resp, result);

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

}
