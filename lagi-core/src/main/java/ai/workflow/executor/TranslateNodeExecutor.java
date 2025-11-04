package ai.workflow.executor;

import ai.translate.TranslateService;
import ai.manager.TranslateManager;
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
 * 翻译节点执行器
 */
public class TranslateNodeExecutor implements INodeExecutor {

    private final TaskStatusManager taskStatusManager = TaskStatusManager.getInstance();
    private final TranslateService translateService = new TranslateService();

    @Override
    public boolean isValid() {
        boolean empty = TranslateManager.getInstance().getAdapters().isEmpty();
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
                throw new WorkflowException("翻译节点缺少输入配置");
            }

            // 解析输入值
            Map<String, Object> inputs = InputValueParser.parseInputs(inputsValues, context);

            // 执行翻译
            String result = callTranslate(inputs);

            Map<String, Object> output = new HashMap<>();
            output.put("result", result);
            output.put("sourceText", inputs.get("text"));

            long endTime = System.currentTimeMillis();
            long timeCost = endTime - startTime;
            TaskReportOutput.Snapshot snapshot = taskStatusManager.createNodeSnapshot(nodeId, output, output, null, null);
            taskStatusManager.updateNodeReport(taskId, nodeId, "succeeded", startTime, endTime, timeCost, snapshot);
            taskStatusManager.addExecutionLog(taskId, nodeId, "翻译节点执行成功", startTime);
            nodeResult = new NodeResult(node.getType(), node.getId(), output, null);

        } catch (Exception e) {
            NodeExecutorUtil.handleException(taskId, nodeId, startTime, "翻译节点", e);
        }

        return nodeResult;
    }

    private String callTranslate(Map<String, Object> inputs) {
        String text = (String) inputs.get("text");
        String targetLanguage = (String) inputs.get("targetLanguage");

        if (text == null || text.isEmpty()) {
            throw new RuntimeException("翻译缺少必需的文本参数");
        }

        if (targetLanguage == null || targetLanguage.isEmpty()) {
            throw new RuntimeException("翻译缺少目标语言参数");
        }

        // 根据目标语言调用相应的翻译方法
        switch (targetLanguage.toLowerCase()) {
            case "en":
            case "english":
                return translateService.toEnglish(text);
            case "zh":
            case "chinese":
                return translateService.toChinese(text);
            default:
                throw new RuntimeException("不支持的目标语言: " + targetLanguage);
        }
    }
}
