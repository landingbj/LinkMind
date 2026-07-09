package ai.migrate.pojo;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class ImportedAgentProfile {
    @JsonAlias({"agent_id"})
    private String agentId;
    @JsonAlias({"display_name"})
    private String displayName;
    private String description;
    @JsonAlias({"system_path"})
    private String systemPath = "system.md";
    @JsonAlias({"history_path"})
    private String historyPath = "history.jsonl";
    /**
     * Compatibility only. Older imported agent.yaml files may still contain this
     * field, but runtime history loading no longer uses it to truncate context.
     */
    @JsonAlias({"max_history_messages"})
    private Integer maxHistoryMessages;
    @JsonAlias({"import_mode"})
    private String importMode;
    @JsonAlias({"created_at"})
    private String createdAt;
}
