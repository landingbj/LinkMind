package ai.finetune;

import ai.database.impl.MysqlAdapter;
import ai.finetune.config.ModelConfigManager;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TrackNetV3 轨迹跟踪模型训练实现类
 * 基于 Docker 容器进行 TrackNetV3 模型的训练、评估、预测等操作
 * 所有操作都会入库记录
 *
 * 代码结构：
 * 1. 常量和静态成员
 * 2. 构造函数
 * 3. 训练任务核心方法
 * 4. Docker容器管理方法
 * 5. 日志流式获取方法
 * 6. 数据库操作方法（CRUD）
 * 7. 工具辅助方法
 */
@Slf4j
public class TrackNetV3Adapter extends DockerTrainerAbstract {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 从配置中读取的日志路径前缀
    private String logPathPrefix;

    // 用于异步执行的线程池
    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    // 数据库连接池适配器（单例模式）
    private static volatile MysqlAdapter mysqlAdapter = null;

    // 模型配置管理器（单例模式）
    private static volatile ModelConfigManager modelConfigManager = null;

    /**
     * 默认构造函数
     */
    public TrackNetV3Adapter() {
        super();
        loadConfigFromYaml();
    }

    /**
     * 带SSH配置的构造函数
     * @param sshHost SSH主机地址
     * @param sshPort SSH端口
     * @param sshUsername SSH用户名
     * @param sshPassword SSH密码
     */
    public TrackNetV3Adapter(String sshHost, int sshPort, String sshUsername, String sshPassword) {
        super(sshHost, sshPort, sshUsername, sshPassword);
        loadConfigFromYaml();
    }

    /**
     * 从lagi.yml加载配置
     */
    private void loadConfigFromYaml() {
        try {
            ai.config.ContextLoader.loadContext();
            if (ai.config.ContextLoader.configuration != null &&
                ai.config.ContextLoader.configuration.getModelPlatformConfig() != null &&
                ai.config.ContextLoader.configuration.getModelPlatformConfig().getDiscriminativeModelsConfig() != null) {

                ai.config.pojo.DiscriminativeModelsConfig discriminativeConfig =
                    ai.config.ContextLoader.configuration.getModelPlatformConfig().getDiscriminativeModelsConfig();

                ai.config.pojo.DiscriminativeModelsConfig.TrackNetV3Config tracknetv3Config =
                    discriminativeConfig.getTracknetv3();

                if (tracknetv3Config != null && tracknetv3Config.getDocker() != null) {
                    ai.config.pojo.DiscriminativeModelsConfig.DockerConfig dockerConfig =
                        tracknetv3Config.getDocker();

                    // 加载Docker配置
                    if (cn.hutool.core.util.StrUtil.isNotBlank(dockerConfig.getImage())) {
                        this.dockerImage = dockerConfig.getImage();
                        super.setDockerImage(this.dockerImage);
                    }
                    if (cn.hutool.core.util.StrUtil.isNotBlank(dockerConfig.getVolumeMount())) {
                        this.volumeMount = dockerConfig.getVolumeMount();
                        super.setVolumeMount(this.volumeMount);
                    }
                    if (cn.hutool.core.util.StrUtil.isNotBlank(dockerConfig.getLogPathPrefix())) {
                        this.logPathPrefix = dockerConfig.getLogPathPrefix();
                    }

                    // 加载SSH配置（如果存在）
                    ai.config.pojo.DiscriminativeModelsConfig.SshConfig sshConfig =
                        discriminativeConfig.getEffectiveSshConfig(tracknetv3Config);
                    if (sshConfig != null && sshConfig.isValid()) {
                        this.sshHost = sshConfig.getHost();
                        this.sshPort = sshConfig.getPort();
                        this.sshUsername = sshConfig.getUsername();
                        this.sshPassword = sshConfig.getPassword();
                    }

                    log.info("从lagi.yml加载TrackNetV3训练器配置成功");
                } else {
                    log.warn("lagi.yml中未配置tracknetv3.docker，使用默认值");
                }
            }
        } catch (Exception e) {
            log.warn("加载配置失败，使用默认值: {}", e.getMessage());
        }
    }

    /**
     * 启动训练任务
     */
    @Override
    public String startTraining(String taskId, String trackId, JSONObject config) {
        try {
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

            // 如果没有指定训练日志文件路径，自动生成到指定目录
            if (!config.containsKey("train_log_file") || config.getStr("train_log_file") == null || config.getStr("train_log_file").isEmpty()) {
                // 从volumeMount中解析容器内路径，或使用默认值
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

            // 确保训练日志目录存在（容器内路径）
            String trainLogFile = config.getStr("train_log_file");
            if (trainLogFile != null && volumeMount != null && volumeMount.contains(":")) {
                String[] mountParts = volumeMount.split(":");
                if (mountParts.length >= 2) {
                    String containerPath = mountParts[1];
                    String hostPath = mountParts[0];
                    if (trainLogFile.startsWith(containerPath)) {
                        String logDir = trainLogFile.substring(0, trainLogFile.lastIndexOf("/"));
                        // 通过 SSH 在宿主机上创建目录（宿主机路径）
                        String hostLogDir = logDir.replace(containerPath, hostPath);
                        String mkdirCommand = "mkdir -p " + hostLogDir;
                        executeRemoteCommand(mkdirCommand);
                    }
                }
            }

            // 构建 Docker 命令
            StringBuilder dockerCmd = new StringBuilder();
            dockerCmd.append("docker run -d"); // -d 后台运行

            // 添加容器名称，便于后续管理
            String containerName = generateContainerName("tracknetv3_train");
            dockerCmd.append(" --name ").append(containerName);

            // GPU 支持
            dockerCmd.append(" --gpus all");

            // 共享内存大小（TrackNetV3 需要）
            dockerCmd.append(" --shm-size=2g");

            // 数据卷挂载
            dockerCmd.append(" -v ").append(volumeMount);

            // 配置环境变量（使用单引号包裹 JSON）
            String configJson = config.toString();
            configJson = configJson.replace("'", "'\\''");
            dockerCmd.append(" -e CONFIG='").append(configJson).append("'");

            // 镜像名称
            dockerCmd.append(" ").append(dockerImage);

            String fullCommand = dockerCmd.toString();
            log.info("开始启动训练任务: taskId={}, trackId={}", taskId, trackId);
            log.info("执行命令: {}", fullCommand);

            // 保存启动任务到数据库
            saveStartTrainingToDB(taskId, trackId, containerName, config);
            // 添加启动训练任务日志
            addTrackNetV3TrainingLog(taskId, "INFO", "TrackNetV3训练任务已启动，容器名称: " + containerName);

            String result = executeRemoteCommand(fullCommand);

            // 如果成功，将容器名称添加到结果中
            if (isSuccess(result)) {
                JSONObject resultJson = JSONUtil.parseObj(result);
                resultJson.put("containerName", containerName);
                resultJson.put("taskId", taskId);
                resultJson.put("trackId", trackId);

                // 更新数据库为运行中状态
                updateTrackNetV3TaskStatus(taskId, "running", "训练任务启动成功");

                // 获取训练日志文件路径（宿主机路径），用于记录到数据库
                String hostLogFilePath = null;
                if (trainLogFile != null && volumeMount != null && volumeMount.contains(":")) {
                    String[] mountParts = volumeMount.split(":");
                    if (mountParts.length >= 2) {
                        String containerPath = mountParts[1];
                        String hostPath = mountParts[0];
                        if (trainLogFile.startsWith(containerPath)) {
                            hostLogFilePath = trainLogFile.replace(containerPath, hostPath);
                        }
                    }
                }
                addTrackNetV3TrainingLog(taskId, "INFO", "容器启动成功，开始训练", hostLogFilePath);

                return resultJson.toString();
            } else {
                // 启动失败，更新数据库状态
                updateTrackNetV3TaskStatus(taskId, "failed", "容器启动失败");
                addTrackNetV3TrainingLog(taskId, "ERROR", "容器启动失败: " + result);
            }

            return result;

        } catch (Exception e) {
            log.error("启动训练任务失败", e);
            // 更新数据库状态为失败
            updateTrackNetV3TaskStatus(taskId, "failed", "启动训练任务异常: " + e.getMessage());
            addTrackNetV3TrainingLog(taskId, "ERROR", "启动训练任务失败: " + e.getMessage());

            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("message", "启动训练任务失败");
            errorResult.put("error", e.getMessage());
            return errorResult.toString();
        }
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
            if (!config.containsKey("train_log_file")) {
                config.put("train_log_file", "/app/data/valuate_" + taskId + ".log");
            }
            config.put("task_id", taskId);

            // 保存评估任务到数据库
            saveEvaluateTaskToDB(taskId, config);
            addTrackNetV3TrainingLog(taskId, "INFO", "开始执行评估任务");

            // 构建 Docker 命令
            StringBuilder dockerCmd = new StringBuilder();
            dockerCmd.append("docker run --rm "); // --rm 自动删除容器
            dockerCmd.append(" --gpus all");

            // 数据卷挂载
            dockerCmd.append(" -v ").append(volumeMount);

            // 配置环境变量
            String configJson = config.toString();
            configJson = configJson.replace("'", "'\\''");
            dockerCmd.append(" -e CONFIG='").append(configJson).append("'");

            // 镜像名称
            dockerCmd.append(" ").append(dockerImage);

            String fullCommand = dockerCmd.toString();
            log.info("开始执行评估任务: taskId={}", taskId);
            log.info("执行命令: {}", fullCommand);

            String result = executeRemoteCommand(fullCommand);

            // 更新评估任务状态
            if (isSuccess(result)) {
                updateTrackNetV3TaskStatus(taskId, "completed", "评估任务完成");
                addTrackNetV3TrainingLog(taskId, "INFO", "评估任务完成");
            } else {
                updateTrackNetV3TaskStatus(taskId, "failed", "评估任务失败");
                addTrackNetV3TrainingLog(taskId, "ERROR", "评估任务失败: " + result);
            }

            return result;

        } catch (Exception e) {
            log.error("执行评估任务失败", e);
            updateTrackNetV3TaskStatus(taskId, "failed", "评估任务异常: " + e.getMessage());
            addTrackNetV3TrainingLog(taskId, "ERROR", "评估任务异常: " + e.getMessage());

            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("message", "执行评估任务失败");
            errorResult.put("error", e.getMessage());
            return errorResult.toString();
        }
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

            // 确保推理日志目录存在（容器内路径）
            String predictLogFile = config.getStr("train_log_file");
            if (predictLogFile != null && volumeMount != null && volumeMount.contains(":")) {
                String[] mountParts = volumeMount.split(":");
                if (mountParts.length >= 2) {
                    String containerPath = mountParts[1];
                    String hostPath = mountParts[0];
                    if (predictLogFile.startsWith(containerPath)) {
                        String logDir = predictLogFile.substring(0, predictLogFile.lastIndexOf("/"));
                        // 通过 SSH 在宿主机上创建目录（宿主机路径）
                        String hostLogDir = logDir.replace(containerPath, hostPath);
                        String mkdirCommand = "mkdir -p " + hostLogDir;
                        executeRemoteCommand(mkdirCommand);
                    }
                }
            }

            // 保存预测任务到数据库
            savePredictTaskToDB(taskId, config);
            addTrackNetV3PredictLog(taskId, "INFO", "开始执行预测任务");

            // 构建 Docker 命令
            StringBuilder dockerCmd = new StringBuilder();
            dockerCmd.append("docker run --rm");
            dockerCmd.append(" --gpus all");

            // 数据卷挂载
            dockerCmd.append(" -v ").append(volumeMount);

            // 配置环境变量
            String configJson = config.toString();
            configJson = configJson.replace("'", "'\\''");
            dockerCmd.append(" -e CONFIG='").append(configJson).append("'");

            // 镜像名称
            dockerCmd.append(" ").append(dockerImage);

            String fullCommand = dockerCmd.toString();
            log.info("开始执行预测任务: taskId={}", taskId);
            log.info("执行命令: {}", fullCommand);

            String result = executeRemoteCommand(fullCommand);

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
                updateTrackNetV3TaskStatus(taskId, "completed", "预测任务完成");
                addTrackNetV3PredictLog(taskId, "INFO", "预测任务完成", hostLogFilePath);
                
                // 确保日志文件已上传到指定路径
                uploadPredictLogFile(taskId, hostLogFilePath);
            } else {
                updateTrackNetV3TaskStatus(taskId, "failed", "预测任务失败");
                addTrackNetV3PredictLog(taskId, "ERROR", "预测任务失败: " + result, hostLogFilePath);
            }

            return result;

        } catch (Exception e) {
            log.error("执行预测任务失败", e);
            updateTrackNetV3TaskStatus(taskId, "failed", "预测任务异常: " + e.getMessage());
            addTrackNetV3PredictLog(taskId, "ERROR", "预测任务异常: " + e.getMessage(), null);

            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("message", "执行预测任务失败");
            errorResult.put("error", e.getMessage());
            return errorResult.toString();
        }
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
            if (!config.containsKey("train_log_file")) {
                config.put("train_log_file", "/app/data/export_" + taskId + ".log");
            }
            config.put("task_id", taskId);

            // 保存导出任务到数据库
            saveExportTaskToDB(taskId, config);
            addTrackNetV3TrainingLog(taskId, "INFO", "开始导出模型");

            // 构建 Docker 命令
            StringBuilder dockerCmd = new StringBuilder();
            dockerCmd.append("docker run --rm");
            dockerCmd.append(" --gpus all");

            // 数据卷挂载
            dockerCmd.append(" -v ").append(volumeMount);

            // 配置环境变量
            String configJson = config.toString();
            configJson = configJson.replace("'", "'\\''");
            dockerCmd.append(" -e CONFIG='").append(configJson).append("'");

            // 镜像名称
            dockerCmd.append(" ").append(dockerImage);

            String fullCommand = dockerCmd.toString();
            log.info("开始导出模型: taskId={}", taskId);
            log.info("执行命令: {}", fullCommand);

            String result = executeRemoteCommand(fullCommand);

            // 更新导出任务状态
            if (isSuccess(result)) {
                updateTrackNetV3TaskStatus(taskId, "completed", "模型导出完成");
                addTrackNetV3TrainingLog(taskId, "INFO", "模型导出完成");
            } else {
                updateTrackNetV3TaskStatus(taskId, "failed", "模型导出失败");
                addTrackNetV3TrainingLog(taskId, "ERROR", "模型导出失败: " + result);
            }

            return result;

        } catch (Exception e) {
            log.error("导出模型失败", e);
            updateTrackNetV3TaskStatus(taskId, "failed", "模型导出异常: " + e.getMessage());
            addTrackNetV3TrainingLog(taskId, "ERROR", "模型导出异常: " + e.getMessage());

            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("message", "导出模型失败");
            errorResult.put("error", e.getMessage());
            return errorResult.toString();
        }
    }

    /**
     * 暂停容器（带业务逻辑）
     */
    @Override
    public String pauseContainer(String containerId) {
        String result = super.pauseContainer(containerId);

        // 从containerId获取taskId
        String taskId = getTaskIdByContainerId(containerId);

        if (isSuccess(result)) {
            // 更新数据库状态为暂停
            if (taskId != null) {
                updateTrackNetV3TaskStatus(taskId, "paused", "容器已暂停");
                addTrackNetV3TrainingLog(taskId, "INFO", "容器已暂停: " + containerId);
            }
        } else {
            // 暂停失败，记录日志
            if (taskId != null) {
                addTrackNetV3TrainingLog(taskId, "ERROR", "暂停容器失败: " + result);
            }
        }

        return result;
    }

    /**
     * 继续容器（恢复暂停的容器，带业务逻辑）
     */
    @Override
    public String resumeContainer(String containerId) {
        String result = super.resumeContainer(containerId);

        // 从containerId获取taskId
        String taskId = getTaskIdByContainerId(containerId);

        if (isSuccess(result)) {
            // 更新数据库状态为运行中
            if (taskId != null) {
                updateTrackNetV3TaskStatus(taskId, "running", "容器已恢复运行");
                addTrackNetV3TrainingLog(taskId, "INFO", "容器已恢复运行: " + containerId);
            }
        } else {
            // 恢复失败，记录日志
            if (taskId != null) {
                addTrackNetV3TrainingLog(taskId, "ERROR", "恢复容器失败: " + result);
            }
        }

        return result;
    }

    /**
     * 停止容器（带业务逻辑）
     */
    @Override
    public String stopContainer(String containerId) {
        String result = super.stopContainer(containerId);

        // 从containerId获取taskId
        String taskId = getTaskIdByContainerId(containerId);

        if (isSuccess(result)) {
            // 更新数据库状态为已停止，并记录结束时间
            if (taskId != null) {
                String endTime = getCurrentTime();
                updateTrackNetV3TaskStopStatus(taskId, endTime);
                addTrackNetV3TrainingLog(taskId, "INFO", "容器已停止: " + containerId);
            }
        } else {
            // 停止失败，记录日志
            if (taskId != null) {
                addTrackNetV3TrainingLog(taskId, "ERROR", "停止容器失败: " + result);
            }
        }

        return result;
    }

    /**
     * 删除容器（带业务逻辑）
     */
    @Override
    public String removeContainer(String containerId) {
        String result = super.removeContainer(containerId);

        // 从containerId获取taskId
        String taskId = getTaskIdByContainerId(containerId);

        if (isSuccess(result)) {
            // 软删除数据库记录
            if (taskId != null) {
                deleteTrackNetV3Task(taskId);
                addTrackNetV3TrainingLog(taskId, "INFO", "容器已删除: " + containerId);
            }
        } else {
            // 删除失败，记录日志
            if (taskId != null) {
                addTrackNetV3TrainingLog(taskId, "ERROR", "删除容器失败: " + result);
            }
        }

        return result;
    }

    /**
     * 查看容器状态（带业务逻辑）
     */
    @Override
    public String getContainerStatus(String containerId) {
        String result = super.getContainerStatus(containerId);

        if (isSuccess(result)) {
            try {
                JSONObject resultJson = JSONUtil.parseObj(result);
                String status = resultJson.getStr("containerStatus", "").trim();

                String[] parts = status.split(";");
                String statusPart = parts.length > 0 ? parts[0].trim() : "";
                String exitCodeStr = parts.length > 1 ? parts[1].trim() : "";

                // 检查容器退出码，判断任务是否失败
                if (!exitCodeStr.isEmpty()) {
                    try {
                        int exitCode = Integer.parseInt(exitCodeStr);
                        if (exitCode != 0 && "exited".equals(statusPart)) {
                            // 容器已退出且退出码不为0，表示任务失败
                            String taskId = getTaskIdByContainerId(containerId);
                            if (taskId != null) {
                                updateTrackNetV3TaskStatus(taskId, "failed", "容器异常退出，退出码: " + exitCode);
                                addTrackNetV3TrainingLog(taskId, "ERROR", "容器异常退出，退出码: " + exitCode);
                            }
                        }
                    } catch (NumberFormatException e) {
                        log.warn("解析退出码失败: {}", exitCodeStr);
                    }
                }
            } catch (Exception e) {
                log.warn("处理容器状态结果失败: {}", e.getMessage());
            }
        }

        return result;
    }



    // ==================== 数据库操作方法（CRUD） ====================

    /**
     * 获取数据库连接池适配器实例（单例模式，双重检查锁定）
     */
    private static MysqlAdapter getMysqlAdapter() {
        if (mysqlAdapter == null) {
            synchronized (TrackNetV3Adapter.class) {
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
     */
    private static ModelConfigManager getModelConfigManager() {
        if (modelConfigManager == null) {
            synchronized (TrackNetV3Adapter.class) {
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
     * 保存启动训练任务到数据库
     */
    private void saveStartTrainingToDB(String taskId, String trackId, String containerName, JSONObject config) {
        // 添加额外参数到配置中
        config.put("track_id", trackId);
        config.put("_container_name", containerName);
        config.put("_docker_image", dockerImage);
        config.put("_status", "starting");

        String currentTime = getCurrentTime();
        String sql = "INSERT INTO ai_training_tasks " +
                "(task_id, track_id, model_name, model_category, model_framework, task_type, " +
                "container_name, container_id, docker_image, gpu_ids, use_gpu, " +
                "dataset_path, model_path, epochs, batch_size, image_size, optimizer, " +
                "status, progress, current_epoch, start_time, created_at, is_deleted, user_id, config_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        // 从配置中读取基本信息
        String modelName = config.getStr("model_name", "tracknetv3");
        String modelCategory = getModelCategory(modelName, config);
        String modelFramework = getModelFramework(modelName, config);
        String datasetPath = config.getStr("data_dir", "");
        String modelPath = config.getStr("model_path", "");
        Integer epochs = config.getInt("epochs", null);
        Integer batchSize = config.getInt("batch_size", null);
        String inputShape = String.valueOf(config.getInt("num_frame", 3));
        String cuda = config.getBool("use_gpu", true) ? "0" : "cpu";
        String optimizer = "adam"; // TrackNetV3 默认使用 adam
        String userId = config.getStr("user_id", null);

        try {
            getMysqlAdapter().executeUpdate(sql,
                    taskId, trackId, modelName, modelCategory, modelFramework,
                    "train", containerName, "", dockerImage,
                    cuda, config.getBool("use_gpu", true) ? 1 : 0,
                    datasetPath, modelPath, epochs, batchSize, inputShape, optimizer,
                    "starting", "0%", 0, currentTime, currentTime, 0, userId, config.toString());

            log.info("训练任务已保存到数据库: taskId={}, modelName={}, category={}, framework={}",
                    taskId, modelName, modelCategory, modelFramework);
        } catch (Exception e) {
            log.error("保存训练任务到数据库失败: taskId={}, error={}", taskId, e.getMessage(), e);
        }
    }

    /**
     * 保存评估任务到数据库
     */
    private void saveEvaluateTaskToDB(String taskId, JSONObject config) {
        String sql = "INSERT INTO ai_training_tasks " +
                "(task_id, track_id, model_name, model_category, model_framework, task_type, " +
                "container_name, dataset_path, model_path, image_size, optimizer, " +
                "status, progress, current_epoch, start_time, created_at, is_deleted, config_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try {
            String modelName = config.getStr("model_name", "tracknetv3");
            String modelCategory = getModelCategory(modelName, config);
            String modelFramework = getModelFramework(modelName, config);
            String datasetPath = config.getStr("data_dir", "");
            String modelPath = config.getStr("model_path", "");
            String inputShape = String.valueOf(config.getInt("num_frames", 3));
            String currentTime = getCurrentTime();

            getMysqlAdapter().executeUpdate(sql,
                    taskId, "", modelName, modelCategory, modelFramework,
                    "evaluate", "", datasetPath, modelPath, inputShape, "adam",
                    "running", "0%", 0, currentTime, currentTime, 0, config.toString());

            log.info("评估任务已保存到数据库: taskId={}, modelName={}, category={}, framework={}",
                    taskId, modelName, modelCategory, modelFramework);
        } catch (Exception e) {
            log.error("保存评估任务到数据库失败: taskId={}, error={}", taskId, e.getMessage(), e);
        }
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
            String modelName = config.getStr("model_name", "tracknetv3");
            String modelCategory = getModelCategory(modelName, config);
            String modelFramework = getModelFramework(modelName, config);
            String modelPath = config.getStr("model_path", "");
            String cuda = config.getBool("use_gpu", true) ? "0" : "cpu";
            String currentTime = getCurrentTime();

            getMysqlAdapter().executeUpdate(sql,
                    taskId, "", modelName, modelCategory, modelFramework,
                    "predict", "", modelPath, cuda, config.getBool("use_gpu", true) ? 1 : 0,
                    "running", "0%", 0, currentTime, currentTime, 0, config.toString());

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
            String modelName = config.getStr("model_name", "tracknetv3");
            String modelCategory = getModelCategory(modelName, config);
            String modelFramework = getModelFramework(modelName, config);
            String modelPath = config.getStr("model_path", "");
            String cuda = config.getBool("use_gpu", false) ? "0" : "cpu";
            String currentTime = getCurrentTime();

            getMysqlAdapter().executeUpdate(sql,
                    taskId, "", modelName, modelCategory, modelFramework,
                    "export", "", modelPath, cuda, config.getBool("use_gpu", false) ? 1 : 0,
                    "running", "0%", 0, currentTime, currentTime, 0, config.toString());

            log.info("导出任务已保存到数据库: taskId={}, modelName={}, category={}, framework={}",
                    taskId, modelName, modelCategory, modelFramework);
        } catch (Exception e) {
            log.error("保存导出任务到数据库失败: taskId={}, error={}", taskId, e.getMessage(), e);
        }
    }

    /**
     * 更新TrackNetV3任务状态
     */
    private void updateTrackNetV3TaskStatus(String taskId, String status, String message) {
        String sql = "UPDATE ai_training_tasks " +
                "SET status = ?, error_message = ?, updated_at = ? " +
                "WHERE task_id = ?";
        try {
            String currentTime = getCurrentTime();
            getMysqlAdapter().executeUpdate(sql, status, message, currentTime, taskId);
            log.info("任务状态已更新: taskId={}, status={}", taskId, status);
        } catch (Exception e) {
            log.error("更新任务状态失败: taskId={}, status={}, error={}", taskId, status, e.getMessage(), e);
        }
    }

    /**
     * 更新TrackNetV3任务停止状态（包含结束时间）
     */
    private void updateTrackNetV3TaskStopStatus(String taskId, String endTime) {
        String sql = "UPDATE ai_training_tasks " +
                "SET status = ?, end_time = ?, updated_at = ? " +
                "WHERE task_id = ?";
        try {
            String currentTime = getCurrentTime();
            getMysqlAdapter().executeUpdate(sql, "stopped", endTime, currentTime, taskId);
            log.info("任务已停止: taskId={}, endTime={}", taskId, endTime);
        } catch (Exception e) {
            log.error("更新任务停止状态失败: taskId={}, error={}", taskId, e.getMessage(), e);
        }
    }

    /**
     * 软删除TrackNetV3任务
     */
    private void deleteTrackNetV3Task(String taskId) {
        String sql = "UPDATE ai_training_tasks " +
                "SET is_deleted = 1, deleted_at = ? " +
                "WHERE task_id = ?";
        try {
            String currentTime = getCurrentTime();
            getMysqlAdapter().executeUpdate(sql, currentTime, taskId);
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
     * 添加TrackNetV3训练日志到数据库（仅系统操作日志）
     */
    private void addTrackNetV3TrainingLog(String taskId, String logLevel, String logMessage) {
        addTrackNetV3TrainingLog(taskId, logLevel, logMessage, null);
    }

    /**
     * 添加TrackNetV3训练日志到数据库（仅系统操作日志）
     * @param taskId 任务ID
     * @param logLevel 日志级别
     * @param logMessage 日志消息
     * @param trainingLogFilePath 训练日志文件路径（宿主机路径，可选，用于记录实际训练日志文件位置）
     */
    private void addTrackNetV3TrainingLog(String taskId, String logLevel, String logMessage, String trainingLogFilePath) {
        String currentTime = getCurrentTime();
        // 使用实际的训练日志文件路径（如果提供），否则使用默认路径
        String defaultLogPath = logPathPrefix != null ? logPathPrefix : "/data/wangshuanglong/log/train/";
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
            List<Map<String, Object>> result = getMysqlAdapter().select(checkSql, taskId);
            if (result != null && !result.isEmpty() && result.get(0).get("cnt") != null) {
                logCount = ((Number) result.get(0).get("cnt")).longValue();
            }

            if (logCount > 0) {
                // 若存在日志，追加内容
                String updateSql = "UPDATE ai_training_logs " +
                        "SET log_message = CONCAT(IFNULL(log_message, ''), ?), " +
                        "log_level = ?, " +
                        "created_at = ? " +
                        "WHERE task_id = ?";
                getMysqlAdapter().executeUpdate(updateSql, logEntry, logLevel, currentTime, taskId);
            } else {
                // 若不存在日志，直接插入新记录
                String insertSql = "INSERT INTO ai_training_logs " +
                        "(task_id, log_level, log_message, created_at, log_file_path) " +
                        "VALUES (?, ?, ?, ?, ?)";
                getMysqlAdapter().executeUpdate(insertSql,
                        taskId, logLevel, logEntry, currentTime, logFilePath);
            }
        } catch (Exception e) {
            log.error("添加训练日志到数据库失败: taskId={}, error={}", taskId, e.getMessage(), e);
        }
    }

    /**
     * 添加TrackNetV3推理日志到数据库（仅系统操作日志）
     * @param taskId 任务ID
     * @param logLevel 日志级别
     * @param logMessage 日志消息
     */
    private void addTrackNetV3PredictLog(String taskId, String logLevel, String logMessage) {
        addTrackNetV3PredictLog(taskId, logLevel, logMessage, null);
    }

    /**
     * 添加TrackNetV3推理日志到数据库（仅系统操作日志）
     * @param taskId 任务ID
     * @param logLevel 日志级别
     * @param logMessage 日志消息
     * @param predictLogFilePath 推理日志文件路径（宿主机路径，可选，用于记录实际推理日志文件位置）
     */
    private void addTrackNetV3PredictLog(String taskId, String logLevel, String logMessage, String predictLogFilePath) {
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
                getMysqlAdapter().executeUpdate(insertSql,
                        taskId, logLevel, logEntry, currentTime, logFilePath);
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

            // 确保目标目录存在
            String mkdirCommand = "mkdir -p " + targetLogPath;
            executeRemoteCommand(mkdirCommand);

            // 如果源文件存在，复制到目标路径
            if (sourceLogFilePath != null && !sourceLogFilePath.isEmpty()) {
                // 检查源文件是否存在
                String checkFileCommand = "test -f " + sourceLogFilePath + " && echo 'exists' || echo 'not_exists'";
                String checkResult = executeRemoteCommand(checkFileCommand);
                JSONObject checkJson = JSONUtil.parseObj(checkResult);
                String output = checkJson.getStr("output", "").trim();

                if ("exists".equals(output)) {
                    // 复制文件到目标路径
                    String copyCommand = "cp " + sourceLogFilePath + " " + targetLogFile;
                    String copyResult = executeRemoteCommand(copyCommand);
                    if (isSuccess(copyResult)) {
                        log.info("推理日志文件已上传: taskId={}, targetPath={}", taskId, targetLogFile);
                        addTrackNetV3PredictLog(taskId, "INFO", "推理日志文件已上传到: " + targetLogFile, targetLogFile);
                    } else {
                        log.warn("推理日志文件上传失败: taskId={}, sourcePath={}, targetPath={}", taskId, sourceLogFilePath, targetLogFile);
                        addTrackNetV3PredictLog(taskId, "WARN", "推理日志文件上传失败: " + copyResult, null);
                    }
                } else {
                    log.warn("推理日志源文件不存在: taskId={}, sourcePath={}", taskId, sourceLogFilePath);
                    addTrackNetV3PredictLog(taskId, "WARN", "推理日志源文件不存在: " + sourceLogFilePath, null);
                }
            } else {
                log.warn("无法确定推理日志源文件路径: taskId={}", taskId);
                addTrackNetV3PredictLog(taskId, "WARN", "无法确定推理日志源文件路径", null);
            }

        } catch (Exception e) {
            log.error("上传推理日志文件失败: taskId={}, error={}", taskId, e.getMessage(), e);
            addTrackNetV3PredictLog(taskId, "ERROR", "上传推理日志文件失败: " + e.getMessage(), null);
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
     * TrackNetV3 应该是 "tracking"
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

        // TrackNet 相关模型都是跟踪模型
        if (lowerModelName.contains("tracknet") || lowerModelName.contains("track") ||
            lowerModelName.contains("tracking") || lowerModelName.contains("motion")) {
            log.debug("根据名称推断为跟踪模型: modelName={}", modelName);
            return "tracking";
        }

        // 3. 默认返回 tracking（TrackNetV3 是跟踪模型）
        log.info("使用默认类别 'tracking': modelName={}", modelName);
        return "tracking";
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
        } else if (lowerModelName.contains("tensorflow") || lowerModelName.contains("tf")) {
            return "tensorflow";
        }

        // 默认返回 pytorch（TrackNetV3 通常使用 PyTorch）
        return "pytorch";
    }

    /**
     * 获取 TrainingTaskRepository 实例
     * 用于查询训练任务列表等操作
     *
     * @return TrainingTaskRepository 实例
     */
    public ai.finetune.repository.TrainingTaskRepository getRepository() {
        if (mysqlAdapter == null) {
            mysqlAdapter = getMysqlAdapter();
        }
        return new ai.finetune.repository.TrainingTaskRepository(mysqlAdapter);
    }
}

