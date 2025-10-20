package ai.agent.dto;

import ai.config.pojo.AgentConfig;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaidLagiAgent extends AgentConfig {
    private BigDecimal balance;
}
