package ai.servlet.api;

import ai.openai.pojo.ChatCompletionChoice;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.openai.pojo.Function;
import ai.openai.pojo.Parameters;
import ai.openai.pojo.Tool;
import ai.openai.pojo.ToolCall;
import ai.openai.pojo.ToolCallFunction;
import ai.openai.pojo.Usage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Translates the public Anthropic Messages and OpenAI Responses schemas to the
 * application's canonical Chat Completions request/response model.
 */
public final class ProtocolCompatibilityCodec {
    public enum Protocol {
        ANTHROPIC,
        RESPONSES
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ChatCompletionRequest toAnthropicRequest(JsonNode root) throws ProtocolException {
        requireObject(root);
        String model = requiredText(root, "model");
        JsonNode maxTokens = root.get("max_tokens");
        if (maxTokens == null || !maxTokens.canConvertToInt()) {
            throw new ProtocolException(400, "invalid_request_error", "max_tokens is required");
        }

        List<ChatMessage> messages = new ArrayList<>();
        String system = contentToText(root.get("system"));
        if (!system.isEmpty()) {
            messages.add(message("system", system));
        }
        JsonNode sourceMessages = root.get("messages");
        if (sourceMessages == null || !sourceMessages.isArray() || sourceMessages.size() == 0) {
            throw new ProtocolException(400, "invalid_request_error", "messages must be a non-empty array");
        }
        for (JsonNode source : sourceMessages) {
            appendAnthropicMessage(messages, source);
        }

        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(model);
        request.setMessages(messages);
        request.setMax_tokens(maxTokens.asInt());
        request.setStream(root.path("stream").asBoolean(false));
        if (root.has("temperature")) {
            request.setTemperature(root.path("temperature").asDouble(1D));
        }
        if (root.has("top_p")) {
            request.setTop_p(root.path("top_p").asDouble());
        }
        request.setTools(toAnthropicTools(root.get("tools")));
        request.setTool_choice(toAnthropicToolChoice(root.get("tool_choice")));
        return request;
    }

    public ChatCompletionRequest toResponsesRequest(JsonNode root, List<ChatMessage> previousMessages)
            throws ProtocolException {
        requireObject(root);
        String model = requiredText(root, "model");
        List<ChatMessage> messages = copyMessages(previousMessages);
        String instructions = contentToText(root.get("instructions"));
        if (!instructions.isEmpty()) {
            messages.add(message("system", instructions));
        }
        JsonNode input = root.get("input");
        if (input == null || input.isNull()) {
            throw new ProtocolException(400, "invalid_request_error", "input is required");
        }
        appendResponsesInput(messages, input);
        if (messages.isEmpty()) {
            throw new ProtocolException(400, "invalid_request_error", "input must contain at least one message");
        }

        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(model);
        request.setMessages(messages);
        request.setStream(root.path("stream").asBoolean(false));
        if (root.has("max_output_tokens")) {
            request.setMax_tokens(root.path("max_output_tokens").asInt());
        }
        if (root.has("temperature")) {
            request.setTemperature(root.path("temperature").asDouble(1D));
        }
        if (root.has("top_p")) {
            request.setTop_p(root.path("top_p").asDouble());
        }
        request.setTools(toResponsesTools(root.get("tools")));
        request.setTool_choice(toResponsesToolChoice(root.get("tool_choice")));
        if (root.has("parallel_tool_calls")) {
            request.setParallel_tool_calls(root.path("parallel_tool_calls").asBoolean());
        }
        return request;
    }

    public ObjectNode toAnthropicResponse(ChatCompletionResult result, String requestedModel) {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("id", newProtocolId("msg"));
        response.put("type", "message");
        response.put("role", "assistant");
        response.put("model", modelOf(result, requestedModel));
        response.set("content", toAnthropicContent(result));
        response.put("stop_reason", toAnthropicStopReason(finishReason(result)));
        response.putNull("stop_sequence");
        response.set("usage", toAnthropicUsage(result == null ? null : result.getUsage()));
        return response;
    }

    public ObjectNode toResponsesResponse(ChatCompletionResult result, String requestedModel, String responseId) {
        ObjectNode response = createResponseEnvelope(result, requestedModel, responseId, "completed");
        response.set("output", toResponsesOutput(result));
        return response;
    }

    public ObjectNode error(String type, String message) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode error = root.putObject("error");
        error.put("type", type == null ? "api_error" : type);
        error.put("message", message == null ? "LinkMind request failed" : message);
        return root;
    }

    public List<ChatMessage> appendAssistantMessage(List<ChatMessage> requestMessages, ChatCompletionResult result) {
        List<ChatMessage> messages = copyMessages(requestMessages);
        ChatMessage assistant = assistantMessage(result);
        if (assistant != null) {
            messages.add(assistant);
        }
        return messages;
    }

    public List<ChatMessage> copyMessages(List<ChatMessage> source) {
        List<ChatMessage> copy = new ArrayList<>();
        if (source == null) {
            return copy;
        }
        for (ChatMessage original : source) {
            if (original == null) {
                continue;
            }
            ChatMessage message = new ChatMessage();
            message.setRole(original.getRole());
            message.setContent(original.getContent());
            message.setReasoning_content(original.getReasoning_content());
            message.setTool_call_id(original.getTool_call_id());
            if (original.getTool_calls() != null) {
                List<ToolCall> calls = new ArrayList<>();
                for (ToolCall originalCall : original.getTool_calls()) {
                    if (originalCall == null) {
                        continue;
                    }
                    ToolCall call = new ToolCall();
                    call.setIndex(originalCall.getIndex());
                    call.setId(originalCall.getId());
                    call.setType(originalCall.getType());
                    if (originalCall.getFunction() != null) {
                        ToolCallFunction function = new ToolCallFunction();
                        function.setName(originalCall.getFunction().getName());
                        function.setArguments(originalCall.getFunction().getArguments());
                        call.setFunction(function);
                    }
                    calls.add(call);
                }
                message.setTool_calls(calls);
            }
            copy.add(message);
        }
        return copy;
    }

    public StreamTranslator newStreamTranslator(Protocol protocol, String requestedModel) {
        return new StreamTranslator(protocol, requestedModel);
    }

    public static String newProtocolId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private void appendAnthropicMessage(List<ChatMessage> target, JsonNode source) throws ProtocolException {
        if (!source.isObject()) {
            throw new ProtocolException(400, "invalid_request_error", "each message must be an object");
        }
        String role = requiredText(source, "role");
        if (!"user".equals(role) && !"assistant".equals(role)) {
            throw new ProtocolException(400, "invalid_request_error", "unsupported message role: " + role);
        }
        JsonNode content = source.get("content");
        if (content == null || content.isNull()) {
            throw new ProtocolException(400, "invalid_request_error", "message content is required");
        }
        if (content.isTextual()) {
            target.add(message(role, content.asText()));
            return;
        }
        if (!content.isArray()) {
            throw new ProtocolException(400, "invalid_request_error", "message content must be text or an array");
        }

        List<JsonNode> ordinaryBlocks = new ArrayList<>();
        List<ToolCall> toolCalls = new ArrayList<>();
        List<ChatMessage> toolResults = new ArrayList<>();
        for (JsonNode block : content) {
            String type = block.path("type").asText();
            if ("tool_use".equals(type)) {
                ToolCall call = new ToolCall();
                call.setId(requiredText(block, "id"));
                call.setType("function");
                ToolCallFunction function = new ToolCallFunction();
                function.setName(requiredText(block, "name"));
                function.setArguments(jsonString(block.get("input"), "{}"));
                call.setFunction(function);
                toolCalls.add(call);
            } else if ("tool_result".equals(type)) {
                ChatMessage toolResult = message("tool", contentToText(block.get("content")));
                toolResult.setTool_call_id(requiredText(block, "tool_use_id"));
                toolResults.add(toolResult);
            } else {
                ordinaryBlocks.add(block);
            }
        }
        String ordinaryContent = blocksToInternalContent(ordinaryBlocks);
        // Anthropic tool_result blocks complete the preceding assistant call;
        // keep them before a same-turn user text block in the internal history.
        target.addAll(toolResults);
        if (!ordinaryContent.isEmpty() || !toolCalls.isEmpty()) {
            ChatMessage message = message(role, ordinaryContent);
            if (!toolCalls.isEmpty()) {
                message.setTool_calls(toolCalls);
            }
            target.add(message);
        }
    }

    private void appendResponsesInput(List<ChatMessage> target, JsonNode input) throws ProtocolException {
        if (input.isTextual()) {
            target.add(message("user", input.asText()));
            return;
        }
        if (!input.isArray()) {
            throw new ProtocolException(400, "invalid_request_error", "input must be text or an array");
        }
        for (JsonNode item : input) {
            if (item.isTextual()) {
                target.add(message("user", item.asText()));
                continue;
            }
            if (!item.isObject()) {
                throw new ProtocolException(400, "invalid_request_error", "input items must be strings or objects");
            }
            String type = item.path("type").asText("message");
            if ("message".equals(type)) {
                String role = item.path("role").asText("user");
                if (!"user".equals(role) && !"assistant".equals(role) && !"developer".equals(role) && !"system".equals(role)) {
                    throw new ProtocolException(400, "invalid_request_error", "unsupported message role: " + role);
                }
                if ("developer".equals(role)) {
                    role = "system";
                }
                target.add(message(role, contentToInternalContent(item.get("content"))));
            } else if ("function_call".equals(type)) {
                ToolCall call = new ToolCall();
                call.setId(firstText(item, "call_id", "id"));
                call.setType("function");
                ToolCallFunction function = new ToolCallFunction();
                function.setName(requiredText(item, "name"));
                function.setArguments(item.path("arguments").asText("{}"));
                call.setFunction(function);
                ChatMessage message = message("assistant", "");
                message.setTool_calls(Collections.singletonList(call));
                target.add(message);
            } else if ("function_call_output".equals(type)) {
                ChatMessage toolResult = message("tool", contentToText(item.get("output")));
                toolResult.setTool_call_id(requiredText(item, "call_id"));
                target.add(toolResult);
            } else if (!"item_reference".equals(type)) {
                throw new ProtocolException(400, "invalid_request_error", "unsupported input item type: " + type);
            }
        }
    }

    private List<Tool> toAnthropicTools(JsonNode node) throws ProtocolException {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isArray()) {
            throw new ProtocolException(400, "invalid_request_error", "tools must be an array");
        }
        List<Tool> tools = new ArrayList<>();
        for (JsonNode source : node) {
            Tool tool = new Tool();
            tool.setType("function");
            Function function = new Function();
            function.setName(requiredText(source, "name"));
            function.setDescription(text(source.get("description")));
            function.setParameters(toParameters(source.get("input_schema")));
            tool.setFunction(function);
            tools.add(tool);
        }
        return tools;
    }

    private List<Tool> toResponsesTools(JsonNode node) throws ProtocolException {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isArray()) {
            throw new ProtocolException(400, "invalid_request_error", "tools must be an array");
        }
        List<Tool> tools = new ArrayList<>();
        for (JsonNode source : node) {
            if (!"function".equals(source.path("type").asText("function"))) {
                continue;
            }
            JsonNode functionSource = source.has("function") ? source.get("function") : source;
            Tool tool = new Tool();
            tool.setType("function");
            Function function = new Function();
            function.setName(requiredText(functionSource, "name"));
            function.setDescription(text(functionSource.get("description")));
            function.setStrict(functionSource.has("strict") ? functionSource.path("strict").asBoolean() : null);
            function.setParameters(toParameters(functionSource.get("parameters")));
            tool.setFunction(function);
            tools.add(tool);
        }
        return tools;
    }

    private Parameters toParameters(JsonNode node) throws ProtocolException {
        if (node == null || node.isNull()) {
            Parameters parameters = new Parameters();
            parameters.setType("object");
            return parameters;
        }
        if (!node.isObject()) {
            throw new ProtocolException(400, "invalid_request_error", "tool schema must be an object");
        }
        try {
            return MAPPER.treeToValue(node, Parameters.class);
        } catch (Exception e) {
            throw new ProtocolException(400, "invalid_request_error", "invalid tool schema");
        }
    }

    private String toAnthropicToolChoice(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String type = node.path("type").asText("auto");
        if ("any".equals(type)) {
            return "required";
        }
        if ("tool".equals(type)) {
            return "required";
        }
        return type;
    }

    private String toResponsesToolChoice(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isObject()) {
            String type = node.path("type").asText();
            if ("function".equals(type)) {
                return "required";
            }
            if (!type.isEmpty()) {
                return type;
            }
        }
        return null;
    }

    private ArrayNode toAnthropicContent(ChatCompletionResult result) {
        ArrayNode content = MAPPER.createArrayNode();
        ChatMessage message = assistantMessage(result);
        if (message == null) {
            return content;
        }
        String text = message.getContent();
        if (text != null && !text.isEmpty()) {
            ObjectNode block = content.addObject();
            block.put("type", "text");
            block.put("text", text);
        }
        if (message.getTool_calls() != null) {
            for (ToolCall call : message.getTool_calls()) {
                if (call == null || call.getFunction() == null) {
                    continue;
                }
                ObjectNode block = content.addObject();
                block.put("type", "tool_use");
                block.put("id", call.getId() == null ? newProtocolId("toolu") : call.getId());
                block.put("name", call.getFunction().getName());
                block.set("input", objectOrEmpty(call.getFunction().getArguments()));
            }
        }
        return content;
    }

    private ArrayNode toResponsesOutput(ChatCompletionResult result) {
        ArrayNode output = MAPPER.createArrayNode();
        ChatMessage message = assistantMessage(result);
        if (message == null) {
            return output;
        }
        String text = message.getContent();
        if (text != null && !text.isEmpty()) {
            ObjectNode messageItem = output.addObject();
            messageItem.put("id", newProtocolId("msg"));
            messageItem.put("type", "message");
            messageItem.put("status", "completed");
            messageItem.put("role", "assistant");
            ArrayNode content = messageItem.putArray("content");
            ObjectNode textBlock = content.addObject();
            textBlock.put("type", "output_text");
            textBlock.put("text", text);
            textBlock.putArray("annotations");
        }
        if (message.getTool_calls() != null) {
            for (ToolCall call : message.getTool_calls()) {
                if (call == null || call.getFunction() == null) {
                    continue;
                }
                ObjectNode function = output.addObject();
                function.put("id", newProtocolId("fc"));
                function.put("type", "function_call");
                function.put("status", "completed");
                function.put("call_id", call.getId() == null ? newProtocolId("call") : call.getId());
                function.put("name", call.getFunction().getName());
                function.put("arguments", call.getFunction().getArguments() == null ? "{}" : call.getFunction().getArguments());
            }
        }
        return output;
    }

    private ObjectNode createResponseEnvelope(ChatCompletionResult result, String requestedModel,
                                              String responseId, String status) {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("id", responseId == null ? newProtocolId("resp") : responseId);
        response.put("object", "response");
        response.put("created_at", System.currentTimeMillis() / 1000L);
        response.put("status", status);
        response.put("model", modelOf(result, requestedModel));
        response.putNull("error");
        response.putNull("incomplete_details");
        response.putNull("instructions");
        response.putNull("previous_response_id");
        response.putNull("reasoning");
        response.put("parallel_tool_calls", true);
        response.put("store", true);
        response.set("usage", toResponsesUsage(result == null ? null : result.getUsage()));
        return response;
    }

    private ObjectNode toAnthropicUsage(Usage usage) {
        ObjectNode result = MAPPER.createObjectNode();
        result.put("input_tokens", usage == null ? 0L : usage.getPrompt_tokens());
        result.put("output_tokens", usage == null ? 0L : usage.getCompletion_tokens());
        return result;
    }

    private ObjectNode toResponsesUsage(Usage usage) {
        ObjectNode result = MAPPER.createObjectNode();
        long input = usage == null ? 0L : usage.getPrompt_tokens();
        long output = usage == null ? 0L : usage.getCompletion_tokens();
        result.put("input_tokens", input);
        result.put("output_tokens", output);
        result.put("total_tokens", usage == null ? input + output : usage.getTotal_tokens());
        ObjectNode inputDetails = result.putObject("input_tokens_details");
        inputDetails.put("cached_tokens", usage == null || usage.getPrompt_tokens_details() == null
                ? 0L : usage.getPrompt_tokens_details().getCached_tokens());
        ObjectNode outputDetails = result.putObject("output_tokens_details");
        outputDetails.put("reasoning_tokens", usage == null || usage.getCompletion_tokens_details() == null
                ? 0L : usage.getCompletion_tokens_details().getReasoning_tokens());
        return result;
    }

    private ChatMessage assistantMessage(ChatCompletionResult result) {
        if (result == null || result.getChoices() == null || result.getChoices().isEmpty()) {
            return null;
        }
        ChatCompletionChoice choice = result.getChoices().get(0);
        return choice.getMessage() != null ? choice.getMessage() : choice.getDelta();
    }

    private String finishReason(ChatCompletionResult result) {
        if (result == null || result.getChoices() == null || result.getChoices().isEmpty()) {
            return "stop";
        }
        String finish = result.getChoices().get(0).getFinish_reason();
        return finish == null ? "stop" : finish;
    }

    private String toAnthropicStopReason(String finishReason) {
        if ("tool_calls".equals(finishReason) || "function_call".equals(finishReason)) {
            return "tool_use";
        }
        if ("length".equals(finishReason)) {
            return "max_tokens";
        }
        return "end_turn";
    }

    private String modelOf(ChatCompletionResult result, String fallback) {
        if (result != null && result.getModel() != null && !result.getModel().trim().isEmpty()) {
            return result.getModel();
        }
        return fallback == null ? "linkmind" : fallback;
    }

    private String contentToInternalContent(JsonNode content) throws ProtocolException {
        if (content == null || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText();
        }
        if (!content.isArray()) {
            throw new ProtocolException(400, "invalid_request_error", "message content must be text or an array");
        }
        List<JsonNode> blocks = new ArrayList<>();
        for (JsonNode block : content) {
            blocks.add(block);
        }
        return blocksToInternalContent(blocks);
    }

    private String blocksToInternalContent(List<JsonNode> blocks) throws ProtocolException {
        boolean hasImage = false;
        StringBuilder plainText = new StringBuilder();
        ArrayNode multimodal = MAPPER.createArrayNode();
        for (JsonNode block : blocks) {
            if (block == null || block.isNull()) {
                continue;
            }
            if (block.isTextual()) {
                appendTextPart(plainText, block.asText());
                continue;
            }
            String type = block.path("type").asText("text");
            if ("text".equals(type) || "input_text".equals(type) || "output_text".equals(type)) {
                appendTextPart(plainText, text(block.get("text")));
            } else if ("image".equals(type) || "input_image".equals(type)) {
                hasImage = true;
                ObjectNode image = multimodal.addObject();
                image.put("type", "image_url");
                ObjectNode imageUrl = image.putObject("image_url");
                String url = imageUrl(block);
                if (url == null || url.isEmpty()) {
                    throw new ProtocolException(400, "invalid_request_error", "image input requires a URL or base64 source");
                }
                imageUrl.put("url", url);
                if (block.has("detail")) {
                    imageUrl.put("detail", block.path("detail").asText());
                }
            }
        }
        if (!hasImage) {
            return plainText.toString();
        }
        if (plainText.length() > 0) {
            ArrayNode withText = MAPPER.createArrayNode();
            ObjectNode text = withText.addObject();
            text.put("type", "text");
            text.put("text", plainText.toString());
            withText.addAll(multimodal);
            return withText.toString();
        }
        return multimodal.toString();
    }

    private String imageUrl(JsonNode block) {
        JsonNode directUrl = block.get("image_url");
        if (directUrl != null && directUrl.isTextual()) {
            return directUrl.asText();
        }
        if (directUrl != null && directUrl.isObject() && directUrl.has("url")) {
            return directUrl.path("url").asText();
        }
        JsonNode source = block.get("source");
        if (source != null && "base64".equals(source.path("type").asText())) {
            String mediaType = source.path("media_type").asText();
            String data = source.path("data").asText();
            if (!mediaType.isEmpty() && !data.isEmpty()) {
                return "data:" + mediaType + ";base64," + data;
            }
        }
        if (source != null && "url".equals(source.path("type").asText())) {
            return source.path("url").asText();
        }
        return null;
    }

    private String contentToText(JsonNode content) {
        if (content == null || content.isNull()) {
            return "";
        }
        if (content.isTextual() || content.isNumber() || content.isBoolean()) {
            return content.asText();
        }
        if (!content.isArray()) {
            return content.path("text").asText("");
        }
        StringBuilder value = new StringBuilder();
        for (JsonNode part : content) {
            if (part.isTextual()) {
                appendTextPart(value, part.asText());
            } else {
                String type = part.path("type").asText();
                if ("text".equals(type) || "input_text".equals(type) || "output_text".equals(type)) {
                    appendTextPart(value, part.path("text").asText());
                }
            }
        }
        return value.toString();
    }

    private void appendTextPart(StringBuilder target, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        if (target.length() > 0) {
            target.append('\n');
        }
        target.append(value);
    }

    private ObjectNode objectOrEmpty(String value) {
        try {
            JsonNode parsed = MAPPER.readTree(value == null ? "{}" : value);
            if (parsed != null && parsed.isObject()) {
                return (ObjectNode) parsed;
            }
        } catch (IOException ignored) {
        }
        return MAPPER.createObjectNode();
    }

    private String jsonString(JsonNode node, String fallback) {
        return node == null || node.isNull() ? fallback : node.toString();
    }

    private ChatMessage message(String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setRole(role);
        message.setContent(content == null ? "" : content);
        return message;
    }

    private String requiredText(JsonNode node, String field) throws ProtocolException {
        String value = text(node == null ? null : node.get(field));
        if (value == null || value.trim().isEmpty()) {
            throw new ProtocolException(400, "invalid_request_error", field + " is required");
        }
        return value;
    }

    private String firstText(JsonNode node, String first, String second) throws ProtocolException {
        String result = text(node.get(first));
        return result == null || result.isEmpty() ? requiredText(node, second) : result;
    }

    private String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private void requireObject(JsonNode node) throws ProtocolException {
        if (node == null || !node.isObject()) {
            throw new ProtocolException(400, "invalid_request_error", "request body must be a JSON object");
        }
    }

    public static final class ProtocolException extends Exception {
        private final int status;
        private final String type;

        public ProtocolException(int status, String type, String message) {
            super(message);
            this.status = status;
            this.type = type;
        }

        public int getStatus() {
            return status;
        }

        public String getType() {
            return type;
        }
    }

    /** Converts OpenAI Chat Completions SSE chunks to protocol-native SSE events. */
    public final class StreamTranslator {
        private final Protocol protocol;
        private final String requestedModel;
        private final String responseId;
        private final String messageId;
        private final ChatMessage assistant = message("assistant", "");
        private final Map<Integer, ToolCall> toolCalls = new LinkedHashMap<>();
        private final Map<Integer, Integer> contentIndexes = new LinkedHashMap<>();
        private final List<String> pendingEvents = new ArrayList<>();
        private boolean started;
        private boolean textStarted;
        private boolean finished;
        private int nextContentIndex;
        private String model;
        private String finishReason = "stop";
        private Usage usage;
        private long sequence;

        private StreamTranslator(Protocol protocol, String requestedModel) {
            this.protocol = protocol;
            this.requestedModel = requestedModel;
            this.responseId = newProtocolId("resp");
            this.messageId = newProtocolId("msg");
            this.model = requestedModel == null ? "linkmind" : requestedModel;
        }

        public List<String> accept(String payload) {
            pendingEvents.clear();
            if (finished) {
                return Collections.emptyList();
            }
            if ("[DONE]".equals(payload == null ? "" : payload.trim())) {
                finish();
                return new ArrayList<>(pendingEvents);
            }
            try {
                JsonNode root = MAPPER.readTree(payload);
                if (root == null || !root.isObject()) {
                    return Collections.emptyList();
                }
                if (root.hasNonNull("model")) {
                    model = root.path("model").asText(model);
                }
                JsonNode usageNode = root.get("usage");
                if (usageNode != null && usageNode.isObject()) {
                    usage = usageFromChat(usageNode);
                }
                JsonNode choices = root.get("choices");
                if (choices == null || !choices.isArray() || choices.size() == 0) {
                    return Collections.emptyList();
                }
                JsonNode choice = choices.get(0);
                JsonNode delta = choice.has("delta") && choice.get("delta").isObject()
                        ? choice.get("delta") : choice.get("message");
                if (delta != null && delta.isObject()) {
                    String text = delta.path("content").asText("");
                    if (!text.isEmpty()) {
                        start();
                        startText();
                        assistant.setContent(assistant.getContent() + text);
                        if (protocol == Protocol.ANTHROPIC) {
                            ObjectNode event = MAPPER.createObjectNode();
                            event.put("type", "content_block_delta");
                            event.put("index", 0);
                            ObjectNode deltaNode = event.putObject("delta");
                            deltaNode.put("type", "text_delta");
                            deltaNode.put("text", text);
                            emit("content_block_delta", event);
                        } else {
                            ObjectNode event = MAPPER.createObjectNode();
                            event.put("type", "response.output_text.delta");
                            event.put("delta", text);
                            event.put("item_id", messageId);
                            event.put("output_index", 0);
                            event.put("content_index", 0);
                            emit(null, event);
                        }
                    }
                    handleToolCalls(delta.get("tool_calls"));
                }
                if (choice.hasNonNull("finish_reason")) {
                    finishReason = choice.path("finish_reason").asText("stop");
                }
            } catch (Exception ignored) {
                // The upstream streaming pipeline may emit provider-specific keepalive lines.
            }
            return new ArrayList<>(pendingEvents);
        }

        public List<String> finish() {
            pendingEvents.clear();
            if (finished) {
                return Collections.emptyList();
            }
            finished = true;
            start();
            if (protocol == Protocol.ANTHROPIC) {
                if (textStarted) {
                    ObjectNode event = MAPPER.createObjectNode();
                    event.put("type", "content_block_stop");
                    event.put("index", 0);
                    emit("content_block_stop", event);
                }
                for (Map.Entry<Integer, ToolCall> entry : toolCalls.entrySet()) {
                    ObjectNode event = MAPPER.createObjectNode();
                    event.put("type", "content_block_stop");
                    event.put("index", contentIndexes.get(entry.getKey()));
                    emit("content_block_stop", event);
                }
                ObjectNode delta = MAPPER.createObjectNode();
                delta.put("type", "message_delta");
                ObjectNode inner = delta.putObject("delta");
                inner.put("stop_reason", toAnthropicStopReason(finishReason));
                inner.putNull("stop_sequence");
                delta.set("usage", toAnthropicUsage(usage));
                emit("message_delta", delta);
                ObjectNode stop = MAPPER.createObjectNode();
                stop.put("type", "message_stop");
                emit("message_stop", stop);
            } else {
                if (textStarted) {
                    ObjectNode done = MAPPER.createObjectNode();
                    done.put("type", "response.output_text.done");
                    done.put("text", assistant.getContent());
                    done.put("item_id", messageId);
                    done.put("output_index", 0);
                    done.put("content_index", 0);
                    emit(null, done);
                    ObjectNode partDone = MAPPER.createObjectNode();
                    partDone.put("type", "response.content_part.done");
                    partDone.put("item_id", messageId);
                    partDone.put("output_index", 0);
                    partDone.put("content_index", 0);
                    ObjectNode part = partDone.putObject("part");
                    part.put("type", "output_text");
                    part.put("text", assistant.getContent());
                    part.putArray("annotations");
                    emit(null, partDone);
                    ObjectNode itemDone = MAPPER.createObjectNode();
                    itemDone.put("type", "response.output_item.done");
                    itemDone.put("output_index", 0);
                    itemDone.set("item", responseMessageItem("completed"));
                    emit(null, itemDone);
                }
                for (Map.Entry<Integer, ToolCall> entry : toolCalls.entrySet()) {
                    ToolCall call = entry.getValue();
                    ObjectNode argumentsDone = MAPPER.createObjectNode();
                    argumentsDone.put("type", "response.function_call_arguments.done");
                    argumentsDone.put("item_id", functionItemId(entry.getKey()));
                    argumentsDone.put("output_index", contentIndexes.get(entry.getKey()));
                    argumentsDone.put("name", call.getFunction().getName());
                    argumentsDone.put("arguments", call.getFunction().getArguments());
                    emit(null, argumentsDone);
                    ObjectNode itemDone = MAPPER.createObjectNode();
                    itemDone.put("type", "response.output_item.done");
                    itemDone.put("output_index", contentIndexes.get(entry.getKey()));
                    itemDone.set("item", responseFunctionItem(call, "completed", entry.getKey()));
                    emit(null, itemDone);
                }
                ChatCompletionResult result = new ChatCompletionResult();
                result.setModel(model);
                result.setUsage(usage);
                ChatCompletionChoice choice = new ChatCompletionChoice();
                choice.setMessage(assistantMessageForResult());
                choice.setFinish_reason(finishReason);
                result.setChoices(Collections.singletonList(choice));
                ObjectNode completed = MAPPER.createObjectNode();
                completed.put("type", "response.completed");
                completed.set("response", toResponsesResponse(result, requestedModel, responseId));
                emit(null, completed);
            }
            return new ArrayList<>(pendingEvents);
        }

        public String getResponseId() {
            return responseId;
        }

        public ChatMessage getAssistantMessage() {
            return assistantMessageForResult();
        }

        public boolean isFinished() {
            return finished;
        }

        private void handleToolCalls(JsonNode calls) {
            if (calls == null || !calls.isArray()) {
                return;
            }
            for (JsonNode callNode : calls) {
                int internalIndex = callNode.path("index").asInt(toolCalls.size());
                ToolCall call = toolCalls.get(internalIndex);
                boolean newCall = call == null;
                if (call == null) {
                    call = new ToolCall();
                    call.setIndex(internalIndex);
                    call.setId(callNode.path("id").asText(newProtocolId("call")));
                    call.setType("function");
                    call.setFunction(new ToolCallFunction());
                    call.getFunction().setArguments("");
                    toolCalls.put(internalIndex, call);
                    contentIndexes.put(internalIndex, nextContentIndex++);
                }
                if (callNode.hasNonNull("id")) {
                    call.setId(callNode.path("id").asText());
                }
                JsonNode function = callNode.get("function");
                if (function != null && function.isObject()) {
                    if (function.hasNonNull("name")) {
                        call.getFunction().setName(function.path("name").asText());
                    }
                    String arguments = function.path("arguments").asText("");
                    if (!arguments.isEmpty()) {
                        call.getFunction().setArguments(call.getFunction().getArguments() + arguments);
                    }
                }
                start();
                int outputIndex = contentIndexes.get(internalIndex);
                if (newCall) {
                    if (protocol == Protocol.ANTHROPIC) {
                        ObjectNode event = MAPPER.createObjectNode();
                        event.put("type", "content_block_start");
                        event.put("index", outputIndex);
                        ObjectNode block = event.putObject("content_block");
                        block.put("type", "tool_use");
                        block.put("id", call.getId());
                        block.put("name", call.getFunction().getName());
                        block.set("input", MAPPER.createObjectNode());
                        emit("content_block_start", event);
                    } else {
                        ObjectNode event = MAPPER.createObjectNode();
                        event.put("type", "response.output_item.added");
                        event.put("output_index", outputIndex);
                        event.set("item", responseFunctionItem(call, "in_progress", internalIndex));
                        emit(null, event);
                    }
                }
                String arguments = function == null ? "" : function.path("arguments").asText("");
                if (!arguments.isEmpty()) {
                    if (protocol == Protocol.ANTHROPIC) {
                        ObjectNode event = MAPPER.createObjectNode();
                        event.put("type", "content_block_delta");
                        event.put("index", outputIndex);
                        ObjectNode delta = event.putObject("delta");
                        delta.put("type", "input_json_delta");
                        delta.put("partial_json", arguments);
                        emit("content_block_delta", event);
                    } else {
                        ObjectNode event = MAPPER.createObjectNode();
                        event.put("type", "response.function_call_arguments.delta");
                        event.put("item_id", functionItemId(internalIndex));
                        event.put("output_index", outputIndex);
                        event.put("delta", arguments);
                        emit(null, event);
                    }
                }
            }
        }

        private void start() {
            if (started) {
                return;
            }
            started = true;
            if (protocol == Protocol.ANTHROPIC) {
                ObjectNode event = MAPPER.createObjectNode();
                event.put("type", "message_start");
                ObjectNode message = event.putObject("message");
                message.put("id", messageId);
                message.put("type", "message");
                message.put("role", "assistant");
                message.put("model", model);
                message.putArray("content");
                message.putNull("stop_reason");
                message.putNull("stop_sequence");
                message.set("usage", toAnthropicUsage(null));
                emit("message_start", event);
            } else {
                ChatCompletionResult result = new ChatCompletionResult();
                result.setModel(model);
                ObjectNode created = MAPPER.createObjectNode();
                created.put("type", "response.created");
                created.set("response", createResponseEnvelope(result, requestedModel, responseId, "in_progress"));
                emit(null, created);
                ObjectNode progress = MAPPER.createObjectNode();
                progress.put("type", "response.in_progress");
                progress.set("response", createResponseEnvelope(result, requestedModel, responseId, "in_progress"));
                emit(null, progress);
            }
        }

        private void startText() {
            if (textStarted) {
                return;
            }
            textStarted = true;
            nextContentIndex = Math.max(nextContentIndex, 1);
            if (protocol == Protocol.ANTHROPIC) {
                ObjectNode event = MAPPER.createObjectNode();
                event.put("type", "content_block_start");
                event.put("index", 0);
                ObjectNode block = event.putObject("content_block");
                block.put("type", "text");
                block.put("text", "");
                emit("content_block_start", event);
            } else {
                ObjectNode itemAdded = MAPPER.createObjectNode();
                itemAdded.put("type", "response.output_item.added");
                itemAdded.put("output_index", 0);
                itemAdded.set("item", responseMessageItem("in_progress"));
                emit(null, itemAdded);
                ObjectNode contentAdded = MAPPER.createObjectNode();
                contentAdded.put("type", "response.content_part.added");
                contentAdded.put("item_id", messageId);
                contentAdded.put("output_index", 0);
                contentAdded.put("content_index", 0);
                ObjectNode part = contentAdded.putObject("part");
                part.put("type", "output_text");
                part.put("text", "");
                part.putArray("annotations");
                emit(null, contentAdded);
            }
        }

        private ObjectNode responseMessageItem(String status) {
            ObjectNode item = MAPPER.createObjectNode();
            item.put("id", messageId);
            item.put("type", "message");
            item.put("status", status);
            item.put("role", "assistant");
            ArrayNode content = item.putArray("content");
            ObjectNode text = content.addObject();
            text.put("type", "output_text");
            text.put("text", assistant.getContent());
            text.putArray("annotations");
            return item;
        }

        private ObjectNode responseFunctionItem(ToolCall call, String status, int internalIndex) {
            ObjectNode item = MAPPER.createObjectNode();
            item.put("id", functionItemId(internalIndex));
            item.put("type", "function_call");
            item.put("status", status);
            item.put("call_id", call.getId());
            item.put("name", call.getFunction().getName());
            item.put("arguments", call.getFunction().getArguments());
            return item;
        }

        private String functionItemId(int internalIndex) {
            return "fc_" + responseId.substring("resp_".length()) + "_" + internalIndex;
        }

        private ChatMessage assistantMessageForResult() {
            ChatMessage result = message("assistant", assistant.getContent());
            if (!toolCalls.isEmpty()) {
                result.setTool_calls(new ArrayList<>(toolCalls.values()));
            }
            return result;
        }

        private Usage usageFromChat(JsonNode node) {
            Usage result = new Usage();
            result.setPrompt_tokens(node.path("prompt_tokens").asLong(0L));
            result.setCompletion_tokens(node.path("completion_tokens").asLong(0L));
            result.setTotal_tokens(node.path("total_tokens").asLong(0L));
            return result;
        }

        private void emit(String eventName, ObjectNode data) {
            if (protocol == Protocol.ANTHROPIC && eventName != null) {
                pendingEvents.add("event: " + eventName + "\n" + "data: " + data.toString() + "\n\n");
                return;
            }
            data.put("sequence_number", ++sequence);
            pendingEvents.add("data: " + data.toString() + "\n\n");
        }
    }
}
