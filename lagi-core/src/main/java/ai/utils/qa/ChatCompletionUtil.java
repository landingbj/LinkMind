package ai.utils.qa;

import ai.openai.pojo.ChatCompletionChoice;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.utils.LagiGlobal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ChatCompletionUtil {
    public static String getLastMessage(ChatCompletionRequest chatCompletionRequest) {
        List<ChatMessage> messages = chatCompletionRequest.getMessages();
        String content = messages.get(messages.size() - 1).getContent().trim();
        return content;
    }


    public static Integer getLastQAUserIndex(List<ChatMessage> messages) {
        boolean lastAssistantIndex = false;
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if(lastAssistantIndex) {
                if(message.getRole().equals(LagiGlobal.LLM_ROLE_USER)) {
                    return i;
                }
            }
            if(message.getRole().equals(LagiGlobal.LLM_ROLE_ASSISTANT) && message.getTool_calls() != null) {
                lastAssistantIndex = true;
            }
        }
        return null;
    }

    public static List<ChatMessage> getIncrementMessages(List<ChatMessage> messages) {
        Integer lastAssistantIndex = ChatCompletionUtil.findLastAssistantIndex(messages);
        if(lastAssistantIndex == null) {
            return messages.stream().filter(message -> !message.getRole().equals(LagiGlobal.LLM_ROLE_SYSTEM)).collect(Collectors.toList());
        }
        return messages.subList(lastAssistantIndex + 1, messages.size());
    }

    public static List<ChatMessage> getHistoryMessages(List<ChatMessage> messages) {
        Integer lastAssistantIndex = ChatCompletionUtil.findLastAssistantIndex(messages);
        Integer firstUserIndex = ChatCompletionUtil.getFirstUserIndex(messages);
        if(lastAssistantIndex == null || firstUserIndex == null) {
            return Collections.emptyList();
        }
        return messages.subList(firstUserIndex, lastAssistantIndex + 1);
    }

    public static List<ChatMessage> getSystemMessages(List<ChatMessage> messages) {
        return messages.stream().filter(message -> message.getRole().equals(LagiGlobal.LLM_ROLE_SYSTEM)).collect(Collectors.toList());
    }

    public static Integer getFirstUserIndex(List<ChatMessage> messages) {
        for (int i = 0; i < messages.size(); i++) {
            String role = messages.get(i).getRole();
            if(role.equals(LagiGlobal.LLM_ROLE_USER)) {
                return i;
            }
        }
        return null;
    }

    public static Integer findValidAssistantIndex(List<ChatMessage> messages) {
        for (int i = messages.size() -1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (LagiGlobal.LLM_ROLE_TOOL.equals(msg.getRole())
                    || msg.getTool_calls() != null
                    || msg.getContent() == null
                    || msg.getContent().trim().isEmpty()) {
                continue;
            }
            if (LagiGlobal.LLM_ROLE_ASSISTANT.equals(msg.getRole())) {
                return i;
            }
        }
        return null;
    }

    public static Integer findLastAssistantIndex(List<ChatMessage> messages) {
        for (int i = messages.size() -1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (LagiGlobal.LLM_ROLE_ASSISTANT.equals(msg.getRole())) {
                return i;
            }
        }
        return null;
    }

    public static Integer findLastAssistantIndex(List<ChatMessage> messages, int lastIndex) {
        for (int i = messages.size() -1; i >= 0 && i > lastIndex; i--) {
            ChatMessage msg = messages.get(i);
            if (LagiGlobal.LLM_ROLE_ASSISTANT.equals(msg.getRole())) {
                return i;
            }
        }
        return null;
    }

    public static void setLastMessage(ChatCompletionRequest chatCompletionRequest, String lastMessage) {
        List<ChatMessage> messages = chatCompletionRequest.getMessages();
        messages.get(messages.size() - 1).setContent(lastMessage);
        ;
    }

    public static void setResultContent(ChatCompletionResult chatCompletionResult, String content) {
        chatCompletionResult.getChoices().get(0).getMessage().setContent(content);
    }

    public static String getFirstAnswer(ChatCompletionResult chatCompletionResult) {
        String content = chatCompletionResult.getChoices().get(0).getMessage().getContent();
        return content;
    }

    public static String getFirstDelta(ChatCompletionResult chatCompletionResult) {
        String content = chatCompletionResult.getChoices().get(0).getDelta().getContent();
        return content;
    }

    public static String getReasoningContent(ChatCompletionResult chatCompletionResult) {
        String reasoningContent = chatCompletionResult.getChoices().get(0).getMessage().getReasoning_content();
        return reasoningContent;
    }

    public static ChatCompletionResult toChatCompletionResult(String message, String model) {
        ChatCompletionResult result = new ChatCompletionResult();
        result.setId(UUID.randomUUID().toString());
        result.setCreated(getCurrentUnixTimestamp());
        ChatCompletionChoice choice = new ChatCompletionChoice();
        choice.setIndex(0);
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setContent(message);
        chatMessage.setRole("assistant");
        choice.setMessage(chatMessage);
        choice.setFinish_reason("stop");
        List<ChatCompletionChoice> choices = new ArrayList<>();
        choices.add(choice);
        result.setChoices(choices);
        return result;
    }

    public static long getCurrentUnixTimestamp() {
        return System.currentTimeMillis() / 1000L;
    }

    public static ChatCompletionRequest cloneChatCompletionRequest(ChatCompletionRequest request) {
        ChatCompletionRequest cloned = new ChatCompletionRequest();
        cloned.setSessionId(request.getSessionId());
        cloned.setModel(request.getModel());
        cloned.setTemperature(request.getTemperature());
        cloned.setMax_tokens(request.getMax_tokens());
        cloned.setCategory(request.getCategory());
        cloned.setStream(request.getStream());
        cloned.setTools(request.getTools());
        cloned.setTool_choice(request.getTool_choice());
        cloned.setParallel_tool_calls(request.getParallel_tool_calls());
        cloned.setPresence_penalty(request.getPresence_penalty());
        cloned.setFrequency_penalty(request.getFrequency_penalty());
        cloned.setTop_p(request.getTop_p());
        cloned.setResponse_format(request.getResponse_format());
        cloned.setStream_options(request.getStream_options());
        cloned.setLogprobs(request.getLogprobs());
        if (request.getMessages() != null) {
            List<ChatMessage> clonedMessages = new ArrayList<>();
            for (ChatMessage message : request.getMessages()) {
                ChatMessage clonedMessage = new ChatMessage();
                clonedMessage.setContent(message.getContent());
                clonedMessage.setRole(message.getRole());
                clonedMessage.setReasoning_content(message.getReasoning_content());
                clonedMessage.setTool_call_id(message.getTool_call_id());
                clonedMessage.setTool_calls(message.getTool_calls());
                clonedMessages.add(clonedMessage);
            }
            cloned.setMessages(clonedMessages);
        }
        return cloned;
    }

    public static String getPrompt(String contextText, String lastMessage) {
        String prompt = "以下是背景信息。\n---------------------\n" + contextText
                + "---------------------\n根据上下文信息而非先前知识，回答这个问题: " + lastMessage + ";\n";
        return prompt;
    }
}
