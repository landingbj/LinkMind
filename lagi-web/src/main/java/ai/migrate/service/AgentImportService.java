package ai.migrate.service;

import ai.config.pojo.AgentConfig;
import ai.manager.AgentManager;
import ai.migrate.parser.ChatTextParser;
import ai.migrate.pojo.AgentImportCommitRequest;
import ai.migrate.pojo.AgentImportPreview;
import ai.migrate.pojo.AgentListItem;
import ai.migrate.pojo.HistoryRecord;
import ai.migrate.pojo.ImportedAgentProfile;
import ai.migrate.pojo.ParsedMessage;
import ai.migrate.service.ImportedAgentStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class AgentImportService {
    private static final int MAX_TEXT_LENGTH = 10 * 1024 * 1024;
    private static final String PREVIEW_ID_PATTERN = "tmp_[a-fA-F0-9]{32}";
    private static final String DEFAULT_SYSTEM_PROMPT = "你是一个从历史对话迁移而来的智能体。请参考导入历史和当前会话上下文，延续原有对话风格，优先直接回答用户当前问题。\n\n除非系统明确提供可调用工具或用户要求实时数据，否则不要只承诺“查询/查找”，应基于已有上下文给出可执行、具体的回答；信息不确定时请说明假设。\n\n如果历史记录里出现仅承诺查询、查找、稍后提供，但没有给出实质内容的回复，请不要模仿这种占位回复，应直接给出当前问题的完整答案。";

    private final ChatTextParser parser = new ChatTextParser();
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final YAMLMapper yamlMapper = new YAMLMapper();

    public AgentImportService() {
        yamlMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    public AgentImportPreview preview(String text, String source) throws IOException {
        validateText(text);
        AgentImportPreview preview = parser.parse(text, source);
        preview.setPreviewId("tmp_" + UUID.randomUUID().toString().replace("-", ""));
        String suggestedName = suggestName(text);
        preview.setSuggestedName(suggestedName);
        preview.setSuggestedAgentId(uniqueAgentId(slugify(suggestedName)));
        savePreview(preview);
        return preview;
    }

    public AgentListItem commit(AgentImportCommitRequest request) throws IOException {
        if (request == null || isBlank(request.getPreviewId())) {
            throw new IllegalArgumentException("previewId 不能为空");
        }
        AgentImportPreview preview = loadPreview(request.getPreviewId());
        if (preview == null) {
            throw new IllegalArgumentException("预览已失效，请重新预览");
        }
        String agentId = sanitizeAgentId(request.getAgentId());
        if (isBlank(agentId)) {
            throw new IllegalArgumentException("agentId 只能包含小写字母、数字、下划线和中划线");
        }
        if (AgentManager.getInstance().get(agentId) != null || Files.exists(ImportedAgentStore.agentDir(agentId))) {
            throw new IllegalArgumentException("agentId 已存在");
        }
        String displayName = isBlank(request.getDisplayName()) ? preview.getSuggestedName() : request.getDisplayName().trim();
        String systemPrompt = isBlank(request.getSystemPrompt()) ? DEFAULT_SYSTEM_PROMPT : request.getSystemPrompt().trim();

        Path dir = ImportedAgentStore.agentDir(agentId);
        Files.createDirectories(dir);
        Files.write(dir.resolve("system.md"), systemPrompt.getBytes(StandardCharsets.UTF_8));
        Files.write(dir.resolve("raw.txt"), preview.getRawText().getBytes(StandardCharsets.UTF_8));
        writeHistory(dir.resolve("history.jsonl"), agentId, preview);
        writeImportReport(dir.resolve("import_report.md"), preview, displayName);

        ImportedAgentProfile profile = new ImportedAgentProfile();
        profile.setAgentId(agentId);
        profile.setDisplayName(displayName);
        profile.setDescription("从历史对话导入的智能体");
        profile.setSystemPath("system.md");
        profile.setHistoryPath("history.jsonl");
        profile.setImportMode(preview.getParseMode());
        profile.setCreatedAt(OffsetDateTime.now().toString());
        yamlMapper.writeValue(dir.resolve("agent.yaml").toFile(), profile);

        AgentConfig config = ImportedAgentStore.buildImportedAgentConfig(agentId);
        ImportedAgentStore.upsertExternalAgentConfig(config);
        ImportedAgentStore.registerImportedAgent(config);

        AgentListItem item = new AgentListItem();
        item.setAgentId(agentId);
        item.setTitle(displayName);
        item.setDescription(profile.getDescription());
        item.setSource("import");
        item.setImported(true);
        item.setTemplateIssues("继续和 " + displayName + " 对话");
        return item;
    }

    private void validateText(String text) {
        if (isBlank(text)) {
            throw new IllegalArgumentException("导入内容不能为空");
        }
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("导入内容不能超过 10MB");
        }
    }

    private void savePreview(AgentImportPreview preview) throws IOException {
        Path path = previewPath(preview.getPreviewId());
        Files.createDirectories(path.getParent());
        jsonMapper.writeValue(path.toFile(), preview);
    }

    private AgentImportPreview loadPreview(String previewId) throws IOException {
        Path path = previewPath(previewId);
        if (!Files.exists(path)) {
            return null;
        }
        return jsonMapper.readValue(path.toFile(), AgentImportPreview.class);
    }

    private Path previewPath(String previewId) {
        String normalizedPreviewId = previewId == null ? "" : previewId.trim();
        if (!normalizedPreviewId.matches(PREVIEW_ID_PATTERN)) {
            throw new IllegalArgumentException("previewId 格式不合法");
        }
        Path previewRoot = ImportedAgentStore.importRoot().resolve("previews").normalize();
        Path path = previewRoot.resolve(normalizedPreviewId + ".json").normalize();
        if (!path.startsWith(previewRoot)) {
            throw new IllegalArgumentException("previewId 格式不合法");
        }
        return path;
    }

    private void writeHistory(Path path, String agentId, AgentImportPreview preview) throws IOException {
        int index = 1;
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (ParsedMessage message : preview.getMessages()) {
                if (message == null || isBlank(message.getContent()) || isBlank(message.getRole())) {
                    continue;
                }
                HistoryRecord record = ImportedAgentStore.buildRecord(agentId, "import_" + preview.getPreviewId(),
                        message.getRole(), message.getContent(), "import", index++);
                if ("plain_context".equals(preview.getParseMode())) {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("parse_mode", "plain_context");
                    record.setMetadata(metadata);
                }
                writer.write(jsonMapper.writeValueAsString(record));
                writer.newLine();
            }
        }
    }

    private void writeImportReport(Path path, AgentImportPreview preview, String displayName) throws IOException {
        StringBuilder report = new StringBuilder();
        report.append("# ").append(displayName).append(" 导入报告\n\n");
        report.append("- parse_mode: ").append(preview.getParseMode()).append('\n');
        report.append("- message_count: ").append(preview.getMessageCount()).append('\n');
        report.append("- turn_count: ").append(preview.getTurnCount()).append('\n');
        if (preview.getWarnings() != null && !preview.getWarnings().isEmpty()) {
            report.append("- warnings: ").append(String.join("；", preview.getWarnings())).append('\n');
        }
        Files.write(path, report.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String suggestName(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return "导入智能体";
        }
        String firstLine = trimmed.split("\\R", 2)[0].trim();
        firstLine = firstLine.replaceAll("[:：].*$", "").trim();
        if (firstLine.length() < 2) {
            return "导入智能体";
        }
        return firstLine.length() > 16 ? firstLine.substring(0, 16) : firstLine;
    }

    private String slugify(String name) {
        String normalized = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFKD)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (isBlank(normalized)) {
            normalized = "imported_agent";
        }
        return normalized;
    }

    private String uniqueAgentId(String base) {
        String candidate = sanitizeAgentId(base);
        if (isBlank(candidate)) {
            candidate = "imported_agent";
        }
        String current = candidate;
        int index = 2;
        while (AgentManager.getInstance().get(current) != null || Files.exists(ImportedAgentStore.agentDir(current))) {
            current = candidate + "_" + index++;
        }
        return current;
    }

    private String sanitizeAgentId(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_-]+")) {
            return "";
        }
        return normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
