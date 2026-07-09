package ai.migrate.pojo;

import lombok.Data;

@Data
public class AgentListItem {
    private String agentId;
    private String title;
    private String description;
    private String source;
    private Boolean imported;
    private String templateIssues;
}
