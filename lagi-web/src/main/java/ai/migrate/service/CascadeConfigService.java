package ai.migrate.service;

import ai.config.ConfigUtil;
import ai.config.ContextLoader;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CascadeConfigService {
    private static final String GENERAL = "general";
    private static final String CASCADE_API_ADDRESS = "cascade_api_address";
    private String lagiYmlPath = null;

    public Map<String, String> getCascadeConfig() throws IOException {
        Map<String, Object> root = loadRootYaml();
        CascadeAddress address = readCascadeAddress(root);
        Map<String, String> result = new LinkedHashMap<String, String>();
        result.put("serverAddress", address.value == null ? "" : address.value);
        result.put("source", address.source == null ? "" : address.source);
        result.put("configPath", getLagiYmlPath());
        return result;
    }

    @SuppressWarnings("unchecked")
    public synchronized Map<String, String> saveCascadeConfig(String serverAddress) throws IOException {
        String normalized = normalizeServerAddress(serverAddress);
        Map<String, Object> root = loadRootYaml();
        Object generalObj = root.get(GENERAL);
        Map<String, Object> general;
        if (generalObj instanceof Map) {
            general = (Map<String, Object>) generalObj;
        } else {
            general = new LinkedHashMap<String, Object>();
            root.put(GENERAL, general);
        }

        if (isBlank(normalized)) {
            general.remove(CASCADE_API_ADDRESS);
        } else {
            general.put(CASCADE_API_ADDRESS, normalized);
        }

        writeRootYaml(root);
        CascadeAddress activeAddress = readCascadeAddress(root);
        ConfigUtil.setCascadeApiAddress(activeAddress.value);

        Map<String, String> result = new LinkedHashMap<String, String>();
        result.put("serverAddress", activeAddress.value == null ? "" : activeAddress.value);
        result.put("source", activeAddress.source == null ? "" : activeAddress.source);
        result.put("configPath", getLagiYmlPath());
        return result;
    }

    private CascadeAddress readCascadeAddress(Map<String, Object> root) {
        String generalAddress = getGeneralCascadeAddress(root);
        if (!isBlank(generalAddress)) {
            return new CascadeAddress(ConfigUtil.normalizeCascadeApiAddress(generalAddress), GENERAL);
        }

        String landingAddress = getLandingCascadeAddress(root);
        if (!isBlank(landingAddress)) {
            return new CascadeAddress(ConfigUtil.normalizeCascadeApiAddress(landingAddress), "landing");
        }

        if (!isBlank(ConfigUtil.CASCADE_API_ADDRESS)) {
            return new CascadeAddress(ConfigUtil.normalizeCascadeApiAddress(ConfigUtil.CASCADE_API_ADDRESS), "runtime");
        }

        return new CascadeAddress("", "");
    }

    @SuppressWarnings("unchecked")
    private String getGeneralCascadeAddress(Map<String, Object> root) {
        Object generalObj = root.get(GENERAL);
        if (!(generalObj instanceof Map)) {
            return "";
        }
        Object addressObj = ((Map<String, Object>) generalObj).get(CASCADE_API_ADDRESS);
        return addressObj == null ? "" : String.valueOf(addressObj);
    }

    @SuppressWarnings("unchecked")
    private String getLandingCascadeAddress(Map<String, Object> root) {
        Object modelsObj = root.get("models");
        if (!(modelsObj instanceof List)) {
            return "";
        }
        for (Object item : (List<?>) modelsObj) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<String, Object> model = (Map<String, Object>) item;
            Object nameObj = model.get("name");
            Object typeObj = model.get("type");
            String name = nameObj == null ? "" : String.valueOf(nameObj);
            String type = typeObj == null ? "" : String.valueOf(typeObj);
            if (!"landing".equalsIgnoreCase(name) && !"landing".equalsIgnoreCase(type)) {
                continue;
            }
            Object addressObj = model.get("api_address");
            if (addressObj != null && !isBlank(String.valueOf(addressObj))) {
                return String.valueOf(addressObj);
            }
        }
        return "";
    }

    private String normalizeServerAddress(String serverAddress) throws IOException {
        String normalized = ConfigUtil.normalizeCascadeApiAddress(serverAddress);
        if (isBlank(normalized)) {
            return "";
        }
        String lower = normalized.toLowerCase();
        if (lower.indexOf("://") >= 0 && !lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new IOException("serverAddress must be a valid http(s) URL");
        }
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            normalized = "http://" + normalized;
        }
        try {
            URI uri = new URI(normalized);
            String scheme = uri.getScheme();
            if (scheme == null
                    || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                    || isBlank(uri.getRawAuthority())) {
                throw new IOException("serverAddress must be a valid http(s) URL");
            }
        } catch (URISyntaxException e) {
            throw new IOException("serverAddress must be a valid http(s) URL", e);
        }
        return ConfigUtil.normalizeCascadeApiAddress(normalized);
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
        return new LinkedHashMap<String, Object>();
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
        ContextLoader.reloadLagiYmlFromFile(path);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static class CascadeAddress {
        final String value;
        final String source;

        CascadeAddress(String value, String source) {
            this.value = value;
            this.source = source;
        }
    }
}
