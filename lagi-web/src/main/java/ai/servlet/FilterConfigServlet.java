package ai.servlet;

import ai.config.pojo.FilterConfig;
import ai.servlet.annotation.Body;
import ai.servlet.annotation.Get;
import ai.servlet.annotation.Post;
import ai.servlet.exceptions.RRException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FilterConfigServlet extends RestfulServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(FilterConfigServlet.class);
    private String lagiYmlPath = null;

    @Get("list")
    public List<FilterConfig> list() {
        try {
            return FilterConfigService.list();
        } catch (Exception e) {
            log.error("get filter config list failed", e);
            return new ArrayList<>();
        }
    }

    @Post("add")
    public Map<String, Object> add(@Body FilterConfig filterConfig) {
        try {
            FilterConfigService.validateFilterConfig(filterConfig);
            FilterConfig saved = FilterConfigService.add(filterConfig);
            reloadConfiguration();
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("success", true);
            result.put("message", "add success");
            result.put("item", saved);
            return result;
        } catch (Exception e) {
            log.error("add filter config failed", e);
            throw businessError("add failed", e);
        }
    }

    @Post("update")
    public Map<String, Object> update(@Body FilterConfig filterConfig) {
        try {
            FilterConfigService.validateFilterConfig(filterConfig);
            FilterConfig saved = FilterConfigService.update(filterConfig);
            reloadConfiguration();
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("success", true);
            result.put("message", "update success");
            result.put("item", saved);
            return result;
        } catch (Exception e) {
            log.error("update filter config failed", e);
            throw businessError("update failed", e);
        }
    }

    @Post("delete")
    public Map<String, Object> delete(@Body Map<String, Object> request) {
        try {
            Long id = request == null ? null : asLong(request.get("id"));
            if (id == null || id <= 0) {
                throw new RuntimeException("filter config id is required");
            }
            FilterConfigService.delete(id);
            reloadConfiguration();
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("success", true);
            result.put("message", "delete success");
            return result;
        } catch (Exception e) {
            log.error("delete filter config failed", e);
            throw businessError("delete failed", e);
        }
    }

    private void reloadConfiguration() {
        try {
            FilterConfigService.refreshRuntimeFilters();
            log.info("filter configuration reloaded successfully");
        } catch (Exception e) {
            log.error("filter configuration reload failed", e);
            throw new RuntimeException("reload filter config failed: " + e.getMessage(), e);
        }
    }

    private List<FilterConfig> loadFromYaml() {
        try {
            String ymlPath = getLagiYmlPath();
            if (ymlPath == null) {
                return new ArrayList<>();
            }
            return ai.config.FilterConfigService.loadFromYamlMap(readYamlMap(ymlPath));
        } catch (Exception e) {
            log.error("load filters from YAML failed", e);
            return new ArrayList<>();
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
                        Files.copy(resourceStream, Paths.get(tempPath), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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

    private String detectEncoding(String filePath) {
        try {
            return ai.utils.EncodingDetector.detectEncoding(filePath);
        } catch (Exception e) {
            log.warn("detect file encoding failed, use UTF-8: {}", filePath, e);
            return "UTF-8";
        }
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
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
}
