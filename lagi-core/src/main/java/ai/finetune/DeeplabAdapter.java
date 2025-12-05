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
 * DeepLab 语义分割模型训练实现类
 * 基于 Docker 容器进行 DeepLab 模型的训练、评估、预测等操作
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
public class DeeplabAdapter extends DockerTrainerAbstract {

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
    public DeeplabAdapter() {
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
    public DeeplabAdapter(String sshHost, int sshPort, String sshUsername, String sshPassword) {
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

                ai.config.pojo.DiscriminativeModelsConfig.DeeplabConfig deeplabConfig =
                    discriminativeConfig.getDeeplab();

                if (deeplabConfig != null && deeplabConfig.getDocker() != null) {
                    ai.config.pojo.DiscriminativeModelsConfig.DockerConfig dockerConfig =
                        deeplabConfig.getDocker();

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

                    log.info("从lagi.yml加载DeepLab训练器配置成功");
                } else {
                    log.warn("lagi.yml中未配置deeplab.docker，使用默认值");
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
//            dockerCmd.append("docker run --rm -d"); // -d 后台运行
            dockerCmd.append("docker run -d"); // -d 后台运行

            // 添加容器名称，便于后续管理
            String containerName = generateContainerName("deeplab_train");
            dockerCmd.append(" --name ").append(containerName);

            // GPU 支持
            dockerCmd.append(" --gpus all");

            // 共享内存大小（Deeplab 需要）
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
            addDeeplabTrainingLog(taskId, "INFO", "DeepLab训练任务已启动，容器名称: " + containerName);

            String result = executeRemoteCommand(fullCommand);

            // 如果成功，将容器名称添加到结果中
            if (isSuccess(result)) {
                JSONObject resultJson = JSONUtil.parseObj(result);
                resultJson.put("containerName", containerName);
                resultJson.put("taskId", taskId);
                resultJson.put("trackId", trackId);

                // 更新数据库为运行中状态
                updateDeeplabTaskStatus(taskId, "running", "训练任务启动成功");

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
                addDeeplabTrainingLog(taskId, "INFO", "容器启动成功，开始训练", hostLogFilePath);

                return resultJson.toString();
            } else {
                // 启动失败，更新数据库状态
                updateDeeplabTaskStatus(taskId, "failed", "容器启动失败");
                addDeeplabTrainingLog(taskId, "ERROR", "容器启动失败: " + result);
            }

            return result;

        } catch (Exception e) {
            log.error("启动训练任务失败", e);
            // 更新数据库状态为失败
            updateDeeplabTaskStatus(taskId, "failed", "启动训练任务异常: " + e.getMessage());
            addDeeplabTrainingLog(taskId, "ERROR", "启动训练任务失败: " + e.getMessage());

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
            if (!config.containsKey("train_log_file")) {
                config.put("train_log_file", "/app/data/valuate_" + taskId + ".log");
            }
            config.put("task_id", taskId);

            // 保存评估任务到数据库
            saveEvaluateTaskToDB(taskId, config);
            addDeeplabTrainingLog(taskId, "INFO", "开始执行评估任务");

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
                updateDeeplabTaskStatus(taskId, "completed", "评估任务完成");
                addDeeplabTrainingLog(taskId, "INFO", "评估任务完成");
            } else {
                updateDeeplabTaskStatus(taskId, "failed", "评估任务失败");
                addDeeplabTrainingLog(taskId, "ERROR", "评估任务失败: " + result);
            }

            return result;

        } catch (Exception e) {
            log.error("执行评估任务失败", e);
            updateDeeplabTaskStatus(taskId, "failed", "评估任务异常: " + e.getMessage());
            addDeeplabTrainingLog(taskId, "ERROR", "评估任务异常: " + e.getMessage());

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
            if (!config.containsKey("track_id")) {
                config.put("track_id", "");
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
            addDeeplabPredictLog(taskId, "INFO", "开始执行预测任务");

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
                updateDeeplabTaskStatus(taskId, "completed", "预测任务完成");
                updateDeeplabTaskProgress(taskId, "100%");
                addDeeplabPredictLog(taskId, "INFO", "预测任务完成", hostLogFilePath);
                
                // 确保日志文件已上传到指定路径
                uploadPredictLogFile(taskId, hostLogFilePath);
            } else {
                updateDeeplabTaskStatus(taskId, "failed", "预测任务失败");
                addDeeplabPredictLog(taskId, "ERROR", "预测任务失败: " + result, hostLogFilePath);
            }

            return result;

        } catch (Exception e) {
            log.error("执行预测任务失败", e);
            updateDeeplabTaskStatus(taskId, "failed", "预测任务异常: " + e.getMessage());
            addDeeplabPredictLog(taskId, "ERROR", "预测任务异常: " + e.getMessage(), null);

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
            if (!config.containsKey("train_log_file")) {
                config.put("train_log_file", "/app/data/export_" + taskId + ".log");
            }
            config.put("task_id", taskId);

            // 保存导出任务到数据库
            saveExportTaskToDB(taskId, config);
            addDeeplabTrainingLog(taskId, "INFO", "开始导出模型");

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
                updateDeeplabTaskStatus(taskId, "completed", "模型导出完成");
                addDeeplabTrainingLog(taskId, "INFO", "模型导出完成");
            } else {
                updateDeeplabTaskStatus(taskId, "failed", "模型导出失败");
                addDeeplabTrainingLog(taskId, "ERROR", "模型导出失败: " + result);
            }

            return result;

        } catch (Exception e) {
            log.error("导出模型失败", e);
            updateDeeplabTaskStatus(taskId, "failed", "模型导出异常: " + e.getMessage());
            addDeeplabTrainingLog(taskId, "ERROR", "模型导出异常: " + e.getMessage());

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
    @Override
    public String pauseContainer(String containerId) {
        String result = super.pauseContainer(containerId);

        // 从containerId获取taskId
        String taskId = getTaskIdByContainerId(containerId);

        if (isSuccess(result)) {
            // 更新数据库状态为暂停
            if (taskId != null) {
                updateDeeplabTaskStatus(taskId, "paused", "容器已暂停");
                addDeeplabTrainingLog(taskId, "INFO", "容器已暂停: " + containerId);
            }
        } else {
            // 暂停失败，记录日志
            if (taskId != null) {
                addDeeplabTrainingLog(taskId, "ERROR", "暂停容器失败: " + result);
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
                updateDeeplabTaskStatus(taskId, "running", "容器已恢复运行");
                addDeeplabTrainingLog(taskId, "INFO", "容器已恢复运行: " + containerId);
            }
        } else {
            // 恢复失败，记录日志
            if (taskId != null) {
                addDeeplabTrainingLog(taskId, "ERROR", "恢复容器失败: " + result);
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
                updateDeeplabTaskStopStatus(taskId, endTime);
                addDeeplabTrainingLog(taskId, "INFO", "容器已停止: " + containerId);
            }
        } else {
            // 停止失败，记录日志
            if (taskId != null) {
                addDeeplabTrainingLog(taskId, "ERROR", "停止容器失败: " + result);
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
                deleteDeeplabTask(taskId);
                addDeeplabTrainingLog(taskId, "INFO", "容器已删除: " + containerId);
            }
        } else {
            // 删除失败，记录日志
            if (taskId != null) {
                addDeeplabTrainingLog(taskId, "ERROR", "删除容器失败: " + result);
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
                                updateDeeplabTaskStatus(taskId, "failed", "容器异常退出，退出码: " + exitCode);
                                addDeeplabTrainingLog(taskId, "ERROR", "容器异常退出，退出码: " + exitCode);
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



    // ==================== 配置创建方法（业务配置） ====================

    /**
     * 创建默认训练配置
     */
    public JSONObject createDefaultTrainConfig() {
        JSONObject config = new JSONObject();
        config.put("the_train_type", "train");
        // 默认训练日志文件路径（会在启动训练时根据 taskId 自动设置）
        config.put("train_log_file", "/app/data/log/train/train.log");
        config.put("model_path", "/app/data/models/deeplab_mobilenetv2.pth");
        config.put("dataset_path", "/app/data/datasets/deeplabv3/VOCdevkit");
        config.put("freeze_train", true);
        config.put("freeze_epoch", 50);
        config.put("freeze_batch_size", 8);
        config.put("epochs", 100);
        config.put("un_freeze_batch_size", 4);
        config.put("cuda", true);
        config.put("distributed", false);
        config.put("fp16", false);
        config.put("num_classes", 21);
        config.put("backbone", "mobilenet");
        config.put("input_shape", 512);
        config.put("save_dir", "/app/data/save_dir");
        config.put("save_period", 5);
        config.put("eval_flag", true);
        config.put("eval_period", 5);
        config.put("focal_loss", false);
        config.put("num_workers", 4);
        config.put("optimizer_type", "sgd");
        config.put("momentum", 0.9);
        config.put("weight_decay", 1e-4);
        config.put("init_lr", 7e-3);
        config.put("min_lr", 7e-5);
        return config;
    }

    /**
     * 创建默认评估配置
     */
    public JSONObject createDefaultEvaluateConfig() {
        JSONObject config = new JSONObject();
        config.put("the_train_type", "valuate");
        config.put("train_log_file", "/app/data/valuate.log");
        config.put("model_path", "/app/data/models/deeplab_mobilenetv2.pth");
        config.put("dataset_path", "/app/data/datasets/deeplabv3/VOCdevkit");
        config.put("num_classes", 21);
        config.put("cuda", true);
        return config;
    }

    /**
     * 创建默认预测配置
     */
    public JSONObject createDefaultPredictConfig() {
        JSONObject config = new JSONObject();
        config.put("the_train_type", "predict");
        config.put("train_log_file", "/app/data/predict.log");
        config.put("model_path", "/app/data/models/deeplab_mobilenetv2.pth");
        config.put("image_path", "/app/data/test.jpg");
        config.put("save_path", "/app/data/predict_result.jpg");
        config.put("mix_type", 0);
        config.put("cuda", true);
        return config;
    }

    /**
     * 创建默认导出配置
     */
    public JSONObject createDefaultExportConfig() {
        JSONObject config = new JSONObject();
        config.put("the_train_type", "export");
        config.put("train_log_file", "/app/data/export.log");
        config.put("model_path", "/app/data/models/deeplab_mobilenetv2.pth");
        config.put("export_dir", "/app/data/models/export/");
        config.put("format", "onnx");
        config.put("cuda", false);
        return config;
    }

    // ==================== 数据库操作方法（CRUD） ====================

    /**
     * 获取数据库连接池适配器实例（单例模式，双重检查锁定）
     */
    private static MysqlAdapter getMysqlAdapter() {
        if (mysqlAdapter == null) {
            synchronized (DeeplabAdapter.class) {
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
            synchronized (DeeplabAdapter.class) {
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
        String modelName = config.getStr("model_name", "deeplabv3");
        String modelCategory = getModelCategory(modelName, config);
        String modelFramework = getModelFramework(modelName, config);
        String datasetPath = config.getStr("dataset_path", "");
        String modelPath = config.getStr("model_path", "");
        Integer epochs = config.getInt("epochs", null);
        Integer batchSize = config.getInt("freeze_batch_size", null);
        String inputShape = String.valueOf(config.getInt("input_shape", 512));
        String cuda = config.getBool("cuda", true) ? "0" : "cpu";
        String optimizer = config.getStr("optimizer_type", "sgd");
        String userId = config.getStr("user_id", null);

        try {
            getMysqlAdapter().executeUpdate(sql,
                    taskId, trackId, modelName, modelCategory, modelFramework,
                    "train", containerName, "", dockerImage,
                    cuda, config.getBool("cuda", true) ? 1 : 0,
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
            String modelName = config.getStr("model_name", "deeplabv3");
            String modelCategory = getModelCategory(modelName, config);
            String modelFramework = getModelFramework(modelName, config);
            String datasetPath = config.getStr("dataset_path", "");
            String modelPath = config.getStr("model_path", "");
            String inputShape = String.valueOf(config.getInt("input_shape", 512));
            String currentTime = getCurrentTime();

            getMysqlAdapter().executeUpdate(sql,
                    taskId, "", modelName, modelCategory, modelFramework,
                    "evaluate", "", datasetPath, modelPath, inputShape, "sgd",
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
                "container_name, docker_image, model_path, gpu_ids, use_gpu, " +
                "status, progress, current_epoch, start_time, created_at, is_deleted, config_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try {
            String modelName = config.getStr("model_name", "deeplabv3");
            String trackId = config.getStr("track_id", "");
            String modelCategory = getModelCategory(modelName, config);
            String modelFramework = getModelFramework(modelName, config);
            String containerName = config.getStr("container_name", "");
            String dockerImage = config.getStr("docker_image", getDockerImage());
            String modelPath = config.getStr("model_path", "");
            String cuda = config.getBool("cuda", true) ? "0" : "cpu";
            String currentTime = getCurrentTime();

            getMysqlAdapter().executeUpdate(sql,
                    taskId, trackId, modelName, modelCategory, modelFramework,
                    "predict", containerName, dockerImage, modelPath, cuda, config.getBool("cuda", true) ? 1 : 0,
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
            String modelName = config.getStr("model_name", "deeplabv3");
            String modelCategory = getModelCategory(modelName, config);
            String modelFramework = getModelFramework(modelName, config);
            String modelPath = config.getStr("model_path", "");
            String cuda = config.getBool("cuda", false) ? "0" : "cpu";
            String currentTime = getCurrentTime();

            getMysqlAdapter().executeUpdate(sql,
                    taskId, "", modelName, modelCategory, modelFramework,
                    "export", "", modelPath, cuda, config.getBool("cuda", false) ? 1 : 0,
                    "running", "0%", 0, currentTime, currentTime, 0, config.toString());

            log.info("导出任务已保存到数据库: taskId={}, modelName={}, category={}, framework={}",
                    taskId, modelName, modelCategory, modelFramework);
        } catch (Exception e) {
            log.error("保存导出任务到数据库失败: taskId={}, error={}", taskId, e.getMessage(), e);
        }
    }

    /**
     * 更新DeepLab任务状态
     */
    private void updateDeeplabTaskStatus(String taskId, String status, String message) {
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
     * 更新DeepLab任务停止状态（包含结束时间）
     */
    private void updateDeeplabTaskStopStatus(String taskId, String endTime) {
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
     * 软删除DeepLab任务
     */
    private void deleteDeeplabTask(String taskId) {
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
     * 添加DeepLab训练日志到数据库（仅系统操作日志）
     */
    private void addDeeplabTrainingLog(String taskId, String logLevel, String logMessage) {
        addDeeplabTrainingLog(taskId, logLevel, logMessage, null);
    }

    /**
     * 添加DeepLab训练日志到数据库（仅系统操作日志）
     * @param taskId 任务ID
     * @param logLevel 日志级别
     * @param logMessage 日志消息
     * @param trainingLogFilePath 训练日志文件路径（宿主机路径，可选，用于记录实际训练日志文件位置）
     */
    private void addDeeplabTrainingLog(String taskId, String logLevel, String logMessage, String trainingLogFilePath) {
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
     * 添加DeepLab推理日志到数据库（仅系统操作日志）
     * @param taskId 任务ID
     * @param logLevel 日志级别
     * @param logMessage 日志消息
     */
    private void addDeeplabPredictLog(String taskId, String logLevel, String logMessage) {
        addDeeplabPredictLog(taskId, logLevel, logMessage, null);
    }

    /**
     * 添加DeepLab推理日志到数据库（仅系统操作日志）
     * @param taskId 任务ID
     * @param logLevel 日志级别
     * @param logMessage 日志消息
     * @param predictLogFilePath 推理日志文件路径（宿主机路径，可选，用于记录实际推理日志文件位置）
     */
    private void addDeeplabPredictLog(String taskId, String logLevel, String logMessage, String predictLogFilePath) {
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
                        addDeeplabPredictLog(taskId, "INFO", "推理日志文件已上传到: " + targetLogFile, targetLogFile);
                    } else {
                        log.warn("推理日志文件上传失败: taskId={}, sourcePath={}, targetPath={}", taskId, sourceLogFilePath, targetLogFile);
                        addDeeplabPredictLog(taskId, "WARN", "推理日志文件上传失败: " + copyResult, null);
                    }
                } else {
                    log.warn("推理日志源文件不存在: taskId={}, sourcePath={}", taskId, sourceLogFilePath);
                    addDeeplabPredictLog(taskId, "WARN", "推理日志源文件不存在: " + sourceLogFilePath, null);
                }
            } else {
                log.warn("无法确定推理日志源文件路径: taskId={}", taskId);
                addDeeplabPredictLog(taskId, "WARN", "无法确定推理日志源文件路径", null);
            }

        } catch (Exception e) {
            log.error("上传推理日志文件失败: taskId={}, error={}", taskId, e.getMessage(), e);
            addDeeplabPredictLog(taskId, "ERROR", "上传推理日志文件失败: " + e.getMessage(), null);
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
     * DeepLab 应该是 "segmentation"
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

        // DeepLab 相关模型都是分割模型
        if (lowerModelName.contains("deeplab") || lowerModelName.contains("unet") ||
            lowerModelName.contains("fcn") || lowerModelName.contains("pidnet") ||
            lowerModelName.contains("segnet") || lowerModelName.contains("maskrcnn") ||
            lowerModelName.contains("pspnet") || lowerModelName.contains("segmentation")) {
            log.debug("根据名称推断为分割模型: modelName={}", modelName);
            return "segmentation";
        }

        // 3. 默认返回 segmentation（DeepLab 是分割模型）
        log.info("使用默认类别 'segmentation': modelName={}", modelName);
        return "segmentation";
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

        // 默认返回 pytorch（DeepLab 通常使用 PyTorch）
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
