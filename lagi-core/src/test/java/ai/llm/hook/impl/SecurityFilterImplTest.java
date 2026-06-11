package ai.llm.hook.impl;

import ai.common.pojo.WordRule;
import ai.common.pojo.WordRules;
import ai.llm.pojo.ModelContext;
import ai.openai.pojo.ChatCompletionChoice;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.openai.pojo.Usage;
import ai.utils.SensitiveWordUtil;
import io.reactivex.Observable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class SecurityFilterImplTest {

    @AfterEach
    void resetRules() {
        SensitiveWordUtil.reloadRules(null, null, -1);
    }

    @Test
    void beforeModelSkipsNullContent() {
        SecurityFilterImpl filter = new SecurityFilterImpl();
        ChatCompletionRequest request = new ChatCompletionRequest();
        ChatMessage message = ChatMessage.builder().role("user").content(null).build();
        request.setMessages(Collections.singletonList(message));

        assertSame(request, filter.beforeModel(ModelContext.builder().request(request).build()));
        assertNull(message.getContent());
    }

    @Test
    void beforeModelFiltersInputRule() {
        SensitiveWordUtil.reloadInputRules(WordRules.builder()
                .rules(Collections.singletonList(rule("prompt", 3, "***")))
                .build());
        SecurityFilterImpl filter = new SecurityFilterImpl();
        ChatCompletionRequest request = new ChatCompletionRequest();
        ChatMessage message = ChatMessage.builder().role("user").content("prompt injection").build();
        request.setMessages(Collections.singletonList(message));

        filter.beforeModel(ModelContext.builder().request(request).build());

        assertEquals(" injection", message.getContent());
    }

    @Test
    void beforeModelBuildsLocalResultForBlockedInput() {
        SensitiveWordUtil.reloadInputRules(WordRules.builder()
                .rules(Collections.singletonList(rule("blocked", 1, "***")))
                .build());
        SecurityFilterImpl filter = new SecurityFilterImpl();
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("test-model");
        ChatMessage message = ChatMessage.builder().role("user").content("blocked prompt").build();
        request.setMessages(Collections.singletonList(message));

        filter.beforeModel(ModelContext.builder().request(request).build());

        assertEquals("", message.getContent());
        assertEquals(Boolean.FALSE, request.getEnableAfter());
        assertEquals("content_filter", request.getLocalCompletionResult().getChoices().get(0).getFinish_reason());
        assertEquals("该问题触发安全过滤规则，已停止生成回答。请调整提问内容后重试。",
                request.getLocalCompletionResult().getChoices().get(0).getMessage().getContent());
    }

    @Test
    void applyUsesNullSafeOutputFiltering() {
        SecurityFilterImpl filter = new SecurityFilterImpl();
        assertNull(filter.apply(null));

        SensitiveWordUtil.reloadOutputRules(WordRules.builder()
                .rules(Collections.singletonList(rule("secret", 2, "***")))
                .build());
        ChatCompletionResult result = messageResult("secret text");

        filter.apply(ModelContext.builder().result(result).build());

        assertEquals("*** text", result.getChoices().get(0).getMessage().getContent());
    }

    @Test
    void streamFiltersAcrossChunksAndPassesUsageAndFinishChunks() throws Exception {
        SensitiveWordUtil.reloadOutputRules(WordRules.builder()
                .rules(Collections.singletonList(rule("secret", 2, "***")))
                .build());
        SecurityFilterImpl filter = new SecurityFilterImpl();
        setQueueCapacity(filter, 2);

        ChatCompletionResult first = deltaResult("s", null);
        ChatCompletionResult second = deltaResult("ecret", null);
        ChatCompletionResult finish = deltaResult(null, "stop");
        ChatCompletionResult usage = new ChatCompletionResult();
        usage.setUsage(Usage.builder().total_tokens(3).build());

        List<ChatCompletionResult> emitted = filter.stream(ModelContext.builder()
                        .streamResult(Observable.fromArray(first, second, usage, finish))
                        .build())
                .toList()
                .blockingGet();

        assertEquals(4, emitted.size());
        assertEquals("***", emitted.get(0).getChoices().get(0).getDelta().getContent());
        assertEquals("***", emitted.get(1).getChoices().get(0).getDelta().getContent());
        assertEquals(3, emitted.get(2).getUsage().getTotal_tokens());
        assertEquals("stop", emitted.get(3).getChoices().get(0).getFinish_reason());
    }

    @Test
    void streamNormalizesInvalidQueueCapacity() throws Exception {
        SensitiveWordUtil.reloadOutputRules(WordRules.builder()
                .rules(Collections.singletonList(rule("secret", 2, "***")))
                .build());
        SecurityFilterImpl filter = new SecurityFilterImpl();
        setQueueCapacity(filter, 0);

        List<ChatCompletionResult> emitted = filter.stream(ModelContext.builder()
                        .streamResult(Observable.fromArray(deltaResult("secret", null)))
                        .build())
                .toList()
                .blockingGet();

        assertEquals(1, emitted.size());
        assertEquals("***", emitted.get(0).getChoices().get(0).getDelta().getContent());
    }

    private WordRule rule(String rule, int level, String mask) {
        return WordRule.builder().rule(rule).level(level).mask(mask).build();
    }

    private ChatCompletionResult messageResult(String content) {
        ChatCompletionChoice choice = new ChatCompletionChoice();
        choice.setMessage(ChatMessage.builder().content(content).build());
        ChatCompletionResult result = new ChatCompletionResult();
        result.setChoices(Collections.singletonList(choice));
        return result;
    }

    private ChatCompletionResult deltaResult(String content, String finishReason) {
        ChatCompletionChoice choice = new ChatCompletionChoice();
        choice.setDelta(ChatMessage.builder().content(content).build());
        choice.setFinish_reason(finishReason);
        ChatCompletionResult result = new ChatCompletionResult();
        result.setChoices(Collections.singletonList(choice));
        return result;
    }

    private void setQueueCapacity(SecurityFilterImpl filter, Integer value) throws Exception {
        Field field = SecurityFilterImpl.class.getDeclaredField("queueCapacity");
        field.setAccessible(true);
        field.set(filter, value);
    }
}
