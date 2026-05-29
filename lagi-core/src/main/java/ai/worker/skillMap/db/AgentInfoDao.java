package ai.worker.skillMap.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public class AgentInfoDao {

    private static final String DB_URL = "jdbc:sqlite:skillMap.db";
    private static HikariDataSource dataSource;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @ToString
    public static class AgentInfo {
        private Integer id;
        private Integer agentId;
        private String agentDescribe;
    }

    static {
        try {
            initializeDataSource();
            // 创建表
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(
                         "CREATE TABLE IF NOT EXISTS agent (" +
                         "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                         "agent_id INTEGER NOT NULL," +
                         "agent_describe TEXT NOT NULL," +
                         "UNIQUE (agent_id)" +
                         ");")) {
                pstmt.executeUpdate();
            }
            log.info("AgentInfoDao initialized successfully");
        } catch (SQLException e) {
            log.error("Error connecting to database", e);
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


    public static void addAgentInfo(AgentInfo agentInfo) {
        String sql = "INSERT INTO agent(agent_id, agent_describe) VALUES(?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, agentInfo.getAgentId());
            pstmt.setString(2, agentInfo.getAgentDescribe());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Error adding agent info", e);
        }
    }


    public static void deleteAgentInfoByAgentId(int agentId) {
        String sql = "DELETE FROM agent WHERE agent_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, agentId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Error deleting agent info by agent_id", e);
        }
    }


    public static void updateAgentInfo(AgentInfo agentInfo) {
        String sql = "UPDATE agent SET agent_describe = ? WHERE agent_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, agentInfo.getAgentDescribe());
            pstmt.setInt(2, agentInfo.getAgentId());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Error updating agent info", e);
        }
    }


    public static List<AgentInfo> getAgentInfosByAgentIds(List<Integer> agentIds) {
        List<AgentInfo> result = new ArrayList<>();
        if (agentIds == null || agentIds.isEmpty()) {
            return result;
        }

        String inClause = String.join(",", Collections.nCopies(agentIds.size(), "?"));
        String sql = "SELECT id, agent_id, agent_describe FROM agent WHERE agent_id IN (" + inClause + ")";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < agentIds.size(); i++) {
                pstmt.setInt(i + 1, agentIds.get(i));
            }

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                AgentInfo agentInfo = AgentInfo.builder()
                        .id(rs.getInt("id"))
                        .agentId(rs.getInt("agent_id"))
                        .agentDescribe(rs.getString("agent_describe"))
                        .build();
                result.add(agentInfo);
            }
        } catch (SQLException e) {
            log.error("Error fetching agent info by agent_ids", e);
        }

        return result;
    }

    public static void saveOrUpdate(AgentInfo agentInfo) {
        String checkExistSql = "SELECT COUNT(*) FROM agent WHERE agent_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkExistSql)) {

            checkStmt.setInt(1, agentInfo.getAgentId());
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                // 记录存在，执行更新
                String updateSql = "UPDATE agent SET agent_describe = ? WHERE agent_id = ?";
                try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                    updateStmt.setString(1, agentInfo.getAgentDescribe());
                    updateStmt.setInt(2, agentInfo.getAgentId());
                    updateStmt.executeUpdate();
                }
            } else {
                // 记录不存在，执行插入
                String insertSql = "INSERT INTO agent(agent_id, agent_describe) VALUES(?, ?)";
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.setInt(1, agentInfo.getAgentId());
                    insertStmt.setString(2, agentInfo.getAgentDescribe());
                    insertStmt.executeUpdate();
                }
            }

        } catch (SQLException e) {
            log.error("Error saving or updating agent info", e);
        }
    }


    public static AgentInfo getByAgentId(int agentId) {
        String sql = "SELECT id, agent_id, agent_describe FROM agent WHERE agent_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, agentId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return AgentInfo.builder()
                        .id(rs.getInt("id"))
                        .agentId(rs.getInt("agent_id"))
                        .agentDescribe(rs.getString("agent_describe"))
                        .build();
            }

        } catch (SQLException e) {
            log.error("Error fetching agent info by agent_id", e);
        }

        return null; // 如果没有找到记录，返回 null
    }

}
