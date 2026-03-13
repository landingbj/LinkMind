package ai.llm.adapter.impl;


import ai.annotation.LLM;
import ai.common.ModelService;
import ai.common.exception.RRException;
import ai.llm.pojo.LlmApiResponse;
import ai.llm.responses.OpenAiResponsesApiUtil;
import ai.llm.responses.ResponseProtocolUtil;
import ai.llm.responses.ResponseSessionContext;
import ai.llm.responses.ResponseSessionManager;
import ai.llm.responses.ResponsesChatCompletionConverter;
import ai.llm.utils.OpenAiApiUtil;
import ai.llm.utils.convert.GptConvert;

import ai.llm.adapter.ILlmAdapter;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@LLM(modelNames = {"gpt-3.5-turbo","gpt-4-1106-preview"})
public class GPTAdapter extends ModelService implements ILlmAdapter {
    private static final Logger logger = LoggerFactory.getLogger(GPTAdapter.class);
    private static final String COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";
    private static final String RESPONSES_URL = "https://api.openai.com/v1/responses";
    private static final int HTTP_TIMEOUT = 15 * 1000;
    private static final ResponseSessionManager SESSION_MANAGER = ResponseSessionManager.getInstance();

    @Override
    public ChatCompletionResult completions(ChatCompletionRequest chatCompletionRequest) {
        setDefaultField(chatCompletionRequest);
        if (ResponseProtocolUtil.isResponseProtocol(this)) {
            ResponseSessionContext sessionContext = SESSION_MANAGER.prepare(chatCompletionRequest, this);
            LlmApiResponse response = OpenAiResponsesApiUtil.createResponse(apiKey, RESPONSES_URL, HTTP_TIMEOUT,
                    ResponsesChatCompletionConverter.toRequest(chatCompletionRequest, sessionContext, chatCompletionRequest.getModel()),
                    GptConvert::convertByResponse, defaultHeaders());
            if(response.getCode() != 200) {
                logger.error(response.getMsg());
                throw new RRException(response.getCode(), response.getMsg());
            }
            SESSION_MANAGER.onSuccess(sessionContext, response.getData() == null ? null : response.getData().getId());
            return response.getData();
        }
        LlmApiResponse completions = OpenAiApiUtil.completions(apiKey, COMPLETIONS_URL, HTTP_TIMEOUT, chatCompletionRequest,
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
        setDefaultField(chatCompletionRequest);
        if (ResponseProtocolUtil.isResponseProtocol(this)) {
            ResponseSessionContext sessionContext = SESSION_MANAGER.prepare(chatCompletionRequest, this);
            LlmApiResponse response = OpenAiResponsesApiUtil.streamResponse(apiKey, RESPONSES_URL, HTTP_TIMEOUT,
                    ResponsesChatCompletionConverter.toRequest(chatCompletionRequest, sessionContext, chatCompletionRequest.getModel()),
                    GptConvert::convertByResponse, defaultHeaders());
            if(response.getCode() != 200) {
                logger.error("open ai responses stream api error {}", response.getMsg());
                throw new RRException(response.getCode(), response.getMsg());
            }
            AtomicReference<String> responseId = new AtomicReference<>();
            return response.getStreamData()
                    .doOnNext(chunk -> {
                        if (chunk != null && chunk.getId() != null) {
                            responseId.set(chunk.getId());
                        }
                    })
                    .doOnComplete(() -> SESSION_MANAGER.onSuccess(sessionContext, responseId.get()));
        }
        LlmApiResponse completions = OpenAiApiUtil.streamCompletions(apiKey, COMPLETIONS_URL, HTTP_TIMEOUT, chatCompletionRequest,
                GptConvert::convertSteamLine2ChatCompletionResult,
                GptConvert::convertByResponse);
        if(completions.getCode() != 200) {
            logger.error("open ai stream api error {}", completions.getMsg());
            throw new RRException(completions.getCode(), completions.getMsg());
        }
        return completions.getStreamData();
    }

    private Map<String, String> defaultHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + apiKey);
        return headers;
    }
}
