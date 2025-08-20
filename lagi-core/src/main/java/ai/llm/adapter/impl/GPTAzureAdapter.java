package ai.llm.adapter.impl;

import ai.annotation.LLM;
import ai.common.ModelService;
import ai.common.exception.RRException;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.pojo.LlmApiResponse;
import ai.llm.utils.OpenAiApiUtil;
import ai.llm.utils.convert.GptAzureConvert;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@LLM(modelNames = {"gpt-3.5-turbo","gpt-4-1106-preview","gpt-4o-20240513"})
public class GPTAzureAdapter extends ModelService implements ILlmAdapter {
    private static final Logger logger = LoggerFactory.getLogger(GPTAzureAdapter.class);
    private static final int HTTP_TIMEOUT = 30 * 1000;
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
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("api-key", getApiKey());

        String json;
        try {
            json = mapper.writeValueAsString(chatCompletionRequest);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        LlmApiResponse completions = OpenAiApiUtil.completions(getApiKey(), getApiAddress(), HTTP_TIMEOUT, json,
                GptAzureConvert::convert2ChatCompletionResult, GptAzureConvert::convertByResponse,
                headers);
        if(completions.getCode() != 200) {
            logger.error("open ai  api error {}", completions.getMsg());
            throw new RRException(completions.getCode(), completions.getMsg());
        }
        return completions.getData();
    }

    @Override
    public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest chatCompletionRequest) {
        setDefaultField(chatCompletionRequest);
        String apiUrl = getApiAddress();
        String apiKey = getApiKey();
        Map<String, String> headers = new HashMap<>();
        headers.put("api-key", apiKey);

        String json;
        try {
            json = mapper.writeValueAsString(chatCompletionRequest);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        LlmApiResponse llmApiResponse = OpenAiApiUtil.streamCompletions(apiKey, apiUrl, HTTP_TIMEOUT, json,
                GptAzureConvert::convertStreamLine2ChatCompletionResult, GptAzureConvert::convertByResponse, headers);
        Integer code = llmApiResponse.getCode();
        if(code != 200) {
            logger.error("open ai stream api error {}", llmApiResponse.getMsg());
            throw new RRException(code, llmApiResponse.getMsg());
        }
        return llmApiResponse.getStreamData();
    }
}
