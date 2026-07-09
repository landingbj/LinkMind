package ai.migrate.parser;

import ai.migrate.pojo.AgentImportPreview;
import ai.migrate.pojo.ParsedMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChatTextParser {
    public AgentImportPreview parse(String text, String source) {
        AgentImportPreview preview = new AgentImportPreview();
        preview.setSource(source);
        preview.setRawText(text);
        List<ParsedMessage> messages = parseMessages(text);
        if (messages.size() >= 2) {
            preview.setParseMode("chat_turns");
            preview.setMessages(messages);
            preview.setTurnCount(countUserTurns(messages));
            preview.setMessageCount(messages.size());
        } else {
            preview.setParseMode("plain_context");
            ParsedMessage message = new ParsedMessage();
            message.setRole("user");
            message.setContent(text.trim());
            preview.getMessages().add(message);
            preview.setTurnCount(1);
            preview.setMessageCount(1);
            preview.getWarnings().add("未识别出标准对话轮次，将作为普通历史背景导入。");
        }
        int sampleSize = Math.min(6, preview.getMessages().size());
        preview.setSamples(new ArrayList<>(preview.getMessages().subList(0, sampleSize)));
        return preview;
    }

    private List<ParsedMessage> parseMessages(String text) {
        List<ParsedMessage> messages = new ArrayList<>();
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        String currentRole = null;
        StringBuilder currentContent = new StringBuilder();
        for (String line : lines) {
            RoleLine roleLine = parseRoleLine(line);
            if (roleLine != null) {
                flush(messages, currentRole, currentContent);
                currentRole = roleLine.role;
                currentContent.append(roleLine.content);
                continue;
            }
            if (currentRole != null) {
                if (currentContent.length() > 0) {
                    currentContent.append('\n');
                }
                currentContent.append(line);
            }
        }
        flush(messages, currentRole, currentContent);
        return messages;
    }

    private RoleLine parseRoleLine(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        int splitIndex = firstSplitIndex(trimmed);
        if (splitIndex <= 0) {
            return null;
        }
        String label = normalizeLabel(trimmed.substring(0, splitIndex));
        String role = toRole(label);
        if (role == null) {
            return null;
        }
        RoleLine roleLine = new RoleLine();
        roleLine.role = role;
        roleLine.content = trimmed.substring(splitIndex + 1).trim();
        return roleLine;
    }

    private int firstSplitIndex(String value) {
        int en = value.indexOf(':');
        int cn = value.indexOf('：');
        if (en < 0) {
            return cn;
        }
        if (cn < 0) {
            return en;
        }
        return Math.min(en, cn);
    }

    private String normalizeLabel(String label) {
        return label.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\[\\]【】（）()_-]", "");
    }

    private String toRole(String label) {
        if ("user".equals(label) || "human".equals(label) || "me".equals(label)
                || "用户".equals(label) || "我".equals(label) || "提问".equals(label) || "问".equals(label)) {
            return "user";
        }
        if ("assistant".equals(label) || "ai".equals(label) || "bot".equals(label) || "agent".equals(label)
                || "助手".equals(label) || "助理".equals(label) || "智能体".equals(label) || "回答".equals(label) || "答".equals(label)) {
            return "assistant";
        }
        if ("system".equals(label) || "系统".equals(label)) {
            return "system";
        }
        return null;
    }

    private void flush(List<ParsedMessage> messages, String role, StringBuilder content) {
        if (role == null) {
            content.setLength(0);
            return;
        }
        String value = content.toString().trim();
        if (!value.isEmpty()) {
            ParsedMessage message = new ParsedMessage();
            message.setRole(role);
            message.setContent(value);
            messages.add(message);
        }
        content.setLength(0);
    }

    private int countUserTurns(List<ParsedMessage> messages) {
        int count = 0;
        for (ParsedMessage message : messages) {
            if (message != null && "user".equals(message.getRole())) {
                count++;
            }
        }
        return count;
    }

    private static class RoleLine {
        private String role;
        private String content;
    }
}
