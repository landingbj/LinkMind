package ai.dto;

import com.google.gson.annotations.SerializedName;
import lombok.*;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PatentDocumentResponse {
    private String patentId;
    private String title;
    @SerializedName("abstract")
    private String abstractText;
    private String content;
    private String generatedAt;
}

