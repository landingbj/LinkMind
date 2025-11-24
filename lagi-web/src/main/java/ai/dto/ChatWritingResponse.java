package ai.dto;

import lombok.*;

import java.util.List;

/**
 * 聊天式撰写响应
 */
@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatWritingResponse {
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 响应内容
     */
    private String content;
    
    /**
     * 处理层次
     */
    private String processingLevel;
    
    /**
     * 参考文档文件名
     */
    private List<String> filename;
    
    /**
     * 参考文档路径
     */
    private List<String> filepath;
    
    /**
     * RAG上下文
     */
    private String context;
    
    /**
     * 上下文块ID
     */
    private List<String> contextChunkIds;
    
    /**
     * 生成的文档ID（如果是生成完整文档）
     */
    private String documentId;
    
    /**
     * 文档标题（如果是生成完整文档）
     */
    private String title;
    
    /**
     * 生成时间
     */
    private String generatedAt;
}

