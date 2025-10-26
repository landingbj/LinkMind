package ai.servlet.dto;

import lombok.Data;

@Data
public class ModelPredictRequest {
    private String model_path;
    private String image_path;
    private boolean use_gpu = true;
    private double conf_threshold = 0.25;
    private double iou_threshold = 0.45;
    private boolean save_result = true;
}
