package ai.llm.responses;

import lombok.Data;

@Data
public class QwenResponseInputItem {
    private String type;
    private String role;
    private Object content;
    private String call_id;
    private String name;
    private String arguments;
    private String output;
}
