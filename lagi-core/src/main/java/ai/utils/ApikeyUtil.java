package ai.utils;

import ai.common.pojo.Response;
import ai.common.utils.LRUCache;
import ai.config.ContextLoader;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.adapter.impl.ProxyLlmAdapter;
import ai.manager.AIManager;
import ai.manager.LlmManager;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ApikeyUtil {
    private static final Gson gson = new Gson();
    private static final Logger logger = LoggerFactory.getLogger(ApikeyUtil.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String SAAS_API_KEY_VALIDATE_PATH = "/saas/api/apikey/validate";
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final Boolean enableAuth = ContextLoader.configuration.getFunctions().getChat().getEnableAuth();
    private static final LRUCache<String, Boolean> invalidApiKey = new LRUCache<>(10000);
    private static final Map<String, String> modelKeyMap = new ConcurrentHashMap<>();

    static {
        AIManager<ILlmAdapter> llmAdapterAIManager = LlmManager.getInstance();
        for (ILlmAdapter adapter : llmAdapterAIManager.getAdapters()) {
            ProxyLlmAdapter proxyLlmAdapter = (ProxyLlmAdapter) adapter;
            if (proxyLlmAdapter == null || proxyLlmAdapter.getApiKey() == null || proxyLlmAdapter.getModel() == null) {
                continue;
            }
            putModelKey(proxyLlmAdapter.getModel(), proxyLlmAdapter.getApiKey());
        }
    }

    public static void putModelKey(String model, String apiKey) {
        modelKeyMap.put(model, apiKey);
    }

    public static String getModelKey(String model) {
        return modelKeyMap.get(model);
    }

    public static String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String apiKey = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        return apiKey.isEmpty() ? null : apiKey;
    }

    public static boolean isApiKeyValid(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return false;
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", BEARER_PREFIX + apiKey.trim());
        boolean result = false;
        try {
            String json = OkHttpUtil.post(AiGlobal.SAAS_URL + "/isApiKeyValid", headers, new HashMap<>(), "", DEFAULT_TIMEOUT_SECONDS);
            Response response = gson.fromJson(json, Response.class);
            if (response != null && "success".equals(response.getStatus())) {
                result = true;
            }
        } catch (Exception e) {
            logger.error("Landing Apikey check error", e);
        }
        return result;
    }

    private static final LRUCache<String, Boolean> landingKeyCache = new LRUCache<>(1000);

    public static boolean isLandingKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return false;
        }
        return landingKeyCache.containsKey(apiKey);
    }

    public static boolean validateModelApiKey(String apiKey) {
        if (!enableAuth) {
            return true;
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return false;
        }
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("apiKey", apiKey.trim());
        String jsonBody = gson.toJson(requestBody);
        try {
            String json = OkHttpUtil.post(AiGlobal.SAAS_URL + SAAS_API_KEY_VALIDATE_PATH, new HashMap<>(), new HashMap<>(), jsonBody, DEFAULT_TIMEOUT_SECONDS);
            Response response = gson.fromJson(json, Response.class);
            if (response != null && !"not_found".equalsIgnoreCase(response.getStatus())) {
                landingKeyCache.put(apiKey, true);
            }
            return !(response != null && "failed".equalsIgnoreCase(response.getStatus()));
        } catch (Exception e) {
            logger.error("ApiKey validate api check error", e);
            return false;
        }
    }

    public static List<String> filterInvalidApiKey(List<String> apiKeyList) {
        return apiKeyList.stream().filter(key -> !isInvalidApiKey(key)).collect(Collectors.toList());
    }

    public static List<String> listInvalidApiKeys(List<String> apiKeyList) {
        return apiKeyList.stream().filter(ApikeyUtil::isInvalidApiKey).collect(Collectors.toList());
    }

    public static boolean isInvalidApiKey(String apiKey) {
        return invalidApiKey.get(apiKey) != null;
    }

    public static void saveInvalidApiKey(String apiKey) {
        invalidApiKey.put(apiKey, true);
    }

    public static void removeInvalidApiKey(String apiKey) {
        invalidApiKey.remove(apiKey);
    }
}
