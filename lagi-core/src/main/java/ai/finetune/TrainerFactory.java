package ai.finetune;

public class TrainerFactory {
    /**
     * 根据模型类型和执行模式创建训练器
     * @param modelType 模型类型 (yolo, deeplab, tracknetv3)
     * @param executionMode 执行模式 (docker, k8s)
     * @return TrainerInterface实例
     */
    public static TrainerInterface createTrainer(String modelType, String executionMode) {
        if ("docker".equalsIgnoreCase(executionMode)) {
            return createDockerTrainer(modelType);
        } else if ("k8s".equalsIgnoreCase(executionMode)) {
            return createK8sTrainer(modelType);
        } else {
            throw new IllegalArgumentException("不支持的执行模式: " + executionMode);
        }
    }

    private static TrainerInterface createDockerTrainer(String modelType) {
        switch (modelType.toLowerCase()) {
            case "yolo":
            case "yolov8":
            case "yolov11":
                return new YoloTrainerAdapter();
            case "deeplab":
            case "deeplabv3":
                return new DeeplabAdapter();
            case "tracknetv3":
            case "tracknet":
                return new TrackNetV3Adapter();
            default:
                throw new IllegalArgumentException("不支持的模型类型: " + modelType);
        }
    }

    private static TrainerInterface createK8sTrainer(String modelType) {
        switch (modelType.toLowerCase()) {
            case "yolo":
            case "yolov8":
            case "yolov11":
                return new YoloK8sAdapter();
            case "deeplab":
            case "deeplabv3":
                return new DeeplabK8sAdapter();
            case "tracknetv3":
            case "tracknet":
                return new TrackNetV3K8sAdapter();
            default:
                throw new IllegalArgumentException("不支持的模型类型: " + modelType);
        }
    }
}
