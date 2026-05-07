package ai.starter.config.impl;


import ai.common.pojo.Backend;
import ai.config.GlobalConfigurations;
import ai.starter.config.util.ConfigUtil;
import ai.utils.YmlLoader;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class DeerFlowSyncServiceImpl extends BaseSyncServiceImpl {

    private static final Logger log = LoggerFactory.getLogger(DeerFlowSyncServiceImpl.class);

    public DeerFlowSyncServiceImpl(String basePath) {
        super(basePath);
    }

    @Override
    public boolean check() {
        if (isBlank(basePath)) {
            return false;
        }
        Path path = Paths.get(basePath);
        File file = path.toFile();
        if(!file.exists()) {
            log.warn("{}: \t {} is not exists, skipping configuration synchronization", name(), path);
            return false;
        }
        if(file.isFile()) {
            return true;
        }
        Path configPath = path.resolve("config.yaml");
        if(!configPath.toFile().exists()) {
            log.warn("{}: \t {} is not exists, skipping configuration synchronization", name(), configPath);
            return false;
        }
        return true;
    }

    @Override
    public void export(String path) {
        load(path);
        Path deerFlowPath = Paths.get(basePath);
        boolean exists = deerFlowPath.toFile().exists();
        if (!exists) {
            log.warn("{}: {} is not exists, skipping configuration synchronization", name(), deerFlowPath);
            return;
        }
        Path configPath = null;
        boolean directory = deerFlowPath.toFile().isDirectory();
        if(!directory) {
            log.warn("{}: {} is not a directory, skipping configuration synchronization", name(), deerFlowPath);
            return;
        }
        configPath = deerFlowPath.resolve("config.yaml");
        if (!Files.exists(configPath)) {
            log.warn("{}: config file is not found: {}", name(), configPath);
            return;
        }
        Map<String, Object> configYaml = YmlLoader.loadYamlAsMap(configPath.toString());
        List<Map<String, Object>> models = (List<Map<String, Object>>)configYaml.get("models");
        GlobalConfigurations globalConfigurations = YmlLoader.loadYaml(ConfigUtil.getLagiYmlPath().toString(), GlobalConfigurations.class);
        List<Backend> backends = globalConfigurations.getFunctions().getChat().getBackends();
        List<Map<String, Object>> collect = backends.stream().filter(backend -> backend.getBackend().startsWith(getNamePrifix())).map(backend -> buildDeerFlowModelConfig(backend, path)).collect(Collectors.toList());
        models.addAll(collect);
        YmlLoader.writeYaml(configPath.toString(), configYaml);
    }

    private @NotNull String getNamePrifix() {
        return "linkmind-" + name().toLowerCase();
    }


    private Map<String, Object> buildDeerFlowModelConfig(Backend backend, String path) {
        String name = backend.getName() == null ? backend.getBackend() : backend.getName();
        Map<String, Object> modelConfig = new HashMap<>();
        modelConfig.put("name", name);
        modelConfig.put("display_name", "linkmind-pro("+backend.getModel()+")");
        modelConfig.put("use", "langchain_openai:ChatOpenAI");
        modelConfig.put("model", backend.getModel());
        modelConfig.put("api_key", "");
        modelConfig.put("base_url", path);
        modelConfig.put("request_timeout", 600.0);
        modelConfig.put("max_retries", 2);
        modelConfig.put("max_tokens", 8192);
        modelConfig.put("supports_thinking", true);
        modelConfig.put("supports_vision", false);
        Map<String, Object> chatTemplateKwargs = new HashMap<>();
        chatTemplateKwargs.put("enable_thinking", true);
        Map<String, Object> extraBody = new HashMap<>();
        extraBody.put("chat_template_kwargs", chatTemplateKwargs);
        Map<String, Object> whenThinkingEnabled = new HashMap<>();
        whenThinkingEnabled.put("extra_body", extraBody);
        modelConfig.put("when_thinking_enabled", whenThinkingEnabled);
        return modelConfig;
    }

    private Backend convertModel2Backend(Map<String, Object> loadModels) {

        String name = (String) loadModels.get("name");
        if(name.startsWith(getNamePrifix())) {
            return null;
        }
        try {
            name = "linkmind-" + name().toLowerCase() + "-" + name;
            Backend backend = new Backend();
            backend.setName(name);
            backend.setType(name);
            backend.setBackend(name);
            String baseUrl = loadModels.get("api_base") == null ? (String)loadModels.get("base_url") : (String)loadModels.get("api_base");
            backend.setEndpoint(toCompletionApiAddress(baseUrl));
            backend.setApiAddress(toCompletionApiAddress(baseUrl));
            backend.setApiKey((String) loadModels.get("api_key"));
            backend.setEnable(true);
            backend.setStream(true);
            backend.setPriority(100);
            String model =   (String) loadModels.get("model");
            String[] split = model.split("/");
            if(split.length > 1) {
                model = split[split.length - 1];
            }
            backend.setModel(model);
            String use = (String) loadModels.get("use");
            String driver = use2Driver(use, model, baseUrl);
            backend.setDriver(driver);
            return backend;
        } catch (Exception e) {
            log.error("{}: \t Failed to convert model to backend: {}", name(), loadModels, e);
        }
        return null;
    }

    private String use2Driver(String use, String model, String baseUrl) {
        if(baseUrl == null) {
            if(use.contains("ChatOpenAI")) {
                return "ai.llm.adapter.impl.GPTAdapter";
            }
            if(use.contains("ChatGoogleGenerativeAI")) {
                return "ai.llm.adapter.impl.GeminiAdapter";
            }
            if(use.contains("ChatAnthropic")) {
                return "ai.llm.adapter.impl.ClaudeAdapter";
            }
        }
        if(use.contains("PatchedChatDeepSeek") && model.contains("deepseek")) {
            return "ai.llm.adapter.impl.DeepSeekAdapter";
        }
        if(use.contains("PatchedChatDeepSeek") && model.contains("doubao")) {
            return "ai.llm.adapter.impl.DoubaoAdapter";
        }
        return "ai.llm.adapter.impl.OpenAIStandardAdapter";
    }

    @Override
    public void load(String urlPath) {
        Path deerFlowPath = Paths.get(basePath);
        boolean exists = deerFlowPath.toFile().exists();
        if (!exists) {
            log.warn("{}: {} is not exists, skipping configuration synchronization", name(), deerFlowPath);
            return;
        }
        Path configPath = null;
        boolean directory = deerFlowPath.toFile().isDirectory();
        if(!directory) {
            log.warn("{}: {} is not a directory, skipping configuration synchronization", name(), deerFlowPath);
            return;
        }
        configPath = deerFlowPath.resolve("config.yaml");
        if (!Files.exists(configPath)) {
            log.warn("{}: config file is not found: {}", name(), configPath);
            return;
        }
        try {
            Map<String, Object> deerFlowConfig = YmlLoader.loadYamlAsMap(configPath.toString());
            List<Map<String, Object>> loadMap = (List<Map<String, Object>>) deerFlowConfig.get("models");
            List<Backend> backends = loadMap.stream().map(this::convertModel2Backend).filter(Objects::nonNull).collect(Collectors.toList());
            Path envPath = deerFlowPath.resolve(".env");
            Map<String, String> envKeyValues = loadEnv(envPath);
            for (Backend backend : backends) {
                String apiKey = backend.getApiKey();
                apiKey = resolveEnvPlaceholder(apiKey, envKeyValues);
                backend.setApiKey(apiKey);
            }
            GlobalConfigurations globalConfigurations = YmlLoader.loadYaml(ConfigUtil.getLagiYmlPath().toString(), GlobalConfigurations.class);
            ConfigUtil.setConfig(globalConfigurations, backends);
            YmlLoader.writeYaml(ConfigUtil.getLagiYmlPath().toString(), globalConfigurations);
        } catch (Exception e) {
            log.error("{}: failed to load and sync config", name(), e);
        }
    }

    @Override
    public String name() {
        return "DeerFlow";
    }



    private Map<String, String> loadEnv(Path envPath) throws IOException {
        Map<String, String> envMap = new HashMap<>();
        if (!Files.exists(envPath)) {
            return envMap;
        }
        List<String> lines = Files.readAllLines(envPath, StandardCharsets.UTF_8);
        for (String rawLine : lines) {
            if (rawLine == null) {
                continue;
            }
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eqIdx = line.indexOf('=');
            if (eqIdx <= 0) {
                continue;
            }
            String key = line.substring(0, eqIdx).trim();
            String value = line.substring(eqIdx + 1).trim();
            if (!key.isEmpty()) {
                envMap.put(key, value);
            }
        }
        return envMap;
    }

    private String resolveEnvPlaceholder(String value, Map<String, String> envMap) {
        if (isBlank(value)) {
            return value;
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("$")) {
            return trimmed;
        }
        String key = trimmed.substring(1);
        String resolved = envMap.get(key);
        return isBlank(resolved) ? trimmed : resolved;
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


    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }



    public static void main(String[] args) {
        DeerFlowSyncServiceImpl service = new DeerFlowSyncServiceImpl("C:\\Users\\Administrator\\Desktop\\project\\lagi\\deer-flow");
        service.export("http://127.0.0.1:8080/v1");
    }
}
