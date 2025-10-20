package ai.workflow.executor;

import ai.workflow.TaskStatusManager;
import ai.workflow.exception.WorkflowException;
import ai.workflow.pojo.*;
import ai.workflow.utils.NodeExecutorUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 循环节点执行器
 */
public class LoopNodeExecutor implements INodeExecutor {

    private final TaskStatusManager taskStatusManager = TaskStatusManager.getInstance();

    @Override
    public NodeResult execute(String taskId, Node node, WorkflowContext context) throws Exception {
        long startTime = System.currentTimeMillis();
        String nodeId = node.getId();
        taskStatusManager.updateNodeReport(taskId, nodeId, "processing", startTime, null, null, null);

        NodeResult finalNodeResult = null;
        try {
            JsonNode data = node.getData();
            JsonNode batchFor = data.get("batchFor");
            JsonNode batchOutputs = data.get("batchOutputs");
            if (batchFor == null) {
                throw new WorkflowException("循环节点缺少批处理配置");
            }
            Object loopData = resolveLoopData(batchFor, context);
            if (!(loopData instanceof List)) {
                throw new WorkflowException("循环数据必须是数组类型");
            }
            List<?> dataList = (List<?>) loopData;
            List<Object> results = new ArrayList<>();

            for (int i = 0; i < dataList.size(); i++) {
                Object item = dataList.get(i);
                WorkflowContext loopContext = createLoopContext(context, item, i);
                Object loopResult = executeLoopBlocks(taskId, node, loopContext);
                results.add(loopResult);
            }
            Map<String, Object> result = new HashMap<>();
            if (batchOutputs != null) {
                batchOutputs.fields().forEachRemaining(entry -> {
                    String outputKey = entry.getKey();
                    JsonNode outputRef = entry.getValue();

                    if (outputRef.has("type") && "ref".equals(outputRef.get("type").asText())) {
                        JsonNode content = outputRef.get("content");
                        if (content.isArray() && content.size() == 2) {
                            String refNodeId = content.get(0).asText();
                            String field = content.get(1).asText();
                            List<Object> extractedResults = new ArrayList<>();
                            for (Object loopResult : results) {
                                if (loopResult instanceof Map) {
                                    Map<?, ?> resultMap = (Map<?, ?>) loopResult;
                                    if (resultMap.containsKey(refNodeId)) {
                                        Object nodeResult = resultMap.get(refNodeId);
                                        if (nodeResult instanceof Map) {
                                            Object fieldValue = ((Map<?, ?>) nodeResult).get(field);
                                            extractedResults.add(fieldValue);
                                        }
                                    }
                                }
                            }
                            result.put(outputKey, extractedResults);
                        }
                    }
                });
            }
            result.put("results", results);
            result.put("count", dataList.size());
            long endTime = System.currentTimeMillis();
            long timeCost = endTime - startTime;
            TaskReportOutput.Snapshot snapshot = taskStatusManager.createNodeSnapshot(nodeId, result, result, null, null);
            taskStatusManager.updateNodeReport(taskId, nodeId, "succeeded", startTime, endTime, timeCost, snapshot);
            taskStatusManager.addExecutionLog(taskId, nodeId, "循环节点执行成功，处理了 " + dataList.size() + " 个项目", startTime);
            finalNodeResult = new NodeResult(node.getType(), node.getId(), result, null);
        } catch (Exception e) {
            NodeExecutorUtil.handleException(taskId, nodeId, startTime, "循环节点", e);
        }
        return finalNodeResult;
    }

    private Object resolveLoopData(JsonNode batchFor, WorkflowContext context) {
        String type = batchFor.get("type").asText();

        if ("ref".equals(type)) {
            JsonNode content = batchFor.get("content");
            if (content.isArray() && content.size() == 2) {
                String nodeId = content.get(0).asText();
                String field = content.get(1).asText();

                Object nodeResult = context.getNodeResult(nodeId);
                if (nodeResult instanceof Map) {
                    return ((Map<?, ?>) nodeResult).get(field);
                }
            }
        }

        return null;
    }

    private WorkflowContext createLoopContext(WorkflowContext parentContext, Object item, int index) {
        WorkflowContext loopContext = new WorkflowContext(new HashMap<>());

        // 继承父上下文的节点结果
        for (Map.Entry<String, Object> entry : parentContext.getAllNodeResults().entrySet()) {
            loopContext.setNodeResult(entry.getKey(), entry.getValue());
        }

        // 设置循环变量
        loopContext.setVariable("item", item);
        loopContext.setVariable("index", index);

        return loopContext;
    }

    private Object executeLoopBlocks(String taskId, Node loopNode, WorkflowContext loopContext) {
        List<Node> blocks = loopNode.getBlocks();
        List<Edge> blockEdges = loopNode.getBlockEdges();

        if (blocks == null || blocks.isEmpty()) {
            return null;
        }

        // 创建内部工作流
        Workflow innerWorkflow = new Workflow();
        for (Node block : blocks) {
            innerWorkflow.addNode(block);
        }

        if (blockEdges != null) {
            for (Edge edge : blockEdges) {
                innerWorkflow.addEdge(edge);
            }
        }

        // 找到循环内部的开始节点
        Node startBlock = null;
        for (Node block : blocks) {
            if ("block-start".equals(block.getType())) {
                startBlock = block;
                break;
            }
        }

        if (startBlock == null) {
            return null;
        }

        // 执行内部工作流
        try {
            return executeInnerWorkflow(taskId, innerWorkflow, startBlock, loopContext);
        } catch (Exception e) {
            throw new WorkflowException("执行循环内部节点失败: " + e.getMessage());
        }
    }

    private Object executeInnerWorkflow(String taskId, Workflow workflow, Node startNode, WorkflowContext context) {
        // 简化的内部工作流执行逻辑
        // 这里应该实现完整的节点执行逻辑
        Map<String, Object> results = new HashMap<>();

        try {
            // 执行开始节点
            INodeExecutor startExecutor = getNodeExecutor(startNode.getType());
            if (startExecutor != null) {
                NodeResult startResult = startExecutor.execute(taskId, startNode, context);
                results.put(startNode.getId(), startResult.getData());
            }

            // 找到下一个节点并执行
            List<Node> nextNodes = getNextNodes(workflow, startNode);
            for (Node nextNode : nextNodes) {
                if (!"block-end".equals(nextNode.getType())) {
                    INodeExecutor executor = getNodeExecutor(nextNode.getType());
                    if (executor != null) {
                        NodeResult result = executor.execute(taskId, nextNode, context);
                        results.put(nextNode.getId(), result.getData());
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("执行循环内部节点失败: " + e.getMessage());
        }

        return results;
    }

    private INodeExecutor getNodeExecutor(String nodeType) {
        // 这里应该从工作流引擎获取节点执行器
        // 简化实现，直接创建执行器
        switch (nodeType) {
            case "llm":
                return new LLMNodeExecutor();
            case "block-start":
                return new BlockStartNodeExecutor();
            case "block-end":
                return new BlockEndNodeExecutor();
            default:
                return null;
        }
    }

    private List<Node> getNextNodes(Workflow workflow, Node currentNode) {
        List<Node> nextNodes = new ArrayList<>();

        for (Edge edge : workflow.getEdges()) {
            if (edge.getSourceNodeId().equals(currentNode.getId())) {
                Node targetNode = workflow.getNodeById(edge.getTargetNodeId());
                if (targetNode != null) {
                    nextNodes.add(targetNode);
                }
            }
        }

        return nextNodes;
    }

    /**
     * 块开始节点执行器
     */
    private static class BlockStartNodeExecutor implements INodeExecutor {
        @Override
        public NodeResult execute(String taskId, Node node, WorkflowContext context) throws Exception {
            return new NodeResult(node.getType(), node.getId(), null, null);
        }
    }

    /**
     * 块结束节点执行器
     */
    private static class BlockEndNodeExecutor implements INodeExecutor {
        @Override
        public NodeResult execute(String taskId, Node node, WorkflowContext context) throws Exception {
            return new NodeResult(node.getType(), node.getId(), null, null);
        }
    }

    /**
     * 获取输入数据映射
     */
    private Map<String, Object> getInputDataMap(WorkflowContext context) {
        // 由于WorkflowContext没有提供获取所有输入数据的方法，
        // 这里返回一个空的映射，实际实现中可能需要修改WorkflowContext
        return new HashMap<>();
    }
}