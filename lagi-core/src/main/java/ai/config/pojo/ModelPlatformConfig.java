package ai.config.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class ModelPlatformConfig {
    @JsonProperty("finetune")
    private FineTuneConfig fineTuneConfig =  new FineTuneConfig();;
    @JsonProperty("deploy")
    private DeployConfig deployConfig =  new DeployConfig();;
    private Boolean remote;
    private String remoteServiceUrl;

}
