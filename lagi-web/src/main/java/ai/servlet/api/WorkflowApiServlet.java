package ai.servlet.api;

import ai.common.pojo.Response;
import ai.config.pojo.AgentConfig;
import ai.agent.AgentService;
import ai.openai.pojo.ChatMessage;
import ai.response.RestfulResponse;
import ai.servlet.BaseServlet;
import ai.agent.dto.LagiAgentResponse;
import ai.utils.FileUtil;
import ai.vector.loader.impl.DocLoader;
import ai.vector.loader.impl.TxtLoader;
import ai.workflow.TaskStatusManager;
import ai.workflow.WorkflowEngine;
import ai.workflow.WorkflowGenerator;
import ai.workflow.pojo.*;
import ai.workflow.utils.DefaultNodeEnum;
import com.google.common.reflect.TypeToken;
import com.google.gson.JsonObject;
import lombok.*;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class WorkflowApiServlet extends BaseServlet {
    private static final Logger logger = LoggerFactory.getLogger(WorkflowApiServlet.class);
    private final AgentService agentService = new AgentService();
    private final TaskStatusManager taskStatusManager = TaskStatusManager.getInstance();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setHeader("Content-Type", "application/json;charset=utf-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);
        if (method.equals("saveFlowSchema")) {
            this.saveFlowSchema(req, resp);
        } else if (method.equals("taskRun")) {
            this.taskRun(req, resp);
        } else if (method.equals("taskCancel")) {
            this.taskCancel(req, resp);
        } else if (method.equals("txt2FlowSchema")) {
            this.txt2FlowSchema(req, resp);
        } else if(method.equals("uploadDoc")) {
            this.uploadDoc(req, resp);
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @ToString
    @Builder
    static class FileResponse {
        private String filename;
        private String filepath;
    }

    private void uploadDoc(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            // 获取上传的文件项
            List<FileResponse> fileResponses = new ArrayList<>();

            // 使用 ServletFileUpload 处理 multipart 请求
            if (ServletFileUpload.isMultipartContent(req)) {
                DiskFileItemFactory factory = new DiskFileItemFactory();
                ServletFileUpload upload = new ServletFileUpload(factory);

                // 设置最大文件大小等配置
                upload.setSizeMax(50 * 1024 * 1024); // 50MB
                upload.setFileSizeMax(50 * 1024 * 1024); // 50MB

                List<FileItem> items = upload.parseRequest(req);

                for (FileItem item : items) {
                    if (!item.isFormField() && item.getName() != null && !item.getName().isEmpty()) {
                        // 创建临时文件
                        String fileName = item.getName();
                        String tempDir = System.getProperty("java.io.tmpdir");
                        File tempFile = File.createTempFile("upload_", "_" + fileName, new File(tempDir));

                        // 保存文件
                        item.write(tempFile);

                        // 添加到响应列表
                        FileResponse fileResponse = FileResponse.builder()
                                .filename(fileName)
                                .filepath(tempFile.getAbsolutePath())
                                .build();
                        fileResponses.add(fileResponse);
                    }
                }
            }

            RestfulResponse<List<FileResponse>> success = RestfulResponse.sucecced(fileResponses);
            responsePrint(resp, toJson(success));
        } catch (Exception e) {
            logger.error("文件上传失败", e);
            RestfulResponse<Object> error = RestfulResponse.error("文件上传失败: " + e.getMessage());
            responsePrint(resp, toJson(error));
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setHeader("Content-Type", "application/json;charset=utf-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);
        if (method.equals("getFlowSchema")) {
            this.getFlowSchema(req, resp);
        } else if (method.equals("taskReport")) {
            this.taskReport(req, resp);
        } else if (method.equals("taskResult")) {
            this.taskResult(req, resp);
        } else if (method.equals("getValidNodes")) {
            this.getValidNodes(req, resp);
        }
    }



    private void txt2FlowSchema(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        JsonObject jsonObject = reqBodyToObj(req, JsonObject.class);
        Integer agentId = jsonObject.get("agentId").getAsInt();
        String input = jsonObject.get("input").toString();
        String docs = jsonObject.get("flowDocList").toString();
        String his = jsonObject.get("history").toString();
        List<FileResponse> flowDocList = gson.fromJson(docs, new TypeToken<List<FileResponse>>() {
        }.getType());
        List<String> contents = flowDocList.stream().map(fileResponse -> {
            // 文件内容提取 支持 txt, doc, docx, pdf, md,  文件 返回字符串
            String fileExtension = FileUtil.getFileExtension(fileResponse.getFilename());
            switch (fileExtension) {
                case "txt":
                    return new TxtLoader().load(fileResponse.getFilepath());
                case "doc":
                case "docx":
                    return new DocLoader().load(fileResponse.getFilepath());
                default:
                    return "";
            }
        }).filter(content -> !content.isEmpty()).collect(Collectors.toList());
        List<ChatMessage> history = gson.fromJson(his, new TypeToken<List<ChatMessage>>() {
        }.getType());
        System.out.println(flowDocList);
        try {
            String schema = WorkflowGenerator.txt2FlowSchema(input,  history, contents);
            AgentConfig agentConfig = new AgentConfig();
            agentConfig.setId(agentId);
            agentConfig.setSchema(schema);
            Response response = agentService.updateLagiAgent(agentConfig);
            response.setData(schema);
            responsePrint(resp, gson.toJson(response));
        } catch (Exception e) {
            Response failed = Response.builder().status("failed").msg("txt2FlowSchema failed").data(null).build();
            responsePrint(resp, gson.toJson(failed));
        }
    }


    private void saveFlowSchema(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        JsonObject jsonObject = reqBodyToObj(req, JsonObject.class);
        Integer agentId = jsonObject.get("agentId").getAsInt();
        String schema = jsonObject.get("schema").toString();
        AgentConfig agentConfig = new AgentConfig();
        agentConfig.setId(agentId);
        agentConfig.setSchema(schema);
        Response response = agentService.updateLagiAgent(agentConfig);
        responsePrint(resp, gson.toJson(response));
    }

    private void getFlowSchema(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        String agentId = req.getParameter("agentId");
        Map<String, Object> result = new HashMap<>();
        LagiAgentResponse lagiAgentResponse = agentService.getLagiAgent(null, agentId);
        if (lagiAgentResponse == null || !"success".equals(lagiAgentResponse.getStatus())
                || lagiAgentResponse.getData() == null) {
            result.put("status", "failed");
            responsePrint(resp, gson.toJson(result));
            return;
        }
        String schema = lagiAgentResponse.getData().getSchema();
        result.put("status", "success");
        result.put("schema", schema);
        responsePrint(resp, toJson(result));
    }


    /**
     * 运行工作流任务
     */
    private void taskRun(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        TaskRunInput taskRunInput = reqBodyToObj(req, TaskRunInput.class);
        Map<String, Object> result = new HashMap<>();
        String taskId = UUID.randomUUID().toString();
        result.put("taskID", taskId);
        WorkflowEngine engine = new WorkflowEngine();
        engine.executeAsync(taskId, taskRunInput.getSchema(), new WorkflowContext(taskRunInput.getInputs()));
        responsePrint(resp, gson.toJson(result));
    }

    /**
     * 取消工作流任务
     */
    private void taskCancel(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");

        try {
            // 解析请求体为TaskCancelInput对象
            TaskCancelInput input = reqBodyToObj(req, TaskCancelInput.class);

            // 验证输入参数
            if (input == null || input.getTaskID() == null || input.getTaskID().trim().isEmpty()) {
                TaskCancelOutput errorOutput = new TaskCancelOutput(false);
                logger.info("=== taskCancel 响应 (参数错误) ===");
                logger.info("响应体: {}", gson.toJson(errorOutput));
                responsePrint(resp, gson.toJson(errorOutput));
                return;
            }

            String taskID = input.getTaskID();

            boolean taskExists = checkTaskExists(taskID);
            boolean taskCompleted = checkTaskCompleted(taskID);
            boolean taskAlreadyCanceled = checkTaskAlreadyCanceled(taskID);

            TaskCancelOutput output;

            if (!taskExists) {
                // 任务不存在
                logger.info("任务不存在: {}", taskID);
                output = new TaskCancelOutput(true);
            } else if (taskCompleted) {
                // 任务已完成，无法取消
                logger.info("任务已完成，无法取消: {}", taskID);
                output = new TaskCancelOutput(false);
            } else if (taskAlreadyCanceled) {
                // 任务已经取消
                logger.info("任务已经取消: {}", taskID);
                output = new TaskCancelOutput(false);
            } else {
                // 执行取消操作
                boolean cancelSuccess = performTaskCancel(taskID);
                output = new TaskCancelOutput(cancelSuccess);
            }
            responsePrint(resp, gson.toJson(output));
        } catch (Exception e) {
            logger.error("taskCancel 处理异常: {}", e.getMessage(), e);
            // 返回错误响应
            TaskCancelOutput errorOutput = new TaskCancelOutput(false);
            responsePrint(resp, gson.toJson(errorOutput));
        }
    }

    /**
     * 检查任务是否存在
     */
    private boolean checkTaskExists(String taskID) {
        return taskStatusManager.taskExists(taskID);
    }

    /**
     * 检查任务是否已完成
     */
    private boolean checkTaskCompleted(String taskID) {
        return taskStatusManager.isTaskCompleted(taskID);
    }

    /**
     * 检查任务是否已经取消
     */
    private boolean checkTaskAlreadyCanceled(String taskID) {
        return taskStatusManager.isTaskCanceled(taskID);
    }

    /**
     * 执行任务取消操作
     */
    private boolean performTaskCancel(String taskID) {
        return taskStatusManager.cancelTask(taskID);
    }

    /**
     * 获取工作流任务报告
     */
    private void taskReport(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        String taskId = req.getParameter("taskID");
        TaskReportOutput taskReportOutput = taskStatusManager.getTaskReport(taskId);
        responsePrint(resp, gson.toJson(taskReportOutput));
    }

    /**
     * 获取工作流任务结果
     */
    private void taskResult(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        String taskId = req.getParameter("taskId");
        TaskReportOutput taskReportOutput = taskStatusManager.getTaskReport(taskId);
        responsePrint(resp, gson.toJson(taskReportOutput));
    }

    private void getValidNodes(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        List<DefaultNodeEnum> valIdNodeEnum = DefaultNodeEnum.getValIdNodeEnum();
        List<String> validNodeName = valIdNodeEnum.stream().map(DefaultNodeEnum::getName).collect(Collectors.toList());
        RestfulResponse<List<String>> sucecced = RestfulResponse.sucecced(validNodeName);
        responsePrint(resp, gson.toJson(sucecced));
    }

}
