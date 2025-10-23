package ai.servlet.dto;

import lombok.Data;
import java.util.List;

@Data
public class ModelValidateResponse {
    private String status;
    private String model_path;
    private String dataset_path;
    private Metrics metrics;
    private String validate_time;

    @Data
    public static class Metrics {
        private double mAP50;
        private double mAP50_95;
        private double precision;
        private double recall;
        private List<ClassMetrics> class_metrics;
        private LossMetrics loss_metrics;
    }

    @Data
    public static class ClassMetrics {
        private int class_id;
        private String class_name;
        private double mAP50;
        private double precision;
        private double recall;
    }

    @Data
    public static class LossMetrics {
        private double box_loss;
        private double cls_loss;
        private double obj_loss;
    }
}
