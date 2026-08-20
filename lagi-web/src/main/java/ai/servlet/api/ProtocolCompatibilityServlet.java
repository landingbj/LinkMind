package ai.servlet.api;

import ai.common.utils.LRUCache;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.utils.ApikeyUtil;
import ai.utils.ModelNameUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Public protocol facade for clients that speak Anthropic Messages or OpenAI
 * Responses. The model invocation itself stays in {@link LlmApiServlet}.
 */
public class ProtocolCompatibilityServlet extends LlmApiServlet {
    private static final long serialVersionUID = 1L;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ProtocolCompatibilityCodec CODEC = new ProtocolCompatibilityCodec();
    private static final LRUCache<String, ResponseConversation> RESPONSE_HISTORY =
            new LRUCache<>(1000, 24, TimeUnit.HOURS);

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        ProtocolCompatibilityCodec.Protocol protocol = resolveProtocol(req);
        try {
            JsonNode body = MAPPER.readTree(requestToJson(req));
            if (body == null) {
                throw new ProtocolCompatibilityCodec.ProtocolException(400, "invalid_request_error", "request body is required");
            }
            String apiKey = extractClientApiKey(req, protocol);
            String owner = apiKey == null || apiKey.isEmpty()
                    ? "session:" + req.getSession().getId() : "key:" + apiKey;
            String previousResponseId = protocol == ProtocolCompatibilityCodec.Protocol.RESPONSES
                    ? optionalText(body.get("previous_response_id")) : null;
            ResponseConversation previous = loadPreviousConversation(previousResponseId, owner);
            List<ChatMessage> previousMessages = previous == null ? null : previous.messages;

            ChatCompletionRequest request = protocol == ProtocolCompatibilityCodec.Protocol.ANTHROPIC
                    ? CODEC.toAnthropicRequest(body)
                    : CODEC.toResponsesRequest(body, previousMessages);
            request.setModel(ModelNameUtil.normalizeOpenAiCompatibleModelName(request.getModel()));
            request.setApiKey(apiKey);
            request.setUserApiKey(apiKey);
            request.setKeyPoolSessionId(previous == null
                    ? protocol.name().toLowerCase() + ":" + owner + ":" + java.util.UUID.randomUUID()
                    : previous.keyPoolSessionId);
            if (previousResponseId != null) {
                request.setSessionId(previousResponseId);
            }

            boolean stream = Boolean.TRUE.equals(request.getStream());
            resp.setContentType(stream ? "text/event-stream;charset=utf-8" : "application/json;charset=utf-8");
            ProtocolResponseWrapper wrapper = new ProtocolResponseWrapper(resp, protocol, request,
                    CODEC, owner, stream);
            executeCompletion(req, wrapper, request);
        } catch (ProtocolCompatibilityCodec.ProtocolException e) {
            writeProtocolError(resp, protocol, e.getStatus(), e.getType(), e.getMessage());
        } catch (Exception e) {
            writeProtocolError(resp, protocol, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "api_error", "LinkMind could not process the request");
        }
    }

    private ProtocolCompatibilityCodec.Protocol resolveProtocol(HttpServletRequest req) {
        String path = req.getRequestURI();
        return path != null && (path.endsWith("/messages") || path.endsWith("/v1/messages"))
                ? ProtocolCompatibilityCodec.Protocol.ANTHROPIC
                : ProtocolCompatibilityCodec.Protocol.RESPONSES;
    }

    private String extractClientApiKey(HttpServletRequest req, ProtocolCompatibilityCodec.Protocol protocol) {
        String bearer = ApikeyUtil.extractBearerToken(req.getHeader("Authorization"));
        if (protocol == ProtocolCompatibilityCodec.Protocol.ANTHROPIC) {
            String anthropicKey = optionalText(req.getHeader("x-api-key"));
            return anthropicKey == null ? bearer : anthropicKey;
        }
        return bearer == null ? optionalText(req.getHeader("x-api-key")) : bearer;
    }

    private ResponseConversation loadPreviousConversation(String id, String owner)
            throws ProtocolCompatibilityCodec.ProtocolException {
        if (id == null) {
            return null;
        }
        ResponseConversation conversation = RESPONSE_HISTORY.get(id);
        if (conversation == null || !conversation.owner.equals(owner)) {
            throw new ProtocolCompatibilityCodec.ProtocolException(404, "invalid_request_error",
                    "previous_response_id was not found");
        }
        return conversation;
    }

    private static String optionalText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return optionalText(node.asText());
    }

    private static String optionalText(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private void writeProtocolError(HttpServletResponse resp, ProtocolCompatibilityCodec.Protocol protocol,
                                    int status, String type, String message) throws IOException {
        int httpStatus = status >= 400 && status < 600 ? status : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        resp.setStatus(httpStatus);
        resp.setContentType("application/json;charset=utf-8");
        ObjectNode error = CODEC.error(type, message);
        if (protocol == ProtocolCompatibilityCodec.Protocol.ANTHROPIC) {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("type", "error");
            root.set("error", error.get("error"));
            error = root;
        }
        PrintWriter writer = resp.getWriter();
        writer.print(error.toString());
        writer.flush();
    }

    private static final class ResponseConversation {
        private final String owner;
        private final String keyPoolSessionId;
        private final List<ChatMessage> messages;

        private ResponseConversation(String owner, String keyPoolSessionId, List<ChatMessage> messages) {
            this.owner = owner;
            this.keyPoolSessionId = keyPoolSessionId;
            this.messages = messages;
        }
    }

    private static final class ProtocolResponseWrapper extends HttpServletResponseWrapper {
        private final HttpServletResponse target;
        private final ProtocolCompatibilityCodec.Protocol protocol;
        private final ChatCompletionRequest request;
        private final ProtocolCompatibilityCodec codec;
        private final String owner;
        private final boolean stream;
        private final StringBuilder body = new StringBuilder();
        private final StringBuilder sseBuffer = new StringBuilder();
        private final ProtocolCompatibilityCodec.StreamTranslator translator;
        private final PrintWriter targetWriter;
        private final PrintWriter writer;
        private int status = HttpServletResponse.SC_OK;
        private boolean closed;
        private boolean historySaved;

        private ProtocolResponseWrapper(HttpServletResponse target,
                                        ProtocolCompatibilityCodec.Protocol protocol,
                                        ChatCompletionRequest request,
                                        ProtocolCompatibilityCodec codec,
                                        String owner,
                                        boolean stream) throws IOException {
            super(target);
            this.target = target;
            this.protocol = protocol;
            this.request = request;
            this.codec = codec;
            this.owner = owner;
            this.stream = stream;
            this.translator = stream ? codec.newStreamTranslator(protocol, request.getModel()) : null;
            this.targetWriter = target.getWriter();
            this.writer = new PrintWriter(new Writer() {
                @Override
                public void write(char[] cbuf, int off, int len) throws IOException {
                    acceptWrite(new String(cbuf, off, len));
                }

                @Override
                public void flush() throws IOException {
                    flushInternal(false);
                }

                @Override
                public void close() throws IOException {
                    flushInternal(true);
                }
            }, true);
        }

        @Override
        public PrintWriter getWriter() {
            return writer;
        }

        @Override
        public void setStatus(int status) {
            this.status = status;
        }

        @Override
        public void sendError(int status) {
            this.status = status;
        }

        @Override
        public void sendError(int status, String message) {
            this.status = status;
            body.append(message == null ? "" : message);
        }

        @Override
        public void setContentType(String type) {
            // The outer protocol determines the response media type.
        }

        @Override
        public void setHeader(String name, String value) {
            if (!"content-type".equalsIgnoreCase(name)) {
                target.setHeader(name, value);
            }
        }

        @Override
        public void addHeader(String name, String value) {
            if (!"content-type".equalsIgnoreCase(name)) {
                target.addHeader(name, value);
            }
        }

        private synchronized void acceptWrite(String value) throws IOException {
            if (closed || value == null || value.isEmpty()) {
                return;
            }
            if (!stream) {
                body.append(value);
                return;
            }
            sseBuffer.append(value);
            emitCompleteSseFrames();
        }

        private synchronized void flushInternal(boolean finish) throws IOException {
            if (closed) {
                return;
            }
            if (stream) {
                emitCompleteSseFrames();
                if (finish) {
                    if (sseBuffer.length() > 0) {
                        emitSseFrame(sseBuffer.toString());
                        sseBuffer.setLength(0);
                    }
                    writeEvents(translator.finish());
                    saveStreamingHistory();
                }
            } else if (finish) {
                emitNonStreamingResponse();
            }
            targetWriter.flush();
            if (finish) {
                closed = true;
                targetWriter.close();
            }
        }

        private void emitCompleteSseFrames() throws IOException {
            int separator;
            while ((separator = findSseSeparator(sseBuffer)) >= 0) {
                String frame = sseBuffer.substring(0, separator);
                int consumed = separator + (sseBuffer.charAt(separator) == '\r' ? 4 : 2);
                sseBuffer.delete(0, consumed);
                emitSseFrame(frame);
            }
        }

        private int findSseSeparator(StringBuilder value) {
            for (int i = 0; i + 1 < value.length(); i++) {
                if (value.charAt(i) == '\n' && value.charAt(i + 1) == '\n') {
                    return i;
                }
                if (i + 3 < value.length() && value.charAt(i) == '\r' && value.charAt(i + 1) == '\n'
                        && value.charAt(i + 2) == '\r' && value.charAt(i + 3) == '\n') {
                    return i;
                }
            }
            return -1;
        }

        private void emitSseFrame(String frame) throws IOException {
            StringBuilder data = new StringBuilder();
            String[] lines = frame.replace("\r\n", "\n").split("\n");
            for (String line : lines) {
                if (line.startsWith("data:")) {
                    if (data.length() > 0) {
                        data.append('\n');
                    }
                    data.append(line.substring(5).trim());
                }
            }
            if (data.length() > 0) {
                writeEvents(translator.accept(data.toString()));
            }
        }

        private void writeEvents(List<String> events) {
            for (String event : events) {
                targetWriter.print(event);
            }
        }

        private void emitNonStreamingResponse() throws IOException {
            int httpStatus = status >= 400 && status < 600 ? status : HttpServletResponse.SC_OK;
            target.setStatus(httpStatus);
            try {
                JsonNode node = MAPPER.readTree(body.toString());
                if (node == null || !node.has("choices")) {
                    writeErrorFromInternalBody(node);
                    return;
                }
                ChatCompletionResult result = MAPPER.treeToValue(node, ChatCompletionResult.class);
                if (protocol == ProtocolCompatibilityCodec.Protocol.ANTHROPIC) {
                    targetWriter.print(codec.toAnthropicResponse(result, request.getModel()).toString());
                } else {
                    String responseId = ProtocolCompatibilityCodec.newProtocolId("resp");
                    targetWriter.print(codec.toResponsesResponse(result, request.getModel(), responseId).toString());
                    saveResponsesHistory(responseId, codec.appendAssistantMessage(request.getMessages(), result));
                }
            } catch (Exception e) {
                writeProtocolError("api_error", "LinkMind returned an invalid model response");
            }
        }

        private void writeErrorFromInternalBody(JsonNode node) {
            String message = "LinkMind request failed";
            if (node != null && node.has("error")) {
                JsonNode error = node.get("error");
                message = error.isTextual() ? error.asText() : error.path("message").asText(message);
            }
            writeProtocolError("api_error", message);
        }

        private void writeProtocolError(String type, String message) {
            ObjectNode error = codec.error(type, message);
            if (protocol == ProtocolCompatibilityCodec.Protocol.ANTHROPIC) {
                ObjectNode root = MAPPER.createObjectNode();
                root.put("type", "error");
                root.set("error", error.get("error"));
                targetWriter.print(root.toString());
            } else {
                targetWriter.print(error.toString());
            }
        }

        private void saveStreamingHistory() {
            if (historySaved || protocol != ProtocolCompatibilityCodec.Protocol.RESPONSES || !translator.isFinished()) {
                return;
            }
            historySaved = true;
            List<ChatMessage> messages = codec.copyMessages(request.getMessages());
            messages.add(translator.getAssistantMessage());
            saveResponsesHistory(translator.getResponseId(), messages);
        }

        private void saveResponsesHistory(String responseId, List<ChatMessage> messages) {
            RESPONSE_HISTORY.put(responseId, new ResponseConversation(owner, request.getKeyPoolSessionId(), messages));
        }
    }
}
