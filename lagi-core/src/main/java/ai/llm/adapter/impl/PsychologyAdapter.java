package ai.llm.adapter.impl;

import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PsychologyAdapter extends OpenAIStandardAdapter {
    private static final Logger logger = LoggerFactory.getLogger(PsychologyAdapter.class);

    @Override
    public String getApiAddress() {
        if (apiAddress == null) {
            apiAddress = "https://api.hunyuan.cloud.tencent.com/v1/chat/completions";
        }
        return apiAddress;
    }

    @Override
    public ChatCompletionResult completions(ChatCompletionRequest chatCompletionRequest) {
        setReplaceModel(chatCompletionRequest);
        return super.completions(chatCompletionRequest);
    }

    @Override
    public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest chatCompletionRequest) {
        setReplaceModel(chatCompletionRequest);
        return super.streamCompletions(chatCompletionRequest);
    }

    private void setReplaceModel(ChatCompletionRequest chatCompletionRequest) {
        chatCompletionRequest.setModel("hunyuan-turbos-latest");
    }
}
