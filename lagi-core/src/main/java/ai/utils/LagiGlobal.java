package ai.utils;

import java.util.List;

import ai.common.pojo.Backend;
import ai.config.ContextLoader;

import ai.common.pojo.Configuration;

public class LagiGlobal {
    private static Configuration config;

    public static String LANDING_API_KEY;

    public static String AGENT_API_KEY = "your-api-key";

    private static String DEFAULT_CATEGORY;

    public static boolean RAG_ENABLE = false;

    static {
        loadConfig();
    }

    public static Configuration getConfig() {
        return config;
    }

    public static Configuration loadConfig() {
        if (config != null) {
            return config;
        }
        ContextLoader.loadContext();
        if (ContextLoader.configuration == null) {
            throw new IllegalStateException("failed to load lagi configuration, please check lagi.yml and startup logs");
        }
        config = ContextLoader.configuration.transformToConfiguration();
        setLandingApikey(config);
        setDefaultCategory(config);
        return config;
    }

    public static String getLandingApikey() {
        return LANDING_API_KEY;
    }

    private static void setLandingApikey(Configuration config) {
        List<Backend> backends = config.getLLM().getBackends();
        for (Backend backend : backends) {
            if (backend.getType().equalsIgnoreCase(LagiGlobal.LLM_TYPE_LANDING)) {
                String apiKey = backend.getApiKey();
                if (apiKey != null && apiKey.startsWith("sk-") && apiKey.length() == 35) {
                    LANDING_API_KEY = apiKey;
                }
            }
        }
    }

    public static String getDefaultCategory() {
        return DEFAULT_CATEGORY;
    }

    private static void setDefaultCategory(Configuration config) {
        if (config.getVectorStores() != null && !config.getVectorStores().isEmpty()){
            DEFAULT_CATEGORY = config.getVectorStores().get(0).getDefaultCategory();
        }
    }

    public static final String LLM_ROLE_USER = "user";
    public static final String LLM_ROLE_ASSISTANT = "assistant";
    public static final String LLM_ROLE_SYSTEM = "system";

    public static final boolean IMAGE_EXTRACT_ENABLE = true;
    public static final String CHAT_COMPLETION_REQUEST = "ChatCompletionRequest";
    public static final String CHAT_COMPLETION_CONFIG = "ChatCompletionConfig";

    public static final String LANDING_MODEL_QA = "qa";
    public static final String LANDING_MODEL_TREE = "tree";
    public static final String LANDING_MODEL_TURING = "turing";

    public static final String LLM_TYPE_LANDING = "Landing";
    public static final String LLM_TYPE_VICUNA = "Vicuna";
    public static final String LLM_TYPE_GPT = "GPT";
    public static final String LLM_TYPE_Qwen = "Qwen";
    public static final String LLM_TYPE_ERNIE = "Ernie";
    public static final String LLM_TYPE_ZHIPU = "Zhipu";
    public static final String LLM_TYPE_MOONSHOT = "Moonshot";
    public static final String LLM_TYPE_BAICHUAN = "Baichuan";
    public static final String LLM_TYPE_SPARK = "Spark";
    public static final String LLM_TYPE_SENSECHAT = "SenseChat";
    public static final String AUDIO_TYPE_LANDING = "Landing";
    public static final String AUDIO_TYPE_ALIBABA = "Alibaba";

    public static final String IMAGE_TYPE_LANDING = "Landing";
    public static final String IMAGE_TYPE_ALIBABA = "Alibaba";


    public static final int ASR_STATUS_SUCCESS = 20000000;
    public static final int ASR_STATUS_FAILURE = 40000000;

    public static final int TTS_STATUS_SUCCESS = 20000000;
    public static final int TTS_STATUS_FAILURE = 40000000;
}
