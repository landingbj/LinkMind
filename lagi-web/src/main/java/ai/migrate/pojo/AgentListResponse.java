package ai.migrate.pojo;

import lombok.Data;

import java.util.List;

@Data
public class AgentListResponse {
    private String status = "success";
    private List<AgentListItem> agents;
}
