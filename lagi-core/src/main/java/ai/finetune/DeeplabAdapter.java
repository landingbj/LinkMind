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

    private static final String DEFAULT_DOCKER_IMAGE = "deeplabv3_trainer:last";
    private static final String DEFAULT_VOLUME_MOUNT = "/data/wangshuanglong:/app/data";
    private static final String LOG_PATH_PREFIX = "/data/deeplab_train_logs/";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
        this.dockerImage = DEFAULT_DOCKER_IMAGE;
        this.volumeMount = DEFAULT_VOLUME_MOUNT;
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
        this.dockerImage = DEFAULT_DOCKER_IMAGE;
        this.volumeMount = DEFAULT_VOLUME_MOUNT;
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
            if (!config.containsKey("train_log_file")) {
                config.put("train_log_file", "/app/data/train_" + taskId + ".log");
            }

            // 构建 Docker 命令
            StringBuilder dockerCmd = new StringBuilder();
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
                addDeeplabTrainingLog(taskId, "INFO", "容器启动成功，开始训练");

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
            dockerCmd.append("docker run --rm"); // --rm 自动删除容器
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
        String taskId = UUID.randomUUID().toString();
        try {
            // 确保配置中包含必要的字段
            if (!config.containsKey("the_train_type")) {
                config.put("the_train_type", "predict");
            }
            if (!config.containsKey("train_log_file")) {
                config.put("train_log_file", "/app/data/predict_" + taskId + ".log");
            }
            config.put("task_id", taskId);

            // 保存预测任务到数据库
            savePredictTaskToDB(taskId, config);
            addDeeplabTrainingLog(taskId, "INFO", "开始执行预测任务");

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

            // 更新预测任务状态
            if (isSuccess(result)) {
                updateDeeplabTaskStatus(taskId, "completed", "预测任务完成");
                addDeeplabTrainingLog(taskId, "INFO", "预测任务完成");
            } else {
                updateDeeplabTaskStatus(taskId, "failed", "预测任务失败");
                addDeeplabTrainingLog(taskId, "ERROR", "预测任务失败: " + result);
            }

            return result;

        } catch (Exception e) {
            log.error("执行预测任务失败", e);
            updateDeeplabTaskStatus(taskId, "failed", "预测任务异常: " + e.getMessage());
            addDeeplabTrainingLog(taskId, "ERROR", "预测任务异常: " + e.getMessage());

            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("message", "执行预测任务失败");
            errorResult.put("error", e.getMessage());
            return errorResult.toString();
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
     * 暂停容器
     */
    @Override
    public String pauseContainer(String containerId) {
        try {
            String command = "docker pause " + containerId;
            log.info("暂停容器: {}", containerId);

            // 从containerId获取taskId
            String taskId = getTaskIdByContainerId(containerId);

            String result = executeRemoteCommand(command);

            if (isSuccess(result)) {
                JSONObject resultJson = JSONUtil.parseObj(result);
                resultJson.put("containerId", containerId);
                resultJson.put("action", "paused");

                // 更新数据库状态为暂停
                if (taskId != null) {
                    updateDeeplabTaskStatus(taskId, "paused", "容器已暂停");
                    addDeeplabTrainingLog(taskId, "INFO", "容器已暂停: " + containerId);
                }

                return resultJson.toString();
            } else {
                // 暂停失败，记录日志
                if (taskId != null) {
                    addDeeplabTrainingLog(taskId, "ERROR", "暂停容器失败: " + result);
                }
            }

            return result;

        } catch (Exception e) {
            log.error("暂停容器失败: {}", containerId, e);
            String taskId = getTaskIdByContainerId(containerId);
            if (taskId != null) {
                addDeeplabTrainingLog(taskId, "ERROR", "暂停容器异常: " + e.getMessage());
            }

            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("message", "暂停容器失败");
            errorResult.put("error", e.getMessage());
            errorResult.put("containerId", containerId);
            return errorResult.toString();
        }
    }

    /**
     * 继续容器（恢复暂停的容器）
     */
    @Override
    public String resumeContainer(String containerId) {
        try {
            String command = "docker unpause " + containerId;
            log.info("恢复容器: {}", containerId);

            // 从containerId获取taskId
            String taskId = getTaskIdByContainerId(containerId);

            String result = executeRemoteCommand(command);

            if (isSuccess(result)) {
                JSONObject resultJson = JSONUtil.parseObj(result);
                resultJson.put("containerId", containerId);
                resultJson.put("action", "resumed");

                // 更新数据库状态为运行中
                if (taskId != null) {
                    updateDeeplabTaskStatus(taskId, "running", "容器已恢复运行");
                    addDeeplabTrainingLog(taskId, "INFO", "容器已恢复运行: " + containerId);
                }

                return resultJson.toString();
            } else {
                // 恢复失败，记录日志
                if (taskId != null) {
                    addDeeplabTrainingLog(taskId, "ERROR", "恢复容器失败: " + result);
                }
            }

            return result;

        } catch (Exception e) {
            log.error("恢复容器失败: {}", containerId, e);
            String taskId = getTaskIdByContainerId(containerId);
            if (taskId != null) {
                addDeeplabTrainingLog(taskId, "ERROR", "恢复容器异常: " + e.getMessage());
            }

            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("message", "恢复容器失败");
            errorResult.put("error", e.getMessage());
            errorResult.put("containerId", containerId);
            return errorResult.toString();
        }
    }

    /**
     * 停止容器
     */
    @Override
    public String stopContainer(String containerId) {
        try {
            String command = "docker stop " + containerId;
            log.info("停止容器: {}", containerId);

            // 从containerId获取taskId
            String taskId = getTaskIdByContainerId(containerId);

            String result = executeRemoteCommand(command);

            if (isSuccess(result)) {
                JSONObject resultJson = JSONUtil.parseObj(result);
                resultJson.put("containerId", containerId);
                resultJson.put("action", "stopped");

                // 更新数据库状态为已停止，并记录结束时间
                if (taskId != null) {
                    String endTime = getCurrentTime();
                    updateDeeplabTaskStopStatus(taskId, endTime);
                    addDeeplabTrainingLog(taskId, "INFO", "容器已停止: " + containerId);
                }

                return resultJson.toString();
            } else {
                // 停止失败，记录日志
                if (taskId != null) {
                    addDeeplabTrainingLog(taskId, "ERROR", "停止容器失败: " + result);
                }
            }

            return result;

        } catch (Exception e) {
            log.error("停止容器失败: {}", containerId, e);
            String taskId = getTaskIdByContainerId(containerId);
            if (taskId != null) {
                addDeeplabTrainingLog(taskId, "ERROR", "停止容器异常: " + e.getMessage());
            }

            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("message", "停止容器失败");
            errorResult.put("error", e.getMessage());
            errorResult.put("containerId", containerId);
            return errorResult.toString();
        }
    }

    /**
     * 删除容器
     */
    @Override
    public String removeContainer(String containerId) {
        try {
            String command = "docker rm -f " + containerId;
            log.info("删除容器: {}", containerId);

            // 从containerId获取taskId
            String taskId = getTaskIdByContainerId(containerId);

            String result = executeRemoteCommand(command);

            if (isSuccess(result)) {
                JSONObject resultJson = JSONUtil.parseObj(result);
                resultJson.put("containerId", containerId);
                resultJson.put("action", "removed");

                // 软删除数据库记录
                if (taskId != null) {
                    deleteDeeplabTask(taskId);
                    addDeeplabTrainingLog(taskId, "INFO", "容器已删除: " + containerId);
                }

                return resultJson.toString();
            } else {
                // 删除失败，记录日志
                if (taskId != null) {
                    addDeeplabTrainingLog(taskId, "ERROR", "删除容器失败: " + result);
                }
            }

            return result;

        } catch (Exception e) {
            log.error("删除容器失败: {}", containerId, e);
            String taskId = getTaskIdByContainerId(containerId);
            if (taskId != null) {
                addDeeplabTrainingLog(taskId, "ERROR", "删除容器异常: " + e.getMessage());
            }

            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("message", "删除容器失败");
            errorResult.put("error", e.getMessage());
            errorResult.put("containerId", containerId);
            return errorResult.toString();
        }
    }

    /**
     * 查看容器状态
     */
    @Override
    public String getContainerStatus(String containerId) {
        try {
            String command = "docker inspect --format='{{.State.Status}};{{.State.ExitCode}}' " + containerId;
            log.info("查询容器状态: {}", containerId);

            String result = executeRemoteCommand(command);

            if (isSuccess(result)) {
                JSONObject resultJson = JSONUtil.parseObj(result);
                String status = resultJson.getStr("output", "").trim();
                resultJson.put("containerId", containerId);
                resultJson.put("containerStatus", status);

                String[] parts = status.split(";");
                String statusPart = parts.length > 0 ? parts[0].trim() : "";
                String exitCodeStr = parts.length > 1 ? parts[1].trim() : "";
                // 检查容器退出码，判断任务是否失败
                try {
                    if (!exitCodeStr.isEmpty()) {
                        int exitCode = Integer.parseInt(exitCodeStr);
                        if (exitCode != 0 && "exited".equals(statusPart)) {
                            // 容器已退出且退出码不为0，表示任务失败
                            String taskId = getTaskIdByContainerId(containerId);
                            if (taskId != null) {
                                updateDeeplabTaskStatus(taskId, "failed", "容器异常退出，退出码: " + exitCode);
                                addDeeplabTrainingLog(taskId, "ERROR", "容器异常退出，退出码: " + exitCode);
                            }
                        }
                    }
                } catch (NumberFormatException e) {
                    log.warn("解析退出码失败: {}", exitCodeStr);
                }

                return resultJson.toString();
            }

            return result;

        } catch (Exception e) {
            log.error("查询容器状态失败: {}", containerId, e);
            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("message", "查询容器状态失败");
            errorResult.put("error", e.getMessage());
            errorResult.put("containerId", containerId);
            return errorResult.toString();
        }
    }

    /**
     * 查看容器日志
     */
    @Override
    public String getContainerLogs(String containerId, int lines) {
        try {
            String command = "docker logs --tail " + lines + " " + containerId;
            log.info("查询容器日志: {}, 显示最后{}行", containerId, lines);

            String result = executeRemoteCommand(command);

            if (isSuccess(result)) {
                JSONObject resultJson = JSONUtil.parseObj(result);
                resultJson.put("containerId", containerId);
                resultJson.put("lines", lines);
                return resultJson.toString();
            }

            return result;

        } catch (Exception e) {
            log.error("查询容器日志失败: {}", containerId, e);
            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("message", "查询容器日志失败");
            errorResult.put("error", e.getMessage());
            errorResult.put("containerId", containerId);
            return errorResult.toString();
        }
    }

    /**
     * 上传文件到容器
     */
    @Override
    public String uploadToContainer(String containerId, String localPath, String containerPath) {
        try {
            String command = "docker cp " + localPath + " " + containerId + ":" + containerPath;
            log.info("上传文件到容器: {} -> {}:{}", localPath, containerId, containerPath);

            String result = executeRemoteCommand(command);

            if (isSuccess(result)) {
                JSONObject resultJson = JSONUtil.parseObj(result);
                resultJson.put("containerId", containerId);
                resultJson.put("localPath", localPath);
                resultJson.put("containerPath", containerPath);
                resultJson.put("action", "uploaded");
                return resultJson.toString();
            }

            return result;

        } catch (Exception e) {
            log.error("上传文件到容器失败: {} -> {}:{}", localPath, containerId, containerPath, e);
            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("message", "上传文件到容器失败");
            errorResult.put("error", e.getMessage());
            errorResult.put("containerId", containerId);
            errorResult.put("localPath", localPath);
            errorResult.put("containerPath", containerPath);
            return errorResult.toString();
        }
    }

    /**
     * 从容器下载文件
     */
    @Override
    public String downloadFromContainer(String containerId, String containerPath, String localPath) {
        try {
            String command = "docker cp " + containerId + ":" + containerPath + " " + localPath;
            log.info("从容器下载文件: {}:{} -> {}", containerId, containerPath, localPath);

            String result = executeRemoteCommand(command);

            if (isSuccess(result)) {
                JSONObject resultJson = JSONUtil.parseObj(result);
                resultJson.put("containerId", containerId);
                resultJson.put("containerPath", containerPath);
                resultJson.put("localPath", localPath);
                resultJson.put("action", "downloaded");
                return resultJson.toString();
            }

            return result;

        } catch (Exception e) {
            log.error("从容器下载文件失败: {}:{} -> {}", containerId, containerPath, localPath, e);
            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("message", "从容器下载文件失败");
            errorResult.put("error", e.getMessage());
            errorResult.put("containerId", containerId);
            errorResult.put("containerPath", containerPath);
            errorResult.put("localPath", localPath);
            return errorResult.toString();
        }
    }

    /**
     * 将容器提交为新镜像
     */
    @Override
    public String commitContainerAsImage(String containerId, String imageName, String imageTag) {
        try {
            String fullImageName = imageName + ":" + imageTag;
            String command = "docker commit " + containerId + " " + fullImageName;
            log.info("将容器提交为镜像: {} -> {}", containerId, fullImageName);

            String result = executeRemoteCommand(command);

            if (isSuccess(result)) {
                JSONObject resultJson = JSONUtil.parseObj(result);
                resultJson.put("containerId", containerId);
                resultJson.put("imageName", imageName);
                resultJson.put("imageTag", imageTag);
                resultJson.put("fullImageName", fullImageName);
                resultJson.put("action", "committed");
                return resultJson.toString();
            }

            return result;

        } catch (Exception e) {
            log.error("提交容器为镜像失败: {} -> {}:{}", containerId, imageName, imageTag, e);
            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("message", "提交容器为镜像失败");
            errorResult.put("error", e.getMessage());
            errorResult.put("containerId", containerId);
            errorResult.put("imageName", imageName);
            errorResult.put("imageTag", imageTag);
            return errorResult.toString();
        }
    }

    // ==================== 配置创建方法（业务配置） ====================

    /**
     * 创建默认训练配置
     */
    public JSONObject createDefaultTrainConfig() {
        JSONObject config = new JSONObject();
        config.put("the_train_type", "train");
        config.put("train_log_file", "/app/data/train.log");
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
                "container_name, model_path, gpu_ids, use_gpu, " +
                "status, progress, current_epoch, start_time, created_at, is_deleted, config_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try {
            String modelName = config.getStr("model_name", "deeplabv3");
            String modelCategory = getModelCategory(modelName, config);
            String modelFramework = getModelFramework(modelName, config);
            String modelPath = config.getStr("model_path", "");
            String cuda = config.getBool("cuda", true) ? "0" : "cpu";
            String currentTime = getCurrentTime();

            getMysqlAdapter().executeUpdate(sql,
                    taskId, "", modelName, modelCategory, modelFramework,
                    "predict", "", modelPath, cuda, config.getBool("cuda", true) ? 1 : 0,
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
     * 添加DeepLab训练日志到数据库
     */
    private void addDeeplabTrainingLog(String taskId, String logLevel, String logMessage) {
        String currentTime = getCurrentTime();
        String logFilePath = LOG_PATH_PREFIX + taskId + ".log";

        // 构造日志条目
        String logEntry = currentTime + " " + logLevel + " " + logMessage + "\n";

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
            log.error("添加训练日志失败: taskId={}, error={}", taskId, e.getMessage(), e);
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
