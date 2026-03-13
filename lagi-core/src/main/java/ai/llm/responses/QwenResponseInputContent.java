package ai.llm.responses;

import ai.openai.pojo.MultiModalContent;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class QwenResponseInputContent {
    private String type;
    private String text;
    @JsonProperty("image_url")
    private MultiModalContent.ImageUrl imageUrl;
}
