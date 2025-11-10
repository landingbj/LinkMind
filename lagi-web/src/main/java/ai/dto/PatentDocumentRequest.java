package ai.dto;

import lombok.*;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PatentDocumentRequest {
    private String patentType;
    private String technicalPoints;
    private String inventorInfo;
    private String userId;
}

