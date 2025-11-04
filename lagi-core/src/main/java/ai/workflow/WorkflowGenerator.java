package ai.workflow;

import ai.config.ContextLoader;
import ai.llm.service.CompletionsService;
import ai.openai.pojo.*;
import ai.utils.JsonExtractor;
import ai.workflow.utils.DefaultNodeEnum;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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

    public static String txt2FlowSchema1(String text) {
        JsonObject workflow = new JsonObject();
        JsonArray nodes = new JsonArray();
        workflow.add("nodes", nodes);
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
                    WorkflowPromptUtil.getPromptToWorkflowStepByStepJson(notValidNodeName),
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

            int maxLoopTime = 50;
            Gson gson = new Gson();
            while (maxLoopTime-- > 0){
                System.out.println(gson.toJson(stage4Request));
                ChatCompletionResult stage4Result = completionsService.completions(stage4Request);
                String json = stage4Result.getChoices().get(0).getMessage().getContent();
                System.out.println("Stage 4 - Workflow JSON: " + json);
                json = JsonExtractor.extractJson(json);
                if(!JsonExtractor.isJson( json)) {
                    continue;
                }
                JsonObject jsonObject = gson.fromJson(json, JsonObject.class);
                JsonElement finished = jsonObject.get("finished");
                if(finished == null) {
                    continue;
                }
                boolean finish  = finished.getAsBoolean();
                JsonElement node = jsonObject.get("node");
                JsonArray edges = jsonObject.getAsJsonArray("edges");
                if(node != null) {
                    nodes.add(node);
                }
                if(edges != null) {
                    workflow.add("edges", edges);
                }
                if(finish) {
                    return workflow.toString();
                }
                ChatMessage assistant = ChatMessage.builder().content(json).role("assistant").build();
                ChatMessage user = ChatMessage.builder().content("继续生成节点或是边列表").role("user").build();
                stage4Request.getMessages().add(assistant);
                stage4Request.getMessages().add(user);
            }

        } catch (Exception e) {
            log.error("Failed to generate workflow: {}", e.getMessage(), e);
            throw new RuntimeException("Workflow generation failed", e);
        }
        throw new RuntimeException("Workflow generation failed");
    }

    public static String txt2FlowSchema(String text, List<ChatMessage> history, List<String> docsContents) {
        StringBuilder docBuilder = new StringBuilder();
        StringBuilder dialogBuilder = new StringBuilder();
        if(!history.isEmpty()) {
            dialogBuilder.append("历史记录：\n");
        }
        for (ChatMessage chatMessage : history) {
            dialogBuilder.append(chatMessage.getRole()).append(": ").append(chatMessage.getContent()).append("\n");
        }
        dialogBuilder.append("用户当前输入:\n").append(text);
        if(docsContents != null && !docsContents.isEmpty()) {
            for (String docContent : docsContents) {
                docBuilder.append("文档内容：").append(docContent).append("\n");
            }
            String docPrompt = WorkflowPromptUtil.getWorkflowDocPrompt();
            ChatCompletionRequest docRequest = completionsService.getCompletionsRequest(
                    docPrompt,
                    docBuilder.append(dialogBuilder).toString(),
                    DEFAULT_TEMPERATURE,
                    DEFAULT_MAX_TOKENS
            );
            ChatCompletionResult completions = completionsService.completions(docRequest);
            dialogBuilder = new StringBuilder(completions.getChoices().get(0).getMessage().getContent());
        }
        System.out.println("对话内容：" +  dialogBuilder);
        return txt2FlowSchema1(dialogBuilder.toString());
//        ChatCompletionRequest genRequest = completionsService.getCompletionsRequest(
//                WorkflowPromptUtil.getWorkflowReActPrompt(),
//                dialogBuilder.toString(),
//                DEFAULT_TEMPERATURE,
//                DEFAULT_MAX_TOKENS
//        );
//        genRequest.setTools(WorkerFlowToolService.getTools());
//        ChatCompletionResult result = completionsService.completions(genRequest);
//        System.out.println(new Gson().toJson(result));
//        ChatMessage assistantMessage = result.getChoices().get(0).getMessage();
//        List<ToolCall> functionCalls = assistantMessage.getTool_calls();
//        List<ChatMessage> chatMessages = genRequest.getMessages();
//        chatMessages.add(assistantMessage);
//        while (functionCalls != null && !functionCalls.isEmpty()) {
//            List<ToolCall> loopFunctionCalls =  new ArrayList<>(functionCalls);
//            List<ToolCall> nextFunctionCalls =  new ArrayList<>();
//            for (ToolCall functionCall: loopFunctionCalls) {
//                String callToolResult = WorkerFlowToolService.invokeTool(functionCall.getFunction().getName(), functionCall.getFunction().getArguments());
//                ChatMessage toolChatMessage = new ChatMessage();
//                toolChatMessage.setRole("tool");
//                toolChatMessage.setTool_call_id(functionCall.getId());
//                toolChatMessage.setContent(callToolResult);
//                chatMessages.add(toolChatMessage);
//                ChatCompletionResult temp = completionsService.completions(genRequest);
//                System.out.println(new Gson().toJson(temp));
//                assistantMessage = temp.getChoices().get(0).getMessage();
//                chatMessages.add(assistantMessage);
//                if (assistantMessage.getTool_calls() != null) {
//                    nextFunctionCalls.addAll(assistantMessage.getTool_calls());
//                }
//                if(temp.getChoices().get(0).getMessage().getContent() != null) {
//                    result.getChoices().get(0).setMessage(assistantMessage);
//                }
//            }
//            functionCalls = nextFunctionCalls;
//        }
//        return "";
    }



    public static void main(String[] args) {
        ContextLoader.loadContext();
        String s = WorkflowGenerator.txt2FlowSchema("帮我生成一个调用天气api的的工作流", Collections.emptyList(), Collections.emptyList());
        System.out.println(s);
    }

}
