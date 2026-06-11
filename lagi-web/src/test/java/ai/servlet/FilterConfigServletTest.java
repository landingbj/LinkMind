package ai.servlet;

import ai.config.pojo.FilterConfig;
import ai.config.pojo.FilterRule;
import ai.starter.InstallerUtil;
import ai.utils.SensitiveWordUtil;
import ai.utils.YmlLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        FilterConfigService.filterConfigCache.clear();
    }

    @Test
    @SuppressWarnings("unchecked")
    void syncToYamlPreservesFiltersObjectShape() throws Exception {
        Path config = writeConfig("filters:\n  enable: false\n  items: []\n");
        System.setProperty(InstallerUtil.CONFIG_FILE_PROPERTY, config.toString());
        FilterConfigService.filterConfigCache.clear();
        FilterConfigService.filterConfigCache.put("sensitive", sensitiveConfig("secret"));

        invokePrivate(newServlet(), "syncToYaml");

        Map<String, Object> root = YmlLoader.loadYamlAsMap(config.toString());
        Map<String, Object> filters = (Map<String, Object>) root.get("filters");
        assertEquals(Boolean.FALSE, filters.get("enable"));
        assertTrue(filters.get("items") instanceof List);
        Map<String, Object> first = (Map<String, Object>) ((List<?>) filters.get("items")).get(0);
        assertEquals("sensitive", first.get("name"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadFromYamlAcceptsLegacyBrokenListShape() throws Exception {
        Path config = writeConfig("filters:\n  - name: sensitive\n    groups:\n      - level: mask\n        rules: secret\n");
        System.setProperty(InstallerUtil.CONFIG_FILE_PROPERTY, config.toString());

        List<FilterConfig> filters = (List<FilterConfig>) invokePrivate(newServlet(), "loadFromYaml");

        assertEquals(1, filters.size());
        assertEquals("sensitive", filters.get(0).getName());
        assertEquals("secret", filters.get(0).getGroups().get(0).getRules());
    }

    @Test
    @SuppressWarnings("unchecked")
    void bundledLagiYmlKeepsFiltersObjectShape() {
        Map<String, Object> root = YmlLoader.readYamlAsMap("lagi.yml");

        assertTrue(root.get("filters") instanceof Map);
        Map<String, Object> filters = (Map<String, Object>) root.get("filters");
        assertEquals(Boolean.TRUE, filters.get("enable"));
        assertTrue(filters.get("items") instanceof List);
        assertTrue(((List<Map<String, Object>>) filters.get("items")).stream()
                .anyMatch(item -> "sensitive_input".equals(item.get("name"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingYamlConfigsAddsNewFiltersWithoutOverwritingExistingDatabaseValues() throws Exception {
        FilterConfigService.filterConfigCache.clear();
        FilterConfigService.filterConfigCache.put("sensitive", sensitiveConfig("db-secret"));
        FilterConfig input = sensitiveConfig("blocked");
        input.setName("sensitive_input");

        List<FilterConfig> missing = (List<FilterConfig>) invokePrivate(
                newServlet(),
                "missingYamlConfigs",
                java.util.Arrays.asList(sensitiveConfig("yaml-secret"), input));

        assertEquals(1, missing.size());
        assertEquals("db-secret", FilterConfigService.filterConfigCache.get("sensitive").getGroups().get(0).getRules());
        assertEquals("sensitive_input", missing.get(0).getName());
        assertEquals("blocked", missing.get(0).getGroups().get(0).getRules());
    }

    @Test
    void invalidRegexIsRejectedBeforeSaving() throws Exception {
        FilterConfig invalid = sensitiveConfig("[");

        assertThrows(Exception.class, () -> invokePrivate(newServlet(), "validateFilterConfig", invalid));
    }

    @Test
    @SuppressWarnings("unchecked")
    void yamlSensitiveInputAndOutputReloadIntoRuntimeRules() throws Exception {
        Path config = writeConfig("filters:\n"
                + "  enable: true\n"
                + "  items:\n"
                + "    - name: sensitive\n"
                + "      groups:\n"
                + "        - level: mask\n"
                + "          rules: 冰毒\n"
                + "          mask: '***'\n"
                + "    - name: sensitive_input\n"
                + "      groups:\n"
                + "        - level: block\n"
                + "          rules: 冰毒\n"
                + "          mask: '***'\n");
        System.setProperty(InstallerUtil.CONFIG_FILE_PROPERTY, config.toString());
        FilterConfigServlet servlet = newServlet();
        FilterConfigService.filterConfigCache.clear();
        for (FilterConfig filter : (List<FilterConfig>) invokePrivate(servlet, "loadFromYaml")) {
            FilterConfigService.filterConfigCache.put(filter.getName(), filter);
        }

        invokePrivate(servlet, "reloadFilterUtils");

        assertEquals("", SensitiveWordUtil.filter("冰毒是什么", SensitiveWordUtil.INPUT_RULE_TYPE));
        assertEquals("***信息", SensitiveWordUtil.filter("冰毒信息", SensitiveWordUtil.OUTPUT_RULE_TYPE));
    }

    private FilterConfigServlet newServlet() throws Exception {
        FilterConfigServlet servlet = new FilterConfigServlet();
        Field pathField = FilterConfigServlet.class.getDeclaredField("lagiYmlPath");
        pathField.setAccessible(true);
        pathField.set(servlet, System.getProperty(InstallerUtil.CONFIG_FILE_PROPERTY));
        return servlet;
    }

    private FilterConfig sensitiveConfig(String ruleText) {
        FilterRule rule = new FilterRule();
        rule.setLevel("mask");
        rule.setRules(ruleText);
        rule.setMask("***");
        FilterConfig config = new FilterConfig();
        config.setName("sensitive");
        config.setGroups(Collections.singletonList(rule));
        return config;
    }

    private Path writeConfig(String content) throws Exception {
        Path config = tempDir.resolve("lagi.yml");
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
