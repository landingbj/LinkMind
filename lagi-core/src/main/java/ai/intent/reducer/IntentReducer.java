package ai.intent.reducer;

import ai.agent.Agent;
import ai.config.pojo.AgentConfig;
import ai.intent.IntentGlobal;
import ai.intent.pojo.IntentDetectParam;
import ai.intent.pojo.IntentDetectResult;
import ai.intent.pojo.IntentResult;
import ai.intent.pojo.IntentRouteResult;
import ai.mr.IReducer;
import ai.mr.reduce.BaseReducer;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.qa.AiGlobalQA;
import ai.router.pojo.LLmRequest;
import ai.utils.LRUCache;
import ai.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class IntentReducer extends BaseReducer implements IReducer {
    private static final Logger logger = LoggerFactory.getLogger(IntentReducer.class);
    private final List<IntentRouteResult> result = new ArrayList<>();
    private static final LRUCache<String, Pair<Integer, Agent<ChatCompletionRequest, ChatCompletionResult>>> agentLRUCache = IntentGlobal.AGENT_LRU_CACHE;
    private final IntentDetectParam intentDetectParam;

    public IntentReducer(IntentDetectParam intentDetectParam) {
        this.intentDetectParam = intentDetectParam;
    }

    @Override
    public void myReducing(List<?> list) {
        LLmRequest llmRequest = this.intentDetectParam.getLlmRequest();
        List<Integer> agents = new ArrayList<>();

        String modal = "text";
        String status = "completion";
        int continuedIndex = llmRequest.getMessages().size() - 1;
        Map<AgentConfig, Double> priorityMap = new HashMap<>();
        Map<Integer, Boolean> streamFlagMap = new HashMap<>();

        for (Object mapperResult : list) {
            List<?> mapperList = (List<?>) mapperResult;
            IntentDetectResult intentDetectResult = (IntentDetectResult) mapperList.get(AiGlobalQA.M_LIST_RESULT_TEXT);
            double priority = (Double) mapperList.get(AiGlobalQA.M_LIST_RESULT_PRIORITY);
            if (intentDetectResult.getModal() != null) {
                modal = intentDetectResult.getModal().getType();
            }
            List<Agent<ChatCompletionRequest, ChatCompletionResult>> tmpAgents = intentDetectResult.getAgents();
            if (tmpAgents == null || tmpAgents.isEmpty()) {
                continue;
            }
            for (Agent<ChatCompletionRequest, ChatCompletionResult> agent : tmpAgents) {
                AgentConfig agentConfig = agent.getAgentConfig();
                if (agentConfig != null) {
                    streamFlagMap.put(agentConfig.getId(), agent.canStream());
                    agents.add(agentConfig.getId());
                    if (priorityMap.containsKey(agentConfig)) {
                        double oldPriority = priorityMap.get(agentConfig);
                        priority = priority + oldPriority;
                    }
                    priorityMap.put(agentConfig, priority);
                }
            }
        }
        agents = sortAgents(priorityMap);

        IntentRouteResult intentRouteResult = new IntentRouteResult();
        intentRouteResult.setModal(modal);
        intentRouteResult.setStatus(status);
        intentRouteResult.setContinuedIndex(continuedIndex);
        intentRouteResult.setAgents(agents);
        intentRouteResult.setInvoke(intentDetectParam.getInvoke());

        boolean allSolid = false;
        if (!streamFlagMap.isEmpty()) {
            allSolid = streamFlagMap.values().stream().allMatch(v -> v == Boolean.FALSE);
        }
        intentRouteResult.setAllSolid(allSolid);
        if (!agents.isEmpty()) {
            intentRouteResult.setFirstStream(streamFlagMap.get(agents.get(0)));
        } else {
            intentRouteResult.setFirstStream(Boolean.FALSE);
        }

        result.add(intentRouteResult);
        logger.info("IntentReducer Finished Reducing.");
    }

    private List<Integer> sortAgents(Map<AgentConfig, Double> priorityMap) {
        return priorityMap.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue())
                .map(entry -> entry.getKey().getId())
                .collect(Collectors.toList());
    }

    @Override
    public synchronized void myReducing(String mapperName, List<?> list, int priority) {
    }

    @Override
    public List<?> getResult() {
        return result;
    }

    private Agent<ChatCompletionRequest, ChatCompletionResult> getRecordOutputAgent(LLmRequest llmRequest, IntentResult intentResult, Agent<ChatCompletionRequest, ChatCompletionResult> outputAgent) {
        Pair<Integer, Agent<ChatCompletionRequest, ChatCompletionResult>> integerAgentPair = agentLRUCache.get(llmRequest.getSessionId());
        if (integerAgentPair != null && Objects.equals(integerAgentPair.getPA(), intentResult.getContinuedIndex())) {
            outputAgent = integerAgentPair.getPB();
        }
        return outputAgent;
    }
}
