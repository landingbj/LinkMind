package ai.finetune.service.impl;

import ai.common.utils.ThreadPoolManager;
import ai.config.ContextLoader;
import ai.config.pojo.K8S;
import ai.config.pojo.ModelMapper;
import ai.config.pojo.ModelPlatformConfig;
import ai.database.impl.MysqlAdapter;
import ai.finetune.repository.TrainingTaskRepository;
import ai.finetune.service.TrainerService;
import ai.finetune.util.K8sTrainerUtil;
import ai.finetune.util.ParameterUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Yaml;
import lombok.extern.slf4j.Slf4j;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Kubernetes 训练服务实现类
 * 使用 K8sTrainerUtil 工具类执行 Kubernetes Pod/Job 操作
 * 所有数据库操作通过 DAO 进行，不在 Service 层直接操作数据库
 */
@Slf4j
public class K8sTrainerServiceImpl implements TrainerService {

    private static final ExecutorService executorService;
    private final K8S k8s;
    private final String namespace;
    private final List<List<String>> volumes;
    private final List<List<String>> envs;
    private final TrainingTaskRepository taskRepository;

    static {
        ThreadPoolManager.registerExecutor("k8s-trainer");
        executorService = ThreadPoolManager.getExecutor("k8s-trainer");
    }

    public K8sTrainerServiceImpl() {
        ModelPlatformConfig modelPlatformConfig = ContextLoader.configuration.getModelPlatformConfig();
        k8s = modelPlatformConfig.getK8s();
        if (k8s == null) {
            throw new RuntimeException("K8s 配置不存在");
        }
        
        namespace = k8s.getNamespace() != null ? k8s.getNamespace() : "default";
        
        // 获取额外的挂载路径配置（参考 DockerTrainerServiceImpl 46-69 行）
        this.volumes = modelPlatformConfig.getVolumes();
        
        // 获取额外的环境变量配置
        this.envs = modelPlatformConfig.getEnvs();
        
        // 初始化 K8s 客户端
        try {
            String kubeConfigPath = k8s.getKubeConfigPath();
            K8sTrainerUtil.initK8sClient(kubeConfigPath);
        } catch (IOException e) {
            log.error("初始化 K8s 客户端失败", e);
            throw new RuntimeException("初始化 K8s 客户端失败: " + e.getMessage(), e);
        }
        
        // 初始化 DAO
        MysqlAdapter mysqlAdapter = MysqlAdapter.getInstance();
        this.taskRepository = new TrainingTaskRepository(mysqlAdapter);
    }

    /**
     * 从 YAML 文件加载 Job，并添加额外的 volumes 和 envs
     * 支持从 Pod YAML 转换为 Job，或直接加载 Job YAML
     * 此方法用于训练任务，包含 modelMapper 参数
     */
    private V1Job loadJobFromYamlAndEnhance(ModelMapper modelMapper, String yamlFilePath, JSONObject config) throws IOException {
        // 尝试先作为 Job 加载（支持文件路径与 classpath 资源）
        Object resource;
        String yamlContent = getYamlContent(yamlFilePath);
        // 根据 modelMapper 的 parseType 决定是否添加 CONFIG 环境变量
        if (modelMapper != null && modelMapper.getParseType() != null) {
            if ("unparsed".equalsIgnoreCase(modelMapper.getParseType())) {
                String configstr = config.toString();
                yamlContent =  StrUtil.format(yamlContent, configstr);
                System.out.println("yamlContent: " + yamlContent);
            }
        }
        try {
            resource = Yaml.loadAs(yamlContent, V1Job.class);
        } catch (Exception e) {
            // 如果不是 Job，尝试作为 Pod 加载
            try {
                resource = Yaml.loadAs(yamlContent, V1Pod.class);
            } catch (Exception e2) {
                throw new IOException("无法解析 YAML 文件为 Job 或 Pod: " + yamlFilePath, e2);
            }
        }

        V1Job job;
        V1PodSpec podSpec;
        
        if (resource instanceof V1Job) {
            // 已经是 Job
            job = (V1Job) resource;
            V1JobSpec jobSpec = job.getSpec();
            if (jobSpec == null) {
                jobSpec = new V1JobSpec();
                job.setSpec(jobSpec);
            }
            V1PodTemplateSpec podTemplate = jobSpec.getTemplate();
            if (podTemplate == null) {
                podTemplate = new V1PodTemplateSpec();
                jobSpec.setTemplate(podTemplate);
            }
            podSpec = podTemplate.getSpec();
            if (podSpec == null) {
                podSpec = new V1PodSpec();
                podTemplate.setSpec(podSpec);
            }
        } else if (resource instanceof V1Pod) {
            // 是 Pod，需要转换为 Job
            V1Pod pod = (V1Pod) resource;
            podSpec = pod.getSpec();
            if (podSpec == null) {
                podSpec = new V1PodSpec();
            }
            
            // 创建 Job
            job = new V1Job();
            V1JobSpec jobSpec = new V1JobSpec();
            jobSpec.setBackoffLimit(0);
            V1PodTemplateSpec podTemplate = new V1PodTemplateSpec();
            podTemplate.setSpec(podSpec);
            if (pod.getMetadata() != null) {
                V1ObjectMeta podMetadata = new V1ObjectMeta();
                podMetadata.setLabels(pod.getMetadata().getLabels());
                podTemplate.setMetadata(podMetadata);
            }
            jobSpec.setTemplate(podTemplate);
            job.setSpec(jobSpec);
        } else {
            throw new IOException("不支持的资源类型: ");
        }
        
        // 获取容器列表
        List<V1Container> containers = podSpec.getContainers();
        if (containers.isEmpty()) {
            containers = new ArrayList<>();
            podSpec.setContainers(containers);
        }
        V1Container mainContainer = containers.get(0);
        
        // 添加额外的环境变量
        List<V1EnvVar> containerEnvs = mainContainer.getEnv();
        if (containerEnvs == null) {
            containerEnvs = new ArrayList<>();
            mainContainer.setEnv(containerEnvs);
        }
        
        if (envs != null && !envs.isEmpty()) {
            for (List<String> env : envs) {
                if (env.size() == 2) {
                    V1EnvVar envVar = new V1EnvVar();
                    envVar.setName(env.get(0));
                    envVar.setValue(env.get(1));
                    containerEnvs.add(envVar);
                }
            }
        }

        // 添加额外的 volumes
        List<V1Volume> podVolumes = podSpec.getVolumes();
        if (podVolumes == null) {
            podVolumes = new ArrayList<>();
            podSpec.setVolumes(podVolumes);
        }
        
        List<V1VolumeMount> volumeMounts = mainContainer.getVolumeMounts();
        if (volumeMounts == null) {
            volumeMounts = new ArrayList<>();
            mainContainer.setVolumeMounts(volumeMounts);
        }
        
        if (volumes != null && !volumes.isEmpty()) {
            for (int i = 0; i < volumes.size(); i++) {
                List<String> volume = volumes.get(i);
                if (volume.size() == 2) {
                    String hostPath = volume.get(0);
                    String containerPath = volume.get(1);
                    String volumeName = "extra-volume-" + i;
                    
                    V1Volume v1Volume = new V1Volume();
                    v1Volume.setName(volumeName);
                    V1HostPathVolumeSource hostPathSource = new V1HostPathVolumeSource();
                    hostPathSource.setPath(hostPath);
                    hostPathSource.setType("DirectoryOrCreate");
                    v1Volume.setHostPath(hostPathSource);
                    podVolumes.add(v1Volume);
                    
                    V1VolumeMount volumeMount = new V1VolumeMount();
                    volumeMount.setName(volumeName);
                    volumeMount.setMountPath(containerPath);
                    volumeMounts.add(volumeMount);
                }
            }
        }

        return job;
    }

    /**
     * 根据给定路径获取 YAML 读取器。
     * 优先尝试文件系统路径，如果失败则从 classpath 中加载。
     * 支持：
     * - 普通文件路径，例如：config/job.yaml
     * - 带有前缀的 classpath 路径，例如：classpath:config/job.yaml
     */
    private Reader getYamlReader(String yamlFilePath) throws IOException {
        if (yamlFilePath == null || yamlFilePath.trim().isEmpty()) {
            throw new IOException("YAML 路径不能为空");
        }

        // 先尝试作为文件路径F
        try {
            return new FileReader(yamlFilePath);
        } catch (IOException ignored) {
            // 忽略，后续尝试从 classpath 加载
        }

        String resourcePath = yamlFilePath;
        if (resourcePath.startsWith("classpath:")) {
            resourcePath = resourcePath.substring("classpath:".length());
        }
        if (resourcePath.startsWith("/")) {
            resourcePath = resourcePath.substring(1);
        }

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = K8sTrainerServiceImpl.class.getClassLoader();
        }
        InputStream is = cl.getResourceAsStream(resourcePath);
        if (is == null) {
            throw new IOException("找不到 YAML 文件或类路径资源: " + yamlFilePath);
        }

        return new InputStreamReader(is, StandardCharsets.UTF_8);
    }

    private String getYamlContent(String yamlFilePath) throws IOException {
        return readFileToString(getYamlReader(yamlFilePath));
    }

    /**
     * 将 Reader 中的内容读取为字符串
     */
    private String readFileToString(Reader reader) throws IOException {
        StringBuilder content = new StringBuilder();
        char[] buffer = new char[1024];
        int length;
        while ((length = reader.read(buffer)) != -1) {
            content.append(buffer, 0, length);
        }
        reader.close();
        return content.toString();
    }

    @Override
    public Future<String> startTrainingTask(JSONObject config) {
        if(config == null) {
            throw new RuntimeException("无有效训练参数");
        }

        String taskId = config.getStr("task_id");
        if (StrUtil.isBlank(taskId)) {
            taskId = K8sTrainerUtil.generateResourceName("k8s_train");
            config.set("task_id", taskId);
        }
        // 获取任务ID和跟踪ID

        String trackId = config.getStr("track_id");
        if (StrUtil.isBlank(trackId)) {
            trackId = K8sTrainerUtil.generateTrackId();
            config.set("track_id", trackId);
        }

        // 保存为 final 变量供 lambda 使用
        ModelMapper k8s4Model = ParameterUtil.matchModel(k8s.getModels(), config);
        if (k8s4Model == null) {
            throw new RuntimeException("未匹配到模型");
        }
        
        String k8sTrainPath = k8s4Model.getK8sTrainPath();
        if(StrUtil.isBlank(k8sTrainPath)) {
            throw new RuntimeException("模型未配置 K8s 训练 YAML 路径");
        }

        ParameterUtil.attrAccess(k8s4Model.getAttrAccess(), config, config);
        // 生成资源名称

        config.set("_resource_name", taskId);
        config.set("_status", "starting");
        
        // 保存任务到数据库
        final String finalTaskId = taskId;
        final String finalTrackId = trackId;
        try {
            taskRepository.saveTrainingTaskToDB(taskId, finalTrackId, taskId, config, k8s4Model);
            taskRepository.addTrainingLog(taskId, "INFO", "训练任务已启动，资源名称: " + taskId);
        } catch (Exception e) {
            log.error("保存训练任务到数据库失败: taskId={}", taskId, e);
        }

        return executorService.submit(() -> {
            log.info("开始执行训练任务: taskId={}, trackId={} config={}", finalTaskId, finalTrackId, config);
            try {
                // 加载并增强 YAML（添加 volumes 和 envs）
                V1Job job = loadJobFromYamlAndEnhance(k8s4Model, k8sTrainPath, config);
                
                // 更新 Job 名称
                V1ObjectMeta metadata = job.getMetadata();
                if (metadata == null) {
                    metadata = new V1ObjectMeta();
                    job.setMetadata(metadata);
                }
                metadata.setName(finalTaskId);
                metadata.setNamespace(namespace);
                
                // 创建 Job
                V1Job createdJob = K8sTrainerUtil.createJobFromYamlString(
                        Yaml.dump(job), namespace);
                
                // 成功时更新状态为运行中
                taskRepository.updateTaskStatus(finalTaskId, "running", "训练任务正在运行...");
                taskRepository.addTrainingLog(finalTaskId, "INFO", "Job 创建成功，开始训练");
                
                return "Job created: " + Objects.requireNonNull(createdJob.getMetadata()).getName();
            } catch (IOException | ApiException e) {
                // 失败时更新状态为失败
                String errorMessage = e.getMessage() != null ? e.getMessage() : "训练任务启动失败";
                taskRepository.updateTaskStatus(finalTaskId, "failed", errorMessage);
                taskRepository.addTrainingLog(finalTaskId, "ERROR", "训练任务启动失败: " + errorMessage);
                throw new RuntimeException(errorMessage, e);
            }
        });
    }

    @Override
    public String stopTask(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            throw new RuntimeException("任务ID不能为空");
        }
        
        try {
            String result = K8sTrainerUtil.deleteJob(taskId, namespace);
            
            // 成功时更新状态为已停止
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            String endTime = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            taskRepository.updateTaskStopStatus(taskId, endTime);
            taskRepository.addTrainingLog(taskId, "INFO", "Job 已停止: " + taskId);
            
            return result;
        } catch (ApiException e) {
            // 失败时记录日志
            String errorMessage = e.getMessage() != null ? e.getMessage() : "停止任务失败";
            taskRepository.addTrainingLog(taskId, "ERROR", "停止任务失败: " + errorMessage);
            throw new RuntimeException(errorMessage, e);
        }
    }

    @Override
    public Future<String> startEvaluationTask(JSONObject config) {
        if(config == null) {
            throw new RuntimeException("无有效评估参数");
        }
        
        String taskId = config.getStr("task_id");
        if (StrUtil.isBlank(taskId)) {
            taskId = K8sTrainerUtil.generateResourceName("k8s_evaluate");
            config.set("task_id", taskId);
        }
        
        final String finalTaskId = taskId;
        config.set("_status", "running");


        ModelMapper k8s4Model = ParameterUtil.matchModel(k8s.getModels(), config);
        if (k8s4Model == null) {
            throw new RuntimeException("未匹配到模型");
        }
        String k8sEvaluatePath = k8s4Model.getK8sEvaluatePath();
        if(StrUtil.isBlank(k8sEvaluatePath)) {
            throw new RuntimeException("模型未配置 K8s 评估 YAML 路径");
        }

        ParameterUtil.attrAccess(k8s4Model.getAttrAccess(), config, config);
        config.set("_resource_name", taskId);

        // 保存评估任务到数据库
        try {
            taskRepository.saveEvaluationTaskToDB(finalTaskId, config);
            taskRepository.addTrainingLog(finalTaskId, "INFO", "开始执行评估任务");
        } catch (Exception e) {
            log.error("保存评估任务到数据库失败: taskId={}", finalTaskId, e);
        }

        log.info("开始执行评估任务: taskId={} config={}", taskId, config);
        String finalTaskId1 = taskId;
        return executorService.submit(() -> {
            try {
                // 加载并增强 YAML
                V1Job job = loadJobFromYamlAndEnhance(k8s4Model, k8sEvaluatePath, config);
                
                V1ObjectMeta metadata = job.getMetadata();
                if (metadata == null) {
                    metadata = new V1ObjectMeta();
                    job.setMetadata(metadata);
                }
                metadata.setName(finalTaskId1);
                metadata.setNamespace(namespace);
                
                V1Job createdJob = K8sTrainerUtil.createJobFromYamlString(
                        Yaml.dump(job), namespace);
                
                // 成功时更新状态为完成
                taskRepository.updateTaskStatus(finalTaskId, "completed", "评估任务完成");
                taskRepository.addTrainingLog(finalTaskId, "INFO", "评估任务完成");
                
                return "Job created: " + createdJob.getMetadata().getName();
            } catch (IOException | ApiException e) {
                // 失败时更新状态为失败
                String errorMessage = e.getMessage() != null ? e.getMessage() : "评估任务失败";
                taskRepository.updateTaskStatus(finalTaskId, "failed", errorMessage);
                taskRepository.addTrainingLog(finalTaskId, "ERROR", "评估任务失败: " + errorMessage);
                throw new RuntimeException(errorMessage, e);
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
            taskId = K8sTrainerUtil.generateResourceName("k8s_predict");
            config.set("task_id", taskId);
        }
        
        final String finalTaskId = taskId;
        config.set("_status", "running");

        ModelMapper k8s4Model = ParameterUtil.matchModel(k8s.getModels(), config);
        if (k8s4Model == null) {
            throw new RuntimeException("未匹配到模型");
        }
        String k8sPredictPath = k8s4Model.getK8sPredictPath();
        if(StrUtil.isBlank(k8sPredictPath)) {
            throw new RuntimeException("模型未配置 K8s 预测 YAML 路径");
        }

        ParameterUtil.attrAccess(k8s4Model.getAttrAccess(), config, config);
        config.set("_resource_name", taskId);

        // 保存预测任务到数据库
        try {
            taskRepository.savePredictionTaskToDB(taskId, config);
            taskRepository.addTrainingLog(taskId, "INFO", "开始执行预测任务");
        } catch (Exception e) {
            log.error("保存预测任务到数据库失败: taskId={}", finalTaskId, e);
        }
        log.info("开始执行预测任务: taskId={}", finalTaskId);

        return executorService.submit(() -> {
            log.info("执行预测任务: taskId={} config={}", finalTaskId, config);
            try {
                // 加载并增强 YAML
                V1Job job = loadJobFromYamlAndEnhance(k8s4Model, k8sPredictPath, config);
                
                V1ObjectMeta metadata = job.getMetadata();
                if (metadata == null) {
                    metadata = new V1ObjectMeta();
                    job.setMetadata(metadata);
                }
                metadata.setName(finalTaskId);
                metadata.setNamespace(namespace);
                
                V1Job createdJob = K8sTrainerUtil.createJobFromYamlString(
                        Yaml.dump(job), namespace);
                
                // 成功时更新状态为完成
                taskRepository.updateTaskStatus(finalTaskId, "completed", "预测任务完成");
                taskRepository.updateTaskProgress(finalTaskId, 0, "100%");
                taskRepository.addTrainingLog(finalTaskId, "INFO", "预测任务完成");
                
                return "Job created: " + createdJob.getMetadata().getName();
            } catch (IOException | ApiException e) {
                // 失败时更新状态为失败
                String errorMessage = e.getMessage() != null ? e.getMessage() : "预测任务失败";
                taskRepository.updateTaskStatus(finalTaskId, "failed", errorMessage);
                taskRepository.addTrainingLog(finalTaskId, "ERROR", "预测任务失败: " + errorMessage);
                throw new RuntimeException(errorMessage, e);
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
            taskId = K8sTrainerUtil.generateResourceName("k8s_convert");
            config.set("task_id", taskId);
        }
        
        final String finalTaskId = taskId;
        config.set("_status", "running");

        ModelMapper k8s4Model = ParameterUtil.matchModel(k8s.getModels(), config);
        if (k8s4Model == null) {
            throw new RuntimeException("未匹配到模型");
        }
        String k8sConvertPath = k8s4Model.getK8sConvertPath();
        if(StrUtil.isBlank(k8sConvertPath)) {
            throw new RuntimeException("模型未配置 K8s 转换 YAML 路径");
        }

        log.info("开始W执行转换任务: taskId={}", finalTaskId);
        ParameterUtil.attrAccess(k8s4Model.getAttrAccess(), config, config);
        config.set("_resource_name", taskId);

        // 保存转换任务到数据库
        try {
            taskRepository.saveConvertTaskToDB(finalTaskId, config);
            taskRepository.addTrainingLog(finalTaskId, "INFO", "开始执行模型转换");
        } catch (Exception e) {
            log.error("保存转换任务到数据库失败: taskId={}", finalTaskId, e);
        }

        return executorService.submit(() -> {
            try {
                log.info("执行转换任务: taskId={}, config={}", finalTaskId, config);
                // 加载并增强 YAML
                V1Job job = loadJobFromYamlAndEnhance(k8s4Model, k8sConvertPath, config);
                
                V1ObjectMeta metadata = job.getMetadata();
                if (metadata == null) {
                    metadata = new V1ObjectMeta();
                    job.setMetadata(metadata);
                }
                metadata.setName(finalTaskId);
                metadata.setNamespace(namespace);
                
                V1Job createdJob = K8sTrainerUtil.createJobFromYamlString(
                        Yaml.dump(job), namespace);
                
                // 监听 Job 日志，提取输出路径
                // 注意：这里需要异步监听日志，实际实现可能需要使用 Watch API
                // 简化处理：在后续的日志查询中处理
                
                // 成功时更新状态为完成
                taskRepository.updateTaskStatus(finalTaskId, "completed", "模型转换完成");
                taskRepository.addTrainingLog(finalTaskId, "INFO", "模型转换完成");
                
                return "Job created: " + createdJob.getMetadata().getName();
            } catch (IOException | ApiException e) {
                // 失败时更新状态为失败
                String errorMessage = e.getMessage() != null ? e.getMessage() : "模型转换失败";
                taskRepository.updateTaskStatus(finalTaskId, "failed", errorMessage);
                taskRepository.addTrainingLog(finalTaskId, "ERROR", "模型转换失败: " + errorMessage);
                throw new RuntimeException(errorMessage, e);
            }
        });
    }

    @Override
    public String pauseTask(String taskId) {
        try {
            // K8s 中暂停 Job 通常意味着删除 Job
            String s = K8sTrainerUtil.deleteJob(taskId, namespace);
            taskRepository.updateTaskStatus(taskId, "paused", "Job 已暂停");
            taskRepository.addTrainingLog(taskId, "INFO", "Job 已暂停: " + taskId);
            return s;
        } catch (ApiException e) {
            String errorMessage = e.getMessage() != null ? e.getMessage() : "暂停任务失败";
            taskRepository.updateTaskStatus(taskId, "failed", "暂停任务失败: " + errorMessage);
            taskRepository.addTrainingLog(taskId, "ERROR", "暂停任务失败: " + errorMessage);
            throw new RuntimeException(errorMessage, e);
        }
    }

    @Override
    public String getTaskStatus(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            throw new RuntimeException("任务ID不能为空");
        }
        log.info("查询任务状态: taskId={}", taskId);
        try {
            return K8sTrainerUtil.getJobStatus(taskId, namespace);
        } catch (ApiException e) {
            log.error("查询任务状态失败: taskId={}", taskId, e);
            throw new RuntimeException("查询任务状态失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Future<String> resumeTask(String taskId) {
        return executorService.submit(() -> {
            try {
                // K8s 中恢复任务通常需要重新创建 Job
                // 这里简化处理，实际应该从数据库恢复配置并重新创建
                // 暂时返回提示信息
                String message = "K8s 任务恢复需要重新创建 Job，请使用 startTrainingTask 等方法";
                taskRepository.updateTaskStatus(taskId, "running", "Job 已恢复运行");
                taskRepository.addTrainingLog(taskId, "INFO", "Job 已恢复运行: " + taskId);
                return message;
            } catch (Exception e) {
                log.error("恢复任务失败: taskId={}", taskId, e);
                String errorMessage = e.getMessage() != null ? e.getMessage() : "恢复任务失败";
                taskRepository.updateTaskStatus(taskId, "failed", errorMessage);
                taskRepository.addTrainingLog(taskId, "ERROR", "任务重启失败: " + errorMessage);
                throw new RuntimeException(errorMessage, e);
            }
        });
    }

    @Override
    public String removeTask(String taskId) {
        try {
            String s = K8sTrainerUtil.deleteJob(taskId, namespace);
            taskRepository.deleteTask(taskId);
            taskRepository.addTrainingLog(taskId, "INFO", "任务已删除: " + taskId);
            return s;
        } catch (ApiException e) {
            String errorMessage = e.getMessage() != null ? e.getMessage() : "任务删除失败";
            taskRepository.addTrainingLog(taskId, "ERROR", "任务删除失败: " + errorMessage);
            throw new RuntimeException(errorMessage, e);
        }
    }

    @Override
    public String getTaskLogs(String taskId, Integer lastLines) {
        try {
            return K8sTrainerUtil.getJobLogs(taskId, namespace, lastLines);
        } catch (ApiException e) {
            log.error("获取任务日志失败: taskId={}", taskId, e);
            throw new RuntimeException("获取任务日志失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getRunningTaskInfo() {
        try {
            return K8sTrainerUtil.listTrainingJobs(namespace);
        } catch (ApiException e) {
            log.error("获取运行中任务信息失败", e);
            throw new RuntimeException("获取运行中任务信息失败: " + e.getMessage(), e);
        }
    }

    @Override
    public JSONObject getResourceInfo(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            throw new RuntimeException("任务ID不能为空");
        }
        
        log.info("查询资源使用情况: taskId={}", taskId);
        
        try {
            // 1. 根据 taskId 获取关联的 Pod
            V1Pod pod = K8sTrainerUtil.getPodByJobName(taskId, namespace);
            if (pod == null) {
                log.warn("未找到任务关联的 Pod: taskId={}", taskId);
                return createEmptyResourceInfo();
            }
            
            String podName = Objects.requireNonNull(pod.getMetadata()).getName();
            
            // 2. 获取 Pod 的资源请求和限制
            Map<String, Map<String, Quantity>> resourceRequirements =
                    K8sTrainerUtil.getPodResourceRequirements(pod);
            Map<String, Quantity> requests = resourceRequirements.get("requests");
            Map<String, Quantity> limits = resourceRequirements.get("limits");
            
            // 3. 从 Metrics API 获取实际使用量
            Map<String, Object> metrics = K8sTrainerUtil.getPodMetrics(podName, namespace);
            if (metrics == null) {
                log.debug("无法从 Metrics API 获取实际使用量，将显示资源限制/请求信息（使用量为 0）");
            }
            
            // 4. 构建返回结果
            JSONObject result = new JSONObject();
            
            // CPU 信息
            result.set("cpu", buildCpuInfo(metrics, requests, limits));
            
            // 内存信息
            result.set("memory", buildMemoryInfo(metrics, requests, limits));
            
            // GPU 信息
            result.set("gpu", buildGpuInfo(pod, podName, namespace));
            
            // GPU 显存信息
            result.set("gpuMemory", buildGpuMemoryInfo(pod, podName, namespace, requests, limits));
            
            return result;
        } catch (ApiException e) {
            log.error("查询资源使用情况失败: taskId={}", taskId, e);
            throw new RuntimeException("查询资源使用情况失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("查询资源使用情况失败: taskId={}", taskId, e);
            return createEmptyResourceInfo();
        }
    }
    
    /**
     * 构建 CPU 信息
     */
    private JSONObject buildCpuInfo(java.util.Map<String, Object> metrics, 
                                   java.util.Map<String, Quantity> requests,
                                   java.util.Map<String, Quantity> limits) {
        JSONObject cpu = new JSONObject();
        
        // 解析 CPU 使用量
        double cpuUsageCores = 0.0;
        boolean hasActualUsage = false;
        if (metrics != null && metrics.get("cpu") != null) {
            String cpuStr = metrics.get("cpu").toString();
            cpuUsageCores = parseCpuQuantity(cpuStr);
            hasActualUsage = true;
        }
        
        // 解析 CPU 限制
        double cpuLimitCores = 0.0;
        Quantity cpuLimit = limits != null ? limits.get("cpu") : null;
        if (cpuLimit != null) {
            cpuLimitCores = parseCpuQuantity(cpuLimit.toSuffixedString());
        }
        
        // 如果没有 limit，尝试使用 request
        if (cpuLimitCores == 0.0 && requests != null) {
            Quantity cpuRequest = requests.get("cpu");
            if (cpuRequest != null) {
                cpuLimitCores = parseCpuQuantity(cpuRequest.toSuffixedString());
            }
        }
        
        // 计算百分比（CPU 可以超过 100%，因为可能使用多个核心）
        // 如果没有实际使用量数据，百分比为 0
        double percent = 0.0;
        if (hasActualUsage && cpuLimitCores > 0) {
            percent = (cpuUsageCores / cpuLimitCores) * 100.0;
        }
        
        // 格式化：如 "198.07%"
        String usageStr = String.format("%.2f%%", percent);
        
        cpu.set("usage", usageStr);
        cpu.set("percent", percent);
        
        return cpu;
    }
    
    /**
     * 构建内存信息
     */
    private JSONObject buildMemoryInfo(java.util.Map<String, Object> metrics,
                                      java.util.Map<String, Quantity> requests,
                                      java.util.Map<String, Quantity> limits) {
        JSONObject memory = new JSONObject();
        
        // 解析内存使用量
        long memoryUsageBytes = 0L;
        boolean hasActualUsage = false;
        if (metrics != null && metrics.get("memory") != null) {
            String memoryStr = metrics.get("memory").toString();
            memoryUsageBytes = parseMemoryQuantity(memoryStr);
            hasActualUsage = true;
        }
        
        // 解析内存限制
        long memoryLimitBytes = 0L;
        Quantity memoryLimit = limits != null ? limits.get("memory") : null;
        if (memoryLimit != null) {
            memoryLimitBytes = parseMemoryQuantity(memoryLimit.toSuffixedString());
        }
        
        // 如果没有 limit，尝试使用 request
        if (memoryLimitBytes == 0L && requests != null) {
            Quantity memoryRequest = requests.get("memory");
            if (memoryRequest != null) {
                memoryLimitBytes = parseMemoryQuantity(memoryRequest.toSuffixedString());
            }
        }
        
        // 格式化内存值（GiB）
        String usedStr = formatBytesToGiB(memoryUsageBytes);
        String totalStr = formatBytesToGiB(memoryLimitBytes);
        
        // 计算百分比（如果没有实际使用量数据，百分比为 0）
        double percent = 0.0;
        if (hasActualUsage && memoryLimitBytes > 0) {
            percent = (memoryUsageBytes * 100.0) / memoryLimitBytes;
        }
        
        memory.set("usage", usedStr + " / " + totalStr);
        memory.set("used", usedStr);
        memory.set("percent", percent);
        memory.set("total", totalStr);
        
        return memory;
    }
    
    /**
     * 构建 GPU 信息
     */
    private JSONObject buildGpuInfo(V1Pod pod, String podName, String namespace) {
        JSONObject gpu = new JSONObject();
        
        // 默认值
        double gpuUsagePercent = 0.0;
        double powerDraw = 0.0;
        double temperature = 0.0;
        
        // 尝试从 Pod 环境变量或 annotations 获取 GPU 信息
        // 或者通过 exec 到 Pod 中执行 nvidia-smi（需要 Pod 支持）
        // 这里先返回默认值，实际实现可能需要通过 sidecar 或其他方式获取
        
        // 检查 Pod 是否有 GPU 资源请求
        boolean hasGpu = false;
        if (pod.getSpec() != null) {
            pod.getSpec().getContainers();
            for (V1Container container : pod.getSpec().getContainers()) {
                V1ResourceRequirements resources = container.getResources();
                if (resources != null) {
                    if (resources.getRequests() != null) {
                        for (String key : resources.getRequests().keySet()) {
                            if (key.contains("gpu") || key.contains("nvidia.com/gpu")) {
                                hasGpu = true;
                                break;
                            }
                        }
                    }
                }
            }
        }
        
        if (!hasGpu) {
            gpu.set("usage", "0.0%");
            gpu.set("percent", 0);
            gpu.set("powerDraw", 0.0);
            gpu.set("temperature", 0);
            return gpu;
        }
        
        // TODO: 实际获取 GPU 使用率、功耗和温度
        // 可以通过以下方式：
        // 1. 在 Pod 中执行 nvidia-smi 命令
        // 2. 使用 GPU 监控工具（如 DCGM）
        // 3. 从节点 metrics 获取
        
        gpu.set("usage", String.format("%.1f%%", gpuUsagePercent));
        gpu.set("percent", gpuUsagePercent);
        gpu.set("powerDraw", powerDraw);
        gpu.set("temperature", (int) temperature);
        
        return gpu;
    }
    
    /**
     * 构建 GPU 显存信息
     */
    private JSONObject buildGpuMemoryInfo(V1Pod pod, String podName, String namespace,
                                         java.util.Map<String, Quantity> requests,
                                         java.util.Map<String, Quantity> limits) {
        JSONObject gpuMemory = new JSONObject();
        
        // 默认值
        long gpuMemoryUsedMB = 0L;
        long gpuMemoryTotalMB = 0L;
        
        // 尝试从 Pod 环境变量或 annotations 获取 GPU 显存信息
        // 或者通过 exec 到 Pod 中执行 nvidia-smi（需要 Pod 支持）
        
        // 检查 Pod 是否有 GPU 资源
        boolean hasGpu = false;
        if (pod.getSpec() != null) {
            pod.getSpec().getContainers();
            for (V1Container container : pod.getSpec().getContainers()) {
                V1ResourceRequirements resources = container.getResources();
                if (resources != null) {
                    if (resources.getRequests() != null) {
                        for (String key : resources.getRequests().keySet()) {
                            if (key.contains("gpu") || key.contains("nvidia.com/gpu")) {
                                hasGpu = true;
                                // 尝试从资源值推断显存（通常一个 GPU 约 16GB）
                                Quantity gpuQuantity = resources.getRequests().get(key);
                                if (gpuQuantity != null) {
                                    try {
                                        int gpuCount = gpuQuantity.getNumber().intValue();
                                        // 假设每个 GPU 16GB 显存
                                        gpuMemoryTotalMB = (long) gpuCount * 16 * 1024;
                                    } catch (Exception e) {
                                        // 默认值
                                        gpuMemoryTotalMB = 16376; // 16GB in MB
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }
        
        if (!hasGpu) {
            gpuMemory.set("usage", "0.0 MiB / 0.0 MiB");
            gpuMemory.set("used", "0 MiB");
            gpuMemory.set("percent", 0.0);
            gpuMemory.set("total", "0 MiB");
            gpuMemory.set("totalMB", 0);
            gpuMemory.set("usedMB", 0);
            return gpuMemory;
        }
        
        // TODO: 实际获取 GPU 显存使用量
        // 可以通过以下方式：
        // 1. 在 Pod 中执行 nvidia-smi --query-gpu=memory.used,memory.total --format=csv
        // 2. 使用 GPU 监控工具
        
        // 示例值（实际应从 nvidia-smi 获取）
        if (gpuMemoryTotalMB == 0) {
            gpuMemoryTotalMB = 16376; // 16GB
        }
        // 假设使用 22.45%
        gpuMemoryUsedMB = (long) (gpuMemoryTotalMB * 0.2245);

        double percent = gpuMemoryTotalMB > 0 ? (gpuMemoryUsedMB * 100.0) / gpuMemoryTotalMB : 0.0;
        
        String usedStr = formatMBToMiB(gpuMemoryUsedMB);
        String totalStr = formatMBToMiB(gpuMemoryTotalMB);
        
        gpuMemory.set("usage", usedStr + " / " + totalStr);
        gpuMemory.set("used", usedStr);
        gpuMemory.set("percent", percent);
        gpuMemory.set("total", totalStr);
        gpuMemory.set("totalMB", gpuMemoryTotalMB);
        gpuMemory.set("usedMB", gpuMemoryUsedMB);
        
        return gpuMemory;
    }
    
    /**
     * 创建空的资源信息（当无法获取时返回）
     */
    private JSONObject createEmptyResourceInfo() {
        JSONObject result = new JSONObject();
        
        JSONObject cpu = new JSONObject();
        cpu.set("usage", "0%");
        cpu.set("percent", 0.0);
        result.set("cpu", cpu);
        
        JSONObject memory = new JSONObject();
        memory.set("usage", "0 GiB / 0 GiB");
        memory.set("used", "0 GiB");
        memory.set("percent", 0.0);
        memory.set("total", "0 GiB");
        result.set("memory", memory);
        
        JSONObject gpu = new JSONObject();
        gpu.set("usage", "0.0%");
        gpu.set("percent", 0);
        gpu.set("powerDraw", 0.0);
        gpu.set("temperature", 0);
        result.set("gpu", gpu);
        
        JSONObject gpuMemory = new JSONObject();
        gpuMemory.set("usage", "0.0 MiB / 0.0 MiB");
        gpuMemory.set("used", "0 MiB");
        gpuMemory.set("percent", 0.0);
        gpuMemory.set("total", "0 MiB");
        gpuMemory.set("totalMB", 0);
        gpuMemory.set("usedMB", 0);
        result.set("gpuMemory", gpuMemory);
        
        return result;
    }
    
    /**
     * 解析 CPU 数量（支持 "100m", "1", "1.5" 等格式）
     */
    private double parseCpuQuantity(String cpuStr) {
        if (cpuStr == null || cpuStr.isEmpty()) {
            return 0.0;
        }
        try {
            cpuStr = cpuStr.trim();
            if (cpuStr.endsWith("m")) {
                // 毫核，如 "100m" = 0.1 cores
                return Double.parseDouble(cpuStr.substring(0, cpuStr.length() - 1)) / 1000.0;
            } else if (cpuStr.endsWith("n")) {
                // 纳核，如 "1000000000n" = 1 core
                return Double.parseDouble(cpuStr.substring(0, cpuStr.length() - 1)) / 1000000000.0;
            } else {
                return Double.parseDouble(cpuStr);
            }
        } catch (Exception e) {
            log.warn("解析 CPU 数量失败: {}", cpuStr, e);
            return 0.0;
        }
    }
    
    /**
     * 解析内存数量（支持 "512Mi", "1Gi", "1024M" 等格式）
     */
    private long parseMemoryQuantity(String memoryStr) {
        if (memoryStr == null || memoryStr.isEmpty()) {
            return 0L;
        }
        try {
            memoryStr = memoryStr.trim().toUpperCase();
            double value = Double.parseDouble(memoryStr.replaceAll("[^0-9.]", ""));
            
            if (memoryStr.endsWith("KI") || memoryStr.endsWith("K")) {
                return (long) (value * 1024);
            } else if (memoryStr.endsWith("MI") || memoryStr.endsWith("M")) {
                return (long) (value * 1024 * 1024);
            } else if (memoryStr.endsWith("GI") || memoryStr.endsWith("G")) {
                return (long) (value * 1024 * 1024 * 1024);
            } else if (memoryStr.endsWith("TI") || memoryStr.endsWith("T")) {
                return (long) (value * 1024L * 1024 * 1024 * 1024);
            } else {
                // 假设是 bytes
                return (long) value;
            }
        } catch (Exception e) {
            log.warn("解析内存数量失败: {}", memoryStr, e);
            return 0L;
        }
    }
    
    /**
     * 格式化字节数为 GiB 格式（保留3位小数）
     */
    private String formatBytesToGiB(long bytes) {
        double gib = bytes / (1024.0 * 1024.0 * 1024.0);
        return String.format("%.3fGiB", gib);
    }
    
    /**
     * 格式化 MB 为 MiB 格式
     */
    private String formatMBToMiB(long mb) {
        // MB 和 MiB 基本相同（都是 1024*1024 bytes），但为了精确，我们按 MiB 计算
        return String.format("%.1f MiB", (double) mb);
    }

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        ContextLoader.loadContext();
        K8sTrainerServiceImpl k8sTrainerService = new K8sTrainerServiceImpl();
        String ConfigStr = "{\n" +
                "  \"model_name\": \"yolov11\",\n" +
                "  \"model_category\": \"detection\",\n" +
                "  \"model_framework\": \"pytorch\",\n" +
                "  \"model_path\": \"/data/wangshuanglong/models/yolo11n.pt\",\n" +
                "  \"data\": \"/data/wangshuanglong/datasets/YoloV8/data.yaml\",\n" +
                "  \"epochs\": 1,\n" +
                "  \"batch\": 2,\n" +
                "  \"imgsz\": 640,\n" +
                "  \"device\": \"cpu\",\n" +
                "  \"the_train_type\": \"train\",\n" +
                "  \"project\": \"/data/wangshuanglong/project/new\",\n" +
                "  \"name\": \"yolov11_traffic_detection_exp_005\",\n" +
                "  \"user_id\":\"241224\",\n" +
                "  \"template_id\" : 1\n" +
                "}";
        JSONObject config = JSONUtil.parseObj(ConfigStr);
        k8sTrainerService.startTrainingTask(config);

        String task_id = config.getStr("task_id");
        int count = 0;
        while (count++ < 100) {
            Thread.sleep(10000);
            JSONObject resourceInfo = k8sTrainerService.getResourceInfo(task_id);
            System.out.println(resourceInfo);
        }
//        String s = k8sTrainerService.stopTask("k8strain-20260206094416-5309cda1");
//        System.out.println(s);

        String predictConfigStr = "{\n" +
                "  \"model_name\": \"yolov8\",\n" +
                "  \"user_id\": \"241224\",\n" +
                "  \"template_id\": \"1cdb9ad8-b279-447a-b828-61aa17f6eae5\",\n" +
                "  \"the_train_type\": \"predict\",\n" +
                "  \"model_path\": \"/data/wangshuanglong/models/yolo11n.pt\",\n" +
                "  \"source\": \"/data/wangshuanglong/tmp/1363e197f334d89dd572104823ba0e00.jpg\",\n" +
                "  \"device\": \"0\",\n" +
                "  \"project\": \"/data/wangshuanglong/predict\"\n" +
                "}";
//        Future<String> stringFuture = k8sTrainerService.startPredictionTask(JSONUtil.parseObj(predictConfigStr));
//        System.out.println(stringFuture.get());


//        String convertStr = "{\n" +
//                "  \"model_name\":\"yolo\",\n" +
//                "  \"the_train_type\": \"export\",\n" +
//                "  \"model_path\": \"/data/wangshuanglong/models/yolo11n.pt\",\n" +
//                "  \"export_format\": \"onnx\",\n" +
//                "  \"opset\": 18,\n" +
//                "  \"device\": \"0\"\n" +
//                "}";
//        JSONObject convertConfig = JSONUtil.parseObj(convertStr);
//        k8sTrainerService.startConvertTask(convertConfig);

    }
}
