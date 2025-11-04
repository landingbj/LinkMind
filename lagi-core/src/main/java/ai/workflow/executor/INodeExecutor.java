package ai.workflow.executor;

import ai.workflow.pojo.Node;
import ai.workflow.pojo.NodeResult;
import ai.workflow.pojo.WorkflowContext;

import java.util.Collections;
import java.util.List;

/**
 * 节点执行器接口
 */
public interface INodeExecutor {
    /**
     * 执行节点
     */
    NodeResult execute(String taskId, Node node, WorkflowContext context) throws Exception;
    
    /**
     * 节点是否有效（可用）
     */
    default boolean isValid() {
        return true;
    }
    
    /**
     * 获取节点的标准输出字段名称列表
     * 用于工作流验证，确保节点引用使用正确的字段名
     * 
     * @return 标准输出字段列表，如 ["result"]、["intent"]、["statusCode", "body"]
     *         返回 null 表示该节点类型不需要验证输出字段（如 start、condition）
     */
    default List<String> getStandardOutputFields() {
        // 大多数节点默认输出 "result" 字段
        return Collections.singletonList("result");
    }
}