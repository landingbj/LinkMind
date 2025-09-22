package ai.intent.mapper;

import ai.agent.Agent;
import ai.intent.IntentGlobal;
import ai.intent.pojo.IntentDetectParam;
import ai.intent.pojo.IntentDetectResult;
import ai.llm.utils.SummaryUtil;
import ai.mr.IMapper;
import ai.mr.mapper.BaseMapper;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.qa.AiGlobalQA;
import ai.router.pojo.LLmRequest;
import ai.utils.LRUCache;
import ai.utils.qa.ChatCompletionUtil;
import ai.worker.pojo.IntentResponse;
import ai.worker.skillMap.SkillMapUtil;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;

import java.util.ArrayList;
import java.util.List;


@Setter
@Slf4j
public class RankAgentByKeywordMapper extends BaseMapper implements IMapper {
    private static final Double priority = 100d;
    private static final LRUCache<LLmRequest, IntentResponse> intentKeywordCache = IntentGlobal.INTENT_KEYWORD_CACHE;

    @Override
    public List<?> myMapping() {
        List<Object> result = new ArrayList<>();
        IntentDetectParam param = (IntentDetectParam) this.getParameters().get(IntentGlobal.MAPPER_INTENT_PARAM);

        LLmRequest llmRequest = param.getLlmRequest();
        List<Agent<ChatCompletionRequest, ChatCompletionResult>> allAgents = param.getAllAgents();
        String invoke = param.getInvoke();
        llmRequest = SerializationUtils.clone(llmRequest);
        if (invoke != null && !invoke.isEmpty()) {
            SummaryUtil.setInvoke(llmRequest, invoke);
        }

        IntentResponse intentDetect = intentKeywordCache.get(llmRequest);
        if (intentDetect == null) {
            String userInput = ChatCompletionUtil.getLastMessage(llmRequest);
            intentDetect = SkillMapUtil.intentDetect(userInput);
            if (intentDetect != null) {
                intentKeywordCache.put(llmRequest, intentDetect);
            }
        }

        List<Agent<ChatCompletionRequest, ChatCompletionResult>> skillMapAgentList = SkillMapUtil.rankAgentByIntentKeyword(allAgents, intentDetect);
        IntentDetectResult intentResult = new IntentDetectResult();
        intentResult.setAgents(skillMapAgentList);
        result.add(AiGlobalQA.M_LIST_RESULT_TEXT, intentResult);
        result.add(AiGlobalQA.M_LIST_RESULT_PRIORITY, priority);
        return result;
    }
}
