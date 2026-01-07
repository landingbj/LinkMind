package ai.router;


import ai.agent.Agent;
import ai.common.exception.RRException;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.service.FreezingService;
import ai.llm.utils.LLMErrorConstants;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.router.pojo.RouteAgentResult;
import ai.router.pojo.RouteCompletionResult;
import ai.utils.SensitiveWordUtil;
import cn.hutool.core.bean.BeanUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class FailOverRoute extends Route {

    public FailOverRoute(List<Route> routes) {
        super(routes);
        this.routes = routes;
//        System.out.println("FailOverRoute : " + routes);
    }

    @Override
    public RouteAgentResult invokeAgent(ChatCompletionRequest request, List<Agent<ChatCompletionRequest, ChatCompletionResult>> agents) {
        for (Route route : routes) {
            RouteAgentResult result = route.invokeAgent(request, agents);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    @Override
    public RouteCompletionResult invokeLlm(ChatCompletionRequest request) {
        for (Route route : routes) {
            RouteCompletionResult result = route.invokeLlm(request);
            if (result != null) {
                return result;
            }
        }
        return null;
    }
}
