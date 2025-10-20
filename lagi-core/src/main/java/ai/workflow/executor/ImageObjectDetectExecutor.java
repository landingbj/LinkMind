package ai.workflow.executor;

import ai.common.exception.RRException;
import ai.image.pojo.ImageDetectParam;
import ai.image.pojo.ObjectDetectResult;
import ai.image.service.AllImageService;
import ai.manager.ImageObjectDetectManager;
import ai.workflow.TaskStatusManager;
import ai.workflow.exception.WorkflowException;
import ai.workflow.pojo.Node;
import ai.workflow.pojo.NodeResult;
import ai.workflow.pojo.TaskReportOutput;
import ai.workflow.pojo.WorkflowContext;
import ai.workflow.utils.InputValueParser;
import ai.workflow.utils.NodeExecutorUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class ImageObjectDetectExecutor implements INodeExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ImageObjectDetectExecutor.class);

    private final TaskStatusManager taskStatusManager = TaskStatusManager.getInstance();
    private final AllImageService imageService = new AllImageService();

    @Override
    public boolean isValid() {
        boolean empty = ImageObjectDetectManager.getInstance().getAdapters().isEmpty();
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
                throw new WorkflowException("Image2Text节点缺少输入配置");
            }

            // 解析输入值
            Map<String, Object> inputs = InputValueParser.parseInputs(inputsValues, context);

            // 执行object detect
            ObjectDetectResult result = callImageObjectDetect(inputs);

            Map<String, Object> output = new HashMap<>();
            output.put("result", result);

            long endTime = System.currentTimeMillis();
            long timeCost = endTime - startTime;
            TaskReportOutput.Snapshot nodeSnapshot = taskStatusManager.createNodeSnapshot(nodeId, output, output, null, null);
            taskStatusManager.updateNodeReport(taskId, nodeId, "succeeded", startTime, endTime, timeCost, nodeSnapshot);
            taskStatusManager.addExecutionLog(taskId, nodeId, "ImageObjectDetect节点执行成功", startTime);
            nodeResult = new NodeResult(node.getType(), node.getId(), output, null);

        } catch (Exception e) {
            NodeExecutorUtil.handleException(taskId, nodeId, startTime, "ImageObjectDetect节点", e);
        }

        return nodeResult;
    }

    private ObjectDetectResult callImageObjectDetect(Map<String, Object> inputs) {
        String imageUrl = (String) inputs.get("imageUrl");
        ImageDetectParam param = ImageDetectParam.builder().build();

        // 设置模型参数（如果提供）
        if (inputs.containsKey("model")) {
            param.setModel((String) inputs.get("model"));
        }

        param.setImageUrl(imageUrl);

        if (imageUrl != null && (imageUrl.startsWith("http://") || imageUrl.startsWith("https://"))) {
            param.setImageUrl(imageUrl);
            return imageService.detect(param);
        }
        throw new RRException("图片路径不合法请使用网络路径");

    }
}
