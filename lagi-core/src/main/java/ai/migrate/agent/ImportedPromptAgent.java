package ai.migrate.agent;

import ai.agent.Agent;
import ai.config.pojo.AgentConfig;
import ai.llm.service.CompletionsService;
import ai.migrate.pojo.HistoryRecord;
import ai.migrate.pojo.ImportedAgentProfile;
import ai.migrate.service.ImportedAgentStore;
import ai.openai.pojo.ChatCompletionChoice;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.utils.qa.ChatCompletionUtil;

import java.util.ArrayList;
import java.util.List;

public class ImportedPromptAgent extends Agent<ChatCompletionRequest, ChatCompletionResult> {
    private final CompletionsService completionsService = new CompletionsService();
    private ImportedAgentProfile profile;

    public ImportedPromptAgent(AgentConfig agentConfig) {
        this.agentConfig = agentConfig;
        this.profile = ImportedAgentStore.loadProfile(agentConfig);
    }

    @Override
    public ChatCompletionResult communicate(ChatCompletionRequest data) {
        ImportedAgentProfile currentProfile = getProfile();
        if (currentProfile == null) {
            return null;
        }
        String latestUser = findLatestUser(data);
        ChatCompletionRequest request = copyRequest(data);
        request.setStream(false);
        request.setMessages(buildMessages(data, currentProfile, latestUser));
        ChatCompletionResult result = completionsService.completions(request);
        String answer = firstAnswer(result);
        if (!isBlank(latestUser) && !isBlank(answer)) {
            ImportedAgentStore.appendRuntimeHistory(agentConfig, currentProfile, data.getSessionId(), latestUser, answer);
        }
        return result;
    }

    private ImportedAgentProfile getProfile() {
        if (profile == null) {
            profile = ImportedAgentStore.loadProfile(agentConfig);
        }
        return profile;
    }

    private List<ChatMessage> buildMessages(ChatCompletionRequest data, ImportedAgentProfile profile, String latestUser) {
        String systemPrompt = ImportedAgentStore.readSystemPrompt(agentConfig, profile);
        List<HistoryRecord> history = ImportedAgentStore.readLastHistory(agentConfig, profile);
        return mergeMessages(systemPrompt, history, data == null ? null : data.getMessages(), latestUser);
    }

    static List<ChatMessage> mergeMessages(String systemPrompt,
                                           List<HistoryRecord> history,
                                           List<ChatMessage> requestMessages,
                                           String latestUser) {
        List<ChatMessage> messages = new ArrayList<>();
        if (!isBlank(systemPrompt)) {
            messages.add(ChatMessage.builder().role("system").content(systemPrompt).build());
        }
        if (history != null) {
            for (HistoryRecord record : history) {
                ChatMessage message = toHistoryMessage(record);
                if (message != null) {
                    messages.add(message);
                }
            }
        }
        List<ChatMessage> currentMessages = collectRequestMessages(requestMessages);
        if (currentMessages.isEmpty() && !isBlank(latestUser)) {
            currentMessages.add(ChatMessage.builder().role("user").content(latestUser).build());
        }
        appendNonOverlapping(messages, currentMessages);
        return messages;
    }

    private static ChatMessage toHistoryMessage(HistoryRecord record) {
        if (record == null || isBlank(record.getRole()) || isBlank(record.getContent())) {
            return null;
        }
        if (!isConversationRole(record.getRole()) && !"system".equals(record.getRole())) {
            return null;
        }
        if ("assistant".equals(record.getRole()) && isLookupPlaceholder(record.getContent())) {
            return null;
        }
        return ChatMessage.builder().role(record.getRole()).content(record.getContent()).build();
    }

    static boolean isLookupPlaceholder(String content) {
        if (isBlank(content)) {
            return false;
        }
        String text = content.trim();
        boolean lookupIntent = text.contains("查找")
                || text.contains("查询")
                || text.contains("查一下")
                || text.contains("搜索")
                || text.contains("检索");
        boolean futureAction = text.contains("我来")
                || text.contains("现在就")
                || text.contains("让我先")
                || text.contains("然后")
                || text.contains("为您");
        boolean hasSubstantialAnswer = text.contains("第1天")
                || text.contains("第一天")
                || text.contains("上午")
                || text.contains("下午")
                || text.contains("预算")
                || text.contains("费用")
                || text.contains("建议")
                || text.contains("推荐")
                || text.contains("方案如下")
                || text.contains("行程如下")
                || text.contains("路线");
        return lookupIntent && futureAction && !hasSubstantialAnswer && text.length() <= 120;
    }

    private static List<ChatMessage> collectRequestMessages(List<ChatMessage> requestMessages) {
        List<ChatMessage> messages = new ArrayList<>();
        if (requestMessages == null) {
            return messages;
        }
        for (ChatMessage message : requestMessages) {
            if (message == null || !isConversationRole(message.getRole()) || isBlank(message.getContent())) {
                continue;
            }
            if ("assistant".equals(message.getRole()) && isLookupPlaceholder(message.getContent())) {
                continue;
            }
            messages.add(ChatMessage.builder().role(message.getRole()).content(message.getContent()).build());
        }
        return messages;
    }

    private static void appendNonOverlapping(List<ChatMessage> messages, List<ChatMessage> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return;
        }
        int overlap = findSuffixPrefixOverlap(messages, incoming);
        for (int i = overlap; i < incoming.size(); i++) {
            messages.add(incoming.get(i));
        }
    }

    private static int findSuffixPrefixOverlap(List<ChatMessage> messages, List<ChatMessage> incoming) {
        int max = Math.min(messages.size(), incoming.size());
        for (int size = max; size > 0; size--) {
            if (matchesSuffixPrefix(messages, incoming, size)) {
                return size;
            }
        }
        return 0;
    }

    private static boolean matchesSuffixPrefix(List<ChatMessage> messages, List<ChatMessage> incoming, int size) {
        int start = messages.size() - size;
        for (int i = 0; i < size; i++) {
            if (!sameMessage(messages.get(start + i), incoming.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameMessage(ChatMessage left, ChatMessage right) {
        if (left == null || right == null) {
            return false;
        }
        return safe(left.getRole()).equals(safe(right.getRole()))
                && safe(left.getContent()).equals(safe(right.getContent()));
    }

    private static boolean isConversationRole(String role) {
        return "user".equals(role) || "assistant".equals(role);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private ChatCompletionRequest copyRequest(ChatCompletionRequest source) {
        ChatCompletionRequest request = new ChatCompletionRequest();
        if (source == null) {
            return request;
        }
        request.setSessionId(source.getSessionId());
        request.setExtraBody(source.getExtraBody());
        request.setModel(source.getModel());
        request.setTemperature(source.getTemperature());
        request.setMax_tokens(source.getMax_tokens());
        request.setMax_completion_tokens(source.getMax_completion_tokens());
        request.setCategory(source.getCategory());
        request.setStream(source.getStream());
        request.setTools(source.getTools());
        request.setTool_choice(source.getTool_choice());
        request.setParallel_tool_calls(source.getParallel_tool_calls());
        request.setPresence_penalty(source.getPresence_penalty());
        request.setFrequency_penalty(source.getFrequency_penalty());
        request.setTop_p(source.getTop_p());
        request.setResponse_format(source.getResponse_format());
        request.setStream_options(source.getStream_options());
        request.setLogprobs(source.getLogprobs());
        request.setEnableHook(source.getEnableHook());
        request.setEnableAfter(source.getEnableAfter());
        request.setStore(source.getStore());
        request.setApiKey(source.getApiKey());
        request.setUserApiKey(source.getUserApiKey());
        request.setChat_template_kwargs(source.getChat_template_kwargs());
        request.setPreserveInputMessages(true);
        request.setTools(null);
        request.setTool_choice(null);
        request.setParallel_tool_calls(null);
        return request;
    }

    private String findLatestUser(ChatCompletionRequest data) {
        if (data == null || data.getMessages() == null) {
            return null;
        }
        for (int i = data.getMessages().size() - 1; i >= 0; i--) {
            ChatMessage message = data.getMessages().get(i);
            if (message != null && "user".equals(message.getRole()) && !isBlank(message.getContent())) {
                return message.getContent();
            }
        }
        return null;
    }

    private String firstAnswer(ChatCompletionResult result) {
        if (result == null || result.getChoices() == null || result.getChoices().isEmpty()) {
            return null;
        }
        ChatCompletionChoice choice = result.getChoices().get(0);
        if (choice == null || choice.getMessage() == null) {
            return null;
        }
        String answer = ChatCompletionUtil.getFirstAnswer(result);
        if (!isBlank(answer)) {
            return answer;
        }
        return choice.getMessage().getContent();
    }

    @Override
    public void connect() {
    }

    @Override
    public void terminate() {
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void send(ChatCompletionRequest request) {
    }

    @Override
    public ChatCompletionResult receive() {
        return null;
    }
}
