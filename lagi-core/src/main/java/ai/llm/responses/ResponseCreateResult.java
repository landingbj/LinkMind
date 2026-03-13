package ai.llm.responses;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class ResponseCreateResult {
    private String id;
    private String model;
    private String status;
    private JsonNode output;
    private JsonNode usage;
}
