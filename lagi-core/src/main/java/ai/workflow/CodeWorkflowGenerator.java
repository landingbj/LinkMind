package ai.workflow;

import ai.common.pojo.IndexSearchData;
import ai.llm.service.CompletionsService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.utils.JsonExtractor;
import ai.utils.ResourceUtil;
import ai.vector.VectorStoreService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Code workflow generator for analyzing code structure and generating flow diagrams
 * Based on WorkflowGenerator but specialized for code analysis
 */
@Slf4j
public class CodeWorkflowGenerator {
    private static final CompletionsService completionsService = new CompletionsService();
    private static final VectorStoreService vectorStoreService = new VectorStoreService();

    private static final double DEFAULT_TEMPERATURE = 0;
    private static final int DEFAULT_MAX_TOKENS = 8192;

    // Knowledge base search parameters
    private static final int SIMILARITY_TOP_K = 5;
    private static final double SIMILARITY_CUTOFF = 0.1;

    /**
     * Generate workflow schema from uploaded files
     *
     * @param uploadedFiles list of uploaded files
     * @param filesInfo     list of file information maps
     * @param knowledgeBase knowledge base category for vector search
     * @param description   additional description for understanding the code context
     * @return Workflow JSON string representing code flow
     */
    public static CodeFlowResult code2FlowSchema(List<File> uploadedFiles, List<Map<String, String>> filesInfo,
                                                 String knowledgeBase, String description) {
//        if (uploadedFiles != null) {
//            return ResourceUtil.loadAsString("/temp/debug/code_flow_debug_01.json");
//        }

        log.info("Generating workflow from uploaded files, knowledgeBase: {}, description: {}",
                knowledgeBase, description);

        // Build combined code content from files
        String codeContent = buildCodeContentFromFiles(uploadedFiles, filesInfo);

        if (codeContent.trim().isEmpty()) {
            log.warn("No code content to analyze");
            CodeFlowResult result = new CodeFlowResult();
            result.setData("{}");
            return result;
        }

        // Pre-check: Assess whether the provided code has sufficient information
        String missingInfoMessage = assessCodeCompleteness(codeContent, description);
        if (missingInfoMessage != null && !missingInfoMessage.trim().isEmpty()) {
            log.warn("Code content lacks necessary information: {}", missingInfoMessage);
            CodeFlowResult result = new CodeFlowResult();
            result.setMissingInfoMessage(missingInfoMessage.trim());
            return result;
        }

        // Extract business logic from code using LLM
        String businessLogic = extractBusinessLogicFromCode(codeContent, knowledgeBase, description);

        CodeFlowResult result = new CodeFlowResult();
        result.setData(businessLogic);
        return result;
    }

    /**
     * Assess if the provided code content includes the necessary information to extract business logic.
     * Returns null/empty if sufficient; otherwise returns a human-readable message describing what is missing.
     */
    private static String assessCodeCompleteness(String codeContent, String description) {
        log.info("Assessing code completeness before business logic extraction");

        String template = ResourceUtil.loadAsString("/prompts/dev_code_check.md");
        String descText = (description != null && !description.trim().isEmpty()) ? description : "无额外描述";
        String prompt = template.replace("${{DESCRIPTION}}", descText)
                .replace("${{CODE_CONTENT}}", codeContent);

        try {
            ChatCompletionRequest request = completionsService.getCompletionsRequest(
                    null,
                    prompt,
                    DEFAULT_TEMPERATURE,
                    DEFAULT_MAX_TOKENS
            );

            ChatCompletionResult result = completionsService.completions(request);
            String response = result.getChoices().get(0).getMessage().getContent();

            if (response == null || response.trim().isEmpty()) {
                log.warn("LLM returned empty response for completeness assessment; assuming sufficient");
                return null;
            }

            String trimmed = response.trim();
            if (trimmed.toUpperCase().contains("NO_ISSUES")) {
                return null;
            }

            if (trimmed.toUpperCase().startsWith("MISSING:")) {
                return trimmed.substring("MISSING:".length()).trim();
            }

            // Fallback: if not clearly sufficient, treat response as missing rationale
            return trimmed;
        } catch (Exception e) {
            log.error("Error assessing code completeness", e);
            // On failure, do not block the flow; proceed as sufficient
            return null;
        }
    }

    /**
     * Extract business logic from code content using LLM
     *
     * @param codeContent   the code content to analyze
     * @param knowledgeBase knowledge base category for vector search
     * @param description   additional description for understanding the code context
     * @return JSON string representing the workflow diagram
     */
    private static String extractBusinessLogicFromCode(String codeContent, String knowledgeBase, String description) {
        log.info("Extracting business logic from code using LLM");

        // Step 1: Extract business logic text description from code
        String businessLogicDescription = codeToBusinessLogicDescription(codeContent, description);

        if (businessLogicDescription == null || businessLogicDescription.trim().isEmpty()) {
            log.warn("Failed to extract business logic description from code");
            return "{}";
        }

        log.info("Successfully extracted business logic description");

        String summaryTexts = null;
        if (knowledgeBase != null && !knowledgeBase.isEmpty()) {
            summaryTexts = summarizeBusinessLogicToKeyTexts(businessLogicDescription);
        }
        // Step 2: Summarize business logic to key texts for knowledge base search
        if (summaryTexts != null && !summaryTexts.trim().isEmpty()) {
            log.info("Successfully summarized business logic to key texts: {}", summaryTexts);

            // Step 3: Search knowledge base with key texts
            String knowledgeBaseContext = searchKnowledgeBase(summaryTexts, knowledgeBase);

            if (knowledgeBaseContext != null && !knowledgeBaseContext.trim().isEmpty()) {
                log.info("Successfully retrieved knowledge base context");

                // Step 4: Enhance business logic description with knowledge base context
                String enhancedLogicDescription = enhanceBusinessLogicWithKnowledge(
                        businessLogicDescription, knowledgeBaseContext);

                if (enhancedLogicDescription != null && !enhancedLogicDescription.trim().isEmpty()) {
                    log.info("Successfully enhanced business logic description with knowledge base");
                    businessLogicDescription = enhancedLogicDescription;
                }
            } else {
                log.info("No relevant knowledge base context found, using original business logic");
            }
        }

        // Step 5: Convert business logic description to flow diagram
        String flowDiagram = businessLogicToFlowDiagram(businessLogicDescription, description);

        if (flowDiagram == null || flowDiagram.trim().isEmpty()) {
            log.warn("Failed to convert business logic to flow diagram");
            return "{}";
        }

        flowDiagram = JsonExtractor.extractJson(flowDiagram);

        log.info("Successfully generated flow diagram from business logic");
        return flowDiagram;
    }

    /**
     * Step 1: Extract business logic text description from code
     *
     * @param codeContent the code content to analyze
     * @param description additional description for understanding the code context
     * @return text description of the business logic
     */
    private static String codeToBusinessLogicDescription(String codeContent, String description) {
        log.info("Step 1: Extracting business logic text description from code");

//        if (codeContent != null) {
//            return ResourceUtil.loadAsString("/temp/debug/code_logic_debug_01.md");
//        }

        String promptTemplate = buildCodeToLogicPrompt(codeContent, description);

        try {
            ChatCompletionRequest request = completionsService.getCompletionsRequest(
                    null,
                    promptTemplate,
                    DEFAULT_TEMPERATURE,
                    DEFAULT_MAX_TOKENS
            );

            ChatCompletionResult result = completionsService.completions(request);
            String response = result.getChoices().get(0).getMessage().getContent();

            if (response != null && !response.trim().isEmpty()) {
                return response;
            } else {
                log.warn("LLM returned empty response for business logic extraction");
                return null;
            }
        } catch (Exception e) {
            log.error("Error extracting business logic description from code", e);
            return null;
        }
    }

    /**
     * Step 2: Summarize business logic to key texts for knowledge base search
     *
     * @param businessLogicDescription the business logic text description
     * @return key texts for knowledge base search, one per line
     */
    private static String summarizeBusinessLogicToKeyTexts(String businessLogicDescription) {
        log.info("Step 2: Summarizing business logic to key texts");

        String promptTemplate = buildLogicToSummaryPrompt(businessLogicDescription);

        try {
            ChatCompletionRequest request = completionsService.getCompletionsRequest(
                    null,
                    promptTemplate,
                    DEFAULT_TEMPERATURE,
                    DEFAULT_MAX_TOKENS
            );

            ChatCompletionResult result = completionsService.completions(request);
            String response = result.getChoices().get(0).getMessage().getContent();

            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            } else {
                log.warn("LLM returned empty response for summary texts");
                return null;
            }
        } catch (Exception e) {
            log.error("Error summarizing business logic to key texts", e);
            return null;
        }
    }

    /**
     * Step 3: Search knowledge base with key texts
     *
     * @param summaryTexts  key texts from business logic summary, one per line
     * @param knowledgeBase knowledge base category for vector search
     * @return concatenated knowledge base search results
     */
    private static String searchKnowledgeBase(String summaryTexts, String knowledgeBase) {
        if (knowledgeBase == null || knowledgeBase.isEmpty()) {
            return null;
        }
        log.info("Step 3: Searching knowledge base with key texts, category: {}", knowledgeBase);

        try {
            // Use provided knowledgeBase or fall back to default
            String category = knowledgeBase;

            String[] queries = summaryTexts.split("\n");
            StringBuilder knowledgeContext = new StringBuilder();

            Set<String> addedTexts = new HashSet<>();

            for (String query : queries) {
                query = query.trim();
                if (query.isEmpty() || query.startsWith("```") || query.startsWith("#")) {
                    continue;
                }

                log.info("Searching knowledge base with query: {}", query);

                List<IndexSearchData> searchResults = vectorStoreService.search(
                        query,
                        SIMILARITY_TOP_K,
                        SIMILARITY_CUTOFF,
                        null,
                        category
                );

                if (searchResults != null && !searchResults.isEmpty()) {
                    knowledgeContext.append("\n### Query: ").append(query).append("\n");
                    for (IndexSearchData result : searchResults) {
                        String text = result.getText();
                        if (text != null && !addedTexts.contains(text)) {
                            knowledgeContext.append("- ").append(text).append("\n");
                            addedTexts.add(text);
                        }
                    }
                }
            }

            String context = knowledgeContext.toString().trim();
            if (!context.isEmpty()) {
                log.info("Successfully retrieved {} characters of knowledge base context", context.length());
                return context;
            } else {
                log.info("No relevant knowledge base results found");
                return null;
            }
        } catch (Exception e) {
            log.error("Error searching knowledge base", e);
            return null;
        }
    }

    /**
     * Step 4: Enhance business logic description with knowledge base context
     *
     * @param businessLogicDescription original business logic description
     * @param knowledgeBaseContext     context from knowledge base search
     * @return enhanced business logic description
     */
    private static String enhanceBusinessLogicWithKnowledge(String businessLogicDescription,
                                                            String knowledgeBaseContext) {
        log.info("Step 4: Enhancing business logic with knowledge base context");

        String promptTemplate = buildLogicEnhancePrompt(businessLogicDescription, knowledgeBaseContext);

        try {
            ChatCompletionRequest request = completionsService.getCompletionsRequest(
                    null,
                    promptTemplate,
                    DEFAULT_TEMPERATURE,
                    DEFAULT_MAX_TOKENS
            );

            ChatCompletionResult result = completionsService.completions(request);
            String response = result.getChoices().get(0).getMessage().getContent();

            if (response != null && !response.trim().isEmpty()) {
                return response;
            } else {
                log.warn("LLM returned empty response for enhanced logic");
                return null;
            }
        } catch (Exception e) {
            log.error("Error enhancing business logic with knowledge", e);
            return null;
        }
    }

    /**
     * Step 5: Convert business logic description to flow diagram
     *
     * @param businessLogicDescription the business logic text description
     * @param description              additional description for understanding the code context
     * @return JSON string representing the flow diagram
     */
    private static String businessLogicToFlowDiagram(String businessLogicDescription, String description) {
        log.info("Step 5: Converting business logic description to flow diagram");

//        if (businessLogicDescription != null) {
//            return ResourceUtil.loadAsString("/temp/code_flow_01.json");
//        }


        String promptTemplate = buildLogicToFlowPrompt(businessLogicDescription, description);

        try {
            ChatCompletionRequest request = completionsService.getCompletionsRequest(
                    null,
                    promptTemplate,
                    DEFAULT_TEMPERATURE,
                    DEFAULT_MAX_TOKENS
            );

            ChatCompletionResult result = completionsService.completions(request);
            String response = result.getChoices().get(0).getMessage().getContent();

            if (response != null && !response.trim().isEmpty()) {
                return response;
            } else {
                log.warn("LLM returned empty response for flow diagram generation");
                return null;
            }
        } catch (Exception e) {
            log.error("Error converting business logic to flow diagram", e);
            return null;
        }
    }

    /**
     * Build prompt for Step 1: Code to business logic description
     *
     * @param codeContent the code content to analyze
     * @param description additional description for understanding the code context
     * @return formatted prompt for LLM
     */
    private static String buildCodeToLogicPrompt(String codeContent, String description) {
        String template = ResourceUtil.loadAsString("/prompts/dev_code_to_logic.md");
        String descriptionText = (description != null && !description.trim().isEmpty())
                ? description : "无额外描述";
        return template.replace("${{CODE_CONTENT}}", codeContent)
                .replace("${{DESCRIPTION}}", descriptionText);
    }

    /**
     * Build prompt for Step 2: Business logic to summary key texts
     *
     * @param businessLogicDescription the business logic text description
     * @return formatted prompt for LLM
     */
    private static String buildLogicToSummaryPrompt(String businessLogicDescription) {
        String template = ResourceUtil.loadAsString("/prompts/dev_logic_to_summary.md");
        return template.replace("${{BUSINESS_LOGIC_DESCRIPTION}}", businessLogicDescription);
    }

    /**
     * Build prompt for Step 4: Enhance logic with knowledge base
     *
     * @param businessLogicDescription original business logic description
     * @param knowledgeBaseContext     context from knowledge base
     * @return formatted prompt for LLM
     */
    private static String buildLogicEnhancePrompt(String businessLogicDescription, String knowledgeBaseContext) {
        String template = ResourceUtil.loadAsString("/prompts/dev_logic_enhance.md");
        return template.replace("${{BUSINESS_LOGIC_DESCRIPTION}}", businessLogicDescription)
                .replace("${{KNOWLEDGE_BASE_CONTEXT}}", knowledgeBaseContext);
    }

    /**
     * Build prompt for Step 5: Business logic description to flow diagram
     *
     * @param businessLogicDescription the business logic text description
     * @param description              additional description for understanding the code context
     * @return formatted prompt for LLM
     */
    private static String buildLogicToFlowPrompt(String businessLogicDescription, String description) {
        String template = ResourceUtil.loadAsString("/prompts/dev_logic_to_flow.md");
        String descriptionText = (description != null && !description.trim().isEmpty())
                ? description : "无额外描述";
        return template.replace("${{BUSINESS_LOGIC_DESCRIPTION}}", businessLogicDescription)
                .replace("${{DESCRIPTION}}", descriptionText);
    }

    /**
     * Build combined code content from uploaded files
     *
     * @param uploadedFiles list of uploaded files
     * @param filesInfo     list of file information maps
     * @return combined code content string
     */
    public static String buildCodeContentFromFiles(List<File> uploadedFiles, List<Map<String, String>> filesInfo) {
        StringBuilder codeContent = new StringBuilder();

        for (File file : uploadedFiles) {
            try {
                String content = readFileContent(file);
                String originalFileName = getOriginalFileName(file, filesInfo);

                // Append to combined code content
                codeContent.append("// File: ").append(originalFileName).append("\n");
                codeContent.append(content).append("\n\n");

                log.info("Processed content from file: {}", originalFileName);
            } catch (Exception e) {
                log.error("Error reading content from file: {}", file.getName(), e);
            }
        }

        return codeContent.toString();
    }

    /**
     * Read file content with proper encoding handling
     *
     * @param file the file to read
     * @return file content as string
     * @throws IOException if error reading file
     */
    private static String readFileContent(File file) throws IOException {
        try {
            // Try UTF-8 first
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Fallback to system default encoding
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }
            return content.toString();
        }
    }

    /**
     * Get original file name from saved file and files info
     *
     * @param file      the saved file
     * @param filesInfo list of file information
     * @return original file name
     */
    private static String getOriginalFileName(File file, List<Map<String, String>> filesInfo) {
        for (Map<String, String> fileInfo : filesInfo) {
            // Match by file size since we don't store the generated filename
            String size = String.valueOf(file.length());
            if (size.equals(fileInfo.get("size"))) {
                return fileInfo.get("fileName");
            }
        }
        return file.getName(); // Fallback to generated filename
    }

    @Data
    public static class CodeFlowResult {
        private String data;
        private String missingInfoMessage;
    }
}
