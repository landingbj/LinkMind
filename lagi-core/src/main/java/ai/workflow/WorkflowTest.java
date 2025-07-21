package ai.workflow;

import ai.utils.OkHttpUtil;
import ai.workflow.pojo.WorkflowResult;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 工作流测试类
 */
public class WorkflowTest {
    private static final Gson gson = new Gson();

    public static void main(String[] args) throws IOException {
        System.out.println("workflowJson started...");
        LagiAgentResponse lagiAgentResponse = getLagiAgent(null, "1");
        WorkflowEngine engine = new WorkflowEngine();

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("query", "Hello LinkMind.");
        List<String> arrayObj = new ArrayList<>();
        arrayObj.add("item1");
        arrayObj.add("item2");
        inputData.put("array_obj", arrayObj);
        inputData.put("modelName", "lagi_0912");

        String workflowJson = lagiAgentResponse.getData().getSchema();
        System.out.println(workflowJson);
        String taskId = UUID.randomUUID().toString();
        WorkflowResult result = engine.execute(taskId, workflowJson, inputData);
        if (result.isSuccess()) {
            System.out.println("工作流执行成功: " + result.getResult());
        } else {
            System.out.println("工作流执行失败: " + result.getErrorMessage());
        }
    }


    public static LagiAgentResponse getLagiAgent(String lagiUserId, String agentId) throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put("lagiUserId", lagiUserId);
        params.put("agentId", agentId);
        String resultJson = OkHttpUtil.get("https://saas.landingbj.com/agent/getLagiAgent", params);
        LagiAgentResponse lagiAgentResponse = gson.fromJson(resultJson, LagiAgentResponse.class);
        return lagiAgentResponse;
    }

    /**
     * 读取resource文件中的工作流JSON配置，返回string
     */
    public static String readWorkflowJsonFromResource(String resourcePath) {
        try (InputStream inputStream = WorkflowTest.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            StringBuilder sb = new StringBuilder();
            byte[] buffer = new byte[4096];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                sb.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
            }
            return sb.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read workflow JSON from resource", e);
        }
    }
}