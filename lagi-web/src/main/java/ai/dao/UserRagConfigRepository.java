package ai.dao;

import ai.servlet.dto.UserRagConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class UserRagConfigRepository {
    private static final String DB_URL = "jdbc:sqlite:saas.db";
    private static HikariDataSource dataSource;

    static {
        try {
            initializeDataSource();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize SQLite driver", e);
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

    // 根据用户ID和知识库ID查找 RAG 配置
    public UserRagConfig findByUserIdAndKnowledgeBaseId(String userId, Long knowledgeBaseId) {
        String sql = "SELECT id, user_id, knowledge_base_id, enable_fulltext, enable_graph, enable_text2qa, " +
                     "wenben_chunk_size, biaoge_chunk_size, tuwen_chunk_size, similarity_top_k, similarity_cutoff, " +
                     "created_at, updated_at " +
                     "FROM user_rag_config WHERE user_id = ? AND knowledge_base_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setLong(2, knowledgeBaseId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapToUserRagConfig(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // 保存或更新 RAG 配置
    public void save(UserRagConfig config) {
        String sql = config.getId() == null
                ? "INSERT INTO user_rag_config (user_id, knowledge_base_id, enable union fulltext, enable_graph, enable_text2qa, " +
                  "wenben_chunk_size, biaoge_chunk_size, tuwen_chunk_size, similarity_top_k, similarity_cutoff, created_at, updated_at) " +
                  "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                : "UPDATE user_rag_config SET user_id = ?, knowledge_base_id = ?, enable_fulltext = ?, enable_graph = ?, " +
                  "enable_text2qa = ?, wenben_chunk_size = ?, biaoge_chunk_size = ?, tuwen_chunk_size = ?, " +
                  "similarity_top_k = ?, similarity_cutoff = ?, updated_at = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, config.getUserId());
            ps.setLong(2, config.getKnowledgeBaseId());
            ps.setBoolean(3, config.isEnableFulltext());
            ps.setBoolean(4, config.isEnableGraph());
            ps.setBoolean(5, config.isEnableText2qa());
            ps.setInt(6, config.getWenbenChunkSize());
            ps.setInt(7, config.getBiaogeChunkSize());
            ps.setInt(8, config.getTuwenChunkSize());
            ps.setInt(9, config.getSimilarityTopK());
            ps.setDouble(10, config.getSimilarityCutoff());
            Timestamp now = new Timestamp(System.currentTimeMillis());
            if (config.getId() == null) {
                ps.setTimestamp(11, now);
                ps.setTimestamp(12, now);
            } else {
                ps.setTimestamp(11, now);
                ps.setLong(12, config.getId());
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 删除 RAG 配置
    public void deleteById(Long id) {
        String sql = "DELETE FROM user_rag_config WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 将 ResultSet 映射为 UserRagConfig 对象
    private UserRagConfig mapToUserRagConfig(ResultSet rs) throws SQLException {
        UserRagConfig config = new UserRagConfig();
        config.setId(rs.getLong("id"));
        config.setUserId(rs.getString("user_id"));
        config.setKnowledgeBaseId(rs.getLong("knowledge_base_id"));
        config.setEnableFulltext(rs.getBoolean("enable_fulltext"));
        config.setEnableGraph(rs.getBoolean("enable_graph"));
        config.setEnableText2qa(rs.getBoolean("enable_text2qa"));
        config.setWenbenChunkSize(rs.getInt("wenben_chunk_size"));
        config.setBiaogeChunkSize(rs.getInt("biaoge_chunk_size"));
        config.setTuwenChunkSize(rs.getInt("tuwen_chunk_size"));
        config.setSimilarityTopK(rs.getInt("similarity_top_k"));
        config.setSimilarityCutoff(rs.getDouble("similarity_cutoff"));
        return config;
    }
}
