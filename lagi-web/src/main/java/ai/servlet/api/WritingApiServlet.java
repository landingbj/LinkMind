package ai.servlet.api;

import ai.common.pojo.IndexSearchData;
import ai.dto.PatentDocumentRequest;
import ai.dto.PatentDocumentResponse;
import ai.dto.ProjectMaterialRequest;
import ai.dto.ProjectMaterialResponse;
import ai.llm.pojo.GetRagContext;
import ai.llm.service.CompletionsService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.servlet.BaseServlet;
import ai.service.WritingTemplateManager;
import ai.utils.LagiGlobal;
import ai.utils.qa.ChatCompletionUtil;
import ai.vector.VectorDbService;
import ai.utils.MigrateGlobal;
import com.google.common.collect.Lists;
import org.apache.commons.io.IOUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WritingApiServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;
    private final CompletionsService completionsService = new CompletionsService();
    private final VectorDbService vectorDbService = new VectorDbService(MigrateGlobal.config);

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setHeader("Content-Type", "application/json;charset=utf-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);
        
        if (method.equals("project-material")) {
            this.projectMaterial(req, resp);
        } else if (method.equals("patent-document")) {
            this.patentDocument(req, resp);
        } else {
            Map<String, Object> result = new HashMap<>();
            result.put("status", "failed");
            result.put("message", "Invalid endpoint");
            responsePrint(resp, toJson(result));
        }
    }

    private void projectMaterial(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        
        String jsonString = IOUtils.toString(req.getInputStream(), StandardCharsets.UTF_8);
        ProjectMaterialRequest request = gson.fromJson(jsonString, ProjectMaterialRequest.class);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            String templateType = request.getTemplateType();
            if (templateType == null || templateType.trim().isEmpty()) {
                templateType = "国家科技计划项目申报书";
            }
            
            String template = WritingTemplateManager.getProjectTemplate(templateType);
            String prompt = template
                    .replace("{projectType}", request.getProjectType() != null ? request.getProjectType() : "")
                    .replace("{enterpriseInfo}", request.getEnterpriseInfo() != null ? request.getEnterpriseInfo() : "")
                    .replace("{projectInfo}", request.getProjectInfo() != null ? request.getProjectInfo() : "");
            
            ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest();
            chatCompletionRequest.setTemperature(0.7);
            chatCompletionRequest.setMax_tokens(4096);
            chatCompletionRequest.setStream(false);
            chatCompletionRequest.setCategory(LagiGlobal.getDefaultCategory());
            
            ChatMessage message = new ChatMessage();
            message.setRole("user");
            message.setContent(prompt);
            chatCompletionRequest.setMessages(Lists.newArrayList(message));
            
            GetRagContext context = null;
            List<IndexSearchData> indexSearchDataList = vectorDbService.searchByContext(chatCompletionRequest);
            if (indexSearchDataList != null && !indexSearchDataList.isEmpty()) {
                context = completionsService.getRagContext(indexSearchDataList);
                if (context != null) {
                    String contextStr = context.getContext();
                    completionsService.addVectorDBContext(chatCompletionRequest, contextStr);
                    ChatMessage chatMessage = chatCompletionRequest.getMessages().get(chatCompletionRequest.getMessages().size() - 1);
                    chatCompletionRequest.setMessages(Lists.newArrayList(chatMessage));
                }
            }
            
            ChatCompletionResult chatCompletionResult = completionsService.completions(chatCompletionRequest);
            
            if (chatCompletionResult != null && chatCompletionResult.getChoices() != null && !chatCompletionResult.getChoices().isEmpty()) {
                String content = ChatCompletionUtil.getFirstAnswer(chatCompletionResult);
                
                List<String> filenames = context != null && context.getFilenames() != null ? context.getFilenames() : Lists.newArrayList();
                List<String> filepaths = context != null && context.getFilePaths() != null ? context.getFilePaths() : Lists.newArrayList();
                List<String> chunkIds = context != null && context.getChunkIds() != null ? context.getChunkIds() : Lists.newArrayList();
                ProjectMaterialResponse response = ProjectMaterialResponse.builder()
                        .documentId("doc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                        .title("科技项目申报书")
                        .content(content)
                        .generatedAt(Instant.now().toString())
                        .filename(filenames)
                        .filepath(filepaths)
                        .context(context != null ? context.getContext() : null)
                        .contextChunkIds(chunkIds)
                        .build();
                
                result.put("status", "success");
                result.put("data", response);
            } else {
                result.put("status", "failed");
                result.put("message", "生成失败，请稍后重试");
            }
        } catch (Exception e) {
            result.put("status", "failed");
            result.put("message", "服务器内部错误: " + e.getMessage());
        }
        
        responsePrint(resp, toJson(result));
    }

    private void patentDocument(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        
        String jsonString = IOUtils.toString(req.getInputStream(), StandardCharsets.UTF_8);
        PatentDocumentRequest request = gson.fromJson(jsonString, PatentDocumentRequest.class);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            String patentType = request.getPatentType();
            if (patentType == null || patentType.trim().isEmpty()) {
                patentType = "发明专利";
            }
            
            String template = WritingTemplateManager.getPatentTemplate(patentType);
            String prompt = template
                    .replace("{patentType}", patentType)
                    .replace("{technicalPoints}", request.getTechnicalPoints() != null ? request.getTechnicalPoints() : "")
                    .replace("{inventorInfo}", request.getInventorInfo() != null ? request.getInventorInfo() : "");
            
            ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest();
            chatCompletionRequest.setTemperature(0.7);
            chatCompletionRequest.setMax_tokens(4096);
            chatCompletionRequest.setStream(false);
            chatCompletionRequest.setCategory(LagiGlobal.getDefaultCategory());
            
            ChatMessage message = new ChatMessage();
            message.setRole("user");
            message.setContent(prompt);
            chatCompletionRequest.setMessages(Lists.newArrayList(message));
            
            GetRagContext context = null;
            List<IndexSearchData> indexSearchDataList = vectorDbService.searchByContext(chatCompletionRequest);
            if (indexSearchDataList != null && !indexSearchDataList.isEmpty()) {
                context = completionsService.getRagContext(indexSearchDataList);
                if (context != null) {
                    String contextStr = context.getContext();
                    completionsService.addVectorDBContext(chatCompletionRequest, contextStr);
                    ChatMessage chatMessage = chatCompletionRequest.getMessages().get(chatCompletionRequest.getMessages().size() - 1);
                    chatCompletionRequest.setMessages(Lists.newArrayList(chatMessage));
                }
            }
            
            ChatCompletionResult chatCompletionResult = completionsService.completions(chatCompletionRequest);
            
            if (chatCompletionResult != null && chatCompletionResult.getChoices() != null && !chatCompletionResult.getChoices().isEmpty()) {
                String content = ChatCompletionUtil.getFirstAnswer(chatCompletionResult);
                
                String patentAbstract = extractAbstract(content);
                String title = extractTitle(content);
                
                List<String> filenames = context != null && context.getFilenames() != null ? context.getFilenames() : Lists.newArrayList();
                List<String> filepaths = context != null && context.getFilePaths() != null ? context.getFilePaths() : Lists.newArrayList();
                List<String> chunkIds = context != null && context.getChunkIds() != null ? context.getChunkIds() : Lists.newArrayList();
                PatentDocumentResponse response = PatentDocumentResponse.builder()
                        .patentId("patent_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                        .title(title)
                        .abstractText(patentAbstract)
                        .content(content)
                        .generatedAt(Instant.now().toString())
                        .filename(filenames)
                        .filepath(filepaths)
                        .context(context != null ? context.getContext() : null)
                        .contextChunkIds(chunkIds)
                        .build();
                
                result.put("status", "success");
                result.put("data", response);
            } else {
                result.put("status", "failed");
                result.put("message", "生成失败，请稍后重试");
            }
        } catch (Exception e) {
            result.put("status", "failed");
            result.put("message", "服务器内部错误: " + e.getMessage());
        }
        
        responsePrint(resp, toJson(result));
    }
    
    private String extractAbstract(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        int abstractIndex = content.indexOf("摘要");
        int claimsIndex = content.indexOf("权利要求");
        if (abstractIndex >= 0 && claimsIndex > abstractIndex) {
            return content.substring(abstractIndex, claimsIndex).trim();
        }
        if (content.length() > 500) {
            return content.substring(0, 500) + "...";
        }
        return content;
    }
    
    private String extractTitle(String content) {
        if (content == null || content.isEmpty()) {
            return "专利文档";
        }
        int titleIndex = content.indexOf("专利名称");
        int fieldIndex = content.indexOf("技术领域");
        if (titleIndex >= 0 && fieldIndex > titleIndex) {
            String title = content.substring(titleIndex + 4, fieldIndex).trim();
            if (title.length() > 100) {
                title = title.substring(0, 100);
            }
            return title;
        }
        String[] lines = content.split("\n");
        if (lines.length > 0 && lines[0].length() > 0) {
            String firstLine = lines[0].trim();
            if (firstLine.length() > 100) {
                firstLine = firstLine.substring(0, 100);
            }
            return firstLine;
        }
        return "专利文档";
    }
}

