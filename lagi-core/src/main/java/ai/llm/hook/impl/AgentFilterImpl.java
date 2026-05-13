package ai.llm.hook.impl;

import ai.agent.service.AgentMessageQueueService;
import ai.annotation.Component;
import ai.annotation.ConditionalOnProperty;
import ai.annotation.Order;
import ai.llm.hook.AfterModel;
import ai.llm.hook.BeforeModel;
import ai.llm.pojo.ModelContext;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.utils.qa.ChatCompletionUtil;
import io.reactivex.Observable;

import java.util.List;

@Order(-1)
@Component
@ConditionalOnProperty(name = "skills.enable", havingValue = "true")
public class AgentFilterImpl implements BeforeModel, AfterModel {

    @Override
    public ChatCompletionRequest beforeModel(ModelContext context) {
        ChatCompletionRequest request = context.getRequest();
        List<ChatMessage> chatMessages = request.getMessages();

        List<ChatMessage> systemMessages = ChatCompletionUtil.getSystemMessages(chatMessages);
        AgentMessageQueueService.getInstance().cacheSystemMessages(request, systemMessages);
        return request;
    }

    @Override
    public ChatCompletionResult apply(ModelContext context) {
        return context.getResult();
    }

    @Override
    public Observable<ChatCompletionResult> stream(ModelContext context) {
        return context.getStreamResult();
    }

}