package ai.workflow.tools.impl;

import ai.openai.pojo.Tool;
import ai.workflow.tools.AgentTool;


public class NodesTool implements AgentTool {

    @Override
    public String getName() {
        return "nodes";
    }

    @Override
    public Tool getTool() {
        return null;
    }

    @Override
    public String invoke(String jsonStr) {
        return "";
    }
}
