package ai.llm.utils;

import ai.common.ModelService;
import ai.llm.adapter.ILlmAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LlmAdapterFactory {

    private static final Logger log = LoggerFactory.getLogger(LlmAdapterFactory.class);

    public static ILlmAdapter getLlmAdapter(String modelType, String modelName, Integer priority, String apiKey, String endpoint) {
        String driver = "ai.llm.adapter.impl.OpenAIStandardAdapter";
        if("Baichuan".equals(modelType)) {
            driver = "ai.llm.adapter.impl.BaichuanAdapter";
        }else if ("Claude".equals(modelType)) {
            driver = "ai.llm.adapter.impl.ClaudeAdapter";
        }else if ("DeepSeek".equals(modelType)) {
            driver = "ai.llm.adapter.impl.DeepSeekAdapter";
        }else if ("Doubao".equals(modelType)) {
            driver = "ai.llm.adapter.impl.DoubaoAdapter";
        }else if ("Ernie".equals(modelType)) {
            driver = "ai.llm.adapter.impl.ErnieAdapter";
        } else if ("Gemini".equals(modelType)) {
            driver = "ai.llm.adapter.impl.GeminiAdapter";
        } else if ("GPT".equals(modelType)) {
            driver = "ai.llm.adapter.impl.GPTAdapter";
        } else if ("GPTAzure".equals(modelType)) {
            driver = "ai.llm.adapter.impl.GPTAzureAdapter";
        } else if ("Moonshot".equals(modelType)) {
            driver ="ai.llm.adapter.impl.MoonshotAdapter";
        } else if ("Qwen".equals(modelType)) {
            driver = "ai.llm.adapter.impl.QwenAdapter";
        } else if ("Sense".equals(modelType)) {
            driver = "ai.llm.adapter.impl.SenseChatAdapter";
        }else if ("Spark".equals(modelType)) {
            driver = "ai.llm.adapter.impl.SparkAdapter";
        } else if ("Zhipu".equals(modelType)) {
            driver = "ai.llm.adapter.impl.ZhipuAdapter";
        }
        try {
            Class<?> aClass = Class.forName(driver);
            ModelService m = (ModelService) aClass.getDeclaredConstructor().newInstance();
            m.setModel(modelName);
            m.setApiKey(apiKey);
            m.setPriority(priority);
            m.setApiAddress(endpoint);
            return (ILlmAdapter) m;
        } catch (Exception e) {
            log.error("LlmAdapterFactory getLlmAdapter error", e);
        }
        return null;
    }

}
