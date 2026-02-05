package ai.config.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class ModelMapper {
    @JsonProperty("parse_type")
    private String parseType;
    @JsonProperty("model_pattern")
    private String modelPattern;
    @JsonProperty("attr_mapping")
    private String attrMapping;
    @JsonProperty("attr_access")
    private String attrAccess;
    @JsonProperty("train_cmd")
    private String trainCmd;
    @JsonProperty("predict_cmd")
    private String predictCmd;
    @JsonProperty("convert_cmd")
    private String convertCmd;
    @JsonProperty("evaluate_cmd")
    private String evaluateCmd;
    @JsonProperty("k8s_train_path")
    private String k8sTrainPath;
    @JsonProperty("k8s_predict_path")
    private String k8sPredictPath;
    @JsonProperty("k8s_convert_path")
    private String k8sConvertPath;
    @JsonProperty("k8s_evaluate_path")
    private String k8sEvaluatePath;
}
