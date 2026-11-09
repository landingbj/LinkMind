package ai.llm.utils;

import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.openai.pojo.Function;
import ai.openai.pojo.Tool;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiApiUtilTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void serializesMultimodalContentAsAnOpenAiContentArray() throws Exception {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("qwen3.7-plus");
        request.setMessages(Collections.singletonList(ChatMessage.builder()
                .role("user")
                .content("[{\"type\":\"text\",\"text\":\"图片上的文字是什么？\"},"
                        + "{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:image/png;base64,AAAA\","
                        + "\"detail\":\"high\"}}]")
                .build()));

        JsonNode payload = MAPPER.readTree(OpenAiApiUtil.serializeChatCompletionRequest(request));
        JsonNode content = payload.path("messages").get(0).path("content");

        assertTrue(content.isArray());
        assertEquals("图片上的文字是什么？", content.get(0).path("text").asText());
        assertEquals("image_url", content.get(1).path("type").asText());
        assertEquals("data:image/png;base64,AAAA", content.get(1).path("image_url").path("url").asText());
        assertEquals("high", content.get(1).path("image_url").path("detail").asText());
    }

    @Test
    void keepsOrdinaryTextAsAString() throws Exception {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setMessages(Collections.singletonList(ChatMessage.builder()
                .role("user")
                .content("[\"this is ordinary text, not multimodal content\"]")
                .build()));

        JsonNode payload = MAPPER.readTree(OpenAiApiUtil.serializeChatCompletionRequest(request));
        JsonNode content = payload.path("messages").get(0).path("content");

        assertTrue(content.isTextual());
        assertFalse(content.isArray());
        assertEquals("[\"this is ordinary text, not multimodal content\"]", content.asText());
    }

    @Test
    void keepsImageContentStructuredWhenAnotherMessageIsPlainText() throws Exception {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setMessages(Arrays.asList(
                ChatMessage.builder().role("user")
                        .content("[{\"type\":\"image_url\",\"image_url\":{\"url\":\"https://example.com/a.png\"}}]")
                        .build(),
                ChatMessage.builder().role("assistant").content("I will inspect the image.").build()));

        JsonNode payload = MAPPER.readTree(OpenAiApiUtil.serializeChatCompletionRequest(request));

        assertTrue(payload.path("messages").get(0).path("content").isArray());
        assertTrue(payload.path("messages").get(1).path("content").isTextual());
    }

    @Test
    void forwardsTheMultiContentCompletionRequestShape() throws Exception {
        String requestJson = "{"
                + "\"model\":\"Alibaba/qwen3.7-plus\","
                + "\"stream\":true,"
                + "\"max_completion_tokens\":32768,"
                + "\"store\":false,"
                + "\"stream_options\":{\"include_usage\":true},"
                + "\"messages\":[{\"role\":\"user\",\"content\":["
                + "{\"type\":\"text\",\"text\":\"识别这张图片\"},"
                + "{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:image/png;base64,AAAA\"}}"
                + "]}]}";
        ChatCompletionRequest request = MAPPER.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue(requestJson, ChatCompletionRequest.class);

        JsonNode payload = MAPPER.readTree(OpenAiApiUtil.serializeChatCompletionRequest(request));

        assertEquals("Alibaba/qwen3.7-plus", payload.path("model").asText());
        assertTrue(payload.path("stream").asBoolean());
        assertEquals(32768, payload.path("max_completion_tokens").asInt());
        assertFalse(payload.path("store").asBoolean());
        assertTrue(payload.path("stream_options").path("include_usage").asBoolean());
        assertTrue(payload.path("messages").get(0).path("content").isArray());
        assertEquals("image_url", payload.path("messages").get(0).path("content").get(1).path("type").asText());
    }

    @Test
    void removesStreamOptionsForNonStreamingRequests() throws Exception {
        String requestJson = "{"
                + "\"model\":\"Alibaba/qwen3.7-plus\","
                + "\"stream\":false,"
                + "\"stream_options\":{\"include_usage\":true},"
                + "\"messages\":[{\"role\":\"user\",\"content\":\"describe the image\"}]}";
        ChatCompletionRequest request = MAPPER.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue(requestJson, ChatCompletionRequest.class);

        JsonNode payload = MAPPER.readTree(OpenAiApiUtil.serializeChatCompletionRequest(request));

        assertFalse(payload.path("stream").asBoolean());
        assertFalse(payload.has("stream_options"));
    }

    @Test
    void omitsTemperatureForClaudeModelsThatRejectTheDeprecatedParameter() throws Exception {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("anthropic/claude-haiku-4-5-20251001");
        request.setTemperature(0.7D);

        JsonNode payload = MAPPER.readTree(OpenAiApiUtil.serializeChatCompletionRequest(request));

        assertFalse(payload.has("temperature"));
    }

    @Test
    void omitsOpenAiStrictFlagFromClaudeTools() throws Exception {
        Function function = new Function();
        function.setName("read_file");
        Tool tool = new Tool();
        tool.setType("function");
        tool.setFunction(function);
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("claude-opus-5");
        request.setTools(Collections.singletonList(tool));

        JsonNode payload = MAPPER.readTree(OpenAiApiUtil.serializeChatCompletionRequest(request));

        assertFalse(payload.path("tools").get(0).path("function").has("strict"));
    }

    @Test
    void keepsTemperatureForModelsThatSupportIt() throws Exception {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("Alibaba/qwen3.5-flash");
        request.setTemperature(0.7D);

        JsonNode payload = MAPPER.readTree(OpenAiApiUtil.serializeChatCompletionRequest(request));

        assertEquals(0.7D, payload.path("temperature").asDouble());
    }

    @Test
    void configuresAllStreamingTimeouts() {
        OkHttpClient client = OpenAiApiUtil.createStreamingClient(600, null);

        assertEquals(600_000, client.connectTimeoutMillis());
        assertEquals(60_000, client.readTimeoutMillis());
        assertEquals(600_000, client.writeTimeoutMillis());
    }

    @Test
    void preservesShorterProviderIdleTimeout() {
        OkHttpClient client = OpenAiApiUtil.createStreamingClient(20, null);

        assertEquals(20_000, client.readTimeoutMillis());
    }

    @Test
    void receivesSseChunksWithoutDrainingTheSuccessResponseBody() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.getRequestBody().close();
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            String payload = "data: {\"id\":\"vision-chunk\"}\n\n"
                    + "data: [DONE]\n\n";
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(payload.getBytes("UTF-8"));
                output.flush();
            }
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            ChatCompletionResult expected = new ChatCompletionResult();
            expected.setId("vision-chunk");
            ai.llm.pojo.LlmApiResponse response = OpenAiApiUtil.streamCompletions(
                    "test-key",
                    "http://127.0.0.1:" + port + "/v1/chat/completions",
                    2,
                    "{\"model\":\"Alibaba/qwen3.7-plus\",\"stream\":true}",
                    data -> data.contains("vision-chunk") ? expected : null,
                    ignored -> LLMErrorConstants.OTHER_ERROR,
                    new HashMap<>(),
                    null);

            assertEquals(200, response.getCode());
            assertNotNull(response.getStreamData());
            assertEquals("vision-chunk", response.getStreamData()
                    .timeout(2, TimeUnit.SECONDS)
                    .blockingFirst()
                    .getId());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void completesWhenOnlySseKeepAliveTrafficRemains() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.getRequestBody().close();
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write("data: {\"id\":\"first-model-event\"}\n\n".getBytes("UTF-8"));
                output.flush();
                for (int i = 0; i < 6; i++) {
                    Thread.sleep(250);
                    output.write(": keepalive\n\n".getBytes("UTF-8"));
                    output.flush();
                }
            } catch (IOException ignored) {
                // The client is expected to cancel after the model-event idle timeout.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            ChatCompletionResult expected = new ChatCompletionResult();
            expected.setId("first-model-event");
            ai.llm.pojo.LlmApiResponse response = OpenAiApiUtil.streamCompletions(
                    "test-key",
                    "http://127.0.0.1:" + port + "/v1/chat/completions",
                    10,
                    "{\"model\":\"test\",\"stream\":true}",
                    data -> data.contains("first-model-event") ? expected : null,
                    ignored -> LLMErrorConstants.OTHER_ERROR,
                    new HashMap<>(),
                    null,
                    1);

            List<ChatCompletionResult> chunks = response.getStreamData()
                    .timeout(4, TimeUnit.SECONDS)
                    .toList()
                    .blockingGet();

            assertEquals(1, chunks.size());
            assertEquals("first-model-event", chunks.get(0).getId());
            assertEquals(LLMErrorConstants.TIME_OUT, response.getCode());
        } finally {
            server.stop(0);
        }
    }
}
