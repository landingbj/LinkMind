package ai.common;


import ai.common.utils.LRUCache;
import ai.llm.pojo.EnhanceChatCompletionRequest;
import ai.llm.responses.ResponseProtocolConstants;
import ai.openai.pojo.ChatCompletionRequest;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Data
public class ModelService implements ModelVerify {
    protected String appId;
    protected String backend;
    protected String apiKey;
    protected String secretKey;
    protected String appKey;
    protected String accessKeyId;
    protected String accessKeySecret;
    protected Integer priority;
    protected String model;
    protected String type;
    protected String apiAddress;
    protected String endpoint;
    protected String deployment;
    protected String apiVersion;
    protected String securityKey;
    protected String accessToken;
    private String others;
    protected String alias;
    protected Boolean enable;
    protected String router;
    protected Integer concurrency;
    protected String protocol = ResponseProtocolConstants.COMPLETION;
    protected Boolean function;
    protected List<String> apiKeys;
    protected String keyRoute;
    private transient final AtomicInteger keyCounter = new AtomicInteger(-1);
    private transient final LRUCache<String, String> sessionKeyCache = new LRUCache<>(1000, 30, TimeUnit.DAYS);

    @Override
    public boolean verify() {
        if (apiKeys != null && !apiKeys.isEmpty()) {
            return apiKeys.stream().anyMatch(k -> k != null && !k.startsWith("you"));
        }
        return getApiKey() != null && !getApiKey().startsWith("you");
    }

    public String getApiKey(ChatCompletionRequest request) {
        if (request != null && request.getSelectedBackendApiKey() != null) {
            return request.getSelectedBackendApiKey();
        }
        return apiKey;
    }

    /**
     * Select one key per conversation and retain that assignment for the
     * conversation's lifetime. Calls without a session identity retain the
     * original per-request round-robin behaviour.
     */
    public synchronized String selectNextKey(ChatCompletionRequest request) {
        if (apiKeys == null || apiKeys.isEmpty()) {
            return apiKey;
        }
        String sessionKey = extractSessionKey(request);
        if (sessionKey != null) {
            String cached = sessionKeyCache.get(sessionKey);
            if (cached != null && apiKeys.contains(cached)) {
                return cached;
            }
        }
        int current, next;
        do {
            current = keyCounter.get();
            next = (current + 1) % apiKeys.size();
        } while (!keyCounter.compareAndSet(current, next));
        String selected = apiKeys.get(next);
        if (sessionKey != null) {
            sessionKeyCache.put(sessionKey, selected);
        }
        return selected;
    }

    private String extractSessionKey(ChatCompletionRequest request) {
        if (request == null) {
            return null;
        }
        String servletSessionId = request.getKeyPoolSessionId();
        if (servletSessionId != null && !servletSessionId.trim().isEmpty()) {
            return servletSessionId;
        }
        String sessionId = request.getSessionId();
        if (sessionId != null && !sessionId.trim().isEmpty()) {
            return sessionId;
        }
        return null;
    }

    protected void setDefaultField(ChatCompletionRequest request) {
        if (request.getModel() == null) {
            request.setModel(getModel());
        }
        if (request instanceof EnhanceChatCompletionRequest) {
            ((EnhanceChatCompletionRequest) request).setIp(null);
            ((EnhanceChatCompletionRequest) request).setBrowserIp(null);
        }
        request.setCategory(null);
        request.setApiKey(null);
        if (function != null && !function) {
            request.setTools(null);
            request.setTool_choice(null);
            request.setParallel_tool_calls(null);
        }
        request.setExtraBody(null);
        request.setUserApiKey(null);
    }
}
