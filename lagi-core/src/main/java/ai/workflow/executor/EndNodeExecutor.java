package ai.workflow.executor;

import ai.workflow.TaskStatusManager;
import ai.workflow.pojo.Node;
import ai.workflow.pojo.NodeResult;
import ai.workflow.pojo.TaskReportOutput;
import ai.workflow.pojo.WorkflowContext;
import ai.workflow.utils.InputValueParser;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * 结束节点执行器
 * 注意：结束节点不立即更新状态，由工作流引擎在确认整个工作流完成后统一更新
 */
@Slf4j
public class EndNodeExecutor implements INodeExecutor {

    private final TaskStatusManager taskStatusManager = TaskStatusManager.getInstance();
    
    @Override
    public List<String> getStandardOutputFields() {
        // end 节点没有输出字段，不需要验证
        return null;
    }

    @Override
    public NodeResult execute(String taskId, Node node, WorkflowContext context) throws Exception {
        String nodeId = node.getId();
        long startTime = System.currentTimeMillis();
        
        try {
            JsonNode data = node.getData();
            JsonNode inputsValues = data.get("inputsValues");
            Map<String, Object> inputs = InputValueParser.parseInputs(inputsValues, context);
            Map<String, Object> result = inputs;
            long endTime = System.currentTimeMillis();
            long timeCost = endTime - startTime;
            
            TaskReportOutput.Snapshot snapshot = taskStatusManager.createNodeSnapshot(nodeId, result, result, null, null);
            
            // 将状态更新信息保存到上下文中，供工作流引擎后续处理
            context.setVariable("end_node_" + nodeId + "_start_time", startTime);
            context.setVariable("end_node_" + nodeId + "_end_time", endTime);
            context.setVariable("end_node_" + nodeId + "_time_cost", timeCost);
            context.setVariable("end_node_" + nodeId + "_snapshot", snapshot);
            context.setVariable("end_node_" + nodeId + "_result", result);
            context.setVariable("end_node_" + nodeId + "_result_size", result.size());
            
            return new NodeResult(node.getType(), node.getId(),result, null);
            
        } catch (Exception e) {
            // 记录错误但不立即更新状态
            long endTime = System.currentTimeMillis();
            long timeCost = endTime - startTime;
            
            context.setVariable("end_node_" + nodeId + "_error", e.getMessage());
            context.setVariable("end_node_" + nodeId + "_start_time", startTime);
            context.setVariable("end_node_" + nodeId + "_end_time", endTime);
            context.setVariable("end_node_" + nodeId + "_time_cost", timeCost);
            throw e;
        }
    }
    
    /**
     * 更新结束节点状态（由工作流引擎调用）
     */
    public void updateEndNodeStatus(String taskId, String nodeId, WorkflowContext context) {
        try {
            Long startTime = (Long) context.getVariable("end_node_" + nodeId + "_start_time");
            Long endTime = (Long) context.getVariable("end_node_" + nodeId + "_end_time");
            Long timeCost = (Long) context.getVariable("end_node_" + nodeId + "_time_cost");
            TaskReportOutput.Snapshot snapshot = (TaskReportOutput.Snapshot) context.getVariable("end_node_" + nodeId + "_snapshot");
            Integer resultSize = (Integer) context.getVariable("end_node_" + nodeId + "_result_size");
            String error = (String) context.getVariable("end_node_" + nodeId + "_error");
            
            if (error != null) {
                // 更新为失败状态
                taskStatusManager.updateNodeStatus(taskId, nodeId, "failed");
                taskStatusManager.updateNodeReport(taskId, nodeId, "failed", startTime, endTime, timeCost, snapshot);
                taskStatusManager.addErrorLog(taskId, nodeId, "结束节点执行失败: " + error, endTime);
            } else {
                // 更新为成功状态
                taskStatusManager.updateNodeStatus(taskId, nodeId, "succeeded");
                taskStatusManager.updateNodeReport(taskId, nodeId, "succeeded", startTime, endTime, timeCost, snapshot);
                taskStatusManager.addExecutionLog(taskId, nodeId, "结束节点执行成功，输出字段数: " + resultSize, startTime);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }
}