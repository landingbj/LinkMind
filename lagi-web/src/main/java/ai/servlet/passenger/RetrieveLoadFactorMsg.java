package ai.servlet.passenger;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * CV系统满载率消息实体类
 */
@Data
public class RetrieveLoadFactorMsg {

    /** 车辆编号 */
    @JsonProperty("bus_no")
    private String busNo;

    /** 摄像头编号 */
    @JsonProperty("camera_no")
    private String cameraNo;

    /** 时间戳字符串 */
    @JsonProperty("timestamp")
    private String timestamp;

    /** 解析后的时间戳 */
    private LocalDateTime parsedTimestamp;

    /** 车辆总人数 */
    @JsonProperty("count")
    private Integer count;

    /** 满载率（小数形式，例如0.9表示90%） */
    @JsonProperty("factor")
    private BigDecimal factor;

    /** 事件类型（固定为load_factor） */
    private String event = "load_factor";

    /** 完整的WebSocket消息JSON */
    private String originalMessage;

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

    public String getCameraNo() {
        return cameraNo;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public LocalDateTime getParsedTimestamp() {
        return parsedTimestamp;
    }

    public Integer getCount() {
        return count;
    }

    public BigDecimal getFactor() {
        return factor;
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

    public void setCount(Integer count) {
        this.count = count;
    }

    public void setFactor(BigDecimal factor) {
        this.factor = factor;
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
                System.err.println("[RetrieveLoadFactorMsg] 解析时间戳失败: " + timestampStr + ", 错误: " + e2.getMessage());
                return null;
            }
        }
    }

    public String getSqeNo() {
        return sqeNo;
    }

    public void setSqeNo(String sqeNo) {
        this.sqeNo = sqeNo;
    }

    /**
     * 获取满载率百分比（用于显示）
     */
    public String getFactorPercentage() {
        if (factor == null) {
            return "0%";
        }
        return String.format("%.1f%%", factor.doubleValue() * 100);
    }
}
