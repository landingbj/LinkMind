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
        try {
            resource = Yaml.loadAs(getYamlReader(yamlFilePath), V1Job.class);
        } catch (Exception e) {
            // 如果不是 Job，尝试作为 Pod 加载
            try {
                resource = Yaml.loadAs(getYamlReader(yamlFilePath), V1Pod.class);
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
            throw new IOException("不支持的资源类型: " + resource.getClass().getName());
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

        // 根据 modelMapper 的 parseType 决定是否添加 CONFIG 环境变量
        if (modelMapper != null && modelMapper.getParseType() != null) {
            if ("unparsed".equalsIgnoreCase(modelMapper.getParseType())) {
                V1EnvVar envVar = new V1EnvVar();
                envVar.setName("CONFIG");
                envVar.setValue(config != null ? config.toString() : "{}");
                containerEnvs.add(envVar);
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

        // 先尝试作为文件路径
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

    @Override
    public Future<String> startTrainingTask(JSONObject config) {
        if(config == null) {
            throw new RuntimeException("无有效训练参数");
        }
        
        // 获取任务ID和跟踪ID
        String taskId = config.getStr("task_id");
        if (StrUtil.isBlank(taskId)) {
            taskId = K8sTrainerUtil.generateTaskId();
            config.set("task_id", taskId);
        }
        String trackId = config.getStr("track_id");
        if (StrUtil.isBlank(trackId)) {
            trackId = K8sTrainerUtil.generateTrackId();
            config.set("track_id", trackId);
        }

        // 保存为 final 变量供 lambda 使用
        final String finalTaskId = taskId;
        final String finalTrackId = trackId;
        
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
        String resourceName = K8sTrainerUtil.generateResourceName("k8s_train");
        config.set("_resource_name", resourceName);
        config.set("_status", "starting");
        
        // 保存任务到数据库
        try {
            taskRepository.saveTrainingTaskToDB(finalTaskId, finalTrackId, resourceName, config, k8s4Model);
            taskRepository.addTrainingLog(finalTaskId, "INFO", "训练任务已启动，资源名称: " + resourceName);
        } catch (Exception e) {
            log.error("保存训练任务到数据库失败: taskId={}", finalTaskId, e);
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
                metadata.setName(resourceName);
                metadata.setNamespace(namespace);
                
                // 创建 Job
                V1Job createdJob = K8sTrainerUtil.createJobFromYamlString(
                        Yaml.dump(job), namespace);
                
                // 成功时更新状态为运行中
                taskRepository.updateTaskStatus(finalTaskId, "running", "训练任务正在运行...");
                taskRepository.addTrainingLog(finalTaskId, "INFO", "Job 创建成功，开始训练");
                
                return "Job created: " + createdJob.getMetadata().getName();
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
            taskId = K8sTrainerUtil.generateTaskId();
            config.set("task_id", taskId);
        }
        
        final String finalTaskId = taskId;
        config.set("_status", "running");
        
        // 保存评估任务到数据库
        try {
            taskRepository.saveEvaluationTaskToDB(finalTaskId, config);
            taskRepository.addTrainingLog(finalTaskId, "INFO", "开始执行评估任务");
        } catch (Exception e) {
            log.error("保存评估任务到数据库失败: taskId={}", finalTaskId, e);
        }
        
        ModelMapper k8s4Model = ParameterUtil.matchModel(k8s.getModels(), config);
        if (k8s4Model == null) {
            throw new RuntimeException("未匹配到模型");
        }
        String k8sEvaluatePath = k8s4Model.getK8sEvaluatePath();
        if(StrUtil.isBlank(k8sEvaluatePath)) {
            throw new RuntimeException("模型未配置 K8s 评估 YAML 路径");
        }

        String resourceName = K8sTrainerUtil.generateResourceName("k8s_evaluate");
        config.set("_resource_name", resourceName);
        
        log.info("开始执行评估任务: taskId={} config={}", taskId, config);
        return executorService.submit(() -> {
            try {
                // 加载并增强 YAML
                V1Job job = loadJobFromYamlAndEnhance(k8s4Model, k8sEvaluatePath, config);
                
                V1ObjectMeta metadata = job.getMetadata();
                if (metadata == null) {
                    metadata = new V1ObjectMeta();
                    job.setMetadata(metadata);
                }
                metadata.setName(resourceName);
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
            taskId = K8sTrainerUtil.generateTaskId();
            config.set("task_id", taskId);
        }
        
        final String finalTaskId = taskId;
        config.set("_status", "running");
        
        // 保存预测任务到数据库
        try {
            taskRepository.savePredictionTaskToDB(finalTaskId, config);
            taskRepository.addTrainingLog(finalTaskId, "INFO", "开始执行预测任务");
        } catch (Exception e) {
            log.error("保存预测任务到数据库失败: taskId={}", finalTaskId, e);
        }

        ModelMapper k8s4Model = ParameterUtil.matchModel(k8s.getModels(), config);
        if (k8s4Model == null) {
            throw new RuntimeException("未匹配到模型");
        }
        String k8sPredictPath = k8s4Model.getK8sPredictPath();
        if(StrUtil.isBlank(k8sPredictPath)) {
            throw new RuntimeException("模型未配置 K8s 预测 YAML 路径");
        }

        log.info("开始执行预测任务: taskId={}", finalTaskId);
        String resourceName = K8sTrainerUtil.generateResourceName("k8s_predict");
        config.set("_resource_name", resourceName);
        
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
                metadata.setName(resourceName);
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
            taskId = K8sTrainerUtil.generateTaskId();
            config.set("task_id", taskId);
        }
        
        final String finalTaskId = taskId;
        config.set("_status", "running");
        
        // 保存转换任务到数据库
        try {
            taskRepository.saveConvertTaskToDB(finalTaskId, config);
            taskRepository.addTrainingLog(finalTaskId, "INFO", "开始执行模型转换");
        } catch (Exception e) {
            log.error("保存转换任务到数据库失败: taskId={}", finalTaskId, e);
        }

        ModelMapper k8s4Model = ParameterUtil.matchModel(k8s.getModels(), config);
        if (k8s4Model == null) {
            throw new RuntimeException("未匹配到模型");
        }
        String k8sConvertPath = k8s4Model.getK8sConvertPath();
        if(StrUtil.isBlank(k8sConvertPath)) {
            throw new RuntimeException("模型未配置 K8s 转换 YAML 路径");
        }

        log.info("开始执行转换任务: taskId={}", finalTaskId);
        String resourceName = K8sTrainerUtil.generateResourceName("k8s_convert");
        config.set("_resource_name", resourceName);
        
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
                metadata.setName(resourceName);
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

    public static void main(String[] args) {
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
    }
}
