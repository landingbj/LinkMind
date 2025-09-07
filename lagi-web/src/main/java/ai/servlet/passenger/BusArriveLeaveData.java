package ai.servlet.passenger;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * 公交车到离站数据实体类
 */
@Data
public class BusArriveLeaveData {

    /** 车辆自编号/车辆ID */
    @JsonProperty("busNo")
    private String busNo;

    /** 车辆编号 */
    @JsonProperty("busSelfNo")
    private String busSelfNo;

    /** 车辆ID */
    @JsonProperty("busId")
    private Long busId;

    /** 是否到离站标识（1=到站，2=离站） */
    @JsonProperty("isArriveOrLeft")
    private String isArriveOrLeft;

    /** 站点ID */
    @JsonProperty("stationId")
    private String stationId;

    /** 站点名称 */
    @JsonProperty("stationName")
    private String stationName;

    /** 下一站点序号 */
    @JsonProperty("nextStationSeqNum")
    private String nextStationSeqNum;

    /** 线路编号 */
    @JsonProperty("routeNo")
    private String routeNo;

    /** 交通类型（4=上行，5=下行） */
    @JsonProperty("trafficType")
    private String trafficType;

    /** 方向（up=上行，down=下行） */
    private String direction;

    /** 源地址 */
    @JsonProperty("srcAddr")
    private String srcAddr;

    /** 序列号 */
    @JsonProperty("seqNum")
    private Long seqNum;

    /** 包时间戳 */
    @JsonProperty("packetTime")
    private Long packetTime;

    /** 包类型 */
    @JsonProperty("pktType")
    private Integer pktType;

    /** 原始Kafka消息JSON */
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

    public String getBusSelfNo() {
        return busSelfNo;
    }

    public Long getBusId() {
        return busId;
    }

    public String getIsArriveOrLeft() {
        return isArriveOrLeft;
    }

    public String getStationId() {
        return stationId;
    }

    public String getStationName() {
        return stationName;
    }

    public String getNextStationSeqNum() {
        return nextStationSeqNum;
    }

    public String getRouteNo() {
        return routeNo;
    }

    public String getTrafficType() {
        return trafficType;
    }

    public String getDirection() {
        return direction;
    }

    public String getSrcAddr() {
        return srcAddr;
    }

    public Long getSeqNum() {
        return seqNum;
    }

    public Long getPacketTime() {
        return packetTime;
    }

    public Integer getPktType() {
        return pktType;
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

    public void setBusSelfNo(String busSelfNo) {
        this.busSelfNo = busSelfNo;
    }

    public void setBusId(Long busId) {
        this.busId = busId;
    }

    public void setIsArriveOrLeft(String isArriveOrLeft) {
        this.isArriveOrLeft = isArriveOrLeft;
    }

    public void setStationId(String stationId) {
        this.stationId = stationId;
    }

    public void setStationName(String stationName) {
        this.stationName = stationName;
    }

    public void setNextStationSeqNum(String nextStationSeqNum) {
        this.nextStationSeqNum = nextStationSeqNum;
    }

    public void setRouteNo(String routeNo) {
        this.routeNo = routeNo;
    }

    public void setTrafficType(String trafficType) {
        this.trafficType = trafficType;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public void setSrcAddr(String srcAddr) {
        this.srcAddr = srcAddr;
    }

    public void setSeqNum(Long seqNum) {
        this.seqNum = seqNum;
    }

    public void setPacketTime(Long packetTime) {
        this.packetTime = packetTime;
    }

    public void setPktType(Integer pktType) {
        this.pktType = pktType;
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
}



