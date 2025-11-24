package ai.servlet.api;

import ai.common.utils.ObservableList;
import ai.config.ContextLoader;
import ai.config.pojo.DiscriminativeModelsConfig;
import ai.finetune.YoloTrainerAdapter;
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    // 存储任务 ID 到容器名称的映射
    private static final Map<String, String> taskContainerMap = new ConcurrentHashMap<>();

    // 存储任务ID到流式输出的映射
    private static final Map<String, ObservableList<String>> taskStreamMap = new ConcurrentHashMap<>();

    /**
     * 检查训练服务是否可用
     */
    private boolean isTrainerAvailable() {
        return yoloTrainer != null || !trainerMap.isEmpty();
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
            default:
                resp.setStatus(404);
                Map<String, String> error = new HashMap<>();
                error.put("error", "接口不存在: " + method);
                responsePrint(resp, toJson(error));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doGet(req, resp);
    }

    /**
     * 启动训练任务（通用版，支持所有模型）
     * POST /ai/training/start
     *
     * 请求参数（JSON）：
     * {
     *   "model_name": "任意模型名称",  // 必填：如 yolov8, yolov11, centernet, custom_model 等
     *   "model_category": "可选",      // 可选：detection, segmentation, classification 等（不指定则自动推断）
     *   "model_framework": "可选",     // 可选：pytorch, tensorflow, paddle 等（不指定则自动推断）
     *   "task_id": "可选，不传则自动生成",
     *   "track_id": "可选，不传则自动生成",
     *   "model_path": "/app/data/models/model.pt",
     *   "data": "/app/data/datasets/data.yaml",
     *   "epochs": 10,
     *   "batch": 2,
     *   "imgsz": 640,
     *   "device": "0",
     *   "project": "/app/data/project",
     *   "name": "experiment_name"
     * }
     */
    private void handleStartTraining(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            String jsonBody = requestToJson(req);
            JSONObject config = JSONUtil.parseObj(jsonBody);

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
                result = startYoloTraining((YoloTrainerAdapter) trainer, taskId, trackId, config);
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
                
                // 提取训练配置信息
                JSONObject trainConfig = new JSONObject();
                trainConfig.put("dataset_path", config.getStr("data", ""));
                trainConfig.put("model_path", config.getStr("model_path", ""));
                trainConfig.put("epochs", config.getInt("epochs", 0));
                trainConfig.put("batch", config.getInt("batch", 0));
                trainConfig.put("imgsz", config.getInt("imgsz", 640));
                
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
    private String startYoloTraining(YoloTrainerAdapter trainer, String taskId, String trackId, JSONObject config) {
        // 从配置文件获取默认配置
        DiscriminativeModelsConfig discriminativeConfig = ContextLoader.configuration
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
     * 暂停容器
     * POST /ai/training/pause?taskId=xxx 或 ?containerId=xxx
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

            String result = yoloTrainer.evaluate(config);
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

            String result = yoloTrainer.predict(config);
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

            String result = yoloTrainer.exportModel(config);
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
            modelInfo.put("yolov8", "球员检测 - YOLOv8");
            modelInfo.put("yolov11", "足球检测 - YOLOv11");
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
}
