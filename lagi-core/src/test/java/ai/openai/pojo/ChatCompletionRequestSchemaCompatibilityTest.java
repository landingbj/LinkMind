package ai.openai.pojo;

import ai.llm.pojo.EnhanceChatCompletionRequest;
import cn.hutool.core.bean.BeanUtil;
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

    @Test
    void shouldPreserveAccountAttributionWhenCopyingForModelExecution() {
        ChatCompletionRequest source = new ChatCompletionRequest();
        source.setAccountUserId("cuihua-user-1");
        source.setAccountUserApiKeyId(42L);

        EnhanceChatCompletionRequest target = new EnhanceChatCompletionRequest();
        BeanUtil.copyProperties(source, target);
        source.copyAccountAttributionTo(target);

        assertEquals("cuihua-user-1", target.getAccountUserId());
        assertEquals(Long.valueOf(42L), target.getAccountUserApiKeyId());

        target.setModel("Alibaba/shared");
        target.setKeyPoolSessionId("box-session");
        ChatCompletionRequest upstream = target.forUpstreamModel("shared");

        assertEquals("Alibaba/shared", target.getModel());
        assertEquals("shared", upstream.getModel());
        assertEquals("box-session", upstream.getKeyPoolSessionId());
        assertEquals("cuihua-user-1", upstream.getAccountUserId());
        assertEquals(Long.valueOf(42L), upstream.getAccountUserApiKeyId());
    }
}
