package ai.llm.responses;

import lombok.Data;

@Data
public class ResponseInputContent {
    private String type;
    private String text;
    private String image_url;
    private String detail;
}
