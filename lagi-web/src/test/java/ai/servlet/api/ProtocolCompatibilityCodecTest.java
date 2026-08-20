package ai.servlet.api;

import ai.openai.pojo.ChatCompletionChoice;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.openai.pojo.ToolCall;
import ai.openai.pojo.ToolCallFunction;
import ai.openai.pojo.Usage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolCompatibilityCodecTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ProtocolCompatibilityCodec codec = new ProtocolCompatibilityCodec();

    @Test
    void convertsAnthropicToolUseAndToolResultToChatCompletions() throws Exception {
        JsonNode request = MAPPER.readTree("{"
                + "\"model\":\"claude-sonnet\",\"max_tokens\":512,"
                + "\"system\":\"Be concise\","
                + "\"messages\":["
                + "{\"role\":\"assistant\",\"content\":[{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"read_file\",\"input\":{\"path\":\"README.md\"}}]},"
                + "{\"role\":\"user\",\"content\":[{\"type\":\"tool_result\",\"tool_use_id\":\"toolu_1\",\"content\":\"contents\"},{\"type\":\"text\",\"text\":\"continue\"}]}"
                + "]}");

        ChatCompletionRequest converted = codec.toAnthropicRequest(request);

        assertEquals("claude-sonnet", converted.getModel());
        assertEquals("system", converted.getMessages().get(0).getRole());
        assertEquals("read_file", converted.getMessages().get(1).getTool_calls().get(0).getFunction().getName());
        assertEquals("toolu_1", converted.getMessages().get(2).getTool_call_id());
        assertEquals("contents", converted.getMessages().get(2).getContent());
        assertEquals("continue", converted.getMessages().get(3).getContent());
    }

    @Test
    void convertsResponsesInputAndPreviousHistory() throws Exception {
        JsonNode request = MAPPER.readTree("{"
                + "\"model\":\"gpt-5.4\",\"instructions\":\"Use tools\","
                + "\"input\":[{\"type\":\"function_call_output\",\"call_id\":\"call_1\",\"output\":\"ok\"},"
                + "{\"role\":\"user\",\"content\":[{\"type\":\"input_text\",\"text\":\"next\"}]}]}");
        ChatMessage prior = new ChatMessage();
        prior.setRole("assistant");
        prior.setContent("earlier");

        ChatCompletionRequest converted = codec.toResponsesRequest(request, Collections.singletonList(prior));

        assertEquals("assistant", converted.getMessages().get(0).getRole());
        assertEquals("system", converted.getMessages().get(1).getRole());
        assertEquals("tool", converted.getMessages().get(2).getRole());
        assertEquals("call_1", converted.getMessages().get(2).getTool_call_id());
        assertEquals("next", converted.getMessages().get(3).getContent());
    }

    @Test
    void emitsAnthropicAndResponsesToolResults() throws Exception {
        ChatCompletionResult result = completionWithToolCall();

        JsonNode anthropic = codec.toAnthropicResponse(result, "claude-sonnet");
        assertEquals("message", anthropic.path("type").asText());
        assertEquals("text", anthropic.path("content").get(0).path("type").asText());
        assertEquals("tool_use", anthropic.path("content").get(1).path("type").asText());
        assertEquals("tool_use", anthropic.path("stop_reason").asText());

        JsonNode responses = codec.toResponsesResponse(result, "gpt-5.4", "resp_test");
        assertEquals("response", responses.path("object").asText());
        assertEquals("function_call", responses.path("output").get(1).path("type").asText());
        assertEquals("call_1", responses.path("output").get(1).path("call_id").asText());
    }

    @Test
    void convertsStreamingChatChunksToResponsesEvents() {
        ProtocolCompatibilityCodec.StreamTranslator translator =
                codec.newStreamTranslator(ProtocolCompatibilityCodec.Protocol.RESPONSES, "gpt-5.4");
        List<String> events = translator.accept("{\"model\":\"gpt-5.4\",\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}");
        List<String> completed = translator.finish();

        assertTrue(events.stream().anyMatch(event -> event.contains("response.output_text.delta")));
        assertTrue(completed.stream().anyMatch(event -> event.contains("response.completed")));
        assertFalse(translator.getAssistantMessage().getContent().isEmpty());
    }

    @Test
    void convertsStreamingChatChunksToAnthropicEvents() {
        ProtocolCompatibilityCodec.StreamTranslator translator =
                codec.newStreamTranslator(ProtocolCompatibilityCodec.Protocol.ANTHROPIC, "claude-sonnet");
        List<String> events = translator.accept("{\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}");
        List<String> completed = translator.finish();

        assertTrue(events.stream().anyMatch(event -> event.startsWith("event: message_start")));
        assertTrue(events.stream().anyMatch(event -> event.contains("content_block_delta")));
        assertTrue(completed.stream().anyMatch(event -> event.startsWith("event: message_stop")));
    }

    private ChatCompletionResult completionWithToolCall() {
        ToolCallFunction function = new ToolCallFunction();
        function.setName("read_file");
        function.setArguments("{\"path\":\"README.md\"}");
        ToolCall toolCall = new ToolCall();
        toolCall.setId("call_1");
        toolCall.setType("function");
        toolCall.setFunction(function);

        ChatMessage message = new ChatMessage();
        message.setRole("assistant");
        message.setContent("I will inspect it.");
        message.setTool_calls(Collections.singletonList(toolCall));
        ChatCompletionChoice choice = new ChatCompletionChoice();
        choice.setMessage(message);
        choice.setFinish_reason("tool_calls");

        Usage usage = new Usage();
        usage.setPrompt_tokens(8);
        usage.setCompletion_tokens(5);
        usage.setTotal_tokens(13);
        ChatCompletionResult result = new ChatCompletionResult();
        result.setModel("gpt-5.4");
        result.setChoices(Collections.singletonList(choice));
        result.setUsage(usage);
        return result;
    }
}
