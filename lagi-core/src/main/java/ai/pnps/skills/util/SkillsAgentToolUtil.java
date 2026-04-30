package ai.pnps.skills.util;

import ai.config.ContextLoader;
import ai.config.pojo.SkillsConfig;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ToolCall;
import ai.pnps.skills.SkillLoader;
import ai.pnps.skills.filesystem.Filesystem;
import ai.pnps.skills.pojo.SkillEntry;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static helpers for {@link ai.pnps.skills.SkillsAgent} tool-call parsing and OpenClaw-style tool arguments.
 */
public final class SkillsAgentToolUtil {
    private static final SkillLoader skillLoader = new SkillLoader();

    private SkillsAgentToolUtil() {
    }

    public static Map<String, String> parseArguments(String raw) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        JsonNode node = SkillsJsons.parseObject(raw);
        if (node == null || !node.isObject()) {
            return map;
        }
        node.fields().forEachRemaining(e -> {
            String v = e.getValue() == null || e.getValue().isNull()
                    ? null
                    : (e.getValue().isTextual() ? e.getValue().asText() : e.getValue().toString());
            map.put(e.getKey(), v);
        });
        return map;
    }

    /**
     * Read tool path: prefers existing {@code file_path}, else {@code path}.
     */
    public static Path resolveReadPathFromToolArgs(Map<String, String> args) {
        return FilesystemToolUtil.resolveReadPath(args);
    }

    /**
     * Path for write/edit: {@code path} or {@code file_path} (file need not exist yet).
     */
    public static Path resolveFilePathFromToolArgs(Map<String, String> args) {
        if (args == null) {
            return null;
        }
        String p = SkillUtil.trimToNull(args.get("path"));
        String fp = SkillUtil.trimToNull(args.get("file_path"));
        if (p != null) {
            return Paths.get(p);
        }
        if (fp != null) {
            return Paths.get(fp);
        }
        return null;
    }

    /**
     * Write tool: {@code content} (required by schema; may be empty string).
     */
    public static String toolArgContent(Map<String, String> args) {
        if (args == null || !args.containsKey("content")) {
            return null;
        }
        String c = args.get("content");
        return c == null ? "" : c;
    }

    public static String toolArgOldText(Map<String, String> args) {
        if (args == null) {
            return null;
        }
        if (args.containsKey("oldText")) {
            return args.get("oldText");
        }
        if (args.containsKey("old_string")) {
            return args.get("old_string");
        }
        return null;
    }

    public static String toolArgNewText(Map<String, String> args) {
        if (args == null) {
            return "";
        }
        if (args.containsKey("newText")) {
            String v = args.get("newText");
            return v == null ? "" : v;
        }
        if (args.containsKey("new_string")) {
            String v = args.get("new_string");
            return v == null ? "" : v;
        }
        return "";
    }

    public static Path resolveExecPathFromToolArgs(Map<String, String> args) {
        return FilesystemToolUtil.resolveExecWorkDir(args);
    }

    public static long resolveExecTimeoutSeconds(Map<String, String> args, long defaultSeconds) {
        return FilesystemToolUtil.resolveExecTimeoutSeconds(args, defaultSeconds);
    }

    public static String toolCallFunctionName(ToolCall toolCall) {
        if (toolCall.getFunction() != null && toolCall.getFunction().getName() != null) {
            return toolCall.getFunction().getName();
        }
        return null;
    }

    public static String toolArgumentsJson(ToolCall toolCall) {
        return toolCall.getFunction() != null ? toolCall.getFunction().getArguments() : null;
    }



    public static Integer parseOptionalPositiveInt(Map<String, String> args, String key) {
        return FilesystemToolUtil.parseOptionalPositiveInt(args, key);
    }

    public static Integer parseOptionalNonNegativeInt(Map<String, String> args, String key) {
        return FilesystemToolUtil.parseOptionalNonNegativeInt(args, key);
    }

    public static String readFromToolArgs(Filesystem fs, Map<String, String> args, ChatCompletionRequest request) {
        String content = fs.read(args);
        if (args == null) {
            return content;
        }
        if (args.containsKey("file_path")) {
            String pathStr = args.get("file_path");
            args.put("path", pathStr);
        }
        if (args.containsKey("path")) {
            String pathStr = args.get("path");
            String skillName = getSkillName(pathStr);
            if (skillName == null) {
                return content;
            } else if (skillName.equals("social-channel")) {
                String skillBaseDir = resolveSkillBaseDir(pathStr);
                return SocialSkillUtil.generateSkill(skillBaseDir, content, request);
            }
            return content;
        }
        if (args.containsKey("name")) {
            String skillName = getRealSkillName(args.get("name"));
            SkillsConfig skillsConfig = ContextLoader.configuration.getSkills();
            List<String> configuredRoots = skillsConfig == null ? null : skillsConfig.getRoots();
            SkillEntry skillEntry = skillLoader.load(configuredRoots, skillName);
            String skillMdPath = skillEntry.getSkillMdPath().toAbsolutePath().toString();
            args.put("path", skillMdPath);
            content = fs.read(args);
            if (skillName.equals("social-channel")) {
                String skillBaseDir = resolveSkillBaseDir(skillMdPath);
                return SocialSkillUtil.generateSkill(skillBaseDir, content, request);
            }
        }
        return content;
    }

    /**
     * Returns the absolute parent directory of the given SKILL.md path,
     * which is used as the {@code skillBaseDir} for placeholder rendering.
     */
    private static String resolveSkillBaseDir(String skillMdPath) {
        if (skillMdPath == null || skillMdPath.isEmpty()) {
            return "";
        }
        try {
            Path p = Paths.get(skillMdPath).toAbsolutePath();
            Path parent = p.getParent();
            return parent == null ? "" : parent.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String getSkillName(String path) {
        String trimmedPath = SkillUtil.trimToNull(path);
        if (trimmedPath == null) {
            return null;
        }

        Path skillPath = Paths.get(trimmedPath);
        Path fileName = skillPath.getFileName();
        if (fileName == null || !"SKILL.md".equals(fileName.toString())) {
            return null;
        }

        Path parent = skillPath.getParent();
        if (parent == null || parent.getFileName() == null) {
            return null;
        }
        return parent.getFileName().toString();
    }

    private static final String LANDINGBJ_SKILL_PREFIX = "landingbj/";

    public static String getLandingbjSkillName(String name) {
        if (name == null) {
            return null;
        }
        return LANDINGBJ_SKILL_PREFIX + name;
    }

    public static String getRealSkillName(String name) {
        if (name == null) {
            return null;
        }
        return name.replace(LANDINGBJ_SKILL_PREFIX, "");
    }

    public static boolean isLandingbjSkillName(String name) {
        if (name == null) {
            return false;
        }
        return name.startsWith(LANDINGBJ_SKILL_PREFIX);
    }
}
