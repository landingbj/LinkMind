package ai.openai.pojo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatCompletionRequestSchemaCompatibilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDeserializeToolSchemaWithPatternProperties() throws Exception {
        String json = "{\n" +
                "  \"model\": \"gpt-5.4\",\n" +
                "  \"messages\": [{\"role\": \"user\", \"content\": \"hi\"}],\n" +
                "  \"tools\": [{\n" +
                "    \"type\": \"function\",\n" +
                "    \"function\": {\n" +
                "      \"name\": \"exec\",\n" +
                "      \"description\": \"Execute shell commands\",\n" +
                "      \"strict\": false,\n" +
                "      \"parameters\": {\n" +
                "        \"type\": \"object\",\n" +
                "        \"required\": [\"command\"],\n" +
                "        \"properties\": {\n" +
                "          \"env\": {\n" +
                "            \"type\": \"object\",\n" +
                "            \"patternProperties\": {\n" +
                "              \"^(.*)$\": {\n" +
                "                \"type\": \"string\"\n" +
                "              }\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }]\n" +
                "}";

        ChatCompletionRequest request = objectMapper.readValue(json, ChatCompletionRequest.class);
        assertNotNull(request.getTools());
        assertEquals(1, request.getTools().size());

        Property env = request.getTools().get(0).getFunction().getParameters().getProperties().get("env");
        assertNotNull(env);
        Object patternProperties = env.getSchemaExtensions().get("patternProperties");
        assertTrue(patternProperties instanceof Map);
        assertEquals("string", ((Map<?, ?>) ((Map<?, ?>) patternProperties).get("^(.*)$")).get("type"));
    }
}
