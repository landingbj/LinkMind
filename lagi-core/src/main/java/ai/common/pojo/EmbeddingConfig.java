package ai.common.pojo;

import lombok.Data;

@Data
public class EmbeddingConfig {
    private String backend;
    private String type;
    private String api_key;
    private String model_name;
    private String api_endpoint;
    private String secret_key;
    private String model_path;
}
