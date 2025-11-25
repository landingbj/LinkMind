package ai.servlet.api;

import ai.common.pojo.IndexSearchData;
import ai.dto.*;
import ai.llm.pojo.GetRagContext;
import ai.llm.service.CompletionsService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.servlet.BaseServlet;
import ai.service.ChatSessionService;
import ai.service.ProcessingLevelDetector;
import ai.service.WritingTemplateManager;
import ai.utils.LagiGlobal;
import ai.utils.qa.ChatCompletionUtil;
import ai.vector.VectorDbService;
import ai.utils.MigrateGlobal;
import com.google.common.collect.Lists;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WritingApiServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(WritingApiServlet.class);
    private final CompletionsService completionsService = new CompletionsService();
    private final VectorDbService vectorDbService = new VectorDbService(MigrateGlobal.config);
    private final ChatSessionService chatSessionService = new ChatSessionService();

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
        } else if (method.equals("chat-writing")) {
            this.chatWriting(req, resp);
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
    
    /**
     * 聊天式撰写接口
     * 支持申报材料和专利材料的聊天式撰写，包含3个处理层次：RAG、生成段落、生成文章
     */
    private void chatWriting(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        
        String jsonString = IOUtils.toString(req.getInputStream(), StandardCharsets.UTF_8);
        ChatWritingRequest request = gson.fromJson(jsonString, ChatWritingRequest.class);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 验证必填参数
            if (request.getMessages() == null || request.getMessages().isEmpty()) {
                result.put("status", "failed");
                result.put("message", "messages参数必填，至少需要一条用户消息");
                responsePrint(resp, toJson(result));
                return;
            }
            
            // 验证最后一条消息必须是用户消息
            ChatMessage lastMessage = request.getMessages().get(request.getMessages().size() - 1);
            if (lastMessage == null || lastMessage.getContent() == null || lastMessage.getContent().trim().isEmpty()) {
                result.put("status", "failed");
                result.put("message", "最后一条消息内容不能为空");
                responsePrint(resp, toJson(result));
                return;
            }
            
            // 获取当前用户消息（最后一条）
            String currentUserMessage = lastMessage.getContent();
            
            // 自动判断撰写类型（如果未提供）
            String writingType = request.getWritingType();
            if (writingType == null || writingType.trim().isEmpty()) {
                writingType = ProcessingLevelDetector.detectWritingType(currentUserMessage);
                logger.info("自动判断撰写类型: {} - {}", writingType, currentUserMessage);
            }
            
            // 获取或创建会话ID
            String sessionId = chatSessionService.getOrCreateSessionId(request.getSessionId());
            
            // 设置会话级知识库分类
            if (request.getSessionCategory() != null && !request.getSessionCategory().trim().isEmpty()) {
                chatSessionService.setSessionCategory(sessionId, request.getSessionCategory());
            }
            
            // 自动判断处理层次
            String processingLevel = ProcessingLevelDetector.detectProcessingLevel(
                currentUserMessage, writingType);
            
            logger.info("自动判断处理层次: {} - {}", processingLevel, currentUserMessage);
            
            // 获取会话历史（用于上下文）
            List<ChatMessage> history = chatSessionService.getSessionHistory(sessionId);
            
            // 构建ChatCompletionRequest
            ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest();
            chatCompletionRequest.setSessionId(sessionId);
            chatCompletionRequest.setTemperature(0.7);
            chatCompletionRequest.setMax_tokens(4096);
            chatCompletionRequest.setStream(false);
            
            // 根据处理层次构建不同的提示词
            String systemPrompt = "";
            GetRagContext context = null;
            List<IndexSearchData> indexSearchDataList = null;
            
            if (WritingTemplateManager.LEVEL_RAG.equals(processingLevel)) {
                // RAG层次：基本提问
                systemPrompt = WritingTemplateManager.getRagPrompt(writingType);
                chatCompletionRequest.setCategory(chatSessionService.getRagCategory(sessionId, request.getGlobalCategory()));
                
                // 检索知识库
                String query = currentUserMessage;
                indexSearchDataList = chatSessionService.searchWithDualKnowledgeBase(
                    sessionId, request.getGlobalCategory(), query);
                
                if (indexSearchDataList != null && !indexSearchDataList.isEmpty()) {
                    context = completionsService.getRagContext(indexSearchDataList);
                }
                
            } else if (WritingTemplateManager.LEVEL_PARAGRAPH.equals(processingLevel)) {
                // 段落生成层次：生成表格中要求的内容
                chatCompletionRequest.setCategory(chatSessionService.getRagCategory(sessionId, request.getGlobalCategory()));
                
                // 检索知识库获取参考内容
                String query = currentUserMessage;
                indexSearchDataList = chatSessionService.searchWithDualKnowledgeBase(
                    sessionId, request.getGlobalCategory(), query);
                
                String contextStr = "";
                if (indexSearchDataList != null && !indexSearchDataList.isEmpty()) {
                    context = completionsService.getRagContext(indexSearchDataList);
                    contextStr = context != null ? context.getContext() : "";
                }
                
                systemPrompt = WritingTemplateManager.getParagraphPrompt(
                    writingType, currentUserMessage, contextStr);
                
            } else if (WritingTemplateManager.LEVEL_DOCUMENT.equals(processingLevel)) {
                // 文档生成层次：生成完整文档
                chatCompletionRequest.setCategory(chatSessionService.getRagCategory(sessionId, request.getGlobalCategory()));
                
                // 检索知识库获取申报要求或专利模板
                String query = "申报要求 申报通知 申报指南";
                if (ProcessingLevelDetector.WRITING_TYPE_PATENT.equals(writingType)) {
                    query = "专利模板 专利标准 专利要求";
                }
                indexSearchDataList = chatSessionService.searchWithDualKnowledgeBase(
                    sessionId, request.getGlobalCategory(), query);
                
                String contextStr = "";
                if (indexSearchDataList != null && !indexSearchDataList.isEmpty()) {
                    context = completionsService.getRagContext(indexSearchDataList);
                    contextStr = context != null ? context.getContext() : "";
                }
                
                String templateType = request.getTemplateType();
                if (templateType == null || templateType.trim().isEmpty()) {
                    if (ProcessingLevelDetector.WRITING_TYPE_PATENT.equals(writingType)) {
                        // 自动判断专利类型（如果未提供）
                        String patentType = request.getPatentType();
                        if (patentType == null || patentType.trim().isEmpty()) {
                            patentType = ProcessingLevelDetector.detectPatentType(currentUserMessage);
                            logger.info("自动判断专利类型: {} - {}", patentType, currentUserMessage);
                        }
                        templateType = patentType;
                    } else {
                        templateType = "国家科技计划项目申报书";
                    }
                }
                
                systemPrompt = WritingTemplateManager.getDocumentPrompt(
                    writingType, templateType, contextStr);
            }
            
            // 构建消息列表
            List<ChatMessage> messages = new ArrayList<>();
            
            // 添加系统提示词
            if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
                ChatMessage systemMsg = new ChatMessage();
                systemMsg.setRole("system");
                systemMsg.setContent(systemPrompt);
                messages.add(systemMsg);
            }
            
            // 合并历史消息和请求中的消息
            // 优先使用请求中的消息（如果提供了），否则使用会话历史
            List<ChatMessage> conversationMessages = new ArrayList<>();
            
            if (request.getMessages() != null && request.getMessages().size() > 1) {
                // 如果请求中提供了多条消息，使用请求中的消息（除了最后一条，因为最后一条是当前消息）
                for (int i = 0; i < request.getMessages().size() - 1; i++) {
                    ChatMessage msg = request.getMessages().get(i);
                    ChatMessage chatMsg = new ChatMessage();
                    chatMsg.setRole(msg.getRole() != null ? msg.getRole() : "user");
                    chatMsg.setContent(msg.getContent());
                    conversationMessages.add(chatMsg);
                }
            } else if (history != null && !history.isEmpty()) {
                // 否则使用会话历史（保留最近10条）
                int startIndex = Math.max(0, history.size() - 10);
                for (int i = startIndex; i < history.size(); i++) {
                    ai.openai.pojo.ChatMessage histMsg = history.get(i);
                    ChatMessage msg = new ChatMessage();
                    msg.setRole(histMsg.getRole());
                    msg.setContent(histMsg.getContent());
                    conversationMessages.add(msg);
                }
            }
            
            // 添加历史消息到消息列表
            messages.addAll(conversationMessages);
            
            // 添加当前用户消息（最后一条）
            ChatMessage userMsg = new ChatMessage();
            userMsg.setRole("user");
            userMsg.setContent(currentUserMessage);
            messages.add(userMsg);
            
            chatCompletionRequest.setMessages(messages);
            
            // 如果有RAG上下文，添加到请求中
            if (context != null && context.getContext() != null && !context.getContext().trim().isEmpty()) {
                // 保存原始消息列表
                List<ChatMessage> originalMessages = new ArrayList<>(messages);
                // 添加RAG上下文
                completionsService.addVectorDBContext(chatCompletionRequest, context.getContext());
                // 获取增强后的最后一条消息
                ChatMessage enhancedLastMsg = chatCompletionRequest.getMessages().get(chatCompletionRequest.getMessages().size() - 1);
                // 重建消息列表：系统消息 + 历史消息 + 增强后的用户消息
                List<ChatMessage> enhancedMessages = new ArrayList<>();
                if (!originalMessages.isEmpty() && "system".equals(originalMessages.get(0).getRole())) {
                    enhancedMessages.add(originalMessages.get(0)); // 系统消息
                }
                // 添加历史消息（跳过系统消息和最后一条用户消息）
                for (int i = 1; i < originalMessages.size() - 1; i++) {
                    enhancedMessages.add(originalMessages.get(i));
                }
                enhancedMessages.add(enhancedLastMsg); // 增强后的用户消息
                chatCompletionRequest.setMessages(enhancedMessages);
            }
            
            // 调用大模型生成
            ChatCompletionResult chatCompletionResult = completionsService.completions(chatCompletionRequest);
            
            if (chatCompletionResult != null && chatCompletionResult.getChoices() != null 
                    && !chatCompletionResult.getChoices().isEmpty()) {
                String content = ChatCompletionUtil.getFirstAnswer(chatCompletionResult);
                
                // 保存到会话历史
                chatSessionService.addMessage(sessionId, "user", currentUserMessage);
                chatSessionService.addMessage(sessionId, "assistant", content);
                
                // 构建响应
                List<String> filenames = context != null && context.getFilenames() != null 
                    ? context.getFilenames() : Lists.newArrayList();
                List<String> filepaths = context != null && context.getFilePaths() != null 
                    ? context.getFilePaths() : Lists.newArrayList();
                List<String> chunkIds = context != null && context.getChunkIds() != null 
                    ? context.getChunkIds() : Lists.newArrayList();
                
                ChatWritingResponse response = ChatWritingResponse.builder()
                        .sessionId(sessionId)
                        .content(content)
                        .processingLevel(processingLevel)
                        .filename(filenames)
                        .filepath(filepaths)
                        .context(context != null ? context.getContext() : null)
                        .contextChunkIds(chunkIds)
                        .generatedAt(Instant.now().toString())
                        .build();
                
                // 如果是生成完整文档，添加文档ID和标题
                if (WritingTemplateManager.LEVEL_DOCUMENT.equals(processingLevel)) {
                    String documentId = "doc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                    response.setDocumentId(documentId);
                    
                    if (ProcessingLevelDetector.WRITING_TYPE_PATENT.equals(writingType)) {
                        response.setTitle(extractTitle(content));
                    } else {
                        response.setTitle("科技项目申报书");
                    }
                }
                
                result.put("status", "success");
                result.put("data", response);
            } else {
                result.put("status", "failed");
                result.put("message", "生成失败，请稍后重试");
            }
        } catch (Exception e) {
            logger.error("聊天式撰写失败", e);
            result.put("status", "failed");
            result.put("message", "服务器内部错误: " + e.getMessage());
        }
        
        responsePrint(resp, toJson(result));
    }
}

