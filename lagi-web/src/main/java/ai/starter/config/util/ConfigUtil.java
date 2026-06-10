package ai.starter.config.util;

import ai.common.pojo.Backend;
import ai.config.GlobalConfigurations;
import ai.config.pojo.ModelFunction;
import ai.starter.InstallerUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ConfigUtil {

    private static final String CONFIG_FILE_PROPERTY = InstallerUtil.CONFIG_FILE_PROPERTY;

    public static Path getLagiYmlPath() {
        String configFilePath = System.getProperty(CONFIG_FILE_PROPERTY);
        if (configFilePath == null || configFilePath.trim().isEmpty()) {
            return null;
        }
        return Paths.get(configFilePath);
    }

    public static void setConfig(GlobalConfigurations config, List<Backend> backends) {
        if (config == null || backends == null || backends.isEmpty()) {
            return;
        }
        if (config.getFunctions() == null || config.getFunctions().getChat() == null) {
            return;
        }
        ModelFunction chat = config.getFunctions().getChat();

        for (Backend sourceBackend : backends) {
            if (sourceBackend == null) {
                continue;
            }

            // build models
            List<Backend> models = config.getModels();
            Backend modelBackend = new Backend();
            BeanUtil.copyProperties(sourceBackend, modelBackend, "protocol");
            if (models == null || models.isEmpty()) {
                List<Backend> newModels = new ArrayList<>();
                newModels.add(modelBackend);
                config.setModels(newModels);
                modelBackend.setBackend(null);
                modelBackend.setProtocol(null);
            } else {
                appendOrSetBackend(models, modelBackend);
                modelBackend.setBackend(null);
                modelBackend.setProtocol(null);
            }

            // build chat function
            List<Backend> chatBackends = chat.getBackends();
            Backend chatBackend = new Backend();
            BeanUtil.copyProperties(sourceBackend, chatBackend, "endpoint", "apiAddress", "driver", "drivers", "apiKey", "secretKey", "appId", "appKey",
                    "accessKeyId", "accessKeySecret", "securityKey", "accessToken", "others", "alias", "cacheEnable",
                    "cacheDir", "router", "dependingOnTheContext", "filter", "concurrency", "function");
            chatBackend.setBackend(sourceBackend.getName());
            if (chatBackends == null || chatBackends.isEmpty()) {
                chat.setRoute(StrUtil.format("best({})", chatBackend.getName()));
                List<Backend> newChatBackends = new ArrayList<>();
                newChatBackends.add(chatBackend);
                chat.setBackends(newChatBackends);
                chatBackend.setName(null);
                continue;
            }

            String route = chat.getRoute();
            String format = StrUtil.format("best(({}),", chatBackend.getName());
            if (StrUtil.isNotBlank(route) && !route.startsWith(format)) {
                String replace = route.replace("best(", format);
                chat.setRoute(replace);
            }
            appendOrSetBackend(chatBackends, chatBackend);
            chatBackend.setName(null);

        }
    }

    private static void appendOrSetBackend(List<Backend> targetBackends, Backend sourceBackend) {
        boolean replaced = false;
        for (int i = 0; i < targetBackends.size(); i++) {
            Backend backend = targetBackends.get(i);
            if (backend == null) {
                continue;
            }
            String name = backend.getName() == null ? backend.getBackend() : backend.getName();
            if (name != null && name.equals(sourceBackend.getName())) {
                targetBackends.set(i, sourceBackend);
                replaced = true;
            }
        }
        if (!replaced) {
            targetBackends.add(sourceBackend);
        }
    }

}
