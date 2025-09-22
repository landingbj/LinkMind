package ai.servlet.api;

import ai.agent.Agent;
import ai.common.utils.LRUCache;
import ai.intent.IntentGlobal;
import ai.intent.container.IntentContainer;
import ai.intent.enums.IntentTypeEnum;
import ai.intent.impl.SampleIntentServiceImpl;
import ai.intent.mapper.ModalDetectMapper;
import ai.intent.mapper.RankAgentByKeywordMapper;
import ai.intent.mapper.UserLlmMapper;
import ai.intent.pojo.IntentDetectParam;
import ai.intent.pojo.IntentRouteResult;
import ai.intent.reducer.IntentReducer;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.utils.ContextUtil;
import ai.migrate.service.AgentService;
import ai.mr.IMapper;
import ai.mr.IRContainer;
import ai.mr.IReducer;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.router.pojo.LLmRequest;
import ai.servlet.RestfulServlet;
import ai.servlet.annotation.Post;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class IntentApiServlet extends RestfulServlet {
    private static final long serialVersionUID = 1L;
    private final Gson gson = new Gson();

    private final AgentService agentService = new AgentService();

    private static final LRUCache<String, IntentRouteResult> intentRouteResultCache = new LRUCache<>(1000, 120, TimeUnit.SECONDS);

    private final SampleIntentServiceImpl sampleIntentService = new SampleIntentServiceImpl();

    @Post("detect")
    public void detect(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        resp.setHeader("Content-Type", "application/json;charset=utf-8");
        PrintWriter out = resp.getWriter();

        LLmRequest llmRequest = reqBodyToObj(req, LLmRequest.class);
        IntentRouteResult result;

        IntentTypeEnum modalEnum = sampleIntentService.detectType(llmRequest);

        if (modalEnum != IntentTypeEnum.TEXT || llmRequest.getMax_tokens() <= 0) {
            result = new IntentRouteResult();
            result.setModal(modalEnum.getName());
        } else {
            String uri = req.getScheme() + "://" + req.getServerName() + ":" + req.getServerPort();
            List<Agent<ChatCompletionRequest, ChatCompletionResult>> allAgents = getAllAgents(llmRequest, uri);
            List<ILlmAdapter> userLlmAdapters = getUserLlmAdapters(llmRequest.getUserId());

            IntentDetectParam intentDetectParam = new IntentDetectParam();
            intentDetectParam.setLlmRequest(llmRequest);
            intentDetectParam.setAllAgents(allAgents);
            intentDetectParam.setUserLlmAdapters(userLlmAdapters);

            if (llmRequest.getAgentId() != null) {
                result = appointAgent(llmRequest.getAgentId());
            } else {
                String sessionId = llmRequest.getSessionId();
                IntentRouteResult cachedResult = intentRouteResultCache.get(sessionId);
                long count = llmRequest.getMessages().stream().filter(message -> message.getRole().equals("user")).count();
                boolean isContinued = false;
                if (count > 1) {
                    isContinued = ContextUtil.checkLastMsgContinuity(llmRequest);
                }
                if (cachedResult != null && isContinued) {
                    result = cachedResult;
                } else {
//                if (count > 1) {
//                    String invoke = SummaryUtil.invoke(llmRequest);
//                    intentDetectParam.setInvoke(invoke);
//                }
                    result = detect(intentDetectParam);
                    intentRouteResultCache.put(sessionId, result);
                }
            }
        }

        if (result == null) {
            returnError(resp, out);
            return;
        }

        out.print(gson.toJson(result));
        out.flush();
        out.close();
    }

    private void returnError(HttpServletResponse resp, PrintWriter out) {
        Map<String, Object> body = new HashMap<>();
        body.put("message", "Intent detection failed, please try again later.");
        body.put("code", 500);
        resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        out.print(gson.toJson(body));
        out.flush();
        out.close();
    }

    private IntentRouteResult appointAgent(int agentId) {
        IntentRouteResult intentRouteResult = new IntentRouteResult();
        List<Integer> agents = new ArrayList<>();
        intentRouteResult.setModal("text");
        intentRouteResult.setStatus("completion");
        intentRouteResult.setContinuedIndex(0);
        agents.add(agentId);
        intentRouteResult.setAgents(agents);
        return intentRouteResult;
    }

    private IntentRouteResult detect(IntentDetectParam intentDetectParam) {
        Map<String, Object> params = new HashMap<>();
        params.put(IntentGlobal.MAPPER_INTENT_PARAM, intentDetectParam);

        try (IRContainer contain = new IntentContainer()) {
            IMapper modalDetectMapper = new ModalDetectMapper();
            modalDetectMapper.setParameters(params);
            contain.registerMapper(modalDetectMapper);

            IMapper rankAgentByKeywordMapper = new RankAgentByKeywordMapper();
            rankAgentByKeywordMapper.setParameters(params);
            contain.registerMapper(rankAgentByKeywordMapper);

            IMapper userLlmMapper = new UserLlmMapper();
            userLlmMapper.setParameters(params);
            contain.registerMapper(userLlmMapper);

            IReducer intentReducer = new IntentReducer(intentDetectParam);
            contain.registerReducer(intentReducer);

            @SuppressWarnings("unchecked")
            List<IntentRouteResult> result = (List<IntentRouteResult>) contain.Init().running();
            IntentRouteResult intentRouteResult = null;
            if (result != null && !result.isEmpty()) {
                intentRouteResult = result.get(0);
            }
            return intentRouteResult;
        }
    }

    private List<Agent<ChatCompletionRequest, ChatCompletionResult>> getAllAgents(LLmRequest llmRequest, String uri) throws IOException {
        return agentService.getAllAgents(llmRequest, uri);
    }

    public List<ILlmAdapter> getUserLlmAdapters(String userId) {
        return agentService.getUserLlmAdapters(userId);
    }
}
