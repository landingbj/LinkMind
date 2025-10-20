package ai.agent.dto;

import lombok.Data;

@Data
public class DeductExpensesRequest {
    private String userId;
    private Integer agentId;
}
