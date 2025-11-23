package ai.workflow.executor;

import ai.video.pojo.InputFile;
import ai.video.pojo.VideoGeneratorRequest;
import ai.video.pojo.VideoJobResponse;
import ai.video.service.AllVideoService;
import ai.manager.Image2VideoManager;
import ai.workflow.TaskStatusManager;
import ai.workflow.exception.WorkflowException;
import ai.workflow.pojo.Node;
import ai.workflow.pojo.NodeResult;
import ai.workflow.pojo.TaskReportOutput;
import ai.workflow.pojo.WorkflowContext;
import ai.workflow.utils.InputValueParser;
import ai.workflow.utils.NodeExecutorUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 图像生成视频节点执行器
 */
public class Image2VideoNodeExecutor implements INodeExecutor {

    private final TaskStatusManager taskStatusManager = TaskStatusManager.getInstance();
    private final AllVideoService videoService = new AllVideoService();

    @Override
    public boolean isValid() {
        boolean empty = Image2VideoManager.getInstance().getAdapters().isEmpty();
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
                throw new WorkflowException("Image2Video节点缺少输入配置");
            }

            // 解析输入值
            Map<String, Object> inputs = InputValueParser.parseInputs(inputsValues, context);

            // 执行图像生成视频
            VideoJobResponse result = callImage2Video(inputs);

            Map<String, Object> output = new HashMap<>();
            output.put("result", result);

            long endTime = System.currentTimeMillis();
            long timeCost = endTime - startTime;
            TaskReportOutput.Snapshot nodeSnapshot = taskStatusManager.createNodeSnapshot(nodeId, output, output, null, null);
            taskStatusManager.updateNodeReport(taskId, nodeId, "succeeded", startTime, endTime, timeCost, nodeSnapshot);
            taskStatusManager.addExecutionLog(taskId, nodeId, "Image2Video节点执行成功", startTime);
            nodeResult = new NodeResult(node.getType(), node.getId(), output, null);

        } catch (Exception e) {
            NodeExecutorUtil.handleException(taskId, nodeId, startTime, "Image2Video节点", e);
        }

        return nodeResult;
    }

    private VideoJobResponse callImage2Video(Map<String, Object> inputs) {
        VideoGeneratorRequest param = new VideoGeneratorRequest();

        // 设置必需参数
        if (inputs.containsKey("imageUrl")) {
            InputFile imageUrl = InputFile.builder().url((String) inputs.get("imageUrl")).build();
            param.setInputFileList(Collections.singletonList(imageUrl));
        } else {
            throw new RuntimeException("Image2Video缺少必需的图像URL参数");
        }

        // 设置可选参数
        if (inputs.containsKey("model")) {
            param.setModel((String) inputs.get("model"));
        }

        if (inputs.containsKey("prompt")) {
            param.setIntPutText(Collections.singletonList((String) inputs.get("prompt")));
        }

        // 执行图像生成视频
        return videoService.image2Video(param);
    }
}
