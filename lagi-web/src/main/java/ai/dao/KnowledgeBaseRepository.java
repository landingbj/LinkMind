package ai.dao;

import ai.servlet.dto.KnowledgeBase;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class KnowledgeBaseRepository {
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

    // 根据用户ID和分类查找知识库
    public KnowledgeBase findByUserIdAndCategory(String userId, String category) {
        String sql = "SELECT id, user_id, name, description, category, is_public, is_active, created_at, updated_at " +
                     "FROM knowledge_base WHERE user_id = ? AND category = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, category);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapToKnowledgeBase(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // 根据用户ID和活跃状态查找知识库
    public KnowledgeBase findByUserIdAndIsActive(String userId, boolean isActive) {
        String sql = "SELECT id, user_id, name, description, category, is_public, is_active, created_at, updated_at " +
                     "FROM knowledge_base WHERE user_id = ? AND is_active = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setBoolean(2, isActive);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapToKnowledgeBase(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // 查找用户的第一个知识库（按创建时间排序）
    public KnowledgeBase findFirstByUserId(String userId) {
        String sql = "SELECT id, user_id, name, description, category, is_public, is_active, created_at, updated_at " +
                     "FROM knowledge_base WHERE user_id = ? ORDER BY created_at ASC LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapToKnowledgeBase(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // 将用户的所有知识库设置为非活跃
    public void updateAllIsActiveToFalse(String userId) {
        String sql = "UPDATE knowledge_base SET is_active = ? WHERE user_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, false);
            ps.setString(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 更新指定知识库的活跃状态
    public void updateIsActive(String userId, Long knowledgeBaseId, boolean isActive) {
        String sql = "UPDATE knowledge_base SET is_active = ? WHERE user_id = ? AND id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, isActive);
            ps.setString(2, userId);
            ps.setLong(3, knowledgeBaseId);
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected == 0) {
                throw new RuntimeException("No knowledge base found for userId: " + userId + " and id: " + knowledgeBaseId);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 保存或更新知识库
    public void save(KnowledgeBase kb) {
        String sql = kb.getId() == null
                ? "INSERT INTO knowledge_base (user_id, name, description, category, is_public, is_active, created_at, updated_at) " +
                  "VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                : "UPDATE knowledge_base SET user_id = ?, name = ?, description = ?, category = ?, is_public = ?, is_active = ?, updated_at = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, kb.getUserId());
            ps.setString(2, kb.getName());
            ps.setString(3, kb.getDescription());
            ps.setString(4, kb.getCategory());
            ps.setBoolean(5, kb.isPublic());
            ps.setBoolean(6, kb.isActive());
            Timestamp now = new Timestamp(System.currentTimeMillis());
            if (kb.getId() == null) {
                ps.setTimestamp(7, now);
                ps.setTimestamp(8, now);
            } else {
                ps.setTimestamp(7, now);
                ps.setLong(8, kb.getId());
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 删除知识库
    public void deleteById(Long id) {
        String sql = "DELETE FROM knowledge_base WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 将 ResultSet 映射为 KnowledgeBase 对象
    private KnowledgeBase mapToKnowledgeBase(ResultSet rs) throws SQLException {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(rs.getLong("id"));
        kb.setUserId(rs.getString("user_id"));
        kb.setName(rs.getString("name"));
        kb.setDescription(rs.getString("description"));
        kb.setCategory(rs.getString("category"));
        kb.setPublic(rs.getBoolean("is_public"));
        kb.setActive(rs.getBoolean("is_active"));
        return kb;
    }

    public KnowledgeBase findDefaultByUserId(String userId) {
        String sql = "SELECT * FROM knowledge_base WHERE user_id = ? AND is_default = 1 LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapToKnowledgeBase(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

}
