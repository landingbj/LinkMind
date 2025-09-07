package ai.servlet.passenger;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * 公交刷卡数据实体类
 */
@Data
public class BusCardSwipeData {

    /** 车辆自编号 */
    @JsonProperty("busSelfNo")
    private String busSelfNo;

    /** 卡号 */
    @JsonProperty("cardNo")
    private String cardNo;

    /** 主卡类型 */
    @JsonProperty("cardType")
    private String cardType;

    /** 子卡类型 */
    @JsonProperty("childCardType")
    private String childCardType;

    /** 上下车标识 */
    @JsonProperty("onOff")
    private String onOff;

    /** 交易号 */
    @JsonProperty("tradeNo")
    private String tradeNo;

    /** 交易时间 */
    @JsonProperty("tradeTime")
    private String tradeTime;

    // 解析后的交易时间
    private LocalDateTime parsedTradeTime;

    // 数据入库时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createdAt;

    /** 开关门唯一批次号 */
    @JsonProperty("sqeNo")
    private String sqeNo;

    // 手动添加getter方法以确保兼容性
    public String getBusSelfNo() {
        return busSelfNo;
    }

    public String getCardNo() {
        return cardNo;
    }

    public String getCardType() {
        return cardType;
    }

    public String getChildCardType() {
        return childCardType;
    }

    public String getOnOff() {
        return onOff;
    }

    public String getTradeNo() {
        return tradeNo;
    }

    public String getTradeTime() {
        return tradeTime;
    }

    public LocalDateTime getParsedTradeTime() {
        return parsedTradeTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // 手动添加setter方法以确保兼容性
    public void setBusSelfNo(String busSelfNo) {
        this.busSelfNo = busSelfNo;
    }

    public void setCardNo(String cardNo) {
        this.cardNo = cardNo;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public void setChildCardType(String childCardType) {
        this.childCardType = childCardType;
    }

    public void setOnOff(String onOff) {
        this.onOff = onOff;
    }

    public void setTradeNo(String tradeNo) {
        this.tradeNo = tradeNo;
    }

    public void setTradeTime(String tradeTime) {
        this.tradeTime = tradeTime;
    }

    public void setParsedTradeTime(LocalDateTime parsedTradeTime) {
        this.parsedTradeTime = parsedTradeTime;
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
