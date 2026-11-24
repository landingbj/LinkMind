package ai.openai.utils;

import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * Opt-in, metadata-only tracing for multimodal calls.
 *
 * <p>Enable with {@code -Dlinkmind.trace.multimodal=true}. The trace never
 * emits API keys, user prompt text, image URLs, or Base64 image data.</p>
 */
public final class MultimodalTraceUtil {
    private static final Logger log = LoggerFactory.getLogger(MultimodalTraceUtil.class);
    private static final String TRACE_PROPERTY = "linkmind.trace.multimodal";
    private static final ConcurrentHashMap<String, Long> START_TIMES = new ConcurrentHashMap<>();
    private static final Set<String> ONCE_STAGES = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<String> CURRENT_TRACE_ID = new ThreadLocal<>();

    private MultimodalTraceUtil() {
    }

    public static void logRequest(String stage, ChatCompletionRequest request, String endpoint) {
        if (!enabled() || request == null) {
            return;
        }
        String traceId = ensureTraceId(request);
        if ("api-received".equals(stage)) {
            START_TIMES.put(traceId, System.nanoTime());
            CURRENT_TRACE_ID.set(traceId);
            ONCE_STAGES.remove(traceId + ":upstream-first-sse");
            ONCE_STAGES.remove(traceId + ":upstream-first-meaningful");
            ONCE_STAGES.remove(traceId + ":client-first-sse");
        }
        TraceSummary summary = summarize(request);
        log.info("LM_MULTIMODAL_TRACE stage={} traceId={} elapsedMs={} model={} endpoint={} stream={} messages={} "
                        + "streamOptions={} textParts={} imageParts={} images={}",
                stage,
                traceId,
                elapsedMillis(traceId),
                safe(request.getModel()),
                endpoint == null ? "-" : endpoint,
                Boolean.TRUE.equals(request.getStream()),
                summary.messageCount,
                summarizeStreamOptions(request),
                summary.textParts,
                summary.imageParts,
                summary.images.isEmpty() ? "-" : join(summary.images));
    }

    public static void logResult(String stage, ChatCompletionRequest request, String endpoint, Integer statusCode) {
        if (!enabled() || request == null) {
            return;
        }
        log.info("LM_MULTIMODAL_TRACE stage={} traceId={} elapsedMs={} model={} endpoint={} status={}",
                stage,
                ensureTraceId(request),
                elapsedMillis(ensureTraceId(request)),
                safe(request.getModel()),
                endpoint == null ? "-" : endpoint,
                statusCode == null ? "-" : statusCode);
    }

    /** Logs a timing marker once per request without exposing request or response content. */
    public static void logFirstEvent(String stage, ChatCompletionRequest request) {
        if (!enabled() || request == null) {
            return;
        }
        String traceId = ensureTraceId(request);
        if (ONCE_STAGES.add(traceId + ":" + stage)) {
            log.info("LM_MULTIMODAL_TRACE stage={} traceId={} elapsedMs={} model={}",
                    stage, traceId, elapsedMillis(traceId), safe(request.getModel()));
        }
    }

    public static void logEvent(String stage, ChatCompletionRequest request) {
        if (!enabled() || request == null) {
            return;
        }
        String traceId = ensureTraceId(request);
        log.info("LM_MULTIMODAL_TRACE stage={} traceId={} elapsedMs={} model={}",
                stage, traceId, elapsedMillis(traceId), safe(request.getModel()));
    }

    /** Logs a servlet-thread event for helpers that do not receive the request object. */
    public static void logCurrentEvent(String stage, String endpoint, String outcome) {
        if (!enabled()) {
            return;
        }
        String traceId = CURRENT_TRACE_ID.get();
        if (traceId == null) {
            return;
        }
        log.info("LM_MULTIMODAL_TRACE stage={} traceId={} elapsedMs={} endpoint={} outcome={}",
                stage, traceId, elapsedMillis(traceId), endpoint == null ? "-" : endpoint,
                outcome == null ? "-" : outcome);
    }

    public static void clearCurrentTrace() {
        CURRENT_TRACE_ID.remove();
    }

    private static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty(TRACE_PROPERTY, "false"));
    }

    private static String ensureTraceId(ChatCompletionRequest request) {
        if (request.getTraceId() == null || request.getTraceId().trim().isEmpty()) {
            request.setTraceId(UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        }
        return request.getTraceId();
    }

    private static long elapsedMillis(String traceId) {
        Long startedAt = START_TIMES.get(traceId);
        return startedAt == null ? -1L : (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private static TraceSummary summarize(ChatCompletionRequest request) {
        TraceSummary summary = new TraceSummary();
        if (request.getMessages() == null) {
            return summary;
        }
        summary.messageCount = request.getMessages().size();
        for (ChatMessage message : request.getMessages()) {
            if (message == null) {
                continue;
            }
            ArrayNode parts = MultiModalContentUtil.parseOpenAiContent(message.getContent());
            if (parts == null) {
                continue;
            }
            for (JsonNode part : parts) {
                String type = part.path("type").asText();
                if ("text".equals(type)) {
                    summary.textParts++;
                } else if ("image_url".equals(type)) {
                    summary.imageParts++;
                    summary.images.add(describeImage(part.path("image_url").path("url").asText()));
                }
            }
        }
        return summary;
    }

    private static String describeImage(String imageUrl) {
        if (imageUrl == null) {
            return "invalid";
        }
        byte[] imageBytes = null;
        if (imageUrl.startsWith("data:")) {
            int comma = imageUrl.indexOf(',');
            if (comma >= 0 && comma < imageUrl.length() - 1) {
                try {
                    imageBytes = Base64.getDecoder().decode(imageUrl.substring(comma + 1));
                } catch (IllegalArgumentException ignored) {
                    // Keep a digest of the data URL if its Base64 cannot be decoded.
                }
            }
        }
        byte[] digestSource = imageBytes == null ? imageUrl.getBytes(StandardCharsets.UTF_8) : imageBytes;
        return "data=" + imageUrl.startsWith("data:")
                + ";urlChars=" + imageUrl.length()
                + ";bytes=" + digestSource.length
                + ";sha256=" + sha256(digestSource);
    }

    private static String sha256(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder output = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                output.append(String.format("%02x", item));
            }
            return output.toString();
        } catch (Exception ignored) {
            return "unavailable";
        }
    }

    private static String join(List<String> values) {
        StringBuilder output = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                output.append('|');
            }
            output.append(values.get(index));
        }
        return output.toString();
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }

    private static String summarizeStreamOptions(ChatCompletionRequest request) {
        if (request.getStream_options() == null || request.getStream_options().isEmpty()) {
            return "-";
        }
        List<String> names = new ArrayList<>(request.getStream_options().keySet());
        names.sort(String::compareTo);
        return join(names);
    }

    private static final class TraceSummary {
        private int messageCount;
        private int textParts;
        private int imageParts;
        private final List<String> images = new ArrayList<>();
    }
}
