package ai.utils;

import ai.common.pojo.WordRule;
import ai.common.pojo.WordRules;
import ai.openai.pojo.ChatCompletionChoice;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.regex.PatternSyntaxException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SensitiveWordUtilTest {

    @AfterEach
    void resetRules() {
        SensitiveWordUtil.reloadRules(null, null, -1);
    }

    @Test
    void outputAndInputRulesReloadImmediately() {
        SensitiveWordUtil.reloadOutputRules(rules(rule("secret", 2, "***")));
        SensitiveWordUtil.reloadInputRules(rules(rule("prompt", 3, "***")));

        assertEquals("*** value", SensitiveWordUtil.filter("secret value", SensitiveWordUtil.OUTPUT_RULE_TYPE));
        assertEquals(" injection", SensitiveWordUtil.filter("prompt injection", SensitiveWordUtil.INPUT_RULE_TYPE));
    }

    @Test
    void oldRuleStopsMatchingAfterReload() {
        SensitiveWordUtil.reloadOutputRules(rules(rule("old", 2, "***")));
        assertEquals("*** value", SensitiveWordUtil.filter("old value", SensitiveWordUtil.OUTPUT_RULE_TYPE));

        SensitiveWordUtil.reloadOutputRules(rules(rule("new", 2, "***")));

        assertEquals("old value", SensitiveWordUtil.filter("old value", SensitiveWordUtil.OUTPUT_RULE_TYPE));
        assertEquals("*** value", SensitiveWordUtil.filter("new value", SensitiveWordUtil.OUTPUT_RULE_TYPE));
    }

    @Test
    void maskWithReplacementSpecialCharactersDoesNotThrow() {
        SensitiveWordUtil.reloadOutputRules(rules(rule("secret", 2, "$x\\y")));

        assertEquals("$x\\y value", SensitiveWordUtil.filter("secret value", SensitiveWordUtil.OUTPUT_RULE_TYPE));
    }

    @Test
    void invalidRegexIsRejectedWithoutReplacingExistingRules() {
        SensitiveWordUtil.reloadOutputRules(rules(rule("safe", 2, "***")));

        assertThrows(PatternSyntaxException.class,
                () -> SensitiveWordUtil.reloadOutputRules(rules(rule("[", 2, "***"))));

        assertEquals("*** text", SensitiveWordUtil.filter("safe text", SensitiveWordUtil.OUTPUT_RULE_TYPE));
    }

    @Test
    void nullChatCompletionResultIsReturnedSafely() {
        assertNull(SensitiveWordUtil.filter4ChatCompletionResult(null));

        ChatCompletionResult empty = new ChatCompletionResult();
        assertSame(empty, SensitiveWordUtil.filter4ChatCompletionResult(empty));
    }

    @Test
    void chatCompletionResultContentIsFiltered() {
        SensitiveWordUtil.reloadOutputRules(rules(rule("secret", 2, "***")));
        ChatCompletionResult result = resultWithMessage("secret text");

        SensitiveWordUtil.filter4ChatCompletionResult(result);

        assertEquals("*** text", result.getChoices().get(0).getMessage().getContent());
    }

    private WordRules rules(WordRule... rules) {
        return WordRules.builder().rules(Arrays.asList(rules)).build();
    }

    private WordRule rule(String rule, int level, String mask) {
        return WordRule.builder().rule(rule).level(level).mask(mask).build();
    }

    private ChatCompletionResult resultWithMessage(String content) {
        ChatMessage message = ChatMessage.builder().content(content).build();
        ChatCompletionChoice choice = new ChatCompletionChoice();
        choice.setMessage(message);
        ChatCompletionResult result = new ChatCompletionResult();
        result.setChoices(Collections.singletonList(choice));
        return result;
    }
}
