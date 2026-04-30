package ai.migrate.service;

import ai.config.ContextLoader;
import ai.config.pojo.GeneralConfig;
import ai.dto.LagiModelInfo;
import ai.dto.ModelApiKey;
import ai.migrate.dao.ApiKeyDao;
import ai.utils.AiGlobal;
import ai.utils.OkHttpUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class ApiKeyService {
    private static final String OPENAI_COMPATIBLE = "OpenAICompatible";
    private static final String LANDING = "Landing";
    private String lagiYmlPath = null;

    private static final String SAAS_BASE_URL = AiGlobal.SAAS_URL;
    private final Gson gson = new Gson();
    private final ApiKeyDao apiKeyDao = new ApiKeyDao();
    private final AtomicBoolean localSynced = new AtomicBoolean(false);
    private final AtomicBoolean landingSynced = new AtomicBoolean(false);

    public ApiKeyService() {
        syncApiKeysToLocalDb(null);
    }

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
        }
        return lagiYmlPath;
    }

    private String detectEncoding(String filePath) {
        try {
            return ai.utils.EncodingDetector.detectEncoding(filePath);
        } catch (Exception e) {
            return "UTF-8";
        }
    }

    private Yaml createYaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        return new Yaml(options);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadRootYaml() throws IOException {
        String path = getLagiYmlPath();
        if (path == null) {
            throw new IOException("Cannot find lagi.yml");
        }
        String encoding = detectEncoding(path);
        if (encoding == null || encoding.trim().isEmpty()) {
            encoding = "UTF-8";
        }
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        Object loaded = createYaml().load(new String(bytes, Charset.forName(encoding)));
        if (loaded instanceof Map) {
            return (Map<String, Object>) loaded;
        }
        return new LinkedHashMap<>();
    }

    private void writeRootYaml(Map<String, Object> root) throws IOException {
        String path = getLagiYmlPath();
        if (path == null) {
            throw new IOException("Cannot find lagi.yml");
        }
        OutputStreamWriter writer = new OutputStreamWriter(
                Files.newOutputStream(Paths.get(path)),
                StandardCharsets.UTF_8
        );
        try {
            createYaml().dump(root, writer);
        } finally {
            writer.close();
        }
        String writtenPath = getLagiYmlPath();
        if (writtenPath != null) {
            ContextLoader.reloadLagiYmlFromFile(writtenPath);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getModels(Map<String, Object> root) {
        Object modelsObj = root.get("models");
        if (!(modelsObj instanceof List)) {
            return new ArrayList<Map<String, Object>>();
        }
        List<?> raw = (List<?>) modelsObj;
        List<Map<String, Object>> models = new ArrayList<Map<String, Object>>();
        for (Object item : raw) {
            if (item instanceof Map) {
                models.add((Map<String, Object>) item);
            }
        }
        return models;
    }

    private Map<String, Object> findModelByName(List<Map<String, Object>> models, String modelName) {
        for (Map<String, Object> model : models) {
            Object name = model.get("name");
            if (name != null && modelName.equals(name.toString().trim())) {
                return model;
            }
        }
        return null;
    }

    private Map<String, Object> findModelByProvider(List<Map<String, Object>> models, String provider) {
        for (Map<String, Object> model : models) {
            Object typeObj = model.get("type");
            if (typeObj != null && provider.equalsIgnoreCase(typeObj.toString().trim())) {
                return model;
            }
        }
        return null;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private boolean isApiKeyUsable(String apiKey) {
        if (isBlank(apiKey)) {
            return false;
        }
        String value = apiKey.trim();
        return !("your-api-key".equalsIgnoreCase(value));
    }

    private String normalizeProvider(String provider) {
        if (provider == null) {
            return "";
        }
        String p = provider.trim();
        if (OPENAI_COMPATIBLE.equalsIgnoreCase(p)) {
            return OPENAI_COMPATIBLE;
        }
        return p;
    }

    /**
     * When true (default), API key UI manages local DB keys plus remote Landing keys.
     * When false, only remote Landing keys are listed or mutated.
     */
    public boolean isLocalApiKeyEditable() {
        try {
            if (ContextLoader.configuration == null) {
                return true;
            }
            GeneralConfig general = ContextLoader.configuration.getGeneral();
            if (general == null) {
                return true;
            }
            Boolean b = general.getLocalApiKeyEditable();
            return b == null || b;
        } catch (Exception e) {
            return true;
        }
    }

    public List<ModelApiKey> listApiKeys(String userId) throws IOException {
        syncApiKeysToLocalDb(userId);
        List<ModelApiKey> all = new ArrayList<>();
        if (isLocalApiKeyEditable()) {
            try {
                all.addAll(apiKeyDao.listByUserIdOrEmpty(userId));
            } catch (Exception e) {
                throw new IOException("list local api keys failed: " + e.getMessage(), e);
            }
        } else {
            if (!isBlank(userId)) {
                all.addAll(getLandingApiKeys(userId));
            }
        }
        return all;
    }

    private void syncApiKeysToLocalDb(String userId) {
        if (!isLocalApiKeyEditable()) {
            return;
        }
        try {
            if (localSynced.compareAndSet(false, true)) {
                syncLocalApiKeysToDb(userId);
            }
            if (!isBlank(userId) && landingSynced.compareAndSet(false, true)) {
                syncLandingApiKeysToDb(userId.trim());
            }
        } catch (Exception e) {
            log.error("sync api keys to local db failed: {}", e.getMessage(), e);
            localSynced.set(false);
            landingSynced.set(false);
        }
    }

    private void syncLocalApiKeysToDb(String userId) throws Exception {
        List<ModelApiKey> localApiKeys = getLocalApiKeys();
        for (ModelApiKey item : localApiKeys) {
            if (!isBlank(userId)) {
                item.setUserId(userId.trim());
            }
            insertIfApiKeyNotExists(item);
            // Local keys are all read from lagi.yml (api_key / api_keys) -> mark as active.
            if (!isBlank(item.getApiKey())) {
                apiKeyDao.setStatusByApiKey(item.getApiKey().trim(), 1);
            }
        }
    }

    private void syncLandingApiKeysToDb(String userId) throws Exception {
        if (isBlank(userId)) {
            throw new IOException("userId is required for Landing");
        }
        List<ModelApiKey> landingApiKeys = getLandingApiKeys(userId);
        Set<String> configuredKeys = collectConfiguredApiKeys();
        for (ModelApiKey item : landingApiKeys) {
            item.setUserId(userId.trim());
            insertIfApiKeyNotExists(item);
            String apiKey = item.getApiKey() == null ? "" : item.getApiKey().trim();
            // If this Landing key is also configured in lagi.yml (api_key / api_keys), mark it active.
            if (!apiKey.isEmpty() && configuredKeys.contains(apiKey)) {
                apiKeyDao.setStatusByApiKey(apiKey, 1);
            }
        }
    }

    private Set<String> collectConfiguredApiKeys() throws IOException {
        Map<String, Object> root = loadRootYaml();
        List<Map<String, Object>> models = getModels(root);
        Set<String> keys = new HashSet<String>();
        for (Map<String, Object> model : models) {
            String singleKey = readApiKeyString(model);
            if (isApiKeyUsable(singleKey)) {
                keys.add(singleKey.trim());
            }
            for (String k : readApiKeysList(model)) {
                if (isApiKeyUsable(k)) {
                    keys.add(k.trim());
                }
            }
        }
        return keys;
    }

    private void insertIfApiKeyNotExists(ModelApiKey source) throws Exception {
        if (source == null || isBlank(source.getApiKey())) {
            return;
        }
        String apiKey = source.getApiKey().trim();
        if (apiKeyDao.existsByApiKey(apiKey)) {
            return;
        }
        ModelApiKey row = new ModelApiKey();
        row.setName(source.getName());
        row.setProvider(normalizeProvider(source.getProvider()));
        row.setApiKey(apiKey);
        if (LANDING.equalsIgnoreCase(row.getProvider())) {
            if (isBlank(source.getUserId())) {
                throw new IOException("userId is required for Landing");
            }
            row.setUserId(source.getUserId().trim());
        } else {
            row.setUserId(null);
        }
        row.setApiAddress(isBlank(source.getApiAddress()) ? "" : source.getApiAddress().trim());
        row.setStatus(source.getStatus() == null ? 0 : source.getStatus());
        apiKeyDao.insert(row);
    }

    public synchronized List<ModelApiKey> getLocalApiKeys() throws IOException {
        Map<String, Object> root = loadRootYaml();
        List<Map<String, Object>> models = getModels(root);
        List<ModelApiKey> result = new ArrayList<ModelApiKey>();
        for (Map<String, Object> model : models) {
            Object nameObj = model.get("name");
            if (nameObj == null) {
                continue;
            }
            String provider = normalizeProvider(model.get("type") == null ? "" : model.get("type").toString());
            Object apiAddressObj = model.get("api_address");
            String apiAddress = apiAddressObj == null ? "" : apiAddressObj.toString();
            if (!OPENAI_COMPATIBLE.equalsIgnoreCase(provider)) {
                apiAddress = "";
            }
            // Collect usable keys from both api_key and api_keys, de-duplicated per model.
            Set<String> collected = new LinkedHashSet<String>();
            String singleKey = readApiKeyString(model);
            if (isApiKeyUsable(singleKey)) {
                collected.add(singleKey.trim());
            }
            for (String k : readApiKeysList(model)) {
                if (isApiKeyUsable(k)) {
                    collected.add(k.trim());
                }
            }
            for (String apiKey : collected) {
                ModelApiKey row = new ModelApiKey();
                row.setName(nameObj.toString());
                row.setProvider(provider);
                row.setApiKey(apiKey);
                row.setApiAddress(apiAddress);
                result.add(row);
            }
        }
        return result;
    }

    public synchronized Map<String, String> getApiKey(String modelName) throws IOException {
        if (modelName == null || modelName.trim().isEmpty()) {
            throw new IOException("modelName is required");
        }
        Map<String, Object> root = loadRootYaml();
        Map<String, Object> model = findModelByName(getModels(root), modelName.trim());
        if (model == null) {
            return null;
        }
        Map<String, String> result = new LinkedHashMap<String, String>();
        Object typeObj = model.get("type");
        String provider = normalizeProvider(typeObj == null ? "" : typeObj.toString());
        Object apiKeyObj = model.get("api_key");
        Object apiAddressObj = model.get("api_address");
        String apiKey = apiKeyObj == null ? "" : apiKeyObj.toString();
        if (!isApiKeyUsable(apiKey)) {
            return null;
        }
        String apiAddress = apiAddressObj == null ? "" : apiAddressObj.toString();
        if (!OPENAI_COMPATIBLE.equalsIgnoreCase(provider)) {
            apiAddress = "";
        }
        result.put("name", modelName.trim());
        result.put("provider", provider);
        result.put("api_key", apiKey);
        result.put("api_address", apiAddress);
        return result;
    }

    public synchronized void deleteApiKey(Long id, String apiKey, String provider, String userId) throws IOException {
        String p = normalizeProvider(provider);
        if (LANDING.equalsIgnoreCase(p)) {
            if (isBlank(userId)) {
                throw new IOException("userId is required for Landing");
            }
            String targetApiKey = apiKey;
            if (isBlank(targetApiKey)) {
                throw new IOException("Landing api key not found");
            }
            deleteLandingApiKey(targetApiKey);
            try {
                apiKeyDao.deleteByApiKey(targetApiKey);
            } catch (Exception e) {
                throw new IOException("delete local Landing api key failed: " + e.getMessage(), e);
            }
            return;
        }
        if (!isLocalApiKeyEditable()) {
            throw new IOException("local api key management is disabled");
        }
        try {
            String targetApiKey = apiKey;
            ModelApiKey target = null;
            if (!isBlank(targetApiKey)) {
                target = apiKeyDao.findByApiKey(targetApiKey);
            }
            if (target == null) {
                if (id == null) {
                    throw new IOException("apiKey is required");
                }
                List<ModelApiKey> localList = apiKeyDao.listAll();
                for (ModelApiKey item : localList) {
                    if (item.getId() != null && item.getId().equals(id)) {
                        target = item;
                        break;
                    }
                }
            }
            if (target == null) {
                throw new IOException("Local api key not found");
            }
            if (target.getStatus() != null && target.getStatus() == 1) {
                resetProviderApiKeyInConfig(target.getProvider(), target.getApiKey());
            }
            apiKeyDao.deleteByApiKey(target.getApiKey());
        } catch (Exception e) {
            throw new IOException("delete local api key failed: " + e.getMessage(), e);
        }
    }

    public synchronized List<String> listProviders() throws IOException {
        if (!isLocalApiKeyEditable()) {
            return Collections.singletonList(LANDING);
        }
        Map<String, Object> root = loadRootYaml();
        List<Map<String, Object>> models = getModels(root);
        Set<String> providerSet = new HashSet<>();
        for (Map<String, Object> model : models) {
            Object typeObj = model.get("type");
            if (typeObj == null) {
                continue;
            }
            String provider = normalizeProvider(typeObj.toString());
            if (!isBlank(provider)) {
                providerSet.add(provider);
            }
        }
        List<String> result = new ArrayList<String>(providerSet);
        result.sort(String::compareToIgnoreCase);
        return result;
    }

    public synchronized void addApiKey(String name, String provider, String apiKey, String modelValue, String apiAddress, String userId) throws IOException {
        if (isBlank(provider)) {
            throw new IOException("provider is required");
        }
        if (isBlank(name)) {
            throw new IOException("name is required");
        }
        String normalizedProvider = normalizeProvider(provider);
        if (isBlank(apiKey) && !LANDING.equalsIgnoreCase(normalizedProvider)) {
            throw new IOException("apiKey is required");
        }
        String nameTrimmed = name.trim();

        if (LANDING.equalsIgnoreCase(normalizedProvider)) {
            if (isBlank(userId)) {
                throw new IOException("userId is required for Landing");
            }
            ModelApiKey landingRequest = new ModelApiKey();
            // Display label for SAAS; lagi.yml model "name" is never updated from this field.
            landingRequest.setName(nameTrimmed);
            landingRequest.setUserId(userId.trim());
            ModelApiKey localLanding = addLandingApiKey(landingRequest);
            if (isLocalApiKeyEditable()) {
                localLanding.setStatus(0);
                localLanding.setProvider(LANDING);
                try {
                    insertIfApiKeyNotExists(localLanding);
                } catch (Exception e) {
                    throw new IOException("sync Landing api key to local db failed: " + e.getMessage(), e);
                }
            }
            return;
        }
        if (!isLocalApiKeyEditable()) {
            throw new IOException("local api key management is disabled");
        }

        ModelApiKey local = new ModelApiKey();
        // Stored in api_key_store only; applyProviderApiKeyToConfig does not write this to lagi.yml.
        local.setName(nameTrimmed);
        local.setProvider(normalizedProvider);
        local.setApiKey(apiKey.trim());
        local.setUserId(null);
        local.setApiAddress(isBlank(apiAddress) ? "" : apiAddress.trim());
        local.setStatus(0);
        try {
            apiKeyDao.insert(local);
        } catch (Exception e) {
            throw new IOException("add local api key failed: " + e.getMessage(), e);
        }
    }

    public synchronized void toggleApiKey(Long id, String provider, boolean enabled, String userId) throws IOException {
        if (id == null) {
            throw new IOException("id is required");
        }
        String p = normalizeProvider(provider);
        if (LANDING.equalsIgnoreCase(p)) {
            if (isBlank(userId)) {
                throw new IOException("userId is required for Landing");
            }
            String targetApiKey = null;
            try {
                List<ModelApiKey> localList = apiKeyDao.listByUserIdOrEmpty(userId);
                for (ModelApiKey item : localList) {
                    if (item.getId() != null && item.getId().equals(id)) {
                        targetApiKey = item.getApiKey();
                        break;
                    }
                }
            } catch (Exception e) {
                throw new IOException("query local api key failed: " + e.getMessage(), e);
            }
            if (isBlank(targetApiKey)) {
                throw new IOException("Landing api key not found");
            }
            List<ModelApiKey> list = getLandingApiKeys(userId);
            ModelApiKey target = null;
            for (ModelApiKey item : list) {
                if (!isBlank(item.getApiKey()) && targetApiKey.equals(item.getApiKey().trim())) {
                    target = item;
                    break;
                }
            }
            if (target == null) {
                throw new IOException("Landing api key not found");
            }
            if (enabled) {
                applyProviderApiKeyToConfig(LANDING, target.getApiKey());
                if (isLocalApiKeyEditable()) {
                    try {
                        insertIfApiKeyNotExists(target);
                        apiKeyDao.disableProviderKeys(LANDING);
                        apiKeyDao.setStatusByApiKey(target.getApiKey(), 1);
                    } catch (Exception e) {
                        throw new IOException("sync Landing toggle to local db failed: " + e.getMessage(), e);
                    }
                }
            } else {
                resetProviderApiKeyInConfig(LANDING, target.getApiKey());
                if (isLocalApiKeyEditable()) {
                    try {
                        apiKeyDao.setStatusByApiKey(target.getApiKey(), 0);
                    } catch (Exception e) {
                        throw new IOException("sync Landing toggle to local db failed: " + e.getMessage(), e);
                    }
                }
            }
            return;
        }
        if (!isLocalApiKeyEditable()) {
            throw new IOException("local api key management is disabled");
        }

        try {
            ModelApiKey target = null;
            List<ModelApiKey> localList = apiKeyDao.listAll();
            for (ModelApiKey item : localList) {
                if (item.getId() != null && item.getId().equals(id)) {
                    target = item;
                    break;
                }
            }
            if (target == null) {
                throw new IOException("Local api key not found");
            }
            if (enabled) {
                apiKeyDao.disableProviderKeys(target.getProvider());
                apiKeyDao.setStatusByApiKey(target.getApiKey(), 1);
                applyProviderApiKeyToConfig(target.getProvider(), target.getApiKey());
            } else {
                apiKeyDao.setStatusByApiKey(target.getApiKey(), 0);
                resetProviderApiKeyInConfig(target.getProvider(), target.getApiKey());
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("toggle local api key failed: " + e.getMessage(), e);
        }
    }

    public ModelApiKey addLandingApiKey(ModelApiKey requestBody) throws IOException {
        if (requestBody == null) {
            throw new IOException("request body is required");
        }
        String url = SAAS_BASE_URL + "/saas/api/apikey/addApiKey";
        String requestJson = gson.toJson(requestBody);
        String resultJson = OkHttpUtil.post(url, requestJson);
        ModelApiKey response = gson.fromJson(resultJson, ModelApiKey.class);
        return response;
    }

    /**
     * Deletes a Landing API key on SAAS (POST /saas/api/apikey/deleteModelApiKey, body: {"apiKey":...}).
     */
    public void deleteLandingApiKey(String apiKey) throws IOException {
        if (isBlank(apiKey)) {
            throw new IOException("apiKey is required");
        }
        String url = SAAS_BASE_URL + "/saas/api/apikey/deleteModelApiKey";
        Map<String, String> payload = new LinkedHashMap<String, String>();
        payload.put("apiKey", apiKey.trim());
        String requestJson = gson.toJson(payload);
        OkHttpUtil.post(url, requestJson);
    }

    /**
     * Writes only api_key (and Landing model list) into lagi.yml. Never updates the model entry's
     * {@code name} field — user-provided key labels stay in DB / SAAS only.
     */
    private void applyProviderApiKeyToConfig(String provider, String apiKey) throws IOException {
        if (isBlank(provider) || isBlank(apiKey)) {
            throw new IOException("provider/apiKey is required");
        }
        Map<String, Object> root = loadRootYaml();
        Map<String, Object> model = findModelByProvider(getModels(root), provider);
        if (model == null) {
            throw new IOException("Provider not found in models: " + provider);
        }
        String trimmedKey = apiKey.trim();
        List<String> apiKeys = readApiKeysList(model);
        String currentApiKey = readApiKeyString(model);
        // Add to api_keys only if neither api_keys nor api_key already contains it.
        if (!apiKeys.contains(trimmedKey) && !trimmedKey.equals(currentApiKey)) {
            apiKeys.add(trimmedKey);
            model.put("api_keys", apiKeys);
        }
        writeRootYaml(root);
    }

    private void resetProviderApiKeyInConfig(String provider, String apiKey) throws IOException {
        if (isBlank(provider)) {
            throw new IOException("provider is required");
        }
        Map<String, Object> root = loadRootYaml();
        Map<String, Object> model = findModelByProvider(getModels(root), provider);
        if (model == null) {
            throw new IOException("Provider not found in models: " + provider);
        }
        if (isBlank(apiKey)) {
            writeRootYaml(root);
            return;
        }
        String trimmedKey = apiKey.trim();
        // Remove the key from api_keys list (all occurrences).
        List<String> apiKeys = readApiKeysList(model);
        apiKeys.removeIf(k -> k != null && trimmedKey.equals(k.trim()));
        if (apiKeys.isEmpty()) {
            model.remove("api_keys");
        } else {
            model.put("api_keys", apiKeys);
        }
        // Reset api_key if it matches the target key.
        String currentApiKey = readApiKeyString(model);
        if (trimmedKey.equals(currentApiKey)) {
            model.put("api_key", "your-api-key");
        }
        writeRootYaml(root);
    }

    @SuppressWarnings("unchecked")
    private List<String> readApiKeysList(Map<String, Object> model) {
        Object raw = model.get("api_keys");
        List<String> result = new ArrayList<String>();
        if (raw instanceof List) {
            for (Object item : (List<Object>) raw) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
        } else if (raw instanceof String) {
            String str = ((String) raw).trim();
            if (!str.isEmpty()) {
                for (String part : str.split(",")) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) {
                        result.add(trimmed);
                    }
                }
            }
        }
        return result;
    }

    private String readApiKeyString(Map<String, Object> model) {
        Object raw = model.get("api_key");
        return raw == null ? null : raw.toString().trim();
    }

    public String getLandingModels() throws IOException {
        List<LagiModelInfo> modelInfoList = listModelInfo();
        StringBuilder models = new StringBuilder();
        for (int i = 0; i < modelInfoList.size(); i++) {
            if (i > 0) {
                models.append(",");
            }
            LagiModelInfo modelInfo = modelInfoList.get(i);
            models.append(modelInfo.getProvider()).append("/").append(modelInfo.getModelName());
        }
        return models.toString();
    }

    public List<LagiModelInfo> listModelInfo() throws IOException {
        String url = SAAS_BASE_URL + "/saas/api/apikey/listModelInfo";
        String resultJson = OkHttpUtil.get(url);
        Type listType = new TypeToken<ArrayList<LagiModelInfo>>() {
        }.getType();
        List<LagiModelInfo> list = gson.fromJson(resultJson, listType);
        return list != null ? list : new ArrayList<>();
    }

    public List<ModelApiKey> getLandingApiKeys(String userId) throws IOException {
        String url = SAAS_BASE_URL + "/saas/api/apikey/listModelApiKeys";
        Map<String, String> params = new HashMap<>();
        params.put("userId", userId);
        String resultJson = OkHttpUtil.get(url, params);
        Type listType = new TypeToken<ArrayList<ModelApiKey>>() {
        }.getType();
        List<ModelApiKey> list = gson.fromJson(resultJson, listType);
        if (list != null) {
            list.forEach(item -> {
                item.setProvider("Landing");
                item.setStatus(0);
            });
        }
        return list != null ? list : new ArrayList<>();
    }
}
