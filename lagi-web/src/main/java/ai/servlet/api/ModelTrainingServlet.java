package ai.servlet.api;

import ai.common.utils.ObservableList;
import ai.config.ContextLoader;
import ai.config.pojo.DiscriminativeModelsConfig;
import ai.finetune.YoloTrainerAdapter;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用AI模型训练任务管理 Servlet
 * 支持15种不同类型的模型：YOLO, CRNN, HRNet, PIDNet等
 * 提供训练任务的完整生命周期管理和流式输出
 *
 * 支持的模型类型：
 * - detection: yolov8, yolov11, centernet, tracknetv3
 * - segmentation: pidnet, deeplabv3
 * - recognition: crnn
 * - reid: sports_reid, clip_reid
 * - feature_extraction: resnet18, osnet
 * - keypoint_detection: hrnet
 * - event_detection: tdeed
 * - video_segmentation: transnetv2
 * - ocr: paddleocrv4
 */
@Slf4j
public class ModelTrainingServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    // 存储模型名称到 Trainer 实例的映射（支持多个模型）
    private static final Map<String, YoloTrainerAdapter> trainerMap = new ConcurrentHashMap<>();

    // 存储任务 ID 到容器名称的映射
    private static final Map<String, String> taskContainerMap = new ConcurrentHashMap<>();

    // 存储任务ID到流式输出的映射
    private static final Map<String, ObservableList<String>> taskStreamMap = new ConcurrentHashMap<>();

    // 存储任务ID到模型名称的映射
    private static final Map<String, String> taskModelMap = new ConcurrentHashMap<>();

    /**
     * 检查训练器是否可用
     */
    private boolean isTrainerAvailable(String modelName) {
        return trainerMap.containsKey(modelName);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
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
            case "lists":
                handleTaskList(req, resp);
                break;
            case "pretrain":
                handlePretrain(req, resp);
                break;
            default:
                resp.setStatus(404);
                Map<String, String> error = new HashMap<>();
                error.put("error", "接口不存在: " + method);
                responsePrint(resp, toJson(error));
        }
    }

    /**
     * 可用预训练模型列表查询
     */
    private void handlePretrain(HttpServletRequest req, HttpServletResponse resp) {

    }



    /**
     * 获取或创建指定模型的训练器
     */
    private YoloTrainerAdapter getOrCreateTrainer(String modelName) {
        return trainerMap.computeIfAbsent(modelName, key -> {
            try {
                log.info("为模型 {} 创建新的训练器实例", modelName);
                // 这里暂时还是用YoloTrainer，未来可以扩展为工厂模式
                return createTrainerForModel(modelName);
            } catch (Exception e) {
                log.error("创建模型训练器失败: modelName={}", modelName, e);
                return null;
            }
        });
    }

    /**
     * 根据模型名称创建对应的训练器
     */
    private YoloTrainerAdapter createTrainerForModel(String modelName) {
        // 目前所有模型都使用类似的Docker训练方式，使用YoloTrainer作为基础
        // 未来可以根据模型类型创建不同的Trainer实现

        YoloTrainerAdapter trainer = new YoloTrainerAdapter();

        // 从配置文件读取模型特定的配置
        try {
            DiscriminativeModelsConfig discriminativeConfig = ContextLoader.configuration
                    .getModelPlatformConfig()
                    .getDiscriminativeModelsConfig();

            if (discriminativeConfig != null) {
                // 优先使用模型特定的配置，否则使用通用配置
                DiscriminativeModelsConfig.SshConfig ssh = discriminativeConfig.getCommonSsh();

                // 根据模型名称获取对应的配置
                DiscriminativeModelsConfig.DockerConfig docker = null;
                if ("yolov8".equals(modelName) || "yolov11".equals(modelName)) {
                    DiscriminativeModelsConfig.YoloConfig yoloConfig = discriminativeConfig.getYolo();
                    if (yoloConfig != null) {
                        ssh = discriminativeConfig.getEffectiveSshConfig(yoloConfig);
                        docker = yoloConfig.getDocker();
                    }
                }
                // TODO: 为其他模型添加配置读取逻辑

                if (ssh != null && ssh.isValid()) {
                    trainer.setRemoteServer(
                            ssh.getHost(),
                            ssh.getPort(),
                            ssh.getUsername(),
                            ssh.getPassword()
                    );
                    log.info("✓ 模型 {} SSH 配置加载成功: {}:{}", modelName, ssh.getHost(), ssh.getPort());
                }

                if (docker != null && docker.isValid()) {
                    if (docker.getImage() != null) {
                        trainer.setDockerImage(docker.getImage());
                    }
                    if (docker.getVolumeMount() != null) {
                        trainer.setVolumeMount(docker.getVolumeMount());
                    }
                    log.info("✓ 模型 {} Docker 配置加载成功", modelName);
                }
            }
        } catch (Exception e) {
            log.error("加载模型配置失败: modelName={}", modelName, e);
        }

        return trainer;
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
        error.put("message", "模型训练服务未启用或配置不正确: " + modelName);
        error.put("detail", "请检查 lagi.yml 中的 discriminative_models 配置");

        response.getWriter().write(error.toString());
    }

    @Override
    public void init() throws ServletException {
        super.init();
       ContextLoader.loadContext();
    }



    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doGet(req, resp);
    }
    /**
     * 查询训练任务列表
     * POST /api/model/lists
     *
     * 请求参数（JSON）：
     * {
     *   "page": 1,        // 页码，默认为1
     *   "page_size": 10   // 每页数量，默认为10
     * }
     *
     * 返回结果：
     * {
     *   "status": "SUCCESS",
     *   "data": [
     *     {
     *       "taskId": "uuid",
     *       "datasetName": "dataset-name",
     *       "epochs": 20,
     *       "status": "completed",
     *       "progress": "100%",
     *       "currentEpoch": 20,
     *       "startTime": "2025-11-18 10:44:26",
     *       "createdAt": "2025-11-18 10:44:26"
     *     }
     *   ],
     *   "total": 2,
     *   "statusCount": {
     *     "running": 0,
     *     "stopped": 0,
     *     "waiting": 0,
     *     "completed": 1,
     *     "failed": 1
     *   }
     * }
     */
    private void handleTaskList(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            // 解析请求参数
            String jsonBody = requestToJson(req);
            JSONObject requestParams = JSONUtil.parseObj(jsonBody);

            int page = requestParams.getInt("page", 1);
            int pageSize = requestParams.getInt("page_size", 10);

            // 参数校验
            if (page < 1) {
                page = 1;
            }
            if (pageSize < 1 || pageSize > 100) {
                pageSize = 10;
            }
            // 调用 repository 查询任务列表
            Map<String, Object> result = AITrainingServlet.yoloTrainer.getRepository().getTaskList(page, pageSize);

            // 返回结果
            responsePrint(resp, JSONUtil.toJsonStr(result));

        } catch (Exception e) {
            log.error("查询任务列表失败", e);
            resp.setStatus(500);
            JSONObject error = new JSONObject();
            error.put("status", "ERROR");
            error.put("message", "查询任务列表失败: " + e.getMessage());
            responsePrint(resp, error.toString());
        }
    }


    /**
     * 启动训练任务（通用）
     * POST /model/training/start
     *
     * 请求参数（JSON）：
     * {
     *   "model_name": "yolov8",  // 必需：模型名称
     *   "task_id": "可选，不传则自动生成",
     *   "track_id": "可选，不传则自动生成",
     *   "model_path": "/app/data/models/yolo11n.pt",
     *   "data": "/app/data/datasets/YoloV8/data.yaml",
     *   "epochs": 10,
     *   "batch": 2,
     *   "imgsz": 640,
     *   "device": "0",
     *   ... // 其他模型特定参数
     * }
     */
    private void handleStartTraining(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            String jsonBody = requestToJson(req);
            JSONObject config = JSONUtil.parseObj(jsonBody);

            // 获取模型名称（必需参数）
            String modelName = config.getStr("model_name");
            if (modelName == null || modelName.isEmpty()) {
                resp.setStatus(400);
                Map<String, String> error = new HashMap<>();
                error.put("error", "缺少必需参数: model_name");
                error.put("支持的模型", "yolov8, yolov11, centernet, crnn, hrnet, pidnet, deeplabv3, sports_reid, clip_reid, resnet18, osnet, tdeed, transnetv2, tracknetv3, paddleocrv4");
                responsePrint(resp, toJson(error));
                return;
            }

            // 获取或创建该模型的训练器
            YoloTrainerAdapter trainer = getOrCreateTrainer(modelName);
            if (trainer == null) {
                sendServiceUnavailableError(resp, modelName);
                return;
            }

            // 生成或获取任务ID
            String taskId = config.getStr("task_id");
            if (taskId == null || taskId.isEmpty()) {
                taskId = UUID.randomUUID().toString();
            }

            String trackId = config.getStr("track_id");
            if (trackId == null || trackId.isEmpty()) {
                trackId = UUID.randomUUID().toString();
            }

            // 记录任务所属的模型
            taskModelMap.put(taskId, modelName);

            // 构建训练配置
            JSONObject trainConfig = buildTrainConfig(config, taskId, trackId, modelName);

            // 启动训练
            String result = trainer.startTraining(taskId, trackId, trainConfig);

            // 保存任务到容器的映射
            if (YoloTrainerAdapter.isSuccess(result)) {
                JSONObject resultJson = JSONUtil.parseObj(result);
                String containerName = resultJson.getStr("containerName");
                taskContainerMap.put(taskId, containerName);

                // 在返回结果中添加模型名称
                resultJson.put("modelName", modelName);
                result = resultJson.toString();
            }

            responsePrint(resp, result);

        } catch (Exception e) {
            log.error("启动训练任务失败", e);
            resp.setStatus(500);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            responsePrint(resp, toJson(error));
        }
    }

    /**
     * 构建训练配置
     */
    private JSONObject buildTrainConfig(JSONObject inputConfig, String taskId, String trackId, String modelName) {
        JSONObject trainConfig = new JSONObject();
        trainConfig.put("the_train_type", "train");
        trainConfig.put("task_id", taskId);
        trainConfig.put("track_id", trackId);
        trainConfig.put("model_name", modelName);

        // 根据不同模型设置默认值
        switch (modelName) {
            case "yolov8":
            case "yolov11":
                trainConfig.put("train_log_file", inputConfig.getStr("train_log_file", "/app/data/train.log"));
                trainConfig.put("model_path", inputConfig.getStr("model_path", "/app/data/models/yolo11n.pt"));
                trainConfig.put("data", inputConfig.getStr("data", "/app/data/datasets/YoloV8/data.yaml"));
                trainConfig.put("epochs", inputConfig.getInt("epochs", 10));
                trainConfig.put("batch", inputConfig.getInt("batch", 2));
                trainConfig.put("imgsz", inputConfig.getInt("imgsz", 640));
                trainConfig.put("device", inputConfig.getStr("device", "0"));
                trainConfig.put("optimizer", inputConfig.getStr("optimizer", "sgd"));
                trainConfig.put("project", inputConfig.getStr("project", "/app/data/project"));
                trainConfig.put("runs_dir", inputConfig.getStr("runs_dir", "/app/data"));
                trainConfig.put("exist_ok", inputConfig.getBool("exist_ok", true));
                trainConfig.put("name", inputConfig.getStr("name", modelName + "_experiment_" + System.currentTimeMillis()));
                break;

            case "crnn":
                trainConfig.put("model_path", inputConfig.getStr("model_path", "/app/data/models/crnn.pth"));
                trainConfig.put("data", inputConfig.getStr("data", "/app/data/datasets/text_recognition"));
                trainConfig.put("epochs", inputConfig.getInt("epochs", 100));
                trainConfig.put("batch", inputConfig.getInt("batch", 64));
                trainConfig.put("imgsz", inputConfig.getStr("imgsz", "32x100"));
                trainConfig.put("device", inputConfig.getStr("device", "0"));
                trainConfig.put("optimizer", inputConfig.getStr("optimizer", "adam"));
                trainConfig.put("learning_rate", inputConfig.getDouble("learning_rate", 0.001));
                break;

            case "hrnet":
                trainConfig.put("model_path", inputConfig.getStr("model_path", "/app/data/models/hrnet_w32.pth"));
                trainConfig.put("data", inputConfig.getStr("data", "/app/data/datasets/keypoint"));
                trainConfig.put("epochs", inputConfig.getInt("epochs", 210));
                trainConfig.put("batch", inputConfig.getInt("batch", 16));
                trainConfig.put("imgsz", inputConfig.getStr("imgsz", "384x288"));
                trainConfig.put("device", inputConfig.getStr("device", "0"));
                trainConfig.put("optimizer", inputConfig.getStr("optimizer", "adam"));
                trainConfig.put("learning_rate", inputConfig.getDouble("learning_rate", 0.001));
                break;

            // TODO: 为其他模型添加默认配置
            default:
                // 通用默认配置
                trainConfig.put("model_path", inputConfig.getStr("model_path", "/app/data/models/model.pth"));
                trainConfig.put("data", inputConfig.getStr("data", "/app/data/datasets/default"));
                trainConfig.put("epochs", inputConfig.getInt("epochs", 100));
                trainConfig.put("batch", inputConfig.getInt("batch", 16));
                trainConfig.put("imgsz", inputConfig.getStr("imgsz", "640"));
                trainConfig.put("device", inputConfig.getStr("device", "0"));
                trainConfig.put("optimizer", inputConfig.getStr("optimizer", "adam"));
        }

        // 复制所有其他参数
        for (String key : inputConfig.keySet()) {
            if (!trainConfig.containsKey(key)) {
                trainConfig.put(key, inputConfig.get(key));
            }
        }

        return trainConfig;
    }

    /**
     * 暂停容器
     * POST /model/training/pause?taskId=xxx
     */
    private void handlePauseContainer(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            String taskId = req.getParameter("taskId");
            String containerId = getContainerIdFromRequest(req);

            if (containerId == null) {
                resp.setStatus(400);
                Map<String, String> error = new HashMap<>();
                error.put("error", "缺少 taskId 或 containerId 参数");
                responsePrint(resp, toJson(error));
                return;
            }

            // 获取对应的训练器
            YoloTrainerAdapter trainer = getTrainerForTask(taskId);
            if (trainer == null) {
                resp.setStatus(404);
                Map<String, String> error = new HashMap<>();
                error.put("error", "找不到对应的训练器");
                responsePrint(resp, toJson(error));
                return;
            }

            String result = trainer.pauseContainer(containerId);
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
     * POST /model/training/resume?taskId=xxx
     */
    private void handleResumeContainer(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            String taskId = req.getParameter("taskId");
            String containerId = getContainerIdFromRequest(req);

            if (containerId == null) {
                resp.setStatus(400);
                Map<String, String> error = new HashMap<>();
                error.put("error", "缺少 taskId 或 containerId 参数");
                responsePrint(resp, toJson(error));
                return;
            }

            YoloTrainerAdapter trainer = getTrainerForTask(taskId);
            if (trainer == null) {
                resp.setStatus(404);
                Map<String, String> error = new HashMap<>();
                error.put("error", "找不到对应的训练器");
                responsePrint(resp, toJson(error));
                return;
            }

            String result = trainer.resumeContainer(containerId);
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
     * POST /model/training/stop?taskId=xxx
     */
    private void handleStopContainer(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            String taskId = req.getParameter("taskId");
            String containerId = getContainerIdFromRequest(req);

            if (containerId == null) {
                resp.setStatus(400);
                Map<String, String> error = new HashMap<>();
                error.put("error", "缺少 taskId 或 containerId 参数");
                responsePrint(resp, toJson(error));
                return;
            }

            YoloTrainerAdapter trainer = getTrainerForTask(taskId);
            if (trainer == null) {
                resp.setStatus(404);
                Map<String, String> error = new HashMap<>();
                error.put("error", "找不到对应的训练器");
                responsePrint(resp, toJson(error));
                return;
            }

            String result = trainer.stopContainer(containerId);
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
     * 查看容器日志
     * GET /model/training/logs?taskId=xxx&lines=100
     */
    private void handleGetLogs(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            String taskId = req.getParameter("taskId");
            String containerId = getContainerIdFromRequest(req);

            if (containerId == null) {
                resp.setStatus(400);
                Map<String, String> error = new HashMap<>();
                error.put("error", "缺少 taskId 或 containerId 参数");
                responsePrint(resp, toJson(error));
                return;
            }

            YoloTrainerAdapter trainer = getTrainerForTask(taskId);
            if (trainer == null) {
                resp.setStatus(404);
                Map<String, String> error = new HashMap<>();
                error.put("error", "找不到对应的训练器");
                responsePrint(resp, toJson(error));
                return;
            }

            String linesStr = req.getParameter("lines");
            int lines = linesStr != null ? Integer.parseInt(linesStr) : 100;

            String result = trainer.getContainerLogs(containerId, lines);
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
     * 流式获取容器日志
     * GET /model/training/stream?taskId=xxx
     */
    private void handleStreamLogs(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String taskId = req.getParameter("taskId");
        String containerId = getContainerIdFromRequest(req);

        if (containerId == null) {
            resp.setStatus(400);
            resp.setContentType("application/json;charset=utf-8");
            Map<String, String> error = new HashMap<>();
            error.put("error", "缺少 taskId 或 containerId 参数");
            responsePrint(resp, toJson(error));
            return;
        }

        YoloTrainerAdapter trainer = getTrainerForTask(taskId);
        if (trainer == null) {
            resp.setStatus(404);
            resp.setContentType("application/json;charset=utf-8");
            Map<String, String> error = new HashMap<>();
            error.put("error", "找不到对应的训练器");
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
            // 创建流式日志输出
            ObservableList<String> logStream = trainer.getContainerLogsStream(containerId);

            // 订阅日志流
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
     * POST /model/training/evaluate
     */
    private void handleEvaluate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            String jsonBody = requestToJson(req);
            JSONObject config = JSONUtil.parseObj(jsonBody);

            String modelName = config.getStr("model_name", "yolov8");
            YoloTrainerAdapter trainer = getOrCreateTrainer(modelName);

            if (trainer == null) {
                sendServiceUnavailableError(resp, modelName);
                return;
            }

            String result = trainer.evaluate(config);
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
     * POST /model/training/predict
     */
    private void handlePredict(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            String jsonBody = requestToJson(req);
            JSONObject config = JSONUtil.parseObj(jsonBody);

            String modelName = config.getStr("model_name", "yolov8");
            YoloTrainerAdapter trainer = getOrCreateTrainer(modelName);

            if (trainer == null) {
                sendServiceUnavailableError(resp, modelName);
                return;
            }

            String result = trainer.predict(config);
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
     * POST /model/training/export
     */
    private void handleExportModel(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            String jsonBody = requestToJson(req);
            JSONObject config = JSONUtil.parseObj(jsonBody);

            String modelName = config.getStr("model_name", "yolov8");
            YoloTrainerAdapter trainer = getOrCreateTrainer(modelName);

            if (trainer == null) {
                sendServiceUnavailableError(resp, modelName);
                return;
            }

            String result = trainer.exportModel(config);
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
     * POST /model/training/upload
     */
    private void handleUploadFile(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            String jsonBody = requestToJson(req);
            JSONObject params = JSONUtil.parseObj(jsonBody);

            String taskId = params.getStr("taskId");
            String containerId = params.getStr("containerId");

            if (containerId == null && taskId != null) {
                containerId = taskContainerMap.get(taskId);
            }

            if (containerId == null) {
                resp.setStatus(400);
                Map<String, String> error = new HashMap<>();
                error.put("error", "缺少 taskId 或 containerId 参数");
                responsePrint(resp, toJson(error));
                return;
            }

            YoloTrainerAdapter trainer = getTrainerForTask(taskId);
            if (trainer == null) {
                resp.setStatus(404);
                Map<String, String> error = new HashMap<>();
                error.put("error", "找不到对应的训练器");
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

            String result = trainer.uploadToContainer(containerId, localPath, containerPath);
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
     * POST /model/training/download
     */
    private void handleDownloadFile(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            String jsonBody = requestToJson(req);
            JSONObject params = JSONUtil.parseObj(jsonBody);

            String taskId = params.getStr("taskId");
            String containerId = params.getStr("containerId");

            if (containerId == null && taskId != null) {
                containerId = taskContainerMap.get(taskId);
            }

            if (containerId == null) {
                resp.setStatus(400);
                Map<String, String> error = new HashMap<>();
                error.put("error", "缺少 taskId 或 containerId 参数");
                responsePrint(resp, toJson(error));
                return;
            }

            YoloTrainerAdapter trainer = getTrainerForTask(taskId);
            if (trainer == null) {
                resp.setStatus(404);
                Map<String, String> error = new HashMap<>();
                error.put("error", "找不到对应的训练器");
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

            String result = trainer.downloadFromContainer(containerId, containerPath, localPath);
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
     * POST /model/training/commit
     */
    private void handleCommitImage(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            String jsonBody = requestToJson(req);
            JSONObject params = JSONUtil.parseObj(jsonBody);

            String taskId = params.getStr("taskId");
            String containerId = params.getStr("containerId");

            if (containerId == null && taskId != null) {
                containerId = taskContainerMap.get(taskId);
            }

            if (containerId == null) {
                resp.setStatus(400);
                Map<String, String> error = new HashMap<>();
                error.put("error", "缺少 taskId 或 containerId 参数");
                responsePrint(resp, toJson(error));
                return;
            }

            YoloTrainerAdapter trainer = getTrainerForTask(taskId);
            if (trainer == null) {
                resp.setStatus(404);
                Map<String, String> error = new HashMap<>();
                error.put("error", "找不到对应的训练器");
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

            String result = trainer.commitContainerAsImage(containerId, imageName, imageTag);
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
     * GET /model/training/list
     */
    private void handleListContainers(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            String modelName = req.getParameter("model_name");

            if (modelName != null && !modelName.isEmpty()) {
                // 列出特定模型的容器
                YoloTrainerAdapter trainer = trainerMap.get(modelName);
                if (trainer == null) {
                    resp.setStatus(404);
                    Map<String, String> error = new HashMap<>();
                    error.put("error", "模型训练器不存在: " + modelName);
                    responsePrint(resp, toJson(error));
                    return;
                }
                String result = trainer.listTrainingContainers();
                responsePrint(resp, result);
            } else {
                // 列出所有容器
                Map<String, Object> result = new HashMap<>();
                for (Map.Entry<String, YoloTrainerAdapter> entry : trainerMap.entrySet()) {
                    String model = entry.getKey();
                    YoloTrainerAdapter trainer = entry.getValue();
                    result.put(model, trainer.listTrainingContainers());
                }
                responsePrint(resp, toJson(result));
            }

        } catch (Exception e) {
            log.error("列出容器失败", e);
            resp.setStatus(500);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            responsePrint(resp, toJson(error));
        }
    }

    /**
     * 列出支持的模型
     * GET /model/training/models
     */
    private void handleListModels(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        Map<String, Object> result = new HashMap<>();

        Map<String, String[]> modelsByCategory = new HashMap<>();
        modelsByCategory.put("detection", new String[]{"yolov8", "yolov11", "centernet", "tracknetv3"});
        modelsByCategory.put("segmentation", new String[]{"pidnet", "deeplabv3"});
        modelsByCategory.put("recognition", new String[]{"crnn"});
        modelsByCategory.put("reid", new String[]{"sports_reid", "clip_reid"});
        modelsByCategory.put("feature_extraction", new String[]{"resnet18", "osnet"});
        modelsByCategory.put("keypoint_detection", new String[]{"hrnet"});
        modelsByCategory.put("event_detection", new String[]{"tdeed"});
        modelsByCategory.put("video_segmentation", new String[]{"transnetv2"});
        modelsByCategory.put("ocr", new String[]{"paddleocrv4"});

        result.put("supported_models", modelsByCategory);
        result.put("active_trainers", trainerMap.keySet());
        result.put("total_active_tasks", taskContainerMap.size());

        responsePrint(resp, toJson(result));
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
     * 根据任务ID获取对应的训练器
     */
    private YoloTrainerAdapter getTrainerForTask(String taskId) {
        if (taskId == null) {
            return null;
        }
        String modelName = taskModelMap.get(taskId);
        if (modelName == null) {
            // 如果找不到模型名称，尝试使用默认的yolov8
            modelName = "yolov8";
        }
        return trainerMap.get(modelName);
    }
}
