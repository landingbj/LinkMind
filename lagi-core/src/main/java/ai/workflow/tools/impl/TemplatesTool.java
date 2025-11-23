package ai.workflow.tools.impl;

import ai.openai.pojo.Tool;
import ai.workflow.tools.AgentTool;


public class TemplatesTool implements AgentTool {

    @Override
    public String getName() {
        return "templates";
    }

    @Override
    public Tool getTool() {
        return null;
    }

    @Override
    public String invoke(String jsonStr) {
        return "模板库无数据";
    }
}
