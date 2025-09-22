package ai.agent.chat.rag;

import ai.agent.chat.BaseChatAgent;
import ai.common.exception.RRException;
import ai.common.pojo.Backend;
import ai.config.ContextLoader;
import ai.config.pojo.AgentConfig;
import ai.config.pojo.RAGFunction;
import ai.llm.pojo.LlmApiResponse;
import ai.llm.utils.OpenAiApiUtil;
import ai.llm.utils.convert.GptConvert;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.router.pojo.LLmRequest;
import ai.utils.OkHttpUtil;
import com.google.gson.Gson;
import io.reactivex.Observable;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class LocalRagAgent extends BaseChatAgent {
    private final Gson gson = new Gson();
    private final RAGFunction RAG_CONFIG = ContextLoader.configuration.getStores().getRag();
    private final Backend agentGeneralConfiguration = ContextLoader.configuration.getAgentGeneralConfiguration();
    private static final int HTTP_TIMEOUT = 60;

    public LocalRagAgent(AgentConfig agentConfig) {
        super(agentConfig);
    }

    @Override
    public ChatCompletionResult communicate(ChatCompletionRequest data) {
        String endpoint = agentConfig.getEndpoint();
        if (agentGeneralConfiguration.getEndpoint() != null) {
            endpoint = agentGeneralConfiguration.getEndpoint();
        }
        String responseJson;
        try {
            responseJson = OkHttpUtil.post(endpoint + "/v1/chat/completions", gson.toJson(data));
        } catch (IOException e) {
            String SAMPLE_COMPLETION_RESULT_PATTERN = "{\"created\":0,\"choices\":[{\"index\":0,\"message\":{\"content\":\"%s\"}}]}";
            responseJson = String.format(SAMPLE_COMPLETION_RESULT_PATTERN, RAG_CONFIG.getDefaultText());
            log.error("RagMapper.myMapping: OkHttpUtil.post error", e);
        }
        return gson.fromJson(responseJson, ChatCompletionResult.class);
    }

    private String toBody(ChatCompletionRequest data) {
        String body;
        if(data instanceof LLmRequest) {
            LLmRequest o =  (LLmRequest) data;
            body = gson.toJson(o);
        } else {
            body = gson.toJson(data);
        }
        return body;
    }

    @Override
    public Observable<ChatCompletionResult> stream(ChatCompletionRequest data) {
        String endpoint = agentConfig.getEndpoint();
        if (agentGeneralConfiguration.getEndpoint() != null) {
            endpoint = agentGeneralConfiguration.getEndpoint();
        }
        LlmApiResponse completions = OpenAiApiUtil.streamCompletions("", endpoint + "/v1/chat/completions", HTTP_TIMEOUT, (LLmRequest) data,
                this::convertSteamLine2ChatCompletionResult, GptConvert::convertByResponse);
//        if (completions.getCode() != 200) {
//            log.error("LocalRagAgent  stream api : code {}  error  {}", completions.getCode(), completions.getMsg());
//            throw new RRException(completions.getCode(), completions.getMsg());
//        }
        return completions.getStreamData();
    }

    @Override
    public boolean canStream() {
        return true;
    }

    public ChatCompletionResult convertSteamLine2ChatCompletionResult(String body) {
        if (body.equals("[DONE]")) {
            return null;
        }
        ChatCompletionResult result = gson.fromJson(body, ChatCompletionResult.class);
        return result;
    }
}
