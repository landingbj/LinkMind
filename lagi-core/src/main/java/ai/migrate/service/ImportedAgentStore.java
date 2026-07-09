package ai.migrate.service;

import ai.config.pojo.AgentConfig;
import ai.manager.AgentManager;
import ai.migrate.pojo.HistoryRecord;
import ai.migrate.pojo.ImportedAgentProfile;
import ai.utils.YmlLoader;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ImportedAgentStore {
    public static final String IMPORT_ROOT = "data/imports";
    public static final String AGENTS_DIR = IMPORT_ROOT + "/agents";
    public static final String CATALOG_PATH = IMPORT_ROOT + "/agents.yml";
    public static final String IMPORTED_AGENT_DRIVER = "ai.migrate.agent.ImportedPromptAgent";
    public static final String DEFAULT_WRONG_CASE = "抱歉，我暂时无法处理这个请求";

    private static final Object LOCK = new Object();
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final YAMLMapper YAML_MAPPER = new YAMLMapper();
    private static volatile long lastLoadedAt = -1L;

    static {
        YAML_MAPPER.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        YAML_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        YAML_MAPPER.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
    }

    private ImportedAgentStore() {
    }

    public static Path importRoot() {
        return Paths.get(IMPORT_ROOT);
    }

    public static Path agentsRoot() {
        return Paths.get(AGENTS_DIR);
    }

    public static Path catalogPath() {
        return Paths.get(CATALOG_PATH);
    }

    public static Path agentDir(String agentId) {
        return agentsRoot().resolve(agentId).normalize();
    }

    public static Path profilePath(String agentId) {
        return agentDir(agentId).resolve("agent.yaml").normalize();
    }

    public static Path resolveRelativeToProfile(AgentConfig config, String relativePath) {
        Path profile = Paths.get(config.getEndpoint()).normalize();
        Path base = profile.getParent();
        if (base == null) {
            base = Paths.get(".");
        }
        return base.resolve(relativePath).normalize();
    }

    public static ImportedAgentProfile loadProfile(AgentConfig config) {
        return YmlLoader.loadYaml(config.getEndpoint(), ImportedAgentProfile.class);
    }

    public static String readSystemPrompt(AgentConfig config, ImportedAgentProfile profile) {
        String systemPath = profile.getSystemPath() == null ? "system.md" : profile.getSystemPath();
        Path path = resolveRelativeToProfile(config, systemPath);
        if (!Files.exists(path)) {
            return "";
        }
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("读取导入智能体 system 失败: {}", path, e);
            return "";
        }
    }

    public static List<HistoryRecord> readLastHistory(AgentConfig config, ImportedAgentProfile profile) {
        String historyPath = profile.getHistoryPath() == null ? "history.jsonl" : profile.getHistoryPath();
        Path path = resolveRelativeToProfile(config, historyPath);
        if (!Files.exists(path)) {
            return Collections.emptyList();
        }
        List<HistoryRecord> all = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    all.add(JSON_MAPPER.readValue(trimmed, HistoryRecord.class));
                } catch (Exception ignored) {
                    log.warn("跳过无法解析的 history.jsonl 行: {}", path);
                }
            }
        } catch (IOException e) {
            log.warn("读取导入智能体 history 失败: {}", path, e);
            return Collections.emptyList();
        }
        return all;
    }

    public static void appendRuntimeHistory(AgentConfig config,
                                            ImportedAgentProfile profile,
                                            String sessionId,
                                            String userContent,
                                            String assistantContent) {
        if (isBlank(userContent) || isBlank(assistantContent)) {
            return;
        }
        String historyPath = profile.getHistoryPath() == null ? "history.jsonl" : profile.getHistoryPath();
        Path path = resolveRelativeToProfile(config, historyPath);
        synchronized (LOCK) {
            try {
                Path parent = path.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                int nextIndex = countLines(path) + 1;
                try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)) {
                    writer.write(JSON_MAPPER.writeValueAsString(buildRecord(profile.getAgentId(), sessionId, "user",
                            userContent, "runtime", nextIndex)));
                    writer.newLine();
                    writer.write(JSON_MAPPER.writeValueAsString(buildRecord(profile.getAgentId(), sessionId, "assistant",
                            assistantContent, "runtime", nextIndex + 1)));
                    writer.newLine();
                }
            } catch (IOException e) {
                log.warn("追加导入智能体 history 失败: {}", path, e);
            }
        }
    }

    public static HistoryRecord buildRecord(String agentId,
                                            String sessionId,
                                            String role,
                                            String content,
                                            String source,
                                            int index) {
        HistoryRecord record = new HistoryRecord();
        record.setAgentId(agentId);
        record.setSessionId(sessionId);
        record.setRole(role);
        record.setContent(content);
        record.setSource(source);
        record.setCreatedAt(OffsetDateTime.now().toString());
        record.setIndex(index);
        return record;
    }

    public static List<AgentConfig> loadExternalAgentConfigs() {
        Path catalog = catalogPath();
        if (!Files.exists(catalog)) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> map = YAML_MAPPER.readValue(catalog.toFile(), new TypeReference<Map<String, Object>>() {});
            Object agents = map.get("agents");
            if (agents == null) {
                return Collections.emptyList();
            }
            return YAML_MAPPER.convertValue(agents, new TypeReference<List<AgentConfig>>() {});
        } catch (IOException e) {
            log.warn("读取导入智能体清单失败: {}", catalog, e);
            return Collections.emptyList();
        }
    }

    public static void writeExternalAgentConfigs(List<AgentConfig> configs) throws IOException {
        Path catalog = catalogPath();
        Path parent = catalog.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("agents", configs == null ? Collections.emptyList() : configs);
        YAML_MAPPER.writeValue(catalog.toFile(), map);
    }

    public static AgentConfig buildImportedAgentConfig(String agentId) {
        AgentConfig config = new AgentConfig();
        config.setEnable(true);
        config.setName(agentId);
        config.setDriver(IMPORTED_AGENT_DRIVER);
        config.setEndpoint(profilePath(agentId).toString().replace('\\', '/'));
        config.setWrongCase(DEFAULT_WRONG_CASE);
        return config;
    }

    public static void upsertExternalAgentConfig(AgentConfig newConfig) throws IOException {
        synchronized (LOCK) {
            List<AgentConfig> configs = new ArrayList<>(loadExternalAgentConfigs());
            boolean replaced = false;
            for (int i = 0; i < configs.size(); i++) {
                AgentConfig config = configs.get(i);
                if (config != null && newConfig.getName().equals(config.getName())) {
                    configs.set(i, newConfig);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                configs.add(newConfig);
            }
            writeExternalAgentConfigs(configs);
        }
    }

    public static void registerImportedAgent(AgentConfig config) {
        if (config == null || Boolean.FALSE.equals(config.getEnable()) || isBlank(config.getName())) {
            return;
        }
        AgentManager.getInstance().register(Collections.singletonList(config));
    }

    public static void ensureExternalAgentsRegistered() {
        Path catalog = catalogPath();
        long modified = -1L;
        try {
            if (Files.exists(catalog)) {
                modified = Files.getLastModifiedTime(catalog).toMillis();
            }
        } catch (IOException ignored) {
        }
        if (modified == lastLoadedAt) {
            return;
        }
        synchronized (LOCK) {
            if (modified == lastLoadedAt) {
                return;
            }
            List<AgentConfig> configs = loadExternalAgentConfigs();
            if (!configs.isEmpty()) {
                AgentManager.getInstance().register(configs);
            }
            lastLoadedAt = modified;
        }
    }

    public static Map<String, ImportedAgentProfile> loadImportedProfilesByAgentId() {
        Map<String, ImportedAgentProfile> result = new HashMap<>();
        for (AgentConfig config : loadExternalAgentConfigs()) {
            if (config == null || isBlank(config.getName()) || isBlank(config.getEndpoint())) {
                continue;
            }
            ImportedAgentProfile profile = YmlLoader.loadYaml(config.getEndpoint(), ImportedAgentProfile.class);
            if (profile != null) {
                result.put(config.getName(), profile);
            }
        }
        return result;
    }

    private static int countLines(Path path) throws IOException {
        if (!Files.exists(path)) {
            return 0;
        }
        int count = 0;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            while (reader.readLine() != null) {
                count++;
            }
        }
        return count;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
