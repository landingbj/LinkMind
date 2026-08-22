package ai.account;

import ai.common.db.HikariDS;
import ai.utils.AiGlobal;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Database boundary for the shared Cuihua / LinkMind account model.
 * <p>
 * Cuihua owns {@code users}. LinkMind writes only its integration tables and
 * reads Cuihua users through views, so either application can evolve its own
 * data without duplicating accounts.
 */
@Slf4j
public class CuihuaAccountDao {
    private static final Object SCHEMA_LOCK = new Object();
    private static volatile boolean schemaReady;

    private static final String CREATE_CLIENT_TOKEN_TABLE = ""
            + "CREATE TABLE IF NOT EXISTS linkmind_client_api_key ("
            + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + " user_id TEXT NOT NULL,"
            + " token_hash TEXT NOT NULL UNIQUE,"
            + " token_prefix TEXT NOT NULL,"
            + " name TEXT NOT NULL DEFAULT '',"
            + " status INTEGER NOT NULL DEFAULT 1,"
            + " created_at INTEGER NOT NULL,"
            + " expires_at INTEGER,"
            + " last_used_at INTEGER"
            + ")";

    private static final String CREATE_LOGIN_SESSION_TABLE = ""
            + "CREATE TABLE IF NOT EXISTS linkmind_login_session ("
            + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + " user_id TEXT NOT NULL,"
            + " session_hash TEXT NOT NULL UNIQUE,"
            + " expires_at INTEGER NOT NULL,"
            + " created_at INTEGER NOT NULL,"
            + " last_seen_at INTEGER"
            + ")";

    /**
     * Ensures LinkMind's non-user tables and views exist. Returns false until
     * Cuihua has created the shared {@code users} table in the same database.
     */
    public boolean ensureSchema() {
        if (schemaReady) {
            return true;
        }
        synchronized (SCHEMA_LOCK) {
            if (schemaReady) {
                return true;
            }
            try (Connection conn = HikariDS.getConnection(AiGlobal.DEFAULT_DB)) {
                if (!hasUsersTable(conn)) {
                    return false;
                }
                try (Statement statement = conn.createStatement()) {
                    statement.executeUpdate(CREATE_CLIENT_TOKEN_TABLE);
                    statement.executeUpdate(CREATE_LOGIN_SESSION_TABLE);
                    statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_linkmind_client_api_key_user_id "
                            + "ON linkmind_client_api_key(user_id)");
                    statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_linkmind_login_session_user_id "
                            + "ON linkmind_login_session(user_id)");
                    statement.executeUpdate("CREATE VIEW IF NOT EXISTS linkmind_user_view AS "
                            + "SELECT id AS user_id, username, email, display_name, role, status, first_login, "
                            + "failed_attempts, locked_until, created_at FROM users");
                    statement.executeUpdate("CREATE VIEW IF NOT EXISTS linkmind_user_auth_view AS "
                            + "SELECT id AS user_id, username, email, display_name, password_hash, role, status, "
                            + "locked_until, created_at FROM users");
                    statement.executeUpdate("CREATE VIEW IF NOT EXISTS linkmind_user_token_usage_view AS "
                            + "SELECT u.user_id, u.username, u.email, u.display_name, s.provider, s.model, "
                            + "COUNT(s.id) AS request_count, "
                            + "COALESCE(SUM(s.prompt_tokens), 0) AS prompt_tokens, "
                            + "COALESCE(SUM(s.completion_tokens), 0) AS completion_tokens, "
                            + "COALESCE(SUM(s.total_tokens), 0) AS total_tokens, "
                            + "MIN(s.created_at) AS first_request_at, MAX(s.created_at) AS last_request_at "
                            + "FROM linkmind_user_view u LEFT JOIN llm_token_statistics s ON s.user_id = u.user_id "
                            + "GROUP BY u.user_id, u.username, u.email, u.display_name, s.provider, s.model");
                }
                schemaReady = true;
                return true;
            } catch (SQLException e) {
                log.error("initialize Cuihua account integration schema failed", e);
                return false;
            }
        }
    }

    public CuihuaUser findActiveUserByLogin(String login) throws SQLException {
        if (!ensureSchema() || isBlank(login)) {
            return null;
        }
        String sql = "SELECT user_id, username, email, display_name, password_hash, role, status, locked_until, created_at "
                + "FROM linkmind_user_auth_view WHERE (username = ? OR email = ?) "
                + "AND LOWER(status) = 'active' "
                + "AND (locked_until IS NULL OR datetime(locked_until) <= datetime('now')) LIMIT 1";
        try (Connection conn = HikariDS.getConnection(AiGlobal.DEFAULT_DB);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, login.trim());
            ps.setString(2, login.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapUser(rs) : null;
            }
        }
    }

    public CuihuaUser findActiveUserById(String userId) throws SQLException {
        if (!ensureSchema() || isBlank(userId)) {
            return null;
        }
        String sql = "SELECT user_id, username, email, display_name, password_hash, role, status, locked_until, created_at "
                + "FROM linkmind_user_auth_view WHERE user_id = ? AND LOWER(status) = 'active' "
                + "AND (locked_until IS NULL OR datetime(locked_until) <= datetime('now')) LIMIT 1";
        try (Connection conn = HikariDS.getConnection(AiGlobal.DEFAULT_DB);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapUser(rs) : null;
            }
        }
    }

    public LinkMindClientToken findClientToken(String tokenHash) throws SQLException {
        if (!ensureSchema() || isBlank(tokenHash)) {
            return null;
        }
        String sql = "SELECT id, user_id, token_prefix, name, status, created_at, expires_at, last_used_at "
                + "FROM linkmind_client_api_key WHERE token_hash = ? LIMIT 1";
        try (Connection conn = HikariDS.getConnection(AiGlobal.DEFAULT_DB);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapClientToken(rs) : null;
            }
        }
    }

    public void insertClientToken(LinkMindClientToken token, String tokenHash) throws SQLException {
        if (!ensureSchema()) {
            throw new SQLException("shared Cuihua users table is not available");
        }
        String sql = "INSERT INTO linkmind_client_api_key(user_id, token_hash, token_prefix, name, status, created_at, expires_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = HikariDS.getConnection(AiGlobal.DEFAULT_DB);
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, token.getUserId());
            ps.setString(2, tokenHash);
            ps.setString(3, token.getTokenPrefix());
            ps.setString(4, token.getName());
            ps.setInt(5, token.isEnabled() ? 1 : 0);
            ps.setLong(6, token.getCreatedAt());
            if (token.getExpiresAt() == null) {
                ps.setObject(7, null);
            } else {
                ps.setLong(7, token.getExpiresAt());
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    token.setId(keys.getLong(1));
                }
            }
        }
    }

    public List<LinkMindClientToken> listClientTokens(String userId) throws SQLException {
        if (!ensureSchema() || isBlank(userId)) {
            return new ArrayList<LinkMindClientToken>();
        }
        String sql = "SELECT id, user_id, token_prefix, name, status, created_at, expires_at, last_used_at "
                + "FROM linkmind_client_api_key WHERE user_id = ? ORDER BY id DESC";
        List<LinkMindClientToken> tokens = new ArrayList<LinkMindClientToken>();
        try (Connection conn = HikariDS.getConnection(AiGlobal.DEFAULT_DB);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tokens.add(mapClientToken(rs));
                }
            }
        }
        return tokens;
    }

    public LinkMindClientToken findClientToken(long id, String userId) throws SQLException {
        if (!ensureSchema() || id <= 0 || isBlank(userId)) {
            return null;
        }
        String sql = "SELECT id, user_id, token_prefix, name, status, created_at, expires_at, last_used_at "
                + "FROM linkmind_client_api_key WHERE id = ? AND user_id = ? LIMIT 1";
        try (Connection conn = HikariDS.getConnection(AiGlobal.DEFAULT_DB);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setString(2, userId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapClientToken(rs) : null;
            }
        }
    }

    public boolean updateClientToken(LinkMindClientToken token) throws SQLException {
        if (!ensureSchema() || token == null || token.getId() <= 0 || isBlank(token.getUserId())) {
            return false;
        }
        String sql = "UPDATE linkmind_client_api_key SET name = ?, status = ?, expires_at = ? "
                + "WHERE id = ? AND user_id = ?";
        try (Connection conn = HikariDS.getConnection(AiGlobal.DEFAULT_DB);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token.getName() == null ? "" : token.getName().trim());
            ps.setInt(2, token.isEnabled() ? 1 : 0);
            if (token.getExpiresAt() == null) {
                ps.setObject(3, null);
            } else {
                ps.setLong(3, token.getExpiresAt());
            }
            ps.setLong(4, token.getId());
            ps.setString(5, token.getUserId().trim());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean revokeClientToken(long id, String userId) throws SQLException {
        if (!ensureSchema() || id <= 0 || isBlank(userId)) {
            return false;
        }
        try (Connection conn = HikariDS.getConnection(AiGlobal.DEFAULT_DB);
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE linkmind_client_api_key SET status = 0 WHERE id = ? AND user_id = ?")) {
            ps.setLong(1, id);
            ps.setString(2, userId.trim());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteClientToken(long id, String userId) throws SQLException {
        if (!ensureSchema() || id <= 0 || isBlank(userId)) {
            return false;
        }
        try (Connection conn = HikariDS.getConnection(AiGlobal.DEFAULT_DB);
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM linkmind_client_api_key WHERE id = ? AND user_id = ?")) {
            ps.setLong(1, id);
            ps.setString(2, userId.trim());
            return ps.executeUpdate() > 0;
        }
    }

    public void touchClientToken(long id) {
        if (id <= 0 || !ensureSchema()) {
            return;
        }
        try (Connection conn = HikariDS.getConnection(AiGlobal.DEFAULT_DB);
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE linkmind_client_api_key SET last_used_at = ? WHERE id = ?")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warn("update LinkMind client token last-used time failed: {}", e.getMessage());
        }
    }

    public void insertLoginSession(String userId, String sessionHash, long expiresAt) throws SQLException {
        if (!ensureSchema()) {
            throw new SQLException("shared Cuihua users table is not available");
        }
        String sql = "INSERT INTO linkmind_login_session(user_id, session_hash, expires_at, created_at, last_seen_at) "
                + "VALUES (?, ?, ?, ?, ?)";
        long now = System.currentTimeMillis();
        try (Connection conn = HikariDS.getConnection(AiGlobal.DEFAULT_DB);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, sessionHash);
            ps.setLong(3, expiresAt);
            ps.setLong(4, now);
            ps.setLong(5, now);
            ps.executeUpdate();
        }
    }

    public CuihuaUser findActiveUserBySessionHash(String sessionHash) throws SQLException {
        if (!ensureSchema() || isBlank(sessionHash)) {
            return null;
        }
        String sql = "SELECT u.user_id, u.username, u.email, u.display_name, u.password_hash, u.role, u.status, "
                + "u.locked_until, u.created_at FROM linkmind_login_session s "
                + "JOIN linkmind_user_auth_view u ON u.user_id = s.user_id "
                + "WHERE s.session_hash = ? AND s.expires_at > ? AND LOWER(u.status) = 'active' "
                + "AND (u.locked_until IS NULL OR datetime(u.locked_until) <= datetime('now')) LIMIT 1";
        try (Connection conn = HikariDS.getConnection(AiGlobal.DEFAULT_DB);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionHash);
            ps.setLong(2, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapUser(rs) : null;
            }
        }
    }

    public void touchLoginSession(String sessionHash) {
        if (isBlank(sessionHash) || !ensureSchema()) {
            return;
        }
        try (Connection conn = HikariDS.getConnection(AiGlobal.DEFAULT_DB);
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE linkmind_login_session SET last_seen_at = ? WHERE session_hash = ?")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, sessionHash);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warn("update LinkMind session last-seen time failed: {}", e.getMessage());
        }
    }

    public void deleteLoginSession(String sessionHash) throws SQLException {
        if (isBlank(sessionHash) || !ensureSchema()) {
            return;
        }
        try (Connection conn = HikariDS.getConnection(AiGlobal.DEFAULT_DB);
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM linkmind_login_session WHERE session_hash = ?")) {
            ps.setString(1, sessionHash);
            ps.executeUpdate();
        }
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                result.append(String.format("%02x", b & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private boolean hasUsersTable(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'users' LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        }
    }

    private CuihuaUser mapUser(ResultSet rs) throws SQLException {
        return CuihuaUser.builder()
                .userId(rs.getString("user_id"))
                .username(rs.getString("username"))
                .email(rs.getString("email"))
                .displayName(rs.getString("display_name"))
                .passwordHash(rs.getString("password_hash"))
                .role(rs.getString("role"))
                .status(rs.getString("status"))
                .lockedUntil(rs.getString("locked_until"))
                .createdAt(rs.getLong("created_at"))
                .build();
    }

    private LinkMindClientToken mapClientToken(ResultSet rs) throws SQLException {
        long expiresAt = rs.getLong("expires_at");
        boolean hasExpiresAt = !rs.wasNull();
        long lastUsedAt = rs.getLong("last_used_at");
        boolean hasLastUsedAt = !rs.wasNull();
        return LinkMindClientToken.builder()
                .id(rs.getLong("id"))
                .userId(rs.getString("user_id"))
                .tokenPrefix(rs.getString("token_prefix"))
                .name(rs.getString("name"))
                .enabled(rs.getInt("status") == 1)
                .createdAt(rs.getLong("created_at"))
                .expiresAt(hasExpiresAt ? expiresAt : null)
                .lastUsedAt(hasLastUsedAt ? lastUsedAt : null)
                .build();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
