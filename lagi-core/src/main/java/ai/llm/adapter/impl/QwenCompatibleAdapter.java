package ai.llm.adapter.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QwenCompatibleAdapter extends OpenAIStandardAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ai.llm.adapter.impl.QwenCompatibleAdapter.class);

    @Override
    public String getApiAddress() {
        if (apiAddress == null) {
            apiAddress = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
        }
        return apiAddress;
    }
}
