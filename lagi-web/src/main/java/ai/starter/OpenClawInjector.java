package ai.starter;

import ai.utils.AiGlobal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;


@Slf4j
public final class OpenClawInjector {

    private static final String OPEN_CLAW_DIR_NAME = ".openclaw";
    private static final String[] MODELS_JSON_PATH_SEGMENTS = {"agents", "main", "agent", "models.json"};
    private static final String OPENCLAW_JSON = "openclaw.json";

    public static final String OPENCLAW_LINKMIND_API_KEY_PROPERTY = "openclaw.linkmind.apiKey";

    public static final String OPENCLAW_LINKMIND_SET_DEFAULT_PROPERTY = "openclaw.linkmind.setDefault";

    private static final String PROVIDER_NAME = AiGlobal.DEFAULT_PROVIDER_NAME;
    public static final String DEFAULT_MODEL_ID = AiGlobal.DEFAULT_MODEL_ID;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OpenClawInjector() {
    }

    public static void inject(String openAiCompatibleBaseUrl) {
        if (openAiCompatibleBaseUrl == null || openAiCompatibleBaseUrl.trim().isEmpty()) {
            return;
        }
        try {
            Path modelsJson = resolveModelsJsonPath();
            if (modelsJson == null) {
                return;
            }
            Files.createDirectories(modelsJson.getParent());

            ObjectNode root = readOrCreateRoot(modelsJson);
            ObjectNode providers = ensureObject(root, "providers");

            ObjectNode provider = providers.has(PROVIDER_NAME) && providers.get(PROVIDER_NAME).isObject()
                    ? (ObjectNode) providers.get(PROVIDER_NAME)
                    : providers.putObject(PROVIDER_NAME);

            provider.put("baseUrl", openAiCompatibleBaseUrl.trim());
            if (!provider.hasNonNull("api")) {
                provider.put("api", "openai-completions");
            }

            String apiKey = System.getProperty(OPENCLAW_LINKMIND_API_KEY_PROPERTY);
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                provider.put("apiKey", apiKey.trim());
            } else {
                // do not force apiKey; avoid overriding user's secret management
                provider.remove("apiKey");
            }

            ArrayNode models = provider.has("models") && provider.get("models").isArray()
                    ? (ArrayNode) provider.get("models")
                    : provider.putArray("models");
            if (models.isEmpty()) {
                ObjectNode m = models.addObject();
                m.put("id", DEFAULT_MODEL_ID);
                m.put("name", "LinkMind Chat (OpenAI Compatible)");
                m.put("reasoning", false);
                ArrayNode inputArr = m.putArray("input");
                inputArr.add("text");
                ObjectNode cost = m.putObject("cost");
                cost.put("input", 0);
                cost.put("output", 0);
                cost.put("cacheRead", 0);
                cost.put("cacheWrite", 0);
                m.put("contextWindow", 128000);
                m.put("maxTokens", 8192);
                m.put("api", "openai-completions");
            }

            writeJsonAtomic(modelsJson, root);
            log.info("Injected LinkMind provider into OpenClaw: {} -> providers.{}", modelsJson, PROVIDER_NAME);

            injectOpenClawJson(openAiCompatibleBaseUrl.trim());
        } catch (Exception e) {
            log.warn("Failed to inject LinkMind provider into OpenClaw", e);
        }
    }

    /**
     *  ~/.openclaw/openclaw.json
     */
    private static void injectOpenClawJson(String openAiCompatibleBaseUrl) throws IOException {
        Path openclawJson = resolveOpenClawJsonPath();
        if (openclawJson == null) return;
        Files.createDirectories(openclawJson.getParent());

        ObjectNode root = readOrCreateObjectRoot(openclawJson);


        ObjectNode modelsNode = ensureObject(root, "models");
        ObjectNode providersNode = ensureObject(modelsNode, "providers");
        ObjectNode linkmindProvider = providersNode.has(PROVIDER_NAME) && providersNode.get(PROVIDER_NAME).isObject()
                ? (ObjectNode) providersNode.get(PROVIDER_NAME)
                : providersNode.putObject(PROVIDER_NAME);
        linkmindProvider.put("baseUrl", openAiCompatibleBaseUrl);

        if (!linkmindProvider.hasNonNull("api")) {
            linkmindProvider.put("api", "openai-completions");
        }

        String apiKey = System.getProperty(OPENCLAW_LINKMIND_API_KEY_PROPERTY);
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            linkmindProvider.put("apiKey", apiKey.trim());
        } else {
            linkmindProvider.remove("apiKey");
        }

        ArrayNode providerModels = linkmindProvider.has("models") && linkmindProvider.get("models").isArray()
                ? (ArrayNode) linkmindProvider.get("models")
                : linkmindProvider.putArray("models");
        if (!containsModelId(providerModels, DEFAULT_MODEL_ID)) {
            ObjectNode m = providerModels.addObject();
            m.put("id", DEFAULT_MODEL_ID);
            m.put("name", "LinkMind Chat (OpenAI Compatible)");
            m.put("reasoning", false);
            ArrayNode inputArr = m.putArray("input");
            inputArr.add("text");
            ObjectNode cost = m.putObject("cost");
            cost.put("input", 0);
            cost.put("output", 0);
            cost.put("cacheRead", 0);
            cost.put("cacheWrite", 0);
            m.put("contextWindow", 128000);
            m.put("maxTokens", 8192);
            m.put("api", "openai-completions");
        }

        if (apiKey != null && !apiKey.trim().isEmpty()) {
            ObjectNode authNode = ensureObject(root, "auth");
            ObjectNode profilesNode = ensureObject(authNode, "profiles");
            String profileKey = PROVIDER_NAME + ":default";
            ObjectNode profile = profilesNode.has(profileKey) && profilesNode.get(profileKey).isObject()
                    ? (ObjectNode) profilesNode.get(profileKey)
                    : profilesNode.putObject(profileKey);
            profile.put("provider", PROVIDER_NAME);
            if (!profile.hasNonNull("mode")) {
                profile.put("mode", "api_key");
            }
        }

        ObjectNode agentsNode = ensureObject(root, "agents");
        ObjectNode defaultsNode = ensureObject(agentsNode, "defaults");
        ObjectNode defaultsModelsNode = ensureObject(defaultsNode, "models");
        String modelKey = PROVIDER_NAME + "/" + DEFAULT_MODEL_ID;
        ObjectNode modelCfg = defaultsModelsNode.has(modelKey) && defaultsModelsNode.get(modelKey).isObject()
                ? (ObjectNode) defaultsModelsNode.get(modelKey)
                : defaultsModelsNode.putObject(modelKey);
        if (!modelCfg.hasNonNull("alias")) {
            modelCfg.put("alias", DEFAULT_MODEL_ID);
        }

        boolean setDefault = Boolean.parseBoolean(System.getProperty(OPENCLAW_LINKMIND_SET_DEFAULT_PROPERTY, "false"));
        if (setDefault) {
            ObjectNode modelNode = ensureObject(defaultsNode, "model");
            JsonNode primary = modelNode.get("primary");
            if (primary == null || (primary.isTextual() && primary.asText().trim().isEmpty())) {
                modelNode.put("primary", modelKey);
            }
        }

        writeJsonAtomic(openclawJson, root);
        log.info("Injected LinkMind provider into OpenClaw main config: {}", openclawJson);
    }

    private static Path resolveModelsJsonPath() {
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.trim().isEmpty()) {
            log.warn("Cannot get user home directory");
            return null;
        }

        Path openClawDir = Paths.get(userHome, OPEN_CLAW_DIR_NAME);
        Path modelsJsonPath = openClawDir;
        for (String segment : MODELS_JSON_PATH_SEGMENTS) {
            modelsJsonPath = modelsJsonPath.resolve(segment);
        }
        return modelsJsonPath;
    }

    private static Path resolveOpenClawJsonPath() {
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.trim().isEmpty()) {
            log.warn("Cannot get user home directory");
            return null;
        }
        return Paths.get(userHome, OPEN_CLAW_DIR_NAME, OPENCLAW_JSON);
    }

    private static ObjectNode readOrCreateRoot(Path modelsJson) throws IOException {
        if (!Files.exists(modelsJson)) {
            ObjectNode root = MAPPER.createObjectNode();
            root.putObject("providers");
            return root;
        }
        String content = new String(Files.readAllBytes(modelsJson), StandardCharsets.UTF_8);
        if (content == null || content.trim().isEmpty()) {
            ObjectNode root = MAPPER.createObjectNode();
            root.putObject("providers");
            return root;
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(content);
        } catch (JsonProcessingException e) {
            Path bak = modelsJson.resolveSibling(modelsJson.getFileName() + ".bak");
            Files.copy(modelsJson, bak, StandardCopyOption.REPLACE_EXISTING);
            ObjectNode root = MAPPER.createObjectNode();
            root.putObject("providers");
            return root;
        }
        if (node != null && node.isObject()) {
            return (ObjectNode) node;
        }
        ObjectNode root = MAPPER.createObjectNode();
        root.putObject("providers");
        return root;
    }

    private static ObjectNode readOrCreateObjectRoot(Path jsonFile) throws IOException {
        if (!Files.exists(jsonFile)) {
            return MAPPER.createObjectNode();
        }
        String content = new String(Files.readAllBytes(jsonFile), StandardCharsets.UTF_8);
        if (content == null || content.trim().isEmpty()) {
            return MAPPER.createObjectNode();
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(content);
        } catch (JsonProcessingException e) {
            Path bak = jsonFile.resolveSibling(jsonFile.getFileName() + ".bak");
            Files.copy(jsonFile, bak, StandardCopyOption.REPLACE_EXISTING);
            return MAPPER.createObjectNode();
        }
        return (node != null && node.isObject()) ? (ObjectNode) node : MAPPER.createObjectNode();
    }

    private static ObjectNode ensureObject(ObjectNode parent, String field) {
        JsonNode n = parent.get(field);
        if (n != null && n.isObject()) {
            return (ObjectNode) n;
        }
        return parent.putObject(field);
    }

    private static boolean containsModelId(ArrayNode models, String id) {
        if (models == null || id == null || id.trim().isEmpty()) return false;
        for (int i = 0; i < models.size(); i++) {
            JsonNode n = models.get(i);
            if (n != null && n.isObject()) {
                JsonNode idNode = n.get("id");
                if (idNode != null && idNode.isTextual() && id.equals(idNode.asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void writeJsonAtomic(Path path, JsonNode node) throws IOException {
        String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node) + System.lineSeparator();
        Path tmp = Files.createTempFile(path.getParent(), "openclaw_models_", ".json");
        try {
            Files.write(tmp, json.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}

