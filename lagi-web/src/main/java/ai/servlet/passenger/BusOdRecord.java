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

    /** 数据日期（用于按天统计）
     * 示例：2025-09-09
     */
    private LocalDate date;

    /** 开门时间
     * 示例：2025-09-09 12:26:45
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime timestampBegin;

    /** 关门时间
     * 示例：2025-09-09 12:27:30
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime timestampEnd;

    /** 公交车编号
     * 示例：2-8091
     */
    private String busNo;

    /** 车辆ID
     * 示例：1001032473
     */
    private Long busId;

    /** 摄像头编号
     * 示例：DVR0 或 01
     */
    private String cameraNo;

    /** 线路编号
     * 示例：3301000100121015（GPS/到离站的 routeNo）
     */
    private String lineId;

    /** 线路运行方向（从GPS数据获取：up=上行, down=下行, circular=环形）
     * 示例：up
     */
    private String routeDirection;

    /**
     * 区间客流数集合（JSON数组字符串）
     * 结构：数组中每个元素表示一个上/下车区间，包含区间统计与乘客明细
     * 示例：
     * [
     *   {
     *     "stationNameOn": "清河坊",
     *     "stationNameOff": "和平路站",
     *     "stationIdOff": "3301000101243477",
     *     "stationIdOn": "3301000101243477",
     *     "passengerFlowCount": 2,
     *     "detail": [
     *       {
     *         "featureVector": [0.0312, -0.1023, 0.4551, 0.0007],
     *         "stationIdOn": "3301000101243477",
     *         "stationNameOn": "清河坊",
     *         "stationIdOff": "3301000101243477",
     *         "stationNameOff": "和平路站"
     *       },
     *       {
     *         "featureVector": [-0.1435, 0.0721, 0.5104, -0.0129],
     *         "stationIdOn": "3301000101243477",
     *         "stationNameOn": "清河坊",
     *         "stationIdOff": "3301000101243477",
     *         "stationNameOff": "和平路站"
     *       }
     *     ]
     *   }
     * ]
     */
    private String sectionPassengerFlowCount;

    /** 本站站点名称（从车辆到离站信号获取）
     * 示例：汤大线24号路口
     */
    private String currentStationName;

    /**
     * CV推送的乘客特征向量集合（JSON数组格式）
     * 结构：[{"feature":"...","direction":"up|down","timestamp":"yyyy-MM-dd HH:mm:ss","image":"http(s)://...","position":{"xLeftUp":100,"yLeftUp":100,"xRightBottom":200,"yRightBottom":200}}]
     * 示例：[{"feature":"BASE64或向量串","direction":"up","timestamp":"2025-09-09 12:26:50","image":"https://.../xxx.jpg","position":{"xLeftUp":100,"yLeftUp":120,"xRightBottom":200,"yRightBottom":260}}]
     */
    private String passengerFeatures;

    /** 本站上车人数
     * 示例：3（按方向图片计数或CV聚合）
     */
    private Integer upCount;

    /** 本站下车人数
     * 示例：2（按方向图片计数或CV聚合）
     */
    private Integer downCount;

    /** 车辆经度
     * 示例：29.90658416
     */
    private BigDecimal gpsLat;

    /** 车辆纬度
     * 示例：119.87444275
     */
    private BigDecimal gpsLng;

    /** 乘客图片URL集合（JSON数组格式，OSS URL）
     * 结构：[{"location":"up","images":["https://...jpg","https://...jpg"]},{"location":"down","images":["https://...jpg"]}]
     * 示例：[{"location":"up","images":["https://gateway-busfusion.ibuscloud.com/admin/sys-file/oss/file?fileName=cv_2-8091_...jpg"]},{"location":"down","images":["https://gateway-busfusion.ibuscloud.com/admin/sys-file/oss/file?fileName=cv_2-8091_...jpg")}]
     */
    private String passengerImages;

    /**
     * 乘客视频URL（JSON数组字符串），按上下车方向分别生成
     * 结构：[{"location":"up","videoUrl":"https://...mp4"},{"location":"down","videoUrl":"https://...mp4"}]
     * 示例：[{"location":"up","videoUrl":"https://gateway-busfusion.ibuscloud.com/admin/sys-file/oss/file?fileName=...mp4"}]
     */
    private String passengerVideoUrl;

    /**
     * 乘客图像坐标（JSON格式字符串）
     * 结构：[{"xLeftUp":100,"yLeftUp":100,"xRightBottom":200,"yRightBottom":200},...]
     * 示例：[{"xLeftUp":100,"yLeftUp":120,"xRightBottom":200,"yRightBottom":260}]
     */
    private String passengerPosition;

    /**
     * 满载率（小数形式，例如 0.9 表示90%）
     * 直接存储CV系统推送的factor值
     * 示例：0.85
     */
    private BigDecimal fullLoadRate;

    /** 大模型识别的乘客特征文字描述
     * 结构：[{"location":"up","features":["乘客1: 紫色上衣，深色长裤，脚穿深色鞋子，右手持有物品。"]},{"location":"down","features":["乘客1: 黑色上衣，黑色裤子，脚穿深色鞋子，右手提透明袋子。"]}]
     * 示例：[{"location":"up","features":["乘客1: 紫色上衣，深色长裤，脚穿深色鞋子，右手持有物品。"]}]
     */
    private String featureDescription;

    /**
     * 车辆当前总人数（CV满载率推送的count）
     * 示例：37
     */
    private Integer vehicleTotalCount;


    /**
     * 大模型识别的总人数（AI基于窗口内所有图片统计）
     * 示例：5
     */
    private Integer aiTotalCount;

    /**
     * 刷卡人数（票务数据）- JSON格式
     * 结构：{
     *   "upCount": 3,
     *   "downCount": 1,
     *   "totalCount": 4,
     *   "detail": [
     *     {"busSelfNo":"6-6314","cardNo":"00004880100230243214","cardType":"0800","childCardType":"0800","tradeTime":"2025-08-14 12:10:15","onOff":"up","direction":"上车"}
     *   ]
     * }
     * 示例：见上结构
     */
    private String ticketJson;

    /** 上车刷卡人数
     * 示例：3
     */
    private Integer ticketUpCount;

    /** 下车刷卡人数
     * 示例：1
     */
    private Integer ticketDownCount;

    /** 数据入库时间
     * 示例：2025-09-09 12:27:31
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createdAt;

    /**
     * 开关门唯一批次号
     * 格式：{busId}_{timestamp}_{uuid8位}
     * 示例：1001032473_20250909_122730_ab12cd34
     * 用于关联同一开关门事件的所有相关数据
     */
    private String sqeNo;

    /**
     * 车辆到离站信号原始数据JSON数组（用于溯源）
     * 结构：[{"eventType":"door_open","kafkaData":{...}},{"eventType":"door_close","kafkaData":{...}}]
     * 示例：[{"eventType":"door_open","kafkaData":{"stationId":"3301000101622286","stationName":"汤大线24号路口","timestamp":"2025-09-09 12:26:45"}}]
     */
    private String retrieveBusGpsMsg;

    /**
     * CV推送的原始downup事件数据JSON数组（按 sqe_no 聚合后的唯一集合A）
     * 结构：[{"event":"downup","data":{"sqe_no":"...","bus_no":"...","timestamp":"...","events":[{"direction":"up","feature":"...","image":"http(s)://...","box_x":100,"box_y":100,"box_w":100,"box_h":100}]}}]
     * 示例：[{"event":"downup","data":{"sqe_no":"1001032473_20250909_122730_ab12cd34","bus_no":"2-8091","timestamp":"2025-09-09 12:27:00","events":[{"direction":"up","feature":"xxxxx","image":"https://...jpg","box_x":100,"box_y":100,"box_w":100,"box_h":100}]}}]
     */
    private String retrieveDownupMsg;
}
