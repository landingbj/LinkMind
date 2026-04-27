package ai.pnps.skills;

import ai.pnps.skills.pojo.*;
import lombok.Getter;

import java.net.URI;
import java.net.URL;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SkillLoader {
    @Getter
    public static final class LoadOptions {
        private final int maxCandidatesPerRoot;
        private final int maxSkillsLoadedPerSource;
        private final long maxSkillFileBytes;

        public LoadOptions(int maxCandidatesPerRoot, int maxSkillsLoadedPerSource, long maxSkillFileBytes) {
            this.maxCandidatesPerRoot = maxCandidatesPerRoot;
            this.maxSkillsLoadedPerSource = maxSkillsLoadedPerSource;
            this.maxSkillFileBytes = maxSkillFileBytes;
        }

    }

    @Getter
    public static final class SkillSourceSpec {
        private final Path dir;
        private final String source;
        private final int precedence;

        public SkillSourceSpec(Path dir, String source, int precedence) {
            this.dir = dir;
            this.source = source;
            this.precedence = precedence;
        }

    }

    private static final int DEFAULT_MAX_CANDIDATES_PER_ROOT = 300;
    private static final int DEFAULT_MAX_SKILLS_LOADED_PER_SOURCE = 200;
    private static final long DEFAULT_MAX_SKILL_FILE_BYTES = 256_000L;

    private static final LoadOptions DEFAULT_LOAD_OPTIONS =
            new LoadOptions(DEFAULT_MAX_CANDIDATES_PER_ROOT, DEFAULT_MAX_SKILLS_LOADED_PER_SOURCE, DEFAULT_MAX_SKILL_FILE_BYTES);

    public List<SkillEntry> load(List<?> roots) {
        if (roots == null || roots.isEmpty()) {
            return Collections.emptyList();
        }
        List<SkillSourceSpec> sources = new ArrayList<SkillSourceSpec>();
        int precedence = 0;
        for (Object root : roots) {
            Path resolvedRoot = resolveRoot(root);
            if (resolvedRoot == null) {
                continue;
            }
            sources.add(new SkillSourceSpec(resolvedRoot, "legacy", precedence++));
        }
        return loadBySources(sources, DEFAULT_LOAD_OPTIONS);
    }

    public SkillEntry load(List<?> roots, String skillName) {
        if (skillName == null || skillName.trim().isEmpty()) {
            return null;
        }
        List<SkillEntry> loaded = load(roots);
        if (loaded.isEmpty()) {
            return null;
        }
        for (SkillEntry entry : loaded) {
            if (entry == null) {
                continue;
            }
            String name = trim(entry.getName());
            if (skillName.equalsIgnoreCase(name)) {
                return entry;
            }
        }
        return null;
    }   

    public List<SkillEntry> loadDefaultWorkspaceRoots(Path workspaceDir) {
        List<SkillSourceSpec> roots = new ArrayList<SkillSourceSpec>();
        int precedence = 0;
        if (workspaceDir != null) {
            roots.add(new SkillSourceSpec(workspaceDir.resolve("skills"), "workspace", precedence++));
            roots.add(new SkillSourceSpec(workspaceDir.resolve(".agents").resolve("skills"), "agents-skills-project", precedence++));
        }
        String home = System.getProperty("user.home");
        if (home != null && !home.trim().isEmpty()) {
            roots.add(new SkillSourceSpec(Paths.get(home, ".agents", "skills"), "agents-skills-personal", precedence));
        }
        return loadBySources(roots, DEFAULT_LOAD_OPTIONS);
    }

    public List<SkillEntry> loadBySources(List<SkillSourceSpec> sources, LoadOptions options) {
        if (sources == null || sources.isEmpty()) {
            return Collections.emptyList();
        }
        List<SkillSourceSpec> sorted = new ArrayList<SkillSourceSpec>(sources);
        sorted.sort(Comparator.comparingInt(SkillSourceSpec::getPrecedence));
        Map<String, SkillEntry> merged = new LinkedHashMap<String, SkillEntry>();
        for (SkillSourceSpec source : sorted) {
            if (source == null || source.getDir() == null) {
                continue;
            }
            List<SkillEntry> loaded = loadFromRoot(source.getDir(), source.getSource(), options == null ? DEFAULT_LOAD_OPTIONS : options);
            for (SkillEntry skill : loaded) {
                merged.put(skill.getKey(), skill);
            }
        }
        return new ArrayList<>(merged.values());
    }

    private Path resolveRoot(Object root) {
        if (root == null) {
            return null;
        }
        if (root instanceof Path) {
            return (Path) root;
        }
        if (root instanceof String) {
            return resolveRootString((String) root);
        }
        return null;
    }

    private Path resolveRootString(String root) {
        if (root == null) {
            return null;
        }
        String normalized = root.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.startsWith("classpath:")) {
            String classpathLocation = normalized.substring("classpath:".length()).trim();
            if (classpathLocation.startsWith("/")) {
                classpathLocation = classpathLocation.substring(1);
            }
            if (classpathLocation.isEmpty()) {
                return null;
            }
            URL url = Thread.currentThread().getContextClassLoader().getResource(classpathLocation);
            if (url == null) {
                url = SkillLoader.class.getClassLoader().getResource(classpathLocation);
            }
            if (url == null) {
                return null;
            }
            try {
                URI uri = url.toURI();
                if (!"file".equalsIgnoreCase(uri.getScheme())) {
                    return null;
                }
                return Paths.get(uri);
            } catch (Exception ignored) {
                return null;
            }
        }
        return Paths.get(normalized);
    }


    private List<SkillEntry> loadFromRoot(Path root, String source, LoadOptions options) {
        try {
            Path realRoot = root.toRealPath();
            if (!Files.isDirectory(realRoot)) {
                return Collections.emptyList();
            }
            Path scanRoot = resolveNestedSkillsRoot(realRoot);
            List<Path> candidates = new ArrayList<Path>();
            Path rootSkill = scanRoot.resolve("SKILL.md");
            if (Files.isRegularFile(rootSkill)) {
                candidates.add(scanRoot);
            } else {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(scanRoot)) {
                    for (Path child : stream) {
                        String name = child.getFileName() == null ? "" : child.getFileName().toString();
                        if (name.startsWith(".") || "node_modules".equals(name)) {
                            continue;
                        }
                        if (Files.isDirectory(child) && Files.isRegularFile(child.resolve("SKILL.md"))) {
                            candidates.add(child);
                        }
                    }
                }
            }
            candidates.sort(Comparator.comparing(Path::toString));
            int maxSkillsLoadedPerSource = Math.max(0, options.getMaxSkillsLoadedPerSource());
            if (maxSkillsLoadedPerSource > 0 && candidates.size() > maxSkillsLoadedPerSource) {
                candidates = new ArrayList<Path>(candidates.subList(0, maxSkillsLoadedPerSource));
            }

            List<SkillEntry> entries = new ArrayList<SkillEntry>();
            for (Path skillDir : candidates) {
                SkillEntry entry = loadSingle(realRoot, skillDir, source, options);
                if (entry != null) {
                    entries.add(entry);
                }
            }
            return entries;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private Path resolveNestedSkillsRoot(Path root) {
        Path nested = root.resolve("skills");
        if (Files.isDirectory(nested)) {
            return nested;
        }
        return root;
    }

    private SkillEntry loadSingle(Path root, Path skillDir, String source, LoadOptions options) throws IOException {
        Path realDir = skillDir.toRealPath();
        if (!realDir.startsWith(root)) {
            return null;
        }
        Path skillMd = realDir.resolve("SKILL.md");
        if (!Files.isRegularFile(skillMd)) {
            return null;
        }
        if (options.getMaxSkillFileBytes() > 0 && Files.size(skillMd) > options.getMaxSkillFileBytes()) {
            return null;
        }
        String raw = new String(Files.readAllBytes(skillMd), StandardCharsets.UTF_8);
        Frontmatter fm = parseFrontmatter(raw);
        String name = pickName(fm.map.get("name"), realDir.getFileName() == null ? "skill" : realDir.getFileName().toString());
        String description = pickName(fm.map.get("description"), "");
        if (description.isEmpty()) {
            return null;
        }
        String key = normalizeKey(name);
        SkillInvocationPolicy invocation = resolveInvocationPolicy(fm.map);
        SkillExposure exposure = resolveExposure(invocation, fm.map);
        OpenClawSkillMetadata metadata = resolveMetadata(fm.map);
        return new SkillEntry(key, name, description, realDir, skillMd, fm.map, invocation, exposure, metadata, source, null);
    }

    private SkillInvocationPolicy resolveInvocationPolicy(Map<String, String> frontmatter) {
        boolean disableModelInvocation = readBoolean(frontmatter, Arrays.asList("disable-model-invocation", "disable_model_invocation"), false);
        boolean userInvocable = readBoolean(frontmatter, Arrays.asList("user-invocable", "user_invocable"), true);
        return new SkillInvocationPolicy(userInvocable, disableModelInvocation);
    }

    private SkillExposure resolveExposure(SkillInvocationPolicy invocation, Map<String, String> frontmatter) {
        boolean includeInAvailableSkillsPrompt = !readBoolean(
                frontmatter, Arrays.asList("exclude-from-available-skills-prompt", "exclude_from_available_skills_prompt"), false
        );
        if (frontmatter.containsKey("include-in-available-skills-prompt") || frontmatter.containsKey("include_in_available_skills_prompt")) {
            includeInAvailableSkillsPrompt = readBoolean(
                    frontmatter, Arrays.asList("include-in-available-skills-prompt", "include_in_available_skills_prompt"), includeInAvailableSkillsPrompt
            );
        } else if (invocation != null && invocation.isDisableModelInvocation()) {
            includeInAvailableSkillsPrompt = false;
        }
        boolean includeInRuntimeRegistry = readBoolean(
                frontmatter,
                Arrays.asList("include-in-runtime-registry", "include_in_runtime_registry"),
                true
        );
        boolean userInvocable = invocation == null || invocation.isUserInvocable();
        return new SkillExposure(includeInRuntimeRegistry, includeInAvailableSkillsPrompt, userInvocable);
    }

    private OpenClawSkillMetadata resolveMetadata(Map<String, String> frontmatter) {
        String primaryEnv = trim(frontmatter.get("primary-env"));
        return new OpenClawSkillMetadata(primaryEnv, Collections.<String>emptyList(), Collections.<String>emptyList(), Collections.<String>emptyList());
    }

    private boolean readBoolean(Map<String, String> frontmatter, List<String> keys, boolean defaultValue) {
        for (String key : keys) {
            String raw = trim(frontmatter.get(key));
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            if ("true".equalsIgnoreCase(raw) || "1".equals(raw) || "yes".equalsIgnoreCase(raw)) {
                return true;
            }
            if ("false".equalsIgnoreCase(raw) || "0".equals(raw) || "no".equalsIgnoreCase(raw)) {
                return false;
            }
        }
        return defaultValue;
    }

    private String trim(String text) {
        return text == null ? null : text.trim();
    }

    private Frontmatter parseFrontmatter(String raw) {
        if (raw == null) {
            return new Frontmatter(new LinkedHashMap<String, String>());
        }
        String normalized = raw.replace("\r\n", "\n");
        if (!normalized.startsWith("---\n")) {
            return new Frontmatter(new LinkedHashMap<String, String>());
        }
        int end = normalized.indexOf("\n---\n", 4);
        if (end < 0) {
            return new Frontmatter(new LinkedHashMap<String, String>());
        }
        String yaml = normalized.substring(4, end);
        Map<String, String> map = new LinkedHashMap<String, String>();
        String[] lines = yaml.split("\n");
        for (String line : lines) {
            int idx = line.indexOf(':');
            if (idx <= 0) {
                continue;
            }
            String k = line.substring(0, idx).trim().toLowerCase();
            String v = line.substring(idx + 1).trim();
            if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                v = v.substring(1, v.length() - 1);
            }
            if (!k.isEmpty()) {
                map.put(k, v);
            }
        }
        return new Frontmatter(map);
    }

    private String pickName(String v, String fallback) {
        String value = v == null ? "" : v.trim();
        return value.isEmpty() ? fallback : value;
    }

    private String normalizeKey(String name) {
        String lower = name == null ? "" : name.trim().toLowerCase();
        lower = lower.replaceAll("[^a-z0-9._-]+", "_");
        return lower.isEmpty() ? "skill" : lower;
    }

    private static final class Frontmatter {
        private final Map<String, String> map;

        private Frontmatter(Map<String, String> map) {
            this.map = map;
        }
    }
}
