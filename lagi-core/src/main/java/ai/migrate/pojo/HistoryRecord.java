package ai.migrate.pojo;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.Map;

@Data
public class HistoryRecord {
    @JsonAlias({"agent_id"})
    private String agentId;
    @JsonAlias({"session_id"})
    private String sessionId;
    private String role;
    private String content;
    private String source;
    @JsonAlias({"created_at"})
    private String createdAt;
    private Integer index;
    private Map<String, Object> metadata;
}
