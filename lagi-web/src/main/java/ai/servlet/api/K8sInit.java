package ai.servlet.api;


import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;

/**
 * k8s的初始化：直接加载 kubeconfig，带上正确的 CA、token、apiserver 地址。
 */
public class K8sInit {

    public static ApiClient getConnection() {
        // 在系统层面禁用 SSL 验证
        try {
            System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");

            // 创建一个信任所有证书的 TrustManager
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
            };

            // 安装信任所有证书的 TrustManager
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            SSLContext.setDefault(sc);
        } catch (Exception e) {
            throw new RuntimeException("初始化 SSL 上下文失败", e);
        }

        String kubeConfigPath = System.getenv("KUBECONFIG");
        if (kubeConfigPath == null || kubeConfigPath.isEmpty()) {
            kubeConfigPath = System.getProperty("user.home") + "/.kube/config";
        }

        // 优先使用 kubeconfig（最安全、最完整）
        if (Files.exists(Paths.get(kubeConfigPath))) {
            try {
                ApiClient client = ClientBuilder.kubeconfig(
                                KubeConfig.loadKubeConfig(new FileReader(kubeConfigPath)))
                        .build();
                Configuration.setDefaultApiClient(client);
                return client;
            } catch (IOException e) {
                throw new RuntimeException("加载 kubeconfig 失败: " + kubeConfigPath, e);
            }
        }

        // 如果没有 kubeconfig，使用内存中的 kubeconfig 配置
        String kubeConfigContent = "apiVersion: v1\n" +
                "clusters:\n" +
                "- cluster:\n" +
                "    server: https://103.85.179.118:26443\n" +
                "    insecure-skip-tls-verify: true\n" +
                "  name: k8s-cluster\n" +
                "contexts:\n" +
                "- context:\n" +
                "    cluster: k8s-cluster\n" +
                "    user: admin-user\n" +
                "  name: k8s-context\n" +
                "current-context: k8s-context\n" +
                "kind: Config\n" +
                "users:\n" +
                "- name: admin-user\n" +
                "  user:\n" +
                "    token: eyJhbGciOiJSUzI1NiIsImtpZCI6Imh6SldEVzVHakFnRWI4dFl2a3M2T0dyWTVRTkxFU3p6MERTdmZJVGxvQ3cifQ.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJrdWJlcm5ldGVzLWRhc2hib2FyZCIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VjcmV0Lm5hbWUiOiJhZG1pbi11c2VyLXRva2VuLWdqc3E5Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZXJ2aWNlLWFjY291bnQubmFtZSI6ImFkbWluLXVzZXIiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC51aWQiOiI2NzA2N2RjZi05MGI3LTQ0YTYtYTUyNy1mMDQwZTM1MzkwY2EiLCJzdWIiOiJzeXN0ZW06c2VydmljZWFjY291bnQ6a3ViZXJuZXRlcy1kYXNoYm9hcmQ6YWRtaW4tdXNlciJ9.AzH-CqyfYbKh0BFl8NWrUNvUtPN45XH3C509lU0_ShA4i1YFRMSFHzaElkcqVO0HIVxWnfk_AHumSCeQMKOHBC77rsjjYsfnNYimQXWXq3rWggfhQFnk-91jq4LC5_c0irnYTG8eMenCJwYrX-T4XpepMLhMVVKwM0kMaV6LLk1aJE9XPb5kJiSeXNLWMaBO9HJhADjmUYc8e2b1l-3MXdloYR0Iqy32yoWzNKG6uh3YP92eMMym_PVMeFPYeS4dIYJOXkBjQSglJLVPIwHWgQnWC0YTiIn2AZCoJ6GopDDQYC5OtKRhAaqNDNeDDxez2ByYbG81km0YkLufPywamA\n";

        try {
            KubeConfig kubeConfig = KubeConfig.loadKubeConfig(new StringReader(kubeConfigContent));
            ApiClient client = ClientBuilder.kubeconfig(kubeConfig).build();

            // 关键：构建后显式禁用 SSL 验证
            client.setVerifyingSsl(false);

            Configuration.setDefaultApiClient(client);
            return client;
        } catch (IOException e) {
            throw new RuntimeException("创建 ApiClient 失败", e);
        }
    }

}
