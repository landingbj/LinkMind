package ai.llm.adapter.impl;

import ai.annotation.LLM;
import ai.common.ModelService;
import ai.common.exception.RRException;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.pojo.EnhanceChatCompletionRequest;
import ai.llm.pojo.LlmApiResponse;
import ai.llm.utils.OpenAiApiUtil;
import ai.llm.utils.convert.GptConvert;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import com.google.gson.Gson;
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@LLM(modelNames = {"*"})
public class OpenAIStandardAdapter extends ModelService implements ILlmAdapter {
    private static final Logger logger = LoggerFactory.getLogger(OpenAIStandardAdapter.class);
    private static final int HTTP_TIMEOUT = 30 * 1000;

    @Override
    public ChatCompletionResult completions(ChatCompletionRequest chatCompletionRequest) {
        setDefaultModel(chatCompletionRequest);
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
        setDefaultModel(chatCompletionRequest);
        LlmApiResponse completions = OpenAiApiUtil.streamCompletions(apiKey, getApiAddress(), HTTP_TIMEOUT, chatCompletionRequest,
                GptConvert::convertSteamLine2ChatCompletionResult, GptConvert::convertByResponse);
//        if(completions.getCode() != 200) {
//            logger.error("openai  stream api : code{}  error  {}", completions.getCode(), completions.getMsg());
//            throw new RRException(completions.getCode(), completions.getMsg());
//        }
        return completions.getStreamData();
    }

    private void setDefaultModel(ChatCompletionRequest request) {
        request.setCategory(null);
        if (request.getModel() == null) {
            request.setModel(getModel());
        }
        if (request instanceof EnhanceChatCompletionRequest) {
            ((EnhanceChatCompletionRequest) request).setIp(null);
            ((EnhanceChatCompletionRequest) request).setBrowserIp(null);
            ((EnhanceChatCompletionRequest) request).setUserId(null);
            ((EnhanceChatCompletionRequest) request).setPriority(null);
            request.setSessionId(null);
        }
    }
}
