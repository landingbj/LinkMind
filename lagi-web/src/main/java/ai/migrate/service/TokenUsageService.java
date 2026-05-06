package ai.migrate.service;

import ai.common.utils.LRUCache;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.Usage;
import ai.utils.AiGlobal;
import ai.utils.ApikeyUtil;
import ai.utils.OkHttpUtil;
import ai.utils.OkHttpUtil.HttpPostResult;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
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
                    log.info("Sending calculateUsage request: {}", body);
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
