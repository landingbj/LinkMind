package ai.agent.customer.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode
public class ToolLlmRequest {
    private String prompt;
    private List<List<String>> history;
    private String userMsg;
}
