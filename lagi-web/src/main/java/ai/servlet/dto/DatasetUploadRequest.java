package ai.servlet.dto;

import lombok.Data;

@Data
public class DatasetUploadRequest {
    private String dataset_name;
    private String description;
    private String user_id;
}
