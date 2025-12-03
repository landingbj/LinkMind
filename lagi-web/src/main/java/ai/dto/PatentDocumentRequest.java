package ai.dto;

import com.google.gson.annotations.SerializedName;
import lombok.*;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PatentDocumentRequest {
    @SerializedName("patentType")
    private String patentType;
    
    @SerializedName("technicalPoints")
    private String technicalPoints;
    
    @SerializedName("inventorInfo")
    private String inventorInfo;
    
    @SerializedName("userId")
    private String userId;
}

