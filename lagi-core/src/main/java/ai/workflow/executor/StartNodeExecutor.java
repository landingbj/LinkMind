package ai.workflow.executor;

import ai.workflow.TaskStatusManager;
import ai.workflow.pojo.Node;
import ai.workflow.pojo.NodeResult;
import ai.workflow.pojo.TaskReportOutput;
import ai.workflow.pojo.WorkflowContext;
import ai.workflow.utils.NodeExecutorUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

/**
 * 开始节点执行器
 */
public class StartNodeExecutor implements INodeExecutor {
    private final TaskStatusManager taskStatusManager = TaskStatusManager.getInstance();

    @Override
    public NodeResult execute(String taskId, Node node, WorkflowContext context) throws Exception {
        long startTime = System.currentTimeMillis();
        String nodeId = node.getId();
        taskStatusManager.updateNodeReport(taskId, nodeId, "processing", startTime, null, null,null);
        NodeResult nodeResult = null;
        try {
            JsonNode data = node.getData();
            JsonNode outputs = data.get("outputs");
            Map<String, Object> result = new HashMap<>();
            if (outputs != null) {
                JsonNode properties = outputs.get("properties");
                if (properties != null) {
                    properties.fields().forEachRemaining(entry -> {
                        String key = entry.getKey();
                        JsonNode property = entry.getValue();

                        Object inputValue = context.getInputData(key);
                        if (inputValue != null) {
                            result.put(key, inputValue);
                        } else if (property.has("default")) {
                            JsonNode defaultValue = property.get("default");
                            if (defaultValue.isTextual()) {
                                result.put(key, defaultValue.asText());
                            } else if (defaultValue.isBoolean()) {
                                result.put(key, defaultValue.asBoolean());
                            } else if (defaultValue.isNumber()) {
                                result.put(key, defaultValue.asDouble());
                            }
                        }
                    });
                }
            }
            NodeExecutorUtil.sleep();
            long endTime = System.currentTimeMillis();
            long timeCost = endTime - startTime;
            TaskReportOutput.Snapshot snapshot = taskStatusManager.createNodeSnapshot(nodeId, result, result, null, null);
            taskStatusManager.updateNodeReport(taskId, nodeId, "succeeded", startTime, endTime, timeCost, snapshot);
            taskStatusManager.addExecutionLog(taskId, nodeId, "开始节点执行成功", startTime);
            nodeResult= new NodeResult(node.getType(), node.getId(),result, null);
        } catch (Exception e) {
            NodeExecutorUtil.handleException(taskId, nodeId, startTime, "开始节点", e);
        } return nodeResult;
    }
}