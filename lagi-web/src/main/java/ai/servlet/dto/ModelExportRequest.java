package ai.servlet.dto;

import lombok.Data;

@Data
public class ModelExportRequest {
    private String model_path;
    private String export_format = "onnx";
    private boolean use_gpu = true;
    private int imgsz = 640;
    private boolean simplify = true;
}
