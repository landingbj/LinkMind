package ai.llm.responses;

import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatMessage;
import ai.openai.pojo.MultiModalContent;
import ai.openai.pojo.Tool;
import ai.openai.pojo.Function;
import ai.openai.pojo.Parameters;
import ai.openai.pojo.Property;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class QwenResponsesChatCompletionConverterTest {

    @Test
    void shouldUseStringContentForAssistantAndSystemMessages() {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("qwen-plus");

        ResponseSessionContext context = new ResponseSessionContext();
        context.setInputMessages(Arrays.asList(
                ChatMessage.builder().role("system").content("你是一个助手").build(),
                ChatMessage.builder().role("assistant").content("历史回答").build()
        ));

        QwenResponseCreateRequest responseRequest = QwenResponsesChatCompletionConverter.toRequest(request, context, "qwen-plus");
        List<QwenResponseInputItem> input = (List<QwenResponseInputItem>) responseRequest.getInput();
        assertEquals("你是一个助手", input.get(0).getContent());
        assertEquals("历史回答", input.get(1).getContent());
    }

    @Test
    void shouldUseQwenUserMultimodalAndFlattenTools() {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel("qwen-plus");
        request.setTools(Collections.singletonList(createTool()));

        MultiModalContent image = new MultiModalContent();
        image.setType("image_url");
        MultiModalContent.ImageUrl imageUrl = new MultiModalContent.ImageUrl();
        imageUrl.setUrl("https://example.com/a.png");
        image.setImageUrl(imageUrl);
        MultiModalContent text = new MultiModalContent();
        text.setType("text");
        text.setText("看这张图");

        ResponseSessionContext context = new ResponseSessionContext();
        context.setInputMessages(Collections.singletonList(ChatMessage.builder()
                .role("user")
                .content("[{\"type\":\"text\",\"text\":\"看这张图\"},{\"type\":\"image_url\",\"image_url\":{\"url\":\"https://example.com/a.png\"}}]")
                .build()));

        QwenResponseCreateRequest responseRequest = QwenResponsesChatCompletionConverter.toRequest(request, context, "qwen-plus");
        List<QwenResponseInputItem> input = (List<QwenResponseInputItem>) responseRequest.getInput();
        assertInstanceOf(List.class, input.get(0).getContent());
        List<?> content = (List<?>) input.get(0).getContent();
        assertEquals("text", ((QwenResponseInputContent) content.get(0)).getType());
        assertEquals("image_url", ((QwenResponseInputContent) content.get(1)).getType());

        assertNotNull(responseRequest.getTools());
        assertEquals("function", responseRequest.getTools().get(0).getType());
        assertEquals("exec", responseRequest.getTools().get(0).getName());
        assertNotNull(responseRequest.getTools().get(0).getParameters());
    }

    private Tool createTool() {
        Tool tool = new Tool();
        tool.setType("function");
        Function function = new Function();
        function.setName("exec");
        function.setDescription("execute command");
        Parameters parameters = new Parameters();
        parameters.setType("object");
        HashMap<String, Property> properties = new HashMap<>();
        Property property = new Property();
        property.setType("string");
        properties.put("command", property);
        parameters.setProperties(properties);
        parameters.setRequired(Collections.singletonList("command"));
        function.setParameters(parameters);
        tool.setFunction(function);
        return tool;
    }
}
