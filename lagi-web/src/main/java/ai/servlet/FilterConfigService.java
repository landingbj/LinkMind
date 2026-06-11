package ai.servlet;

import ai.config.pojo.FilterConfig;
import ai.config.pojo.FilterRule;
import ai.database.impl.SqliteAdapter;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Callable;

@Slf4j
public class FilterConfigService {
    private static volatile SqliteAdapter sqliteAdapter;
    private static final Callable<Connection> DEFAULT_CONNECTION_FACTORY = new Callable<Connection>() {
        @Override
        public Connection call() throws Exception {
            return getSqliteAdapter().getCon();
        }
    };
    private static volatile Callable<Connection> connectionFactory = DEFAULT_CONNECTION_FACTORY;
    public static final Map<String, FilterConfig> filterConfigCache = new ConcurrentHashMap<>();
    private static volatile boolean tableInitialized = false;
    private static final Gson GSON = new Gson();

    private static SqliteAdapter getSqliteAdapter() {
        SqliteAdapter adapter = sqliteAdapter;
        if (adapter == null) {
            synchronized (FilterConfigService.class) {
                adapter = sqliteAdapter;
                if (adapter == null) {
                    adapter = new SqliteAdapter();
                    sqliteAdapter = adapter;
                }
            }
        }
        return adapter;
    }

    private static Connection getConnection() throws Exception {
        return connectionFactory.call();
    }

    private static synchronized void ensureTableExists() {
        if (tableInitialized) {
            return;
        }
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS lagi_filter_config (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name VARCHAR(64) NOT NULL UNIQUE," +
                    "rules TEXT," +
                    "groups TEXT," +
                    "filter_window_length INTEGER DEFAULT 0," +
                    "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                    ")";
            stmt.executeUpdate(sql);
            ensureColumn(stmt, "rules", "TEXT");
            ensureColumn(stmt, "groups", "TEXT");
            ensureColumn(stmt, "filter_window_length", "INTEGER DEFAULT 0");
            ensureColumn(stmt, "create_time", "DATETIME");
            ensureColumn(stmt, "update_time", "DATETIME");
            tableInitialized = true;
            loadCacheFromDatabase();
        } catch (Exception e) {
            log.error("init lagi_filter_config table failed", e);
            throw new RuntimeException("init filter config table failed: " + e.getMessage(), e);
        }
    }

    private static void ensureColumn(Statement stmt, String columnName, String definition) throws Exception {
        if (getExistingColumns(stmt).contains(columnName)) {
            return;
        }
        stmt.executeUpdate("ALTER TABLE lagi_filter_config ADD COLUMN " + columnName + " " + definition);
        log.info("migrated lagi_filter_config, added column: {}", columnName);
    }

    private static Set<String> getExistingColumns(Statement stmt) throws Exception {
        Set<String> columns = new HashSet<>();
        try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(lagi_filter_config)")) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        }
        return columns;
    }

    public static void loadFromDatabase() {
        ensureTableExists();
        loadCacheFromDatabase();
    }

    private static void loadCacheFromDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM lagi_filter_config")) {
            filterConfigCache.clear();
            while (rs.next()) {
                FilterConfig config = new FilterConfig();
                config.setName(rs.getString("name"));
                config.setRules(rs.getString("rules"));
                config.setGroups(parseGroups(rs.getString("groups")));
                config.setFilterWindowLength(rs.getInt("filter_window_length"));
                filterConfigCache.put(config.getName(), config);
            }
        } catch (Exception e) {
            log.error("load filter config from database failed", e);
            throw new RuntimeException("load filter config failed: " + e.getMessage(), e);
        }
    }

    public static List<FilterConfig> list() {
        ensureTableExists();
        return cachedList();
    }

    static List<FilterConfig> cachedList() {
        return new ArrayList<>(filterConfigCache.values());
    }

    public static void add(FilterConfig config) {
        ensureTableExists();
        if (filterConfigCache.containsKey(config.getName())) {
            log.info("filter config {} already exists, update instead", config.getName());
            update(config);
            return;
        }

        String sql = "INSERT INTO lagi_filter_config (name, rules, groups, filter_window_length) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, config.getName());
            pstmt.setString(2, config.getRules());
            pstmt.setString(3, groupsToJson(config.getGroups()));
            pstmt.setInt(4, config.getFilterWindowLength());
            pstmt.executeUpdate();
            filterConfigCache.put(config.getName(), config);
            log.info("add filter config success: {}", config.getName());
        } catch (Exception e) {
            log.error("add filter config failed", e);
            throw new RuntimeException("add filter config failed: " + e.getMessage(), e);
        }
    }

    public static void update(FilterConfig config) {
        ensureTableExists();
        if (!filterConfigCache.containsKey(config.getName())) {
            throw new RuntimeException("filter config not found: " + config.getName());
        }

        String sql = "UPDATE lagi_filter_config SET rules = ?, groups = ?, filter_window_length = ?, update_time = datetime('now') WHERE name = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, config.getRules());
            pstmt.setString(2, groupsToJson(config.getGroups()));
            pstmt.setInt(3, config.getFilterWindowLength());
            pstmt.setString(4, config.getName());
            int rows = pstmt.executeUpdate();
            if (rows == 0) {
                throw new RuntimeException("filter config not found in database: " + config.getName());
            }
            filterConfigCache.put(config.getName(), config);
            log.info("update filter config success: {}", config.getName());
        } catch (Exception e) {
            log.error("update filter config failed", e);
            throw new RuntimeException("update filter config failed: " + e.getMessage(), e);
        }
    }

    public static void delete(String name) {
        ensureTableExists();
        if (!filterConfigCache.containsKey(name)) {
            throw new RuntimeException("filter config not found: " + name);
        }

        String sql = "DELETE FROM lagi_filter_config WHERE name = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            int rows = pstmt.executeUpdate();
            if (rows == 0) {
                throw new RuntimeException("filter config not found in database: " + name);
            }
            filterConfigCache.remove(name);
            log.info("delete filter config success: {}", name);
        } catch (Exception e) {
            log.error("delete filter config failed", e);
            throw new RuntimeException("delete filter config failed: " + e.getMessage(), e);
        }
    }

    private static String groupsToJson(List<FilterRule> groups) {
        if (groups == null || groups.isEmpty()) {
            return null;
        }
        List<Map<String, String>> groupsList = new ArrayList<>();
        for (FilterRule rule : groups) {
            Map<String, String> groupMap = new HashMap<>();
            groupMap.put("level", rule.getLevel());
            groupMap.put("rules", rule.getRules());
            groupMap.put("mask", rule.getMask());
            groupsList.add(groupMap);
        }
        return GSON.toJson(groupsList);
    }

    private static List<FilterRule> parseGroups(String groupsJson) {
        List<FilterRule> filterRules = new ArrayList<>();
        if (groupsJson == null || groupsJson.trim().isEmpty()) {
            return filterRules;
        }
        try {
            List<Map<String, String>> groupsList = GSON.fromJson(groupsJson,
                    new TypeToken<List<Map<String, String>>>() {
                    }.getType());
            if (groupsList == null) {
                return filterRules;
            }
            for (Map<String, String> groupMap : groupsList) {
                FilterRule rule = new FilterRule();
                rule.setLevel(groupMap.get("level"));
                rule.setRules(groupMap.get("rules"));
                rule.setMask(groupMap.get("mask"));
                filterRules.add(rule);
            }
        } catch (Exception e) {
            log.warn("parse filter groups JSON failed: {}", groupsJson, e);
        }
        return filterRules;
    }

    static synchronized void setConnectionFactoryForTests(Callable<Connection> factory) {
        connectionFactory = factory == null ? DEFAULT_CONNECTION_FACTORY : factory;
        sqliteAdapter = null;
        tableInitialized = false;
        filterConfigCache.clear();
    }

    static synchronized void resetForTests() {
        connectionFactory = DEFAULT_CONNECTION_FACTORY;
        sqliteAdapter = null;
        tableInitialized = false;
        filterConfigCache.clear();
    }
}
