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

    /** 车辆ID */
    private Long busId;

    /** 摄像头编号 */
    private String cameraNo;

    /** 线路编号 */
    private String lineId;

    /** 线路运行方向（从GPS数据获取：up=上行, down=下行, circular=环形） */
    private String routeDirection;

    /** 上车站点ID */
    private String stationIdOn;

    /** 上车站点名称 */
    private String stationNameOn;

    /** 下车站点ID */
    private String stationIdOff;

    /** 下车站点名称 */
    private String stationNameOff;

    /** 本站站点名称（从车辆到离站信号获取） */
    private String currentStationName;

    /** 
     * 乘客特征向量集合（JSON数组格式）
     * 格式：[{"feature":"xxx","direction":"up","timestamp":"xxx","image":"xxx","position":{"xLeftUp":100,"yLeftUp":100,"xRightBottom":200,"yRightBottom":200}}]
     */
    private String passengerFeatures;

    /** 本站上车人数（CV系统识别） */
    private Integer upCount;

    /** 本站下车人数（CV系统识别） */
    private Integer downCount;

    /** 车辆经度 */
    private BigDecimal gpsLat;

    /** 车辆纬度 */
    private BigDecimal gpsLng;

    /** 乘客图片URL集合（JSON数组格式，OSS URL） */
    private String passengerImages;

    /**
     * 乘客图像坐标（JSON格式字符串）
     * 例如 [{"xLeftUp":100,"yLeftUp":100,"xRightBottom":200,"yRightBottom":200},...]
     */
    private String passengerPosition;

    /** 满载率（百分比，例如 75.25 表示75.25%） */
    private BigDecimal fullLoadRate;

    /** 乘客特征文字描述 */
    private String featureDescription;

    /** 
     * 车辆当前总人数（来自CV系统满载率推送的count字段）
     * 表示车辆在某个时刻的实时载客总数
     */
    private Integer vehicleTotalCount;


    /** 
     * 大模型识别的上车人数（AI分析图片/视频得出）
     * 作为CV系统上车人数的补充验证
     */
    private Integer aiUpCount;

    /** 
     * 大模型识别的下车人数（AI分析图片/视频得出）
     * 作为CV系统下车人数的补充验证
     */
    private Integer aiDownCount;

    /** 刷卡人数（票务数据） */
    private Integer ticketCount;

    /** 数据入库时间 */
    private LocalDateTime createdAt;
}
