package ai.llm.responses;

import ai.openai.pojo.*;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class ResponsesChatCompletionConverter {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ResponsesChatCompletionConverter() {
    }

    public static ResponseCreateRequest toRequest(ChatCompletionRequest request,
                                                  ResponseSessionContext sessionContext,
                                                  String modelName) {
        ResponseCreateRequest responseRequest = new ResponseCreateRequest();
        responseRequest.setModel(modelName);
        responseRequest.setInstructions(extractInstructions(sessionContext));
        responseRequest.setInput(toInputItems(sessionContext.getInputMessages()));
        responseRequest.setPrevious_response_id(sessionContext.getPreviousResponseId());
        responseRequest.setStream(Boolean.TRUE.equals(request.getStream()));
        responseRequest.setMax_output_tokens(request.getMax_tokens());
        responseRequest.setTools(toTools(request.getTools()));
        responseRequest.setTool_choice(toToolChoice(request.getTool_choice()));
        responseRequest.setParallel_tool_calls(request.getParallel_tool_calls());
        responseRequest.setText(toText(request.getResponse_format()));
        return responseRequest;
    }

    public static ChatCompletionResult convertResponse(String body) {
        try {
            JsonNode root = MAPPER.readTree(body);
            return convertResponse(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert responses payload", e);
        }
    }

    public static ChatCompletionResult convertStreamEvent(String body) {
        //记录日志
        if (StrUtil.isBlank(body) || "[DONE]".equals(body)) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(body);
            String type = root.path("type").asText();
            if ("response.output_text.delta".equals(type)) {
                String delta = root.path("delta").asText();
                if (StrUtil.isBlank(delta)) {
                    return null;
                }
                return createDeltaChunk(root.path("response_id").asText(null), delta);
            }
            if ("response.completed".equals(type) || "response.incomplete".equals(type)) {
                JsonNode response = root.path("response");
                ChatCompletionResult result = convertResponse(response);
                if (result.getChoices() != null && !result.getChoices().isEmpty()) {
                    result.getChoices().get(0).setFinish_reason("response.incomplete".equals(type) ? "length" : "stop");
                    if (result.getChoices().get(0).getMessage() != null) {
                        result.getChoices().get(0).getMessage().setContent("");
                    }
                }
                return result;
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert responses stream event", e);
        }
    }

    private static ChatCompletionResult convertResponse(JsonNode root) {
        ChatCompletionResult result = new ChatCompletionResult();
        result.setId(root.path("id").asText(null));
        result.setModel(root.path("model").asText(null));
        result.setObject("chat.completion");
        result.setCreated(System.currentTimeMillis() / 1000L);

        ChatMessage message = new ChatMessage();
        message.setRole("assistant");
        message.setContent(extractOutputText(root.path("output")));
        String reasoning = extractReasoning(root.path("output"));
        if (StrUtil.isNotBlank(reasoning)) {
            message.setReasoning_content(reasoning);
        }
        List<ToolCall> toolCalls = extractToolCalls(root.path("output"));
        if (!toolCalls.isEmpty()) {
            message.setTool_calls(toolCalls);
        }

        ChatCompletionChoice choice = new ChatCompletionChoice();
        choice.setIndex(0);
        choice.setMessage(message);
        choice.setDelta(message);
        choice.setFinish_reason("stop");
        result.setChoices(Collections.singletonList(choice));
        result.setUsage(toUsage(root.path("usage")));
        return result;
    }

    private static ChatCompletionResult createDeltaChunk(String responseId, String delta) {
        ChatCompletionResult result = new ChatCompletionResult();
        result.setId(responseId);
        result.setObject("chat.completion.chunk");
        result.setCreated(System.currentTimeMillis() / 1000L);
        ChatMessage message = new ChatMessage();
        message.setRole("assistant");
        message.setContent(delta);
        ChatMessage deltaChunk = new ChatMessage();
        deltaChunk.setRole("assistant");
        deltaChunk.setContent(delta);
        ChatCompletionChoice choice = new ChatCompletionChoice();
        choice.setIndex(0);
        choice.setMessage(message);
        choice.setDelta(deltaChunk);
        result.setChoices(Collections.singletonList(choice));
        return result;
    }

    private static Usage toUsage(JsonNode usageNode) {
        Usage usage = new Usage();
        if (usageNode == null || usageNode.isMissingNode()) {
            return usage;
        }
        usage.setPrompt_tokens(usageNode.path("input_tokens").asLong(0L));
        usage.setCompletion_tokens(usageNode.path("output_tokens").asLong(0L));
        usage.setTotal_tokens(usageNode.path("total_tokens").asLong(0L));
        return usage;
    }

    private static List<ResponseInputItem> toInputItems(List<ChatMessage> messages) {
        List<ResponseInputItem> items = new ArrayList<>();
        if (messages == null) {
            return items;
        }
        for (ChatMessage message : messages) {
            if ("system".equals(message.getRole())) {
                continue;
            }
            if ("tool".equals(message.getRole()) && StrUtil.isNotBlank(message.getTool_call_id())) {
                ResponseInputItem item = new ResponseInputItem();
                item.setType("function_call_output");
                item.setCall_id(message.getTool_call_id());
                item.setOutput(message.getContent());
                items.add(item);
                continue;
            }
            if (message.getTool_calls() != null && !message.getTool_calls().isEmpty()) {
                if (StrUtil.isNotBlank(message.getContent())) {
                    items.add(createMessageItem(message.getRole(), message.getContent()));
                }
                for (ToolCall toolCall : message.getTool_calls()) {
                    ResponseInputItem item = new ResponseInputItem();
                    item.setType("function_call");
                    item.setCall_id(toolCall.getId());
                    if (toolCall.getFunction() != null) {
                        item.setName(toolCall.getFunction().getName());
                        item.setArguments(toolCall.getFunction().getArguments());
                    }
                    items.add(item);
                }
                continue;
            }
            items.add(createMessageItem(message.getRole(), message.getContent()));
        }
        return items;
    }

    private static String extractInstructions(ResponseSessionContext sessionContext) {
        List<ChatMessage> messages = sessionContext.getNormalizedMessages();
        if (messages == null || messages.isEmpty()) {
            messages = sessionContext.getInputMessages();
        }
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        String instructions = messages.stream()
                .filter(message -> message != null && "system".equals(message.getRole()) && StrUtil.isNotBlank(message.getContent()))
                .map(ChatMessage::getContent)
                .collect(Collectors.joining("\n\n"));
        return StrUtil.isBlank(instructions) ? null : instructions;
    }

    private static ResponseInputItem createMessageItem(String role, String content) {
        ResponseInputItem item = new ResponseInputItem();
        item.setRole(role);
        item.setContent(toInputContents(role, content));
        return item;
    }

    private static List<ResponseInputContent> toInputContents(String role, String content) {
        String textType = getTextContentType(role);
        if (StrUtil.isBlank(content)) {
            ResponseInputContent inputContent = new ResponseInputContent();
            inputContent.setType(textType);
            inputContent.setText("");
            return Collections.singletonList(inputContent);
        }
        try {
            List<MultiModalContent> multimodalContents = MAPPER.readValue(content, new TypeReference<List<MultiModalContent>>() {});
            List<ResponseInputContent> responseContents = new ArrayList<>();
            for (MultiModalContent multimodalContent : multimodalContents) {
                ResponseInputContent inputContent = new ResponseInputContent();
                if ("text".equals(multimodalContent.getType())) {
                    inputContent.setType(textType);
                    inputContent.setText(multimodalContent.getText());
                } else if ("image_url".equals(multimodalContent.getType()) && multimodalContent.getImageUrl() != null) {
                    inputContent.setType("input_image");
                    inputContent.setImage_url(multimodalContent.getImageUrl().getUrl());
                    inputContent.setDetail(multimodalContent.getImageUrl().getDetail());
                } else {
                    continue;
                }
                responseContents.add(inputContent);
            }
            if (!responseContents.isEmpty()) {
                return responseContents;
            }
        } catch (Exception ignored) {
        }
        ResponseInputContent inputContent = new ResponseInputContent();
        inputContent.setType(textType);
        inputContent.setText(content);
        return Collections.singletonList(inputContent);
    }

    private static String getTextContentType(String role) {
        if ("assistant".equals(role)) {
            return "output_text";
        }
        return "input_text";
    }

    private static List<ResponseTool> toTools(List<Tool> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }
        List<ResponseTool> responseTools = new ArrayList<>();
        for (Tool tool : tools) {
            ResponseTool responseTool = new ResponseTool();
            responseTool.setType(tool.getType());
            if (tool.getFunction() != null) {
                responseTool.setName(tool.getFunction().getName());
                responseTool.setDescription(tool.getFunction().getDescription());
                responseTool.setParameters(tool.getFunction().getParameters());
                responseTool.setStrict(tool.getFunction().getStrict());
            }
            responseTools.add(responseTool);
        }
        return responseTools;
    }

    private static Object toToolChoice(String toolChoice) {
        if (StrUtil.isBlank(toolChoice)) {
            return null;
        }
        return toolChoice;
    }

    private static ResponseText toText(ResponseFormat responseFormat) {
        if (responseFormat == null) {
            return null;
        }
        if (!"json_schema".equals(responseFormat.getType()) || responseFormat.getJson_schema() == null) {
            throw ResponseProtocolUtil.invalidRequest("responses protocol only supports json_schema response_format");
        }
        ResponseTextFormat format = new ResponseTextFormat();
        format.setType(responseFormat.getType());
        format.setName(responseFormat.getJson_schema().getName());
        format.setSchema(responseFormat.getJson_schema().getSchema());
        format.setStrict(responseFormat.getJson_schema().getStrict());
        ResponseText text = new ResponseText();
        text.setFormat(format);
        return text;
    }

    private static String extractOutputText(JsonNode outputNode) {
        List<String> parts = new ArrayList<>();
        if (outputNode == null || outputNode.isMissingNode() || !outputNode.isArray()) {
            return "";
        }
        for (JsonNode item : outputNode) {
            if (!"message".equals(item.path("type").asText())) {
                continue;
            }
            JsonNode contentNode = item.path("content");
            if (!contentNode.isArray()) {
                continue;
            }
            for (JsonNode contentItem : contentNode) {
                if ("output_text".equals(contentItem.path("type").asText())) {
                    parts.add(contentItem.path("text").asText(""));
                }
            }
        }
        return String.join("", parts);
    }

    private static String extractReasoning(JsonNode outputNode) {
        List<String> parts = new ArrayList<>();
        if (outputNode == null || outputNode.isMissingNode() || !outputNode.isArray()) {
            return "";
        }
        for (JsonNode item : outputNode) {
            if (!"reasoning".equals(item.path("type").asText())) {
                continue;
            }
            JsonNode summaryNode = item.path("summary");
            if (!summaryNode.isArray()) {
                continue;
            }
            for (JsonNode summaryItem : summaryNode) {
                if (summaryItem.has("text")) {
                    parts.add(summaryItem.path("text").asText(""));
                }
            }
        }
        return String.join("", parts);
    }

    private static List<ToolCall> extractToolCalls(JsonNode outputNode) {
        List<ToolCall> toolCalls = new ArrayList<>();
        if (outputNode == null || outputNode.isMissingNode() || !outputNode.isArray()) {
            return toolCalls;
        }
        for (JsonNode item : outputNode) {
            if (!"function_call".equals(item.path("type").asText())) {
                continue;
            }
            ToolCall toolCall = new ToolCall();
            toolCall.setId(item.path("call_id").asText(item.path("id").asText(null)));
            toolCall.setType("function");
            ToolCallFunction function = new ToolCallFunction();
            function.setName(item.path("name").asText(null));
            function.setArguments(item.path("arguments").asText(null));
            toolCall.setFunction(function);
            toolCalls.add(toolCall);
        }
        return toolCalls;
    }
}
