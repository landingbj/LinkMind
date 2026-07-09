package ai.migrate.pojo;

import lombok.Data;

@Data
public class AgentImportCommitRequest {
    private String previewId;
    private String agentId;
    private String displayName;
    private String systemPrompt;
}
