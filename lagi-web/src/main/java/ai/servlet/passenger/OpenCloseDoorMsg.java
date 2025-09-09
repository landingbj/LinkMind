package ai.servlet.passenger;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 开关门WebSocket消息实体类
 */
@Data
public class OpenCloseDoorMsg {
    private static final Logger logger = LoggerFactory.getLogger(OpenCloseDoorMsg.class);

    /** 车辆编号 */
    @JsonProperty("bus_no")
    private String busNo;

    /** 车辆ID */
    @JsonProperty("bus_id")
    private String busId;

    /** 摄像头编号 */
    @JsonProperty("camera_no")
    private String cameraNo;

    /** 动作（open=开门，close=关门） */
    @JsonProperty("action")
    private String action;

    /** 时间戳字符串 */
    @JsonProperty("timestamp")
    private String timestamp;

    /** 解析后的时间戳 */
    private LocalDateTime parsedTimestamp;

    /** 站点ID */
    private String stationId;

    /** 站点名称 */
    private String stationName;

    /** 事件类型（固定为open_close_door） */
    private String event = "open_close_door";

    /** 完整的WebSocket消息JSON */
    private String originalMessage;

    /** 数据入库时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createdAt;

    /** 开关门唯一批次号 */
    @JsonProperty("sqe_no")
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

    public String getAction() {
        return action;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public LocalDateTime getParsedTimestamp() {
        return parsedTimestamp;
    }

    public String getStationId() {
        return stationId;
    }

    public String getStationName() {
        return stationName;
    }

    public String getEvent() {
        return event;
    }

    public String getOriginalMessage() {
        return originalMessage;
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

    public void setAction(String action) {
        this.action = action;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
        // 自动解析时间戳
        this.parsedTimestamp = parseTimestamp(timestamp);
    }

    public void setParsedTimestamp(LocalDateTime parsedTimestamp) {
        this.parsedTimestamp = parsedTimestamp;
    }

    public void setStationId(String stationId) {
        this.stationId = stationId;
    }

    public void setStationName(String stationName) {
        this.stationName = stationName;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public void setOriginalMessage(String originalMessage) {
        this.originalMessage = originalMessage;
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
                logger.error("[OpenCloseDoorMsg] 解析时间戳失败: {}, 错误: {}", timestampStr, e2.getMessage(), e2);
                return null;
            }
        }
    }
}
