package ai.workflow.executor;

import ai.intent.IntentService;
import ai.intent.impl.SampleIntentServiceImpl;
import ai.intent.pojo.IntentResult;
import ai.llm.service.CompletionsService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.workflow.TaskStatusManager;
import ai.workflow.exception.WorkflowException;
import ai.workflow.pojo.Node;
import ai.workflow.pojo.NodeResult;
import ai.workflow.pojo.TaskReportOutput;
import ai.workflow.pojo.WorkflowContext;
import ai.workflow.utils.DefaultNodeEnum;
import ai.workflow.utils.InputValueParser;
import ai.workflow.utils.NodeExecutorUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 意图识别节点执行器
 */
public class IntentNodeExecutor implements INodeExecutor {
    private final TaskStatusManager taskStatusManager = TaskStatusManager.getInstance();
    private final IntentService sampleIntentService = new SampleIntentServiceImpl();
    private final CompletionsService completionsService = new CompletionsService();
    
    @Override
    public List<String> getStandardOutputFields() {
        // intent-recognition 节点输出 "intent" 字段
        return Collections.singletonList("intent");
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
                throw new WorkflowException("意图识别节点缺少输入配置");
            }
            Map<String, Object> inputs = InputValueParser.parseInputs(inputsValues, context);
            // 执行意图识别
            String intent = performIntentRecognition(inputs);
            // 构建输出结果
            Map<String, Object> result = new HashMap<>();
            result.put("intent", intent);
            long endTime = System.currentTimeMillis();
            long timeCost = endTime - startTime;
            Map<String, Object> inputData = getInputDataMap(context);
            TaskReportOutput.Snapshot snapshot = taskStatusManager.createNodeSnapshot(nodeId, inputData, result, null, null);
            taskStatusManager.updateNodeReport(taskId, nodeId, "succeeded", startTime, endTime, timeCost, snapshot);
            taskStatusManager.addExecutionLog(taskId, nodeId, "意图识别节点执行成功，识别意图: " + intent, startTime);
            nodeResult = new NodeResult(node.getType(), node.getId(), result, null);
        } catch (Exception e) {
            NodeExecutorUtil.handleException(taskId, nodeId, startTime, "意图识别节点", e);
        }
        return nodeResult;
    }

    /**
     * 从上下文获取输入文本
     */
    private String getTextFromContext(JsonNode data, WorkflowContext context) {
        if (data.has("text")) {
            JsonNode textNode = data.get("text");
            if (textNode.has("type") && "ref".equals(textNode.get("type").asText())) {
                JsonNode content = textNode.get("content");
                if (content.isArray() && content.size() == 2) {
                    String nodeId = content.get(0).asText();
                    String field = content.get(1).asText();
                    Object nodeResult = context.getNodeResult(nodeId);
                    if (nodeResult instanceof Map) {
                        Object value = ((Map<?, ?>) nodeResult).get(field);
                        return value != null ? value.toString() : "";
                    }
                }
            } else if (textNode.isTextual()) {
                return textNode.asText();
            }
        }
        return "";
    }

    /**
     * 执行意图识别
     */
    private String performIntentRecognition(Map<String, Object> inputs) {
        String text = (String) inputs.get("text");
        ChatCompletionRequest request = completionsService.getCompletionsRequest(text);
        IntentResult intentModal = sampleIntentService.detectIntent(request);
        return intentModal.getType();
    }

    /**
     * 获取输入数据映射
     */
    private Map<String, Object> getInputDataMap(WorkflowContext context) {
        Map<String, Object> inputData = new HashMap<>();
        inputData.putAll(context.getAllNodeResults());
        return inputData;
    }
} 