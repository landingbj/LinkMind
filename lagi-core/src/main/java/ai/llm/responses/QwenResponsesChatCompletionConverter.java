package ai.llm.responses;

import ai.openai.pojo.*;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class QwenResponsesChatCompletionConverter {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private QwenResponsesChatCompletionConverter() {
    }

    public static QwenResponseCreateRequest toRequest(ChatCompletionRequest request,
                                                      ResponseSessionContext sessionContext,
                                                      String modelName) {
        QwenResponseCreateRequest responseRequest = new QwenResponseCreateRequest();
        responseRequest.setModel(modelName);
        responseRequest.setInput(toInputItems(sessionContext.getInputMessages()));
        responseRequest.setPrevious_response_id(sessionContext.getPreviousResponseId());
        responseRequest.setStream(Boolean.TRUE.equals(request.getStream()));
        responseRequest.setMax_output_tokens(request.getMax_tokens());
        responseRequest.setTools(toTools(request.getTools()));
        responseRequest.setTool_choice(toToolChoice(request.getTool_choice()));
        responseRequest.setParallel_tool_calls(request.getParallel_tool_calls());
        responseRequest.setText(toText(request.getResponse_format()));
        responseRequest.setTemperature(request.getTemperature());
        responseRequest.setTop_p(request.getTop_p());
        return responseRequest;
    }

    private static List<QwenResponseInputItem> toInputItems(List<ChatMessage> messages) {
        List<QwenResponseInputItem> items = new ArrayList<>();
        if (messages == null) {
            return items;
        }
        for (ChatMessage message : messages) {
            if ("tool".equals(message.getRole()) && StrUtil.isNotBlank(message.getTool_call_id())) {
                QwenResponseInputItem item = new QwenResponseInputItem();
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
                    QwenResponseInputItem item = new QwenResponseInputItem();
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

    private static QwenResponseInputItem createMessageItem(String role, String content) {
        QwenResponseInputItem item = new QwenResponseInputItem();
        item.setRole(role);
        item.setContent(toMessageContent(role, content));
        return item;
    }

    private static Object toMessageContent(String role, String content) {
        if (!"user".equals(role)) {
            return content == null ? "" : content;
        }
        if (StrUtil.isBlank(content)) {
            return "";
        }
        try {
            List<MultiModalContent> multimodalContents = MAPPER.readValue(content, new TypeReference<List<MultiModalContent>>() {});
            List<QwenResponseInputContent> responseContents = new ArrayList<>();
            for (MultiModalContent multimodalContent : multimodalContents) {
                QwenResponseInputContent inputContent = new QwenResponseInputContent();
                if ("text".equals(multimodalContent.getType())) {
                    inputContent.setType("text");
                    inputContent.setText(multimodalContent.getText());
                } else if ("image_url".equals(multimodalContent.getType()) && multimodalContent.getImageUrl() != null) {
                    inputContent.setType("image_url");
                    inputContent.setImageUrl(multimodalContent.getImageUrl());
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
        return content;
    }

    private static List<ResponseTool> toTools(List<Tool> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }
        List<ResponseTool> responseTools = new ArrayList<>();
        for (Tool tool : tools) {
            if (tool == null) {
                continue;
            }
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
        return responseTools.isEmpty() ? null : responseTools;
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
        if ("json_object".equals(responseFormat.getType())) {
            ResponseTextFormat format = new ResponseTextFormat();
            format.setType(responseFormat.getType());
            ResponseText text = new ResponseText();
            text.setFormat(format);
            return text;
        }
        if (!"json_schema".equals(responseFormat.getType()) || responseFormat.getJson_schema() == null) {
            throw ResponseProtocolUtil.invalidRequest("qwen responses protocol only supports json_object or json_schema response_format");
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
}
