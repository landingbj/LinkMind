package ai.llm.responses;

import ai.openai.pojo.Parameters;
import lombok.Data;

@Data
public class ResponseTool {
    private String type;
    private String name;
    private String description;
    private Parameters parameters;
    private Boolean strict;
}
