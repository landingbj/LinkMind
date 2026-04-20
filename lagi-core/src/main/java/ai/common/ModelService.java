package ai.common;


import ai.llm.pojo.EnhanceChatCompletionRequest;
import ai.llm.responses.ResponseProtocolConstants;
import ai.openai.pojo.ChatCompletionRequest;
import lombok.Data;

import java.util.List;
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

    @Override
    public boolean verify() {
        if (apiKeys != null && !apiKeys.isEmpty()) {
            return apiKeys.stream().anyMatch(k -> k != null && !k.startsWith("you"));
        }
        return getApiKey() != null && !getApiKey().startsWith("you");
    }

    public String selectNextKey() {
        if (apiKeys == null || apiKeys.isEmpty()) {
            return apiKey;
        }
        int current, next;
        do {
            current = keyCounter.get();
            next = (current + 1) % apiKeys.size();
        } while (!keyCounter.compareAndSet(current, next));
        return apiKeys.get(next);
    }

    public boolean hasKeyPool() {
        return apiKeys != null && apiKeys.size() > 1 && keyRoute != null;
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
    }
}
