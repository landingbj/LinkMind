package ai.config;

import ai.config.pojo.SkillsConfig;

public class ConfigUtil {
    public static String CASCADE_API_ADDRESS;

    public static final String MODE_SERVER = "server";
    public static final String MODE_MATE = "mate";

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
}
