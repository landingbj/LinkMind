package ai.servlet.api;

import ai.common.db.HikariDS;
import ai.common.db.TokenLinkDataSource;
import ai.utils.AiGlobal;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Read-only readiness endpoint for container orchestration. */
public class HealthApiServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private static final String[] SAAS_REQUIRED_TABLES = {
            "linkmind_login_session", "linkmind_model_prices"
    };
    private static final String[] TOKEN_LINK_REQUIRED_TABLES = {
            "users", "linkmind_user_api_keys"
    };

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        try {
            verifyReady();
            response.setStatus(HttpServletResponse.SC_OK);
            try (PrintWriter writer = response.getWriter()) {
                writer.write("{\"status\":\"ok\",\"migration\":\"complete\"}");
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            try (PrintWriter writer = response.getWriter()) {
                writer.write("{\"status\":\"unavailable\",\"migration\":\"incomplete\"}");
            }
        }
    }

    static void verifyReady() throws SQLException, IOException {
        Path dataDir = resolveDataDir();
        verifyDatabaseFile(dataDir.resolve("saas.db"), "saas");

        Path tokenLinkDb = TokenLinkDataSource.getDatabasePath();
        verifyDatabaseFile(tokenLinkDb, "tokenlink");

        try (Connection saas = HikariDS.getConnection(AiGlobal.DEFAULT_DB);
             Connection tokenLink = TokenLinkDataSource.getConnection()) {
            verifyDatabase(saas, "saas", SAAS_REQUIRED_TABLES);
            verifyDatabase(tokenLink, "tokenlink", TOKEN_LINK_REQUIRED_TABLES);
        }
    }

    private static Path resolveDataDir() throws IOException {
        String configured = System.getProperty(HikariDS.DATA_DIR_PROPERTY);
        if (configured == null || configured.trim().isEmpty()) {
            throw new IOException("LinkMind data directory is not configured");
        }
        Path dataDir = Paths.get(configured).toAbsolutePath().normalize();
        if (!Files.isDirectory(dataDir) || !Files.isReadable(dataDir)) {
            throw new IOException("LinkMind data directory is unavailable: " + dataDir);
        }
        return dataDir;
    }

    private static void verifyDatabaseFile(Path path, String name) throws IOException {
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IOException(name + " SQLite database is unavailable: " + path);
        }
    }

    private static void verifyDatabase(Connection connection, String name, String[] requiredTables) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet quickCheck = statement.executeQuery("PRAGMA quick_check(1)")) {
            if (!quickCheck.next() || !"ok".equalsIgnoreCase(quickCheck.getString(1))) {
                throw new SQLException(name + " SQLite integrity check failed");
            }
        }

        for (String table : requiredTables) {
            try (java.sql.PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
                statement.setString(1, table);
                try (ResultSet tables = statement.executeQuery()) {
                    if (!tables.next()) {
                        throw new SQLException(name + " migration is incomplete; missing table " + table);
                    }
                }
            }
        }
    }
}
