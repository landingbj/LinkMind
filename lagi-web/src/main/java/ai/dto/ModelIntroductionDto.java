package ai.dto;


import lombok.Data;

@Data
public class ModelIntroductionDto {

    private String modelName;
    private String version;
    private String title;
    private String description;
    private String detailContent;
    private Integer categoryId;
    private String modelType;
    private String framework;
    private String algorithm;
    private String inputShape;
    private String outputShape;
    private Integer totalParams;
    private Integer trainableParams;
    private Integer nonTrainableParams;
    private Float accuracy;
    private Float precision;
    private Float recall;
    private Float f1Score;
    private String tags;
    private String status;
    private String author;
    private String docLink;
    private String iconLink;
    private String createdAt;
    private Integer isDeleted;
}
