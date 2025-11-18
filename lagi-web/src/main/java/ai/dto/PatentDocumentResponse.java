package ai.dto;

import com.google.gson.annotations.SerializedName;
import lombok.*;

import java.util.List;

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
    private List<String> filename;
    private List<String> filepath;
    private String context;
    private List<String> contextChunkIds;
}

