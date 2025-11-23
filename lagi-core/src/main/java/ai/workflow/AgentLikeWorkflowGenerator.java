package ai.workflow;

import ai.llm.service.CompletionsService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.utils.JsonExtractor;
import ai.workflow.utils.NodeReferenceValidator;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Agent-like Workflow Generator
 * 使用多阶段推理和自我验证机制生成稳定的工作流
 *
 * 核心特性:
 * 1. 多阶段推理: 任务分解 -> 节点选择 -> 配置生成 -> 边连接 -> 验证修正
 * 2. 自我验证: 每个阶段都进行验证，发现问题自动重试
 * 3. 节点可扩展: 节点定义与生成逻辑分离，新增节点无需修改代码
 * 4. 历史感知: 支持对话历史和文档上下文
 */
@Slf4j
public class AgentLikeWorkflowGenerator {

    private static final CompletionsService completionsService = new CompletionsService();
    private static final Gson gson = new Gson();

    private static final double DEFAULT_TEMPERATURE = 0.3;
    private static final int DEFAULT_MAX_TOKENS = 4096;
    private static final int MAX_RETRIES = 3;
    private static final int MAX_GENERATION_STEPS = 50;

    /**
     * 工作流生成上下文
     */
    private static class GenerationContext {
        String userText;
        List<ChatMessage> history;
        List<String> docsContents;

        // 生成过程中的中间结果
        String taskAnalysis;           // 任务分析结果
        String userSpecifiedConfigs;   // 用户指定的节点配置信息（如模型名称、参数等）
        String nodeSelection;          // 节点选择结果
        JsonArray generatedNodes;      // 已生成的节点
        JsonArray generatedEdges;      // 已生成的边

        // 验证和修正
        int retryCount;                // 重试次数

        GenerationContext(String userText, List<ChatMessage> history, List<String> docsContents) {
            this.userText = userText;
            this.history = history != null ? history : Collections.emptyList();
            this.docsContents = docsContents != null ? docsContents : Collections.emptyList();
            this.generatedNodes = new JsonArray();
            this.generatedEdges = new JsonArray();
            this.retryCount = 0;
        }
    }

    /**
     * 主入口：生成工作流 JSON
     *
     * @param text 用户输入文本
     * @param history 对话历史
     * @param docsContents 文档内容
     * @return 工作流 JSON 字符串
     */
    public static String generate(String text, List<ChatMessage> history, List<String> docsContents) {
        log.info("Starting agent-like workflow generation");
        log.info("User input: {}", text);

        GenerationContext context = new GenerationContext(text, history, docsContents);

        try {
            // Stage 1: 文档和历史上下文处理（如果有）
            String processedInput = stage1_ProcessContext(context);
            context.userText = processedInput;

            // Stage 2: 任务分析和分解
            stage2_TaskAnalysis(context);

            // Stage 3: 节点选择和验证
            stage3_NodeSelection(context);

            // Stage 4: 逐步生成节点和边
            stage4_GenerateWorkflow(context);

            // Stage 5: 最终验证和修正
            String workflowJson = stage5_ValidateAndFix(context);

            log.info("Workflow generation completed successfully");
            return workflowJson;

        } catch (Exception e) {
            log.error("Failed to generate workflow: {}", e.getMessage(), e);
            throw new RuntimeException("Workflow generation failed", e);
        }
    }

    /**
     * Stage 1: 处理文档和历史上下文
     * 如果有文档或历史，先提取关键信息并整合
     */
    private static String stage1_ProcessContext(GenerationContext context) {
        log.info("Stage 1: Processing context (docs + history)");

        // 构建对话历史
        StringBuilder dialogBuilder = new StringBuilder();
        if (!context.history.isEmpty()) {
            dialogBuilder.append("历史对话：\n");
            for (ChatMessage msg : context.history) {
                dialogBuilder.append(msg.getRole()).append(": ")
                        .append(msg.getContent()).append("\n");
            }
            dialogBuilder.append("\n");
        }
        dialogBuilder.append("用户当前输入：\n").append(context.userText);

        // 如果有文档，使用 LLM 提取工作流相关信息
        if (!context.docsContents.isEmpty()) {
            StringBuilder docBuilder = new StringBuilder();
            for (String docContent : context.docsContents) {
                docBuilder.append("文档内容：\n").append(docContent).append("\n\n");
            }

            String docPrompt = WorkflowPromptUtil.getWorkflowDocPrompt() + 
                    "\n\n## ⚠️ 额外要求：配置信息提取\n" +
                    "在提取工作流描述时，特别注意提取以下配置信息：\n" +
                    "1. 节点使用的具体模型名称（如 qwen-turbo、gpt-4 等）\n" +
                    "2. 知识库类别或名称\n" +
                    "3. API的URL和参数\n" +
                    "4. 其他明确指定的节点配置参数\n" +
                    "这些配置信息必须在 workflow_text 中明确体现。";
            String combinedInput = docBuilder.append(dialogBuilder).toString();

            String extractedInfo = callLLMWithRetry(docPrompt, combinedInput, MAX_RETRIES);
            if (extractedInfo != null) {
                JsonObject jsonObject = gson.fromJson(extractedInfo, JsonObject.class);
                if (jsonObject.has("workflow_text")) {
                    String workflowText = jsonObject.get("workflow_text").getAsString();
                    log.info("Extracted workflow text from documents: {}", workflowText);
                    return workflowText;
                }
            }
        }

        return dialogBuilder.toString();
    }

    /**
     * Stage 2: 任务分析和分解
     * 分析用户需求，分解为具体的处理步骤，并提取用户指定的配置信息
     */
    private static void stage2_TaskAnalysis(GenerationContext context) {
        log.info("Stage 2: Task analysis and decomposition");

        String prompt = buildTaskAnalysisPrompt();

        ChatCompletionRequest request = completionsService.getCompletionsRequest(
                prompt,
                context.userText,
                DEFAULT_TEMPERATURE,
                DEFAULT_MAX_TOKENS
        );

        ChatCompletionResult result = completionsService.completions(request);
        String responseContent = result.getChoices().get(0).getMessage().getContent();

        // 尝试提取JSON格式的响应（包含任务分析和配置信息）
        String extractedJson = JsonExtractor.extractJson(responseContent);
        if (JsonExtractor.isJson(extractedJson)) {
            try {
                JsonObject analysisObj = gson.fromJson(extractedJson, JsonObject.class);
                
                // 提取任务分析
                if (analysisObj.has("task_analysis")) {
                    context.taskAnalysis = analysisObj.get("task_analysis").getAsString();
                } else {
                    context.taskAnalysis = responseContent;
                }
                
                // 提取用户指定的配置信息
                if (analysisObj.has("user_specified_configs")) {
                    context.userSpecifiedConfigs = analysisObj.get("user_specified_configs").getAsString();
                    log.info("Extracted user-specified configs: {}", context.userSpecifiedConfigs);
                } else {
                    context.userSpecifiedConfigs = "";
                }
            } catch (Exception e) {
                log.warn("Failed to parse analysis JSON, using raw response: {}", e.getMessage());
                context.taskAnalysis = responseContent;
                context.userSpecifiedConfigs = "";
            }
        } else {
            // 如果不是JSON格式，直接使用原始响应
            context.taskAnalysis = responseContent;
            context.userSpecifiedConfigs = "";
        }

        log.info("Task analysis result: {}", context.taskAnalysis);
    }

    /**
     * Stage 3: 节点选择和验证
     * 根据任务分析，选择合适的节点类型
     */
    private static void stage3_NodeSelection(GenerationContext context) {
        log.info("Stage 3: Node selection and validation");

        List<String> notValidNodeName = ai.workflow.utils.DefaultNodeEnum.getNotValidNodeName();
        String basePrompt = WorkflowPromptUtil.getNodeMatchingPrompt(notValidNodeName);

        // 增强提示词，强调简洁原则
        String enhancedPrompt = basePrompt +
                "\n\n## ⚠️ 简洁性要求（非常重要）\n\n" +
                "在选择节点时，**必须遵循最少节点原则**：\n\n" +
                "1. **优先使用单节点方案**：如果一个节点能完成任务，不要选择多个节点\n" +
                "2. **避免功能重叠**：不要选择功能重复或冗余的节点\n" +
                "3. **直接功能映射**：\n" +
                "   - 文生视频 → 只需 `text2video`\n" +
                "   - 文生图 → 只需 `text2image`\n" +
                "   - 图生视频 → 只需 `image2video`\n" +
                "   - 文本翻译 → 只需 `translate`\n" +
                "   - 语音识别 → 只需 `asr`\n" +
                "4. **不要过度分解**：除非任务明确需要多个步骤，否则不要拆分\n\n" +
                "**错误示例**：\n" +
                "❌ 用户要文生视频，却选择：llm + text2image + image2video（过度拆分）\n" +
                "✅ 正确做法：只选择 text2video\n\n" +
                "**选择标准**：\n" +
                "- 如果任务分析显示只需1-2个步骤，那么只选择1-2个核心节点（加上start和end）\n" +
                "- 如果不确定，优先选择功能最直接的节点\n";

        // 注入任务分析结果
        String combinedInput = "【任务分析】\n" + context.taskAnalysis +
                "\n\n【用户输入】\n" + context.userText;

        ChatCompletionRequest request = completionsService.getCompletionsRequest(
                enhancedPrompt,
                combinedInput,
                DEFAULT_TEMPERATURE,
                DEFAULT_MAX_TOKENS
        );

        ChatCompletionResult result = completionsService.completions(request);
        String nodeSelection = result.getChoices().get(0).getMessage().getContent();

        context.nodeSelection = nodeSelection;
        log.info("Node selection result: {}", nodeSelection);
    }

    /**
     * Stage 4: 逐步生成工作流节点和边
     * 使用迭代式生成，每次生成一个或多个节点及其连接
     */
    private static void stage4_GenerateWorkflow(GenerationContext context) {
        log.info("Stage 4: Generating workflow step by step");

        List<String> notValidNodeName = ai.workflow.utils.DefaultNodeEnum.getNotValidNodeName();
        String basePrompt = WorkflowPromptUtil.getPromptToWorkflowStepByStepJson(notValidNodeName);

        // 构建用户配置信息部分
        String configSection = "";
        if (context.userSpecifiedConfigs != null && !context.userSpecifiedConfigs.trim().isEmpty()) {
            configSection = "\n\n【⚠️ 用户明确指定的配置信息 - 必须严格遵守】\n" + 
                    context.userSpecifiedConfigs + 
                    "\n\n**配置应用规则**：\n" +
                    "- 生成节点时，必须将用户指定的配置信息准确填入对应节点的字段中\n" +
                    "- 例如：如果用户指定\"LLM使用qwen-turbo\"，则LLM节点的inputsValues中model字段必须设为{\"type\": \"string\", \"content\": \"qwen-turbo\"}\n" +
                    "- 例如：如果用户指定\"查询客服知识库\"，则knowledge-base节点的category字段必须设为{\"type\": \"string\", \"content\": \"客服知识库\"}\n" +
                    "- 不要忽略或遗漏用户明确指定的任何配置参数\n";
        }

        // 添加简洁性提醒和条件节点特殊规则到输入中
        String combinedInput = "⚠️ **重要提醒：请严格遵循任务分析和节点选择的结果，不要添加额外的节点**\n\n" +
                "【任务分析】\n" + context.taskAnalysis +
                "\n\n【节点选择】\n" + context.nodeSelection +
                configSection +
                "\n\n【用户输入】\n" + context.userText +
                "\n\n**生成原则**：\n" +
                "- 只生成节点选择阶段确定的节点，不要添加其他节点\n" +
                "- 如果节点选择只列出了3-4个节点，那就只生成这些节点\n" +
                "- 保持工作流简洁，避免冗余节点\n" +
                "- 必须严格按照用户明确指定的配置信息设置节点参数\n\n" +
                "## ⚠️ 条件节点边连接特殊规则（非常重要）\n\n" +
                "**当工作流包含 condition（条件判断）节点时，必须遵循以下规则：**\n\n" +
                "1. **条件节点结构**：\n" +
                "   ```json\n" +
                "   {\n" +
                "     \"id\": \"condition_xxxxx\",\n" +
                "     \"type\": \"condition\",\n" +
                "     \"data\": {\n" +
                "       \"conditions\": [\n" +
                "         {\"key\": \"if_xxxxx\", \"value\": {...}},  // 第一个分支\n" +
                "         {\"key\": \"if_yyyyy\", \"value\": {...}}   // 第二个分支\n" +
                "       ]\n" +
                "     }\n" +
                "   }\n" +
                "   ```\n\n" +
                "2. **条件节点的边必须包含 sourcePortID**：\n" +
                "   从条件节点出发的每条边都必须指定 `sourcePortID`，值为对应条件的 `key`\n" +
                "   ```json\n" +
                "   // 正确示例\n" +
                "   {\"sourceNodeID\": \"condition_xxxxx\", \"targetNodeID\": \"llm_1\", \"sourcePortID\": \"if_xxxxx\"},\n" +
                "   {\"sourceNodeID\": \"condition_xxxxx\", \"targetNodeID\": \"text2image_1\", \"sourcePortID\": \"if_yyyyy\"}\n" +
                "   ```\n\n" +
                "3. **完整示例参考**：\n" +
                "   ```json\n" +
                "   // 条件节点\n" +
                "   {\"id\": \"condition_lhcoc9\", \"type\": \"condition\",\n" +
                "    \"data\": {\"conditions\": [\n" +
                "      {\"key\": \"if_lhcoc9\", \"value\": {...}},\n" +
                "      {\"key\": \"if_le493Z\", \"value\": {...}}\n" +
                "    ]}}\n\n" +
                "   // 对应的边（注意 sourcePortID）\n" +
                "   {\"sourceNodeID\": \"condition_lhcoc9\", \"targetNodeID\": \"kb_BRQB8\", \"sourcePortID\": \"if_lhcoc9\"},\n" +
                "   {\"sourceNodeID\": \"condition_lhcoc9\", \"targetNodeID\": \"text2image_BRQB8\", \"sourcePortID\": \"if_le493Z\"}\n" +
                "   ```\n\n" +
                "4. **重要提醒**：\n" +
                "   - 每个条件分支必须有一条对应的边\n" +
                "   - sourcePortID 必须与条件的 key 完全匹配\n" +
                "   - 不要遗漏 sourcePortID 字段\n\n" +
                "## ⚠️ End 节点多输入规则（非常重要）\n\n" +
                "**当多个节点都指向 end 节点时（例如条件分支汇聚），end 节点必须定义多个 inputs：**\n\n" +
                "1. **分析指向 end 的所有节点**：\n" +
                "   - 条件分支A → llm_1 → end\n" +
                "   - 条件分支B → text2image_1 → end\n\n" +
                "2. **为每个输入定义不同的字段名**：\n" +
                "   ```json\n" +
                "   {\n" +
                "     \"id\": \"end_0\",\n" +
                "     \"type\": \"end\",\n" +
                "     \"data\": {\n" +
                "       \"inputs\": {\n" +
                "         \"type\": \"object\",\n" +
                "         \"required\": [\"answer\", \"image_url\"],  // 多个输入字段\n" +
                "         \"properties\": {\n" +
                "           \"answer\": {\"type\": \"string\"},      // 来自 llm\n" +
                "           \"image_url\": {\"type\": \"string\"}    // 来自 text2image\n" +
                "         }\n" +
                "       },\n" +
                "       \"inputsValues\": {\n" +
                "         \"answer\": {\"type\": \"ref\", \"content\": [\"llm_1\", \"result\"]},\n" +
                "         \"image_url\": {\"type\": \"ref\", \"content\": [\"text2image_1\", \"result\"]}\n" +
                "       }\n" +
                "     }\n" +
                "   }\n" +
                "   ```\n\n" +
                "3. **字段命名建议**：\n" +
                "   - 根据节点功能命名：llm → answer、text2image → image_url\n" +
                "   - 如果同类型节点有多个：result1、result2 或 llm_result、api_result\n\n" +
                "4. **完整示例（条件分支汇聚到 end）**：\n" +
                "   ```json\n" +
                "   // 场景：用户问题 → 意图识别 → 条件判断\n" +
                "   //   分支1（文本）：知识库 → LLM → end\n" +
                "   //   分支2（图片）：text2image → end\n" +
                "   \n" +
                "   // end 节点配置\n" +
                "   {\n" +
                "     \"id\": \"end_0\",\n" +
                "     \"data\": {\n" +
                "       \"inputs\": {\n" +
                "         \"required\": [\"answer\", \"image_url\"],\n" +
                "         \"properties\": {\n" +
                "           \"answer\": {\"type\": \"string\"},\n" +
                "           \"image_url\": {\"type\": \"string\"}\n" +
                "         }\n" +
                "       },\n" +
                "       \"inputsValues\": {\n" +
                "         \"answer\": {\"type\": \"ref\", \"content\": [\"llm_1\", \"result\"]},\n" +
                "         \"image_url\": {\"type\": \"ref\", \"content\": [\"text2image_BRQB8\", \"result\"]}\n" +
                "       }\n" +
                "     }\n" +
                "   }\n" +
                "   \n" +
                "   // 对应的边\n" +
                "   [\n" +
                "     {\"sourceNodeID\": \"llm_1\", \"targetNodeID\": \"end_0\"},\n" +
                "     {\"sourceNodeID\": \"text2image_BRQB8\", \"targetNodeID\": \"end_0\"}\n" +
                "   ]\n" +
                "   ```\n\n" +
                "5. **关键规则**：\n" +
                "   - 有几个节点指向 end，就定义几个 input 字段\n" +
                "   - 每个 input 字段名必须唯一且有意义\n" +
                "   - inputsValues 中每个字段都要正确引用对应节点的输出\n" +
                "   - 所有指向 end 的节点都应该在 inputs.required 数组中列出\n";

        ChatCompletionRequest request = completionsService.getCompletionsRequest(
                basePrompt,
                combinedInput,
                DEFAULT_TEMPERATURE,
                DEFAULT_MAX_TOKENS
        );

        int stepCount = 0;
        int maxSteps = MAX_GENERATION_STEPS;

        while (maxSteps-- > 0) {
            stepCount++;
            log.info("Generation step {}", stepCount);

            ChatCompletionResult result = completionsService.completions(request);
            String responseJson = result.getChoices().get(0).getMessage().getContent();

            // 提取 JSON
            responseJson = JsonExtractor.extractJson(responseJson);
            if (!JsonExtractor.isJson(responseJson)) {
                log.warn("Step {}: Invalid JSON response, retrying", stepCount);
                continue;
            }

            JsonObject responseObj = gson.fromJson(responseJson, JsonObject.class);

            // 检查是否完成
            JsonElement finishedElement = responseObj.get("finished");
            if (finishedElement == null) {
                log.warn("Step {}: Missing 'finished' field", stepCount);
                continue;
            }

            boolean finished = finishedElement.getAsBoolean();

            // 记录计划信息
            logGenerationPlan(responseObj, stepCount);

            // 提取并添加节点
            JsonElement nodeElement = responseObj.get("node");
            if (nodeElement != null && !nodeElement.isJsonNull()) {
                context.generatedNodes.add(nodeElement);
                logNodeAdded(nodeElement, stepCount);
            }

            // 提取并更新边
            JsonElement edgesElement = responseObj.get("edges");
            if (edgesElement != null && edgesElement.isJsonArray()) {
                context.generatedEdges = edgesElement.getAsJsonArray();
                log.info("Step {}: Updated edges (total: {})", stepCount, context.generatedEdges.size());
            }

            // 检查是否完成
            if (finished) {
                log.info("Workflow generation finished after {} steps", stepCount);
                break;
            }

            // 准备下一轮对话
            ChatMessage assistant = ChatMessage.builder()
                    .content(responseJson)
                    .role("assistant")
                    .build();
            ChatMessage user = ChatMessage.builder()
                    .content("继续生成节点或边列表")
                    .role("user")
                    .build();

            request.getMessages().add(assistant);
            request.getMessages().add(user);
        }

        if (maxSteps <= 0) {
            log.error("Generation exceeded maximum steps: {}", MAX_GENERATION_STEPS);
            throw new RuntimeException("Workflow generation exceeded maximum steps");
        }
    }

    /**
     * Stage 5: 验证和修正
     * 验证生成的工作流，如果有问题则尝试修正
     */
    private static String stage5_ValidateAndFix(GenerationContext context) {
        log.info("Stage 5: Validation and fixing");

        // 构建完整的工作流 JSON
        JsonObject workflow = new JsonObject();
        workflow.add("nodes", context.generatedNodes);
        workflow.add("edges", context.generatedEdges);

        String workflowJson = gson.toJson(workflow);

        // 特殊验证1：检查条件节点的边是否包含 sourcePortID
        validateConditionNodeEdges(context.generatedNodes, context.generatedEdges);

        // 特殊验证2：检查 end 节点是否正确处理多输入
        validateEndNodeInputs(context.generatedNodes, context.generatedEdges);

        // 使用 NodeReferenceValidator 验证
        NodeReferenceValidator.ValidationResult validationResult =
                NodeReferenceValidator.validate(workflowJson);

        if (!validationResult.hasErrors() && !validationResult.hasWarnings()) {
            log.info("Workflow validation passed");
            return workflowJson;
        }

        // 记录问题
        if (validationResult.hasWarnings()) {
            log.warn("Workflow has warnings:\n{}", validationResult);
        }

        if (validationResult.hasErrors()) {
            log.error("Workflow has errors:\n{}", validationResult);

            // 尝试自动修正
            if (context.retryCount < MAX_RETRIES) {
                context.retryCount++;
                log.info("Attempting to fix workflow (attempt {}/{})",
                        context.retryCount, MAX_RETRIES);

                String fixedJson = attemptAutoFix(workflowJson, validationResult, context);
                if (fixedJson != null) {
                    // 重新验证
                    NodeReferenceValidator.ValidationResult revalidation =
                            NodeReferenceValidator.validate(fixedJson);
                    if (!revalidation.hasErrors()) {
                        log.info("Auto-fix successful");
                        return fixedJson;
                    }
                }
            }

            // 如果无法修正，仍然返回，但记录错误
            log.warn("Returning workflow with validation errors");
        }

        return workflowJson;
    }

    /**
     * 尝试自动修正工作流中的问题
     */
    private static String attemptAutoFix(String workflowJson,
                                         NodeReferenceValidator.ValidationResult validationResult,
                                         GenerationContext context) {
        log.info("Attempting to auto-fix workflow issues");

        try {
            // 构建修正提示
            String fixPrompt = buildFixPrompt(validationResult);
            String fixInput = "【原始工作流】\n" + workflowJson +
                    "\n\n【验证问题】\n" + validationResult.toString() +
                    "\n\n【原始需求】\n" + context.userText;

            ChatCompletionRequest request = completionsService.getCompletionsRequest(
                    fixPrompt,
                    fixInput,
                    DEFAULT_TEMPERATURE,
                    DEFAULT_MAX_TOKENS
            );

            ChatCompletionResult result = completionsService.completions(request);
            String fixedJson = result.getChoices().get(0).getMessage().getContent();
            fixedJson = JsonExtractor.extractJson(fixedJson);

            if (JsonExtractor.isJson(fixedJson)) {
                return fixedJson;
            }

        } catch (Exception e) {
            log.error("Auto-fix failed: {}", e.getMessage(), e);
        }

        return null;
    }

    /**
     * 使用重试机制调用 LLM
     */
    private static String callLLMWithRetry(String prompt, String input, int maxRetries) {
        int retries = maxRetries;
        while (retries-- > 0) {
            try {
                ChatCompletionRequest request = completionsService.getCompletionsRequest(
                        prompt, input, DEFAULT_TEMPERATURE, DEFAULT_MAX_TOKENS
                );
                ChatCompletionResult result = completionsService.completions(request);
                String json = result.getChoices().get(0).getMessage().getContent();
                json = JsonExtractor.extractJson(json);

                if (JsonExtractor.isJson(json)) {
                    return json;
                }
            } catch (Exception e) {
                log.warn("LLM call failed (retries left: {}): {}", retries, e.getMessage());
            }
        }
        return null;
    }

    /**
     * 构建任务分析提示词
     */
    private static String buildTaskAnalysisPrompt() {
        return "# 任务分析与配置提取\n\n" +
                "你是一个工作流设计专家。请分析用户的需求，**遵循简洁原则**，识别完成任务所需的**最少步骤**，" +
                "同时**提取用户明确指定的节点配置信息**（如模型名称、参数设置等）。\n\n" +
                "## ⚠️ 重要原则\n\n" +
                "1. **简洁优先**：能用一个节点完成的任务，不要拆分成多个步骤\n" +
                "2. **避免冗余**：不要添加不必要的中间处理步骤\n" +
                "3. **直接映射**：如果用户明确要求某个功能（如文生视频），直接使用对应的专用节点（text2video）\n" +
                "4. **保留配置细节**：用户明确指定的配置信息（如模型名称、参数）必须完整提取并保留\n\n" +
                "## 常见节点能力\n\n" +
                "- **text2video**: 直接从文本生成视频，无需先生成图片\n" +
                "- **text2image**: 直接从文本生成图片\n" +
                "- **image2video**: 从图片生成视频\n" +
                "- **llm**: 文本理解和生成，对话回答（支持model参数指定模型）\n" +
                "- **knowledge-base**: 检索知识库内容（支持category参数指定知识库）\n" +
                "- **api**: 调用外部API接口（支持url、method等参数）\n" +
                "- **translate**: 翻译文本\n" +
                "- **tts**: 文本转语音（支持voice、model等参数）\n" +
                "- **asr**: 语音转文本\n\n" +
                "## 分析要求\n\n" +
                "1. **识别核心需求**：用户真正想要什么？\n" +
                "2. **检查是否有直接节点**：是否存在能直接完成任务的节点？\n" +
                "3. **最小化步骤**：只列出**必需**的处理步骤，避免过度分解\n" +
                "4. **评估复杂度**：评估任务复杂度（简单/中等/复杂）\n" +
                "5. **提取配置信息**：识别并提取用户明确指定的所有配置参数\n\n" +
                "## 配置信息提取（重点）\n\n" +
                "仔细分析用户输入，提取以下类型的配置信息：\n" +
                "1. **模型指定**：如\"使用 qwen-turbo 模型\"、\"用 gpt-4\"、\"使用通义千问\"\n" +
                "2. **参数设置**：如\"温度设为0.7\"、\"最大长度2000\"、\"top_p=0.9\"\n" +
                "3. **资源指定**：如\"查询客服知识库\"、\"使用prod环境的API\"\n" +
                "4. **格式要求**：如\"输出JSON格式\"、\"语音使用女声\"\n" +
                "5. **其他配置**：任何用户明确指定的节点配置细节\n\n" +
                "⚠️ **注意**：\n" +
                "- 只提取用户**明确指定**的配置，不要推测或添加默认值\n" +
                "- 配置信息要具体到节点类型和字段名\n" +
                "- 如果用户没有指定任何配置，输出\"无\"\n\n" +
                "## 输出格式（JSON）\n\n" +
                "```json\n" +
                "{\n" +
                "  \"task_analysis\": \"### 任务目标\\n[一句话描述]\\n\\n### 核心功能识别\\n[节点类型]\\n\\n### 输入信息\\n- [输入1]\\n\\n### 输出结果\\n- [输出1]\\n\\n### 处理步骤（最少必需步骤）\\n1. [步骤1]\\n\\n### 复杂度评估\\n[简单/中等/复杂]\\n\\n### 简洁性检查\\n[说明]\",\n" +
                "  \"user_specified_configs\": \"【节点配置信息】\\n- LLM节点: model=qwen-turbo\\n- Knowledge-Base节点: category=客服知识库\\n- 或：无用户指定的配置\"\n" +
                "}\n" +
                "```\n\n" +
                "## 示例\n\n" +
                "### 示例1：包含配置信息\n" +
                "**用户输入**：创建一个工作流，使用qwen-turbo模型回答问题，先查询客服知识库\n\n" +
                "**输出**：\n" +
                "```json\n" +
                "{\n" +
                "  \"task_analysis\": \"### 任务目标\\n创建知识库问答工作流\\n\\n### 核心功能识别\\nknowledge-base + llm\\n\\n### 处理步骤\\n1. 检索客服知识库\\n2. 使用LLM生成回答\\n\\n### 复杂度评估\\n简单\",\n" +
                "  \"user_specified_configs\": \"【节点配置信息】\\n- LLM节点: model字段必须设置为 'qwen-turbo'\\n- Knowledge-Base节点: category字段必须设置为 '客服知识库'\"\n" +
                "}\n" +
                "```\n\n" +
                "### 示例2：无配置信息\n" +
                "**用户输入**：生成一个文生视频的工作流\n\n" +
                "**输出**：\n" +
                "```json\n" +
                "{\n" +
                "  \"task_analysis\": \"### 任务目标\\n文本生成视频\\n\\n### 核心功能识别\\ntext2video\\n\\n### 处理步骤\\n1. 使用text2video直接生成\\n\\n### 复杂度评估\\n简单\",\n" +
                "  \"user_specified_configs\": \"无用户明确指定的配置信息\"\n" +
                "}\n" +
                "```\n\n" +
                "请严格按照JSON格式输出，不要包含其他说明。";
    }

    /**
     * 构建修正提示词
     */
    private static String buildFixPrompt(NodeReferenceValidator.ValidationResult validationResult) {
        return "# 工作流修正\n\n" +
                "你是一个工作流修正专家。工作流生成后发现了一些问题，请修正它。\n\n" +
                "## 修正要求\n\n" +
                "1. **保持原意**：修正时保持原工作流的功能和意图\n" +
                "2. **修复错误**：修复所有验证错误\n" +
                "3. **改进警告**：尽量改进警告提示的问题\n" +
                "4. **完整输出**：输出完整的修正后的工作流 JSON\n\n" +
                "## 常见问题及修正方法\n\n" +
                "### 1. 节点引用错误\n" +
                "- 节点引用不存在的字段：检查节点输出字段是否正确（如 llm 节点输出 result 而非 answer）\n" +
                "- 边连接错误：检查 sourceNodeID 和 targetNodeID 是否存在\n\n" +
                "### 2. 结构错误\n" +
                "- 缺少必需节点：工作流必须有 start 和 end 节点\n" +
                "- 节点孤立：所有节点都应该连接到工作流中\n\n" +
                "### 3. 条件节点边连接错误（⚠️ 非常重要）\n" +
                "**如果工作流包含 condition 节点，从条件节点出发的边必须包含 `sourcePortID` 字段：**\n\n" +
                "错误示例（缺少 sourcePortID）：\n" +
                "```json\n" +
                "// ❌ 错误：从条件节点出发的边缺少 sourcePortID\n" +
                "{\"sourceNodeID\": \"condition_xxx\", \"targetNodeID\": \"llm_1\"}\n" +
                "```\n\n" +
                "正确示例（包含 sourcePortID）：\n" +
                "```json\n" +
                "// ✓ 正确：必须包含 sourcePortID，值为条件的 key\n" +
                "{\"sourceNodeID\": \"condition_xxx\", \"targetNodeID\": \"llm_1\", \"sourcePortID\": \"if_xxxxx\"}\n" +
                "```\n\n" +
                "修正步骤：\n" +
                "1. 找到条件节点的所有条件分支（data.conditions[].key）\n" +
                "2. 为从该条件节点出发的每条边添加 sourcePortID\n" +
                "3. sourcePortID 的值必须是对应条件分支的 key\n" +
                "4. 确保每个条件分支都有一条对应的边\n\n" +
                "完整示例：\n" +
                "```json\n" +
                "// 条件节点\n" +
                "{\"id\": \"condition_lhcoc9\", \"type\": \"condition\",\n" +
                " \"data\": {\"conditions\": [\n" +
                "   {\"key\": \"if_lhcoc9\", \"value\": {...}},\n" +
                "   {\"key\": \"if_le493Z\", \"value\": {...}}\n" +
                " ]}}\n\n" +
                "// 对应的边（必须包含 sourcePortID）\n" +
                "[\n" +
                "  {\"sourceNodeID\": \"condition_lhcoc9\", \"targetNodeID\": \"kb_1\", \"sourcePortID\": \"if_lhcoc9\"},\n" +
                "  {\"sourceNodeID\": \"condition_lhcoc9\", \"targetNodeID\": \"text2image_1\", \"sourcePortID\": \"if_le493Z\"}\n" +
                "]\n" +
                "```\n\n" +
                "### 4. End 节点多输入错误（⚠️ 非常重要）\n" +
                "**当多个节点指向 end 节点时，end 必须定义多个 input 字段来接收不同的输出：**\n\n" +
                "错误示例（多个节点指向 end，但 end 只有一个 input）：\n" +
                "```json\n" +
                "// ❌ 错误：llm_1 和 text2image_1 都指向 end，但 end 只接收一个输入\n" +
                "{\n" +
                "  \"id\": \"end_0\",\n" +
                "  \"data\": {\n" +
                "    \"inputs\": {\n" +
                "      \"properties\": {\"result\": {\"type\": \"string\"}},  // 只有一个字段\n" +
                "      \"required\": [\"result\"]\n" +
                "    },\n" +
                "    \"inputsValues\": {\n" +
                "      \"result\": {\"type\": \"ref\", \"content\": [\"llm_1\", \"result\"]}\n" +
                "      // 缺少 text2image_1 的输入！\n" +
                "    }\n" +
                "  }\n" +
                "}\n" +
                "```\n\n" +
                "正确示例（为每个输入源定义字段）：\n" +
                "```json\n" +
                "// ✓ 正确：定义多个 input 字段接收不同节点的输出\n" +
                "{\n" +
                "  \"id\": \"end_0\",\n" +
                "  \"data\": {\n" +
                "    \"inputs\": {\n" +
                "      \"properties\": {\n" +
                "        \"answer\": {\"type\": \"string\"},       // 接收 llm 输出\n" +
                "        \"image_url\": {\"type\": \"string\"}     // 接收 text2image 输出\n" +
                "      },\n" +
                "      \"required\": [\"answer\", \"image_url\"]  // 两个都是必需的\n" +
                "    },\n" +
                "    \"inputsValues\": {\n" +
                "      \"answer\": {\"type\": \"ref\", \"content\": [\"llm_1\", \"result\"]},\n" +
                "      \"image_url\": {\"type\": \"ref\", \"content\": [\"text2image_1\", \"result\"]}\n" +
                "    }\n" +
                "  }\n" +
                "}\n" +
                "```\n\n" +
                "修正步骤：\n" +
                "1. 查找所有指向 end 节点的边，统计有多少个源节点\n" +
                "2. 为每个源节点在 end 的 inputs.properties 中定义一个字段\n" +
                "3. 字段命名要有意义（如 answer, image_url, api_result）\n" +
                "4. 在 inputsValues 中为每个字段正确设置引用\n" +
                "5. 将所有字段加入 inputs.required 数组\n\n" +
                "请只输出修正后的完整 JSON，不要包含其他说明。";
    }

    /**
     * 记录生成计划信息
     */
    private static void logGenerationPlan(JsonObject responseObj, int stepCount) {
        JsonElement planElement = responseObj.get("plan");
        if (planElement != null && planElement.isJsonObject()) {
            JsonObject plan = planElement.getAsJsonObject();
            log.info("Step {}: Generation Plan", stepCount);

            if (plan.has("current_step")) {
                log.info("  Current Step: {}", plan.get("current_step").getAsString());
            }
            if (plan.has("next_action")) {
                log.info("  Next Action: {}", plan.get("next_action").getAsString());
            }
        }
    }

    /**
     * 记录添加的节点信息
     */
    private static void logNodeAdded(JsonElement nodeElement, int stepCount) {
        if (nodeElement.isJsonObject()) {
            JsonObject node = nodeElement.getAsJsonObject();
            String nodeId = node.has("id") ? node.get("id").getAsString() : "unknown";
            String nodeType = node.has("type") ? node.get("type").getAsString() : "unknown";
            log.info("Step {}: Added node {} (type: {})", stepCount, nodeId, nodeType);
        }
    }

    /**
     * 验证条件节点的边是否包含必需的 sourcePortID
     */
    private static void validateConditionNodeEdges(JsonArray nodes, JsonArray edges) {
        // 找出所有条件节点
        Map<String, JsonArray> conditionNodeKeys = new HashMap<>();

        for (JsonElement nodeElement : nodes) {
            if (nodeElement.isJsonObject()) {
                JsonObject node = nodeElement.getAsJsonObject();
                if (node.has("type") && "condition".equals(node.get("type").getAsString())) {
                    String nodeId = node.get("id").getAsString();

                    // 提取条件节点的所有 key
                    JsonArray keys = new JsonArray();
                    if (node.has("data")) {
                        JsonObject data = node.getAsJsonObject("data");
                        if (data.has("conditions")) {
                            JsonArray conditions = data.getAsJsonArray("conditions");
                            for (JsonElement condElement : conditions) {
                                if (condElement.isJsonObject()) {
                                    JsonObject condition = condElement.getAsJsonObject();
                                    if (condition.has("key")) {
                                        keys.add(condition.get("key"));
                                    }
                                }
                            }
                        }
                    }

                    conditionNodeKeys.put(nodeId, keys);
                    log.debug("Found condition node {} with {} branches", nodeId, keys.size());
                }
            }
        }

        // 验证从条件节点出发的边是否包含 sourcePortID
        for (JsonElement edgeElement : edges) {
            if (edgeElement.isJsonObject()) {
                JsonObject edge = edgeElement.getAsJsonObject();
                if (edge.has("sourceNodeID")) {
                    String sourceNodeId = edge.get("sourceNodeID").getAsString();

                    // 如果源节点是条件节点
                    if (conditionNodeKeys.containsKey(sourceNodeId)) {
                        if (!edge.has("sourcePortID")) {
                            log.warn("⚠️ Edge from condition node '{}' is missing 'sourcePortID' field. " +
                                            "This edge will not work correctly! Edge: {}",
                                    sourceNodeId, edge);
                        } else {
                            String sourcePortId = edge.get("sourcePortID").getAsString();
                            JsonArray validKeys = conditionNodeKeys.get(sourceNodeId);

                            // 检查 sourcePortID 是否是有效的条件 key
                            boolean isValid = false;
                            for (JsonElement keyElement : validKeys) {
                                if (keyElement.getAsString().equals(sourcePortId)) {
                                    isValid = true;
                                    break;
                                }
                            }

                            if (!isValid) {
                                log.warn("⚠️ Edge from condition node '{}' has invalid sourcePortID '{}'. " +
                                                "Valid keys are: {}",
                                        sourceNodeId, sourcePortId, validKeys);
                            } else {
                                log.debug("✓ Edge from condition node '{}' has valid sourcePortID '{}'",
                                        sourceNodeId, sourcePortId);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 验证 end 节点是否正确处理多输入
     */
    private static void validateEndNodeInputs(JsonArray nodes, JsonArray edges) {
        // 找出所有 end 节点
        Map<String, JsonObject> endNodes = new HashMap<>();
        for (JsonElement nodeElement : nodes) {
            if (nodeElement.isJsonObject()) {
                JsonObject node = nodeElement.getAsJsonObject();
                if (node.has("type") && "end".equals(node.get("type").getAsString())) {
                    String nodeId = node.get("id").getAsString();
                    endNodes.put(nodeId, node);
                }
            }
        }

        // 对每个 end 节点，检查有多少个节点指向它
        for (Map.Entry<String, JsonObject> entry : endNodes.entrySet()) {
            String endNodeId = entry.getKey();
            JsonObject endNode = entry.getValue();

            // 统计指向该 end 节点的边
            List<String> sourceNodes = new ArrayList<>();
            for (JsonElement edgeElement : edges) {
                if (edgeElement.isJsonObject()) {
                    JsonObject edge = edgeElement.getAsJsonObject();
                    if (edge.has("targetNodeID") &&
                            endNodeId.equals(edge.get("targetNodeID").getAsString())) {
                        if (edge.has("sourceNodeID")) {
                            sourceNodes.add(edge.get("sourceNodeID").getAsString());
                        }
                    }
                }
            }

            int sourceCount = sourceNodes.size();
            log.debug("End node '{}' has {} incoming edges from: {}",
                    endNodeId, sourceCount, sourceNodes);

            // 检查 end 节点的 inputs 定义
            if (endNode.has("data")) {
                JsonObject data = endNode.getAsJsonObject("data");

                int inputFieldCount = 0;
                if (data.has("inputs")) {
                    JsonObject inputs = data.getAsJsonObject("inputs");
                    if (inputs.has("properties")) {
                        JsonObject properties = inputs.getAsJsonObject("properties");
                        inputFieldCount = properties.size();
                    }
                }

                // 如果有多个节点指向 end，检查 inputs 数量
                if (sourceCount > 1) {
                    if (inputFieldCount < sourceCount) {
                        log.warn("⚠️ End node '{}' has {} incoming edges but only {} input fields defined. " +
                                        "Should define {} input fields to receive all outputs. " +
                                        "Incoming nodes: {}",
                                endNodeId, sourceCount, inputFieldCount, sourceCount, sourceNodes);
                    } else if (inputFieldCount == sourceCount) {
                        log.info("✓ End node '{}' correctly has {} input fields for {} incoming edges",
                                endNodeId, inputFieldCount, sourceCount);
                    } else {
                        log.debug("End node '{}' has {} input fields for {} incoming edges (may include optional fields)",
                                endNodeId, inputFieldCount, sourceCount);
                    }
                } else if (sourceCount == 1) {
                    log.debug("End node '{}' has single input (typical case)", endNodeId);
                } else if (sourceCount == 0) {
                    log.warn("⚠️ End node '{}' has no incoming edges! This is likely an error.", endNodeId);
                }
            } else {
                log.warn("⚠️ End node '{}' is missing 'data' field", endNodeId);
            }
        }

        if (endNodes.isEmpty()) {
            log.warn("⚠️ Workflow has no end nodes!");
        }
    }
}

