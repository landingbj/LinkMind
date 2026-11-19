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
import ai.llm.utils.LLMErrorConstants;
import ai.llm.utils.OpenAiApiUtil;
import ai.llm.utils.convert.GptConvert;
import ai.openai.pojo.ChatCompletionChoice;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.openai.utils.MultimodalTraceUtil;
import ai.utils.AiGlobal;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;


@LLM(modelNames = {"*"})
public class OpenAIStandardAdapter extends ModelService implements ILlmAdapter {
    private static final Logger logger = LoggerFactory.getLogger(OpenAIStandardAdapter.class);
    private static final int HTTP_TIMEOUT = AiGlobal.LLM_TIME_OUT_SECONDS;
    private static final ResponseSessionManager SESSION_MANAGER = ResponseSessionManager.getInstance();
    private static final String EMPTY_STREAM_RESPONSE_MESSAGE =
            "{\"error\":\"The upstream model returned an empty response after one retry.\"}";

    @Override
    public ChatCompletionResult completions(ChatCompletionRequest chatCompletionRequest) {
        String reqApiKey = getApiKey(chatCompletionRequest);
        boolean responses = ResponseProtocolUtil.isResponseProtocol(this);
        String responseAddress = responses ? getResponsesApiAddress() : null;
        List<String> toolScope = responses ? toolScope(chatCompletionRequest, reqApiKey, responseAddress) : null;
        setDefaultField(chatCompletionRequest);
        if (responses) {
            ResponseSessionContext sessionContext = SESSION_MANAGER.prepare(chatCompletionRequest, this, toolScope);
            LlmApiResponse response = OpenAiResponsesApiUtil.createResponse(reqApiKey, responseAddress, HTTP_TIMEOUT,
                    ResponsesChatCompletionConverter.toRequest(chatCompletionRequest, sessionContext, chatCompletionRequest.getModel()),
                    GptConvert::convertByResponse, defaultHeaders(reqApiKey));
            if(response.getCode() != 200) {
                logger.error("openai responses api : code{}  error  {}", response.getCode(), response.getMsg());
                throw responseFailure(response, sessionContext);
            }
            SESSION_MANAGER.onSuccess(sessionContext,
                    response.getData() == null ? null : response.getData().getId(),
                    extractAssistantMessage(response.getData()), toolScope);
            return response.getData();
        }
        MultimodalTraceUtil.logRequest("http-dispatch", chatCompletionRequest, getApiAddress());
        LlmApiResponse completions = OpenAiApiUtil.completions(reqApiKey, getApiAddress(), HTTP_TIMEOUT, chatCompletionRequest,
                GptConvert::convert2ChatCompletionResult, GptConvert::convertByResponse);
        MultimodalTraceUtil.logResult("http-result", chatCompletionRequest, getApiAddress(), completions.getCode());
        if(completions.getCode() != 200) {
            logger.error("openai api : code{}  error  {}", completions.getCode(), completions.getMsg());
            throw new RRException(completions.getCode(), completions.getMsg());
        }
        return completions.getData();
    }



    @Override
    public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest chatCompletionRequest) {
        String reqApiKey = getApiKey(chatCompletionRequest);
        boolean responses = ResponseProtocolUtil.isResponseProtocol(this);
        String responseAddress = responses ? getResponsesApiAddress() : null;
        List<String> toolScope = responses ? toolScope(chatCompletionRequest, reqApiKey, responseAddress) : null;
        setDefaultField(chatCompletionRequest);
        if (responses) {
            ResponseSessionContext sessionContext = SESSION_MANAGER.prepare(chatCompletionRequest, this, toolScope);
            LlmApiResponse response = OpenAiResponsesApiUtil.streamResponse(reqApiKey, responseAddress, HTTP_TIMEOUT,
                    ResponsesChatCompletionConverter.toRequest(chatCompletionRequest, sessionContext, chatCompletionRequest.getModel()),
                    GptConvert::convertByResponse, defaultHeaders(reqApiKey));
            if(response.getCode() != 200) {
                logger.error("openai responses stream api : code{}  error  {}", response.getCode(), response.getMsg());
                throw responseFailure(response, sessionContext);
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
                    .doOnComplete(() -> SESSION_MANAGER.onSuccess(
                            sessionContext, responseId.get(), assistantMessage.get(), toolScope));
        }
        Observable<ChatCompletionResult> firstAttempt = bufferUntilMeaningfulOutput(
                openStandardStream(reqApiKey, chatCompletionRequest));
        return firstAttempt.switchIfEmpty(Observable.defer(() -> {
            logger.warn("OpenAI-compatible model returned an empty stream; retrying once. model={}",
                    chatCompletionRequest.getModel());
            try {
                return bufferUntilMeaningfulOutput(openStandardStream(reqApiKey, chatCompletionRequest))
                        .switchIfEmpty(Observable.just(LLMErrorConstants.errorResponse(chatCompletionRequest,
                                LLMErrorConstants.OTHER_ERROR, EMPTY_STREAM_RESPONSE_MESSAGE)));
            } catch (RRException e) {
                return Observable.just(LLMErrorConstants.errorResponse(chatCompletionRequest,
                        e.getCode(), e.getMsg()));
            }
        }));
    }

    private Observable<ChatCompletionResult> openStandardStream(String apiKey, ChatCompletionRequest request) {
        MultimodalTraceUtil.logRequest("http-dispatch", request, getApiAddress());
        LlmApiResponse completions = OpenAiApiUtil.streamCompletions(apiKey, getApiAddress(), HTTP_TIMEOUT, request,
                GptConvert::convertSteamLine2ChatCompletionResult, GptConvert::convertByResponse);
        MultimodalTraceUtil.logResult("http-result", request, getApiAddress(), completions.getCode());
        if(completions.getCode() != 200) {
            logger.error("openai stream api : code{} error {}", completions.getCode(), completions.getMsg());
            throw new RRException(completions.getCode(), completions.getMsg());
        }
        return completions.getStreamData();
    }

    /**
     * Some OpenAI-compatible upstreams end a successful SSE request with only
     * empty deltas and usage. Delay forwarding until a real model output is
     * seen, so that a completely empty attempt can be safely retried.
     */
    private Observable<ChatCompletionResult> bufferUntilMeaningfulOutput(Observable<ChatCompletionResult> source) {
        return Observable.create(emitter -> {
            List<ChatCompletionResult> buffered = new ArrayList<>();
            AtomicReference<Boolean> hasMeaningfulOutput = new AtomicReference<>(false);
            Disposable subscription = source.subscribe(chunk -> {
                if (Boolean.TRUE.equals(hasMeaningfulOutput.get())) {
                    if (!emitter.isDisposed()) {
                        emitter.onNext(chunk);
                    }
                    return;
                }

                buffered.add(chunk);
                if (!hasMeaningfulOutput(chunk)) {
                    return;
                }

                hasMeaningfulOutput.set(true);
                if (!emitter.isDisposed()) {
                    for (ChatCompletionResult bufferedChunk : buffered) {
                        emitter.onNext(bufferedChunk);
                    }
                }
                buffered.clear();
            }, emitter::onError, () -> {
                buffered.clear();
                if (!emitter.isDisposed()) {
                    emitter.onComplete();
                }
            });
            emitter.setDisposable(subscription);
        });
    }

    private boolean hasMeaningfulOutput(ChatCompletionResult result) {
        if (result == null || result.getChoices() == null) {
            return false;
        }
        for (ChatCompletionChoice choice : result.getChoices()) {
            if (choice == null) {
                continue;
            }
            if (hasMeaningfulMessage(choice.getDelta()) || hasMeaningfulMessage(choice.getMessage())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMeaningfulMessage(ChatMessage message) {
        return message != null && (StrUtil.isNotBlank(message.getContent())
                || StrUtil.isNotBlank(message.getReasoning_content())
                || (message.getTool_calls() != null && !message.getTool_calls().isEmpty()));
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

    private Map<String, String> defaultHeaders(String apiKey) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + apiKey);
        return headers;
    }

    private List<String> toolScope(ChatCompletionRequest request, String upstreamKey, String responseAddress) {
        String callerKey = StrUtil.blankToDefault(request.getApiKey(), request.getUserApiKey());
        return Arrays.asList(responseAddress, request.getAccountUserId(),
                StrUtil.isBlank(callerKey) ? null : DigestUtil.sha256Hex(callerKey),
                StrUtil.isBlank(upstreamKey) ? null : DigestUtil.sha256Hex(upstreamKey));
    }

    private RRException responseFailure(LlmApiResponse response, ResponseSessionContext context) {
        String error = response.getMsg();
        if (error != null && !context.isStateful()
                && context.getNormalizedMessages().stream().anyMatch(message -> "tool".equals(message.getRole()))) {
            String description = error.toLowerCase(Locale.ROOT);
            if (description.contains("no tool output found for function call")
                    || description.contains("no tool call found for function call output")
                    || description.contains("required 'reasoning' item")
                    || description.contains("required reasoning item")) {
                return ResponseProtocolUtil.invalidRequest(
                        "The upstream could not restore the tool-call history. Please repeat the previous turn.");
            }
        }
        return new RRException(response.getCode(), error);
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
        ChatCompletionChoice choice = chunk.getChoices().get(0);
        ChatMessage message = choice.getDelta() != null ? choice.getDelta() : choice.getMessage();
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
        if ("tool_calls".equals(choice.getFinish_reason()) && message.getTool_calls() != null) {
            aggregate.setTool_calls(message.getTool_calls());
        }
    }

    @Override
    protected void setDefaultField(ChatCompletionRequest request) {
        super.setDefaultField(request);
        if(request.getModel().startsWith("gpt")) {
            ResponsesChatCompletionConverter.fixupFunctionCallId(request);
        }
        if(Boolean.TRUE.equals(request.getStream())) {
            Map<String, Object> streamOptions = request.getStream_options();
            if(streamOptions != null) {
                streamOptions.put("include_usage", true);
            } else {
                streamOptions = new HashMap<>();
                streamOptions.put("include_usage", true);
            }
            request.setStream_options(streamOptions);
        }
    }

}
