package ai.finetune;

import ai.common.utils.ObservableList;
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
public class YoloTrainerAdapter extends DockerTrainerAbstract {

//    static {
//        //initialize Profiles
//        ContextLoader.loadContext();
//    }

    private static final String DEFAULT_DOCKER_IMAGE = "yolov8_trainer:last";
    private static final String DEFAULT_VOLUME_MOUNT = "/data/wangshuanglong:/app/data";
    private static final String LOG_PATH_PREFIX = "/data/yolo_train_logs/";
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
    public YoloTrainerAdapter() {
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
    public YoloTrainerAdapter(String sshHost, int sshPort, String sshUsername, String sshPassword) {
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

            // 构建 Docker 命令
            StringBuilder dockerCmd = new StringBuilder();
            dockerCmd.append("docker run -d"); // -d 后台运行

            // 添加容器名称，便于后续管理
            String containerName = generateContainerName("yolo_train");
            dockerCmd.append(" --name ").append(containerName);

            // GPU 支持
            dockerCmd.append(" --gpus all");

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
            addYoloTrainingLog(taskId, "INFO", "YOLO训练任务已启动，容器名称: " + containerName);

            String result = executeRemoteCommand(fullCommand);

            // 如果成功，将容器名称添加到结果中
            if (isSuccess(result)) {
                JSONObject resultJson = JSONUtil.parseObj(result);
                resultJson.put("containerName", containerName);
                resultJson.put("taskId", taskId);
                resultJson.put("trackId", trackId);

                // 更新数据库为运行中状态
                updateYoloTaskStatus(taskId, "running", "训练任务启动成功");
                addYoloTrainingLog(taskId, "INFO", "容器启动成功，开始训练");

                return resultJson.toString();
            } else {
                // 启动失败，更新数据库状态
                updateYoloTaskStatus(taskId, "failed", "容器启动失败");
                addYoloTrainingLog(taskId, "ERROR", "容器启动失败: " + result);
            }

            return result;

        } catch (Exception e) {
            log.error("启动训练任务失败", e);
            // 更新数据库状态为失败
            updateYoloTaskStatus(taskId, "failed", "启动训练任务异常: " + e.getMessage());
            addYoloTrainingLog(taskId, "ERROR", "启动训练任务失败: " + e.getMessage());

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

            // 构建 Docker 命令
            StringBuilder dockerCmd = new StringBuilder();
            dockerCmd.append("docker run --rm"); // --rm 自动删除容器

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
        String taskId = UUID.randomUUID().toString();
        try {
            // 确保配置中包含必要的字段
            if (!config.containsKey("the_train_type")) {
                config.put("the_train_type", "predict");
            }
            config.put("task_id", taskId);

            // 保存预测任务到数据库
            savePredictTaskToDB(taskId, config);
            addYoloTrainingLog(taskId, "INFO", "开始执行预测任务");

            // 构建 Docker 命令
            StringBuilder dockerCmd = new StringBuilder();
            dockerCmd.append("docker run --rm");

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
                updateYoloTaskStatus(taskId, "completed", "预测任务完成");
                addYoloTrainingLog(taskId, "INFO", "预测任务完成");
            } else {
                updateYoloTaskStatus(taskId, "failed", "预测任务失败");
                addYoloTrainingLog(taskId, "ERROR", "预测任务失败: " + result);
            }

            return result;

        } catch (Exception e) {
            log.error("执行预测任务失败", e);
            updateYoloTaskStatus(taskId, "failed", "预测任务异常: " + e.getMessage());
            addYoloTrainingLog(taskId, "ERROR", "预测任务异常: " + e.getMessage());

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
            config.put("task_id", taskId);

            // 保存导出任务到数据库
            saveExportTaskToDB(taskId, config);
            addYoloTrainingLog(taskId, "INFO", "开始导出模型");

            // 构建 Docker 命令
            StringBuilder dockerCmd = new StringBuilder();
            dockerCmd.append("docker run --rm");

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
                    updateYoloTaskStatus(taskId, "paused", "容器已暂停");
                    addYoloTrainingLog(taskId, "INFO", "容器已暂停: " + containerId);
                }

                return resultJson.toString();
            } else {
                // 暂停失败，记录日志
                if (taskId != null) {
                    addYoloTrainingLog(taskId, "ERROR", "暂停容器失败: " + result);
                }
            }

            return result;

        } catch (Exception e) {
            log.error("暂停容器失败: {}", containerId, e);
            String taskId = getTaskIdByContainerId(containerId);
            if (taskId != null) {
                addYoloTrainingLog(taskId, "ERROR", "暂停容器异常: " + e.getMessage());
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
                    updateYoloTaskStatus(taskId, "running", "容器已恢复运行");
                    addYoloTrainingLog(taskId, "INFO", "容器已恢复运行: " + containerId);
                }

                return resultJson.toString();
            } else {
                // 恢复失败，记录日志
                if (taskId != null) {
                    addYoloTrainingLog(taskId, "ERROR", "恢复容器失败: " + result);
                }
            }

            return result;

        } catch (Exception e) {
            log.error("恢复容器失败: {}", containerId, e);
            String taskId = getTaskIdByContainerId(containerId);
            if (taskId != null) {
                addYoloTrainingLog(taskId, "ERROR", "恢复容器异常: " + e.getMessage());
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
                    updateYoloTaskStopStatus(taskId, endTime);
                    addYoloTrainingLog(taskId, "INFO", "容器已停止: " + containerId);
                }

                return resultJson.toString();
            } else {
                // 停止失败，记录日志
                if (taskId != null) {
                    addYoloTrainingLog(taskId, "ERROR", "停止容器失败: " + result);
                }
            }

            return result;

        } catch (Exception e) {
            log.error("停止容器失败: {}", containerId, e);
            String taskId = getTaskIdByContainerId(containerId);
            if (taskId != null) {
                addYoloTrainingLog(taskId, "ERROR", "停止容器异常: " + e.getMessage());
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
                    deleteYoloTask(taskId);
                    addYoloTrainingLog(taskId, "INFO", "容器已删除: " + containerId);
                }

                return resultJson.toString();
            } else {
                // 删除失败，记录日志
                if (taskId != null) {
                    addYoloTrainingLog(taskId, "ERROR", "删除容器失败: " + result);
                }
            }

            return result;

        } catch (Exception e) {
            log.error("删除容器失败: {}", containerId, e);
            String taskId = getTaskIdByContainerId(containerId);
            if (taskId != null) {
                addYoloTrainingLog(taskId, "ERROR", "删除容器异常: " + e.getMessage());
            }

            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("message", "删除容器失败");
            errorResult.put("error", e.getMessage());
            errorResult.put("containerId", containerId);
            return errorResult.toString();
        }
    }
    public String removeContainer(String containerId,String taskId) {
                if (taskId != null) {
                    deleteYoloTask(taskId);
                    addYoloTrainingLog(taskId, "INFO", "容器已删除: " + containerId);
                }
                JSONObject resultJson = new JSONObject();
                 resultJson.put("status", "success");
                resultJson.put("message", "远程任务执行成功");
                return resultJson.toString();
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
                String exitCodeStr = parts.length > 1 ? parts[1].trim() : "";
                // 检查容器退出码，判断任务是否失败
                try {
                    int exitCode = Integer.parseInt(exitCodeStr);
                    if (exitCode != 0 && "exited".equals(status)) {
                        // 容器已退出且退出码不为0，表示任务失败
                        String taskId = getTaskIdByContainerId(containerId);
                        if (taskId != null) {
                            updateYoloTaskStatus(taskId, "failed", "容器异常退出，退出码: " + exitCode);
                            addYoloTrainingLog(taskId, "ERROR", "容器异常退出，退出码: " + exitCode);
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

    /**
     * 列出所有 YOLO 训练容器
     */
    public String listTrainingContainers() {
        try {
            String command = "docker ps -a --filter name=yolo_train --format 'table {{.ID}}\\t{{.Names}}\\t{{.Status}}\\t{{.CreatedAt}}'";
            log.info("列出所有 YOLO 训练容器");
            return executeRemoteCommand(command);
        } catch (Exception e) {
            log.error("列出容器失败", e);
            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("message", "列出容器失败");
            errorResult.put("error", e.getMessage());
            return errorResult.toString();
        }
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
                // 构建命令：docker logs -f containerId
                String command = "docker logs -f " + containerId;
                log.info("开始流式获取容器日志: {}", containerId);

                // 执行远程命令并流式读取输出
                executeRemoteCommandStream(command, logStream);

            } catch (Exception e) {
                log.error("流式获取容器日志失败: {}", containerId, e);
                logStream.add("Error: " + e.getMessage());
            } finally {
                logStream.onComplete();
            }
        });

        return logStream;
    }

    /**
     * 流式执行远程命令（用于长时间运行的命令）
     */
    private void executeRemoteCommandStream(String command, ObservableList<String> outputStream) {
        com.jcraft.jsch.Session session = null;
        com.jcraft.jsch.ChannelExec channelExec = null;

        try {
            // 创建 JSch 对象
            com.jcraft.jsch.JSch jsch = new com.jcraft.jsch.JSch();

            // 创建 SSH 会话
            session = jsch.getSession(sshUsername, sshHost, sshPort);
            session.setPassword(sshPassword);

            // 配置 SSH 连接
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            config.put("server_host_key", "rsa-sha2-512,rsa-sha2-256,ssh-ed25519,ssh-rsa,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521");
            config.put("PubkeyAcceptedAlgorithms", "rsa-sha2-512,rsa-sha2-256,ssh-ed25519,ssh-rsa,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521");

            session.setConfig(config);
            session.setTimeout(300000);
            session.connect();

            // 打开执行通道
            channelExec = (com.jcraft.jsch.ChannelExec) session.openChannel("exec");
            channelExec.setCommand(command);

            // 获取输入流
            java.io.InputStream in = channelExec.getInputStream();
            java.io.InputStream err = channelExec.getErrStream();

            // 执行命令
            channelExec.connect();

            // 读取标准输出（流式）
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputStream.add(line);
                }
            }

            // 读取错误输出
            try (java.io.BufferedReader errorReader = new java.io.BufferedReader(new java.io.InputStreamReader(err))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    outputStream.add("[ERROR] " + line);
                }
            }

        } catch (Exception e) {
            log.error("流式执行远程命令失败", e);
            outputStream.add("[ERROR] " + e.getMessage());
        } finally {
            // 关闭连接
            if (channelExec != null && channelExec.isConnected()) {
                channelExec.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
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
     * 获取数据库连接池适配器实例（单例模式，双重检查锁定）
     * 使用数据库连接池提高性能和资源利用率
     */
    private static MysqlAdapter getMysqlAdapter() {
        if (mysqlAdapter == null) {
            synchronized (YoloTrainerAdapter.class) {
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
            synchronized (YoloTrainerAdapter.class) {
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
                "dataset_path, model_path, epochs, batch_size, image_size, optimizer, " +
                "status, progress, current_epoch, start_time, created_at, is_deleted, user_id, config_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        // 从配置中读取 user_id
        String userId = task.config.getStr("user_id", null);

        getMysqlAdapter().executeUpdate(sql,
                task.taskId, task.trackId, task.modelName, task.modelCategory, task.modelFramework,
                "train", task.containerName, "", task.dockerImage,
                task.device, !task.device.equals("cpu") ? 1 : 0,
                task.datasetPath, task.modelPath, task.epochs, task.batchSize, task.imageSize, task.optimizer,
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
                    "running",
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
     * 添加YOLO训练日志到数据库
     */
    private void addYoloTrainingLog(String taskId, String logLevel, String logMessage) {
        String currentTime = getCurrentTime();
        String logFilePath = LOG_PATH_PREFIX + taskId + ".log";

        // 构造日志条目
        String logEntry = currentTime + " " + logLevel + " " + logMessage + "\n";

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
                // 若存在日志，追加内容
                String updateSql = "UPDATE ai_training_logs " +
                        "SET log_message = CONCAT(IFNULL(log_message, ''), ?), " +
                        "log_level = ?, " +
                        "created_at = ? " +
                        "WHERE task_id = ?";
                // 使用数据库连接池执行更新操作
                getMysqlAdapter().executeUpdate(updateSql, logEntry, logLevel, currentTime, taskId);
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

    // ==================== 测试示例 ====================

    public static void main(String[] args) {
        // 创建 YoloTrainer 实例
        YoloTrainerAdapter trainer = new YoloTrainerAdapter();

        // 配置远程服务器
        trainer.setRemoteServer("103.85.179.118", 40022, "wangshuanglong", "chnvideo@2012");

        System.out.println("=== YOLO 训练器测试 ===\n");

        // 生成随机 ID
        String taskId = generateTaskId();
        String trackId = generateTrackId();

        System.out.println("Task ID: " + taskId);
        System.out.println("Track ID: " + trackId);
        System.out.println();

        // 1. 启动训练任务
        System.out.println("=== 1. 启动训练任务 ===");
        String trainResult = trainer.startTraining(taskId, trackId);
        System.out.println("训练结果: " + trainResult);

        // 解析容器名称
        String containerName = null;
        if (isSuccess(trainResult)) {
            JSONObject resultJson = JSONUtil.parseObj(trainResult);
            containerName = resultJson.getStr("containerName");
            System.out.println("容器名称: " + containerName);
        }
        System.out.println();

        // 等待一段时间
        try {
            System.out.println("等待 5 秒...");
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (containerName != null) {
            // 2. 查看容器状态
            System.out.println("=== 2. 查看容器状态 ===");
            String statusResult = trainer.getContainerStatus(containerName);
            System.out.println("状态结果: " + statusResult);
            System.out.println();

            // 3. 查看容器日志
            System.out.println("=== 3. 查看容器日志 ===");
            String logsResult = trainer.getContainerLogs(containerName, 50);
            System.out.println("日志结果: " + logsResult);
            System.out.println();

            // 4. 暂停容器
            System.out.println("=== 4. 暂停容器 ===");
            String pauseResult = trainer.pauseContainer(containerName);
            System.out.println("暂停结果: " + pauseResult);
            System.out.println();

            // 等待
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // 5. 恢复容器
            System.out.println("=== 5. 恢复容器 ===");
            String resumeResult = trainer.resumeContainer(containerName);
            System.out.println("恢复结果: " + resumeResult);
            System.out.println();

            // 6. 上传文件到容器（示例）
            System.out.println("=== 6. 上传文件到容器 ===");
            String uploadResult = trainer.uploadToContainer(
                containerName,
                "/data/wangshuanglong/test_config.json",
                "/app/data/uploaded_config.json"
            );
            System.out.println("上传结果: " + uploadResult);
            System.out.println();

            // 7. 从容器下载文件（示例）
            System.out.println("=== 7. 从容器下载文件 ===");
            String downloadResult = trainer.downloadFromContainer(
                containerName,
                "/app/data/train.log",
                "/data/wangshuanglong/downloaded_train.log"
            );
            System.out.println("下载结果: " + downloadResult);
            System.out.println();

            // 8. 提交容器为新镜像（示例）
            System.out.println("=== 8. 提交容器为新镜像 ===");
            String commitResult = trainer.commitContainerAsImage(
                containerName,
                "yolo_trained_model",
                "v1.0"
            );
            System.out.println("提交结果: " + commitResult);
            System.out.println();

            // 9. 停止并删除容器（清理）
            System.out.println("=== 9. 停止并删除容器 ===");
            String stopResult = trainer.stopContainer(containerName);
            System.out.println("停止结果: " + stopResult);

            String removeResult = trainer.removeContainer(containerName,"");
            System.out.println("删除结果: " + removeResult);
        }

        System.out.println("\n=== 测试完成 ===");
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

