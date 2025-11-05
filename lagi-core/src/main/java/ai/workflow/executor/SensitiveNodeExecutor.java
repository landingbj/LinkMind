package ai.workflow.executor;

import ai.utils.SensitiveWordUtil;
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
 * 敏感词过滤节点执行器
 */
public class SensitiveNodeExecutor implements INodeExecutor {

    private final TaskStatusManager taskStatusManager = TaskStatusManager.getInstance();

    @Override
    public boolean isValid() {
        // 敏感词过滤功能始终有效
        return true;
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
                throw new WorkflowException("敏感词过滤节点缺少输入配置");
            }

            // 解析输入值
            Map<String, Object> inputs = InputValueParser.parseInputs(inputsValues, context);

            // 执行敏感词过滤
            String result = callSensitiveFilter(inputs);

            Map<String, Object> output = new HashMap<>();
            output.put("result", result);

            long endTime = System.currentTimeMillis();
            long timeCost = endTime - startTime;
            TaskReportOutput.Snapshot nodeSnapshot = taskStatusManager.createNodeSnapshot(nodeId, inputs, output, null, null);
            taskStatusManager.updateNodeReport(taskId, nodeId, "succeeded", startTime, endTime, timeCost, nodeSnapshot);
            taskStatusManager.addExecutionLog(taskId, nodeId, "敏感词过滤节点执行成功", startTime);
            nodeResult = new NodeResult(node.getType(), node.getId(), output, null);

        } catch (Exception e) {
            NodeExecutorUtil.handleException(taskId, nodeId, startTime, "敏感词过滤节点", e);
        }

        return nodeResult;
    }

    private String callSensitiveFilter(Map<String, Object> inputs) {
        String text = (String) inputs.get("text");

        if (text == null || text.isEmpty()) {
            throw new RuntimeException("敏感词过滤缺少必需的文本参数");
        }

        // 执行敏感词过滤
        return SensitiveWordUtil.filter(text);
    }
}
