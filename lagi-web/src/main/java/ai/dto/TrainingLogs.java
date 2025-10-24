package ai.dto;


import lombok.Data;

@Data
public class TrainingLogs {

    private Integer id;

    private String taskId;

    private String logLevel;

    private String logMessage;

    private String createdAt;

    private String logFilePath;
}
