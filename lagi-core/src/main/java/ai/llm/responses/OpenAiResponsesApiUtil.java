package ai.llm.responses;

import ai.llm.pojo.LlmApiResponse;
import ai.llm.utils.OpenAiApiUtil;
import com.google.gson.Gson;
import okhttp3.Response;

import java.net.Proxy;
import java.util.Map;
import java.util.function.Function;

public final class OpenAiResponsesApiUtil {
    private static final Gson GSON = new Gson();

    private OpenAiResponsesApiUtil() {
    }

    public static LlmApiResponse createResponse(String apiKey,
                                                String apiUrl,
                                                Integer timeout,
                                                ResponseCreateRequest request,
                                                Function<Response, Integer> convertErrorFunc,
                                                Map<String, String> headers) {
        return createResponse(apiKey, apiUrl, timeout, (Object) request, convertErrorFunc, headers);
    }

    public static LlmApiResponse createResponse(String apiKey,
                                                String apiUrl,
                                                Integer timeout,
                                                Object request,
                                                Function<Response, Integer> convertErrorFunc,
                                                Map<String, String> headers) {
        return OpenAiApiUtil.completions(apiKey, apiUrl, timeout, GSON.toJson(request),
                ResponsesChatCompletionConverter::convertResponse,
                convertErrorFunc,
                headers,
                null);
    }

    public static LlmApiResponse createResponse(String apiKey,
                                                String apiUrl,
                                                Integer timeout,
                                                String json,
                                                Function<Response, Integer> convertErrorFunc,
                                                Map<String, String> headers,
                                                Proxy proxy) {
        return OpenAiApiUtil.completions(apiKey, apiUrl, timeout, json,
                ResponsesChatCompletionConverter::convertResponse,
                convertErrorFunc,
                headers,
                proxy);
    }

    public static LlmApiResponse streamResponse(String apiKey,
                                                String apiUrl,
                                                Integer timeout,
                                                ResponseCreateRequest request,
                                                Function<Response, Integer> convertErrorFunc,
                                                Map<String, String> headers) {
        return streamResponse(apiKey, apiUrl, timeout, (Object) request, convertErrorFunc, headers);
    }

    public static LlmApiResponse streamResponse(String apiKey,
                                                String apiUrl,
                                                Integer timeout,
                                                Object request,
                                                Function<Response, Integer> convertErrorFunc,
                                                Map<String, String> headers) {
        return OpenAiApiUtil.streamCompletions(apiKey, apiUrl, timeout, GSON.toJson(request),
                ResponsesChatCompletionConverter::convertStreamEvent,
                convertErrorFunc,
                headers,
                null);
    }

    public static LlmApiResponse streamResponse(String apiKey,
                                                String apiUrl,
                                                Integer timeout,
                                                String json,
                                                Function<Response, Integer> convertErrorFunc,
                                                Map<String, String> headers,
                                                Proxy proxy) {
        return OpenAiApiUtil.streamCompletions(apiKey, apiUrl, timeout, json,
                ResponsesChatCompletionConverter::convertStreamEvent,
                convertErrorFunc,
                headers,
                proxy);
    }
}
