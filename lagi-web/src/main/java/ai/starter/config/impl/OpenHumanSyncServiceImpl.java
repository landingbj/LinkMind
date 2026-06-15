package ai.starter.config.impl;

import ai.common.pojo.Backend;
import ai.config.GlobalConfigurations;
import ai.starter.config.util.ConfigUtil;
import ai.utils.YmlLoader;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
public class OpenHumanSyncServiceImpl extends BaseSyncServiceImpl {

    private static final String CONFIG_TOML = "config.toml";
    private static final String ACTIVE_USER_TOML = "active_user.toml";
    private static final String ACTIVE_WORKSPACE_TOML = "active_workspace.toml";
    private static final String AUTH_PROFILES_JSON = "auth-profiles.json";
    private static final String OPENHUMAN_DIR_NAME = ".openhuman";
    private static final String WORKSPACE_DIR_NAME = "workspace";
    private static final String ENV_WORKSPACE = "OPENHUMAN_WORKSPACE";
    private static final String ENV_LINKMIND_API_KEY = "LINKMIND_API_KEY";
    private static final String LINKMIND_API_KEY_PROPERTY = "linkmind.apiKey";
    private static final String OPENHUMAN_LINKMIND_API_KEY_PROPERTY = "openhuman.linkmind.apiKey";
    private static final String PRE_LOGIN_USER_ID = "local";
    private static final String LINKMIND_SLUG = "linkmind";
    private static final String LINKMIND_ID = "p_linkmind_linkmind";
    private static final String LINKMIND_LABEL = "LinkMind";
    private static final String LEGACY_LINKMIND_MODEL = "linkmind-pro";
    private static final String LINKMIND_MODEL = "Alibaba/qwen3.6-plus";
    private static final String LINKMIND_ROUTE = LINKMIND_SLUG + ":" + LINKMIND_MODEL;
    private static final String LINKMIND_PROVIDER_KEY = "provider:" + LINKMIND_SLUG;
    private static final String LINKMIND_PROFILE_ID = LINKMIND_PROVIDER_KEY + ":default";
    private static final String OPENAI_STANDARD_DRIVER = "ai.llm.adapter.impl.OpenAIStandardAdapter";

    private static final Set<String> OPENHUMAN_ABSTRACT_MODELS = new LinkedHashSet<>();

    static {
        OPENHUMAN_ABSTRACT_MODELS.add("chat-v1");
        OPENHUMAN_ABSTRACT_MODELS.add("reasoning-v1");
        OPENHUMAN_ABSTRACT_MODELS.add("reasoning-quick-v1");
        OPENHUMAN_ABSTRACT_MODELS.add("agentic-v1");
        OPENHUMAN_ABSTRACT_MODELS.add("coding-v1");
        OPENHUMAN_ABSTRACT_MODELS.add("summarization-v1");
    }

    private static final String[] EXPORT_ROUTE_FIELDS = {
            "chat_provider",
            "reasoning_provider",
            "agentic_provider",
            "coding_provider"
    };

    private static final String[] EXPORT_BACKGROUND_ROUTE_FIELDS = {
            "memory_provider",
            "heartbeat_provider",
            "learning_provider",
            "subconscious_provider"
    };

    private static final String[] LOAD_ROUTE_FIELDS = {
            "chat_provider",
            "reasoning_provider",
            "agentic_provider",
            "coding_provider",
            "memory_provider",
            "embeddings_provider",
            "heartbeat_provider",
            "learning_provider",
            "subconscious_provider"
    };

    private final String envWorkspace;
    private final String userHome;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenHumanSyncServiceImpl(String basePath) {
        this(basePath, System.getenv(ENV_WORKSPACE), System.getProperty("user.home"));
    }

    OpenHumanSyncServiceImpl(String basePath, String envWorkspace, String userHome) {
        super(basePath);
        this.envWorkspace = envWorkspace;
        this.userHome = userHome;
    }

    @Override
    public boolean check() {
        Path configPath = resolveConfigPath();
        if (configPath == null) {
            return false;
        }
        if (Files.isRegularFile(configPath)) {
            return true;
        }
        Path parent = configPath.getParent();
        return parent != null && Files.isDirectory(parent);
    }

    @Override
    public void export(String path) {
        Path configPath = resolveConfigPath();
        if (configPath == null) {
            log.warn("{}: config path could not be resolved, skipping configuration synchronization", name());
            return;
        }
        try {
            Map<String, Object> config = loadTomlMap(configPath);
            List<Map<String, Object>> providers = getCloudProviders(config);
            Map<String, Object> linkmindProvider = upsertLinkMindProvider(providers, path);
            config.put("cloud_providers", providers);

            String primaryCloud = stringValue(config.get("primary_cloud"));
            if (isBlank(primaryCloud) || pointsToOpenHuman(primaryCloud, providers)) {
                config.put("primary_cloud", stringValue(linkmindProvider.get("id")));
            }

            String defaultModel = stringValue(config.get("default_model"));
            if (shouldReplaceDefaultModel(defaultModel)) {
                config.put("default_model", LINKMIND_MODEL);
            }

            for (String field : EXPORT_ROUTE_FIELDS) {
                String current = stringValue(config.get(field));
                if (shouldReplaceRoute(current, providers)) {
                    config.put(field, LINKMIND_ROUTE);
                }
            }

            for (String field : EXPORT_BACKGROUND_ROUTE_FIELDS) {
                String current = stringValue(config.get(field));
                if (shouldReplaceRoute(current, providers)) {
                    config.put(field, LINKMIND_ROUTE);
                }
            }

            writeToml(configPath, config);
            writeLinkMindAuthProfile(configPath);
            log.info("{}: injected LinkMind provider into {}", name(), configPath);
        } catch (Exception e) {
            log.error("{}: failed to export configuration", name(), e);
        }
    }

    @Override
    public void load(String path) {
        Path configPath = resolveConfigPath();
        if (configPath == null || !Files.isRegularFile(configPath)) {
            log.warn("{}: config file not found, skipping configuration synchronization", name());
            return;
        }
        Path lagiYmlPath = ConfigUtil.getLagiYmlPath();
        if (lagiYmlPath == null || !Files.exists(lagiYmlPath)) {
            log.error("{}: Lagi.yml not found", name());
            return;
        }
        try {
            List<Backend> backends = loadBackends(configPath);
            if (backends.isEmpty()) {
                log.warn("{}: no supported OpenAI-compatible providers found in {}", name(), configPath);
                return;
            }
            GlobalConfigurations globalConfigurations = YmlLoader.loadYaml(lagiYmlPath.toString(), GlobalConfigurations.class);
            ConfigUtil.setConfig(globalConfigurations, backends);
            YmlLoader.writeYaml(lagiYmlPath.toString(), globalConfigurations);
        } catch (Exception e) {
            log.error("{}: failed to load and sync config", name(), e);
        }
    }

    @Override
    public String name() {
        return "OpenHuman";
    }

    Path resolveConfigPath() {
        if (isNotBlank(basePath)) {
            return resolveOverridePath(Paths.get(basePath.trim()));
        }
        if (isNotBlank(envWorkspace)) {
            return resolveOverridePath(Paths.get(envWorkspace.trim()));
        }
        if (isBlank(userHome)) {
            return null;
        }
        return resolveRootPath(Paths.get(userHome, OPENHUMAN_DIR_NAME));
    }

    List<Backend> loadBackends(Path configPath) throws IOException {
        Map<String, Object> config = loadTomlMap(configPath);
        List<Map<String, Object>> providers = getCloudProviders(config);
        Map<String, String> routeModels = collectRouteModels(config);
        Map<String, String> authTokens = loadAuthTokens(configPath.getParent());
        List<Backend> backends = new ArrayList<>();

        for (Map<String, Object> provider : providers) {
            String slug = providerSlug(provider);
            String authStyle = stringValue(provider.get("auth_style"));
            String endpoint = stringValue(provider.get("endpoint"));
            if (!isSupportedProvider(slug, authStyle, endpoint)) {
                continue;
            }
            String model = routeModels.get(slug);
            if (isBlank(model)) {
                model = stringValue(provider.get("default_model"));
            }
            if (isBlank(model)) {
                log.debug("{}: provider {} has no routed model, skipping", name(), slug);
                continue;
            }

            Backend backend = new Backend();
            String backendName = "linkmind-" + name().toLowerCase() + "-" + slug;
            backend.setName(backendName);
            backend.setType(backendName);
            backend.setBackend(backendName);
            backend.setModel(model);
            backend.setEnable(true);
            backend.setStream(true);
            backend.setPriority(100);
            backend.setDriver(OPENAI_STANDARD_DRIVER);
            backend.setProtocol("completion");
            backend.setEndpoint(toCompletionApiAddress(endpoint));
            backend.setApiAddress(toCompletionApiAddress(endpoint));
            if ("bearer".equals(normalize(authStyle))) {
                backend.setApiKey(authTokens.get(slug));
            }
            backends.add(backend);
        }
        return backends;
    }

    private Path resolveOverridePath(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (Files.isRegularFile(normalized) || CONFIG_TOML.equals(fileName(normalized))) {
            return normalized;
        }
        if (!looksLikeOpenHumanRoot(normalized) && Files.isRegularFile(normalized.resolve(CONFIG_TOML))) {
            return normalized.resolve(CONFIG_TOML).toAbsolutePath().normalize();
        }
        if (looksLikeOpenHumanRoot(normalized)) {
            return resolveRootPath(normalized);
        }
        if (WORKSPACE_DIR_NAME.equals(fileName(normalized))) {
            Path parent = normalized.getParent();
            if (parent != null) {
                return parent.resolve(OPENHUMAN_DIR_NAME).resolve(CONFIG_TOML).toAbsolutePath().normalize();
            }
        }
        return normalized.resolve(CONFIG_TOML);
    }

    private boolean looksLikeOpenHumanRoot(Path path) {
        return OPENHUMAN_DIR_NAME.equals(fileName(path))
                || Files.exists(path.resolve(ACTIVE_USER_TOML))
                || Files.isDirectory(path.resolve("users"));
    }

    private Path resolveRootPath(Path root) {
        String userId = readActiveUserId(root);
        if (isBlank(userId)) {
            Path activeWorkspaceConfig = readActiveWorkspaceConfigPath(root);
            if (activeWorkspaceConfig != null) {
                return activeWorkspaceConfig;
            }
            userId = PRE_LOGIN_USER_ID;
        }
        return root.resolve("users").resolve(userId).resolve(CONFIG_TOML).toAbsolutePath().normalize();
    }

    private Path readActiveWorkspaceConfigPath(Path root) {
        Path activeWorkspacePath = root.resolve(ACTIVE_WORKSPACE_TOML);
        if (!Files.isRegularFile(activeWorkspacePath)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(activeWorkspacePath, StandardCharsets.UTF_8)) {
            String configDir = new Toml().read(reader).getString("config_dir");
            if (isBlank(configDir)) {
                return null;
            }
            Path path = Paths.get(configDir.trim());
            if (!path.isAbsolute()) {
                path = root.resolve(path);
            }
            return path.resolve(CONFIG_TOML).toAbsolutePath().normalize();
        } catch (Exception e) {
            log.debug("{}: failed to read active workspace from {}", name(), activeWorkspacePath, e);
            return null;
        }
    }

    private String readActiveUserId(Path root) {
        Path activeUserPath = root.resolve(ACTIVE_USER_TOML);
        if (!Files.isRegularFile(activeUserPath)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(activeUserPath, StandardCharsets.UTF_8)) {
            String userId = new Toml().read(reader).getString("user_id");
            return isSafeUserId(userId) ? userId.trim() : null;
        } catch (Exception e) {
            log.debug("{}: failed to read active user from {}", name(), activeUserPath, e);
            return null;
        }
    }

    private boolean isSafeUserId(String userId) {
        if (isBlank(userId)) {
            return false;
        }
        String trimmed = userId.trim();
        if (".".equals(trimmed) || "..".equals(trimmed)) {
            return false;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (ch == '/' || ch == '\\' || ch == ':' || Character.isISOControl(ch)) {
                return false;
            }
            boolean allowed = Character.isLetterOrDigit(ch) || ch == '.' || ch == '_' || ch == '@' || ch == '-';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Object> loadTomlMap(Path configPath) throws IOException {
        if (configPath == null || !Files.isRegularFile(configPath)) {
            return new LinkedHashMap<>();
        }
        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            return mutableMap(new Toml().read(reader).toMap());
        }
    }

    private void writeToml(Path configPath, Map<String, Object> config) throws IOException {
        Path parent = configPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tempPath = parent == null
                ? Files.createTempFile("openhuman_config_", ".toml")
                : Files.createTempFile(parent, "openhuman_config_", ".toml");
        try (Writer writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
            new TomlWriter().write(config, writer);
        }
        Files.move(tempPath, configPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private List<Map<String, Object>> getCloudProviders(Map<String, Object> config) {
        Object value = config.get("cloud_providers");
        List<Map<String, Object>> providers = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item instanceof Map) {
                    providers.add(mutableMap((Map<?, ?>) item));
                }
            }
        }
        return providers;
    }

    private Map<String, Object> upsertLinkMindProvider(List<Map<String, Object>> providers, String endpoint) {
        Map<String, Object> target = null;
        List<Map<String, Object>> duplicates = new ArrayList<>();
        for (Map<String, Object> provider : providers) {
            if (LINKMIND_SLUG.equals(providerSlug(provider))) {
                if (target == null) {
                    target = provider;
                } else {
                    duplicates.add(provider);
                }
            }
        }
        providers.removeAll(duplicates);
        if (target == null) {
            target = new LinkedHashMap<>();
            providers.add(target);
        }
        if (isBlank(stringValue(target.get("id")))) {
            target.put("id", LINKMIND_ID);
        }
        target.put("slug", LINKMIND_SLUG);
        target.put("label", LINKMIND_LABEL);
        target.put("endpoint", endpoint);
        target.put("auth_style", "bearer");
        target.put("default_model", LINKMIND_MODEL);
        return target;
    }

    private void writeLinkMindAuthProfile(Path configPath) throws IOException {
        String apiKey = resolveLinkMindApiKey();
        if (isBlank(apiKey)) {
            log.warn("{}: LinkMind API key is not configured; keeping existing auth profile unchanged", name());
            return;
        }
        Path configDir = configPath == null ? null : configPath.getParent();
        if (configDir == null) {
            log.warn("{}: config directory is not available; cannot update auth profile", name());
            return;
        }
        Files.createDirectories(configDir);
        Path authPath = configDir.resolve(AUTH_PROFILES_JSON);
        Map<String, Object> root = readAuthProfilesRoot(authPath);
        root.put("schema_version", numberOrDefault(root.get("schema_version"), 1));
        root.put("updated_at", isoNow());

        Map<String, Object> activeProfiles = objectMap(root.get("active_profiles"));
        Map<String, Object> profiles = objectMap(root.get("profiles"));
        activeProfiles.put(LINKMIND_PROVIDER_KEY, LINKMIND_PROFILE_ID);

        Map<String, Object> profile = objectMap(profiles.get(LINKMIND_PROFILE_ID));
        String createdAt = stringValue(profile.get("created_at"));
        if (isBlank(createdAt)) {
            createdAt = isoNow();
        }
        profile.put("id", LINKMIND_PROFILE_ID);
        profile.put("provider", LINKMIND_PROVIDER_KEY);
        profile.put("profile_name", "default");
        profile.put("kind", "token");
        profile.put("token", apiKey.trim());
        profile.put("created_at", createdAt);
        profile.put("updated_at", isoNow());
        if (!(profile.get("metadata") instanceof Map)) {
            profile.put("metadata", new LinkedHashMap<String, Object>());
        }
        profiles.put(LINKMIND_PROFILE_ID, profile);
        root.put("active_profiles", activeProfiles);
        root.put("profiles", profiles);

        writeJsonAtomic(authPath, root);
        writeDevKeychain(configPath, apiKey.trim());
    }

    private Map<String, Object> readAuthProfilesRoot(Path authPath) throws IOException {
        if (!Files.isRegularFile(authPath)) {
            return new LinkedHashMap<>();
        }
        try {
            return mutableMap(objectMapper.readValue(authPath.toFile(), new TypeReference<Map<String, Object>>() {}));
        } catch (Exception e) {
            Path backupPath = authPath.resolveSibling(AUTH_PROFILES_JSON + ".bak");
            Files.copy(authPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            log.warn("{}: invalid auth profiles JSON backed up to {}", name(), backupPath);
            return new LinkedHashMap<>();
        }
    }

    private void writeJsonAtomic(Path path, Map<String, Object> root) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tempPath = parent == null
                ? Files.createTempFile("openhuman_auth_profiles_", ".json")
                : Files.createTempFile(parent, "openhuman_auth_profiles_", ".json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempPath.toFile(), root);
        Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
    }

    private void writeDevKeychain(Path configPath, String apiKey) throws IOException {
        Path userDir = configPath == null ? null : configPath.getParent();
        Path usersDir = userDir == null ? null : userDir.getParent();
        Path rootDir = usersDir == null ? null : usersDir.getParent();
        if (rootDir == null || !"users".equals(fileName(usersDir)) || isBlank(fileName(userDir))) {
            return;
        }
        Path keychainPath = rootDir.resolve("dev-keychain.json");
        if (!Files.exists(keychainPath) && !OPENHUMAN_DIR_NAME.equals(fileName(rootDir))) {
            return;
        }
        Map<String, Object> keychain = readJsonObject(keychainPath);
        Map<String, Object> tokenPayload = new LinkedHashMap<>();
        tokenPayload.put("access_token", null);
        tokenPayload.put("id_token", null);
        tokenPayload.put("refresh_token", null);
        tokenPayload.put("token", apiKey);
        String keychainKey = fileName(userDir) + ":auth:" + LINKMIND_PROFILE_ID;
        keychain.put(keychainKey, objectMapper.writeValueAsString(tokenPayload));
        writeJsonAtomic(keychainPath, keychain);
    }

    private Map<String, Object> readJsonObject(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return new LinkedHashMap<>();
        }
        try {
            return mutableMap(objectMapper.readValue(path.toFile(), new TypeReference<Map<String, Object>>() {}));
        } catch (Exception e) {
            Path backupPath = path.resolveSibling(fileName(path) + ".bak");
            Files.copy(path, backupPath, StandardCopyOption.REPLACE_EXISTING);
            log.warn("{}: invalid JSON backed up to {}", name(), backupPath);
            return new LinkedHashMap<>();
        }
    }

    private Object numberOrDefault(Object value, int defaultValue) {
        return value instanceof Number ? value : defaultValue;
    }

    private String resolveLinkMindApiKey() {
        String apiKey = firstNotBlank(
                System.getProperty(OPENHUMAN_LINKMIND_API_KEY_PROPERTY),
                System.getProperty(LINKMIND_API_KEY_PROPERTY),
                System.getenv(ENV_LINKMIND_API_KEY)
        );
        return apiKey == null ? null : apiKey.trim();
    }

    private String firstNotBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String isoNow() {
        return java.time.Instant.now().toString();
    }

    private boolean shouldReplaceDefaultModel(String model) {
        if (isBlank(model)) {
            return true;
        }
        String normalized = normalize(model);
        return OPENHUMAN_ABSTRACT_MODELS.contains(normalized)
                || LEGACY_LINKMIND_MODEL.equals(normalized);
    }

    private boolean shouldReplaceRoute(String route, List<Map<String, Object>> providers) {
        if (isBlank(route)) {
            return true;
        }
        String normalized = normalize(route);
        if ("cloud".equals(normalized) || "primary".equals(normalized)
                || "default".equals(normalized) || "openhuman".equals(normalized)) {
            return true;
        }
        if (OPENHUMAN_ABSTRACT_MODELS.contains(normalized)) {
            return true;
        }
        if ((LINKMIND_SLUG + ":" + LEGACY_LINKMIND_MODEL).equals(normalized)) {
            return true;
        }
        if (normalized.startsWith("openhuman:")) {
            return true;
        }
        int colonIdx = normalized.indexOf(':');
        if (colonIdx > 0) {
            String slug = normalized.substring(0, colonIdx);
            return pointsToOpenHuman(slug, providers);
        }
        return false;
    }

    private boolean pointsToOpenHuman(String idOrSlug, List<Map<String, Object>> providers) {
        if (isBlank(idOrSlug)) {
            return true;
        }
        String value = normalize(idOrSlug);
        if ("openhuman".equals(value) || "cloud".equals(value) || "primary".equals(value)) {
            return true;
        }
        for (Map<String, Object> provider : providers) {
            String id = normalize(stringValue(provider.get("id")));
            String slug = providerSlug(provider);
            if (value.equals(id) || value.equals(slug)) {
                return isOpenHumanProvider(provider);
            }
        }
        return false;
    }

    private boolean isOpenHumanProvider(Map<String, Object> provider) {
        String slug = providerSlug(provider);
        String authStyle = normalize(stringValue(provider.get("auth_style")));
        String endpoint = normalize(stringValue(provider.get("endpoint")));
        return "openhuman".equals(slug)
                || "openhuman_jwt".equals(authStyle)
                || isOpenHumanEndpoint(endpoint);
    }

    private boolean isSupportedProvider(String slug, String authStyle, String endpoint) {
        String normalizedSlug = normalize(slug);
        String normalizedAuthStyle = normalize(authStyle);
        if (isBlank(normalizedSlug) || LINKMIND_SLUG.equals(normalizedSlug) || "openhuman".equals(normalizedSlug)) {
            return false;
        }
        if ("anthropic".equals(normalizedSlug) || "anthropic".equals(normalizedAuthStyle)
                || "openhuman_jwt".equals(normalizedAuthStyle)) {
            return false;
        }
        if (!("bearer".equals(normalizedAuthStyle) || "none".equals(normalizedAuthStyle) || isBlank(normalizedAuthStyle))) {
            return false;
        }
        return isNotBlank(endpoint) && !isOpenHumanEndpoint(normalize(endpoint));
    }

    private boolean isOpenHumanEndpoint(String endpoint) {
        return endpoint.contains("api.openhuman.ai")
                || endpoint.contains("api.tinyhumans.ai")
                || endpoint.contains("openhuman.ai")
                || endpoint.contains("tinyhumans.ai");
    }

    private Map<String, String> collectRouteModels(Map<String, Object> config) {
        Map<String, String> routeModels = new LinkedHashMap<>();
        for (String field : LOAD_ROUTE_FIELDS) {
            String route = stringValue(config.get(field));
            if (isBlank(route)) {
                continue;
            }
            int colonIdx = route.indexOf(':');
            if (colonIdx <= 0 || colonIdx == route.length() - 1) {
                continue;
            }
            String slug = normalize(route.substring(0, colonIdx));
            String model = route.substring(colonIdx + 1).trim();
            int tempIdx = model.lastIndexOf('@');
            if (tempIdx > 0) {
                model = model.substring(0, tempIdx).trim();
            }
            if (isNotBlank(slug) && isNotBlank(model) && !routeModels.containsKey(slug)) {
                routeModels.put(slug, model);
            }
        }
        return routeModels;
    }

    private Map<String, String> loadAuthTokens(Path configDir) {
        if (configDir == null) {
            return Collections.emptyMap();
        }
        Path authPath = configDir.resolve(AUTH_PROFILES_JSON);
        if (!Files.isRegularFile(authPath)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> root = objectMapper.readValue(authPath.toFile(), new TypeReference<Map<String, Object>>() {});
            Map<String, Object> activeProfiles = objectMap(root.get("active_profiles"));
            Map<String, Object> profiles = objectMap(root.get("profiles"));
            Map<String, String> result = new LinkedHashMap<>();
            Set<String> providerKeys = new LinkedHashSet<>();
            for (Object value : activeProfiles.keySet()) {
                if (value != null) {
                    providerKeys.add(value.toString());
                }
            }
            for (Object value : profiles.values()) {
                Map<String, Object> profile = objectMap(value);
                String provider = stringValue(profile.get("provider"));
                if (isNotBlank(provider)) {
                    providerKeys.add(provider);
                }
            }
            for (String providerKey : providerKeys) {
                String slug = normalize(providerKey.startsWith("provider:")
                        ? providerKey.substring("provider:".length())
                        : providerKey);
                String token = findToken(providerKey, activeProfiles, profiles);
                if (isNotBlank(slug) && isNotBlank(token)) {
                    result.put(slug, token);
                }
            }
            return result;
        } catch (Exception e) {
            log.debug("{}: failed to read auth profiles from {}", name(), authPath, e);
            return Collections.emptyMap();
        }
    }

    private String findToken(String providerKey, Map<String, Object> activeProfiles, Map<String, Object> profiles) {
        String activeProfileId = stringValue(activeProfiles.get(providerKey));
        if (isNotBlank(activeProfileId)) {
            String token = tokenFromProfile(objectMap(profiles.get(activeProfileId)));
            if (isNotBlank(token)) {
                return token;
            }
        }
        for (Object value : profiles.values()) {
            Map<String, Object> profile = objectMap(value);
            if (providerKey.equals(stringValue(profile.get("provider")))) {
                String token = tokenFromProfile(profile);
                if (isNotBlank(token)) {
                    return token;
                }
            }
        }
        return null;
    }

    private String tokenFromProfile(Map<String, Object> profile) {
        if (!"token".equals(normalize(stringValue(profile.get("kind"))))) {
            return null;
        }
        return stringValue(profile.get("token"));
    }

    private Map<String, Object> objectMap(Object value) {
        if (value instanceof Map) {
            return mutableMap((Map<?, ?>) value);
        }
        return new LinkedHashMap<>();
    }

    private Map<String, Object> mutableMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            result.put(entry.getKey().toString(), mutableValue(entry.getValue()));
        }
        return result;
    }

    private Object mutableValue(Object value) {
        if (value instanceof Map) {
            return mutableMap((Map<?, ?>) value);
        }
        if (value instanceof List) {
            List<Object> result = new ArrayList<>();
            for (Object item : (List<?>) value) {
                result.add(mutableValue(item));
            }
            return result;
        }
        return value;
    }

    private String providerSlug(Map<String, Object> provider) {
        String slug = stringValue(provider.get("slug"));
        if (isBlank(slug)) {
            slug = stringValue(provider.get("type"));
        }
        return normalize(slug);
    }

    private String toCompletionApiAddress(String apiBase) {
        if (isBlank(apiBase)) {
            return null;
        }
        String base = apiBase.trim();
        if (base.endsWith("/chat/completions")) {
            return base;
        }
        if (base.endsWith("/")) {
            return base + "chat/completions";
        }
        return base + "/chat/completions";
    }

    private String fileName(Path path) {
        return path == null || path.getFileName() == null ? "" : path.getFileName().toString();
    }

    private String stringValue(Object value) {
        return value == null ? null : Objects.toString(value, null);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isNotBlank(String value) {
        return !isBlank(value);
    }
}
