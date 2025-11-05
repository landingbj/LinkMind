package ai.workflow;

import ai.config.ContextLoader;
import ai.llm.service.CompletionsService;
import ai.openai.pojo.*;
import ai.utils.JsonExtractor;
import ai.workflow.utils.DefaultNodeEnum;
import ai.workflow.utils.NodeReferenceValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


@Slf4j
public class WorkflowGenerator {
    private static final CompletionsService completionsService = new CompletionsService();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final double DEFAULT_TEMPERATURE = 0.3;
    private static final int DEFAULT_MAX_TOKENS = 4096;
    private static final int MAX_RETRIES = 3;

    /**
     * Workflow generation.
     *
     * @param text Natural language description
     * @return Workflow JSON string
     */
    public static String txt2FlowSchema(String text) {
        // TODO 修改提示词，补全所有的节点类型
        // TODO 需要测试一些复杂的生成指令，修改提示词以提高生成效果。
        log.info("Generating workflow from text: {}", text);
        List<String> notValidNodeName = DefaultNodeEnum.getNotValidNodeName();
        try {
            log.info("Stage 1: Extracting start and end node variables");
            ChatCompletionRequest stage1Request = completionsService.getCompletionsRequest(
                    WorkflowPromptUtil.VARIABLE_EXTRACT_PROMPT,
                    text,
                    DEFAULT_TEMPERATURE,
                    DEFAULT_MAX_TOKENS
            );

            ChatCompletionResult stage1Result = completionsService.completions(stage1Request);
            String variablesJson = stage1Result.getChoices().get(0).getMessage().getContent();
            variablesJson = JsonExtractor.extractJson(variablesJson);
            log.info("Stage 1 - Variables extracted: {}", variablesJson);

            // Build variable description for injection into subsequent prompts
            String variableDescription = buildVariableDescription(variablesJson);

            log.info("Stage 2: Extracting structured process description");
            String stage2PromptWithVariables = injectVariablesIntoPrompt(
                    WorkflowPromptUtil.USER_INFO_EXTRACT_PROMPT,
                    variableDescription
            );
            ChatCompletionRequest stage2Request = completionsService.getCompletionsRequest(
                    stage2PromptWithVariables,
                    text,
                    DEFAULT_TEMPERATURE,
                    DEFAULT_MAX_TOKENS
            );
            ChatCompletionResult stage2Result = completionsService.completions(stage2Request);
            String structuredDescription = stage2Result.getChoices().get(0).getMessage().getContent();
            log.info("Stage 2 - Structured description: {}", structuredDescription);

            log.info("Stage 3: Matching required nodes based on process");
            String stage3PromptWithVariables = injectVariablesIntoPrompt(
                    WorkflowPromptUtil.getNodeMatchingPrompt(notValidNodeName),
                    variableDescription
            );
            ChatCompletionRequest stage3Request = completionsService.getCompletionsRequest(
                    stage3PromptWithVariables,
                    structuredDescription,
                    DEFAULT_TEMPERATURE,
                    DEFAULT_MAX_TOKENS
            );
            ChatCompletionResult stage3Result = completionsService.completions(stage3Request);
            String nodeMatching = stage3Result.getChoices().get(0).getMessage().getContent();
            log.info("Stage 3 - Node matching result: {}", nodeMatching);

            log.info("Stage 4: Generating workflow JSON configuration");
            String stage4PromptWithVariables = injectVariablesIntoPrompt(
                    WorkflowPromptUtil.getPromptToWorkflowJson(notValidNodeName),
                    variableDescription
            );
            // Combine structured description, node matching, and variables for final stage
            String combinedInput = "【提取的变量】\n" + variablesJson +
                    "\n\n【流程描述】\n" + structuredDescription +
                    "\n\n【节点匹配结果】\n" + nodeMatching;
            ChatCompletionRequest stage4Request = completionsService.getCompletionsRequest(
                    stage4PromptWithVariables,
                    combinedInput,
                    DEFAULT_TEMPERATURE,
                    DEFAULT_MAX_TOKENS
            );
            ChatCompletionResult stage4Result = completionsService.completions(stage4Request);
            String json = stage4Result.getChoices().get(0).getMessage().getContent();
            log.info("Stage 4 - LLM Response: {}", json);

            // Extract and validate JSON
            json = JsonExtractor.extractJson(json);

            if (json == null || json.isEmpty()) {
                throw new IllegalStateException("Failed to extract JSON from LLM response");
            }
            
            // Validate node references and log issues
            validateAndLogWorkflowReferences(json, "txt2FlowSchema");

            return json;

        } catch (Exception e) {
            log.error("Failed to generate workflow: {}", e.getMessage(), e);
            throw new RuntimeException("Workflow generation failed", e);
        }
    }

    /**
     * Build variable description from extracted variables JSON
     *
     * @param variablesJson JSON string containing start and end node variables
     * @return Formatted variable description
     */
    private static String buildVariableDescription(String variablesJson) {
        try {
            JsonNode variables = objectMapper.readTree(variablesJson);
            StringBuilder desc = new StringBuilder();

            desc.append("\n## 已提取的节点变量信息\n\n");

            // Start node variables
            desc.append("### 开始节点的输入变量：\n");
            JsonNode startVars = variables.get("startNodeVariables");
            if (startVars != null && startVars.isArray()) {
                for (JsonNode var : startVars) {
                    desc.append("- **").append(var.get("name").asText()).append("** (")
                            .append(var.get("type").asText()).append(")")
                            .append(var.has("required") && var.get("required").asBoolean() ? " [必填]" : " [可选]")
                            .append(": ").append(var.get("desc").asText()).append("\n");
                }
            }

            desc.append("\n### 结束节点的输出变量：\n");
            JsonNode endVars = variables.get("endNodeVariables");
            if (endVars != null && endVars.isArray()) {
                for (JsonNode var : endVars) {
                    desc.append("- **").append(var.get("name").asText()).append("** (")
                            .append(var.get("type").asText()).append("): ")
                            .append(var.get("desc").asText());
                    if (var.has("source")) {
                        desc.append(" [来源: ").append(var.get("source").asText()).append("]");
                    }
                    desc.append("\n");
                }
            }

            desc.append("\n**请在生成流程描述和节点配置时，使用上述变量定义。**\n");

            return desc.toString();

        } catch (Exception e) {
            log.warn("Failed to parse variables JSON, using default: {}", e.getMessage());
            return "\n## 使用默认变量\n开始节点: query\n结束节点: result\n";
        }
    }

    /**
     * Inject variable description into prompt
     *
     * @param originalPrompt      Original prompt text
     * @param variableDescription Variable description to inject
     * @return Modified prompt with variables
     */
    private static String injectVariablesIntoPrompt(String originalPrompt, String variableDescription) {
        // Insert variable description after the task description section
        // Look for "## 输出要求" or similar sections to insert before them
        if (originalPrompt.contains("## 输出要求")) {
            return originalPrompt.replace("## 输出要求", variableDescription + "\n## 输出要求");
        } else if (originalPrompt.contains("## 输出格式")) {
            return originalPrompt.replace("## 输出格式", variableDescription + "\n## 输出格式");
        } else if (originalPrompt.contains("## 可用节点类型")) {
            return originalPrompt.replace("## 可用节点类型", variableDescription + "\n## 可用节点类型");
        } else {
            // If no suitable insertion point found, append at the end before examples
            if (originalPrompt.contains("## 示例")) {
                return originalPrompt.replace("## 示例", variableDescription + "\n## 示例");
            }
            // Last resort: append at the beginning after title
            return originalPrompt.replaceFirst("(# [^\n]+\n)", "$1" + variableDescription + "\n");
        }
    }

    /**
     * Generate workflow with conversation history context
     *
     * @param text    Current request
     * @param history Conversation history
     * @return Workflow JSON string
     */
    public static String txt2FlowSchemaWithHistory(String text, List<ChatMessage> history) {
        log.info("Generating workflow with history context");

        // Build context with history
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("### Conversation History:\n");

        for (ChatMessage msg : history) {
            contextBuilder.append(msg.getRole())
                    .append(": ")
                    .append(msg.getContent())
                    .append("\n");
        }

        contextBuilder.append("\n### Current Request:\n").append(text);

        return txt2FlowSchema(contextBuilder.toString());
    }

    /**
     * Generate workflow with retry mechanism
     *
     * @param text       Natural language description
     * @param maxRetries Maximum retry attempts
     * @return Workflow JSON string
     */
    public static String txt2FlowSchemaWithRetry(String text, int maxRetries) {
        log.info("Generating workflow with retry (max retries: {})", maxRetries);

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.debug("Attempt {}/{}", attempt, maxRetries);

                String json = txt2FlowSchema(text);

                // Validate the generated JSON
                validateWorkflowJson(json);

                log.info("Successfully generated workflow on attempt {}", attempt);
                return json;

            } catch (Exception e) {
                log.warn("Attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());

                if (attempt == maxRetries) {
                    throw new RuntimeException(
                            "Failed to generate valid workflow after " + maxRetries + " attempts",
                            e
                    );
                }

                // Wait before retry (exponential backoff)
                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry", ie);
                }
            }
        }

        return null;
    }

    /**
     * Async workflow generation
     *
     * @param text Natural language description
     * @return CompletableFuture with workflow JSON
     */
    public static CompletableFuture<String> txt2FlowSchemaAsync(String text) {
        return CompletableFuture.supplyAsync(() -> txt2FlowSchema(text));
    }

    /**
     * Batch workflow generation
     *
     * @param texts List of natural language descriptions
     * @return List of workflow JSON strings
     */
    public static List<String> batchGenerate(List<String> texts) {
        log.info("Batch generating {} workflows", texts.size());

        return texts.parallelStream()
                .map(WorkflowGenerator::txt2FlowSchema)
                .collect(Collectors.toList());
    }


    /**
     * Validate workflow JSON structure
     *
     * @param json Workflow JSON string
     * @throws IllegalArgumentException if validation fails
     */
    public static void validateWorkflowJson(String json) throws IllegalArgumentException {
        try {
            JsonNode root = objectMapper.readTree(json);

            // Check required top-level fields
            if (!root.has("nodes") || !root.has("edges")) {
                throw new IllegalArgumentException("Missing required fields: nodes or edges");
            }

            JsonNode nodes = root.get("nodes");
            JsonNode edges = root.get("edges");

            if (!nodes.isArray() || !edges.isArray()) {
                throw new IllegalArgumentException("nodes and edges must be arrays");
            }

            // Collect node IDs and validate node structure
            Set<String> nodeIds = new HashSet<>();
            boolean hasStart = false;
            boolean hasEnd = false;

            for (JsonNode node : nodes) {
                // Validate required node fields
                if (!node.has("id") || !node.has("type") || !node.has("data")) {
                    throw new IllegalArgumentException("Node missing required fields: id, type, or data");
                }

                String nodeId = node.get("id").asText();
                String nodeType = node.get("type").asText();

                // Check for duplicate IDs
                if (nodeIds.contains(nodeId)) {
                    throw new IllegalArgumentException("Duplicate node ID: " + nodeId);
                }
                nodeIds.add(nodeId);

                // Check for start and end nodes
                if ("start".equals(nodeType)) hasStart = true;
                if ("end".equals(nodeType)) hasEnd = true;

                // Validate node has meta with position
                if (!node.has("meta") || !node.get("meta").has("position")) {
                    throw new IllegalArgumentException("Node missing meta.position: " + nodeId);
                }
            }

            // Workflow must have start and end nodes
            if (!hasStart) {
                throw new IllegalArgumentException("Workflow must have a start node");
            }
            if (!hasEnd) {
                throw new IllegalArgumentException("Workflow must have at least one end node");
            }

            // Validate edges
            for (JsonNode edge : edges) {
                if (!edge.has("sourceNodeID") || !edge.has("targetNodeID")) {
                    throw new IllegalArgumentException("Edge missing required fields: sourceNodeID or targetNodeID");
                }

                String sourceId = edge.get("sourceNodeID").asText();
                String targetId = edge.get("targetNodeID").asText();

                if (!nodeIds.contains(sourceId)) {
                    throw new IllegalArgumentException("Edge references non-existent source node: " + sourceId);
                }
                if (!nodeIds.contains(targetId)) {
                    throw new IllegalArgumentException("Edge references non-existent target node: " + targetId);
                }
            }

            log.debug("Workflow validation passed: {} nodes, {} edges", nodeIds.size(), edges.size());

        } catch (Exception e) {
            throw new IllegalArgumentException("JSON validation failed: " + e.getMessage(), e);
        }
    }



    /**
     * Generate workflow from text with history and document context (New Agent-like version)
     * 
     * 使用新的智能体式生成器，提供更稳定的生成结果：
     * 1. 多阶段推理：任务分析 -> 节点选择 -> 配置生成 -> 边连接 -> 验证修正
     * 2. 自我验证：每个阶段都进行验证，发现问题自动重试
     * 3. 节点可扩展：节点定义与生成逻辑分离，新增节点无需修改代码
     * 
     * @param text User input text
     * @param history Conversation history
     * @param docsContents Document contents
     * @param lastWorkflow Last generated workflow JSON (可选，如果用户需要修改现有工作流)
     * @return Workflow JSON string
     */
    public static String txt2FlowSchema(String text, List<ChatMessage> history, List<String> docsContents, String lastWorkflow) {
        log.info("Generating/Modifying workflow with agent-like generator");
        log.info("User input: {}", text);
        log.info("History messages: {}", history != null ? history.size() : 0);
        log.info("Document contents: {}", docsContents != null ? docsContents.size() : 0);
        log.info("Has last workflow: {}", lastWorkflow != null && !lastWorkflow.isEmpty());
        
        int retries = 0;
        while (retries <= MAX_RETRIES) {
            try {
                // 步骤1: 分析用户意图 - 是生成新工作流还是修改现有工作流
                UserIntent intent = analyzeUserIntent(text, history, lastWorkflow);
                log.info("User intent detected: {}", intent);
                
                String workflowJson;
                
                if (intent == UserIntent.MODIFY_WORKFLOW && lastWorkflow != null && !lastWorkflow.isEmpty()) {
                    // 使用智能体式工作流修改器
                    log.info("Using workflow modifier to update existing workflow");
                    workflowJson = AgentLikeWorkflowModifier.modify(text, history, docsContents, lastWorkflow);
                } else {
                    // 使用智能体式生成器生成新工作流
                    log.info("Using workflow generator to create new workflow");
                    workflowJson = AgentLikeWorkflowGenerator.generate(text, history, docsContents);
                }

                log.info("Workflow generation/modification completed");
                return workflowJson;
            } catch (Exception e) {
                log.error("Agent-like generation/modification failed, retry times: {} message: {}", retries, e.getMessage());
            }
            retries++;
        }
        throw new RuntimeException("Workflow generation/modification failed after " + MAX_RETRIES + " retries");
    }

    
    /**
     * 用户意图枚举
     */
    private enum UserIntent {
        GENERATE_NEW_WORKFLOW,  // 生成新的工作流
        MODIFY_WORKFLOW         // 修改现有工作流
    }
    
    /**
     * 分析用户意图：判断用户是想生成新工作流还是修改现有工作流
     * 
     * @param text 用户输入
     * @param history 对话历史
     * @param lastWorkflow 上一个工作流（如果有）
     * @return 用户意图
     */
    private static UserIntent analyzeUserIntent(String text, List<ChatMessage> history, String lastWorkflow) {
        // 如果没有上一个工作流，只能生成新的
        if (lastWorkflow == null || lastWorkflow.isEmpty()) {
            return UserIntent.GENERATE_NEW_WORKFLOW;
        }
        
        // 使用 LLM 分析用户意图
        try {
            String prompt = buildIntentAnalysisPrompt();
            String userContext = buildUserContext(text, history);
            
            ChatCompletionRequest request = completionsService.getCompletionsRequest(
                    prompt,
                    userContext,
                    DEFAULT_TEMPERATURE,
                    512  // 意图分析不需要太多 tokens
            );
            
            ChatCompletionResult result = completionsService.completions(request);
            String response = result.getChoices().get(0).getMessage().getContent().trim().toLowerCase();
            
            log.debug("Intent analysis response: {}", response);
            
            // 解析响应
            if (response.contains("modify") || response.contains("修改") || 
                response.contains("update") || response.contains("更新") ||
                response.contains("change") || response.contains("改变") ||
                response.contains("add") || response.contains("添加") || response.contains("增加") ||
                response.contains("delete") || response.contains("删除") || response.contains("移除") ||
                response.contains("remove") || response.contains("调整")) {
                return UserIntent.MODIFY_WORKFLOW;
            }
            
        } catch (Exception e) {
            log.warn("Failed to analyze user intent using LLM, falling back to keyword matching: {}", e.getMessage());
        }
        
        // 回退到关键词匹配
        String lowerText = text.toLowerCase();
        
        // 修改意图的关键词
        String[] modifyKeywords = {
            "修改", "更新", "update", "modify", "change", "调整",
            "添加", "add", "增加", "新增", "append",
            "删除", "delete", "remove", "移除", "去掉",
            "替换", "replace", "改成",
            "连接", "connect", "断开", "disconnect"
        };
        
        for (String keyword : modifyKeywords) {
            if (lowerText.contains(keyword)) {
                return UserIntent.MODIFY_WORKFLOW;
            }
        }
        
        // 生成新工作流的关键词
        String[] generateKeywords = {
            "生成", "generate", "create", "创建", "新建",
            "帮我做", "我需要", "我想要"
        };
        
        for (String keyword : generateKeywords) {
            if (lowerText.contains(keyword)) {
                return UserIntent.GENERATE_NEW_WORKFLOW;
            }
        }
        
        // 默认：如果有明确的工作流描述且没有修改关键词，倾向于生成新的
        return UserIntent.GENERATE_NEW_WORKFLOW;
    }
    
    /**
     * 构建意图分析的提示词
     */
    private static String buildIntentAnalysisPrompt() {
        return "# 用户意图分析任务\n\n" +
                "你是一个工作流系统的意图分析器。你需要判断用户的输入是想要：\n" +
                "1. **生成新的工作流** (GENERATE)\n" +
                "2. **修改现有的工作流** (MODIFY)\n\n" +
                "## 判断标准\n\n" +
                "### MODIFY（修改现有工作流）的特征：\n" +
                "- 用户提到修改、更新、调整现有流程\n" +
                "- 用户要添加、删除、替换某个节点\n" +
                "- 用户要改变节点之间的连接关系\n" +
                "- 用户要修改某个节点的配置或参数\n" +
                "- 用户在对话历史中已经有一个工作流，现在要基于它做改动\n\n" +
                "### GENERATE（生成新工作流）的特征：\n" +
                "- 用户描述了一个全新的业务流程\n" +
                "- 用户明确说要创建、生成一个新的工作流\n" +
                "- 用户的需求与之前的工作流无关\n\n" +
                "## 输出格式\n\n" +
                "只输出一个词：`GENERATE` 或 `MODIFY`\n\n" +
                "## 示例\n\n" +
                "用户输入：\"帮我在刚才的工作流中添加一个数据库查询节点\"\n" +
                "输出：MODIFY\n\n" +
                "用户输入：\"帮我生成一个调用天气API的工作流\"\n" +
                "输出：GENERATE\n\n" +
                "用户输入：\"把LLM节点删掉\"\n" +
                "输出：MODIFY\n\n" +
                "用户输入：\"创建一个处理用户订单的流程\"\n" +
                "输出：GENERATE\n";
    }
    
    /**
     * 构建用户上下文（包含当前输入和历史）
     */
    private static String buildUserContext(String text, List<ChatMessage> history) {
        StringBuilder context = new StringBuilder();
        
        if (history != null && !history.isEmpty()) {
            context.append("【对话历史】\n");
            // 只取最近的几条历史，避免context过长
            int startIdx = Math.max(0, history.size() - 5);
            for (int i = startIdx; i < history.size(); i++) {
                ChatMessage msg = history.get(i);
                context.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
            context.append("\n");
        }
        
        context.append("【当前用户输入】\n");
        context.append(text);
        
        return context.toString();
    }
    




    /**
     * 验证并记录工作流引用问题
     * @param workflowJson 工作流JSON字符串
     * @param methodName 调用方法名（用于日志）
     */
    private static void validateAndLogWorkflowReferences(String workflowJson, String methodName) {
        try {
            NodeReferenceValidator.ValidationResult validationResult = NodeReferenceValidator.validate(workflowJson);
            
            if (validationResult.hasWarnings()) {
                log.warn("[{}] Workflow JSON has reference warnings:\n{}", methodName, validationResult);
            }
            
            if (validationResult.hasErrors()) {
                log.error("[{}] Workflow JSON has reference errors:\n{}", methodName, validationResult);
            }
            
            if (!validationResult.hasWarnings() && !validationResult.hasErrors()) {
                log.info("[{}] Workflow JSON validation passed successfully", methodName);
            }
        } catch (Exception e) {
            log.error("[{}] Failed to validate workflow references: {}", methodName, e.getMessage(), e);
        }
    }

    public static void main(String[] args) {
        ContextLoader.loadContext();
        
        // 示例1: 生成新的工作流
        System.out.println("=== 示例1: 生成新工作流 ===");
        String workflow1 = WorkflowGenerator.txt2FlowSchema(
                "帮我生成一个调用天气api的工作流", 
                Collections.emptyList(), 
                Collections.emptyList(),
                null  // 没有上一个工作流
        );
        System.out.println(workflow1);
        
        // 示例2: 修改现有工作流
        System.out.println("\n=== 示例2: 修改现有工作流 ===");
        String workflow2 = WorkflowGenerator.txt2FlowSchema(
                "在工作流中添加一个LLM节点来总结天气信息", 
                Collections.emptyList(), 
                Collections.emptyList(),
                workflow1  // 传入上一个工作流
        );
        System.out.println(workflow2);
    }

}
