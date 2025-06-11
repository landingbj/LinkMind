package ai.intent.mapper;

import ai.agent.Agent;
import ai.intent.IntentGlobal;
import ai.intent.pojo.IntentDetectParam;
import ai.intent.pojo.IntentDetectResult;
import ai.mr.IMapper;
import ai.mr.mapper.BaseMapper;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.qa.AiGlobalQA;
import ai.router.pojo.LLmRequest;
import ai.utils.qa.ChatCompletionUtil;
import ai.worker.skillMap.SkillMapUtil;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;


@Setter
@Slf4j
public class PickAgentByDescribeMapper extends BaseMapper implements IMapper {
    private static final Double priority = 1d;
    @Override
    public List<?> myMapping() {
        List<Object> result = new ArrayList<>();
        IntentDetectParam param = (IntentDetectParam) this.getParameters().get(IntentGlobal.MAPPER_INTENT_PARAM);

        LLmRequest llmRequest = param.getLlmRequest();
        List<Agent<ChatCompletionRequest, ChatCompletionResult>> allAgents = param.getAllAgents();

        log.info("PickAgentByDescribeMapper llmRequest: {}", llmRequest);

        List<Agent<ChatCompletionRequest, ChatCompletionResult>> pickAgentList = null;
        Future<List<Agent<ChatCompletionRequest, ChatCompletionResult>>> future = SkillMapUtil.asyncPickAgentByDescribe(ChatCompletionUtil.getLastMessage(llmRequest), allAgents);
        try {
            pickAgentList = future.get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("pick up agent error", e);
        }

        IntentDetectResult intentResult = new IntentDetectResult();
        intentResult.setAgents(pickAgentList);
        result.add(AiGlobalQA.M_LIST_RESULT_TEXT, intentResult);
        result.add(AiGlobalQA.M_LIST_RESULT_PRIORITY, priority);

        log.info("PickAgentByDescribeMapper intentResult: {}", intentResult);

        return result;
    }
}
