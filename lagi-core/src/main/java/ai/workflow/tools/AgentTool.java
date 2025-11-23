package ai.workflow.tools;

import ai.openai.pojo.Tool;

public interface AgentTool {
    String getName();
    Tool getTool();
    String invoke(String jsonStr);
    default boolean isValid(){
        return true;
    };
}
