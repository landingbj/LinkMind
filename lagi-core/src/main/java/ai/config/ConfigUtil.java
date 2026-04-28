package ai.config;

import ai.config.pojo.SkillsConfig;

public class ConfigUtil {
    public static String CASCADE_API_ADDRESS;

    public static final String MODE_SERVER = "server";
    public static final String MODE_MATE = "mate";
    public static String APP_HOST;
    public static int APP_PORT;

    private ConfigUtil() {
    }

    public static String getRunningMode() {
        GlobalConfigurations globalConfigurations = ContextLoader.configuration;
        if (globalConfigurations == null) {
            return MODE_MATE;
        }

        SkillsConfig skillsConfig = globalConfigurations.getSkills();
        if (skillsConfig == null) {
            return MODE_MATE;
        }

        String rule = skillsConfig.getRule();
        if (rule != null && MODE_SERVER.equalsIgnoreCase(rule.trim())) {
            return MODE_SERVER;
        }

        return MODE_MATE;
    }

    public static String getAppHost() {
        return APP_HOST;
    }

    public static int getAppPort() {
        return APP_PORT;
    }

    public static void setAppPort(int port) {
        APP_PORT = port;
    }

    public static void setAppHost(String host) {
        APP_HOST = host;
    }

    public static String getBaseUrl() {
        return "http://" + getAppHost() + ":" + getAppPort();
    }
}
