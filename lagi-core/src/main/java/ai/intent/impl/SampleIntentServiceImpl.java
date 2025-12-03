package ai.intent.impl;


import ai.common.pojo.IndexSearchData;
import ai.common.pojo.VectorStoreConfig;
import ai.common.utils.ThreadPoolManager;
import ai.intent.IntentService;
import ai.intent.enums.IntentStatusEnum;
import ai.intent.enums.IntentTypeEnum;
import ai.intent.pojo.IntentResult;
import ai.medusa.utils.PromptCacheTrigger;
import ai.openai.pojo.ChatCompletionRequest;
import ai.utils.ContinueWordUtil;
import ai.utils.StoppingWordUtil;
import ai.utils.StrFilterUtil;
import ai.utils.qa.ChatCompletionUtil;
import ai.vector.VectorStoreService;
import ai.vector.pojo.MultiQueryCondition;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;


@Slf4j
public class SampleIntentServiceImpl implements IntentService {
    private static final String punctuations = "[\\.,;!\\?，。；！？]";
    private static final ExecutorService executor;

    static {
        ThreadPoolManager.registerExecutor("vector_intent");
        executor = ThreadPoolManager.getExecutor("vector_intent");
    }

    private List<String> splitByPunctuation(String content) {
        String[] split = content.split(punctuations);
        return Arrays.stream(split).filter(StrUtil::isNotBlank).collect(Collectors.toList());
    }

    private IntentTypeEnum detectType(ChatCompletionRequest chatCompletionRequest) {
        String lastMessage = ChatCompletionUtil.getLastMessage(chatCompletionRequest);
        List<String> segments = splitByPunctuation(lastMessage);
        IntentTypeEnum[] enums = IntentTypeEnum.values();
        for (IntentTypeEnum e : enums) {
            if (e.matches(lastMessage, segments)) {
                return e;
            }
        }
        return IntentTypeEnum.TEXT;
    }

    @Override
    public IntentResult detectIntent(ChatCompletionRequest chatCompletionRequest, Map<String, Object> where) {
        IntentTypeEnum intentTypeEnum = detectType(chatCompletionRequest);
        IntentResult intentResult = new IntentResult();
        intentResult.setType(intentTypeEnum.getName());
        if (intentTypeEnum != IntentTypeEnum.TEXT
                || chatCompletionRequest.getMax_tokens() <= 0) {
            return intentResult;
        }
        intentResult.setStatus(IntentStatusEnum.COMPLETION.getName());
        List<Integer> res = PromptCacheTrigger.analyzeChatBoundariesForIntent(chatCompletionRequest);
        if (res.size() == 1) {
            return intentResult;
        }
        String lastQ = ChatCompletionUtil.getLastMessage(chatCompletionRequest);
        boolean isStop = StoppingWordUtil.containsStoppingWorlds(lastQ);
        if (isStop) {
            return intentResult;
        }
        Integer lIndex = res.get(0);
        boolean isContinue = ContinueWordUtil.containsStoppingWorlds(lastQ);
        if (isContinue) {
            intentResult.setStatus(IntentStatusEnum.CONTINUE.getName());
            intentResult.setContinuedIndex(lIndex);
            return intentResult;
        }
        setIntentByVector(chatCompletionRequest, lIndex, lastQ, intentResult, where);
        return intentResult;
    }

    @Override
    public IntentResult detectIntent(ChatCompletionRequest chatCompletionRequest) {
        return detectIntent(chatCompletionRequest, null);
    }

    private static void setIntentByVector(ChatCompletionRequest chatCompletionRequest, Integer lIndex, String lastQ, IntentResult intentResult, Map<String, Object> where) {
        VectorStoreService vectorStoreService = new VectorStoreService();
        String lQ = chatCompletionRequest.getMessages().get(lIndex).getContent();
        String complexQ = lQ + lastQ;
        lastQ = StrFilterUtil.filterPunctuations(lastQ);
        complexQ = StrFilterUtil.filterPunctuations(complexQ);
        String finalLastQ = lastQ;
        String finalComplexQ = complexQ;

        VectorStoreConfig vectorStoreConfig = vectorStoreService.getVectorStoreConfig();
        List<String> texts = new ArrayList<>();
        texts.add(finalLastQ);
        texts.add(finalComplexQ);
        MultiQueryCondition multiQueryCondition = MultiQueryCondition.builder()
                .category(chatCompletionRequest.getCategory())
                .texts(texts)
                .where(where)
                .n(vectorStoreConfig.getSimilarityTopK())
                .build();
        
        List<List<IndexSearchData>> indexSearchDataList = vectorStoreService.search(multiQueryCondition);

        try {
            List<IndexSearchData> l = indexSearchDataList.get(0);
            List<IndexSearchData> c = indexSearchDataList.get(1);
            boolean vectorContinue = false;
            if (!l.isEmpty() && !c.isEmpty()) {
                if (c.get(0).getDistance() < l.get(0).getDistance()) {
                    vectorContinue = true;
                }
            } else if (!l.isEmpty()) {
                vectorContinue = true;
            }
            if (vectorContinue) {
                intentResult.setStatus(IntentStatusEnum.CONTINUE.getName());
                intentResult.setContinuedIndex(lIndex);
                intentResult.setIndexSearchDataList(c);
            } else {
                intentResult.setIndexSearchDataList(l);
            }
        } catch (Exception e) {
            log.error("detectIntent error", e);
        }
    }
}
