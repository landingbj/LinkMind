package ai.workflow.executor;

import ai.audio.service.AudioService;
import ai.common.pojo.AsrResult;
import ai.common.pojo.AudioRequestParam;
import ai.manager.ASRManager;
import ai.utils.FileUtil;
import ai.workflow.TaskStatusManager;
import ai.workflow.exception.WorkflowException;
import ai.workflow.pojo.Node;
import ai.workflow.pojo.NodeResult;
import ai.workflow.pojo.TaskReportOutput;
import ai.workflow.pojo.WorkflowContext;
import ai.workflow.utils.InputValueParser;
import ai.workflow.utils.NodeExecutorUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ASR节点执行器
 */
public class ASRNodeExecutor implements INodeExecutor {

    private final TaskStatusManager taskStatusManager = TaskStatusManager.getInstance();
    private final AudioService audioService = new AudioService();

    public boolean isValid() {
        boolean empty = ASRManager.getInstance().getAdapters().isEmpty();
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
                throw new WorkflowException("ASR节点缺少输入配置");
            }

            // 解析输入值
            Map<String, Object> inputs = InputValueParser.parseInputs(inputsValues, context);

            // 执行ASR识别
            AsrResult result = callASR(inputs);

            Map<String, Object> output = new HashMap<>();
            output.put("result", result);

            long endTime = System.currentTimeMillis();
            long timeCost = endTime - startTime;
            TaskReportOutput.Snapshot snapshot = taskStatusManager.createNodeSnapshot(nodeId, output, output, null, null);
            taskStatusManager.updateNodeReport(taskId, nodeId, "succeeded", startTime, endTime, timeCost, snapshot);
            taskStatusManager.addExecutionLog(taskId, nodeId, "ASR节点执行成功", startTime);
            nodeResult = new NodeResult(node.getType(), node.getId(), output, null);

        } catch (Exception e) {
            NodeExecutorUtil.handleException(taskId, nodeId, startTime, "ASR节点", e);
        }

        return nodeResult;
    }

    private AsrResult callASR(Map<String, Object> inputs) {
        String audioUrl = (String) inputs.get("audioUrl");
        AudioRequestParam param = new AudioRequestParam();

        // 设置模型参数（如果提供）
        if (inputs.containsKey("model")) {
            param.setModel((String) inputs.get("model"));
        }

        // 设置其他可选参数
        if (inputs.containsKey("format")) {
            param.setFormat((String) inputs.get("format"));
        }

        if (inputs.containsKey("sample_rate")) {
            param.setSample_rate(((Double) inputs.get("sample_rate")).intValue());
        }


        String audioFilePath = audioUrl;
        File tempFile = null;

        // 如果是HTTP URL，则下载为临时文件
        if (audioUrl != null && (audioUrl.startsWith("http://") || audioUrl.startsWith("https://"))) {
            try {
                // 创建临时文件
                String tempDir = System.getProperty("java.io.tmpdir");
                String fileExtension = ".wav";
                if (param.getFormat() != null) {
                    fileExtension = "." + param.getFormat();
                }

                String tempFileName = UUID.randomUUID().toString() + fileExtension;
                tempFile = FileUtil.urlToFile(audioUrl, tempDir + File.separator + tempFileName);

                if (tempFile != null && tempFile.exists()) {
                    audioFilePath = tempFile.getAbsolutePath();
                } else {
                    throw new RuntimeException("下载音频文件失败");
                }
            } catch (Exception e) {
                throw new RuntimeException("下载音频文件时出错: " + e.getMessage(), e);
            }
        }

        try {
            // 执行ASR识别
            return audioService.asr(audioFilePath, param);
        } finally {
            // 如果是临时文件，则执行完成后删除
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
}
