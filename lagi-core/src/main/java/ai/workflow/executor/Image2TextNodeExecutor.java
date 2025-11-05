package ai.workflow.executor;

import ai.common.exception.RRException;
import ai.common.pojo.FileRequest;
import ai.common.pojo.ImageToTextResponse;
import ai.image.service.AllImageService;
import ai.manager.Image2TextManger;
import ai.utils.ImageUtil;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class Image2TextNodeExecutor implements INodeExecutor {

    private static final Logger logger = LoggerFactory.getLogger(Image2TextNodeExecutor.class);

    private final TaskStatusManager taskStatusManager = TaskStatusManager.getInstance();
    private final AllImageService imageService = new AllImageService();

    @Override
    public boolean isValid() {
        boolean empty = Image2TextManger.getInstance().getAdapters().isEmpty();
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

            // 执行图像转文字
            ImageToTextResponse result = callImage2Text(inputs);

            Map<String, Object> output = new HashMap<>();
            output.put("result", result);

            long endTime = System.currentTimeMillis();
            long timeCost = endTime - startTime;
            TaskReportOutput.Snapshot nodeSnapshot = taskStatusManager.createNodeSnapshot(nodeId, output, output, null, null);
            taskStatusManager.updateNodeReport(taskId, nodeId, "succeeded", startTime, endTime, timeCost, nodeSnapshot);
            taskStatusManager.addExecutionLog(taskId, nodeId, "Image2Text节点执行成功", startTime);
            nodeResult = new NodeResult(node.getType(), node.getId(), output, null);

        } catch (Exception e) {
            NodeExecutorUtil.handleException(taskId, nodeId, startTime, "Image2Text节点", e);
        }

        return nodeResult;
    }

    private ImageToTextResponse callImage2Text(Map<String, Object> inputs) {
        String imageUrl = (String) inputs.get("imageUrl");
        FileRequest param = FileRequest.builder().build();

        // 设置模型参数（如果提供）
        if (inputs.containsKey("model")) {
            param.setModel((String) inputs.get("model"));
        }

        param.setImageUrl(imageUrl);

        if (imageUrl != null && (imageUrl.startsWith("http://") || imageUrl.startsWith("https://"))) {
            File tempFile = null;
            if ((imageUrl.startsWith("http://") || imageUrl.startsWith("https://"))) {
                try {
                    Path tempPath = Files.createTempFile("image-2-text",  ".jpg");
                    tempFile = ImageUtil.urlToFile(imageUrl, tempPath.toFile().getAbsolutePath());
                    if(tempFile != null) {
                        param.setImageUrl(tempFile.getAbsolutePath());
                        return imageService.toText(param);
                    }
                } catch (IOException e) {
                }
            }

        }
        throw new RRException("图片路径不合法请使用网络路径");

    }
}
