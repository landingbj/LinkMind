package ai.openai.pojo;

import lombok.Data;

import java.util.Map;

@Data
public class JsonSchema {
    private String name;
    private Map schema;
    private Boolean strict = Boolean.TRUE;
}
