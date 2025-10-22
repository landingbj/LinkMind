package ai.servlet.dto;

import lombok.Data;

@Data
public class DatasetUploadResponse {
    private String status;
    private String dataset_name;
    private String dataset_path;
    private String message;
    private String created_at;
}
