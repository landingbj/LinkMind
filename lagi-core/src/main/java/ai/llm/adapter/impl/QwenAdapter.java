package ai.llm.adapter.impl;

import ai.annotation.LLM;
import ai.common.ModelService;
import ai.common.exception.RRException;
import ai.llm.adapter.ILlmAdapter;
import ai.common.utils.MappingIterable;
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
import com.alibaba.dashscope.tools.ToolCallFunction;
import com.alibaba.dashscope.tools.ToolFunction;
import com.google.gson.Gson;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@LLM(modelNames = {"qwen-turbo","qwen-plus","qwen-max","qwen-max-1201","qwen-max-longcontext"})
public class QwenAdapter extends ModelService implements ILlmAdapter {


    @Override
    public ChatCompletionResult completions(ChatCompletionRequest chatCompletionRequest) {
        // 检查请求参数是否为空或无效
        if (chatCompletionRequest == null || chatCompletionRequest.getMessages() == null || chatCompletionRequest.getMessages().isEmpty()) {
            log.error("聊天补全请求参数为空或消息列表为空");
            throw new RRException(400, "QWEN_API_INVALID_PARAM: 请求参数为空或消息列表为空，请检查输入内容");
        }
        List<ChatMessage> messages = chatCompletionRequest.getMessages();
        List<ChatMessage> filteredMessages = messages.stream()
                .filter(message -> message != null && message.getContent() != null)
                .collect(Collectors.toList());

        chatCompletionRequest.setMessages(filteredMessages);
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
                    ToolCallFunction toolCallFunction = new ToolCallFunction();
//                    com.alibaba.dashscope.tools.ToolCallFunction build = com.alibaba.dashscope.tools.ToolCallFunction.builder().build();
                    BeanUtil.copyProperties(toolCall, toolCallFunction);
                    return toolCallFunction;
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

}
