package ai.llm.responses;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class ResponseStreamEvent {
    private String type;
    private JsonNode response;
    private JsonNode item;
    private String delta;
    private String response_id;
}
