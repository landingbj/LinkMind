package ai.dto;

public enum TaskStatus {

    PENDING("pending", "任务已创建但未开始"),
    RUNNING("running", "任务正在运行"),
    STOPPED("stopped", "任务已被手动停止"),
    COMPLETED("completed", "任务已正常完成"),
    FAILED("failed", "任务执行失败"),

    TASKRUNNING("run","训练任务已启动"),
    TASKSTOPPED("stop","训练任务已停止"),
    TASKRESUME("resume","训练任务已恢复");

    private final String value;
    private final String desc;

    TaskStatus(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public String getValue() {
        return value;
    }

    public static TaskStatus parse(String value) {
        for (TaskStatus status : TaskStatus.values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的任务状态：" + value);
    }

}
