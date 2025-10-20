package ai.agent.dto;

import ai.config.pojo.AgentConfig;
import lombok.Data;

@Data
public class OrchestrationItem extends AgentConfig {
    private String task;
    private String logic;

    public OrchestrationItem(String task, String logic) {
    }
}
