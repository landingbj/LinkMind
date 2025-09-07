package ai.servlet.passenger;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * CV系统满载率消息数据库服务类
 * 负责连接PolarDB并保存满载率消息到retrieve_load_factor_msg表
 */
public class RetrieveLoadFactorMsgDbService {

    // PolarDB连接配置
    private static final String DB_URL = "jdbc:mysql://20.17.39.67:3306/gjdev?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai";
    private static final String DB_USER = "gongjiao";
    private static final String DB_PASSWORD = "Gj@sCt1@";

    private HikariDataSource dataSource;

    public RetrieveLoadFactorMsgDbService() {
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
            System.out.println("[RetrieveLoadFactorMsgDbService] 数据库连接池初始化完成");
        }
    }

    /**
     * 保存CV满载率消息到数据库
     */
    public boolean saveLoadFactorMsg(RetrieveLoadFactorMsg loadFactorMsg) {
        String sql = "INSERT INTO retrieve_load_factor_msg (bus_no, camera_no, timestamp, event, " +
                    "passenger_count, load_factor, original_message, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, loadFactorMsg.getBusNo());
            stmt.setString(2, loadFactorMsg.getCameraNo());
            stmt.setTimestamp(3, loadFactorMsg.getParsedTimestamp() != null ?
                java.sql.Timestamp.valueOf(loadFactorMsg.getParsedTimestamp()) : null);
            stmt.setString(4, loadFactorMsg.getEvent());
            stmt.setObject(5, loadFactorMsg.getCount());
            stmt.setBigDecimal(6, loadFactorMsg.getFactor());
            stmt.setString(7, loadFactorMsg.getOriginalMessage());
            stmt.setTimestamp(8, java.sql.Timestamp.valueOf(LocalDateTime.now()));

            int result = stmt.executeUpdate();

            if (Config.LOG_DEBUG) {
                System.out.println(String.format("[RetrieveLoadFactorMsgDbService] 保存满载率消息成功: 车辆=%s, 人数=%d, 满载率=%s",
                    loadFactorMsg.getBusNo(), loadFactorMsg.getCount(), loadFactorMsg.getFactorPercentage()));
            }

            return result > 0;

        } catch (SQLException e) {
            if (Config.LOG_ERROR) {
                System.err.println(String.format("[RetrieveLoadFactorMsgDbService] 保存满载率消息失败: 车辆=%s, 错误=%s",
                    loadFactorMsg.getBusNo(), e.getMessage()));
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
                System.out.println("[RetrieveLoadFactorMsgDbService] 数据库连接池已关闭");
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
                System.err.println("[RetrieveLoadFactorMsgDbService] 数据库连接测试失败: " + e.getMessage());
            }
            return false;
        }
    }
}
