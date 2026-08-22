package ai.account;

import ai.common.db.HikariDS;
import ai.llm.dao.TokenStatisticsDao;
import ai.llm.pojo.TokenStatisticsRange;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CuihuaAccountIntegrationTest {
    private static final String USER_ID = "cuihua-user-1";
    private static final String SYSTEM_ADMIN_ID = "cuihua-system-admin-1";

    @BeforeAll
    static void createSharedDatabase() throws Exception {
        Path dataDir = Files.createTempDirectory("linkmind-cuihua-test-");
        System.setProperty(HikariDS.DATA_DIR_PROPERTY, dataDir.toString());
        Class.forName("org.sqlite.JDBC");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dataDir.resolve("saas.db"));
             Statement statement = conn.createStatement()) {
            statement.execute("CREATE TABLE users ("
                    + "id VARCHAR(64) NOT NULL PRIMARY KEY, username VARCHAR(128) NOT NULL, "
                    + "email VARCHAR(320) NOT NULL, display_name VARCHAR(120) NOT NULL, "
                    + "password_hash TEXT NOT NULL, role VARCHAR(32) NOT NULL DEFAULT 'user', "
                    + "status VARCHAR(32) NOT NULL DEFAULT 'active', first_login BOOLEAN NOT NULL DEFAULT 1, "
                    + "failed_attempts INTEGER NOT NULL DEFAULT 0, locked_until DATETIME, created_at DATETIME NOT NULL)");
            statement.execute("CREATE UNIQUE INDEX ix_users_username ON users(username)");
            statement.execute("CREATE UNIQUE INDEX ix_users_email ON users(email)");
        }

        Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
        char[] password = "correct-horse-battery-staple".toCharArray();
        String hash;
        try {
            hash = argon2.hash(3, 65536, 4, password);
        } finally {
            argon2.wipeArray(password);
        }
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dataDir.resolve("saas.db"));
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO users(id, username, email, display_name, password_hash, role, status, created_at) "
                             + "VALUES (?, ?, ?, ?, ?, ?, 'active', datetime('now'))")) {
            insertUser(ps, USER_ID, "cuihua", "cuihua@example.test", "Cuihua User", hash, "user");
            insertUser(ps, SYSTEM_ADMIN_ID, "system-admin", "admin@example.test", "System Admin", hash, "system_admin");
        }
    }

    private static void insertUser(PreparedStatement ps, String id, String username, String email,
                                   String displayName, String passwordHash, String role) throws Exception {
        ps.setString(1, id);
        ps.setString(2, username);
        ps.setString(3, email);
        ps.setString(4, displayName);
        ps.setString(5, passwordHash);
        ps.setString(6, role);
        ps.executeUpdate();
    }

    @Test
    void authenticatesSharedUserAndAttributesUsageToThatUser() throws Exception {
        CuihuaAccountService accountService = new CuihuaAccountService();
        CuihuaAccountService.LoginResult login = accountService.login(
                "cuihua@example.test", "correct-horse-battery-staple", 60_000L);
        assertNotNull(login);
        assertEquals(USER_ID, login.getUser().getUserId());
        assertNull(accountService.login("cuihua", "wrong-password", 60_000L));
        assertNull(accountService.loginSystemAdmin("cuihua", "correct-horse-battery-staple", 60_000L));
        CuihuaAccountService.LoginResult systemAdmin = accountService.loginSystemAdmin(
                "system-admin", "correct-horse-battery-staple", 60_000L);
        assertNotNull(systemAdmin);
        assertEquals(SYSTEM_ADMIN_ID, systemAdmin.getUser().getUserId());
        assertEquals(USER_ID, accountService.resolveSession(login.getSessionToken()).getUserId());
        accountService.revokeSession(login.getSessionToken());
        assertNull(accountService.resolveSession(login.getSessionToken()));

        LinkMindClientTokenService tokenService = new LinkMindClientTokenService();
        LinkMindClientToken clientToken = tokenService.issue(USER_ID, "integration-test", null);
        assertNotNull(clientToken.getToken());
        LinkMindClientTokenService.AuthenticationResult token = tokenService.authenticate(clientToken.getToken());
        assertTrue(token.isTokenMatched());
        assertTrue(token.isAuthenticated());
        assertEquals(USER_ID, token.getUser().getUserId());
        assertFalse(tokenService.authenticate("lmk_invalid").isTokenMatched());

        LinkMindClientToken updated = tokenService.update(clientToken.getId(), USER_ID,
                "renamed", false, null, false);
        assertNotNull(updated);
        assertEquals("renamed", updated.getName());
        assertFalse(updated.isEnabled());
        assertFalse(tokenService.authenticate(clientToken.getToken()).isAuthenticated());
        assertTrue(tokenService.delete(clientToken.getId(), USER_ID));
        assertTrue(tokenService.list(USER_ID).isEmpty());

        TokenStatisticsDao statisticsDao = new TokenStatisticsDao();
        statisticsDao.insert(11L, 7L, 18L, 0L, "Test", "test-model", "session-1", USER_ID);
        assertEquals(18L, statisticsDao.summarize(TokenStatisticsRange.ALL, USER_ID).getTotalTokensConsumed());

        try (Connection conn = HikariDS.getConnection("saas");
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT total_tokens FROM linkmind_user_token_usage_view WHERE user_id = ?")) {
            ps.setString(1, USER_ID);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(18L, rs.getLong("total_tokens"));
            }
        }
    }
}
