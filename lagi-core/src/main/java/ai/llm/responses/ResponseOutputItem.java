package ai.llm.responses;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class ResponseOutputItem {
    private String id;
    private String type;
    private String role;
    private JsonNode content;
    private String call_id;
    private String name;
    private String arguments;
}
