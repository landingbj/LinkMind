package ai.finetune;

import ai.common.utils.ObservableList;
import ai.config.ContextLoader;
import ai.config.pojo.DiscriminativeModelsConfig;
import ai.database.impl.MysqlAdapter;
import ai.finetune.config.ModelConfigManager;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * YOLO 模型训练实现类
 * 基于 Docker 容器进行 YOLO 模型的训练、评估、预测等操作
 * 所有操作都会入库记录
 *
 * 代码结构：
 * 1. 常量和静态成员
 * 2. 构造函数
 * 3. 训练任务核心方法
 * 4. Docker容器管理方法
 * 5. 日志流式获取方法
 * 6. 配置创建方法（业务配置）
 * 7. 数据库操作方法（CRUD）
 * 8. 工具辅助方法
 */
@Slf4j
public class YoloK8sAdapter extends K8sTrainerAbstract {

//    static {
//        //initialize Profiles
//        ContextLoader.loadContext();
//    }

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 从配置中读取的日志路径前缀
    private String logPathPrefix;

    // 从配置中读取的资源限制
    private ai.config.pojo.DiscriminativeModelsConfig.K8sConfig.Resources resourcesConfig;

    // 从配置中读取的自定义卷挂载和卷配置
    private List<ai.config.pojo.DiscriminativeModelsConfig.K8sConfig.VolumeMount> customVolumeMounts;
    private List<ai.config.pojo.DiscriminativeModelsConfig.K8sConfig.Volume> customVolumes;

    // 用于异步执行的线程池
    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    // 用于轮询Job状态的定时任务执行器
    private static final ScheduledExecutorService jobStatusPollingExecutor = 
            Executors.newScheduledThreadPool(10);
    
    // 存储任务ID到轮询任务的映射 (taskId -> ScheduledFuture)
    private static final Map<String, ScheduledFuture<?>> POLLING_TASKS = new ConcurrentHashMap<>();
    
    // 轮询间隔（秒）
    private static final int POLL_INTERVAL_SECONDS = 15;
    
    // 首次轮询延迟（秒），给Job一些时间创建Pod
    private static final int INITIAL_DELAY_SECONDS = 3;

    // 数据库连接池适配器（已废弃，使用全局共享的 MysqlAdapterManager）
    @Deprecated
    @Getter
    private static volatile MysqlAdapter mysqlAdapter = MysqlAdapter.getInstance();

    // 模型配置管理器（单例模式）
    private static volatile ModelConfigManager modelConfigManager = null;

    /**
     * 默认构造函数
     */
    public YoloK8sAdapter() {
        super();
        loadConfigFromYaml();
    }



    /**
     * 从 lagi.yml 加载 K8s + YOLO 配置
     * 只读取：model_platform.discriminative_models.k8s.cluster_config 和 yolo.*
     */
    private void loadConfigFromYaml() {
        try {
            ContextLoader.loadContext();
            if (ContextLoader.configuration == null
                    || ContextLoader.configuration.getModelPlatformConfig() == null
                    || ContextLoader.configuration.getModelPlatformConfig().getDiscriminativeModelsConfig() == null) {
                log.warn("判别式模型配置(discriminative_models)不存在，无法加载 K8s/YOLO 配置");
                return;
            }

            DiscriminativeModelsConfig dm =
                    ContextLoader.configuration.getModelPlatformConfig().getDiscriminativeModelsConfig();

            // 1) K8s 集群配置：model_platform.discriminative_models.k8s.cluster_config
            if (dm.getK8s() != null && dm.getK8s().getClusterConfig() != null) {
                DiscriminativeModelsConfig.K8sConfig.ClusterConfig cluster = dm.getK8s().getClusterConfig();
                if (StrUtil.isNotBlank(cluster.getApiServer())) {
                    this.apiServer = cluster.getApiServer();
                }
                if (StrUtil.isNotBlank(cluster.getToken())) {
                    this.token = cluster.getToken();
                }
                if (StrUtil.isNotBlank(cluster.getNamespace())) {
                    this.namespace = cluster.getNamespace();
                }
                if (cluster.getVerifyTls() != null) {
                    // verifyTls = true 表示严格校验证书，这里直接映射到 trustCerts/disableHostnameVerification
                    this.trustCerts = cluster.getVerifyTls();
                }
                // 读取私有镜像仓库配置
                if (StrUtil.isNotBlank(cluster.getRegistryUrl())) {
                    this.registryUrl = cluster.getRegistryUrl();
                }
                if (StrUtil.isNotBlank(cluster.getRegistryUsername())) {
                    this.registryUsername = cluster.getRegistryUsername();
                }
                if (StrUtil.isNotBlank(cluster.getRegistryPassword())) {
                    this.registryPassword = cluster.getRegistryPassword();
                }
            }

            // 2) 根据 execution_mode 决定从哪个配置读取镜像
            String executionMode = dm.getExecutionMode();
            if ("k8s".equalsIgnoreCase(executionMode)) {
                // K8s 模式：从 k8s.yolo.k8s_config.dockerImage 读取镜像
                if (dm.getK8s() != null && dm.getK8s().getYolo() != null
                        && dm.getK8s().getYolo().getK8sConfig() != null) {
                    DiscriminativeModelsConfig.K8sConfig.YoloK8sConfig.YoloK8sPodConfig k8sConfig =
                            dm.getK8s().getYolo().getK8sConfig();
                    if (StrUtil.isNotBlank(k8sConfig.getDockerImage())) {
                        this.dockerImage = k8sConfig.getDockerImage();
                        super.setDockerImage(this.dockerImage);
                        log.info("K8s模式：从 k8s.yolo.k8s_config.dockerImage 读取镜像: {}", this.dockerImage);
                    }
                    // 读取镜像拉取密钥名称
                    if (StrUtil.isNotBlank(k8sConfig.getImagePullSecret())) {
                        this.imagePullSecret = k8sConfig.getImagePullSecret();
                        log.info("K8s模式：从 k8s.yolo.k8s_config.imagePullSecret 读取密钥名称: {}", this.imagePullSecret);
                    }
                }
                // 读取YOLO的重启策略
                if (dm.getK8s() != null && dm.getK8s().getYolo() != null
                        && StrUtil.isNotBlank(dm.getK8s().getYolo().getRestartPolicy())) {
                    this.restartPolicy = dm.getK8s().getYolo().getRestartPolicy();
                    log.info("K8s模式：从 k8s.yolo.restart_policy 读取重启策略: {}", this.restartPolicy);
                }
                // 读取YOLO的资源配置
                if (dm.getK8s() != null && dm.getK8s().getYolo() != null
                        && dm.getK8s().getYolo().getResources() != null) {
                    this.resourcesConfig = dm.getK8s().getYolo().getResources();
                    log.info("K8s模式：从 k8s.yolo.resources 读取资源配置");
                }
                // 读取YOLO的自定义卷挂载配置
                if (dm.getK8s() != null && dm.getK8s().getYolo() != null
                        && dm.getK8s().getYolo().getK8sConfig() != null
                        && dm.getK8s().getYolo().getK8sConfig().getVolumeMounts() != null) {
                    this.customVolumeMounts = dm.getK8s().getYolo().getK8sConfig().getVolumeMounts();
                    log.info("K8s模式：从 k8s.yolo.k8s_config.volumeMounts 读取卷挂载配置");
                }
                // 读取YOLO的自定义卷配置
                if (dm.getK8s() != null && dm.getK8s().getYolo() != null
                        && dm.getK8s().getYolo().getVolumes() != null) {
                    this.customVolumes = dm.getK8s().getYolo().getVolumes();
                    log.info("K8s模式：从 k8s.yolo.volumes 读取卷配置");
                }
            } else {
                // Docker 模式：从 yolo.docker.image 读取镜像（兼容旧配置）
                if (dm.getYolo() != null) {
                    // 2.1 docker 节点（镜像 + volume_mount + 日志前缀）
                    if (dm.getYolo().getDocker() != null) {
                        DiscriminativeModelsConfig.DockerConfig docker = dm.getYolo().getDocker();
                        if (StrUtil.isNotBlank(docker.getImage())) {
                            this.dockerImage = docker.getImage();
                            super.setDockerImage(this.dockerImage);
                            log.info("Docker模式：从 yolo.docker.image 读取镜像: {}", this.dockerImage);
                        }
                        if (StrUtil.isNotBlank(docker.getVolumeMount())) {
                            this.volumeMount = docker.getVolumeMount();
                            super.setVolumeMount(this.volumeMount);
                        }
                        if (StrUtil.isNotBlank(docker.getLogPathPrefix())) {
                            this.logPathPrefix = docker.getLogPathPrefix();
                        }
                        if (StrUtil.isNotBlank(docker.getShmSize())) {
                            this.shmSize = docker.getShmSize();
                        }
                    }
                }
            }

            // 3) 如果 K8s 模式下也需要读取 volume_mount 等配置，可以从 yolo.docker 读取（作为fallback）
            if ("k8s".equalsIgnoreCase(executionMode) && dm.getYolo() != null
                    && dm.getYolo().getDocker() != null) {
                DiscriminativeModelsConfig.DockerConfig docker = dm.getYolo().getDocker();
                if (StrUtil.isBlank(this.volumeMount) && StrUtil.isNotBlank(docker.getVolumeMount())) {
                    this.volumeMount = docker.getVolumeMount();
                    super.setVolumeMount(this.volumeMount);
                }
                if (StrUtil.isBlank(this.logPathPrefix) && StrUtil.isNotBlank(docker.getLogPathPrefix())) {
                    this.logPathPrefix = docker.getLogPathPrefix();
                }
                if (StrUtil.isBlank(this.shmSize) && StrUtil.isNotBlank(docker.getShmSize())) {
                    this.shmSize = docker.getShmSize();
                }
            }

            log.info("已从 model_platform.discriminative_models 加载 K8s/YOLO 配置");
            log.info("  配置摘要: apiServer={}, namespace={}, image={}, volumeMount={}",
                    this.apiServer, this.namespace, this.dockerImage, this.volumeMount);
            log.info("  GPU节点选择器: {}={}",
                    this.gpuNodeSelectorKey != null ? this.gpuNodeSelectorKey : "未配置",
                    this.gpuNodeSelectorValue != null ? this.gpuNodeSelectorValue : "未配置");

            // 验证关键配置
            if (this.dockerImage == null || this.dockerImage.isEmpty()) {
                log.warn("  ⚠ 警告: Docker镜像未配置");
            }
            if (this.apiServer == null || this.apiServer.isEmpty()) {
                log.warn("  ⚠ 警告: K8s API Server未配置");
            }
        } catch (Exception e) {
            log.error("加载 lagi.yml 中的 K8s/YOLO 配置失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 启动训练任务
     */
    @Override
    public String startTraining(String taskId, String trackId, JSONObject config) {
        log.info("========== 启动YOLO训练任务 ==========");
        log.info("任务ID: {}, 跟踪ID: {}", taskId, trackId);
        try {
            // 确保 K8s 客户端已初始化
            log.info("[1/7] 初始化K8s客户端...");
            initK8sClient();
            log.info("[1/7] ✓ K8s客户端初始化完成");

            // 确保配置中包含必要的字段
            log.info("[2/7] 验证训练配置...");
            if (!config.containsKey("the_train_type")) {
                config.put("the_train_type", "train");
            }
            if (!config.containsKey("task_id")) {
                config.put("task_id", taskId);
            }
            if (!config.containsKey("track_id")) {
                config.put("track_id", trackId);
            }
            log.info("[2/7] ✓ 配置验证完成");

            // 生成日志文件路径
            log.info("[3/7] 生成日志文件路径...");
            if (!config.containsKey("train_log_file") || config.getStr("train_log_file") == null || config.getStr("train_log_file").isEmpty()) {
                String containerDataPath = "/app/data";
                if (volumeMount != null && volumeMount.contains(":")) {
                    String[] parts = volumeMount.split(":");
                    if (parts.length >= 2) {
                        containerDataPath = parts[1];
                    }
                }
                String trainLogFile = containerDataPath + "/log/train/" + taskId + ".log";
                config.put("train_log_file", trainLogFile);
                log.info("  生成日志路径: {}", trainLogFile);
            }
            log.info("[3/7] ✓ 日志路径生成完成");

            // 生成 Job 名称（对应原来的容器名称）
            log.info("[4/7] 生成Job名称...");
            String jobName = generateContainerName("yolo_train");
            jobName = jobName.replace("_", "-");
            log.info("  Job名称: {}", jobName);
            log.info("[4/7] ✓ Job名称生成完成");

            // 构建配置 JSON
            log.info("[5/7] 构建配置JSON...");
            String configJson = config.toString();
            log.info("[5/7] ✓ 配置JSON构建完成");

            // 根据 device 配置判断是否启用 GPU
            log.info("[6/7] 检查GPU配置...");
            String device = config.getStr("device", "cpu");
            boolean useGpu = device != null && !device.equalsIgnoreCase("cpu") && !device.isEmpty();
            log.info("  device={}, useGpu={}", device, useGpu);
            if (useGpu) {
                log.info("  GPU节点选择器: {}={}", gpuNodeSelectorKey, gpuNodeSelectorValue);
            }
            log.info("[6/7] ✓ GPU配置检查完成");

            // 创建 Kubernetes Job
            log.info("[7/7] 创建K8s Job...");
            log.info("  Job名称: {}", jobName);
            log.info("  镜像: {}", dockerImage);
            log.info("  命名空间: {}", namespace);
            if (dockerImage == null || dockerImage.isEmpty()) {
                log.error("  ✗ 错误: dockerImage为空，无法创建Job");
                throw new IllegalArgumentException("dockerImage不能为空，请检查配置文件中的镜像配置");
            }

            JSONObject jobResult = createOneOffJob(jobName, dockerImage, configJson, useGpu, null, resourcesConfig);
            String jobStatus = jobResult.getStr("status");
            if ("success".equals(jobStatus)) {
                log.info("  ✓ Job创建成功");
            } else {
                log.error("  ✗ Job创建失败: {}", jobResult.getStr("message"));
                if (jobResult.containsKey("httpCode")) {
                    log.error("  HTTP状态码: {}", jobResult.getInt("httpCode"));
                }
                throw new RuntimeException("创建K8s Job失败: " + jobResult.getStr("message"));
            }
            log.info("[7/7] ✓ Job创建完成");

            // 保存任务到数据库（保留原有逻辑）
            log.info("保存任务信息到数据库...");
            String datasetPath = (String)config.get("data");
            if (datasetPath != null && !datasetPath.isEmpty()){
                String sql = "SELECT dataset_name FROM dataset_records WHERE dataset_path = ?";
                List<Map<String, Object>> datasetList = getMysqlAdapter().select(sql, datasetPath);
                if (datasetList != null && !datasetList.isEmpty()) {
                    String datasetName = (String)datasetList.get(0).get("dataset_name");
                    config.put("dataset_name", datasetName);
                }
            }

            // 注意：containerName 改为 jobName，containerId 也使用 jobName
            saveStartTrainingToDB(taskId, trackId, jobName, config);
            addYoloTrainingLog(taskId, "INFO", "YOLO训练任务已启动，Job名称: " + jobName);
            log.info("✓ 任务信息已保存到数据库");

            // 启动Job状态轮询，确保状态变化能同步到数据库
            startJobStatusPolling(taskId, jobName);
            log.info("✓ Job状态轮询已启动: taskId={}, jobName={}", taskId, jobName);

            // 构建返回结果
            log.info("========== YOLO训练任务启动成功 ==========");
            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("message", "训练任务已启动");
            result.put("containerName", jobName);
            result.put("containerId", jobName);  // K8s中Job名称即容器标识
            result.put("jobName", jobName);
            result.put("namespace", namespace);

            return result.toString();

        } catch (Exception e) {
            log.error("========== YOLO训练任务启动失败 ==========");
            log.error("任务ID: {}", taskId);
            log.error("错误类型: {}", e.getClass().getSimpleName());
            log.error("错误消息: {}", e.getMessage());
            if (e.getCause() != null) {
                log.error("根本原因: {}", e.getCause().getMessage());
            }
            log.error("错误堆栈: ", e);

            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("message", "启动训练任务失败");
            errorResult.put("error", e.getMessage());
            errorResult.put("errorType", e.getClass().getSimpleName());
            return errorResult.toString();
        }
    }

    /**
     * 便捷方法：使用默认配置启动训练
     */
    public String startTraining(String taskId, String trackId) {
        JSONObject config = createDefaultTrainConfig();
        config.put("task_id", taskId);
        config.put("track_id", trackId);
        return startTraining(taskId, trackId, config);
    }

    /**
     * 执行评估任务
     */
    @Override
    public String evaluate(JSONObject config) {
        String taskId = UUID.randomUUID().toString();
        try {
            // 确保配置中包含必要的字段
            if (!config.containsKey("the_train_type")) {
                config.put("the_train_type", "valuate");
            }
            config.put("task_id", taskId);

            // 保存评估任务到数据库
            saveEvaluateTaskToDB(taskId, config);
            addYoloTrainingLog(taskId, "INFO", "开始执行评估任务");

            String jobName = generateContainerName("yolo_eval");
            jobName = jobName.replace("_", "-");
            // 根据 device 配置判断是否启用 GPU
            String device = config.getStr("device", "cpu");
            boolean useGpu = device != null && !device.equalsIgnoreCase("cpu") && !device.isEmpty();
            String result = createOneOffJob(jobName, dockerImage, config.toString(), useGpu, null, resourcesConfig).toString();

            // 更新评估任务状态
            if (isSuccess(result)) {
                updateYoloTaskStatus(taskId, "completed", "评估任务完成");
                addYoloTrainingLog(taskId, "INFO", "评估任务完成");
            } else {
                updateYoloTaskStatus(taskId, "failed", "评估任务失败");
                addYoloTrainingLog(taskId, "ERROR", "评估任务失败: " + result);
            }

            return result;

        } catch (Exception e) {
            log.error("执行评估任务失败", e);
            updateYoloTaskStatus(taskId, "failed", "评估任务异常: " + e.getMessage());
            addYoloTrainingLog(taskId, "ERROR", "评估任务异常: " + e.getMessage());

            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("message", "执行评估任务失败");
            errorResult.put("error", e.getMessage());
            return errorResult.toString();
        }
    }

    /**
     * 便捷方法：使用默认配置执行评估
     */
    public String evaluate() {
        JSONObject config = createDefaultEvaluateConfig();
        return evaluate(config);
    }

    /**
     * 执行预测任务
     */
    @Override
    public String predict(JSONObject config) {
        String taskId = config.getStr("task_id");
        try {
            // 确保配置中包含必要的字段
            if (!config.containsKey("the_train_type")) {
                config.put("the_train_type", "predict");
            }
            if (!config.containsKey("task_id")) {
                config.put("task_id", taskId);
            }

            // 如果没有指定推理日志文件路径，自动生成到指定目录
            if (!config.containsKey("train_log_file") || config.getStr("train_log_file") == null || config.getStr("train_log_file").isEmpty()) {
                // 从volumeMount中解析容器内路径，或使用默认值
                String containerDataPath = "/app/data";
                if (volumeMount != null && volumeMount.contains(":")) {
                    String[] parts = volumeMount.split(":");
                    if (parts.length >= 2) {
                        containerDataPath = parts[1];
                    }
                }
                String predictLogFile = containerDataPath + "/log/predict/" + taskId + ".log";
                config.put("train_log_file", predictLogFile);
            }

            // 保存预测任务到数据库（初始状态为pending）
            savePredictTaskToDB(taskId, config);
            addYoloPredictLog(taskId, "INFO", "预测任务已创建，等待执行");

            String predictLogFile = config.getStr("train_log_file");

            String jobName = generateContainerName("yolo_predict");
            jobName = jobName.replace("_", "-");
            // 根据 device 配置判断是否启用 GPU
            String device = config.getStr("device", "cpu");
            boolean useGpu = device != null && !device.equalsIgnoreCase("cpu") && !device.isEmpty();
            String result = createOneOffJob(jobName, dockerImage, config.toString(), useGpu, null, resourcesConfig).toString();

            // 获取推理日志文件路径（宿主机路径），用于记录到数据库
            String hostLogFilePath = null;
            if (predictLogFile != null && volumeMount != null && volumeMount.contains(":")) {
                String[] mountParts = volumeMount.split(":");
                if (mountParts.length >= 2) {
                    String containerPath = mountParts[1];
                    String hostPath = mountParts[0];
                    if (predictLogFile.startsWith(containerPath)) {
                        hostLogFilePath = predictLogFile.replace(containerPath, hostPath);
                    }
                }
            }

            // 更新预测任务状态
            if (isSuccess(result)) {
                // Job创建成功，更新状态为running（Job实际执行是异步的，但此时Job已提交到K8s）
                updateYoloTaskStatus(taskId, "running", "预测任务已提交到K8s，等待执行");
                addYoloPredictLog(taskId, "INFO", "预测任务已提交到K8s，Job名称: " + jobName, hostLogFilePath);
                // 注意：Job的实际执行完成状态需要通过监控Job状态来更新，这里不直接设置为completed
            } else {
                updateYoloTaskStatus(taskId, "failed", "创建预测任务失败: " + result);
                addYoloPredictLog(taskId, "ERROR", "创建预测任务失败: " + result, hostLogFilePath);
            }

            return result;

        } catch (Exception e) {
            log.error("执行预测任务失败", e);
            updateYoloTaskStatus(taskId, "failed", "预测任务异常: " + e.getMessage());
            addYoloPredictLog(taskId, "ERROR", "预测任务异常: " + e.getMessage(), null);

            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("message", "执行预测任务失败");
            errorResult.put("error", e.getMessage());
            return errorResult.toString();
        }
    }

    private void updateDeeplabTaskProgress(String taskId, String progress) {
        String updateSql = "UPDATE ai_training_tasks SET progress = ?, end_time = ? WHERE task_id = ?";
        try {
            String currentTime = getCurrentTime();
            getMysqlAdapter().executeUpdate(updateSql, progress, currentTime, taskId);
            log.info("任务进度已更新: taskId={}, progress={}", taskId, progress);
        } catch (Exception e) {
            log.error("更新任务进度失败: taskId={}, progress={}", taskId, progress, e.getMessage(), e);
        }
    }

    /**
     * 便捷方法：使用默认配置执行预测
     */
    public String predict() {
        JSONObject config = createDefaultPredictConfig();
        return predict(config);
    }

    /**
     * 导出模型
     */
    @Override
    public String exportModel(JSONObject config) {
        return convert(config);
    }

    /**
     * 便捷方法：使用默认配置导出模型
     */
    public String exportModel() {
        JSONObject config = createDefaultExportConfig();
        return exportModel(config);
    }

    /**
     * 模型格式转换（K8s版本）
     * 对应 Docker 版本的 convert 方法
     */
    public String convert(JSONObject parameters) {
        String taskId = UUID.randomUUID().toString();
        try {
            // 按约定将 convert 视为 YOLO 导出（export）
            parameters.put("the_train_type", "export");
            parameters.put("task_id", taskId);

            // 验证模型文件是否存在
            String hostModelPath = parameters.getStr("model_path");
            if (hostModelPath == null || hostModelPath.isEmpty()) {
                throw new RuntimeException("模型路径不能为空");
            }
            Path path = Paths.get(hostModelPath);
            boolean exists = path.toFile().exists();
            if (!exists) {
                throw new RuntimeException("模型文件不存在: " + hostModelPath);
            }

            // 取父目录的完整路径，用于构建额外的 volume 挂载
            Path modelDir = path.getParent();
            String modelDirPath = modelDir.toAbsolutePath().toString();
            log.info("模型目录: {}", modelDirPath);

            // 确保配置中包含必要的字段
            parameters.putIfAbsent("model_path", hostModelPath);
            parameters.putIfAbsent("export_format", "onnx");

            // 构建额外信息
            JSONObject extraInfo = new JSONObject();
            Long hostFileSize = ai.finetune.utils.PathConvertUtil.getFileSize(hostModelPath);
            extraInfo.put("model_file_size", hostFileSize);
            extraInfo.put("export_format", parameters.getStr("export_format"));

            // 如果没有指定转换日志文件路径，自动生成到指定目录
            if (!parameters.containsKey("train_log_file") || parameters.getStr("train_log_file") == null || parameters.getStr("train_log_file").isEmpty()) {
                // 从volumeMount中解析容器内路径，或使用默认值
                String containerDataPath = "/app/data";
                if (volumeMount != null && volumeMount.contains(":")) {
                    String[] parts = volumeMount.split(":");
                    if (parts.length >= 2) {
                        containerDataPath = parts[1];
                    }
                }
                String convertLogFile = containerDataPath + "/log/convert/" + taskId + ".log";
                parameters.put("train_log_file", convertLogFile);
            }

            // 保存转换任务到数据库（带 remark）
            saveExportTaskToDB(taskId, parameters, extraInfo.toString());
            addYoloConvertLog(taskId, "INFO", "开始模型转换");

            // 生成 Job 名称
            String jobName = generateContainerName("yolo_convert");
            jobName = jobName.replace("_", "-");
            log.info("转换任务Job名称: {}", jobName);

            // 根据 device 配置判断是否启用 GPU
            String device = parameters.getStr("device", "cpu");
            boolean useGpu = device != null && !device.equalsIgnoreCase("cpu") && !device.isEmpty();

            // 构建配置 JSON
            String configJson = parameters.toString();

            // 创建 K8s Job，需要添加额外的 volume 挂载（模型目录）
            // 保存原始的 customVolumes 和 customVolumeMounts
            List<ai.config.pojo.DiscriminativeModelsConfig.K8sConfig.Volume> originalVolumes = this.customVolumes;
            List<ai.config.pojo.DiscriminativeModelsConfig.K8sConfig.VolumeMount> originalVolumeMounts = this.customVolumeMounts;

            try {
                // 创建额外的 volume 和 volumeMount 用于模型目录
                List<ai.config.pojo.DiscriminativeModelsConfig.K8sConfig.Volume> volumesWithModel = new ArrayList<>();
                List<ai.config.pojo.DiscriminativeModelsConfig.K8sConfig.VolumeMount> volumeMountsWithModel = new ArrayList<>();

                // 如果有原始的自定义 volumes，先添加它们
                if (originalVolumes != null && !originalVolumes.isEmpty()) {
                    volumesWithModel.addAll(originalVolumes);
                }
                if (originalVolumeMounts != null && !originalVolumeMounts.isEmpty()) {
                    volumeMountsWithModel.addAll(originalVolumeMounts);
                }

                // 添加模型目录的 volume
                ai.config.pojo.DiscriminativeModelsConfig.K8sConfig.Volume modelVolume = 
                    new ai.config.pojo.DiscriminativeModelsConfig.K8sConfig.Volume();
                modelVolume.setName("model-volume");
                ai.config.pojo.DiscriminativeModelsConfig.K8sConfig.HostPath modelHostPathConfig = 
                    new ai.config.pojo.DiscriminativeModelsConfig.K8sConfig.HostPath();
                modelHostPathConfig.setPath(modelDirPath);
                modelHostPathConfig.setType("DirectoryOrCreate");
                modelVolume.setHostPath(modelHostPathConfig);
                volumesWithModel.add(modelVolume);

                // 添加模型目录的 volumeMount
                ai.config.pojo.DiscriminativeModelsConfig.K8sConfig.VolumeMount modelVolumeMount = 
                    new ai.config.pojo.DiscriminativeModelsConfig.K8sConfig.VolumeMount();
                modelVolumeMount.setName("model-volume");
                modelVolumeMount.setMountPath(modelDirPath);
                volumeMountsWithModel.add(modelVolumeMount);

                // 临时设置 customVolumes 和 customVolumeMounts
                this.customVolumes = volumesWithModel;
                this.customVolumeMounts = volumeMountsWithModel;

                log.info("创建K8s Job，包含模型目录挂载: modelDir={}", modelDirPath);
                addYoloConvertLog(taskId, "INFO", "开始创建K8s Job");

                // 创建 K8s Job
                JSONObject jobResult = createOneOffJob(jobName, dockerImage, configJson, useGpu, null, resourcesConfig);
                String jobStatus = jobResult.getStr("status");

                if ("success".equals(jobStatus)) {
                    log.info("✓ Job创建成功: {}", jobName);
                    addYoloConvertLog(taskId, "INFO", "K8s Job创建成功: " + jobName);

                    // 启动Job状态轮询
                    startJobStatusPolling(taskId, jobName);
                    log.info("✓ Job状态轮询已启动: taskId={}, jobName={}", taskId, jobName);

                    // 获取转换日志文件路径（宿主机路径），用于记录到数据库
                    String convertLogFile = parameters.getStr("train_log_file");
                    String hostLogFilePath = null;
                    if (convertLogFile != null && volumeMount != null && volumeMount.contains(":")) {
                        String[] mountParts = volumeMount.split(":");
                        if (mountParts.length >= 2) {
                            String containerPath = mountParts[1];
                            String hostMountPath = mountParts[0];
                            if (convertLogFile.startsWith(containerPath)) {
                                hostLogFilePath = convertLogFile.replace(containerPath, hostMountPath);
                            }
                        }
                    }
                    // 记录日志文件路径
                    if (hostLogFilePath != null) {
                        addYoloConvertLog(taskId, "INFO", "转换日志文件路径: " + hostLogFilePath, hostLogFilePath);
                    }

                    // 构建返回结果
                    JSONObject resultJson = new JSONObject();
                    resultJson.put("status", "success");
                    resultJson.put("message", "模型转换任务已提交到K8s");
                    resultJson.put("taskId", taskId);
                    resultJson.put("jobName", jobName);
                    resultJson.put("namespace", namespace);
                    resultJson.put("containerName", jobName);
                    resultJson.put("containerId", jobName);

                    // 注意：Job的实际执行是异步的，转换结果需要通过轮询获取
                    // 这里只返回Job创建成功的结果
                    return resultJson.toString();

                } else {
                    log.error("✗ Job创建失败: {}", jobResult.getStr("message"));
                    String errorMsg = "创建K8s Job失败: " + jobResult.getStr("message");
                    updateYoloTaskStatus(taskId, "failed", errorMsg);
                    addYoloConvertLog(taskId, "ERROR", errorMsg);

                    JSONObject errorResult = new JSONObject();
                    errorResult.put("status", "error");
                    errorResult.put("message", "创建K8s Job失败");
                    errorResult.put("error", jobResult.getStr("message"));
                    errorResult.put("taskId", taskId);
                    return errorResult.toString();
                }

            } finally {
                // 恢复原始的 customVolumes 和 customVolumeMounts
                this.customVolumes = originalVolumes;
                this.customVolumeMounts = originalVolumeMounts;
            }

        } catch (Exception e) {
            log.error("模型转换异常: taskId={}", taskId, e);
            updateYoloTaskStatus(taskId, "failed", "模型转换异常: " + e.getMessage());
            addYoloConvertLog(taskId, "ERROR", "模型转换异常: " + e.getMessage());

            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("message", "模型转换失败");
            errorResult.put("error", e.getMessage());
            errorResult.put("taskId", taskId);
            return errorResult.toString();
        }
    }

    /**
     * 暂停容器（带业务逻辑）
     */
    public String pauseContainer(String containerId) {
        JSONObject res = new JSONObject();
        res.put("status", "error");
        res.put("message", "K8s Job 不支持暂停/恢复，请使用停止或删除");

        // 从containerId获取taskId
        String taskId = getTaskIdByContainerId(containerId);

        if (taskId != null) {
            updateYoloTaskStatus(taskId, "paused", "K8s Job 不支持暂停，建议停止后重启");
            addYoloTrainingLog(taskId, "WARN", "K8s Job 不支持暂停: " + containerId);
        }

        return res.toString();
    }

    /**
     * 继续容器（恢复暂停的容器，带业务逻辑）
     */
    public String resumeContainer(String containerId) {
        JSONObject res = new JSONObject();
        res.put("status", "error");
        res.put("message", "K8s Job 不支持暂停/恢复，请重新提交任务");

        // 从containerId获取taskId
        String taskId = getTaskIdByContainerId(containerId);

        if (taskId != null) {
            addYoloTrainingLog(taskId, "WARN", "K8s Job 不支持恢复: " + containerId);
        }

        return res.toString();
    }

    /**
     * 停止容器（带业务逻辑）
     */
    public String stopContainer(String containerId) {
        String result = deleteJob(containerId).toString();

        // 从containerId获取taskId
        String taskId = getTaskIdByContainerId(containerId);

        if (isSuccess(result)) {
            // 更新数据库状态为已停止，并记录结束时间
            if (taskId != null) {
                String endTime = getCurrentTime();
                updateYoloTaskStopStatus(taskId, endTime);
                addYoloTrainingLog(taskId, "INFO", "容器已停止: " + containerId);
            }
        } else {
            // 停止失败，记录日志
            if (taskId != null) {
                addYoloTrainingLog(taskId, "ERROR", "停止容器失败: " + result);
            }
        }

        return result;
    }

    /**
     * 删除容器（带业务逻辑）
     */
    public String removeContainer(String containerId) {
        String result = deleteJob(containerId).toString();

        // 从containerId获取taskId
        String taskId = getTaskIdByContainerId(containerId);

        if (isSuccess(result)) {
            // 软删除数据库记录
            if (taskId != null) {
                deleteYoloTask(taskId);
                addYoloTrainingLog(taskId, "INFO", "容器已删除: " + containerId);
            }
        } else {
            // 删除失败，记录日志
            if (taskId != null) {
                addYoloTrainingLog(taskId, "ERROR", "删除容器失败: " + result);
            }
        }

        return result;
    }
    public String removeContainer(String containerId,String taskId) {
        if (taskId != null) {
            deleteYoloTask(taskId);
            addYoloTrainingLog(taskId, "INFO", "容器已删除: " + containerId);
        }
        JSONObject resultJson = new JSONObject();
        resultJson.put("status", "success");
        resultJson.put("message", "远程任务执行成功");
        String containerName = getContainerNameByTaskId(taskId);
        deleteJob(containerName);
        return resultJson.toString();
    }

    /**
     * 查看容器状态（带业务逻辑）
     */
    public String getContainerStatus(String containerId) {
        JSONObject jobStatus = getJobStatus(containerId);
        if (isSuccess(jobStatus.toString())) {
            String taskId = getTaskIdByContainerId(containerId);
            String phase = jobStatus.getStr("jobPhase", "Unknown");
            if (taskId != null) {
                if ("Failed".equalsIgnoreCase(phase)) {
                    updateYoloTaskStatus(taskId, "failed", "Job 失败");
                } else if ("Complete".equalsIgnoreCase(phase) || "Succeeded".equalsIgnoreCase(phase)) {
                    updateYoloTaskStatus(taskId, "completed", "Job 完成");
                }
            }

            // 生成 containerStatus 字段，根据优先级：containerState -> podPhase -> jobPhase
            String containerStatus = null;
            String containerState = jobStatus.getStr("containerState");
            String podPhase = jobStatus.getStr("podPhase");
            String jobPhase = jobStatus.getStr("jobPhase");
            Integer exitCode = jobStatus.getInt("containerExitCode");

            if (containerState != null) {
                // 根据 containerState 映射到 containerStatus
                if ("Running".equals(containerState)) {
                    containerStatus = "running";
                } else if ("Terminated".equals(containerState)) {
                    if (exitCode != null && exitCode != 0) {
                        containerStatus = "exited";
                    } else {
                        containerStatus = "exited";
                    }
                } else if ("Waiting".equals(containerState)) {
                    containerStatus = "waiting";
                }
            } else if (podPhase != null) {
                // 根据 podPhase 映射
                if ("Running".equalsIgnoreCase(podPhase)) {
                    containerStatus = "running";
                } else if ("Succeeded".equalsIgnoreCase(podPhase)) {
                    containerStatus = "exited";
                } else if ("Failed".equalsIgnoreCase(podPhase)) {
                    containerStatus = "exited";
                } else if ("Pending".equalsIgnoreCase(podPhase)) {
                    containerStatus = "waiting";
                }
            } else if (jobPhase != null) {
                // 根据 jobPhase 映射
                if ("Complete".equalsIgnoreCase(jobPhase) || "Succeeded".equalsIgnoreCase(jobPhase)) {
                    containerStatus = "exited";
                } else if ("Failed".equalsIgnoreCase(jobPhase)) {
                    containerStatus = "exited";
                }
            }

            // 如果还是没有状态，使用默认值
            if (containerStatus == null || containerStatus.isEmpty()) {
                containerStatus = "unknown";
            }

            // 设置 containerStatus 字段
            jobStatus.put("containerStatus", containerStatus);

            // 生成 output 字段，格式：状态;退出码（与 Docker 实现保持一致）
            String output = containerStatus;
            if (exitCode != null) {
                output = containerStatus + ";" + exitCode;
            } else {
                output = containerStatus + ";0";
            }
            jobStatus.put("output", output);
        }
        return jobStatus.toString();
    }





    // ==================== 日志流式获取方法 ====================

    /**
     * 流式获取容器日志（基于 ObservableList）
     * @param containerId 容器ID或容器名称
     * @return ObservableList 流式日志输出
     */
    public ObservableList<String> getContainerLogsStream(String containerId) {
        ObservableList<String> logStream = new ObservableList<>();

        executorService.submit(() -> {
            try {
                log.info("开始流式获取容器日志: {}", containerId);
                streamJobLogs(containerId, logStream::add);

            } catch (Exception e) {
                log.error("流式获取容器日志失败: {}", containerId, e);
                logStream.add("Error: " + e.getMessage());
            } finally {
                logStream.onComplete();
            }
        });

        return logStream;
    }

    // ==================== 配置创建方法（业务配置） ====================

    /**
     * 创建默认训练配置
     */
    public JSONObject createDefaultTrainConfig() {
        JSONObject config = new JSONObject();
        config.put("the_train_type", "train");
        // 默认训练日志文件路径（会在启动训练时根据 taskId 自动设置）
        config.put("train_log_file", "/app/data/log/train/train.log");
        config.put("model_path", "/app/data/models/yolo11n.pt");
        config.put("data", "/app/data/datasets/YoloV8/data.yaml");
        config.put("epochs", 1);
        config.put("batch", 1);
        config.put("imgsz", 640);
        config.put("device", "0");
        config.put("exist_ok", true);
        config.put("project", "/app/data/project");
        config.put("runs_dir", "/app/data");
        config.put("name", "yolo_experiment_" + System.currentTimeMillis());
        return config;
    }

    /**
     * 创建默认评估配置
     */
    public JSONObject createDefaultEvaluateConfig() {
        JSONObject config = new JSONObject();
        config.put("the_train_type", "valuate");
        config.put("model_path", "/app/data/models/yolo11n.pt");
        config.put("data", "/app/data/datasets/YoloV8/data.yaml");
        config.put("imgsz", 640);
        config.put("device", "cpu");
        return config;
    }

    /**
     * 创建默认预测配置
     */
    public JSONObject createDefaultPredictConfig() {
        JSONObject config = new JSONObject();
        config.put("the_train_type", "predict");
        config.put("model_path", "/app/data/models/yolo11n.pt");
        config.put("source", "/app/data/test.jpg");
        config.put("conf_thres", 0.25);
        config.put("iou_thres", 0.45);
        config.put("device", "cpu");
        config.put("save", true);
        config.put("project", "/app/data/predict");
        return config;
    }

    /**
     * 创建默认导出配置
     */
    public JSONObject createDefaultExportConfig() {
        JSONObject config = new JSONObject();
        config.put("the_train_type", "export");
        config.put("model_path", "/app/data/models/yolo11n.pt");
        config.put("export_format", "onnx");
        config.put("device", "cpu");
        return config;
    }

    // ==================== 数据库操作方法（CRUD） ====================


    /**
     * 获取模型配置管理器实例（单例模式，双重检查锁定）
     * 从数据库加载模型配置，提高配置灵活性
     */
    private static ModelConfigManager getModelConfigManager() {
        if (modelConfigManager == null) {
            synchronized (YoloK8sAdapter.class) {
                if (modelConfigManager == null) {
                    modelConfigManager = new ModelConfigManager(getMysqlAdapter());
                    modelConfigManager.loadConfigsFromDatabase();
                    log.info("模型配置管理器已初始化，已加载 {} 个模型配置",
                            modelConfigManager.getAllConfigs().size());
                }
            }
        }
        return modelConfigManager;
    }


    /**
     * 任务数据传输对象 - 用于统一保存任务信息
     */
    private static class TaskDTO {
        String taskId;
        String trackId;
        String taskType;  // train, evaluate, predict, export
        String modelName;
        String modelCategory;
        String modelFramework;
        String containerName;
        String dockerImage;
        String datasetPath;
        String modelPath;
        Integer epochs;
        Integer batchSize;
        String imageSize;
        String device;
        String optimizer;
        String status;
        JSONObject config;

        public TaskDTO(String taskId, String taskType, JSONObject config) {
            this.taskId = taskId;
            this.taskType = taskType;
            this.config = config;

            // 从配置中读取基本信息
            this.modelName = config.getStr("model_name", "yolov8");
            this.trackId = config.getStr("track_id", "");
            this.containerName = config.getStr("_container_name", "");
            this.dockerImage = config.getStr("_docker_image", "");

            // 获取模型配置
            ModelConfigManager.ModelConfig modelConfig =
                    getModelConfigManager().getModelConfig(this.modelName, config);
            this.modelCategory = modelConfig.getModelCategory();
            this.modelFramework = modelConfig.getModelFramework();

            // 读取训练参数
            this.datasetPath = config.getStr("data", "");
            this.modelPath = config.getStr("model_path", "");
            this.epochs = config.getInt("epochs", null);
            this.batchSize = config.getInt("batch", null);
            this.imageSize = String.valueOf(config.getInt("imgsz", 640));
            this.device = config.getStr("device", "0");
            this.optimizer = config.getStr("optimizer", "sgd");
            this.status = config.getStr("_status", "running");
        }
    }

    /**
     * 通用保存任务方法 - 消除代码重复
     */
    private void saveTaskToDB(TaskDTO task) {
        try {
            String currentTime = getCurrentTime();

            switch (task.taskType) {
                case "train":
                    saveTrainTask(task, currentTime);
                    break;
                case "evaluate":
                    saveEvaluateTask(task, currentTime);
                    break;
                case "predict":
                    savePredictTask(task, currentTime);
                    break;
                case "export":
                    saveExportTask(task, currentTime);
                    break;
                default:
                    log.warn("未知的任务类型: {}", task.taskType);
            }

            log.info("任务已保存到数据库: taskId={}, type={}, model={}, category={}, framework={}",
                    task.taskId, task.taskType, task.modelName, task.modelCategory, task.modelFramework);
        } catch (Exception e) {
            log.error("保存任务到数据库失败: taskId={}, type={}, error={}",
                    task.taskId, task.taskType, e.getMessage(), e);
        }
    }

    /**
     * 保存训练任务
     */
    private void saveTrainTask(TaskDTO task, String currentTime) {
        String sql = "INSERT INTO ai_training_tasks " +
                "(task_id, track_id, model_name, model_category, model_framework, task_type, " +
                "container_name, container_id, docker_image, gpu_ids, use_gpu, " +
                "dataset_path, dataset_name, model_path, epochs, batch_size, image_size, optimizer, " +
                "status, progress, current_epoch, start_time, created_at, is_deleted, user_id, " +
                "model_id, dataset_id, output_path, config_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        // 从配置中读取 user_id
        String userId = task.config.getStr("user_id", null);
        String datasetName = task.config.getStr("dataset_name", "");
        
        // 从配置中读取 model_id 和 dataset_id
        Long modelId = null;
        Long datasetId = null;
        if (task.config.containsKey("original_model_id")) {
            Object modelIdObj = task.config.get("original_model_id");
            if (modelIdObj instanceof Number) {
                modelId = ((Number) modelIdObj).longValue();
            } else if (modelIdObj != null) {
                try {
                    modelId = Long.parseLong(modelIdObj.toString());
                } catch (NumberFormatException e) {
                    log.warn("无效的original_model_id: {}", modelIdObj);
                }
            }
        }
        if (task.config.containsKey("original_dataset_id")) {
            Object datasetIdObj = task.config.get("original_dataset_id");
            if (datasetIdObj instanceof Number) {
                datasetId = ((Number) datasetIdObj).longValue();
            } else if (datasetIdObj != null) {
                try {
                    datasetId = Long.parseLong(datasetIdObj.toString());
                } catch (NumberFormatException e) {
                    log.warn("无效的original_dataset_id: {}", datasetIdObj);
                }
            }
        }
        
        // 从配置中读取 output_path（project路径）
        String outputPath = task.config.getStr("project", null);
        // 如果output_path为空但model_id存在，根据model_id生成output_path
        if (outputPath == null && modelId != null) {
            ai.config.ModelStorageConfig storageConfig = ai.config.ModelStorageConfig.getInstance();
            outputPath = storageConfig.getProjectPath() + "/" + modelId;
        }

        getMysqlAdapter().executeUpdate(sql,
                task.taskId, task.trackId, task.modelName, task.modelCategory, task.modelFramework,
                "train", task.containerName, "", task.dockerImage,
                task.device, !task.device.equals("cpu") ? 1 : 0,
                task.datasetPath, datasetName, task.modelPath, task.epochs, task.batchSize, task.imageSize, task.optimizer,
                "starting", "0%", 0, currentTime, currentTime, 0, userId,
                modelId, datasetId, outputPath, task.config.toString());
    }

    /**
     * 保存评估任务
     */
    private void saveEvaluateTask(TaskDTO task, String currentTime) {
        String sql = "INSERT INTO ai_training_tasks " +
                "(task_id, track_id, model_name, model_category, model_framework, task_type, " +
                "container_name, dataset_path, model_path, image_size, optimizer, " +
                "status, progress, current_epoch, start_time, created_at, is_deleted, config_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        getMysqlAdapter().executeUpdate(sql,
                task.taskId, "", task.modelName, task.modelCategory, task.modelFramework,
                "evaluate", "", task.datasetPath, task.modelPath, task.imageSize, task.optimizer,
                task.status, "0%", 0, currentTime, currentTime, 0, task.config.toString());
    }

    /**
     * 保存预测任务
     */
    private void savePredictTask(TaskDTO task, String currentTime) {
        String sql = "INSERT INTO ai_training_tasks " +
                "(task_id, track_id, model_name, model_category, model_framework, task_type, " +
                "container_name, model_path, gpu_ids, use_gpu, " +
                "status, progress, current_epoch, start_time, created_at, is_deleted, config_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        getMysqlAdapter().executeUpdate(sql,
                task.taskId, "", task.modelName, task.modelCategory, task.modelFramework,
                "predict", "", task.modelPath, task.device, !task.device.equals("cpu") ? 1 : 0,
                task.status, "0%", 0, currentTime, currentTime, 0, task.config.toString());
    }

    /**
     * 保存导出任务
     */
    private void saveExportTask(TaskDTO task, String currentTime) {
        String sql = "INSERT INTO ai_training_tasks " +
                "(task_id, track_id, model_name, model_category, model_framework, task_type, " +
                "container_name, model_path, gpu_ids, use_gpu, " +
                "status, progress, current_epoch, start_time, created_at, is_deleted, config_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        getMysqlAdapter().executeUpdate(sql,
                task.taskId, "", task.modelName, task.modelCategory, task.modelFramework,
                "export", "", task.modelPath, task.device, !task.device.equals("cpu") ? 1 : 0,
                task.status, "0%", 0, currentTime, currentTime, 0, task.config.toString());
    }

    /**
     * 保存启动训练任务到数据库
     */
    private void saveStartTrainingToDB(String taskId, String trackId, String containerName, JSONObject config) {
        // 添加额外参数到配置中
        config.put("track_id", trackId);
        config.put("_container_name", containerName);
        config.put("_docker_image", dockerImage);
        config.put("_status", "starting");

        // 使用通用保存方法
        TaskDTO task = new TaskDTO(taskId, "train", config);
        saveTaskToDB(task);
    }

    /**
     * 保存评估任务到数据库
     */
    private void saveEvaluateTaskToDB(String taskId, JSONObject config) {
        TaskDTO task = new TaskDTO(taskId, "evaluate", config);
        saveTaskToDB(task);
    }

    /**
     * 保存预测任务到数据库
     */
    private void savePredictTaskToDB(String taskId, JSONObject config) {
        String sql = "INSERT INTO ai_training_tasks " +
                "(task_id, track_id, model_name, model_category, model_framework, task_type, " +
                "container_name, model_path, gpu_ids, use_gpu, " +
                "status, progress, current_epoch, start_time, created_at, is_deleted, config_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            // 从配置中读取模型名称，如果没有则默认为 yolov8
            String modelName = config.getStr("model_name", "yolov8");
            String modelCategory = getModelCategory(modelName, config);
            String modelFramework = getModelFramework(modelName, config);
            String modelPath = config.getStr("model_path", "");
            String device = config.getStr("device", "cpu");
            String currentTime = getCurrentTime();

            // 使用数据库连接池执行插入操作
            getMysqlAdapter().executeUpdate(
                    sql,
                    taskId,
                    "",                 // track_id
                    modelName,          // model_name - 从配置中读取
                    modelCategory,      // model_category - 动态获取
                    modelFramework,     // model_framework - 动态获取
                    "predict",          // task_type
                    "",                 // container_name
                    modelPath,
                    device,             // gpu_ids
                    !device.equals("cpu") ? 1 : 0, // use_gpu
                    "pending",
                    "0%",
                    0,
                    currentTime,
                    currentTime,
                    0,
                    config.toString()
            );
            log.info("预测任务已保存到数据库: taskId={}, modelName={}, category={}, framework={}",
                    taskId, modelName, modelCategory, modelFramework);
        } catch (Exception e) {
            log.error("保存预测任务到数据库失败: taskId={}, error={}", taskId, e.getMessage(), e);
        }
    }

    /**
     * 保存导出任务到数据库（带 remark）
     */

    private void saveExportTaskToDB(String taskId, JSONObject config, String remark) {
        String sql = "INSERT INTO ai_training_tasks " +
                "(task_id, track_id, model_name, model_category, model_framework, task_type, " +
                "container_name, model_path, gpu_ids, use_gpu, " +
                "status, progress, current_epoch, start_time, created_at, is_deleted, config_json, remark) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            // 从配置中读取模型名称，如果没有则默认为 yolov8
            String modelName = config.getStr("model_name", "yolov8");
            String modelCategory = getModelCategory(modelName, config);
            String modelFramework = getModelFramework(modelName, config);
            String modelPath = config.getStr("model_path", "");
            String device = config.getStr("device", "cpu");
            String currentTime = getCurrentTime();

            // 使用数据库连接池执行插入操作
            getMysqlAdapter().executeUpdate(
                    sql,
                    taskId,
                    "",                 // track_id
                    modelName,          // model_name - 从配置中读取
                    modelCategory,      // model_category - 动态获取
                    modelFramework,     // model_framework - 动态获取
                    "export",           // task_type
                    "",                 // container_name
                    modelPath,
                    device,             // gpu_ids
                    !device.equals("cpu") ? 1 : 0, // use_gpu
                    "running",
                    "0%",
                    0,
                    currentTime,
                    currentTime,
                    0,
                    config.toString(),
                    remark != null ? remark : ""
            );
            log.info("导出任务已保存到数据库: taskId={}, modelName={}, category={}, framework={}",
                    taskId, modelName, modelCategory, modelFramework);
        } catch (Exception e) {
            log.error("保存导出任务到数据库失败: taskId={}, error={}", taskId, e.getMessage(), e);
        }
    }
    /**
     * 保存导出任务到数据库
     */
    private void saveExportTaskToDB(String taskId, JSONObject config) {
        String sql = "INSERT INTO ai_training_tasks " +
                "(task_id, track_id, model_name, model_category, model_framework, task_type, " +
                "container_name, model_path, gpu_ids, use_gpu, " +
                "status, progress, current_epoch, start_time, created_at, is_deleted, config_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            // 从配置中读取模型名称，如果没有则默认为 yolov8
            String modelName = config.getStr("model_name", "yolov8");
            String modelCategory = getModelCategory(modelName, config);
            String modelFramework = getModelFramework(modelName, config);
            String modelPath = config.getStr("model_path", "");
            String device = config.getStr("device", "cpu");
            String currentTime = getCurrentTime();

            // 使用数据库连接池执行插入操作
            getMysqlAdapter().executeUpdate(
                    sql,
                    taskId,
                    "",                 // track_id
                    modelName,          // model_name - 从配置中读取
                    modelCategory,      // model_category - 动态获取
                    modelFramework,     // model_framework - 动态获取
                    "export",           // task_type
                    "",                 // container_name
                    modelPath,
                    device,             // gpu_ids
                    !device.equals("cpu") ? 1 : 0, // use_gpu
                    "running",
                    "0%",
                    0,
                    currentTime,
                    currentTime,
                    0,
                    config.toString()
            );
            log.info("导出任务已保存到数据库: taskId={}, modelName={}, category={}, framework={}",
                    taskId, modelName, modelCategory, modelFramework);
        } catch (Exception e) {
            log.error("保存导出任务到数据库失败: taskId={}, error={}", taskId, e.getMessage(), e);
        }
    }

    /**
     * 更新YOLO任务状态（带 exportPath 和 remark）
     */
    private void updateYoloTaskStatus(String taskId, String status, String message, String outputPath, String remark) {
        String sql = "UPDATE ai_training_tasks " +
                "SET status = ?, error_message = ?, updated_at = ?, output_path = ?, remark = ? " +
                "WHERE task_id = ?";
        try {
            String currentTime = getCurrentTime();
            // 使用数据库连接池执行更新操作
            getMysqlAdapter().executeUpdate(
                    sql,
                    status,
                    message,
                    currentTime,
                    outputPath != null ? outputPath : "",
                    remark != null ? remark : "",
                    taskId
            );
            log.info("任务状态已更新: taskId={}, status={}, outputPath={}", taskId, status, outputPath);
            
            // 如果任务已完成或失败，停止轮询
            if ("completed".equals(status) || "failed".equals(status) || "stopped".equals(status)) {
                stopJobStatusPolling(taskId);
                log.info("任务状态已更新为终态，停止轮询: taskId={}, status={}", taskId, status);
            }
        } catch (Exception e) {
            log.error("更新任务状态失败: taskId={}, status={}, error={}", taskId, status, e.getMessage(), e);
            // 即使更新失败，如果是终态，也要尝试停止轮询
            if ("completed".equals(status) || "failed".equals(status) || "stopped".equals(status)) {
                stopJobStatusPolling(taskId);
            }
        }
    }

    /**
     * 更新YOLO任务状态
     */
    private void updateYoloTaskStatus(String taskId, String status, String message) {
        String sql = "UPDATE ai_training_tasks " +
                "SET status = ?, error_message = ?, updated_at = ? " +
                "WHERE task_id = ?";
        try {
            String currentTime = getCurrentTime();
            // 使用数据库连接池执行更新操作
            getMysqlAdapter().executeUpdate(
                    sql,
                    status,
                    message,
                    currentTime,
                    taskId
            );
            log.info("任务状态已更新: taskId={}, status={}", taskId, status);
            
            // 如果任务已完成或失败，停止轮询
            if ("completed".equals(status) || "failed".equals(status) || "stopped".equals(status)) {
                stopJobStatusPolling(taskId);
                log.info("任务状态已更新为终态，停止轮询: taskId={}, status={}", taskId, status);
            }
        } catch (Exception e) {
            log.error("更新任务状态失败: taskId={}, status={}, error={}", taskId, status, e.getMessage(), e);
            // 即使更新失败，如果是终态，也要尝试停止轮询
            if ("completed".equals(status) || "failed".equals(status) || "stopped".equals(status)) {
                stopJobStatusPolling(taskId);
            }
        }
    }

    /**
     * 更新YOLO任务停止状态（包含结束时间）
     */
    private void updateYoloTaskStopStatus(String taskId, String endTime) {
        String sql = "UPDATE ai_training_tasks " +
                "SET status = ?, end_time = ?, updated_at = ? " +
                "WHERE task_id = ?";
        try {
            String currentTime = getCurrentTime();
            // 使用数据库连接池执行更新操作
            getMysqlAdapter().executeUpdate(
                    sql,
                    "stopped",
                    endTime,
                    currentTime,
                    taskId
            );
            log.info("任务已停止: taskId={}, endTime={}", taskId, endTime);
            
            // 停止状态是终态，停止轮询
            stopJobStatusPolling(taskId);
        } catch (Exception e) {
            log.error("更新任务停止状态失败: taskId={}, error={}", taskId, e.getMessage(), e);
            // 即使更新失败，也要尝试停止轮询
            stopJobStatusPolling(taskId);
        }
    }

    /**
     * 更新训练任务进度和轮次
     */
    private void updateYoloTaskProgress(String taskId, int currentEpoch, String progress) {
        String sql = "UPDATE ai_training_tasks " +
                "SET current_epoch = ?, progress = ?, updated_at = ? " +
                "WHERE task_id = ?";
        try {
            String currentTime = getCurrentTime();
            // 使用数据库连接池执行更新操作
            getMysqlAdapter().executeUpdate(
                    sql,
                    currentEpoch,
                    progress,
                    currentTime,
                    taskId
            );
            log.debug("任务进度已更新: taskId={}, epoch={}, progress={}", taskId, currentEpoch, progress);
        } catch (Exception e) {
            log.error("更新任务进度失败: taskId={}, error={}", taskId, e.getMessage(), e);
        }
    }

    /**
     * 更新训练任务完成信息
     */
    private void updateYoloTaskComplete(String taskId, String trainDir) {
        // 如果output_path为空，使用trainDir作为output_path
        String sql = "UPDATE ai_training_tasks " +
                "SET status = ?, end_time = ?, train_dir = ?, " +
                "output_path = COALESCE(output_path, ?), updated_at = ?, progress = '100%' " +
                "WHERE task_id = ?";
        try {
            String currentTime = getCurrentTime();
            // 使用数据库连接池执行更新操作
            getMysqlAdapter().executeUpdate(
                    sql,
                    "completed",
                    currentTime,
                    trainDir,
                    trainDir,  // 如果output_path为空，使用trainDir
                    currentTime,
                    taskId
            );
            log.info("任务已完成: taskId={}, trainDir={}", taskId, trainDir);
            addYoloTrainingLog(taskId, "INFO", "训练任务已完成，输出目录: " + trainDir);
            
            // 任务已完成，停止轮询
            stopJobStatusPolling(taskId);
            
            // 训练完成后自动入库新模型
            try {
                ai.finetune.utils.TrainingPostProcessor postProcessor = new ai.finetune.utils.TrainingPostProcessor();
                postProcessor.processTrainingCompletion(taskId);
            } catch (Exception e) {
                log.warn("训练后自动入库处理失败: taskId={}", taskId, e);
            }
        } catch (Exception e) {
            log.error("更新任务完成信息失败: taskId={}, error={}", taskId, e.getMessage(), e);
            // 即使更新失败，也要尝试停止轮询
            stopJobStatusPolling(taskId);
        }
    }

    /**
     * 软删除YOLO任务
     */
    private void deleteYoloTask(String taskId) {
        String sql = "UPDATE ai_training_tasks " +
                "SET is_deleted = 1, deleted_at = ? " +
                "WHERE task_id = ?";
        try {
            String currentTime = getCurrentTime();
            // 使用数据库连接池执行更新操作
            getMysqlAdapter().executeUpdate(
                    sql,
                    currentTime,
                    taskId
            );
            log.info("任务已删除（软删除）: taskId={}", taskId);
        } catch (Exception e) {
            log.error("删除任务失败: taskId={}, error={}", taskId, e.getMessage(), e);
        }
    }


    /**
     * 根据容器ID获取任务ID
     */
    private String getTaskIdByContainerId(String containerId) {
        String sql = "SELECT task_id FROM ai_training_tasks " +
                "WHERE container_name = ? OR container_id = ? LIMIT 1";
        try {
            // 使用数据库连接池执行查询操作
            List<Map<String, Object>> result = getMysqlAdapter().select(sql, containerId, containerId);
            if (result != null && !result.isEmpty()) {
                return (String) result.get(0).get("task_id");
            }
        } catch (Exception e) {
            log.error("根据容器ID获取任务ID失败: containerId={}, error={}", containerId, e.getMessage(), e);
        }
        return null;
    }

    /**
     * 根据任务ID获取容器名称
     */
    private String getContainerNameByTaskId(String taskId) {
        String sql = "SELECT container_name FROM ai_training_tasks " +
                "WHERE task_id = ? LIMIT 1";
        try {
            // 使用数据库连接池执行查询操作
            List<Map<String, Object>> result = getMysqlAdapter().select(sql, taskId);
            if (result != null && !result.isEmpty()) {
                return (String) result.get(0).get("container_name");
            }
        } catch (Exception e) {
            log.error("根据任务ID获取容器名称失败: taskId={}, error={}", taskId, e.getMessage(), e);
        }
        return null;
    }

    /**
     * 添加YOLO训练日志到数据库（仅系统操作日志）
     * @param taskId 任务ID
     * @param logLevel 日志级别
     * @param logMessage 日志消息
     */
    private void addYoloTrainingLog(String taskId, String logLevel, String logMessage) {
        addYoloTrainingLog(taskId, logLevel, logMessage, null);
    }

    /**
     * 添加YOLO训练日志到数据库（仅系统操作日志）
     * @param taskId 任务ID
     * @param logLevel 日志级别
     * @param logMessage 日志消息
     * @param trainingLogFilePath 训练日志文件路径（宿主机路径，可选，用于记录实际训练日志文件位置）
     */
    private void addYoloTrainingLog(String taskId, String logLevel, String logMessage, String trainingLogFilePath) {
        String currentTime = getCurrentTime();
        // 使用实际的训练日志文件路径（如果提供），否则使用默认路径
        String defaultLogPath = logPathPrefix != null ? logPathPrefix : "/data/log/";
        if (!defaultLogPath.endsWith("/")) {
            defaultLogPath += "/";
        }
        String logFilePath = trainingLogFilePath != null ? trainingLogFilePath : (defaultLogPath + taskId + ".log");

        // 构造日志条目
        String logEntry = currentTime + " " + logLevel + " " + logMessage + "\n";

        // 只写入到数据库（系统操作日志）
        // 检查该任务是否已存在日志记录
        String checkSql = "SELECT COUNT(*) AS cnt FROM ai_training_logs WHERE task_id = ?";

        try {
            long logCount = 0;
            // 使用数据库连接池执行查询操作
            List<Map<String, Object>> result = getMysqlAdapter().select(checkSql, taskId);
            if (result != null && !result.isEmpty() && result.get(0).get("cnt") != null) {
                logCount = ((Number) result.get(0).get("cnt")).longValue();
            }

            if (logCount > 0) {
                // 若存在日志，追加内容，同时更新训练日志文件路径（如果提供了新的路径）
                String updateSql;
                if (trainingLogFilePath != null) {
                    updateSql = "UPDATE ai_training_logs " +
                            "SET log_message = CONCAT(IFNULL(log_message, ''), ?), " +
                            "log_level = ?, " +
                            "log_file_path = ?, " +
                            "created_at = ? " +
                            "WHERE task_id = ?";
                    getMysqlAdapter().executeUpdate(updateSql, logEntry, logLevel, logFilePath, currentTime, taskId);
                } else {
                    updateSql = "UPDATE ai_training_logs " +
                            "SET log_message = CONCAT(IFNULL(log_message, ''), ?), " +
                            "log_level = ?, " +
                            "created_at = ? " +
                            "WHERE task_id = ?";
                    getMysqlAdapter().executeUpdate(updateSql, logEntry, logLevel, currentTime, taskId);
                }
            } else {
                // 若不存在日志，直接插入新记录
                String insertSql = "INSERT INTO ai_training_logs " +
                        "(task_id, log_level, log_message, created_at, log_file_path) " +
                        "VALUES (?, ?, ?, ?, ?)";
                // 使用数据库连接池执行插入操作
                getMysqlAdapter().executeUpdate(insertSql,
                        taskId,
                        logLevel,
                        logEntry,
                        currentTime,
                        logFilePath);
            }
        } catch (Exception e) {
            log.error("添加训练日志到数据库失败: taskId={}, error={}", taskId, e.getMessage(), e);
        }
    }

    /**
     * 添加YOLO转换日志到数据库（仅系统操作日志）
     * @param taskId 任务ID
     * @param logLevel 日志级别
     * @param logMessage 日志消息
     */
    private void addYoloConvertLog(String taskId, String logLevel, String logMessage) {
        addYoloConvertLog(taskId, logLevel, logMessage, null);
    }

    /**
     * 添加YOLO转换日志到数据库（仅系统操作日志）
     * @param taskId 任务ID
     * @param logLevel 日志级别
     * @param logMessage 日志消息
     * @param convertLogFilePath 转换日志文件路径（宿主机路径，可选，用于记录实际转换日志文件位置）
     */
    private void addYoloConvertLog(String taskId, String logLevel, String logMessage, String convertLogFilePath) {
        String currentTime = getCurrentTime();
        // 使用实际的转换日志文件路径（如果提供），否则使用默认路径
        String defaultLogPath = logPathPrefix != null ? logPathPrefix : "/data/log/";
        if (!defaultLogPath.endsWith("/")) {
            defaultLogPath += "/";
        }
        defaultLogPath += "convert/";
        String logFilePath = convertLogFilePath != null ? convertLogFilePath : (defaultLogPath + taskId + ".log");

        // 构造日志条目
        String logEntry = currentTime + " " + logLevel + " " + logMessage + "\n";

        // 只写入到数据库（系统操作日志）
        // 检查该任务是否已存在日志记录
        String checkSql = "SELECT COUNT(*) AS cnt FROM ai_training_logs WHERE task_id = ?";

        try {
            long logCount = 0;
            // 使用数据库连接池执行查询操作
            List<Map<String, Object>> result = getMysqlAdapter().select(checkSql, taskId);
            if (result != null && !result.isEmpty() && result.get(0).get("cnt") != null) {
                logCount = ((Number) result.get(0).get("cnt")).longValue();
            }

            if (logCount > 0) {
                // 若存在日志，追加内容，同时更新转换日志文件路径（如果提供了新的路径）
                String updateSql;
                if (convertLogFilePath != null) {
                    updateSql = "UPDATE ai_training_logs " +
                            "SET log_message = CONCAT(IFNULL(log_message, ''), ?), " +
                            "log_level = ?, " +
                            "log_file_path = ?, " +
                            "created_at = ? " +
                            "WHERE task_id = ?";
                    getMysqlAdapter().executeUpdate(updateSql, logEntry, logLevel, logFilePath, currentTime, taskId);
                } else {
                    updateSql = "UPDATE ai_training_logs " +
                            "SET log_message = CONCAT(IFNULL(log_message, ''), ?), " +
                            "log_level = ?, " +
                            "created_at = ? " +
                            "WHERE task_id = ?";
                    getMysqlAdapter().executeUpdate(updateSql, logEntry, logLevel, currentTime, taskId);
                }
            } else {
                // 若不存在日志，直接插入新记录
                String insertSql = "INSERT INTO ai_training_logs " +
                        "(task_id, log_level, log_message, created_at, log_file_path) " +
                        "VALUES (?, ?, ?, ?, ?)";
                // 使用数据库连接池执行插入操作
                getMysqlAdapter().executeUpdate(insertSql,
                        taskId,
                        logLevel,
                        logEntry,
                        currentTime,
                        logFilePath);
            }
        } catch (Exception e) {
            log.error("添加转换日志到数据库失败: taskId={}, error={}", taskId, e.getMessage(), e);
        }
    }

    /**
     * 添加YOLO推理日志到数据库（仅系统操作日志）
     * @param taskId 任务ID
     * @param logLevel 日志级别
     * @param logMessage 日志消息
     */
    private void addYoloPredictLog(String taskId, String logLevel, String logMessage) {
        addYoloPredictLog(taskId, logLevel, logMessage, null);
    }

    /**
     * 添加YOLO推理日志到数据库（仅系统操作日志）
     * @param taskId 任务ID
     * @param logLevel 日志级别
     * @param logMessage 日志消息
     * @param predictLogFilePath 推理日志文件路径（宿主机路径，可选，用于记录实际推理日志文件位置）
     */
    private void addYoloPredictLog(String taskId, String logLevel, String logMessage, String predictLogFilePath) {
        String currentTime = getCurrentTime();
        // 使用实际的推理日志文件路径（如果提供），否则使用默认路径
        String defaultLogPath = "/data/log/predict/";
        if (!defaultLogPath.endsWith("/")) {
            defaultLogPath += "/";
        }
        String logFilePath = predictLogFilePath != null ? predictLogFilePath : (defaultLogPath + taskId + ".log");

        // 构造日志条目
        String logEntry = currentTime + " " + logLevel + " " + logMessage + "\n";

        // 只写入到数据库（系统操作日志）
        // 检查该任务是否已存在日志记录
        String checkSql = "SELECT COUNT(*) AS cnt FROM ai_training_logs WHERE task_id = ?";

        try {
            long logCount = 0;
            // 使用数据库连接池执行查询操作
            List<Map<String, Object>> result = getMysqlAdapter().select(checkSql, taskId);
            if (result != null && !result.isEmpty() && result.get(0).get("cnt") != null) {
                logCount = ((Number) result.get(0).get("cnt")).longValue();
            }

            if (logCount > 0) {
                // 若存在日志，追加内容，同时更新推理日志文件路径（如果提供了新的路径）
                String updateSql;
                if (predictLogFilePath != null) {
                    updateSql = "UPDATE ai_training_logs " +
                            "SET log_message = CONCAT(IFNULL(log_message, ''), ?), " +
                            "log_level = ?, " +
                            "log_file_path = ?, " +
                            "created_at = ? " +
                            "WHERE task_id = ?";
                    getMysqlAdapter().executeUpdate(updateSql, logEntry, logLevel, logFilePath, currentTime, taskId);
                } else {
                    updateSql = "UPDATE ai_training_logs " +
                            "SET log_message = CONCAT(IFNULL(log_message, ''), ?), " +
                            "log_level = ?, " +
                            "created_at = ? " +
                            "WHERE task_id = ?";
                    getMysqlAdapter().executeUpdate(updateSql, logEntry, logLevel, currentTime, taskId);
                }
            } else {
                // 若不存在日志，直接插入新记录
                String insertSql = "INSERT INTO ai_training_logs " +
                        "(task_id, log_level, log_message, created_at, log_file_path) " +
                        "VALUES (?, ?, ?, ?, ?)";
                // 使用数据库连接池执行插入操作
                getMysqlAdapter().executeUpdate(insertSql,
                        taskId,
                        logLevel,
                        logEntry,
                        currentTime,
                        logFilePath);
            }
        } catch (Exception e) {
            log.error("添加推理日志到数据库失败: taskId={}, error={}", taskId, e.getMessage(), e);
        }
    }

    /**
     * 上传推理日志文件到指定路径
     * @param taskId 任务ID
     * @param sourceLogFilePath 源日志文件路径（宿主机路径，如果为null则从volumeMount推导）
     */
    private void uploadPredictLogFile(String taskId, String sourceLogFilePath) {
        try {
            // 目标路径
            String targetLogPath = "/data/log/predict/";
            if (!targetLogPath.endsWith("/")) {
                targetLogPath += "/";
            }
            String targetLogFile = targetLogPath + taskId + ".log";

            // 如果源路径为空，尝试从volumeMount推导
            if (sourceLogFilePath == null || sourceLogFilePath.isEmpty()) {
                if (volumeMount != null && volumeMount.contains(":")) {
                    String[] mountParts = volumeMount.split(":");
                    if (mountParts.length >= 2) {
                        String containerPath = mountParts[1];
                        String hostPath = mountParts[0];
                        // 容器内路径
                        String containerLogFile = containerPath + "/log/predict/" + taskId + ".log";
                        // 宿主机路径
                        sourceLogFilePath = containerLogFile.replace(containerPath, hostPath);
                    }
                }
            }

            // 如果源路径仍然为空，使用默认路径
            if (sourceLogFilePath == null || sourceLogFilePath.isEmpty()) {
                if (volumeMount != null && volumeMount.contains(":")) {
                    String[] mountParts = volumeMount.split(":");
                    if (mountParts.length >= 2) {
                        String hostPath = mountParts[0];
                        sourceLogFilePath = hostPath + "/log/predict/" + taskId + ".log";
                    }
                }
            }

            // K8s 模式下不再通过宿主机 SSH 复制日志，只记录路径提示
            if (sourceLogFilePath != null && !sourceLogFilePath.isEmpty()) {
                log.info("推理日志文件可在宿主机路径查找: {}", sourceLogFilePath);
                addYoloPredictLog(taskId, "INFO", "推理日志文件路径: " + sourceLogFilePath, sourceLogFilePath);
            } else {
                log.warn("无法确定推理日志源文件路径: taskId={}", taskId);
                addYoloPredictLog(taskId, "WARN", "无法确定推理日志源文件路径", null);
            }

        } catch (Exception e) {
            log.error("上传推理日志文件失败: taskId={}, error={}", taskId, e.getMessage(), e);
            addYoloPredictLog(taskId, "ERROR", "上传推理日志文件失败: " + e.getMessage(), null);
        }
    }


    // ==================== 工具辅助方法 ====================

    /**
     * 获取当前时间字符串
     */
    private String getCurrentTime() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }

    // ==================== Job状态轮询方法 ====================

    /**
     * 启动Job状态轮询
     * 每15秒检查一次Job状态，确保状态变化能同步到数据库
     * 
     * @param taskId 任务ID
     * @param jobName Job名称（即container_name）
     */
    private void startJobStatusPolling(String taskId, String jobName) {
        // 防止重复启动轮询
        if (POLLING_TASKS.containsKey(taskId)) {
            log.warn("任务已在轮询中，跳过重复启动: taskId={}, jobName={}", taskId, jobName);
            return;
        }

        log.info("🚀 启动Job状态轮询: taskId={}, jobName={}, 轮询间隔={}秒, 首次延迟={}秒", 
                taskId, jobName, POLL_INTERVAL_SECONDS, INITIAL_DELAY_SECONDS);

        // 启动定时任务：延迟3秒后开始第一次轮询，之后每15秒轮询一次
        // 轮询会调用 getJobStatus() 方法，该方法通过 K8s API (batchApi.readNamespacedJob) 获取Job状态
        ScheduledFuture<?> future = jobStatusPollingExecutor.scheduleWithFixedDelay(
                () -> {
                    try {
                        pollJobStatus(taskId, jobName);
                    } catch (Exception e) {
                        log.error("轮询任务执行异常: taskId={}, jobName={}, error={}", 
                                taskId, jobName, e.getMessage(), e);
                    }
                },
                INITIAL_DELAY_SECONDS,
                POLL_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );

        // 保存轮询任务引用，用于后续停止
        POLLING_TASKS.put(taskId, future);
        log.info("✅ 轮询任务已启动并注册: taskId={}, 总轮询任务数={}", taskId, POLLING_TASKS.size());
    }

    /**
     * 停止Job状态轮询
     * 
     * @param taskId 任务ID
     */
    private void stopJobStatusPolling(String taskId) {
        ScheduledFuture<?> future = POLLING_TASKS.remove(taskId);
        if (future != null && !future.isCancelled() && !future.isDone()) {
            future.cancel(false);  // false表示不中断正在执行的任务
            log.info("Job状态轮询已停止: taskId={}", taskId);
        }
    }

    /**
     * 轮询Job状态并更新数据库
     * 核心逻辑：检查K8s Job状态，如果已完成或失败，必须同步到数据库
     * 
     * @param taskId 任务ID
     * @param jobName Job名称
     */
    private void pollJobStatus(String taskId, String jobName) {
        try {
            log.info("🔄 开始轮询Job状态: taskId={}, jobName={} (通过K8s API获取)", taskId, jobName);

            // 获取Job状态（调用K8s API）
            JSONObject jobStatus = getJobStatus(jobName);
            String status = jobStatus.getStr("status");
            
            log.debug("K8s API返回状态: taskId={}, status={}", taskId, status);

            // 如果获取状态失败或Job不存在，记录日志但不更新
            if (!"success".equals(status)) {
                if ("容器未找到".equals(status) || "not_found".equals(status)) {
                    log.warn("Job不存在，可能已被删除，停止轮询: taskId={}, jobName={}", taskId, jobName);
                    stopJobStatusPolling(taskId);
                    // Job不存在时，更新数据库状态为failed（如果当前状态不是终态）
                    updateTaskStatusIfNotFinal(taskId, "failed", "Job已被删除");
                } else {
                    log.warn("获取Job状态失败: taskId={}, jobName={}, status={}", taskId, jobName, status);
                }
                return;
            }

            // 解析Job状态
            String jobPhase = jobStatus.getStr("jobPhase", "");
            String podPhase = jobStatus.getStr("podPhase", "");
            String containerState = jobStatus.getStr("containerState", "");
            Integer exitCode = jobStatus.getInt("containerExitCode");
            String containerReason = jobStatus.getStr("containerReason", "");

            log.info("📊 Job状态详情 (来自K8s API): taskId={}, jobPhase={}, podPhase={}, containerState={}, exitCode={}",
                    taskId, jobPhase, podPhase, containerState, exitCode);

            // 判断Job状态并更新数据库
            boolean shouldStopPolling = false;
            String newStatus = null;
            String errorMessage = null;
            boolean updateEndTime = false;

            // 状态映射：K8s Job Phase -> 数据库 Status
            if ("Complete".equalsIgnoreCase(jobPhase) || "Succeeded".equalsIgnoreCase(jobPhase) 
                    || "Succeeded".equalsIgnoreCase(podPhase)) {
                // Job完成
                newStatus = "completed";
                updateEndTime = true;
                shouldStopPolling = true;
                log.info("检测到Job已完成: taskId={}, jobName={}", taskId, jobName);
                
            } else if ("Failed".equalsIgnoreCase(jobPhase) || "Failed".equalsIgnoreCase(podPhase)) {
                // Job失败
                newStatus = "failed";
                updateEndTime = true;
                shouldStopPolling = true;
                
                // 构建错误信息
                StringBuilder errorMsg = new StringBuilder("Job执行失败");
                if (containerReason != null && !containerReason.isEmpty()) {
                    errorMsg.append(": ").append(containerReason);
                }
                if (exitCode != null) {
                    errorMsg.append(" (退出码: ").append(exitCode).append(")");
                }
                errorMessage = errorMsg.toString();
                
                log.warn("检测到Job失败: taskId={}, jobName={}, error={}", taskId, jobName, errorMessage);
                
            } else if ("Terminated".equals(containerState)) {
                // 容器已终止
                if (exitCode != null && exitCode == 0) {
                    // 退出码为0，视为成功完成
                    newStatus = "completed";
                    updateEndTime = true;
                    shouldStopPolling = true;
                    log.info("检测到容器正常终止: taskId={}, jobName={}, exitCode=0", taskId, jobName);
                } else {
                    // 退出码非0，视为失败
                    newStatus = "failed";
                    updateEndTime = true;
                    shouldStopPolling = true;
                    
                    StringBuilder errorMsg = new StringBuilder("容器异常终止");
                    if (exitCode != null) {
                        errorMsg.append(" (退出码: ").append(exitCode).append(")");
                    }
                    if (containerReason != null && !containerReason.isEmpty()) {
                        errorMsg.append(", 原因: ").append(containerReason);
                    }
                    errorMessage = errorMsg.toString();
                    
                    log.warn("检测到容器异常终止: taskId={}, jobName={}, error={}", taskId, jobName, errorMessage);
                }
                
            } else if ("Running".equals(containerState) || "Running".equalsIgnoreCase(podPhase)) {
                // 容器正在运行
                newStatus = "running";
                log.debug("Job正在运行: taskId={}, jobName={}", taskId, jobName);
                
            } else if ("Waiting".equals(containerState) || "Pending".equalsIgnoreCase(podPhase)) {
                // 容器等待中
                newStatus = "starting";
                log.debug("Job等待中: taskId={}, jobName={}", taskId, jobName);
                
            } else {
                // 其他状态，保持当前状态或设置为unknown
                log.debug("Job状态未知: taskId={}, jobPhase={}, podPhase={}, containerState={}", 
                        taskId, jobPhase, podPhase, containerState);
                // 不更新状态，继续轮询
                return;
            }

            // 如果状态有变化，更新数据库（重点：完成和失败必须更新）
            if (newStatus != null) {
                log.info("📝 准备更新数据库状态: taskId={}, newStatus={}, updateEndTime={}", 
                        taskId, newStatus, updateEndTime);
                updateTaskStatusFromPolling(taskId, newStatus, errorMessage, updateEndTime);
                
                // 如果是终态（completed/failed），停止轮询
                if (shouldStopPolling) {
                    stopJobStatusPolling(taskId);
                    log.info("🛑 任务已进入终态，停止轮询: taskId={}, status={}", taskId, newStatus);
                }
            } else {
                log.debug("任务状态未变化，继续轮询: taskId={}, jobPhase={}, podPhase={}", 
                        taskId, jobPhase, podPhase);
            }

        } catch (Exception e) {
            log.error("❌ 轮询Job状态异常: taskId={}, jobName={}, error={}", taskId, jobName, e.getMessage(), e);
            // 异常不影响其他任务的轮询，继续执行
        }
    }

    /**
     * 更新任务状态（从轮询结果）
     * 确保完成和失败状态一定能更新到数据库（带重试机制）
     * 
     * @param taskId 任务ID
     * @param newStatus 新状态
     * @param errorMessage 错误信息（可选）
     * @param updateEndTime 是否更新结束时间
     */
    private void updateTaskStatusFromPolling(String taskId, String newStatus, String errorMessage, boolean updateEndTime) {
        try {
            // 先查询当前数据库状态，避免重复更新
            String currentStatus = getCurrentTaskStatus(taskId);
            if (newStatus.equals(currentStatus)) {
                log.debug("任务状态未变化，跳过更新: taskId={}, status={}", taskId, newStatus);
                return;
            }

            String currentTime = getCurrentTime();
            String sql;
            int updateResult;

            if (updateEndTime) {
                // 更新状态、结束时间和错误信息
                sql = "UPDATE ai_training_tasks " +
                      "SET status = ?, end_time = ?, error_message = ?, updated_at = ? " +
                      "WHERE task_id = ?";
                updateResult = getMysqlAdapter().executeUpdate(
                        sql,
                        newStatus,
                        currentTime,
                        errorMessage != null ? errorMessage : "",
                        currentTime,
                        taskId
                );
            } else {
                // 只更新状态和错误信息
                sql = "UPDATE ai_training_tasks " +
                      "SET status = ?, error_message = ?, updated_at = ? " +
                      "WHERE task_id = ?";
                updateResult = getMysqlAdapter().executeUpdate(
                        sql,
                        newStatus,
                        errorMessage != null ? errorMessage : "",
                        currentTime,
                        taskId
                );
            }

            if (updateResult > 0) {
                log.info("任务状态已更新: taskId={}, oldStatus={}, newStatus={}, updateEndTime={}", 
                        taskId, currentStatus, newStatus, updateEndTime);
                
                // 训练完成后自动入库新模型（仅当状态从非completed变为completed时）
                if ("completed".equals(newStatus) && !"completed".equals(currentStatus)) {
                    try {
                        // 检查是否是训练任务（task_type = 'train'）
                        String checkSql = "SELECT task_type FROM ai_training_tasks WHERE task_id = ? LIMIT 1";
                        List<Map<String, Object>> taskResult = getMysqlAdapter().select(checkSql, taskId);
                        if (taskResult != null && !taskResult.isEmpty()) {
                            String taskType = (String) taskResult.get(0).get("task_type");
                            if ("train".equals(taskType)) {
                                log.info("训练任务已完成，触发自动入库: taskId={}", taskId);
                                ai.finetune.utils.TrainingPostProcessor postProcessor = new ai.finetune.utils.TrainingPostProcessor();
                                postProcessor.processTrainingCompletion(taskId);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("训练后自动入库处理失败: taskId={}", taskId, e);
                    }
                }
            } else {
                log.warn("任务状态更新失败（可能记录不存在）: taskId={}, newStatus={}", taskId, newStatus);
            }

            // 如果是完成或失败状态，确保更新成功（关键要求）
            if (("completed".equals(newStatus) || "failed".equals(newStatus)) && updateResult == 0) {
                log.error("⚠️ 关键状态更新失败，尝试重试: taskId={}, status={}", taskId, newStatus);
                // 重试一次
                retryUpdateTaskStatus(taskId, newStatus, errorMessage, updateEndTime, currentTime);
            }

        } catch (Exception e) {
            log.error("更新任务状态失败: taskId={}, status={}, error={}", taskId, newStatus, e.getMessage(), e);
            
            // 如果是完成或失败状态，即使异常也要重试
            if ("completed".equals(newStatus) || "failed".equals(newStatus)) {
                log.error("⚠️ 关键状态更新异常，尝试重试: taskId={}, status={}", taskId, newStatus);
                try {
                    String currentTime = getCurrentTime();
                    retryUpdateTaskStatus(taskId, newStatus, errorMessage, updateEndTime, currentTime);
                } catch (Exception retryException) {
                    log.error("重试更新任务状态仍然失败: taskId={}, status={}, error={}", 
                            taskId, newStatus, retryException.getMessage(), retryException);
                }
            }
        }
    }

    /**
     * 重试更新任务状态（用于关键状态：completed/failed）
     * 
     * @param taskId 任务ID
     * @param newStatus 新状态
     * @param errorMessage 错误信息
     * @param updateEndTime 是否更新结束时间
     * @param currentTime 当前时间
     */
    private void retryUpdateTaskStatus(String taskId, String newStatus, String errorMessage, 
                                      boolean updateEndTime, String currentTime) {
        try {
            String sql;
            if (updateEndTime) {
                sql = "UPDATE ai_training_tasks " +
                      "SET status = ?, end_time = ?, error_message = ?, updated_at = ? " +
                      "WHERE task_id = ?";
                getMysqlAdapter().executeUpdate(sql, newStatus, currentTime, 
                        errorMessage != null ? errorMessage : "", currentTime, taskId);
            } else {
                sql = "UPDATE ai_training_tasks " +
                      "SET status = ?, error_message = ?, updated_at = ? " +
                      "WHERE task_id = ?";
                getMysqlAdapter().executeUpdate(sql, newStatus, 
                        errorMessage != null ? errorMessage : "", currentTime, taskId);
            }
            log.info("重试更新任务状态成功: taskId={}, status={}", taskId, newStatus);
        } catch (Exception e) {
            log.error("重试更新任务状态失败: taskId={}, status={}, error={}", taskId, newStatus, e.getMessage(), e);
        }
    }

    /**
     * 获取任务当前状态
     * 
     * @param taskId 任务ID
     * @return 当前状态，如果不存在返回null
     */
    private String getCurrentTaskStatus(String taskId) {
        try {
            String sql = "SELECT status FROM ai_training_tasks WHERE task_id = ? LIMIT 1";
            List<Map<String, Object>> result = getMysqlAdapter().select(sql, taskId);
            if (result != null && !result.isEmpty() && result.get(0).get("status") != null) {
                return (String) result.get(0).get("status");
            }
        } catch (Exception e) {
            log.error("查询任务当前状态失败: taskId={}, error={}", taskId, e.getMessage(), e);
        }
        return null;
    }

    /**
     * 更新任务状态（如果当前状态不是终态）
     * 用于Job不存在时的状态更新
     * 
     * @param taskId 任务ID
     * @param newStatus 新状态
     * @param errorMessage 错误信息
     */
    private void updateTaskStatusIfNotFinal(String taskId, String newStatus, String errorMessage) {
        try {
            String currentStatus = getCurrentTaskStatus(taskId);
            if (currentStatus == null) {
                log.warn("任务不存在，无法更新状态: taskId={}", taskId);
                return;
            }
            
            // 如果当前状态已经是终态，不更新
            if ("completed".equals(currentStatus) || "failed".equals(currentStatus) 
                    || "stopped".equals(currentStatus)) {
                log.debug("任务已是终态，不更新: taskId={}, currentStatus={}", taskId, currentStatus);
                return;
            }
            
            // 更新状态
            updateTaskStatusFromPolling(taskId, newStatus, errorMessage, true);
        } catch (Exception e) {
            log.error("更新任务状态失败: taskId={}, status={}, error={}", taskId, newStatus, e.getMessage(), e);
        }
    }

    /**
     * 根据模型名称获取模型类别
     * 优先从配置中读取，如果没有则根据模型名称推断
     *
     * 支持的类别：
     * - detection: 目标检测
     * - segmentation: 图像分割
     * - recognition: 文字识别
     * - reid: 重识别
     * - keypoint: 关键点检测
     * - feature_extraction: 特征提取
     * - event_detection: 事件检测
     * - video_segmentation: 视频分割
     * - classification: 图像分类
     * - tracking: 目标跟踪
     * - pose_estimation: 姿态估计
     * - gan: 生成对抗网络
     * - custom: 自定义模型
     *
     * @param modelName 模型名称
     * @param config 配置对象
     * @return 模型类别
     */
    private String getModelCategory(String modelName, JSONObject config) {
        // 1. 优先从配置中读取（最高优先级）
        if (config.containsKey("model_category")) {
            String category = config.getStr("model_category");
            log.debug("使用配置中的模型类别: modelName={}, category={}", modelName, category);
            return category;
        }

        // 2. 根据模型名称推断类别（基于关键词匹配）
        String lowerModelName = modelName.toLowerCase();

        // 检测类模型
        if (lowerModelName.contains("yolo") || lowerModelName.contains("ssd") ||
                lowerModelName.contains("rcnn") || lowerModelName.contains("fasterrcnn") ||
                lowerModelName.contains("centernet") || lowerModelName.contains("tracknet") ||
                lowerModelName.contains("retinanet") || lowerModelName.contains("efficientdet") ||
                lowerModelName.contains("detector")) {
            log.debug("根据名称推断为检测模型: modelName={}", modelName);
            return "detection";
        }

        // 分割类模型
        if (lowerModelName.contains("unet") || lowerModelName.contains("fcn") ||
                lowerModelName.contains("pidnet") || lowerModelName.contains("deeplab") ||
                lowerModelName.contains("segnet") || lowerModelName.contains("maskrcnn") ||
                lowerModelName.contains("pspnet") || lowerModelName.contains("segmentation")) {
            log.debug("根据名称推断为分割模型: modelName={}", modelName);
            return "segmentation";
        }

        // 识别类模型
        if (lowerModelName.contains("crnn") || lowerModelName.contains("ocr") ||
                lowerModelName.contains("recogn") || lowerModelName.contains("tesseract") ||
                lowerModelName.contains("paddleocr") || lowerModelName.contains("easyocr")) {
            log.debug("根据名称推断为识别模型: modelName={}", modelName);
            return "recognition";
        }

        // 重识别模型
        if (lowerModelName.contains("reid") || lowerModelName.contains("clip") ||
                lowerModelName.contains("triplet") || lowerModelName.contains("metric")) {
            log.debug("根据名称推断为重识别模型: modelName={}", modelName);
            return "reid";
        }

        // 关键点检测模型
        if (lowerModelName.contains("hrnet") || lowerModelName.contains("keypoint") ||
                lowerModelName.contains("openpose") || lowerModelName.contains("alphapose")) {
            log.debug("根据名称推断为关键点检测模型: modelName={}", modelName);
            return "keypoint";
        }

        // 特征提取模型
        if (lowerModelName.contains("resnet") || lowerModelName.contains("osnet") ||
                lowerModelName.contains("vgg") || lowerModelName.contains("mobilenet") ||
                lowerModelName.contains("efficientnet") || lowerModelName.contains("densenet") ||
                lowerModelName.contains("inception") || lowerModelName.contains("feature")) {
            log.debug("根据名称推断为特征提取模型: modelName={}", modelName);
            return "feature_extraction";
        }

        // 事件检测模型
        if (lowerModelName.contains("tdeed") || lowerModelName.contains("event") ||
                lowerModelName.contains("action")) {
            log.debug("根据名称推断为事件检测模型: modelName={}", modelName);
            return "event_detection";
        }

        // 视频分割模型
        if (lowerModelName.contains("transnet") || lowerModelName.contains("video") ||
                lowerModelName.contains("temporal")) {
            log.debug("根据名称推断为视频分割模型: modelName={}", modelName);
            return "video_segmentation";
        }

        // 分类模型
        if (lowerModelName.contains("classif") || lowerModelName.contains("alexnet") ||
                lowerModelName.contains("squeezenet")) {
            log.debug("根据名称推断为分类模型: modelName={}", modelName);
            return "classification";
        }

        // 跟踪模型
        if (lowerModelName.contains("track") || lowerModelName.contains("sort") ||
                lowerModelName.contains("deepsort") || lowerModelName.contains("bytetrack")) {
            log.debug("根据名称推断为跟踪模型: modelName={}", modelName);
            return "tracking";
        }

        // 姿态估计模型
        if (lowerModelName.contains("pose") || lowerModelName.contains("posenet")) {
            log.debug("根据名称推断为姿态估计模型: modelName={}", modelName);
            return "pose_estimation";
        }

        // 生成模型
        if (lowerModelName.contains("gan") || lowerModelName.contains("generator") ||
                lowerModelName.contains("diffusion") || lowerModelName.contains("vae")) {
            log.debug("根据名称推断为生成模型: modelName={}", modelName);
            return "gan";
        }

        // 3. 无法推断，返回默认值 "custom"（自定义模型）
        log.info("无法根据模型名称推断类别，使用默认值 'custom': modelName={}", modelName);
        log.info("建议在请求中明确指定 'model_category' 字段");
        return "custom";
    }

    /**
     * 根据模型名称获取模型框架
     * 优先从配置中读取，如果没有则根据模型名称推断
     */
    private String getModelFramework(String modelName, JSONObject config) {
        // 优先从配置中读取
        if (config.containsKey("model_framework")) {
            return config.getStr("model_framework");
        }

        // 根据模型名称推断框架
        String lowerModelName = modelName.toLowerCase();
        if (lowerModelName.contains("paddle") || lowerModelName.contains("ocr")) {
            return "paddle";
        } else if (lowerModelName.contains("transnet")) {
            return "tensorflow";
        }

        // 默认返回 pytorch（大多数模型使用 PyTorch）
        return "pytorch";
    }

    /**
     * 获取 TrainingTaskRepository 实例
     * 用于查询训练任务列表等操作
     *
     * @return TrainingTaskRepository 实例
     */
    public ai.finetune.repository.TrainingTaskRepository getRepository() {
        // 使用全局共享的连接池
        MysqlAdapter adapter = getMysqlAdapter();
        return new ai.finetune.repository.TrainingTaskRepository(adapter);
    }
}

