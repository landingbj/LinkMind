package ai.llm.adapter.impl;

import ai.annotation.LLM;
import ai.common.ModelService;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.pojo.ClaudeCompletionRequest;
import ai.llm.pojo.ClaudeCompletionResponse;
import ai.llm.pojo.LlmApiResponse;
import ai.llm.utils.OpenAiApiUtil;
import ai.llm.utils.convert.ClaudeConvert;
import ai.openai.pojo.*;
import ai.utils.LagiGlobal;
import ai.utils.OkHttpUtil;
import ai.utils.qa.ChatCompletionUtil;
import com.google.gson.Gson;
import io.reactivex.Observable;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

@Slf4j
@LLM(modelNames = {"claude-3-5-haiku-latest", "claude-3-5-haiku-20241022", "claude-3-5-sonnet-latest",
        "claude-3-5-sonnet-20241022", "claude-3-5-sonnet-20240620", "claude-3-opus-latest",
        "claude-3-opus-20240229", "claude-3-sonnet-20240229", "claude-3-haiku-20240307", "claude-2.1", "claude-2.0"})
public class ClaudeAdapter extends ModelService implements ILlmAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ClaudeAdapter.class);
    private static final String BASE_URL = "https://api.anthropic.com";
    private static final Gson gson = new Gson();

    @Override
    public ChatCompletionResult completions(ChatCompletionRequest chatCompletionRequest) {
        String reqApiKey = getApiKey(chatCompletionRequest);
        setDefaultField(chatCompletionRequest);
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("x-api-key", reqApiKey);
        headers.put("anthropic-version", "2023-06-01");
        String jsonResult;
        try {
            jsonResult = OkHttpUtil.post(BASE_URL + "/v1/messages", headers, new HashMap<>(), gson.toJson(convertRequest(chatCompletionRequest)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return convertResponse(gson.fromJson(jsonResult, ClaudeCompletionResponse.class));
    }

    @Override
    public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest chatCompletionRequest) {
        String reqApiKey = getApiKey(chatCompletionRequest);
        setDefaultField(chatCompletionRequest);
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("x-api-key", reqApiKey);
        headers.put("anthropic-version", "2023-06-01");
        LlmApiResponse llmStreamApiResponse = OpenAiApiUtil.streamCompletions(
                reqApiKey,
                BASE_URL + "/v1/messages",
                30,
                gson.toJson(convertRequest(chatCompletionRequest)),
                ClaudeConvert::convertStreamLine2ChatCompletionResult,
                ClaudeConvert::convertByResponse,
                headers
        );
        return llmStreamApiResponse.getStreamData();
    }

    private ClaudeCompletionRequest convertRequest(ChatCompletionRequest request) {
        List<ClaudeCompletionRequest.Message> messages = new ArrayList<>();
        String system = null;
        for (ChatMessage chatMessage : request.getMessages()) {
            if (chatMessage.getRole().equals(LagiGlobal.LLM_ROLE_SYSTEM)) {
                system = chatMessage.getContent();
                continue;
            }
            ClaudeCompletionRequest.Message message = ClaudeCompletionRequest.Message.builder()
                    .role(chatMessage.getRole())
                    .content(chatMessage.getContent())
                    .build();
            messages.add(message);
        }
        ClaudeCompletionRequest result = ClaudeCompletionRequest.builder()
                .model(request.getModel())
                .max_tokens(request.getMax_tokens())
                .temperature(request.getTemperature())
                .stream(request.getStream())
                .system(system)
                .messages(messages)
                .build();
        return result;
    }

    private ChatCompletionResult convertResponse(ClaudeCompletionResponse response) {
        ChatCompletionResult result = new ChatCompletionResult();
        result.setId(response.getId());
        result.setCreated(ChatCompletionUtil.getCurrentUnixTimestamp());
        ChatCompletionChoice choice = new ChatCompletionChoice();
        choice.setIndex(0);
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setContent(response.getContent().get(0).getText());
        chatMessage.setRole(response.getRole());
        choice.setMessage(chatMessage);
        choice.setFinish_reason(response.getStop_reason());
        List<ChatCompletionChoice> choices = new ArrayList<>();
        choices.add(choice);
        result.setChoices(choices);
        Usage usage = new Usage();
        usage.setPrompt_tokens(response.getUsage().getInput_tokens());
        usage.setCompletion_tokens(response.getUsage().getOutput_tokens());
        usage.setTotal_tokens(response.getUsage().getInput_tokens() + response.getUsage().getOutput_tokens());
        result.setUsage(usage);
        return result;
    }
}
