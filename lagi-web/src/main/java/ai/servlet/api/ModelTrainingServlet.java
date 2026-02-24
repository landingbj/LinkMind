package ai.servlet.api;

import ai.common.utils.ObservableList;
import ai.config.ContextLoader;
import ai.config.UploadConfig;
import ai.config.pojo.DiscriminativeModelsConfig;
import ai.database.impl.MysqlAdapter;
import ai.finetune.YoloK8sAdapter;
import ai.finetune.YoloTrainerAdapter;
import ai.finetune.repository.TrainingTaskRepository;
import ai.servlet.BaseServlet;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
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
    private static final Map<String, YoloK8sAdapter> trainerMap = new ConcurrentHashMap<>();

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
            case "logs":
                handleGetLogs(req, resp);
                break;
            case "upload":
                handleUploadFile(req, resp);
                break;
            case "download":
                handleDownloadFile(req, resp);
                break;
            case "downloadTaskOutput":
                handleDownloadOutPut(req, resp);
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
            case "uploadModel":
                handleUploadModel(req, resp);
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

    public void handleUploadModel(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> response = new HashMap<>();

        try {
            // 检查请求是否为multipart/form-data格式
            if (!ServletFileUpload.isMultipartContent(req)) {
                resp.setStatus(400);
                response.put("status", "failed");
                response.put("message", "请求必须是multipart/form-data格式");
                response.put("code", 400);
                responsePrint(resp, toJson(response));
                return;
            }

            // 解析multipart请求
            DiskFileItemFactory factory = new DiskFileItemFactory();
            ServletFileUpload upload = new ServletFileUpload(factory);
            @SuppressWarnings("unchecked")
            List<FileItem> items = upload.parseRequest(req);

            String modelName = null;
            FileItem fileItem = null;

            // 解析表单字段
            for (FileItem item : items) {
                if (item.isFormField()) {
                    String fieldName = item.getFieldName();
                    String fieldValue = item.getString("UTF-8");

                    switch (fieldName) {
                        case "model_name":
                            modelName = fieldValue;
                            break;
                    }
                } else {
                    fileItem = item;
                }
            }

            // 验证必填字段
            if (modelName == null || modelName.trim().isEmpty()) {
                resp.setStatus(400);
                response.put("status", "failed");
                response.put("message", "模型名称不能为空");
                response.put("code", 400);
                responsePrint(resp, toJson(response));
                return;
            }

            if (fileItem == null || fileItem.getSize() == 0) {
                resp.setStatus(400);
                response.put("status", "failed");
                response.put("message", "请选择要上传的文件");
                response.put("code", 400);
                responsePrint(resp, toJson(response));
                return;
            }

            // 获取SSH配置
            DiscriminativeModelsConfig.SshConfig sshConfig = getSshConfig();
            if (sshConfig == null || !sshConfig.isValid()) {
                resp.setStatus(500);
                response.put("status", "failed");
                response.put("message", "SSH配置未找到或无效");
                response.put("code", 500);
                responsePrint(resp, toJson(response));
                return;
            }

            // 远程服务器路径
            //String remoteDir = "/data/wangshuanglong/datasets";
            String remoteDir = "/home/tongguoshan";
            String fileName = fileItem.getName();
            // 确保文件名唯一，添加时间戳
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String remoteFileName = timestamp + "_" + fileName;
            String remoteFilePath = remoteDir + "/" + remoteFileName;

            // 上传文件到远程服务器
            boolean uploadSuccess = false;
            InputStream fileInputStream = null;
            try {
                fileInputStream = fileItem.getInputStream();
                String host = "103.85.179.118";
                int port = 40022;
                String username = "tongguoshan";
                String password = "Hezuo@123";
                uploadSuccess = uploadFileToRemoteServer(
                        host,
                        port,
                        username ,
                        password ,
//                        sshConfig.getHost(),
//                        sshConfig.getPort(),
//                        sshConfig.getUsername(),
//                        sshConfig.getPassword(),
                        fileInputStream,
                        remoteFilePath
                );
            } catch (IOException e) {
                // 打印完整的异常栈和错误信息
                log.error("上传文件到远程服务器时发生异常：", e);
                e.printStackTrace();
                // 也可以把异常信息返回给前端，方便排查
                throw new RuntimeException("SFTP上传失败：" + e.getMessage(), e);
            }finally {
                // 确保输入流关闭（try-with-resources也可以，但手动关闭更稳妥）
                if (fileInputStream != null) {
                    try {
                        fileInputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            if (!uploadSuccess) {
                resp.setStatus(500);
                response.put("status", "failed");
                response.put("message", "文件上传到远程服务器失败");
                response.put("code", 500);
                responsePrint(resp, toJson(response));
                return;
            }

            // 保存信息到数据库
            String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            long fileSize = fileItem.getSize();


            // 返回成功响应
            resp.setStatus(200);
            response.put("status", "success");
            response.put("message", "模型上传成功");
            response.put("code", 200);
            Map<String, Object> data = new HashMap<>();
            data.put("model_name", modelName);
            data.put("model_path", remoteFilePath);
            data.put("file_size", fileSize);
            data.put("created_at", currentTime);
            response.put("data", data);
            responsePrint(resp, toJson(response));

        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(500);
            response.put("status", "failed");
            response.put("message", "上传失败: " + e.getMessage());
            response.put("code", 500);
            responsePrint(resp, toJson(response));
        }
    }

    /**
     * 使用本地文件写入替代原 SFTP 上传（K8s 模式推荐挂载共享卷）
     */
    private boolean uploadFileToRemoteServer(String host, int port, String username,
                                             String password, InputStream fileInputStream,
                                             String remoteFilePath) {
        try {
            Path target = java.nio.file.Paths.get(remoteFilePath);
            Files.createDirectories(target.getParent());
            Files.copy(fileInputStream, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            log.info("文件已保存到本地路径（替代原 SFTP）: {}", remoteFilePath);
            return true;
        } catch (Exception e) {
            log.warn("保存文件失败（原SFTP逻辑已移除）: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取SSH配置
     */
    private DiscriminativeModelsConfig.SshConfig getSshConfig() {
        try {
            ContextLoader.loadContext();
            if (ContextLoader.configuration != null &&
                    ContextLoader.configuration.getModelPlatformConfig() != null &&
                    ContextLoader.configuration.getModelPlatformConfig().getDiscriminativeModelsConfig() != null) {

                DiscriminativeModelsConfig discriminativeConfig =
                        ContextLoader.configuration.getModelPlatformConfig().getDiscriminativeModelsConfig();

                // 优先使用通用SSH配置
                if (discriminativeConfig.getCommonSsh() != null &&
                        discriminativeConfig.getCommonSsh().isValid()) {
                    return discriminativeConfig.getCommonSsh();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 可用预训练模型列表查询
     */
    private void handlePretrain(HttpServletRequest req, HttpServletResponse resp) {

    }



    /**
     * 获取或创建指定模型的训练器
     */
    private YoloK8sAdapter getOrCreateTrainer(String modelName) {
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
    private YoloK8sAdapter createTrainerForModel(String modelName) {
        // 目前所有模型都使用类似的Docker训练方式，使用YoloTrainer作为基础
        // 未来可以根据模型类型创建不同的Trainer实现

        YoloK8sAdapter trainer = new YoloK8sAdapter();

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

            // 解析请求参数（从JSON请求体中获取）
            int page = 1; // 默认页码为1
            int pageSize = 10; // 默认每页条数为10

            // 先将请求体转为JSON对象
            String jsonBody = requestToJson(req);
            JsonObject jsonNode = gson.fromJson(jsonBody, JsonObject.class);

            // 解析page参数（从JSON中获取）
            if (jsonNode.has("page") && !jsonNode.get("page").isJsonNull()) {
                try {
                    // 支持JSON中为数字类型或字符串类型的数字
                    if (jsonNode.get("page").isJsonPrimitive() && jsonNode.get("page").getAsJsonPrimitive().isNumber()) {
                        page = jsonNode.get("page").getAsInt();
                    } else {
                        // 若为字符串类型，先转为字符串再解析
                        String pageStr = jsonNode.get("page").getAsString().trim();
                        page = Integer.parseInt(pageStr);
                    }
                    // 页码不能小于等于0，否则用默认值1
                    if (page <= 0) {
                        page = 1;
                    }
                } catch (NumberFormatException e) {
                    // 解析失败时保持默认值1
                    page = 1;
                }
            }

            // 解析page_size参数（从JSON中获取）
            if (jsonNode.has("page_size") && !jsonNode.get("page_size").isJsonNull()) {
                try {
                    // 支持JSON中为数字类型或字符串类型的数字
                    if (jsonNode.get("page_size").isJsonPrimitive() && jsonNode.get("page_size").getAsJsonPrimitive().isNumber()) {
                        pageSize = jsonNode.get("page_size").getAsInt();
                    } else {
                        // 若为字符串类型，先转为字符串再解析
                        String pageSizeStr = jsonNode.get("page_size").getAsString().trim();
                        pageSize = Integer.parseInt(pageSizeStr);
                    }
                    // 每页条数不能小于等于0，否则用默认值10
                    if (pageSize <= 0) {
                        pageSize = 10;
                    }
                } catch (NumberFormatException e) {
                    // 解析失败时保持默认值10
                    pageSize = 10;
                }
            }

            // 解析task_type参数（原有逻辑保留，增加null校验）
            String taskType = null;
            if (jsonNode.has("task_type") && !jsonNode.get("task_type").isJsonNull()) {
                taskType = jsonNode.get("task_type").getAsString().trim();
            }
            if (taskType == null || taskType.isEmpty()) {
                //默认是训练列表
                taskType = "train";
            }

            // 获取 TrainingTaskRepository
            TrainingTaskRepository repository = getTrainingTaskRepository();
            if (repository == null) {
                resp.setStatus(503);
                JSONObject error = new JSONObject();
                error.put("status", "ERROR");
                error.put("message", "训练服务未初始化，无法获取任务列表。请检查配置或等待服务初始化完成。");
                error.put("code", "SERVICE_UNAVAILABLE");
                responsePrint(resp, error.toString());
                return;
            }

            Map<String, Object> result = repository.getTaskList(taskType, page, pageSize);

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

            YoloK8sAdapter trainer = getTrainerForTask(taskId);
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
     * 上传文件到容器
     * POST /model/training/upload
     */
    private void handleUploadFile(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        UploadConfig bean = ContextLoader.getBean(UploadConfig.class);
        if (bean == null) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "无上传配置");
            resp.setStatus(500);
            responsePrint(resp, toJson(error));
            return;
        }
        if (bean.getCommon() == null || bean.getCommon().getHostUploadPath() == null) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "无公共存储地址配置");
            resp.setStatus(500);
            responsePrint(resp, toJson(error));
            return;
        }

        Path path = Paths.get(bean.getCommon().getHostUploadPath());
        if (!path.toFile().exists()) {
            path.toFile().mkdirs();
        }

        Map<String, Object> response = new HashMap<>();
        try {
            if (!ServletFileUpload.isMultipartContent(req)) {
                resp.setStatus(400);
                response.put("status", "failed");
                response.put("message", "请求必须是multipart/form-data格式");
                response.put("code", 400);
                responsePrint(resp, toJson(response));
                return;
            }

            DiskFileItemFactory factory = new DiskFileItemFactory();
            ServletFileUpload upload = new ServletFileUpload(factory);
            @SuppressWarnings("unchecked")
            List<FileItem> items = upload.parseRequest(req);

            FileItem fileItem = null;
            for (FileItem item : items) {
                if (!item.isFormField()) {
                    fileItem = item;
                    break;
                }
            }

            if (fileItem == null || fileItem.getSize() == 0) {
                resp.setStatus(400);
                response.put("status", "failed");
                response.put("message", "请选择要上传的文件");
                response.put("code", 400);
                responsePrint(resp, toJson(response));
                return;
            }

            String originalName = Paths.get(fileItem.getName()).getFileName().toString();
            String folderName = UUID.randomUUID().toString();
            Path targetDir = path.resolve(folderName);
            Files.createDirectories(targetDir);
            Path target = targetDir.resolve(originalName);

            try (InputStream in = fileItem.getInputStream()) {
                Files.copy(in, target);
            }

            response.put("status", "success");
            response.put("message", "上传成功");
            response.put("file_name", originalName);
            response.put("saved_path", target.toString());
            responsePrint(resp, toJson(response));

        } catch (Exception e) {
            log.error("上传文件失败", e);
            resp.setStatus(500);
            response.put("status", "failed");
            response.put("message", e.getMessage());
            response.put("code", 500);
            responsePrint(resp, toJson(response));
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

            YoloK8sAdapter trainer = getTrainerForTask(taskId);
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


    private void handleDownloadOutPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            // 从GET请求中获取id参数
            String id = req.getParameter("taskId");
            if (id == null || id.trim().isEmpty()) {
                resp.setStatus(400);
                resp.setContentType("application/json;charset=utf-8");
                Map<String, String> error = new HashMap<>();
                error.put("error", "缺少必需参数: taskId");
                responsePrint(resp, toJson(error));
                return;
            }

            // 获取 TrainingTaskRepository
            TrainingTaskRepository repository = getTrainingTaskRepository();
            if (repository == null) {
                resp.setStatus(503);
                resp.setContentType("application/json;charset=utf-8");
                Map<String, String> error = new HashMap<>();
                error.put("error", "训练服务未初始化，无法获取任务信息");
                responsePrint(resp, toJson(error));
                return;
            }

            // 根据id查询任务详情
            Map<String, Object> taskDetail = repository.getTaskDetailByTaskId(id);
            if (taskDetail == null) {
                resp.setStatus(404);
                resp.setContentType("application/json;charset=utf-8");
                Map<String, String> error = new HashMap<>();
                error.put("error", "未找到任务: " + id);
                responsePrint(resp, toJson(error));
                return;
            }

            // 获取output_path字段（注释中的outputfile应该是指这个字段）
            String outputPath = (String) taskDetail.get("output_path");
            if (outputPath == null || outputPath.trim().isEmpty()) {
                resp.setStatus(404);
                resp.setContentType("application/json;charset=utf-8");
                Map<String, String> error = new HashMap<>();
                error.put("error", "任务输出文件路径为空: " + id);
                responsePrint(resp, toJson(error));
                return;
            }

            // 将路径转换为File对象
            java.io.File file = new java.io.File(outputPath);
            if (!file.exists()) {
                resp.setStatus(404);
                resp.setContentType("application/json;charset=utf-8");
                Map<String, String> error = new HashMap<>();
                error.put("error", "输出文件不存在: " + outputPath);
                responsePrint(resp, toJson(error));
                return;
            }

            // 检查文件是否可读
            if (!file.canRead()) {
                resp.setStatus(500);
                resp.setContentType("application/json;charset=utf-8");
                Map<String, String> error = new HashMap<>();
                error.put("error", "文件无读取权限: " + outputPath);
                responsePrint(resp, toJson(error));
                return;
            }

            // 判断是文件还是目录
            if (file.isDirectory()) {
                // 如果是目录，压缩成ZIP文件下载
                try {
                    Path tempZipFile = Files.createTempFile("download_", ".zip");
                    zipDirectory(file.toPath(), tempZipFile);

                    String fileName = file.getName() + ".zip";
                    String encodedFileName = java.net.URLEncoder.encode(fileName, "UTF-8");

                    // 设置响应头
                    resp.setContentType("application/zip");
                    resp.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"");
                    resp.setContentLengthLong(Files.size(tempZipFile));

                    // 传输文件
                    try (InputStream inputStream = Files.newInputStream(tempZipFile);
                         java.io.OutputStream outputStream = resp.getOutputStream()) {

                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                        outputStream.flush();
                    }

                    // 删除临时文件
                    Files.deleteIfExists(tempZipFile);
                    log.info("目录下载成功: {} (压缩为 {})", outputPath, fileName);

                } catch (Exception e) {
                    log.error("目录压缩下载失败: {}", outputPath, e);
                    resp.setStatus(500);
                    resp.setContentType("application/json;charset=utf-8");
                    Map<String, String> error = new HashMap<>();
                    error.put("error", "目录压缩下载失败: " + e.getMessage());
                    responsePrint(resp, toJson(error));
                }
            } else {
                // 如果是文件，直接下载
                String fileName = file.getName();
                String encodedFileName = java.net.URLEncoder.encode(fileName, "UTF-8");

                // 设置响应头
                resp.setContentType("application/octet-stream");
                resp.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"");
                resp.setContentLengthLong(file.length());

                // 传输文件
                try (java.io.FileInputStream fileInputStream = new java.io.FileInputStream(file);
                     java.io.OutputStream outputStream = resp.getOutputStream()) {

                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    outputStream.flush();
                }

                log.info("文件下载成功: {}", outputPath);
            }

        } catch (Exception e) {
            log.error("下载任务输出文件失败", e);
            resp.setStatus(500);
            resp.setContentType("application/json;charset=utf-8");
            Map<String, String> error = new HashMap<>();
            error.put("error", "下载失败: " + e.getMessage());
            responsePrint(resp, toJson(error));
        }
    }

    /**
     * 压缩目录为ZIP文件
     */
    private void zipDirectory(Path sourceDir, Path zipFile) throws IOException {
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(zipFile))) {
            Files.walkFileTree(sourceDir, new java.nio.file.SimpleFileVisitor<Path>() {
                @Override
                public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                    Path targetFile = sourceDir.relativize(file);
                    zos.putNextEntry(new java.util.zip.ZipEntry(targetFile.toString().replace("\\", "/")));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                    if (!sourceDir.equals(dir)) {
                        Path targetDir = sourceDir.relativize(dir);
                        zos.putNextEntry(new java.util.zip.ZipEntry(targetDir.toString().replace("\\", "/") + "/"));
                        zos.closeEntry();
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
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

            YoloK8sAdapter trainer = getTrainerForTask(taskId);
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
                YoloK8sAdapter trainer = trainerMap.get(modelName);
                if (trainer == null) {
                    resp.setStatus(404);
                    Map<String, String> error = new HashMap<>();
                    error.put("error", "模型训练器不存在: " + modelName);
                    responsePrint(resp, toJson(error));
                    return;
                }
                String result = null;//trainer.listTrainingContainers();
                responsePrint(resp, result);
            } else {
                // 列出所有容器
                Map<String, Object> result = new HashMap<>();
                for (Map.Entry<String, YoloK8sAdapter> entry : trainerMap.entrySet()) {
                    String model = entry.getKey();
                    YoloK8sAdapter trainer = entry.getValue();
                    //result.put(model, trainer.listTrainingContainers());
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
    private YoloK8sAdapter getTrainerForTask(String taskId) {
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

    /**
     * 获取 TrainingTaskRepository 实例
     * 优先从 AITrainingServlet.yoloTrainer 获取，如果为 null 则尝试其他方式
     */
    private TrainingTaskRepository getTrainingTaskRepository() {
        return new TrainingTaskRepository(MysqlAdapter.getInstance());
    }
}
