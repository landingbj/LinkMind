package ai.llm.utils.convert;

import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GptConvertTest {

    @Test
    void shouldPreserveToolCallIndexInStreamChunk() {
        String body = "{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"Bash\",\"arguments\":\"\"}}]},\"finish_reason\":null}]}";

        ChatCompletionResult result = GptConvert.convertSteamLine2ChatCompletionResult(body);

        ToolCall toolCall = result.getChoices().get(0).getDelta().getTool_calls().get(0);
        assertEquals(Integer.valueOf(0), toolCall.getIndex());
    }

    @Test
    void shouldPreserveParallelToolCallIndexesInStreamChunk() {
        String body = "{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":["
                + "{\"index\":0,\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"Bash\",\"arguments\":\"\"}},"
                + "{\"index\":1,\"id\":\"call_2\",\"type\":\"function\",\"function\":{\"name\":\"Glob\",\"arguments\":\"\"}}"
                + "]},\"finish_reason\":null}]}";

        ChatCompletionResult result = GptConvert.convertSteamLine2ChatCompletionResult(body);

        List<ToolCall> toolCalls = result.getChoices().get(0).getDelta().getTool_calls();
        assertEquals(Integer.valueOf(0), toolCalls.get(0).getIndex());
        assertEquals(Integer.valueOf(1), toolCalls.get(1).getIndex());
    }
}
