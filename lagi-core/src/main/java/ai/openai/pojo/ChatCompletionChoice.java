package ai.openai.pojo;

import lombok.Data;

import java.io.Serializable;

@Data
public class ChatCompletionChoice implements Serializable {
    private int index;
    private ChatMessage message;
    private ChatMessage delta;
    private String finish_reason;
    private Logprobs logprobs;
}
