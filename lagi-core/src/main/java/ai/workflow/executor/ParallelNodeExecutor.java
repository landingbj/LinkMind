package ai.workflow.executor;

import ai.common.utils.ThreadPoolManager;
import ai.workflow.TaskStatusManager;
import ai.workflow.exception.WorkflowException;
import ai.workflow.pojo.*;
import ai.workflow.utils.DefaultNodeEnum;
import ai.workflow.utils.NodeExecutorUtil;

import java.util.*;
import java.util.concurrent.*;

public class ParallelNodeExecutor implements INodeExecutor {

    private final TaskStatusManager taskStatusManager = TaskStatusManager.getInstance();
    private final ExecutorService executorService;

    public ParallelNodeExecutor() {
        ThreadPoolManager.registerExecutor("parallel-node-executor");
        executorService = ThreadPoolManager.getExecutor("parallel-node-executor");
    }

    @Override
    public NodeResult execute(String taskId, Node node, WorkflowContext context) throws Exception {
        long startTime = System.currentTimeMillis();
        String nodeId = node.getId();
        taskStatusManager.updateNodeReport(taskId, nodeId, "processing", startTime, null, null, null);

        NodeResult finalNodeResult = null;
        try {
            List<Node> blocks = node.getBlocks();
            List<Edge> blockEdges = node.getBlockEdges();

            if (blocks == null || blocks.isEmpty()) {
                throw new WorkflowException("并行节点缺少块配置");
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

            // 查找唯一的 block-start 节点
            Node startBlock = findSingleNodeByType(blocks, "block-start");
            if (startBlock == null) {
                throw new WorkflowException("并行节点缺少开始块");
            }

            // 获取所有从 block-start 连接出去的节点（即各个并行分支）
            List<Node> nextNodes = getNextNodes(innerWorkflow, startBlock);

            // 并行执行这些节点
            List<Future<Map<String, Object>>> futures = new ArrayList<>();
            for (Node branchNode : nextNodes) {
                Future<Map<String, Object>> future = executorService.submit(() -> {
                    WorkflowContext parallelContext = new WorkflowContext(new ConcurrentHashMap<>());
                    for (Map.Entry<String, Object> entry : context.getAllNodeResults().entrySet()) {
                        parallelContext.setNodeResult(entry.getKey(), entry.getValue());
                    }
                    return executeBranch(taskId, innerWorkflow, branchNode, parallelContext);
                });
                futures.add(future);
            }

            // 收集所有结果
            List<Object> results = new ArrayList<>();
            for (Future<Map<String, Object>> future : futures) {
                results.add(future.get());
            }

            Map<String, Object> result = new HashMap<>();
            result.put("parallelResults", results);

            long endTime = System.currentTimeMillis();
            long timeCost = endTime - startTime;
            TaskReportOutput.Snapshot snapshot = taskStatusManager.createNodeSnapshot(nodeId, result, result, null, null);
            taskStatusManager.updateNodeReport(taskId, nodeId, "succeeded", startTime, endTime, timeCost, snapshot);
            taskStatusManager.addExecutionLog(taskId, nodeId, "并行节点执行成功，处理了 " + results.size() + " 个分支", startTime);
            finalNodeResult = new NodeResult(node.getType(), node.getId(), result, null);
        } catch (Exception e) {
            NodeExecutorUtil.handleException(taskId, nodeId, startTime, "并行节点", e);
        }
        return finalNodeResult;
    }

    /**
     * 执行单个并行分支
     */
    private Map<String, Object> executeBranch(String taskId, Workflow workflow, Node startNode, WorkflowContext context) {
        Map<String, Object> results = new HashMap<>();

        try {
            // 执行起始节点
            INodeExecutor startExecutor = getNodeExecutor(startNode.getType());
            if (startExecutor != null) {
                NodeResult startResult = startExecutor.execute(taskId, startNode, context);
                if (startResult != null && startResult.getData() != null) {
                    results.put(startNode.getId(), startResult.getData());
                }
            }

            // 沿着路径继续执行后续节点直到 block-end
            Node currentNode = startNode;
            while (true) {
                List<Node> nextNodes = getNextNodes(workflow, currentNode);
                if (nextNodes.isEmpty()) break;

                Node nextNode = nextNodes.get(0); // 假设每个节点只有一个输出
                if ("block-end".equals(nextNode.getType())) {
                    break; // 到达终点
                }

                INodeExecutor executor = getNodeExecutor(nextNode.getType());
                if (executor != null) {
                    NodeResult result = executor.execute(taskId, nextNode, context);
                    if (result != null && result.getData() != null) {
                        results.put(nextNode.getId(), result.getData());
                    }
                }

                currentNode = nextNode;
            }

        } catch (Exception e) {
            System.err.println("执行并行分支失败: " + e.getMessage());
        }

        return results;
    }

    private INodeExecutor getNodeExecutor(String nodeType) {
        switch (nodeType) {
            case "block-start":
                return new BlockStartNodeExecutor();
            case "block-end":
                return new BlockEndNodeExecutor();
            default:
                return DefaultNodeEnum.getValidNodeExecutor(nodeType);
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

    private Node findSingleNodeByType(List<Node> nodes, String type) {
        for (Node node : nodes) {
            if (type.equals(node.getType())) {
                return node;
            }
        }
        return null;
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
}
