package ai.config.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Builder
@JsonDeserialize(using = FiltersConfigDeserializer.class)
public class FiltersConfig {
    private Boolean enable = true;
    @JsonProperty("items")
    private List<FilterConfig> items;
}
