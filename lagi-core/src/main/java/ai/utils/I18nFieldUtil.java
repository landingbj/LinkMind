package ai.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utility for storing/parsing multilingual values in a single text column.
 *
 * Storage format:
 * {
 *   "default": "zh-CN",
 *   "values": {
 *     "zh-CN": "...",
 *     "en-US": "..."
 *   }
 * }
 *
 * Legacy plain-text values are auto-wrapped using a simple Chinese-character
 * heuristic to detect their source language.
 */
public final class I18nFieldUtil {

    public static final String DEFAULT_LANG = "zh-CN";

    private I18nFieldUtil() {
    }

    public static String detectLang(String text) {
        if (text == null) {
            return DEFAULT_LANG;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                return "zh-CN";
            }
        }
        return "en-US";
    }

    public static String normalizeLang(String lang) {
        if (lang == null) {
            return null;
        }
        String trimmed = lang.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.equalsIgnoreCase("en") || trimmed.toLowerCase().startsWith("en-")) {
            return "en-US";
        }
        if (trimmed.equalsIgnoreCase("zh") || trimmed.toLowerCase().startsWith("zh-")) {
            return "zh-CN";
        }
        return trimmed;
    }

    public static class I18nValue {
        private String defaultLang;
        private final Map<String, String> values = new LinkedHashMap<String, String>();

        public String getDefaultLang() {
            return defaultLang;
        }

        public Map<String, String> getValues() {
            return values;
        }

        public boolean has(String lang) {
            return lang != null && values.containsKey(lang);
        }

        public String resolve(String preferLang) {
            if (preferLang != null && values.containsKey(preferLang)) {
                return values.get(preferLang);
            }
            if (defaultLang != null && values.containsKey(defaultLang)) {
                return values.get(defaultLang);
            }
            if (!values.isEmpty()) {
                return values.values().iterator().next();
            }
            return "";
        }

        public String getDefaultValue() {
            return resolve(defaultLang);
        }

        public void put(String lang, String value) {
            if (lang == null || lang.isEmpty()) {
                return;
            }
            values.put(lang, value == null ? "" : value);
            if (defaultLang == null) {
                defaultLang = lang;
            }
        }

        public String toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("default", defaultLang == null ? DEFAULT_LANG : defaultLang);
            JsonObject vs = new JsonObject();
            for (Map.Entry<String, String> e : values.entrySet()) {
                vs.addProperty(e.getKey(), e.getValue() == null ? "" : e.getValue());
            }
            o.add("values", vs);
            return o.toString();
        }
    }

    public static I18nValue parse(String stored) {
        I18nValue v = new I18nValue();
        if (stored == null) {
            v.put(DEFAULT_LANG, "");
            return v;
        }
        String trimmed = stored.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                JsonElement el = JsonParser.parseString(trimmed);
                if (el != null && el.isJsonObject()) {
                    JsonObject obj = el.getAsJsonObject();
                    String defLang = null;
                    if (obj.has("default") && !obj.get("default").isJsonNull()) {
                        defLang = obj.get("default").getAsString();
                    }
                    if (obj.has("values") && obj.get("values").isJsonObject()) {
                        JsonObject vs = obj.getAsJsonObject("values");
                        for (Map.Entry<String, JsonElement> e : vs.entrySet()) {
                            JsonElement valEl = e.getValue();
                            v.values.put(e.getKey(), valEl.isJsonNull() ? "" : valEl.getAsString());
                        }
                    }
                    if (defLang != null && !defLang.isEmpty()) {
                        v.defaultLang = defLang;
                    } else if (!v.values.isEmpty()) {
                        v.defaultLang = v.values.keySet().iterator().next();
                    }
                    if (!v.values.isEmpty()) {
                        return v;
                    }
                }
            } catch (Exception ignored) {
                // Fall through to legacy handling below
            }
        }
        String legacy = stored == null ? "" : stored;
        String lang = detectLang(legacy);
        v.put(lang, legacy);
        return v;
    }

    /**
     * Wraps the supplied plain text into the JSON storage format, marking
     * the provided language (or the auto-detected one) as the default.
     */
    public static String wrapAsDefault(String text, String lang) {
        I18nValue v = new I18nValue();
        String detectedLang = normalizeLang(lang);
        if (detectedLang == null) {
            detectedLang = detectLang(text);
        }
        v.put(detectedLang, text == null ? "" : text);
        return v.toJson();
    }

    /**
     * Inserts or replaces the translated value for the given language while
     * preserving the default language and other translations.
     */
    public static String upsertTranslation(String stored, String lang, String translated) {
        I18nValue v = parse(stored);
        String normalized = normalizeLang(lang);
        if (normalized == null) {
            return v.toJson();
        }
        v.values.put(normalized, translated == null ? "" : translated);
        if (v.defaultLang == null) {
            v.defaultLang = normalized;
        }
        return v.toJson();
    }

    public static String resolve(String stored, String preferLang) {
        return parse(stored).resolve(normalizeLang(preferLang));
    }
}
