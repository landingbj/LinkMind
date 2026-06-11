package ai.utils;

import ai.common.pojo.WordRule;
import ai.common.pojo.WordRules;
import ai.common.utils.LRUCache;
import ai.openai.pojo.ChatCompletionChoice;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class SensitiveWordUtil {
    private static volatile RuleState outputRuleState = RuleState.empty();
    private static volatile RuleState inputRuleState = RuleState.empty();
    private static int filterWindowLength = -1;

    public static final String INPUT_RULE_TYPE = "input";
    public static final String OUTPUT_RULE_TYPE = "output";

    /** Monitor filter_name for input sensitive rules. */
    public static final String MONITOR_FILTER_INPUT = "sensitive_input";
    /** Monitor filter_name for output sensitive rules. */
    public static final String MONITOR_FILTER_OUTPUT = "sensitive";

    private static final LRUCache<String, String> filterSlidingWindow = new LRUCache<>(10000, 30, TimeUnit.MINUTES);
    private static final LRUCache<String, Boolean> blockMap = new LRUCache<>(10000, 30, TimeUnit.MINUTES);

    static {
        reloadOutputRules(JsonFileLoadUtil.readWordLRulesList("/sensitive_word.json", WordRules.class));
        reloadInputRules(JsonFileLoadUtil.readWordLRulesList("/sensitive_input.json", WordRules.class));
    }

    public static synchronized void setFilterWindowLength(int length) {
        filterWindowLength = length;
    }

    public static void pushOutputRule(WordRules wordRules) {
        reloadOutputRules(wordRules);
    }

    public static void pushInputRule(WordRules wordRules) {
        reloadInputRules(wordRules);
    }

    public static synchronized void reloadOutputRules(WordRules wordRules) {
        outputRuleState = buildRuleState(wordRules);
        clearRuntimeState();
    }

    public static synchronized void reloadInputRules(WordRules wordRules) {
        inputRuleState = buildRuleState(wordRules);
        clearRuntimeState();
    }

    public static synchronized void reloadRules(WordRules outputRules, WordRules inputRules, int windowLength) {
        RuleState newOutputRuleState = buildRuleState(outputRules);
        RuleState newInputRuleState = buildRuleState(inputRules);
        outputRuleState = newOutputRuleState;
        inputRuleState = newInputRuleState;
        filterWindowLength = windowLength;
        clearRuntimeState();
    }

    public static void validateRules(WordRules wordRules) {
        buildRuleState(wordRules);
    }

    public static boolean isInputBlocked(String message) {
        RuleMatch match = findFirstRuleMatch(message, inputRuleState);
        return match != null && match.wordRule.getLevel() != null && match.wordRule.getLevel() == 1;
    }

    private static void clearRuntimeState() {
        filterSlidingWindow.clear();
        blockMap.clear();
    }

    private static String monitorNameForRuleType(String ruleType) {
        return INPUT_RULE_TYPE.equalsIgnoreCase(ruleType) ? MONITOR_FILTER_INPUT : MONITOR_FILTER_OUTPUT;
    }

    private static RuleState getRuleState(String type) {
        if (INPUT_RULE_TYPE.equalsIgnoreCase(type)) {
            return inputRuleState;
        }
        return outputRuleState;
    }

    private static RuleState buildRuleState(WordRules wordRules) {
        if (wordRules == null || wordRules.getRules() == null) {
            return RuleState.empty();
        }
        Map<String, WordRule> rules = new LinkedHashMap<>();
        Map<String, Pattern> patterns = new LinkedHashMap<>();
        for (WordRule originalRule : wordRules.getRules()) {
            if (originalRule == null || StrUtil.isBlank(originalRule.getRule())) {
                continue;
            }
            WordRule rule = copyRuleWithDefaults(originalRule, wordRules);
            Pattern pattern = Pattern.compile(rule.getRule());
            rules.put(rule.getRule(), rule);
            patterns.put(rule.getRule(), pattern);
        }
        return new RuleState(rules, patterns);
    }

    private static WordRule copyRuleWithDefaults(WordRule originalRule, WordRules defaults) {
        String mask = originalRule.getMask();
        if (mask == null) {
            mask = defaults.getMask();
        }
        Integer level = originalRule.getLevel();
        if (level == null) {
            level = defaults.getLevel();
        }
        return WordRule.builder()
                .rule(originalRule.getRule())
                .mask(mask)
                .level(level)
                .build();
    }

    public static String filter(String message, String ruleType) {
        return filter(message, Integer.MAX_VALUE, ruleType);
    }

    public static String filter(String message, int times, String ruleType) {
        if (message == null || times <= 0) {
            return message;
        }
        int count = 0;
        RuleState state = getRuleState(ruleType);
        for (Map.Entry<String, Pattern> entry : state.patterns.entrySet()) {
            String rule = entry.getKey();
            Matcher matcher = entry.getValue().matcher(message);
            if (matcher.find()) {
                log.info("sensitive message: {} match group: {} \t rule:{}", message, matcher.group(), rule);
                WordRule wordRule = state.rules.get(rule);
                if (wordRule != null) {
                    String filterContent = buildFilterContent(rule, message);
                    if (wordRule.getLevel() == 1) {
                        message = "";
                        FilterMonitorUtil.recordFilterAction(monitorNameForRuleType(ruleType), "block", filterContent);
                        break;
                    } else if (wordRule.getLevel() == 2) {
                        message = entry.getValue().matcher(message).replaceAll(Matcher.quoteReplacement(defaultMask(wordRule)));
                        FilterMonitorUtil.recordFilterAction(monitorNameForRuleType(ruleType), "mask", filterContent);
                    } else if (wordRule.getLevel() == 3) {
                        message = entry.getValue().matcher(message).replaceAll("");
                        FilterMonitorUtil.recordFilterAction(monitorNameForRuleType(ruleType), "erase", filterContent);
                    }
                    count++;
                }
                if (count >= times) {
                    break;
                }
            }
        }
        return message;
    }

    private static String buildFilterContent(String rule, String message) {
        return "匹配规则: " + rule + ", 原始内容: " + (message.length() > 500 ? message.substring(0, 500) : message);
    }

    private static String defaultMask(WordRule wordRule) {
        return wordRule.getMask() == null ? "***" : wordRule.getMask();
    }

    private static final class RuleState {
        final Map<String, WordRule> rules;
        final Map<String, Pattern> patterns;

        RuleState(Map<String, WordRule> rules, Map<String, Pattern> patterns) {
            this.rules = Collections.unmodifiableMap(new LinkedHashMap<>(rules));
            this.patterns = Collections.unmodifiableMap(new LinkedHashMap<>(patterns));
        }

        static RuleState empty() {
            return new RuleState(Collections.emptyMap(), Collections.emptyMap());
        }
    }

    private static final class RuleMatch {
        final String rule;
        final WordRule wordRule;
        final Pattern pattern;
        final String filterContent;

        RuleMatch(String rule, WordRule wordRule, Pattern pattern, String filterContent) {
            this.rule = rule;
            this.wordRule = wordRule;
            this.pattern = pattern;
            this.filterContent = filterContent;
        }
    }

    private static RuleMatch findFirstRuleMatch(String message, RuleState state) {
        if (message == null) {
            return null;
        }
        for (Map.Entry<String, Pattern> entry : state.patterns.entrySet()) {
            String rule = entry.getKey();
            Matcher matcher = entry.getValue().matcher(message);
            if (matcher.find()) {
                WordRule wordRule = state.rules.get(rule);
                if (wordRule != null) {
                    return new RuleMatch(rule, wordRule, entry.getValue(), buildFilterContent(rule, message));
                }
            }
        }
        return null;
    }

    private static RuleMatch findFirstOutputRuleMatch(String message) {
        return findFirstRuleMatch(message, outputRuleState);
    }

    public static String getNullOrReplaceContent(String message) {
        RuleMatch match = findFirstOutputRuleMatch(message);
        if (match == null) {
            return null;
        }
        Integer level = match.wordRule.getLevel();
        if (level == null) {
            return null;
        }
        if (level == 1) {
            return "";
        } else if (level == 2) {
            return defaultMask(match.wordRule);
        } else if (level == 3) {
            return "";
        }
        return null;
    }

    public static String replaceOutputContent(String message) {
        RuleMatch match = findFirstOutputRuleMatch(message);
        if (match == null || match.wordRule.getLevel() == null) {
            return message;
        }
        if (match.wordRule.getLevel() == 1) {
            return "";
        } else if (match.wordRule.getLevel() == 2) {
            return match.pattern.matcher(message).replaceAll(Matcher.quoteReplacement(defaultMask(match.wordRule)));
        } else if (match.wordRule.getLevel() == 3) {
            return match.pattern.matcher(message).replaceAll("");
        }
        return message;
    }

    /**
     * Streaming output path records monitor data when the first output sensitive rule is hit.
     */
    public static void recordOutputStreamFilter(String message) {
        RuleMatch match = findFirstOutputRuleMatch(message);
        if (match == null) {
            return;
        }
        Integer level = match.wordRule.getLevel();
        if (level == null) {
            return;
        }
        String action;
        if (level == 1) {
            action = "block";
        } else if (level == 2) {
            action = "mask";
        } else if (level == 3) {
            action = "erase";
        } else {
            return;
        }
        FilterMonitorUtil.recordFilterAction(MONITOR_FILTER_OUTPUT, action, match.filterContent);
    }

    public static ChatCompletionResult filter4ChatCompletionResult(ChatCompletionResult chatCompletionResult) {
        if (chatCompletionResult == null
                || chatCompletionResult.getChoices() == null
                || chatCompletionResult.getChoices().isEmpty()
                || chatCompletionResult.getChoices().get(0) == null
                || chatCompletionResult.getChoices().get(0).getMessage() == null) {
            return chatCompletionResult;
        }
        ChatMessage message = chatCompletionResult.getChoices().get(0).getMessage();
        message.setContent(filter(message.getContent(), Integer.MAX_VALUE, OUTPUT_RULE_TYPE));
        return chatCompletionResult;
    }

    public static ChatCompletionResult filter(ChatCompletionResult chatCompletionResult) {
        return filter(chatCompletionResult, false);
    }

    public static ChatCompletionResult filter(ChatCompletionResult chatCompletionResult, boolean stream) {
        if (filterWindowLength <= 0
                || chatCompletionResult == null
                || chatCompletionResult.getChoices() == null
                || chatCompletionResult.getChoices().isEmpty()
                || chatCompletionResult.getChoices().get(0) == null) {
            return chatCompletionResult;
        }
        ChatCompletionChoice chatCompletionChoice = chatCompletionResult.getChoices().get(0);
        ChatMessage chatMessage = stream ? chatCompletionChoice.getDelta() : chatCompletionChoice.getMessage();
        if (chatMessage == null || (chatMessage.getContent() == null && chatCompletionChoice.getFinish_reason() == null)) {
            return chatCompletionResult;
        }

        String id = chatCompletionResult.getId();
        if (id == null) {
            id = "";
        }

        if (blockMap.containsKey(id)) {
            chatMessage.setContent("");
            return chatCompletionResult;
        }

        if (chatCompletionChoice.getFinish_reason() != null && stream) {
            if (filterSlidingWindow.containsKey(id)) {
                chatMessage.setContent(filterSlidingWindow.get(id) + (chatMessage.getContent() == null ? "" : chatMessage.getContent()));
            }
            return chatCompletionResult;
        }

        String content = chatMessage.getContent();
        if (content == null) {
            return chatCompletionResult;
        }
        if (filterSlidingWindow.containsKey(id)) {
            chatMessage.setContent(filterSlidingWindow.get(id) + content);
        } else {
            filterSlidingWindow.put(id, content);
        }

        RuleMatch match = findFirstOutputRuleMatch(chatMessage.getContent());
        if (match != null && match.wordRule.getLevel() != null) {
            log.info("sensitive message: {} match group: {}", chatMessage.getContent(), match.rule);
            if (match.wordRule.getLevel() == 1) {
                FilterMonitorUtil.recordFilterAction(MONITOR_FILTER_OUTPUT, "block", match.filterContent);
                blockMap.put(id, true);
            } else if (match.wordRule.getLevel() == 2) {
                chatMessage.setContent(match.pattern.matcher(chatMessage.getContent()).replaceAll(Matcher.quoteReplacement(defaultMask(match.wordRule))));
                FilterMonitorUtil.recordFilterAction(MONITOR_FILTER_OUTPUT, "mask", match.filterContent);
            } else if (match.wordRule.getLevel() == 3) {
                chatMessage.setContent(match.pattern.matcher(chatMessage.getContent()).replaceAll(""));
                FilterMonitorUtil.recordFilterAction(MONITOR_FILTER_OUTPUT, "erase", match.filterContent);
            }
        }

        if (blockMap.containsKey(id)) {
            chatMessage.setContent("");
            return chatCompletionResult;
        }

        if (stream) {
            String tempContent = chatMessage.getContent();
            if (tempContent == null) {
                return chatCompletionResult;
            }
            if (tempContent.length() > filterWindowLength) {
                chatMessage.setContent(tempContent.substring(0, tempContent.length() - filterWindowLength));
                filterSlidingWindow.put(id, tempContent.substring(tempContent.length() - filterWindowLength));
            } else {
                chatMessage.setContent("");
                filterSlidingWindow.put(id, tempContent);
            }
        }
        return chatCompletionResult;
    }

    public static synchronized void clearRuleMap() {
        outputRuleState = RuleState.empty();
        clearRuntimeState();
        filterWindowLength = -1;
    }

    public static synchronized void clearInputRuleMap() {
        inputRuleState = RuleState.empty();
        clearRuntimeState();
    }
}
