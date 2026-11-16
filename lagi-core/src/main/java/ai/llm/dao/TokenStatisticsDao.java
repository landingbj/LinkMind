package ai.llm.dao;

import ai.common.db.HikariDS;
import ai.llm.pojo.TokenStatisticsCurrencyCost;
import ai.llm.pojo.TokenStatisticsDetail;
import ai.llm.pojo.TokenStatisticsGuardInfo;
import ai.llm.pojo.TokenStatisticsPageResult;
import ai.llm.pojo.TokenStatisticsRange;
import ai.llm.pojo.TokenStatisticsSessionItem;
import ai.llm.pojo.TokenStatisticsSessionPageResult;
import ai.llm.pojo.TokenStatisticsSummary;
import ai.llm.pojo.TokenStatisticsTrendBucket;
import ai.llm.pojo.TokenStatisticsUserTrend;
import ai.llm.pojo.TokenStatisticsUserModelUsage;
import ai.llm.pojo.TokenStatisticsUserUsage;
import ai.llm.pojo.TokenUsageCost;
import ai.utils.AiGlobal;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public class TokenStatisticsDao {
    private static final int MAX_INSERT_RETRY = 3;
    private static final Object COST_NORMALIZATION_LOCK = new Object();

    private static final String CREATE_SQL = ""
            + "CREATE TABLE IF NOT EXISTS llm_token_statistics ("
            + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + " created_at INTEGER NOT NULL,"
            + " prompt_tokens INTEGER NOT NULL,"
            + " cached_input_tokens INTEGER NOT NULL DEFAULT 0,"
            + " completion_tokens INTEGER NOT NULL,"
            + " total_tokens INTEGER NOT NULL,"
            + " saved_tokens INTEGER NOT NULL,"
            + " provider TEXT,"
            + " model TEXT,"
            + " session_id TEXT,"
            + " user_id TEXT,"
            + " user_api_key_id INTEGER,"
            + " input_cost DECIMAL(20,8),"
            + " output_cost DECIMAL(20,8),"
            + " total_cost DECIMAL(20,8),"
            + " currency TEXT,"
            + " price_source TEXT"
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
                ensureColumnExists(conn, "llm_token_statistics", "cached_input_tokens",
                        "ALTER TABLE llm_token_statistics ADD COLUMN cached_input_tokens INTEGER NOT NULL DEFAULT 0");
                ensureColumnExists(conn, "llm_token_statistics", "session_id",
                        "ALTER TABLE llm_token_statistics ADD COLUMN session_id TEXT");
                ensureColumnExists(conn, "llm_token_statistics", "user_id",
                        "ALTER TABLE llm_token_statistics ADD COLUMN user_id TEXT");
                ensureColumnExists(conn, "llm_token_statistics", "user_api_key_id",
                        "ALTER TABLE llm_token_statistics ADD COLUMN user_api_key_id INTEGER");
                ensureColumnExists(conn, "llm_token_statistics", "input_cost",
                        "ALTER TABLE llm_token_statistics ADD COLUMN input_cost DECIMAL(20,8)");
                ensureColumnExists(conn, "llm_token_statistics", "output_cost",
                        "ALTER TABLE llm_token_statistics ADD COLUMN output_cost DECIMAL(20,8)");
                ensureColumnExists(conn, "llm_token_statistics", "total_cost",
                        "ALTER TABLE llm_token_statistics ADD COLUMN total_cost DECIMAL(20,8)");
                ensureColumnExists(conn, "llm_token_statistics", "currency",
                        "ALTER TABLE llm_token_statistics ADD COLUMN currency TEXT");
                ensureColumnExists(conn, "llm_token_statistics", "price_source",
                        "ALTER TABLE llm_token_statistics ADD COLUMN price_source TEXT");
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
        insert(promptTokens, completionTokens, totalTokens, savedTokens, provider, model, sessionId, userId, null);
    }

    public void insert(long promptTokens, long completionTokens, long totalTokens, long savedTokens,
                       String provider, String model, String sessionId, String userId, Long userApiKeyId) {
        insert(promptTokens, 0L, completionTokens, totalTokens, savedTokens,
                provider, model, sessionId, userId, userApiKeyId);
    }

    public void insert(long promptTokens, long cachedInputTokens, long completionTokens, long totalTokens, long savedTokens,
                       String provider, String model, String sessionId, String userId, Long userApiKeyId) {
        TokenUsageCost cost = new ModelPriceDao().calculate(provider, model, promptTokens, cachedInputTokens,
                completionTokens);
        String sql = "INSERT INTO llm_token_statistics (created_at, prompt_tokens, cached_input_tokens, completion_tokens, "
                + "total_tokens, saved_tokens, provider, model, session_id, user_id, user_api_key_id, input_cost, output_cost, "
                + "total_cost, currency, price_source) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        int retry = 0;
        while (retry <= MAX_INSERT_RETRY) {
            try (Connection conn = HikariDS.getConnection(AiGlobal.DEFAULT_DB);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, System.currentTimeMillis());
                ps.setLong(2, promptTokens);
                ps.setLong(3, Math.min(Math.max(0L, cachedInputTokens), Math.max(0L, promptTokens)));
                ps.setLong(4, completionTokens);
                ps.setLong(5, totalTokens);
                ps.setLong(6, savedTokens);
                ps.setString(7, provider);
                ps.setString(8, model);
                ps.setString(9, sessionId);
                ps.setString(10, userId);
                if (userApiKeyId == null) {
                    ps.setNull(11, java.sql.Types.INTEGER);
                } else {
                    ps.setLong(11, userApiKeyId);
                }
                setNullableDecimal(ps, 12, cost == null ? null : cost.getInputCost());
                setNullableDecimal(ps, 13, cost == null ? null : cost.getOutputCost());
                setNullableDecimal(ps, 14, cost == null ? null : cost.getTotalCost());
                ps.setString(15, cost == null ? null : cost.getCurrency());
                ps.setString(16, cost == null ? null : cost.getPriceSource());
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
        normalizeHistoricalCostsToCny();
        long[] bounds = millisBounds(range);
        long start = bounds[0];
        long end = bounds[1];
        boolean scoped = hasUserId(userId);
        String sql = "SELECT COUNT(1), COALESCE(SUM(total_tokens), 0), COALESCE(SUM(saved_tokens), 0), "
                + "COALESCE(SUM(prompt_tokens), 0), COALESCE(SUM(completion_tokens), 0), "
                + "COALESCE(SUM(CASE WHEN total_cost IS NOT NULL THEN 1 ELSE 0 END), 0) "
                + "FROM llm_token_statistics WHERE created_at >= ? AND created_at < ?"
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
                    long promptTokens = rs.getLong(4);
                    long completionTokens = rs.getLong(5);
                    long pricedRecordCount = rs.getLong(6);
                    int days = calendarDayCount(start, end);
                    long dailyAvg = days <= 0 ? 0 : Math.round((double) totalConsumed / (double) days);
                    List<TokenStatisticsCurrencyCost> costs = summarizeCosts(start, end, userId);
                    TokenStatisticsSummary.TokenStatisticsSummaryBuilder builder = TokenStatisticsSummary.builder()
                            .range(range)
                            .totalTokensConsumed(totalConsumed)
                            .totalSavedTokens(totalSaved)
                            .dailyAvgTokensConsumed(dailyAvg)
                            .recordCount(recordCount)
                            .totalPromptTokens(promptTokens)
                            .totalCompletionTokens(completionTokens)
                            .pricedRecordCount(pricedRecordCount)
                            .unpricedRecordCount(recordCount - pricedRecordCount)
                            .costsByCurrency(costs);
                    if (costs.size() == 1) {
                        builder.currency(costs.get(0).getCurrency()).totalCost(costs.get(0).getTotalCost());
                    }
                    return builder.build();
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

    /** Aggregates usage by attributed user for an exact half-open time window. */
    public List<TokenStatisticsUserUsage> queryUserUsage(long startMs, long endMsExclusive, String userId) {
        return queryUserUsage(startMs, endMsExclusive, userId, null, Collections.<String>emptyList());
    }

    /** Aggregates usage by user after applying the configured platform selection. */
    public List<TokenStatisticsUserUsage> queryUserUsage(long startMs, long endMsExclusive, String userId,
                                                         String provider, List<String> configuredProviders) {
        normalizeHistoricalCostsToCny();
        boolean scoped = hasUserId(userId);
        ProviderSqlFilter providerFilter = providerSqlFilter(provider, configuredProviders);
        String normalizedUser = "CASE WHEN user_id IS NULL OR TRIM(user_id) = '' "
                + "THEN NULL ELSE TRIM(user_id) END";
        String sql = "SELECT " + normalizedUser + " AS usage_user_id, COUNT(1) AS request_count, "
                + "COALESCE(SUM(prompt_tokens),0) AS prompt_tokens, "
                + "COALESCE(SUM(completion_tokens),0) AS completion_tokens, "
                + "COALESCE(SUM(total_tokens),0) AS total_tokens, "
                + "SUM(CASE WHEN total_cost IS NOT NULL AND UPPER(currency) = 'CNY' THEN total_cost END) AS total_cost, "
                + "COALESCE(SUM(CASE WHEN total_cost IS NOT NULL AND UPPER(currency) = 'CNY' "
                + "THEN 1 ELSE 0 END),0) AS priced_record_count, "
                + "COALESCE(SUM(CASE WHEN total_cost IS NULL OR currency IS NULL OR UPPER(currency) <> 'CNY' "
                + "THEN 1 ELSE 0 END),0) AS unpriced_record_count "
                + "FROM llm_token_statistics WHERE created_at >= ? AND created_at < ? AND total_tokens > 0"
                + (scoped ? " AND user_id = ?" : "")
                + providerFilter.getSql()
                + " GROUP BY " + normalizedUser;
        List<TokenStatisticsUserUsage> result = new ArrayList<TokenStatisticsUserUsage>();
        try (Connection conn = HikariDS.getConnection(AiGlobal.DEFAULT_DB);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, startMs);
            ps.setLong(2, endMsExclusive);
            if (scoped) {
                ps.setString(3, userId.trim());
            }
            providerFilter.bind(ps, scoped ? 4 : 3);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long pricedRecordCount = rs.getLong("priced_record_count");
                    result.add(TokenStatisticsUserUsage.builder()
                            .userId(rs.getString("usage_user_id"))
                            .requestCount(rs.getLong("request_count"))
                            .promptTokens(rs.getLong("prompt_tokens"))
                            .completionTokens(rs.getLong("completion_tokens"))
                            .totalTokens(rs.getLong("total_tokens"))
                            .totalCost(rs.getBigDecimal("total_cost"))
                            .currency(pricedRecordCount > 0L ? ModelPriceDao.CNY : null)
                            .pricedRecordCount(pricedRecordCount)
                            .unpricedRecordCount(rs.getLong("unpriced_record_count"))
                            .build());
                }
            }
        } catch (SQLException e) {
            log.error("query token statistics by user failed", e);
        }
        return result;
    }

    /** Aggregates exact-window usage by calendar bucket and user for later identity filtering. */
    public List<TokenStatisticsUserTrend> queryUserTrend(long startMs, long endMsExclusive, String userId,
                                                         TokenStatisticsTrendBucket bucket) {
        return queryUserTrend(startMs, endMsExclusive, userId, bucket,
                null, Collections.<String>emptyList());
    }

    /** Aggregates trend buckets after applying the configured platform selection. */
    public List<TokenStatisticsUserTrend> queryUserTrend(long startMs, long endMsExclusive, String userId,
                                                         TokenStatisticsTrendBucket bucket, String provider,
                                                         List<String> configuredProviders) {
        normalizeHistoricalCostsToCny();
        boolean scoped = hasUserId(userId);
        ProviderSqlFilter providerFilter = providerSqlFilter(provider, configuredProviders);
        String normalizedUser = "CASE WHEN user_id IS NULL OR TRIM(user_id) = '' "
                + "THEN NULL ELSE TRIM(user_id) END";
        String bucketExpression = trendBucketExpression(bucket);
        String sql = "SELECT " + bucketExpression + " AS usage_bucket, " + normalizedUser
                + " AS usage_user_id, COUNT(1) AS request_count, "
                + "COALESCE(SUM(total_tokens),0) AS total_tokens, "
                + "SUM(CASE WHEN total_cost IS NOT NULL AND UPPER(currency) = 'CNY' THEN total_cost END) AS total_cost, "
                + "COALESCE(SUM(CASE WHEN total_cost IS NOT NULL AND UPPER(currency) = 'CNY' "
                + "THEN 1 ELSE 0 END),0) AS priced_record_count, "
                + "COALESCE(SUM(CASE WHEN total_cost IS NULL OR currency IS NULL OR UPPER(currency) <> 'CNY' "
                + "THEN 1 ELSE 0 END),0) AS unpriced_record_count "
                + "FROM llm_token_statistics WHERE created_at >= ? AND created_at < ? AND total_tokens > 0"
                + (scoped ? " AND user_id = ?" : "")
                + providerFilter.getSql()
                + " GROUP BY " + bucketExpression + ", " + normalizedUser
                + " ORDER BY usage_bucket ASC";
        List<TokenStatisticsUserTrend> result = new ArrayList<TokenStatisticsUserTrend>();
        try (Connection conn = HikariDS.getConnection(AiGlobal.DEFAULT_DB);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, startMs);
            ps.setLong(2, endMsExclusive);
            if (scoped) {
                ps.setString(3, userId.trim());
            }
            providerFilter.bind(ps, scoped ? 4 : 3);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long pricedRecordCount = rs.getLong("priced_record_count");
                    result.add(TokenStatisticsUserTrend.builder()
                            .bucket(rs.getString("usage_bucket"))
                            .userId(rs.getString("usage_user_id"))
                            .requestCount(rs.getLong("request_count"))
                            .totalTokens(rs.getLong("total_tokens"))
                            .totalCost(rs.getBigDecimal("total_cost"))
                            .currency(pricedRecordCount > 0L ? ModelPriceDao.CNY : null)
                            .pricedRecordCount(pricedRecordCount)
                            .unpricedRecordCount(rs.getLong("unpriced_record_count"))
                            .build());
                }
            }
        } catch (SQLException e) {
            log.error("query token statistics trend by user failed", e);
        }
        return result;
    }

    /** Returns distinct non-blank models by raw user for accurate user and dashboard model counts. */
    public List<TokenStatisticsUserModelUsage> queryUserModels(long startMs, long endMsExclusive, String userId,
                                                               String provider,
                                                               List<String> configuredProviders) {
        boolean scoped = hasUserId(userId);
        ProviderSqlFilter providerFilter = providerSqlFilter(provider, configuredProviders);
        String normalizedUser = "CASE WHEN user_id IS NULL OR TRIM(user_id) = '' "
                + "THEN NULL ELSE TRIM(user_id) END";
        String sql = "SELECT " + normalizedUser + " AS usage_user_id, TRIM(model) AS usage_model "
                + "FROM llm_token_statistics WHERE created_at >= ? AND created_at < ?"
                + " AND total_tokens > 0 AND model IS NOT NULL AND TRIM(model) <> ''"
                + (scoped ? " AND user_id = ?" : "")
                + providerFilter.getSql()
                + " GROUP BY " + normalizedUser + ", TRIM(model)";
        List<TokenStatisticsUserModelUsage> result = new ArrayList<TokenStatisticsUserModelUsage>();
        try (Connection conn = HikariDS.getConnection(AiGlobal.DEFAULT_DB);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, startMs);
            ps.setLong(2, endMsExclusive);
            if (scoped) {
                ps.setString(3, userId.trim());
            }
            providerFilter.bind(ps, scoped ? 4 : 3);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(TokenStatisticsUserModelUsage.builder()
                            .userId(rs.getString("usage_user_id"))
                            .model(rs.getString("usage_model"))
                            .build());
                }
            }
        } catch (SQLException e) {
            log.error("query token statistics models by user failed", e);
        }
        return result;
    }

    static ProviderSqlFilter providerSqlFilter(String provider, List<String> configuredProviders) {
        String selected = normalizeText(provider);
        if (selected == null) {
            return new ProviderSqlFilter("", Collections.<String>emptyList());
        }
        if (!"__other__".equals(selected)) {
            return new ProviderSqlFilter(" AND LOWER(TRIM(provider)) = LOWER(?)",
                    Collections.singletonList(selected));
        }
        List<String> configured = new ArrayList<String>();
        if (configuredProviders != null) {
            for (String configuredProvider : configuredProviders) {
                String normalized = normalizeText(configuredProvider);
                if (normalized != null && !containsIgnoreCase(configured, normalized)) {
                    configured.add(normalized);
                }
            }
        }
        if (configured.isEmpty()) {
            return new ProviderSqlFilter("", Collections.<String>emptyList());
        }
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < configured.size(); i++) {
            if (i > 0) {
                placeholders.append(',');
            }
            placeholders.append('?');
        }
        return new ProviderSqlFilter(" AND (provider IS NULL OR TRIM(provider) = '' "
                + "OR LOWER(TRIM(provider)) NOT IN (" + placeholders + "))", configured);
    }

    private static boolean containsIgnoreCase(List<String> values, String candidate) {
        for (String value : values) {
            if (value.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeText(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    static final class ProviderSqlFilter {
        private final String sql;
        private final List<String> parameters;

        private ProviderSqlFilter(String sql, List<String> parameters) {
            this.sql = sql;
            this.parameters = parameters;
        }

        String getSql() {
            return sql;
        }

        void bind(PreparedStatement ps, int startIndex) throws SQLException {
            for (int i = 0; i < parameters.size(); i++) {
                ps.setString(startIndex + i, parameters.get(i));
            }
        }
    }

    private static String trendBucketExpression(TokenStatisticsTrendBucket bucket) {
        if (bucket == TokenStatisticsTrendBucket.HOUR) {
            return "strftime('%Y-%m-%dT%H:00:00', created_at / 1000, 'unixepoch', 'localtime')";
        }
        if (bucket == TokenStatisticsTrendBucket.MONTH) {
            return "strftime('%Y-%m-01T00:00:00', created_at / 1000, 'unixepoch', 'localtime')";
        }
        return "strftime('%Y-%m-%dT00:00:00', created_at / 1000, 'unixepoch', 'localtime')";
    }

    /**
     * Paginated detail rows, ordered by {@code id} descending (newest first). {@code page} is 1-based.
     */
    public TokenStatisticsPageResult queryDetails(TokenStatisticsRange range, int page, int pageSize) {
        return queryDetails(range, page, pageSize, null);
    }

    public TokenStatisticsPageResult queryDetails(TokenStatisticsRange range, int page, int pageSize, String userId) {
        normalizeHistoricalCostsToCny();
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
        String sql = "SELECT id, created_at, prompt_tokens, completion_tokens, total_tokens, saved_tokens, provider, "
                + "model, session_id, user_id, user_api_key_id, input_cost, output_cost, total_cost, currency, price_source "
                + "FROM llm_token_statistics WHERE created_at >= ? AND created_at < ?"
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
                            .userId(rs.getString("user_id"))
                            .userApiKeyId(nullableLong(rs, "user_api_key_id"))
                            .inputCost(rs.getBigDecimal("input_cost"))
                            .outputCost(rs.getBigDecimal("output_cost"))
                            .totalCost(rs.getBigDecimal("total_cost"))
                            .currency(rs.getString("currency"))
                            .priceSource(rs.getString("price_source"))
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
        normalizeHistoricalCostsToCny();
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
                "SUM(total_cost) AS total_cost, MIN(currency) AS currency, " +
                "COUNT(DISTINCT CASE WHEN total_cost IS NOT NULL THEN currency END) AS currency_count, " +
                "COALESCE(SUM(CASE WHEN total_cost IS NOT NULL THEN 1 ELSE 0 END),0) AS priced_record_count, " +
                "COALESCE(SUM(CASE WHEN total_cost IS NULL THEN 1 ELSE 0 END),0) AS unpriced_record_count, " +
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
                            .estimatedCost(0D)
                            .totalCost(rs.getLong("currency_count") == 1 ? rs.getBigDecimal("total_cost") : null)
                            .currency(rs.getLong("currency_count") == 1 ? rs.getString("currency") : null)
                            .pricedRecordCount(rs.getLong("priced_record_count"))
                            .unpricedRecordCount(rs.getLong("unpriced_record_count"))
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
        LocalDate first;
        if (range == TokenStatisticsRange.THIS_WEEK) {
            first = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        } else if (range == TokenStatisticsRange.THIS_MONTH) {
            first = today.withDayOfMonth(1);
        } else {
            first = today.minusDays(range.getCalendarSpanDays() - 1L);
        }
        long startMs = first.atStartOfDay(zone).toInstant().toEpochMilli();
        long endMs = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        return new long[]{startMs, endMs};
    }

    private static int calendarDayCount(long startMs, long endMsExclusive) {
        if (endMsExclusive == Long.MAX_VALUE) {
            return 0;
        }
        ZoneId zone = ZoneId.systemDefault();
        LocalDate start = java.time.Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate();
        LocalDate end = java.time.Instant.ofEpochMilli(endMsExclusive).atZone(zone).toLocalDate();
        return (int) Math.max(1L, ChronoUnit.DAYS.between(start, end));
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

    /**
     * Makes pre-existing statistics consistent with the local CNY-only price
     * catalogue. Old USD rows retain their original amount and are converted
     * at the configured reference rate; rows without an amount are repriced
     * through the current catalogue or its similar-model fallback.
     */
    private void normalizeHistoricalCostsToCny() {
        synchronized (COST_NORMALIZATION_LOCK) {
            List<HistoricalCostRecord> records = new ArrayList<HistoricalCostRecord>();
            String selectSql = "SELECT id, prompt_tokens, completion_tokens, provider, model, input_cost, output_cost, "
                    + "total_cost, currency FROM llm_token_statistics "
                    + "WHERE total_cost IS NULL OR currency IS NULL OR UPPER(currency) <> 'CNY'";
            try (Connection conn = HikariDS.getConnection(AiGlobal.DEFAULT_DB);
                 PreparedStatement ps = conn.prepareStatement(selectSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    records.add(new HistoricalCostRecord(
                            rs.getLong("id"), rs.getLong("prompt_tokens"), rs.getLong("completion_tokens"),
                            rs.getString("provider"), rs.getString("model"), rs.getBigDecimal("input_cost"),
                            rs.getBigDecimal("output_cost"), rs.getBigDecimal("total_cost"), rs.getString("currency")));
                }
            } catch (SQLException e) {
                log.error("query historical token costs for CNY normalization failed", e);
                return;
            }
            if (records.isEmpty()) {
                return;
            }

            ModelPriceDao priceDao = new ModelPriceDao();
            try {
                priceDao.initialize();
                try (Connection conn = HikariDS.getConnection(AiGlobal.DEFAULT_DB);
                     PreparedStatement ps = conn.prepareStatement("UPDATE llm_token_statistics SET input_cost=?, "
                             + "output_cost=?, total_cost=?, currency=?, price_source=? WHERE id=?")) {
                    for (HistoricalCostRecord record : records) {
                        TokenUsageCost normalized = record.isUsdPriced()
                                ? record.convertUsdToCny()
                                : priceDao.calculate(record.provider, record.model,
                                        record.promptTokens, record.completionTokens);
                        if (normalized == null) {
                            log.warn("skip CNY normalization for token statistic {} because the price catalogue is unavailable",
                                    record.id);
                            continue;
                        }
                        setNullableDecimal(ps, 1, normalized.getInputCost());
                        setNullableDecimal(ps, 2, normalized.getOutputCost());
                        setNullableDecimal(ps, 3, normalized.getTotalCost());
                        ps.setString(4, ModelPriceDao.CNY);
                        ps.setString(5, normalized.getPriceSource());
                        ps.setLong(6, record.id);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            } catch (SQLException e) {
                log.error("normalize historical token costs to CNY failed", e);
            }
        }
    }

    private static final class HistoricalCostRecord {
        private final long id;
        private final long promptTokens;
        private final long completionTokens;
        private final String provider;
        private final String model;
        private final BigDecimal inputCost;
        private final BigDecimal outputCost;
        private final BigDecimal totalCost;
        private final String currency;

        private HistoricalCostRecord(long id, long promptTokens, long completionTokens, String provider, String model,
                                     BigDecimal inputCost, BigDecimal outputCost, BigDecimal totalCost, String currency) {
            this.id = id;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.provider = provider;
            this.model = model;
            this.inputCost = inputCost;
            this.outputCost = outputCost;
            this.totalCost = totalCost;
            this.currency = currency;
        }

        private boolean isUsdPriced() {
            return totalCost != null && currency != null && "USD".equalsIgnoreCase(currency.trim());
        }

        private TokenUsageCost convertUsdToCny() {
            return TokenUsageCost.builder()
                    .inputCost(ModelPriceDao.toCny(inputCost, currency))
                    .outputCost(ModelPriceDao.toCny(outputCost, currency))
                    .totalCost(ModelPriceDao.toCny(totalCost, currency))
                    .currency(ModelPriceDao.CNY)
                    .priceSource("usd_to_cny")
                    .build();
        }
    }

    private List<TokenStatisticsCurrencyCost> summarizeCosts(long start, long end, String userId) {
        boolean scoped = hasUserId(userId);
        String sql = "SELECT currency, SUM(total_cost) AS total_cost, COUNT(1) AS record_count "
                + "FROM llm_token_statistics WHERE created_at >= ? AND created_at < ? "
                + "AND total_cost IS NOT NULL AND currency IS NOT NULL"
                + (scoped ? " AND user_id = ?" : "")
                + " GROUP BY currency ORDER BY currency";
        List<TokenStatisticsCurrencyCost> result = new ArrayList<TokenStatisticsCurrencyCost>();
        try (Connection conn = HikariDS.getConnection(AiGlobal.DEFAULT_DB);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, start);
            ps.setLong(2, end);
            if (scoped) {
                ps.setString(3, userId.trim());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(TokenStatisticsCurrencyCost.builder()
                            .currency(rs.getString("currency"))
                            .totalCost(rs.getBigDecimal("total_cost"))
                            .recordCount(rs.getLong("record_count"))
                            .build());
                }
            }
        } catch (SQLException e) {
            log.error("summarize token costs failed", e);
        }
        return result;
    }

    private static void setNullableDecimal(PreparedStatement statement, int index, BigDecimal value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.DECIMAL);
        } else {
            statement.setBigDecimal(index, value);
        }
    }

    private static Long nullableLong(ResultSet rs, String field) throws SQLException {
        long value = rs.getLong(field);
        return rs.wasNull() ? null : value;
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
