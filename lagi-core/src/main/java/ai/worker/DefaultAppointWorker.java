package ai.worker;

import ai.agent.Agent;
import ai.config.pojo.WorkerConfig;
import ai.manager.AgentManager;
import ai.migrate.service.ImportedAgentStore;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.router.Route;
import ai.router.Routers;
import ai.router.utils.RouteGlobal;
import ai.router.utils.RouterParser;
import ai.utils.qa.ChatCompletionUtil;
import cn.hutool.core.bean.BeanUtil;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class DefaultAppointWorker extends RouteWorker {

    protected Map<String, Agent<ChatCompletionRequest, ChatCompletionResult>> agentMap = new ConcurrentHashMap<>();

    protected WorkerConfig workerConfig;

    private final Route route;



    public DefaultAppointWorker(WorkerConfig workerConfig) {
        this.workerConfig = workerConfig;
//        String routeName = RouterParser.getRuleName(workerConfig.getRoute());
        this.route = Routers.getInstance().getRoute(workerConfig.getRoute());
        ImportedAgentStore.ensureExternalAgentsRegistered();
        List<String> agents = RouterParser.getParams(workerConfig.getRoute());
        if(agents.size() == 1 && RouteGlobal.WILDCARD_STRING.equals(agents.get(0))) {
            List<Agent<?, ?>> allAgents = AgentManager.getInstance().agents();
            for (Agent<?, ?> agent : allAgents) {
                if(agent.getAgentConfig() !=null) {
                    try {
                        String appId = agent.getAgentConfig().getName();
                        agentMap.put(appId, (Agent<ChatCompletionRequest, ChatCompletionResult>) agent);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        if(agents.size() > 1) {
            for (String agentId : agents) {
                Agent<ChatCompletionRequest, ChatCompletionResult> agent =
                        (Agent<ChatCompletionRequest, ChatCompletionResult>) AgentManager.getInstance().get(agentId);
                if(agent != null) {
                    agentMap.put(agent.getAgentConfig().getName(), agent);
                }
            }
        }
    }


    @Override
    public ChatCompletionResult work(ChatCompletionRequest data) {
        return call(data);
    }

    @Override
    public ChatCompletionResult call(ChatCompletionRequest data) {
        String agentId = (String)BeanUtil.getFieldValue(data, "agentId");
        Agent<ChatCompletionRequest, ChatCompletionResult> trAgent = agentMap.get(agentId);
        if(trAgent == null) {
            ImportedAgentStore.ensureExternalAgentsRegistered();
            trAgent = (Agent<ChatCompletionRequest, ChatCompletionResult>) AgentManager.getInstance().get(agentId);
            if(trAgent != null && trAgent.getAgentConfig() != null) {
                agentMap.put(agentId, trAgent);
            }
        }
        if(trAgent != null) {
            List<ChatCompletionResult> invoke = route.invokeAgent(data, Lists.newArrayList(trAgent)).getResult();
            if(invoke != null && !invoke.isEmpty()) {
                ChatCompletionResult communicate = invoke.get(0);
                try {
                    SkillMap skillMap = new SkillMap();
                    skillMap.saveAgentScore(trAgent.getAgentConfig(), ChatCompletionUtil.getLastMessage(data), ChatCompletionUtil.getFirstAnswer(communicate));
                } catch (Exception e) {

                }
                return communicate;
            }
        }
        return null;
    }

    @Override
    public void notify(ChatCompletionRequest data) {

    }
}
