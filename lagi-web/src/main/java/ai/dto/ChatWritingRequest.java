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
     * 可选，如果不提供，系统会根据用户输入自动判断
     */
    private String writingType;
    
    /**
     * 聊天消息数组（必填）
     * 支持多轮对话，每条消息包含role（user/assistant）和content
     * 最后一条消息应该是user角色，表示当前用户输入
     */
    private List<ChatMessage> messages;
    
    /**
     * 知识库分类（全局知识库）
     */
    private String globalCategory;
    
    /**
     * 会话级知识库分类（仅当前会话生效）
     */
    private String sessionCategory;
    
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

