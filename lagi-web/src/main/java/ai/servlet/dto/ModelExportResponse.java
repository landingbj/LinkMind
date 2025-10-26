package ai.servlet.dto;

import lombok.Data;

@Data
public class ModelExportResponse {
    private String status;
    private String model_path;
    private String export_format;
    private String export_path;
    private String model_size;
    private String export_time;
    private String message;
}
