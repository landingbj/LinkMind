package ai.servlet.passenger;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * 开关门WebSocket消息数据库服务类
 * 负责连接PolarDB并保存开关门消息到open_close_door_msg表
 */
public class OpenCloseDoorMsgDbService {

    // PolarDB连接配置
    private static final String DB_URL = "jdbc:mysql://20.17.39.67:3306/gjdev?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai";
    private static final String DB_USER = "gongjiao";
    private static final String DB_PASSWORD = "Gj@sCt1@";

    private HikariDataSource dataSource;

    public OpenCloseDoorMsgDbService() {
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
            System.out.println("[OpenCloseDoorMsgDbService] 数据库连接池初始化完成");
        }
    }

    /**
     * 保存开关门WebSocket消息到数据库
     */
    public boolean saveOpenCloseDoorMsg(OpenCloseDoorMsg doorMsg) {
        String sql = "INSERT INTO open_close_door_msg (bus_no, bus_id, camera_no, action, timestamp, " +
                    "station_id, station_name, event, original_message, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, doorMsg.getBusNo());
            stmt.setString(2, doorMsg.getBusId());
            stmt.setString(3, doorMsg.getCameraNo());
            stmt.setString(4, doorMsg.getAction());
            stmt.setTimestamp(5, doorMsg.getParsedTimestamp() != null ?
                java.sql.Timestamp.valueOf(doorMsg.getParsedTimestamp()) : null);
            stmt.setString(6, doorMsg.getStationId());
            stmt.setString(7, doorMsg.getStationName());
            stmt.setString(8, doorMsg.getEvent());
            stmt.setString(9, doorMsg.getOriginalMessage());
            stmt.setTimestamp(10, java.sql.Timestamp.valueOf(LocalDateTime.now()));

            int result = stmt.executeUpdate();

            if (Config.LOG_DEBUG) {
                System.out.println(String.format("[OpenCloseDoorMsgDbService] 保存开关门消息成功: 车辆=%s, 动作=%s, 站点=%s",
                    doorMsg.getBusNo(), doorMsg.getAction(), doorMsg.getStationName()));
            }

            return result > 0;

        } catch (SQLException e) {
            if (Config.LOG_ERROR) {
                System.err.println(String.format("[OpenCloseDoorMsgDbService] 保存开关门消息失败: 车辆=%s, 错误=%s",
                    doorMsg.getBusNo(), e.getMessage()));
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
                System.out.println("[OpenCloseDoorMsgDbService] 数据库连接池已关闭");
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
                System.err.println("[OpenCloseDoorMsgDbService] 数据库连接测试失败: " + e.getMessage());
            }
            return false;
        }
    }
}
