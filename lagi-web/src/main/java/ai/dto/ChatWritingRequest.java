package ai.dto;

import ai.openai.pojo.ChatMessage;
import lombok.*;

import java.util.List;

/**
 * 聊天式撰写请求
 * 支持申报材料和专利材料的聊天式撰写
 */
@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatWritingRequest {
    /**
     * 会话ID，用于维护聊天上下文
     */
    private String sessionId;
    
    /**
     * 撰写类型：project-material（申报材料）或 patent-document（专利材料）
     */
    private String writingType;
    
    /**
     * 用户消息内容
     */
    private String message;
    
    /**
     * 知识库分类（全局知识库）
     */
    private String globalCategory;
    
    /**
     * 会话级知识库分类（仅当前会话生效）
     */
    private String sessionCategory;
    
    /**
     * 处理层次：
     * - rag: 基本RAG提问
     * - paragraph: 生成段落（如"项目亮点介绍"、"单位简介"）
     * - document: 生成完整文档（项目申报书或专利文档）
     */
    private String processingLevel;
    
    /**
     * 项目类型（申报材料时使用）
     */
    private String projectType;
    
    /**
     * 专利类型（专利材料时使用）：发明专利、实用新型、外观设计
     */
    private String patentType;
    
    /**
     * 模板类型（可选）
     */
    private String templateType;
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 聊天消息历史（可选，用于上下文）
     * 注意：实际使用时会从会话服务中获取，此字段主要用于兼容
     */
    private List<ChatMessage> messageHistory;
    
    /**
     * 其他参数
     */
    private java.util.Map<String, Object> extraParams;
}

