package ai.llm.responses;

import ai.openai.pojo.*;
import ai.utils.LagiGlobal;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public final class ResponsesChatCompletionConverter {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ResponsesChatCompletionConverter() {
    }

    public static ResponseCreateRequest toRequest(ChatCompletionRequest request,
                                                  ResponseSessionContext sessionContext,
                                                  String modelName) {
//        if (modelName.toLowerCase().startsWith("gpt")) {
//            fixupFunctionCallId(request);
//        }
        ResponseCreateRequest responseRequest = new ResponseCreateRequest();
        responseRequest.setModel(modelName);
        responseRequest.setInstructions(extractInstructions(sessionContext));
        responseRequest.setInput(toInputItems(sessionContext.getInputMessages()));
        responseRequest.setPrevious_response_id(sessionContext.getPreviousResponseId());
        responseRequest.setStream(Boolean.TRUE.equals(request.getStream()));
        responseRequest.setMax_output_tokens(request.getMax_tokens());
        List<ResponseTool> requestTools = toTools(request.getTools(), modelName);
        if (requestTools != null) {
            responseRequest.setTools(requestTools);
            responseRequest.setTool_choice(toToolChoice(request.getTool_choice(), requestTools));
            responseRequest.setParallel_tool_calls(request.getParallel_tool_calls());
        }
        responseRequest.setText(toText(request.getResponse_format()));
        return responseRequest;
    }

    public static void fixupFunctionCallId(ChatCompletionRequest request) {
        List<ChatMessage> chatMessages = request.getMessages();
        ChatMessage lastAssistantMessage = null;
        for (ChatMessage chatMessage : chatMessages) {
            // assistant
            if (chatMessage.getTool_calls() != null && !chatMessage.getTool_calls().isEmpty()) {
                for (ToolCall toolCall : chatMessage.getTool_calls()) {
                    if (toolCall.getId() != null) {
                        String id = toolCall.getId();
                        if(id.startsWith("call") && !id.startsWith("call_")){
                            toolCall.setId(id.replaceAll("call", "call_"));
                        }
                    }
                }
                List<ToolCall> toolCall = chatMessage.getTool_calls().stream().filter(tl -> !tl.getId().startsWith("call_auto")).collect(Collectors.toList());
                chatMessage.setTool_calls(toolCall);
            }
            // tool
            if(chatMessage.getTool_call_id() != null) {
                String id = chatMessage.getTool_call_id();
                if(id.startsWith("call") && !id.startsWith("call_")) {
                    chatMessage.setTool_call_id(id.replaceAll("call", "call_"));
                    id = chatMessage.getTool_call_id();
                    if(id.startsWith("call_auto")) {
                        try {
                            int index = Integer.parseInt(id.replace("call_auto", "")) - 1;
                            if(lastAssistantMessage != null && lastAssistantMessage.getTool_calls() != null) {
                                String id1 = lastAssistantMessage.getTool_calls().get(index).getId();
                                chatMessage.setTool_call_id(id1);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
            if(chatMessage.getRole().equals(LagiGlobal.LLM_ROLE_ASSISTANT)) {
                lastAssistantMessage = chatMessage;
            }
        }
        List<Tool> tools = request.getTools();
        if (tools != null) {
            for (Tool tool : tools) {
                if (tool.getFunction() != null && tool.getFunction().getParameters() != null) {
                    Set<String> rs = new HashSet<>(tool.getFunction().getParameters().getRequired());
                    Set<String> ps = tool.getFunction().getParameters().getProperties().keySet();
                    boolean b = rs.containsAll(ps);
                    if(!b) {
                        tool.getFunction().setStrict(false);
                    }
                }
            }
        }
    }

    public static ChatCompletionResult convertResponse(String body) {
        try {
            JsonNode root = MAPPER.readTree(body);
            return convertResponse(root, false);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert responses payload", e);
        }
    }

    public static ChatCompletionResult convertStreamEvent(String body) {
        //记录日志
//        System.out.println("body:" + body);
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
                ChatCompletionResult result = convertResponse(response, true);
                if (result.getChoices() != null && !result.getChoices().isEmpty()) {
                    ChatCompletionChoice choice = result.getChoices().get(0);
                    if ("response.incomplete".equals(type)) {
                        choice.setFinish_reason("length");
                    } else if (choice.getDelta() != null
                            && choice.getDelta().getTool_calls() != null
                            && !choice.getDelta().getTool_calls().isEmpty()) {
                        choice.setFinish_reason("tool_calls");
                    } else {
                        choice.setFinish_reason("stop");
                    }
                    if (choice.getDelta() != null) {
                        choice.getDelta().setContent("");
                    }
                }
                return result;
            }
//            if("response.output_item.added".equals(type)) {
//                JsonNode itemNode = root.path("item");
//                String itemType = itemNode.path("type").asText();
//                if ("function_call".equals(itemType)) {
//                    String callId = itemNode.path("call_id").asText("");
//                    String name = itemNode.path("name").asText("");
//                    String arguments = itemNode.path("arguments").asText("");
//                    return createFunctionCallDeltaChunk( callId, name, arguments);
//                }
//            }
//            if("response.function_call_arguments.delta".equals(type)) {
//                String delta = root.path("delta").asText("");
//                return createFunctionArgumentsDeltaChunk( delta);
//            }
//            if("response.function_call_arguments.done".equals(type)) {
//                return createFunctionArgumentsDeltaChunk("");
//            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert responses stream event", e);
        }
    }

//    private static ChatCompletionResult createFunctionCallDeltaChunk(String callId, String name, String arguments) {
//        ChatCompletionResult result = new ChatCompletionResult();
//        result.setCreated(0);
//
//        ToolCallFunction function = new ToolCallFunction();
//        function.setName(name);
//        function.setArguments(arguments);
//
//        ToolCall toolCall = new ToolCall();
//        toolCall.setId(callId);
//        toolCall.setType("function");
//        toolCall.setFunction(function);
//
//        ChatMessage message = new ChatMessage();
//        message.setRole("assistant");
//        message.setContent("");
//        message.setTool_calls(Collections.singletonList(toolCall));
//
//        ChatCompletionChoice choice = new ChatCompletionChoice();
//        choice.setMessage(message);
//        result.setChoices(Collections.singletonList(choice));
//        return result;
//    }

    private static ChatCompletionResult createFunctionArgumentsDeltaChunk(String delta) {
        ChatCompletionResult result = new ChatCompletionResult();
        result.setId("");
        result.setCreated(0);

        ToolCallFunction function = new ToolCallFunction();
        function.setArguments(delta);

        ToolCall toolCall = new ToolCall();
        toolCall.setId("");
        toolCall.setType("function");
        toolCall.setFunction(function);

        ChatMessage message = new ChatMessage();
        message.setRole("assistant");
        message.setContent("");
        message.setTool_calls(Collections.singletonList(toolCall));


        ChatCompletionChoice choice = new ChatCompletionChoice();
        choice.setMessage(message);
        result.setChoices(Collections.singletonList(choice));
        return result;
    }
    private static ChatCompletionResult convertResponse(JsonNode root, boolean stream) {
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
        if (stream) {
            choice.setDelta(message);
        } else {
            choice.setMessage(message);
        }
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

        PromptTokensDetails promptTokensDetails = new PromptTokensDetails();
        promptTokensDetails.setCached_tokens(usageNode.path("input_tokens_details").path("cached_tokens").asLong(0L));
        usage.setPrompt_tokens_details(promptTokensDetails);

        CompletionTokensDetails completionTokensDetails = new CompletionTokensDetails();
        completionTokensDetails.setReasoning_tokens(usageNode.path("output_tokens_details").path("reasoning_tokens").asLong(0L));
        usage.setCompletion_tokens_details(completionTokensDetails);
        return usage;
    }

    private static List<ResponseInputItem> toInputItems(List<ChatMessage> messages) {
        List<ResponseInputItem> items = new ArrayList<>();
        Set<String> emittedFunctionCallIds = new LinkedHashSet<>();
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
                    if (toolCall == null || StrUtil.isBlank(toolCall.getId())) {
                        continue;
                    }
                    if (!emittedFunctionCallIds.add(toolCall.getId())) {
                        log.warn("Skip duplicated function_call item, call_id={}", toolCall.getId());
                        continue;
                    }
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
        List<ChatMessage> messages = sessionContext.getInputMessages();
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
        item.setType("message");
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

    private static List<ResponseTool> toTools(List<Tool> tools, String modelName) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }
        List<ResponseTool> responseTools = new ArrayList<>();
        for (Tool tool : tools) {
            ResponseTool responseTool = new ResponseTool();
            responseTool.setType(tool.getType());
            if (tool.getFunction() != null) {
                if (modelName.toLowerCase().contains("qwen") && tool.getFunction().getName().equals("web_search")) {
                    responseTool.setName("web_search2");
                } else {
                    responseTool.setName(tool.getFunction().getName());
                }
                responseTool.setDescription(tool.getFunction().getDescription());
                Parameters parameters = tool.getFunction().getParameters();
                Boolean strict = tool.getFunction().getStrict();
                // Azure/OpenAI strict function mode requires required[] to contain every property key.
                // If incoming schema is not strict-compatible, downgrade strict to avoid hard request failure.
                if (Boolean.TRUE.equals(strict) && !isStrictCompatible(parameters)) {
                    strict = Boolean.FALSE;
                }
                responseTool.setParameters(parameters);
                responseTool.setStrict(strict);
            }
            responseTools.add(responseTool);
        }
        return responseTools;
    }

    private static boolean isStrictCompatible(Parameters parameters) {
        if (parameters == null || parameters.getProperties() == null || parameters.getProperties().isEmpty()) {
            return true;
        }
        List<String> required = parameters.getRequired();
        if (required == null) {
            return false;
        }
        return required.containsAll(parameters.getProperties().keySet());
    }

    private static Object toToolChoice(String toolChoice, List<ResponseTool> requestTools) {
        if (StrUtil.isBlank(toolChoice)) {
            AllowedToolsRequest request = new AllowedToolsRequest();
            List<AllowedToolsRequest.Tool> tools = new ArrayList<>();
            request.setMode("auto");
            request.setTools(tools);
            request.setType("allowed_tools");
            for (ResponseTool requestTool : requestTools) {
                AllowedToolsRequest.Tool tool = new AllowedToolsRequest.Tool();
                tool.setType(requestTool.getType());
                tool.setName(requestTool.getName());
                tools.add(tool);
            }
            return request;
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
