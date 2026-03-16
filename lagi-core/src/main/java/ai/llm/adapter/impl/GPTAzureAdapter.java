package ai.llm.adapter.impl;

import ai.annotation.LLM;
import ai.common.ModelService;
import ai.common.exception.RRException;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.pojo.LlmApiResponse;
import ai.llm.responses.OpenAiResponsesApiUtil;
import ai.llm.responses.ResponseProtocolUtil;
import ai.llm.responses.ResponseSessionContext;
import ai.llm.responses.ResponseSessionManager;
import ai.llm.responses.ResponsesChatCompletionConverter;
import ai.llm.utils.OpenAiApiUtil;
import ai.llm.utils.convert.GptAzureConvert;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@LLM(modelNames = {"gpt-3.5-turbo","gpt-4-1106-preview","gpt-4o-20240513"})
public class GPTAzureAdapter extends ModelService implements ILlmAdapter {
    private static final Logger logger = LoggerFactory.getLogger(GPTAzureAdapter.class);
    private static final int HTTP_TIMEOUT = 30 * 1000;
    private static final ResponseSessionManager SESSION_MANAGER = ResponseSessionManager.getInstance();
    private final ObjectMapper mapper;

    public GPTAzureAdapter() {
        this.mapper = new ObjectMapper();
        this.mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @Override
    public String getApiAddress() {
        return getEndpoint() + "openai/deployments/" + getDeployment() + "/chat/completions?api-version=" + getApiVersion();
    }

    @Override
    public ChatCompletionResult completions(ChatCompletionRequest chatCompletionRequest) {
        setDefaultField(chatCompletionRequest);
        chatCompletionRequest.setMax_completion_tokens(chatCompletionRequest.getMax_tokens());
        chatCompletionRequest.setMax_tokens(null);
        if (ResponseProtocolUtil.isResponseProtocol(this)) {
            ResponseSessionContext sessionContext = SESSION_MANAGER.prepare(chatCompletionRequest, this);
            String json = toJson(ResponsesChatCompletionConverter.toRequest(chatCompletionRequest, sessionContext, getDeployment()));
            Proxy proxy = new Proxy(Proxy.Type.SOCKS, GptAzureConvert.convertProxyUrl2InetSocketAddress());
            LlmApiResponse response = OpenAiResponsesApiUtil.createResponse(getApiKey(), getResponsesApiAddress(), HTTP_TIMEOUT, json,
                    GptAzureConvert::convertByResponse, responseHeaders(), null);
            if(response.getCode() != 200) {
                logger.error("open ai azure responses api error {}", response.getMsg());
                throw new RRException(response.getCode(), response.getMsg());
            }
            SESSION_MANAGER.onSuccess(sessionContext,
                    response.getData() == null ? null : response.getData().getId(),
                    extractAssistantMessage(response.getData()));
            return response.getData();
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("api-key", getApiKey());

        String json = toJson(chatCompletionRequest);
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, GptAzureConvert.convertProxyUrl2InetSocketAddress());
        LlmApiResponse completions = OpenAiApiUtil.completions(getApiKey(), getApiAddress(), HTTP_TIMEOUT, json,
                GptAzureConvert::convert2ChatCompletionResult, GptAzureConvert::convertByResponse,
                headers,proxy);
        if(completions.getCode() != 200) {
            logger.error("open ai  api error {}", completions.getMsg());
            throw new RRException(completions.getCode(), completions.getMsg());
        }
        return completions.getData();
    }

    @Override
    public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest chatCompletionRequest) {
        setDefaultField(chatCompletionRequest);
        if (ResponseProtocolUtil.isResponseProtocol(this)) {
            ResponseSessionContext sessionContext = SESSION_MANAGER.prepare(chatCompletionRequest, this);
            Proxy proxy = new Proxy(Proxy.Type.SOCKS, GptAzureConvert.convertProxyUrl2InetSocketAddress());
            String json = toJson(ResponsesChatCompletionConverter.toRequest(chatCompletionRequest, sessionContext, getDeployment()));
            LlmApiResponse response = OpenAiResponsesApiUtil.streamResponse(getApiKey(), getResponsesApiAddress(), HTTP_TIMEOUT, json,
                    GptAzureConvert::convertByResponse, responseHeaders(), proxy);
            Integer code = response.getCode();
            if(code != null && code != 200) {
                logger.error("open ai azure responses stream api error {}", response.getMsg());
                throw new RRException(code, response.getMsg());
            }
            AtomicReference<String> responseId = new AtomicReference<>();
            AtomicReference<ChatMessage> assistantMessage = new AtomicReference<>(ChatMessage.builder().role("assistant").content("").build());
            return response.getStreamData()
                    .doOnNext(chunk -> {
                        if (chunk != null && chunk.getId() != null) {
                            responseId.set(chunk.getId());
                        }
                        mergeAssistantMessage(assistantMessage.get(), chunk);
                    })
                    .doOnComplete(() -> SESSION_MANAGER.onSuccess(sessionContext, responseId.get(), assistantMessage.get()));
        }
        String apiUrl = getApiAddress();
        String apiKey = getApiKey();
        Map<String, String> headers = new HashMap<>();
        headers.put("api-key", apiKey);
        Map incloudUsage = new HashMap<>();
        incloudUsage.put("include_usage", true);
        chatCompletionRequest.setStream_options(incloudUsage);
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, GptAzureConvert.convertProxyUrl2InetSocketAddress());
        String json = toJson(chatCompletionRequest);
        LlmApiResponse llmApiResponse = OpenAiApiUtil.streamCompletions(apiKey, apiUrl, HTTP_TIMEOUT, json,
                GptAzureConvert::convertStreamLine2ChatCompletionResult, GptAzureConvert::convertByResponse, headers, proxy);
        Integer code = llmApiResponse.getCode();
        if(code != null && code != 200) {
            logger.error("open ai stream api error {}", llmApiResponse.getMsg());
            throw new RRException(code, llmApiResponse.getMsg());
        }
        return llmApiResponse.getStreamData();
    }

    private String getResponsesApiAddress() {
        if (StrUtil.isBlank(getEndpoint())) {
            throw ResponseProtocolUtil.invalidRequest("endpoint is required for azure response protocol");
        }
        if (StrUtil.isBlank(getDeployment())) {
            throw ResponseProtocolUtil.invalidRequest("deployment is required for azure response protocol");
        }
        String endpoint = getEndpoint().endsWith("/") ? getEndpoint() : getEndpoint() + "/";
        return endpoint + "openai/v1/responses";
    }

    private Map<String, String> responseHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("api-key", getApiKey());
        return headers;
    }

    private String toJson(Object payload) {
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private ChatMessage extractAssistantMessage(ChatCompletionResult result) {
        if (result == null || result.getChoices() == null || result.getChoices().isEmpty()) {
            return null;
        }
        return result.getChoices().get(0).getMessage();
    }

    private void mergeAssistantMessage(ChatMessage aggregate, ChatCompletionResult chunk) {
        if (aggregate == null || chunk == null || chunk.getChoices() == null || chunk.getChoices().isEmpty()) {
            return;
        }
        ChatMessage message = chunk.getChoices().get(0).getMessage();
        if (message == null) {
            return;
        }
        if (message.getRole() != null) {
            aggregate.setRole(message.getRole());
        }
        if (message.getContent() != null) {
            aggregate.setContent((aggregate.getContent() == null ? "" : aggregate.getContent()) + message.getContent());
        }
        if (message.getReasoning_content() != null) {
            aggregate.setReasoning_content(message.getReasoning_content());
        }
        if (message.getTool_calls() != null && !message.getTool_calls().isEmpty()) {
            aggregate.setTool_calls(message.getTool_calls());
        }
    }
}
