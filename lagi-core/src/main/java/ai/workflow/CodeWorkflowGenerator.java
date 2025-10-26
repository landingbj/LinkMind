package ai.workflow;

import ai.llm.service.CompletionsService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.utils.ResourceUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/**
 * Code workflow generator for analyzing code structure and generating flow diagrams
 * Based on WorkflowGenerator but specialized for code analysis
 */
@Slf4j
public class CodeWorkflowGenerator {
    private static final CompletionsService completionsService = new CompletionsService();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final double DEFAULT_TEMPERATURE = 0.3;
    private static final int DEFAULT_MAX_TOKENS = 4096;

    /**
     * Generate workflow schema from uploaded files
     *
     * @param uploadedFiles list of uploaded files
     * @param filesInfo     list of file information maps
     * @return Workflow JSON string representing code flow
     */
    public static String code2FlowSchema(List<File> uploadedFiles, List<Map<String, String>> filesInfo) {
        log.info("Generating workflow from uploaded files");
        
        // Build combined code content from files
        String codeContent = buildCodeContentFromFiles(uploadedFiles, filesInfo);
        
        if (codeContent.trim().isEmpty()) {
            log.warn("No code content to analyze");
            return "{}";
        }
        
        // Extract business logic from code using LLM
        String businessLogic = extractBusinessLogicFromCode(codeContent);
        
        return businessLogic;
    }
    
    /**
     * Extract business logic from code content using LLM (two-step process)
     *
     * @param codeContent the code content to analyze
     * @return JSON string representing the workflow diagram
     */
    private static String extractBusinessLogicFromCode(String codeContent) {
        log.info("Extracting business logic from code using LLM (two-step process)");
        
        // Step 1: Extract business logic text description from code
        String businessLogicDescription = codeToBusinessLogicDescription(codeContent);
        
        if (businessLogicDescription == null || businessLogicDescription.trim().isEmpty()) {
            log.warn("Failed to extract business logic description from code");
            return "{}";
        }
        
        log.info("Successfully extracted business logic description, businessLogicDescription: {}", businessLogicDescription);
        
        // Step 2: Convert business logic description to flow diagram
        String flowDiagram = businessLogicToFlowDiagram(businessLogicDescription);
        
        if (flowDiagram == null || flowDiagram.trim().isEmpty()) {
            log.warn("Failed to convert business logic to flow diagram");
            return "{}";
        }
        
        log.info("Successfully generated flow diagram from business logic, flowDiagram: {}", flowDiagram);
        return flowDiagram;
    }
    
    /**
     * Step 1: Extract business logic text description from code
     *
     * @param codeContent the code content to analyze
     * @return text description of the business logic
     */
    private static String codeToBusinessLogicDescription(String codeContent) {
        log.info("Step 1: Extracting business logic text description from code");
        
        String promptTemplate = buildCodeToLogicPrompt(codeContent);
        
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
     * Step 2: Convert business logic description to flow diagram
     *
     * @param businessLogicDescription the business logic text description
     * @return JSON string representing the flow diagram
     */
    private static String businessLogicToFlowDiagram(String businessLogicDescription) {
        log.info("Step 2: Converting business logic description to flow diagram");
        
        String promptTemplate = buildLogicToFlowPrompt(businessLogicDescription);
        
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
     * @return formatted prompt for LLM
     */
    private static String buildCodeToLogicPrompt(String codeContent) {
        String template = ResourceUtil.loadAsString("/prompts/dev_code_to_logic.md");
        return template.replace("${{CODE_CONTENT}}", codeContent);
    }
    
    /**
     * Build prompt for Step 2: Business logic description to flow diagram
     *
     * @param businessLogicDescription the business logic text description
     * @return formatted prompt for LLM
     */
    private static String buildLogicToFlowPrompt(String businessLogicDescription) {
        String template = ResourceUtil.loadAsString("/prompts/dev_logic_to_flow.md");
        return template.replace("${{BUSINESS_LOGIC_DESCRIPTION}}", businessLogicDescription);
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
}
