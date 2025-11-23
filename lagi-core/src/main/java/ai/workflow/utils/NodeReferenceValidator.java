package ai.workflow.utils;

import ai.workflow.executor.INodeExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.Queue;
import java.util.LinkedList;

/**
 * 节点引用验证工具类
 * 验证工作流JSON中的节点引用是否正确，特别是字段名是否使用了标准输出字段
 * 
 * 注意：本验证器通过 DefaultNodeEnum 动态获取节点的标准输出字段，
 * 新增节点类型时只需在节点执行器中实现 getStandardOutputFields() 方法即可
 */
@Slf4j
public class NodeReferenceValidator {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 验证工作流JSON中的所有引用
     * @param workflowJson 工作流JSON字符串
     * @return 验证结果，包含错误信息列表
     */
    public static ValidationResult validate(String workflowJson) {
        ValidationResult result = new ValidationResult();
        
        try {
            JsonNode root = objectMapper.readTree(workflowJson);
            JsonNode nodes = root.get("nodes");
            JsonNode edges = root.get("edges");
            
            if (nodes == null || !nodes.isArray()) {
                result.addError("Missing or invalid 'nodes' array");
                return result;
            }
            
            // 构建节点类型映射
            Map<String, String> nodeTypeMap = new HashMap<>();
            for (JsonNode node : nodes) {
                String nodeId = node.get("id").asText();
                String nodeType = node.get("type").asText();
                nodeTypeMap.put(nodeId, nodeType);
            }
            
            // 验证必须的 start 和 end 节点
            validateMandatoryNodes(nodeTypeMap, result);
            
            // 验证边的连接正确性
            validateEdgeConnections(nodes, edges, result);
            
            // 验证每个节点的引用
            for (JsonNode node : nodes) {
                String nodeId = node.get("id").asText();
                JsonNode data = node.get("data");
                
                if (data == null) {
                    continue;
                }
                
                // 验证 inputsValues 中的引用
                JsonNode inputsValues = data.get("inputsValues");
                if (inputsValues != null) {
                    validateInputsValues(inputsValues, nodeId, nodeTypeMap, result);
                }
            }
            
        } catch (Exception e) {
            result.addError("Failed to parse workflow JSON: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 验证工作流是否包含必需的 start 和 end 节点
     */
    private static void validateMandatoryNodes(Map<String, String> nodeTypeMap, ValidationResult result) {
        boolean hasStart = false;
        boolean hasEnd = false;
        
        for (Map.Entry<String, String> entry : nodeTypeMap.entrySet()) {
            String nodeType = entry.getValue();
            if ("start".equals(nodeType)) {
                hasStart = true;
            }
            if ("end".equals(nodeType)) {
                hasEnd = true;
            }
        }
        
        if (!hasStart) {
            result.addError("Workflow is missing required 'start' node. Every workflow must have at least one start node.");
        }
        
        if (!hasEnd) {
            result.addError("Workflow is missing required 'end' node. Every workflow must have at least one end node.");
        }
    }
    
    /**
     * 验证边的连接正确性
     * 检查：重复路径、环路、孤立节点、不可达节点
     */
    private static void validateEdgeConnections(JsonNode nodes, JsonNode edges, ValidationResult result) {
        if (nodes == null || edges == null) {
            return;
        }
        
        // 构建邻接表和入度表
        Map<String, List<String>> adjacencyList = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        Set<String> allNodeIds = new HashSet<>();
        
        // 初始化
        for (JsonNode node : nodes) {
            String nodeId = node.get("id").asText();
            allNodeIds.add(nodeId);
            adjacencyList.put(nodeId, new ArrayList<>());
            inDegree.put(nodeId, 0);
        }
        
        // 构建图
        for (JsonNode edge : edges) {
            String sourceId = edge.get("sourceNodeID").asText();
            String targetId = edge.get("targetNodeID").asText();
            
            adjacencyList.get(sourceId).add(targetId);
            inDegree.put(targetId, inDegree.get(targetId) + 1);
        }
        
        // 检查1：检测环路（使用拓扑排序）
        detectCycles(adjacencyList, inDegree, result);
        
        // 检查2：检测孤立节点（既没有入边也没有出边，且不是start或end）
        detectIsolatedNodes(nodes, adjacencyList, inDegree, result);
        
        // 检查3：检测重复路径（节点的上游和该节点都连接了同一下游）
        detectRedundantPaths(adjacencyList, result);
        
        // 检查4：检测不可达的end节点
        detectUnreachableEndNodes(nodes, adjacencyList, result);
    }
    
    /**
     * 检测环路（使用拓扑排序）
     */
    private static void detectCycles(Map<String, List<String>> adjacencyList, 
                                     Map<String, Integer> inDegree, ValidationResult result) {
        Map<String, Integer> inDegreeCopy = new HashMap<>(inDegree);
        Queue<String> queue = new LinkedList<>();
        int processedNodes = 0;
        
        // 将入度为0的节点加入队列
        for (Map.Entry<String, Integer> entry : inDegreeCopy.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }
        
        // 拓扑排序
        while (!queue.isEmpty()) {
            String current = queue.poll();
            processedNodes++;
            
            for (String neighbor : adjacencyList.get(current)) {
                inDegreeCopy.put(neighbor, inDegreeCopy.get(neighbor) - 1);
                if (inDegreeCopy.get(neighbor) == 0) {
                    queue.offer(neighbor);
                }
            }
        }
        
        // 如果处理的节点数少于总节点数，说明存在环
        if (processedNodes < adjacencyList.size()) {
            result.addError("Workflow contains cycles (circular dependencies). DFS execution will fail.");
        }
    }
    
    /**
     * 检测孤立节点
     */
    private static void detectIsolatedNodes(JsonNode nodes, Map<String, List<String>> adjacencyList, 
                                           Map<String, Integer> inDegree, ValidationResult result) {
        for (JsonNode node : nodes) {
            String nodeId = node.get("id").asText();
            String nodeType = node.get("type").asText();
            
            // start和end节点允许只有单向连接
            if ("start".equals(nodeType) || "end".equals(nodeType)) {
                continue;
            }
            
            boolean hasIncoming = inDegree.get(nodeId) > 0;
            boolean hasOutgoing = !adjacencyList.get(nodeId).isEmpty();
            
            if (!hasIncoming && !hasOutgoing) {
                result.addWarning(String.format(
                    "Node '%s' is isolated (no incoming or outgoing edges). This node will never be executed.",
                    nodeId
                ));
            }
        }
    }
    
    /**
     * 检测重复路径（节点A和节点B都指向节点C，且A→B存在）
     * 这会导致DFS执行时C被执行多次
     */
    private static void detectRedundantPaths(Map<String, List<String>> adjacencyList, ValidationResult result) {
        for (Map.Entry<String, List<String>> entry : adjacencyList.entrySet()) {
            String nodeA = entry.getKey();
            List<String> directChildren = entry.getValue();
            
            // 获取A的所有后代节点（可达节点）
            Set<String> allDescendants = new HashSet<>();
            findAllDescendants(nodeA, adjacencyList, allDescendants);
            
            // 检查A的直接子节点的子节点是否在A的直接子节点列表中
            for (String directChild : directChildren) {
                List<String> childChildren = adjacencyList.get(directChild);
                
                for (String grandChild : childChildren) {
                    if (directChildren.contains(grandChild)) {
                        result.addError(String.format(
                            "Redundant path detected: Node '%s' connects to '%s', and '%s' also connects to '%s'. " +
                            "This will cause '%s' to be executed multiple times in DFS traversal. " +
                            "Remove the edge '%s' → '%s'.",
                            nodeA, directChild, directChild, grandChild, grandChild, nodeA, grandChild
                        ));
                    }
                }
            }
        }
    }
    
    /**
     * 递归查找所有后代节点
     */
    private static void findAllDescendants(String node, Map<String, List<String>> adjacencyList, Set<String> descendants) {
        for (String child : adjacencyList.get(node)) {
            if (!descendants.contains(child)) {
                descendants.add(child);
                findAllDescendants(child, adjacencyList, descendants);
            }
        }
    }
    
    /**
     * 检测不可达的end节点
     */
    private static void detectUnreachableEndNodes(JsonNode nodes, Map<String, List<String>> adjacencyList, 
                                                  ValidationResult result) {
        // 找到所有start节点
        Set<String> startNodes = new HashSet<>();
        Set<String> endNodes = new HashSet<>();
        
        for (JsonNode node : nodes) {
            String nodeId = node.get("id").asText();
            String nodeType = node.get("type").asText();
            
            if ("start".equals(nodeType)) {
                startNodes.add(nodeId);
            } else if ("end".equals(nodeType)) {
                endNodes.add(nodeId);
            }
        }
        
        // 从start节点开始DFS，找到所有可达节点
        Set<String> reachable = new HashSet<>();
        for (String startNode : startNodes) {
            dfs(startNode, adjacencyList, reachable);
        }
        
        // 检查end节点是否可达
        for (String endNode : endNodes) {
            if (!reachable.contains(endNode)) {
                result.addError(String.format(
                    "End node '%s' is unreachable from start nodes. This indicates a disconnected workflow.",
                    endNode
                ));
            }
        }
    }
    
    /**
     * DFS遍历
     */
    private static void dfs(String node, Map<String, List<String>> adjacencyList, Set<String> visited) {
        if (visited.contains(node)) {
            return;
        }
        visited.add(node);
        
        for (String neighbor : adjacencyList.get(node)) {
            dfs(neighbor, adjacencyList, visited);
        }
    }
    
    /**
     * 验证 inputsValues 中的所有引用
     */
    private static void validateInputsValues(JsonNode inputsValues, String currentNodeId, 
                                             Map<String, String> nodeTypeMap, ValidationResult result) {
        inputsValues.fields().forEachRemaining(entry -> {
            String inputKey = entry.getKey();
            JsonNode value = entry.getValue();
            
            if (value.has("type") && "ref".equals(value.get("type").asText())) {
                JsonNode content = value.get("content");
                if (content != null && content.isArray() && content.size() == 2) {
                    String refNodeId = content.get(0).asText();
                    String refField = content.get(1).asText();
                    
                    // 验证引用的节点是否存在
                    if (!nodeTypeMap.containsKey(refNodeId)) {
                        result.addError(String.format(
                            "Node '%s' references non-existent node '%s' in field '%s'",
                            currentNodeId, refNodeId, inputKey
                        ));
                        return;
                    }
                    
                    // 验证引用的字段是否是标准字段
                    String refNodeType = nodeTypeMap.get(refNodeId);
                    validateFieldReference(currentNodeId, refNodeId, refField, refNodeType, result);
                }
            } else if (value.has("type") && "template".equals(value.get("type").asText())) {
                // 验证模板中的引用
                JsonNode content = value.get("content");
                if (content != null && content.isTextual()) {
                    validateTemplateReferences(content.asText(), currentNodeId, nodeTypeMap, result);
                }
            }
        });
    }
    
    /**
     * 验证字段引用是否使用标准字段名
     */
    private static void validateFieldReference(String currentNodeId, String refNodeId, 
                                               String refField, String refNodeType, ValidationResult result) {
        if (!isStandardOutputField(refNodeType, refField)) {
            result.addWarning(String.format(
                "Node '%s' references non-standard field '%s.%s' (type: %s). Expected: %s",
                currentNodeId, refNodeId, refField, refNodeType,
                getExpectedFields(refNodeType)
            ));
        }
    }
    
    /**
     * 验证模板字符串中的引用（{{nodeId.field}} 格式）
     */
    private static void validateTemplateReferences(String template, String currentNodeId,
                                                   Map<String, String> nodeTypeMap, ValidationResult result) {
        // 匹配 {{nodeId.field}} 格式
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{\\{([^.}]+)\\.([^}]+)\\}\\}");
        java.util.regex.Matcher matcher = pattern.matcher(template);
        
        while (matcher.find()) {
            String refNodeId = matcher.group(1);
            String refField = matcher.group(2);
            
            // 验证引用的节点是否存在
            if (!nodeTypeMap.containsKey(refNodeId)) {
                result.addWarning(String.format(
                    "Node '%s' template references non-existent node '%s'",
                    currentNodeId, refNodeId
                ));
                continue;
            }
            
            // 验证引用的字段是否是标准字段
            String refNodeType = nodeTypeMap.get(refNodeId);
            validateFieldReference(currentNodeId, refNodeId, refField, refNodeType, result);
        }
    }
    
    /**
     * 检查字段是否是节点类型的标准输出字段
     * 通过 DefaultNodeEnum 动态获取节点执行器的标准输出字段定义
     */
    private static boolean isStandardOutputField(String nodeType, String field) {
        // 从 DefaultNodeEnum 获取节点执行器
        INodeExecutor executor = DefaultNodeEnum.getValidNodeExecutor(nodeType);
        if (executor == null) {
            // 未知节点类型，不验证
            log.debug("Unknown node type: {}, skip validation", nodeType);
            return true;
        }
        
        // 获取标准输出字段
        List<String> standardFields = executor.getStandardOutputFields();
        if (standardFields == null) {
            // 返回 null 表示该节点不需要验证输出字段（如 start、condition、end）
            return true;
        }
        
        return standardFields.contains(field);
    }
    
    /**
     * 获取节点类型的期望字段列表（用于错误提示）
     * 通过 DefaultNodeEnum 动态获取节点执行器的标准输出字段定义
     */
    private static String getExpectedFields(String nodeType) {
        INodeExecutor executor = DefaultNodeEnum.getValidNodeExecutor(nodeType);
        if (executor == null) {
            return "unknown";
        }
        
        List<String> standardFields = executor.getStandardOutputFields();
        if (standardFields == null) {
            return "dynamic or none";
        }
        
        return String.join(", ", standardFields);
    }
    
    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        
        public void addError(String error) {
            errors.add(error);
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
        
        public List<String> getErrors() {
            return errors;
        }
        
        public List<String> getWarnings() {
            return warnings;
        }
        
        public boolean isValid() {
            return errors.isEmpty();
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (!errors.isEmpty()) {
                sb.append("Errors:\n");
                for (String error : errors) {
                    sb.append("  - ").append(error).append("\n");
                }
            }
            if (!warnings.isEmpty()) {
                sb.append("Warnings:\n");
                for (String warning : warnings) {
                    sb.append("  - ").append(warning).append("\n");
                }
            }
            if (errors.isEmpty() && warnings.isEmpty()) {
                sb.append("Validation passed!");
            }
            return sb.toString();
        }
    }
}

