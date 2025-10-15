package ai.workflow;

import ai.config.ContextLoader;
import ai.llm.service.CompletionsService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.utils.JsonExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


@Slf4j
public class WorkflowGenerator {
    private static final CompletionsService completionsService = new CompletionsService();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final double DEFAULT_TEMPERATURE = 0.3;
    private static final int DEFAULT_MAX_TOKENS = 4096;

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
                    WorkflowPromptUtil.getNodeMatchingPrompt(Collections.emptyList()),
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
                    WorkflowPromptUtil.getPromptToWorkflowJson(Collections.emptyList()),
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

    public static void main(String[] args) {
        ContextLoader.loadContext();
        System.out.println(WorkflowGenerator.txt2FlowSchema("帮我生成一个调用天气api的的工作流"));
    }

}
