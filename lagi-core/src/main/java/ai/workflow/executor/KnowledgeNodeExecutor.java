package ai.workflow.executor;

import ai.common.pojo.IndexSearchData;
import ai.config.ContextLoader;
import ai.config.GlobalConfigurations;
import ai.config.pojo.StoreConfig;
import ai.llm.pojo.GetRagContext;
import ai.llm.service.CompletionsService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.vector.VectorStoreService;
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
import java.util.List;
import java.util.Map;

/**
 * 知识库节点执行器
 */
public class KnowledgeNodeExecutor implements INodeExecutor {
    private final TaskStatusManager taskStatusManager = TaskStatusManager.getInstance();
    private final VectorStoreService vectorStoreService = new VectorStoreService();
    private final CompletionsService completionsService = new CompletionsService();


    @Override
    public boolean isValid() {
        GlobalConfigurations configuration = ContextLoader.configuration;
        if(configuration != null && configuration.getStores() != null ) {
            StoreConfig stores = configuration.getStores();
            return stores.getRag().getEnable();
        }
        return false;
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
                throw new WorkflowException("知识库节点缺少输入配置");
            }
            Map<String, Object> inputs = InputValueParser.parseInputs(inputsValues, context);

            // 执行知识库查询
            String result = performKnowledgeBaseSearch(inputs);

            // 构建输出结果
            Map<String, Object> outputResult = new HashMap<>();
            if (result != null) {
                outputResult.put("result", result);
            }
            long endTime = System.currentTimeMillis();
            long timeCost = endTime - startTime;

            TaskReportOutput.Snapshot snapshot = taskStatusManager.createNodeSnapshot(nodeId, inputs, outputResult, null, null);
            taskStatusManager.updateNodeReport(taskId, nodeId, "succeeded", startTime, endTime, timeCost, snapshot);
            taskStatusManager.addExecutionLog(taskId, nodeId, "知识库节点执行成功，查询: " + inputs, startTime);
            nodeResult = new NodeResult(outputResult, null);
        } catch (Exception e) {
            NodeExecutorUtil.handleException(taskId, nodeId, startTime, "知识库节点", e);
        }
        return nodeResult;
    }

    /**
     * 执行知识库搜索
     */
    private String performKnowledgeBaseSearch(Map<String, Object> inputs) {
        String query = (String) inputs.get("query");
        String category = (String) inputs.get("category");
        ChatCompletionRequest request = completionsService.getCompletionsRequest(query);
        request.setCategory(category);
        List<IndexSearchData> indexSearchDataList = vectorStoreService.search(request);
        GetRagContext ragContext = completionsService.getRagContext(indexSearchDataList);
        if (ragContext == null) {
            return null;
        }
        return ragContext.getContext();
    }
} 