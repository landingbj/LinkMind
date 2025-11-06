package ai.openai.pojo;

import lombok.Data;

@Data
public class ResponseFormat {
    private String type;
    private JsonSchema json_schema;
}
