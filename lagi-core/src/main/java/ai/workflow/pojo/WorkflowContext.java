package ai.workflow.pojo;

import lombok.Getter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作流执行上下文
 */
public class WorkflowContext {
    @Getter
    private final Map<String, Object> inputData;
    private final Map<String, Object> nodeResults;
    private final Map<String, Object> variables;
    @Getter
    private final Map<String, Object> memories;
    
    public WorkflowContext(Map<String, Object> inputData) {
        this.inputData = inputData != null ? inputData : new ConcurrentHashMap<>();
        this.nodeResults = new ConcurrentHashMap<>();
        this.variables = new ConcurrentHashMap<>();
        this.memories = new ConcurrentHashMap<>();
    }

    public WorkflowContext(Map<String, Object> inputData, Map<String, Object> memories) {
        this.inputData = inputData != null ? inputData : new ConcurrentHashMap<>();
        this.nodeResults = new ConcurrentHashMap<>();
        this.variables = new ConcurrentHashMap<>();
        this.memories =  memories != null ? memories : new ConcurrentHashMap<>();
    }
    
    public Object getInputData(String key) {
        return inputData.get(key);
    }

    public void setNodeResult(String nodeId, Object result) {
        nodeResults.put(nodeId, result);
    }
    
    public Object getNodeResult(String nodeId) {
        return nodeResults.get(nodeId);
    }
    
    public void setVariable(String key, Object value) {
        variables.put(key, value);
    }
    
    public Object getVariable(String key) {
        return variables.get(key);
    }
    
    public Map<String, Object> getAllNodeResults() {
        return new ConcurrentHashMap<>(nodeResults);
    }
    
    /**
     * 解析变量引用，支持 {{nodeId.field}} 格式
     */
    public Object resolveReference(String reference) {
        if (reference == null || !reference.startsWith("{{") || !reference.endsWith("}}")) {
            return reference;
        }
        
        String refPath = reference.substring(2, reference.length() - 2);
        String[] parts = refPath.split("\\.");
        
        if (parts.length != 2) {
            return reference;
        }
        
        String nodeId = parts[0];
        String field = parts[1];
        
        Object nodeResult = getNodeResult(nodeId);
        if (nodeResult instanceof Map) {
            return ((Map<?, ?>) nodeResult).get(field);
        }
        
        return reference;
    }
}