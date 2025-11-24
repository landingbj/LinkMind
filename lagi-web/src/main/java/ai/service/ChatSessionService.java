package ai.service;

import ai.openai.pojo.ChatMessage;
import ai.utils.MigrateGlobal;
import ai.vector.VectorDbService;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天会话管理服务
 * 支持全局知识库和会话级知识库
 */
public class ChatSessionService {
    private static final Logger logger = LoggerFactory.getLogger(ChatSessionService.class);
    
    // 会话消息历史存储：sessionId -> List<ChatMessage>
    private static final Map<String, List<ChatMessage>> sessionHistory = new ConcurrentHashMap<>();
    
    // 会话级知识库分类映射：sessionId -> category
    private static final Map<String, String> sessionCategories = new ConcurrentHashMap<>();
    
    private final VectorDbService vectorDbService;
    
    public ChatSessionService() {
        this.vectorDbService = new VectorDbService(MigrateGlobal.config);
    }
    
    /**
     * 获取或创建会话ID
     */
    public String getOrCreateSessionId(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }
        if (!sessionHistory.containsKey(sessionId)) {
            sessionHistory.put(sessionId, new ArrayList<>());
        }
        return sessionId;
    }
    
    /**
     * 设置会话级知识库分类
     */
    public void setSessionCategory(String sessionId, String category) {
        if (sessionId != null && category != null && !category.trim().isEmpty()) {
            sessionCategories.put(sessionId, category);
        }
    }
    
    /**
     * 获取会话级知识库分类
     */
    public String getSessionCategory(String sessionId) {
        return sessionCategories.get(sessionId);
    }
    
    /**
     * 添加消息到会话历史
     */
    public void addMessage(String sessionId, String role, String content) {
        sessionId = getOrCreateSessionId(sessionId);
        List<ChatMessage> history = sessionHistory.get(sessionId);
        ChatMessage message = new ChatMessage();
        message.setRole(role);
        message.setContent(content);
        history.add(message);
        
        // 限制历史记录长度，保留最近50条消息
        if (history.size() > 50) {
            history.remove(0);
        }
    }
    
    /**
     * 获取会话消息历史
     */
    public List<ChatMessage> getSessionHistory(String sessionId) {
        if (sessionId == null) {
            return Lists.newArrayList();
        }
        return sessionHistory.getOrDefault(sessionId, Lists.newArrayList());
    }
    
    /**
     * 清空会话历史
     */
    public void clearSession(String sessionId) {
        if (sessionId != null) {
            sessionHistory.remove(sessionId);
            sessionCategories.remove(sessionId);
        }
    }
    
    /**
     * 获取用于RAG检索的category
     * 优先使用会话级category，如果没有则使用全局category
     */
    public String getRagCategory(String sessionId, String globalCategory) {
        String sessionCategory = getSessionCategory(sessionId);
        if (sessionCategory != null && !sessionCategory.trim().isEmpty()) {
            return sessionCategory;
        }
        return globalCategory;
    }
    
    /**
     * 合并全局和会话级知识库的检索结果
     * 先检索全局知识库，再检索会话级知识库
     */
    public List<ai.common.pojo.IndexSearchData> searchWithDualKnowledgeBase(
            String sessionId, 
            String globalCategory, 
            String query) {
        List<ai.common.pojo.IndexSearchData> results = new ArrayList<>();
        
        // 检索全局知识库
        if (globalCategory != null && !globalCategory.trim().isEmpty()) {
            try {
                List<ai.common.pojo.IndexSearchData> globalResults = 
                    vectorDbService.search(query, globalCategory);
                if (globalResults != null) {
                    results.addAll(globalResults);
                }
            } catch (Exception e) {
                logger.warn("检索全局知识库失败: {}", e.getMessage());
            }
        }
        
        // 检索会话级知识库
        String sessionCategory = getSessionCategory(sessionId);
        if (sessionCategory != null && !sessionCategory.trim().isEmpty()) {
            try {
                List<ai.common.pojo.IndexSearchData> sessionResults = 
                    vectorDbService.search(query, sessionCategory);
                if (sessionResults != null) {
                    results.addAll(sessionResults);
                }
            } catch (Exception e) {
                logger.warn("检索会话级知识库失败: {}", e.getMessage());
            }
        }
        
        // 去重并排序（按相似度）
        return deduplicateAndSort(results);
    }
    
    /**
     * 去重并排序检索结果
     */
    private List<ai.common.pojo.IndexSearchData> deduplicateAndSort(
            List<ai.common.pojo.IndexSearchData> results) {
        if (results == null || results.isEmpty()) {
            return Lists.newArrayList();
        }
        
        // 使用LinkedHashMap去重（保留第一个出现的）
        Map<String, ai.common.pojo.IndexSearchData> uniqueResults = new LinkedHashMap<>();
        for (ai.common.pojo.IndexSearchData data : results) {
            if (data != null && data.getId() != null) {
                uniqueResults.putIfAbsent(data.getId(), data);
            }
        }
        
        // 按distance排序（距离越小越相似）
        List<ai.common.pojo.IndexSearchData> sorted = new ArrayList<>(uniqueResults.values());
        sorted.sort(Comparator.comparingDouble(ai.common.pojo.IndexSearchData::getDistance));
        
        return sorted;
    }
}

