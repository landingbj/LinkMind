package ai.starter.config.impl;

import ai.starter.InstallerUtil;
import ai.utils.YmlLoader;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moandjiezana.toml.Toml;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenHumanSyncServiceImplTest {

    @TempDir
    Path tempDir;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private String previousConfigProperty;
    private String previousLinkMindApiKeyProperty;
    private String previousOpenHumanApiKeyProperty;

    @BeforeEach
    void rememberConfigProperty() {
        previousConfigProperty = System.getProperty(InstallerUtil.CONFIG_FILE_PROPERTY);
        previousLinkMindApiKeyProperty = System.getProperty(InstallerUtil.LINKMIND_API_KEY_PROPERTY);
        previousOpenHumanApiKeyProperty = System.getProperty("openhuman.linkmind.apiKey");
        System.clearProperty(InstallerUtil.LINKMIND_API_KEY_PROPERTY);
        System.clearProperty("openhuman.linkmind.apiKey");
    }

    @AfterEach
    void restoreConfigProperty() {
        if (previousConfigProperty == null) {
            System.clearProperty(InstallerUtil.CONFIG_FILE_PROPERTY);
        } else {
            System.setProperty(InstallerUtil.CONFIG_FILE_PROPERTY, previousConfigProperty);
        }
        if (previousLinkMindApiKeyProperty == null) {
            System.clearProperty(InstallerUtil.LINKMIND_API_KEY_PROPERTY);
        } else {
            System.setProperty(InstallerUtil.LINKMIND_API_KEY_PROPERTY, previousLinkMindApiKeyProperty);
        }
        if (previousOpenHumanApiKeyProperty == null) {
            System.clearProperty("openhuman.linkmind.apiKey");
        } else {
            System.setProperty("openhuman.linkmind.apiKey", previousOpenHumanApiKeyProperty);
        }
    }

    @Test
    void resolvesDefaultActiveLocalEnvAndExplicitPaths() throws Exception {
        Path home = tempDir.resolve("home");
        Path root = home.resolve(".openhuman");
        Files.createDirectories(root.resolve("users").resolve("user-123"));
        Files.write(root.resolve("active_user.toml"), "user_id = \"user-123\"\n".getBytes(StandardCharsets.UTF_8));

        OpenHumanSyncServiceImpl activeUserService = new OpenHumanSyncServiceImpl("", null, home.toString());
        assertEquals(root.resolve("users").resolve("user-123").resolve("config.toml").toAbsolutePath().normalize(),
                activeUserService.resolveConfigPath());

        Files.delete(root.resolve("active_user.toml"));
        OpenHumanSyncServiceImpl localUserService = new OpenHumanSyncServiceImpl("", null, home.toString());
        assertEquals(root.resolve("users").resolve("local").resolve("config.toml").toAbsolutePath().normalize(),
                localUserService.resolveConfigPath());

        Files.write(root.resolve("active_workspace.toml"), "config_dir = \"custom-config\"\n".getBytes(StandardCharsets.UTF_8));
        OpenHumanSyncServiceImpl activeWorkspaceService = new OpenHumanSyncServiceImpl("", null, home.toString());
        assertEquals(root.resolve("custom-config").resolve("config.toml").toAbsolutePath().normalize(),
                activeWorkspaceService.resolveConfigPath());
        Files.delete(root.resolve("active_workspace.toml"));

        Path envWorkspace = tempDir.resolve("workspace");
        Files.createDirectories(envWorkspace);
        OpenHumanSyncServiceImpl envService = new OpenHumanSyncServiceImpl("", envWorkspace.toString(), home.toString());
        assertEquals(tempDir.resolve(".openhuman").resolve("config.toml").toAbsolutePath().normalize(),
                envService.resolveConfigPath());

        Path envConfigDir = tempDir.resolve("workspace-config");
        Files.createDirectories(envConfigDir);
        OpenHumanSyncServiceImpl envConfigDirService = new OpenHumanSyncServiceImpl("", envConfigDir.toString(), home.toString());
        assertEquals(envConfigDir.resolve("config.toml").toAbsolutePath().normalize(),
                envConfigDirService.resolveConfigPath());

        Path explicitConfig = tempDir.resolve("custom").resolve("config.toml");
        OpenHumanSyncServiceImpl explicitService = new OpenHumanSyncServiceImpl(explicitConfig.toString(), null, home.toString());
        assertEquals(explicitConfig.toAbsolutePath().normalize(), explicitService.resolveConfigPath());
    }

    @Test
    void exportCreatesConfigInExistingDirectory() throws Exception {
        Path configDir = tempDir.resolve("fresh-openhuman");
        Files.createDirectories(configDir);

        OpenHumanSyncServiceImpl service = new OpenHumanSyncServiceImpl(configDir.toString(), null, null);
        assertTrue(service.check());
        service.export("http://127.0.0.1:18080/v1");

        Path configPath = configDir.resolve("config.toml");
        assertTrue(Files.exists(configPath));
        Toml toml = new Toml().read(configPath.toFile());
        assertEquals("Alibaba/qwen3.6-plus", toml.getString("default_model"));
        assertEquals("linkmind:Alibaba/qwen3.6-plus", toml.getString("chat_provider"));
        assertEquals("linkmind:Alibaba/qwen3.6-plus", toml.getString("reasoning_provider"));
        assertEquals("linkmind:Alibaba/qwen3.6-plus", toml.getString("memory_provider"));
        assertEquals("linkmind:Alibaba/qwen3.6-plus", toml.getString("heartbeat_provider"));
        assertEquals("linkmind:Alibaba/qwen3.6-plus", toml.getString("learning_provider"));
        assertEquals("linkmind:Alibaba/qwen3.6-plus", toml.getString("subconscious_provider"));
        assertEquals("p_linkmind_linkmind", toml.getString("primary_cloud"));
        List<Toml> providers = toml.getTables("cloud_providers");
        assertEquals(1, providers.size());
        assertEquals("linkmind", providers.get(0).getString("slug"));
        assertEquals("bearer", providers.get(0).getString("auth_style"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void exportWritesLinkMindAuthProfileWhenApiKeyIsConfigured() throws Exception {
        System.setProperty(InstallerUtil.LINKMIND_API_KEY_PROPERTY, "sk-test-linkmind");
        Path configDir = tempDir.resolve("openhuman-auth");
        Files.createDirectories(configDir);
        Files.write(configDir.resolve("auth-profiles.json"), (
                "{\n" +
                        "  \"schema_version\": 1,\n" +
                        "  \"active_profiles\": {\"app-session\": \"app-session:default\"},\n" +
                        "  \"profiles\": {\n" +
                        "    \"app-session:default\": {\n" +
                        "      \"id\": \"app-session:default\",\n" +
                        "      \"provider\": \"app-session\",\n" +
                        "      \"profile_name\": \"default\",\n" +
                        "      \"kind\": \"token\"\n" +
                        "    }\n" +
                        "  }\n" +
                        "}\n"
        ).getBytes(StandardCharsets.UTF_8));

        OpenHumanSyncServiceImpl service = new OpenHumanSyncServiceImpl(configDir.toString(), null, null);
        service.export("http://127.0.0.1:18080/v1");
        service.export("http://127.0.0.1:18080/v1");

        Map<String, Object> root = OBJECT_MAPPER.readValue(
                configDir.resolve("auth-profiles.json").toFile(),
                new TypeReference<Map<String, Object>>() {}
        );
        Map<String, Object> activeProfiles = (Map<String, Object>) root.get("active_profiles");
        Map<String, Object> profiles = (Map<String, Object>) root.get("profiles");
        assertEquals("app-session:default", activeProfiles.get("app-session"));
        assertEquals("provider:linkmind:default", activeProfiles.get("provider:linkmind"));
        assertEquals(2, profiles.size());

        Map<String, Object> profile = (Map<String, Object>) profiles.get("provider:linkmind:default");
        assertEquals("provider:linkmind:default", profile.get("id"));
        assertEquals("provider:linkmind", profile.get("provider"));
        assertEquals("default", profile.get("profile_name"));
        assertEquals("token", profile.get("kind"));
        assertEquals("sk-test-linkmind", profile.get("token"));
        assertNotNull(profile.get("created_at"));
        assertNotNull(profile.get("updated_at"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void exportWritesLocalDevKeychainForOpenHumanUserConfig() throws Exception {
        System.setProperty(InstallerUtil.LINKMIND_API_KEY_PROPERTY, "sk-test-linkmind");
        Path root = tempDir.resolve(".openhuman");
        Path configPath = root.resolve("users").resolve("local-laptop-test").resolve("config.toml");
        Files.createDirectories(configPath.getParent());
        Files.write(root.resolve("dev-keychain.json"), "{}\n".getBytes(StandardCharsets.UTF_8));

        OpenHumanSyncServiceImpl service = new OpenHumanSyncServiceImpl(configPath.toString(), null, null);
        service.export("http://127.0.0.1:18080/v1");

        Map<String, Object> keychain = OBJECT_MAPPER.readValue(
                root.resolve("dev-keychain.json").toFile(),
                new TypeReference<Map<String, Object>>() {}
        );
        String keychainValue = (String) keychain.get("local-laptop-test:auth:provider:linkmind:default");
        assertNotNull(keychainValue);
        Map<String, Object> tokenPayload = OBJECT_MAPPER.readValue(
                keychainValue,
                new TypeReference<Map<String, Object>>() {}
        );
        assertEquals("sk-test-linkmind", tokenPayload.get("token"));
        assertTrue(tokenPayload.containsKey("access_token"));
        assertTrue(tokenPayload.containsKey("refresh_token"));
        assertTrue(tokenPayload.containsKey("id_token"));
    }

    @Test
    void exportInjectsLinkMindProviderIdempotentlyAndPreservesCustomRoutes() throws Exception {
        Path configPath = tempDir.resolve("config.toml");
        Files.write(configPath, (
                "default_model = \"chat-v1\"\n" +
                        "chat_provider = \"openhuman\"\n" +
                        "reasoning_provider = \"cloud\"\n" +
                        "agentic_provider = \"\"\n" +
                        "coding_provider = \"openai:gpt-4o\"\n" +
                        "memory_provider = \"summarization-v1\"\n" +
                        "heartbeat_provider = \"openhuman\"\n" +
                        "learning_provider = \"\"\n" +
                        "subconscious_provider = \"openai:gpt-4o\"\n" +
                        "primary_cloud = \"p_openhuman\"\n\n" +
                        "[[cloud_providers]]\n" +
                        "id = \"p_openhuman\"\n" +
                        "slug = \"openhuman\"\n" +
                        "label = \"OpenHuman\"\n" +
                        "endpoint = \"https://api.openhuman.ai/v1\"\n" +
                        "auth_style = \"openhuman_jwt\"\n\n" +
                        "[[cloud_providers]]\n" +
                        "id = \"p_openai\"\n" +
                        "slug = \"openai\"\n" +
                        "label = \"OpenAI\"\n" +
                        "endpoint = \"https://api.openai.com/v1\"\n" +
                        "auth_style = \"bearer\"\n"
        ).getBytes(StandardCharsets.UTF_8));

        OpenHumanSyncServiceImpl service = new OpenHumanSyncServiceImpl(configPath.toString(), null, null);
        service.export("http://127.0.0.1:18080/v1");
        service.export("http://127.0.0.1:18080/v1");

        Toml toml = new Toml().read(configPath.toFile());
        assertEquals("Alibaba/qwen3.6-plus", toml.getString("default_model"));
        assertEquals("linkmind:Alibaba/qwen3.6-plus", toml.getString("chat_provider"));
        assertEquals("linkmind:Alibaba/qwen3.6-plus", toml.getString("reasoning_provider"));
        assertEquals("linkmind:Alibaba/qwen3.6-plus", toml.getString("agentic_provider"));
        assertEquals("openai:gpt-4o", toml.getString("coding_provider"));
        assertEquals("linkmind:Alibaba/qwen3.6-plus", toml.getString("memory_provider"));
        assertEquals("linkmind:Alibaba/qwen3.6-plus", toml.getString("heartbeat_provider"));
        assertEquals("linkmind:Alibaba/qwen3.6-plus", toml.getString("learning_provider"));
        assertEquals("openai:gpt-4o", toml.getString("subconscious_provider"));
        assertEquals("p_linkmind_linkmind", toml.getString("primary_cloud"));

        List<Toml> providers = toml.getTables("cloud_providers");
        long linkmindCount = providers.stream()
                .filter(provider -> "linkmind".equals(provider.getString("slug")))
                .count();
        assertEquals(1, linkmindCount);
        assertTrue(providers.stream().anyMatch(provider -> "openai".equals(provider.getString("slug"))));
    }

    @Test
    void exportUpgradesLegacyLinkMindProRoutes() throws Exception {
        Path configPath = tempDir.resolve("legacy-openhuman").resolve("config.toml");
        Files.createDirectories(configPath.getParent());
        Files.write(configPath, (
                "default_model = \"linkmind-pro\"\n" +
                        "chat_provider = \"linkmind:linkmind-pro\"\n" +
                        "reasoning_provider = \"linkmind:custom-model\"\n\n" +
                        "[[cloud_providers]]\n" +
                        "id = \"p_linkmind_linkmind\"\n" +
                        "slug = \"linkmind\"\n" +
                        "label = \"LinkMind\"\n" +
                        "endpoint = \"http://127.0.0.1:8080/v1\"\n" +
                        "auth_style = \"bearer\"\n" +
                        "default_model = \"linkmind-pro\"\n"
        ).getBytes(StandardCharsets.UTF_8));

        OpenHumanSyncServiceImpl service = new OpenHumanSyncServiceImpl(configPath.toString(), null, null);
        service.export("http://127.0.0.1:18080/v1");

        Toml toml = new Toml().read(configPath.toFile());
        assertEquals("Alibaba/qwen3.6-plus", toml.getString("default_model"));
        assertEquals("linkmind:Alibaba/qwen3.6-plus", toml.getString("chat_provider"));
        assertEquals("linkmind:custom-model", toml.getString("reasoning_provider"));
        List<Toml> providers = toml.getTables("cloud_providers");
        assertEquals("Alibaba/qwen3.6-plus", providers.get(0).getString("default_model"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadImportsSupportedProvidersAndPlainTextAuthProfiles() throws Exception {
        Path configPath = tempDir.resolve("openhuman").resolve("config.toml");
        Files.createDirectories(configPath.getParent());
        Files.write(configPath, (
                "chat_provider = \"openai:gpt-4o\"\n" +
                        "agentic_provider = \"noneapi:free-model\"\n" +
                        "reasoning_provider = \"linkmind:Alibaba/qwen3.6-plus\"\n" +
                        "coding_provider = \"anthropic:claude-sonnet\"\n\n" +
                        "memory_provider = \"ohalt:managed-model\"\n\n" +
                        "[[cloud_providers]]\n" +
                        "id = \"p_openai\"\n" +
                        "slug = \"openai\"\n" +
                        "label = \"OpenAI\"\n" +
                        "endpoint = \"https://api.openai.com/v1\"\n" +
                        "auth_style = \"bearer\"\n\n" +
                        "[[cloud_providers]]\n" +
                        "id = \"p_none\"\n" +
                        "slug = \"noneapi\"\n" +
                        "label = \"No Auth API\"\n" +
                        "endpoint = \"http://127.0.0.1:9000/v1\"\n" +
                        "auth_style = \"none\"\n\n" +
                        "[[cloud_providers]]\n" +
                        "id = \"p_linkmind\"\n" +
                        "slug = \"linkmind\"\n" +
                        "label = \"LinkMind\"\n" +
                        "endpoint = \"http://127.0.0.1:8080/v1\"\n" +
                        "auth_style = \"bearer\"\n\n" +
                        "[[cloud_providers]]\n" +
                        "id = \"p_anthropic\"\n" +
                        "slug = \"anthropic\"\n" +
                        "label = \"Anthropic\"\n" +
                        "endpoint = \"https://api.anthropic.com/v1\"\n" +
                        "auth_style = \"anthropic\"\n"
                        + "\n[[cloud_providers]]\n" +
                        "id = \"p_ohalt\"\n" +
                        "slug = \"ohalt\"\n" +
                        "label = \"OpenHuman Alternate\"\n" +
                        "endpoint = \"https://api.tinyhumans.ai/v1\"\n" +
                        "auth_style = \"bearer\"\n"
        ).getBytes(StandardCharsets.UTF_8));

        Files.write(configPath.getParent().resolve("auth-profiles.json"), (
                "{\n" +
                        "  \"schema_version\": 1,\n" +
                        "  \"active_profiles\": {\"provider:OpenAI\": \"provider:openai:default\"},\n" +
                        "  \"profiles\": {\n" +
                        "    \"provider:openai:default\": {\n" +
                        "      \"id\": \"provider:openai:default\",\n" +
                        "      \"provider\": \"provider:OpenAI\",\n" +
                        "      \"profile_name\": \"default\",\n" +
                        "      \"kind\": \"token\",\n" +
                        "      \"token\": \"sk-openai-test\"\n" +
                        "    }\n" +
                        "  }\n" +
                        "}\n"
        ).getBytes(StandardCharsets.UTF_8));

        Path lagiYml = tempDir.resolve("lagi.yml");
        Files.write(lagiYml, (
                "models: []\n" +
                        "functions:\n" +
                        "  chat:\n" +
                        "    route: best(default)\n" +
                        "    backends: []\n"
        ).getBytes(StandardCharsets.UTF_8));
        System.setProperty(InstallerUtil.CONFIG_FILE_PROPERTY, lagiYml.toString());

        OpenHumanSyncServiceImpl service = new OpenHumanSyncServiceImpl(configPath.toString(), null, null);
        service.load("http://127.0.0.1:8080/v1");

        Map<String, Object> yaml = YmlLoader.loadYamlAsMap(lagiYml.toString());
        List<Map<String, Object>> models = (List<Map<String, Object>>) yaml.get("models");
        assertNotNull(models);
        Map<String, Object> openAi = findByName(models, "linkmind-openhuman-openai");
        assertEquals("gpt-4o", openAi.get("model"));
        assertEquals("https://api.openai.com/v1/chat/completions", openAi.get("api_address"));
        assertEquals("sk-openai-test", openAi.get("api_key"));

        Map<String, Object> noneApi = findByName(models, "linkmind-openhuman-noneapi");
        assertEquals("free-model", noneApi.get("model"));
        assertEquals("http://127.0.0.1:9000/v1/chat/completions", noneApi.get("api_address"));
        assertFalse(noneApi.containsKey("api_key"));

        assertTrue(models.stream().noneMatch(model -> "linkmind-openhuman-linkmind".equals(model.get("name"))));
        assertTrue(models.stream().noneMatch(model -> "linkmind-openhuman-anthropic".equals(model.get("name"))));
        assertTrue(models.stream().noneMatch(model -> "linkmind-openhuman-ohalt".equals(model.get("name"))));
    }

    private Map<String, Object> findByName(List<Map<String, Object>> models, String name) {
        return models.stream()
                .filter(model -> name.equals(model.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("model not found: " + name));
    }
}
