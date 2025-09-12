package ai.servlet.passenger;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * CV系统downup消息数据库服务类
 * 负责连接PolarDB并保存downup消息到retrieve_downup_msg表
 */
public class RetrieveDownUpMsgDbService {

    // PolarDB连接配置
    private static final String DB_URL = "jdbc:mysql://20.17.39.67:3306/gjdev?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai";
    private static final String DB_USER = "gongjiao";
    private static final String DB_PASSWORD = "Gj@sCt1@";

    private HikariDataSource dataSource;

    public RetrieveDownUpMsgDbService() {
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
            System.out.println("[RetrieveDownUpMsgDbService] 数据库连接池初始化完成");
        }
    }

    /**
     * 保存CV downup消息到数据库
     */
    public boolean saveDownUpMsg(RetrieveDownUpMsg downUpMsg) {
        String sql = "INSERT INTO retrieve_downup_msg (bus_no, bus_id, camera_no, timestamp, event, " +
                    "events_json, up_count, down_count, original_message, created_at, sqe_no) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, downUpMsg.getBusNo());
            stmt.setString(2, downUpMsg.getBusId());
            stmt.setString(3, downUpMsg.getCameraNo());
            stmt.setTimestamp(4, downUpMsg.getParsedTimestamp() != null ?
                java.sql.Timestamp.valueOf(downUpMsg.getParsedTimestamp()) : null);
            stmt.setString(5, downUpMsg.getEvent());
            stmt.setString(6, downUpMsg.getEventsJson());
            stmt.setObject(7, downUpMsg.getUpCount());
            stmt.setObject(8, downUpMsg.getDownCount());
            stmt.setString(9, downUpMsg.getOriginalMessage());
            stmt.setTimestamp(10, java.sql.Timestamp.valueOf(LocalDateTime.now()));
            // 🔥 设置sqe_no字段
            stmt.setString(11, downUpMsg.getSqeNo());

            int result = stmt.executeUpdate();

            if (Config.LOG_DEBUG) {
                System.out.println(String.format("[RetrieveDownUpMsgDbService] 🔥 保存downup消息成功: 车辆=%s, 上车=%d, 下车=%d, sqe_no=%s",
                    downUpMsg.getBusNo(), downUpMsg.getUpCount(), downUpMsg.getDownCount(), downUpMsg.getSqeNo()));
            }

            return result > 0;

        } catch (SQLException e) {
            if (Config.LOG_ERROR) {
                System.err.println(String.format("[RetrieveDownUpMsgDbService] 保存downup消息失败: 车辆=%s, 错误=%s",
                    downUpMsg.getBusNo(), e.getMessage()));
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
                System.out.println("[RetrieveDownUpMsgDbService] 数据库连接池已关闭");
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
                System.err.println("[RetrieveDownUpMsgDbService] 数据库连接测试失败: " + e.getMessage());
            }
            return false;
        }
    }
}
