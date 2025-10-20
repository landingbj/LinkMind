package ai.agent.dto;

import ai.config.pojo.AgentConfig;
import lombok.Data;

import java.util.List;

@Data
public class LagiAgentListResponse {
    private String status;
    private Integer totalRow;
    private Integer totalPage;
    private Integer pageNumber;
    private Integer pageSize;
    private List<AgentConfig> data;
}
