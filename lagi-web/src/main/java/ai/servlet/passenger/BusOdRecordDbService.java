package ai.servlet.passenger;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 公交OD记录数据库服务类
 * 负责查询和更新bus_od_record表
 */
public class BusOdRecordDbService {
    private static final Logger logger = LoggerFactory.getLogger(BusOdRecordDbService.class);

    // PostgreSQL连接配置
    private static final String DB_URL = "jdbc:postgresql://20.17.39.40:5432/GJ_DW";
    private static final String DB_USER = "bus_bike_reader_hzw";
    private static final String DB_PASSWORD = "bus_bike_reader_hzw@123";

    private static final DateTimeFormatter TRADE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private HikariDataSource dataSource;

    public BusOdRecordDbService() {
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
        config.setDriverClassName("org.postgresql.Driver");

        // 连接池配置
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setLeakDetectionThreshold(60000);

        this.dataSource = new HikariDataSource(config);

        if (Config.LOG_INFO) {
            logger.info("[BusOdRecordDbService] 数据库连接池初始化完成");
        }
    }

    /**
     * 查询bus_od_record表
     * 条件：bus_no匹配且时间差小于1分钟
     */
    public BusOdRecord findLatestByBusNoAndTime(String busNo, String tradeTime) {
        String sql = "SELECT * FROM ads.bus_od_record " +
                    "WHERE bus_no = ? " +
                    "  AND ABS(EXTRACT(EPOCH FROM (created_at - TIMESTAMP ?))) <= 60 " +
                    "ORDER BY created_at DESC " +
                    "LIMIT 1";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, busNo);
            stmt.setString(2, tradeTime);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    BusOdRecord record = new BusOdRecord();
                    record.setId(rs.getLong("id"));
                    record.setBusNo(rs.getString("bus_no"));

                    // 处理bus_id字段 - 可能是String或Long类型
                    String busIdStr = rs.getString("bus_id");
                    if (busIdStr != null && !busIdStr.isEmpty()) {
                        try {
                            record.setBusId(Long.parseLong(busIdStr));
                        } catch (NumberFormatException e) {
                            // 如果无法转换为Long，设置为null
                            record.setBusId(null);
                        }
                    } else {
                        record.setBusId(null);
                    }

                    record.setCameraNo(rs.getString("camera_no"));

                    // 设置当前站点名称（使用current_station_name字段）
                    record.setCurrentStationName(rs.getString("current_station_name"));

                    // 处理时间戳字段
                    java.sql.Timestamp timestampBegin = rs.getTimestamp("timestamp_begin");
                    if (timestampBegin != null) {
                        record.setTimestampBegin(timestampBegin.toLocalDateTime());
                    }

                    java.sql.Timestamp timestampEnd = rs.getTimestamp("timestamp_end");
                    if (timestampEnd != null) {
                        record.setTimestampEnd(timestampEnd.toLocalDateTime());
                    }

                    record.setUpCount(rs.getInt("up_count"));
                    record.setDownCount(rs.getInt("down_count"));
                    record.setTicketJson(rs.getString("ticket_json"));
                    record.setTicketUpCount(rs.getInt("ticket_up_count"));
                    record.setTicketDownCount(rs.getInt("ticket_down_count"));
                    record.setSqeNo(rs.getString("sqe_no"));

                    // 处理创建时间
                    java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
                    if (createdAt != null) {
                        record.setCreatedAt(createdAt.toLocalDateTime());
                    }

                    if (Config.LOG_DEBUG) {
                        logger.debug(String.format("[BusOdRecordDbService] 查询到记录: id=%d, busNo=%s, tradeTime=%s",
                            record.getId(), busNo, tradeTime));
                    }

                    return record;
                }
            }

            if (Config.LOG_DEBUG) {
                logger.debug(String.format("[BusOdRecordDbService] 未找到匹配记录: busNo=%s, tradeTime=%s",
                    busNo, tradeTime));
            }

            return null;

        } catch (SQLException e) {
            if (Config.LOG_ERROR) {
                logger.error(String.format("[BusOdRecordDbService] 查询失败: busNo=%s, tradeTime=%s, 错误=%s",
                    busNo, tradeTime, e.getMessage()), e);
            }
            return null;
        }
    }

    /**
     * 更新ticket_json字段
     */
    public boolean updateTicketJson(Long id, String ticketJson, int upCount, int downCount) {
        String sql = "UPDATE ads.bus_od_record SET " +
                    "ticket_json = ?, " +
                    "ticket_up_count = ?, " +
                    "ticket_down_count = ? " +
                    "WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, ticketJson);
            stmt.setInt(2, upCount);
            stmt.setInt(3, downCount);
            stmt.setLong(4, id);

            int result = stmt.executeUpdate();

            // 记录更新时间和详细信息
            LocalDateTime updateTime = LocalDateTime.now();
            if (Config.LOG_INFO) {
                logger.info(String.format("[BusOdRecordDbService] 🔥 更新ticket_json成功: id=%d, upCount=%d, downCount=%d, totalCount=%d, 更新时间=%s",
                    id, upCount, downCount, upCount + downCount, updateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
            }

            return result > 0;

        } catch (SQLException e) {
            if (Config.LOG_ERROR) {
                logger.error(String.format("[BusOdRecordDbService] 更新ticket_json失败: id=%d, 错误=%s",
                    id, e.getMessage()), e);
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
                logger.info("[BusOdRecordDbService] 数据库连接池已关闭");
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
                logger.error("[BusOdRecordDbService] 数据库连接测试失败: " + e.getMessage());
            }
            return false;
        }
    }
}
