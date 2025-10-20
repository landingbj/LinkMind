package ai.agent.dto;

import lombok.Data;

import java.util.List;

@Data
public class FeeRequiredAgentRequest {
    private List<Integer> agentIds;
    private Boolean isFeeRequired;
}
