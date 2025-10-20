package ai.agent.dto;

import ai.config.pojo.AgentConfig;
import lombok.Data;

@Data
public class LagiAgentResponse {
    private String status;
    private AgentConfig data;
}
