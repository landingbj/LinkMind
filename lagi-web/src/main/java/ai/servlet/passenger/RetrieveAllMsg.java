package ai.servlet.passenger;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * 所有消息记录实体类
 * 用于保存所有接收到的Kafka和WebSocket消息
 */
@Data
public class RetrieveAllMsg {

    /** 主键ID */
    private Long id;

    /** 车辆编号 */
    @JsonProperty("bus_no")
    private String busNo;

    /** 消息类型 */
    @JsonProperty("message_type")
    private String messageType;

    /** Kafka主题名称 */
    @JsonProperty("topic")
    private String topic;

    /** WebSocket事件类型 */
    @JsonProperty("event")
    private String event;

    /** 消息来源 */
    @JsonProperty("source")
    private String source;

    /** 原始消息内容JSON */
    @JsonProperty("raw_message")
    private String rawMessage;

    /** 车辆ID */
    @JsonProperty("bus_id")
    private String busId;

    /** 摄像头编号 */
    @JsonProperty("camera_no")
    private String cameraNo;

    /** 站点ID */
    @JsonProperty("station_id")
    private String stationId;

    /** 站点名称 */
    @JsonProperty("station_name")
    private String stationName;

    /** 线路编号 */
    @JsonProperty("route_no")
    private String routeNo;

    /** 消息时间戳 */
    @JsonProperty("message_timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime messageTimestamp;

    /** 接收时间 */
    @JsonProperty("received_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime receivedAt;

    /** 数据入库时间 */
    @JsonProperty("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createdAt;

    /** 开关门唯一批次号 */
    @JsonProperty("sqeNo")
    private String sqeNo;

    // 手动添加getter和setter方法以确保兼容性
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBusNo() {
        return busNo;
    }

    public void setBusNo(String busNo) {
        this.busNo = busNo;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getRawMessage() {
        return rawMessage;
    }

    public void setRawMessage(String rawMessage) {
        this.rawMessage = rawMessage;
    }

    public String getBusId() {
        return busId;
    }

    public void setBusId(String busId) {
        this.busId = busId;
    }

    public String getCameraNo() {
        return cameraNo;
    }

    public void setCameraNo(String cameraNo) {
        this.cameraNo = cameraNo;
    }

    public String getStationId() {
        return stationId;
    }

    public void setStationId(String stationId) {
        this.stationId = stationId;
    }

    public String getStationName() {
        return stationName;
    }

    public void setStationName(String stationName) {
        this.stationName = stationName;
    }

    public String getRouteNo() {
        return routeNo;
    }

    public void setRouteNo(String routeNo) {
        this.routeNo = routeNo;
    }

    public LocalDateTime getMessageTimestamp() {
        return messageTimestamp;
    }

    public void setMessageTimestamp(LocalDateTime messageTimestamp) {
        this.messageTimestamp = messageTimestamp;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
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
}
