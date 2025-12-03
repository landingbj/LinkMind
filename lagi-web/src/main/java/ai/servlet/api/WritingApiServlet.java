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
            // 参数验证：至少需要提供项目信息或企业信息
            if ((request.getProjectInfo() == null || request.getProjectInfo().trim().isEmpty()) 
                    && (request.getEnterpriseInfo() == null || request.getEnterpriseInfo().trim().isEmpty())) {
                result.put("status", "failed");
                result.put("message", "projectInfo和enterpriseInfo至少需要提供一个");
                responsePrint(resp, toJson(result));
                return;
            }
            
            String templateType = request.getTemplateType();
            if (templateType == null || templateType.trim().isEmpty()) {
                templateType = "国家科技计划项目申报书";
            }
            
            String template = WritingTemplateManager.getProjectTemplate(templateType);
            String projectType = request.getProjectType() != null ? request.getProjectType().trim() : "";
            String enterpriseInfo = request.getEnterpriseInfo() != null ? request.getEnterpriseInfo().trim() : "";
            String projectInfo = request.getProjectInfo() != null ? request.getProjectInfo().trim() : "";
            
            String prompt = template
                    .replace("{projectType}", projectType)
                    .replace("{enterpriseInfo}", enterpriseInfo)
                    .replace("{projectInfo}", projectInfo);
            
            logger.info("项目申报材料生成 - 模板类型: {}, 项目类型: {}", templateType, projectType);
            logger.info("生成的提示词（前500字符）: {}", prompt.length() > 500 ? prompt.substring(0, 500) + "..." : prompt);
            logger.info("企业信息: {}", enterpriseInfo.length() > 100 ? enterpriseInfo.substring(0, 100) + "..." : enterpriseInfo);
            logger.info("项目信息: {}", projectInfo.length() > 100 ? projectInfo.substring(0, 100) + "..." : projectInfo);
            
            ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest();
            chatCompletionRequest.setTemperature(0.7);
            chatCompletionRequest.setMax_tokens(4096);
            chatCompletionRequest.setStream(false);
            chatCompletionRequest.setCategory(LagiGlobal.getDefaultCategory());
            
            ChatMessage message = new ChatMessage();
            message.setRole("user");
            message.setContent(prompt);
            chatCompletionRequest.setMessages(Lists.newArrayList(message));
            
            // RAG检索：使用项目信息作为查询，确保检索到的内容与用户输入相关
            // 注意：RAG检索是可选的，如果失败应该继续执行
            GetRagContext context = null;
            String queryText = projectInfo;
            if (queryText == null || queryText.trim().isEmpty()) {
                queryText = enterpriseInfo;
            }
            
            if (queryText != null && !queryText.trim().isEmpty()) {
                try {
                    // 创建一个临时请求用于RAG检索，使用项目信息作为查询
                    ChatCompletionRequest ragRequest = new ChatCompletionRequest();
                    ChatMessage ragMessage = new ChatMessage();
                    ragMessage.setRole("user");
                    ragMessage.setContent(queryText); // 使用项目信息进行检索
                    ragRequest.setMessages(Lists.newArrayList(ragMessage));
                    ragRequest.setCategory(LagiGlobal.getDefaultCategory());
                    // 设置必要的字段，避免NullPointerException
                    ragRequest.setMax_tokens(4096);
                    ragRequest.setTemperature(0.7);
                    ragRequest.setStream(false);
                    
                    List<IndexSearchData> indexSearchDataList = vectorDbService.searchByContext(ragRequest);
                    if (indexSearchDataList != null && !indexSearchDataList.isEmpty()) {
                        context = completionsService.getRagContext(indexSearchDataList);
                        if (context != null && context.getContext() != null && !context.getContext().trim().isEmpty()) {
                            // 将RAG上下文添加到提示词中，而不是替换用户输入
                            String contextStr = context.getContext();
                            // 在提示词末尾添加RAG上下文作为参考
                            prompt = prompt + "\n\n参考文档内容（申报要求、通知等，仅供参考，请以用户提供的项目信息为准）：\n" + contextStr;
                            message.setContent(prompt);
                            chatCompletionRequest.setMessages(Lists.newArrayList(message));
                            logger.info("已添加RAG上下文，上下文长度: {}", contextStr.length());
                        }
                    }
                } catch (Exception e) {
                    // RAG检索失败不影响主流程，记录日志后继续执行
                    logger.warn("RAG检索失败，将不使用RAG上下文: {}", e.getMessage());
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
            logger.error("项目申报材料生成失败", e);
            result.put("status", "failed");
            result.put("message", "服务器内部错误: " + e.getMessage());
        }
        
        responsePrint(resp, toJson(result));
    }

    private void patentDocument(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            String jsonString = IOUtils.toString(req.getInputStream(), StandardCharsets.UTF_8);
            logger.info("接收到的JSON请求: {}", jsonString);
            
            if (jsonString == null || jsonString.trim().isEmpty()) {
                logger.error("请求体为空");
                result.put("status", "failed");
                result.put("message", "请求体不能为空");
                responsePrint(resp, toJson(result));
                return;
            }
            
            PatentDocumentRequest request = null;
            try {
                request = gson.fromJson(jsonString, PatentDocumentRequest.class);
            } catch (Exception e) {
                logger.error("JSON解析失败", e);
                result.put("status", "failed");
                result.put("message", "JSON格式错误: " + e.getMessage());
                responsePrint(resp, toJson(result));
                return;
            }
            
            if (request == null) {
                logger.error("解析后的请求对象为null");
                result.put("status", "failed");
                result.put("message", "请求解析失败");
                responsePrint(resp, toJson(result));
                return;
            }
            
            logger.info("解析后的请求对象: patentType={}, technicalPoints={}, inventorInfo={}, userId={}", 
                    request.getPatentType(), 
                    request.getTechnicalPoints(), 
                    request.getInventorInfo(), 
                    request.getUserId());
            
            // 如果 technicalPoints 为 null，尝试手动从 JSON 中提取
            if (request.getTechnicalPoints() == null && jsonString.contains("technicalPoints")) {
                logger.warn("technicalPoints为null，但JSON中包含该字段，尝试手动解析");
                try {
                    com.google.gson.JsonObject jsonObject = gson.fromJson(jsonString, com.google.gson.JsonObject.class);
                    if (jsonObject.has("technicalPoints")) {
                        String manualTechnicalPoints = jsonObject.get("technicalPoints").getAsString();
                        logger.info("手动解析得到的technicalPoints: {}", manualTechnicalPoints);
                        // 使用反射设置值
                        java.lang.reflect.Field field = PatentDocumentRequest.class.getDeclaredField("technicalPoints");
                        field.setAccessible(true);
                        field.set(request, manualTechnicalPoints);
                        logger.info("已通过反射设置technicalPoints值");
                    }
                } catch (Exception e) {
                    logger.error("手动解析technicalPoints失败", e);
                }
            }
            
            // 参数验证
            String technicalPoints = request.getTechnicalPoints();
            if (technicalPoints == null) {
                logger.warn("technicalPoints为null");
                result.put("status", "failed");
                result.put("message", "technicalPoints参数不能为空");
                responsePrint(resp, toJson(result));
                return;
            }
            
            if (technicalPoints.trim().isEmpty()) {
                logger.warn("technicalPoints为空字符串");
                result.put("status", "failed");
                result.put("message", "technicalPoints参数不能为空");
                responsePrint(resp, toJson(result));
                return;
            }
            
            // 专利类型映射：支持英文和中文
            String patentType = normalizePatentType(request.getPatentType());
            logger.info("专利类型: {} (原始: {})", patentType, request.getPatentType());
            
            // 获取模板并替换参数
            String template = WritingTemplateManager.getPatentTemplate(patentType);
            technicalPoints = technicalPoints.trim(); // 已经验证过不为空
            String inventorInfo = request.getInventorInfo() != null ? request.getInventorInfo().trim() : "";
            
            String prompt = template
                    .replace("{patentType}", patentType)
                    .replace("{technicalPoints}", technicalPoints)
                    .replace("{inventorInfo}", inventorInfo);
            
            logger.info("生成的提示词（前500字符）: {}", prompt.length() > 500 ? prompt.substring(0, 500) + "..." : prompt);
            logger.info("技术要点: {}", technicalPoints);
            logger.info("发明人信息: {}", inventorInfo);
            
            ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest();
            chatCompletionRequest.setTemperature(0.7);
            chatCompletionRequest.setMax_tokens(4096);
            chatCompletionRequest.setStream(false);
            chatCompletionRequest.setCategory(LagiGlobal.getDefaultCategory());
            
            ChatMessage message = new ChatMessage();
            message.setRole("user");
            message.setContent(prompt);
            chatCompletionRequest.setMessages(Lists.newArrayList(message));
            
            // RAG检索：使用技术要点作为查询，而不是整个提示词
            // 这样可以确保检索到的内容与用户输入相关
            // 注意：RAG检索是可选的，如果失败应该继续执行
            GetRagContext context = null;
            if (technicalPoints != null && !technicalPoints.trim().isEmpty()) {
                try {
                    // 创建一个临时请求用于RAG检索，使用技术要点作为查询
                    ChatCompletionRequest ragRequest = new ChatCompletionRequest();
                    ChatMessage ragMessage = new ChatMessage();
                    ragMessage.setRole("user");
                    ragMessage.setContent(technicalPoints); // 使用技术要点进行检索
                    ragRequest.setMessages(Lists.newArrayList(ragMessage));
                    ragRequest.setCategory(LagiGlobal.getDefaultCategory());
                    // 设置必要的字段，避免NullPointerException
                    ragRequest.setMax_tokens(4096);
                    ragRequest.setTemperature(0.7);
                    ragRequest.setStream(false);
                    
                    List<IndexSearchData> indexSearchDataList = vectorDbService.searchByContext(ragRequest);
                    if (indexSearchDataList != null && !indexSearchDataList.isEmpty()) {
                        context = completionsService.getRagContext(indexSearchDataList);
                        if (context != null && context.getContext() != null && !context.getContext().trim().isEmpty()) {
                            // 将RAG上下文添加到提示词中，而不是替换用户输入
                            String contextStr = context.getContext();
                            // 在提示词末尾添加RAG上下文作为参考
                            prompt = prompt + "\n\n参考文档内容（仅供参考，请以用户提供的技术要点为准）：\n" + contextStr;
                            message.setContent(prompt);
                            chatCompletionRequest.setMessages(Lists.newArrayList(message));
                            logger.info("已添加RAG上下文，上下文长度: {}", contextStr.length());
                        }
                    }
                } catch (Exception e) {
                    // RAG检索失败不影响主流程，记录日志后继续执行
                    logger.warn("RAG检索失败，将不使用RAG上下文: {}", e.getMessage());
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
            logger.error("专利文档生成失败", e);
            result.put("status", "failed");
            result.put("message", "服务器内部错误: " + e.getMessage());
        }
        
        responsePrint(resp, toJson(result));
    }
    
    /**
     * 标准化专利类型：将英文或中文专利类型转换为中文标准格式
     */
    private String normalizePatentType(String patentType) {
        if (patentType == null || patentType.trim().isEmpty()) {
            return "发明专利";
        }
        
        String normalized = patentType.trim().toLowerCase();
        
        // 英文到中文的映射
        if (normalized.equals("invention_patent") || normalized.equals("invention") || normalized.equals("发明专利")) {
            return "发明专利";
        } else if (normalized.equals("utility_model") || normalized.equals("utility") || normalized.equals("实用新型")) {
            return "实用新型";
        } else if (normalized.equals("design_patent") || normalized.equals("design") || normalized.equals("外观设计")) {
            return "外观设计";
        }
        
        // 如果已经是中文，直接返回
        if (patentType.contains("发明") || patentType.contains("实用新型") || patentType.contains("外观设计")) {
            return patentType;
        }
        
        // 默认返回发明专利
        return "发明专利";
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
                        // 自动判断项目申报模板类型（如果未提供）
                        templateType = ProcessingLevelDetector.detectProjectTemplateType(currentUserMessage);
                        logger.info("自动判断项目申报模板类型: {} - {}", templateType, currentUserMessage);
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

