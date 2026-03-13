package ai.llm.responses;

import lombok.Data;

import java.util.Map;

@Data
public class ResponseTextFormat {
    private String type;
    private String name;
    private Map schema;
    private Boolean strict;
}
