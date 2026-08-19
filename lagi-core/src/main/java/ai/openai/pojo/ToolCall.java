package ai.openai.pojo;

import lombok.Data;

@Data
public class ToolCall {
    private Integer index;
    private String id;
    private String type;
    private ToolCallFunction function;
}
