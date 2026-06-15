package ai.config;

import ai.common.db.HikariDS;
import ai.common.pojo.WordRule;
import ai.common.pojo.WordRules;
import ai.config.pojo.FilterConfig;
import ai.config.pojo.FilterRule;
import ai.config.pojo.FiltersConfig;
import ai.utils.AiGlobal;
import ai.utils.ContinueWordUtil;
import ai.utils.PriorityWordUtil;
import ai.utils.SensitiveWordUtil;
import ai.utils.StoppingWordUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Slf4j
public class FilterConfigService {
    private static final Callable<Connection> DEFAULT_CONNECTION_FACTORY = new Callable<Connection>() {
        @Override
        public Connection call() throws Exception {
            return HikariDS.getConnection(AiGlobal.DEFAULT_DB);
        }
    };

    private static volatile Callable<Connection> connectionFactory = DEFAULT_CONNECTION_FACTORY;
    private static volatile boolean tableInitialized = false;
    private static final Gson GSON = new Gson();
    private static final Set<String> VALID_FILTER_NAMES = new HashSet<>(
            Arrays.asList("sensitive", "sensitive_input", "priority", "continue", "stopping"));

    public static final Map<Long, FilterConfig> filterConfigCache = new ConcurrentHashMap<>();

    private FilterConfigService() {
    }

    private static Connection getConnection() throws Exception {
        return connectionFactory.call();
    }

    public static synchronized void initialize(FiltersConfig yamlFilters) {
        ensureTableExists();
        loadCacheFromDatabase();
        if (filterConfigCache.isEmpty() && yamlFilters != null && yamlFilters.getItems() != null && !yamlFilters.getItems().isEmpty()) {
            seedFromYaml(yamlFilters.getItems());
            loadCacheFromDatabase();
        }
        refreshRuntimeFilters(cachedList());
    }

    public static List<FilterConfig> list() {
        ensureTableExists();
        loadCacheFromDatabase();
        return cachedList();
    }

    public static List<FilterConfig> cachedList() {
        List<FilterConfig> result = new ArrayList<>(filterConfigCache.values());
        result.sort((left, right) -> {
            Long leftId = left == null ? null : left.getId();
            Long rightId = right == null ? null : right.getId();
            if (leftId == null && rightId == null) {
                return 0;
            }
            if (leftId == null) {
                return 1;
            }
            if (rightId == null) {
                return -1;
            }
            return leftId.compareTo(rightId);
        });
        return result;
    }

    public static FilterConfig add(FilterConfig config) {
        ensureTableExists();
        validateFilterConfig(config, false);
        String sql = "INSERT INTO lagi_filter_config (name, rules, groups, filter_window_length) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, config.getName());
            pstmt.setString(2, config.getRules());
            pstmt.setString(3, groupsToJson(config.getGroups()));
            pstmt.setInt(4, config.getFilterWindowLength());
            int rows = pstmt.executeUpdate();
            if (rows == 0) {
                throw new RuntimeException("add filter config affected 0 rows");
            }
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    config.setId(generatedKeys.getLong(1));
                }
            }
            loadCacheFromDatabase();
            log.info("add filter config success: id={}, name={}", config.getId(), config.getName());
            return copyFilterConfig(filterConfigCache.get(config.getId()));
        } catch (Exception e) {
            log.error("add filter config failed", e);
            throw new RuntimeException("add filter config failed: " + e.getMessage(), e);
        }
    }

    public static FilterConfig update(FilterConfig config) {
        ensureTableExists();
        validateFilterConfig(config, true);
        String sql = "UPDATE lagi_filter_config SET name = ?, rules = ?, groups = ?, filter_window_length = ?, update_time = datetime('now') WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, config.getName());
            pstmt.setString(2, config.getRules());
            pstmt.setString(3, groupsToJson(config.getGroups()));
            pstmt.setInt(4, config.getFilterWindowLength());
            pstmt.setLong(5, config.getId());
            int rows = pstmt.executeUpdate();
            if (rows == 0) {
                throw new RuntimeException("filter config not found in database: " + config.getId());
            }
            loadCacheFromDatabase();
            log.info("update filter config success: id={}, name={}", config.getId(), config.getName());
            return copyFilterConfig(filterConfigCache.get(config.getId()));
        } catch (Exception e) {
            log.error("update filter config failed", e);
            throw new RuntimeException("update filter config failed: " + e.getMessage(), e);
        }
    }

    public static void delete(Long id) {
        ensureTableExists();
        if (id == null || id <= 0) {
            throw new RuntimeException("filter config id is required");
        }
        String sql = "DELETE FROM lagi_filter_config WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            int rows = pstmt.executeUpdate();
            if (rows == 0) {
                throw new RuntimeException("filter config not found in database: " + id);
            }
            loadCacheFromDatabase();
            log.info("delete filter config success: id={}", id);
        } catch (Exception e) {
            log.error("delete filter config failed", e);
            throw new RuntimeException("delete filter config failed: " + e.getMessage(), e);
        }
    }

    public static void validateFilterConfig(FilterConfig filterConfig) {
        validateFilterConfig(filterConfig, filterConfig != null && filterConfig.getId() != null);
    }

    private static void validateFilterConfig(FilterConfig filterConfig, boolean requireId) {
        if (filterConfig == null) {
            throw new RuntimeException("filter config is required");
        }
        normalizeFilterConfig(filterConfig);
        if (requireId && (filterConfig.getId() == null || filterConfig.getId() <= 0)) {
            throw new RuntimeException("filter config id is required");
        }
        String name = filterConfig.getName();
        if (name == null || name.trim().isEmpty()) {
            throw new RuntimeException("filter config name is required");
        }
        if (!VALID_FILTER_NAMES.contains(name)) {
            throw new RuntimeException("unsupported filter config name: " + name);
        }
        if ("sensitive".equals(name) || "sensitive_input".equals(name)) {
            SensitiveWordUtil.validateRules(convert2WordRules(filterConfig));
        } else {
            for (String rule : convert2List(filterConfig)) {
                Pattern.compile(rule);
            }
        }
    }

    public static List<FilterConfig> aggregateFilters(Collection<FilterConfig> filters) {
        Map<String, FilterConfig> aggregated = new LinkedHashMap<>();
        if (filters != null) {
            for (FilterConfig filter : filters) {
                if (filter == null || filter.getName() == null) {
                    continue;
                }
                FilterConfig target = aggregated.get(filter.getName());
                if (target == null) {
                    target = new FilterConfig();
                    target.setName(filter.getName());
                    target.setRules(null);
                    target.setGroups(new ArrayList<>());
                    target.setFilterWindowLength(filter.getFilterWindowLength());
                    aggregated.put(filter.getName(), target);
                }
                if (filter.getGroups() != null && !filter.getGroups().isEmpty()) {
                    target.getGroups().addAll(copyRules(filter.getGroups()));
                }
                if (filter.getRules() != null && !filter.getRules().trim().isEmpty()) {
                    target.setRules(joinRules(target.getRules(), filter.getRules()));
                }
                if (filter.getFilterWindowLength() > 0) {
                    target.setFilterWindowLength(filter.getFilterWindowLength());
                }
            }
        }
        return new ArrayList<>(aggregated.values());
    }

    public static void refreshRuntimeFilters() {
        ensureTableExists();
        loadCacheFromDatabase();
        refreshRuntimeFilters(cachedList());
    }

    public static void refreshRuntimeFilters(Collection<FilterConfig> filterEntries) {
        List<FilterConfig> filters = aggregateFilters(filterEntries);
        WordRules outputRules = null;
        WordRules inputRules = null;
        int windowLength = -1;
        List<String> priorityRules = new ArrayList<>();
        List<String> continueRules = new ArrayList<>();
        List<String> stoppingRules = new ArrayList<>();
        for (FilterConfig filter : filters) {
            String name = filter.getName();
            if ("sensitive".equals(name)) {
                outputRules = convert2WordRules(filter);
                windowLength = filter.getFilterWindowLength() > 0 ? filter.getFilterWindowLength() : 200;
            } else if ("sensitive_input".equals(name)) {
                inputRules = convert2WordRules(filter);
            } else if ("priority".equals(name)) {
                priorityRules.addAll(convert2List(filter));
            } else if ("continue".equals(name)) {
                continueRules.addAll(convert2List(filter));
            } else if ("stopping".equals(name)) {
                stoppingRules.addAll(convert2List(filter));
            }
        }
        SensitiveWordUtil.reloadRules(outputRules, inputRules, windowLength);
        PriorityWordUtil.reloadWords(priorityRules);
        ContinueWordUtil.reloadWords(continueRules);
        StoppingWordUtil.reloadWords(stoppingRules);
        refreshInMemoryConfiguration(filters);
    }

    private static synchronized void ensureTableExists() {
        if (tableInitialized) {
            return;
        }
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS lagi_filter_config (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name VARCHAR(64) NOT NULL," +
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
            if (hasUniqueIndexOnName(stmt)) {
                rebuildTableWithoutNameUnique(conn);
            }
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

    private static boolean hasUniqueIndexOnName(Statement stmt) throws Exception {
        List<String> uniqueIndexNames = new ArrayList<>();
        try (ResultSet indexes = stmt.executeQuery("PRAGMA index_list(lagi_filter_config)")) {
            while (indexes.next()) {
                if (indexes.getInt("unique") != 0) {
                    uniqueIndexNames.add(indexes.getString("name"));
                }
            }
        }
        for (String indexName : uniqueIndexNames) {
            try (ResultSet columns = stmt.executeQuery("PRAGMA index_info(\"" + indexName.replace("\"", "\"\"") + "\")")) {
                int count = 0;
                boolean nameOnly = false;
                while (columns.next()) {
                    count++;
                    if ("name".equalsIgnoreCase(columns.getString("name"))) {
                        nameOnly = true;
                    }
                }
                if (count == 1 && nameOnly) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void rebuildTableWithoutNameUnique(Connection conn) throws Exception {
        log.info("migrating lagi_filter_config: remove UNIQUE constraint from name");
        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE lagi_filter_config RENAME TO lagi_filter_config_old");
            stmt.executeUpdate("CREATE TABLE lagi_filter_config (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name VARCHAR(64) NOT NULL," +
                    "rules TEXT," +
                    "groups TEXT," +
                    "filter_window_length INTEGER DEFAULT 0," +
                    "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            stmt.executeUpdate("INSERT INTO lagi_filter_config (id, name, rules, groups, filter_window_length, create_time, update_time) " +
                    "SELECT id, name, rules, groups, filter_window_length, " +
                    "COALESCE(create_time, CURRENT_TIMESTAMP), COALESCE(update_time, CURRENT_TIMESTAMP) " +
                    "FROM lagi_filter_config_old");
            stmt.executeUpdate("DROP TABLE lagi_filter_config_old");
            conn.commit();
        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(originalAutoCommit);
        }
    }

    private static void loadCacheFromDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, name, rules, groups, filter_window_length FROM lagi_filter_config ORDER BY id")) {
            filterConfigCache.clear();
            while (rs.next()) {
                FilterConfig config = new FilterConfig();
                config.setId(rs.getLong("id"));
                config.setName(rs.getString("name"));
                config.setRules(rs.getString("rules"));
                config.setGroups(parseGroups(rs.getString("groups")));
                config.setFilterWindowLength(rs.getInt("filter_window_length"));
                filterConfigCache.put(config.getId(), config);
            }
        } catch (Exception e) {
            log.error("load filter config from database failed", e);
            throw new RuntimeException("load filter config failed: " + e.getMessage(), e);
        }
    }

    private static void seedFromYaml(List<FilterConfig> yamlFilters) {
        if (yamlFilters == null || yamlFilters.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO lagi_filter_config (name, rules, groups, filter_window_length) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            int seeded = 0;
            for (FilterConfig filter : yamlFilters) {
                normalizeFilterConfig(filter);
                if (filter == null || filter.getName() == null || !VALID_FILTER_NAMES.contains(filter.getName())) {
                    continue;
                }
                pstmt.setString(1, filter.getName());
                pstmt.setString(2, filter.getRules());
                pstmt.setString(3, groupsToJson(filter.getGroups()));
                pstmt.setInt(4, filter.getFilterWindowLength());
                pstmt.addBatch();
                seeded++;
            }
            if (seeded > 0) {
                pstmt.executeBatch();
            }
            log.info("seeded {} filter configs from YAML", seeded);
        } catch (Exception e) {
            log.error("seed filter config from YAML failed", e);
            throw new RuntimeException("seed filter config from YAML failed: " + e.getMessage(), e);
        }
    }

    public static List<FilterConfig> loadFromYamlResource(String yamlName) {
        try (InputStream resourceStream = FilterConfigService.class.getResourceAsStream("/" + yamlName)) {
            if (resourceStream == null) {
                return Collections.emptyList();
            }
            ObjectMapper mapper = new YAMLMapper();
            mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            @SuppressWarnings("unchecked")
            Map<String, Object> root = mapper.readValue(new InputStreamReader(resourceStream, StandardCharsets.UTF_8), Map.class);
            return loadFromYamlMap(root);
        } catch (Exception e) {
            log.warn("load filter config from YAML resource failed: {}", yamlName, e);
            return Collections.emptyList();
        }
    }

    public static List<FilterConfig> loadFromYamlMap(Map<String, Object> yamlMap) {
        if (yamlMap == null) {
            return Collections.emptyList();
        }
        Object filtersObj = yamlMap.get("filters");
        Object itemsObj = filtersObj;
        if (filtersObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> filtersMap = (Map<String, Object>) filtersObj;
            itemsObj = filtersMap.get("items");
        }
        if (!(itemsObj instanceof List)) {
            return Collections.emptyList();
        }
        List<FilterConfig> result = new ArrayList<>();
        for (Object item : (List<?>) itemsObj) {
            if (!(item instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> filterMap = (Map<String, Object>) item;
            FilterConfig config = mapToFilterConfig(filterMap);
            if (config.getName() != null) {
                result.add(config);
            }
        }
        return result;
    }

    private static FilterConfig mapToFilterConfig(Map<String, Object> filterMap) {
        FilterConfig config = new FilterConfig();
        config.setName(asString(filterMap.get("name")));
        config.setRules(asString(filterMap.get("rules")));
        config.setFilterWindowLength(asInt(filterMap.get("filter_window_length")));

        Object groupsObj = filterMap.get("groups");
        if (groupsObj instanceof List) {
            List<FilterRule> groups = new ArrayList<>();
            for (Object item : (List<?>) groupsObj) {
                if (!(item instanceof Map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> groupMap = (Map<String, Object>) item;
                FilterRule rule = new FilterRule();
                rule.setLevel(asString(groupMap.get("level")));
                rule.setRules(asString(groupMap.get("rules")));
                if (groupMap.get("mask") != null) {
                    rule.setMask(asString(groupMap.get("mask")));
                }
                groups.add(rule);
            }
            config.setGroups(groups);
        }
        normalizeFilterConfig(config);
        return config;
    }

    private static String groupsToJson(List<FilterRule> groups) {
        if (groups == null || groups.isEmpty()) {
            return null;
        }
        List<Map<String, String>> groupsList = new ArrayList<>();
        for (FilterRule rule : groups) {
            Map<String, String> groupMap = new LinkedHashMap<>();
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

    public static WordRules convert2WordRules(FilterConfig filter) {
        if (filter == null || filter.getGroups() == null || filter.getGroups().isEmpty()) {
            return null;
        }
        List<WordRule> rules = new ArrayList<>();
        for (FilterRule group : filter.getGroups()) {
            if (group == null || group.getRules() == null || group.getRules().trim().isEmpty()) {
                continue;
            }
            for (String rule : convert2ListRules(group.getRules())) {
                if (rule.trim().isEmpty()) {
                    continue;
                }
                rules.add(WordRule.builder()
                        .level(convertLevel(group.getLevel()))
                        .mask(group.getMask())
                        .rule(rule.trim())
                        .build());
            }
        }
        return WordRules.builder().rules(rules).build();
    }

    public static List<String> convert2List(FilterConfig filterItem) {
        String rules = filterItem == null ? null : filterItem.getRules();
        if (rules == null || rules.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return convert2ListRules(rules);
    }

    public static List<String> convert2ListRules(String rules) {
        if (rules == null || rules.trim().isEmpty()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaping = false;
        for (int i = 0; i < rules.length(); i++) {
            char ch = rules.charAt(i);
            if (escaping) {
                if (isRuleDelimiter(ch) || ch == '\\') {
                    current.append(ch);
                } else {
                    current.append('\\').append(ch);
                }
                escaping = false;
                continue;
            }
            if (ch == '\\') {
                escaping = true;
                continue;
            }
            if (isRuleDelimiter(ch)) {
                addRuleToken(result, current);
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        if (escaping) {
            current.append('\\');
        }
        addRuleToken(result, current);
        return result;
    }

    private static boolean isRuleDelimiter(char ch) {
        return ch == ',' || ch == '，' || ch == '、' || ch == ';' || ch == '；' || ch == '\n' || ch == '\r';
    }

    private static void addRuleToken(List<String> result, StringBuilder token) {
        String value = token.toString().trim();
        if (!value.isEmpty()) {
            result.add(value);
        }
    }

    private static int convertLevel(String level) {
        if (level == null) {
            return 2;
        }
        if ("erase".equalsIgnoreCase(level) || "3".equals(level)) {
            return 3;
        }
        if ("block".equalsIgnoreCase(level) || "1".equals(level)) {
            return 1;
        }
        if ("mask".equalsIgnoreCase(level) || "2".equals(level)) {
            return 2;
        }
        try {
            int levelInt = Integer.parseInt(level);
            return levelInt >= 1 && levelInt <= 3 ? levelInt : 2;
        } catch (NumberFormatException e) {
            return 2;
        }
    }

    private static void refreshInMemoryConfiguration(List<FilterConfig> filters) {
        if (ContextLoader.configuration == null) {
            return;
        }
        FiltersConfig filtersConfig = ContextLoader.configuration.getFilters();
        if (filtersConfig == null) {
            filtersConfig = new FiltersConfig();
            ContextLoader.configuration.setFilters(filtersConfig);
        }
        filtersConfig.setItems(filters);
    }

    private static void normalizeFilterConfig(FilterConfig filterConfig) {
        if (filterConfig == null) {
            return;
        }
        filterConfig.setName(trimToNull(filterConfig.getName()));
        filterConfig.setRules(trimToNull(filterConfig.getRules()));
        if (filterConfig.getGroups() == null) {
            return;
        }
        List<FilterRule> normalizedGroups = new ArrayList<>();
        for (FilterRule group : filterConfig.getGroups()) {
            if (group == null) {
                continue;
            }
            group.setLevel(trimToNull(group.getLevel()));
            group.setRules(trimToNull(group.getRules()));
            group.setMask(trimToNull(group.getMask()));
            if (group.getLevel() != null || group.getRules() != null || group.getMask() != null) {
                normalizedGroups.add(group);
            }
        }
        filterConfig.setGroups(normalizedGroups.isEmpty() ? null : normalizedGroups);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String joinRules(String current, String next) {
        if (current == null || current.trim().isEmpty()) {
            return next;
        }
        if (next == null || next.trim().isEmpty()) {
            return current;
        }
        return current + "," + next;
    }

    private static List<FilterRule> copyRules(List<FilterRule> sourceRules) {
        if (sourceRules == null) {
            return new ArrayList<>();
        }
        List<FilterRule> copied = new ArrayList<>();
        for (FilterRule sourceRule : sourceRules) {
            if (sourceRule == null) {
                continue;
            }
            FilterRule rule = new FilterRule();
            rule.setLevel(sourceRule.getLevel());
            rule.setMask(sourceRule.getMask());
            rule.setRules(sourceRule.getRules());
            copied.add(rule);
        }
        return copied;
    }

    private static FilterConfig copyFilterConfig(FilterConfig source) {
        if (source == null) {
            return null;
        }
        FilterConfig copy = new FilterConfig();
        copy.setId(source.getId());
        copy.setName(source.getName());
        copy.setRules(source.getRules());
        copy.setFilterWindowLength(source.getFilterWindowLength());
        copy.setGroups(copyRules(source.getGroups()));
        return copy;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int asInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static synchronized void setConnectionFactoryForTests(Callable<Connection> factory) {
        connectionFactory = factory == null ? DEFAULT_CONNECTION_FACTORY : factory;
        tableInitialized = false;
        filterConfigCache.clear();
    }

    public static synchronized void resetForTests() {
        connectionFactory = DEFAULT_CONNECTION_FACTORY;
        tableInitialized = false;
        filterConfigCache.clear();
    }
}
