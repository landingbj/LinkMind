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
    
    // Redis TTL配置（秒）
    public static final int REDIS_TTL_DOOR_STATUS = 3600;        // 门状态缓存1小时
    public static final int REDIS_TTL_GPS = 1800;                // GPS缓存30分钟
    public static final int REDIS_TTL_ARRIVE_LEAVE = 3600;       // 到离站缓存1小时
    public static final int REDIS_TTL_FEATURES = 7200;           // 特征向量缓存2小时
    public static final int REDIS_TTL_OPEN_TIME = 7200;          // 开门时间窗口2小时
    public static final int REDIS_TTL_COUNTS = 86400;            // 计数缓存1天
    public static final int REDIS_TTL_STATION_GPS = 86400 * 7;   // 站点GPS缓存7天
    
    // 日志级别配置
    public static final boolean LOG_DEBUG = false;               // 是否打印调试日志
    public static final boolean LOG_INFO = true;                 // 是否打印信息日志
    public static final boolean LOG_ERROR = true;                // 是否打印错误日志

    public static String getDbUrl() { return DB_URL; }
    public static String getDbUser() { return DB_USER; }
    public static String getDbPassword() { return DB_PASSWORD; }
}