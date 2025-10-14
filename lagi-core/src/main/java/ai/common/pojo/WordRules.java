package ai.common.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@ToString
@Builder
public class WordRules {
    private String mask;
    private Integer level;
    @JsonProperty("enable_request_filter")
    private boolean enableRequestFilter;
    @JsonProperty("request_filter_message")
    private String requestFilterMessage;
    private List<WordRule> rules;

    public WordRules() {
        mask = "...";
        level = 3;
    }
}
