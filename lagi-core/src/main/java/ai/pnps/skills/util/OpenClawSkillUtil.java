package ai.pnps.skills.util;

import ai.pnps.skills.pojo.SkillEntry;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OpenClawSkillUtil {

    /**
     * Match only block-style <available_skills> sections (tag starts at a line boundary),
     * so inline guidance like "scan <available_skills> <description> entries" is ignored.
     */
    private static final Pattern AVAILABLE_SKILLS_BLOCK = Pattern.compile(
            "(?is)(?:^|\\R)\\s*<available_skills\\b[^>]*>.*?</available_skills>\\s*(?=\\R|$)"
    );
    private static final Pattern SKILL_BLOCK = Pattern.compile("(?is)<skill\\b[^>]*>\\s*(.*?)\\s*</skill>");
    private static final Pattern NAME_PATTERN = Pattern.compile("(?is)<name\\b[^>]*>(.*?)</name>");
    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile("(?is)<description\\b[^>]*>(.*?)</description>");
    private static final Pattern LOCATION_PATTERN = Pattern.compile("(?is)<location\\b[^>]*>(.*?)</location>");

    /**
     * Match leaf-level skill entries in the dash/indent style, e.g.
     * "    - claude-code: Delegate coding to Claude Code CLI (features, PRs)."
     * Group 1 = skill name, group 2 = description (may be empty).
     */
    private static final Pattern DASH_SKILL_LINE = Pattern.compile(
            "(?m)^\\s*-\\s+([A-Za-z0-9][\\w.\\-]*)\\s*:\\s*(.*)$"
    );


    public static List<SkillEntry> skillExtractor(String systemMessage) {
        String normalizedSystemMessage = normalizeEscapedTags(systemMessage);
        Matcher skillsMatcher = AVAILABLE_SKILLS_BLOCK.matcher(normalizedSystemMessage);
        if (!skillsMatcher.find()) {
            return Collections.emptyList();
        }
        String block = skillsMatcher.group();
        List<SkillEntry> extracted = new ArrayList<>();
        Matcher skillMatcher = SKILL_BLOCK.matcher(block);
        boolean hasXmlSkill = false;
        while (skillMatcher.find()) {
            hasXmlSkill = true;
            String skillBody = skillMatcher.group(1);
            String name = extractTagValue(skillBody, NAME_PATTERN);
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            String description = extractTagValue(skillBody, DESCRIPTION_PATTERN);
            String location = extractTagValue(skillBody, LOCATION_PATTERN);

            extracted.add(new SkillEntry(
                    name,
                    name,
                    description == null ? "" : description,
                    null,
                    location == null || location.trim().isEmpty() ? null : Paths.get(location),
                    new HashMap<String, String>(),
                    null,
                    null,
                    null,
                    null,
                    "cli"
            ));
        }
        if (!hasXmlSkill) {
            // Fallback: dash/indent style. Each leaf entry "  - name: description"
            // is a skill; non-dash lines like "category:" are treated as group headers.
            Matcher dashMatcher = DASH_SKILL_LINE.matcher(block);
            while (dashMatcher.find()) {
                String name = dashMatcher.group(1).trim();
                if (name.isEmpty()) {
                    continue;
                }
                String description = dashMatcher.group(2) == null ? "" : dashMatcher.group(2).trim();
                extracted.add(new SkillEntry(
                        name,
                        name,
                        description,
                        null,
                        null,
                        new HashMap<String, String>(),
                        null,
                        null,
                        null,
                        null,
                        "cli"
                ));
            }
        }
        return extracted;
    }

    public static String skill2Content(List<SkillEntry> skills) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n<available_skills>\n");
        for (SkillEntry skill : skills) {
            if (skill == null || skill.getName() == null || skill.getName().trim().isEmpty()) {
                continue;
            }
            sb.append("  <skill>\n");
            sb.append("    <name>").append(xmlEscape(skill.getName())).append("</name>\n");
            sb.append("    <description>").append(xmlEscape(skill.getDescription())).append("</description>\n");
            if (skill.getSkillMdPath() != null) {
                sb.append("    <location>").append(xmlEscape(skill.getSkillMdPath().toString())).append("</location>\n");
            }
            if(skill.getSkillDir() != null) {
                sb.append("    <workdir>").append(xmlEscape(skill.getSkillDir().toString())).append("</workdir>\n");
            }
            sb.append("  </skill>\n");
        }
        sb.append("</available_skills>");
        return sb.toString();
    }


    public static String replaceAvailableSkill(String content, List<SkillEntry> finalSkills) {
        Matcher matcher = AVAILABLE_SKILLS_BLOCK.matcher(content);
        if (matcher.find()) {
            return matcher.replaceFirst(Matcher.quoteReplacement(OpenClawSkillUtil.skill2Content(finalSkills)));
        }
        return content;
    }

    private static String normalizeEscapedTags(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        return content
                .replace("\\u003c", "<")
                .replace("\\u003e", ">")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }


    private static String extractTagValue(String source, Pattern pattern) {
        Matcher matcher = pattern.matcher(source);
        if (!matcher.find()) {
            return null;
        }
        return xmlUnescape(matcher.group(1).trim());
    }

    private static String xmlEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String xmlUnescape(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
    }




}
