package ai.common.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Retry helper for SQLite write paths. SQLite serializes writers at the
 * file level, so concurrent JDBC writers under Hikari may surface as
 * {@code SQLITE_BUSY} / {@code SQLITE_LOCKED} / {@code SQLITE_BUSY_SNAPSHOT}.
 * {@code SQLITE_BUSY_SNAPSHOT} in particular cannot be resolved by the
 * driver's {@code busy_timeout} because the transaction snapshot is stale
 * and the only safe recovery is to re-run the whole operation. This helper
 * retries with exponential backoff + jitter so callers don't have to
 * duplicate the boilerplate.
 */
public final class SqliteRetry {

    private static final Logger log = LoggerFactory.getLogger(SqliteRetry.class);

    /** Default maximum attempts (including the first call). */
    private static final int DEFAULT_MAX_ATTEMPTS = 8;
    /** Initial backoff before retrying. */
    private static final long INITIAL_BACKOFF_MS = 25L;
    /** Upper bound on per-retry sleep. */
    private static final long MAX_BACKOFF_MS = 400L;

    private SqliteRetry() {
    }

    @FunctionalInterface
    public interface SqlCallable<T> {
        T call() throws SQLException;
    }

    @FunctionalInterface
    public interface SqlRunnable {
        void run() throws SQLException;
    }

    public static <T> T execute(SqlCallable<T> action) throws SQLException {
        return execute(DEFAULT_MAX_ATTEMPTS, action);
    }

    public static void run(SqlRunnable action) throws SQLException {
        execute(DEFAULT_MAX_ATTEMPTS, () -> {
            action.run();
            return null;
        });
    }

    public static <T> T execute(int maxAttempts, SqlCallable<T> action) throws SQLException {
        if (maxAttempts < 1) {
            maxAttempts = 1;
        }
        SQLException last = null;
        long backoff = INITIAL_BACKOFF_MS;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.call();
            } catch (SQLException e) {
                if (!isRetryable(e) || attempt == maxAttempts) {
                    throw e;
                }
                last = e;
                long jitter = ThreadLocalRandom.current().nextLong(backoff + 1);
                long sleep = backoff + jitter;
                log.warn("SqliteRetry attempt {}/{} hit busy/locked ({}); backing off {}ms",
                        attempt, maxAttempts, summarize(e), sleep);
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
                backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
            }
        }
        // Unreachable in normal control flow; satisfy the compiler.
        throw last != null ? last : new SQLException("SqliteRetry: no attempts executed");
    }

    /**
     * @return {@code true} when the exception (or any cause) reports a
     * transient SQLite contention state worth retrying.
     */
    public static boolean isRetryable(SQLException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String msg = t.getMessage();
            if (msg != null) {
                String up = msg.toUpperCase();
                if (up.contains("SQLITE_BUSY")
                        || up.contains("SQLITE_LOCKED")
                        || up.contains("DATABASE IS LOCKED")
                        || up.contains("BUSY_SNAPSHOT")) {
                    return true;
                }
            }
        }
        int code = e.getErrorCode();
        // SQLite primary codes 5 (BUSY) / 6 (LOCKED) and common extended codes
        // 517 (BUSY_SNAPSHOT), 261 (LOCKED_SHAREDCACHE), 773 (BUSY_RECOVERY).
        return code == 5 || code == 6 || code == 517 || code == 261 || code == 773;
    }

    private static String summarize(SQLException e) {
        String msg = e.getMessage();
        if (msg == null) {
            return "errorCode=" + e.getErrorCode();
        }
        int nl = msg.indexOf('\n');
        return (nl > 0 ? msg.substring(0, nl) : msg);
    }
}
