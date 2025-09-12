package ai.servlet.passenger;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * 所有WebSocket消息记录数据库服务类
 * 负责连接PolarDB并保存所有WebSocket消息到retrieve_all_ws表
 */
public class RetrieveAllWsDbService {

    // PolarDB连接配置
    private static final String DB_URL = "jdbc:mysql://20.17.39.67:3306/gjdev?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai";
    private static final String DB_USER = "gongjiao";
    private static final String DB_PASSWORD = "Gj@sCt1@";

    private HikariDataSource dataSource;

    public RetrieveAllWsDbService() {
        initDataSource();
    }

    /**
     * 初始化数据库连接池
     */
    private void initDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DB_URL);
        config.setUsername(DB_USER);
        config.setPassword(DB_PASSWORD);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        // 连接池配置
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setLeakDetectionThreshold(60000);

        this.dataSource = new HikariDataSource(config);

        if (Config.LOG_INFO) {
            System.out.println("[RetrieveAllWsDbService] 数据库连接池初始化完成");
        }
    }

    /**
     * 保存所有WebSocket消息到数据库
     */
    public boolean saveAllWebSocketMessage(RetrieveAllWs allWs) {
        // 对downup事件的raw_message进行优化
        String optimizedRawMessage = optimizeDownUpRawMessage(allWs.getRawMessage(), allWs.getEvent());

        String sql = "INSERT INTO retrieve_all_ws (bus_no, event, raw_message, bus_id, camera_no, " +
                    "station_id, station_name, message_timestamp, received_at, created_at, sqe_no) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, allWs.getBusNo());
            stmt.setString(2, allWs.getEvent());
            stmt.setString(3, optimizedRawMessage);
            stmt.setString(4, allWs.getBusId());
            stmt.setString(5, allWs.getCameraNo());
            stmt.setString(6, allWs.getStationId());
            stmt.setString(7, allWs.getStationName());
            stmt.setTimestamp(8, allWs.getMessageTimestamp() != null ?
                java.sql.Timestamp.valueOf(allWs.getMessageTimestamp()) : null);
            stmt.setTimestamp(9, allWs.getReceivedAt() != null ?
                java.sql.Timestamp.valueOf(allWs.getReceivedAt()) :
                java.sql.Timestamp.valueOf(LocalDateTime.now()));
            stmt.setTimestamp(10, java.sql.Timestamp.valueOf(LocalDateTime.now()));
            // 🔥 设置sqe_no字段
            stmt.setString(11, allWs.getSqeNo());

            int result = stmt.executeUpdate();

            if (Config.LOG_DEBUG) {
                System.out.println(String.format("[RetrieveAllWsDbService] 🔥 保存WebSocket消息成功: 车辆=%s, 事件=%s, sqe_no=%s",
                    allWs.getBusNo(), allWs.getEvent(), allWs.getSqeNo()));
            }

            return result > 0;

        } catch (SQLException e) {
            if (Config.LOG_ERROR) {
                System.err.println(String.format("[RetrieveAllWsDbService] 🔥 保存WebSocket消息失败: 车辆=%s, 事件=%s, sqe_no=%s, 错误=%s",
                    allWs.getBusNo(), allWs.getEvent(), allWs.getSqeNo(), e.getMessage()));
                e.printStackTrace();
            }
            return false;
        }
    }

    /**
     * 关闭数据库连接池
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            if (Config.LOG_INFO) {
                System.out.println("[RetrieveAllWsDbService] 数据库连接池已关闭");
            }
        }
    }

    /**
     * 测试数据库连接
     */
    public boolean testConnection() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(5);
        } catch (SQLException e) {
            if (Config.LOG_ERROR) {
                System.err.println("[RetrieveAllWsDbService] 数据库连接测试失败: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * 优化downup事件的raw_message，将image和feature字段替换为"有"
     */
    private String optimizeDownUpRawMessage(String rawMessage, String event) {
        if (!"downup".equals(event)) {
            return rawMessage;
        }

        try {
            JSONObject messageJson = new JSONObject(rawMessage);
            JSONObject data = messageJson.optJSONObject("data");
            if (data != null) {
                JSONArray events = data.optJSONArray("events");
                if (events != null) {
                    for (int i = 0; i < events.length(); i++) {
                        JSONObject eventObj = events.getJSONObject(i);

                        // 优化image字段
                        if (eventObj.has("image") && !eventObj.isNull("image") &&
                            !eventObj.getString("image").trim().isEmpty()) {
                            eventObj.put("image", "有");
                        }

                        // 优化feature字段
                        if (eventObj.has("feature") && !eventObj.isNull("feature") &&
                            !eventObj.getString("feature").trim().isEmpty()) {
                            eventObj.put("feature", "有");
                        }
                    }
                }
            }
            return messageJson.toString();
        } catch (Exception e) {
            if (Config.LOG_ERROR) {
                System.err.println("[RetrieveAllWsDbService] 优化downup消息失败: " + e.getMessage());
            }
            return rawMessage; // 优化失败时返回原始消息
        }
    }
}
