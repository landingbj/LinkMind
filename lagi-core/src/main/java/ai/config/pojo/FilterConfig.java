package ai.config.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class FilterConfig {
    private String name;
    private List<FilterRule> groups;
    private String rules;
    @JsonProperty("filter_window_length")
    private int filterWindowLength;
}
