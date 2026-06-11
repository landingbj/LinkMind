package ai.servlet;

import ai.common.pojo.WordRule;
import ai.common.pojo.WordRules;
import ai.config.pojo.FilterConfig;
import ai.config.pojo.FilterRule;
import ai.config.pojo.FiltersConfig;
import ai.servlet.annotation.Body;
import ai.servlet.annotation.Get;
import ai.servlet.annotation.Post;
import ai.servlet.exceptions.RRException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FilterConfigServlet extends RestfulServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(FilterConfigServlet.class);
    private String lagiYmlPath = null;

    private String getLagiYmlPath() {
        if (lagiYmlPath == null) {
            String configFile = System.getProperty(ai.starter.InstallerUtil.CONFIG_FILE_PROPERTY);
            if (configFile != null && !configFile.isEmpty()) {
                File f = new File(configFile);
                if (f.exists() && f.isFile()) {
                    lagiYmlPath = configFile;
                    return lagiYmlPath;
                }
            }
            String userDir = System.getProperty("user.dir");
            String[] possiblePaths = {
                    userDir + "/lagi-web/src/main/resources/lagi.yml",
                    userDir + "/src/main/resources/lagi.yml",
                    "../lagi-web/src/main/resources/lagi.yml",
                    userDir + "/WEB-INF/classes/lagi.yml",
                    "lagi.yml"
            };
            for (String path : possiblePaths) {
                File file = new File(path);
                if (file.exists() && file.isFile()) {
                    lagiYmlPath = path;
                    break;
                }
            }
            if (lagiYmlPath == null) {
                try (InputStream resourceStream = FilterConfigServlet.class.getResourceAsStream("/lagi.yml")) {
                    if (resourceStream != null) {
                        String tempPath = System.getProperty("java.io.tmpdir") + "/lagi.yml";
                        Files.copy(resourceStream, Paths.get(tempPath), StandardCopyOption.REPLACE_EXISTING);
                        File tempFile = new File(tempPath);
                        if (tempFile.exists()) {
                            lagiYmlPath = tempPath;
                        }
                    }
                } catch (Exception e) {
                    log.warn("copy classpath lagi.yml to temp failed", e);
                }
            }
        }
        return lagiYmlPath;
    }

    @Get("list")
    public List<FilterConfig> list() {
        try {
            FilterConfigService.list();
            syncFromYamlToDatabase();
            return FilterConfigService.list();
        } catch (Exception e) {
            log.error("get filter config list failed", e);
            return new ArrayList<>();
        }
    }

    private synchronized void syncFromYamlToDatabase() {
        try {
            List<FilterConfig> yamlFilters = loadFromYaml();
            if (yamlFilters != null && !yamlFilters.isEmpty()) {
                int added = 0;
                for (FilterConfig config : missingYamlConfigs(yamlFilters)) {
                    try {
                        FilterConfigService.add(config);
                        added++;
                    } catch (Exception e) {
                        log.warn("sync filter config to database failed: {}", config.getName(), e);
                    }
                }
                if (added > 0) {
                    log.info("synced {} missing filter configs from YAML to database", added);
                }
            }
        } catch (Exception e) {
            log.error("sync YAML to database failed", e);
        }
    }

    private List<FilterConfig> missingYamlConfigs(List<FilterConfig> yamlFilters) {
        List<FilterConfig> missing = new ArrayList<>();
        if (yamlFilters == null || yamlFilters.isEmpty()) {
            return missing;
        }
        for (FilterConfig config : yamlFilters) {
            if (config == null || config.getName() == null) {
                continue;
            }
            if (!FilterConfigService.filterConfigCache.containsKey(config.getName())) {
                missing.add(config);
            }
        }
        return missing;
    }

    private List<FilterConfig> loadFromYaml() {
        try {
            String ymlPath = getLagiYmlPath();
            if (ymlPath == null) {
                return new ArrayList<>();
            }

            Object yamlRoot = readYamlMap(ymlPath).get("filters");
            Object filtersObj = extractFilterItems(yamlRoot);
            if (!(filtersObj instanceof List)) {
                return new ArrayList<>();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> filtersList = (List<Map<String, Object>>) filtersObj;
            List<FilterConfig> filterConfigs = new ArrayList<>();
            for (Map<String, Object> filterMap : filtersList) {
                FilterConfig config = mapToFilterConfig(filterMap);
                if (config.getName() != null) {
                    filterConfigs.add(config);
                }
            }
            return filterConfigs;
        } catch (Exception e) {
            log.error("load filters from YAML failed", e);
            return new ArrayList<>();
        }
    }

    @Post("add")
    public Map<String, Object> add(@Body FilterConfig filterConfig) {
        try {
            normalizeFilterConfig(filterConfig);
            validateFilterConfig(filterConfig);
            String name = filterConfig.getName();
            FilterConfigService.list();
            syncFromYamlToDatabase();
            boolean existed = FilterConfigService.filterConfigCache.containsKey(name);
            FilterConfig oldConfig = copyFilterConfig(FilterConfigService.filterConfigCache.get(name));
            FilterConfigService.add(filterConfig);
            try {
                syncToYaml();
                reloadConfiguration();
            } catch (Exception writeOrReloadEx) {
                rollbackSavedFilter(name, oldConfig);
                throw writeOrReloadEx;
            }
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("success", true);
            result.put("message", existed ? "更新成功（过滤器已存在，已自动更新）" : "添加成功");
            return result;
        } catch (Exception e) {
            log.error("add filter config failed", e);
            throw businessError("添加失败", e);
        }
    }

    @Post("update")
    public Map<String, Object> update(@Body FilterConfig filterConfig) {
        try {
            normalizeFilterConfig(filterConfig);
            validateFilterConfig(filterConfig);
            String name = filterConfig.getName();
            FilterConfigService.list();
            syncFromYamlToDatabase();
            FilterConfig oldConfig = copyFilterConfig(FilterConfigService.filterConfigCache.get(name));
            FilterConfigService.update(filterConfig);
            try {
                syncToYaml();
                reloadConfiguration();
            } catch (Exception writeOrReloadEx) {
                rollbackSavedFilter(name, oldConfig);
                throw writeOrReloadEx;
            }
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("success", true);
            result.put("message", "更新成功");
            return result;
        } catch (Exception e) {
            log.error("update filter config failed", e);
            throw businessError("更新失败", e);
        }
    }

    @Post("delete")
    public Map<String, Object> delete(@Body Map<String, String> request) {
        try {
            String name = request == null ? null : request.get("name");
            if (name == null || name.trim().isEmpty()) {
                throw new RuntimeException("过滤器名称不能为空");
            }

            FilterConfigService.list();
            FilterConfig oldConfig = copyFilterConfig(FilterConfigService.filterConfigCache.get(name));
            FilterConfigService.delete(name);
            try {
                syncToYaml();
                reloadConfiguration();
            } catch (Exception writeOrReloadEx) {
                rollbackSavedFilter(name, oldConfig);
                throw writeOrReloadEx;
            }
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("success", true);
            result.put("message", "删除成功");
            return result;
        } catch (Exception e) {
            log.error("delete filter config failed", e);
            throw businessError("删除失败", e);
        }
    }

    private synchronized void syncToYaml() {
        String ymlPath = getLagiYmlPath();
        if (ymlPath == null) {
            throw new RuntimeException("无法同步到 YAML：找不到 lagi.yml 文件");
        }

        try {
            File ymlFile = new File(ymlPath);
            if (!ymlFile.exists()) {
                throw new RuntimeException("无法同步到 YAML：文件不存在");
            }
            if (!ymlFile.canWrite()) {
                throw new RuntimeException("无法同步到 YAML：文件没有写权限");
            }

            Path ymlFilePath = Paths.get(ymlPath);
            Path backupPath = Paths.get(ymlPath + ".backup." + System.currentTimeMillis());
            Files.copy(ymlFilePath, backupPath, StandardCopyOption.REPLACE_EXISTING);

            Map<String, Object> yamlMap = readYamlMap(ymlPath);
            List<Map<String, Object>> filtersList = new ArrayList<>();
            for (FilterConfig config : FilterConfigService.cachedList()) {
                filtersList.add(filterConfigToMap(config));
            }

            Map<String, Object> filtersMap = new LinkedHashMap<>();
            Object oldFilters = yamlMap.get("filters");
            if (oldFilters instanceof Map) {
                @SuppressWarnings("unchecked")
                Object enable = ((Map<String, Object>) oldFilters).get("enable");
                filtersMap.put("enable", enable == null ? Boolean.TRUE : enable);
            } else {
                filtersMap.put("enable", Boolean.TRUE);
            }
            filtersMap.put("items", filtersList);
            yamlMap.put("filters", filtersMap);

            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            options.setIndent(2);

            Yaml yaml = new Yaml(options);
            Path tempPath = Files.createTempFile(ymlFilePath.toAbsolutePath().getParent(), ymlFilePath.getFileName().toString(), ".tmp");
            try (OutputStreamWriter writer = new OutputStreamWriter(Files.newOutputStream(tempPath), StandardCharsets.UTF_8)) {
                yaml.dump(yamlMap, writer);
            }
            moveTempFile(tempPath, ymlFilePath);

            log.info("successfully synced filter config to YAML, backup: {}", backupPath);
        } catch (Exception e) {
            log.error("sync filter config to YAML failed", e);
            throw new RuntimeException("同步到 YAML 文件失败: " + e.getMessage(), e);
        }
    }

    private void moveTempFile(Path tempPath, Path targetPath) throws Exception {
        try {
            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Map<String, Object> readYamlMap(String ymlPath) throws Exception {
        String encoding = detectEncoding(ymlPath);
        if (encoding == null) {
            encoding = "UTF-8";
        }
        ObjectMapper mapper = new YAMLMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        try (InputStreamReader reader = new InputStreamReader(
                Files.newInputStream(Paths.get(ymlPath)),
                java.nio.charset.Charset.forName(encoding)
        )) {
            @SuppressWarnings("unchecked")
            Map<String, Object> yamlMap = mapper.readValue(reader, Map.class);
            return yamlMap == null ? new LinkedHashMap<>() : yamlMap;
        }
    }

    private Object extractFilterItems(Object filtersObj) {
        if (filtersObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> filtersMap = (Map<String, Object>) filtersObj;
            return filtersMap.get("items");
        }
        return filtersObj;
    }

    private FilterConfig mapToFilterConfig(Map<String, Object> filterMap) {
        FilterConfig config = new FilterConfig();
        config.setName(asString(filterMap.get("name")));
        config.setRules(asString(filterMap.get("rules")));
        config.setFilterWindowLength(asInt(filterMap.get("filter_window_length")));

        Object groupsObj = filterMap.get("groups");
        if (groupsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> groupsList = (List<Map<String, Object>>) groupsObj;
            List<FilterRule> groups = new ArrayList<>();
            for (Map<String, Object> groupMap : groupsList) {
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
        return config;
    }

    private Map<String, Object> filterConfigToMap(FilterConfig config) {
        Map<String, Object> filterMap = new LinkedHashMap<>();
        filterMap.put("name", config.getName());

        if (config.getGroups() != null && !config.getGroups().isEmpty()) {
            List<Map<String, Object>> groupsList = new ArrayList<>();
            for (FilterRule rule : config.getGroups()) {
                Map<String, Object> groupMap = new LinkedHashMap<>();
                groupMap.put("level", rule.getLevel());
                groupMap.put("rules", rule.getRules());
                if (rule.getMask() != null) {
                    groupMap.put("mask", rule.getMask());
                }
                groupsList.add(groupMap);
            }
            filterMap.put("groups", groupsList);
        }

        if (config.getRules() != null) {
            filterMap.put("rules", config.getRules());
        }

        if (config.getFilterWindowLength() > 0) {
            filterMap.put("filter_window_length", config.getFilterWindowLength());
        }
        return filterMap;
    }

    private String detectEncoding(String filePath) {
        try {
            return ai.utils.EncodingDetector.detectEncoding(filePath);
        } catch (Exception e) {
            log.warn("detect file encoding failed, use UTF-8: {}", filePath, e);
            return "UTF-8";
        }
    }

    private void reloadConfiguration() {
        try {
            reloadFilterUtils();
            log.info("filter configuration reloaded successfully");
        } catch (Exception e) {
            log.error("filter configuration reload failed", e);
            throw new RuntimeException("配置重新加载失败: " + e.getMessage(), e);
        }
    }

    private void reloadFilterUtils() {
        List<FilterConfig> filters = FilterConfigService.cachedList();
        WordRules outputRules = null;
        WordRules inputRules = null;
        int windowLength = -1;
        for (FilterConfig filter : filters) {
            String name = filter.getName();
            if ("sensitive".equals(name)) {
                outputRules = convert2WordRules(filter);
                windowLength = filter.getFilterWindowLength() > 0 ? filter.getFilterWindowLength() : 200;
            } else if ("sensitive_input".equals(name)) {
                inputRules = convert2WordRules(filter);
            } else if ("priority".equals(name)) {
                ai.utils.PriorityWordUtil.reloadWords(convert2List(filter));
            } else if ("continue".equals(name)) {
                ai.utils.ContinueWordUtil.reloadWords(convert2List(filter));
            } else if ("stopping".equals(name)) {
                ai.utils.StoppingWordUtil.reloadWords(convert2List(filter));
            }
        }
        ai.utils.SensitiveWordUtil.reloadRules(outputRules, inputRules, windowLength);
        refreshInMemoryConfiguration(filters);
    }

    private void refreshInMemoryConfiguration(List<FilterConfig> filters) {
        if (ai.config.ContextLoader.configuration == null) {
            return;
        }
        FiltersConfig filtersConfig = ai.config.ContextLoader.configuration.getFilters();
        if (filtersConfig == null) {
            filtersConfig = new FiltersConfig();
            ai.config.ContextLoader.configuration.setFilters(filtersConfig);
        }
        filtersConfig.setItems(filters);
    }

    private void validateFilterConfig(FilterConfig filterConfig) {
        if (filterConfig == null || filterConfig.getName() == null || filterConfig.getName().trim().isEmpty()) {
            throw new RuntimeException("过滤器名称不能为空");
        }
        String name = filterConfig.getName();
        if ("sensitive".equals(name) || "sensitive_input".equals(name)) {
            ai.utils.SensitiveWordUtil.validateRules(convert2WordRules(filterConfig));
        } else if ("priority".equals(name) || "continue".equals(name) || "stopping".equals(name)) {
            for (String rule : convert2List(filterConfig)) {
                Pattern.compile(rule);
            }
        } else {
            throw new RuntimeException("不支持的过滤器类型: " + name);
        }
    }

    private void normalizeFilterConfig(FilterConfig filterConfig) {
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private RRException businessError(String operation, Exception e) {
        String message = unwrapMessage(e);
        return new RRException(operation + (message == null || message.isEmpty() ? "" : ": " + message));
    }

    private String unwrapMessage(Throwable throwable) {
        Throwable current = throwable;
        String message = null;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().trim().isEmpty()) {
                message = current.getMessage().trim();
            }
            current = current.getCause();
        }
        return message;
    }

    private void rollbackSavedFilter(String name, FilterConfig oldConfig) {
        try {
            if (oldConfig == null) {
                if (FilterConfigService.filterConfigCache.containsKey(name)) {
                    FilterConfigService.delete(name);
                }
            } else if (FilterConfigService.filterConfigCache.containsKey(name)) {
                FilterConfigService.update(oldConfig);
            } else {
                FilterConfigService.add(oldConfig);
            }
        } catch (Exception rollbackEx) {
            log.error("rollback filter config failed: {}", name, rollbackEx);
        }
    }

    private FilterConfig copyFilterConfig(FilterConfig source) {
        if (source == null) {
            return null;
        }
        FilterConfig copy = new FilterConfig();
        copy.setName(source.getName());
        copy.setRules(source.getRules());
        copy.setFilterWindowLength(source.getFilterWindowLength());
        if (source.getGroups() != null) {
            List<FilterRule> groups = new ArrayList<>();
            for (FilterRule sourceRule : source.getGroups()) {
                FilterRule rule = new FilterRule();
                rule.setLevel(sourceRule.getLevel());
                rule.setMask(sourceRule.getMask());
                rule.setRules(sourceRule.getRules());
                groups.add(rule);
            }
            copy.setGroups(groups);
        }
        return copy;
    }

    private WordRules convert2WordRules(FilterConfig filter) {
        if (filter == null || filter.getGroups() == null || filter.getGroups().isEmpty()) {
            return null;
        }
        List<WordRule> rules = new ArrayList<>();
        for (FilterRule group : filter.getGroups()) {
            if (group.getRules() == null || group.getRules().trim().isEmpty()) {
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

    private int convertLevel(String level) {
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

    private List<String> convert2List(FilterConfig filterItem) {
        String rules = filterItem == null ? null : filterItem.getRules();
        if (rules == null || rules.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return convert2ListRules(rules);
    }

    private List<String> convert2ListRules(String rules) {
        String escapedCommaPlaceholder = "__LAGI_ESCAPED_COMMA__";
        String s = rules.replace("\\,", escapedCommaPlaceholder);
        List<String> collect = Arrays.stream(s.split(","))
                .map(String::trim)
                .filter(str -> !str.isEmpty())
                .collect(Collectors.toList());
        return collect.stream()
                .map(temp -> temp.replace(escapedCommaPlaceholder, ","))
                .collect(Collectors.toList());
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int asInt(Object value) {
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
}
