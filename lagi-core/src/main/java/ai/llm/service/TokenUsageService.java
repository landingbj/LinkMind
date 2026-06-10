package ai.llm.service;

import ai.common.utils.LRUCache;
import ai.config.ContextLoader;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.Usage;
import ai.utils.AiGlobal;
import ai.utils.ApikeyUtil;
import ai.utils.ModelNameUtil;
import ai.utils.OkHttpUtil;
import ai.utils.OkHttpUtil.HttpPostResult;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
public class TokenUsageService {
    private static final Gson gson = new Gson();
    private static final TokenUsageService INSTANCE = new TokenUsageService();
    private static final String CALCULATE_USAGE_URL = AiGlobal.SAAS_URL + "/saas/api/apikey/calculateUsage";
    private static final long RETRY_INTERVAL_MILLIS = 3000L;
    private static final int CONSUMER_THREADS = 3;
    private final TokenUsageQueue tokenUsageQueue = TokenUsageQueue.getInstance();
    private final LRUCache<String, Boolean> usageCache = new LRUCache<>(1000, 600, TimeUnit.SECONDS);

    private TokenUsageService() {
        for (int i = 0; i < CONSUMER_THREADS; i++) {
            ExecutorService reportExecutor = Executors.newFixedThreadPool(CONSUMER_THREADS, r -> {
                Thread thread = new Thread(r, "token-usage-report");
                thread.setDaemon(true);
                return thread;
            });
            reportExecutor.submit(this::reportUsageToSaasAsync);
        }
    }

    public static TokenUsageService getInstance() {
        return INSTANCE;
    }

    /**
     * Convenience entry point that gates on the global {@code chat.tokenCharge}
     * flag, sanitises the apiKey, and resolves the model name from the request
     * (falling back to the response). Used by all chat-completion sites that
     * want to bill the caller for an LLM call.
     *
     * @param apiKey  caller-provided apiKey; when blank, falls back to
     *                {@code request.getApiKey()}
     * @param request the original chat completion request (for model / fallback apiKey)
     * @param result  the LLM response carrying {@code id}, {@code model}, and {@code usage}
     */
    public void recordUsage(String apiKey, ChatCompletionRequest request, ChatCompletionResult result) {
        String effectiveApiKey = apiKey;
        if (isBlank(effectiveApiKey) && request != null) {
            effectiveApiKey = request.getApiKey();
        }
        ApikeyUtil.removeInvalidApiKey(effectiveApiKey);
        if (!isTokenChargeEnabled()) {
            log.debug("recordUsage skipped: tokenCharge disabled");
            return;
        }
        if (result == null || result.getUsage() == null) {
            log.debug("recordUsage skipped: result or usage is null");
            return;
        }
        String modelName = resolveModelName(request, result);
        if (isBlank(modelName)) {
            log.debug("[{}] recordUsage skipped: model name is blank (id={})", maskApiKey(effectiveApiKey), result.getId());
            return;
        }
        log.info("[{}] recordUsage id={}, model={}, promptTokens={}, completionTokens={}", maskApiKey(effectiveApiKey),
                result.getId(), modelName,
                result.getUsage().getPrompt_tokens(),
                result.getUsage().getCompletion_tokens());
        recordUsage(result.getId(), effectiveApiKey, modelName, result.getUsage());
    }

    /**
     * Overload for callers that bill the apiKey carried by the request itself
     * (typical for internally synthesised agent requests).
     */
    public void recordUsage(ChatCompletionRequest request, ChatCompletionResult result) {
        recordUsage(null, request, result);
    }

    private boolean isTokenChargeEnabled() {
        try {
            Boolean tokenCharge = ContextLoader.configuration.getFunctions().getChat().getTokenCharge();
            return Boolean.TRUE.equals(tokenCharge);
        } catch (Exception e) {
            // Configuration not loaded yet (e.g. unit tests): treat as disabled.
            return false;
        }
    }

    private String resolveModelName(ChatCompletionRequest request, ChatCompletionResult result) {
        return ModelNameUtil.resolveBillingModelName(
                request == null ? null : request.getModel(),
                result == null ? null : result.getModel());
    }

    public void recordUsage(String id, String apiKey, String modelName, Usage usage) {
        if (!ApikeyUtil.isLandingKey(apiKey)) {
            return;
        }
        if (usageCache.get(id) != null) {
            return;
        }
        if (isBlank(apiKey) || isBlank(modelName) || usage == null) {
            return;
        }
        boolean offered = tokenUsageQueue.enqueue(apiKey.trim(), modelName.trim(), usage);
        if (!offered) {
            log.warn("Token usage enqueue skipped");
        }
        usageCache.put(id, true);
    }

    public void reportUsageToSaasAsync() {
        while (true) {
            TokenUsageQueue.TokenUsageRecord record = takeRecord();
            if (record == null) {
                continue;
            }
            String body = buildCalculateUsagePayload(record);
            while (true) {
                try {
                    log.info("Sending calculateUsage request: {}", maskApiKeyInPayload(body));
                    HttpPostResult res = OkHttpUtil.postJsonWithStatus(CALCULATE_USAGE_URL, body);
                    if (res.getCode() == 200) {
                        log.debug("calculateUsage success: {}", res.getBody());
                        break;
                    }
                    if (res.getCode() == 400) {
                        log.warn("calculateUsage client error (no retry): {} {}", res.getCode(), res.getBody());
                        break;
                    }
                    log.warn("calculateUsage failed, retrying: {} {}", res.getCode(), res.getBody());
                    sleepSilently(RETRY_INTERVAL_MILLIS);
                } catch (IOException e) {
                    log.warn("calculateUsage request error, retrying until success", e);
                    sleepSilently(RETRY_INTERVAL_MILLIS);
                }
            }
        }
    }

    private String buildCalculateUsagePayload(TokenUsageQueue.TokenUsageRecord record) {
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("apiKey", record.getApiKey());
        payload.put("modelName", record.getModelName());
        Usage u = record.getUsage();
        payload.put("inputTokens", u == null ? 0L : u.getPrompt_tokens());
        payload.put("outputTokens", u == null ? 0L : u.getCompletion_tokens());
        return gson.toJson(payload);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String maskApiKey(String apiKey) {
        if (isBlank(apiKey)) {
            return "";
        }
        String trimmed = apiKey.trim();
        int keepPrefix = Math.min(7, trimmed.length());
        int keepSuffix = Math.min(4, Math.max(0, trimmed.length() - keepPrefix));
        if (trimmed.length() <= keepPrefix + keepSuffix) {
            return "***";
        }
        return trimmed.substring(0, keepPrefix) + "..." + trimmed.substring(trimmed.length() - keepSuffix);
    }

    private static String maskApiKeyInPayload(String body) {
        if (body == null) {
            return null;
        }
        return body.replaceAll("(\"apiKey\"\\s*:\\s*\")([^\"]+)(\")", "$1***$3");
    }

    private void sleepSilently(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException ignored) {
            // Keep background consumer alive.
        }
    }

    private TokenUsageQueue.TokenUsageRecord takeRecord() {
        try {
            return tokenUsageQueue.take();
        } catch (InterruptedException ignored) {
            return null;
        }
    }
}
