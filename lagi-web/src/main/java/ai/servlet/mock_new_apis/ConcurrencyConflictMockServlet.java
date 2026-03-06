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
 * 并发控制和冲突检测Mock接口
 */
public class ConcurrencyConflictMockServlet extends BaseServlet {
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
                case "set-mutex-lock":
                    result = handleSetMutexLock(jsonString);
                    break;
                case "set-semaphore":
                    result = handleSetSemaphore(jsonString);
                    break;
                case "set-read-write-lock":
                    result = handleSetReadWriteLock(jsonString);
                    break;
                case "set-optimistic-lock":
                    result = handleSetOptimisticLock(jsonString);
                    break;
                case "set-pessimistic-lock":
                    result = handleSetPessimisticLock(jsonString);
                    break;
                case "set-distributed-lock":
                    result = handleSetDistributedLock(jsonString);
                    break;
                case "set-atomic-operation":
                    result = handleSetAtomicOperation(jsonString);
                    break;
                case "set-transaction-isolation":
                    result = handleSetTransactionIsolation(jsonString);
                    break;
                case "set-deadlock-detection":
                    result = handleSetDeadlockDetection(jsonString);
                    break;
                case "set-resource-quota":
                    result = handleSetResourceQuota(jsonString);
                    break;
                case "set-timeout-mechanism":
                    result = handleSetTimeoutMechanism(jsonString);
                    break;
                case "set-retry-mechanism":
                    result = handleSetRetryMechanism(jsonString);
                    break;
                case "set-circuit-breaker":
                    result = handleSetCircuitBreaker(jsonString);
                    break;
                case "set-rate-limiting":
                    result = handleSetRateLimiting(jsonString);
                    break;
                case "set-backpressure":
                    result = handleSetBackpressure(jsonString);
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

    private Map<String, Object> handleSetMutexLock(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "互斥锁设置成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleSetSemaphore(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "信号量设置成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleSetReadWriteLock(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "读写锁设置成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleSetOptimisticLock(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "乐观锁设置成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleSetPessimisticLock(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "悲观锁设置成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleSetDistributedLock(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "分布式锁设置成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleSetAtomicOperation(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "原子操作设置成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleSetTransactionIsolation(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "事务隔离级别设置成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleSetDeadlockDetection(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "死锁检测设置成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleSetResourceQuota(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "资源配额设置成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleSetTimeoutMechanism(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "超时机制设置成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleSetRetryMechanism(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "重试机制设置成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleSetCircuitBreaker(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "熔断器设置成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleSetRateLimiting(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "限流设置成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private Map<String, Object> handleSetBackpressure(String jsonString) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "背压设置成功");
        result.put("data", gson.fromJson(jsonString, Map.class));
        return result;
    }

    private String toJson(Map<String, Object> result) {
        return gson.toJson(result);
    }
}