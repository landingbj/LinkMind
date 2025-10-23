package ai.servlet.dto;

import lombok.Data;

@Data
public class ModelValidateRequest {
    private String model_path;
    private String dataset_path;
    private boolean use_gpu = true;
    private int imgsz = 640;
}
