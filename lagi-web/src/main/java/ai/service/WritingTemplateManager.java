package ai.service;

import java.util.HashMap;
import java.util.Map;

public class WritingTemplateManager {
    
    private static final Map<String, String> PROJECT_TEMPLATES = new HashMap<>();
    private static final Map<String, String> PATENT_TEMPLATES = new HashMap<>();
    
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

