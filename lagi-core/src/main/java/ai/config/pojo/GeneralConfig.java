package ai.config.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class GeneralConfig {
    @JsonProperty("local_api_key_editable")
    private Boolean localApiKeyEditable = true;

    @JsonProperty("cascade_api_address")
    private String cascadeApiAddress;
}
