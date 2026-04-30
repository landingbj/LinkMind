package ai.llm.adapter.impl;

import ai.annotation.LLM;
import ai.common.ModelService;
import ai.common.exception.RRException;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.pojo.GeminiRequest;
import ai.llm.pojo.LlmApiResponse;
import ai.llm.utils.OpenAiApiUtil;
import ai.llm.utils.convert.GeminiConvert;
import ai.openai.pojo.*;
import ai.utils.LagiGlobal;
import io.reactivex.Observable;
import okhttp3.HttpUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@LLM(modelNames = {"gemini-1.5-flash-latest","gemini-1.5-flash-latest","gemini-1.0-pro","gemini-1.5-pro-latest"})
public class GeminiAdapter extends ModelService implements ILlmAdapter {
    private static final Logger logger = LoggerFactory.getLogger(GeminiAdapter.class);
    private static final String COMPLETIONS_URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent";
    private static final String STEAM_COMPLETIONS_URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:streamGenerateContent";

    @Override
    public ChatCompletionResult completions(ChatCompletionRequest chatCompletionRequest) {
        LlmApiResponse llmApiResponse = getLlmApiResponse(chatCompletionRequest);
        if(llmApiResponse.getCode() != 200) {
            throw new RRException(llmApiResponse.getCode(), llmApiResponse.getMsg());
        }
        return llmApiResponse.getData();
    }

    @Override
    public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest chatCompletionRequest) {
        LlmApiResponse llmStreamApiResponse = getLlmStreamApiResponse(chatCompletionRequest);
        if(llmStreamApiResponse.getCode() != 200) {
            throw new RRException(llmStreamApiResponse.getCode(), llmStreamApiResponse.getMsg());
        }
        return llmStreamApiResponse.getStreamData();
    }


    private LlmApiResponse getLlmApiResponse(ChatCompletionRequest chatCompletionRequest) {
        String reqApiKey = getApiKey(chatCompletionRequest);
        setDefaultField(chatCompletionRequest);
        String url = COMPLETIONS_URL.replace("{model}", getModel());
        Map<String, String> params = new HashMap<>();
        params.put("key", reqApiKey);
        HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
        for (Map.Entry<String, String> param : params.entrySet()) {
            urlBuilder.addQueryParameter(param.getKey(), param.getValue());
        }
        HttpUrl finalUrl = urlBuilder.build();
        return OpenAiApiUtil.completions(reqApiKey, finalUrl.toString(), 30, chatCompletionRequest,
                GeminiConvert::convertStringResponse,
                GeminiConvert::convertByResponse);
    }

    private LlmApiResponse getLlmStreamApiResponse(ChatCompletionRequest chatCompletionRequest) {
        String reqApiKey = getApiKey(chatCompletionRequest);
        setDefaultField(chatCompletionRequest);
        String apiKey = reqApiKey;
        String url = STEAM_COMPLETIONS_URL.replace("{model}", getModel()) + "?key=" + apiKey + "&alt=sse";
        return OpenAiApiUtil.streamCompletions(apiKey, url, 30, chatCompletionRequest,
                GeminiConvert::convertStringResponse,
                GeminiConvert::convertByResponse);
    }

    private GeminiRequest convertRequest(ChatCompletionRequest request) {
        GeminiRequest result = new GeminiRequest();
        List<GeminiRequest.Content> contents = new ArrayList<>();
        for (ChatMessage chatMessage : request.getMessages()) {
            List<GeminiRequest.Part> parts = new ArrayList<>();
            GeminiRequest.Part part = GeminiRequest.Part.builder().text(chatMessage.getContent()).build();
            parts.add(part);
            if (chatMessage.getRole().equals(LagiGlobal.LLM_ROLE_ASSISTANT)) {
                contents.add(GeminiRequest.Content.builder().role("model").parts(parts).build());
            } else if (chatMessage.getRole().equals(LagiGlobal.LLM_ROLE_USER)) {
                contents.add(GeminiRequest.Content.builder().role("user").parts(parts).build());
            } else if (chatMessage.getRole().equals(LagiGlobal.LLM_ROLE_SYSTEM)) {
                GeminiRequest.SystemInstruction systemInstruction = new GeminiRequest.SystemInstruction();
                systemInstruction.setParts(part);
                result.setSystemInstruction(systemInstruction);
            }
        }
        GeminiRequest.GenerationConfig config = new GeminiRequest.GenerationConfig();
        config.setTemperature(request.getTemperature());
        config.setMaxOutputTokens(request.getMax_tokens());
        result.setGenerationConfig(config);
        result.setContents(contents);
        return result;
    }

}
