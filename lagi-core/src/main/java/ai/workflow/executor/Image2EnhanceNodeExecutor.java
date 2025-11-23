package ai.workflow.executor;

import ai.common.pojo.ImageEnhanceResult;
import ai.image.pojo.ImageEnhanceRequest;
import ai.image.service.AllImageService;
import ai.manager.ImageEnhanceManager;
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
 * 图像增强节点执行器
 */
public class Image2EnhanceNodeExecutor implements INodeExecutor {

    private final TaskStatusManager taskStatusManager = TaskStatusManager.getInstance();
    private final AllImageService imageService = new AllImageService();

    @Override
    public boolean isValid() {
        boolean empty = ImageEnhanceManager.getInstance().getAdapters().isEmpty();
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
                throw new WorkflowException("Image2Enhance节点缺少输入配置");
            }

            // 解析输入值
            Map<String, Object> inputs = InputValueParser.parseInputs(inputsValues, context);

            // 执行图像增强
            ImageEnhanceResult result = callImageEnhance(inputs);

            Map<String, Object> output = new HashMap<>();
            output.put("result", result);

            long endTime = System.currentTimeMillis();
            long timeCost = endTime - startTime;
            TaskReportOutput.Snapshot nodeSnapshot = taskStatusManager.createNodeSnapshot(nodeId, output, output, null, null);
            taskStatusManager.updateNodeReport(taskId, nodeId, "succeeded", startTime, endTime, timeCost, nodeSnapshot);
            taskStatusManager.addExecutionLog(taskId, nodeId, "Image2Enhance节点执行成功", startTime);
            nodeResult = new NodeResult(node.getType(), node.getId(), output, null);

        } catch (Exception e) {
            NodeExecutorUtil.handleException(taskId, nodeId, startTime, "Image2Enhance节点", e);
        }

        return nodeResult;
    }

    private ImageEnhanceResult callImageEnhance(Map<String, Object> inputs) {
        ImageEnhanceRequest param = new ImageEnhanceRequest();

        // 设置必需参数
        if (inputs.containsKey("imageUrl")) {
            param.setImageUrl((String) inputs.get("imageUrl"));
        } else {
            throw new RuntimeException("Image2Enhance缺少必需的图像URL参数");
        }

        // 设置可选参数
        if (inputs.containsKey("model")) {
            param.setModel((String) inputs.get("model"));
        }

        // 执行图像增强
        return imageService.enhance(param);
    }
}
