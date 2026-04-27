package ai.pnps.skills.util;

import ai.openai.pojo.Tool;
import ai.pnps.skills.pojo.ScriptExecutionResult;
import ai.pnps.skills.pojo.SkillExecutionPlan;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.*;

public class SkillUtil {

    private static final Logger log = LoggerFactory.getLogger(SkillUtil.class);

    private static final String DEFAULT_SKILL_FUNCTION_RESOURCE = "skills/function.json";

    private static final ObjectMapper TOOL_OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static volatile List<Tool> cachedSkillTools;

    private static final String[] READ_TOOL_NAMES = {"read", "read_file", "skill_view"};

    /**
     * Load the built-in skill tool definitions from the default classpath resource.
     */
    public static List<Tool> loadSkillTools() {
        return loadSkillTools(DEFAULT_SKILL_FUNCTION_RESOURCE);
    }

    /**
     * Load tool definitions from the given classpath resource and convert them to {@link Tool} objects.
     * Result is cached for the default resource path to avoid repeated I/O.
     */
    public static List<Tool> loadSkillTools(String resourcePath) {
        boolean useCache = DEFAULT_SKILL_FUNCTION_RESOURCE.equals(resourcePath);
        if (useCache) {
            List<Tool> cached = cachedSkillTools;
            if (cached != null) {
                return cached;
            }
        }
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = SkillUtil.class.getClassLoader();
        }
        try (InputStream in = cl.getResourceAsStream(resourcePath)) {
            if (in == null) {
                log.warn("Skill function resource not found: {}", resourcePath);
                return Collections.emptyList();
            }
            List<Tool> tools = TOOL_OBJECT_MAPPER.readValue(in, new TypeReference<List<Tool>>() {
            });
            List<Tool> result = tools == null ? Collections.<Tool>emptyList() : tools;
            if (useCache) {
                cachedSkillTools = result;
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to load skill tools from {}: {}", resourcePath, e.getMessage());
            return Collections.emptyList();
        }
    }

    public static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public static String firstNonBlank(String a, String b, String fallback) {
        String x = trimToNull(a);
        if (x != null) {
            return x;
        }
        String y = trimToNull(b);
        if (y != null) {
            return y;
        }
        return fallback == null ? "." : fallback;
    }

    public static SkillExecutionPlan.Script scriptFromProviderExecArgs(Map<String, String> args) {
        if (args == null) {
            return new SkillExecutionPlan.Script((String) null, null, null);
        }
        String command = trimToNull(args.get("command"));
        if (command != null) {
            String wd = firstNonBlank(args.get("workdir"), args.get("working_dir"), ".");
            return new SkillExecutionPlan.Script(shellArgvForCommand(command), wd);
        }
        List<String> argv = parseListArguments(args.get("argv"));
        if (!argv.isEmpty()) {
            String wd = firstNonBlank(args.get("working_dir"), args.get("workdir"), ".");
            return new SkillExecutionPlan.Script(argv, wd);
        }
        String legacyWd = firstNonBlank(args.get("working_dir"), args.get("workdir"), ".");
        return new SkillExecutionPlan.Script(
                args.get("path"),
                parseListArguments(args.get("args")),
                legacyWd);
    }

    /**
     * OpenClaw / provider-native exec: {@code command} is a shell string; run via {@code cmd /c} or {@code sh -c}.
     */
    private static List<String> shellArgvForCommand(String command) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return Arrays.asList("cmd", "/c", command);
        }
        return Arrays.asList("sh", "-c", command);
    }

    private static List<String> parseListArguments(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyList();
        }
        JsonNode n;
        try {
            n = new ObjectMapper().readTree(raw);
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
        if (n == null || !n.isArray()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode item : n) {
            if (item != null && !item.isNull()) {
                out.add(item.isTextual() ? item.asText() : item.toString());
            }
        }
        return out;
    }

    public static String formatExecToolResult(ScriptExecutionResult lastExec) {
        StringBuilder sb = new StringBuilder();
        sb.append("exit_code=").append(lastExec.getExitCode())
                .append(", timeout=").append(lastExec.isTimeout())
                .append(", no_output_timeout=").append(lastExec.isNoOutputTimeout())
                .append("\n");
        sb.append(lastExec.getOutput());
        return sb.toString();
    }

    public static boolean isReadTool(String toolName) {
        return Arrays.asList(READ_TOOL_NAMES).contains(toolName);
    }
}
