package ai.workflow.executor;

import ai.workflow.TaskStatusManager;
import ai.workflow.exception.WorkflowException;
import ai.workflow.pojo.Node;
import ai.workflow.pojo.NodeResult;
import ai.workflow.pojo.TaskReportOutput;
import ai.workflow.pojo.WorkflowContext;
import ai.workflow.utils.NodeExecutorUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 条件节点执行器
 */
public class ConditionNodeExecutor implements INodeExecutor {

    private final TaskStatusManager taskStatusManager = TaskStatusManager.getInstance();

    @Override
    public NodeResult execute(String taskId, Node node, WorkflowContext context) throws Exception {
        long startTime = System.currentTimeMillis();
        String nodeId = node.getId();
        taskStatusManager.updateNodeReport(taskId, nodeId, "processing", startTime, null, null, null);
        NodeResult nodeResult = null;
        try {
            JsonNode data = node.getData();
            JsonNode conditions = data.get("conditions");

            if (conditions == null || !conditions.isArray()) {
                throw new WorkflowException("条件节点缺少条件配置");
            }

            List<Map<String, Object>> matchedConditions = new ArrayList<>();

            for (JsonNode condition : conditions) {
                String key = condition.get("key").asText();
                JsonNode value = condition.get("value");

                if (evaluateCondition(value, context)) {
                    Map<String, Object> conditionResult = new HashMap<>();
                    conditionResult.put("key", key);
                    conditionResult.put("matched", true);
                    conditionResult.put("condition", condition);
                    matchedConditions.add(conditionResult);
                } else {
                    Map<String, Object> conditionResult = new HashMap<>();
                    conditionResult.put("key", key);
                    conditionResult.put("matched", false);
                    conditionResult.put("condition", condition);
                    matchedConditions.add(conditionResult);
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("conditions", matchedConditions);
            result.put("matchedCount", matchedConditions.stream()
                    .filter(c -> (Boolean) c.get("matched"))
                    .count());

            List<String> outputPorts = matchedConditions.stream()
                    .filter(c -> (Boolean) c.get("matched"))
                    .map(c -> (String) c.get("key"))
                    .collect(Collectors.toList());

            // 确定最终的分支
            String finalBranch = determineFinalBranch(outputPorts);
            NodeExecutorUtil.sleep();
            long endTime = System.currentTimeMillis();
            long timeCost = endTime - startTime;
            // 获取输入数据
            Map<String, Object> inputData = getInputDataMap(context);
            TaskReportOutput.Snapshot snapshot = taskStatusManager.createNodeSnapshot(nodeId, inputData, inputData, finalBranch, null);
            taskStatusManager.updateNodeReport(taskId, nodeId, "succeeded", startTime, endTime, timeCost, snapshot);
            taskStatusManager.addExecutionLog(taskId, nodeId, "条件节点执行成功，匹配条件: " + outputPorts, startTime);
            nodeResult = new NodeResult(node.getType(), node.getId(), result, outputPorts);
        } catch (Exception e) {
            NodeExecutorUtil.handleException(taskId, nodeId, startTime, "条件节点", e);
        }
        return nodeResult;
    }

    /**
     * 确定最终的分支
     * @param outputPorts 匹配的输出端口列表
     * @return 最终分支名称
     */
    private String determineFinalBranch(List<String> outputPorts) {
        if (outputPorts.isEmpty()) {
            return "default";
        } else if (outputPorts.size() == 1) {
            return outputPorts.get(0);
        } else {
            return String.join(", ", outputPorts);
        }
    }

    /**
     * 获取输入数据映射
     */
    private Map<String, Object> getInputDataMap(WorkflowContext context) {
        Map<String, Object> inputData = new HashMap<>();
        inputData.putAll(context.getAllNodeResults());
        return inputData;
    }

    private boolean evaluateCondition(JsonNode conditionValue, WorkflowContext context) {
        if (conditionValue.has("left") && conditionValue.has("operator") && conditionValue.has("right")) {
            JsonNode left = conditionValue.get("left");
            String operator = conditionValue.get("operator").asText();
            JsonNode right = conditionValue.get("right");
            Object leftValue = resolveValue(left, context);
            Object rightValue = resolveValue(right, context);
            return evaluateOperator(leftValue, operator, rightValue);
        } else {
            return evaluateDirectCondition(conditionValue, context);
        }
    }

    private boolean evaluateDirectCondition(JsonNode conditionValue, WorkflowContext context) {
        if (conditionValue.has("type")) {
            String type = conditionValue.get("type").asText();
            if ("ref".equals(type)) {
                JsonNode content = conditionValue.get("content");
                if (content.isArray() && content.size() == 2) {
                    String nodeId = content.get(0).asText();
                    String field = content.get(1).asText();

                    Object nodeResult = context.getNodeResult(nodeId);
                    if (nodeResult instanceof Map) {
                        Object value = ((Map<?, ?>) nodeResult).get(field);
                        return Boolean.TRUE.equals(value);
                    }
                }
            }
        }
        return false;
    }

    private Object resolveValue(JsonNode valueNode, WorkflowContext context) {
        String type = valueNode.get("type").asText();

        if ("ref".equals(type)) {
            JsonNode content = valueNode.get("content");
            if (content.isArray() && content.size() == 2) {
                String nodeId = content.get(0).asText();
                String field = content.get(1).asText();

                Object nodeResult = context.getNodeResult(nodeId);
                if (nodeResult instanceof Map) {
                    return ((Map<?, ?>) nodeResult).get(field);
                }
            }
        } else if ("constant".equals(type)) {
            JsonNode content = valueNode.get("content");
            if (content.isTextual()) {
                return content.asText();
            } else if (content.isNumber()) {
                return content.asDouble();
            } else if (content.isBoolean()) {
                return content.asBoolean();
            }
        }

        return null;
    }

    private boolean evaluateOperator(Object left, String operator, Object right) {
        switch (operator) {
            case "eq":
                return Objects.equals(left, right);
            case "neq":
                return !Objects.equals(left, right);
            case "gt":
                return compareNumbers(left, right) > 0;
            case "gte":
                return compareNumbers(left, right) >= 0;
            case "lt":
                return compareNumbers(left, right) < 0;
            case "lte":
                return compareNumbers(left, right) <= 0;
            case "in":
                return evaluateInOperator(left, right);
            case "nin":
                return !evaluateInOperator(left, right);
            case "contains":
                return left != null && right != null && left.toString().contains(right.toString());
            case "not_contains":
                return left == null || right == null || !left.toString().contains(right.toString());
            case "is_empty":
                return isEmpty(left);
            case "is_not_empty":
                return !isEmpty(left);
            case "is_true":
                return Boolean.TRUE.equals(left);
            case "is_false":
                return Boolean.FALSE.equals(left);
            default:
                return false;
        }
    }

    /**
     * 检查值是否为空
     */
    private boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String) {
            return ((String) value).trim().isEmpty();
        }
        if (value instanceof Collection) {
            return ((Collection<?>) value).isEmpty();
        }
        if (value instanceof Map) {
            return ((Map<?, ?>) value).isEmpty();
        }
        return false;
    }

    /**
     * 检查值是否在集合中
     */
    private boolean evaluateInOperator(Object left, Object right) {
        if (left == null || right == null) {
            return false;
        }
        
        if (right instanceof Collection) {
            return ((Collection<?>) right).contains(left);
        }
        
        if (right instanceof String) {
            // 尝试将字符串解析为数组
            try {
                String[] items = right.toString().split(",");
                for (String item : items) {
                    if (Objects.equals(left.toString(), item.trim())) {
                        return true;
                    }
                }
            } catch (Exception e) {
                // 如果解析失败，直接比较字符串
                return Objects.equals(left.toString(), right.toString());
            }
        }
        
        return Objects.equals(left, right);
    }

    private int compareNumbers(Object left, Object right) {
        double leftNum = Double.parseDouble(left.toString());
        double rightNum = Double.parseDouble(right.toString());
        return Double.compare(leftNum, rightNum);
    }
}
