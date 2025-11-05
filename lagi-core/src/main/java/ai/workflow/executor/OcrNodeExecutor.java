package ai.workflow.executor;

import ai.ocr.OcrService;
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
import java.util.*;

/**
 * OCR节点执行器
 */
public class OcrNodeExecutor implements INodeExecutor {

    private final TaskStatusManager taskStatusManager = TaskStatusManager.getInstance();
    private final OcrService ocrService = new OcrService();

    @Override
    public boolean isValid() {
        // OCR服务始终有效
        return true;
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
                throw new WorkflowException("OCR节点缺少输入配置");
            }

            // 解析输入值
            Map<String, Object> inputs = InputValueParser.parseInputs(inputsValues, context);

            // 执行OCR识别
            List<String> result = callOcr(inputs);

            Map<String, Object> output = new HashMap<>();
            output.put("result", result);

            long endTime = System.currentTimeMillis();
            long timeCost = endTime - startTime;
            TaskReportOutput.Snapshot nodeSnapshot = taskStatusManager.createNodeSnapshot(nodeId, output, output, null, null);
            taskStatusManager.updateNodeReport(taskId, nodeId, "succeeded", startTime, endTime, timeCost, nodeSnapshot);
            taskStatusManager.addExecutionLog(taskId, nodeId, "OCR节点执行成功", startTime);
            nodeResult = new NodeResult(node.getType(), node.getId(), output, null);

        } catch (Exception e) {
            NodeExecutorUtil.handleException(taskId, nodeId, startTime, "OCR节点", e);
        }

        return nodeResult;
    }

    private List<String> callOcr(Map<String, Object> inputs) {
        String imageUrl = (String) inputs.get("imageUrl");

        if (imageUrl == null || imageUrl.isEmpty()) {
            throw new RuntimeException("OCR缺少必需的图像URL参数");
        }

        String imageFilePath = imageUrl;
        File tempFile = null;

        // 如果是HTTP URL，则下载为临时文件
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
            try {
                // 创建临时文件
                String tempDir = System.getProperty("java.io.tmpdir");
                String fileExtension = ".jpg";

                String tempFileName = UUID.randomUUID().toString() + fileExtension;
                tempFile = FileUtil.urlToFile(imageUrl, tempDir + File.separator + tempFileName);

                if (tempFile != null && tempFile.exists()) {
                    imageFilePath = tempFile.getAbsolutePath();
                } else {
                    throw new RuntimeException("下载音频文件失败");
                }
            } catch (Exception e) {
                throw new RuntimeException("下载音频文件时出错: " + e.getMessage(), e);
            }
        }

        File imageFile = new File(imageFilePath);
        // 支持单个图像文件OCR识别
        return ocrService.image2Ocr(Collections.singletonList(imageFile));
    }
}
