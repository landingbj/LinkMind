package ai.starter;

import ai.pnps.skills.SkillLoader;
import ai.pnps.skills.pojo.SkillEntry;
import ai.starter.config.ConfigSyncService;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

@Slf4j
public class InstallerUtil {
    public static final String CONFIG_FILE_PROPERTY = "linkmind.config";
    private static final String EXPORT_TO_OPENCLAW_ARG = "--export-to-openclaw=";
    private static final String IMPORT_FROM_OPENCLAW_ARG = "--import-from-openclaw=";
    private static final String RUNTIME_CHOICE_ARG = "--runtime-choice=";
    private static final String LINKMIND_API_KEY_ARG = "--linkmind-api-key=";
    public static final String LINKMIND_API_KEY_PROPERTY = "linkmind.apiKey";
    public static final String DEER_FLOW_PATH = "--deer-flow-path=";
    public static final String OPENHUMAN_PATH = "--openhuman-path=";
    private static final String SKILLS_ROOT_ARG = "--skills-root=";
    private static final String INSTALL_MEDUSA_ARG = "--install-medusa=";
    private static final String LAGI_YML = "lagi.yml";
    private static final String SQLITE_RESOURCE_DIR = "sqlite";
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_HOST = "localhost";

    public static void main(String[] args) throws IOException {
        File jarFile = InstallerUtil.getJarFile();
        InstallerUtil.applyConfigAndDataDir(args, jarFile);
        InstallerUtil.applyLinkMindApiKey(args);
        InstallerUtil.applyRuntimeSkillsConfig(args);
        String deerFlowPath = parseArg(args, DEER_FLOW_PATH);
        String openHumanPath = parseArg(args, OPENHUMAN_PATH);
        ConfigSyncService configSyncService = new ConfigSyncService(DEFAULT_PORT, new String [] {"", deerFlowPath, "", openHumanPath });
        String injectAgent = parseArg(args, "--inject-agent=");
        int witch = Integer.parseInt(injectAgent == null ? "0" : injectAgent);
        configSyncService.sync(witch, witch);
//        OpenClawUtil.sync(DEFAULT_PORT, exportToOpenClaw, importFromOpenClaw);
    }

    public static int resolvePort(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--port=")) {
                return Integer.parseInt(arg.substring("--port=".length()));
            }
        }
        String envPort = System.getProperty("server.port", System.getenv("SERVER_PORT"));
        if (envPort != null && !envPort.isEmpty()) {
            return Integer.parseInt(envPort);
        }
        return DEFAULT_PORT;
    }

    /**
     * Binds Tomcat; override with --host=, server.address, or SERVER_ADDRESS.
     */
    public static String resolveHost(String[] args) {
        String fromArg = InstallerUtil.parseArg(args, "--host=");
        if (fromArg != null && !fromArg.isEmpty()) {
            return fromArg;
        }
        String envHost = System.getProperty("server.address", System.getenv("SERVER_ADDRESS"));
        if (envHost != null && !envHost.isEmpty()) {
            return envHost.trim();
        }
        return DEFAULT_HOST;
    }

    public static boolean resolveExportToOpenClaw(String[] args) {
        return Boolean.parseBoolean(parseArg(args, EXPORT_TO_OPENCLAW_ARG));
    }

    public static boolean resolveImportFromOpenClaw(String[] args) {
        return Boolean.parseBoolean(parseArg(args, IMPORT_FROM_OPENCLAW_ARG));
    }

    public static String resolveRuntimeChoice(String[] args) {
        String value = parseArg(args, RUNTIME_CHOICE_ARG);
        if (value == null || value.isEmpty()) {
            return "mate";
        }
        String normalized = value.trim().toLowerCase();
        if (Arrays.asList("2", "server").contains(normalized)) {
            return "server";
        }
        return "mate";
    }

    public static boolean resolveEnableSync(String[] args) {
        String val = parseArg(args, "--enable-sync=");
        if (val == null) {
            val = System.getProperty("enable.sync", System.getenv("ENABLE_SYNC"));
        }
        if (val != null && !val.isEmpty()) {
            return Boolean.parseBoolean(val);
        }
        return true;
    }

    public static File getJarFile() {
        try {
            URL location = InstallerUtil.class.getProtectionDomain().getCodeSource().getLocation();
            URI uri = location.toURI();
            Path jarPath;
            if ("jar".equals(uri.getScheme())) {
                String ssp = uri.getRawSchemeSpecificPart();
                int excl = ssp.indexOf('!');
                String filePath = excl >= 0 ? ssp.substring(0, excl) : ssp;
                jarPath = Paths.get(URI.create(filePath));
            } else {
                jarPath = Paths.get(uri);
            }
            File f = jarPath.toFile();
            return f.isFile() ? f : null;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /**
     * Parses --config= and --data-dir= (or system property / env), applies defaults for IDE vs JAR,
     * copies resources when running from JAR and default dirs are used, then sets system properties.
     */
    public static void applyConfigAndDataDir(String[] args, File jarFile) throws IOException {
        String configArg = parseArg(args, "--config=");
        if (configArg == null) configArg = System.getProperty("config.dir", System.getenv("CONFIG_DIR"));
        String dataDirArg = parseArg(args, "--data-dir=");
        if (dataDirArg == null) dataDirArg = System.getProperty("data.dir", System.getenv("DATA_DIR"));

        Path configDir;
        Path dataDir;

        if (jarFile == null) {
            // IDE: defaults point to resources directory
            configDir = configArg != null && !configArg.isEmpty()
                    ? Paths.get(configArg).toAbsolutePath().normalize()
                    : getResourceDir(LAGI_YML);
            dataDir = dataDirArg != null && !dataDirArg.isEmpty()
                    ? Paths.get(dataDirArg).toAbsolutePath().normalize()
                    : getResourceDir(SQLITE_RESOURCE_DIR + "/saas.db");
        } else {
            // JAR: defaults are jarDir/config and jarDir/data; copy from resources if missing
            Path jarDir = jarFile.getParentFile().toPath();
            configDir = configArg != null && !configArg.isEmpty()
                    ? Paths.get(configArg).toAbsolutePath().normalize()
                    : jarDir.resolve("config");
            dataDir = dataDirArg != null && !dataDirArg.isEmpty()
                    ? Paths.get(dataDirArg).toAbsolutePath().normalize()
                    : jarDir.resolve("data");

            if (configArg == null || configArg.isEmpty()) {
                Files.createDirectories(configDir);
                copyResourceIfAbsent(InstallerUtil.class.getClassLoader(), LAGI_YML, configDir.resolve(LAGI_YML));
            }
            if (dataDirArg == null || dataDirArg.isEmpty()) {
                Files.createDirectories(dataDir);
                copySqliteToDataDir(jarFile.toPath(), dataDir);
            }
        }

        System.setProperty(CONFIG_FILE_PROPERTY, configDir.resolve(LAGI_YML).toString());
        System.setProperty(ai.common.db.HikariDS.DATA_DIR_PROPERTY, dataDir.toString());
        log.debug("Config: {} (lagi.yml)", configDir);
        log.debug("Data dir: {} (saas.db)", dataDir);
    }

    public static String parseArg(String[] args, String prefix) {
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                String value = arg.substring(prefix.length()).trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    public static void applyLinkMindApiKey(String[] args) {
        String apiKey = parseArg(args, LINKMIND_API_KEY_ARG);
        if (apiKey == null) {
            apiKey = System.getenv("LINKMIND_API_KEY");
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return;
        }
        System.setProperty(LINKMIND_API_KEY_PROPERTY, apiKey.trim());
    }

    private static void applyRuntimeSkillsConfig(String[] args) throws IOException {
        String configFile = System.getProperty(CONFIG_FILE_PROPERTY);
        if (configFile == null || configFile.trim().isEmpty()) {
            log.warn("Skip runtime config: {} is empty", CONFIG_FILE_PROPERTY);
            return;
        }
        Path configPath = Paths.get(configFile).toAbsolutePath().normalize();
        String runtimeChoice = resolveRuntimeChoice(args);
        boolean serverRuntime = "server".equals(runtimeChoice);
        boolean installMedusa = serverRuntime && Boolean.parseBoolean(parseArg(args, INSTALL_MEDUSA_ARG));
        Path skillsRoot = null;
        if (serverRuntime) {
            String skillsRootArg = parseArg(args, SKILLS_ROOT_ARG);
            if (skillsRootArg != null && !skillsRootArg.trim().isEmpty()) {
                skillsRoot = Paths.get(skillsRootArg).toAbsolutePath().normalize();
            }
        }
        try {
            updateSkillsConfig(configPath, runtimeChoice, serverRuntime ? Boolean.TRUE : null, skillsRoot, serverRuntime, installMedusa);
        } catch (Exception e) {
            log.error("Skip runtime skills config due to invalid YAML at {}. Please fix this file and retry if needed.", configPath, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void updateSkillsConfig(Path configPath, String runtimeMode, Boolean enableSkills, Path skillsRoot, boolean serverRuntime, boolean installMedusa) throws IOException {
        // Default YAMLMapper can emit broken single-quoted multiline scalars (e.g. filters.rules), which then fail on re-read.
        YAMLFactory yamlFactory = new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                .enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE)
                .enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR);
        YAMLMapper yamlMapper = new YAMLMapper(yamlFactory);
        Map<String, Object> rootMap = new LinkedHashMap<>();
        if (Files.exists(configPath)) {
            try (InputStream in = Files.newInputStream(configPath)) {
                Map<String, Object> loaded = yamlMapper.readValue(in, new TypeReference<Map<String, Object>>() {
                });
                if (loaded != null) {
                    rootMap.putAll(loaded);
                }
            } catch (Exception e) {
                log.warn("Skip runtime skills config due to invalid YAML at {}. Please fix this file and retry if needed.", configPath, e);
            }
        }

        Object skillsObj = rootMap.get("skills");
        Map<String, Object> skillsMap = skillsObj instanceof Map
                ? new LinkedHashMap<>((Map<String, Object>) skillsObj)
                : new LinkedHashMap<String, Object>();
        if (enableSkills != null) {
            skillsMap.put("enable", enableSkills);
        }
        if (skillsRoot != null) {
            // roots 里只追加安装器传入的解压目录；内层目录推断仅用于发现 items，避免写入“多出来的错误路径”
            Path discoveryRoot = resolveSkillRootForConfig(skillsRoot);
            skillsMap.put("roots", mergeSkillRoots(skillsMap, skillsRoot));
            appendSkillItems(skillsMap, discoveryRoot);
        }
        rootMap.put("skills", skillsMap);
        if (installMedusa) {
            applyMedusaAcceleratorConfig(rootMap);
        } else if (serverRuntime) {
            disableMedusaAcceleratorConfig(rootMap);
        }

        Object generalObj = rootMap.get("general");
        Map<String, Object> generalMap = generalObj instanceof Map
                ? new LinkedHashMap<>((Map<String, Object>) generalObj)
                : new LinkedHashMap<String, Object>();
        generalMap.put("mode", runtimeMode);
        rootMap.put("general", generalMap);

        if (configPath.getParent() != null) {
            Files.createDirectories(configPath.getParent());
        }
        yamlMapper.writeValue(configPath.toFile(), rootMap);
    }

    @SuppressWarnings("unchecked")
    private static void applyMedusaAcceleratorConfig(Map<String, Object> rootMap) {
        Object storesObj = rootMap.get("stores");
        Map<String, Object> storesMap = storesObj instanceof Map
                ? new LinkedHashMap<>((Map<String, Object>) storesObj)
                : new LinkedHashMap<String, Object>();

        Map<String, Object> medusaMap = new LinkedHashMap<>();
        medusaMap.put("enable", true);
        medusaMap.put("algorithm", "hash");
        medusaMap.put("core_pool_size", 5);
        medusaMap.put("maximum_pool_size", 30);
        storesMap.put("medusa", medusaMap);
        rootMap.put("stores", storesMap);
    }

    @SuppressWarnings("unchecked")
    private static void disableMedusaAcceleratorConfig(Map<String, Object> rootMap) {
        Object storesObj = rootMap.get("stores");
        Map<String, Object> storesMap = storesObj instanceof Map
                ? new LinkedHashMap<>((Map<String, Object>) storesObj)
                : new LinkedHashMap<String, Object>();
        Object medusaObj = storesMap.get("medusa");
        Map<String, Object> medusaMap = medusaObj instanceof Map
                ? new LinkedHashMap<>((Map<String, Object>) medusaObj)
                : new LinkedHashMap<String, Object>();
        medusaMap.put("enable", false);
        storesMap.put("medusa", medusaMap);
        rootMap.put("stores", storesMap);
    }

    /**
     * 保留 YAML 里已有的 {@code skills.roots}（任意 Jackson/SnakeYAML 常见形态），再追加安装器解压目录（去重）。
     */
    private static List<String> mergeSkillRoots(Map<String, Object> skillsMap, Path unpackedDirForYaml) {
        List<String> merged = new ArrayList<>();
        Set<String> seenNorm = new HashSet<>();
        collectExistingRoots(skillsMap.get("roots"), merged, seenNorm);
        addRootIfAbsent(merged, seenNorm, unpackedDirForYaml.toAbsolutePath().normalize().toString());
        return merged;
    }

    /**
     * roots 可能是 List、其它 Collection、字符串、或 Java 数组；仅识别为 List 时会漏掉 classpath:skills 等配置。
     */
    private static void collectExistingRoots(Object rootsObj, List<String> merged, Set<String> seenNorm) {
        if (rootsObj == null) {
            return;
        }
        if (rootsObj instanceof Map) {
            log.warn("skills.roots is a map; expected list or scalar. Skipping existing roots.");
            return;
        }
        if (rootsObj instanceof CharSequence) {
            addRootIfAbsent(merged, seenNorm, rootsObj.toString().trim());
            return;
        }
        if (rootsObj instanceof Iterable && !(rootsObj instanceof Map)) {
            for (Object o : (Iterable<?>) rootsObj) {
                if (o != null) {
                    addRootIfAbsent(merged, seenNorm, o.toString().trim());
                }
            }
            return;
        }
        if (rootsObj.getClass().isArray()) {
            int n = Array.getLength(rootsObj);
            for (int i = 0; i < n; i++) {
                Object o = Array.get(rootsObj, i);
                if (o != null) {
                    addRootIfAbsent(merged, seenNorm, o.toString().trim());
                }
            }
            return;
        }
        addRootIfAbsent(merged, seenNorm, rootsObj.toString().trim());
    }

    private static void addRootIfAbsent(List<String> merged, Set<String> seenNorm, String s) {
        if (s == null || s.isEmpty()) {
            return;
        }
        String norm = normalizeRootForDedup(s);
        if (norm.isEmpty()) {
            return;
        }
        if (seenNorm.contains(norm)) {
            return;
        }
        seenNorm.add(norm);
        merged.add(s);
    }

    private static String normalizeRootForDedup(String pathOrUri) {
        String t = pathOrUri == null ? "" : pathOrUri.trim();
        if (t.isEmpty()) {
            return "";
        }
        if (t.startsWith("classpath:")) {
            return t;
        }
        try {
            return Paths.get(t).toAbsolutePath().normalize().toString();
        } catch (Exception e) {
            return t;
        }
    }

    /**
     * Installers unzip into a folder that often contains a single top-level directory before skill dirs
     * (e.g. {@code .../popular_skills/<bundle>/skill-a/SKILL.md}). {@link SkillLoader} expects roots where
     * either {@code root/skills/...} or immediate child dirs contain {@code SKILL.md}; this picks such a path.
     */
    private static Path resolveSkillRootForConfig(Path declaredUnpackDir) {
        Path real = declaredUnpackDir.toAbsolutePath().normalize();
        if (!Files.isDirectory(real)) {
            return real;
        }
        if (looksLikeSkillLoaderRoot(real)) {
            return real;
        }
        try {
            List<Path> subdirs = listDirectSubdirectories(real);
            if (subdirs.size() == 1) {
                Path inner = subdirs.get(0).toAbsolutePath().normalize();
                if (looksLikeSkillLoaderRoot(inner)) {
                    return inner;
                }
            }
        } catch (IOException e) {
            log.debug("resolveSkillRootForConfig: {}", real, e);
        }
        return real;
    }

    private static List<Path> listDirectSubdirectories(Path dir) throws IOException {
        List<Path> subdirs = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                String name = child.getFileName() == null ? "" : child.getFileName().toString();
                if (name.startsWith(".") || "node_modules".equals(name)) {
                    continue;
                }
                if (Files.isDirectory(child)) {
                    subdirs.add(child);
                }
            }
        }
        return subdirs;
    }

    /**
     * Mirrors {@link SkillLoader} scan root: {@code root/skills} when present, else {@code root}.
     */
    private static boolean looksLikeSkillLoaderRoot(Path root) {
        try {
            Path scanRoot = Files.isDirectory(root.resolve("skills")) ? root.resolve("skills") : root;
            Path rootSkill = scanRoot.resolve("SKILL.md");
            if (Files.isRegularFile(rootSkill)) {
                return true;
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(scanRoot)) {
                for (Path child : stream) {
                    String name = child.getFileName() == null ? "" : child.getFileName().toString();
                    if (name.startsWith(".") || "node_modules".equals(name)) {
                        continue;
                    }
                    if (Files.isDirectory(child) && Files.isRegularFile(child.resolve("SKILL.md"))) {
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            log.debug("looksLikeSkillLoaderRoot: {}", root, e);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static void appendSkillItems(Map<String, Object> skillsMap, Path skillsRoot) {
        List<Map<String, String>> discoveredItems = discoverSkillItems(skillsRoot);
        if (discoveredItems.isEmpty()) {
            return;
        }

        Object itemsObj = skillsMap.get("items");
        List<Map<String, Object>> items = itemsObj instanceof List
                ? new ArrayList<>((List<Map<String, Object>>) itemsObj)
                : new ArrayList<Map<String, Object>>();

        Set<String> existingNames = new HashSet<>();
        for (Map<String, Object> item : items) {
            Object name = item.get("name");
            if (name instanceof String && !((String) name).trim().isEmpty()) {
                existingNames.add(((String) name).trim());
            }
        }

        for (Map<String, String> discoveredItem : discoveredItems) {
            String name = discoveredItem.get("name");
            if (name == null || name.trim().isEmpty() || existingNames.contains(name.trim())) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", name.trim());
            item.put("description", discoveredItem.get("description"));
            items.add(item);
            existingNames.add(name.trim());
        }
        skillsMap.put("items", items);
    }

    private static List<Map<String, String>> discoverSkillItems(Path skillsRoot) {
        if (skillsRoot == null) {
            return Collections.emptyList();
        }
        Path root = skillsRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return Collections.emptyList();
        }
        List<Map<String, String>> result = new ArrayList<>();
        try {
            SkillLoader loader = new SkillLoader();
            List<SkillEntry> entries = loader.load(Collections.singletonList(root.toString()));
            for (SkillEntry entry : entries) {
                String name = trimToNull(entry == null ? null : entry.getName());
                String description = trimToNull(entry == null ? null : entry.getDescription());
                if (name == null || description == null) {
                    continue;
                }
                Map<String, String> item = new LinkedHashMap<>();
                item.put("name", name);
                item.put("description", description);
                result.add(item);
            }
        } catch (Exception e) {
            log.warn("Discover skills failed in: {}", root, e);
        }
        return result;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Resolves the filesystem path of a classpath resource (for IDE run).
     */
    private static Path getResourceDir(String resourceName) {
        URL url = InstallerUtil.class.getClassLoader().getResource(resourceName);
        if (url == null || !"file".equals(url.getProtocol())) {
            throw new IllegalStateException("Cannot resolve resource path for: " + resourceName + " (not a file URL)");
        }
        try {
            Path path = Paths.get(url.toURI());
            return path.getParent();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Cannot resolve resource path for: " + resourceName, e);
        }
    }

    /**
     * Copies sqlite/* from the JAR into dataDir (e.g. saas.db). Skips if target already exists.
     */
    private static void copySqliteToDataDir(Path jarPath, Path dataDir) throws IOException {
        String prefix = SQLITE_RESOURCE_DIR + "/";
        try (ZipFile zf = new ZipFile(jarPath.toFile())) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory() || !e.getName().startsWith(prefix)) continue;
                String rel = e.getName().substring(prefix.length());
                Path dest = dataDir.resolve(rel).normalize();
                if (!dest.startsWith(dataDir)) continue;
                if (Files.exists(dest)) continue;
                Files.createDirectories(dest.getParent());
                try (InputStream in = zf.getInputStream(e)) {
                    Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void copyResourceIfAbsent(ClassLoader cl, String resourceName, Path target) throws IOException {
        if (Files.exists(target)) {
            return;
        }
        URL url = cl.getResource(resourceName);
        if (url == null) {
            return;
        }
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        try (InputStream in = url.openStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
