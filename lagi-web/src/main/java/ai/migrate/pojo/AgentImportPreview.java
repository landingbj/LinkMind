package ai.migrate.pojo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AgentImportPreview {
    private String previewId;
    private String suggestedAgentId;
    private String suggestedName;
    private String parseMode;
    private Integer turnCount;
    private Integer messageCount;
    private String source;
    private String rawText;
    private List<ParsedMessage> messages = new ArrayList<>();
    private List<ParsedMessage> samples = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
}
