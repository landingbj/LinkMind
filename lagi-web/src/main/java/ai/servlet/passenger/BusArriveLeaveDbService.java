package ai.servlet.passenger;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * 公交到离站数据数据库服务类
 * 负责连接PolarDB并保存到离站数据到bus_arrive_leave_logs表
 */
public class BusArriveLeaveDbService {

    // PolarDB连接配置
    private static final String DB_URL = "jdbc:mysql://20.17.39.67:3306/gjdev?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai";
    private static final String DB_USER = "gongjiao";
    private static final String DB_PASSWORD = "Gj@sCt1@";

    private HikariDataSource dataSource;

    public BusArriveLeaveDbService() {
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
            System.out.println("[BusArriveLeaveDbService] 数据库连接池初始化完成");
        }
    }

    /**
     * 保存到离站数据到数据库
     */
    public boolean saveArriveLeaveData(BusArriveLeaveData arriveLeaveData) {
        String sql = "INSERT INTO bus_arrive_leave_logs (bus_no, bus_self_no, bus_id, is_arrive_or_left, " +
                    "station_id, station_name, next_station_seq_num, route_no, traffic_type, direction, " +
                    "src_addr, seq_num, packet_time, pkt_type, original_message, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, arriveLeaveData.getBusNo());
            stmt.setString(2, arriveLeaveData.getBusSelfNo());
            stmt.setObject(3, arriveLeaveData.getBusId());
            stmt.setString(4, arriveLeaveData.getIsArriveOrLeft());
            stmt.setString(5, arriveLeaveData.getStationId());
            stmt.setString(6, arriveLeaveData.getStationName());
            stmt.setString(7, arriveLeaveData.getNextStationSeqNum());
            stmt.setString(8, arriveLeaveData.getRouteNo());
            stmt.setString(9, arriveLeaveData.getTrafficType());
            stmt.setString(10, arriveLeaveData.getDirection());
            stmt.setString(11, arriveLeaveData.getSrcAddr());
            stmt.setObject(12, arriveLeaveData.getSeqNum());
            stmt.setObject(13, arriveLeaveData.getPacketTime());
            stmt.setObject(14, arriveLeaveData.getPktType());
            stmt.setString(15, arriveLeaveData.getOriginalMessage());
            stmt.setTimestamp(16, java.sql.Timestamp.valueOf(LocalDateTime.now()));

            int result = stmt.executeUpdate();

            if (Config.LOG_DEBUG) {
                System.out.println(String.format("[BusArriveLeaveDbService] 保存到离站数据成功: 车辆=%s, 站点=%s, 状态=%s",
                    arriveLeaveData.getBusNo(), arriveLeaveData.getStationName(),
                    "1".equals(arriveLeaveData.getIsArriveOrLeft()) ? "到站" : "离站"));
            }

            return result > 0;

        } catch (SQLException e) {
            if (Config.LOG_ERROR) {
                System.err.println(String.format("[BusArriveLeaveDbService] 保存到离站数据失败: 车辆=%s, 错误=%s",
                    arriveLeaveData.getBusNo(), e.getMessage()));
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
                System.out.println("[BusArriveLeaveDbService] 数据库连接池已关闭");
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
                System.err.println("[BusArriveLeaveDbService] 数据库连接测试失败: " + e.getMessage());
            }
            return false;
        }
    }
}



