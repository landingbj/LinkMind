package ai.finetune.service.impl;

import ai.common.utils.ThreadPoolManager;
import ai.config.ContextLoader;
import ai.config.pojo.Docker;
import ai.config.pojo.ModelMapper;
import ai.config.pojo.ModelPlatformConfig;
import ai.database.impl.MysqlAdapter;
import ai.finetune.config.ModelConfigManager;
import ai.finetune.dto.TrainingTaskDTO;
import ai.finetune.repository.TrainingTaskRepository;
import ai.finetune.service.TrainerService;
import ai.finetune.util.AttributeUtil;
import ai.finetune.util.DockerTrainerUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

/**
 * Docker 训练服务实现类
 * 使用 DockerTrainerUtil 工具类执行 Docker 容器操作
 * 所有数据库操作通过 DAO 进行，不在 Service 层直接操作数据库
 */
@Slf4j
public class DockerTrainerServiceImpl implements TrainerService {

    private static final ExecutorService executorService;
    private final Docker docker;
    private final String volumes;
    private final String envs;
    private final TrainingTaskRepository taskRepository;
    private final ModelConfigManager modelConfigManager;

    static {
        ThreadPoolManager.registerExecutor("docker-trainer");
        executorService = ThreadPoolManager.getExecutor("docker-trainer");
    }

    public DockerTrainerServiceImpl() {
        ModelPlatformConfig modelPlatformConfig = ContextLoader.configuration.getModelPlatformConfig();
        docker = modelPlatformConfig.getDocker();
        List<List<String>> volumes = modelPlatformConfig.getVolumes();
        StringBuilder volumesSB = new StringBuilder();
        if(volumes != null && !volumes.isEmpty()) {
            for (List<String> volume : volumes) {
                if(volume.size() == 2) {
                    volumesSB.append(" -v ");
                    volumesSB.append(volume.get(0));
                    volumesSB.append(":").append(volume.get(1)).append(" ");
                }
            }
        }
        this.volumes = volumesSB.toString();
        List<List<String>> envs = modelPlatformConfig.getEnvs();
        StringBuilder envsSB = new StringBuilder();
        if(envs != null && !envs.isEmpty()) {
            for (List<String> env : envs) {
                if(env.size() == 2) {
                    envsSB.append(" -e ").append(env.get(0)).append("=").append(env.get(1)).append(" ");
                }
            }
        }
        this.envs = envsSB.toString();
        
        // 初始化 DAO
        MysqlAdapter mysqlAdapter = MysqlAdapter.getInstance();
        this.taskRepository = new TrainingTaskRepository(mysqlAdapter);
        this.modelConfigManager = new ModelConfigManager(mysqlAdapter);
        this.modelConfigManager.loadConfigsFromDatabase();
    }

    /**
     * 构建 Docker 命令
     */
    private String buildCmd(String parseType, String cmdPattern, String containerId , JSONObject config) {
        if(parseType == null) {
            parseType = "unparsed";
        }
        if(parseType.equals("unparsed")) {
            return StrUtil.format(cmdPattern, "--name " + containerId + " " + volumes + " " + envs + " " , config.toString());
        }
        return null;
    }

    /**
     * 匹配模型配置
     */
    private ModelMapper matchModel(JSONObject config) {
        String modelName = config.getStr("model_name", "");
        List<ModelMapper> models = docker.getModels();
        if (models == null) {
            return null;
        }
        for (ModelMapper model : models) {
            String modelPattern = model.getModelPattern();
            if (modelPattern != null && Pattern.matches(modelPattern, modelName)) {
                return model;
            }
        }
        return null;
    }



    /**
     * 保存训练任务到数据库
     */
    private void saveTrainingTaskToDB(String taskId, String trackId, String containerName, JSONObject config, ModelMapper modelMapper) {
        try {
            String modelName = config.getStr("model_name", "custom_model");
            ModelConfigManager.ModelConfig modelConfig = modelConfigManager.getModelConfig(modelName, config);
            
            // 从配置中读取数据集名称
            String datasetName = config.getStr("dataset_name", "外部数据集");
            String datasetPath = config.getStr("data", "");
            if (StrUtil.isNotBlank(datasetPath)) {
                // 通过 DAO 查询数据集名称
                String queriedName = taskRepository.getDatasetNameByStoragePath(datasetPath);
                if (StrUtil.isNotBlank(queriedName)) {
                    datasetName = queriedName;
                    config.set("dataset_name", datasetName);
                }
            }
            
            // 构建 TrainingTaskDTO
            TrainingTaskDTO task = TrainingTaskDTO.builder()
                    .taskId(taskId)
                    .trackId(trackId)
                    .taskType("train")
                    .modelName(modelName)
                    .modelCategory(modelConfig.getModelCategory())
                    .modelFramework(modelConfig.getModelFramework())
                    .containerName(containerName)
                    .dockerImage(config.getStr("_docker_image", ""))
                    .datasetPath(datasetPath)
                    .modelPath(config.getStr("model_path", ""))
                    .epochs(config.getInt("epochs", null))
                    .batchSize(config.getInt("batch", null))
                    .imageSize(config.getInt("imgsz") != null ? String.valueOf(config.getInt("imgsz")) : null)
                    .optimizer(config.getStr("optimizer", "sgd"))
                    .gpuIds(config.getStr("device", "0"))
                    .useGpu(config.getStr("device", "0").equals("cpu") ? "0" : "1")
                    .status("starting")
                    .progress("0%")
                    .configJson(config)
                    .build();

            AttributeUtil.attrMapping(modelMapper.getAttrMapping(), config, task);
            // 设置额外的字段（如果 Builder 不支持）
            TrainingTaskDTO taskDTO = task;
            if (StrUtil.isNotBlank(datasetName)) {
                taskDTO.setDatasetName(datasetName);
            }
            if (StrUtil.isNotBlank(config.getStr("user_id"))) {
                taskDTO.setUserId(config.getStr("user_id"));
            }
            if (config.getInt("template_id") != null) {
                taskDTO.setTemplateId(config.getInt("template_id"));
            }
            
            taskRepository.saveTask(taskDTO);
            log.info("训练任务已保存到数据库: taskId={}, model={}, category={}, framework={}",
                    taskId, modelName, modelConfig.getModelCategory(), modelConfig.getModelFramework());
        } catch (Exception e) {
            log.error("保存训练任务到数据库失败: taskId={}, error={}", taskId, e.getMessage(), e);
            throw new RuntimeException("保存训练任务到数据库失败: " + e.getMessage(), e);
        }
    }

    /**
     * 保存评估任务到数据库
     */
    private void saveEvaluationTaskToDB(String taskId, JSONObject config) {
        try {
            String modelName = config.getStr("model_name", "custom_model");
            ModelConfigManager.ModelConfig modelConfig = modelConfigManager.getModelConfig(modelName, config);
            
            TrainingTaskDTO task = TrainingTaskDTO.builder()
                    .taskId(taskId)
                    .taskType("evaluate")
                    .modelName(modelName)
                    .modelCategory(modelConfig.getModelCategory())
                    .modelFramework(modelConfig.getModelFramework())
                    .datasetPath(config.getStr("data", ""))
                    .modelPath(config.getStr("model_path", ""))
                    .imageSize(config.getInt("imgsz") != null ? String.valueOf(config.getInt("imgsz")) : null)
                    .optimizer(config.getStr("optimizer", "sgd"))
                    .status("running")
                    .progress("0%")
                    .configJson(config)
                    .build();

            // 设置额外的字段
            if (StrUtil.isNotBlank(config.getStr("user_id"))) {
                task.setUserId(config.getStr("user_id"));
            }
            
            taskRepository.saveTask(task);
            log.info("评估任务已保存到数据库: taskId={}, model={}", taskId, modelName);
        } catch (Exception e) {
            log.error("保存评估任务到数据库失败: taskId={}, error={}", taskId, e.getMessage(), e);
            throw new RuntimeException("保存评估任务到数据库失败: " + e.getMessage(), e);
        }
    }

    /**
     * 保存预测任务到数据库
     */
    private void savePredictionTaskToDB(String taskId, JSONObject config) {
        try {
            String modelName = config.getStr("model_name", "custom_model");
            ModelConfigManager.ModelConfig modelConfig = modelConfigManager.getModelConfig(modelName, config);
            
            TrainingTaskDTO task = TrainingTaskDTO.builder()
                    .taskId(taskId)
                    .taskType("predict")
                    .modelName(modelName)
                    .modelCategory(modelConfig.getModelCategory())
                    .modelFramework(modelConfig.getModelFramework())
                    .modelPath(config.getStr("model_path", ""))
                    .gpuIds(config.getStr("device", "cpu"))
                    .useGpu(config.getStr("device", "cpu").equals("cpu") ? "0" : "1")
                    .status("running")
                    .progress("0%")
                    .configJson(config)
                    .build();
            
            // 设置额外的字段
            if (StrUtil.isNotBlank(config.getStr("user_id"))) {
                task.setUserId(config.getStr("user_id"));
            }
            
            taskRepository.saveTask(task);
            log.info("预测任务已保存到数据库: taskId={}, model={}", taskId, modelName);
        } catch (Exception e) {
            log.error("保存预测任务到数据库失败: taskId={}, error={}", taskId, e.getMessage(), e);
            throw new RuntimeException("保存预测任务到数据库失败: " + e.getMessage(), e);
        }
    }

    /**
     * 保存转换任务到数据库
     */
    private void saveConvertTaskToDB(String taskId, JSONObject config) {
        try {
            String modelName = config.getStr("model_name", "custom_model");
            ModelConfigManager.ModelConfig modelConfig = modelConfigManager.getModelConfig(modelName, config);
            
            TrainingTaskDTO task = TrainingTaskDTO.builder()
                    .taskId(taskId)
                    .taskType("export")
                    .modelName(modelName)
                    .modelCategory(modelConfig.getModelCategory())
                    .modelFramework(modelConfig.getModelFramework())
                    .modelPath(config.getStr("model_path", ""))
                    .gpuIds(config.getStr("device", "cpu"))
                    .useGpu(config.getStr("device", "cpu").equals("cpu") ? "0" : "1")
                    .status("running")
                    .progress("0%")
                    .configJson(config)
                    .build();
            
            // 设置额外的字段
            if (StrUtil.isNotBlank(config.getStr("user_id"))) {
                task.setUserId(config.getStr("user_id"));
            }
            
            taskRepository.saveTask(task);
            log.info("转换任务已保存到数据库: taskId={}, model={}", taskId, modelName);
        } catch (Exception e) {
            log.error("保存转换任务到数据库失败: taskId={}, error={}", taskId, e.getMessage(), e);
            throw new RuntimeException("保存转换任务到数据库失败: " + e.getMessage(), e);
        }
    }


    @Override
    public Future<String> startTrainingTask(JSONObject config) {
        if(config == null) {
            throw new RuntimeException("无有效训练参数");
        }
        
        // 获取任务ID和跟踪ID
        String taskId = config.getStr("task_id");
        if (StrUtil.isBlank(taskId)) {
            taskId = DockerTrainerUtil.generateTaskId();
            config.set("task_id", taskId);
        }
        String trackId = config.getStr("track_id");
        if (StrUtil.isBlank(trackId)) {
            trackId = DockerTrainerUtil.generateTrackId();
            config.set("track_id", trackId);
        }

        // 保存为 final 变量供 lambda 使用
        final String finalTaskId = taskId;
        final String finalTrackId = trackId;
        
        ModelMapper docker4Model = matchModel(config);
        if (docker4Model == null) {
            throw new RuntimeException("未匹配到模型");
        }
        String trainCmd = docker4Model.getTrainCmd();
        if(StrUtil.isBlank(trainCmd)) {
            throw new RuntimeException("模型未配置训练命令");
        }

        AttributeUtil.attrAccess(docker4Model.getAttrAccess(), config, config);
        String cmd = buildCmd(docker4Model.getParseType(), trainCmd, taskId , config);
        System.out.println("cmd: " + cmd);
        if(StrUtil.isBlank(cmd)) {
            throw new RuntimeException("构建训练命令失败");
        }
        // 生成容器名称
        String containerName = DockerTrainerUtil.generateContainerName("docker_train");
        config.set("_container_name", containerName);
        // Docker 镜像名称从配置中获取，如果没有则使用空字符串
        String dockerImage = config.getStr("docker_image", "");
        config.set("_docker_image", dockerImage);
        config.set("_status", "starting");
        
        // 保存任务到数据库
        try {
            // TODO 2026/2/4 开启事务
            saveTrainingTaskToDB(finalTaskId, finalTrackId, containerName, config, docker4Model);
            taskRepository.addTrainingLog(finalTaskId, "INFO", "训练任务已启动，容器名称: " + containerName);
        } catch (Exception e) {
            log.error("保存训练任务到数据库失败: taskId={}", finalTaskId, e);
        }
        
        return executorService.submit(() -> {
                log.info("开始执行训练任务: taskId={}, trackId={}", finalTaskId, finalTrackId);
                log.info("执行训练命令: {}", cmd);
                try {
                    String result = DockerTrainerUtil.executeRemoteCommand(
                            docker.getHost(), docker.getPort(), docker.getUsername(), docker.getPassword(), cmd, (a)->{
                                taskRepository.updateTaskStatus(finalTaskId, "running", "训练任务正在运行...");
                                return a;
                            });
                    // 成功时更新状态为运行中
                    taskRepository.updateTaskStatus(finalTaskId, "completed", "训练任务启动成功");
                    taskRepository.addTrainingLog(finalTaskId, "INFO", "容器启动成功，开始训练");
                    return result;
                } catch (RuntimeException e) {
                    // 失败时更新状态为失败，异常信息在 message 里
                    String errorMessage = e.getMessage() != null ? e.getMessage() : "训练任务启动失败";
                    taskRepository.updateTaskStatus(finalTaskId, "failed", errorMessage);
                    taskRepository.addTrainingLog(finalTaskId, "ERROR", "训练任务启动失败: " + errorMessage);
                    throw e;
                }
        });
    }
    


    @Override
    public String stopTask(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            throw new RuntimeException("任务ID不能为空");
        }
        
        try {
            String result = DockerTrainerUtil.stopContainer(
                    docker.getHost(), docker.getPort(), docker.getUsername(), docker.getPassword(), taskId);
            
            // 成功时更新状态为已停止
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            String endTime = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            taskRepository.updateTaskStopStatus(taskId, endTime);
            taskRepository.addTrainingLog(taskId, "INFO", "容器已停止: " + taskId);
            
            return result;
        } catch (RuntimeException e) {
            // 失败时记录日志
            String errorMessage = e.getMessage() != null ? e.getMessage() : "停止任务失败";
            taskRepository.addTrainingLog(taskId, "ERROR", "停止任务失败: " + errorMessage);
            throw e;
        }
    }

    @Override
    public Future<String> startEvaluationTask(JSONObject config) {
        if(config == null) {
            throw new RuntimeException("无有效评估参数");
        }
        
        String taskId = config.getStr("task_id");
        if (StrUtil.isBlank(taskId)) {
            taskId = DockerTrainerUtil.generateTaskId();
            config.set("task_id", taskId);
        }
        
        final String finalTaskId = taskId;
        config.set("_status", "running");
        
        // 保存评估任务到数据库
        try {
            saveEvaluationTaskToDB(finalTaskId, config);
            taskRepository.addTrainingLog(finalTaskId, "INFO", "开始执行评估任务");
        } catch (Exception e) {
            log.error("保存评估任务到数据库失败: taskId={}", finalTaskId, e);
        }
        
        ModelMapper docker4Model = matchModel(config);
        if (docker4Model == null) {
            throw new RuntimeException("未匹配到模型");
        }
        String evaluateCmd = docker4Model.getEvaluateCmd();
        if(StrUtil.isBlank(evaluateCmd)) {
            throw new RuntimeException("模型未配置评估命令");
        }

        String cmd = buildCmd(docker4Model.getParseType(), evaluateCmd, taskId, config);
        log.info("开始执行评估任务: taskId={}", taskId);
        log.info("执行评估命令: {}", cmd);
        if(StrUtil.isBlank(cmd)) {
            throw new RuntimeException("构建评估命令失败");
        }
        return executorService.submit(() -> {
            try {
                String result = DockerTrainerUtil.executeRemoteCommand(
                        docker.getHost(), docker.getPort(), docker.getUsername(), docker.getPassword(), cmd);
                
                // 成功时更新状态为完成
                taskRepository.updateTaskStatus(finalTaskId, "completed", "评估任务完成");
                taskRepository.addTrainingLog(finalTaskId, "INFO", "评估任务完成");
                
                return result;
            } catch (RuntimeException e) {
                // 失败时更新状态为失败，异常信息在 message 里
                String errorMessage = e.getMessage() != null ? e.getMessage() : "评估任务失败";
                taskRepository.updateTaskStatus(finalTaskId, "failed", errorMessage);
                taskRepository.addTrainingLog(finalTaskId, "ERROR", "评估任务失败: " + errorMessage);
                throw e;
            }
        });
    }


    @Override
    public Future<String> startPredictionTask(JSONObject config) {
        if(config == null) {
            throw new RuntimeException("无有效预测参数");
        }
        
        String taskId = config.getStr("task_id");
        if (StrUtil.isBlank(taskId)) {
            taskId = DockerTrainerUtil.generateTaskId();
            config.set("task_id", taskId);
        }
        
        final String finalTaskId = taskId;
        config.set("_status", "running");
        
        // 保存预测任务到数据库
        try {
            savePredictionTaskToDB(finalTaskId, config);
            taskRepository.addTrainingLog(finalTaskId, "INFO", "开始执行预测任务");
        } catch (Exception e) {
            log.error("保存预测任务到数据库失败: taskId={}", finalTaskId, e);
        }
        
        ModelMapper docker4Model = matchModel(config);
        if (docker4Model == null) {
            throw new RuntimeException("未匹配到模型");
        }
        String predictCmd = docker4Model.getPredictCmd();
        if(StrUtil.isBlank(predictCmd)) {
            throw new RuntimeException("模型未配置预测命令");
        }

        log.info("开始执行预测任务: taskId={}", finalTaskId);
        String cmd = buildCmd(docker4Model.getParseType(), predictCmd, taskId, config);
        if(StrUtil.isBlank(cmd)) {
            throw new RuntimeException("构建预测命令失败");
        }
        return executorService.submit(() -> {
            log.info("执行预测命令: {}", cmd);
            try {
                String result = DockerTrainerUtil.executeRemoteCommand(
                        docker.getHost(), docker.getPort(), docker.getUsername(), docker.getPassword(), cmd);
                
                // 成功时更新状态为完成
                taskRepository.updateTaskStatus(finalTaskId, "completed", "预测任务完成");
                taskRepository.updateTaskProgress(finalTaskId, 0, "100%");
                taskRepository.addTrainingLog(finalTaskId, "INFO", "预测任务完成");
                
                return result;
            } catch (RuntimeException e) {
                // 失败时更新状态为失败，异常信息在 message 里
                String errorMessage = e.getMessage() != null ? e.getMessage() : "预测任务失败";
                taskRepository.updateTaskStatus(finalTaskId, "failed", errorMessage);
                taskRepository.addTrainingLog(finalTaskId, "ERROR", "预测任务失败: " + errorMessage);
                throw e;
            }
        });
    }



    @Override
    public Future<String> startConvertTask(JSONObject config) {
        if(config == null) {
            throw new RuntimeException("无有效转换参数");
        }
        
        String taskId = config.getStr("task_id");
        if (StrUtil.isBlank(taskId)) {
            taskId = DockerTrainerUtil.generateTaskId();
            config.set("task_id", taskId);
        }
        
        final String finalTaskId = taskId;
        config.set("_status", "running");
        
        // 保存转换任务到数据库
        try {
            saveConvertTaskToDB(finalTaskId, config);
            taskRepository.addTrainingLog(finalTaskId, "INFO", "开始执行模型转换");
        } catch (Exception e) {
            log.error("保存转换任务到数据库失败: taskId={}", finalTaskId, e);
        }
        
        ModelMapper docker4Model = matchModel(config);
        if (docker4Model == null) {
            throw new RuntimeException("未匹配到模型");
        }
        String convertCmd = docker4Model.getConvertCmd();
        if(StrUtil.isBlank(convertCmd)) {
            throw new RuntimeException("模型未配置转换命令");
        }

        log.info("开始执行转换任务: taskId={}", finalTaskId);
        String cmd = buildCmd(docker4Model.getParseType(), convertCmd, taskId, config);
        if(StrUtil.isBlank(cmd)) {
            throw new RuntimeException("构建转换命令失败");
        }
        return executorService.submit(() -> {
            try {
                log.info("执行转换命令: {}", cmd);
                String result = DockerTrainerUtil.executeRemoteCommand(
                        docker.getHost(), docker.getPort(), docker.getUsername(), docker.getPassword(), cmd);
                // TODO 2026/2/3 更新转化地址
                // 成功时更新状态为完成
                taskRepository.updateTaskStatus(finalTaskId, "completed", "模型转换完成");
                taskRepository.addTrainingLog(finalTaskId, "INFO", "模型转换完成");
                
                return result;
            } catch (RuntimeException e) {
                // 失败时更新状态为失败，异常信息在 message 里
                String errorMessage = e.getMessage() != null ? e.getMessage() : "模型转换失败";
                taskRepository.updateTaskStatus(finalTaskId, "failed", errorMessage);
                taskRepository.addTrainingLog(finalTaskId, "ERROR", "模型转换失败: " + errorMessage);
                throw e;
            }

        });

    }

    @Override
    public String pauseTask(String taskId) {
        try {
            String s = DockerTrainerUtil.pauseContainer(docker.getHost(), docker.getPort(), docker.getUsername(), docker.getPassword(), taskId);
            taskRepository.updateTaskStatus(taskId, "paused", "容器已暂停");
            taskRepository.addTrainingLog(taskId, "INFO", "容器已暂停: " + taskId);
            return s;
        } catch (RuntimeException e) {
            taskRepository.updateTaskStatus(taskId, "failed", "暂停任务失败: " + e.getMessage());
            taskRepository.addTrainingLog(taskId, "ERROR", "暂停任务失败: " + e.getMessage());
            throw e;
        }
    }


    @Override
    public String getTaskStatus(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            throw new RuntimeException("任务ID不能为空");
        }
        log.info("查询任务状态: taskId={}, containerId={}", taskId, taskId);
        return DockerTrainerUtil.getContainerStatus(
                docker.getHost(), docker.getPort(), docker.getUsername(), docker.getPassword(), taskId);

    }

    @Override
    public Future<String> resumeTask(String taskId) {
        return executorService.submit(() -> {
            try {
                String s = DockerTrainerUtil.resumeContainer(docker.getHost(), docker.getPort(), docker.getUsername(), docker.getPassword(), taskId);
                taskRepository.updateTaskStatus(taskId, "running", "容器已恢复运行");
                taskRepository.addTrainingLog(taskId, "INFO", "容器已恢复运行: " + taskId);
                return s;
            } catch (RuntimeException e) {
                log.error("恢复任务失败: taskId={}", taskId, e);
                taskRepository.updateTaskStatus(taskId, "failed", e.getMessage());
                taskRepository.addTrainingLog(taskId, "ERROR", "任务重启失败: " + e.getMessage());
                throw e;
            }
        });

    }

    @Override
    public String removeTask(String taskId) {
        try {
            String s = DockerTrainerUtil.removeContainer(docker.getHost(), docker.getPort(), docker.getUsername(), docker.getPassword(), taskId);
            taskRepository.deleteTask(taskId);
            taskRepository.addTrainingLog(taskId, "INFO", "任务已删除: " + taskId);
            return s;
        } catch (RuntimeException e) {
            taskRepository.addTrainingLog(taskId, "ERROR", "任务删除失败: " + e.getMessage());
            throw e;
        }
    }

    @Override
    public String getTaskLogs(String taskId, Integer lastLines) {
        return DockerTrainerUtil.getContainerLogs(docker.getHost(), docker.getPort(), docker.getUsername(), docker.getPassword(), taskId, lastLines);
    }

    @Override
    public String getRunningTaskInfo() {
        return DockerTrainerUtil.listTrainingContainers(docker.getHost(), docker.getPort(), docker.getUsername(), docker.getPassword());
    }

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        ContextLoader.loadContext();
        DockerTrainerServiceImpl dockerTrainerService = new DockerTrainerServiceImpl();
        String ConfigStr = "{\n" +
                "  \"model_name\": \"yolov11\",\n" +
                "  \"model_category\": \"detection\",\n" +
                "  \"model_framework\": \"pytorch\",\n" +
                "  \"model_path\": \"/data/wangshuanglong/models/yolo11n.pt\",\n" +
                "  \"data\": \"/data/wangshuanglong/datasets/YoloV8/data.yaml\",\n" +
                "  \"epochs\": 1,\n" +
                "  \"batch\": 2,\n" +
                "  \"imgsz\": 640,\n" +
                "  \"device\": \"0\",\n" +
                "  \"the_train_type\": \"train\",\n" +
                "  \"project\": \"/data/wangshuanglong/project/new\",\n" +
                "  \"name\": \"yolov11_traffic_detection_exp_005\",\n" +
                "  \"user_id\":\"241224\",\n" +
                "  \"template_id\" : 1\n" +
                "}";
        JSONObject config = JSONUtil.parseObj(ConfigStr);
        Future<String> stringFuture = dockerTrainerService.startTrainingTask(config);
//        System.out.println(stringFuture.get());
//        for (int i = 0; i < 20; i++) {
//            System.out.println(dockerTrainerService.getTaskStatus("train_task_20261124_004"));
//            Thread.sleep(1000);
//        }
    }
}
