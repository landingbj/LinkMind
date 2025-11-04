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
        return StringUtils.replaceEach(template, new String[]{"${{node-list-template}}",
        }, new String[]{nodesJson.toString()});
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
        return StringUtils.replaceEach(template, new String[]{"${{node-list-template}}",
        }, new String[]{nodesJson.toString()});
    }

    public static String getWorkflowDocPrompt() {
        return ResourceUtil.loadAsString("/prompts/workflow_doc_prompt.md");
    }

}
