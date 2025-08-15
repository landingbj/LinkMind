package ai.servlet.passenger;

import java.time.LocalDateTime;

/**
 * 开关门状态消息实体类
 */
public class DoorStatusMessage {

    /**
     * 车辆自编号
     */
    private String busSelfNo;

    /**
     * 车辆id
     */
    private Long busId;

    /**
     * 门1开关状态
     * 0x0:Closed
     * 0x1:Open
     * 0x2:Error
     * 0x3:Invalid
     */
    private Integer door1OpenSts;

    /**
     * 门3开关状态
     * 0x0:Closed
     * 0x1:Open
     * 0x2:Error
     * 0x3:Invalid
     */
    private Integer door3OpenSts;

    /**
     * 门5开关状态
     * 0x0:Closed
     * 0x1:Open
     * 0x2:Error
     * 0x3:Invalid
     */
    private Integer door5LockSts;

    /**
     * 业务上报时间
     */
    private LocalDateTime time;

    // 构造函数
    public DoorStatusMessage() {}

    public DoorStatusMessage(String busSelfNo, Long busId, Integer door1OpenSts,
                             Integer door3OpenSts, Integer door5LockSts, LocalDateTime time) {
        this.busSelfNo = busSelfNo;
        this.busId = busId;
        this.door1OpenSts = door1OpenSts;
        this.door3OpenSts = door3OpenSts;
        this.door5LockSts = door5LockSts;
        this.time = time;
    }

    // Getter和Setter方法
    public String getBusSelfNo() {
        return busSelfNo;
    }

    public void setBusSelfNo(String busSelfNo) {
        this.busSelfNo = busSelfNo;
    }

    public Long getBusId() {
        return busId;
    }

    public void setBusId(Long busId) {
        this.busId = busId;
    }

    public Integer getDoor1OpenSts() {
        return door1OpenSts;
    }

    public void setDoor1OpenSts(Integer door1OpenSts) {
        this.door1OpenSts = door1OpenSts;
    }

    public Integer getDoor3OpenSts() {
        return door3OpenSts;
    }

    public void setDoor3OpenSts(Integer door3OpenSts) {
        this.door3OpenSts = door3OpenSts;
    }

    public Integer getDoor5LockSts() {
        return door5LockSts;
    }

    public void setDoor5LockSts(Integer door5LockSts) {
        this.door5LockSts = door5LockSts;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public void setTime(LocalDateTime time) {
        this.time = time;
    }

    /**
     * 获取门状态描述
     */
    public String getDoorStatusDescription(Integer doorStatus) {
        if (doorStatus == null) return "Unknown";
        switch (doorStatus) {
            case 0: return "Closed";
            case 1: return "Open";
            case 2: return "Error";
            case 3: return "Invalid";
            default: return "Unknown";
        }
    }

    /**
     * 检查是否有门处于打开状态
     */
    public boolean hasOpenDoor() {
        return (door1OpenSts != null && door1OpenSts == 1) ||
                (door3OpenSts != null && door3OpenSts == 1) ||
                (door5LockSts != null && door5LockSts == 1);
    }

    /**
     * 检查是否有门处于错误状态
     */
    public boolean hasErrorDoor() {
        return (door1OpenSts != null && door1OpenSts == 2) ||
                (door3OpenSts != null && door3OpenSts == 2) ||
                (door5LockSts != null && door5LockSts == 2);
    }

    @Override
    public String toString() {
        return "DoorStatusMessage{" +
                "busSelfNo='" + busSelfNo + '\'' +
                ", busId=" + busId +
                ", door1OpenSts=" + door1OpenSts + "(" + getDoorStatusDescription(door1OpenSts) + ")" +
                ", door3OpenSts=" + door3OpenSts + "(" + getDoorStatusDescription(door3OpenSts) + ")" +
                ", door5LockSts=" + door5LockSts + "(" + getDoorStatusDescription(door5LockSts) + ")" +
                ", time=" + time +
                '}';
    }
}