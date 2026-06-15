package ai.utils;

import ai.database.impl.SqliteAdapter;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class FilterMonitorUtil {
    private static final String SAAS_CONFIG_PATH = "/hikari-saas.properties";
    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private static volatile SqliteAdapter sqliteAdapter;
    private static volatile boolean tableInitialized = false;
    private static volatile boolean monitorUnavailable = false;
    private static final int MAX_RETRY = 3;

    private static SqliteAdapter getSqliteAdapter() {
        SqliteAdapter adapter = sqliteAdapter;
        if (adapter == null) {
            synchronized (FilterMonitorUtil.class) {
                adapter = sqliteAdapter;
                if (adapter == null) {
                    adapter = new SqliteAdapter();
                    sqliteAdapter = adapter;
                }
            }
        }
        return adapter;
    }

    private static synchronized void ensureTableExists() {
        if (tableInitialized) {
            return;
        }
        try {
            SqliteAdapter adapter = getSqliteAdapter();
            try (Connection conn = adapter.getCon()) {
                if (conn == null || conn.isClosed()) {
                    throw new SQLException("database connection unavailable");
                }
                DatabaseMetaData dbm = conn.getMetaData();
                try (ResultSet tables = dbm.getTables(null, null, "lagi_filter_monitor", null)) {
                    if (!tables.next()) {
                        String createTable = "CREATE TABLE IF NOT EXISTS lagi_filter_monitor (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                                "filter_name VARCHAR(64) NOT NULL," +
                                "action_type VARCHAR(32) NOT NULL," +
                                "content TEXT," +
                                "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                                ")";
                        conn.createStatement().executeUpdate(createTable);
                    }
                }
            }
            tableInitialized = true;
        } catch (Exception e) {
            log.error("init filter monitor table failed", e);
            throw new IllegalStateException("init filter monitor table failed", e);
        }
    }

    public static void recordFilterAction(String filterName, String actionType, String content) {
        if ("reload".equalsIgnoreCase(actionType)) {
            log.debug("skip system filter action: filterName={}, actionType={}", filterName, actionType);
            return;
        }
        if (monitorUnavailable || FilterMonitorUtil.class.getResource(SAAS_CONFIG_PATH) == null) {
            monitorUnavailable = true;
            log.debug("filter monitor database config is unavailable, skip record");
            return;
        }

        executorService.submit(() -> {
            try {
                ensureTableExists();
                SqliteAdapter adapter = getSqliteAdapter();
                int retry = 0;
                while (retry <= MAX_RETRY) {
                    try (Connection conn = adapter.getCon()) {
                        if (conn == null || conn.isClosed()) {
                            log.warn("database connection unavailable, skip filter monitor record");
                            return;
                        }

                        String sql = "INSERT INTO lagi_filter_monitor (filter_name, action_type, content, create_time) VALUES (?, ?, ?, datetime('now', 'localtime'))";
                        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                            pstmt.setString(1, filterName != null ? filterName : "");
                            pstmt.setString(2, actionType != null ? actionType : "");
                            pstmt.setString(3, sanitizeContent(content));
                            pstmt.executeUpdate();
                            log.debug("record filter action: filterName={}, actionType={}", filterName, actionType);
                            return;
                        }
                    } catch (SQLException e) {
                        if (!isSqliteBusy(e) || retry == MAX_RETRY) {
                            throw e;
                        }
                        retry++;
                        sleepQuietly(100L * retry);
                    }
                }
            } catch (Throwable e) {
                if (isPermanentMonitorFailure(e)) {
                    monitorUnavailable = true;
                }
                log.error("record filter action failed: filterName={}, actionType={}", filterName, actionType, e);
            }
        });
    }

    private static boolean isPermanentMonitorFailure(Throwable e) {
        Throwable current = e;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("Cannot find property file")) {
                return true;
            }
            current = current.getCause();
        }
        return e instanceof LinkageError;
    }

    private static String sanitizeContent(String content) {
        if (content == null) {
            return null;
        }
        String contentToSave = content.length() > 1000 ? content.substring(0, 1000) : content;
        try {
            byte[] bytes = contentToSave.getBytes(StandardCharsets.UTF_8);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return contentToSave.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        }
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
