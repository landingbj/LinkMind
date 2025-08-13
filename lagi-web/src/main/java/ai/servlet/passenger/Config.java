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
    public static final String VIDEO_UNDERSTAND_API = "http://20.17.127.20:8005/api/v1/video_understand";
    public static final String CV_WEBSOCKET_URI = "ws://20.17.127.16:6000/v1/bus-counting-info-push";

    public static String getDbUrl() { return DB_URL; }
    public static String getDbUser() { return DB_USER; }
    public static String getDbPassword() { return DB_PASSWORD; }
}