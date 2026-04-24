package ai.llm.responses;

import ai.common.ModelService;
import ai.common.utils.LRUCache;
import ai.intent.impl.SampleIntentServiceImpl;
import ai.medusa.pojo.PromptInput;
import ai.medusa.pojo.PromptParameter;
import ai.openai.pojo.ChatMessage;
import ai.openai.pojo.ToolCall;
import ai.openai.pojo.ToolCallFunction;
import ai.utils.LagiGlobal;
import ai.utils.qa.ChatCompletionUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ResponseSessionManager {
    private static final int CACHE_SIZE = 1000;
    private static final long TTL_DAYS = 5L;

    private static ResponseSessionState responseSessionState = null;

    private static final ResponseSessionManager INSTANCE = new ResponseSessionManager();
    private final LRUCache<String, ResponseSessionState> sessionCache =
            new LRUCache<>(CACHE_SIZE, TTL_DAYS, TimeUnit.DAYS);

    private final LRUCache<List<PromptInput>, ResponseSessionState> splitSessionCache =
            new LRUCache<>(CACHE_SIZE, TTL_DAYS, TimeUnit.DAYS);

    public static ResponseSessionManager getInstance() {
        return INSTANCE;
    }


    public ResponseSessionContext prepare(ai.openai.pojo.ChatCompletionRequest request, ModelService modelService) {
        List<ChatMessage> chatMessages = request.getMessages();
        List<ChatMessage> historyMessages = ChatCompletionUtil.getHistoryMessages(chatMessages);
        List<ChatMessage> incrementMessages = ChatCompletionUtil.getIncrementMessages(chatMessages);

        ResponseSessionContext context = new ResponseSessionContext();
        context.setSessionId(request.getSessionId());
        context.setBackend(modelService.getBackend());
        context.setModel(request.getModel() == null ? modelService.getModel() : request.getModel());
        context.setProtocol(ResponseProtocolUtil.normalize(modelService.getProtocol()));
        context.setAllHistoryChatMessage(chatMessages);

        // first message return context without previous response id
        if(historyMessages.isEmpty()) {
            removeCachedSession(historyMessages, true);
            Integer firstUserIndex = ChatCompletionUtil.getFirstUserIndex(chatMessages);
            context.setSplitStartIndex(firstUserIndex);
            return newConversationContext(chatMessages, incrementMessages, context);
        }
        // has history
        // check boundary
        SampleIntentServiceImpl sampleIntentService = new SampleIntentServiceImpl();
        // first value : user index second value: assistant(ps :not tool call assistant) index
        List<Integer> boundary = sampleIntentService.theFinalRoundOfConversation(chatMessages);
        ResponseSessionState cachedSession = getCachedSession(historyMessages);
        //  Determine the boundaries of the conversation split
        if(cachedSession == null) {
            if(boundary.isEmpty()) {
                // cache all
                Integer firstUserIndex = ChatCompletionUtil.getFirstUserIndex(chatMessages);
                context.setSplitStartIndex(firstUserIndex);
                return newConversationContext(chatMessages, incrementMessages, context);
            }
            // If no cache hit, the message after the start of the cache boundary
            List<ChatMessage> systemMessages = ChatCompletionUtil.getSystemMessages(chatMessages);
            incrementMessages = chatMessages.subList(boundary.get(0), chatMessages.size());
            List<ChatMessage> newChatMessages = new ArrayList<>(systemMessages);
            newChatMessages.addAll(incrementMessages);
            context.setSplitStartIndex(boundary.get(0));
            return newConversationContext(newChatMessages, incrementMessages, context);
        }
        // if cache and boundary is empty, append to cache
        if(boundary.isEmpty()) {
            context.setStateful(true);
            List<ChatMessage> inputMessages = new ArrayList<>(ChatCompletionUtil.getSystemMessages(chatMessages));
            inputMessages.addAll(incrementMessages);
            context.setInputMessages(inputMessages);
            context.setNormalizedMessages(incrementMessages);
            context.setPreviousResponseId(cachedSession.getPreviousResponseId());
            Integer firstUserIndex = ChatCompletionUtil.getFirstUserIndex(chatMessages);
            context.setSplitStartIndex(firstUserIndex);
            return context;
        }
        Integer conversationStartIndex = cachedSession.getConversationStartIndex();
        Integer currentConversationStartIndex = boundary.get(0);
        boolean isContinue = Objects.equals(currentConversationStartIndex, conversationStartIndex);
        List<ChatMessage> systemMessages = ChatCompletionUtil.getSystemMessages(chatMessages);
        boolean sameSystemMessages = isSameSystemMessages(systemMessages, cachedSession.getSystemMessages());
        // When reusing previousResponseId, skip system messages if unchanged; otherwise resend them so the cache gets refreshed on success.
        List<ChatMessage> newChatMessages = sameSystemMessages ? new ArrayList<>() : new ArrayList<>(systemMessages);
        if(isContinue) {
            // send incremental messages
            newChatMessages.addAll(incrementMessages);
            context.setStateful(true);
            context.setInputMessages(newChatMessages);
            context.setNormalizedMessages(incrementMessages);
            context.setPreviousResponseId(cachedSession.getPreviousResponseId());
            context.setSplitStartIndex(currentConversationStartIndex);
            return context;
        }
        // The new cache contains all the messages after the boundary as newly added.
        incrementMessages = chatMessages.subList(boundary.get(0), chatMessages.size());
        newChatMessages.addAll(incrementMessages);
        context.setSplitStartIndex(boundary.get(0));
        return newConversationContext(newChatMessages, incrementMessages, context);
    }

    private ResponseSessionState getCachedSession(List<ChatMessage> historyMessages) {
//        return splitSessionCache.get(convert2PromptInputs(historyMessages));
        return responseSessionState;
    }

    private void setCachedSession(List<ChatMessage> historyMessages, ResponseSessionState  state) {
//        splitSessionCache.put(convert2PromptInputs(historyMessages),  state);
            responseSessionState = state;
    }

    private void removeCachedSession(List<ChatMessage> historyMessages, boolean flag) {
//        splitSessionCache.remove(convert2PromptInputs(historyMessages));
        if(flag) {
            responseSessionState = null;
        }
    }



    private ResponseSessionContext newConversationContext(List<ChatMessage> inputMessage, List<ChatMessage> incrementalMessages, ResponseSessionContext context) {
        context.setInputMessages(inputMessage);
        context.setNormalizedMessages(incrementalMessages);
        context.setStateful(false);
        return context;
    }


    private List<PromptInput> convert2PromptInputs(List<ChatMessage> messages) {
        List<PromptInput> promptInputs =  new ArrayList<>();
        for (ChatMessage message : messages) {
            PromptInput promptInput = PromptInput.builder()
                    .promptList(Lists.newArrayList(message.getContent() == null ? "" : message.getContent()))
                    .parameter(PromptParameter.builder().role(message.getRole()).build())
                    .build();
            promptInputs.add(promptInput);
        }
        return promptInputs;
    }


    public void onSuccess(ResponseSessionContext context, String responseId) {
        onSuccess(context, responseId, null);
    }

    public void onSuccess(ResponseSessionContext context, String responseId, ChatMessage responseMessage) {
        if (context == null  || StrUtil.isBlank(responseId)) {
            return;
        };
        List<ChatMessage> currentSystemMessages = ChatCompletionUtil.getSystemMessages(context.getAllHistoryChatMessage());
        ResponseSessionState responseSessionState = getCachedSession(context.getAllHistoryChatMessage());
        if(responseSessionState != null && context.getPreviousResponseId() != null) {
            responseSessionState.setConversationStartIndex(context.getSplitStartIndex());
            List<ChatMessage> historyChatMessage = new ArrayList<>(context.getAllHistoryChatMessage());
            historyChatMessage.add(responseMessage);
            responseSessionState.setPreviousResponseId(responseId);
            // Refresh cached system messages so the next turn can decide whether to resend them.
            responseSessionState.setSystemMessages(currentSystemMessages);
            setCachedSession(historyChatMessage, responseSessionState);
            removeCachedSession(context.getAllHistoryChatMessage(), false);
        } else {
            ResponseSessionState state = new ResponseSessionState();
            state.setPreviousResponseId(responseId);
            state.setBackend(context.getBackend());
            state.setModel(context.getModel());
            state.setProtocol(context.getProtocol());
            state.setConversationStartIndex(context.getSplitStartIndex());
            state.setSystemMessages(currentSystemMessages);
            List<ChatMessage> normalizeMessages = new ArrayList<>(context.getAllHistoryChatMessage());
            normalizeMessages.add(responseMessage);
            setCachedSession(normalizeMessages, state);
        }
    }

    private boolean isSameSystemMessages(List<ChatMessage> current, List<ChatMessage> cached) {
        if (current == null) {
            current = Collections.emptyList();
        }
        if (cached == null) {
            cached = Collections.emptyList();
        }
        return current.equals(cached);
    }

    private boolean isReusable(ResponseSessionState state, ResponseSessionContext context) {
        return state != null
                && StrUtil.isNotBlank(state.getPreviousResponseId())
                && StrUtil.equals(state.getBackend(), context.getBackend())
                && StrUtil.equals(state.getModel(), context.getModel())
                && StrUtil.equals(state.getProtocol(), context.getProtocol());
    }

    private List<ChatMessage> getIncrementalMessages(List<ChatMessage> previous, List<ChatMessage> current) {
        if (previous == null || current == null || current.size() <= previous.size()) {
            return null;
        }
        for (int i = 0; i < previous.size(); i++) {
            if (!previous.get(i).equals(current.get(i))) {
                return null;
            }
        }
        return normalizeMessages(current.subList(previous.size(), current.size()));
    }

    private List<ChatMessage> normalizeMessages(List<ChatMessage> messages) {
        List<ChatMessage> normalized = new ArrayList<>();
        if (messages == null) {
            return normalized;
        }
        for (ChatMessage message : messages) {
            ChatMessage copy = normalizeMessage(message);
            if (copy != null) {
                normalized.add(copy);
            }
        }
        return normalized;
    }

    private ChatMessage normalizeMessage(ChatMessage message) {
        if (message == null) {
            return null;
        }
        ChatMessage copy = new ChatMessage();
        copy.setRole(message.getRole());
        copy.setContent(message.getContent());
        copy.setReasoning_content(message.getReasoning_content());
        copy.setTool_call_id(message.getTool_call_id());
        if (message.getTool_calls() != null) {
            List<ToolCall> calls = new ArrayList<>();
            for (ToolCall toolCall : message.getTool_calls()) {
                ToolCall callCopy = new ToolCall();
                callCopy.setId(toolCall.getId());
                callCopy.setType(toolCall.getType());
                if (toolCall.getFunction() != null) {
                    ToolCallFunction function = new ToolCallFunction();
                    function.setName(toolCall.getFunction().getName());
                    function.setArguments(toolCall.getFunction().getArguments());
                    callCopy.setFunction(function);
                }
                calls.add(callCopy);
            }
            copy.setTool_calls(calls);
        }
        return copy;
    }
}
