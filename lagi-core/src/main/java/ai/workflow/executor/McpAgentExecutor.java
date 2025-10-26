package ai.workflow.executor;

import ai.agent.mcp.IMcpAgent;
import ai.agent.mcp.McpAgent;
import ai.config.pojo.AgentConfig;
import ai.llm.service.CompletionsService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.utils.qa.ChatCompletionUtil;
import ai.workflow.TaskStatusManager;
import ai.workflow.exception.WorkflowException;
import ai.workflow.pojo.Node;
import ai.workflow.pojo.NodeResult;
import ai.workflow.pojo.TaskReportOutput;
import ai.workflow.pojo.WorkflowContext;
import ai.workflow.utils.InputValueParser;
import ai.workflow.utils.NodeExecutorUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

/**
 * 大语言模型节点执行器
 */
public class McpAgentExecutor implements INodeExecutor {

    private final TaskStatusManager taskStatusManager = TaskStatusManager.getInstance();
    private final CompletionsService completionsService = new CompletionsService();


    @Override
    public NodeResult execute(String taskId, Node node, WorkflowContext context) throws Exception {
        long startTime = System.currentTimeMillis();
        String nodeId = node.getId();
        taskStatusManager.updateNodeReport(taskId, nodeId, "processing", startTime, null, null, null);

        NodeResult nodeResult = null;
        try {
            JsonNode data = node.getData();
            JsonNode inputsValues = data.get("inputsValues");

            if (inputsValues == null) {
                throw new WorkflowException("mcp agent节点缺少输入配置");
            }
            // 解析输入值
            Map<String, Object> inputs = InputValueParser.parseInputs(inputsValues, context);
            // 模拟agent调用
            String result = callAgent(inputs);
            Map<String, Object> output = new HashMap<>();
            output.put("result", result);

            long endTime = System.currentTimeMillis();
            long timeCost = endTime - startTime;
            TaskReportOutput.Snapshot snapshot = taskStatusManager.createNodeSnapshot(nodeId, output, output, null, null);
            taskStatusManager.updateNodeReport(taskId, nodeId, "succeeded", startTime, endTime, timeCost, snapshot);
            taskStatusManager.addExecutionLog(taskId, nodeId, "mcp agent节点执行成功，生成长度: " + result.length(), startTime);
            nodeResult =  new NodeResult(node.getType(), node.getId(), output, null);

        } catch (Exception e) {
            NodeExecutorUtil.handleException(taskId, nodeId, startTime, "mcp agent节点", e);
        } return nodeResult;
    }

    private String callAgent(Map<String, Object> inputs) {
        String prompt = (String) inputs.get("query");
        if (prompt == null) {
            throw new WorkflowException("请输入用户请求");
        }
        String mcp = (String) inputs.get("mcp");
        if (mcp == null) {
            throw new WorkflowException("请配置mcp 地址");
        }
        ChatCompletionRequest request = completionsService.getCompletionsRequest(prompt);
        request.setMax_tokens(4096);
        IMcpAgent mcpAgent = new IMcpAgent(mcp);
        ChatCompletionResult communicate = mcpAgent.communicate(request);
        if (communicate != null) {
            return ChatCompletionUtil.getFirstAnswer(communicate);
        }
        throw new WorkflowException("agent节点调用失败");
    }
}