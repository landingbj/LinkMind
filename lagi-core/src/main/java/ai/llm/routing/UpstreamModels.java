package ai.llm.routing;

import ai.common.ModelService;
import ai.llm.adapter.impl.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Startup discovery. A configuration reload reuses the process-local result for the same connection. */
public final class UpstreamModels {
    private static final Logger LOG = LoggerFactory.getLogger(UpstreamModels.class);
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .callTimeout(5, TimeUnit.SECONDS).followRedirects(false).build();
    private static final Map<String, List<String>> CACHE = new ConcurrentHashMap<>();

    private UpstreamModels() {}

    public static List<String> models(ModelService service, boolean discoverUpstream) {
        String key = BringYourOwnKeyRouter.configuredKey(service);
        if (key == null || (service instanceof LandingAdapter && ((LandingAdapter) service).isFinalServer())) {
            return Collections.emptyList();
        }
        try {
            HttpUrl url = modelsUrl(service);
            if (url == null) {
                return Collections.emptyList();
            }
            String cacheKey = service.getClass().getName() + " " + url + " " + ByteString.encodeUtf8(key).sha256().hex();
            return discoverUpstream ? CACHE.computeIfAbsent(cacheKey, ignored -> fetch(service, url, key))
                    : CACHE.getOrDefault(cacheKey, Collections.emptyList());
        } catch (RuntimeException e) {
            LOG.warn("Invalid model discovery endpoint for backend {}", service.getBackend());
            return Collections.emptyList();
        }
    }

    private static HttpUrl modelsUrl(ModelService service) {
        if (service instanceof GPTAzureAdapter) {
            HttpUrl base = HttpUrl.parse(service.getEndpoint() == null ? "" : service.getEndpoint());
            if (base == null) return null;
            String root = base.encodedPath().replaceFirst("/+$", "").replaceFirst("/openai(?:/v1)?$", "");
            return base.newBuilder().encodedPath(root + "/openai/v1/models").build();
        }
        String address = service.getApiAddress();
        if (address == null || address.trim().isEmpty()) {
            if (service instanceof QwenAdapter) address = "https://dashscope.aliyuncs.com/compatible-mode/v1";
            else if (service instanceof GPTAdapter) address = "https://api.openai.com/v1";
            else if (service instanceof GrokAdapter) address = "https://api.x.ai/v1";
            else if (service instanceof ClaudeAdapter) address = "https://api.anthropic.com/v1";
            else if (service instanceof GeminiAdapter) address = "https://generativelanguage.googleapis.com/v1beta";
            else if (service instanceof SparkAdapter) address = "https://spark-api-open.xf-yun.com/v1";
            else if (service instanceof SenseChatAdapter) address = "https://api.sensenova.cn/compatible-mode/v1";
            else return null;
        }
        HttpUrl base = HttpUrl.parse(address);
        if (base == null) return null;
        String path = base.encodedPath().replaceFirst("/+$", "")
                .replaceFirst("/(?:chat/completions|responses|messages|models)$", "");
        return base.newBuilder().encodedPath(path + "/models").build();
    }

    private static List<String> fetch(ModelService service, HttpUrl endpoint, String key) {
        boolean gemini = service instanceof GeminiAdapter;
        boolean anthropic = service instanceof ClaudeAdapter;
        Set<String> ids = new LinkedHashSet<>();
        Set<String> cursors = new HashSet<>();
        HttpUrl url = endpoint;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        try {
            do {
                if (System.nanoTime() >= deadline) throw new IOException("Model listing timed out");
                Request.Builder get = new Request.Builder().url(url);
                if (gemini) get.header("x-goog-api-key", key);
                else if (anthropic) get.header("x-api-key", key).header("anthropic-version", "2023-06-01");
                else if (service instanceof GPTAzureAdapter) get.header("api-key", key);
                else get.header("Authorization", "Bearer " + key);

                String cursor;
                try (Response response = HTTP.newCall(get.build()).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        LOG.warn("Model discovery returned HTTP {} for backend {}; using configured models",
                                response.code(), service.getBackend());
                        return Collections.emptyList();
                    }
                    JsonObject page = JsonParser.parseReader(response.body().charStream()).getAsJsonObject();
                    JsonArray items = page.getAsJsonArray(gemini ? "models" : "data");
                    if (items == null) throw new IOException("Missing model list");
                    for (JsonElement item : items) {
                        JsonObject entry = item.getAsJsonObject();
                        String id = string(entry, gemini ? "name" : "id");
                        if (id != null && !id.trim().isEmpty()) {
                            ids.add(gemini && id.startsWith("models/") ? id.substring(7) : id.trim());
                        }
                    }
                    cursor = gemini ? string(page, "nextPageToken") : null;
                    if (!gemini && page.has("has_more") && page.get("has_more").getAsBoolean()) {
                        cursor = string(page, "last_id");
                        if (cursor == null || cursor.isEmpty()) throw new IOException("Missing models cursor");
                    }
                }
                if (cursor == null || cursor.isEmpty()) break;
                if (!cursors.add(cursor)) throw new IOException("Repeated models cursor");
                String parameter = gemini ? "pageToken" : anthropic ? "after_id" : "after";
                url = endpoint.newBuilder().setQueryParameter(parameter, cursor).build();
            } while (true);
            return Collections.unmodifiableList(new ArrayList<>(ids));
        } catch (IOException | RuntimeException e) {
            LOG.warn("Model discovery failed for backend {} ({}); using configured models",
                    service.getBackend(), e.getClass().getSimpleName());
            return Collections.emptyList();
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : null;
    }
}
