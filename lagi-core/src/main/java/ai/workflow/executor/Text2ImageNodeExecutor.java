package ai.workflow.executor;

import ai.common.pojo.ImageGenerationRequest;
import ai.common.pojo.ImageGenerationResult;
import ai.image.service.AllImageService;
import ai.manager.ImageGenerationManager;
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
 * 文本生成图像节点执行器
 */
public class Text2ImageNodeExecutor implements INodeExecutor {

    private final TaskStatusManager taskStatusManager = TaskStatusManager.getInstance();
    private final AllImageService imageService = new AllImageService();

    @Override
    public boolean isValid() {
        boolean empty = ImageGenerationManager.getInstance().getAdapters().isEmpty();
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
                throw new WorkflowException("Text2Image节点缺少输入配置");
            }

            // 解析输入值
            Map<String, Object> inputs = InputValueParser.parseInputs(inputsValues, context);

            // 执行文本生成图像
            ImageGenerationResult result = callText2Image(inputs);

            Map<String, Object> output = new HashMap<>();
            output.put("result", result);

            long endTime = System.currentTimeMillis();
            long timeCost = endTime - startTime;
            TaskReportOutput.Snapshot nodeSnapshot = taskStatusManager.createNodeSnapshot(nodeId, output, output, null, null);
            taskStatusManager.updateNodeReport(taskId, nodeId, "succeeded", startTime, endTime, timeCost, nodeSnapshot);
            taskStatusManager.addExecutionLog(taskId, nodeId, "Text2Image节点执行成功", startTime);
            nodeResult = new NodeResult(node.getType(), node.getId(), output, null);

        } catch (Exception e) {
            NodeExecutorUtil.handleException(taskId, nodeId, startTime, "Text2Image节点", e);
        }

        return nodeResult;
    }

    private ImageGenerationResult callText2Image(Map<String, Object> inputs) {
        ImageGenerationRequest param = ImageGenerationRequest.builder().build();

        // 设置必需参数
        if (inputs.containsKey("prompt")) {
            param.setPrompt((String) inputs.get("prompt"));
        } else {
            throw new RuntimeException("Text2Image缺少必需的提示词参数");
        }

        // 设置可选参数
        if (inputs.containsKey("model")) {
            param.setModel((String) inputs.get("model"));
        }

        if (inputs.containsKey("negative_prompt")) {
            param.setNegative_prompt((String) inputs.get("negative_prompt"));
        }

        if (inputs.containsKey("style")) {
            param.setStyle((String) inputs.get("style"));
        }
        // 执行文本生成图像
        return imageService.generations(param);
    }
}
