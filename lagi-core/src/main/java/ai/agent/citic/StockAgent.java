package ai.agent.citic;

import ai.agent.pojo.NewConversationRequest;
import ai.agent.pojo.NewConversationResponse;
import ai.agent.pojo.StockRequest;
import ai.agent.pojo.StockResponse;
import ai.config.pojo.AgentConfig;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.common.utils.LRUCache;
import ai.utils.OkHttpUtil;
import ai.utils.qa.ChatCompletionUtil;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class StockAgent extends CiticAgent{
    private static final Logger logger = LoggerFactory.getLogger(StockAgent.class);
    private static final Gson gson = new Gson();
    private static final String BASE_URL = "https://qianfan.baidubce.com/v2/app/conversation/runs";
    private static final String NEW_CONVERSATION_URL = "https://qianfan.baidubce.com/v2/app/conversation";
    private static final LRUCache<String, String> sessionCache = new LRUCache<>(1000, 5, TimeUnit.DAYS);


    public StockAgent(AgentConfig agentConfig) {
        this.agentConfig = agentConfig;
    }

    public ChatCompletionResult chat(ChatCompletionRequest request) throws IOException {
        StockRequest stockRequest = convertRequest(request, this.agentConfig);
        String json = gson.toJson(stockRequest);
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Appbuilder-Authorization", "Bearer " + this.agentConfig.getToken());
        String responseJson = OkHttpUtil.post(BASE_URL, headers, new HashMap<>(), json);
        System.out.println(responseJson);
        StockResponse response = gson.fromJson(responseJson, StockResponse.class);
        return convertResult(response);
    }

    private StockRequest convertRequest(ChatCompletionRequest request, AgentConfig agentConfig) {
        String queryText = String.format("用大约 100 个字回答用三引号分隔的问题:\"\"\"%s\"\"\"", ChatCompletionUtil.getLastMessage(request));
        StockRequest stockRequest = StockRequest.builder()
                .stream(request.getStream())
                .app_id(agentConfig.getAppId())
                .query(queryText)
                .conversation_id(getConversationId(request.getSessionId()))
                .build();
        return stockRequest;
    }

    private String getConversationId(String sessionId) {
        String conversationId = sessionCache.get(sessionId);
        if (conversationId == null) {
            conversationId = createNewConversation();
            sessionCache.put(sessionId, conversationId);
        }
        return conversationId;
    }

    private String createNewConversation() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Appbuilder-Authorization", "Bearer " + agentConfig.getToken());
        String responseJson = null;
        NewConversationRequest newConversationRequest = NewConversationRequest.builder().app_id(agentConfig.getAppId()).build();
        try {
            responseJson = OkHttpUtil.post(NEW_CONVERSATION_URL, headers, new HashMap<>(), gson.toJson(newConversationRequest));
        } catch (IOException e) {
            logger.error("create new conversation error", e);
        }
        NewConversationResponse response = gson.fromJson(responseJson, NewConversationResponse.class);
        if (response.getCode() != null) {
            throw new RuntimeException("create new conversation error message: " + response.getCode() + " " + response.getMessage());
        }
        return response.getConversation_id();
    }

    private ChatCompletionResult convertResult(StockResponse response) {
        if (response == null) {
            return null;
        }
        if (response.getCode() != null) {
            throw new RuntimeException("stock error message: " + response.getCode() + " " + response.getMessage());
        }
        String answer = response.getAnswer();
        ChatCompletionResult result = ChatCompletionUtil.toChatCompletionResult(answer, null);
        return result;
    }
}
