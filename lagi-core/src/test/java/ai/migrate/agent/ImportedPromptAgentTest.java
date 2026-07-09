package ai.migrate.agent;

import ai.migrate.pojo.HistoryRecord;
import ai.openai.pojo.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportedPromptAgentTest {

    @Test
    void mergeMessagesIncludesImportedHistoryAndCurrentConversation() {
        List<HistoryRecord> history = Arrays.asList(
                record("user", "预算3000，3天，想做国内短途旅行。"),
                record("assistant", "可以考虑重庆、成都、西安、青岛等目的地。")
        );
        List<ChatMessage> requestMessages = Arrays.asList(
                message("user", "对西安比较感兴趣"),
                message("assistant", "西安很适合短途历史文化旅行。"),
                message("user", "定制一份3天行程方案")
        );

        List<ChatMessage> messages = ImportedPromptAgent.mergeMessages(
                "继续历史对话。", history, requestMessages, "定制一份3天行程方案");

        assertEquals(6, messages.size());
        assertMessage(messages.get(0), "system", "继续历史对话。");
        assertMessage(messages.get(1), "user", "预算3000，3天，想做国内短途旅行。");
        assertMessage(messages.get(2), "assistant", "可以考虑重庆、成都、西安、青岛等目的地。");
        assertMessage(messages.get(3), "user", "对西安比较感兴趣");
        assertMessage(messages.get(4), "assistant", "西安很适合短途历史文化旅行。");
        assertMessage(messages.get(5), "user", "定制一份3天行程方案");
    }

    @Test
    void mergeMessagesDoesNotDuplicateConversationAlreadyInHistory() {
        List<HistoryRecord> history = Arrays.asList(
                record("user", "预算3000，3天，想做国内短途旅行。"),
                record("assistant", "可以考虑重庆、成都、西安、青岛等目的地。"),
                record("user", "对西安比较感兴趣"),
                record("assistant", "西安很适合短途历史文化旅行。")
        );
        List<ChatMessage> requestMessages = Arrays.asList(
                message("user", "对西安比较感兴趣"),
                message("assistant", "西安很适合短途历史文化旅行。"),
                message("user", "定制一份3天行程方案")
        );

        List<ChatMessage> messages = ImportedPromptAgent.mergeMessages(
                "", history, requestMessages, "定制一份3天行程方案");

        assertEquals(5, messages.size());
        assertMessage(messages.get(2), "user", "对西安比较感兴趣");
        assertMessage(messages.get(3), "assistant", "西安很适合短途历史文化旅行。");
        assertMessage(messages.get(4), "user", "定制一份3天行程方案");
    }

    @Test
    void mergeMessagesFallsBackToLatestUserWhenRequestMessagesAreMissing() {
        List<ChatMessage> messages = ImportedPromptAgent.mergeMessages(
                "", Collections.<HistoryRecord>emptyList(), null, "继续对话");

        assertEquals(1, messages.size());
        assertMessage(messages.get(0), "user", "继续对话");
    }

    @Test
    void mergeMessagesSkipsLookupPlaceholders() {
        List<HistoryRecord> history = Arrays.asList(
                record("user", "对西安比较感兴趣"),
                record("assistant", "好的！我来查找西安最新的旅游攻略，为您定制一份详细方案。"),
                record("user", "定制一份3天行程方案")
        );
        List<ChatMessage> requestMessages = Arrays.asList(
                message("assistant", "好的！现在就来为您查找西安最新的旅游信息，定制详细的3天行程！"),
                message("user", "请直接给出行程")
        );

        List<ChatMessage> messages = ImportedPromptAgent.mergeMessages(
                "直接回答。", history, requestMessages, "请直接给出行程");

        assertEquals(4, messages.size());
        assertMessage(messages.get(0), "system", "直接回答。");
        assertMessage(messages.get(1), "user", "对西安比较感兴趣");
        assertMessage(messages.get(2), "user", "定制一份3天行程方案");
        assertMessage(messages.get(3), "user", "请直接给出行程");
    }

    @Test
    void lookupPlaceholderDetectionKeepsSubstantialAnswers() {
        assertTrue(ImportedPromptAgent.isLookupPlaceholder("好的！我来查找西安最新的旅游攻略，为您定制一份详细方案。"));
        assertFalse(ImportedPromptAgent.isLookupPlaceholder("第1天上午游览陕西历史博物馆，下午去大雁塔，晚上逛大唐不夜城。"));
    }

    private HistoryRecord record(String role, String content) {
        HistoryRecord record = new HistoryRecord();
        record.setRole(role);
        record.setContent(content);
        return record;
    }

    private ChatMessage message(String role, String content) {
        return ChatMessage.builder().role(role).content(content).build();
    }

    private void assertMessage(ChatMessage message, String role, String content) {
        assertEquals(role, message.getRole());
        assertEquals(content, message.getContent());
    }
}
