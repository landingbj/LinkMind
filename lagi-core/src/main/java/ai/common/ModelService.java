package ai.common;


import ai.llm.pojo.EnhanceChatCompletionRequest;
import ai.llm.responses.ResponseProtocolConstants;
import ai.openai.pojo.ChatCompletionRequest;
import lombok.Data;
@Data
public class ModelService implements ModelVerify{

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
    @Override
    public boolean verify() {
        if(getApiKey() == null || getApiKey().startsWith("you")) {
            return false;
        }
        return true;
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
        if (function != null && !function) {
            request.setTools(null);
            request.setTool_choice(null);
            request.setParallel_tool_calls(null);
        }
    }
}
