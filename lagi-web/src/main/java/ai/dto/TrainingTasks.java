package ai.dto;

import lombok.Data;

@Data
public class TrainingTasks {

    private Long id;
    private String taskId;
    private String datasetName;
    private String datasetPath;
    private String modelPath;
    private Integer epochs; // 总轮次
    private Integer batchSize; // 批次大小（修正字段名）
    private Integer imageSize; // 图片尺寸（修正字段名）
    private Boolean useGpu;
    private String status; // 存储状态字符串（如"running"）
    private String progress; // 存储百分比字符串（如"45%"）
    private Integer currentEpoch;
    private String startTime;
    private String endTime;
    private String trainDir;
    private String errorMsg; // 新增：错误信息
    private String createdAt;
    private String updatedAt;
    private String userId;
    private Integer templateId;

    private Integer isDeleted; //0 = 未删除（默认），1 = 已删除
    private String deletedAt;

    // 辅助方法：设置状态枚举
    public void setStatusEnum(TaskStatus status) {
        this.status = status.getValue();
    }

    // 辅助方法：获取状态枚举
    public TaskStatus getStatusEnum() {
        return TaskStatus.parse(this.status);
    }
}
