package ai.account;

import ai.common.db.HikariDS;
import ai.common.db.TokenLinkDataSource;
import ai.config.pojo.TokenLinkConfig;
import ai.llm.dao.TokenStatisticsDao;
import ai.llm.dao.ModelPriceDao;
import ai.llm.pojo.TokenStatisticsRange;
import ai.llm.pojo.TokenStatisticsTrendBucket;
import ai.llm.pojo.TokenStatisticsUserTrend;
import ai.llm.pojo.TokenStatisticsUserModelUsage;
import ai.llm.pojo.TokenStatisticsUserUsage;
import ai.servlet.api.TokenUsageAuthorizationService;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CuihuaAccountIntegrationTest {
    private static final String USER_ID = "cuihua-user-1";
    private static final String OTHER_USER_ID = "cuihua-user-2";
    private static final String ADMIN_ID = "cuihua-admin-1";
    private static final String SYSTEM_ADMIN_ID = "cuihua-system-admin-1";
    private static final String PASSWORD = "correct-horse-battery-staple";

    private static String originalJdbcUrl;
    private static String passwordHash;

    @BeforeAll
    static void createSeparateDatabases() throws Exception {
        Path dataDir = Files.createTempDirectory("linkmind-tokenlink-test-");
        originalJdbcUrl = HikariDS.getJdbcUrlForTests();
        HikariDS.setJdbcUrlForTests(sqliteJdbcUrl(dataDir.resolve("saas.db")));

        CuihuaAccountService.resetForTests();
        CuihuaAccountDao.resetSchemaForTests();
        TokenLinkConfig config = new TokenLinkConfig();
        config.setDatabasePath(dataDir.resolve("tokenlink.db").toAbsolutePath().toString());
        CuihuaAccountService.initialize(config);
        assertDefaultCatalogPriceCount(123L);
        createLinkMindDatabase();
        createCuihuaUsers();
    }

    @AfterAll
    static void restoreDataSources() {
        CuihuaAccountService.resetForTests();
        if (originalJdbcUrl != null) {
            HikariDS.setJdbcUrlForTests(originalJdbcUrl);
        }
    }

    @Test
    void usesTokenLinkUsersAndKeepsTheSharedDatabaseUserOnly() throws Exception {
        CuihuaAccountService accountService = new CuihuaAccountService();
        CuihuaAccountService.LoginResult login = accountService.login("cuihua", PASSWORD, 60_000L);
        assertNotNull(login);
        assertEquals(USER_ID, login.getUser().getUserId());
        assertNull(accountService.login("cuihua", "wrong-password", 60_000L));
        assertNull(accountService.loginSystemAdmin("cuihua", PASSWORD, 60_000L));

        CuihuaAccountService.LoginResult systemAdmin = accountService.loginSystemAdminBySso(
                SYSTEM_ADMIN_ID, 60_000L);
        assertNotNull(systemAdmin);
        assertEquals(SYSTEM_ADMIN_ID, systemAdmin.getUser().getUserId());
        assertEquals("system_admin", systemAdmin.getUser().getRole());
        assertEquals(USER_ID, accountService.resolveSession(login.getSessionToken()).getUserId());
        assertNull(accountService.loginSystemAdminBySso(USER_ID, 60_000L));

        setUserLoginStatus(SYSTEM_ADMIN_ID, "logged_out");
        assertNull(accountService.loginSystemAdminBySso(SYSTEM_ADMIN_ID, 60_000L));
        setUserLoginStatus(SYSTEM_ADMIN_ID, "logged_in");

        deleteUser(USER_ID);
        assertNull(accountService.resolveSession(login.getSessionToken()));

        insertUser(USER_ID, "user", "cuihua", "logged_in");
        assertEquals(USER_ID, accountService.resolveSession(login.getSessionToken()).getUserId());

        TokenStatisticsDao statisticsDao = new TokenStatisticsDao();
        statisticsDao.insert(11L, 7L, 18L, 0L, "Test", "test-model", "session-1", USER_ID);
        assertTrue(statisticsDao.summarize(TokenStatisticsRange.ALL, USER_ID).getTotalTokensConsumed() >= 18L);

        assertTokenLinkSchema();
        assertLocalDatabaseHasNoTokenLinkKeyTable();

        accountService.revokeSession(login.getSessionToken());
        assertNull(accountService.resolveSession(login.getSessionToken()));
    }

    @Test
    void resolvesCurrentDisplayNamesAndFallsBackToUsernames() throws Exception {
        setUserDisplayName(USER_ID, "  翠花  ");
        setUserDisplayName(OTHER_USER_ID, "   ");
        setUserDisplayName(ADMIN_ID, null);
        setUserDisplayName(SYSTEM_ADMIN_ID, "");
        Map<String, String> displayNames = new CuihuaAccountService().findUserDisplayNames(Arrays.asList(
                USER_ID, OTHER_USER_ID, ADMIN_ID, SYSTEM_ADMIN_ID, "missing-user", USER_ID));

        assertEquals("翠花", displayNames.get(USER_ID));
        assertEquals("cuihua-user-2", displayNames.get(OTHER_USER_ID));
        assertEquals("cuihua-admin", displayNames.get(ADMIN_ID));
        assertEquals("system-admin", displayNames.get(SYSTEM_ADMIN_ID));
        assertFalse(displayNames.containsKey("missing-user"));

        Map<String, CuihuaUserIdentity> identities = new CuihuaAccountService().findUserIdentities(
                Arrays.asList(USER_ID, OTHER_USER_ID, "missing-user"));
        assertEquals("翠花", identities.get(USER_ID).getDisplayName());
        assertEquals("cuihua", identities.get(USER_ID).getUsername());
        assertNull(identities.get(OTHER_USER_ID).getDisplayName());
        assertEquals("cuihua-user-2", identities.get(OTHER_USER_ID).getUsername());
        assertFalse(identities.containsKey("missing-user"));
    }

    @Test
    void aggregatesExactWindowUsageByUserAndAdaptiveTrendBucket() throws Exception {
        TokenStatisticsDao statisticsDao = new TokenStatisticsDao();
        long start = 2_208_988_800_000L; // 2040-01-01T00:00:00Z
        insertDashboardUsage(start + 3_600_000L, "dashboard-user", 100L, 20L, new BigDecimal("1.20"));
        insertDashboardUsage(start + 3_900_000L, "dashboard-user", 200L, 40L, new BigDecimal("2.40"));
        insertDashboardUsage(start + 7_200_000L, "dashboard-missing", 300L, 60L, new BigDecimal("3.60"));

        List<TokenStatisticsUserUsage> users = statisticsDao.queryUserUsage(
                start, start + 24L * 60L * 60L * 1000L, null);
        TokenStatisticsUserUsage dashboardUser = users.stream()
                .filter(row -> "dashboard-user".equals(row.getUserId()))
                .findFirst().get();
        assertEquals(2L, dashboardUser.getRequestCount());
        assertEquals(360L, dashboardUser.getTotalTokens());
        assertEquals(0, new BigDecimal("3.60").compareTo(dashboardUser.getTotalCost()));

        List<TokenStatisticsUserTrend> trend = statisticsDao.queryUserTrend(
                start, start + 24L * 60L * 60L * 1000L, null, TokenStatisticsTrendBucket.HOUR);
        assertEquals(1, trend.stream().filter(row -> "dashboard-user".equals(row.getUserId())).count());
        assertTrue(trend.stream().allMatch(row -> row.getBucket().endsWith(":00:00")));
    }

    @Test
    void dashboardPlatformFilterUsesProviderAndGroupsUnknownProvidersAsOther() throws Exception {
        TokenStatisticsDao statisticsDao = new TokenStatisticsDao();
        long start = 2_240_611_200_000L; // 2041-01-01T00:00:00Z
        insertDashboardUsage(start + 1_000L, "platform-user", 100L, 10L,
                new BigDecimal("1.00"), "Alibaba", "model-a");
        insertDashboardUsage(start + 2_000L, "platform-user", 200L, 20L,
                new BigDecimal("2.00"), "Alibaba", "model-b");
        insertDashboardUsage(start + 3_000L, "other-user", 300L, 30L,
                new BigDecimal("3.00"), "LegacyProvider", "legacy-model");
        insertDashboardUsage(start + 4_000L, "platform-user", 0L, 0L,
                new BigDecimal("9.00"), "Alibaba", "invalid-empty-usage");
        List<String> configured = Arrays.asList("Alibaba", "OpenAI");

        List<TokenStatisticsUserUsage> alibaba = statisticsDao.queryUserUsage(
                start, start + 60_000L, null, "Alibaba", configured);
        List<TokenStatisticsUserModelUsage> models = statisticsDao.queryUserModels(
                start, start + 60_000L, null, "Alibaba", configured);
        List<TokenStatisticsUserUsage> other = statisticsDao.queryUserUsage(
                start, start + 60_000L, null, "__other__", configured);

        assertEquals(1, alibaba.size());
        assertEquals(2L, alibaba.get(0).getRequestCount());
        assertEquals(2, models.size());
        assertEquals("other-user", other.get(0).getUserId());
        assertEquals(330L, other.get(0).getTotalTokens());
    }

    @Test
    void localUserKeysSupportManyKeysAndDoNotDependOnUserLoginStatus() throws Exception {
        LocalUserApiKeyService keyService = new LocalUserApiKeyService();
        LocalUserApiKey first = keyService.create(USER_ID, "cuihua-default");
        LocalUserApiKey second = keyService.create(USER_ID, "employee-laptop");

        assertTrue(first.getApiKey().startsWith(LocalUserApiKeyService.KEY_PREFIX));
        assertTrue(second.getApiKey().startsWith(LocalUserApiKeyService.KEY_PREFIX));
        assertEquals(2L, keyService.count(USER_ID, "active"));
        assertEquals(2, keyService.list(USER_ID, "all", 1, 20).size());

        setUserLoginStatus(USER_ID, "logged_out");
        assertNotNull(keyService.authenticate(first.getApiKey()));
        LocalUserApiKey revoked = keyService.revoke(USER_ID, first.getId());
        assertEquals("revoked", revoked.getStatus());
        assertNull(keyService.authenticate(first.getApiKey()));
        assertNotNull(keyService.authenticate(second.getApiKey()));
        setUserLoginStatus(USER_ID, "logged_in");
    }

    @Test
    void tokenStatisticsCalculateCostOnlyFromLocalConfiguredPrices() throws Exception {
        ModelPriceDao priceDao = new ModelPriceDao();
        priceDao.upsert("Test", "priced-model", new BigDecimal("10"), new BigDecimal("20"),
                "CNY", System.currentTimeMillis() - 1L);

        TokenStatisticsDao statisticsDao = new TokenStatisticsDao();
        statisticsDao.insert(1_000_000L, 500_000L, 1_500_000L, 0L,
                "Test", "priced-model", "priced-session", USER_ID, 99L);
        assertEquals(0, new BigDecimal("20.00000000").compareTo(statisticsDao
                .summarize(TokenStatisticsRange.ALL, USER_ID).getTotalCost()));
        assertEquals("CNY", statisticsDao.summarize(TokenStatisticsRange.ALL, USER_ID).getCurrency());
        assertTrue(statisticsDao.queryDetails(TokenStatisticsRange.ALL, 1, 20, USER_ID).getRecords().stream()
                .anyMatch(record -> "priced-model".equals(record.getModel())
                        && Long.valueOf(99L).equals(record.getUserApiKeyId())));
    }

    @Test
    void officialPriceCatalogSeedsKnownModelsAndAllowsLaterOverrides() throws Exception {
        ModelPriceDao priceDao = new ModelPriceDao();
        assertEquals(0, new BigDecimal("0.90000000").compareTo(priceDao
                .calculate("Landing", "qwen-flash", 1_000_000L, 500_000L).getTotalCost()));
        assertEquals("CNY", priceDao.calculate("Landing", "qwen-flash", 1L, 1L).getCurrency());
        assertEquals("CNY", priceDao.calculate("Landing", "model-without-official-price", 1L, 1L).getCurrency());
        assertEquals("similar_model_estimate", priceDao
                .calculate("Landing", "model-without-official-price", 1L, 1L).getPriceSource());
        assertEquals(0, new BigDecimal("36.00000000").compareTo(priceDao
                .calculate("openai", "gpt-5.4", 1_000_000L, 0L).getTotalCost()));
        assertEquals(0, new BigDecimal("14.40000000").compareTo(priceDao
                .calculate("openai", "gpt-6.2-sol", 1_000_000L, 0L).getTotalCost()));
        assertEquals(0, new BigDecimal("0.72000000").compareTo(priceDao
                .calculate("openai", "gpt-6.2-sol", 1_000_000L, 1_000_000L, 0L).getTotalCost()));
        assertEquals(0, new BigDecimal("54.54000000").compareTo(priceDao
                .calculate("google", "gemini-4-hydrogen", 1_000_000L, 1_000_000L, 1_000_000L).getTotalCost()));
        assertEquals(0, new BigDecimal("145.44000000").compareTo(priceDao
                .calculate("anthropic", "claude-opus-5-5", 1_000_000L, 1_000_000L, 1_000_000L).getTotalCost()));

        priceDao.upsert("Alibaba", "qwen-flash", new BigDecimal("9"), new BigDecimal("18"),
                "CNY", System.currentTimeMillis() - 1L);
        assertEquals(0, new BigDecimal("18.00000000").compareTo(priceDao
                .calculate("Alibaba", "qwen-flash", 1_000_000L, 500_000L).getTotalCost()));
    }

    @Test
    void historicalUsageCostsAreConvertedOrEstimatedInCny() throws Exception {
        TokenStatisticsDao statisticsDao = new TokenStatisticsDao();
        try (Connection conn = HikariDS.getConnection("saas");
             PreparedStatement ps = conn.prepareStatement("INSERT INTO llm_token_statistics (created_at, prompt_tokens, "
                     + "completion_tokens, total_tokens, saved_tokens, provider, model, session_id, user_id, "
                     + "input_cost, output_cost, total_cost, currency, price_source) "
                     + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setLong(2, 1_000_000L);
            ps.setLong(3, 0L);
            ps.setLong(4, 1_000_000L);
            ps.setLong(5, 0L);
            ps.setString(6, "openai");
            ps.setString(7, "gpt-5.4");
            ps.setString(8, "legacy-usd-row");
            ps.setString(9, USER_ID);
            ps.setBigDecimal(10, new BigDecimal("2.5"));
            ps.setBigDecimal(11, BigDecimal.ZERO);
            ps.setBigDecimal(12, new BigDecimal("2.5"));
            ps.setString(13, "USD");
            ps.setString(14, "legacy");
            ps.addBatch();

            ps.setLong(1, System.currentTimeMillis());
            ps.setLong(2, 1_000_000L);
            ps.setLong(3, 0L);
            ps.setLong(4, 1_000_000L);
            ps.setLong(5, 0L);
            ps.setString(6, "unlisted-provider");
            ps.setString(7, "unlisted-mini-model");
            ps.setString(8, "legacy-empty-row");
            ps.setString(9, USER_ID);
            ps.setNull(10, java.sql.Types.DECIMAL);
            ps.setNull(11, java.sql.Types.DECIMAL);
            ps.setNull(12, java.sql.Types.DECIMAL);
            ps.setNull(13, java.sql.Types.VARCHAR);
            ps.setNull(14, java.sql.Types.VARCHAR);
            ps.addBatch();
            ps.executeBatch();
        }

        assertTrue(statisticsDao.queryDetails(TokenStatisticsRange.ALL, 1, 100, USER_ID).getRecords().stream()
                .anyMatch(record -> "gpt-5.4".equals(record.getModel())
                        && "CNY".equals(record.getCurrency())
                        && "usd_to_cny".equals(record.getPriceSource())
                        && new BigDecimal("18.00000000").compareTo(record.getTotalCost()) == 0));
        assertTrue(statisticsDao.queryDetails(TokenStatisticsRange.ALL, 1, 100, USER_ID).getRecords().stream()
                .anyMatch(record -> "unlisted-mini-model".equals(record.getModel())
                        && "CNY".equals(record.getCurrency())
                        && "similar_model_estimate".equals(record.getPriceSource())
                        && new BigDecimal("0.50000000").compareTo(record.getTotalCost()) == 0));
    }

    @Test
    void integrationRangeAliasesMatchTheDocumentedWeekAndMonthValues() {
        assertEquals(TokenStatisticsRange.THIS_WEEK, TokenStatisticsRange.fromQueryParam("week"));
        assertEquals(TokenStatisticsRange.THIS_MONTH, TokenStatisticsRange.fromQueryParam("month"));
        assertEquals(TokenStatisticsRange.LAST_7_DAYS, TokenStatisticsRange.fromQueryParam("7d"));
        assertEquals(TokenStatisticsRange.LAST_30_DAYS, TokenStatisticsRange.fromQueryParam("30d"));
    }

    @Test
    void tokenUsageScopeRestrictsUsersAndAllowsAdministratorsToInspectAllUsers() throws Exception {
        TokenUsageAuthorizationService authorizationService = new TokenUsageAuthorizationService();

        TokenUsageAuthorizationService.TokenUsageScope userDefault = authorizationService
                .authorizeCuihuaActor(USER_ID, null);
        assertEquals(TokenUsageAuthorizationService.Scope.SELF, userDefault.getScope());
        assertEquals(USER_ID, userDefault.getQueryUserId());

        TokenUsageAuthorizationService.TokenUsageAuthorizationException denied = assertThrows(
                TokenUsageAuthorizationService.TokenUsageAuthorizationException.class,
                () -> authorizationService.authorizeCuihuaActor(USER_ID, OTHER_USER_ID));
        assertEquals(TokenUsageAuthorizationService.Reason.ACCESS_DENIED, denied.getReason());

        TokenUsageAuthorizationService.TokenUsageScope adminDefault = authorizationService
                .authorizeCuihuaActor(ADMIN_ID, null);
        assertEquals(TokenUsageAuthorizationService.Scope.ALL, adminDefault.getScope());
        assertNull(adminDefault.getQueryUserId());

        TokenUsageAuthorizationService.TokenUsageScope adminUser = authorizationService
                .authorizeCuihuaActor(ADMIN_ID, OTHER_USER_ID);
        assertEquals(TokenUsageAuthorizationService.Scope.USER, adminUser.getScope());
        assertEquals(OTHER_USER_ID, adminUser.getQueryUserId());

        TokenUsageAuthorizationService.TokenUsageScope systemAdminDefault = authorizationService
                .authorizeCuihuaActor(SYSTEM_ADMIN_ID, null);
        assertEquals(TokenUsageAuthorizationService.Scope.ALL, systemAdminDefault.getScope());
        TokenUsageAuthorizationService.TokenUsageAuthorizationException missingActor = assertThrows(
                TokenUsageAuthorizationService.TokenUsageAuthorizationException.class,
                () -> authorizationService.authorizeCuihuaActor(null, null));
        assertEquals(TokenUsageAuthorizationService.Reason.CUIHUA_ACTOR_REQUIRED, missingActor.getReason());
    }

    private static void createLinkMindDatabase() throws SQLException {
        try (Connection conn = HikariDS.getConnection("saas");
             Statement statement = conn.createStatement()) {
            statement.execute("CREATE TABLE llm_token_statistics ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, created_at INTEGER NOT NULL, "
                    + "prompt_tokens INTEGER NOT NULL, completion_tokens INTEGER NOT NULL, "
                    + "total_tokens INTEGER NOT NULL, saved_tokens INTEGER NOT NULL, "
                    + "provider TEXT, model TEXT, session_id TEXT, user_id TEXT, user_api_key_id INTEGER, "
                    + "input_cost DECIMAL(20,8), output_cost DECIMAL(20,8), total_cost DECIMAL(20,8), "
                    + "currency TEXT, price_source TEXT)");
        }
    }

    private static void insertDashboardUsage(long createdAt, String userId, long promptTokens,
                                             long completionTokens, BigDecimal totalCost) throws SQLException {
        insertDashboardUsage(createdAt, userId, promptTokens, completionTokens, totalCost,
                "Test", "dashboard-model");
    }

    private static void insertDashboardUsage(long createdAt, String userId, long promptTokens,
                                             long completionTokens, BigDecimal totalCost,
                                             String provider, String model) throws SQLException {
        try (Connection conn = HikariDS.getConnection("saas");
             PreparedStatement ps = conn.prepareStatement("INSERT INTO llm_token_statistics (created_at, "
                     + "prompt_tokens, completion_tokens, total_tokens, saved_tokens, provider, model, session_id, "
                     + "user_id, input_cost, output_cost, total_cost, currency, price_source) "
                     + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, createdAt);
            ps.setLong(2, promptTokens);
            ps.setLong(3, completionTokens);
            ps.setLong(4, promptTokens + completionTokens);
            ps.setLong(5, 0L);
            ps.setString(6, provider);
            ps.setString(7, model);
            ps.setString(8, "dashboard-session-" + createdAt);
            ps.setString(9, userId);
            ps.setBigDecimal(10, totalCost);
            ps.setBigDecimal(11, BigDecimal.ZERO);
            ps.setBigDecimal(12, totalCost);
            ps.setString(13, "CNY");
            ps.setString(14, "test");
            ps.executeUpdate();
        }
    }

    private static void assertDefaultCatalogPriceCount(long expected) throws SQLException {
        try (Connection conn = HikariDS.getConnection("saas");
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM linkmind_model_prices WHERE effective_at = 0");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(expected, rs.getLong(1));
        }
    }

    private static void createCuihuaUsers() throws Exception {
        Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
        char[] password = PASSWORD.toCharArray();
        try {
            passwordHash = argon2.hash(3, 65536, 4, password);
        } finally {
            argon2.wipeArray(password);
        }
        insertUser(USER_ID, "user", "cuihua", "  翠花  ", "logged_in");
        insertUser(OTHER_USER_ID, "user", "cuihua-user-2", "   ", "logged_in");
        insertUser(ADMIN_ID, "admin", "cuihua-admin", null, "logged_in");
        insertUser(SYSTEM_ADMIN_ID, "system_admin", "system-admin", "系统管理员", "logged_in");
    }

    private static void insertUser(String id, String role, String username, String loginStatus) throws SQLException {
        insertUser(id, role, username, null, loginStatus);
    }

    private static void insertUser(String id, String role, String username, String displayName,
                                   String loginStatus) throws SQLException {
        try (Connection conn = TokenLinkDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO users(id, role, username, display_name, password_hash, user_login_status) "
                             + "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, role);
            ps.setString(3, username);
            ps.setString(4, displayName);
            ps.setString(5, passwordHash);
            ps.setString(6, loginStatus);
            ps.executeUpdate();
        }
    }

    private static void setUserLoginStatus(String userId, String status) throws SQLException {
        try (Connection conn = TokenLinkDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE users SET user_login_status = ? WHERE id = ?")) {
            ps.setString(1, status);
            ps.setString(2, userId);
            ps.executeUpdate();
        }
    }

    private static void setUserDisplayName(String userId, String displayName) throws SQLException {
        try (Connection conn = TokenLinkDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE users SET display_name = ? WHERE id = ?")) {
            ps.setString(1, displayName);
            ps.setString(2, userId);
            ps.executeUpdate();
        }
    }

    private static void deleteUser(String userId) throws SQLException {
        try (Connection conn = TokenLinkDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
            ps.setString(1, userId);
            ps.executeUpdate();
        }
    }

    private static void assertTokenLinkSchema() throws SQLException {
        try (Connection conn = TokenLinkDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM sqlite_master WHERE name = 'users'");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }

    private static void assertLocalDatabaseHasNoTokenLinkKeyTable() throws SQLException {
        try (Connection conn = HikariDS.getConnection("saas");
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM sqlite_master WHERE name = 'user_api_key'");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
        }
    }

    private static String sqliteJdbcUrl(Path path) {
        return "jdbc:sqlite:" + path.toAbsolutePath().toString().replace('\\', '/');
    }
}
