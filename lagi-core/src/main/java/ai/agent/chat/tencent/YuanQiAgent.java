package ai.agent.chat.tencent;

import ai.agent.chat.BaseChatAgent;
import ai.agent.chat.tencent.pojo.CompletionRequest;
import ai.agent.chat.tencent.pojo.CompletionResponse;
import ai.agent.chat.tencent.pojo.Content;
import ai.agent.chat.tencent.pojo.Messages;
import ai.common.exception.RRException;
import ai.config.pojo.AgentConfig;
import ai.llm.utils.LLMErrorConstants;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.utils.ApiInvokeUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class YuanQiAgent extends BaseChatAgent {

    // Reference document： https://docs.qq.com/aio/p/scxmsn78nzsuj64?p=unUU8C3HBocfQSOGAh2BYuC

    private final Gson gson = new Gson();

    public YuanQiAgent(AgentConfig agentConfig) {
        super(agentConfig);
    }


    private CompletionRequest convertRequest(ChatCompletionRequest request) {
        if(request == null) {
            throw new RRException(LLMErrorConstants.RESOURCE_NOT_FOUND_ERROR, "request is null");
        }
        if(request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new RRException(LLMErrorConstants.RESOURCE_NOT_FOUND_ERROR, "request messages is empty");
        }
        List<Messages> messagesList = request.getMessages().stream().map(m -> {
            Messages messages = new Messages();
            messages.setRole(m.getRole());
            messages.setContent(Lists.newArrayList(Content.builder().text(m.getContent()).type("text").build()));
            return messages;
        }).collect(Collectors.toList());
        return CompletionRequest.builder()
                .assistant_id(agentConfig.getAppId())
                .stream(request.getStream())
                .userId(agentConfig.getUserId())
                .messages(messagesList).build();
    }

    private ChatCompletionResult  convertResponse(CompletionResponse response) {
        if(response == null) {
            return null;
        }
        ChatCompletionResult chatCompletionResult = new ChatCompletionResult();
        BeanUtil.copyProperties(response, chatCompletionResult);
        return chatCompletionResult;
    }

    @Override
    public ChatCompletionResult communicate(ChatCompletionRequest data) {
        CompletionRequest completionRequest = convertRequest(data);
        String json = gson.toJson(completionRequest);
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Source", "openapi");
        headers.put("Authorization", StrUtil.format("Bearer {}", StrUtil.isBlank(agentConfig.getApiKey())? agentConfig.getToken() : agentConfig.getApiKey()));
        String post = ApiInvokeUtil.post("https://open.hunyuan.tencent.com/openapi/v1/agent/chat/completions", headers, json, 180, TimeUnit.SECONDS);
        log.info("yuan qi response: " + post);
        CompletionResponse completionResponse = gson.fromJson(post, CompletionResponse.class);
        return convertResponse(completionResponse);
    }
}
