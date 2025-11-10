package ai.dto;

import lombok.*;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProjectMaterialResponse {
    private String documentId;
    private String title;
    private String content;
    private String generatedAt;
}

