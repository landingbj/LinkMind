package ai.dto;

import lombok.*;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProjectMaterialRequest {
    private String projectType;
    private String enterpriseInfo;
    private String projectInfo;
    private String templateType;
    private String userId;
}

