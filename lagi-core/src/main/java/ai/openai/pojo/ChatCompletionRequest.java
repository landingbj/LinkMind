package ai.openai.pojo;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ChatCompletionRequest {
    @JsonAlias({"session_id"})
    private String sessionId;
    @JsonAlias({"extra_body"})
    private ExtraBody extraBody;
    private String model;
    private double temperature;
    private Integer max_tokens;
    private Integer max_completion_tokens;
    private String category;
    private List<ChatMessage> messages;
    private Boolean stream;
    private List<Tool> tools;
    private String tool_choice;
    private Boolean parallel_tool_calls;
    private Double presence_penalty;
    private Double frequency_penalty;
    private Double top_p;
    private ResponseFormat response_format;
    private Map<String, Object> stream_options;
    private Boolean logprobs;
    private transient Boolean enableHook = true;
    private transient Boolean enableAfter = true;
    private Boolean store;
    private String apiKey;
}
