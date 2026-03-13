package ai.llm.responses;

import ai.openai.pojo.ChatMessage;
import lombok.Data;

import java.util.List;

@Data
public class ResponseSessionContext {
    private String sessionId;
    private String previousResponseId;
    private List<ChatMessage> normalizedMessages;
    private List<ChatMessage> inputMessages;
    private String backend;
    private String model;
    private String protocol;
    private boolean stateful;
}
