package ai.workflow;

import ai.config.ContextLoader;
import ai.utils.JsonExtractor;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Test cases for WorkflowGenerator
 */
public class WorkflowGeneratorTest {
    @Before
    public void setUp() {
        ContextLoader.loadContext();
    }

    @Test
    public void testConditionalFlow() {
        String input = "首先识别用户意图。如果是技术问题，搜索知识库。如果是闲聊，直接使用LLM回复";
        String json = WorkflowGenerator.txt2FlowSchema(input);
        json = JsonExtractor.removeSpaces(json);

        assertNotNull("Generated JSON should not be null", json);
        assertTrue("JSON should contain condition node", json.contains("\"type\":\"condition\""));
        assertTrue("JSON should contain sourcePortID in edges", json.contains("sourcePortID"));
    }

    @Test
    public void testLoopFlow() {
        String input = "遍历文档列表，总结每个文档，然后汇总所有摘要";
        String json = WorkflowGenerator.txt2FlowSchema(input);
        json = JsonExtractor.removeSpaces(json);

        assertNotNull("Generated JSON should not be null", json);
        assertTrue("JSON should contain loop node", json.contains("\"type\":\"loop\""));
        assertTrue("JSON should contain blocks", json.contains("\"blocks\""));
    }
}

