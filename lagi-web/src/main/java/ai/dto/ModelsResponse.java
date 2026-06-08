package ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelsResponse implements Serializable {
    private List<ModelInfo> models;
    @JsonProperty("console_default_model")
    private String consoleDefaultModel;
}
