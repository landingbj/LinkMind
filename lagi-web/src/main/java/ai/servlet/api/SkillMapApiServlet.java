package ai.servlet.api;

import ai.agent.Agent;
import ai.config.pojo.AgentConfig;
import ai.llm.utils.SummaryUtil;
import ai.agent.AgentService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.servlet.RestfulServlet;
import ai.servlet.annotation.Body;
import ai.servlet.annotation.Post;
import ai.agent.dto.LagiAgentListResponse;
import ai.utils.qa.ChatCompletionUtil;
import ai.worker.skillMap.SkillMap;
import ai.worker.skillMap.db.AgentInfoDao;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.util.Lists;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
public class SkillMapApiServlet extends RestfulServlet {

    private final SkillMap skillMap =  new SkillMap();
    private AgentService agentService = new AgentService();

    @Post("relatedAgents")
    public List<AgentConfig> queryRelatedAgents(@Body ChatCompletionRequest request) {
        try {
            LagiAgentListResponse lagiAgentList = agentService.getLagiAgentList(null, 1, 1000, "true");
            List<AgentConfig> agentConfigs = lagiAgentList.getData();
            List<SkillMap.PickAgent> describedAgents = agentConfigs.stream()
                    .peek(agentConfig -> {
                        AgentInfoDao.AgentInfo agentInfo = AgentInfoDao.getByAgentId(agentConfig.getId());
                        if(agentInfo != null) {
                            agentConfig.setDescribe(agentInfo.getAgentDescribe());
                        } else {
                            loadDescribe(agentConfig);
                        }
                    })
                    .filter(agentConfig -> {
                        String describe = agentConfig.getDescribe();
                        return StrUtil.isNotBlank(describe) && Boolean.TRUE.equals(agentConfig.getIsFeeRequired());
                    })
                    .map(agentConfig -> {
                        SkillMap.PickAgent pickAgent = new SkillMap.PickAgent();
                        pickAgent.setId(agentConfig.getId());
                        pickAgent.setDescribe(agentConfig.getDescribe());
                        return pickAgent;
                    })
                    .collect(Collectors.toList());
            String question = ChatCompletionUtil.getLastMessage(request);
            if(request.getMessages().stream().filter(chatMessage -> chatMessage.getRole().equals("user")).count() > 1) {
                question = SummaryUtil.invoke(request);
            }
            List<SkillMap.PickAgent> pickAgents = skillMap.pickAgent(question, describedAgents);
            Map<Integer, AgentConfig> agentNameMap = agentConfigs.stream().collect(Collectors.toMap(AgentConfig::getId, o->o));
            return pickAgents.stream()
                    .filter(pickAgent -> pickAgent.getId() != 0)
                    .map(pickAgent -> {
                        AgentConfig agentConfig = new AgentConfig();
                        agentConfig.setId(pickAgent.getId());
                        AgentConfig config = agentNameMap.get(pickAgent.getId());
                        agentConfig.setName(config.getName());
                        agentConfig.setPricePerReq(config.getPricePerReq());
                        return agentConfig;
                    }).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("queryRelatedAgents error", e);
        }


//        IntentResponse intent = skillMap.intentDetect(request);
//        if(intent == null) {
//            return null;
//        }
//        try {
//            List<AgentIntentScore> agentIntentScoreByIntentKeyword = skillMap.getAgentIntentScoreByIntentKeyword(intent.getKeywords());
//            List<Integer> collect = agentIntentScoreByIntentKeyword.stream().map(AgentIntentScore::getAgentId).collect(Collectors.toList());
//            if(collect.isEmpty()) {
//                return null;
//            }
//            FeeRequiredAgentRequest feeRequiredAgentRequest = new FeeRequiredAgentRequest();
//            feeRequiredAgentRequest.setAgentIds(collect);
//            feeRequiredAgentRequest.setIsFeeRequired(true);
//            LagiAgentListResponse feeRequiredAgent = agentService.getFeeRequiredAgent(feeRequiredAgentRequest);
//            List<AgentConfig> data = feeRequiredAgent.getData();
//            data.forEach(agentConfig -> {
//                agentConfig.setAppId(null);
//                agentConfig.setToken(null);
//                agentConfig.setApiKey(null);
//                agentConfig.setDriver(null);
//            });
//            return data;
//        } catch (Exception ignored) {
//        }
        return null;
    }

    private void loadDescribe(AgentConfig agentConfig) {
        CompletableFuture.runAsync(() -> {
                try {
                    Class<?> aClass = Class.forName(agentConfig.getDriver());
                    Constructor<?> constructor = aClass.getConstructor(AgentConfig.class);
                    Agent<ChatCompletionRequest, ChatCompletionResult> agent = (Agent<ChatCompletionRequest, ChatCompletionResult>) constructor.newInstance(agentConfig);
                    ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest();
                    chatCompletionRequest.setTemperature(0.1);
                    chatCompletionRequest.setMax_tokens(1024);
                    chatCompletionRequest.setStream(false);
                    ChatMessage chatMessage = new ChatMessage();
                    chatMessage.setRole("user");
                    chatMessage.setContent("请简明列出你的核心能力，涵盖知识领域、任务处理类型等，要求 50 字内清晰呈现。");
                    chatCompletionRequest.setMessages(Lists.newArrayList(chatMessage));
                    AgentInfoDao.AgentInfo agentInfo = AgentInfoDao.getByAgentId(agentConfig.getId());
                    if(agentInfo != null) {
                        return;
                    }
                    synchronized (agentConfig.getId()) {
                        agentInfo = AgentInfoDao.getByAgentId(agentConfig.getId());
                        if(agentInfo == null) {
                            ChatCompletionResult communicate = agent.communicate(chatCompletionRequest);
                            String describe = ChatCompletionUtil.getFirstAnswer(communicate);
                            log.info("agent {}-{} save describe: {}", agentConfig.getId(), agentConfig.getName(),  describe);
                            AgentInfoDao.saveOrUpdate(
                                    AgentInfoDao.AgentInfo.builder().agentId(agentConfig.getId()).agentDescribe(describe).build()
                            );
                        }
                    }
                } catch (Exception ignored) {
                }
        });
    }


}
