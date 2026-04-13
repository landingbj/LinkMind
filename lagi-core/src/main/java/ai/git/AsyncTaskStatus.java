package ai.git;

import java.time.LocalDateTime;

public class AsyncTaskStatus {
    private String taskId;
    private TaskStatus status;
    private String message;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public AsyncTaskStatus(String taskId, TaskStatus status) {
        this.taskId = taskId;
        this.status = status;
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { 
        this.status = status; 
        this.updateTime = LocalDateTime.now();
    }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public LocalDateTime getCreateTime() { return createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
}
