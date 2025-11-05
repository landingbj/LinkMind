package ai.workflow.tools.impl;

import ai.openai.pojo.Tool;
import ai.workflow.tools.AgentTool;


public class GetTemplateTool implements AgentTool {

    @Override
    public String getName() {
        return "get_template";
    }

    @Override
    public Tool getTool() {
        return null;
    }

    @Override
    public String invoke(String jsonStr) {
        return "无对应模版";
    }
}
