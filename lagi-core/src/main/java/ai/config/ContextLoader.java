package ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ContextLoader {

    private static final Logger log = LoggerFactory.getLogger(ContextLoader.class);

    public static GlobalConfigurations configuration = null;

    private static void loadContextByInputStream(InputStream inputStream) {
        ObjectMapper mapper = new YAMLMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        try {
            InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            GlobalConfigurations loadedConfiguration = mapper.readValue(reader, GlobalConfigurations.class);
            loadedConfiguration.init();
            configuration = loadedConfiguration;
        } catch (Exception e) {
            configuration = null;
            log.error("从 InputStream 加载配置失败", e);
            throw new RuntimeException("加载配置失败", e);
        }
    }

    public static void loadContextByResource(String yamlName) {
        InputStream resourceAsStream = ContextLoader.class.getResourceAsStream("/" + yamlName);
        if (resourceAsStream == null) {
            throw new RuntimeException("classpath resource not found: " + yamlName);
        }
        loadContextByInputStream(resourceAsStream);
    }

    public static void loadContextByFilePath(String filePath) {
        try {
            String encoding = detectEncoding(filePath);
            if (encoding == null) {
                encoding = "UTF-8";
            }
            InputStreamReader reader = new InputStreamReader(
                Files.newInputStream(Paths.get(filePath)),
                java.nio.charset.Charset.forName(encoding)
            );
            ObjectMapper mapper = new YAMLMapper();
            mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            try {
                GlobalConfigurations loadedConfiguration = mapper.readValue(reader, GlobalConfigurations.class);
                loadedConfiguration.init();
                configuration = loadedConfiguration;
                reader.close();
            } catch (Exception e) {
                configuration = null;
                reader.close();
                log.error("加载配置文件失败: {}", filePath, e);
                throw new RuntimeException("加载配置文件失败: " + filePath, e);
            }
        } catch (IOException e) {
            log.error("打开配置文件失败: {}", filePath, e);
            throw new RuntimeException("打开配置文件失败: " + filePath, e);
        }
    }

    private static String detectEncoding(String filePath) {
        try {
            return ai.utils.EncodingDetector.detectEncoding(filePath);
        } catch (Exception e) {
            log.warn("检测文件编码失败，使用默认 UTF-8: {}", filePath, e);
            return "UTF-8";
        }
    }

    public static void loadContext() {
        try {
            if(configuration == null) {
                loadContextByResource("lagi.yml");
            }
        } catch (Exception e) {
            log.warn(e.getMessage());
        }
        if(configuration == null) {
            try {

                loadContextByFilePath("lagi-web/src/main/resources/lagi.yml");
            } catch (Exception e) {
                log.warn(e.getMessage());
            }
        }
        if(configuration == null) {
            try {
                loadContextByFilePath("../lagi-web/src/main/resources/lagi.yml");
            } catch (Exception e) {
                log.warn(e.getMessage());
            }
        }
    }




    public static void main(String[] args) {

        ContextLoader.loadContext();
        System.out.println(ContextLoader.configuration);;
    }
}
