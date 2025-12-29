package ai.finetune;


import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import io.kubernetes.client.util.credentials.AccessTokenAuthentication;
import okhttp3.OkHttpClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

/**
 * 统一的 K8s 客户端构建器，复用“信任所有证书 + kubeconfig 优先”逻辑。
 * 同时支持官方 Java SDK（io.kubernetes.client） 客户端。
 */
public final class K8sClientSupport {

    private static final String DEFAULT_SERVER = "https://103.85.179.118:26443";
    private static final String DEFAULT_TOKEN = "eyJhbGciOiJSUzI1NiIsImtpZCI6Imh6SldEVzVHakFnRWI4dFl2a3M2T0dyWTVRTkxFU3p6MERTdmZJVGxvQ3cifQ.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJrdWJlcm5ldGVzLWRhc2hib2FyZCIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VjcmV0Lm5hbWUiOiJhZG1pbi11c2VyLXRva2VuLWdqc3E5Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZXJ2aWNlLWFjY291bnQubmFtZSI6ImFkbWluLXVzZXIiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC51aWQiOiI2NzA2N2RjZi05MGI3LTQ0YTYtYTUyNy1mMDQwZTM1MzkwY2EiLCJzdWIiOiJzeXN0ZW06c2VydmljZWFjY291bnQ6a3ViZXJuZXRlcy1kYXNoYm9hcmQ6YWRtaW4tdXNlciJ9.AzH-CqyfYbKh0BFl8NWrUNvUtPN45XH3C509lU0_ShA4i1YFRMSFHzaElkcqVO0HIVxWnfk_AHumSCeQMKOHBC77rsjjYsfnNYimQXWXq3rWggfhQFnk-91jq4LC5_c0irnYTG8eMenCJwYrX-T4XpepMLhMVVKwM0kMaV6LLk1aJE9XPb5kJiSeXNLWMaBO9HJhADjmUYc8e2b1l-3MXdloYR0Iqy32yoWzNKG6uh3YP92eMMym_PVMeFPYeS4dIYJOXkBjQSglJLVPIwHWgQnWC0YTiIn2AZCoJ6GopDDQYC5OtKRhAaqNDNeDDxez2ByYbG81km0YkLufPywamA";

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
     * 构建官方 Java SDK 的 ApiClient，优先 kubeconfig，退化到内置 server/token。
     */
    public static ApiClient buildOpenApiClient(String preferredServer, String preferredToken) {
        String kubeConfigPath = resolveKubeConfigPath();
        try {
            ApiClient client;
            if (Files.exists(Paths.get(kubeConfigPath))) {
                client = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(new FileReader(kubeConfigPath))).build();
            } else {
                String server = firstNonEmpty(preferredServer, System.getenv("K8S_API_SERVER"), DEFAULT_SERVER);
                String token = firstNonEmpty(preferredToken, System.getenv("K8S_TOKEN"), DEFAULT_TOKEN);
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

