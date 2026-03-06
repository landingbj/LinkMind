package ai.servlet.mock_new_apis;

import ai.servlet.BaseServlet;
import com.google.gson.Gson;
import org.apache.commons.io.IOUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 租户隔离Mock接口
 */
public class TenantIsolationMockServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;
    protected Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);

        resp.setContentType("application/json;charset=utf-8");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            String jsonString = IOUtils.toString(req.getInputStream(), StandardCharsets.UTF_8);
            
            switch (method) {
                case "allocate-independent-resource-pool":
                    result = handleAllocateIndependentResourcePool(jsonString);
                    break;
                case "set-tenant-quota":
                    result = handleSetTenantQuota(jsonString);
                    break;
                case "isolate-network":
                    result = handleIsolateNetwork(jsonString);
                    break;
                case "isolate-storage":
                    result = handleIsolateStorage(jsonString);
                    break;
                case "isolate-compute":
                    result = handleIsolateCompute(jsonString);
                    break;
                case "isolate-gpu":
                    result = handleIsolateGpu(jsonString);
                    break;
                case "isolate-memory":
                    result = handleIsolateMemory(jsonString);
                    break;
                case "isolate-bandwidth":
                    result = handleIsolateBandwidth(jsonString);
                    break;
                case "isolate-queue":
                    result = handleIsolateQueue(jsonString);
                    break;
                case "isolate-scheduling":
                    result = handleIsolateScheduling(jsonString);
                    break;
                case "isolate-monitoring":
                    result = handleIsolateMonitoring(jsonString);
                    break;
                case "isolate-logging":
                    result = handleIsolateLogging(jsonString);
                    break;
                default:
                    result.put("status", "failed");
                    result.put("message", "接口不存在: " + method);
                    break;
            }
            
        } catch (Exception e) {
            result.put("status", "failed");
            result.put("message", "处理请求失败: " + e.getMessage());
        }
        
        responsePrint(resp, toJson(result));
    }

    private Map<String, Object> handleAllocateIndependentResourcePool(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "独立资源池分配成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleSetTenantQuota(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "租户配额设置成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleIsolateNetwork(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "网络隔离成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleIsolateStorage(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "存储隔离成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleIsolateCompute(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "计算隔离成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleIsolateGpu(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "GPU隔离成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleIsolateMemory(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "内存隔离成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleIsolateBandwidth(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "带宽隔离成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleIsolateQueue(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "队列隔离成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleIsolateScheduling(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "调度隔离成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleIsolateMonitoring(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "监控隔离成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleIsolateLogging(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "日志隔离成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private String toJson(Map<String, Object> result) {
        return gson.toJson(result);
    }
}