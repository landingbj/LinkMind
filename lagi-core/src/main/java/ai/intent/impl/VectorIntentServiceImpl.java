package ai.intent.impl;


import ai.embedding.Embeddings;
import ai.intent.enums.IntentStatusEnum;
import ai.intent.enums.IntentTypeEnum;
import ai.intent.pojo.IntentResult;
import ai.manager.EmbeddingManager;
import ai.medusa.utils.PromptCacheTrigger;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatMessage;
import ai.utils.ContinueWordUtil;
import ai.utils.CosineSimilarityUtil;
import ai.utils.StoppingWordUtil;
import ai.utils.qa.ChatCompletionUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.List;


@Slf4j
public class VectorIntentServiceImpl extends SampleIntentServiceImpl {


    @Override
    public IntentResult detectIntent(ChatCompletionRequest chatCompletionRequest) {
        long startTime = System.currentTimeMillis();
        IntentTypeEnum intentTypeEnum = IntentTypeEnum.TEXT;
        IntentResult intentResult = new IntentResult();
        intentResult.setType(intentTypeEnum.getName());
        intentResult.setStatus(IntentStatusEnum.COMPLETION.getName());
        intentResult.setContinuedIndex(chatCompletionRequest.getMessages().size() - 1);
        List<Integer> res = PromptCacheTrigger.analyzeChatBoundariesForIntent(chatCompletionRequest);
        if(res.size() == 1) {
            System.out.println("VectorIntentServiceImpl detectIntent took " + (System.currentTimeMillis() - startTime) + " milliseconds");
            return intentResult;
        }
        String lastQ = ChatCompletionUtil.getLastMessage(chatCompletionRequest);
        boolean isStop = StoppingWordUtil.containsStoppingWorlds(lastQ);
        if(isStop) {
            System.out.println("VectorIntentServiceImpl detectIntent took " + (System.currentTimeMillis() - startTime) + " milliseconds");
            return intentResult;
        }
        Integer lIndex = res.get(0);
        boolean isContinue = ContinueWordUtil.containsStoppingWorlds(lastQ);
        if(isContinue) {
            intentResult.setStatus(IntentStatusEnum.CONTINUE.getName());
            intentResult.setContinuedIndex(lIndex);
            System.out.println("VectorIntentServiceImpl detectIntent took " + (System.currentTimeMillis() - startTime) + " milliseconds");
            return intentResult;
        }
        setIntentByVector(chatCompletionRequest, lIndex, lastQ, intentResult);
        System.out.println("VectorIntentServiceImpl detectIntent took " + (System.currentTimeMillis() - startTime) + " milliseconds");
        return intentResult;
    }



    @Override
    protected void setIntentByVector(ChatCompletionRequest chatCompletionRequest, Integer lIndex, String lastQ, IntentResult intentResult) {
        Embeddings embeddings = EmbeddingManager.getInstance().getAdapter();
        List<Float> embedding2 = embeddings.createEmbedding(lastQ);
        for (int i = 0; i < chatCompletionRequest.getMessages().size() - 1; i++) {
            if(i < lIndex) {
                continue;
            }
            ChatMessage chatMessage = chatCompletionRequest.getMessages().get(i);
            if(!chatMessage.getRole().equals("user")) {
                continue;
            }
            List<Float> embedding1 = embeddings.createEmbedding(chatMessage.getContent());
            double v1 = CosineSimilarityUtil.calculateCosineSimilarity(embedding1, embedding2);
            log.info("lQ: {}\n lastQ: {} \n v1:{}", chatMessage.getContent(), lastQ, v1);
            if(v1 > 0.80) {
                intentResult.setStatus(IntentStatusEnum.CONTINUE.getName());
                intentResult.setContinuedIndex(lIndex);
                return;
            }
        }
        intentResult.setStatus(IntentStatusEnum.COMPLETION.getName());
    }

}
