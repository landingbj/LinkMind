package ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 处理层次智能判断器
 * 根据用户输入自动判断应该使用哪个处理层次：RAG、生成段落、生成文章
 */
public class ProcessingLevelDetector {
    private static final Logger logger = LoggerFactory.getLogger(ProcessingLevelDetector.class);
    
    // 生成完整文档的关键词
    private static final List<String> DOCUMENT_KEYWORDS = Arrays.asList(
        "生成完整", "写完整", "生成整个", "写整个", "生成全部", "写全部",
        "生成申报书", "写申报书", "生成专利", "写专利", "生成专利文档", "写专利文档",
        "生成项目申报书", "写项目申报书", "生成完整申报书", "写完整申报书",
        "生成完整文档", "写完整文档", "生成整个文档", "写整个文档",
        "按照要求", "按照模板", "按照标准", "按照规范",
        "生成项目申报材料", "写项目申报材料"
    );
    
    // 生成段落的关键词
    private static final List<String> PARAGRAPH_KEYWORDS = Arrays.asList(
        "生成", "写", "撰写", "编写", "创作",
        "项目亮点", "单位简介", "企业简介", "公司简介",
        "技术方案", "实施方案", "项目概述", "项目背景",
        "创新点", "技术优势", "市场前景", "经济效益",
        "团队介绍", "条件保障", "经费预算", "预期成果",
        "一段", "段落", "介绍", "说明", "描述"
    );
    
    // RAG提问的关键词（疑问词）
    private static final List<String> RAG_KEYWORDS = Arrays.asList(
        "什么", "哪些", "怎么", "如何", "为什么", "是否", "有没有",
        "需要", "要求", "条件", "标准", "规范", "流程", "步骤",
        "什么时候", "哪里", "哪个", "多少", "多久"
    );
    
    // 疑问词模式
    private static final Pattern QUESTION_PATTERN = Pattern.compile("[？?]");
    
    // 专利相关关键词
    private static final List<String> PATENT_KEYWORDS = Arrays.asList(
        "专利", "发明", "实用新型", "外观设计", "权利要求", "技术领域",
        "背景技术", "发明内容", "具体实施方式", "专利文档", "专利申请书",
        "专利说明书", "专利摘要", "专利名称", "发明人", "申请人",
        "专利号", "专利申请", "专利撰写", "专利代理"
    );
    
    // 项目申报相关关键词
    private static final List<String> PROJECT_KEYWORDS = Arrays.asList(
        "项目申报", "申报书", "申报材料", "项目申报书", "科技项目",
        "项目概述", "项目背景", "项目目标", "项目内容", "项目方案",
        "项目计划", "项目预算", "项目团队", "项目效益", "项目成果",
        "申报要求", "申报通知", "申报指南", "申报条件", "申报流程",
        "科技计划", "省级项目", "市级项目", "国家级项目", "项目亮点",
        "单位简介", "企业简介", "公司简介", "经费预算", "预期成果"
    );
    
    // 撰写类型常量
    public static final String WRITING_TYPE_PROJECT = "project-material";
    public static final String WRITING_TYPE_PATENT = "patent-document";
    
    // 专利类型关键词
    private static final List<String> INVENTION_PATENT_KEYWORDS = Arrays.asList(
        "发明专利", "发明", "发明创造", "技术方案", "技术方法", "技术工艺"
    );
    
    private static final List<String> UTILITY_MODEL_KEYWORDS = Arrays.asList(
        "实用新型", "实用", "产品结构", "产品形状", "产品构造"
    );
    
    private static final List<String> DESIGN_PATENT_KEYWORDS = Arrays.asList(
        "外观设计", "外观", "产品外观", "设计", "形状", "图案", "色彩"
    );
    
    // 专利类型常量
    public static final String PATENT_TYPE_INVENTION = "发明专利";
    public static final String PATENT_TYPE_UTILITY_MODEL = "实用新型";
    public static final String PATENT_TYPE_DESIGN = "外观设计";
    
    /**
     * 根据用户消息自动判断撰写类型（项目申报还是专利材料）
     * 
     * @param userMessage 用户消息内容
     * @return 撰写类型：project-material 或 patent-document
     */
    public static String detectWritingType(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            // 默认返回项目申报
            return WRITING_TYPE_PROJECT;
        }
        
        String message = userMessage.toLowerCase().trim();
        
        // 统计关键词匹配数量
        int patentScore = countKeywords(message, PATENT_KEYWORDS);
        int projectScore = countKeywords(message, PROJECT_KEYWORDS);
        
        logger.debug("撰写类型判断 - 专利得分: {}, 项目得分: {}, 消息: {}", 
            patentScore, projectScore, userMessage);
        
        // 如果专利关键词明显更多，判断为专利
        if (patentScore > projectScore && patentScore > 0) {
            logger.debug("检测到专利材料需求: {}", userMessage);
            return WRITING_TYPE_PATENT;
        }
        
        // 如果项目关键词更多，或者专利关键词为0，判断为项目申报
        if (projectScore > 0 || patentScore == 0) {
            logger.debug("检测到项目申报需求: {}", userMessage);
            return WRITING_TYPE_PROJECT;
        }
        
        // 默认返回项目申报
        return WRITING_TYPE_PROJECT;
    }
    
    /**
     * 根据用户消息自动判断专利类型
     * 
     * @param userMessage 用户消息内容
     * @return 专利类型：发明专利、实用新型、外观设计
     */
    public static String detectPatentType(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            // 默认返回发明专利
            return PATENT_TYPE_INVENTION;
        }
        
        String message = userMessage.toLowerCase().trim();
        
        // 统计各类型关键词匹配数量
        int inventionScore = countKeywords(message, INVENTION_PATENT_KEYWORDS);
        int utilityModelScore = countKeywords(message, UTILITY_MODEL_KEYWORDS);
        int designScore = countKeywords(message, DESIGN_PATENT_KEYWORDS);
        
        logger.debug("专利类型判断 - 发明得分: {}, 实用新型得分: {}, 外观设计得分: {}, 消息: {}", 
            inventionScore, utilityModelScore, designScore, userMessage);
        
        // 如果外观设计关键词最多，判断为外观设计
        if (designScore > utilityModelScore && designScore > inventionScore && designScore > 0) {
            logger.debug("检测到外观设计专利: {}", userMessage);
            return PATENT_TYPE_DESIGN;
        }
        
        // 如果实用新型关键词最多，判断为实用新型
        if (utilityModelScore > inventionScore && utilityModelScore > 0) {
            logger.debug("检测到实用新型专利: {}", userMessage);
            return PATENT_TYPE_UTILITY_MODEL;
        }
        
        // 如果发明专利关键词最多，或者都没有匹配，默认判断为发明专利
        if (inventionScore > 0 || (utilityModelScore == 0 && designScore == 0)) {
            logger.debug("检测到发明专利（或默认）: {}", userMessage);
            return PATENT_TYPE_INVENTION;
        }
        
        // 默认返回发明专利
        return PATENT_TYPE_INVENTION;
    }
    
    /**
     * 统计消息中包含的关键词数量
     */
    private static int countKeywords(String message, List<String> keywords) {
        int count = 0;
        for (String keyword : keywords) {
            if (message.contains(keyword)) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * 根据用户消息自动判断处理层次
     * 
     * @param userMessage 用户消息内容
     * @param writingType 撰写类型（可选，如果为null会自动判断）
     * @return 处理层次：rag、paragraph、document
     */
    public static String detectProcessingLevel(String userMessage, String writingType) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return WritingTemplateManager.LEVEL_RAG;
        }
        
        String message = userMessage.toLowerCase().trim();
        
        // 1. 优先判断是否是生成完整文档
        if (containsKeywords(message, DOCUMENT_KEYWORDS)) {
            logger.debug("检测到生成完整文档需求: {}", userMessage);
            return WritingTemplateManager.LEVEL_DOCUMENT;
        }
        
        // 2. 判断是否是生成段落
        if (containsKeywords(message, PARAGRAPH_KEYWORDS)) {
            // 但如果包含疑问词，更可能是RAG提问
            if (!containsQuestionWords(message)) {
                logger.debug("检测到生成段落需求: {}", userMessage);
                return WritingTemplateManager.LEVEL_PARAGRAPH;
            }
        }
        
        // 3. 判断是否是RAG提问（包含疑问词或以问号结尾）
        if (containsQuestionWords(message) || QUESTION_PATTERN.matcher(userMessage).find()) {
            logger.debug("检测到RAG提问需求: {}", userMessage);
            return WritingTemplateManager.LEVEL_RAG;
        }
        
        // 4. 默认判断：如果消息较短且没有明显的生成意图，可能是提问
        if (message.length() < 50 && !message.contains("生成") && !message.contains("写")) {
            logger.debug("短消息，判断为RAG提问: {}", userMessage);
            return WritingTemplateManager.LEVEL_RAG;
        }
        
        // 5. 默认判断为生成段落（因为用户可能想生成内容）
        logger.debug("默认判断为生成段落: {}", userMessage);
        return WritingTemplateManager.LEVEL_PARAGRAPH;
    }
    
    /**
     * 检查消息是否包含关键词列表中的任意一个
     */
    private static boolean containsKeywords(String message, List<String> keywords) {
        for (String keyword : keywords) {
            if (message.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查消息是否包含疑问词
     */
    private static boolean containsQuestionWords(String message) {
        return containsKeywords(message, RAG_KEYWORDS);
    }
    
    /**
     * 根据消息历史上下文进一步优化判断
     * 如果历史对话中有生成完整文档的意图，当前消息可能是补充信息
     */
    public static String detectWithContext(String currentMessage, List<String> recentMessages, String writingType) {
        // 检查最近的消息中是否有生成完整文档的意图
        for (String msg : recentMessages) {
            if (msg != null && containsKeywords(msg.toLowerCase(), DOCUMENT_KEYWORDS)) {
                logger.debug("根据上下文判断为生成完整文档: {}", currentMessage);
                return WritingTemplateManager.LEVEL_DOCUMENT;
            }
        }
        
        // 否则使用基本判断
        return detectProcessingLevel(currentMessage, writingType);
    }
}

