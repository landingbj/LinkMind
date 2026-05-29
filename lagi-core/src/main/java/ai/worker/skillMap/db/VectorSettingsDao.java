package ai.worker.skillMap.db;

import ai.common.pojo.UserRagSetting;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class VectorSettingsDao {

    private static final String DB_URL = "jdbc:sqlite:saas.db";
    private static HikariDataSource dataSource;

    static {
        try {
            initializeDataSource();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private synchronized static void initializeDataSource() {
        if (dataSource == null) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(DB_URL);
            config.setDriverClassName("org.sqlite.JDBC");
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            config.setLeakDetectionThreshold(60000);
            dataSource = new HikariDataSource(config);
        }
    }

    private static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            initializeDataSource();
        }
        return dataSource.getConnection();
    }


    public List<UserRagSetting> getUserRagVector(String category, String userId) throws SQLException {
        List<UserRagSetting> result = new ArrayList<>();
        String sql = "select id, user_id, file_type, category, chunk_size, temperature from user_rag_settings where category = ? and user_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, category);
            ps.setString(2, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                UserRagSetting userRagSetting = new UserRagSetting();
                userRagSetting.setId(rs.getInt(1));
                userRagSetting.setUserId(rs.getString(2));
                userRagSetting.setFileType(rs.getString(3));
                userRagSetting.setCategory(rs.getString(4));
                userRagSetting.setChunkSize(rs.getInt(5));
                userRagSetting.setTemperature(rs.getDouble(6));
                result.add(userRagSetting);
            }
        }catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

}
