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
import ai.llm.utils.convert.GptConvert;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import cn.hutool.core.util.StrUtil;
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;


@LLM(modelNames = {"*"})
public class OpenAIStandardAdapter extends ModelService implements ILlmAdapter {
    private static final Logger logger = LoggerFactory.getLogger(OpenAIStandardAdapter.class);
    private static final int HTTP_TIMEOUT = 30 * 1000;
    private static final ResponseSessionManager SESSION_MANAGER = ResponseSessionManager.getInstance();

    @Override
    public ChatCompletionResult completions(ChatCompletionRequest chatCompletionRequest) {
        setDefaultField(chatCompletionRequest);
        if (ResponseProtocolUtil.isResponseProtocol(this)) {
            ResponseSessionContext sessionContext = SESSION_MANAGER.prepare(chatCompletionRequest, this);
            LlmApiResponse response = OpenAiResponsesApiUtil.createResponse(apiKey, getResponsesApiAddress(), HTTP_TIMEOUT,
                    ResponsesChatCompletionConverter.toRequest(chatCompletionRequest, sessionContext, chatCompletionRequest.getModel()),
                    GptConvert::convertByResponse, defaultHeaders());
            if(response.getCode() != 200) {
                logger.error("openai responses api : code{}  error  {}", response.getCode(), response.getMsg());
                throw new RRException(response.getCode(), response.getMsg());
            }
            SESSION_MANAGER.onSuccess(sessionContext, response.getData() == null ? null : response.getData().getId());
            return response.getData();
        }
        LlmApiResponse completions = OpenAiApiUtil.completions(apiKey, getApiAddress(), HTTP_TIMEOUT, chatCompletionRequest,
                GptConvert::convert2ChatCompletionResult, GptConvert::convertByResponse);
        if(completions.getCode() != 200) {
            logger.error("openai api : code{}  error  {}", completions.getCode(), completions.getMsg());
            throw new RRException(completions.getCode(), completions.getMsg());
        }
        return completions.getData();
    }



    @Override
    public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest chatCompletionRequest) {
        setDefaultField(chatCompletionRequest);
        if (ResponseProtocolUtil.isResponseProtocol(this)) {
            ResponseSessionContext sessionContext = SESSION_MANAGER.prepare(chatCompletionRequest, this);
            LlmApiResponse response = OpenAiResponsesApiUtil.streamResponse(apiKey, getResponsesApiAddress(), HTTP_TIMEOUT,
                    ResponsesChatCompletionConverter.toRequest(chatCompletionRequest, sessionContext, chatCompletionRequest.getModel()),
                    GptConvert::convertByResponse, defaultHeaders());
            if(response.getCode() != 200) {
                logger.error("openai responses stream api : code{}  error  {}", response.getCode(), response.getMsg());
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
        LlmApiResponse completions = OpenAiApiUtil.streamCompletions(apiKey, getApiAddress(), HTTP_TIMEOUT, chatCompletionRequest,
                GptConvert::convertSteamLine2ChatCompletionResult, GptConvert::convertByResponse);
        if(completions.getCode() != 200) {
            logger.error("openai  stream api : code{}  error  {}", completions.getCode(), completions.getMsg());
            throw new RRException(completions.getCode(), completions.getMsg());
        }
        return completions.getStreamData();
    }

    protected String getResponsesApiAddress() {
        if (StrUtil.isBlank(getApiAddress())) {
            throw ResponseProtocolUtil.invalidRequest("api_address is required for response protocol");
        }
        if (getApiAddress().endsWith("/responses")) {
            return getApiAddress();
        }
        if (getApiAddress().endsWith("/chat/completions")) {
            return getApiAddress().substring(0, getApiAddress().length() - "/chat/completions".length()) + "/responses";
        }
        throw ResponseProtocolUtil.invalidRequest("api_address must end with /chat/completions or /responses");
    }

    private Map<String, String> defaultHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + apiKey);
        return headers;
    }
}
