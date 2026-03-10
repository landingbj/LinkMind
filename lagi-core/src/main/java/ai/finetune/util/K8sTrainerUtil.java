package ai.finetune.util;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import io.kubernetes.client.util.Yaml;
import io.kubernetes.client.util.credentials.AccessTokenAuthentication;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.io.File;

/**
 * Kubernetes 训练工具类
 * 提供 Kubernetes Pod/Job 操作和管理的工具方法
 */
@Slf4j
public class K8sTrainerUtil {

//    /**
//     * 初始化 Kubernetes 客户端（基于 kubeconfig）
//     *
//     * @param kubeConfigPath kubeconfig 文件路径，如果为 null 则使用默认路径 ~/.kube/config
//     * @throws IOException IO 异常
//     */
//    public static void initK8sClient(String kubeConfigPath) throws IOException {
//        // 禁用 SSL 主机名验证（解决证书 SAN 不匹配问题）
//        disableSSLHostnameVerification();
//
//        // 1. 确定 kubeconfig 路径
//        String configPath;
//        if (kubeConfigPath != null && !kubeConfigPath.trim().isEmpty()) {
//            configPath = kubeConfigPath;
//        } else {
//            // 默认使用 ~/.kube/config
//            configPath = Paths.get(System.getProperty("user.home"), ".kube", "config").toString();
//        }
//
//        log.info("加载 kubeconfig 文件: {}", configPath);
//
//        // 2. 加载 kubeconfig 并构建 ApiClient
//        KubeConfig kubeConfig = KubeConfig.loadKubeConfig(new FileReader(configPath));
//        ApiClient apiClient = ClientBuilder.kubeconfig(kubeConfig).build();
//
//        // 3. 禁用 SSL 验证（解决证书验证问题）
//        apiClient.setVerifyingSsl(false);
//
//        // 4. 设置全局默认客户端（方便后续 API 调用）
//        io.kubernetes.client.openapi.Configuration.setDefaultApiClient(apiClient);
//
//        log.info("Kubernetes 客户端初始化成功（已禁用 SSL 主机名验证）");
//    }
    /**
     * 初始化 Kubernetes 客户端（基于 kubeconfig）
     *
     * @param kubeConfigPath kubeconfig 文件路径，如果为 null 则使用默认路径 ~/.kube/config
     * @throws IOException IO 异常
     */
    public static void initK8sClient(String kubeConfigPath) throws IOException {
        // 禁用 SSL 主机名验证（解决证书 SAN 不匹配问题）
        disableSSLHostnameVerification();

        ApiClient apiClient = null;
        // 1. 第一步：检测 Pod 内 SA 令牌文件是否存在
        String saTokenPath = "/var/run/secrets/kubernetes.io/serviceaccount/token";
        File saTokenFile = new File(saTokenPath);
        if (saTokenFile.exists()) {
            String apiServer = System.getenv("KUBERNETES_SERVICE_HOST");
            String apiPort = System.getenv("KUBERNETES_SERVICE_PORT");
            if (apiServer == null || apiPort == null) {
                throw new IOException("K8s 环境变量未找到");
            }

            String token = new String(Files.readAllBytes(Paths.get(saTokenPath)));
            String basePath = "https://" + apiServer + ":" + apiPort;

            apiClient = ClientBuilder.standard()
                    .setBasePath(basePath)
                    .setVerifyingSsl(false)
                    .setAuthentication(new AccessTokenAuthentication(token))
                    .build();

            Configuration.setDefaultApiClient(apiClient);
        } else {
            // 1. 确定 kubeconfig 路径
            String configPath;
            if (kubeConfigPath != null && !kubeConfigPath.trim().isEmpty()) {
                configPath = kubeConfigPath;
            } else {
                // 默认使用 ~/.kube/config
                configPath = Paths.get(System.getProperty("user.home"), ".kube", "config").toString();
            }

            log.info("加载 kubeconfig 文件: {}", configPath);

            // 2. 加载 kubeconfig 并构建 ApiClient
            KubeConfig kubeConfig = KubeConfig.loadKubeConfig(new FileReader(configPath));
            apiClient = ClientBuilder.kubeconfig(kubeConfig).build();
            // 3. 禁用 SSL 验证（解决证书验证问题）
            apiClient.setVerifyingSsl(false);
        }

        // 4. 设置全局默认客户端（方便后续 API 调用）
        Configuration.setDefaultApiClient(apiClient);
        log.info("Kubernetes 客户端初始化成功（已禁用 SSL 主机名验证）");
    }

    /**
     * 禁用 SSL 主机名验证
     * 用于解决 Kubernetes 证书的 SAN 不包含实际连接 IP 地址的问题
     */
    private static void disableSSLHostnameVerification() {
        try {
            // 设置系统属性禁用主机名验证（适用于 Java 11+）
            System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");

            // 创建一个信任所有证书的 TrustManager
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                            // 信任所有客户端证书
                        }
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                            // 信任所有服务器证书
                        }
                    }
            };

            // 创建一个接受所有主机名的 HostnameVerifier
            HostnameVerifier allHostsValid = new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };

            // 安装信任所有证书的 TrustManager
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            SSLContext.setDefault(sc);

            // 设置全局的 HostnameVerifier
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

            log.debug("已禁用 SSL 主机名验证");
        } catch (Exception e) {
            log.warn("禁用 SSL 主机名验证失败，可能会遇到证书验证问题: {}", e.getMessage());
        }
    }

    /**
     * 从 YAML 文件创建 Pod
     * @param yamlFilePath YAML 文件路径
     * @param namespace 命名空间（默认 default）
     * @return 创建的 Pod 对象
     * @throws IOException IO 异常
     * @throws ApiException Kubernetes API 异常
     */
    public static V1Pod createPodFromYaml(String yamlFilePath, String namespace) throws IOException, ApiException {
        log.info("从 YAML 文件创建 Pod: {}, namespace: {}", yamlFilePath, namespace);

        // 1. 加载 YAML 文件并解析为 V1Pod 对象
        V1Pod pod = Yaml.loadAs(new FileReader(yamlFilePath), V1Pod.class);

        // 2. 初始化 CoreV1Api（处理 Pod 等核心资源）
        CoreV1Api coreV1Api = new CoreV1Api();

        // 3. 创建 Pod（如果 Pod 已存在，会抛出 409 异常）
        try {
            V1Pod createdPod = coreV1Api.createNamespacedPod(
                    namespace,  // 命名空间
                    pod         // 解析后的 Pod 对象
            ).execute();
            log.info("Pod 创建成功，名称: {}", createdPod.getMetadata().getName());
            return createdPod;
        } catch (ApiException e) {
            if (e.getCode() == 409) {
                log.warn("Pod 已存在: {}", pod.getMetadata().getName());
                throw new RuntimeException("Pod 已存在: " + pod.getMetadata().getName(), e);
            } else {
                log.error("创建 Pod 失败: {}", e.getMessage());
                throw new RuntimeException("创建 Pod 失败: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 从 YAML 字符串创建 Pod
     * @param yamlContent YAML 内容
     * @param namespace 命名空间
     * @return 创建的 Pod 对象
     * @throws IOException IO 异常
     * @throws ApiException Kubernetes API 异常
     */
    public static V1Pod createPodFromYamlString(String yamlContent, String namespace) throws IOException, ApiException {
        log.info("从 YAML 字符串创建 Pod, namespace: {}", namespace);

        // 1. 从字符串加载 YAML 并解析为 V1Pod 对象
        V1Pod pod = Yaml.loadAs(new StringReader(yamlContent), V1Pod.class);

        // 2. 初始化 CoreV1Api
        CoreV1Api coreV1Api = new CoreV1Api();

        // 3. 创建 Pod
        try {
            V1Pod createdPod = coreV1Api.createNamespacedPod(
                    namespace,
                    pod
            ).execute();
            log.info("Pod 创建成功，名称: {}", createdPod.getMetadata().getName());
            return createdPod;
        } catch (ApiException e) {
            if (e.getCode() == 409) {
                log.warn("Pod 已存在: {}", pod.getMetadata().getName());
                throw new RuntimeException("Pod 已存在: " + pod.getMetadata().getName(), e);
            } else {
                log.error("创建 Pod 失败: {}", e.getMessage());
                throw new RuntimeException("创建 Pod 失败: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 从 YAML 文件创建 Job
     * @param yamlFilePath YAML 文件路径
     * @param namespace 命名空间（默认 default）
     * @return 创建的 Job 对象
     * @throws IOException IO 异常
     * @throws ApiException Kubernetes API 异常
     */
    public static V1Job createJobFromYaml(String yamlFilePath, String namespace) throws IOException, ApiException {
        log.info("从 YAML 文件创建 Job: {}, namespace: {}", yamlFilePath, namespace);

        // 1. 加载 YAML 文件并解析为 V1Job 对象
        V1Job job = Yaml.loadAs(new FileReader(yamlFilePath), V1Job.class);

        // 2. 初始化 BatchV1Api（处理 Job 等批处理资源）
        BatchV1Api batchV1Api = new BatchV1Api();

        // 3. 创建 Job
        try {
            V1Job createdJob = batchV1Api.createNamespacedJob(
                    namespace,  // 命名空间
                    job         // 解析后的 Job 对象
            ).execute();
            log.info("Job 创建成功，名称: {}", createdJob.getMetadata().getName());
            return createdJob;
        } catch (ApiException e) {
            if (e.getCode() == 409) {
                log.warn("Job 已存在: {}", job.getMetadata().getName());
                throw new RuntimeException("Job 已存在: " + job.getMetadata().getName(), e);
            } else {
                log.error("创建 Job 失败: {}", e.getMessage());
                throw new RuntimeException("创建 Job 失败: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 从 YAML 字符串创建 Job
     * @param yamlContent YAML 内容
     * @param namespace 命名空间
     * @return 创建的 Job 对象
     * @throws IOException IO 异常
     * @throws ApiException Kubernetes API 异常
     */
    public static V1Job createJobFromYamlString(String yamlContent, String namespace) throws IOException, ApiException {
        log.info("从 YAML 字符串创建 Job, namespace: {}", namespace);

        // 1. 从字符串加载 YAML 并解析为 V1Job 对象
        V1Job job = Yaml.loadAs(new StringReader(yamlContent), V1Job.class);

        // 2. 初始化 BatchV1Api
        BatchV1Api batchV1Api = new BatchV1Api();

        // 3. 创建 Job
        try {
            V1Job createdJob = batchV1Api.createNamespacedJob(
                    namespace,
                    job
            ).execute();
            log.info("Job 创建成功，名称: {}", createdJob.getMetadata().getName());
            return createdJob;
        } catch (ApiException e) {
            if (e.getCode() == 409) {
                log.warn("Job 已存在: {}", job.getMetadata().getName());
                throw new RuntimeException("Job 已存在: " + job.getMetadata().getName(), e);
            } else {
                log.error("创建 Job 失败: {}", e.getMessage());
                throw new RuntimeException("创建 Job 失败: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 获取 Pod 状态
     * @param podName Pod 名称
     * @param namespace 命名空间
     * @return Pod 状态信息（格式：status;exitCode）
     * @throws ApiException Kubernetes API 异常
     */
    public static String getPodStatus(String podName, String namespace) throws ApiException {
        log.info("查询 Pod 状态: {}, namespace: {}", podName, namespace);

        CoreV1Api coreV1Api = new CoreV1Api();
        try {
            V1Pod pod = coreV1Api.readNamespacedPod(podName, namespace).execute();
            V1PodStatus status = pod.getStatus();
            String phase = status.getPhase() != null ? status.getPhase() : "Unknown";

            // 获取退出码（如果容器已退出）
            Integer exitCode = null;
            if (status.getContainerStatuses() != null && !status.getContainerStatuses().isEmpty()) {
                V1ContainerStateTerminated terminated = status.getContainerStatuses().get(0).getState().getTerminated();
                if (terminated != null) {
                    exitCode = terminated.getExitCode();
                }
            }

            String result = phase;
            if (exitCode != null) {
                result += ";" + exitCode;
            }
            return result;
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                log.warn("Pod 不存在: {}", podName);
                return "NotFound;0";
            }
            log.error("查询 Pod 状态失败: {}", e.getMessage());
            throw new RuntimeException("查询 Pod 状态失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取 Pod 日志
     * @param podName Pod 名称
     * @param namespace 命名空间
     * @param lastLines 显示最后多少行日志
     * @return 日志内容
     * @throws ApiException Kubernetes API 异常
     */
    public static String getPodLogs(String podName, String namespace, Integer lastLines) throws ApiException {
        log.info("查询 Pod 日志: {}, namespace: {}, 显示最后{}行", podName, namespace, lastLines);

        CoreV1Api coreV1Api = new CoreV1Api();
        try {
            String logContent;
            if (lastLines != null && lastLines > 0) {
                logContent = coreV1Api.readNamespacedPodLog(
                        podName,
                        namespace
                ).tailLines(lastLines).execute();
            } else {
                logContent = coreV1Api.readNamespacedPodLog(
                        podName,
                        namespace
                ).execute();
            }

            return logContent;
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                log.warn("Pod 不存在: {}", podName);
                return "Pod not found";
            }
            log.error("查询 Pod 日志失败: {}", e.getMessage());
            throw new RuntimeException("查询 Pod 日志失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除 Pod
     * @param podName Pod 名称
     * @param namespace 命名空间
     * @return 删除结果
     * @throws ApiException Kubernetes API 异常
     */
    public static String deletePod(String podName, String namespace) throws ApiException {
        log.info("删除 Pod: {}, namespace: {}", podName, namespace);

        CoreV1Api coreV1Api = new CoreV1Api();
        try {
            coreV1Api.deleteNamespacedPod(
                    podName,
                    namespace
            ).execute();
            log.info("Pod 删除成功: {}", podName);
            return "Pod deleted successfully";
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                log.warn("Pod 不存在: {}", podName);
                return "Pod not found";
            }
            log.error("删除 Pod 失败: {}", e.getMessage());
            throw new RuntimeException("删除 Pod 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除 Job
     * @param jobName Job 名称
     * @param namespace 命名空间
     * @return 删除结果
     * @throws ApiException Kubernetes API 异常
     */
    public static String deleteJob(String jobName, String namespace) throws ApiException {
        log.info("删除 Job: {}, namespace: {}", jobName, namespace);

        BatchV1Api batchV1Api = new BatchV1Api();
        try {
            batchV1Api.deleteNamespacedJob(
                    jobName,
                    namespace
            ).execute();
            log.info("Job 删除成功: {}", jobName);
            return "Job deleted successfully";
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                log.warn("Job 不存在: {}", jobName);
                return "Job not found";
            }
            log.error("删除 Job 失败: {}", e.getMessage());
            throw new RuntimeException("删除 Job 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取 Job 状态
     * @param jobName Job 名称
     * @param namespace 命名空间
     * @return Job 状态信息（格式：status;exitCode）
     * @throws ApiException Kubernetes API 异常
     */
    public static String getJobStatus(String jobName, String namespace) throws ApiException {
        log.info("查询 Job 状态: {}, namespace: {}", jobName, namespace);

        BatchV1Api batchV1Api = new BatchV1Api();
        CoreV1Api coreV1Api = new CoreV1Api();
        try {
            V1Job job = batchV1Api.readNamespacedJob(jobName, namespace).execute();
            if (job == null) {
                return "NotFound;0";
            }

            V1JobStatus jobStatus = job.getStatus();
            String phase = "Unknown";

            // 从 Job 条件中获取状态
            if (jobStatus != null && jobStatus.getConditions() != null && !jobStatus.getConditions().isEmpty()) {
                for (V1JobCondition condition : jobStatus.getConditions()) {
                    if ("True".equalsIgnoreCase(condition.getStatus())) {
                        phase = condition.getType();
                        break;
                    }
                }
            }

            // 如果没有条件，根据 active/succeeded/failed 判断
            if ("Unknown".equals(phase) && jobStatus != null) {
                if (jobStatus.getActive() != null && jobStatus.getActive() > 0) {
                    phase = "Running";
                } else if (jobStatus.getSucceeded() != null && jobStatus.getSucceeded() > 0) {
                    phase = "Complete";
                } else if (jobStatus.getFailed() != null && jobStatus.getFailed() > 0) {
                    phase = "Failed";
                }
            }

            // 尝试获取 Pod 的退出码
            Integer exitCode = null;
            try {
                // 查找 Job 关联的 Pod（通过 job-name label）
                V1PodList podList = coreV1Api.listNamespacedPod(namespace)
                        .labelSelector("job-name=" + jobName)
                        .execute();

                if (podList.getItems() != null && !podList.getItems().isEmpty()) {
                    V1Pod pod = podList.getItems().get(0);
                    V1PodStatus podStatus = pod.getStatus();
                    if (podStatus != null && podStatus.getContainerStatuses() != null
                            && !podStatus.getContainerStatuses().isEmpty()) {
                        V1ContainerStateTerminated terminated = podStatus.getContainerStatuses().get(0)
                                .getState().getTerminated();
                        if (terminated != null) {
                            exitCode = terminated.getExitCode();
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("获取 Pod 退出码失败（可能 Pod 还未创建）: {}", e.getMessage());
            }

            String result = phase;
            if (exitCode != null) {
                result += ";" + exitCode;
            }
            return result;
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                log.warn("Job 不存在: {}", jobName);
                return "NotFound;0";
            }
            log.error("查询 Job 状态失败: {}", e.getMessage());
            throw new RuntimeException("查询 Job 状态失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取 Job 日志（从关联的 Pod 获取）
     * @param jobName Job 名称
     * @param namespace 命名空间
     * @param lastLines 显示最后多少行日志
     * @return 日志内容
     * @throws ApiException Kubernetes API 异常
     */
    public static String getJobLogs(String jobName, String namespace, Integer lastLines) throws ApiException {
        log.info("查询 Job 日志: {}, namespace: {}, 显示最后{}行", jobName, namespace, lastLines);

        CoreV1Api coreV1Api = new CoreV1Api();
        try {
            // 查找 Job 关联的 Pod（通过 job-name label）
            V1PodList podList = coreV1Api.listNamespacedPod(namespace)
                    .labelSelector("job-name=" + jobName)
                    .execute();

            if (podList.getItems() == null || podList.getItems().isEmpty()) {
                log.warn("Job 关联的 Pod 不存在: {}", jobName);
                return "Pod not found for job: " + jobName;
            }

            // 优先获取 Running 状态的 Pod，否则使用第一个
            V1Pod targetPod = podList.getItems().stream()
                    .filter(pod -> {
                        V1PodStatus status = pod.getStatus();
                        return status != null && "Running".equalsIgnoreCase(status.getPhase());
                    })
                    .findFirst()
                    .orElse(podList.getItems().get(0));

            String podName = targetPod.getMetadata().getName();
            String logContent;
            if (lastLines != null && lastLines > 0) {
                logContent = coreV1Api.readNamespacedPodLog(
                        podName,
                        namespace
                ).tailLines(lastLines).execute();
            } else {
                logContent = coreV1Api.readNamespacedPodLog(
                        podName,
                        namespace
                ).execute();
            }

            return logContent;
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                log.warn("Job 或 Pod 不存在: {}", jobName);
                return "Job or Pod not found";
            }
            log.error("查询 Job 日志失败: {}", e.getMessage());
            throw new RuntimeException("查询 Job 日志失败: " + e.getMessage(), e);
        }
    }

    /**
     * 列出运行中的训练 Pod
     * @param namespace 命名空间
     * @return Pod 列表信息
     * @throws ApiException Kubernetes API 异常
     */
    public static String listTrainingPods(String namespace) throws ApiException {
        log.info("列出运行中的训练 Pod, namespace: {}", namespace);

        CoreV1Api coreV1Api = new CoreV1Api();
        try {
            V1PodList podList = coreV1Api.listNamespacedPod(namespace).execute();

            StringBuilder result = new StringBuilder();
            result.append("NAME\tSTATUS\tAGE\n");
            for (V1Pod pod : podList.getItems()) {
                String name = pod.getMetadata().getName();
                String phase = pod.getStatus().getPhase() != null ? pod.getStatus().getPhase() : "Unknown";
                String creationTime = pod.getMetadata().getCreationTimestamp() != null
                        ? pod.getMetadata().getCreationTimestamp().toString()
                        : "Unknown";
                result.append(name).append("\t").append(phase).append("\t").append(creationTime).append("\n");
            }

            return result.toString();
        } catch (ApiException e) {
            log.error("列出 Pod 失败: {}", e.getMessage());
            throw new RuntimeException("列出 Pod 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 列出运行中的训练 Job
     * @param namespace 命名空间
     * @return Job 列表信息
     * @throws ApiException Kubernetes API 异常
     */
    public static String listTrainingJobs(String namespace) throws ApiException {
        log.info("列出运行中的训练 Job, namespace: {}", namespace);

        BatchV1Api batchV1Api = new BatchV1Api();
        try {
            V1JobList jobList = batchV1Api.listNamespacedJob(namespace).execute();

            StringBuilder result = new StringBuilder();
            result.append("NAME\tSTATUS\tAGE\n");
            for (V1Job job : jobList.getItems()) {
                String name = job.getMetadata().getName();
                String status = "Unknown";
                V1JobStatus jobStatus = job.getStatus();
                if (jobStatus != null) {
                    if (jobStatus.getActive() != null && jobStatus.getActive() > 0) {
                        status = "Running";
                    } else if (jobStatus.getSucceeded() != null && jobStatus.getSucceeded() > 0) {
                        status = "Complete";
                    } else if (jobStatus.getFailed() != null && jobStatus.getFailed() > 0) {
                        status = "Failed";
                    }
                }
                String creationTime = job.getMetadata().getCreationTimestamp() != null
                        ? job.getMetadata().getCreationTimestamp().toString()
                        : "Unknown";
                result.append(name).append("\t").append(status).append("\t").append(creationTime).append("\n");
            }

            return result.toString();
        } catch (ApiException e) {
            log.error("列出 Job 失败: {}", e.getMessage());
            throw new RuntimeException("列出 Job 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 生成随机的 Track ID（与 DockerTrainerUtil 保持一致）
     */
    public static String generateTrackId() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String timestamp = sdf.format(new Date());
        String uuidPart = UUID.randomUUID().toString().substring(0, 8);
        return "track_" + timestamp + "_" + uuidPart;
    }

    /**
     * 生成 Pod/Job 名称
     * 符合 Kubernetes RFC 1123 子域名规范：
     * - 只能包含小写字母、数字、'-' 或 '.'
     * - 必须以字母或数字开头和结尾
     * - 不能包含下划线 '_'
     */
    public static String generateResourceName(String prefix) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String timestamp = sdf.format(new Date());
        String uuidPart = UUID.randomUUID().toString().substring(0, 8);

        // 将下划线替换为连字符，并转换为小写
        String name = (prefix + "-" + timestamp + "-" + uuidPart).toLowerCase();

        // 确保名称符合 Kubernetes 规范：移除所有不符合规范的字符
        // 只保留小写字母、数字和连字符
        name = name.replaceAll("[^a-z0-9-]", "");

        // 确保以字母或数字开头和结尾（移除开头和结尾的连字符）
        name = name.replaceAll("^-+", "").replaceAll("-+$", "");

        // 如果名称为空或过长（Kubernetes 限制为 63 个字符），生成一个备用名称
        if (name.isEmpty() || name.length() > 63) {
            name = "k8s-" + timestamp + "-" + uuidPart.toLowerCase();
            name = name.replaceAll("[^a-z0-9-]", "");
            name = name.replaceAll("^-+", "").replaceAll("-+$", "");
        }

        return name;
    }

    /**
     * 根据 Job 名称获取关联的 Pod
     * @param jobName Job 名称
     * @param namespace 命名空间
     * @return Pod 对象，如果不存在则返回 null
     * @throws ApiException Kubernetes API 异常
     */
    public static V1Pod getPodByJobName(String jobName, String namespace) throws ApiException {
        log.info("根据 Job 名称查找 Pod: {}, namespace: {}", jobName, namespace);

        CoreV1Api coreV1Api = new CoreV1Api();
        try {
            // 查找 Job 关联的 Pod（通过 job-name label）
            V1PodList podList = coreV1Api.listNamespacedPod(namespace)
                    .labelSelector("job-name=" + jobName)
                    .execute();

            if (podList.getItems() == null || podList.getItems().isEmpty()) {
                log.warn("Job 关联的 Pod 不存在: {}", jobName);
                return null;
            }

            // 优先获取 Running 状态的 Pod，否则使用第一个
            V1Pod targetPod = podList.getItems().stream()
                    .filter(pod -> {
                        V1PodStatus status = pod.getStatus();
                        return status != null && "Running".equalsIgnoreCase(status.getPhase());
                    })
                    .findFirst()
                    .orElse(podList.getItems().get(0));

            return targetPod;
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                log.warn("Job 或 Pod 不存在: {}", jobName);
                return null;
            }
            log.error("查找 Pod 失败: {}", e.getMessage());
            throw new RuntimeException("查找 Pod 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从 Metrics API 获取 Pod 的 CPU 和内存使用情况
     * @param podName Pod 名称
     * @param namespace 命名空间
     * @return 包含 cpu 和 memory 使用量的 Map，如果获取失败则返回 null
     */
    public static java.util.Map<String, Object> getPodMetrics(String podName, String namespace) {
        log.info("获取 Pod 指标: {}, namespace: {}", podName, namespace);

        try {
            ApiClient apiClient = io.kubernetes.client.openapi.Configuration.getDefaultApiClient();
            if (apiClient == null) {
                log.warn("Kubernetes API 客户端未初始化");
                return null;
            }

            String basePath = apiClient.getBasePath();
            if (basePath == null || basePath.isEmpty()) {
                log.warn("API 基础路径为空");
                return null;
            }

            // 尝试 v1beta1 API
            String url = basePath + "/apis/metrics.k8s.io/v1beta1/namespaces/" + namespace + "/pods/" + podName;

            okhttp3.OkHttpClient httpClient = apiClient.getHttpClient();
            if (httpClient == null) {
                log.warn("HTTP 客户端未初始化");
                return null;
            }

            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Accept", "application/json")
                    .build();

            okhttp3.Response response = httpClient.newCall(request).execute();
            try {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    cn.hutool.json.JSONObject metrics = cn.hutool.json.JSONUtil.parseObj(responseBody);

                    if (metrics != null) {
                        cn.hutool.json.JSONArray containers = metrics.getJSONArray("containers");
                        if (containers != null && containers.size() > 0) {
                            cn.hutool.json.JSONObject container = containers.getJSONObject(0);
                            cn.hutool.json.JSONObject usage = container.getJSONObject("usage");

                            if (usage != null) {
                                java.util.Map<String, Object> result = new java.util.HashMap<>();
                                result.put("cpu", usage.getStr("cpu"));
                                result.put("memory", usage.getStr("memory"));
                                log.info("成功从 Metrics API 获取 Pod 指标");
                                return result;
                            }
                        }
                    }
                } else {
                    int code = response.code();
                    if (code == 404) {
                        log.debug("Metrics API 不可用 (404)，集群可能未安装 metrics-server。将使用资源请求/限制作为参考值");
                    } else {
                        log.warn("Metrics API 调用失败: code={}, message={}", code, response.message());
                    }
                }
            } finally {
                if (response != null) {
                    response.close();
                }
            }
        } catch (okhttp3.internal.http2.StreamResetException e) {
            log.debug("Metrics API 连接被重置，可能未安装 metrics-server: {}", e.getMessage());
        } catch (java.net.UnknownHostException e) {
            log.debug("无法解析 Metrics API 主机名，可能未安装 metrics-server: {}", e.getMessage());
        } catch (Exception e) {
            log.debug("获取 Pod 指标失败（Metrics API 可能不可用）: {}", e.getMessage());
        }

        return null;
    }

    /**
     * 获取 Pod 的资源请求和限制
     * @param pod Pod 对象
     * @return 包含 requests 和 limits 的 Map
     */
    public static java.util.Map<String, java.util.Map<String, Quantity>> getPodResourceRequirements(V1Pod pod) {
        java.util.Map<String, java.util.Map<String, Quantity>> result = new java.util.HashMap<>();
        java.util.Map<String, Quantity> requests = new java.util.HashMap<>();
        java.util.Map<String, Quantity> limits = new java.util.HashMap<>();

        if (pod != null && pod.getSpec() != null && pod.getSpec().getContainers() != null) {
            for (V1Container container : pod.getSpec().getContainers()) {
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

        result.put("requests", requests);
        result.put("limits", limits);
        return result;
    }
}
