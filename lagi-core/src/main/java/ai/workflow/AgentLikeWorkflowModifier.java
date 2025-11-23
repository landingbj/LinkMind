package ai.workflow;

import ai.llm.service.CompletionsService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.utils.JsonExtractor;
import ai.utils.ResourceUtil;
import ai.workflow.utils.DefaultNodeEnum;
import ai.workflow.utils.NodeReferenceValidator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Agent-like Workflow Modifier
 * 使用智能体式推理一步步修改现有工作流
 *
 * 核心特性:
 * 1. 意图理解: 分析用户想要对工作流做什么修改
 * 2. 节点匹配: 根据可用节点类型选择合适的节点
 * 3. 依赖分析: 分析节点间的输入输出依赖关系
 * 4. 逐步执行: 一步步地添加、删除、修改节点和边
 * 5. 自动调整: 添加/删除节点时自动调整相关节点的引用
 * 6. 自我验证: 每次修改后验证工作流的完整性
 */
@Slf4j
public class AgentLikeWorkflowModifier {

    private static final CompletionsService completionsService = new CompletionsService();
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    // 节点信息缓存
    private static Map<String, NodeInfo> nodeDefinitions = null;

    private static final double DEFAULT_TEMPERATURE = 0.3;
    private static final int DEFAULT_MAX_TOKENS = 4096;
    private static final int MAX_MODIFICATION_STEPS = 30;

    /**
     * 节点定义信息
     */
    private static class NodeInfo {
        String nodeType;
        String description;      // 节点描述
        String specification;    // 节点详细规范
        String example;         // 节点示例JSON
        
        NodeInfo(String nodeType, String description, String specification, String example) {
            this.nodeType = nodeType;
            this.description = description;
            this.specification = specification;
            this.example = example;
        }
    }

    /**
     * 工作流修改上下文
     */
    private static class ModificationContext {
        String userText;
        List<ChatMessage> history;
        List<String> docsContents;
        JsonObject currentWorkflow;
        Map<String, NodeInfo> availableNodes;  // 可用节点信息

        // 修改过程中的中间结果
        String modificationPlan;        // 修改计划
        List<String> executedSteps;     // 已执行的步骤
        int stepCount;                  // 步骤计数

        ModificationContext(String userText, List<ChatMessage> history, 
                          List<String> docsContents, String workflowJson) {
            this.userText = userText;
            this.history = history != null ? history : Collections.emptyList();
            this.docsContents = docsContents != null ? docsContents : Collections.emptyList();
            this.currentWorkflow = gson.fromJson(workflowJson, JsonObject.class);
            this.availableNodes = loadNodeDefinitions();
            this.executedSteps = new ArrayList<>();
            this.stepCount = 0;
        }
    }

    /**
     * 加载节点定义（使用缓存）
     */
    private static synchronized Map<String, NodeInfo> loadNodeDefinitions() {
        if (nodeDefinitions != null) {
            return nodeDefinitions;
        }

        log.info("Loading node definitions from resources");
        nodeDefinitions = new HashMap<>();
        
        try {
            // 从 prompts/nodes 目录加载所有节点定义
            Map<String, String> nodeFiles = ResourceUtil.loadMultipleFromDirectory(
                    "/prompts/nodes", "node_", ".md");
            
            List<String> ignoreNodes = DefaultNodeEnum.getNotValidNodeName();
            
            for (Map.Entry<String, String> entry : nodeFiles.entrySet()) {
                String nodeType = entry.getKey();
                
                // 跳过无效节点
                if (ignoreNodes.contains(nodeType)) {
                    continue;
                }
                
                String content = entry.getValue();
                
                // 解析节点文件：使用 "=====" 作为分隔符
                String[] parts = content.split("={5,100}");
                
                if (parts.length >= 2) {
                    String description = parts[0].trim();
                    String specification = parts[1].trim();
                    
                    // 提取示例JSON（如果有）
                    String example = "";
                    if (specification.contains("```json")) {
                        int start = specification.indexOf("```json") + 7;
                        int end = specification.indexOf("```", start);
                        if (end > start) {
                            example = specification.substring(start, end).trim();
                        }
                    }
                    
                    NodeInfo nodeInfo = new NodeInfo(nodeType, description, specification, example);
                    nodeDefinitions.put(nodeType, nodeInfo);
                    log.debug("Loaded node definition: {}", nodeType);
                }
            }
            
            log.info("Loaded {} node definitions", nodeDefinitions.size());
            
        } catch (Exception e) {
            log.error("Failed to load node definitions: {}", e.getMessage(), e);
            nodeDefinitions = new HashMap<>();
        }
        
        return nodeDefinitions;
    }

    /**
     * 构建节点信息描述（用于提示词）
     */
    private static String buildNodeInfoDescription(Map<String, NodeInfo> nodes) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 可用节点类型\n\n");
        
        int index = 1;
        for (NodeInfo node : nodes.values()) {
            sb.append("### ").append(index++).append(". ").append(node.description).append("\n\n");
        }
        
        return sb.toString();
    }

    /**
     * 获取特定节点的详细规范
     */
    private static String getNodeSpecification(String nodeType, Map<String, NodeInfo> nodes) {
        NodeInfo nodeInfo = nodes.get(nodeType);
        if (nodeInfo == null) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(nodeType).append(" 节点规范\n\n");
        sb.append(nodeInfo.specification).append("\n");
        
        return sb.toString();
    }

    /**
     * 主入口：修改工作流 JSON
     *
     * @param text 用户输入文本
     * @param history 对话历史
     * @param docsContents 文档内容
     * @param lastWorkflow 上一个工作流JSON
     * @return 修改后的工作流 JSON 字符串
     */
    public static String modify(String text, List<ChatMessage> history, 
                               List<String> docsContents, String lastWorkflow) {
        log.info("Starting agent-like workflow modification");
        log.info("User modification request: {}", text);

        ModificationContext context = new ModificationContext(text, history, docsContents, lastWorkflow);

        try {
            // Stage 1: 分析修改需求
            stage1_AnalyzeModificationRequest(context);

            // Stage 2: 逐步执行修改
            stage2_ExecuteModifications(context);

            // Stage 3: 验证并返回结果
            String modifiedWorkflow = stage3_ValidateAndReturn(context);

            log.info("Workflow modification completed successfully");
            return modifiedWorkflow;

        } catch (Exception e) {
            log.error("Failed to modify workflow: {}", e.getMessage(), e);
            throw new RuntimeException("Workflow modification failed", e);
        }
    }

    /**
     * 分析节点依赖关系
     * 返回：哪些节点引用了指定节点的输出
     */
    private static List<String> analyzeNodeDependencies(JsonObject workflow, String nodeId) {
        List<String> dependentNodes = new ArrayList<>();
        
        if (!workflow.has("nodes")) {
            return dependentNodes;
        }
        
        JsonArray nodes = workflow.getAsJsonArray("nodes");
        
        for (JsonElement nodeElement : nodes) {
            JsonObject node = nodeElement.getAsJsonObject();
            String currentNodeId = node.get("id").getAsString();
            
            // 跳过自己
            if (currentNodeId.equals(nodeId)) {
                continue;
            }
            
            // 检查节点的 inputsValues 是否引用了目标节点
            if (node.has("data") && node.getAsJsonObject("data").has("inputsValues")) {
                JsonObject inputsValues = node.getAsJsonObject("data").getAsJsonObject("inputsValues");
                
                if (hasReferenceToNode(inputsValues, nodeId)) {
                    dependentNodes.add(currentNodeId);
                }
            }
        }
        
        return dependentNodes;
    }
    
    /**
     * 获取节点引用的详细信息
     * 返回：节点引用了哪些其他节点的哪些字段
     */
    private static Map<String, List<String>> getNodeReferences(JsonObject node) {
        Map<String, List<String>> references = new HashMap<>();
        
        if (!node.has("data") || !node.getAsJsonObject("data").has("inputsValues")) {
            return references;
        }
        
        JsonObject inputsValues = node.getAsJsonObject("data").getAsJsonObject("inputsValues");
        collectReferences(inputsValues, references);
        
        return references;
    }
    
    /**
     * 递归收集所有引用
     */
    private static void collectReferences(JsonElement element, Map<String, List<String>> references) {
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            
            // 检查是否是引用类型
            if (obj.has("type") && obj.has("content")) {
                String type = obj.get("type").getAsString();
                
                if ("ref".equals(type)) {
                    JsonElement content = obj.get("content");
                    if (content.isJsonArray()) {
                        JsonArray arr = content.getAsJsonArray();
                        if (arr.size() >= 2) {
                            String refNodeId = arr.get(0).getAsString();
                            String refField = arr.get(1).getAsString();
                            references.computeIfAbsent(refNodeId, k -> new ArrayList<>()).add(refField);
                        }
                    }
                } else if ("template".equals(type)) {
                    String content = obj.get("content").getAsString();
                    // 解析模板中的 {{nodeId.field}} 引用
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{\\{([^.]+)\\.([^}]+)\\}\\}");
                    java.util.regex.Matcher matcher = pattern.matcher(content);
                    while (matcher.find()) {
                        String refNodeId = matcher.group(1);
                        String refField = matcher.group(2);
                        references.computeIfAbsent(refNodeId, k -> new ArrayList<>()).add(refField);
                    }
                }
            }
            
            // 递归检查所有字段
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                collectReferences(entry.getValue(), references);
            }
        } else if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                collectReferences(item, references);
            }
        }
    }

    /**
     * 递归检查 JSON 对象是否包含对指定节点的引用
     */
    private static boolean hasReferenceToNode(JsonElement element, String nodeId) {
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            
            // 检查是否是引用类型
            if (obj.has("type") && obj.has("content")) {
                String type = obj.get("type").getAsString();
                
                if ("ref".equals(type)) {
                    JsonElement content = obj.get("content");
                    if (content.isJsonArray()) {
                        JsonArray arr = content.getAsJsonArray();
                        if (arr.size() > 0 && arr.get(0).getAsString().equals(nodeId)) {
                            return true;
                        }
                    }
                } else if ("template".equals(type)) {
                    String content = obj.get("content").getAsString();
                    // 检查模板中是否包含 {{nodeId.xxx}} 的引用
                    if (content.contains("{{" + nodeId + ".")) {
                        return true;
                    }
                }
            }
            
            // 递归检查所有字段
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                if (hasReferenceToNode(entry.getValue(), nodeId)) {
                    return true;
                }
            }
        } else if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                if (hasReferenceToNode(item, nodeId)) {
                    return true;
                }
            }
        }
        
        return false;
    }

    /**
     * 获取工作流的依赖关系摘要
     */
    private static String buildDependencySummary(ModificationContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 当前工作流节点依赖关系\n\n");
        
        JsonArray nodes = context.currentWorkflow.getAsJsonArray("nodes");
        
        for (JsonElement nodeElement : nodes) {
            JsonObject node = nodeElement.getAsJsonObject();
            String nodeId = node.get("id").getAsString();
            String nodeType = node.get("type").getAsString();
            
            List<String> dependencies = analyzeNodeDependencies(context.currentWorkflow, nodeId);
            
            if (!dependencies.isEmpty()) {
                sb.append("- **").append(nodeId).append("** (").append(nodeType).append(") 被以下节点引用：")
                  .append(String.join(", ", dependencies)).append("\n");
            }
        }
        
        if (sb.length() == 0) {
            sb.append("暂无节点依赖关系\n");
        }
        
        return sb.toString();
    }

    /**
     * Stage 1: 分析修改需求（包含节点匹配）
     */
    private static void stage1_AnalyzeModificationRequest(ModificationContext context) {
        log.info("Stage 1: Analyzing modification request and matching nodes");

        String prompt = buildModificationAnalysisPrompt(context);
        String input = buildModificationInput(context);

        ChatCompletionRequest request = completionsService.getCompletionsRequest(
                prompt,
                input,
                DEFAULT_TEMPERATURE,
                2048
        );

        ChatCompletionResult result = completionsService.completions(request);
        String analysisResult = result.getChoices().get(0).getMessage().getContent();

        log.info("Modification analysis result: {}", analysisResult);
        context.modificationPlan = analysisResult;
    }

    /**
     * Stage 2: 逐步执行修改
     * 使用智能体式对话，让LLM一步步指导修改
     */
    private static void stage2_ExecuteModifications(ModificationContext context) {
        log.info("Stage 2: Executing modifications step by step");

        String systemPrompt = buildStepByStepModificationPrompt();
        List<ChatMessage> messages = new ArrayList<>();
        
        // 系统消息
        messages.add(ChatMessage.builder()
                .role("system")
                .content(systemPrompt)
                .build());
        
        // 初始用户请求
        String initialRequest = buildInitialModificationRequest(context);
        messages.add(ChatMessage.builder()
                .role("user")
                .content(initialRequest)
                .build());

        int maxSteps = MAX_MODIFICATION_STEPS;
        
        while (maxSteps-- > 0) {
            context.stepCount++;
            log.info("Modification step {}", context.stepCount);

            ChatCompletionRequest request = new ChatCompletionRequest();
            request.setMessages(messages);
            request.setTemperature(DEFAULT_TEMPERATURE);
            request.setMax_tokens(DEFAULT_MAX_TOKENS);

            ChatCompletionResult result = completionsService.completions(request);
            String responseContent = result.getChoices().get(0).getMessage().getContent();

            log.debug("Step {} response: {}", context.stepCount, responseContent);

            // 提取 JSON 响应
            String jsonResponse = JsonExtractor.extractJson(responseContent);
            if (jsonResponse == null || jsonResponse.isEmpty()) {
                log.warn("Step {}: No valid JSON response, retrying", context.stepCount);
                continue;
            }

            JsonObject stepResponse = gson.fromJson(jsonResponse, JsonObject.class);

            // 检查是否完成
            boolean finished = stepResponse.has("finished") && stepResponse.get("finished").getAsBoolean();

            // 检查是否请求节点规范
            String requestedNodeType = null;
            if (stepResponse.has("request_node_spec")) {
                requestedNodeType = stepResponse.get("request_node_spec").getAsString();
            }
            
            // 记录操作
            if (stepResponse.has("action")) {
                String action = stepResponse.get("action").getAsString();
                log.info("Step {}: Action = {}", context.stepCount, action);
                
                // 执行操作
                boolean success = executeModificationAction(context, stepResponse);
                
                if (success) {
                    String description = stepResponse.has("description") 
                            ? stepResponse.get("description").getAsString() 
                            : action;
                    context.executedSteps.add(description);
                    log.info("Step {} completed successfully: {}", context.stepCount, description);
                }
            }

            // 如果完成，退出循环
            if (finished) {
                log.info("All modifications completed after {} steps", context.stepCount);
                break;
            }

            // 添加助手响应到对话历史
            messages.add(ChatMessage.builder()
                    .role("assistant")
                    .content(responseContent)
                    .build());

            // 添加当前工作流状态反馈（如果请求了节点规范，则包含节点规范）
            String feedback = buildWorkflowStateFeedback(context, requestedNodeType);
            messages.add(ChatMessage.builder()
                    .role("user")
                    .content(feedback)
                    .build());
        }

        if (maxSteps <= 0) {
            log.warn("Maximum modification steps reached");
        }
    }

    /**
     * 执行具体的修改操作
     */
    private static boolean executeModificationAction(ModificationContext context, JsonObject actionObj) {
        try {
            String action = actionObj.get("action").getAsString();

            switch (action.toLowerCase()) {
                case "add_node":
                    return addNode(context, actionObj);
                case "delete_node":
                    return deleteNode(context, actionObj);
                case "update_node":
                    return updateNode(context, actionObj);
                case "move_node":
                    return moveNode(context, actionObj);
                case "add_edge":
                    return addEdge(context, actionObj);
                case "delete_edge":
                    return deleteEdge(context, actionObj);
                case "update_edge":
                    return updateEdge(context, actionObj);
                default:
                    log.warn("Unknown action: {}", action);
                    return false;
            }
        } catch (Exception e) {
            log.error("Failed to execute modification action: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 添加节点
     * 支持两种模式：
     * 1. 简单添加：只添加节点，不处理边（需要后续手动添加边）
     * 2. 插入模式：在两个节点之间插入新节点（自动更新边）
     */
    private static boolean addNode(ModificationContext context, JsonObject actionObj) {
        if (!actionObj.has("node")) {
            log.warn("add_node action missing 'node' field");
            return false;
        }

        JsonElement nodeElement = actionObj.get("node");
        JsonArray nodes = context.currentWorkflow.getAsJsonArray("nodes");
        JsonArray edges = context.currentWorkflow.getAsJsonArray("edges");
        
        // 检查节点ID是否已存在
        String nodeId = nodeElement.getAsJsonObject().get("id").getAsString();
        for (JsonElement existingNode : nodes) {
            if (existingNode.getAsJsonObject().get("id").getAsString().equals(nodeId)) {
                log.warn("Node with id {} already exists, updating instead", nodeId);
                return updateNode(context, actionObj);
            }
        }
        
        // 添加节点
        nodes.add(nodeElement);
        
        // 检查是否是插入模式（在两个节点之间插入）
        if (actionObj.has("insertBetween")) {
            JsonObject insertInfo = actionObj.getAsJsonObject("insertBetween");
            String prevNodeId = insertInfo.get("previousNode").getAsString();
            String nextNodeId = insertInfo.get("nextNode").getAsString();
            
            log.info("Inserting node {} between {} and {}", nodeId, prevNodeId, nextNodeId);
            
            // 查找并更新 prevNode -> nextNode 的边，改为 prevNode -> newNode
            boolean edgeUpdated = false;
            for (JsonElement edgeElement : edges) {
                JsonObject edge = edgeElement.getAsJsonObject();
                if (edge.get("sourceNodeID").getAsString().equals(prevNodeId) &&
                    edge.get("targetNodeID").getAsString().equals(nextNodeId)) {
                    // 更新边的目标为新节点
                    edge.addProperty("targetNodeID", nodeId);
                    edgeUpdated = true;
                    log.info("Updated edge: {} -> {} (was {} -> {})", prevNodeId, nodeId, prevNodeId, nextNodeId);
                    break;
                }
            }
            
            if (!edgeUpdated) {
                log.warn("Edge {} -> {} not found, will add new edge instead", prevNodeId, nextNodeId);
                // 如果原边不存在，创建新边
                JsonObject newEdge = new JsonObject();
                newEdge.addProperty("sourceNodeID", prevNodeId);
                newEdge.addProperty("targetNodeID", nodeId);
                edges.add(newEdge);
            }
            
            // 添加新边：newNode -> nextNode
            JsonObject newEdge = new JsonObject();
            newEdge.addProperty("sourceNodeID", nodeId);
            newEdge.addProperty("targetNodeID", nextNodeId);
            edges.add(newEdge);
            log.info("Added edge: {} -> {}", nodeId, nextNodeId);
            
            log.info("Successfully inserted node {} between {} and {}", nodeId, prevNodeId, nextNodeId);
            
            // 提示可能需要利用新节点的输出
            context.executedSteps.add("已插入新节点 " + nodeId + " (类型: " + 
                    nodeElement.getAsJsonObject().get("type").getAsString() + ")");
            context.executedSteps.add("新节点位于 " + prevNodeId + " 和 " + nextNodeId + " 之间");
            context.executedSteps.add("提示：检查 " + nextNodeId + " 及后续节点是否可以利用 " + nodeId + " 的输出");
        } else {
            log.info("Added node: {} (Note: You need to add edges to connect this node)", nodeId);
            context.executedSteps.add("已添加新节点 " + nodeId + "，需要添加边来连接它");
        }
        
        return true;
    }

    /**
     * 删除节点（智能边管理）
     * 删除节点时：
     * 1. 删除从被删除节点出发的边（nodeId -> X）
     * 2. 更新指向被删除节点的边（Y -> nodeId），改为指向被删除节点的下一节点（Y -> X）
     */
    private static boolean deleteNode(ModificationContext context, JsonObject actionObj) {
        if (!actionObj.has("nodeId")) {
            log.warn("delete_node action missing 'nodeId' field");
            return false;
        }

        String nodeId = actionObj.get("nodeId").getAsString();
        
        // 检查依赖关系
        List<String> dependentNodes = analyzeNodeDependencies(context.currentWorkflow, nodeId);
        if (!dependentNodes.isEmpty()) {
            log.warn("Node {} is referenced by other nodes: {}", nodeId, dependentNodes);
            log.warn("Deleting this node may break the workflow. Dependent nodes need to be updated.");
            // 记录警告但继续执行，让LLM在后续步骤中处理依赖问题
        }
        
        JsonArray nodes = context.currentWorkflow.getAsJsonArray("nodes");
        JsonArray edges = context.currentWorkflow.getAsJsonArray("edges");

        // 不允许删除 start 和 end 节点
        JsonObject nodeToDelete = null;
        for (JsonElement nodeElement : nodes) {
            JsonObject node = nodeElement.getAsJsonObject();
            if (node.get("id").getAsString().equals(nodeId)) {
                nodeToDelete = node;
                break;
            }
        }
        
        if (nodeToDelete == null) {
            log.warn("Node {} not found", nodeId);
            return false;
        }
        
        String nodeType = nodeToDelete.get("type").getAsString();
        if ("start".equals(nodeType) || "end".equals(nodeType)) {
            log.error("Cannot delete start or end node: {}", nodeId);
            return false;
        }

        // 收集要删除节点的入边和出边
        List<String> incomingNodes = new ArrayList<>();  // 指向被删除节点的节点
        List<String> outgoingNodes = new ArrayList<>();  // 被删除节点指向的节点
        
        for (JsonElement edgeElement : edges) {
            JsonObject edge = edgeElement.getAsJsonObject();
            String source = edge.get("sourceNodeID").getAsString();
            String target = edge.get("targetNodeID").getAsString();
            
            if (target.equals(nodeId)) {
                incomingNodes.add(source);
            }
            if (source.equals(nodeId)) {
                outgoingNodes.add(target);
            }
        }

        log.info("Deleting node {}: {} incoming edges, {} outgoing edges", 
                nodeId, incomingNodes.size(), outgoingNodes.size());

        // 删除节点
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).getAsJsonObject().get("id").getAsString().equals(nodeId)) {
                nodes.remove(i);
                break;
            }
        }

        // 智能边管理
        // 1. 删除从被删除节点出发的边（nodeId -> X）
        List<Integer> edgesToRemove = new ArrayList<>();
        for (int i = 0; i < edges.size(); i++) {
            JsonObject edge = edges.get(i).getAsJsonObject();
            String source = edge.get("sourceNodeID").getAsString();
            if (source.equals(nodeId)) {
                edgesToRemove.add(i);
            }
        }
        
        // 从后往前删除
        for (int i = edgesToRemove.size() - 1; i >= 0; i--) {
            edges.remove(edgesToRemove.get(i).intValue());
        }
        log.info("Deleted {} outgoing edges from node {}", edgesToRemove.size(), nodeId);

        // 2. 更新指向被删除节点的边（Y -> nodeId），改为指向下一节点（Y -> X）
        int updatedEdges = 0;
        for (JsonElement edgeElement : edges) {
            JsonObject edge = edgeElement.getAsJsonObject();
            String target = edge.get("targetNodeID").getAsString();
            
            if (target.equals(nodeId)) {
                String source = edge.get("sourceNodeID").getAsString();
                // 更新边的目标
                if (outgoingNodes.size() == 1) {
                    // 如果被删除节点只有一个下一节点，直接重连
                    edge.addProperty("targetNodeID", outgoingNodes.get(0));
                    log.info("Updated edge: {} -> {} (was {} -> {})", 
                            source, outgoingNodes.get(0), source, nodeId);
                    updatedEdges++;
                } else if (outgoingNodes.size() > 1) {
                    // 如果被删除节点有多个下一节点，连接到第一个，并为其他创建新边
                    edge.addProperty("targetNodeID", outgoingNodes.get(0));
                    log.info("Updated edge: {} -> {} (was {} -> {})", 
                            source, outgoingNodes.get(0), source, nodeId);
                    updatedEdges++;
                    
                    // 为其他输出节点创建新边
                    for (int i = 1; i < outgoingNodes.size(); i++) {
                        JsonObject newEdge = new JsonObject();
                        newEdge.addProperty("sourceNodeID", source);
                        newEdge.addProperty("targetNodeID", outgoingNodes.get(i));
                        edges.add(newEdge);
                        log.info("Added additional edge: {} -> {}", source, outgoingNodes.get(i));
                    }
                } else {
                    // 如果被删除节点没有下一节点，需要删除这条边
                    log.warn("Edge {} -> {} has no target after node deletion, marking for removal", 
                            source, nodeId);
                }
            }
        }
        
        // 删除没有目标的边（目标节点被删除且没有替代）
        edgesToRemove.clear();
        for (int i = 0; i < edges.size(); i++) {
            JsonObject edge = edges.get(i).getAsJsonObject();
            if (edge.get("targetNodeID").getAsString().equals(nodeId)) {
                edgesToRemove.add(i);
            }
        }
        for (int i = edgesToRemove.size() - 1; i >= 0; i--) {
            edges.remove(edgesToRemove.get(i).intValue());
        }

        log.info("Deleted node {} successfully. Updated {} edges, removed orphaned edges.", 
                nodeId, updatedEdges);
        
        // 智能修复依赖节点的引用
        if (!dependentNodes.isEmpty()) {
            log.info("Analyzing and fixing {} dependent nodes after deletion", dependentNodes.size());
            
            // 准备修复建议
            context.executedSteps.add("需要修复以下节点的数据引用: " + String.join(", ", dependentNodes));
            context.executedSteps.add("被删除节点 " + nodeId + " 的上游节点: " + 
                    (incomingNodes.isEmpty() ? "无" : String.join(", ", incomingNodes)));
            context.executedSteps.add("被删除节点 " + nodeId + " 的下游节点: " + 
                    (outgoingNodes.isEmpty() ? "无" : String.join(", ", outgoingNodes)));
            
            log.warn("IMPORTANT: The following nodes reference the deleted node and need updates: {}", dependentNodes);
            log.info("Suggestion: You should analyze the workflow and update these nodes to reference:");
            log.info("  - Upstream nodes: {}", incomingNodes);
            log.info("  - Or adjust the logic based on workflow needs");
        }
        
        return true;
    }

    /**
     * 更新节点（深度合并，保留原有字段）
     */
    private static boolean updateNode(ModificationContext context, JsonObject actionObj) {
        if (!actionObj.has("nodeId")) {
            log.warn("update_node action missing 'nodeId' field");
            return false;
        }

        String nodeId = actionObj.get("nodeId").getAsString();
        JsonArray nodes = context.currentWorkflow.getAsJsonArray("nodes");

        for (int i = 0; i < nodes.size(); i++) {
            JsonObject node = nodes.get(i).getAsJsonObject();
            if (node.get("id").getAsString().equals(nodeId)) {
                // 更新节点的字段
                if (actionObj.has("updates")) {
                    JsonObject updates = actionObj.getAsJsonObject("updates");
                    // 深度合并 updates 到 node，保留原有字段
                    deepMerge(node, updates);
                    log.info("Updated node {} (deep merge)", nodeId);
                } else if (actionObj.has("node")) {
                    // 完全替换节点
                    nodes.set(i, actionObj.get("node"));
                    log.info("Replaced node {}", nodeId);
                }
                return true;
            }
        }

        log.warn("Node {} not found for update", nodeId);
        return false;
    }
    
    /**
     * 深度合并 JSON 对象
     * 将 source 中的字段合并到 target，递归处理嵌套对象
     * 
     * @param target 目标对象（会被修改）
     * @param source 源对象（要合并的内容）
     */
    private static void deepMerge(JsonObject target, JsonObject source) {
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            String key = entry.getKey();
            JsonElement sourceValue = entry.getValue();
            
            if (target.has(key) && target.get(key).isJsonObject() && sourceValue.isJsonObject()) {
                // 如果两边都是对象，递归合并
                deepMerge(target.getAsJsonObject(key), sourceValue.getAsJsonObject());
            } else {
                // 否则，直接替换（包括数组、基本类型等）
                target.add(key, sourceValue);
            }
        }
    }

    /**
     * 移动节点到新位置
     * 移动节点时：
     * 1. 删除节点的所有原有边关系
     * 2. 如果指定了 moveBetween，在两个节点之间重新插入
     * 3. 更新节点的位置坐标（如果提供）
     */
    private static boolean moveNode(ModificationContext context, JsonObject actionObj) {
        if (!actionObj.has("nodeId")) {
            log.warn("move_node action missing 'nodeId' field");
            return false;
        }

        String nodeId = actionObj.get("nodeId").getAsString();
        JsonArray nodes = context.currentWorkflow.getAsJsonArray("nodes");
        JsonArray edges = context.currentWorkflow.getAsJsonArray("edges");

        // 查找要移动的节点
        JsonObject nodeToMove = null;
        for (JsonElement nodeElement : nodes) {
            JsonObject node = nodeElement.getAsJsonObject();
            if (node.get("id").getAsString().equals(nodeId)) {
                nodeToMove = node;
                break;
            }
        }

        if (nodeToMove == null) {
            log.warn("Node {} not found for move", nodeId);
            return false;
        }

        // 不允许移动 start 节点
        String nodeType = nodeToMove.get("type").getAsString();
        if ("start".equals(nodeType)) {
            log.error("Cannot move start node: {}", nodeId);
            return false;
        }

        log.info("Moving node {}", nodeId);

        // 收集节点的当前边关系（用于日志）
        List<String> oldIncoming = new ArrayList<>();
        List<String> oldOutgoing = new ArrayList<>();
        
        for (JsonElement edgeElement : edges) {
            JsonObject edge = edgeElement.getAsJsonObject();
            String source = edge.get("sourceNodeID").getAsString();
            String target = edge.get("targetNodeID").getAsString();
            
            if (target.equals(nodeId)) {
                oldIncoming.add(source);
            }
            if (source.equals(nodeId)) {
                oldOutgoing.add(target);
            }
        }

        log.info("Node {} current connections - incoming: {}, outgoing: {}", 
                nodeId, oldIncoming, oldOutgoing);

        // 步骤1: 删除节点的所有边
        List<Integer> edgesToRemove = new ArrayList<>();
        for (int i = 0; i < edges.size(); i++) {
            JsonObject edge = edges.get(i).getAsJsonObject();
            String source = edge.get("sourceNodeID").getAsString();
            String target = edge.get("targetNodeID").getAsString();
            
            if (source.equals(nodeId) || target.equals(nodeId)) {
                edgesToRemove.add(i);
            }
        }

        // 从后往前删除
        for (int i = edgesToRemove.size() - 1; i >= 0; i--) {
            edges.remove(edgesToRemove.get(i).intValue());
        }
        log.info("Removed {} old edges for node {}", edgesToRemove.size(), nodeId);

        // 步骤2: 如果指定了 moveBetween，在新位置插入
        if (actionObj.has("moveBetween")) {
            JsonObject moveInfo = actionObj.getAsJsonObject("moveBetween");
            String prevNodeId = moveInfo.get("previousNode").getAsString();
            String nextNodeId = moveInfo.get("nextNode").getAsString();

            log.info("Moving node {} between {} and {}", nodeId, prevNodeId, nextNodeId);

            // 删除原来 prevNode -> nextNode 的边（如果存在）
            for (int i = edges.size() - 1; i >= 0; i--) {
                JsonObject edge = edges.get(i).getAsJsonObject();
                if (edge.get("sourceNodeID").getAsString().equals(prevNodeId) &&
                    edge.get("targetNodeID").getAsString().equals(nextNodeId)) {
                    edges.remove(i);
                    log.info("Removed old edge: {} -> {}", prevNodeId, nextNodeId);
                    break;
                }
            }

            // 添加新边: prevNode -> movedNode
            JsonObject edge1 = new JsonObject();
            edge1.addProperty("sourceNodeID", prevNodeId);
            edge1.addProperty("targetNodeID", nodeId);
            edges.add(edge1);
            log.info("Added new edge: {} -> {}", prevNodeId, nodeId);

            // 添加新边: movedNode -> nextNode
            JsonObject edge2 = new JsonObject();
            edge2.addProperty("sourceNodeID", nodeId);
            edge2.addProperty("targetNodeID", nextNodeId);
            edges.add(edge2);
            log.info("Added new edge: {} -> {}", nodeId, nextNodeId);

            context.executedSteps.add("移动节点 " + nodeId + " 到 " + prevNodeId + " 和 " + nextNodeId + " 之间");
        } else {
            log.info("Node {} edges removed, but no new position specified. You need to manually add edges.", nodeId);
            context.executedSteps.add("移动节点 " + nodeId + "，已删除旧边，需要手动添加新边");
        }

        // 步骤3: 更新节点位置坐标（如果提供）
        if (actionObj.has("position")) {
            JsonObject position = actionObj.getAsJsonObject("position");
            if (!nodeToMove.has("meta")) {
                nodeToMove.add("meta", new JsonObject());
            }
            nodeToMove.getAsJsonObject("meta").add("position", position);
            log.info("Updated node {} position to ({}, {})", 
                    nodeId, position.get("x"), position.get("y"));
        }

        log.info("Node {} moved successfully", nodeId);
        return true;
    }

    /**
     * 添加边
     */
    private static boolean addEdge(ModificationContext context, JsonObject actionObj) {
        if (!actionObj.has("edge")) {
            log.warn("add_edge action missing 'edge' field");
            return false;
        }

        JsonElement edgeElement = actionObj.get("edge");
        JsonArray edges = context.currentWorkflow.getAsJsonArray("edges");
        
        // 检查边是否已存在
        JsonObject newEdge = edgeElement.getAsJsonObject();
        String sourceId = newEdge.get("sourceNodeID").getAsString();
        String targetId = newEdge.get("targetNodeID").getAsString();
        
        for (JsonElement existingEdge : edges) {
            JsonObject edge = existingEdge.getAsJsonObject();
            if (edge.get("sourceNodeID").getAsString().equals(sourceId) &&
                edge.get("targetNodeID").getAsString().equals(targetId)) {
                log.warn("Edge from {} to {} already exists", sourceId, targetId);
                return false;
            }
        }
        
        edges.add(edgeElement);
        log.info("Added edge: {} -> {}", sourceId, targetId);
        return true;
    }

    /**
     * 删除边
     */
    private static boolean deleteEdge(ModificationContext context, JsonObject actionObj) {
        String sourceId = actionObj.has("sourceNodeID") ? actionObj.get("sourceNodeID").getAsString() : null;
        String targetId = actionObj.has("targetNodeID") ? actionObj.get("targetNodeID").getAsString() : null;

        if (sourceId == null || targetId == null) {
            log.warn("delete_edge action missing sourceNodeID or targetNodeID");
            return false;
        }

        JsonArray edges = context.currentWorkflow.getAsJsonArray("edges");

        for (int i = 0; i < edges.size(); i++) {
            JsonObject edge = edges.get(i).getAsJsonObject();
            if (edge.get("sourceNodeID").getAsString().equals(sourceId) &&
                edge.get("targetNodeID").getAsString().equals(targetId)) {
                edges.remove(i);
                log.info("Deleted edge: {} -> {}", sourceId, targetId);
                return true;
            }
        }

        log.warn("Edge {} -> {} not found", sourceId, targetId);
        return false;
    }

    /**
     * 更新边
     */
    private static boolean updateEdge(ModificationContext context, JsonObject actionObj) {
        String sourceId = actionObj.has("sourceNodeID") ? actionObj.get("sourceNodeID").getAsString() : null;
        String targetId = actionObj.has("targetNodeID") ? actionObj.get("targetNodeID").getAsString() : null;

        if (sourceId == null || targetId == null) {
            log.warn("update_edge action missing sourceNodeID or targetNodeID");
            return false;
        }

        JsonArray edges = context.currentWorkflow.getAsJsonArray("edges");

        for (int i = 0; i < edges.size(); i++) {
            JsonObject edge = edges.get(i).getAsJsonObject();
            if (edge.get("sourceNodeID").getAsString().equals(sourceId) &&
                edge.get("targetNodeID").getAsString().equals(targetId)) {
                
                // 更新边的字段
                if (actionObj.has("updates")) {
                    JsonObject updates = actionObj.getAsJsonObject("updates");
                    for (String key : updates.keySet()) {
                        edge.add(key, updates.get(key));
                    }
                }
                log.info("Updated edge: {} -> {}", sourceId, targetId);
                return true;
            }
        }

        log.warn("Edge {} -> {} not found for update", sourceId, targetId);
        return false;
    }

    /**
     * Stage 3: 验证并返回修改后的工作流
     */
    private static String stage3_ValidateAndReturn(ModificationContext context) {
        log.info("Stage 3: Validating and returning modified workflow");

        String workflowJson = gson.toJson(context.currentWorkflow);

        try {
            // 使用 NodeReferenceValidator 验证
            NodeReferenceValidator.ValidationResult validationResult = 
                    NodeReferenceValidator.validate(workflowJson);

            if (validationResult.hasErrors()) {
                log.error("Modified workflow has validation errors:\n{}", validationResult);
                // 可以选择尝试自动修复或抛出异常
            }

            if (validationResult.hasWarnings()) {
                log.warn("Modified workflow has validation warnings:\n{}", validationResult);
            }

            if (!validationResult.hasErrors() && !validationResult.hasWarnings()) {
                log.info("Modified workflow validation passed successfully");
            }

        } catch (Exception e) {
            log.error("Failed to validate modified workflow: {}", e.getMessage(), e);
        }

        return workflowJson;
    }

    // ==================== Prompt Building Methods ====================

    /**
     * 构建修改分析提示词（包含节点信息）
     */
    private static String buildModificationAnalysisPrompt(ModificationContext context) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("# 工作流修改需求分析\n\n");
        prompt.append("你是一个工作流修改分析器。请分析用户的修改需求，并制定详细的修改计划。\n\n");
        
        prompt.append("## 任务\n\n");
        prompt.append("1. 理解用户想要对工作流做什么修改\n");
        prompt.append("2. 如果需要添加新节点，从可用节点类型中选择合适的节点\n");
        prompt.append("3. 分析节点间的依赖关系，确保修改不会破坏工作流\n");
        prompt.append("4. 列出具体的修改步骤\n\n");
        
        prompt.append("## 可能的修改类型\n\n");
        prompt.append("- **添加节点**: 在工作流中添加新的处理节点\n");
        prompt.append("- **删除节点**: 移除不需要的节点（注意：删除节点可能影响其他节点）\n");
        prompt.append("- **修改节点**: 更新节点的配置、参数或属性\n");
        prompt.append("- **调整连接**: 添加、删除或修改节点之间的边\n\n");
        
        // 添加可用节点信息
        prompt.append(buildNodeInfoDescription(context.availableNodes));
        prompt.append("\n");
        
        prompt.append("## 输出格式\n\n");
        prompt.append("请用自然语言描述修改计划，包括：\n");
        prompt.append("1. 修改的目标和意图\n");
        prompt.append("2. 需要使用的节点类型（如果添加新节点）\n");
        prompt.append("3. 需要执行的具体步骤\n");
        prompt.append("4. 每个步骤的理由和注意事项\n");
        prompt.append("5. 可能的依赖问题和解决方案\n\n");
        
        return prompt.toString();
    }

    /**
     * 构建修改输入（包含用户请求、历史、当前工作流和依赖关系）
     */
    private static String buildModificationInput(ModificationContext context) {
        StringBuilder input = new StringBuilder();

        // 对话历史
        if (!context.history.isEmpty()) {
            input.append("【对话历史】\n");
            int startIdx = Math.max(0, context.history.size() - 3);
            for (int i = startIdx; i < context.history.size(); i++) {
                ChatMessage msg = context.history.get(i);
                input.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
            input.append("\n");
        }

        // 当前用户请求
        input.append("【用户修改请求】\n");
        input.append(context.userText).append("\n\n");

        // 当前工作流
        input.append("【当前工作流】\n");
        input.append(gson.toJson(context.currentWorkflow)).append("\n\n");
        
        // 依赖关系分析
        input.append(buildDependencySummary(context));

        return input.toString();
    }

    /**
     * 构建逐步修改的系统提示词
     */
    private static String buildStepByStepModificationPrompt() {
        return "# 工作流智能修改助手\n\n" +
                "你是一个工作流修改助手，负责根据用户需求一步步修改现有工作流。\n\n" +
                "## 你的能力\n\n" +
                "你可以执行以下操作：\n" +
                "1. **add_node**: 添加新节点（必须按照节点规范创建）\n" +
                "2. **delete_node**: 删除节点（会自动删除相关边，可选自动重连）\n" +
                "3. **update_node**: 更新节点配置（修改节点的参数、引用等）\n" +
                "4. **move_node**: 移动节点到新位置（自动处理边的重连）\n" +
                "5. **add_edge**: 添加边（连接节点，这很重要！）\n" +
                "6. **delete_edge**: 删除边\n" +
                "7. **update_edge**: 更新边配置\n\n" +
                "## 工作方式\n\n" +
                "每一步，你需要：\n" +
                "1. 分析当前工作流状态和节点依赖关系\n" +
                "2. 决定下一步要执行的操作\n" +
                "3. **添加新节点时**：\n" +
                "   - **方式一（推荐）**：使用 insertBetween 在两个节点之间插入\n" +
                "     * 系统会自动更新原有的边（A->C 变为 A->B）\n" +
                "     * 系统会自动添加新边（B->C）\n" +
                "     * 系统会提示检查后续节点是否可以利用新节点的输出\n" +
                "   - **方式二**：简单添加节点，然后手动添加边\n" +
                "     * 先 add_node 添加节点\n" +
                "     * 再用 add_edge 连接新节点\n" +
                "   - **添加节点后的优化**：\n" +
                "     * 新节点可能产生有用的输出\n" +
                "     * 检查后续节点是否可以利用新节点的输出来简化逻辑\n" +
                "     * 例如：添加了API节点获取数据后，后续LLM节点可以引用API的结果\n" +
                "4. **删除节点时**：\n" +
                "   - 系统会自动执行智能边管理：\n" +
                "     * 删除从被删除节点出发的边（B->C）\n" +
                "     * 更新指向被删除节点的边（A->B 变为 A->C）\n" +
                "   - **重要：数据引用修复**\n" +
                "     * 系统会识别哪些节点引用了被删除节点的输出\n" +
                "     * 你需要分析这些节点，找到合适的替代数据源\n" +
                "     * 通常是被删除节点的上游节点，或者工作流中的其他节点\n" +
                "     * 使用 update_node 修改这些节点的 inputsValues，将引用改为新的数据源\n" +
                "   - **修复策略**：\n" +
                "     * 如果删除了节点B，节点D引用了B.result\n" +
                "     * 查看B的上游节点A，如果A.result可以满足需求，将D的引用改为A.result\n" +
                "     * 或者分析工作流逻辑，选择其他合适的节点输出\n" +
                "5. **移动节点时**：\n" +
                "   - 系统会自动删除节点的所有边\n" +
                "   - 如果指定了 moveBetween，系统会：\n" +
                "     * 删除原来两个节点之间的直接连接\n" +
                "     * 在新位置插入节点并建立边\n" +
                "   - **重要**：移动节点会改变数据流顺序\n" +
                "     * 检查移动后，后续节点的数据引用是否仍然有效\n" +
                "     * 可能需要 update_node 修复引用\n" +
                "6. **修改节点时**：\n" +
                "   - 如果修改会影响输出字段，检查是否有其他节点引用\n" +
                "   - 相应更新依赖节点的引用\n" +
                "7. 输出 JSON 格式的操作指令\n" +
                "8. 等待系统反馈执行结果\n" +
                "9. 继续下一步，直到完成所有修改\n\n" +
                "## 输出格式\n\n" +
                "每一步必须输出 JSON，格式如下：\n\n" +
                "```json\n" +
                "{\n" +
                "  \"thinking\": \"我的思考过程：分析当前状态、要做什么、为什么这样做、有什么依赖问题需要注意\",\n" +
                "  \"action\": \"操作类型（add_node/delete_node/update_node/add_edge/delete_edge/update_edge）\",\n" +
                "  \"description\": \"这一步做什么\",\n" +
                "  \"finished\": false,  // 如果所有修改完成，设为 true\n" +
                "  \n" +
                "  // 根据不同的 action 提供不同的参数：\n" +
                "  \n" +
                "  // add_node: 提供完整的节点对象（必须符合节点规范）\n" +
                "  // 方式一（推荐）：在两个节点之间插入，自动处理边\n" +
                "  \"node\": { \"id\": \"节点类型_唯一ID\", \"type\": \"节点类型\", \"data\": {...}, \"meta\": {\"position\": {\"x\": ..., \"y\": ...}} },\n" +
                "  \"insertBetween\": { \"previousNode\": \"前一节点ID\", \"nextNode\": \"后一节点ID\" },  // 可选，推荐使用\n" +
                "  // 方式二：简单添加节点（需要后续手动添加边）\n" +
                "  \"node\": { \"id\": \"...\", \"type\": \"...\", ... },\n" +
                "  \n" +
                "  // delete_node: 提供节点ID（系统会自动处理边的重连）\n" +
                "  \"nodeId\": \"要删除的节点ID\",\n" +
                "  \n" +
                "  // update_node: 提供节点ID和更新内容（深度合并，保留原有字段）\n" +
                "  \"nodeId\": \"要更新的节点ID\",\n" +
                "  \"updates\": { \"data\": {...} },  // 要更新的字段\n" +
                "  \n" +
                "  // move_node: 移动节点到新位置\n" +
                "  \"nodeId\": \"要移动的节点ID\",\n" +
                "  \"moveBetween\": { \"previousNode\": \"前一节点ID\", \"nextNode\": \"后一节点ID\" },  // 可选\n" +
                "  \"position\": { \"x\": 1000, \"y\": 400 },  // 可选：更新位置坐标\n" +
                "  \n" +
                "  // add_edge: 提供完整的边对象\n" +
                "  \"edge\": { \"sourceNodeID\": \"源节点ID\", \"targetNodeID\": \"目标节点ID\" },\n" +
                "  \n" +
                "  // delete_edge: 提供源和目标节点ID\n" +
                "  \"sourceNodeID\": \"源节点ID\",\n" +
                "  \"targetNodeID\": \"目标节点ID\",\n" +
                "  \n" +
                "  // update_edge: 提供源和目标节点ID，以及更新内容\n" +
                "  \"sourceNodeID\": \"源节点ID\",\n" +
                "  \"targetNodeID\": \"目标节点ID\",\n" +
                "  \"updates\": { \"data\": {...} }\n" +
                "}\n" +
                "```\n\n" +
                "## 重要提醒\n\n" +
                "1. **每次只执行一个操作** - 一步一步来，不要急\n" +
                "2. **节点ID唯一性** - 新节点的ID格式为 \"节点类型_随机字符\"（如 llm_A7BcD）\n" +
                "3. **节点结构规范** - 添加新节点时，严格按照节点规范创建，包括所有必需字段\n" +
                "4. **边的管理（非常重要）**：\n" +
                "   - **添加节点后必须添加边** - 新节点不会自动连接，你必须用add_edge来连接它\n" +
                "   - **删除节点会删除边** - 删除节点时相关的边会被自动删除\n" +
                "   - **删除后可能需要重连** - 删除中间节点后，要么设置reconnect=true自动重连，要么手动添加新边\n" +
                "   - **检查工作流连通性** - 确保所有节点都能从start到达end\n" +
                "5. **节点引用（数据依赖）** - 节点间的引用格式：\n" +
                "   - ref类型：{\"type\": \"ref\", \"content\": [\"源节点ID\", \"字段名\"]}\n" +
                "   - template类型：{\"type\": \"template\", \"content\": \"文本{{源节点ID.字段名}}文本\"}\n" +
                "6. **依赖关系检查** - 删除或修改节点前，检查是否有其他节点引用它的输出\n" +
                "7. **边的完整性** - 添加边时确保源和目标节点都已存在\n" +
                "8. **位置布局** - 新节点的位置要合理，建议每个节点x坐标相隔300-500，避免重叠\n" +
                "9. **start和end节点** - 不能删除start和end节点\n" +
                "10. **节点输出字段** - 注意不同节点的输出字段不同：\n" +
                "   - 大多数节点输出 `result`\n" +
                "   - intent节点输出 `intent`\n" +
                "   - api节点输出 `statusCode` 和 `body`\n" +
                "11. **完成标志** - 所有修改完成后，设置 `finished: true`\n\n" +
                "## 常见操作流程示例\n\n" +
                "### 在两个节点之间插入新节点（推荐方式）：\n" +
                "```json\n" +
                "{\n" +
                "  \"action\": \"add_node\",\n" +
                "  \"description\": \"在 llm_1 和 end_0 之间插入 api 节点\",\n" +
                "  \"node\": { \"id\": \"api_ABC\", \"type\": \"api\", ... },\n" +
                "  \"insertBetween\": { \"previousNode\": \"llm_1\", \"nextNode\": \"end_0\" }\n" +
                "}\n" +
                "```\n" +
                "**自动完成**：原边 llm_1->end_0 变为 llm_1->api_ABC，新增边 api_ABC->end_0\n\n" +
                "### 添加新节点到末尾：\n" +
                "1. 步骤1：add_node 添加新节点\n" +
                "2. 步骤2：add_edge 连接前一个节点到新节点\n\n" +
                "### 删除中间节点（需要修复引用）：\n" +
                "假设工作流：start_0 -> llm_1 -> api_1 -> end_0\n" +
                "api_1节点使用了 {{llm_1.result}}\n\n" +
                "**步骤1：删除节点**\n" +
                "```json\n" +
                "{\n" +
                "  \"action\": \"delete_node\",\n" +
                "  \"description\": \"删除 llm_1 节点\",\n" +
                "  \"nodeId\": \"llm_1\"\n" +
                "}\n" +
                "```\n" +
                "**系统自动完成**：\n" +
                "- 删除边 llm_1->api_1\n" +
                "- 更新边 start_0->llm_1 为 start_0->api_1\n" +
                "- 提示：api_1 引用了 llm_1，需要修复\n\n" +
                "**步骤2：修复引用**\n" +
                "```json\n" +
                "{\n" +
                "  \"action\": \"update_node\",\n" +
                "  \"description\": \"修复 api_1 的引用，改为使用 start_0.query\",\n" +
                "  \"nodeId\": \"api_1\",\n" +
                "  \"updates\": {\n" +
                "    \"data\": {\n" +
                "      \"inputsValues\": {\n" +
                "        \"body\": {\n" +
                "          \"type\": \"template\",\n" +
                "          \"content\": \"{\\\"query\\\": \\\"{{start_0.query}}\\\"}\"  // 改为引用start_0\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n" +
                "```\n\n" +
                "### 替换节点：\n" +
                "1. 步骤1：add_node 添加新节点，使用 insertBetween 在目标节点同位置\n" +
                "2. 步骤2：delete_node 删除旧节点（边会自动调整）\n" +
                "3. 步骤3（如有需要）：update_node 更新数据引用\n\n" +
                "### 移动节点位置：\n" +
                "假设工作流：start_0 -> llm_1 -> api_1 -> end_0\n" +
                "想要将 api_1 移动到 start_0 和 llm_1 之间\n\n" +
                "```json\n" +
                "{\n" +
                "  \"action\": \"move_node\",\n" +
                "  \"description\": \"将 api_1 移动到 start_0 和 llm_1 之间\",\n" +
                "  \"nodeId\": \"api_1\",\n" +
                "  \"moveBetween\": { \"previousNode\": \"start_0\", \"nextNode\": \"llm_1\" },\n" +
                "  \"position\": { \"x\": 800, \"y\": 400 }  // 可选：更新UI位置\n" +
                "}\n" +
                "```\n" +
                "**系统自动完成**：\n" +
                "- 删除边 llm_1->api_1 和 api_1->end_0\n" +
                "- 删除边 start_0->llm_1（如果存在）\n" +
                "- 添加边 start_0->api_1\n" +
                "- 添加边 api_1->llm_1\n" +
                "- 你需要更新后续节点的数据引用，因为数据流顺序改变了\n\n";
    }

    /**
     * 构建初始修改请求（包含节点规范和依赖关系）
     */
    private static String buildInitialModificationRequest(ModificationContext context) {
        StringBuilder request = new StringBuilder();

        request.append("请帮我修改以下工作流：\n\n");
        request.append("【用户需求】\n");
        request.append(context.userText).append("\n\n");
        
        if (context.modificationPlan != null && !context.modificationPlan.isEmpty()) {
            request.append("【修改计划】\n");
            request.append(context.modificationPlan).append("\n\n");
        }

        request.append("【当前工作流】\n");
        request.append(gson.toJson(context.currentWorkflow)).append("\n\n");
        
        // 添加依赖关系信息
        request.append(buildDependencySummary(context)).append("\n");
        
        // 添加可用节点类型列表（简化版）
        request.append("【可用节点类型】\n");
        for (String nodeType : context.availableNodes.keySet()) {
            request.append("- ").append(nodeType).append("\n");
        }
        request.append("\n");
        
        request.append("**重要提示**：\n");
        request.append("1. 如果需要添加新节点，请告诉我节点类型，我会提供该节点的详细规范\n");
        request.append("2. 注意节点间的依赖关系，避免破坏现有引用\n");
        request.append("3. 一步一步来，每次只执行一个操作\n\n");
        request.append("请开始执行修改操作。");

        return request.toString();
    }

    /**
     * 构建工作流状态反馈（包含节点规范，如果请求的话）
     */
    private static String buildWorkflowStateFeedback(ModificationContext context, String requestedNodeType) {
        StringBuilder feedback = new StringBuilder();
        
        // 如果请求了节点规范，优先提供
        if (requestedNodeType != null && !requestedNodeType.isEmpty()) {
            String nodeSpec = getNodeSpecification(requestedNodeType, context.availableNodes);
            if (!nodeSpec.isEmpty()) {
                feedback.append("【").append(requestedNodeType).append(" 节点规范】\n\n");
                feedback.append(nodeSpec).append("\n\n");
                feedback.append("请按照上述规范创建节点。\n\n");
            } else {
                feedback.append("【错误】未找到节点类型 ").append(requestedNodeType).append(" 的规范。\n\n");
            }
        }
        
        feedback.append("【当前工作流状态】\n\n");
        
        JsonArray nodes = context.currentWorkflow.getAsJsonArray("nodes");
        JsonArray edges = context.currentWorkflow.getAsJsonArray("edges");
        
        feedback.append("节点数量: ").append(nodes.size()).append("\n");
        feedback.append("边数量: ").append(edges.size()).append("\n\n");
        
        feedback.append("**节点列表**:\n");
        for (int i = 0; i < nodes.size(); i++) {
            JsonObject node = nodes.get(i).getAsJsonObject();
            feedback.append("  - ").append(node.get("id").getAsString())
                    .append(" (").append(node.get("type").getAsString()).append(")\n");
        }
        feedback.append("\n");
        
        feedback.append("**边列表**:\n");
        if (edges.size() == 0) {
            feedback.append("  （暂无边，工作流未连接！）\n");
        } else {
            for (int i = 0; i < edges.size(); i++) {
                JsonObject edge = edges.get(i).getAsJsonObject();
                feedback.append("  - ").append(edge.get("sourceNodeID").getAsString())
                        .append(" -> ").append(edge.get("targetNodeID").getAsString()).append("\n");
            }
        }
        feedback.append("\n");
        
        // 显示最新的依赖关系（如果有变化）
        String dependencies = buildDependencySummary(context);
        if (!dependencies.contains("暂无节点依赖关系")) {
            feedback.append(dependencies).append("\n");
        }
        
        // 显示最近的执行步骤和提示（特别是引用修复建议）
        if (!context.executedSteps.isEmpty()) {
            int recentSteps = Math.min(3, context.executedSteps.size());
            feedback.append("**最近的操作和提示**:\n");
            for (int i = context.executedSteps.size() - recentSteps; i < context.executedSteps.size(); i++) {
                feedback.append("  - ").append(context.executedSteps.get(i)).append("\n");
            }
            feedback.append("\n");
        }
        
        feedback.append("**下一步操作**:\n");
        feedback.append("- 如果有节点引用需要修复，请使用 update_node 更新这些节点的 inputsValues\n");
        feedback.append("- 分析工作流的数据流，找到合适的替代引用源\n");
        feedback.append("- 修改完成后，设置 finished=true\n");
        feedback.append("- 如果需要查看某个节点类型的详细规范，可以在响应中加入 \"request_node_spec\": \"节点类型\"\n");
        
        return feedback.toString();
    }
}

