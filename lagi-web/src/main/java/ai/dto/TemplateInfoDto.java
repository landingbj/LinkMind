package ai.dto;


import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class TemplateInfoDto implements Serializable {


    private Integer id;

    private String templateId;

    private String name;

    private String description;

    private String type;

    private String category;

    private Integer difficulty;

    private String estimatedTime;

    private List< String> tags;

    private Boolean isBuiltIn;

    private Integer usageCount;

    private Float rating;

    private Boolean isPublic;

    private String createdAt;

    private String updatedAt;
}
