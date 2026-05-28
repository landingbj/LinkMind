package ai.llm.hook.impl;

import ai.agent.pojo.SocialChannelMessage;
import ai.agent.service.AgentMessageQueueService;
import ai.annotation.Component;
import ai.annotation.ConditionalOnProperty;
import ai.annotation.Order;
import ai.llm.hook.AfterModel;
import ai.llm.hook.BeforeModel;
import ai.llm.pojo.ModelContext;
import ai.openai.pojo.*;
import ai.utils.I18nFieldUtil;
import ai.utils.LagiGlobal;
import ai.utils.qa.ChatCompletionUtil;
import io.reactivex.Observable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Order(-1)
@Component
@ConditionalOnProperty(name = "skills.enable", havingValue = "true")
public class AgentFilterImpl implements BeforeModel, AfterModel {

    private static final String SOCIAL_NEW_MESSAGE_NOTICE = "您的社交频道有新消息，请查看。";
    private static final String[] AGENT_SYSTEM_TAG = {"LinkMind", "OpenClaw", "Hermes", "DeerFlow"};

    @Override
    public ChatCompletionRequest beforeModel(ModelContext context) {
        ChatCompletionRequest request = context.getRequest();
        List<ChatMessage> chatMessages = request.getMessages();

        List<ChatMessage> systemMessages = ChatCompletionUtil.getSystemMessages(chatMessages);
        if (containsAgentSystemTag(systemMessages)) {
            AgentMessageQueueService.getInstance().cacheSystemMessages(request, systemMessages);
        }
        return request;
    }

    @Override
    public ChatCompletionResult apply(ModelContext context) {
        ChatCompletionResult result = context.getResult();
        String userId = extractUserId(context == null ? null : context.getRequest());
        if (!isBlank(userId)) {
            appendSocialNotice(result, userId);
        }
        return result;
    }

    @Override
    public Observable<ChatCompletionResult> stream(ModelContext context) {
        Observable<ChatCompletionResult> source = context.getStreamResult();
        String userId = extractUserId(context == null ? null : context.getRequest());
        if (isBlank(userId)) {
            return source;
        }
        final boolean[] hasAssistantOutput = {false};
        return source
                .doOnNext(chunk -> {
                    if (isAssistantMessage(extractChoiceMessage(chunk))) {
                        hasAssistantOutput[0] = true;
                    }
                })
                .concatWith(Observable.defer(() -> {
                    if (!hasAssistantOutput[0]) {
                        return Observable.empty();
                    }
                    String noticeText = buildSocialNoticeText(userId);
                    return noticeText == null
                            ? Observable.empty()
                            : Observable.just(buildNoticeStreamChunk(context, noticeText));
                }));
    }

    private static boolean containsAgentSystemTag(List<ChatMessage> systemMessages) {
        if (systemMessages == null || systemMessages.isEmpty()) {
            return false;
        }
        for (ChatMessage message : systemMessages) {
            if (message == null) {
                continue;
            }
            String content = message.getContent();
            if (isBlank(content)) {
                continue;
            }
            for (String tag : AGENT_SYSTEM_TAG) {
                if (tag != null && content.contains(tag)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void appendSocialNotice(ChatCompletionResult result, String userId) {
        if (result == null || result.getChoices() == null || result.getChoices().isEmpty()) {
            return;
        }
        ChatMessage message = extractChoiceMessage(result);
        if (!isAssistantMessage(message)) {
            return;
        }
        String noticeText = buildSocialNoticeText(userId);
        if (noticeText == null) {
            return;
        }
        String content = message.getContent();
        String suffix = (content == null || content.trim().isEmpty() ? "" : "\n\n") + noticeText;
        message.setContent((content == null ? "" : content) + suffix);
    }

    private static String buildSocialNoticeText(String userId) {
        List<SocialChannelMessage> notices = AgentMessageQueueService.getInstance().pollSocialNotice(userId);
        if (notices == null || notices.isEmpty()) {
            return null;
        }
        Set<String> channelNames = new LinkedHashSet<>();
        for (SocialChannelMessage notice : notices) {
            if (notice == null) {
                continue;
            }
            String channelName = notice.getChannelName();
            if (!isBlank(channelName)) {
                String resolvedName = I18nFieldUtil.parse(channelName.trim()).getDefaultValue();
                if (!isBlank(resolvedName)) {
                    channelNames.add(resolvedName.trim());
                }
            }
        }
        if (channelNames.isEmpty()) {
            return SOCIAL_NEW_MESSAGE_NOTICE;
        }
        return SOCIAL_NEW_MESSAGE_NOTICE + "（" + String.join("、", channelNames) + "）";
    }

    private static ChatCompletionResult buildNoticeStreamChunk(ModelContext context, String noticeText) {
        ChatCompletionResult chunk = new ChatCompletionResult();
        if (context != null && context.getRequest() != null) {
            chunk.setModel(context.getRequest().getModel());
        }
        ChatCompletionChoice choice = new ChatCompletionChoice();
        choice.setIndex(0);
        ChatMessage delta = new ChatMessage();
        delta.setRole(LagiGlobal.LLM_ROLE_ASSISTANT);
        delta.setContent("\n\n>" + noticeText);
        choice.setDelta(delta);
        choice.setFinish_reason("stop");
        chunk.setChoices(Collections.singletonList(choice));
        return chunk;
    }

    private static ChatMessage extractChoiceMessage(ChatCompletionResult result) {
        if (result == null || result.getChoices() == null || result.getChoices().isEmpty()) {
            return null;
        }
        ChatCompletionChoice choice = result.getChoices().get(0);
        if (choice.getMessage() != null) {
            return choice.getMessage();
        }
        return choice.getDelta();
    }

    private static boolean isAssistantMessage(ChatMessage message) {
        if (message == null) {
            return false;
        }
        String role = message.getRole();
        return role != null && LagiGlobal.LLM_ROLE_ASSISTANT.equalsIgnoreCase(role.trim());
    }

    private static String extractUserId(ChatCompletionRequest request) {
        if (request == null) {
            return null;
        }
        ExtraBody extraBody = request.getExtraBody();
        if (extraBody == null) {
            return null;
        }
        return extraBody.getUserId();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

}