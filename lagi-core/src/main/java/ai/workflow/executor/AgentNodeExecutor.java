package ai.workflow.executor;

import ai.agent.AgentService;
import ai.llm.service.CompletionsService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.router.pojo.LLmRequest;
import ai.utils.qa.ChatCompletionUtil;
import ai.worker.DefaultWorker;
import ai.workflow.TaskStatusManager;
import ai.workflow.exception.WorkflowException;
import ai.workflow.pojo.Node;
import ai.workflow.pojo.NodeResult;
import ai.workflow.pojo.TaskReportOutput;
import ai.workflow.pojo.WorkflowContext;
import ai.workflow.utils.InputValueParser;
import ai.workflow.utils.NodeExecutorUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

/**
 * 大语言模型节点执行器
 */
public class AgentNodeExecutor implements INodeExecutor {

    private final TaskStatusManager taskStatusManager = TaskStatusManager.getInstance();
    private final CompletionsService completionsService = new CompletionsService();
    private final AgentService agentService = new AgentService();


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
                throw new WorkflowException("agent节点缺少输入配置");
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
            taskStatusManager.addExecutionLog(taskId, nodeId, "agent节点执行成功，生成长度: " + result.length(), startTime);
            nodeResult =  new NodeResult(node.getType(), node.getId(), output, null);

        } catch (Exception e) {
            NodeExecutorUtil.handleException(taskId, nodeId, startTime, "agent节点", e);
        } return nodeResult;
    }

    private String callAgent(Map<String, Object> inputs) {
        String prompt = (String) inputs.get("query");
        String agentId = (String) inputs.get("agent");
        ChatCompletionRequest request = completionsService.getCompletionsRequest(prompt);
        Gson gson = new Gson();
        String json = gson.toJson(request);
        LLmRequest lLmRequest = gson.fromJson(json, LLmRequest.class);
        lLmRequest.setAgentId(Integer.parseInt(agentId));
        DefaultWorker defaultWorker = new DefaultWorker();
        ChatCompletionResult work = null;
        try {
            work = defaultWorker.work("appointedWorker", agentService.getAllAgents(), lLmRequest);
        } catch (Exception e) {
            throw new WorkflowException("agent节点调用失败, 原因:查询agent失败");
        }
        if(work != null) {
            return ChatCompletionUtil.getFirstAnswer(work);
        }
        throw new WorkflowException("agent节点调用失败");
    }
}