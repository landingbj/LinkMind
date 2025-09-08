package ai.servlet.passenger;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 公交刷卡数据数据库服务类
 * 负责连接PolarDB并保存刷卡数据到bus_card_swipe_data_logs表
 */
public class BusCardSwipeDbService {
    private static final Logger logger = LoggerFactory.getLogger(BusCardSwipeDbService.class);

    // PolarDB连接配置
    private static final String DB_URL = "jdbc:mysql://20.17.39.67:3306/gjdev?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai";
    private static final String DB_USER = "gongjiao";
    private static final String DB_PASSWORD = "Gj@sCt1@";

    private static final DateTimeFormatter TRADE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private HikariDataSource dataSource;

    public BusCardSwipeDbService() {
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
            logger.info("[BusCardSwipeDbService] 数据库连接池初始化完成");
        }
    }

    /**
     * 保存刷卡数据到数据库
     */
    public boolean saveCardSwipeData(BusCardSwipeData cardData) {
        String sql = "INSERT INTO bus_card_swipe_data_logs (bus_self_no, card_no, card_type, child_card_type, " +
                    "on_off, trade_no, trade_time, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // 解析交易时间
            LocalDateTime tradeTime = parseTradeTime(cardData.getTradeTime());

            stmt.setString(1, cardData.getBusSelfNo());
            stmt.setString(2, cardData.getCardNo());
            stmt.setString(3, cardData.getCardType());
            stmt.setString(4, cardData.getChildCardType());
            stmt.setString(5, cardData.getOnOff());
            stmt.setString(6, cardData.getTradeNo());
            stmt.setTimestamp(7, tradeTime != null ? java.sql.Timestamp.valueOf(tradeTime) : null);
            stmt.setTimestamp(8, java.sql.Timestamp.valueOf(LocalDateTime.now()));

            int result = stmt.executeUpdate();

            if (Config.LOG_DEBUG) {
                logger.debug(String.format("[BusCardSwipeDbService] 保存刷卡数据成功: 车辆=%s, 卡号=%s, 交易时间=%s",
                    cardData.getBusSelfNo(), cardData.getCardNo(), cardData.getTradeTime()));
            }

            return result > 0;

        } catch (SQLException e) {
            if (Config.LOG_ERROR) {
                logger.error(String.format("[BusCardSwipeDbService] 保存刷卡数据失败: 车辆=%s, 错误=%s",
                    cardData.getBusSelfNo(), e.getMessage()), e);
            }
            return false;
        }
    }

    /**
     * 解析交易时间字符串
     */
    private LocalDateTime parseTradeTime(String tradeTimeStr) {
        if (tradeTimeStr == null || tradeTimeStr.trim().isEmpty()) {
            return null;
        }

        try {
            return LocalDateTime.parse(tradeTimeStr.trim(), TRADE_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            if (Config.LOG_ERROR) {
                logger.error(String.format("[BusCardSwipeDbService] 解析交易时间失败: %s, 错误: %s",
                    tradeTimeStr, e.getMessage()));
            }
            return null;
        }
    }

    /**
     * 关闭数据库连接池
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            if (Config.LOG_INFO) {
                logger.info("[BusCardSwipeDbService] 数据库连接池已关闭");
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
                logger.error("[BusCardSwipeDbService] 数据库连接测试失败: " + e.getMessage());
            }
            return false;
        }
    }
}




