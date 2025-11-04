package ai.workflow.executor;

import ai.audio.service.AudioService;
import ai.common.pojo.TTSRequestParam;
import ai.common.pojo.TTSResult;
import ai.manager.TTSManager;
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
 * TTS节点执行器
 */
public class TTSNodeExecutor implements INodeExecutor {

    private final TaskStatusManager taskStatusManager = TaskStatusManager.getInstance();
    private final AudioService audioService = new AudioService();

    @Override
    public boolean isValid() {
        boolean empty = TTSManager.getInstance().getAdapters().isEmpty();
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
                throw new WorkflowException("TTS节点缺少输入配置");
            }

            // 解析输入值
            Map<String, Object> inputs = InputValueParser.parseInputs(inputsValues, context);

            // 执行TTS合成
            TTSResult result = callTTS(inputs);

            Map<String, Object> output = new HashMap<>();
            output.put("result", result.getResult());

            long endTime = System.currentTimeMillis();
            long timeCost = endTime - startTime;
            TaskReportOutput.Snapshot snapshot = taskStatusManager.createNodeSnapshot(nodeId, output, output, null, null);
            taskStatusManager.updateNodeReport(taskId, nodeId, "succeeded", startTime, endTime, timeCost, snapshot);
            taskStatusManager.addExecutionLog(taskId, nodeId, "TTS节点执行成功", startTime);
            nodeResult = new NodeResult(node.getType(), node.getId(), output, null);

        } catch (Exception e) {
            NodeExecutorUtil.handleException(taskId, nodeId, startTime, "TTS节点", e);
        }

        return nodeResult;
    }

    private TTSResult callTTS(Map<String, Object> inputs) {
        TTSRequestParam param = new TTSRequestParam();

        // 设置必填参数
        if (inputs.containsKey("text")) {
            param.setText((String) inputs.get("text"));
        } else {
            throw new RuntimeException("TTS缺少必需的文本参数");
        }

        // 设置可选参数
        if (inputs.containsKey("model")) {
            param.setModel((String) inputs.get("model"));
        }

        if (inputs.containsKey("voice")) {
            param.setVoice((String) inputs.get("voice"));
        }

        if (inputs.containsKey("volume")) {
            param.setVolume(((Double) inputs.get("volume")).intValue());
        }

        if (inputs.containsKey("format")) {
            param.setFormat((String) inputs.get("format"));
        }

        // 执行TTS合成
        return audioService.tts(param);
    }
}
