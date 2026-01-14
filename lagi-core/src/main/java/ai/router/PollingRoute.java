package ai.router;

import ai.agent.Agent;
import ai.llm.pojo.EnhanceChatCompletionRequest;
import ai.llm.utils.PollingScheduler;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.router.pojo.RouteAgentResult;
import ai.router.pojo.RouteCompletionResult;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class PollingRoute extends Route {

    public PollingRoute(List<Route> routes) {
        super(routes);
        this.routes = routes;
//        System.out.println("PollingRoute : " + routes);
    }

    private static final AtomicInteger nextServerCyclicCounter = new AtomicInteger(-1);

    private static int incrementAndGetModulo(int modulo) {
        int current;
        int next;
        do {
            current = nextServerCyclicCounter.get();
            next = (current + 1) % modulo;
        } while (!nextServerCyclicCounter.compareAndSet(current, next));
        return next;
    }

    public RouteAgentResult invokeAgent(ChatCompletionRequest request, List<Agent<ChatCompletionRequest, ChatCompletionResult>> agents) {
        for (int i = 0; i < routes.size(); i++) {
            int index = incrementAndGetModulo(routes.size());
            RouteAgentResult result = routes.get(index).invokeAgent(request, agents);
            if (result != null) {
                return result;
            }
        }
        throw new RuntimeException("polling run error");
    }

    @Override
    public RouteCompletionResult invokeLlm(ChatCompletionRequest request) {
        return pollingGetChatCompletionResult(request);
    }


    public RouteCompletionResult pollingGetChatCompletionResult(ChatCompletionRequest chatCompletionRequest) {
        chatCompletionRequest.setModel(null);
        List<Route> pollingRoutes = new ArrayList<>(this.routes);
        // no ipaddress use sample polling
        boolean isSamplePolling = false;
        if (chatCompletionRequest instanceof EnhanceChatCompletionRequest) {
            EnhanceChatCompletionRequest enhanceChatCompletionRequest = (EnhanceChatCompletionRequest) chatCompletionRequest;
            if (enhanceChatCompletionRequest.getIp() != null && !enhanceChatCompletionRequest.getIp().isEmpty()) {
                isSamplePolling = true;
            }
        }
        if (isSamplePolling) {
            EnhanceChatCompletionRequest enhanceChatCompletionRequest = (EnhanceChatCompletionRequest) chatCompletionRequest;
            int hash = enhanceChatCompletionRequest.getIp().hashCode();
            while (!pollingRoutes.isEmpty()) {
                int index = Math.abs(hash) % pollingRoutes.size();
                Route route = pollingRoutes.get(index);
                RouteCompletionResult result = route.invokeLlm(chatCompletionRequest);
                if (result != null) {
                    return result;
                }
                pollingRoutes.remove(index);
            }
        } else {
            while (!pollingRoutes.isEmpty()) {
                Route route = PollingScheduler.routeSchedule(pollingRoutes);
                RouteCompletionResult result = route.invokeLlm(chatCompletionRequest);
                if (result != null) {
                    return result;
                }
                pollingRoutes.remove(route);
            }
        }
        return null;
    }
}
