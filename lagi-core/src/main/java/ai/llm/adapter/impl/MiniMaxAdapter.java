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
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@LLM(modelNames = {"MiniMax-Text-01"})
public class MiniMaxAdapter  extends ModelService implements ILlmAdapter {
    private static final Logger logger = LoggerFactory.getLogger(MiniMaxAdapter.class);
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
        if(completions.getCode() != 200) {
            logger.error("openai  stream api : code{}  error  {}", completions.getCode(), completions.getMsg());
            throw new RRException(completions.getCode(), completions.getMsg());
        }
        return completions.getStreamData();
    }

    private void setDefaultModel(ChatCompletionRequest request) {
        request.setCategory(null);
        this.apiAddress = "https://api.minimax.chat/v1/text/chatcompletion_v2";
        if (request.getModel() == null) {
            request.setModel(getModel());
        }
        if (request instanceof EnhanceChatCompletionRequest) {
            ((EnhanceChatCompletionRequest) request).setIp(null);
            ((EnhanceChatCompletionRequest) request).setBrowserIp(null);
            ((EnhanceChatCompletionRequest) request).setUserId(null);
            request.setSessionId(null);
        }
    }
}
