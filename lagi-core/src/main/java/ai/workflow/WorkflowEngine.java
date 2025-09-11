package ai.workflow;

import ai.llm.service.CompletionsService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.utils.JsonExtractor;
import ai.workflow.exception.WorkflowException;
import ai.workflow.executor.*;
import ai.workflow.pojo.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 工作流引擎主类
 */
@Slf4j
public class WorkflowEngine {
    private final ObjectMapper objectMapper;
    private final Map<String, INodeExecutor> nodeExecutors;
    private final TaskStatusManager taskStatusManager;

    private static final ThreadPoolExecutor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(
            10, 20, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

    public WorkflowEngine() {
        this.objectMapper = new ObjectMapper();
        this.nodeExecutors = new HashMap<>();
        this.taskStatusManager = TaskStatusManager.getInstance();
        // 注册默认节点执行器
        registerDefaultExecutors();
    }

    private void registerDefaultExecutors() {
        nodeExecutors.put("start", new StartNodeExecutor());
        nodeExecutors.put("condition", new ConditionNodeExecutor());
        nodeExecutors.put("loop", new LoopNodeExecutor());
        nodeExecutors.put("llm", new LLMNodeExecutor());
        nodeExecutors.put("end", new EndNodeExecutor());
        nodeExecutors.put("knowledge-base", new KnowledgeNodeExecutor());
        nodeExecutors.put("intent-recognition", new IntentNodeExecutor());
        nodeExecutors.put("program", new GroovyScriptNodeExecutor());
    }

    public void executeAsync(String taskId, String workflowJson, Map<String, Object> inputData) {
        THREAD_POOL_EXECUTOR.execute(() -> {
            try {
                WorkflowResult result = execute(taskId, workflowJson, inputData);
                if (result.isSuccess()) {
                    taskStatusManager.updateTaskStatus(taskId, "succeeded");
                } else {
                    taskStatusManager.updateTaskStatus(taskId, "failed");
                }
            } catch (Exception e) {
                taskStatusManager.updateTaskStatus(taskId, "failed");
                System.err.println("Workflow execution failed: " + e.getMessage());
            }
        });
    }

    /**
     * 执行工作流
     */
    public WorkflowResult execute(String taskId, String workflowJson, Map<String, Object> inputData) {
        try {
            // 创建初始任务报告
            TaskReportOutput initialTaskReport = taskStatusManager.createInitialTaskReport(taskId, workflowJson, inputData);
            taskStatusManager.createTask(initialTaskReport);

            JsonNode workflowConfig = objectMapper.readTree(workflowJson);
            WorkflowContext context = new WorkflowContext(inputData);
            // 解析节点和边
            Workflow workflow = parseWorkflow(workflowConfig);
            // 找到开始节点
            Node startNode = workflow.getStartNode();
            if (startNode == null) {
                throw new WorkflowException("没有找到开始节点");
            }
            // 执行工作流
            WorkflowResult result = executeWorkflow(taskId, workflow, startNode, context);
            if(result.isSuccess()) {
                updateEndNodesStatus(taskId, workflow, context);
            }
            return result;
        } catch (Exception e) {
            taskStatusManager.updateTaskStatus(taskId, "failed");
            return new WorkflowResult(false, null, e.getMessage());
        }
    }

    private Workflow parseWorkflow(JsonNode config) {
        Workflow workflow = new Workflow();

        // 解析节点
        JsonNode nodes = config.get("nodes");
        if (nodes != null && nodes.isArray()) {
            for (JsonNode nodeJson : nodes) {
                Node node = parseNode(nodeJson);
                workflow.addNode(node);
            }
        }

        // 解析边
        JsonNode edges = config.get("edges");
        if (edges != null && edges.isArray()) {
            for (JsonNode edgeJson : edges) {
                Edge edge = parseEdge(edgeJson);
                workflow.addEdge(edge);
            }
        }

        return workflow;
    }

    private Node parseNode(JsonNode nodeJson) {
        String id = nodeJson.get("id").asText();
        String type = nodeJson.get("type").asText();
        JsonNode data = nodeJson.get("data");
        JsonNode meta = nodeJson.get("meta");

        Node node = new Node(id, type);
        node.setData(data);
        node.setMeta(meta);

        // 解析循环节点的内部块
        if ("loop".equals(type)) {
            JsonNode blocks = nodeJson.get("blocks");
            if (blocks != null && blocks.isArray()) {
                List<Node> blockNodes = new ArrayList<>();
                for (JsonNode blockJson : blocks) {
                    blockNodes.add(parseNode(blockJson));
                }
                node.setBlocks(blockNodes);
            }

            JsonNode blockEdges = nodeJson.get("edges");
            if (blockEdges != null && blockEdges.isArray()) {
                List<Edge> edges = new ArrayList<>();
                for (JsonNode edgeJson : blockEdges) {
                    edges.add(parseEdge(edgeJson));
                }
                node.setBlockEdges(edges);
            }
        }

        return node;
    }

    private Edge parseEdge(JsonNode edgeJson) {
        String sourceNodeId = edgeJson.get("sourceNodeID").asText();
        String targetNodeId = edgeJson.get("targetNodeID").asText();
        String sourcePortId = edgeJson.has("sourcePortID") ?
                edgeJson.get("sourcePortID").asText() : null;

        return new Edge(sourceNodeId, targetNodeId, sourcePortId);
    }

    private WorkflowResult executeWorkflow(String taskId, Workflow workflow, Node currentNode, WorkflowContext context) {
        Set<String> visitedNodes = new HashSet<>();
        return executeNode(taskId, workflow, currentNode, context, visitedNodes);
    }

    private WorkflowResult executeNode(String taskId, Workflow workflow, Node node, WorkflowContext context, Set<String> visitedNodes) {
        // 防止无限循环
        if (visitedNodes.contains(node.getId()) && !"loop".equals(node.getType())) {
            throw new WorkflowException("检测到循环依赖: " + node.getId());
        }

        visitedNodes.add(node.getId());

        try {
            // 获取节点执行器
            INodeExecutor executor = nodeExecutors.get(node.getType());
            if (executor == null) {
                throw new WorkflowException("不支持的节点类型: " + node.getType());
            }

            // 执行节点
            NodeResult result = executor.execute(taskId, node, context);

            // 将结果保存到上下文
            context.setNodeResult(node.getId(), result.getData());

            // 如果是结束节点，返回结果但不立即更新状态
            if ("end".equals(node.getType())) {
                return new WorkflowResult(true, result.getData(), null);
            }

            // 找到下一个要执行的节点
            List<Node> nextNodes = getNextNodes(workflow, node, result);

            if (nextNodes.isEmpty()) {
                return new WorkflowResult(false, null, "没有找到下一个节点");
            }

            // 执行所有下一个节点
            List<WorkflowResult> subResults = new ArrayList<>();
            boolean hasSuccess = false;

            for (Node nextNode : nextNodes) {
                try {
                    WorkflowResult nextResult = executeNode(taskId, workflow, nextNode, context, new HashSet<>(visitedNodes));
                    subResults.add(nextResult);
                    if (nextResult.isSuccess()) {
                        hasSuccess = true;
                    }
                } catch (Exception e) {
                    WorkflowResult errorResult = new WorkflowResult(false, null, "执行节点失败: " + e.getMessage());
                    subResults.add(errorResult);
                }
            }

            // 如果有任何子节点成功，则整体成功
            if (hasSuccess) {
                return new WorkflowResult(true, result.getData(), null, subResults);
            } else {
                return new WorkflowResult(false, null, "所有后续节点执行失败", subResults);
            }

        } catch (Exception e) {
            return new WorkflowResult(false, null, "执行节点失败: " + e.getMessage());
        }
    }

    private List<Node> getNextNodes(Workflow workflow, Node currentNode, NodeResult result) {
        List<Node> nextNodes = new ArrayList<>();

        for (Edge edge : workflow.getEdges()) {
            if (edge.getSourceNodeId().equals(currentNode.getId())) {
                // 检查端口匹配
                if (edge.getSourcePortId() != null) {
                    List<String> outputPorts = result.getOutputPorts();
                    if (outputPorts == null || !outputPorts.contains(edge.getSourcePortId())) {
                        continue;
                    }
                }

                Node targetNode = workflow.getNodeById(edge.getTargetNodeId());
                if (targetNode != null) {
                    nextNodes.add(targetNode);
                }
            }
        }
        return nextNodes;
    }
    
    /**
     * 更新所有结束节点的状态
     * 在工作流执行完成后调用
     */
    private void updateEndNodesStatus(String taskId, Workflow workflow, WorkflowContext context) {
        try {
            // 找到所有结束节点
            List<Node> endNodes = new ArrayList<>();
            for (Node node : workflow.getAllNodes()) {
                if ("end".equals(node.getType())) {
                    endNodes.add(node);
                }
            }
            
            // 更新每个结束节点的状态
            EndNodeExecutor endExecutor = (EndNodeExecutor) nodeExecutors.get("end");
            for (Node endNode : endNodes) {
                endExecutor.updateEndNodeStatus(taskId, endNode.getId(), context);
            }
        } catch (Exception e) {
            log.error("Failed to update end nodes status: {}", e.getMessage());
        }
    }

    public static String txt2FlowSchema(String text) {
        CompletionsService completionsService = new CompletionsService();
//        ChatCompletionRequest request = completionsService.getCompletionsRequest(Prompt.USER_INFO_EXTRACT_PROMPT, text, "default");
//        ChatCompletionResult completions = completionsService.completions(request);
//        String prompt = completions.getChoices().get(0).getMessage().getContent();
//        System.out.println(prompt);

        ChatCompletionRequest request = completionsService.getCompletionsRequest(Prompt.PROMPT_TO_WORKFLOW_JSON, text, "default", 4096);
        ChatCompletionResult completions = completionsService.completions(request);
        String json = completions.getChoices().get(0).getMessage().getContent();
        System.out.println(json);
        json = JsonExtractor.extractJson(json);
        return json;
    }

}