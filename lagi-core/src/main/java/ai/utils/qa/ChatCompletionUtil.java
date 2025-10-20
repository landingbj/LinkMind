package ai.utils.qa;

import ai.openai.pojo.ChatCompletionChoice;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatCompletionUtil {
    public static String getLastMessage(ChatCompletionRequest chatCompletionRequest) {
        List<ChatMessage> messages = chatCompletionRequest.getMessages();
        String content = messages.get(messages.size() - 1).getContent().trim();
        return content;
    }

    public static String getLastUserContent(ChatCompletionRequest chatCompletionRequest) {
        List<ChatMessage> chatMessages = chatCompletionRequest.getMessages();
        for (int i = chatMessages.size()-1; i >= 0; i--) {
            ChatMessage chatMessage = chatMessages.get(i);
            if (chatMessage.getRole().equals("user")) {
                return chatMessage.getContent();
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
        cloned.setModel(request.getModel());
        cloned.setTemperature(request.getTemperature());
        cloned.setMax_tokens(request.getMax_tokens());
        cloned.setCategory(request.getCategory());
        cloned.setTools(request.getTools());
        if (request.getMessages() != null) {
            List<ChatMessage> clonedMessages = new ArrayList<>();
            for (ChatMessage message : request.getMessages()) {
                ChatMessage clonedMessage = new ChatMessage();
                clonedMessage.setContent(message.getContent());
                clonedMessage.setRole(message.getRole());
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
