package ai.servlet.dto;

import lombok.Data;
import java.util.List;

@Data
public class ModelPredictResponse {
    private String status;
    private String model_path;
    private String image_path;
    private List<Prediction> predictions;
    private String result_save_path;
    private String predict_time;

    @Data
    public static class Prediction {
        private int class_id;
        private String class_name;
        private double confidence;
        private List<Double> bbox;
        private String bbox_format;
    }
}
