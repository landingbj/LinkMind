package ai.workflow.executor;

import ai.workflow.pojo.Node;
import ai.workflow.pojo.NodeResult;
import ai.workflow.pojo.WorkflowContext;

public interface INodeExecutor {
    NodeResult execute(String taskId, Node node, WorkflowContext context) throws Exception;
    default boolean isValid() {
        return true;
    }
}