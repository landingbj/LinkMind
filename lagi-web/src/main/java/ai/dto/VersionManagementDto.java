package ai.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class VersionManagementDto {


    private Long id;

    private String versionId; // 数据库字段version_id → 驼峰命名versionId

    private String versionName;

    private String versionNumber;

    private String versionType;

    private String modelName;

    private Integer modelIntroductionId;

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
