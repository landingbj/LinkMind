package ai.llm.responses;

import lombok.Data;

import java.util.List;

@Data
public class QwenResponseCreateRequest {
    private String model;
    private Object input;
    private String previous_response_id;
    private Boolean stream;
    private Integer max_output_tokens;
    private List<ResponseTool> tools;
    private Object tool_choice;
    private Boolean parallel_tool_calls;
    private ResponseText text;
    private Double temperature;
    private Double top_p;
}
