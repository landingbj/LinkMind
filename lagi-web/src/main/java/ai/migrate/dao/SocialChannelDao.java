package ai.migrate.dao;

import ai.common.db.HikariDS;
import ai.dto.SocialChannel;
import ai.dto.SocialChannelMessage;
import ai.dto.SocialUser;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SocialChannelDao {
    private static volatile boolean initialized = false;

    private static synchronized void ensureTables() throws SQLException {
        if (initialized) {
            return;
        }
        try (Connection conn = HikariDS.getConnection("saas");
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("PRAGMA foreign_keys = ON");
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS social_users (" +
                            "user_id TEXT PRIMARY KEY," +
                            "username TEXT NOT NULL UNIQUE," +
                            "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                            ")"
            );
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS social_channels (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "name TEXT NOT NULL," +
                            "description TEXT," +
                            "owner_user_id TEXT NOT NULL," +
                            "is_public INTEGER NOT NULL DEFAULT 1 CHECK (is_public IN (0, 1))," +
                            "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "FOREIGN KEY (owner_user_id) REFERENCES social_users(user_id) ON DELETE CASCADE," +
                            "UNIQUE(name, owner_user_id)" +
                            ")"
            );
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS social_channel_subscriptions (" +
                            "user_id TEXT NOT NULL," +
                            "channel_id INTEGER NOT NULL," +
                            "subscribed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (user_id, channel_id)," +
                            "FOREIGN KEY (user_id) REFERENCES social_users(user_id) ON DELETE CASCADE," +
                            "FOREIGN KEY (channel_id) REFERENCES social_channels(id) ON DELETE CASCADE" +
                            ")"
            );
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS social_channel_messages (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "channel_id INTEGER NOT NULL," +
                            "user_id TEXT NOT NULL," +
                            "content TEXT NOT NULL," +
                            "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "FOREIGN KEY (channel_id) REFERENCES social_channels(id) ON DELETE CASCADE" +
                            ")"
            );
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_social_channels_owner ON social_channels(owner_user_id)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_social_subscriptions_channel ON social_channel_subscriptions(channel_id)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_social_messages_channel_created ON social_channel_messages(channel_id, created_at DESC)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_social_messages_user ON social_channel_messages(user_id)");
        }
        initialized = true;
    }

    // ---------- Users ----------

    public SocialUser findUserById(String userId) throws SQLException {
        if (userId == null || userId.trim().isEmpty()) {
            return null;
        }
        ensureTables();
        String sql = "SELECT user_id,username,created_at FROM social_users WHERE user_id = ?";
        try (Connection conn = HikariDS.getConnection("saas");
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
            }
        }
        return null;
    }

    public boolean userExists(String userId) throws SQLException {
        if (userId == null || userId.trim().isEmpty()) {
            return false;
        }
        ensureTables();
        String sql = "SELECT 1 FROM social_users WHERE user_id = ? LIMIT 1";
        try (Connection conn = HikariDS.getConnection("saas");
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Inserts the user when missing. The username must be unique; SQLite will
     * raise a constraint violation if another user already owns it.
     */
    public boolean registerUser(String userId, String username) throws SQLException {
        ensureTables();
        if (userId == null || userId.trim().isEmpty()) {
            throw new SQLException("userId is required");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new SQLException("username is required");
        }
        String sql = "INSERT OR IGNORE INTO social_users(user_id,username) VALUES(?,?)";
        try (Connection conn = HikariDS.getConnection("saas");
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId.trim());
            ps.setString(2, username.trim());
            return ps.executeUpdate() > 0;
        }
    }

    // ---------- Channels ----------

    public SocialChannel findChannelById(long channelId) throws SQLException {
        ensureTables();
        String sql = "SELECT id,name,description,owner_user_id,is_public,created_at FROM social_channels WHERE id = ?";
        try (Connection conn = HikariDS.getConnection("saas");
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, channelId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapChannel(rs);
                }
            }
        }
        return null;
    }

    public boolean isOwner(String userId, long channelId) throws SQLException {
        if (userId == null || userId.trim().isEmpty()) {
            return false;
        }
        ensureTables();
        String sql = "SELECT 1 FROM social_channels WHERE id = ? AND owner_user_id = ? LIMIT 1";
        try (Connection conn = HikariDS.getConnection("saas");
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, channelId);
            ps.setString(2, userId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean isSubscribed(String userId, long channelId) throws SQLException {
        if (userId == null || userId.trim().isEmpty()) {
            return false;
        }
        ensureTables();
        String sql = "SELECT 1 FROM social_channel_subscriptions WHERE user_id = ? AND channel_id = ? LIMIT 1";
        try (Connection conn = HikariDS.getConnection("saas");
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId.trim());
            ps.setLong(2, channelId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Creates a channel and subscribes the owner in one transaction.
     */
    public long createChannelWithOwnerSubscription(String ownerUserId, String name, String description, boolean isPublic)
            throws SQLException {
        ensureTables();
        String owner = ownerUserId == null ? "" : ownerUserId.trim();
        if (owner.isEmpty()) {
            throw new SQLException("ownerUserId is required");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new SQLException("name is required");
        }
        String desc = description == null ? "" : description;
        try (Connection conn = HikariDS.getConnection("saas")) {
            conn.setAutoCommit(false);
            try {
                String insertCh = "INSERT INTO social_channels(name,description,owner_user_id,is_public) VALUES(?,?,?,?)";
                long channelId;
                try (PreparedStatement ps = conn.prepareStatement(insertCh, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, name.trim());
                    ps.setString(2, desc);
                    ps.setString(3, owner);
                    ps.setInt(4, isPublic ? 1 : 0);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("failed to obtain channel id");
                        }
                        channelId = keys.getLong(1);
                    }
                }
                String insertSub = "INSERT OR IGNORE INTO social_channel_subscriptions(user_id,channel_id) VALUES(?,?)";
                try (PreparedStatement ps = conn.prepareStatement(insertSub)) {
                    ps.setString(1, owner);
                    ps.setLong(2, channelId);
                    ps.executeUpdate();
                }
                conn.commit();
                return channelId;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    // ---------- Subscriptions ----------

    public boolean addSubscription(String userId, long channelId) throws SQLException {
        ensureTables();
        if (userId == null || userId.trim().isEmpty()) {
            return false;
        }
        String sql = "INSERT OR IGNORE INTO social_channel_subscriptions(user_id,channel_id) VALUES(?,?)";
        try (Connection conn = HikariDS.getConnection("saas");
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId.trim());
            ps.setLong(2, channelId);
            return ps.executeUpdate() > 0;
        }
    }

    public int removeSubscription(String userId, long channelId) throws SQLException {
        ensureTables();
        if (userId == null || userId.trim().isEmpty()) {
            return 0;
        }
        String sql = "DELETE FROM social_channel_subscriptions WHERE user_id = ? AND channel_id = ?";
        try (Connection conn = HikariDS.getConnection("saas");
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId.trim());
            ps.setLong(2, channelId);
            return ps.executeUpdate();
        }
    }

    public List<SocialChannel> listSubscribedChannels(String userId) throws SQLException {
        ensureTables();
        List<SocialChannel> list = new ArrayList<SocialChannel>();
        if (userId == null || userId.trim().isEmpty()) {
            return list;
        }
        String sql = "SELECT c.id,c.name,c.description,c.owner_user_id,c.is_public,c.created_at " +
                "FROM social_channels c " +
                "INNER JOIN social_channel_subscriptions s ON s.channel_id = c.id " +
                "WHERE s.user_id = ? " +
                "ORDER BY c.created_at DESC";
        try (Connection conn = HikariDS.getConnection("saas");
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapChannel(rs));
                }
            }
        }
        return list;
    }

    public List<SocialChannel> findSubscribedChannelsByName(String userId, String channelName) throws SQLException {
        ensureTables();
        List<SocialChannel> list = new ArrayList<SocialChannel>();
        if (userId == null || userId.trim().isEmpty() || channelName == null || channelName.trim().isEmpty()) {
            return list;
        }
        String sql = "SELECT c.id,c.name,c.description,c.owner_user_id,c.is_public,c.created_at " +
                "FROM social_channels c " +
                "INNER JOIN social_channel_subscriptions s ON s.channel_id = c.id " +
                "WHERE s.user_id = ? AND c.name = ? " +
                "ORDER BY c.created_at DESC";
        try (Connection conn = HikariDS.getConnection("saas");
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId.trim());
            ps.setString(2, channelName.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapChannel(rs));
                }
            }
        }
        return list;
    }

    public List<SocialChannel> listPublicChannels(int limit) throws SQLException {
        ensureTables();
        int lim = limit <= 0 ? 50 : Math.min(limit, 200);
        List<SocialChannel> list = new ArrayList<SocialChannel>();
        String sql = "SELECT id,name,description,owner_user_id,is_public,created_at FROM social_channels " +
                "WHERE is_public = 1 ORDER BY created_at DESC LIMIT ?";
        try (Connection conn = HikariDS.getConnection("saas");
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, lim);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapChannel(rs));
                }
            }
        }
        return list;
    }

    // ---------- Messages ----------

    public List<SocialChannelMessage> listMessages(long channelId, int limit, Long beforeMessageId) throws SQLException {
        ensureTables();
        int lim = limit <= 0 ? 50 : Math.min(limit, 200);
        String sql = "SELECT m.id,m.channel_id,c.name AS channel_name,m.user_id,u.username AS user_name,m.content,m.created_at " +
                "FROM social_channel_messages m " +
                "INNER JOIN social_channels c ON c.id = m.channel_id " +
                "LEFT JOIN social_users u ON u.user_id = m.user_id " +
                "WHERE m.channel_id = ? " +
                (beforeMessageId != null && beforeMessageId > 0 ? "AND m.id < ? " : "") +
                "ORDER BY m.id DESC LIMIT ?";
        List<SocialChannelMessage> list = new ArrayList<SocialChannelMessage>();
        try (Connection conn = HikariDS.getConnection("saas");
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            ps.setLong(idx++, channelId);
            if (beforeMessageId != null && beforeMessageId > 0) {
                ps.setLong(idx++, beforeMessageId);
            }
            ps.setInt(idx, lim);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapMessage(rs));
                }
            }
        }
        return list;
    }

    public long insertMessage(long channelId, String userId, String content) throws SQLException {
        ensureTables();
        if (userId == null || userId.trim().isEmpty()) {
            throw new SQLException("userId is required");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new SQLException("content is required");
        }
        String sql = "INSERT INTO social_channel_messages(channel_id,user_id,content) VALUES(?,?,?)";
        try (Connection conn = HikariDS.getConnection("saas");
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, channelId);
            ps.setString(2, userId.trim());
            ps.setString(3, content.trim());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return -1;
    }

    // ---------- Mapping ----------

    private static SocialUser mapUser(ResultSet rs) throws SQLException {
        SocialUser u = new SocialUser();
        u.setUserId(rs.getString("user_id"));
        u.setUsername(rs.getString("username"));
        u.setCreatedAt(rs.getTimestamp("created_at"));
        return u;
    }

    private static SocialChannel mapChannel(ResultSet rs) throws SQLException {
        SocialChannel c = new SocialChannel();
        c.setId(rs.getLong("id"));
        c.setName(rs.getString("name"));
        c.setDescription(rs.getString("description"));
        c.setOwnerUserId(rs.getString("owner_user_id"));
        c.setIsPublic(rs.getInt("is_public") != 0);
        c.setCreatedAt(rs.getTimestamp("created_at"));
        return c;
    }

    private static SocialChannelMessage mapMessage(ResultSet rs) throws SQLException {
        SocialChannelMessage m = new SocialChannelMessage();
        m.setId(rs.getLong("id"));
        m.setChannelId(rs.getLong("channel_id"));
        m.setChannelName(rs.getString("channel_name"));
        m.setUserId(rs.getString("user_id"));
        String userName = rs.getString("user_name");
        m.setUserName(userName == null || userName.trim().isEmpty() ? m.getUserId() : userName);
        m.setContent(rs.getString("content"));
        m.setCreatedAt(rs.getTimestamp("created_at"));
        return m;
    }
}
