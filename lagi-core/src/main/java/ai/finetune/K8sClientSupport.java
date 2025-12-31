package ai.finetune;

import ai.config.ContextLoader;
import ai.config.pojo.DiscriminativeModelsConfig;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import io.kubernetes.client.util.credentials.AccessTokenAuthentication;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

/**
 * 统一的 K8s 客户端构建器，复用"信任所有证书 + kubeconfig 优先"逻辑。
 * 同时支持官方 Java SDK（io.kubernetes.client） 客户端。
 */
public final class K8sClientSupport {

    private static final Logger log = LoggerFactory.getLogger(K8sClientSupport.class);

    static {
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
        try {
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        @Override
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            SSLContext.setDefault(sc);
        } catch (Exception e) {
            throw new RuntimeException("初始化 SSL 上下文失败", e);
        }
    }

    private K8sClientSupport() {
    }

    /**
     * 构建官方 Java SDK 的 ApiClient，优先 kubeconfig，退化到配置文件、环境变量。
     * 优先级：preferred参数 > kubeconfig > 配置文件 > 环境变量
     */
    public static ApiClient buildOpenApiClient(String preferredServer, String preferredToken) {
        String kubeConfigPath = resolveKubeConfigPath();
        try {
            ApiClient client;
            if (Files.exists(Paths.get(kubeConfigPath))) {
                client = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(new FileReader(kubeConfigPath))).build();
            } else {
                // 从配置文件读取默认值
                String configServer = getServerFromConfig();
                String configToken = getTokenFromConfig();
                
                // 优先级：preferred参数 > 环境变量 > 配置文件
                String server = firstNonEmpty(preferredServer, System.getenv("K8S_API_SERVER"), configServer);
                String token = firstNonEmpty(preferredToken, System.getenv("K8S_TOKEN"), configToken);
                
                if (server == null || token == null) {
                    throw new RuntimeException("无法获取 K8s API Server 或 Token。请配置 lagi.yml 中的 model_platform.discriminative_models.k8s.cluster_config，或设置环境变量 K8S_API_SERVER 和 K8S_TOKEN");
                }
                
                client = new ClientBuilder()
                        .setBasePath(server)
                        .setAuthentication(new AccessTokenAuthentication(token))
                        .setVerifyingSsl(false)
                        .build();
            }
            client.setHttpClient(buildUnsafeOkHttpClient());
            client.setVerifyingSsl(false);
            Configuration.setDefaultApiClient(client);
            return client;
        } catch (IOException e) {
            throw new RuntimeException("创建 K8s ApiClient 失败", e);
        }
    }

    /**
     * 从配置文件读取 K8s API Server 地址
     * 配置路径：model_platform.discriminative_models.k8s.cluster_config.apiServer
     */
    private static String getServerFromConfig() {
        try {
            ContextLoader.loadContext();
            if (ContextLoader.configuration != null
                    && ContextLoader.configuration.getModelPlatformConfig() != null
                    && ContextLoader.configuration.getModelPlatformConfig().getDiscriminativeModelsConfig() != null) {
                
                DiscriminativeModelsConfig dm = ContextLoader.configuration.getModelPlatformConfig().getDiscriminativeModelsConfig();
                if (dm.getK8s() != null && dm.getK8s().getClusterConfig() != null) {
                    String apiServer = dm.getK8s().getClusterConfig().getApiServer();
                    if (apiServer != null && !apiServer.trim().isEmpty()) {
                        return apiServer.trim();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("从配置文件读取 K8s API Server 失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 从配置文件读取 K8s Token
     * 配置路径：model_platform.discriminative_models.k8s.cluster_config.token
     */
    private static String getTokenFromConfig() {
        try {
            ContextLoader.loadContext();
            if (ContextLoader.configuration != null
                    && ContextLoader.configuration.getModelPlatformConfig() != null
                    && ContextLoader.configuration.getModelPlatformConfig().getDiscriminativeModelsConfig() != null) {
                
                DiscriminativeModelsConfig dm = ContextLoader.configuration.getModelPlatformConfig().getDiscriminativeModelsConfig();
                if (dm.getK8s() != null && dm.getK8s().getClusterConfig() != null) {
                    String token = dm.getK8s().getClusterConfig().getToken();
                    if (token != null && !token.trim().isEmpty()) {
                        return token.trim();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("从配置文件读取 K8s Token 失败: {}", e.getMessage());
        }
        return null;
    }



    private static OkHttpClient buildUnsafeOkHttpClient() {
        try {
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };

            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true);
            builder.connectTimeout(30, TimeUnit.SECONDS);
            builder.readTimeout(30, TimeUnit.SECONDS);
            builder.writeTimeout(30, TimeUnit.SECONDS);
            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException("构建不安全的 OkHttpClient 失败", e);
        }
    }

    private static String resolveKubeConfigPath() {
        String kubeConfigPath = System.getenv("KUBECONFIG");
        if (kubeConfigPath == null || kubeConfigPath.isEmpty()) {
            kubeConfigPath = System.getProperty("user.home") + "/.kube/config";
        }
        return kubeConfigPath;
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) {
                return v.trim();
            }
        }
        return null;
    }
}

