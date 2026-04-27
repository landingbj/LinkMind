package ai.pnps.skills;

import ai.pnps.skills.pojo.ScriptExecutionResult;
import ai.pnps.skills.pojo.SkillExecutionPlan;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs skill commands OpenClaw-style: argv array, no shell.
 * Executable/script resolution stays sandboxed to the skill directory, while data-file args may be absolute.
 */
public class SkillScriptExecutor {

    private static final int PER_STREAM_MAX_CHARS = 50_000;
    private static final Set<String> ALLOWED_BARE_COMMANDS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "python", "python3", "py",
            "node", "nodejs",
            "npm", "npx", "corepack", "pnpm", "yarn",
            "bash", "sh",
            "cmd",
            "pwsh", "powershell"
    )));

    public ScriptExecutionResult execute(Path skillDir, SkillExecutionPlan.Script script, long timeoutSeconds) {
        return execute(skillDir, script, timeoutSeconds, 0L, null);
    }

    /**
     * @param noOutputTimeoutSeconds if &gt; 0, kill the process when neither stdout nor stderr receives data for this long (OpenClaw no-output timeout).
     */
    public ScriptExecutionResult execute(Path skillDir, SkillExecutionPlan.Script script,
                                         long timeoutSeconds, long noOutputTimeoutSeconds) {
        return execute(skillDir, script, timeoutSeconds, noOutputTimeoutSeconds, null);
    }

    /**
     * Same as {@link #execute(Path, SkillExecutionPlan.Script, long, long)} with optional extra environment entries
     * merged into the child process (tool/exec {@code env} object).
     */
    public ScriptExecutionResult execute(Path skillDir, SkillExecutionPlan.Script script,
                                         long timeoutSeconds, long noOutputTimeoutSeconds,
                                         Map<String, String> additionalEnvironment) {
        if (skillDir == null || script == null) {
            return new ScriptExecutionResult(-1, "Invalid script plan.", false);
        }
        try {
            Path realRoot = skillDir.toRealPath();
            Path workingDir = resolveUnderSkill(realRoot, script.getWorkingDir());
            if (!Files.isDirectory(workingDir)) {
                return new ScriptExecutionResult(-1, "Working dir not found: " + workingDir, false);
            }

            List<String> rawArgv = buildRawArgv(script);
            if (rawArgv.isEmpty()) {
                return new ScriptExecutionResult(-1, "Invalid script plan: empty command.", false);
            }

            List<String> command = resolveArgv(realRoot, rawArgv);
            if (command == null) {
                return new ScriptExecutionResult(-1, "Command rejected: first token must be an allowed interpreter or a file under the skill directory.", false);
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workingDir.toFile());
            // Force Python subprocess stdout/stderr encoding to UTF-8 on Windows and other platforms.
            pb.environment().put("PYTHONIOENCODING", "UTF-8");
            pb.environment().put("PYTHONUTF8", "1");
            if (additionalEnvironment != null && !additionalEnvironment.isEmpty()) {
                pb.environment().putAll(additionalEnvironment);
            }
            Process process = pb.start();

            StringBuilder stdoutHolder = new StringBuilder();
            StringBuilder stderrHolder = new StringBuilder();
            AtomicLong lastActivityNanos = new AtomicLong(System.nanoTime());
            AtomicBoolean noOutputKill = new AtomicBoolean(false);

            Thread outThread = new Thread(() -> appendStream(process.getInputStream(), stdoutHolder, lastActivityNanos),
                    "skill-exec-stdout");
            Thread errThread = new Thread(() -> appendStream(process.getErrorStream(), stderrHolder, lastActivityNanos),
                    "skill-exec-stderr");
            outThread.setDaemon(true);
            errThread.setDaemon(true);
            outThread.start();
            errThread.start();

            ScheduledExecutorService scheduler = null;
            ScheduledFuture<?> watchdog = null;
            if (noOutputTimeoutSeconds > 0) {
                scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "skill-exec-no-output-watchdog");
                    t.setDaemon(true);
                    return t;
                });
                long thresholdNanos = TimeUnit.SECONDS.toNanos(noOutputTimeoutSeconds);
                watchdog = scheduler.scheduleAtFixedRate(() -> {
                    if (!process.isAlive()) {
                        return;
                    }
                    long idle = System.nanoTime() - lastActivityNanos.get();
                    if (idle >= thresholdNanos) {
                        noOutputKill.set(true);
                        process.destroyForcibly();
                    }
                }, 1, 1, TimeUnit.SECONDS);
            }

            boolean done = process.waitFor(Math.max(1L, timeoutSeconds), TimeUnit.SECONDS);
            if (watchdog != null) {
                watchdog.cancel(false);
            }
            if (scheduler != null) {
                scheduler.shutdownNow();
            }
            if (!done) {
                process.destroyForcibly();
                joinQuiet(outThread, 1000L);
                joinQuiet(errThread, 1000L);
                return new ScriptExecutionResult(-1,
                        trim(stdoutHolder.toString()),
                        trim(stderrHolder.toString()) + "\n[timeout killed]",
                        true,
                        false);
            }
            joinQuiet(outThread, 1000L);
            joinQuiet(errThread, 1000L);

            boolean noOut = noOutputKill.get();
            return new ScriptExecutionResult(
                    process.exitValue(),
                    trim(stdoutHolder.toString()),
                    trim(stderrHolder.toString()),
                    false,
                    noOut);
        } catch (Exception e) {
            return new ScriptExecutionResult(-1, "Execute failed: " + e.getMessage(), false);
        }
    }

    private static List<String> buildRawArgv(SkillExecutionPlan.Script script) {
        if (script.isArgvMode()) {
            return new ArrayList<String>(script.getArgv());
        }
        List<String> c = new ArrayList<String>();
        if (script.getPath() == null || script.getPath().trim().isEmpty()) {
            return c;
        }
        c.add("python");
        c.add(script.getPath().trim());
        c.addAll(script.getArgs());
        return c;
    }

    /**
     * @return null if argv[0] is not permitted.
     */
    private static List<String> resolveArgv(Path skillRoot, List<String> raw) throws Exception {
        List<String> out = new ArrayList<String>(raw.size());
        String cmd0 = raw.get(0);
        String resolved0 = resolveFirstToken(skillRoot, cmd0);
        if (resolved0 == null) {
            return null;
        }
        out.add(resolved0);
        for (int i = 1; i < raw.size(); i++) {
            out.add(resolveArgToken(skillRoot, raw.get(i), raw, i));
        }
        return out;
    }

    private static String resolveFirstToken(Path skillRoot, String token) throws Exception {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        String t = token.trim();
        String lower = t.toLowerCase(Locale.ROOT);
        int slashIdx = Math.max(t.indexOf('/'), t.indexOf('\\'));
        if (slashIdx >= 0 || t.startsWith(".")) {
            Path p = mustResolveUnderSkill(skillRoot, t);
            if (!Files.isRegularFile(p)) {
                return null;
            }
            return p.toString();
        }
        if (looksLikeScriptFilename(t)) {
            Path p = mustResolveUnderSkill(skillRoot, t);
            if (!Files.isRegularFile(p)) {
                return null;
            }
            return p.toString();
        }
        if (ALLOWED_BARE_COMMANDS.contains(lower)) {
            return t;
        }
        Path p = mustResolveUnderSkill(skillRoot, t);
        if (Files.isRegularFile(p)) {
            return p.toString();
        }
        return null;
    }

    private static String resolveArgToken(Path skillRoot, String token, List<String> rawArgv, int index) throws Exception {
        if (token == null) {
            return "";
        }
        if (isLiteralArgument(rawArgv, index)) {
            return token;
        }
        // Allow external absolute file paths as data inputs.
        if (isAbsolutePathToken(token)) {
            return token;
        }
        if (!shouldResolveAsSkillRelativePath(token)) {
            return token;
        }
        return mustResolveUnderSkill(skillRoot, token).toString();
    }

    private static boolean isAbsolutePathToken(String token) {
        try {
            return Paths.get(token).isAbsolute();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean shouldResolveAsSkillRelativePath(String token) {
        if (token.isEmpty()) {
            return false;
        }
        if (token.indexOf('\n') >= 0 || token.indexOf('\r') >= 0) {
            return false;
        }
        // Windows cmd.exe uses /c, /k, etc.; must not be resolved as skill-relative paths.
        if (isWindowsCmdStyleSwitch(token)) {
            return false;
        }
        if (token.contains("/") || token.contains("\\")) {
            return true;
        }
        if (token.startsWith(".")) {
            return true;
        }
        String lower = token.toLowerCase(Locale.ROOT);
        return lower.endsWith(".py") || lower.endsWith(".js") || lower.endsWith(".mjs")
                || lower.endsWith(".cjs") || lower.endsWith(".sh") || lower.endsWith(".ps1")
                || lower.endsWith(".bat") || lower.endsWith(".cmd")
                || lower.endsWith(".exe");
    }

    /**
     * {@code cmd.exe} switch form {@code /X} where X is a single letter (e.g. {@code /c}, {@code /k}).
     */
    private static boolean isWindowsCmdStyleSwitch(String token) {
        if (token.length() != 2 || token.charAt(0) != '/') {
            return false;
        }
        return Character.isLetter(token.charAt(1));
    }

    private static boolean isLiteralArgument(List<String> rawArgv, int index) {
        if (index <= 0 || index >= rawArgv.size()) {
            return false;
        }
        String prev = rawArgv.get(index - 1);
        if (prev == null) {
            return false;
        }
        String lowerPrev = prev.toLowerCase(Locale.ROOT);
        // Interpreter switches whose next argument is code/module/command text.
        return "-c".equals(lowerPrev)
                || "-m".equals(lowerPrev)
                || "-e".equals(lowerPrev)
                || "--eval".equals(lowerPrev)
                || "/c".equals(lowerPrev)
                || "/k".equals(lowerPrev);
    }

    private static boolean looksLikeScriptFilename(String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        return lower.endsWith(".py") || lower.endsWith(".js") || lower.endsWith(".mjs")
                || lower.endsWith(".cjs") || lower.endsWith(".sh") || lower.endsWith(".ps1")
                || lower.endsWith(".bat") || lower.endsWith(".cmd");
    }

    private static Path mustResolveUnderSkill(Path skillRoot, String rel) throws Exception {
        Path base = skillRoot.toRealPath();
        Path resolved = base.resolve(rel).normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException("Path escapes skill dir: " + rel);
        }
        return resolved;
    }

    private static Path resolveUnderSkill(Path skillRoot, String rel) throws Exception {
        return mustResolveUnderSkill(skillRoot, rel == null ? "." : rel);
    }

    private static void appendStream(java.io.InputStream stream, StringBuilder sink, AtomicLong lastActivityNanos) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lastActivityNanos.set(System.nanoTime());
                sink.append(line).append('\n');
                if (sink.length() > PER_STREAM_MAX_CHARS) {
                    sink.delete(0, sink.length() - PER_STREAM_MAX_CHARS);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void joinQuiet(Thread t, long millis) {
        try {
            t.join(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String trim(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= 20_000) {
            return text;
        }
        return text.substring(text.length() - 20_000);
    }
}
