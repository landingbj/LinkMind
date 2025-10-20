package ai.agent.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class ManagerModel {
    private Integer id;
    private String userId;
    private String modelName;
    private Integer online;
    private String apiKey;
    private String modelType;
    private String endpoint;
    private Integer status;
}
