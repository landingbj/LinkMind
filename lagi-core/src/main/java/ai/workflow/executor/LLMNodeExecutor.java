package ai.workflow.executor;

import ai.llm.service.CompletionsService;
import ai.manager.LlmManager;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
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
public class LLMNodeExecutor implements INodeExecutor {

    private final TaskStatusManager taskStatusManager = TaskStatusManager.getInstance();
    private final CompletionsService completionsService = new CompletionsService();

    @Override
    public boolean isValid() {
        boolean empty = LlmManager.getInstance().getAdapters().isEmpty();
        return !empty;
    }

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
                throw new WorkflowException("LLM节点缺少输入配置");
            }
            // 解析输入值
            Map<String, Object> inputs = InputValueParser.parseInputs(inputsValues, context);
            // 模拟LLM调用
            String result = callLLM(inputs);
            Map<String, Object> output = new HashMap<>();
            output.put("result", result);

            long endTime = System.currentTimeMillis();
            long timeCost = endTime - startTime;
            TaskReportOutput.Snapshot snapshot = taskStatusManager.createNodeSnapshot(nodeId, output, output, null, null);
            taskStatusManager.updateNodeReport(taskId, nodeId, "succeeded", startTime, endTime, timeCost, snapshot);
            taskStatusManager.addExecutionLog(taskId, nodeId, "LLM节点执行成功，生成长度: " + result.length(), startTime);
            nodeResult =  new NodeResult(output, null);

        } catch (Exception e) {
            NodeExecutorUtil.handleException(taskId, nodeId, startTime, "LLM节点", e);
        } return nodeResult;
    }

    private String callLLM(Map<String, Object> inputs) {
        String prompt = (String) inputs.get("prompt");
        String model = (String) inputs.get("model");
        ChatCompletionRequest request = completionsService.getCompletionsRequest(prompt);
        request.setModel(model);
        ChatCompletionResult response = completionsService.completions(request);
        return response.getChoices().get(0).getMessage().getContent();
    }
}