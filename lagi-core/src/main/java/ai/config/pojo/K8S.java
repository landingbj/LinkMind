package ai.config.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class K8S {
    @JsonProperty("server")
    private String server;
    @JsonProperty("namespace")
    private String namespace;
    @JsonProperty("token")
    private String token;
    @JsonProperty("kube_config")
    private String kubeConfigPath;
    private List<ModelMapper> models;
}
