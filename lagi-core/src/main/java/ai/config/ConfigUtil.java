package ai.config;

import ai.config.pojo.GeneralConfig;

public class ConfigUtil {
    public static String CASCADE_API_ADDRESS;

    public static final String MODE_SERVER = "server";
    public static final String MODE_MATE = "mate";
    public static String APP_HOST;
    public static int APP_PORT;

    public static String APP_CONTAINER_URL;

    private ConfigUtil() {
    }

    public static void setCascadeApiAddress(String cascadeApiAddress) {
        CASCADE_API_ADDRESS = normalizeCascadeApiAddress(cascadeApiAddress);
    }

    public static String normalizeCascadeApiAddress(String cascadeApiAddress) {
        if (cascadeApiAddress == null) {
            return null;
        }
        String normalized = cascadeApiAddress.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        int queryStart = normalized.indexOf('?');
        if (queryStart >= 0) {
            normalized = normalized.substring(0, queryStart);
        }
        int hashStart = normalized.indexOf('#');
        if (hashStart >= 0) {
            normalized = normalized.substring(0, hashStart);
        }
        normalized = removeTrailingSlash(normalized);
        String lower = normalized.toLowerCase();
        String[] knownApiSuffixes = {
                "/v1/chat/completions",
                "/chat/completions",
                "/v1/responses",
                "/responses"
        };
        for (String suffix : knownApiSuffixes) {
            if (lower.endsWith(suffix)) {
                return removeTrailingSlash(normalized.substring(0, normalized.length() - suffix.length()));
            }
        }
        return normalized;
    }

    private static String removeTrailingSlash(String value) {
        String normalized = value;
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public static String getRunningMode() {
        GlobalConfigurations globalConfigurations = ContextLoader.configuration;
        if (globalConfigurations == null) {
            return MODE_SERVER;
        }

        GeneralConfig general = globalConfigurations.getGeneral();
        if (general == null || general.getMode() == null) {
            return MODE_SERVER;
        }

        String mode = general.getMode().trim();
        if (MODE_SERVER.equalsIgnoreCase(mode)) {
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
        if (getAppHost() == null) {
            return APP_CONTAINER_URL;
        }
        return "http://" + getAppHost() + ":" + getAppPort();
    }
}
