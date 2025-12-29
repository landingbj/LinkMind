package ai.finetune;

import ai.common.utils.ObservableList;
import ai.config.ContextLoader;
import ai.config.pojo.DiscriminativeModelsConfig;
import ai.database.impl.MysqlAdapter;
import ai.finetune.config.ModelConfigManager;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    // 用于异步执行的线程池
    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    // 数据库连接池适配器（已废弃，使用全局共享的 MysqlAdapterManager）
    @Deprecated
    private static volatile MysqlAdapter mysqlAdapter = null;

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

            log.info("已从 model_platform.discriminative_models 加载 K8s/YOLO 配置: apiServer={}, ns={}, image={}, volumeMount={}",
                    this.apiServer, this.namespace, this.dockerImage, this.volumeMount);
        } catch (Exception e) {
            log.warn("加载 lagi.yml 中的 K8s/YOLO 配置失败: {}", e.getMessage());
        }
    }

    /**
     * 启动训练任务
     */
    @Override
    public String startTraining(String taskId, String trackId, JSONObject config) {
        try {
            // 确保 K8s 客户端已初始化
            initK8sClient();

            // 确保配置中包含必要的字段
            if (!config.containsKey("the_train_type")) {
                config.put("the_train_type", "train");
            }
            if (!config.containsKey("task_id")) {
                config.put("task_id", taskId);
            }
            if (!config.containsKey("track_id")) {
                config.put("track_id", trackId);
            }

            // 生成日志文件路径（保留原有逻辑）
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
            }

            // 生成 Job 名称（对应原来的容器名称）
            String jobName = generateContainerName("yolo_train");
            jobName = jobName.replace("_", "-");

            // 构建配置 JSON
            String configJson = config.toString();

            // 根据 device 配置判断是否启用 GPU
            // device 为 "cpu" 或空时，不启用 GPU；否则启用 GPU
            String device = config.getStr("device", "cpu");
            boolean useGpu = device != null && !device.equalsIgnoreCase("cpu") && !device.isEmpty();

            //String dockerImage = "yolov8_trainer:last";
            // 创建 Kubernetes Job
            createOneOffJob(jobName, dockerImage, configJson, useGpu, null);
            log.info("createOneOffJob called, jobName={}, image={}, useGpu={}, device={}", jobName, dockerImage, useGpu, device);

            // 保存任务到数据库（保留原有逻辑）
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

            // 构建返回结果
            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("message", "训练任务已启动");
            result.put("containerName", jobName);
            result.put("containerId", jobName);  // K8s中Job名称即容器标识
            result.put("jobName", jobName);
            result.put("namespace", namespace);

            return result.toString();

        } catch (Exception e) {
            log.error("启动训练任务失败: taskId={}", taskId, e);
            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("message", "启动训练任务失败");
            errorResult.put("error", e.getMessage());
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
            String result = createOneOffJob(jobName, dockerImage, config.toString(), useGpu, null).toString();

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
            String result = createOneOffJob(jobName, dockerImage, config.toString(), useGpu, null).toString();

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
        String taskId = UUID.randomUUID().toString();
        try {
            // 确保配置中包含必要的字段
            if (!config.containsKey("the_train_type")) {
                config.put("the_train_type", "export");
            }
            config.put("task_id", taskId);

            // 保存导出任务到数据库
            saveExportTaskToDB(taskId, config);
            addYoloTrainingLog(taskId, "INFO", "开始导出模型");

            String jobName = generateContainerName("yolo_export");
            jobName = jobName.replace("_", "-");
            // 根据 device 配置判断是否启用 GPU
            String device = config.getStr("device", "cpu");
            boolean useGpu = device != null && !device.equalsIgnoreCase("cpu") && !device.isEmpty();
            String result = createOneOffJob(jobName, dockerImage, config.toString(), useGpu, null).toString();

            // 更新导出任务状态
            if (isSuccess(result)) {
                updateYoloTaskStatus(taskId, "completed", "模型导出完成");
                addYoloTrainingLog(taskId, "INFO", "模型导出完成");
            } else {
                updateYoloTaskStatus(taskId, "failed", "模型导出失败");
                addYoloTrainingLog(taskId, "ERROR", "模型导出失败: " + result);
            }

            return result;

        } catch (Exception e) {
            log.error("导出模型失败", e);
            updateYoloTaskStatus(taskId, "failed", "模型导出异常: " + e.getMessage());
            addYoloTrainingLog(taskId, "ERROR", "模型导出异常: " + e.getMessage());

            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("message", "导出模型失败");
            errorResult.put("error", e.getMessage());
            return errorResult.toString();
        }
    }

    /**
     * 便捷方法：使用默认配置导出模型
     */
    public String exportModel() {
        JSONObject config = createDefaultExportConfig();
        return exportModel(config);
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
     * 获取数据库连接池适配器实例（使用全局共享的连接池）
     * 所有训练器共享同一个连接池，避免 "Too many connections" 错误
     */
    private static MysqlAdapter getMysqlAdapter() {
        if (mysqlAdapter == null) {
            synchronized (YoloK8sAdapter.class) {
                if (mysqlAdapter == null) {
                    mysqlAdapter = new MysqlAdapter("mysql");
                    log.info("数据库连接池已初始化");
                }
            }
        }
        return mysqlAdapter;
    }

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
                "status, progress, current_epoch, start_time, created_at, is_deleted, user_id, config_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        // 从配置中读取 user_id
        String userId = task.config.getStr("user_id", null);
        String datasetName = task.config.getStr("dataset_name", "");

        getMysqlAdapter().executeUpdate(sql,
                task.taskId, task.trackId, task.modelName, task.modelCategory, task.modelFramework,
                "train", task.containerName, "", task.dockerImage,
                task.device, !task.device.equals("cpu") ? 1 : 0,
                task.datasetPath, datasetName, task.modelPath, task.epochs, task.batchSize, task.imageSize, task.optimizer,
                "starting", "0%", 0, currentTime, currentTime, 0, userId, task.config.toString());
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
        } catch (Exception e) {
            log.error("更新任务状态失败: taskId={}, status={}, error={}", taskId, status, e.getMessage(), e);
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
        } catch (Exception e) {
            log.error("更新任务停止状态失败: taskId={}, error={}", taskId, e.getMessage(), e);
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
        String sql = "UPDATE ai_training_tasks " +
                "SET status = ?, end_time = ?, train_dir = ?, updated_at = ?, progress = '100%' " +
                "WHERE task_id = ?";
        try {
            String currentTime = getCurrentTime();
            // 使用数据库连接池执行更新操作
            getMysqlAdapter().executeUpdate(
                    sql,
                    "completed",
                    currentTime,
                    trainDir,
                    currentTime,
                    taskId
            );
            log.info("任务已完成: taskId={}, trainDir={}", taskId, trainDir);
            addYoloTrainingLog(taskId, "INFO", "训练任务已完成，输出目录: " + trainDir);
        } catch (Exception e) {
            log.error("更新任务完成信息失败: taskId={}, error={}", taskId, e.getMessage(), e);
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
        String defaultLogPath = logPathPrefix != null ? logPathPrefix : "/mnt/k8s_data/wangshuanglong/";
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
        String defaultLogPath = "/data/wangshuanglong/log/predict/";
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
            String targetLogPath = "/data/wangshuanglong/log/predict/";
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

