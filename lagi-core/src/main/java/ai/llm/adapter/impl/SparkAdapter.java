package ai.llm.adapter.impl;

import ai.annotation.LLM;
import ai.common.ModelService;
import ai.common.exception.RRException;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.pojo.LlmApiResponse;
import ai.llm.utils.OpenAiApiUtil;
import ai.llm.utils.convert.SparkConvert;
import ai.openai.pojo.*;
import io.reactivex.Observable;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
@LLM(modelNames = {"lite", "generalv3", "pro-128k", "generalv3.5", "max-32k", "4.0Ultra"})
public class SparkAdapter extends ModelService implements ILlmAdapter {
    private static final Logger logger = LoggerFactory.getLogger(SparkAdapter.class);
    private static final String COMPLETIONS_URL = "https://spark-api-open.xf-yun.com/v1/chat/completions";
    private static final int HTTP_TIMEOUT = 15;

    @Override
    public ChatCompletionResult completions(ChatCompletionRequest chatCompletionRequest) {
        LlmApiResponse llmApiResponse = llmCompletions(chatCompletionRequest);
        if (llmApiResponse.getCode() != 200) {
            logger.error("SparkAdapter api code:{}  error:{} ", llmApiResponse.getCode(), llmApiResponse.getMsg());
            throw new RRException(llmApiResponse.getCode(), llmApiResponse.getMsg());
        }
        return llmApiResponse.getData();
    }

    @Override
    public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest chatCompletionRequest) {
        setDefaultField(chatCompletionRequest);
        LlmApiResponse llmApiResponse = llmCompletions(chatCompletionRequest);
        if (llmApiResponse.getCode() != 200) {
            logger.error("SparkAdapter stream  api code:{}  error:{} ", llmApiResponse.getCode(), llmApiResponse.getMsg());
            throw new RRException(llmApiResponse.getCode(), llmApiResponse.getMsg());
        }
        return llmApiResponse.getStreamData();
    }

    public LlmApiResponse llmCompletions(ChatCompletionRequest chatCompletionRequest) {
        String reqApiKey = getApiKey(chatCompletionRequest);
        setDefaultField(chatCompletionRequest);
        LlmApiResponse completions;
        if (chatCompletionRequest.getStream()) {
            completions = OpenAiApiUtil.streamCompletions(reqApiKey,
                    COMPLETIONS_URL,
                    HTTP_TIMEOUT,
                    chatCompletionRequest,
                    SparkConvert::convertStreamLine2ChatCompletionResult,
                    SparkConvert::convertByResponse);
        } else {
            completions = OpenAiApiUtil.completions(reqApiKey,
                    COMPLETIONS_URL,
                    HTTP_TIMEOUT,
                    chatCompletionRequest,
                    SparkConvert::convert2ChatCompletionResult,
                    SparkConvert::convertByResponse);
        }
        return completions;
    }
}
