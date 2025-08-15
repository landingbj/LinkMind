package ai.servlet.passenger;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 公交客流OD记录表实体类
 */
@Data
public class BusOdRecord {

    /** 主键ID */
    private Long id;

    /** 数据日期（用于按天统计） */
    private LocalDate date;

    /** 开门时间 */
    private LocalDateTime timestampBegin;

    /** 关门时间 */
    private LocalDateTime timestampEnd;

    /** 公交车编号 */
    private String busNo;

    /** 车辆ID (新增，从Kafka获取) */
    private Long busId;

    /** 摄像头编号 */
    private String cameraNo;

    /** 线路编号 */
    private String lineId;

    /** 上行/下行，up/down */
    private String direction;

    /** 班次号 */
    private String tripNo;

    /** 上车站点ID */
    private String stationIdOn;

    /** 上车站点名称 */
    private String stationNameOn;

    /** 下车站点ID */
    private String stationIdOff;

    /** 下车站点名称 */
    private String stationNameOff;

    /** 乘客ID向量（base64编码） */
    private String passengerVector;

    /** 本站上车人数 */
    private Integer upCount;

    /** 本站下车人数 */
    private Integer downCount;

    /** 车辆经度 */
    private BigDecimal gpsLat;

    /** 车辆纬度 */
    private BigDecimal gpsLng;

    /** 上下车画面截图（base64编码） */
    private String countingImage;

    /**
     * 乘客图像坐标（JSON格式字符串）
     * 例如 [{"xLeftUp":100,"yLeftUp":100,"xRightBottom":200,"yRightBottom":200},...]
     */
    private String passengerPosition;

    /** 满载率（百分比，例如 75.25 表示75.25%） */
    private BigDecimal fullLoadRate;

    /** 乘客特征文字描述 */
    private String featureDescription;

    /** 本趟次总人数 */
    private Integer totalCount;

    /** 刷卡人数（票务数据） */
    private Integer ticketCount;

    /** 数据来源标识（CV、票务、大模型修正等） */
    private String dataSource;

    /** 数据入库时间 */
    private LocalDateTime createdAt;
}