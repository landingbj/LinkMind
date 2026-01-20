package ai.dto;

import lombok.Data;

@Data
public class VersionManagementDto {


    private Long id;

    private String versionId; // 数据库字段version_id → 驼峰命名versionId

    private String versionName;

    private String versionNumber;

    private String versionType;

    private String modelName;

    /**
     * 关联的模型ID（FK to models.id）
     * @deprecated 已从 modelIntroductionId 改为 modelId，model_introduction 表已合并到 models 表
     */
    @Deprecated
    private Integer modelIntroductionId;
    
    /**
     * 关联的模型ID（FK to models.id）
     * 替代原来的 modelIntroductionId
     */
    private Long modelId;

    private Integer frameworkId;

    private Float accuracyRate;

    private String versionDescription;

    private String tags;

    private String status;

    private String fileSize;

    private Integer subDirCount;

    private String creator;

    private String createAt;

    private String updateAt;

    private Integer isDeleted;
}
