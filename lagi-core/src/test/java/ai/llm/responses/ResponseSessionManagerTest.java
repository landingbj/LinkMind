package ai.llm.responses;

import ai.common.ModelService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class ResponseSessionManagerTest {

    private final ResponseSessionManager sessionManager = ResponseSessionManager.getInstance();

    @Test
    void shouldReusePreviousResponseIdWhenMessagesAppend() {
        ModelService service = buildService();
        ChatCompletionRequest firstRequest = request("session-a", "hello");
        ResponseSessionContext firstContext = sessionManager.prepare(firstRequest, service);
        assertNull(firstContext.getPreviousResponseId());
        assertEquals(1, firstContext.getInputMessages().size());

        sessionManager.onSuccess(firstContext, "resp_1");

        ChatCompletionRequest secondRequest = new ChatCompletionRequest();
        secondRequest.setSessionId("session-a");
        secondRequest.setMessages(Arrays.asList(
                message("user", "hello"),
                message("assistant", "world"),
                message("user", "follow up")
        ));

        ResponseSessionContext secondContext = sessionManager.prepare(secondRequest, service);
        assertEquals("resp_1", secondContext.getPreviousResponseId());
        assertEquals(2, secondContext.getInputMessages().size());
        assertEquals("world", secondContext.getInputMessages().get(0).getContent());
        assertEquals("follow up", secondContext.getInputMessages().get(1).getContent());
    }

    @Test
    void shouldFallbackToFullMessagesWhenHistoryDoesNotAppend() {
        ModelService service = buildService();
        ChatCompletionRequest firstRequest = request("session-b", "hello");
        ResponseSessionContext firstContext = sessionManager.prepare(firstRequest, service);
        sessionManager.onSuccess(firstContext, "resp_2");

        ChatCompletionRequest sameRequest = request("session-b", "hello");
        ResponseSessionContext sameContext = sessionManager.prepare(sameRequest, service);
        assertNull(sameContext.getPreviousResponseId());
        assertEquals(1, sameContext.getInputMessages().size());

        ChatCompletionRequest changedRequest = new ChatCompletionRequest();
        changedRequest.setSessionId("session-b");
        changedRequest.setMessages(Collections.singletonList(message("user", "hello changed")));
        ResponseSessionContext changedContext = sessionManager.prepare(changedRequest, service);
        assertNull(changedContext.getPreviousResponseId());
        assertEquals(1, changedContext.getInputMessages().size());
        assertEquals("hello changed", changedContext.getInputMessages().get(0).getContent());
    }

    private ModelService buildService() {
        ModelService service = new ModelService();
        service.setBackend("chatgpt");
        service.setModel("gpt-4o-mini");
        service.setProtocol(ResponseProtocolConstants.RESPONSE);
        return service;
    }

    private ChatCompletionRequest request(String sessionId, String prompt) {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setSessionId(sessionId);
        request.setMessages(Collections.singletonList(message("user", prompt)));
        return request;
    }

    private ChatMessage message(String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }
}
