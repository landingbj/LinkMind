package ai.workflow.tools;

import ai.openai.pojo.Tool;
import ai.workflow.tools.impl.GenerateEndNodeTool;
import ai.workflow.tools.impl.GenerateStartNodeTool;
import ai.workflow.tools.impl.GetTemplateTool;
import ai.workflow.tools.impl.TemplatesTool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class WorkerFlowToolService {

    private static final Map<String, AgentTool> toolMap = new ConcurrentHashMap<>();

    static {
        registerTool(new TemplatesTool());
        registerTool(new GetTemplateTool());
        registerTool(new GetTemplateTool());
        registerTool(new GenerateStartNodeTool());
        registerTool(new GenerateEndNodeTool());
    }

    public static void registerTool(AgentTool tool) {
        toolMap.put(tool.getName(), tool);
    }

    public static List<Tool> getTools() {
        return toolMap.values().stream().filter(AgentTool::isValid).map(AgentTool::getTool).collect(Collectors.toList());
    }

    public static String invokeTool(String toolName, String args) {
        return toolMap.get(toolName).invoke(args);
    }

}
