package ai.servlet.passenger;

public class Config {
    public static final String DB_URL = "jdbc:postgresql://20.17.39.40:5432/GJ_DW";
    public static final String DB_USER = "gj_dw_r1";
    public static final String DB_PASSWORD = ")xNPm1OKZMgB";
    public static final String REDIS_HOST = "20.17.39.34";
    public static final String REDIS_PASSWORD = "2gHLmc!1hb!s@t";
    public static final int REDIS_PORT = 6379;
    public static final String OSS_UPLOAD_URL = "http://20.17.39.75:30080/admin/sys-file/upload";
    public static final String OSS_FILE_BASE_URL = "https://gateway-busfusion.ibuscloud.com";

    // OSS配置 - 图片文件
    public static final String OSS_GROUP_ID_IMAGE = "307";
    public static final String OSS_TYPE_IMAGE = "10";

    // OSS配置 - 视频文件
    public static final String OSS_GROUP_ID_VIDEO = "305";
    public static final String OSS_TYPE_VIDEO = "20";

    // 大模型API配置 - 支持图片列表和视频两种输入方式
    // 优先使用image_path_list参数，fallback到video_path参数
    public static final String MEDIA_API = "http://20.17.127.55:8010/keliushibie-media";
    public static final String PASSENGER_PROMPT = "请分析车门区域的下车乘客特征";
    public static final int MEDIA_MAX_RETRY = 2;                 // 当返回空特征时的最大重试次数
    public static final int MEDIA_RETRY_BACKOFF_MS = 500;        // 重试退避时间（基础毫秒）

    // FFmpeg 可执行文件路径；默认从 PATH 中查找，如部署机未配置可填绝对路径，如 "/usr/bin/ffmpeg"
    public static final String FFMPEG_PATH = "ffmpeg";

    // 图片处理配置
    public static final boolean ENABLE_IMAGE_PROCESSING = true;        // 是否启用图片处理
    public static final boolean ENABLE_AI_IMAGE_ANALYSIS = true;       // 是否启用AI图片分析
    public static final int MAX_IMAGES_PER_ANALYSIS = 40;              // 每次AI分析的最大图片数量
    public static final int IMAGE_DURATION_SECONDS = 2;                // 每张图片在视频中的播放时长（秒）
    
    // 特征数据配置
    public static final int MAX_FEATURE_SIZE_BYTES = 50000;            // 单个特征数据最大字节数（50KB，支持约10000维特征向量）
    public static final int MAX_FEATURES_PER_WINDOW = 50;              // 每个时间窗口最大特征数量
    public static final int FEATURE_CLEANUP_THRESHOLD = 30;            // 特征清理阈值
    public static final int MAX_FEATURE_VECTOR_DIMENSIONS = 10000;     // 最大特征向量维度数

    // Redis TTL配置（秒）
    public static final int REDIS_TTL_DOOR_STATUS = 3600;        // 门状态缓存1小时
    public static final int REDIS_TTL_GPS = 1800;                // GPS缓存30分钟
    public static final int REDIS_TTL_ARRIVE_LEAVE = 7200;       // 到离站缓存2小时（与特征数据保持一致）
    public static final int REDIS_TTL_FEATURES = 7200;           // 特征向量缓存2小时
    public static final int REDIS_TTL_OPEN_TIME = 7200;          // 开门时间窗口2小时
    public static final int REDIS_TTL_COUNTS = 86400;            // 计数缓存1天
    public static final int REDIS_TTL_STATION_GPS = 86400 * 7;   // 站点GPS缓存7天

    // 日志级别配置
    public static final boolean LOG_DEBUG = true;                // 是否打印调试日志
    public static final boolean LOG_INFO = true;                 // 是否打印信息日志
    public static final boolean LOG_ERROR = true;                // 是否打印错误日志

    // 试点线路专用日志配置
    public static final boolean PILOT_ROUTE_LOG_ENABLED = true;   // 是否启用试点线路流程日志（临时开启用于调试图片问题）
    public static final boolean ARRIVE_LEAVE_LOG_ENABLED = false; // 是否启用车辆到离站信号日志
    public static final boolean ARRIVE_LEAVE_LOG_NON_PILOT_ENABLED = false; // 白名单外线路是否也打印到离站日志

    // 精简流程日志：仅关注是否发送了BusOdRecord
    public static final boolean FLOW_LOG_ENABLED = true;

    // CV结果等待配置（毫秒）
    public static final int CV_RESULT_GRACE_MS = 3000;       // 关门后最多等待CV结果3秒
    public static final int CV_RESULT_STABLE_MS = 800;       // 在800ms内无变化视为稳定
    public static final int CV_RESULT_POLL_INTERVAL_MS = 200; // 轮询间隔200ms

    // 判门参数
    public static final int MIN_DOOR_OPEN_MS = 2000;            // 最小开门时长2s（放宽）
    public static final int MAX_DOOR_OPEN_MS = 120000;          // 最大开门时长2分钟（超时强制关门）
    public static final int CLOSE_CONSECUTIVE_REQUIRED = 1;     // 关门条件单次满足即可（放宽）
    public static final double OPEN_DISTANCE_THRESHOLD_M = 50.0; // 开门距离阈值50m
    public static final double OPEN_SPEED_THRESHOLD_MS = 1.0;    // 开门速度阈值1 m/s
    public static final double CLOSE_DISTANCE_THRESHOLD_M = 60.0;// 关门距离阈值60m（更宽松）
    public static final double CLOSE_SPEED_THRESHOLD_MS = 15.0/3.6; // 关门速度阈值15km/h

    // 开门防抖与图片时间容忍窗口
    public static final int OPEN_DEBOUNCE_SECONDS = 15;              // 同车开门防抖窗口（秒）
    public static final int IMAGE_TIME_TOLERANCE_BEFORE_SECONDS = 30; // 图片时间容忍：开门前
    public static final int IMAGE_TIME_TOLERANCE_AFTER_SECONDS = 30;  // 图片时间容忍：关门后

    // 特征查询回退与重试配置
    public static final int FEATURE_FALLBACK_WINDOW_MINUTES = 5;     // 特征最近窗口回退范围（±分钟）
    public static final int REDIS_FEATURE_FETCH_RETRY = 3;            // Redis特征获取最大重试次数
    public static final int REDIS_FEATURE_FETCH_BACKOFF_MS = 200;     // Redis特征获取重试退避（基础毫秒）

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
