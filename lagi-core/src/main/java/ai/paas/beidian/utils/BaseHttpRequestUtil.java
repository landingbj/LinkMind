package ai.paas.beidian.utils;

import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import okhttp3.*;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class BaseHttpRequestUtil {

    private static final ConnectionPool connectionPool = new ConnectionPool(5, 1, TimeUnit.MINUTES);

    private static OkHttpClient client;

    static {
        try {
            // 创建信任所有证书的 TrustManager
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[]{};
                        }
                    }
            };

            // 创建 SSLContext 并初始化
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            // 创建 HostnameVerifier 验证所有主机名
            HostnameVerifier allHostsValid = (hostname, session) -> true;

            client = new OkHttpClient.Builder()
                    .followRedirects(false)
                    .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0])
                    .hostnameVerifier(allHostsValid)
                    .connectionPool(connectionPool)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Map<String, String> toHeader(String traceId, String spaceId, String authorization, String acceptLanguage) {
        Map<String, String> headers = Maps.newHashMap();
        headers.put("traceId", traceId);
        headers.put("spaceId", spaceId);
        headers.put("authorization", "Bearer " +authorization);
        headers.put("accept-language", acceptLanguage);
        return headers;
    }

    public static String pathUrl(String url, Map<String, String> pathParams) {
        if(pathParams == null) {
            return url;
        }
        return StrUtil.format(url, pathParams);
    }

    // 构建带查询参数的 URL
    private static String buildUrlWithQuery(String baseUrl, Map<String, String> query) {
        HttpUrl.Builder urlBuilder = HttpUrl.get(baseUrl).newBuilder();
        if (query != null) {
            for (Map.Entry<String, String> entry : query.entrySet()) {
                urlBuilder.addQueryParameter(entry.getKey(), entry.getValue());
            }
        }
        return urlBuilder.build().toString();
    }

    public static List<String> getResponseAndCookie(String apiUrl, Map<String, String> query, Map<String, String> headers) throws IOException {
        String url = buildUrlWithQuery(apiUrl, query);

        Request.Builder requestBuilder = new Request.Builder().url(url);
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                requestBuilder.addHeader(entry.getKey(), entry.getValue());
            }
        }

        try (Response response = client.newCall(requestBuilder.build()).execute()) {
            ResponseBody responseBody = response.body();
            if (response.isSuccessful() && responseBody != null) {
                List<String> setCookies = response.headers("Set-Cookie");
                String realCookieValue = setCookies.get(1).split(";")[0].split("=")[1];
                return Lists.newArrayList(responseBody.string(), realCookieValue);
            } else {
                throw new IOException("Request failed: " + response.message());
            }
        }
    }


    public static List<String> getResponseAndHeader(String apiUrl, Map<String, String> query, Map<String, String> headers, String resHeaderName) throws IOException {
        String url = buildUrlWithQuery(apiUrl, query);

        Request.Builder requestBuilder = new Request.Builder().url(url);
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                requestBuilder.addHeader(entry.getKey(), entry.getValue());
            }
        }

        try (Response response = client.newCall(requestBuilder.build()).execute()) {
            ResponseBody responseBody = response.body();
            int code = response.code();
            if(code != 301) {
                throw new IOException("code:" + code);
            }
            String header = response.header(resHeaderName);
            return Lists.newArrayList(responseBody.string(), header);
        }
    }

    // GET 请求
    public static String get(String apiUrl, Map<String, String> query, Map<String, String> headers) throws IOException {
        String url = buildUrlWithQuery(apiUrl, query);

        Request.Builder requestBuilder = new Request.Builder().url(url);
        return request(headers, requestBuilder);
    }

    // PUT 请求
    public static String put(String apiUrl, Map<String, String> query, Map<String, String> headers, String body) throws IOException {
        String url = buildUrlWithQuery(apiUrl, query);

        RequestBody requestBody = RequestBody.create(body, MediaType.get("application/json"));

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .put(requestBody);

        return request(headers, requestBuilder);
    }

    // DELETE 请求
    public static String delete(String apiUrl, Map<String, String> query, Map<String, String> headers) throws IOException {
        String url = buildUrlWithQuery(apiUrl, query);

        Request.Builder requestBuilder = new Request.Builder().url(url).delete();

        return request(headers, requestBuilder);
    }

    // POST 请求
    public static String post(String apiUrl, Map<String, String> query, Map<String, String> headers, String body) throws IOException {

        String url = buildUrlWithQuery(apiUrl, query);

        RequestBody requestBody = RequestBody.create(body, MediaType.get("application/json"));

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .post(requestBody);

        return request(headers, requestBuilder);
    }


    /**
     * PATCH 请求
     *
     * @param apiUrl  接口地址
     * @param query   查询参数
     * @param headers 请求头
     * @param body    请求体（JSON 字符串）
     * @return 响应内容
     * @throws IOException IO 异常
     */
    public static String patch(String apiUrl, Map<String, String> query, Map<String, String> headers, String body) throws IOException {
        String url = buildUrlWithQuery(apiUrl, query);

        RequestBody requestBody = RequestBody.create(body, MediaType.get("application/json"));

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .patch(requestBody);

        return request(headers, requestBuilder);
    }


    private static String request(Map<String, String> headers, Request.Builder requestBuilder) throws IOException {
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                requestBuilder.addHeader(entry.getKey(), entry.getValue());
            }
        }

        try (Response response = client.newCall(requestBuilder.build()).execute()) {
            ResponseBody responseBody = response.body();
            if (response.isSuccessful() && responseBody != null) {
                return responseBody.string();
            } else {
                throw new IOException("Request failed: " + response.message());
            }
        }
    }
}
