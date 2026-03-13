package ai.llm.responses;

import lombok.Data;

import java.util.List;

@Data
public class ResponseInputItem {
    private String type;
    private String role;
    private List<ResponseInputContent> content;
    private String call_id;
    private String name;
    private String arguments;
    private String output;
}
