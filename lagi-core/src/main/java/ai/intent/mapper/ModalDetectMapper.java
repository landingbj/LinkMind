package ai.intent.mapper;

import ai.intent.IntentGlobal;
import ai.intent.IntentService;
import ai.intent.impl.SampleIntentServiceImpl;
import ai.intent.pojo.IntentDetectParam;
import ai.intent.pojo.IntentDetectResult;
import ai.intent.pojo.IntentResult;
import ai.llm.utils.SummaryUtil;
import ai.mr.IMapper;
import ai.mr.mapper.BaseMapper;
import ai.qa.AiGlobalQA;
import ai.router.pojo.LLmRequest;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;

import java.util.ArrayList;
import java.util.List;


@Setter
@Slf4j
public class ModalDetectMapper extends BaseMapper implements IMapper {
    private static final Double priority = 50d;
    private IntentService sampleIntentService = new SampleIntentServiceImpl();

    @Override
    public List<?> myMapping() {
        List<Object> result = new ArrayList<>();
        IntentDetectParam param = (IntentDetectParam) this.getParameters().get(IntentGlobal.MAPPER_INTENT_PARAM);
        LLmRequest llmRequest = param.getLlmRequest();
        String invoke = param.getInvoke();
        llmRequest = SerializationUtils.clone(llmRequest);
        if (invoke != null && !invoke.isEmpty()) {
            SummaryUtil.setInvoke(llmRequest, invoke);
        }
        IntentResult intentModal = sampleIntentService.detectIntent(llmRequest);
        IntentDetectResult intentResult = new IntentDetectResult();
        intentResult.setModal(intentModal);
        result.add(AiGlobalQA.M_LIST_RESULT_TEXT, intentResult);
        result.add(AiGlobalQA.M_LIST_RESULT_PRIORITY, priority);
        return result;
    }
}
