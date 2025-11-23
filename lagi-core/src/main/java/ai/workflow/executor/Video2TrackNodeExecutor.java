package ai.workflow.executor;

import ai.video.pojo.VideoJobResponse;
import ai.video.pojo.VideoTackRequest;
import ai.video.service.AllVideoService;
import ai.manager.Video2TrackManager;
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
 * 视频追踪节点执行器
 */
public class Video2TrackNodeExecutor implements INodeExecutor {

    private final TaskStatusManager taskStatusManager = TaskStatusManager.getInstance();
    private final AllVideoService videoService = new AllVideoService();

    @Override
    public boolean isValid() {
        boolean empty = Video2TrackManager.getInstance().getAdapters().isEmpty();
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
                throw new WorkflowException("Video2Track节点缺少输入配置");
            }

            // 解析输入值
            Map<String, Object> inputs = InputValueParser.parseInputs(inputsValues, context);

            // 执行视频追踪
            VideoJobResponse result = callVideoTrack(inputs);

            Map<String, Object> output = new HashMap<>();
            output.put("result", result);

            long endTime = System.currentTimeMillis();
            long timeCost = endTime - startTime;
            TaskReportOutput.Snapshot nodeSnapshot = taskStatusManager.createNodeSnapshot(nodeId, output, output, null, null);
            taskStatusManager.updateNodeReport(taskId, nodeId, "succeeded", startTime, endTime, timeCost, nodeSnapshot);
            taskStatusManager.addExecutionLog(taskId, nodeId, "Video2Track节点执行成功", startTime);
            nodeResult = new NodeResult(node.getType(), node.getId(), output, null);

        } catch (Exception e) {
            NodeExecutorUtil.handleException(taskId, nodeId, startTime, "Video2Track节点", e);
        }

        return nodeResult;
    }

    private VideoJobResponse callVideoTrack(Map<String, Object> inputs) {
        VideoTackRequest param = new VideoTackRequest();

        // 设置必需参数
        if (inputs.containsKey("videoUrl")) {
            param.setVideoUrl((String) inputs.get("videoUrl"));
        } else {
            throw new RuntimeException("Video2Track缺少必需的视频URL参数");
        }

        // 设置可选参数
        if (inputs.containsKey("model")) {
            param.setModel((String) inputs.get("model"));
        }

        // 执行视频追踪
        return videoService.track(param);
    }
}
