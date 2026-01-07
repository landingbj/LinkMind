package ai.finetune;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.custom.Quantity;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;



@Slf4j
@Getter
@Setter
public abstract class K8sTrainerAbstract implements TrainerInterface{

    // Kubernetes 访问配置
    protected String apiServer;
    protected String token;
    protected String namespace;
    protected boolean trustCerts;
    protected boolean disableHostnameVerification;
    protected String imagePullSecret;

    // 私有镜像仓库配置
    protected String registryUrl;
    protected String registryUsername;
    protected String registryPassword;

    // 重启策略配置
    protected String restartPolicy = "Never";

    // 训练镜像与挂载
    protected String dockerImage;
    protected String volumeMount;
    protected String shmSize;
    protected boolean gpuEnabled;

    // K8s 自定义配置
    protected List<ai.config.pojo.DiscriminativeModelsConfig.K8sConfig.VolumeMount> customVolumeMounts;
    protected List<ai.config.pojo.DiscriminativeModelsConfig.K8sConfig.Volume> customVolumes;

    // GPU 节点选择器（用于指定 GPU 节点标签）
    protected String gpuNodeSelectorKey = "kubernetes.io/hostname";
    protected String gpuNodeSelectorValue = "k8smaster";

    protected ApiClient apiClient;
    protected BatchV1Api batchApi;
    protected CoreV1Api coreApi;

    /**
     * 初始化 Kubernetes 客户端（官方 SDK）。
     */
    public void initK8sClient() {
        if (apiClient != null) {
            log.info("K8s客户端已存在，跳过初始化");
            return;
        }
        log.info("开始初始化K8s客户端...");
        log.info("  API Server: {}", apiServer);
        log.info("  Namespace: {}", namespace);
        try {
            apiClient = K8sClientSupport.buildOpenApiClient(apiServer, token);
            batchApi = new BatchV1Api(apiClient);
            coreApi = new CoreV1Api(apiClient);
            log.info("✓ K8s客户端初始化成功, namespace={}", namespace);
        } catch (Exception e) {
            log.error("✗ K8s客户端初始化失败");
            log.error("  API Server: {}", apiServer);
            log.error("  Namespace: {}", namespace);
            log.error("  错误: {}", e.getMessage());
            throw new RuntimeException("K8s客户端初始化失败: " + e.getMessage(), e);
        }
    }

    // 子类实现
    public abstract String startTraining(String taskId, String trackId, JSONObject config);
    public abstract String evaluate(JSONObject config);
    public abstract String predict(JSONObject config);
    public abstract String exportModel(JSONObject config);

    /**
     * 确保私有镜像仓库的拉取密钥存在，如果不存在则创建
     */
    protected void ensureImagePullSecret() {
        if (registryUrl == null || registryUrl.isEmpty() ||
                registryUsername == null || registryUsername.isEmpty() ||
                registryPassword == null || registryPassword.isEmpty()) {
            log.info("私有镜像仓库配置不完整，跳过创建镜像拉取密钥");
            return;
        }

        if (imagePullSecret == null || imagePullSecret.isEmpty()) {
            log.info("未配置imagePullSecret名称，跳过创建镜像拉取密钥");
            return;
        }

        try {
            ensureClient();

            // 检查密钥是否已存在
            try {
                coreApi.readNamespacedSecret(imagePullSecret, namespace, null, null, null);
                log.info("镜像拉取密钥 '{}' 已存在", imagePullSecret);
                return;
            } catch (ApiException e) {
                    if (e.getCode() != 404) {
                        // 整合ApiException的完整错误信息
                        String errorMsg = String.format(
                                "状态码: %d, 响应体: %s, 消息: %s",
                                e.getCode(),          // HTTP状态码（如500/403）
                                e.getResponseBody(),  // 详细的错误响应体（K8s返回的具体原因）
                                e.getMessage() == null ? "无" : e.getMessage()  // 兼容null的情况
                        );
                        log.warn("检查镜像拉取密钥存在性时出错: {}", errorMsg);
                        return;
                    }
                    // 404表示密钥不存在，需要创建
                    log.info("开始创建镜像拉取密钥: {}", imagePullSecret);
            }

            // 创建 docker-registry 类型的密钥
            V1Secret secret = new V1Secret();
            secret.setApiVersion("v1");
            secret.setKind("Secret");
            secret.setType("kubernetes.io/dockerconfigjson");

            V1ObjectMeta metadata = new V1ObjectMeta();
            metadata.setName(imagePullSecret);
            metadata.setNamespace(namespace);
            secret.setMetadata(metadata);

            // 创建 .dockerconfigjson 数据
            String auth = java.util.Base64.getEncoder().encodeToString(
                    (registryUsername + ":" + registryPassword).getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );

            // 提取registry主机名（去掉协议和路径）
            String registryHost = registryUrl.replaceFirst("https?://", "").split("/")[0];

            String dockerConfigJson = String.format(
                    "{\"auths\":{\"%s\":{\"username\":\"%s\",\"password\":\"%s\",\"auth\":\"%s\"}}}",
                    registryHost, registryUsername, registryPassword, auth
            );

            String encodedConfig = java.util.Base64.getEncoder().encodeToString(
                    dockerConfigJson.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );

            Map<String, byte[]> data = new HashMap<>();
            data.put(".dockerconfigjson", encodedConfig.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            secret.setData(data);

            // 创建密钥
            coreApi.createNamespacedSecret(namespace, secret, null, null, null);
            log.info("成功创建私有镜像仓库拉取密钥: {}", imagePullSecret);

        } catch (ApiException e) {
            log.error("创建镜像拉取密钥失败: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("创建镜像拉取密钥时发生异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 创建一次性 Job（使用默认资源配置）。
     */
    protected JSONObject createOneOffJob(String jobName,
                                         String image,
                                         String configJson,
                                         boolean gpu,
                                         String shm) {
        return createOneOffJob(jobName, image, configJson, gpu, shm, null);
    }

    /**
     * 创建一次性 Job（支持自定义资源配置）。
     */
    protected JSONObject createOneOffJob(String jobName,
                                         String image,
                                         String configJson,
                                         boolean gpu,
                                         String shm,
                                         ai.config.pojo.DiscriminativeModelsConfig.K8sConfig.Resources customResources) {
        log.info("开始创建K8s Job: {}", jobName);
        log.info("  镜像: {}", image);
        log.info("  启用GPU: {}", gpu);

        ensureClient();
        // 确保私有镜像仓库的拉取密钥存在
        ensureImagePullSecret();
        try {
            log.info("解析卷挂载配置...");
            String[] mounts = parseMount(volumeMount);
            String hostPath = mounts[0];
            String containerPath = mounts[1];
            log.info("  宿主机路径: {}, 容器路径: {}", hostPath, containerPath);

            // 构建容器
            log.info("构建容器配置...");
            V1Container container = new V1Container();
            // 根据 jobName 动态推断容器名称
            // yolo_train-xxx -> yolov8-trainer
            // deeplab_train-xxx -> deeplab-trainer
            // tracknet_train-xxx -> tracknet-trainer
            String containerName = inferContainerName(jobName);
            container.setName(containerName);
            container.setImage(image);
            log.info("  容器名称: {}, 镜像: {}", containerName, image);

            if (image == null || image.isEmpty()) {
                log.error("  ✗ 错误: 容器镜像为空");
                throw new IllegalArgumentException("容器镜像不能为空");
            }

            // 环境变量
            V1EnvVar envVar = new V1EnvVar();
            envVar.setName("CONFIG");
            envVar.setValue(configJson);
            container.setEnv(Collections.singletonList(envVar));

            // 设置容器启动命令
//            String[] commandAndArgs = buildContainerCommand(configJson);
//            if (commandAndArgs != null && commandAndArgs.length > 0) {
//                container.setCommand(Arrays.asList(commandAndArgs[0]));
//                if (commandAndArgs.length > 1) {
//                    List<String> args = new ArrayList<>();
//                    for (int i = 1; i < commandAndArgs.length; i++) {
//                        args.add(commandAndArgs[i]);
//                    }
//                    container.setArgs(args);
//                }
//                log.info("设置容器启动命令: command={}, args={}", commandAndArgs[0], container.getArgs());
//            }

            // 数据卷挂载
            List<V1VolumeMount> volumeMounts = new ArrayList<>();

            // 如果有自定义的volumeMounts配置，则使用配置
            if (customVolumeMounts != null && !customVolumeMounts.isEmpty()) {
                for (ai.config.pojo.DiscriminativeModelsConfig.K8sConfig.VolumeMount vm : customVolumeMounts) {
                    V1VolumeMount mount = new V1VolumeMount();
                    mount.setName(vm.getName());
                    mount.setMountPath(vm.getMountPath());
                    volumeMounts.add(mount);
                    log.info("添加自定义卷挂载: name={}, mountPath={}", vm.getName(), vm.getMountPath());
                }
            } else {
                // 使用默认的数据卷挂载
                V1VolumeMount dataMount = new V1VolumeMount();
                dataMount.setName("data-volume");
                dataMount.setMountPath(containerPath);
                volumeMounts.add(dataMount);
            }

            // 共享内存挂载
            if (shm != null && !shm.isEmpty()) {
                V1VolumeMount shmMount = new V1VolumeMount();
                shmMount.setName("dshm");
                shmMount.setMountPath("/dev/shm");
                volumeMounts.add(shmMount);
            }
            container.setVolumeMounts(volumeMounts);

            // ========== 设置资源限制（CPU 和内存） ==========
////                    log.info("设置资源限制...");
////                    V1ResourceRequirements resources = new V1ResourceRequirements();
////                    Map<String, Quantity> requests = new HashMap<>();
////                    Map<String, Quantity> limits = new HashMap<>();
////
////                    // 如果提供了自定义资源配置，则使用配置值
////                    if (customResources != null) {
////                        log.info("  使用自定义资源配置");
////                        if (customResources.getRequests() != null) {
////                            if (customResources.getRequests().getCpu() != null) {
////                                requests.put("cpu", new Quantity(customResources.getRequests().getCpu()));
////                            }
////                            if (customResources.getRequests().getMemory() != null) {
////                                requests.put("memory", new Quantity(customResources.getRequests().getMemory()));
////                            }
////                            if (customResources.getRequests().getNvidiaGpu() != null) {
////                                requests.put("nvidia.com/gpu", new Quantity(customResources.getRequests().getNvidiaGpu()));
////                            }
////                        }
////
////                        if (customResources.getLimits() != null) {
////                            if (customResources.getLimits().getCpu() != null) {
////                                limits.put("cpu", new Quantity(customResources.getLimits().getCpu()));
////                            }
////                            if (customResources.getLimits().getMemory() != null) {
////                                limits.put("memory", new Quantity(customResources.getLimits().getMemory()));
////                            }
////                            if (customResources.getLimits().getNvidiaGpu() != null) {
////                                limits.put("nvidia.com/gpu", new Quantity(customResources.getLimits().getNvidiaGpu()));
////                            }
////                        }
////
////                        log.info("  资源限制: CPU requests={}, limits={}, Memory requests={}, limits={}, GPU requests={}, limits={}",
////                                requests.get("cpu"), limits.get("cpu"),
////                                requests.get("memory"), limits.get("memory"),
////                                requests.get("nvidia.com/gpu"), limits.get("nvidia.com/gpu"));
////                    } else {
////                        // 使用默认资源配置
////                        log.info("  使用默认资源配置");
////                        // 注意：训练需要更多内存，1Gi 可能导致 OOMKilled
////                        // 建议：CPU 2-4核，内存 4-8Gi（根据实际需求调整）
////                        requests.put("cpu", new Quantity("2"));
////                        requests.put("memory", new Quantity("4Gi"));
////                        limits.put("cpu", new Quantity("4"));
////                        limits.put("memory", new Quantity("8Gi"));
////
////                        // GPU 资源（如果启用）
////                        if (gpu) {
////                            requests.put("nvidia.com/gpu", new Quantity("1"));
////                            limits.put("nvidia.com/gpu", new Quantity("1"));
////                            log.info("  启用GPU资源: 1 GPU");
////                        }
////
////                        log.info("  默认资源: CPU requests=2, limits=4, Memory requests=4Gi, limits=8Gi");
////                    }
//
//                    resources.setRequests(requests);
//                    resources.setLimits(limits);
//                    container.setResources(resources);
//                    log.info("✓ 资源限制设置完成");

            // 构建 Pod 规格
            log.info("构建Pod规格...");
            V1PodSpec podSpec = new V1PodSpec();
            podSpec.setRestartPolicy(restartPolicy);
            podSpec.setContainers(Collections.singletonList(container));
            log.info("  重启策略: {}", restartPolicy);

            // ========== 添加Master节点污点容忍（必须，否则无法调度到master节点） ==========
            List<V1Toleration> tolerations = new ArrayList<>();
            V1Toleration masterToleration = new V1Toleration();
            masterToleration.setKey("node-role.kubernetes.io/master");
            masterToleration.setOperator("Exists");
            masterToleration.setEffect("NoSchedule");
            tolerations.add(masterToleration);
            podSpec.setTolerations(tolerations);
            log.info("✓ 已添加 Master 节点污点容忍，允许Pod在master节点运行");

            // ========== 添加 GPU 节点选择器（强制调度到k8smaster） ==========
            // 注意：如果k8smaster没有GPU，即使设置了节点选择器，Pod也无法调度（因为请求了GPU资源）
            // 解决方案：如果目标节点没有GPU，需要移除GPU资源请求，或者调度到有GPU的节点
            if (gpu) {
                if (gpuNodeSelectorKey != null && gpuNodeSelectorValue != null
                        && !gpuNodeSelectorKey.trim().isEmpty()
                        && !gpuNodeSelectorValue.trim().isEmpty()) {
                    Map<String, String> nodeSelector = new HashMap<>();
                    nodeSelector.put(gpuNodeSelectorKey, gpuNodeSelectorValue);
                    podSpec.setNodeSelector(nodeSelector);
                    log.info("✓ 设置 GPU 节点选择器: {}={}", gpuNodeSelectorKey, gpuNodeSelectorValue);
                    log.warn("⚠ 注意：如果目标节点 {} 没有GPU资源，Pod将无法调度！", gpuNodeSelectorValue);
                    log.warn("⚠ 请确保目标节点有GPU，或者移除GPU资源请求");
                } else {
                    log.warn("⚠ GPU已启用，但节点选择器配置为空，将无法调度到指定节点");
                }
            }

            // 数据卷
            List<V1Volume> volumes = new ArrayList<>();

            // 如果有自定义的volumes配置，则使用配置
            if (customVolumes != null && !customVolumes.isEmpty()) {
                for (ai.config.pojo.DiscriminativeModelsConfig.K8sConfig.Volume vol : customVolumes) {
                    V1Volume volume = new V1Volume();
                    volume.setName(vol.getName());
                    if (vol.getHostPath() != null) {
                        V1HostPathVolumeSource hostPathSource = new V1HostPathVolumeSource();
                        hostPathSource.setPath(vol.getHostPath().getPath());
                        hostPathSource.setType(vol.getHostPath().getType());
                        volume.setHostPath(hostPathSource);
                        log.info("添加自定义卷: name={}, hostPath={}, type={}",
                                vol.getName(), vol.getHostPath().getPath(), vol.getHostPath().getType());
                    }
                    volumes.add(volume);
                }
            } else {
                // 使用默认的数据卷
                V1Volume dataVolume = new V1Volume();
                dataVolume.setName("data-volume");
                V1HostPathVolumeSource hostPathSource = new V1HostPathVolumeSource();
                hostPathSource.setPath(hostPath);
                hostPathSource.setType("DirectoryOrCreate");
                dataVolume.setHostPath(hostPathSource);
                volumes.add(dataVolume);
            }

            // 共享内存卷
            if (shm != null && !shm.isEmpty()) {
                V1Volume shmVolume = new V1Volume();
                shmVolume.setName("dshm");
                V1EmptyDirVolumeSource emptyDir = new V1EmptyDirVolumeSource();
                emptyDir.setMedium("Memory");
                if (shm != null && !shm.isEmpty()) {
                    emptyDir.setSizeLimit(Quantity.fromString(shm));
                }
                shmVolume.setEmptyDir(emptyDir);
                volumes.add(shmVolume);
            }
            podSpec.setVolumes(volumes);

            // 镜像拉取密钥
            if (imagePullSecret != null && !imagePullSecret.isEmpty()) {
                V1LocalObjectReference secretRef = new V1LocalObjectReference();
                secretRef.setName(imagePullSecret);
                podSpec.setImagePullSecrets(Collections.singletonList(secretRef));
            }

            // 构建 Pod 模板
            V1PodTemplateSpec podTemplate = new V1PodTemplateSpec();
            V1ObjectMeta podMetadata = new V1ObjectMeta();
            podMetadata.setLabels(Collections.singletonMap("app", jobName));
            podTemplate.setMetadata(podMetadata);
            podTemplate.setSpec(podSpec);

            // 构建 Job 规格
            V1JobSpec jobSpec = new V1JobSpec();
            jobSpec.setBackoffLimit(0);
            jobSpec.setTemplate(podTemplate);

            // 构建 Job
            V1Job job = new V1Job();
            V1ObjectMeta jobMetadata = new V1ObjectMeta();
            jobMetadata.setName(jobName);
            jobMetadata.setNamespace(namespace);
            jobMetadata.setLabels(Collections.singletonMap("app", jobName));
            job.setMetadata(jobMetadata);
            job.setSpec(jobSpec);

            // 创建 Job
            log.info("提交Job到K8s API Server...");
            log.info("  命名空间: {}", namespace);
            V1Job createdJob = batchApi.createNamespacedJob(namespace, job, null, null, null);
            log.info("✓ Job创建成功: {}", jobName);
            if (createdJob.getMetadata() != null && createdJob.getMetadata().getUid() != null) {
                log.info("  Job UID: {}", createdJob.getMetadata().getUid());
            }

            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("message", "K8s Job created");
            result.put("jobName", jobName);
            result.put("namespace", namespace);
            return result;
        } catch (ApiException e) {
            log.error("✗ 创建Job失败: {}", jobName);
            log.error("  HTTP状态码: {}", e.getCode());
            log.error("  错误消息: {}", e.getMessage());
            log.error("  响应体: {}", e.getResponseBody());

            // 根据错误码提供排查建议
            if (e.getCode() == 403) {
                log.error("  → 权限错误: 检查RBAC配置，确保有创建jobs的权限");
            } else if (e.getCode() == 404) {
                log.error("  → 资源不存在: 检查命名空间 '{}' 是否存在", namespace);
            } else if (e.getCode() == 422) {
                log.error("  → 验证错误: Job配置不符合K8s规范，检查Job名称、资源配置等");
            } else if (e.getCode() == 500) {
                log.error("  → 服务器错误: 检查K8s集群状态");
            }

            JSONObject result = new JSONObject();
            result.put("status", "error");
            result.put("message", "创建 Job 失败: " + e.getMessage());
            result.put("jobName", jobName);
            result.put("httpCode", e.getCode());
            result.put("responseBody", e.getResponseBody());
            return result;
        } catch (Exception e) {
            log.error("✗ 创建Job时发生未知错误: {}", jobName);
            log.error("  错误类型: {}", e.getClass().getSimpleName());
            log.error("  错误消息: {}", e.getMessage());
            log.error("  错误堆栈: ", e);

            JSONObject result = new JSONObject();
            result.put("status", "error");
            result.put("message", "创建 Job 失败: " + e.getMessage());
            result.put("jobName", jobName);
            result.put("errorType", e.getClass().getSimpleName());
            return result;
        }
    }

    /**
     * 删除 Job（对应 docker stop/rm）。
     * 先删除关联的 Pod，再删除 Job，确保 Pod 被正确清理。
     */
    public JSONObject deleteJob(String jobName) {
        ensureClient();
        int deletedPodCount = 0;
        try {
            // 先删除关联的 Pod（使用 Kubernetes 自动添加的 job-name 标签，更可靠）
            // 同时尝试使用 app 标签作为备选方案
            V1PodList podList = null;
            try {
                // 优先使用 job-name 标签（Kubernetes 自动添加）
                podList = coreApi.listNamespacedPod(
                        namespace,
                        null,
                        null,
                        null,
                        null,
                        "job-name=" + jobName,
                        null,
                        null,
                        null,
                        null,
                        null
                );
                log.info("使用 job-name 标签查找 Pod: jobName={}, 找到 {} 个 Pod", jobName, podList.getItems().size());
            } catch (ApiException e) {
                log.warn("使用 job-name 标签查找 Pod 失败，尝试使用 app 标签: {}", e.getMessage());
                // 如果 job-name 标签查找失败，尝试使用 app 标签
                try {
                    podList = coreApi.listNamespacedPod(
                            namespace,
                            null,
                            null,
                            null,
                            null,
                            "app=" + jobName,
                            null,
                            null,
                            null,
                            null,
                            null
                    );
                    log.info("使用 app 标签查找 Pod: jobName={}, 找到 {} 个 Pod", jobName, podList.getItems().size());
                } catch (ApiException e2) {
                    log.warn("使用 app 标签查找 Pod 也失败: {}", e2.getMessage());
                }
            }

            // 删除找到的 Pod
            if (podList != null && podList.getItems() != null) {
                for (V1Pod pod : podList.getItems()) {
                    try {
                        String podName = pod.getMetadata().getName();
                        coreApi.deleteNamespacedPod(
                                podName,
                                namespace,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null
                        );
                        deletedPodCount++;
                        log.info("成功删除 Pod: {}", podName);
                    } catch (ApiException e) {
                        // 如果 Pod 已经被删除（404），忽略错误
                        if (e.getCode() == 404) {
                            log.debug("Pod 已被删除或不存在: {}", pod.getMetadata().getName());
                        } else {
                            log.warn("删除 Pod 失败: {}, 错误: {}", pod.getMetadata().getName(), e.getMessage());
                        }
                    }
                }
            }

            // 删除 Job（使用 PropagationPolicy=Background 确保立即删除，不等待 Pod 清理完成）
            try {
                batchApi.deleteNamespacedJob(
                        jobName,
                        namespace,
                        null,
                        null,
                        null,
                        null,
                        "Background",  // 立即删除，不等待依赖对象清理
                        null
                );
                log.info("成功删除 Job: {}, 已删除 {} 个 Pod", jobName, deletedPodCount);
            } catch (ApiException e) {
                // 如果 Job 已经被删除（404），忽略错误
                if (e.getCode() == 404) {
                    log.debug("Job 已被删除或不存在: {}", jobName);
                } else {
                    throw e;  // 其他错误继续抛出
                }
            }

            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("message", "Job deleted");
            result.put("jobName", jobName);
            result.put("deletedPodCount", deletedPodCount);
            return result;
        } catch (ApiException e) {
            log.error("删除 Job 失败: {}", jobName, e);
            JSONObject result = new JSONObject();
            result.put("status", "error");
            result.put("message", "删除 Job 失败: " + e.getMessage());
            result.put("jobName", jobName);
            result.put("deletedPodCount", deletedPodCount);
            return result;
        }
    }

    /**
     * 获取 Job 对应 Pod 的日志。
     */
    public JSONObject tailJobLogs(String jobName, int lines) {
        ensureClient();
        try {
            V1Pod pod = firstPod(jobName);
            String podName = pod.getMetadata().getName();

            // 获取日志
            String logText = coreApi.readNamespacedPodLog(
                    podName,
                    namespace,
                    null,
                    false,
                    false,
                    null,
                    null,
                    false,
                    null,
                    lines,
                    false
            );

            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("message", "log fetched");
            result.put("jobName", jobName);
            result.put("lines", lines);
            result.put("output", logText);
            return result;
        } catch (ApiException e) {
            log.error("获取日志失败 job={}, code={}, body={}", jobName, e.getCode(), e.getResponseBody(), e);
            JSONObject result = new JSONObject();
            result.put("status", "error");
            result.put("message", "获取日志失败: " + e.getMessage());
            result.put("jobName", jobName);
            return result;
        }
    }

    /**
     * 获取 Job / Pod 状态。
     */
    public JSONObject getJobStatus(String jobName) {
        ensureClient();
        JSONObject result = new JSONObject();
        result.put("jobName", jobName);
        result.put("namespace", namespace);

        try {
            V1Job job = batchApi.readNamespacedJob(jobName, namespace, null, null, null);
            if (job == null) {
                result.put("status", "not_found");
                return result;
            }

            V1JobStatus jobStatus = job.getStatus();
            String phase = "Unknown";
            String podPhase = "Unknown";
            String podName = null;

            if (jobStatus != null) {
                List<V1JobCondition> conditions = jobStatus.getConditions();
                if (conditions != null && !conditions.isEmpty()) {
                    Optional<V1JobCondition> completed = conditions.stream()
                            .filter(c -> "True".equalsIgnoreCase(c.getStatus()))
                            .findFirst();
                    if (completed.isPresent()) {
                        phase = completed.get().getType();
                    }
                }
            }

            // 获取 Pod 状态信息
            try {
                V1Pod pod = firstPod(jobName);
                if (pod != null) {
                    podName = pod.getMetadata().getName();
                    V1PodStatus podStatus = pod.getStatus();
                    if (podStatus != null) {
                        podPhase = podStatus.getPhase();

                        // 获取容器状态
                        List<V1ContainerStatus> containerStatuses = podStatus.getContainerStatuses();
                        if (containerStatuses != null && !containerStatuses.isEmpty()) {
                            V1ContainerStatus containerStatus = containerStatuses.get(0);
                            if (containerStatus.getState() != null) {
                                if (containerStatus.getState().getRunning() != null) {
                                    result.put("containerState", "Running");
                                    result.put("containerStartedAt", containerStatus.getState().getRunning().getStartedAt());
                                } else if (containerStatus.getState().getTerminated() != null) {
                                    result.put("containerState", "Terminated");
                                    result.put("containerExitCode", containerStatus.getState().getTerminated().getExitCode());
                                    result.put("containerFinishedAt", containerStatus.getState().getTerminated().getFinishedAt());
                                    result.put("containerReason", containerStatus.getState().getTerminated().getReason());
                                } else if (containerStatus.getState().getWaiting() != null) {
                                    result.put("containerState", "Waiting");
                                    result.put("containerWaitingReason", containerStatus.getState().getWaiting().getReason());
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("获取 Pod 状态失败（可能 Pod 还未创建）: {}", e.getMessage());
            }

            result.put("status", "success");
            result.put("jobPhase", phase);
            result.put("podPhase", podPhase);
            result.put("podName", podName);
            result.put("succeeded", jobStatus != null ? jobStatus.getSucceeded() : null);
            result.put("failed", jobStatus != null ? jobStatus.getFailed() : null);
            result.put("active", jobStatus != null ? jobStatus.getActive() : null);
            return result;
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                result.put("status", "容器未找到");
                return result;
            }
            log.error("获取 Job 状态失败: {}", jobName, e);
            result.put("status", "error");
            result.put("message", "获取 Job 状态失败: " + e.getMessage());
            return result;
        }
    }

    /**
     * 读取实时日志（follow）。
     */
    public void streamJobLogs(String jobName, java.util.function.Consumer<String> consumer) {
        ensureClient();
        try {
            V1Pod pod = firstPod(jobName);
            String podName = pod.getMetadata().getName();

            // 使用 watch 方式获取日志流
            InputStream logStream = null;
//                    coreApi.readNamespacedPodLog(
//                    podName,
//                    namespace,
//                    null,
//                    false,
//                    false,
//                    null,
//                    null,
//                    true,  // follow
//                    null,
//                    null,
//                    false
//            );

            try (java.util.Scanner scanner = new java.util.Scanner(logStream)) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    consumer.accept(line);
                }
            }
        } catch (Exception e) {
            log.error("读取日志失败 job={}", jobName, e);
        }
    }

    /**
     * 提交容器为镜像（不支持）。
     */
    public String commitContainerAsImage(String containerId, String imageName, String imageTag) {
        JSONObject errorResult = new JSONObject();
        errorResult.put("status", "error");
        errorResult.put("message", "Kubernetes模式下不支持提交容器为镜像，请使用CI/CD流程构建镜像");
        return errorResult.toString();
    }

    public static boolean isSuccess(String resultJson) {
        try {
            JSONObject json = JSONUtil.parseObj(resultJson);
            return "success".equals(json.getStr("status"));
        } catch (Exception e) {
            return false;
        }
    }

    public static String generateTaskId() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String timestamp = sdf.format(new Date());
        String uuidPart = UUID.randomUUID().toString().substring(0, 8);
        return "task_" + timestamp + "_" + uuidPart;
    }

    public static String generateTrackId() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String timestamp = sdf.format(new Date());
        String uuidPart = UUID.randomUUID().toString().substring(0, 8);
        return "track_" + timestamp + "_" + uuidPart;
    }

    public static String generateContainerName(String prefix) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String timestamp = sdf.format(new Date());
        String uuidPart = UUID.randomUUID().toString().substring(0, 8);
        return prefix + "_" + timestamp + "_" + uuidPart;
    }

    /**
     * 兼容旧接口：设置远程服务器（K8s 模式下无效，仅保留以避免编译错误）
     */
    public void setRemoteServer(String host, Integer port, String user, String password) {
        // no-op
    }

    /**
     * 获取 Kubernetes 客户端
     */
    public ApiClient getClient() {
        ensureClient();
        return apiClient;
    }

    /**
     * Tail 日志（兼容旧 getContainerLogs）
     */
    public String getContainerLogs(String jobName, int lines) {
        return tailJobLogs(jobName, lines).toString();
    }

    /**
     * 占位：K8s 模式下不支持直接上传/下载文件
     */
    public String uploadToContainer(String containerId, String localPath, String containerPath) {
        JSONObject res = new JSONObject();
        res.put("status", "error");
        res.put("message", "K8s 模式不支持直接上传文件，请使用挂载卷或对象存储");
        return res.toString();
    }

    public String downloadFromContainer(String containerId, String containerPath, String localPath) {
        JSONObject res = new JSONObject();
        res.put("status", "error");
        res.put("message", "K8s 模式不支持直接下载文件，请使用挂载卷或对象存储");
        return res.toString();
    }

    /**
     * 列出当前命名空间中所有「训练」相关的 Job（训练容器）。
     * 约定：所有训练 Job 的名称中包含 "-train-" 或 "_train_" 前缀片段，
     * 例如：yolo-train_20250101_xxxx、deeplab-train-20250101_xxxx、tracknetv3_train_20250101_xxxx。
     */
    public String listTrainingJobs() {
        ensureClient();
        try {
            V1JobList jobList = batchApi.listNamespacedJob(
                    namespace,
                    null,   // pretty
                    null,   // _continue
                    null,   // fieldSelector
                    null,   // labelSelector
                    null,   // resourceVersion
                    null,   // timeoutSeconds
                    null,   // limit
                    null,   // allowWatchBookmarks
                    null,   // _progressListener
                    false   // _async
            );

            List<JSONObject> jobs = new ArrayList<>();
            if (jobList != null && jobList.getItems() != null) {
                for (V1Job job : jobList.getItems()) {
                    V1ObjectMeta meta = job.getMetadata();
                    String name = (meta != null) ? meta.getName() : null;
                    if (name == null) {
                        continue;
                    }
                    // 只保留训练相关的 Job
                    if (!(name.contains("-train-") || name.contains("_train_"))) {
                        continue;
                    }

                    JSONObject item = new JSONObject();
                    item.put("jobName", name);
                    item.put("namespace", namespace);

                    if (meta != null) {
                        item.put("creationTimestamp", meta.getCreationTimestamp());
                        item.put("labels", meta.getLabels());
                    }

                    V1JobStatus status = job.getStatus();
                    if (status != null) {
                        item.put("startTime", status.getStartTime());
                        item.put("completionTime", status.getCompletionTime());
                        item.put("succeeded", status.getSucceeded());
                        item.put("failed", status.getFailed());
                        item.put("active", status.getActive());
                    }

                    // 简单推断模型类型（按前缀拆分）
                    String modelType = null;
                    int idxDash = name.indexOf("-train-");
                    int idxUnderscore = name.indexOf("_train_");
                    int idx = -1;
                    if (idxDash >= 0) {
                        idx = idxDash;
                    } else if (idxUnderscore >= 0) {
                        idx = idxUnderscore;
                    }
                    if (idx > 0) {
                        modelType = name.substring(0, idx);
                    }
                    if (modelType != null) {
                        item.put("modelType", modelType);
                    }

                    jobs.add(item);
                }
            }

            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("namespace", namespace);
            result.put("total", jobs.size());
            result.put("jobs", jobs);
            return result.toString();

        } catch (Exception e) {
            log.error("列出训练 Job 失败", e);
            JSONObject error = new JSONObject();
            error.put("status", "error");
            error.put("message", "列出训练容器失败");
            error.put("error", e.getMessage());
            return error.toString();
        }
    }

    protected void ensureClient() {
        if (apiClient == null) {
            initK8sClient();
        }
    }

    private V1Pod firstPod(String jobName) {
        try {
            V1PodList podList = coreApi.listNamespacedPod(
                    namespace,
                    null,
                    null,
                    null,
                    null,
                    "app=" + jobName,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            if (podList.getItems().isEmpty()) {
                throw new IllegalStateException("未找到 Pod, job=" + jobName);
            }

            // 优先 Running，否则返回第一个
            return podList.getItems().stream()
                    .sorted((a, b) -> {
                        V1PodStatus sa = a.getStatus();
                        V1PodStatus sb = b.getStatus();
                        boolean ar = sa != null && "Running".equalsIgnoreCase(sa.getPhase());
                        boolean br = sb != null && "Running".equalsIgnoreCase(sb.getPhase());
                        return Boolean.compare(br, ar);
                    })
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("未找到 Pod, job=" + jobName));
        } catch (ApiException e) {
            throw new IllegalStateException("查询 Pod 失败, job=" + jobName, e);
        }
    }

    private String[] parseMount(String mount) {
        String host = "/data";
        String container = "/app/data";
        if (mount != null && mount.contains(":")) {
            String[] parts = mount.split(":");
            host = parts[0];
            if (parts.length > 1) {
                container = parts[1];
            }
        }
        return new String[]{host, container};
    }

    /**
     * 根据 jobName 推断容器名称
     * @param jobName Job 名称，例如：yolo-train-xxx, deeplab-train-xxx, tracknet-train-xxx
     * @return 容器名称，例如：yolov8-trainer, deeplab-trainer, tracknet-trainer
     */
    private String inferContainerName(String jobName) {
        if (jobName == null || jobName.isEmpty()) {
            return "trainer";
        }

        String lowerJobName = jobName.toLowerCase();

        // 根据 jobName 中的模型类型推断容器名称
        if (lowerJobName.contains("yolo")) {
            return "yolov8-trainer";
        } else if (lowerJobName.contains("deeplab")) {
            return "deeplab-trainer";
        } else if (lowerJobName.contains("tracknet")) {
            return "tracknet-trainer";
        } else if (lowerJobName.contains("train")) {
            // 默认训练容器名称
            return "trainer";
        }

        // 如果无法推断，返回默认值
        return "trainer";
    }

    /**
     * 根据配置 JSON 构建容器启动命令
     * 从配置中解析训练类型和模型名称，生成相应的启动命令
     *
     * @param configJson 配置 JSON 字符串
     * @return 命令数组，[0] 为 command，[1..n] 为 args
     */
    protected String[] buildContainerCommand(String configJson) {
        try {
            JSONObject config = JSONUtil.parseObj(configJson);
            String trainType = config.getStr("the_train_type", "train");
            String modelName = config.getStr("model_name", "").toLowerCase();

            // 根据训练类型和模型名称构建命令
            String scriptPath = determineScriptPath(trainType, modelName);

            // 使用 shell 形式执行命令，这样可以读取环境变量 $CONFIG
            // command: ["sh", "-c"]
            // args: ["python /app/train.py --config $CONFIG"]
            String command = "sh";
            String args = String.format("python %s --config $CONFIG", scriptPath);

            log.debug("构建容器命令: trainType={}, modelName={}, scriptPath={}", trainType, modelName, scriptPath);

            return new String[]{command, "-c", args};
        } catch (Exception e) {
            log.warn("解析配置失败，使用默认命令: {}", e.getMessage());
            // 默认使用通用训练脚本
            return new String[]{"sh", "-c", "python /app/train.py --config $CONFIG"};
        }
    }

    /**
     * 根据训练类型和模型名称确定脚本路径
     *
     * @param trainType 训练类型：train, valuate, predict, export
     * @param modelName 模型名称
     * @return 脚本路径
     */
    private String determineScriptPath(String trainType, String modelName) {
        // 根据模型名称选择特定脚本
        if (modelName.contains("deeplab") || modelName.contains("deeplabv3")) {
            return "/app/train_deeplab.py";
        } else if (modelName.contains("tracknet") || modelName.contains("tracknetv3")) {
            return "/app/train_tracknet.py";
        } else if (modelName.contains("yolo") || modelName.contains("yolov8") || modelName.contains("yolov11")) {
            return "/app/train.py";
        }

        // 根据训练类型选择通用脚本
        switch (trainType.toLowerCase()) {
            case "train":
            case "valuate":
            case "predict":
            case "export":
                // 默认使用通用训练脚本，脚本内部会根据 the_train_type 参数区分
                return "/app/train.py";
            default:
                return "/app/train.py";
        }
    }

    /**
     * 查询指定Job的资源利用率
     *
     * @param jobName Job名称
     * @param namespace 命名空间，如果为null则使用default
     * @return JSON格式的资源利用率数据（格式与目标格式一致）
     */
    public String getJobResourceUsage(String jobName, String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            namespace = "default";
        }

        JSONObject result = new JSONObject();
        result.put("containerId", jobName);
        result.put("taskId", jobName);
        result.put("timestamp", System.currentTimeMillis());

        try {
            // 1. 获取Job信息
            V1Job job = batchApi.readNamespacedJob(jobName, namespace, null, null, null);
            if (job == null) {
                result.put("status", "error");
                result.put("message", "Job不存在: " + jobName);
                return result.toString();
            }

            // 2. 获取Job关联的Pods（通常Job只有一个Pod）
            String labelSelector = "job-name=" + jobName;
            V1PodList podList = coreApi.listNamespacedPod(
                    namespace, null, null, null, null, labelSelector,
                    null, null, null, null, null
            );

            if (podList.getItems().isEmpty()) {
                result.put("status", "error");
                result.put("message", "Job没有关联的Pod");
                return result.toString();
            }

            // 3. 获取第一个运行中的Pod的资源使用情况
            V1Pod targetPod = null;
            for (V1Pod pod : podList.getItems()) {
                if (pod.getStatus() != null && "Running".equals(pod.getStatus().getPhase())) {
                    targetPod = pod;
                    break;
                }
            }

            // 如果没有运行中的Pod，使用第一个Pod
            if (targetPod == null) {
                targetPod = podList.getItems().get(0);
            }

            // 4. 获取Pod的实际资源使用情况
            JSONObject resourceUsage = getPodActualResourceUsage(targetPod, namespace);

            // 合并到结果中
            result.put("cpu", resourceUsage.getJSONObject("cpu"));
            result.put("memory", resourceUsage.getJSONObject("memory"));
            result.put("gpu", resourceUsage.getJSONObject("gpu"));
            result.put("gpuMemory", resourceUsage.getJSONObject("gpuMemory"));
            result.put("status", "success");

        } catch (ApiException e) {
            result.put("status", "error");
            result.put("message", "API调用失败: " + e.getMessage());
            result.put("code", e.getCode());
            e.printStackTrace();
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "查询失败: " + e.getMessage());
            e.printStackTrace();
        }

        return result.toString();
    }

    /**
     * 获取Pod的实际资源使用情况（符合目标JSON格式）
     */
    private JSONObject getPodActualResourceUsage(V1Pod pod, String namespace) {
        JSONObject result = new JSONObject();

        try {
            V1PodSpec spec = pod.getSpec();
            V1PodStatus status = pod.getStatus();
            String podName = pod.getMetadata().getName();

            // 获取资源请求和限制
            Map<String, Quantity> requests = new HashMap<>();
            Map<String, Quantity> limits = new HashMap<>();

            if (spec != null && spec.getContainers() != null) {
                for (V1Container container : spec.getContainers()) {
                    V1ResourceRequirements resources = container.getResources();
                    if (resources != null) {
                        if (resources.getRequests() != null) {
                            requests.putAll(resources.getRequests());
                        }
                        if (resources.getLimits() != null) {
                            limits.putAll(resources.getLimits());
                        }
                    }
                }
            }

            // 1. 获取CPU使用情况
            JSONObject cpu = getCpuUsage(podName, namespace, requests, limits);
            result.put("cpu", cpu);

            // 2. 获取内存使用情况
            JSONObject memory = getMemoryUsage(podName, namespace, requests, limits);
            result.put("memory", memory);

            // 3. 获取GPU使用情况
            JSONObject gpu = getGpuUsage(podName, namespace, spec);
            result.put("gpu", gpu);

            // 4. 获取GPU显存使用情况
            JSONObject gpuMemory = getGpuMemoryUsage(podName, namespace, spec);
            result.put("gpuMemory", gpuMemory);

        } catch (Exception e) {
            e.printStackTrace();
            // 返回默认值
            result.put("cpu", createDefaultCpu());
            result.put("memory", createDefaultMemory());
            result.put("gpu", createDefaultGpu());
            result.put("gpuMemory", createDefaultGpuMemory());
        }

        return result;
    }

    /**
     * 获取CPU使用情况
     */
    private JSONObject getCpuUsage(String podName, String namespace,
                                   Map<String, Quantity> requests, Map<String, Quantity> limits) {
        JSONObject cpu = new JSONObject();

        try {
            // 尝试通过Metrics API获取实际CPU使用量
            double cpuUsageValue = 0.0;
            double cpuLimitValue = 0.0;

            Quantity cpuLimit = limits.get("cpu");
            if (cpuLimit != null) {
                cpuLimitValue = parseQuantity(cpuLimit);
            }

            // 尝试从Metrics API获取（需要metrics-server）
            try {
                cpuUsageValue = getCpuUsageFromMetrics(podName, namespace);
            } catch (Exception e) {
                // Metrics API不可用，继续使用其他方式
            }

            // 如果无法获取实际使用量，使用请求值作为参考
            if (cpuUsageValue == 0.0) {
                Quantity cpuRequest = requests.get("cpu");
                if (cpuRequest != null) {
                    cpuUsageValue = parseQuantity(cpuRequest);
                }
            }

            // 计算百分比（相对于limit，如果没有limit则相对于request）
            double percent = 0.0;
            if (cpuLimitValue > 0) {
                percent = (cpuUsageValue / cpuLimitValue) * 100.0;
            } else {
                Quantity cpuRequest = requests.get("cpu");
                if (cpuRequest != null) {
                    double cpuRequestValue = parseQuantity(cpuRequest);
                    if (cpuRequestValue > 0) {
                        percent = (cpuUsageValue / cpuRequestValue) * 100.0;
                    }
                }
            }

            // 格式化CPU使用率，保留两位小数（如 "198.07%"）
            // 确保percent保留两位小数
            double roundedPercent = Math.round(percent * 100.0) / 100.0;
            cpu.put("usage", String.format("%.2f%%", roundedPercent));
            cpu.put("percent", roundedPercent);

        } catch (Exception e) {
            cpu.put("usage", "0%");
            cpu.put("percent", 0.0);
        }

        return cpu;
    }

    /**
     * 获取内存使用情况
     */
    private JSONObject getMemoryUsage(String podName, String namespace,
                                      Map<String, Quantity> requests, Map<String, Quantity> limits) {
        JSONObject memory = new JSONObject();

        try {
            long memoryUsageBytes = 0L;
            long memoryLimitBytes = 0L;

            Quantity memoryLimit = limits.get("memory");
            if (memoryLimit != null) {
                memoryLimitBytes = parseMemoryBytes(memoryLimit);
            } else {
                // 如果没有limit，使用request作为参考
                Quantity memoryRequest = requests.get("memory");
                if (memoryRequest != null) {
                    memoryLimitBytes = parseMemoryBytes(memoryRequest);
                }
            }

            // 尝试从Metrics API获取实际内存使用量
            try {
                memoryUsageBytes = getMemoryUsageFromMetrics(podName, namespace);
            } catch (Exception e) {
                // Metrics API不可用，继续使用其他方式
            }

            // 如果无法获取实际使用量，使用请求值作为参考
            if (memoryUsageBytes == 0L) {
                Quantity memoryRequest = requests.get("memory");
                if (memoryRequest != null) {
                    memoryUsageBytes = parseMemoryBytes(memoryRequest);
                }
            }

            // 格式化内存值（使用GiB格式，保留3位小数，如 "6.691GiB"）
            String usedStr = formatMemoryBytesToGiB(memoryUsageBytes);
            String totalStr = formatMemoryBytesToGiB(memoryLimitBytes);

            // 计算百分比
            double percent = 0.0;
            if (memoryLimitBytes > 0) {
                percent = (memoryUsageBytes * 100.0) / memoryLimitBytes;
            }

            // 格式：usage是 "6.691GiB / 14.98GiB"，used和total是 "6.691GiB"
            memory.put("usage", usedStr + " / " + totalStr);
            memory.put("used", usedStr);
            memory.put("percent", Math.round(percent * 100.0) / 100.0);
            memory.put("total", totalStr);

        } catch (Exception e) {
            memory.put("usage", "0 / 0");
            memory.put("used", "0");
            memory.put("percent", 0.0);
            memory.put("total", "0");
        }

        return memory;
    }

    /**
     * 获取GPU使用情况
     */
    private JSONObject getGpuUsage(String podName, String namespace, V1PodSpec spec) {
        JSONObject gpu = new JSONObject();

        try {
            // 尝试在Pod中执行nvidia-smi命令获取GPU信息
            String containerName = null;
            if (spec != null && spec.getContainers() != null && !spec.getContainers().isEmpty()) {
                containerName = spec.getContainers().get(0).getName();
            }

            double gpuPercent = 0.0;
            double powerDraw = 0.0;
            int temperature = 0;

            // 尝试执行nvidia-smi命令
            try {
                // 执行 nvidia-smi --query-gpu=utilization.gpu,power.draw,temperature.gpu --format=csv,noheader,nounits
                String command = "nvidia-smi --query-gpu=utilization.gpu,power.draw,temperature.gpu --format=csv,noheader,nounits";
                String output = execCommandInPod(podName, namespace, containerName, command);

                if (output != null && !output.trim().isEmpty()) {
                    String[] parts = output.trim().split(",");
                    if (parts.length >= 3) {
                        gpuPercent = Double.parseDouble(parts[0].trim());
                        powerDraw = Double.parseDouble(parts[1].trim());
                        temperature = Integer.parseInt(parts[2].trim());
                    }
                }
            } catch (Exception e) {
                // 如果无法执行命令，使用默认值
            }

            gpu.put("usage", String.format("%.1f%%", gpuPercent));
            gpu.put("percent", (int) gpuPercent);
            gpu.put("powerDraw", Math.round(powerDraw * 100.0) / 100.0);
            gpu.put("temperature", temperature);

        } catch (Exception e) {
            gpu.put("usage", "0.0%");
            gpu.put("percent", 0);
            gpu.put("powerDraw", 0.0);
            gpu.put("temperature", 0);
        }

        return gpu;
    }

    /**
     * 获取GPU显存使用情况
     */
    private JSONObject getGpuMemoryUsage(String podName, String namespace, V1PodSpec spec) {
        JSONObject gpuMemory = new JSONObject();

        try {
            String containerName = null;
            if (spec != null && spec.getContainers() != null && !spec.getContainers().isEmpty()) {
                containerName = spec.getContainers().get(0).getName();
            }

            long usedMB = 0L;
            long totalMB = 0L;

            // 尝试执行nvidia-smi命令获取GPU显存信息
            try {
                // 执行 nvidia-smi --query-gpu=memory.used,memory.total --format=csv,noheader,nounits
                String command = "nvidia-smi --query-gpu=memory.used,memory.total --format=csv,noheader,nounits";
                String output = execCommandInPod(podName, namespace, containerName, command);

                if (output != null && !output.trim().isEmpty()) {
                    String[] parts = output.trim().split(",");
                    if (parts.length >= 2) {
                        usedMB = Long.parseLong(parts[0].trim());
                        totalMB = Long.parseLong(parts[1].trim());
                    }
                }
            } catch (Exception e) {
                // 如果无法执行命令，使用默认值
            }

            // 计算百分比
            double percent = 0.0;
            if (totalMB > 0) {
                percent = (usedMB * 100.0) / totalMB;
            }

            // 格式化显存字符串
            // usage格式：带小数点（如 "3677.0 MiB / 16376.0 MiB"）
            // used和total格式：不带小数点，整数（如 "3677 MiB"）
            String usedStrForUsage = String.format("%.1f MiB", (double) usedMB);
            String totalStrForUsage = String.format("%.1f MiB", (double) totalMB);
            String usedStr = usedMB + " MiB";
            String totalStr = totalMB + " MiB";

            // 确保百分比保留两位小数
            double roundedPercent = Math.round(percent * 100.0) / 100.0;

            gpuMemory.put("usage", usedStrForUsage + " / " + totalStrForUsage);
            gpuMemory.put("used", usedStr);
            gpuMemory.put("percent", roundedPercent);
            gpuMemory.put("total", totalStr);
            gpuMemory.put("totalMB", totalMB);
            gpuMemory.put("usedMB", usedMB);

        } catch (Exception e) {
            gpuMemory.put("usage", "0 MiB / 0 MiB");
            gpuMemory.put("used", "0 MiB");
            gpuMemory.put("percent", 0.0);
            gpuMemory.put("total", "0 MiB");
            gpuMemory.put("totalMB", 0);
            gpuMemory.put("usedMB", 0);
        }

        return gpuMemory;
    }

    /**
     * 在Pod中执行命令
     */
    private String execCommandInPod(String podName, String namespace, String containerName, String command) {
        try {
            // 构建kubectl命令
            List<String> cmdList = new ArrayList<>();
            cmdList.add("kubectl");
            cmdList.add("exec");
            cmdList.add("-n");
            cmdList.add(namespace);
            cmdList.add(podName);
            if (containerName != null && !containerName.isEmpty()) {
                cmdList.add("-c");
                cmdList.add(containerName);
            }
            cmdList.add("--");
            cmdList.add("sh");
            cmdList.add("-c");
            cmdList.add(command);

            ProcessBuilder pb = new ProcessBuilder(cmdList);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)
            );

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                // 命令执行失败
                return null;
            }

            return output.toString().trim();

        } catch (Exception e) {
            // 如果kubectl不可用或执行失败，返回null
            // 实际环境中可能需要配置kubectl路径或使用其他方式
            return null;
        }
    }

    /**
     * 从Metrics API获取CPU使用量（cores）
     */
    private double getCpuUsageFromMetrics(String podName, String namespace) {
        try {
            // 使用Kubernetes Java Client调用Metrics API
            // 注意：需要集群中部署了metrics-server
            String basePath = null ;
            //client.getBasePath();
            String url = basePath + "/apis/metrics.k8s.io/v1beta1/namespaces/" + namespace + "/pods/" + podName;

            // 使用ApiClient的HTTP客户端构建请求
            okhttp3.OkHttpClient httpClient = null;
            //client.getHttpClient();
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Accept", "application/json")
                    .build();

            okhttp3.Response response = httpClient.newCall(request).execute();
            try {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();

                    // 解析JSON响应
                    JSONObject metrics = cn.hutool.json.JSONUtil.parseObj(responseBody);
                    if (metrics != null) {
                        cn.hutool.json.JSONArray containers = metrics.getJSONArray("containers");
                        if (containers != null && containers.size() > 0) {
                            JSONObject container = containers.getJSONObject(0);
                            JSONObject usage = container.getJSONObject("usage");
                            if (usage != null) {
                                String cpuStr = usage.getStr("cpu");
                                if (cpuStr != null) {
                                    // 解析CPU字符串（如 "100m" 或 "1"）
                                    return parseQuantity(new Quantity(cpuStr));
                                }
                            }
                        }
                    }
                }
            } finally {
                if (response != null) {
                    response.close();
                }
            }
        } catch (Exception e) {
            // Metrics API不可用或调用失败
        }
        return 0.0;
    }

    /**
     * 从Metrics API获取内存使用量（bytes）
     */
    private long getMemoryUsageFromMetrics(String podName, String namespace) {
        try {
            // 使用Kubernetes Java Client调用Metrics API
            String basePath = null;
            //client.getBasePath();
            String url = basePath + "/apis/metrics.k8s.io/v1beta1/namespaces/" + namespace + "/pods/" + podName;

            // 使用ApiClient的HTTP客户端构建请求
            okhttp3.OkHttpClient httpClient = null;
            //client.getHttpClient();
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Accept", "application/json")
                    .build();

            okhttp3.Response response = httpClient.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();

                // 解析JSON响应
                JSONObject metrics = cn.hutool.json.JSONUtil.parseObj(responseBody);
                if (metrics != null) {
                    cn.hutool.json.JSONArray containers = metrics.getJSONArray("containers");
                    if (containers != null && containers.size() > 0) {
                        JSONObject container = containers.getJSONObject(0);
                        JSONObject usage = container.getJSONObject("usage");
                        if (usage != null) {
                            String memoryStr = usage.getStr("memory");
                            if (memoryStr != null) {
                                // 解析内存字符串（如 "512Mi" 或 "1Gi"）
                                return parseMemoryBytes(new Quantity(memoryStr));
                            }
                        }
                    }
                }
            }
            if (response != null) {
                response.close();
            }
        } catch (Exception e) {
            // Metrics API不可用或调用失败
        }
        return 0L;
    }

    /**
     * 格式化内存字节数为可读格式（GiB/MiB）
     */
    private String formatMemoryBytes(long bytes) {
        if (bytes < 1024 * 1024) {
            return String.format("%.2f MiB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.3f GiB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * 格式化内存字节数为GiB格式（用于内存显示，保留3位小数）
     */
    private String formatMemoryBytesToGiB(long bytes) {
        double gib = bytes / (1024.0 * 1024.0 * 1024.0);
        return String.format("%.3fGiB", gib);
    }

    /**
     * 创建默认CPU对象
     */
    private JSONObject createDefaultCpu() {
        JSONObject cpu = new JSONObject();
        cpu.put("usage", "0%");
        cpu.put("percent", 0.0);
        return cpu;
    }

    /**
     * 创建默认内存对象
     */
    private JSONObject createDefaultMemory() {
        JSONObject memory = new JSONObject();
        memory.put("usage", "0 / 0");
        memory.put("used", "0");
        memory.put("percent", 0.0);
        memory.put("total", "0");
        return memory;
    }

    /**
     * 创建默认GPU对象
     */
    private JSONObject createDefaultGpu() {
        JSONObject gpu = new JSONObject();
        gpu.put("usage", "0.0%");
        gpu.put("percent", 0);
        gpu.put("powerDraw", 0.0);
        gpu.put("temperature", 0);
        return gpu;
    }

    /**
     * 创建默认GPU显存对象
     */
    private JSONObject createDefaultGpuMemory() {
        JSONObject gpuMemory = new JSONObject();
        gpuMemory.put("usage", "0 MiB / 0 MiB");
        gpuMemory.put("used", "0 MiB");
        gpuMemory.put("percent", 0.0);
        gpuMemory.put("total", "0 MiB");
        gpuMemory.put("totalMB", 0);
        gpuMemory.put("usedMB", 0);
        return gpuMemory;
    }

    /**
     * 获取Pod的资源使用情况（旧方法，保留用于兼容）
     */
    private JSONObject getPodResourceUsage(V1Pod pod, String namespace) {
        JSONObject result = new JSONObject();
        result.put("podName", pod.getMetadata().getName());
        result.put("namespace", namespace);

        try {
            V1PodSpec spec = pod.getSpec();
            V1PodStatus status = pod.getStatus();

            // 获取资源请求和限制
            Map<String, Quantity> requests = new HashMap<>();
            Map<String, Quantity> limits = new HashMap<>();

            if (spec != null && spec.getContainers() != null) {
                for (V1Container container : spec.getContainers()) {
                    V1ResourceRequirements resources = container.getResources();
                    if (resources != null) {
                        if (resources.getRequests() != null) {
                            requests.putAll(resources.getRequests());
                        }
                        if (resources.getLimits() != null) {
                            limits.putAll(resources.getLimits());
                        }
                    }
                }
            }

            // CPU信息
            JSONObject cpu = new JSONObject();
            Quantity cpuRequest = requests.get("cpu");
            Quantity cpuLimit = limits.get("cpu");
            if (cpuRequest != null) {
                cpu.put("request", cpuRequest.toSuffixedString());
                cpu.put("requestValue", parseQuantity(cpuRequest));
            } else {
                cpu.put("request", "0");
                cpu.put("requestValue", 0.0);
            }
            if (cpuLimit != null) {
                cpu.put("limit", cpuLimit.toSuffixedString());
                cpu.put("limitValue", parseQuantity(cpuLimit));
            } else {
                cpu.put("limit", "N/A");
                cpu.put("limitValue", 0.0);
            }

            // 注意：实际CPU使用量需要通过Metrics API获取
            // 需要集群中部署了metrics-server，并且有相应的权限
            cpu.put("usage", "N/A");
            cpu.put("usageValue", 0.0);
            cpu.put("usagePercent", 0.0);
            cpu.put("note", "实际使用量需要Metrics API支持");

            result.put("cpu", cpu);

            // 内存信息
            JSONObject memory = new JSONObject();
            Quantity memoryRequest = requests.get("memory");
            Quantity memoryLimit = limits.get("memory");
            if (memoryRequest != null) {
                memory.put("request", memoryRequest.toSuffixedString());
                memory.put("requestValue", parseMemoryBytes(memoryRequest));
            } else {
                memory.put("request", "0");
                memory.put("requestValue", 0L);
            }
            if (memoryLimit != null) {
                memory.put("limit", memoryLimit.toSuffixedString());
                memory.put("limitValue", parseMemoryBytes(memoryLimit));
            } else {
                memory.put("limit", "N/A");
                memory.put("limitValue", 0L);
            }

            // 注意：实际内存使用量需要通过Metrics API获取
            memory.put("usage", "N/A");
            memory.put("usageValue", 0L);
            memory.put("usagePercent", 0.0);
            memory.put("note", "实际使用量需要Metrics API支持");

            result.put("memory", memory);

            // GPU信息
            JSONObject gpu = new JSONObject();
            JSONObject gpuMemory = new JSONObject();

            // 检查是否有GPU资源请求
            Quantity gpuRequest = requests.get("nvidia.com/gpu");
            if (gpuRequest == null) {
                // 尝试其他常见的GPU资源名称
                for (String key : requests.keySet()) {
                    if (key.contains("gpu") || key.contains("GPU")) {
                        gpuRequest = requests.get(key);
                        break;
                    }
                }
            }

            if (gpuRequest != null) {
                gpu.put("request", gpuRequest.toSuffixedString());
                gpu.put("requestValue", parseQuantity(gpuRequest).intValue());
            } else {
                gpu.put("request", "0");
                gpu.put("requestValue", 0);
            }

            // GPU显存（通常通过limits或annotation获取）
            Quantity gpuMemoryLimit = limits.get("nvidia.com/gpu-memory");
            if (gpuMemoryLimit == null) {
                for (String key : limits.keySet()) {
                    if (key.contains("gpu") && key.contains("memory")) {
                        gpuMemoryLimit = limits.get(key);
                        break;
                    }
                }
            }

            if (gpuMemoryLimit != null) {
                gpuMemory.put("limit", gpuMemoryLimit.toSuffixedString());
                gpuMemory.put("limitValue", parseMemoryBytes(gpuMemoryLimit));
            } else {
                gpuMemory.put("limit", "N/A");
                gpuMemory.put("limitValue", 0L);
            }

            result.put("gpu", gpu);
            result.put("gpuMemory", gpuMemory);

            // Pod状态
            if (status != null) {
                result.put("phase", status.getPhase());
            }

        } catch (Exception e) {
            result.put("error", "获取Pod资源信息失败: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    /**
     * 累加资源使用量
     */
    private void accumulateResources(JSONObject total, JSONObject podResources) {
        // 累加CPU
        JSONObject totalCpu = total.getJSONObject("cpu");
        JSONObject podCpu = podResources.getJSONObject("cpu");
        if (podCpu != null && totalCpu != null) {
            Object cpuRequestObj = podCpu.get("requestValue");
            double cpuRequest = cpuRequestObj != null ? ((Number) cpuRequestObj).doubleValue() : 0.0;
            Object totalCpuRequestObj = totalCpu.get("requestValue");
            double totalCpuRequest = totalCpuRequestObj != null ? ((Number) totalCpuRequestObj).doubleValue() : 0.0;
            totalCpu.put("requestValue", totalCpuRequest + cpuRequest);
        }

        // 累加内存
        JSONObject totalMemory = total.getJSONObject("memory");
        JSONObject podMemory = podResources.getJSONObject("memory");
        if (podMemory != null && totalMemory != null) {
            Object memoryRequestObj = podMemory.get("requestValue");
            long memoryRequest = memoryRequestObj != null ? ((Number) memoryRequestObj).longValue() : 0L;
            Object totalMemoryRequestObj = totalMemory.get("requestValue");
            long totalMemoryRequest = totalMemoryRequestObj != null ? ((Number) totalMemoryRequestObj).longValue() : 0L;
            totalMemory.put("requestValue", totalMemoryRequest + memoryRequest);
        }

        // 累加GPU
        JSONObject totalGpu = total.getJSONObject("gpu");
        JSONObject podGpu = podResources.getJSONObject("gpu");
        if (podGpu != null && totalGpu != null) {
            Object gpuRequestObj = podGpu.get("requestValue");
            int gpuRequest = gpuRequestObj != null ? ((Number) gpuRequestObj).intValue() : 0;
            Object totalGpuRequestObj = totalGpu.get("requestValue");
            int totalGpuRequest = totalGpuRequestObj != null ? ((Number) totalGpuRequestObj).intValue() : 0;
            totalGpu.put("requestValue", totalGpuRequest + gpuRequest);
        }

        // 累加GPU显存
        JSONObject totalGpuMemory = total.getJSONObject("gpuMemory");
        JSONObject podGpuMemory = podResources.getJSONObject("gpuMemory");
        if (podGpuMemory != null && totalGpuMemory != null) {
            Object gpuMemoryLimitObj = podGpuMemory.get("limitValue");
            long gpuMemoryLimit = gpuMemoryLimitObj != null ? ((Number) gpuMemoryLimitObj).longValue() : 0L;
            Object totalGpuMemoryLimitObj = totalGpuMemory.get("limitValue");
            long totalGpuMemoryLimit = totalGpuMemoryLimitObj != null ? ((Number) totalGpuMemoryLimitObj).longValue() : 0L;
            totalGpuMemory.put("limitValue", totalGpuMemoryLimit + gpuMemoryLimit);
        }
    }

    /**
     * 解析Quantity为Double（用于CPU，单位：cores）
     */
    private Double parseQuantity(Quantity quantity) {
        if (quantity == null) {
            return 0.0;
        }
        try {
            return quantity.getNumber().doubleValue();
        } catch (Exception e) {
            String str = quantity.toSuffixedString();
            // 处理 "1000m" -> 1.0, "500m" -> 0.5
            if (str.endsWith("m")) {
                return Double.parseDouble(str.substring(0, str.length() - 1)) / 1000.0;
            }
            return Double.parseDouble(str);
        }
    }

    /**
     * 解析Quantity为Long（用于内存，单位：bytes）
     */
    private Long parseMemoryBytes(Quantity quantity) {
        if (quantity == null) {
            return 0L;
        }
        try {
            BigDecimal bytes = quantity.getNumber();
            // Quantity已经是bytes，直接转换
            return bytes.longValue();
        } catch (Exception e) {
            // 如果转换失败，尝试解析字符串
            String str = quantity.toSuffixedString();
            return parseMemoryString(str);
        }
    }

    /**
     * 解析内存字符串为bytes
     */
    private Long parseMemoryString(String memory) {
        if (memory == null || memory.isEmpty()) {
            return 0L;
        }
        memory = memory.trim().toUpperCase();
        try {
            double value = Double.parseDouble(memory.replaceAll("[^0-9.]", ""));
            if (memory.endsWith("KI")) {
                return (long) (value * 1024);
            } else if (memory.endsWith("MI")) {
                return (long) (value * 1024 * 1024);
            } else if (memory.endsWith("GI")) {
                return (long) (value * 1024 * 1024 * 1024);
            } else if (memory.endsWith("TI")) {
                return (long) (value * 1024L * 1024 * 1024 * 1024);
            } else if (memory.endsWith("K")) {
                return (long) (value * 1000);
            } else if (memory.endsWith("M")) {
                return (long) (value * 1000 * 1000);
            } else if (memory.endsWith("G")) {
                return (long) (value * 1000 * 1000 * 1000);
            } else if (memory.endsWith("T")) {
                return (long) (value * 1000L * 1000 * 1000 * 1000);
            } else {
                return (long) value;
            }
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * 格式化输出资源使用情况（便于查看）
     */
    public void printJobResourceUsage(String jobName, String namespace) {
        String result = getJobResourceUsage(jobName, namespace);
        JSONObject json = cn.hutool.json.JSONUtil.parseObj(result);

        System.out.println("========================================");
        System.out.println("Job资源利用率报告");
        System.out.println("========================================");
        System.out.println("Job名称: " + json.getStr("jobName"));
        System.out.println("命名空间: " + json.getStr("namespace"));
        System.out.println("Pod数量: " + json.getInt("podCount", 0));
        System.out.println("状态: " + json.getStr("status"));

        if ("success".equals(json.getStr("status"))) {
            JSONObject totalResources = json.getJSONObject("totalResources");
            if (totalResources != null) {
                System.out.println("\n--- 总资源使用情况 ---");

                JSONObject cpu = totalResources.getJSONObject("cpu");
                if (cpu != null) {
                    System.out.println("CPU请求: " + cpu.get("requestValue") + " cores");
                }

                JSONObject memory = totalResources.getJSONObject("memory");
                if (memory != null) {
                    long memBytes = ((Number) memory.get("requestValue")).longValue();
                    System.out.println("内存请求: " + formatBytes(memBytes));
                }

                JSONObject gpu = totalResources.getJSONObject("gpu");
                if (gpu != null) {
                    System.out.println("GPU请求: " + gpu.get("requestValue") + " GPUs");
                }

                JSONObject gpuMemory = totalResources.getJSONObject("gpuMemory");
                if (gpuMemory != null) {
                    long gpuMemBytes = ((Number) gpuMemory.get("limitValue")).longValue();
                    if (gpuMemBytes > 0) {
                        System.out.println("GPU显存限制: " + formatBytes(gpuMemBytes));
                    }
                }
            }
        } else {
            System.out.println("错误信息: " + json.getStr("message"));
        }

        System.out.println("========================================");
    }

    /**
     * 格式化字节数为可读格式
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}
