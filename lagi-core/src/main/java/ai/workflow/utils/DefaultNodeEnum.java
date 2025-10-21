package ai.workflow.utils;

import ai.workflow.executor.*;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public enum DefaultNodeEnum {

    StartNode("start", new StartNodeExecutor()),
    ConditionNode("condition", new ConditionNodeExecutor()),
    LoopNode("loop", new LoopNodeExecutor()),
    LLMNode("llm", new LLMNodeExecutor()),
    EndNode("end", new EndNodeExecutor()),
    KnowledgeNode("knowledge-base", new KnowledgeNodeExecutor()),
    IntentNode("intent-recognition", new IntentNodeExecutor()),
    GroovyScriptNode("program", new GroovyScriptNodeExecutor()),
    ApiCallNode("api", new ApiCallNodeExecutor()),
    ASRNode("asr", new ASRNodeExecutor()),
    Image2TextNode("image2text", new Image2TextNodeExecutor()),
    Image2DetectNode("image2detect", new ImageObjectDetectExecutor()),
    AgentNode("agent", new AgentNodeExecutor()),
    DatabaseQueryNode("database-query", new DatabaseQueryNodeExecutor()),
    DatabaseUpdateNode("database-update", new DatabaseUpdateNodeExecutor()),
    ;


    private final String name;
    private final INodeExecutor iNodeExecutor;


    DefaultNodeEnum(String name, INodeExecutor iNodeExecutor) {
        this.name = name;
        this.iNodeExecutor = iNodeExecutor;
    }


    public static List<DefaultNodeEnum> getValIdNodeEnum() {
        List<DefaultNodeEnum> nodeEnums = new ArrayList<>();
        for (DefaultNodeEnum nodeEnum : DefaultNodeEnum.values()) {
            if(nodeEnum.iNodeExecutor.isValid()) {
                nodeEnums.add(nodeEnum);
            }
        }
        return nodeEnums;
    }

    public static List<String> getNotValidNodeName() {
        List<String> nodeNames = new ArrayList<>();
        for (DefaultNodeEnum nodeEnum : DefaultNodeEnum.values()) {
            if(!nodeEnum.iNodeExecutor.isValid()) {
                nodeNames.add(nodeEnum.getName());
            }
        }
        return nodeNames;
    }


}
