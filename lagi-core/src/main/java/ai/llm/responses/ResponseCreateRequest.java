package ai.llm.responses;

import lombok.Data;

import java.util.List;

@Data
public class ResponseCreateRequest {
    private String model;
    private List<ResponseInputItem> input;
    private String previous_response_id;
    private Boolean stream;
    private Integer max_output_tokens;
    private List<ResponseTool> tools;
    private Object tool_choice;
    private Boolean parallel_tool_calls;
    private ResponseText text;
}
