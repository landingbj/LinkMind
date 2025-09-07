package ai.servlet.passenger;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * CV系统downup消息实体类
 */
@Data
public class RetrieveDownUpMsg {

    /** 车辆编号 */
    @JsonProperty("bus_no")
    private String busNo;

    /** 车辆ID */
    @JsonProperty("bus_id")
    private String busId;

    /** 摄像头编号 */
    @JsonProperty("camera_no")
    private String cameraNo;

    /** 时间戳字符串 */
    @JsonProperty("timestamp")
    private String timestamp;

    /** 解析后的时间戳 */
    private LocalDateTime parsedTimestamp;

    /** 事件类型（固定为downup） */
    private String event = "downup";

    /** 事件列表 */
    @JsonProperty("events")
    private List<DownUpEvent> events;

    /** 事件列表JSON字符串（用于数据库存储） */
    private String eventsJson;

    /** 完整的WebSocket消息JSON */
    private String originalMessage;

    /** 上车事件数量 */
    private Integer upCount;

    /** 下车事件数量 */
    private Integer downCount;

    /** 数据入库时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createdAt;

    /** 开关门唯一批次号 */
    @JsonProperty("sqeNo")
    private String sqeNo;

    // 手动添加getter方法以确保兼容性
    public String getBusNo() {
        return busNo;
    }

    public String getBusId() {
        return busId;
    }

    public String getCameraNo() {
        return cameraNo;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public LocalDateTime getParsedTimestamp() {
        return parsedTimestamp;
    }

    public String getEvent() {
        return event;
    }

    public List<DownUpEvent> getEvents() {
        return events;
    }

    public String getEventsJson() {
        return eventsJson;
    }

    public String getOriginalMessage() {
        return originalMessage;
    }

    public Integer getUpCount() {
        return upCount;
    }

    public Integer getDownCount() {
        return downCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // 手动添加setter方法以确保兼容性
    public void setBusNo(String busNo) {
        this.busNo = busNo;
    }

    public void setBusId(String busId) {
        this.busId = busId;
    }

    public void setCameraNo(String cameraNo) {
        this.cameraNo = cameraNo;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
        // 自动解析时间戳
        this.parsedTimestamp = parseTimestamp(timestamp);
    }

    public void setParsedTimestamp(LocalDateTime parsedTimestamp) {
        this.parsedTimestamp = parsedTimestamp;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public void setEvents(List<DownUpEvent> events) {
        this.events = events;
        // 自动计算上下车数量
        calculateCounts();
    }

    public void setEventsJson(String eventsJson) {
        this.eventsJson = eventsJson;
    }

    public void setOriginalMessage(String originalMessage) {
        this.originalMessage = originalMessage;
    }

    public void setUpCount(Integer upCount) {
        this.upCount = upCount;
    }

    public void setDownCount(Integer downCount) {
        this.downCount = downCount;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getSqeNo() {
        return sqeNo;
    }

    public void setSqeNo(String sqeNo) {
        this.sqeNo = sqeNo;
    }

    /**
     * 解析时间戳字符串
     */
    private LocalDateTime parseTimestamp(String timestampStr) {
        if (timestampStr == null || timestampStr.trim().isEmpty()) {
            return null;
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return LocalDateTime.parse(timestampStr.trim(), formatter);
        } catch (DateTimeParseException e) {
            // 如果解析失败，尝试其他格式
            try {
                return LocalDateTime.parse(timestampStr.trim());
            } catch (DateTimeParseException e2) {
                System.err.println("[RetrieveDownUpMsg] 解析时间戳失败: " + timestampStr + ", 错误: " + e2.getMessage());
                return null;
            }
        }
    }

    /**
     * 计算上下车事件数量
     */
    private void calculateCounts() {
        if (events == null) {
            this.upCount = 0;
            this.downCount = 0;
            return;
        }

        long upCount = events.stream().filter(event -> "up".equals(event.getDirection())).count();
        long downCount = events.stream().filter(event -> "down".equals(event.getDirection())).count();

        this.upCount = (int) upCount;
        this.downCount = (int) downCount;
    }
}
