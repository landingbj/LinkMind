package ai.openai.pojo;

import lombok.Data;
import lombok.ToString;

import java.util.List;
@Data
@ToString
public class ChatCompletionResult {
    private String id;
    private String object;
    private long created;
    private String model;
    private List<ChatCompletionChoice> choices;
    private Usage usage;
    private String system_fingerprint;
}
