package ai.servlet;

import ai.config.pojo.FilterConfig;
import ai.config.pojo.FilterRule;
import ai.starter.InstallerUtil;
import ai.utils.SensitiveWordUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterConfigServletTest {

    @TempDir
    Path tempDir;

    private final String previousConfigProperty = System.getProperty(InstallerUtil.CONFIG_FILE_PROPERTY);

    @AfterEach
    void restoreConfigProperty() {
        if (previousConfigProperty == null) {
            System.clearProperty(InstallerUtil.CONFIG_FILE_PROPERTY);
        } else {
            System.setProperty(InstallerUtil.CONFIG_FILE_PROPERTY, previousConfigProperty);
        }
        FilterConfigService.resetForTests();
        SensitiveWordUtil.reloadRules(null, null, -1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadFromYamlAcceptsObjectAndLegacyListShape() throws Exception {
        Path objectConfig = writeConfig("filters:\n  enable: true\n  items:\n    - name: sensitive\n      groups:\n        - level: mask\n          rules: secret\n");
        System.setProperty(InstallerUtil.CONFIG_FILE_PROPERTY, objectConfig.toString());
        List<FilterConfig> objectFilters = (List<FilterConfig>) invokePrivate(newServlet(), "loadFromYaml");
        assertEquals(1, objectFilters.size());
        assertEquals("sensitive", objectFilters.get(0).getName());

        Path listConfig = writeConfig("filters:\n  - name: priority\n    rules: weather\n");
        System.setProperty(InstallerUtil.CONFIG_FILE_PROPERTY, listConfig.toString());
        List<FilterConfig> listFilters = (List<FilterConfig>) invokePrivate(newServlet(), "loadFromYaml");
        assertEquals(1, listFilters.size());
        assertEquals("priority", listFilters.get(0).getName());
    }

    @Test
    void addUpdateDeleteUseDatabaseAndDoNotModifyYaml() throws Exception {
        Path config = writeConfig("filters:\n  enable: true\n  items: []\n");
        System.setProperty(InstallerUtil.CONFIG_FILE_PROPERTY, config.toString());
        String originalYaml = new String(Files.readAllBytes(config), StandardCharsets.UTF_8);
        useTempDb("crud-saas.db");
        FilterConfigServlet servlet = newServlet();

        FilterConfig first = priorityConfig("alpha");
        FilterConfig second = priorityConfig("beta");
        assertEquals(Boolean.TRUE, servlet.add(first).get("success"));
        assertEquals(Boolean.TRUE, servlet.add(second).get("success"));

        List<FilterConfig> afterAdd = FilterConfigService.list();
        assertEquals(2, afterAdd.size());
        assertTrue(afterAdd.stream().anyMatch(item -> "alpha".equals(item.getRules())));
        assertTrue(afterAdd.stream().anyMatch(item -> "beta".equals(item.getRules())));

        FilterConfig toUpdate = afterAdd.get(0);
        toUpdate.setRules("gamma");
        assertEquals(Boolean.TRUE, servlet.update(toUpdate).get("success"));

        List<FilterConfig> afterUpdate = FilterConfigService.list();
        assertEquals(2, afterUpdate.size());
        assertTrue(afterUpdate.stream().anyMatch(item -> "gamma".equals(item.getRules())));
        assertTrue(afterUpdate.stream().anyMatch(item -> "beta".equals(item.getRules())));
        assertFalse(afterUpdate.stream().anyMatch(item -> "alpha".equals(item.getRules())));

        Map<String, Object> deleteRequest = new HashMap<>();
        deleteRequest.put("id", toUpdate.getId());
        assertEquals(Boolean.TRUE, servlet.delete(deleteRequest).get("success"));
        List<FilterConfig> afterDelete = FilterConfigService.list();
        assertEquals(1, afterDelete.size());
        assertEquals("beta", afterDelete.get(0).getRules());

        assertEquals(originalYaml, new String(Files.readAllBytes(config), StandardCharsets.UTF_8));
    }

    @Test
    void addSensitiveRuleReloadsRuntimeImmediately() throws Exception {
        useTempDb("runtime-saas.db");
        FilterConfigServlet servlet = newServlet();

        FilterConfig output = sensitiveConfig("secret", "sensitive", "mask");
        servlet.add(output);
        FilterConfig input = sensitiveConfig("blocked", "sensitive_input", "block");
        servlet.add(input);

        assertEquals("*** text", SensitiveWordUtil.filter("secret text", SensitiveWordUtil.OUTPUT_RULE_TYPE));
        assertEquals("", SensitiveWordUtil.filter("blocked prompt", SensitiveWordUtil.INPUT_RULE_TYPE));
    }

    @Test
    void invalidRegexIsRejectedBeforeSavingAndDoesNotPolluteRuntime() throws Exception {
        useTempDb("invalid-saas.db");
        FilterConfigServlet servlet = newServlet();
        servlet.add(sensitiveConfig("safe", "sensitive", "mask"));
        assertEquals("*** text", SensitiveWordUtil.filter("safe text", SensitiveWordUtil.OUTPUT_RULE_TYPE));

        assertThrows(Exception.class, () -> servlet.add(sensitiveConfig("[", "sensitive", "mask")));

        List<FilterConfig> configs = FilterConfigService.list();
        assertEquals(1, configs.size());
        assertEquals("*** text", SensitiveWordUtil.filter("safe text", SensitiveWordUtil.OUTPUT_RULE_TYPE));
    }

    private FilterConfigServlet newServlet() throws Exception {
        FilterConfigServlet servlet = new FilterConfigServlet();
        Field pathField = FilterConfigServlet.class.getDeclaredField("lagiYmlPath");
        pathField.setAccessible(true);
        pathField.set(servlet, System.getProperty(InstallerUtil.CONFIG_FILE_PROPERTY));
        return servlet;
    }

    private void useTempDb(String name) {
        Path db = tempDir.resolve(name);
        String jdbcUrl = "jdbc:sqlite:" + db.toAbsolutePath().toString().replace('\\', '/');
        FilterConfigService.setConnectionFactoryForTests(() -> DriverManager.getConnection(jdbcUrl));
    }

    private FilterConfig priorityConfig(String rules) {
        FilterConfig config = new FilterConfig();
        config.setName("priority");
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

    private Path writeConfig(String content) throws Exception {
        Path config = tempDir.resolve("lagi-" + System.nanoTime() + ".yml");
        Files.write(config, content.getBytes(StandardCharsets.UTF_8));
        return config;
    }

    private Object invokePrivate(Object target, String methodName, Object... args) throws Exception {
        Method method = findPrivateMethod(methodName, args);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private Method findPrivateMethod(String methodName, Object... args) throws Exception {
        for (Method method : FilterConfigServlet.class.getDeclaredMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterTypes().length != args.length) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            boolean matches = true;
            for (int i = 0; i < parameterTypes.length; i++) {
                if (args[i] != null && !parameterTypes[i].isAssignableFrom(args[i].getClass())) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return method;
            }
        }
        throw new NoSuchMethodException(methodName);
    }
}
