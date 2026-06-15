package ai.config;

import ai.config.pojo.FilterConfig;
import ai.config.pojo.FilterRule;
import ai.config.pojo.FiltersConfig;
import ai.intent.impl.SampleIntentServiceImpl;
import ai.openai.pojo.ChatCompletionChoice;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.utils.ContinueWordUtil;
import ai.utils.PriorityWordUtil;
import ai.utils.SensitiveWordUtil;
import ai.utils.StoppingWordUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterConfigServiceTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void reset() {
        FilterConfigService.resetForTests();
        SensitiveWordUtil.reloadRules(null, null, -1);
        PriorityWordUtil.reloadWords(null);
        ContinueWordUtil.reloadWords(null);
        StoppingWordUtil.reloadWords(null);
    }

    @Test
    void addSameTypeCreatesMultipleRowsAndUpdateDeleteUseId() throws Exception {
        useTempDb("multi-saas.db");

        FilterConfig first = FilterConfigService.add(simpleConfig("priority", "alpha"));
        FilterConfig second = FilterConfigService.add(simpleConfig("priority", "beta"));

        assertNotNull(first.getId());
        assertNotNull(second.getId());
        assertFalse(first.getId().equals(second.getId()));
        assertEquals(2, FilterConfigService.list().size());

        first.setRules("gamma");
        FilterConfigService.update(first);
        List<FilterConfig> updated = FilterConfigService.list();
        assertEquals(2, updated.size());
        assertTrue(updated.stream().anyMatch(item -> first.getId().equals(item.getId()) && "gamma".equals(item.getRules())));
        assertTrue(updated.stream().anyMatch(item -> second.getId().equals(item.getId()) && "beta".equals(item.getRules())));

        FilterConfigService.delete(first.getId());
        List<FilterConfig> remaining = FilterConfigService.list();
        assertEquals(1, remaining.size());
        assertEquals(second.getId(), remaining.get(0).getId());
        assertEquals("beta", remaining.get(0).getRules());
    }

    @Test
    void legacyUniqueNameTableIsMigratedWithoutDataLoss() throws Exception {
        Path db = tempDir.resolve("legacy-saas.db");
        String jdbcUrl = jdbcUrl(db);
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE lagi_filter_config (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name VARCHAR(64) NOT NULL UNIQUE," +
                    "rules TEXT," +
                    "groups TEXT," +
                    "filter_window_length INTEGER DEFAULT 0," +
                    "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            stmt.executeUpdate("INSERT INTO lagi_filter_config (name, rules) VALUES ('priority', 'alpha')");
        }
        FilterConfigService.setConnectionFactoryForTests(() -> DriverManager.getConnection(jdbcUrl));

        assertEquals(1, FilterConfigService.list().size());
        FilterConfigService.add(simpleConfig("priority", "beta"));

        List<FilterConfig> list = FilterConfigService.list();
        assertEquals(2, list.size());
        assertTrue(list.stream().anyMatch(item -> "alpha".equals(item.getRules())));
        assertTrue(list.stream().anyMatch(item -> "beta".equals(item.getRules())));
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA index_list(lagi_filter_config)")) {
            while (rs.next()) {
                assertEquals(0, rs.getInt("unique"));
            }
        }
    }

    @Test
    void initializeSeedsYamlOnlyWhenDatabaseIsEmpty() throws Exception {
        useTempDb("seed-saas.db");
        FiltersConfig yaml = new FiltersConfig();
        yaml.setItems(Collections.singletonList(simpleConfig("priority", "yaml")));

        FilterConfigService.initialize(yaml);
        assertEquals(1, FilterConfigService.list().size());
        assertEquals("yaml", FilterConfigService.list().get(0).getRules());

        FilterConfigService.add(simpleConfig("priority", "db"));
        FiltersConfig newYaml = new FiltersConfig();
        newYaml.setItems(Collections.singletonList(simpleConfig("priority", "new-yaml")));
        FilterConfigService.initialize(newYaml);

        List<FilterConfig> list = FilterConfigService.list();
        assertEquals(2, list.size());
        assertFalse(list.stream().anyMatch(item -> "new-yaml".equals(item.getRules())));
    }

    @Test
    void initializeWithNullYamlLoadsExistingDatabaseRules() throws Exception {
        useTempDb("restart-saas.db");
        FilterConfigService.add(sensitiveConfig("restart", "sensitive", "mask"));
        SensitiveWordUtil.reloadRules(null, null, -1);
        FilterConfigService.resetForTests();
        String jdbcUrl = jdbcUrl(tempDir.resolve("restart-saas.db"));
        FilterConfigService.setConnectionFactoryForTests(() -> DriverManager.getConnection(jdbcUrl));

        FilterConfigService.initialize(null);

        assertEquals("*** value", SensitiveWordUtil.filter("restart value", SensitiveWordUtil.OUTPUT_RULE_TYPE));
    }

    @Test
    void globalConfigurationAcceptsLegacyFiltersListShape() throws Exception {
        ObjectMapper mapper = new YAMLMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        GlobalConfigurations config = mapper.readValue(
                "filters:\n" +
                        "  - name: priority\n" +
                        "    rules: weather\n",
                GlobalConfigurations.class);

        assertNotNull(config.getFilters());
        assertEquals(Boolean.TRUE, config.getFilters().getEnable());
        assertEquals(1, config.getFilters().getItems().size());
        assertEquals("priority", config.getFilters().getItems().get(0).getName());
        assertEquals("weather", config.getFilters().getItems().get(0).getRules());
    }

    @Test
    void runtimeRefreshAggregatesAllFiveFilterTypes() throws Exception {
        useTempDb("runtime-saas.db");
        FilterConfigService.add(sensitiveConfig("secret", "sensitive", "mask"));
        FilterConfigService.add(sensitiveConfig("blocked", "sensitive_input", "block"));
        FilterConfigService.add(simpleConfig("priority", "vip"));
        FilterConfigService.add(simpleConfig("continue", "next"));
        FilterConfigService.add(simpleConfig("stopping", "bye"));

        FilterConfigService.refreshRuntimeFilters();

        assertEquals("*** text", SensitiveWordUtil.filter("secret text", SensitiveWordUtil.OUTPUT_RULE_TYPE));
        assertEquals("", SensitiveWordUtil.filter("blocked prompt", SensitiveWordUtil.INPUT_RULE_TYPE));
        assertTrue(PriorityWordUtil.containPriorityWord("this is vip"));
        assertTrue(ContinueWordUtil.containsStoppingWorlds("next"));
        assertTrue(StoppingWordUtil.containsStoppingWorlds("bye"));
    }

    @Test
    void convertListRulesAcceptsChineseSeparatorsAndKeepsEscapedDelimiters() {
        assertEquals(Arrays.asList("赌博", "赌博场", "稳赚不赔", "线上堵场", "博彩", "下注", "炸金花"),
                FilterConfigService.convert2ListRules("赌博，赌博场，稳赚不赔，线上堵场，博彩，下注，炸金花"));
        assertEquals(Arrays.asList("alpha", "beta", "gamma", "delta", "literal,comma", "literal，comma", "literal、slash"),
                FilterConfigService.convert2ListRules("alpha、beta\ngamma；delta;literal\\,comma，literal\\，comma、literal\\、slash"));
    }

    @Test
    void chineseSeparatedSensitiveInputRulesApplyAfterRuntimeRefresh() throws Exception {
        useTempDb("chinese-separator-saas.db");
        FilterConfigService.add(sensitiveConfig("赌博，赌博场，稳赚不赔，线上堵场，博彩，下注，炸金花",
                "sensitive_input", "block"));

        FilterConfigService.refreshRuntimeFilters();

        assertEquals("", SensitiveWordUtil.filter("炸金花怎么玩", SensitiveWordUtil.INPUT_RULE_TYPE));
        assertEquals("", SensitiveWordUtil.filter("这是稳赚不赔的吗", SensitiveWordUtil.INPUT_RULE_TYPE));
    }

    @Test
    void priorityAggregationChangesChoiceOrdering() throws Exception {
        useTempDb("priority-saas.db");
        FilterConfigService.add(simpleConfig("priority", "vip"));
        FilterConfigService.refreshRuntimeFilters();

        ChatCompletionChoice normal = choice("normal answer");
        ChatCompletionChoice priority = choice("vip answer");
        ChatCompletionResult result = new ChatCompletionResult();
        result.setChoices(Arrays.asList(normal, priority));

        List<ChatCompletionChoice> sorted = PriorityWordUtil.sortByPriorityWord(result);

        assertEquals("normal answer", sorted.get(0).getMessage().getContent());
        assertEquals("vip answer", sorted.get(1).getMessage().getContent());
    }

    @Test
    void stoppingWordMarksNewTopicInsteadOfBlockingRequest() throws Exception {
        useTempDb("stopping-saas.db");
        FilterConfigService.add(simpleConfig("continue", "continue"));
        FilterConfigService.add(simpleConfig("stopping", "new topic"));
        FilterConfigService.refreshRuntimeFilters();

        SampleIntentServiceImpl intentService = new SampleIntentServiceImpl();

        assertTrue(ContinueWordUtil.containsStoppingWorlds("continue"));
        assertTrue(StoppingWordUtil.containsStoppingWorlds("new topic"));
        assertTrue(intentService.isContinue(Collections.emptyList(), userMessage("continue")));
        assertFalse(intentService.isContinue(Collections.emptyList(), userMessage("new topic continue")));
    }

    @Test
    void stoppingAndContinueRulesIgnoreNullContent() {
        assertFalse(StoppingWordUtil.containsStoppingWorlds(null));
        assertFalse(ContinueWordUtil.containsStoppingWorlds(null));
        assertEquals(Collections.emptyList(), StoppingWordUtil.getStoppingIndex(Arrays.asList(
                ChatMessage.builder().role("user").content(null).build(),
                ChatMessage.builder().role(null).content("anything").build(),
                null
        )));
    }

    private void useTempDb(String name) {
        String jdbcUrl = jdbcUrl(tempDir.resolve(name));
        FilterConfigService.setConnectionFactoryForTests(() -> DriverManager.getConnection(jdbcUrl));
    }

    private String jdbcUrl(Path db) {
        return "jdbc:sqlite:" + db.toAbsolutePath().toString().replace('\\', '/');
    }

    private FilterConfig simpleConfig(String name, String rules) {
        FilterConfig config = new FilterConfig();
        config.setName(name);
        config.setRules(rules);
        return config;
    }

    private FilterConfig sensitiveConfig(String ruleText, String name, String level) {
        FilterRule rule = new FilterRule();
        rule.setLevel(level);
        rule.setRules(ruleText);
        rule.setMask("***");
        FilterConfig config = new FilterConfig();
        config.setName(name);
        config.setGroups(Collections.singletonList(rule));
        return config;
    }

    private ChatCompletionChoice choice(String content) {
        ChatCompletionChoice choice = new ChatCompletionChoice();
        choice.setMessage(ChatMessage.builder().content(content).build());
        return choice;
    }

    private ChatMessage userMessage(String content) {
        return ChatMessage.builder().role("user").content(content).build();
    }
}
