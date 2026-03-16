package ai.llm.adapter.impl;

import ai.annotation.LLM;
import ai.common.ModelService;
import ai.common.exception.RRException;
import ai.llm.adapter.ILlmAdapter;
import ai.common.utils.MappingIterable;
import ai.llm.pojo.LlmApiResponse;
import ai.llm.responses.OpenAiResponsesApiUtil;
import ai.llm.responses.QwenResponseProtocolUtil;
import ai.llm.responses.QwenResponsesChatCompletionConverter;
import ai.llm.responses.ResponseProtocolUtil;
import ai.llm.responses.ResponseSessionContext;
import ai.llm.responses.ResponseSessionManager;
import ai.llm.utils.convert.QwenConvert;
import ai.openai.pojo.*;
import ai.utils.qa.ChatCompletionUtil;
import cn.hutool.core.bean.BeanUtil;
import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.tools.ToolBase;
import com.alibaba.dashscope.tools.ToolCallBase;
import com.alibaba.dashscope.tools.ToolFunction;
import com.google.gson.Gson;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@LLM(modelNames = {"qwen-turbo","qwen-plus","qwen-max","qwen-max-1201","qwen-max-longcontext",
        "qwen-flash","qwen3.5-plus","qwen3.5-flash","qwen3-max","qwen3-coder-plus","qwen3-coder-flash"})
public class QwenAdapter extends ModelService implements ILlmAdapter {
    private static final int HTTP_TIMEOUT = 30;
    private static final ResponseSessionManager SESSION_MANAGER = ResponseSessionManager.getInstance();


    @Override
    public ChatCompletionResult completions(ChatCompletionRequest chatCompletionRequest) {
        setDefaultField(chatCompletionRequest);
        if (ResponseProtocolUtil.isResponseProtocol(this)) {
            ResponseSessionContext sessionContext = SESSION_MANAGER.prepare(chatCompletionRequest, this);
            LlmApiResponse response = OpenAiResponsesApiUtil.createResponse(getApiKey(), getResponsesApiAddress(), HTTP_TIMEOUT,
                    QwenResponsesChatCompletionConverter.toRequest(chatCompletionRequest, sessionContext,
                            Optional.ofNullable(chatCompletionRequest.getModel()).orElse(getModel())),
                    QwenConvert::convertByHttpResponse, defaultHeaders());
            if(response.getCode() != 200) {
                log.error("qwen responses api error {}", response.getMsg());
                throw new RRException(response.getCode(), response.getMsg());
            }
            SESSION_MANAGER.onSuccess(sessionContext,
                    response.getData() == null ? null : response.getData().getId(),
                    extractAssistantMessage(response.getData()));
            return response.getData();
        }
        Generation gen = new Generation();
        GenerationParam param = convertRequest(chatCompletionRequest);
        GenerationResult result;
        try {
            result = gen.call(param);
        } catch (Exception e) {
            RRException exception = QwenConvert.convert2RRexception(e);
            log.error("qwen  api code {} error {}", exception.getCode(), exception.getMsg());
            throw new RRException(exception.getCode(), exception.getMsg());
        }
        return convertResponse(result);
    }

    @Override
    public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest chatCompletionRequest) {
        setDefaultField(chatCompletionRequest);
        if (ResponseProtocolUtil.isResponseProtocol(this)) {
            ResponseSessionContext sessionContext = SESSION_MANAGER.prepare(chatCompletionRequest, this);
            LlmApiResponse response = OpenAiResponsesApiUtil.streamResponse(getApiKey(), getResponsesApiAddress(), HTTP_TIMEOUT,
                    QwenResponsesChatCompletionConverter.toRequest(chatCompletionRequest, sessionContext,
                            Optional.ofNullable(chatCompletionRequest.getModel()).orElse(getModel())),
                    QwenConvert::convertByHttpResponse, defaultHeaders());
            if(response.getCode() != 200) {
                log.error("qwen responses stream api error {}", response.getMsg());
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
        Generation gen = new Generation();
        GenerationParam param = convertRequest(chatCompletionRequest);
        Flowable<GenerationResult> result = null;
        try {
            result = gen.streamCall(param);
        } catch (NoApiKeyException | InputRequiredException e) {
            throw new RuntimeException(e);
        }
        Iterable<GenerationResult> resultIterable = result.blockingIterable();
        try {
            boolean b = resultIterable.iterator().hasNext();
        } catch (ApiException e) {
            RRException exception = QwenConvert.convert2RRexception(e);
            log.error("qwen  stream  api code {} error {}", exception.getCode(), exception.getMsg());
            throw exception;
        }
        Iterable<ChatCompletionResult> iterable = new MappingIterable<>(resultIterable, this::convertResponse);
        return Observable.fromIterable(iterable);
    }

    private GenerationParam convertRequest(ChatCompletionRequest request) {
        List<Message> messages = new ArrayList<>();
        for (ChatMessage chatMessage : request.getMessages()) {
            List<ToolCall> toolCalls = chatMessage.getTool_calls();
            List<ToolCallBase> collect = null;
            if(toolCalls != null) {
                collect = toolCalls.stream().map(toolCall -> {
                    com.alibaba.dashscope.tools.ToolCallFunction build = com.alibaba.dashscope.tools.ToolCallFunction.builder().build();
                    BeanUtil.copyProperties(toolCall, build);
                    return build;
                }).collect(Collectors.toList());
            }
            Message msg = Message.builder()
                    .role(chatMessage.getRole())
                    .content(chatMessage.getContent())
                    .toolCallId(chatMessage.getTool_call_id())
                    .toolCalls(collect)
                    .build();
            messages.add(msg);
        }

        boolean stream = Optional.ofNullable(request.getStream()).orElse(false);
        String model = Optional.ofNullable(request.getModel()).orElse(getModel());

        int maxTokens = request.getMax_tokens();
        if (request.getMax_tokens() >= 2000) {
            maxTokens = 2000;
        }

        List<Tool> tools = request.getTools();
        List<ToolBase> toolFunctions = null;
        if(tools != null) {
            toolFunctions = tools.stream().map(tool -> {
                String json = new Gson().toJson(tool);
                return new Gson().fromJson(json, ToolFunction.class);
            }).collect(Collectors.toList());
        }
        return GenerationParam.builder()
                .apiKey(getApiKey())
                .model(model)
                .messages(messages)
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .maxTokens(maxTokens)
                .temperature((float) request.getTemperature())
                .enableSearch(stream)
                .incrementalOutput(stream)
                .tools(toolFunctions)
                .build();
    }

    private ChatCompletionResult convertResponse(GenerationResult response) {
        ChatCompletionResult result = new ChatCompletionResult();
        result.setId(response.getRequestId());
        result.setCreated(ChatCompletionUtil.getCurrentUnixTimestamp());
        ChatCompletionChoice choice = new ChatCompletionChoice();
        choice.setIndex(0);
        ChatMessage chatMessage = new ChatMessage();
        Message message = response.getOutput().getChoices().get(0).getMessage();
        chatMessage.setContent(message.getContent());
        chatMessage.setRole("assistant");
        List<ToolCallBase> toolCalls = message.getToolCalls();
        List<ToolCall> toolCallList = new ArrayList<>();
        if(toolCalls != null) {
            toolCallList = toolCalls.stream().map(toolCall -> {
                String json = new Gson().toJson(toolCall);
                return new Gson().fromJson(json, ToolCall.class);
            }).collect(Collectors.toList());
        }
        chatMessage.setTool_calls(toolCallList);
        choice.setMessage(chatMessage);
        choice.setFinish_reason(response.getOutput().getFinishReason());
        List<ChatCompletionChoice> choices = new ArrayList<>();
        choices.add(choice);
        result.setChoices(choices);
        return result;
    }

    private String getResponsesApiAddress() {
        return QwenResponseProtocolUtil.resolveResponsesApiAddress(getApiAddress());
    }

    private Map<String, String> defaultHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + getApiKey());
        return headers;
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
