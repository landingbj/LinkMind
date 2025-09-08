package ai.servlet.passenger;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * 所有消息记录数据库服务类
 * 负责连接PolarDB并保存所有消息到retrieve_all_msg表
 */
public class RetrieveAllMsgDbService {
    private static final Logger logger = LoggerFactory.getLogger(RetrieveAllMsgDbService.class);

    // PolarDB连接配置
    private static final String DB_URL = "jdbc:mysql://20.17.39.67:3306/gjdev?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai";
    private static final String DB_USER = "gongjiao";
    private static final String DB_PASSWORD = "Gj@sCt1@";

    private HikariDataSource dataSource;

    public RetrieveAllMsgDbService() {
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
            logger.info("[RetrieveAllMsgDbService] 数据库连接池初始化完成");
        }
    }

    /**
     * 保存所有消息到数据库
     */
    public boolean saveAllMessage(RetrieveAllMsg allMsg) {
        String sql = "INSERT INTO retrieve_all_msg (bus_no, message_type, topic, event, source, " +
                    "raw_message, bus_id, camera_no, station_id, station_name, route_no, " +
                    "message_timestamp, received_at, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, allMsg.getBusNo());
            stmt.setString(2, allMsg.getMessageType());
            stmt.setString(3, allMsg.getTopic());
            stmt.setString(4, allMsg.getEvent());
            stmt.setString(5, allMsg.getSource());
            stmt.setString(6, allMsg.getRawMessage());
            stmt.setString(7, allMsg.getBusId());
            stmt.setString(8, allMsg.getCameraNo());
            stmt.setString(9, allMsg.getStationId());
            stmt.setString(10, allMsg.getStationName());
            stmt.setString(11, allMsg.getRouteNo());
            stmt.setTimestamp(12, allMsg.getMessageTimestamp() != null ?
                java.sql.Timestamp.valueOf(allMsg.getMessageTimestamp()) : null);
            stmt.setTimestamp(13, allMsg.getReceivedAt() != null ?
                java.sql.Timestamp.valueOf(allMsg.getReceivedAt()) :
                java.sql.Timestamp.valueOf(LocalDateTime.now()));
            stmt.setTimestamp(14, java.sql.Timestamp.valueOf(LocalDateTime.now()));

            int result = stmt.executeUpdate();

            if (Config.LOG_DEBUG) {
                logger.info("[RetrieveAllMsgDbService] 保存消息成功: 车辆={}, 类型={}, 来源={}",
                    allMsg.getBusNo(), allMsg.getMessageType(), allMsg.getSource());
            }

            return result > 0;

        } catch (SQLException e) {
            if (Config.LOG_ERROR) {
                logger.error("[RetrieveAllMsgDbService] 保存消息失败: 车辆={}, 类型={}, 错误={}",
                    allMsg.getBusNo(), allMsg.getMessageType(), e.getMessage());
                logger.error("[RetrieveAllMsgDbService] 异常堆栈", e);
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
                logger.info("[RetrieveAllMsgDbService] 数据库连接池已关闭");
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
                logger.error("[RetrieveAllMsgDbService] 数据库连接测试失败: {}", e.getMessage());
            }
            return false;
        }
    }
}
