package ai.llm.utils;

import ai.common.utils.ObservableList;
import ai.llm.pojo.LlmApiResponse;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.utils.MultiModalContentUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cn.hutool.core.text.StrFormatter;
import com.google.gson.Gson;
import io.reactivex.Observable;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class OpenAiApiUtil {

    /**
     * SSE providers may keep a TCP connection open after abandoning a response.
     * Bound the interval between events without limiting the overall generation time.
     */
    static final int MAX_STREAM_IDLE_TIMEOUT_SECONDS = 60;

    private static final Gson gson = new Gson();
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final ConnectionPool CONNECTION_POOL = new ConnectionPool(
            10, // 最大空闲连接数
            60, // 保持连接的时间
            TimeUnit.MINUTES
    );
    private static final Logger log = LoggerFactory.getLogger(OpenAiApiUtil.class);

    public static LlmApiResponse completions(String apikey, String apiUrl,
                                             Integer timeout,
                                             ChatCompletionRequest req,
                                             Function<String, ChatCompletionResult> convertResponseFunc,
                                             Function<Response, Integer> convertErrorFunc) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + apikey);
        return completions(apikey, apiUrl, timeout, req, convertResponseFunc, convertErrorFunc, headers);
    }

    public static LlmApiResponse streamCompletions(String apikey, String apiUrl,
                                                   Integer timeout,
                                                   ChatCompletionRequest req,
                                                   Function<String, ChatCompletionResult> convertResponseFunc,
                                                   Function<Response, Integer> convertErrorFunc) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + apikey);
        return streamCompletions(apikey, apiUrl, timeout, req, convertResponseFunc, convertErrorFunc, headers);
    }

    public static LlmApiResponse completions(String apikey, String apiUrl,
                                             Integer timeout,
                                             ChatCompletionRequest req,
                                             Function<String, ChatCompletionResult> convertResponseFunc,
                                             Function<Response, Integer> convertErrorFunc,
                                             Map<String, String> headers) {
        return completions(apikey, apiUrl, timeout, serializeChatCompletionRequest(req), convertResponseFunc,
                convertErrorFunc, headers, null);
    }

    public static LlmApiResponse completions(String apikey, String apiUrl,
                                             Integer timeout,
                                             String json,
                                             Function<String, ChatCompletionResult> convertResponseFunc,
                                             Function<Response, Integer> convertErrorFunc,
                                             Map<String, String> headers, Proxy proxy) {
        java.net.Authenticator.setDefault(new java.net.Authenticator() {
            @Override
            protected java.net.PasswordAuthentication getPasswordAuthentication() {
                return new java.net.PasswordAuthentication("socks5", "digimeta".toCharArray());
            }
        });
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .writeTimeout(timeout, TimeUnit.SECONDS)
                .connectionPool(CONNECTION_POOL)
                .proxy(proxy)
                .build();
        MediaType mediaType = MediaType.get("application/json");
        RequestBody body = RequestBody.create(json, mediaType);
        Request.Builder requestBuilder = new Request.Builder()
                .url(apiUrl)
                .post(body);
        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                requestBuilder.addHeader(header.getKey(), header.getValue());
            }
        }
        Request request = requestBuilder.build();
        LlmApiResponse result = LlmApiResponse.builder().build();
        try (Response response = client.newCall(request).execute();) {
            String bodyStr = response.body().string();
            if (response.code() != 200) {
                Integer code = convertErrorFunc.apply(response);
                result.setCode(code);
                result.setMsg(bodyStr);
                return result;
            }
            result.setCode(200);
            result.setData(convertResponseFunc.apply(bodyStr));
        } catch (IOException e) {
            result.setCode(LLMErrorConstants.TIME_OUT);
            result.setMsg(e.getMessage());
            log.error(e.getMessage());
        }
        return result;
    }

    public static LlmApiResponse streamCompletions(String apikey, String apiUrl,
                                                   Integer timeout,
                                                   ChatCompletionRequest req,
                                                   Function<String, ChatCompletionResult> convertResponseFunc,
                                                   Function<Response, Integer> convertErrorFunc, Map<String, String> headers) {
        return streamCompletions(apikey, apiUrl, timeout, serializeChatCompletionRequest(req), convertResponseFunc,
                convertErrorFunc, headers, null);
    }

    /**
     * The public API stores multimodal message content as a JSON string so the
     * internal request type can serve both text and image inputs. Gson does not
     * honour {@code ContentSerializer}, so convert only recognised multimodal
     * content back to an array before forwarding an OpenAI-compatible request.
     */
    static String serializeChatCompletionRequest(ChatCompletionRequest request) {
        String payload = gson.toJson(request);
        try {
            JsonNode root = JSON_MAPPER.readTree(payload);
            if (root instanceof ObjectNode && !Boolean.TRUE.equals(request.getStream())) {
                // OpenAI-compatible providers reject stream_options on regular
                // completions. Some clients send it unconditionally.
                ((ObjectNode) root).remove("stream_options");
            }
            if (root instanceof ObjectNode && doesNotSupportTemperature(request.getModel())) {
                // Claude's current OpenAI-compatible endpoint rejects this
                // deprecated parameter, even when it has the default value.
                ((ObjectNode) root).remove("temperature");
                removeOpenAiStrictFlagsFromClaudeTools((ObjectNode) root);
            }
            JsonNode messages = root.get("messages");
            if (messages == null || !messages.isArray()) {
                return JSON_MAPPER.writeValueAsString(root);
            }
            for (JsonNode message : messages) {
                if (!(message instanceof ObjectNode)) {
                    continue;
                }
                ObjectNode objectMessage = (ObjectNode) message;
                JsonNode content = objectMessage.get("content");
                if (content == null || !content.isTextual()) {
                    continue;
                }
                ArrayNode multimodal = MultiModalContentUtil.parseOpenAiContent(content.asText());
                if (multimodal != null) {
                    objectMessage.set("content", multimodal);
                }
            }
            return JSON_MAPPER.writeValueAsString(root);
        } catch (Exception ignored) {
            // Plain text may look like JSON. Keep the original Gson payload if
            // it is not a valid recognised multimodal message.
            return payload;
        }
    }

    private static boolean doesNotSupportTemperature(String model) {
        if (model == null) {
            return false;
        }
        String normalized = model.trim().toLowerCase(Locale.ROOT);
        int providerSeparator = normalized.lastIndexOf('/');
        String modelName = providerSeparator >= 0 ? normalized.substring(providerSeparator + 1) : normalized;
        return modelName.startsWith("claude");
    }

    /**
     * Claude's OpenAI-compatible endpoint accepts function tools but rejects
     * OpenAI's optional {@code strict} extension. The internal Function POJO
     * defaults it to true, so remove it only for Claude requests at the final
     * outbound serialization boundary.
     */
    private static void removeOpenAiStrictFlagsFromClaudeTools(ObjectNode root) {
        JsonNode tools = root.get("tools");
        if (tools == null || !tools.isArray()) {
            return;
        }
        for (JsonNode tool : tools) {
            if (!(tool instanceof ObjectNode)) {
                continue;
            }
            ObjectNode objectTool = (ObjectNode) tool;
            objectTool.remove("strict");
            JsonNode function = objectTool.get("function");
            if (function instanceof ObjectNode) {
                ((ObjectNode) function).remove("strict");
            }
        }
    }

    public static LlmApiResponse streamCompletions(String apikey, String apiUrl,
                                                   Integer timeout,
                                                   String json,
                                                   Function<String, ChatCompletionResult> convertResponseFunc,
                                                   Function<Response, Integer> convertErrorFunc, Map<String, String> headers) {
        return streamCompletions(apikey, apiUrl, timeout, json, convertResponseFunc, convertErrorFunc, headers, null);
    }
    public static LlmApiResponse streamCompletions(String apikey, String apiUrl,
                                                   Integer timeout,
                                                   String json,
                                                   Function<String, ChatCompletionResult> convertResponseFunc,
                                                   Function<Response, Integer> convertErrorFunc, Map<String, String> headers,
                                                   Proxy proxy) {
//        java.net.Authenticator.setDefault(new java.net.Authenticator() {
//            @Override
//            protected java.net.PasswordAuthentication getPasswordAuthentication() {
//                return new java.net.PasswordAuthentication("socks5", "digimeta".toCharArray());
//            }
//        });
        OkHttpClient client = createStreamingClient(timeout, proxy);
        MediaType mediaType = MediaType.get("application/json");
        RequestBody body = RequestBody.create(json, mediaType);
        Request.Builder requestBuilder = new Request.Builder()
                .url(apiUrl)
                .header("Accept", "text/event-stream")
                .header("timestamp", String.valueOf(System.currentTimeMillis()))
                .post(body);
        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                requestBuilder.addHeader(header.getKey(), header.getValue());
            }
        }
        Request request = requestBuilder.build();
        LlmApiResponse result = LlmApiResponse.builder().build();
        EventSource.Factory factory = EventSources.createFactory(client);
        ObservableList<ChatCompletionResult> res = new ObservableList<>();
        factory.newEventSource(request, new EventSourceListener() {
            @Override
            public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
//                log.info("ServerSentEventUtil streamCompletions onOpen: response = {}", response);
                int code = response.code();
                if (code != 200) {
                    result.setCode(convertErrorFunc.apply(response));
                    result.setMsg(readResponseBody(response));
                    closeConnection(eventSource);
                } else {
                    // EventSource owns this body. Reading it here drains the SSE
                    // stream before onEvent can receive any model chunks.
                    result.setCode(code);
                }
            }
            @Override
            public void onEvent(@NotNull EventSource eventSource, @Nullable String id, @Nullable String type, @NotNull String data) {
//                log.info("ServerSentEventUtil streamCompletions onEvent: data = {}" , data);
                ChatCompletionResult chatCompletionResult = convertResponseFunc.apply(data);
                if (chatCompletionResult != null) {
                    res.add(chatCompletionResult);
                }
            }
            @Override
            public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
                if (hasSocketTimeout(t)) {
                    result.setCode(LLMErrorConstants.TIME_OUT);
                    result.setMsg(errorMessage(t));
                } else if (response != null) {
                    result.setCode(convertErrorFunc.apply(response));
                    result.setMsg(readResponseBody(response));
                } else {
                    result.setCode(LLMErrorConstants.OTHER_ERROR);
                    result.setMsg(errorMessage(t));
                }
                if(t != null) {
                    log.error("model request failed error {}", t.getMessage());
                }
                closeConnection(eventSource);
            }

            @Override
            public void onClosed(@NotNull EventSource eventSource) {
                closeConnection(eventSource);
            }

            private void closeConnection(EventSource eventSource) {
                res.onComplete();
                eventSource.cancel();
                client.dispatcher().executorService().shutdown();
            }
        });
        Iterable<ChatCompletionResult> iterable = res.getObservable().blockingIterable();
        iterable.iterator().hasNext();
        result.setStreamData(Observable.fromIterable(iterable));
//        result.setStreamData(res.getObservable());
        return result;
    }

    static OkHttpClient createStreamingClient(Integer timeout, Proxy proxy) {
        int streamIdleTimeout = Math.min(timeout, MAX_STREAM_IDLE_TIMEOUT_SECONDS);
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.SECONDS)
                .readTimeout(streamIdleTimeout, TimeUnit.SECONDS)
                .writeTimeout(timeout, TimeUnit.SECONDS)
                .connectionPool(CONNECTION_POOL);
        if (proxy != null) {
            builder.proxy(proxy);
        }
        return builder.build();
    }

    private static boolean hasSocketTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String readResponseBody(Response response) {
        if (response.body() == null) {
            return "";
        }
        try {
            return response.body().string();
        } catch (IOException e) {
            return errorMessage(e);
        }
    }

    private static String errorMessage(Throwable throwable) {
        String message = throwable == null ? "model request failed" : throwable.getMessage();
        return StrFormatter.format("{\"error\":\"{}\"}", message == null ? "model request failed" : message);
    }


}
