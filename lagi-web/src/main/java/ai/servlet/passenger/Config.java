package ai.servlet.passenger;

public class Config {
    public static final String DB_URL = "jdbc:postgresql://20.17.39.23:5432/GJ_DW";
    public static final String DB_USER = "gj_dw_r1";
    public static final String DB_PASSWORD = ")xNPm1OKZMgB";
    public static final String REDIS_HOST = "20.17.39.68";
    public static final String REDIS_PASSWORD = ")xNPm1OKZMgB";
    public static final int REDIS_PORT = 6379;
    public static final String OSS_UPLOAD_URL = "http://20.17.39.66:8089/api/admin/sys-file/upload";
    public static final String OSS_FILE_BASE_URL = "http://20.17.39.66:8089/api";
    public static final String OSS_DIR = "PassengerFlowRecognition";
    public static final String OSS_GROUP_ID = "305";
    public static final String OSS_TYPE = "20";
    public static final String MEDIA_API = "http://20.17.127.20:8010/keliushibie-media";
    public static final String PASSENGER_PROMPT = "请分析车门区域的下车乘客特征";

    // 图片处理配置
    public static final boolean ENABLE_IMAGE_PROCESSING = true;        // 是否启用图片处理
    public static final boolean ENABLE_AI_IMAGE_ANALYSIS = true;       // 是否启用AI图片分析
    public static final int MAX_IMAGES_PER_ANALYSIS = 40;              // 每次AI分析的最大图片数量

    // Redis TTL配置（秒）
    public static final int REDIS_TTL_DOOR_STATUS = 3600;        // 门状态缓存1小时
    public static final int REDIS_TTL_GPS = 1800;                // GPS缓存30分钟
    public static final int REDIS_TTL_ARRIVE_LEAVE = 3600;       // 到离站缓存1小时
    public static final int REDIS_TTL_FEATURES = 7200;           // 特征向量缓存2小时
    public static final int REDIS_TTL_OPEN_TIME = 7200;          // 开门时间窗口2小时
    public static final int REDIS_TTL_COUNTS = 86400;            // 计数缓存1天
    public static final int REDIS_TTL_STATION_GPS = 86400 * 7;   // 站点GPS缓存7天

    // 日志级别配置
    public static final boolean LOG_DEBUG = false;                // 是否打印调试日志
    public static final boolean LOG_INFO = true;                 // 是否打印信息日志
    public static final boolean LOG_ERROR = true;                // 是否打印错误日志

    // 试点线路专用日志配置
    public static final boolean PILOT_ROUTE_LOG_ENABLED = false;  // 是否启用试点线路流程日志
    public static final boolean ARRIVE_LEAVE_LOG_ENABLED = false; // 是否启用车辆到离站信号日志
    public static final boolean ARRIVE_LEAVE_LOG_NON_PILOT_ENABLED = false; // 白名单外线路是否也打印到离站日志

    // 精简流程日志：仅关注是否发送了BusOdRecord
    public static final boolean FLOW_LOG_ENABLED = true;

    /** 应用关闭超时时间（毫秒） */
    public static final int APP_SHUTDOWN_TIMEOUT_MS = 30000;

    /** Kafka消费者关闭超时时间（毫秒） */
    public static final int KAFKA_SHUTDOWN_TIMEOUT_MS = 30000;

    /** Redis清理工具关闭超时时间（毫秒） */
    public static final int REDIS_CLEANUP_SHUTDOWN_TIMEOUT_MS = 30000;

    /** 应用关闭等待时间（毫秒） */
    public static final int APP_SHUTDOWN_WAIT_MS = 2000;

    public static String getDbUrl() { return DB_URL; }
    public static String getDbUser() { return DB_USER; }
    public static String getDbPassword() { return DB_PASSWORD; }
}
