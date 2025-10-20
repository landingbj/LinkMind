package ai.workflow.pojo;

import lombok.Getter;

import java.util.List;
import java.util.Arrays;

/**
 * 节点执行结果
 */
@Getter
public class NodeResult {
    private final String nodeType;
    private final String nodeId;
    private final Object data;
    private final List<String> outputPorts;
    
    public NodeResult(String nodeType, String nodeId, Object data, List<String> outputPorts) {
        this.data = data;
        this.outputPorts = outputPorts;
        this.nodeType = nodeType;
        this.nodeId = nodeId;
    }

    // 为了向后兼容，保留获取单个输出端口的方法
    public String getOutputPort() {     return outputPorts != null && !outputPorts.isEmpty() ? outputPorts.get(0) : null; 
    }
}