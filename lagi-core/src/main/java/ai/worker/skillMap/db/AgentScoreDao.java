package ai.worker.skillMap.db;

import ai.dao.Conn;
import ai.dao.IConn;
import ai.index.BaseIndex;
import ai.worker.pojo.AgentIntentScore;
import ai.worker.pojo.UserRagVector;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.*;

@Slf4j
public class AgentScoreDao {

    private static final String DB_URL = "jdbc:sqlite:skillMap.db";
    private static HikariDataSource dataSource;

    static {
        try {
            initializeDataSource();
            // 创建表
            try (Connection conn = getConnection()) {
                String sql = "CREATE TABLE IF NOT EXISTS agent_scores (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "agent_id INTEGER NOT NULL," +
                        "agent_name TEXT NOT NULL," +
                        "keyword TEXT NOT NULL," +
                        "question TEXT," +
                        "score REAL NOT NULL," +
                        "UNIQUE (agent_id, keyword)" +
                        ");";

                String sqlAgentKeywordLog = "CREATE TABLE IF NOT EXISTS agent_keyword_log (" +
                        "agent_id INTEGER NOT NULL," +
                        "keyword TEXT NOT NULL," +
                        "PRIMARY KEY (agent_id, keyword)" +
                        ");";

                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.executeUpdate();
                }

                try (PreparedStatement pstmtAgentKeywordLog = conn.prepareStatement(sqlAgentKeywordLog)) {
                    pstmtAgentKeywordLog.executeUpdate();
                }
            }
            log.info("AgentScoreDao initialized successfully");
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

    public void saveScore(Integer agentId, String agentName, String keyword, String question, Double score) {
        String sql = "INSERT INTO agent_scores(agent_id, agent_name,keyword, question, score) VALUES(?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, agentId);
            pstmt.setString(2, agentName);
            pstmt.setString(3, keyword);
            pstmt.setString(4, question);
            pstmt.setDouble(5, score);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Error connecting to database", e);
        }
    }

    public void saveOrUpdateScore(Integer agentId, String agentName, String keyword, String question, Double score) {
        String sql = "INSERT INTO agent_scores(agent_id, agent_name, keyword, question, score) \n" +
                "VALUES(?, ?, ?, ?, ?) \n" +
                "ON CONFLICT(agent_id, keyword) DO UPDATE SET \n" +
                "    agent_name = excluded.agent_name, \n" +
                "    keyword = excluded.keyword, \n" +
                "    question = excluded.question, \n" +
                "    score = excluded.score;";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, agentId);
            pstmt.setString(2, agentName);
            pstmt.setString(3, keyword);
            pstmt.setString(4, question);
            pstmt.setDouble(5, score);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Error connecting to database", e);
        }
    }

    public List<AgentIntentScore> getAgentScore(String keyword) {
        String sql = "SELECT agent_id, agent_name, keyword, question, score FROM agent_scores WHERE keyword = ? and score > 0";
        List<AgentIntentScore> scores = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, keyword);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                AgentIntentScore score = AgentIntentScore.builder()
                        .agentId(rs.getInt("agent_id"))
                        .agentName(rs.getString("agent_name"))
                        .keyword(rs.getString("keyword"))
                        .score(rs.getDouble("score"))
                        .question(rs.getString("question"))
                        .build();
                scores.add(score);
            }
        } catch (SQLException e) {
            log.error("Error connecting to database", e);
        }
        return scores;
    }

    public double getTotalScoreByAgentIdAndKeywords(int agentId, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return 0.0;
        }
        StringBuilder sqlBuilder = new StringBuilder("SELECT SUM(score) as total_score FROM agent_scores WHERE agent_id = ? AND keyword IN (");
        for (int i = 0; i < keywords.size(); i++) {
            sqlBuilder.append("?");
            if (i < keywords.size() - 1) {
                sqlBuilder.append(", ");
            }
        }
        sqlBuilder.append(")");

        String sql = sqlBuilder.toString();
        double totalScore = 0.0;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, agentId);
            for (int i = 0; i < keywords.size(); i++) {
                pstmt.setString(i + 2, keywords.get(i));
            }
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                totalScore = rs.getDouble("total_score");
            }
        } catch (SQLException e) {
            log.error("Error querying agent_scores", e);
        }
        return totalScore;
    }

    public void insertAgentKeywordLog(int agentId, String keyword) {
        String checkSql = "SELECT COUNT(*) FROM agent_keyword_log WHERE agent_id = ? AND keyword = ?";
        String insertSql = "INSERT INTO agent_keyword_log(agent_id, keyword) VALUES(?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkSql);
             PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
            checkStmt.setInt(1, agentId);
            checkStmt.setString(2, keyword);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next() && rs.getInt(1) == 0) {
                insertStmt.setInt(1, agentId);
                insertStmt.setString(2, keyword);
                insertStmt.executeUpdate();
            }
        } catch (Exception e) {
            log.error("Error inserting into agent_keyword_log", e);
        }
    }

    // 根据 keyword 查询所有对应的 agent_id 集合的方法
    public Set<Integer> getAgentIdsByKeyword(String keyword) {
        String sql = "SELECT agent_id FROM agent_keyword_log WHERE keyword = ?";
        Set<Integer> agentIds = new HashSet<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, keyword);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                agentIds.add(rs.getInt("agent_id"));
            }
        } catch (SQLException e) {
            log.error("Error querying agent_keyword_log", e);
        }
        return agentIds;
    }

    public Set<Integer> getAgentIdsByKeyword(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return Collections.emptySet();
        }
        StringBuilder sqlBuilder = new StringBuilder("SELECT agent_id FROM agent_keyword_log WHERE keyword IN (");
        for (int i = 0; i < keywords.size(); i++) {
            sqlBuilder.append("?");
            if (i < keywords.size() - 1) {
                sqlBuilder.append(", ");
            }
        }
        sqlBuilder.append(") GROUP BY agent_id having count(1) == ?");

        String sql = sqlBuilder.toString();
        Set<Integer> agentIds = new HashSet<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < keywords.size(); i++) {
                pstmt.setString(i + 1, keywords.get(i));
            }
            pstmt.setInt(keywords.size() + 1, keywords.size());
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                agentIds.add(rs.getInt("agent_id"));
            }
        } catch (SQLException e) {
            log.error("Error querying agent_keyword_log", e);
        }
        return agentIds;
    }


    public  Integer countAgentKeywords(int agentId, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return 0;
        }
        StringBuilder sqlBuilder = new StringBuilder("SELECT  COUNT(1) as count FROM agent_keyword_log WHERE agent_id = ? AND keyword IN (");
        for (int i = 0; i < keywords.size(); i++) {
            sqlBuilder.append("?");
            if (i < keywords.size() - 1) {
                sqlBuilder.append(", ");
            }
        }
        sqlBuilder.append(") GROUP BY agent_id");

        String sql = sqlBuilder.toString();
        int count = 0;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, agentId);
            for (int i = 0; i < keywords.size(); i++) {
                pstmt.setString(i + 2, keywords.get(i));
            }
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                count = rs.getInt("count");
            }
        } catch (SQLException e) {
            log.error("Error querying agent_keyword_log", e);
        }
        return count;
    }

    public List<AgentIntentScore> getAgentIdName() {
        String sql = "SELECT distinct agent_id, agent_name FROM agent_scores";
        List<AgentIntentScore> scores = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                AgentIntentScore score = AgentIntentScore.builder()
                        .agentId(rs.getInt("agent_id"))
                        .agentName(rs.getString("agent_name"))
                        .build();
                scores.add(score);
            }
        } catch (SQLException e) {
            log.error("Error connecting to database", e);
        }
        return scores;
    }

    public Integer getNameByID(String name) {
        String sql = "SELECT agent_id FROM agent_scores WHERE agent_name = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("agent_id");
            }
        } catch (SQLException e) {
            log.error("Error querying agent_scores", e);
        }
        return null;
    }

}
