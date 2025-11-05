package ai.workflow;

import ai.utils.ResourceUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class WorkflowPromptUtil {
    private static String startNodeDescription = "开始节点 , 可以添加输出, 默认的输入为用户的 query , 也可根据需求添加后续几点可能会使用的其他 属性 : 如 llm 需要用到的 modelName等";
    private static String endNodeDescription = "结束输出节点, 用于接受上一步节点的输出, 提取对应的信息作为最终输出";
    private static String KnowLegeBaseNodeDescription = "知识库检索节点, 接受用户的输入 或 其他信息如具体哪个知识库， 对知识库进行检索, 并将检索结果已字符串传形式输出";
    private static String llmNodeDescription = "LLM 节点, 利用大模型的功能， 根据设定的提示词， 用户输入, 回答用户的问题";
    private static String intentNodeDescription = "意图识别 节点, 根据用户的输入, 判定用户的意图输出为一个字符串 ： 其主要的作用是判断用是否是多模态的问题, ";

    public static final String VARIABLE_EXTRACT_PROMPT = ResourceUtil.loadAsString("/prompts/workflow_variable_extract.md");
    public static final String USER_INFO_EXTRACT_PROMPT = ResourceUtil.loadAsString("/prompts/workflow_user_info_extract.md");
    public static final String NODE_OUTPUT_SPEC = ResourceUtil.loadAsString("/prompts/workflow_node_output_spec.md");

    // 动态加载节点字段规范
    private static final String NODE_FIELDS_SPEC = ResourceUtil.loadAsString("/prompts/workflow_node_fields_spec.md");

//    private static final String NODE_MATCHING_PROMPT;
//    private static final String PROMPT_TO_WORKFLOW_JSON;


    public static String getPromptToWorkflowJson(List<String> ignoreNodes) {
        String template = ResourceUtil.loadAsString("/prompts/workflow_text_to_json.md");
        Map<String, String> map = ResourceUtil.loadMultipleFromDirectory("/prompts/nodes", "node_", ".md");
        StringBuilder nodesJson = new StringBuilder();
        Set<String> strings = map.keySet();
        int count = 1;
        for (String key : strings) {
            if(ignoreNodes.contains(key)) {
                continue;
            }
            String value = map.getOrDefault(key, "");
            String[] split = value.split("={5,100}");
            String json = split[1];
            nodesJson.append("\n\n").append("### 2.").append(count).append(" ").append(json);
            count++;
        }
        return StringUtils.replaceEach(template, new String[]{"${{node-list-template}}",
        }, new String[]{nodesJson.toString()});
    }

    public static String getPromptToWorkflowStepByStepJson(List<String> ignoreNodes) {
        String template = ResourceUtil.loadAsString("/prompts/workflow_text_to_json_step_by_step.md");
        Map<String, String> map = ResourceUtil.loadMultipleFromDirectory("/prompts/nodes", "node_", ".md");
        StringBuilder nodesJson = new StringBuilder();
        Set<String> strings = map.keySet();
        int count = 1;
        for (String key : strings) {
            if(ignoreNodes.contains(key)) {
                continue;
            }
            String value = map.getOrDefault(key, "");
            String[] split = value.split("={5,100}");
            String json = split[1];
            nodesJson.append("\n\n").append("### 2.").append(count).append(" ").append(json);
            count++;
        }

        // 注入节点输出字段规范的核心内容
        String outputSpecReminder = buildOutputSpecReminder();
        String finalTemplate = template.replace("${{node-list-template}}", nodesJson.toString());

        // 在第三步之前插入输出字段规范提醒
        if (finalTemplate.contains("## 第三步：")) {
            finalTemplate = finalTemplate.replace("## 第三步：",
                    outputSpecReminder + "\n\n## 第三步：");
        }

        return finalTemplate;
    }

    /**
     * 构建节点输出字段规范提醒内容（从文件动态加载）
     */
    private static String buildOutputSpecReminder() {
        if (NODE_FIELDS_SPEC == null || NODE_FIELDS_SPEC.isEmpty()) {
            // 降级方案：如果文件加载失败，使用简化版本
            return "\n### 节点输出字段规范提醒\n\n" +
                    "**在生成每个节点时，必须确保输出字段使用标准名称：**\n" +
                    "- llm、knowledge-base、program、agent等大多数节点 → 输出字段为 `result`\n" +
                    "- intent-recognition 节点 → 输出字段为 `intent`\n" +
                    "- api 节点 → 输出字段为 `statusCode` 和 `body`\n" +
                    "- 引用时必须使用这些标准字段名，不可使用 answer、response、output 等自定义名称\n";
        }

        // 从完整规范中提取输出字段表格部分
        return "\n### 节点输出字段规范提醒\n\n" +
                "**在生成每个节点时，必须确保输出字段使用标准名称：**\n\n" +
                extractOutputFieldsTable() +
                "\n- 引用时必须使用这些标准字段名，不可使用 answer、response、output 等自定义名称\n";
    }



    public static String getNodeMatchingPrompt(List<String> ignoreNodes) {
        String template = ResourceUtil.loadAsString("/prompts/workflow_node_matching.md");
        Map<String, String> map = ResourceUtil.loadMultipleFromDirectory("/prompts/nodes", "node_", ".md");
        StringBuilder nodesJson = new StringBuilder();
        Set<String> strings = map.keySet();
        int count = 1;
        for (String key : strings) {
            if(ignoreNodes.contains(key)) {
                continue;
            }
            String value = map.getOrDefault(key, "");
            String[] split = value.split("={5,100}");
            String description = split[0];
            nodesJson.append("\n\n").append("### ").append(count).append(". ").append(description);
            count++;
        }

        String finalTemplate = template.replace("${{node-list-template}}", nodesJson.toString());

        // 添加输出字段规范提醒
        String outputSpecReminder = "\n\n**重要提醒：节点输出字段**\n" +
                "- 在匹配节点时，请注意大多数节点的输出字段统一为 `result`\n" +
                "- intent-recognition 节点输出为 `intent`\n" +
                "- api 节点输出为 `statusCode` 和 `body`\n";

        if (finalTemplate.contains("## 输出要求")) {
            finalTemplate = finalTemplate.replace("## 输出要求",
                    outputSpecReminder + "\n## 输出要求");
        }

        return finalTemplate;
    }

    public static String getWorkflowDocPrompt() {
        String template = ResourceUtil.loadAsString("/prompts/workflow_doc_prompt.md");

        // 注入节点输出字段规范提醒
        String outputSpecReminder = buildNodeOutputSpecForDoc();

        // 在输出格式要求之前插入节点字段规范
        if (template.contains("### 输出格式要求")) {
            template = template.replace("### 输出格式要求",
                    outputSpecReminder + "\n\n### 输出格式要求");
        } else if (template.contains("## 输出格式要求")) {
            template = template.replace("## 输出格式要求",
                    outputSpecReminder + "\n\n## 输出格式要求");
        }

        return template;
    }

    /**
     * 构建用于文档提取的节点输出字段规范说明（从文件动态加载）
     */
    private static String buildNodeOutputSpecForDoc() {
        if (NODE_FIELDS_SPEC == null || NODE_FIELDS_SPEC.isEmpty()) {
            // 降级方案：如果文件加载失败，使用简化版本
            return "\n### 节点标准字段规范（重要）\n\n" +
                    "**在提取工作流信息时，必须使用标准的节点字段名称。**\n" +
                    "大多数节点输出字段为 `result`，intent-recognition 为 `intent`，api 为 `statusCode` 和 `body`。\n";
        }

        // 直接使用加载的完整规范，添加标题
        return "\n### 节点标准字段规范（重要）\n\n" +
                "**在提取工作流信息时，必须使用标准的节点字段名称，不可自行推测、添加或使用语义相似的名称：**\n\n" +
                NODE_FIELDS_SPEC;
    }

    /**
     * 提取输出字段表格（用于简短提醒）
     */
    private static String extractOutputFieldsTable() {
        if (NODE_FIELDS_SPEC == null || NODE_FIELDS_SPEC.isEmpty()) {
            return "- 大多数节点 → `result`\n- intent-recognition → `intent`\n- api → `statusCode`, `body`\n";
        }

        // 提取 "## 输出字段规范" 到下一个 "##" 之间的内容
        String[] sections = NODE_FIELDS_SPEC.split("##");
        for (String section : sections) {
            if (section.trim().startsWith("输出字段规范")) {
                // 找到输出字段规范部分，提取表格
                String[] lines = section.split("\n");
                StringBuilder table = new StringBuilder();
                boolean inTable = false;

                for (String line : lines) {
                    if (line.trim().startsWith("|") && !line.contains("---")) {
                        inTable = true;
                        table.append(line).append("\n");
                    } else if (inTable && !line.trim().startsWith("|")) {
                        break;
                    }
                }

                if (table.length() > 0) {
                    return table.toString();
                }
            }
        }

        // 如果提取失败，返回简化版本
        return "- 大多数节点 → `result`\n- intent-recognition → `intent`\n- api → `statusCode`, `body`\n";
    }

}
