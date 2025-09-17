package ai.utils;

import ai.common.pojo.WordRule;
import ai.common.pojo.WordRules;
import ai.common.utils.LRUCache;
import ai.config.ContextLoader;
import ai.config.pojo.FilterConfig;
import ai.openai.pojo.ChatCompletionChoice;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class SensitiveWordUtil {
    private static final Map<String, WordRule> ruleMap = new HashMap<>();
    private static final Map<String, Pattern> patternCache = new HashMap<>();
    private static final List<FilterConfig> filterConfigs = ContextLoader.configuration.getFilters();
    private static int filterWindowLength = -1;

    private static final LRUCache<String, String> filterSlidingWindow = new LRUCache<>(10000, 30, TimeUnit.MINUTES);
    private static final LRUCache<String, Boolean> blockMap = new LRUCache<>(10000, 30, TimeUnit.MINUTES);

    static {
        WordRules wordRules = JsonFileLoadUtil.readWordLRulesList("/sensitive_word.json", WordRules.class);
        pushWordRule(wordRules);
        if (filterConfigs != null) {
            for (FilterConfig filter : filterConfigs) {
                if (filter.getName().equals("sensitive")) {
                    filterWindowLength = filter.getFilterWindowLength();
                }
            }
        }
    }

    public static void pushWordRule(WordRules wordRules) {
        if (wordRules != null) {
            if (wordRules.getRules() != null) {
                wordRules.getRules().stream()
                        .filter(r -> StrUtil.isNotBlank(r.getRule()))
                        .peek(r -> {
                            r.setRule(r.getRule().toLowerCase());
                            if (r.getMask() == null) {
                                r.setMask(wordRules.getMask());
                            }
                            if (r.getLevel() == null) {
                                r.setLevel(wordRules.getLevel());
                            }
                        })
                        .forEach(r -> {
                            ruleMap.put(r.getRule(), r);
                        });
            }
        }
    }

    private static Pattern getCompiledPattern(String rule) {
        return patternCache.computeIfAbsent(rule, Pattern::compile);
    }

    public static ChatCompletionResult filter(ChatCompletionResult chatCompletionResult) {
        return filter(chatCompletionResult, false);
    }

    public static ChatCompletionResult filter(ChatCompletionResult chatCompletionResult, boolean stream) {
        if (filterWindowLength <= 0 || chatCompletionResult == null || chatCompletionResult.getChoices() == null || chatCompletionResult.getChoices().isEmpty()) {
            return chatCompletionResult;
        }
        ChatCompletionChoice chatCompletionChoice = chatCompletionResult.getChoices().get(0);
        ChatMessage chatMessage;

        if (stream) {
            if (chatCompletionChoice.getDelta() == null) {
                return chatCompletionResult;
            }
            if (chatCompletionChoice.getDelta().getContent() == null && chatCompletionChoice.getFinish_reason() == null) {
                return chatCompletionResult;
            }
            chatMessage = chatCompletionChoice.getDelta();
        } else {
            if (chatCompletionChoice.getMessage() == null) {
                return chatCompletionResult;
            }
            if (chatCompletionChoice.getMessage().getContent() == null && chatCompletionChoice.getFinish_reason() == null) {
                return chatCompletionResult;
            }
            chatMessage = chatCompletionChoice.getMessage();
        }

        String finishReason = chatCompletionChoice.getFinish_reason();
        String id = chatCompletionResult.getId();

        if (blockMap.containsKey(id)) {
            chatMessage.setContent("");
            return chatCompletionResult;
        }

        if (finishReason != null && stream) {
            if (filterSlidingWindow.containsKey(id)) {
                if (chatMessage.getContent() != null) {
                    chatMessage.setContent(filterSlidingWindow.get(id) + chatMessage.getContent());
                } else {
                    chatMessage.setContent(filterSlidingWindow.get(id));
                }
            }
            return chatCompletionResult;
        }
        String content = chatMessage.getContent();
        if (filterSlidingWindow.containsKey(id)) {
            String oldContent = filterSlidingWindow.get(id);
            chatMessage.setContent(oldContent + content);
        } else {
            filterSlidingWindow.put(id, content);
        }
        Set<String> rules = ruleMap.keySet();
        for (String rule : rules) {
            Pattern p = getCompiledPattern(rule);
            String message = chatMessage.getContent().toLowerCase();
            Matcher matcher = p.matcher(message);
            if (matcher.find()) {
                log.info("sensitive message: {} match group: {}", message, matcher.group());
                WordRule wordRule = ruleMap.get(rule);
                if (wordRule != null) {
                    String replaceContent = "";
                    if (wordRule.getLevel() == 1) {
                        blockMap.put(id, true);
                        break;
                    } else if (wordRule.getLevel() == 2) {
                        replaceContent = message.replaceAll(rule, wordRule.getMask());
                    } else if (wordRule.getLevel() == 3) {
                        replaceContent = message.replaceAll(rule, "");
                    }
                    chatMessage.setContent(replaceContent);
                }
            }
        }

        if (blockMap.containsKey(id)) {
            chatMessage.setContent("");
            return chatCompletionResult;
        }

        String tempContent = chatMessage.getContent();
        if (tempContent.length() > filterWindowLength) {
            chatMessage.setContent(tempContent.substring(0, tempContent.length() - filterWindowLength));
            filterSlidingWindow.put(id, tempContent.substring(tempContent.length() - filterWindowLength));
        } else {
            chatMessage.setContent("");
            filterSlidingWindow.put(id, tempContent);
        }
        return chatCompletionResult;
    }
}
