package ai.llm.responses;

import ai.openai.pojo.ChatMessage;
import lombok.Data;

import java.util.List;

@Data
public class ResponseSessionState {
    private String previousResponseId;
    private List<ChatMessage> messages;
    private String backend;
    private String model;
    private String protocol;
}
