package ai.llm.responses;

import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatMessage;
import ai.openai.pojo.ChatCompletionResult;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class ResponsesChatCompletionConverterTest {

    @Test
    void shouldConvertResponseBodyToChatCompletionResult() {
        String body = "{\n" +
                "  \"id\": \"resp_123\",\n" +
                "  \"model\": \"gpt-4.1\",\n" +
                "  \"output\": [\n" +
                "    {\n" +
                "      \"type\": \"reasoning\",\n" +
                "      \"summary\": [{\"text\": \"reasoning summary\"}]\n" +
                "    },\n" +
                "    {\n" +
                "      \"type\": \"function_call\",\n" +
                "      \"id\": \"fc_1\",\n" +
                "      \"call_id\": \"call_1\",\n" +
                "      \"name\": \"lookup\",\n" +
                "      \"arguments\": \"{\\\"city\\\":\\\"beijing\\\"}\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"type\": \"message\",\n" +
                "      \"role\": \"assistant\",\n" +
                "      \"content\": [\n" +
                "        {\"type\": \"output_text\", \"text\": \"hello \"},\n" +
                "        {\"type\": \"output_text\", \"text\": \"world\"}\n" +
                "      ]\n" +
                "    }\n" +
                "  ],\n" +
                "  \"usage\": {\n" +
                "    \"input_tokens\": 10,\n" +
                "    \"output_tokens\": 5,\n" +
                "    \"total_tokens\": 15\n" +
                "  }\n" +
                "}";

        ChatCompletionResult result = ResponsesChatCompletionConverter.convertResponse(body);
        assertEquals("resp_123", result.getId());
        assertEquals("gpt-4.1", result.getModel());
        assertEquals("hello world", result.getChoices().get(0).getMessage().getContent());
        assertEquals("reasoning summary", result.getChoices().get(0).getMessage().getReasoning_content());
        assertEquals(1, result.getChoices().get(0).getMessage().getTool_calls().size());
        assertEquals("lookup", result.getChoices().get(0).getMessage().getTool_calls().get(0).getFunction().getName());
        assertEquals(10, result.getUsage().getPrompt_tokens());
        assertEquals(5, result.getUsage().getCompletion_tokens());
        assertEquals(15, result.getUsage().getTotal_tokens());
    }

    @Test
    void shouldConvertStreamEvents() {
        String deltaEvent = "{\"type\":\"response.output_text.delta\",\"response_id\":\"resp_456\",\"delta\":\"hi\"}";
        ChatCompletionResult delta = ResponsesChatCompletionConverter.convertStreamEvent(deltaEvent);
        assertNotNull(delta);
        assertEquals("resp_456", delta.getId());
        assertEquals("hi", delta.getChoices().get(0).getMessage().getContent());

        String completedEvent = "{\n" +
                "  \"type\": \"response.completed\",\n" +
                "  \"response\": {\n" +
                "    \"id\": \"resp_456\",\n" +
                "    \"output\": [{\"type\": \"message\", \"role\": \"assistant\", \"content\": [{\"type\": \"output_text\", \"text\": \"done\"}]}],\n" +
                "    \"usage\": {\"input_tokens\": 1, \"output_tokens\": 2, \"total_tokens\": 3}\n" +
                "  }\n" +
                "}";
        ChatCompletionResult completed = ResponsesChatCompletionConverter.convertStreamEvent(completedEvent);
        assertNotNull(completed);
        assertEquals("resp_456", completed.getId());
        assertEquals("stop", completed.getChoices().get(0).getFinish_reason());
        assertEquals("", completed.getChoices().get(0).getMessage().getContent());
        assertEquals(3, completed.getUsage().getTotal_tokens());
    }

    @Test
    void shouldMapAssistantHistoryToOutputText() {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("gpt-5.4");

        ResponseSessionContext context = new ResponseSessionContext();
        context.setInputMessages(Collections.singletonList(ChatMessage.builder()
                .role("assistant")
                .content("我是助手历史消息")
                .build()));

        ResponseCreateRequest responseRequest = ResponsesChatCompletionConverter.toRequest(request, context, "gpt-5.4");
        assertEquals("assistant", responseRequest.getInput().get(0).getRole());
        assertEquals("output_text", responseRequest.getInput().get(0).getContent().get(0).getType());
        assertEquals("我是助手历史消息", responseRequest.getInput().get(0).getContent().get(0).getText());
    }

    @Test
    void shouldMoveSystemPromptToInstructions() {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("gpt-5.4");

        ResponseSessionContext context = new ResponseSessionContext();
        context.setNormalizedMessages(java.util.Arrays.asList(
                ChatMessage.builder().role("system").content("你是一个有用的助理").build(),
                ChatMessage.builder().role("user").content("你好").build()
        ));
        context.setInputMessages(context.getNormalizedMessages());

        ResponseCreateRequest responseRequest = ResponsesChatCompletionConverter.toRequest(request, context, "gpt-5.4");
        assertEquals("你是一个有用的助理", responseRequest.getInstructions());
        assertEquals(1, responseRequest.getInput().size());
        assertEquals("user", responseRequest.getInput().get(0).getRole());
    }
}
