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
 * 数据中心和负载均衡Mock接口
 */
public class DatacenterMockServlet extends BaseServlet {
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
                case "geo-optimal-selection":
                    result = handleGeoOptimalSelection(jsonString);
                    break;
                case "auto-merge-tasks":
                    result = handleAutoMergeTasks(jsonString);
                    break;
                case "monitor-load-status":
                    result = handleMonitorLoadStatus(jsonString);
                    break;
                case "distribute-multi-nodes":
                    result = handleDistributeMultiNodes(jsonString);
                    break;
                case "customize-strategy":
                    result = handleCustomizeStrategy(jsonString);
                    break;
                case "allocate-by-business-priority":
                    result = handleAllocateByBusinessPriority(jsonString);
                    break;
                case "visualize-load-distribution":
                    result = handleVisualizeLoadDistribution(jsonString);
                    break;
                case "visualize-load-status":
                    result = handleVisualizeLoadStatus(jsonString);
                    break;
                case "auto-scale-on-demand":
                    result = handleAutoScaleOnDemand(jsonString);
                    break;
                case "migrate-low-priority":
                    result = handleMigrateLowPriority(jsonString);
                    break;
                case "sync-data-models":
                    result = handleSyncDataModels(jsonString);
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

    private Map<String, Object> handleGeoOptimalSelection(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "地理最优选择成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleAutoMergeTasks(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "任务自动合并成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleMonitorLoadStatus(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "负载状态监控成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleDistributeMultiNodes(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "多节点分发成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleCustomizeStrategy(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "自定义策略成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleAllocateByBusinessPriority(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "按业务优先级分配成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleVisualizeLoadDistribution(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "负载分布可视化成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleVisualizeLoadStatus(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "负载状态可视化成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleAutoScaleOnDemand(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "按需自动扩展成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleMigrateLowPriority(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "低优先级迁移成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleSyncDataModels(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "数据模型同步成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private String toJson(Map<String, Object> result) {
        return gson.toJson(result);
    }
}