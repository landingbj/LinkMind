package ai.worker.skillMap;

import ai.agent.Agent;
import ai.agent.chat.rag.LocalRagAgent;
import ai.agent.mcp.McpAgent;
import ai.common.ModelService;
import ai.common.pojo.Backend;
import ai.common.utils.ThreadPoolManager;
import ai.config.pojo.AgentConfig;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.service.FreezingService;
import ai.llm.utils.SummaryUtil;
import ai.manager.LlmManager;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.router.pojo.LLmRequest;
import ai.utils.LagiGlobal;
import ai.utils.qa.ChatCompletionUtil;
import ai.worker.pojo.AgentIntentScore;
import ai.worker.pojo.IntentResponse;
import ai.worker.skillMap.db.AgentInfoDao;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class SkillMapUtil {
    private static final SkillMap skillMap = new SkillMap();
    private static final Double THRESHOLD = 5.0;

    private static final String RAG_AGENT = "ai.agent.chat.rag.LocalRagAgent";
    private static final ExecutorService executorService;
    private static final List<Agent<ChatCompletionRequest, ChatCompletionResult>> llmAndAgentList;
    private static Agent<ChatCompletionRequest, ChatCompletionResult> highestPriorityLlm;

    static {
        llmAndAgentList = initAgents();
        ThreadPoolManager.registerExecutor("SkillMapUtil", 3, 3, 1000);
        executorService= ThreadPoolManager.getExecutor("SkillMapUtil");
    }

    public static List<Agent<ChatCompletionRequest, ChatCompletionResult>> initAgents() {
        Map<String, Integer> ragAgentMap = skillMap.getRagAgentIdName();
        List<AgentConfig> llmAndAgentConfigList = new ArrayList<>();
        List<AgentConfig> agentConfigList = LagiGlobal.getConfig().getAgents();
        llmAndAgentConfigList.addAll(agentConfigList);
        String highestPriorityModel = null;
        int highestPriority = 0;
        for (Backend llmConfig: LagiGlobal.getConfig().getLLM().getChatBackends()) {
            if (llmConfig.getPriority() > highestPriority) {
                highestPriority = llmConfig.getPriority();
                highestPriorityModel = llmConfig.getDefaultModel();
            }
            AgentConfig agentConfig = new AgentConfig();
            agentConfig.setDriver(RAG_AGENT);
            agentConfig.setName(llmConfig.getDefaultModel());
            llmAndAgentConfigList.add(agentConfig);
        }
        List<Agent<ChatCompletionRequest, ChatCompletionResult>> llmAndAgentList = convert2AgentList(llmAndAgentConfigList, new HashMap<>());

        for (Agent<ChatCompletionRequest, ChatCompletionResult> agent : llmAndAgentList) {
            agent.getAgentConfig().setIsFeeRequired(false);
            agent.getAgentConfig().setCanOutPut(true);
            if (agent instanceof LocalRagAgent) {
                LocalRagAgent ragAgent = (LocalRagAgent) agent;
                Integer agentId = ragAgentMap.get(ragAgent.getAgentConfig().getName());
                if (agentId != null) {
                    ragAgent.getAgentConfig().setId(agentId);
                } else {
                    ragAgent.getAgentConfig().setId(skillMap.getRandomRagAgentId());
                }
                if (ragAgent.getAgentConfig().getName().equalsIgnoreCase(highestPriorityModel)) {
                    highestPriorityLlm = ragAgent;
                }
            }
        }
        return llmAndAgentList;
    }

    public static Agent<ChatCompletionRequest, ChatCompletionResult> getHighestPriorityLlm() {
        String name = highestPriorityLlm.getAgentConfig().getName();
        ILlmAdapter adapter = LlmManager.getInstance().getAdapter(name);
        boolean b = FreezingService.notFreezingAdapter(adapter);
        if(b) {
            return highestPriorityLlm;
        } else {
            List<ILlmAdapter> collect = LlmManager.getInstance().getAdapters().stream().filter(a -> a != null && FreezingService.notFreezingAdapter(a)).collect(Collectors.toList());
            if(!collect.isEmpty()) {
                ILlmAdapter adapter1 = collect.get(0);
                ModelService modelService =  (ModelService) adapter1;
                String model = modelService.getModel();
                highestPriorityLlm.getAgentConfig().setName(model);
            }
        }
        return highestPriorityLlm;
    }

    public static List<Agent<ChatCompletionRequest, ChatCompletionResult>> getLlmAndAgentList() {
        return llmAndAgentList;
    }

    public static List<Agent<ChatCompletionRequest, ChatCompletionResult>> convert2AgentList(List<ILlmAdapter> adapters) {
        List<AgentConfig> collect = adapters.stream().map(adapter -> {
            AgentConfig agentConfig = new AgentConfig();
            ModelService modelService = (ModelService) adapter;
            agentConfig.setDriver(RAG_AGENT);
            agentConfig.setName(modelService.getModel());
            return agentConfig;
        }).collect(Collectors.toList());
        return convert2AgentList(collect, new HashMap<>());
    }

    public static Future<IntentResponse> asyncIntentDetect(
            String question) {
        return executorService.submit(() -> skillMap.intentDetect(question));
    }

    public static IntentResponse intentDetect(
            String question) {
        return  skillMap.intentDetect(question);
    }

    public static List<Agent<ChatCompletionRequest, ChatCompletionResult>> rankAgentByIntentKeyword(
            List<Agent<ChatCompletionRequest, ChatCompletionResult>> agentList,
            String question) {
        // TODO 2025/5/13 update rank logic
//        return Collections.emptyList();
        IntentResponse intentResponse = skillMap.intentDetect(question);
        return rankAgentByIntentKeyword(agentList,  intentResponse, THRESHOLD);
    }

    public static List<Agent<ChatCompletionRequest, ChatCompletionResult>> rankAgentByIntentKeyword(
            List<Agent<ChatCompletionRequest, ChatCompletionResult>> agentList,
            IntentResponse intent) {
        return rankAgentByIntentKeyword(agentList, intent,  THRESHOLD);
    }

    private static List<Integer> pickAgentByDescribe(LLmRequest llmRequest, List<Agent<ChatCompletionRequest, ChatCompletionResult>> allAgents) {
        List<Integer> agents;
        if(llmRequest.getIntent().getAgents() != null) {
            agents = llmRequest.getIntent().getAgents();
        } else {
            agents = new ArrayList<>();
        }

        String invoke = SummaryUtil.invoke(llmRequest);
        System.out.println("invoke: " + invoke);
        if (invoke != null && !invoke.isEmpty()) {
            SummaryUtil.setInvoke(llmRequest, invoke);
        }
        List<Agent<ChatCompletionRequest, ChatCompletionResult>> pickAgentList = null;
        Future<List<Agent<ChatCompletionRequest, ChatCompletionResult>>> future = SkillMapUtil.asyncPickAgentByDescribe(ChatCompletionUtil.getLastMessage(llmRequest), allAgents);
        try {
            pickAgentList = future.get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("pick up agent error", e);
        }
        if (pickAgentList != null) {
            for (Agent<ChatCompletionRequest, ChatCompletionResult> agent : pickAgentList) {
                agents.add(agent.getAgentConfig().getId());
            }
        }
        return agents;
    }

    public static List<Agent<ChatCompletionRequest, ChatCompletionResult>> pickAgentByDescribe(
            String question, List<Agent<ChatCompletionRequest, ChatCompletionResult>> agentList) {
        agentList = agentList.stream().filter(agent->{
            if(Boolean.TRUE.equals(agent.getAgentConfig().getIsFeeRequired())) {
                return Boolean.TRUE.equals(agent.getAgentConfig().getCanOutPut());
            }
            return true;
        }).collect(Collectors.toList());
        List<SkillMap.PickAgent> pickAgents = agentList.stream().filter(agent -> StrUtil.isNotBlank(agent.getAgentConfig().getDescribe())).map(agent -> {
            AgentConfig agentConfig = agent.getAgentConfig();
            return SkillMap.PickAgent.builder().describe(agent.getAgentConfig().getDescribe()).id(agentConfig.getId()).build();
        }).collect(Collectors.toList());
        SkillMap skillMap1 = new SkillMap();
        List<SkillMap.PickAgent> pickAgents1 = skillMap1.pickAgent(question, pickAgents);
        Set<Integer> set = pickAgents1.stream().map(SkillMap.PickAgent::getId).collect(Collectors.toSet());
        return agentList.stream().filter(agent -> set.contains(agent.getAgentConfig().getId())).collect(Collectors.toList());
    }

    public static Future<List<Agent<ChatCompletionRequest, ChatCompletionResult>>> asyncPickAgentByDescribe(
            String question) {
        return executorService.submit(() -> pickAgentByDescribe(question));
    }

    public static <T> List<List<T>> splitList(List<T> originalList, int chunkSize) {
        List<List<T>> result = new ArrayList<>();
        if (originalList == null || originalList.isEmpty()) {
            return result;
        }
        for (int i = 0; i < originalList.size(); i += chunkSize) {
            int endIndex = Math.min(i + chunkSize, originalList.size());
            result.add(new ArrayList<>(originalList.subList(i, endIndex)));
        }
        return result;
    }

    public static Future<List<Agent<ChatCompletionRequest, ChatCompletionResult>>> asyncPickAgentByDescribe(
            String question, List<Agent<ChatCompletionRequest, ChatCompletionResult>> agentList) {
        if(agentList.size() > 50) {
            List<List<Agent<ChatCompletionRequest, ChatCompletionResult>>> lists = splitList(agentList, 50);
            return executorService.submit(() -> lists.stream().map(list -> asyncPickAgentByDescribe(question, list)).flatMap(future -> {
                try {
                    return future.get().stream();
                } catch (Exception e) {
                    return Stream.empty();
                }
            }).collect(Collectors.toList()));
        }
        return executorService.submit(() -> pickAgentByDescribe(question, agentList));
    }

    public static List<Agent<ChatCompletionRequest, ChatCompletionResult>> pickAgentByDescribe(
            String question) {
        List<Agent<ChatCompletionRequest, ChatCompletionResult>> agentList = getLlmAndAgentList();
        return pickAgentByDescribe(question, agentList);
    }


    public static List<Agent<ChatCompletionRequest, ChatCompletionResult>> rankAgentByIntentKeyword(
            List<Agent<ChatCompletionRequest, ChatCompletionResult>> agentList, IntentResponse intentResponse, Double edge) {
        return skillMap.rankAgentByIntentKeyword(agentList,intentResponse, edge);
    }

    public static List<Agent<ChatCompletionRequest, ChatCompletionResult>> convertConfig2AgentList(List<AgentConfig> agentConfigs) {
        Map<String, Constructor<?>> agentMap = new HashMap<>();
        return agentConfigs.stream().map(agentConfig -> {
            if (agentConfig == null) {
                return null;
            }
            return convertAgentObject(agentConfig, agentMap);
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public static List<Agent<ChatCompletionRequest, ChatCompletionResult>> convert2AgentList(List<AgentConfig> agentConfigs, Map<Integer, Boolean> haveABalance) {
        Map<String, Constructor<?>> agentMap = new HashMap<>();
        return agentConfigs.stream().map(agentConfig -> {
            if (agentConfig == null) {
                return null;
            }
            agentConfig.setCanOutPut(haveABalance.getOrDefault(agentConfig.getId(), false));
            return convertAgentObject(agentConfig, agentMap);
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private static Agent<ChatCompletionRequest, ChatCompletionResult> convertAgentObject(AgentConfig agentConfig, Map<String, Constructor<?>> agentMap) {
        String driver = agentConfig.getDriver();
        Agent<ChatCompletionRequest, ChatCompletionResult> agent = null;
        if(agentConfig.getId() != null) {
            AgentInfoDao.AgentInfo info = AgentInfoDao.getByAgentId(agentConfig.getId());
            if(info != null) {
                agentConfig.setDescribe(info.getAgentDescribe());
            }
        }
        if (!agentMap.containsKey(driver)) {
            try {
                Class<?> aClass = Class.forName(driver);
                Constructor<?> constructor = aClass.getConstructor(AgentConfig.class);
                agentMap.put(driver, constructor);
                agent = (Agent<ChatCompletionRequest, ChatCompletionResult>) constructor.newInstance(agentConfig);
            } catch (Exception ignored) {
            }
        } else {
            Constructor<?> constructor = agentMap.get(driver);
            try {
                agent = (Agent<ChatCompletionRequest, ChatCompletionResult>) constructor.newInstance(agentConfig);
            } catch (Exception ignored) {
            }
        }
        return agent;
    }

    private static void saveAgents(IntentResponse intentResponse, ChatCompletionRequest request, List<Agent<ChatCompletionRequest, ChatCompletionResult>> agentList) {
        for (Agent<ChatCompletionRequest, ChatCompletionResult> agent : agentList) {
            if (agent instanceof LocalRagAgent) {
                request.setModel(agent.getAgentConfig().getName());
            }
            try {
                saveScore(intentResponse, request, agent);
            } catch (Exception e) {
                log.error("scoreAgents error");
            }
        }
    }

    public static void asyncScoreAgents(ChatCompletionRequest request, List<Agent<ChatCompletionRequest, ChatCompletionResult>> agentList,
                                        Agent<ChatCompletionRequest, ChatCompletionResult> outputAgent) {
        executorService.submit(() -> {
            request.setStream(false);
            IntentResponse intentResponse = skillMap.intentDetect(ChatCompletionUtil.getLastMessage(request));
            saveAgents(intentResponse, request, agentList);
        });
    }

    public static void asyncScoreAgents(IntentResponse intentResponse, LLmRequest llmRequest,
                                        final List<Agent<ChatCompletionRequest, ChatCompletionResult>> allAgents, Agent<ChatCompletionRequest, ChatCompletionResult> outputAgent) {
        executorService.submit(() -> {
            List<Integer> agents = pickAgentByDescribe(llmRequest, allAgents);

            List<Agent<ChatCompletionRequest, ChatCompletionResult>> pickedAgents = allAgents.stream()
                .filter(agent -> (agent instanceof LocalRagAgent) || agents.contains(agent.getAgentConfig().getId()))
                .collect(Collectors.toList());

            pickedAgents = pickedAgents.stream().filter(agent ->
                            !Objects.equals(outputAgent.getAgentConfig().getId(), agent.getAgentConfig().getId())
            ).collect(Collectors.toList());

            llmRequest.setStream(false);
            saveAgents(intentResponse, llmRequest, pickedAgents);
        });
    }

    public static double getOrInsertScore(ChatCompletionRequest request, Agent<ChatCompletionRequest, ChatCompletionResult> agent, ChatCompletionResult chatCompletionResult) {
        IntentResponse intentResponse = skillMap.intentDetect(ChatCompletionUtil.getLastMessage(request));
        return getOrInsertScore(intentResponse, request, agent, chatCompletionResult);
    }

    public static Double getOrInsertScore(IntentResponse intentResponse, ChatCompletionRequest request, Agent<ChatCompletionRequest, ChatCompletionResult> agent, ChatCompletionResult chatCompletionResult) {
        if(intentResponse == null) {
            return null;
        }
        List<String> keywords = intentResponse.getKeywords();
        AgentIntentScore agentIntentScore = skillMap.agentIntentScore(agent.getAgentConfig().getId(), keywords);
        double scoring;
        if (agentIntentScore == null) {
            scoring = skillMap.scoring(ChatCompletionUtil.getLastMessage(request), ChatCompletionUtil.getFirstAnswer(chatCompletionResult));
            skillMap.saveAgentScore(agent.getAgentConfig(), keywords, scoring);
        } else {
            scoring = agentIntentScore.getScore();
        }
        return scoring;
    }

    public static Double updateScore(IntentResponse intentResponse, ChatCompletionRequest request, Agent<ChatCompletionRequest, ChatCompletionResult> agent, ChatCompletionResult chatCompletionResult) {
        List<String> keywords = intentResponse.getKeywords();
        if(intentResponse == null) {
            return null;
        }
        double scoring;
        scoring = skillMap.scoring(ChatCompletionUtil.getLastMessage(request), ChatCompletionUtil.getFirstAnswer(chatCompletionResult));
        skillMap.updateScore(agent.getAgentConfig(), keywords, scoring);
        return scoring;
    }

    public static Integer getNameByID(String name) {
        return skillMap.getNameByID(name);
    }

    public static void saveScore(IntentResponse intentResponse, ChatCompletionRequest request, Agent<ChatCompletionRequest, ChatCompletionResult> agent) {
        if(intentResponse == null ||  intentResponse.getKeywords()  == null ) {
            return;
        }
        List<String> keywords = intentResponse.getKeywords();
        AgentIntentScore agentIntentScore = skillMap.agentIntentScore(agent.getAgentConfig().getId(), keywords);
        if (agentIntentScore == null) {
            ChatCompletionResult chatCompletionResult = agent.communicate(request);
            if(chatCompletionResult != null) {
                double scoring = skillMap.scoring(ChatCompletionUtil.getLastMessage(request), ChatCompletionUtil.getFirstAnswer(chatCompletionResult));
                skillMap.saveAgentScore(agent.getAgentConfig(), keywords, scoring);
            }
        }
    }
}
