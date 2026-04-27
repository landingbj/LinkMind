package ai.common;


import ai.common.utils.LRUCache;
import ai.llm.pojo.EnhanceChatCompletionRequest;
import ai.llm.responses.ResponseProtocolConstants;
import ai.openai.pojo.ChatCompletionRequest;
import lombok.Data;

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
    private transient final LRUCache<String, String> ipKeyCache = new LRUCache<>(1000, 30, TimeUnit.DAYS);

    @Override
    public boolean verify() {
        if (apiKeys != null && !apiKeys.isEmpty()) {
            return apiKeys.stream().anyMatch(k -> k != null && !k.startsWith("you"));
        }
        return getApiKey() != null && !getApiKey().startsWith("you");
    }

    public String selectNextKey(ChatCompletionRequest request) {
        if (apiKeys == null || apiKeys.isEmpty()) {
            return apiKey;
        }
        String ip = extractIp(request);
        if (ip != null && !ip.isEmpty()) {
            String cached = ipKeyCache.get(ip);
            if (cached != null) {
                return cached;
            }
        }
        int current, next;
        do {
            current = keyCounter.get();
            next = (current + 1) % apiKeys.size();
        } while (!keyCounter.compareAndSet(current, next));
        String selected = apiKeys.get(next);
        if (ip != null && !ip.isEmpty()) {
            ipKeyCache.put(ip, selected);
        }
        return selected;
    }

    private String extractIp(ChatCompletionRequest request) {
        if (request instanceof EnhanceChatCompletionRequest) {
            return ((EnhanceChatCompletionRequest) request).getIp();
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
    }
}
