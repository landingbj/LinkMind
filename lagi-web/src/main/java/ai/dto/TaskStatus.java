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

    public String getDesc() {
        return desc;
    }

    public static TaskStatus parse(String value) {
        for (TaskStatus status : TaskStatus.values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的任务状态：" + value);
    }

    // 新增：判断是否为终端状态（无需继续轮询）
    public boolean isTerminal() {
        // 终端状态：STOPPED、COMPLETED、FAILED、TASKSTOPPED
        return this == STOPPED
                || this == COMPLETED
                || this == FAILED
                || this == TASKSTOPPED;
    }

    // 新增：从训练平台返回的状态字符串解析为本地枚举
    // （平台返回的status可能是"completed"、"failed"等，需要映射到本地枚举）
    public static TaskStatus fromPlatformStatus(String platformStatus) {
        if (platformStatus == null) {
            return RUNNING; // 默认视为运行中
        }
        String normalized = platformStatus.trim().toLowerCase();
        // 映射平台状态到本地枚举（根据实际平台返回值调整）
        switch (normalized) {
            case "completed":
            case "success":
                return COMPLETED;
            case "failed":
            case "error":
                return FAILED;
            case "stopped":
            case "cancelled":
                return STOPPED; // 或TASKSTOPPED，根据平台实际返回值选择
            case "running":
            case "in_progress":
                return RUNNING;
            case "resumed":
                return TASKRESUME;
            default:
                // 未知状态默认视为运行中（避免误判终止）
                return RUNNING;
        }
    }

}
