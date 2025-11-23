package ai.workflow.executor;

import ai.workflow.TaskStatusManager;
import ai.workflow.exception.WorkflowException;
import ai.workflow.pojo.Node;
import ai.workflow.pojo.NodeResult;
import ai.workflow.pojo.TaskReportOutput;
import ai.workflow.pojo.WorkflowContext;
import ai.workflow.utils.InputValueParser;
import ai.workflow.utils.NodeExecutorUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import okhttp3.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * API调用节点执行器
 * 支持GET/POST/PUT/DELETE等HTTP方法
 * 支持设置请求头、查询参数和请求体
 */
public class ApiCallNodeExecutor implements INodeExecutor {

    private final TaskStatusManager taskStatusManager = TaskStatusManager.getInstance();
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public List<String> getStandardOutputFields() {
        // api 节点输出 "statusCode" 和 "body" 字段
        return Arrays.asList("statusCode", "body");
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
                throw new WorkflowException("API调用节点缺少输入配置");
            }

            Map<String, Object> inputs = InputValueParser.parseInputs(inputsValues, context);

            // 解析API调用参数
            String url = (String) inputs.get("url");
            String method = (String) inputs.get("method");
            Map<String, String> headers = new Gson().fromJson((String) inputs.get("headers"), new TypeToken<Map<String, String>>(){}.getType());
            Map<String, String> query = new Gson().fromJson((String) inputs.get("query") , new TypeToken<Map<String, String>>(){}.getType());
            Object body = inputs.get("body");

            if (url == null || url.isEmpty()) {
                throw new WorkflowException("API调用节点缺少URL配置");
            }

            if (method == null || method.isEmpty()) {
                method = "GET"; // 默认GET方法
            }

            // 构造完整的URL（包含查询参数）
            HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
            if (query != null) {
                for (Map.Entry<String, String> entry : query.entrySet()) {
                    urlBuilder.addQueryParameter(entry.getKey(), entry.getValue());
                }
            }
            String fullUrl = urlBuilder.build().toString();

            // 构造请求
            Request request = buildRequest(fullUrl, method, headers, body);

            // 执行请求
            try (Response response = httpClient.newCall(request).execute()) {
                Map<String, Object> outputResult = new HashMap<>();
                outputResult.put("statusCode", response.code());

                if (response.body() != null) {
                    String responseBody = response.body().string();
                    outputResult.put("body", responseBody);

                }

                long endTime = System.currentTimeMillis();
                long timeCost = endTime - startTime;

                TaskReportOutput.Snapshot snapshot = taskStatusManager.createNodeSnapshot(nodeId, inputs, outputResult, null, null);
                taskStatusManager.updateNodeReport(taskId, nodeId, "succeeded", startTime, endTime, timeCost, snapshot);
                taskStatusManager.addExecutionLog(taskId, nodeId, "API调用节点执行成功，状态码: " + response.code(), startTime);
                nodeResult = new NodeResult(node.getType(), nodeId,outputResult, null);
            }
        } catch (Exception e) {
            NodeExecutorUtil.handleException(taskId, nodeId, startTime, "API调用节点", e);
        }
        return nodeResult;
    }

    /**
     * 构造HTTP请求
     *
     * @param url     请求URL
     * @param method  HTTP方法
     * @param headers 请求头
     * @param body    请求体
     * @return 构造的请求对象
     */
    private Request buildRequest(String url, String method, Map<String, String> headers, Object body) throws IOException {
        Request.Builder requestBuilder = new Request.Builder().url(url);

        // 添加请求头
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                requestBuilder.addHeader(entry.getKey(), entry.getValue());
            }
        }

        // 根据方法类型设置请求体
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            RequestBody requestBody = createRequestBody(body, headers);
            switch (method.toUpperCase()) {
                case "POST":
                    requestBuilder.post(requestBody);
                    break;
                case "PUT":
                    requestBuilder.put(requestBody);
                    break;
                case "DELETE":
                    requestBuilder.delete(requestBody);
                    break;
                case "PATCH":
                    requestBuilder.patch(requestBody);
                    break;
                default:
                    requestBuilder.method(method, requestBody);
                    break;
            }
        } else {
            // GET和HEAD请求不能有请求体
            if ("GET".equalsIgnoreCase(method)) {
                requestBuilder.get();
            } else if ("HEAD".equalsIgnoreCase(method)) {
                requestBuilder.head();
            }
        }

        return requestBuilder.build();
    }

    /**
     * 创建请求体
     *
     * @param body    请求体数据
     * @param headers 请求头
     * @return RequestBody对象
     */
    private RequestBody createRequestBody(Object body, Map<String, String> headers) throws IOException {
        if (body == null) {
            return RequestBody.create(new byte[0], null);
        }

        // 检查Content-Type头
        String contentType = null;
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if ("Content-Type".equalsIgnoreCase(entry.getKey())) {
                    contentType = entry.getValue();
                    break;
                }
            }
        }

        // 根据Content-Type或body类型创建相应的请求体
        if (contentType != null) {
            MediaType mediaType = MediaType.parse(contentType);
            if (body instanceof String) {
                return RequestBody.create((String) body, mediaType);
            } else {
                return RequestBody.create(objectMapper.writeValueAsString(body), mediaType);
            }
        } else {
            // 默认使用JSON格式
            if (body instanceof String) {
                return RequestBody.create((String) body, MediaType.get("application/json"));
            } else {
                return RequestBody.create(objectMapper.writeValueAsString(body), MediaType.get("application/json"));
            }
        }
    }
}
