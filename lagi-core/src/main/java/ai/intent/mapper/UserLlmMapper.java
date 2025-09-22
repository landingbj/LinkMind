package ai.intent.mapper;

import ai.agent.Agent;
import ai.intent.IntentGlobal;
import ai.intent.pojo.IntentDetectParam;
import ai.intent.pojo.IntentDetectResult;
import ai.llm.adapter.ILlmAdapter;
import ai.mr.IMapper;
import ai.mr.mapper.BaseMapper;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.qa.AiGlobalQA;
import ai.worker.skillMap.SkillMapUtil;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;


@Setter
@Slf4j
public class UserLlmMapper extends BaseMapper implements IMapper {
    private static final Double priority = 50d;

    @Override
    public List<?> myMapping() {
        List<Object> result = new ArrayList<>();
        IntentDetectParam param = (IntentDetectParam) this.getParameters().get(IntentGlobal.MAPPER_INTENT_PARAM);

        List<ILlmAdapter> userLlmAdapters = param.getUserLlmAdapters();
        List<Agent<ChatCompletionRequest, ChatCompletionResult>> llmAndAgentList = SkillMapUtil.convert2AgentList(userLlmAdapters);

        IntentDetectResult intentResult = new IntentDetectResult();
        intentResult.setAgents(llmAndAgentList);
        result.add(AiGlobalQA.M_LIST_RESULT_TEXT, intentResult);
        result.add(AiGlobalQA.M_LIST_RESULT_PRIORITY, priority);
        return result;
    }
}
