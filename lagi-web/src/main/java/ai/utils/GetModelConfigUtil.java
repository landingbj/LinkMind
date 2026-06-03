package ai.utils;

import ai.dto.LagiModelInfo;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class GetModelConfigUtil {
    private static final String URL = "https://saas.landingbj.com/saas/api/apikey/listModelInfo";
    private static final String DEFAULT_YML_PATH = "lagi-examples/config/OpenClaw/116/lagi.yml";
    private static final Gson GSON = new Gson();
    private static final Set<String> OPEN_ROUTER_PROVIDERS = new HashSet<>(Arrays.asList("google", "openai", "anthropic"));

    public static void main(String[] args) {
        String ymlPath = args.length > 0 ? args[0] : DEFAULT_YML_PATH;
        try {
            updateLagiYml(ymlPath);
            System.out.println("Updated model config in: " + ymlPath);
        } catch (IOException e) {
            System.err.println("Failed to update lagi.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void updateLagiYml(String ymlPath) throws IOException {
        String response = OkHttpUtil.get(URL);
        List<LagiModelInfo> modelInfos = parseResponse(response);
        ModelConfigData configData = buildModelConfigData(modelInfos);

        Path path = Paths.get(ymlPath);
        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        String updated = replaceModelFieldsInYaml(content, configData);
        Files.write(path, updated.getBytes(StandardCharsets.UTF_8));
    }

    private static List<LagiModelInfo> parseResponse(String response) {
        Type listType = new TypeToken<List<LagiModelInfo>>() {
        }.getType();
        List<LagiModelInfo> modelInfos = GSON.fromJson(response, listType);
        return modelInfos == null ? new ArrayList<LagiModelInfo>() : modelInfos;
    }

    private static ModelConfigData buildModelConfigData(List<LagiModelInfo> modelInfos) {
        Map<String, List<String>> byProvider = new LinkedHashMap<String, List<String>>();
        List<String> landingModels = new ArrayList<String>();
        List<String> landingOpenRouterModels = new ArrayList<String>();
        List<String> openRouterModels = new ArrayList<String>();

        for (LagiModelInfo modelInfo : modelInfos) {
            if (modelInfo == null || modelInfo.getProvider() == null || modelInfo.getModelName() == null) {
                continue;
            }
            String provider = modelInfo.getProvider();
            String modelName = modelInfo.getModelName();

            List<String> providerModels = byProvider.get(provider);
            if (providerModels == null) {
                providerModels = new ArrayList<String>();
                byProvider.put(provider, providerModels);
            }
            providerModels.add(modelName);

            if (!OPEN_ROUTER_PROVIDERS.contains(provider)) {
                landingModels.add(provider + "/" + modelName);
            } else {
                landingOpenRouterModels.add(provider + "/" + modelName);
                openRouterModels.add(modelName);
            }
        }

        return new ModelConfigData(byProvider, landingModels, landingOpenRouterModels, openRouterModels);
    }

    /**
     * Replace only {@code model:} lines under the top-level {@code models:} section via string processing,
     * so YAML comments and formatting elsewhere stay intact.
     */
    static String replaceModelFieldsInYaml(String content, ModelConfigData configData) {
        int modelsStart = indexOfLineKey(content, "models:");
        if (modelsStart < 0) {
            return content;
        }
        int modelsBodyStart = content.indexOf('\n', modelsStart) + 1;
        int modelsEnd = indexOfLineKey(content, "stores:");
        if (modelsEnd < 0 || modelsEnd <= modelsBodyStart) {
            return content;
        }

        String modelsSection = content.substring(modelsBodyStart, modelsEnd);
        String updatedModelsSection = replaceModelFieldsInModelsSection(modelsSection, configData);
        return content.substring(0, modelsBodyStart) + updatedModelsSection + content.substring(modelsEnd);
    }

    private static int indexOfLineKey(String content, String key) {
        int from = 0;
        while (from < content.length()) {
            int lineStart = from;
            int lineEnd = content.indexOf('\n', from);
            if (lineEnd < 0) {
                lineEnd = content.length();
            }
            String line = content.substring(lineStart, lineEnd);
            if (line.equals(key) || line.startsWith(key + " ")) {
                return lineStart;
            }
            from = lineEnd < content.length() ? lineEnd + 1 : content.length();
        }
        return -1;
    }

    private static String replaceModelFieldsInModelsSection(String section, ModelConfigData configData) {
        String lineSeparator = section.contains("\r\n") ? "\r\n" : "\n";
        String[] lines = section.split(lineSeparator, -1);
        StringBuilder result = new StringBuilder(section.length());

        String currentName = null;
        String currentType = null;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            if (line.startsWith("  - name:")) {
                currentName = trimYamlValue(line.substring("  - name:".length()));
                currentType = null;
            } else if (line.startsWith("    type:")) {
                currentType = trimYamlValue(line.substring("    type:".length()));
            } else if (line.startsWith("    model:")) {
                String newModels = configData.resolveModelList(currentName, currentType);
                if (newModels != null) {
                    line = "    model: " + newModels;
                }
            }

            result.append(line);
            if (i < lines.length - 1) {
                result.append(lineSeparator);
            }
        }
        return result.toString();
    }

    private static String trimYamlValue(String value) {
        return value == null ? null : value.trim();
    }

    private static String joinWithComma(List<String> values) {
        StringJoiner joiner = new StringJoiner(",");
        for (String value : values) {
            joiner.add(value);
        }
        return joiner.toString();
    }

    private static final class ModelConfigData {
        private final Map<String, List<String>> byProvider;
        private final String landingModels;
        private final String landingOpenRouterModels;
        private final String openRouterModels;

        ModelConfigData(Map<String, List<String>> byProvider,
                        List<String> landingModels,
                        List<String> landingOpenRouterModels,
                        List<String> openRouterModels) {
            this.byProvider = byProvider;
            this.landingModels = joinWithComma(landingModels);
            this.landingOpenRouterModels = joinWithComma(landingOpenRouterModels);
            this.openRouterModels = joinWithComma(openRouterModels);
        }

        String resolveModelList(String name, String type) {
            if ("landing".equals(name)) {
                return landingModels;
            }
            if ("landing-openrouter".equals(name)) {
                return landingOpenRouterModels;
            }
            if ("openrouter".equals(name)) {
                return openRouterModels;
            }
            if (type != null && byProvider.containsKey(type)) {
                return joinWithComma(byProvider.get(type));
            }
            return null;
        }
    }
}
