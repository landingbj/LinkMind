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
 * I/O任务调度Mock接口
 */
public class TaskIoMockServlet extends BaseServlet {
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
                case "allocate-ssd-storage":
                    result = handleAllocateSsdStorage(jsonString);
                    break;
                case "allocate-hdd-storage":
                    result = handleAllocateHddStorage(jsonString);
                    break;
                case "boost-for-high-io":
                    result = handleBoostForHighIo(jsonString);
                    break;
                case "set-flow-control-threshold":
                    result = handleSetFlowControlThreshold(jsonString);
                    break;
                case "monitor-iops-real-time":
                    result = handleMonitorIopsRealTime(jsonString);
                    break;
                case "enable-caching":
                    result = handleEnableCaching(jsonString);
                    break;
                case "allocate-dma-acceleration":
                    result = handleAllocateDmaAcceleration(jsonString);
                    break;
                case "merge-batch-processing":
                    result = handleMergeBatchProcessing(jsonString);
                    break;
                case "set-qos-policy":
                    result = handleSetQosPolicy(jsonString);
                    break;
                case "set-soft-resource-limit":
                    result = handleSetSoftResourceLimit(jsonString);
                    break;
                case "failover-to-backup":
                    result = handleFailoverToBackup(jsonString);
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

    private Map<String, Object> handleAllocateSsdStorage(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "SSD存储分配成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleAllocateHddStorage(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "HDD存储分配成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleBoostForHighIo(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "高I/O带宽提升成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleSetFlowControlThreshold(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "流量控制阈值设置成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleMonitorIopsRealTime(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "实时IOPS监控成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleEnableCaching(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "缓存启用成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleAllocateDmaAcceleration(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "DMA加速分配成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleMergeBatchProcessing(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "批量处理合并成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleSetQosPolicy(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "QoS策略设置成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleSetSoftResourceLimit(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "软资源限制设置成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleFailoverToBackup(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "故障切换至备份成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private String toJson(Map<String, Object> result) {
        return gson.toJson(result);
    }
}