package ai.service;

import java.util.HashMap;
import java.util.Map;

public class WritingTemplateManager {
    
    private static final Map<String, String> PROJECT_TEMPLATES = new HashMap<>();
    private static final Map<String, String> PATENT_TEMPLATES = new HashMap<>();
    
    // 处理层次常量
    public static final String LEVEL_RAG = "rag";
    public static final String LEVEL_PARAGRAPH = "paragraph";
    public static final String LEVEL_DOCUMENT = "document";
    
    static {
        PROJECT_TEMPLATES.put("国家科技计划项目申报书", getNationalProjectTemplate());
        PROJECT_TEMPLATES.put("省级科技计划项目申报书", getProvincialProjectTemplate());
        PROJECT_TEMPLATES.put("市级科技计划项目申报书", getCityProjectTemplate());
        
        PATENT_TEMPLATES.put("发明专利", getInventionPatentTemplate());
        PATENT_TEMPLATES.put("实用新型", getUtilityModelTemplate());
        PATENT_TEMPLATES.put("外观设计", getDesignPatentTemplate());
    }
    
    public static String getProjectTemplate(String templateType) {
        return PROJECT_TEMPLATES.getOrDefault(templateType, getDefaultProjectTemplate());
    }
    
    public static String getPatentTemplate(String patentType) {
        return PATENT_TEMPLATES.getOrDefault(patentType, getDefaultPatentTemplate());
    }
    
    /**
     * 获取RAG提问的系统提示词
     */
    public static String getRagPrompt(String writingType) {
        if ("patent-document".equals(writingType)) {
            return "你是一个专业的专利撰写助手。用户上传了专利相关的文档，你可以基于这些文档回答用户的问题。\n" +
                   "请根据文档内容准确回答用户的问题，如果文档中没有相关信息，请明确说明。";
        } else {
            return "你是一个专业的科技项目申报材料撰写助手。用户上传了项目申报相关的文档（如申报通知、要求、模板等），你可以基于这些文档回答用户的问题。\n" +
                   "请根据文档内容准确回答用户的问题，如果文档中没有相关信息，请明确说明。";
        }
    }
    
    /**
     * 获取生成段落的系统提示词
     */
    public static String getParagraphPrompt(String writingType, String userQuery, String context) {
        if ("patent-document".equals(writingType)) {
            return String.format(
                "你是一个专业的专利撰写专家。用户需要生成专利文档中的某个段落内容。\n\n" +
                "用户需求：%s\n\n" +
                "参考文档内容：\n%s\n\n" +
                "请根据用户需求和参考文档，生成符合专利撰写规范的段落内容。要求：\n" +
                "1. 内容准确、专业\n" +
                "2. 符合专利撰写规范\n" +
                "3. 语言流畅、逻辑清晰\n" +
                "4. 段落长度适中（通常200-500字）",
                userQuery, context != null ? context : "无参考文档"
            );
        } else {
            return String.format(
                "你是一个专业的科技项目申报材料撰写专家。用户需要生成项目申报表格中某个字段的内容（如\"项目亮点介绍\"、\"单位简介\"等）。\n\n" +
                "用户需求：%s\n\n" +
                "参考文档内容：\n%s\n\n" +
                "请根据用户需求和参考文档，生成符合项目申报要求的段落内容。要求：\n" +
                "1. 内容详实、重点突出\n" +
                "2. 符合项目申报规范\n" +
                "3. 语言流畅、逻辑清晰\n" +
                "4. 段落长度适中（通常200-500字）\n" +
                "注意：不需要生成表格格式，只需要生成表格中要求填写的文字内容。",
                userQuery, context != null ? context : "无参考文档"
            );
        }
    }
    
    /**
     * 获取生成完整文档的系统提示词
     */
    public static String getDocumentPrompt(String writingType, String templateType, String context) {
        if ("patent-document".equals(writingType)) {
            return String.format(
                "你是一个专业的专利撰写专家。请根据用户提供的专利内容，按照官方标准模板生成完整的专利文档。\n\n" +
                "参考文档内容：\n%s\n\n" +
                "请按照以下结构撰写完整的专利文档：\n" +
                "1. 专利名称\n" +
                "2. 技术领域\n" +
                "3. 背景技术\n" +
                "4. 发明内容\n" +
                "5. 附图说明\n" +
                "6. 具体实施方式\n" +
                "7. 权利要求书\n" +
                "8. 摘要\n\n" +
                "要求：\n" +
                "1. 技术描述准确、详细\n" +
                "2. 权利要求清晰、完整\n" +
                "3. 符合专利撰写规范\n" +
                "4. 内容完整、逻辑清晰",
                context != null ? context : "无参考文档"
            );
        } else {
            String template = getProjectTemplate(templateType);
            return String.format(
                "%s\n\n" +
                "参考文档内容（申报要求、通知等）：\n%s\n\n" +
                "请根据参考文档中的申报要求，生成完整的项目申报书。要求：\n" +
                "1. 严格按照参考文档中的申报要求撰写\n" +
                "2. 内容详实、逻辑清晰\n" +
                "3. 符合项目申报规范\n" +
                "4. 结构完整、格式规范",
                template, context != null ? context : "无参考文档"
            );
        }
    }
    
    private static String getNationalProjectTemplate() {
        return "你是一个专业的科技项目申报材料撰写专家。请根据以下信息撰写国家科技计划项目申报书。\n\n" +
                "项目类型：{projectType}\n" +
                "企业信息：{enterpriseInfo}\n" +
                "项目信息：{projectInfo}\n\n" +
                "请按照以下结构撰写申报材料：\n" +
                "1. 项目概述\n" +
                "2. 项目背景与意义\n" +
                "3. 项目目标与内容\n" +
                "4. 技术方案与创新点\n" +
                "5. 实施计划与进度安排\n" +
                "6. 预期成果与效益\n" +
                "7. 项目团队与条件保障\n" +
                "8. 经费预算\n\n" +
                "要求内容详实、逻辑清晰、符合国家科技计划项目申报规范。";
    }
    
    private static String getProvincialProjectTemplate() {
        return "你是一个专业的科技项目申报材料撰写专家。请根据以下信息撰写省级科技计划项目申报书。\n\n" +
                "项目类型：{projectType}\n" +
                "企业信息：{enterpriseInfo}\n" +
                "项目信息：{projectInfo}\n\n" +
                "请按照以下结构撰写申报材料：\n" +
                "1. 项目概述\n" +
                "2. 项目背景与意义\n" +
                "3. 项目目标与内容\n" +
                "4. 技术方案与创新点\n" +
                "5. 实施计划与进度安排\n" +
                "6. 预期成果与效益\n" +
                "7. 项目团队与条件保障\n" +
                "8. 经费预算\n\n" +
                "要求内容详实、逻辑清晰、符合省级科技计划项目申报规范。";
    }
    
    private static String getCityProjectTemplate() {
        return "你是一个专业的科技项目申报材料撰写专家。请根据以下信息撰写市级科技计划项目申报书。\n\n" +
                "项目类型：{projectType}\n" +
                "企业信息：{enterpriseInfo}\n" +
                "项目信息：{projectInfo}\n\n" +
                "请按照以下结构撰写申报材料：\n" +
                "1. 项目概述\n" +
                "2. 项目背景与意义\n" +
                "3. 项目目标与内容\n" +
                "4. 技术方案与创新点\n" +
                "5. 实施计划与进度安排\n" +
                "6. 预期成果与效益\n" +
                "7. 项目团队与条件保障\n" +
                "8. 经费预算\n\n" +
                "要求内容详实、逻辑清晰、符合市级科技计划项目申报规范。";
    }
    
    private static String getDefaultProjectTemplate() {
        return "你是一个专业的科技项目申报材料撰写专家。请根据以下信息撰写科技项目申报材料。\n\n" +
                "项目类型：{projectType}\n" +
                "企业信息：{enterpriseInfo}\n" +
                "项目信息：{projectInfo}\n\n" +
                "请撰写完整的科技项目申报材料，要求内容详实、逻辑清晰、符合科技项目申报规范。";
    }
    
    private static String getInventionPatentTemplate() {
        return "你是一个专业的专利撰写专家。请根据以下信息撰写发明专利文档。\n\n" +
                "专利类型：发明专利\n" +
                "技术点描述：{technicalPoints}\n" +
                "发明人信息：{inventorInfo}\n\n" +
                "请按照以下结构撰写专利文档：\n" +
                "1. 专利名称\n" +
                "2. 技术领域\n" +
                "3. 背景技术\n" +
                "4. 发明内容\n" +
                "5. 附图说明\n" +
                "6. 具体实施方式\n" +
                "7. 权利要求书\n" +
                "8. 摘要\n\n" +
                "要求技术描述准确、权利要求清晰、符合发明专利撰写规范。";
    }
    
    private static String getUtilityModelTemplate() {
        return "你是一个专业的专利撰写专家。请根据以下信息撰写实用新型专利文档。\n\n" +
                "专利类型：实用新型\n" +
                "技术点描述：{technicalPoints}\n" +
                "发明人信息：{inventorInfo}\n\n" +
                "请按照以下结构撰写专利文档：\n" +
                "1. 专利名称\n" +
                "2. 技术领域\n" +
                "3. 背景技术\n" +
                "4. 实用新型内容\n" +
                "5. 附图说明\n" +
                "6. 具体实施方式\n" +
                "7. 权利要求书\n" +
                "8. 摘要\n\n" +
                "要求技术描述准确、权利要求清晰、符合实用新型专利撰写规范。";
    }
    
    private static String getDesignPatentTemplate() {
        return "你是一个专业的专利撰写专家。请根据以下信息撰写外观设计专利文档。\n\n" +
                "专利类型：外观设计\n" +
                "技术点描述：{technicalPoints}\n" +
                "发明人信息：{inventorInfo}\n\n" +
                "请按照以下结构撰写专利文档：\n" +
                "1. 专利名称\n" +
                "2. 产品用途\n" +
                "3. 设计要点\n" +
                "4. 附图说明\n" +
                "5. 简要说明\n\n" +
                "要求设计描述准确、附图说明清晰、符合外观设计专利撰写规范。";
    }
    
    private static String getDefaultPatentTemplate() {
        return "你是一个专业的专利撰写专家。请根据以下信息撰写专利文档。\n\n" +
                "专利类型：{patentType}\n" +
                "技术点描述：{technicalPoints}\n" +
                "发明人信息：{inventorInfo}\n\n" +
                "请撰写完整的专利文档，包括专利名称、技术领域、背景技术、发明内容、具体实施方式、权利要求书和摘要等部分，要求技术描述准确、权利要求清晰、符合专利撰写规范。";
    }
}

