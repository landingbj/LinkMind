package ai.servlet.passenger;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;

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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime timestampBegin;

    /** 关门时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
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

    /** 
     * 区间客流数集合（JSON数组格式）
     * 格式：[{"stationIdOn":"xxx","stationNameOn":"xxx","stationIdOff":"xxx","stationNameOff":"xxx","passengerFlowCount":5}]
     */
    private String sectionPassengerFlowCount;

    /** 本站站点名称（从车辆到离站信号获取） */
    private String currentStationName;

    /** 
     * 乘客特征向量集合（JSON数组格式）
     * 格式：[{"feature":"xxx","direction":"up","timestamp":"xxx","image":"xxx","position":{"xLeftUp":100,"yLeftUp":100,"xRightBottom":200,"yRightBottom":200}}]
     */
    private String passengerFeatures;

    /** 本站上车人数 */
    private Integer upCount;

    /** 本站下车人数 */
    private Integer downCount;

    /** 车辆经度 */
    private BigDecimal gpsLat;

    /** 车辆纬度 */
    private BigDecimal gpsLng;

    /** 乘客图片URL集合（JSON数组格式，OSS URL） */
    private String passengerImages;

    /**
     * 乘客视频URL（JSON数组字符串），按上下车方向分别生成
     * 形如：[{"location":"up","videoUrl":"..."},{"location":"down","videoUrl":"..."}]
     */
    private String passengerVideoUrl;

    /**
     * 乘客图像坐标（JSON格式字符串）
     * 例如 [{"xLeftUp":100,"yLeftUp":100,"xRightBottom":200,"yRightBottom":200},...]
     */
    private String passengerPosition;

    /** 
     * 满载率（小数形式，例如 0.9 表示满载率90%，0.75 表示满载率75%）
     * 直接存储CV系统推送的factor值，不做百分比转换
     */
    private BigDecimal fullLoadRate;

    /** 乘客特征文字描述 */
    private String featureDescription;

    /** 
     * 车辆当前总人数（来自CV系统满载率推送的count字段）
     * 表示车辆在某个时刻的实时载客总数，直接存储CV推送的原始值
     */
    private Integer vehicleTotalCount;


    /** 
     * 大模型识别的总人数（AI基于窗口内所有图片统计）
     */
    private Integer aiTotalCount;

    /** 刷卡人数（票务数据） */
    private Integer ticketCount;

    /** 数据入库时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createdAt;
}
