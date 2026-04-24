package ai.llm.responses;

import ai.openai.pojo.ChatMessage;
import lombok.Data;

import java.util.List;
import java.util.Vector;

@Data
public class ResponseSessionState {
    private String previousResponseId;
    private List<ChatMessage> messages;
    private String backend;
    private String model;
    private String protocol;
    private Integer ConversationStartIndex;
    // Cached system messages sent along with the previousResponseId.
    // Used to decide whether the next turn needs to resend system messages.
    private List<ChatMessage> systemMessages;
}
