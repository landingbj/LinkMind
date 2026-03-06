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
 * 点播推理服务资源管理Mock接口
 */
public class VodResourceMockServlet extends BaseServlet {
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
                case "vod-cpu-resource-support":
                    result = handleVodCpuResourceSupport(jsonString);
                    break;
                case "vod-memory-resource-support":
                    result = handleVodMemoryResourceSupport(jsonString);
                    break;
                case "preprocess-compress":
                    result = handlePreprocessCompress(jsonString);
                    break;
                case "schedule-timed-delay":
                    result = handleScheduleTimedDelay(jsonString);
                    break;
                case "allocate-by-priority":
                    result = handleAllocateByPriority(jsonString);
                    break;
                case "merge-gpu":
                    result = handleMergeGpu(jsonString);
                    break;
                case "schedule-off-peak":
                    result = handleScheduleOffPeak(jsonString);
                    break;
                case "adjust-storage-io":
                    result = handleAdjustStorageIo(jsonString);
                    break;
                case "allocate-offline":
                    result = handleAllocateOffline(jsonString);
                    break;
                case "allocate-edge-by-region":
                    result = handleAllocateEdgeByRegion(jsonString);
                    break;
                case "allocate-by-cpu-threshold":
                    result = handleAllocateByCpuThreshold(jsonString);
                    break;
                case "queue-grading":
                    result = handleQueueGrading(jsonString);
                    break;
                case "use-shared-cpu-pool":
                    result = handleUseSharedCpuPool(jsonString);
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

    private Map<String, Object> handleVodCpuResourceSupport(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "点播CPU资源支持成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleVodMemoryResourceSupport(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "点播内存资源支持成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handlePreprocessCompress(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "预处理压缩成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleScheduleTimedDelay(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "定时延迟调度成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleAllocateByPriority(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "按优先级分配成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleMergeGpu(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "GPU合并成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleScheduleOffPeak(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "错峰调度成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleAdjustStorageIo(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "存储IO调整成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleAllocateOffline(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "离线分配成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleAllocateEdgeByRegion(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "按区域边缘分配成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleAllocateByCpuThreshold(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "按CPU阈值分配成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleQueueGrading(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "队列分级成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleUseSharedCpuPool(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "共享CPU池使用成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private String toJson(Map<String, Object> result) {
        return gson.toJson(result);
    }
}