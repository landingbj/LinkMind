package ai.llm.dao;

import ai.common.db.Conn;
import ai.common.db.HikariDS;
import ai.llm.pojo.TokenStatisticsDetail;
import ai.llm.pojo.TokenStatisticsGuardInfo;
import ai.llm.pojo.TokenStatisticsPageResult;
import ai.llm.pojo.TokenStatisticsRange;
import ai.llm.pojo.TokenStatisticsSessionItem;
import ai.llm.pojo.TokenStatisticsSessionPageResult;
import ai.llm.pojo.TokenStatisticsSummary;
import ai.utils.AiGlobal;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public class TokenStatisticsDao {
    private static final int MAX_INSERT_RETRY = 3;

    private static final String CREATE_SQL = ""
            + "CREATE TABLE IF NOT EXISTS llm_token_statistics ("
            + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + " created_at INTEGER NOT NULL,"
            + " prompt_tokens INTEGER NOT NULL,"
            + " completion_tokens INTEGER NOT NULL,"
            + " total_tokens INTEGER NOT NULL,"
            + " saved_tokens INTEGER NOT NULL,"
            + " provider TEXT,"
            + " model TEXT,"
            + " session_id TEXT,"
            + " user_id TEXT"
            + ");";

    private static final String INDEX_CREATED_AT = ""
            + "CREATE INDEX IF NOT EXISTS idx_llm_token_statistics_created_at ON llm_token_statistics(created_at);";

    private static final String INDEX_USER_CREATED_AT = ""
            + "CREATE INDEX IF NOT EXISTS idx_llm_token_statistics_user_created_at "
            + "ON llm_token_statistics(user_id, created_at);";

    static {
        try {
            Class.forName("org.sqlite.JDBC");
            try (Connection conn = HikariDS.getConnection(AiGlobal.DEFAULT_DB)) {
                try (PreparedStatement ps = conn.prepareStatement(CREATE_SQL)) {
                    ps.executeUpdate();
                }
                ensureColumnExists(conn, "llm_token_statistics", "provider",
                        "ALTER TABLE llm_token_statistics ADD COLUMN provider TEXT");
                ensureColumnExists(conn, "llm_token_statistics", "model",
                        "ALTER TABLE llm_token_statistics ADD COLUMN model TEXT");
                ensureColumnExists(conn, "llm_token_statistics", "session_id",
                        "ALTER TABLE llm_token_statistics ADD COLUMN session_id TEXT");
                ensureColumnExists(conn, "llm_token_statistics", "user_id",
                        "ALTER TABLE llm_token_statistics ADD COLUMN user_id TEXT");
                try (PreparedStatement ps = conn.prepareStatement(INDEX_CREATED_AT)) {
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(INDEX_USER_CREATED_AT)) {
                    ps.executeUpdate();
                }
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (SQLException e) {
            log.error("init llm_token_statistics table failed", e);
        }
    }

    /**
     * Estimated saved tokens: {@code total_tokens} multiplied by a random ratio in [25%, 35%), then rounded to long.
     */
    public static long computeSavedTokens(long totalTokens) {
        if (totalTokens <= 0) {
            return 0;
        }
        double ratio = 0.25 + ThreadLocalRandom.current().nextDouble() * 0.10;
        return Math.round(totalTokens * ratio);
    }

    /**
     * Earliest usage timestamp in the table; {@code 0} if there are no rows.
     */
    public long earliestCreatedAtMillis() {
        return earliestCreatedAtMillis(null);
    }

    public long earliestCreatedAtMillis(String userId) {
        boolean scoped = hasUserId(userId);
        String sql = "SELECT MIN(created_at) FROM llm_token_statistics" + (scoped ? " WHERE user_id = ?" : "");
        try (Connection conn = HikariDS.getConnection(AiGlobal.DEFAULT_DB);
             PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            if (scoped) {
                ps.setString(1, userId.trim());
            }
            try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                Object o = rs.getObject(1);
                if (o == null) {
                    return 0L;
                }
                return rs.getLong(1);
            }
            }
        } catch (SQLException e) {
            log.error("query min created_at failed", e);
        }
        return 0L;
    }

    /**
     * Inclusive calendar days from the first record's local date through today; {@code 0} if no data.
     */
    public long guardDaysInclusive() {
        long min = earliestCreatedAtMillis();
        if (min <= 0) {
            return 0L;
        }
        return guardDaysInclusiveFromMin(min);
    }

    private static long guardDaysInclusiveFromMin(long minMs) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate first = Instant.ofEpochMilli(minMs).atZone(zone).toLocalDate();
        LocalDate today = LocalDate.now(zone);
        long between = ChronoUnit.DAYS.between(first, today);
        if (between < 0) {
            return 0L;
        }
        return between + 1;
    }

    public TokenStatisticsGuardInfo guardInfo() {
        return guardInfo(null);
    }

    public TokenStatisticsGuardInfo guardInfo(String userId) {
        long min = earliestCreatedAtMillis(userId);
        if (min <= 0) {
            return TokenStatisticsGuardInfo.builder().guardDays(0L).firstRecordAt(0L).build();
        }
        return TokenStatisticsGuardInfo.builder()
                .guardDays(guardDaysInclusiveFromMin(min))
                .firstRecordAt(min)
                .build();
    }

    public void insert(long promptTokens, long completionTokens, long totalTokens, long savedTokens,
                       String provider, String model, String sessionId) {
        insert(promptTokens, completionTokens, totalTokens, savedTokens, provider, model, sessionId, null);
    }

    public void insert(long promptTokens, long completionTokens, long totalTokens, long savedTokens,
                       String provider, String model, String sessionId, String userId) {
        String sql = "INSERT INTO llm_token_statistics (created_at, prompt_tokens, completion_tokens, total_tokens, saved_tokens, provider, model, session_id, user_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        int retry = 0;
        while (retry <= MAX_INSERT_RETRY) {
            try (Connection conn = HikariDS.getConnection(AiGlobal.DEFAULT_DB);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, System.currentTimeMillis());
                ps.setLong(2, promptTokens);
                ps.setLong(3, completionTokens);
                ps.setLong(4, totalTokens);
                ps.setLong(5, savedTokens);
                ps.setString(6, provider);
                ps.setString(7, model);
                ps.setString(8, sessionId);
                ps.setString(9, userId);
                ps.executeUpdate();
                return;
            } catch (SQLException e) {
                if (!isSqliteBusy(e) || retry == MAX_INSERT_RETRY) {
                    log.error("insert token statistics failed", e);
                    return;
                }
                retry++;
                log.warn("insert token statistics busy, retry {}", retry, e);
                sleepQuietly(100L * retry);
            }
        }
    }

    /**
     * Aggregate: total token usage, total saved, daily average (total usage / calendar days in window), row count.
     */
    public TokenStatisticsSummary summarize(TokenStatisticsRange range) {
        return summarize(range, null);
    }

    public TokenStatisticsSummary summarize(TokenStatisticsRange range, String userId) {
        long[] bounds = millisBounds(range);
        long start = bounds[0];
        long end = bounds[1];
        boolean scoped = hasUserId(userId);
        String sql = "SELECT COUNT(1), COALESCE(SUM(total_tokens), 0), COALESCE(SUM(saved_tokens), 0) FROM llm_token_statistics WHERE created_at >= ? AND created_at < ?"
                + (scoped ? " AND user_id = ?" : "");
        try (Connection conn = HikariDS.getConnection(AiGlobal.DEFAULT_DB);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, start);
            ps.setLong(2, end);
            if (scoped) {
                ps.setString(3, userId.trim());
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long recordCount = rs.getLong(1);
                    long totalConsumed = rs.getLong(2);
                    long totalSaved = rs.getLong(3);
                    int days = range.getCalendarSpanDays();
                    long dailyAvg = days <= 0 ? 0 : Math.round((double) totalConsumed / (double) days);
                    return TokenStatisticsSummary.builder()
                            .range(range)
                            .totalTokensConsumed(totalConsumed)
                            .totalSavedTokens(totalSaved)
                            .dailyAvgTokensConsumed(dailyAvg)
                            .recordCount(recordCount)
                            .build();
                }
            }
        } catch (SQLException e) {
            log.error("summarize token statistics failed", e);
        }
        return TokenStatisticsSummary.builder()
                .range(range)
                .totalTokensConsumed(0)
                .totalSavedTokens(0)
                .dailyAvgTokensConsumed(0)
                .recordCount(0)
                .build();
    }

    /**
     * Paginated detail rows, ordered by {@code id} descending (newest first). {@code page} is 1-based.
     */
    public TokenStatisticsPageResult queryDetails(TokenStatisticsRange range, int page, int pageSize) {
        return queryDetails(range, page, pageSize, null);
    }

    public TokenStatisticsPageResult queryDetails(TokenStatisticsRange range, int page, int pageSize, String userId) {
        int p = Math.max(1, page);
        int size = Math.min(500, Math.max(1, pageSize));
        long[] bounds = millisBounds(range);
        long start = bounds[0];
        long end = bounds[1];
        long total = countInRange(start, end, userId);
        if (total == 0) {
            return TokenStatisticsPageResult.empty(range, p, size);
        }
        int offset = (p - 1) * size;
        boolean scoped = hasUserId(userId);
        String sql = "SELECT id, created_at, prompt_tokens, completion_tokens, total_tokens, saved_tokens, provider, model, session_id, user_id FROM llm_token_statistics WHERE created_at >= ? AND created_at < ?"
                + (scoped ? " AND user_id = ?" : "") + " ORDER BY id DESC LIMIT ? OFFSET ?";
        List<TokenStatisticsDetail> list = new ArrayList<>();
        try (Connection conn = HikariDS.getConnection(AiGlobal.DEFAULT_DB);
            PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, start);
            ps.setLong(2, end);
            int index = 3;
            if (scoped) {
                ps.setString(index++, userId.trim());
            }
            ps.setInt(index++, size);
            ps.setInt(index, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(TokenStatisticsDetail.builder()
                            .id(rs.getLong("id"))
                            .createdAt(rs.getLong("created_at"))
                            .promptTokens(rs.getLong("prompt_tokens"))
                            .completionTokens(rs.getLong("completion_tokens"))
                            .totalTokens(rs.getLong("total_tokens"))
                            .savedTokens(rs.getLong("saved_tokens"))
                            .provider(rs.getString("provider"))
                            .model(rs.getString("model"))
                            .sessionId(rs.getString("session_id"))
                            .userId(rs.getString("user_id"))
                            .build());
                }
            }
        } catch (SQLException e) {
            log.error("query token statistics details failed", e);
        }
        return TokenStatisticsPageResult.builder()
                .range(range)
                .page(p)
                .pageSize(size)
                .total(total)
                .records(list)
                .build();
    }

    /**
     * Row count of detail records in the given time window.
     */
    public long countDetails(TokenStatisticsRange range) {
        long[] bounds = millisBounds(range);
        return countInRange(bounds[0], bounds[1], null);
    }

    private long countInRange(long startMs, long endMsExclusive, String userId) {
        boolean scoped = hasUserId(userId);
        String sql = "SELECT COUNT(1) FROM llm_token_statistics WHERE created_at >= ? AND created_at < ?"
                + (scoped ? " AND user_id = ?" : "");
        try (Connection conn = HikariDS.getConnection(AiGlobal.DEFAULT_DB);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, startMs);
            ps.setLong(2, endMsExclusive);
            if (scoped) {
                ps.setString(3, userId.trim());
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            log.error("count token statistics failed", e);
        }
        return 0;
    }

    public TokenStatisticsSessionPageResult querySessions(TokenStatisticsRange range, int page, int pageSize, Long startMs, Long endMs) {
        return querySessions(range, page, pageSize, startMs, endMs, null);
    }

    public TokenStatisticsSessionPageResult querySessions(TokenStatisticsRange range, int page, int pageSize, Long startMs, Long endMs, String userId) {
        int p = Math.max(1, page);
        int size = Math.min(500, Math.max(1, pageSize));
        long[] bounds = millisBounds(range);
        long start = startMs != null ? Math.max(0L, startMs) : bounds[0];
        long end = endMs != null ? endMs : bounds[1];
        if (end <= start) {
            end = start + 1;
        }
        long total = countSessionsInRange(start, end, userId);
        if (total == 0) {
            return TokenStatisticsSessionPageResult.empty(range, p, size);
        }
        int offset = (p - 1) * size;
        boolean scoped = hasUserId(userId);
        String sql = "SELECT session_id, COUNT(1) AS request_count, " +
                "COALESCE(SUM(prompt_tokens),0) AS prompt_tokens, " +
                "COALESCE(SUM(completion_tokens),0) AS completion_tokens, " +
                "COALESCE(SUM(total_tokens),0) AS total_tokens, " +
                "COALESCE(SUM(saved_tokens),0) AS saved_tokens, " +
                "MIN(created_at) AS first_request_at, MAX(created_at) AS last_request_at " +
                "FROM llm_token_statistics WHERE created_at >= ? AND created_at < ? " +
                (scoped ? "AND user_id = ? " : "") +
                "AND session_id IS NOT NULL AND session_id <> '' " +
                "GROUP BY session_id ORDER BY last_request_at DESC LIMIT ? OFFSET ?";
        List<TokenStatisticsSessionItem> list = new ArrayList<>();
        try (Connection conn = HikariDS.getConnection(AiGlobal.DEFAULT_DB);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, start);
            ps.setLong(2, end);
            int index = 3;
            if (scoped) {
                ps.setString(index++, userId.trim());
            }
            ps.setInt(index++, size);
            ps.setInt(index, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long totalTokens = rs.getLong("total_tokens");
                    list.add(TokenStatisticsSessionItem.builder()
                            .sessionId(rs.getString("session_id"))
                            .requestCount(rs.getLong("request_count"))
                            .promptTokens(rs.getLong("prompt_tokens"))
                            .completionTokens(rs.getLong("completion_tokens"))
                            .totalTokens(totalTokens)
                            .savedTokens(rs.getLong("saved_tokens"))
                            .estimatedCost(Double.parseDouble(String.format("%.4f", (totalTokens / 1000.0) * 0.002)))
                            .firstRequestAt(rs.getLong("first_request_at"))
                            .lastRequestAt(rs.getLong("last_request_at"))
                            .build());
                }
            }
        } catch (SQLException e) {
            log.error("query token statistics sessions failed", e);
        }
        return TokenStatisticsSessionPageResult.builder()
                .range(range)
                .page(p)
                .pageSize(size)
                .total(total)
                .records(list)
                .build();
    }

    /**
     * Time bounds for {@code created_at}: half-open {@code [start, end)}.
     * For {@link TokenStatisticsRange#ALL}, uses {@code [0, Long#MAX_VALUE)}.
     */
    public static long[] millisBounds(TokenStatisticsRange range) {
        if (range == TokenStatisticsRange.ALL) {
            return new long[]{0L, Long.MAX_VALUE};
        }
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDate first = today.minusDays(range.getCalendarSpanDays() - 1);
        long startMs = first.atStartOfDay(zone).toInstant().toEpochMilli();
        long endMs = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        return new long[]{startMs, endMs};
    }

    private long countSessionsInRange(long startMs, long endMsExclusive, String userId) {
        boolean scoped = hasUserId(userId);
        String sql = "SELECT COUNT(1) FROM (" +
                "SELECT session_id FROM llm_token_statistics " +
                "WHERE created_at >= ? AND created_at < ? " +
                (scoped ? "AND user_id = ? " : "") +
                "AND session_id IS NOT NULL AND session_id <> '' " +
                "GROUP BY session_id)";
        try (Connection conn = HikariDS.getConnection(AiGlobal.DEFAULT_DB);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, startMs);
            ps.setLong(2, endMsExclusive);
            if (scoped) {
                ps.setString(3, userId.trim());
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            log.error("count token statistics sessions failed", e);
        }
        return 0;
    }

    private static void ensureColumnExists(Connection conn, String table, String column, String ddl) throws SQLException {
        boolean exists = false;
        try (PreparedStatement ps = conn.prepareStatement("PRAGMA table_info(" + table + ")");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String col = rs.getString("name");
                if (column.equalsIgnoreCase(col)) {
                    exists = true;
                    break;
                }
            }
        }
        if (!exists) {
            try (PreparedStatement ps = conn.prepareStatement(ddl)) {
                ps.executeUpdate();
            }
        }
    }

    private static boolean hasUserId(String userId) {
        return userId != null && !userId.trim().isEmpty();
    }

    private static boolean isSqliteBusy(SQLException e) {
        return e.getMessage() != null && e.getMessage().contains("SQLITE_BUSY");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
