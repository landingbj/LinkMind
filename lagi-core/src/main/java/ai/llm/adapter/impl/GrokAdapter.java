package ai.llm.adapter.impl;


import ai.annotation.LLM;
import ai.common.ModelService;
import ai.common.exception.RRException;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.pojo.LlmApiResponse;
import ai.llm.responses.*;
import ai.llm.utils.OpenAiApiUtil;
import ai.llm.utils.convert.GptConvert;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@LLM(modelNames = {"grok-3","grok-4-0709"})
public class GrokAdapter extends ModelService implements ILlmAdapter {
    private static final Logger logger = LoggerFactory.getLogger(GrokAdapter.class);
    private static final String COMPLETIONS_URL = "https://api.x.ai/v1/chat/completions";
    private static final String RESPONSES_URL = "https://api.x.ai/v1/responses";
    private static final int HTTP_TIMEOUT = 15 * 1000;
    private static final ResponseSessionManager SESSION_MANAGER = ResponseSessionManager.getInstance();

    @Override
    public ChatCompletionResult completions(ChatCompletionRequest chatCompletionRequest) {
        String reqApiKey = getApiKey(chatCompletionRequest);
        setDefaultField(chatCompletionRequest);
        if (ResponseProtocolUtil.isResponseProtocol(this)) {
            ResponseSessionContext sessionContext = SESSION_MANAGER.prepare(chatCompletionRequest, this);
            LlmApiResponse response = OpenAiResponsesApiUtil.createResponse(reqApiKey, RESPONSES_URL, HTTP_TIMEOUT,
                    ResponsesChatCompletionConverter.toRequest(chatCompletionRequest, sessionContext, chatCompletionRequest.getModel()),
                    GptConvert::convertByResponse, defaultHeaders(reqApiKey));
            if(response.getCode() != 200) {
                logger.error(response.getMsg());
                throw new RRException(response.getCode(), response.getMsg());
            }
            SESSION_MANAGER.onSuccess(sessionContext,
                    response.getData() == null ? null : response.getData().getId(),
                    extractAssistantMessage(response.getData()));
            return response.getData();
        }
        LlmApiResponse completions = OpenAiApiUtil.completions(reqApiKey, COMPLETIONS_URL, HTTP_TIMEOUT, chatCompletionRequest,
                GptConvert::convert2ChatCompletionResult,
                GptConvert::convertByResponse);
        if(completions.getCode() != 200) {
            logger.error(completions.getMsg());
            throw new RRException(completions.getCode(), completions.getMsg());
        }
        return completions.getData();
    }

    @Override
    public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest chatCompletionRequest) {
        String reqApiKey = getApiKey(chatCompletionRequest);
        setDefaultField(chatCompletionRequest);
        if (ResponseProtocolUtil.isResponseProtocol(this)) {
            ResponseSessionContext sessionContext = SESSION_MANAGER.prepare(chatCompletionRequest, this);
            LlmApiResponse response = OpenAiResponsesApiUtil.streamResponse(reqApiKey, RESPONSES_URL, HTTP_TIMEOUT,
                    ResponsesChatCompletionConverter.toRequest(chatCompletionRequest, sessionContext, chatCompletionRequest.getModel()),
                    GptConvert::convertByResponse, defaultHeaders(reqApiKey));
            if(response.getCode() != 200) {
                logger.error("open ai responses stream api error {}", response.getMsg());
                throw new RRException(response.getCode(), response.getMsg());
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
        LlmApiResponse completions = OpenAiApiUtil.streamCompletions(reqApiKey, COMPLETIONS_URL, HTTP_TIMEOUT, chatCompletionRequest,
                GptConvert::convertSteamLine2ChatCompletionResult,
                GptConvert::convertByResponse);
        if(completions.getCode() != 200) {
            logger.error("open ai stream api error {}", completions.getMsg());
            throw new RRException(completions.getCode(), completions.getMsg());
        }
        return completions.getStreamData();
    }

    private Map<String, String> defaultHeaders(String apiKey) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + apiKey);
        return headers;
    }


    @Override
    protected void  setDefaultField(ChatCompletionRequest request) {
        super.setDefaultField(request);
        Integer maxTokens = request.getMax_tokens();
        if(maxTokens != null && request.getMax_completion_tokens() ==  null) {
            request.setMax_completion_tokens(maxTokens);
        }
        request.setPresence_penalty(null);
        request.setFrequency_penalty(null);
        if(request.getModel() != null && !request.getModel().startsWith("grok-3")) {
            request.setResponse_format(null);
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
