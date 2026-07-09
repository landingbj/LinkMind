package ai.llm.hook.impl;

import ai.llm.pojo.ModelContext;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;

class InputCompressionImplTest {

    @Test
    void beforeModelKeepsMessagesWhenPreserveInputMessagesIsEnabled() {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setPreserveInputMessages(true);
        request.setMessages(Arrays.asList(
                ChatMessage.builder().role("system").content("系统提示").build(),
                ChatMessage.builder().role("user").content("第一轮").build(),
                ChatMessage.builder().role("assistant").content("第一轮回答").build(),
                ChatMessage.builder().role("user").content("第二轮").build()
        ));

        ChatCompletionRequest result = new InputCompressionImpl().beforeModel(ModelContext.builder()
                .request(request)
                .build());

        assertSame(request, result);
        assertEquals(4, result.getMessages().size());
        assertEquals("第一轮", result.getMessages().get(1).getContent());
    }
}
