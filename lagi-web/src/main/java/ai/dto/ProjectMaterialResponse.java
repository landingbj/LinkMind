package ai.dto;

import lombok.*;

import java.util.List;

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
    private List<String> filename;
    private List<String> filepath;
    private String context;
    private List<String> contextChunkIds;
}

