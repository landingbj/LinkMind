package ai.medusa.utils;

import ai.common.pojo.IndexSearchData;
import ai.common.pojo.QaPair;
import ai.common.utils.ThreadPoolManager;
import ai.llm.pojo.EnhanceChatCompletionRequest;
import ai.llm.pojo.GetRagContext;
import ai.llm.service.CompletionsService;
import ai.llm.utils.CompletionUtil;
import ai.llm.utils.PriorityLock;
import ai.medusa.impl.CompletionCache;
import ai.medusa.impl.QaCache;
import ai.medusa.pojo.CacheItem;
import ai.medusa.pojo.PromptInput;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.utils.*;
import ai.utils.qa.ChatCompletionUtil;
import ai.vector.VectorDbService;
import cn.hutool.core.bean.BeanUtil;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class PromptCacheTrigger {
    private static final Logger log = LoggerFactory.getLogger(PromptCacheTrigger.class);
    private final CompletionsService completionsService = new CompletionsService();
    private final CompletionCache completionCache;
    private static volatile ThreadPoolExecutor executorService;
    private final VectorDbService vectorStoreService = new VectorDbService();
    private final LRUCache<PromptInput, List<ChatCompletionResult>> promptCache;
    private final QaCache qaCache;
    private final CachePersistence cachePersistence = CachePersistence.getInstance();
    private static final int KEEP_ALIVE_TIME = 60;

    private static final LRUCache<List<ChatMessage>, String> rawAnswerCache;

    static {
        rawAnswerCache = new LRUCache<>(PromptCacheConfig.RAW_ANSWER_CACHE_SIZE);
    }

    public PromptCacheTrigger(CompletionCache completionCache) {
        this.completionCache = completionCache;
        this.promptCache = completionCache.getPromptCache();
        this.qaCache = completionCache.getQaCache();
    }

    public PromptCacheTrigger() {
        completionCache = null;
        promptCache = null;
        qaCache = null;
    }

    public void triggerWriteCache(PromptInput promptInput, ChatCompletionResult chatCompletionResult) {
        triggerWriteCache(promptInput, chatCompletionResult, true, true);
    }

    public void triggerWriteCache(PromptInput promptInput, ChatCompletionResult chatCompletionResult, boolean needPersistent, boolean flush) {
        getExecutorService().execute(() -> writeCache(promptInput, chatCompletionResult, needPersistent, flush));
    }

    private static ThreadPoolExecutor getExecutorService() {
        ThreadPoolExecutor executor = executorService;
        if (executor != null) {
            return executor;
        }
        synchronized (PromptCacheTrigger.class) {
            if (executorService == null) {
                int corePoolSize = PromptCacheConfig.CORE_POOL_SIZE;
                int maximumPoolSize = Math.max(corePoolSize, PromptCacheConfig.MAXIMUM_POOL_SIZE);
                executorService = new ThreadPoolExecutor(
                        corePoolSize,
                        maximumPoolSize,
                        KEEP_ALIVE_TIME,
                        TimeUnit.SECONDS,
                        new ArrayBlockingQueue<>(corePoolSize * 1000)
                );
                executorService.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
            }
            return executorService;
        }
    }

    public void writeCache(PromptInput promptInput, ChatCompletionResult chatCompletionResult, boolean needPersistent, boolean flush) {
        PromptInputUtil.setApproximateTemperature(promptInput);

        String newestPrompt = PromptInputUtil.getNewestPrompt(promptInput);

        if (chatCompletionResult != null) {
            putCache(newestPrompt, promptInput, chatCompletionResult, needPersistent, flush);
            return;
        }

        PromptInput promptInputWithBoundaries = analyzeChatBoundaries(promptInput);


        chatCompletionResult = completionsWithContext(promptInputWithBoundaries);
        if (chatCompletionResult == null) {
            return;
        }

        PromptInput lastPromptInput = PromptInputUtil.getLastPromptInput(promptInputWithBoundaries);
        List<ChatCompletionResult> completionResultList = promptCache.get(lastPromptInput);
        // If the prompt list has only one prompt, add the prompt input to the cache.
        // If the prompt list has more than one prompt and the last prompt is not in the prompt cache, add the prompt to the cache.
        if (promptInputWithBoundaries.getPromptList().size() == 1 && completionResultList == null) {
            putCache(newestPrompt, promptInputWithBoundaries, chatCompletionResult, needPersistent, flush);
            return;
        }

        // If the completionResultList size does not match the promptInput size, return.
        if (completionResultList == null) {
            return;
        }

        // If the prompt list has more than one prompt and the last prompt is in the prompt cache, append the prompt to the cache.
        putCache(promptInputWithBoundaries, lastPromptInput, chatCompletionResult, newestPrompt, needPersistent, flush);
    }

    private synchronized void putCache(PromptInput promptInputWithBoundaries, PromptInput lastPromptInput,
                                       ChatCompletionResult chatCompletionResult, String newestPrompt, boolean needPersistent, boolean flush) {
        String lastPrompt = PromptInputUtil.getLastPrompt(promptInputWithBoundaries);
        List<PromptInput> promptInputList = qaCache.get(lastPrompt);
        if (promptInputList == null || promptInputList.isEmpty()) {
            return;
        }
//        int index = promptInputList.indexOf(lastPromptInput);
//        PromptInput promptInputInCache = promptInputList.get(index);
        PromptInput promptInputInCache = promptInputList.get(0);
        List<ChatCompletionResult> completionResults = promptCache.get(promptInputInCache);
        if (completionResults == null) {
            return;
        }
        completionResults = new ArrayList<>(completionResults);
        completionResults.add(chatCompletionResult);
//        promptCache.remove(promptInputInCache);

        List<String> promptList = new ArrayList<>(promptInputInCache.getPromptList());
        promptList.add(newestPrompt);
        PromptInput newPromptInput = PromptInput.builder().promptList(promptList)
                .parameter(promptInputInCache.getParameter()).build();

//        promptInputInCache.getPromptList().add(newestPrompt);
        List<PromptInput> promptInputs = qaCache.get(newestPrompt);
        if(promptInputs == null) {
            promptInputs = new ArrayList<>();
        }
        promptInputs.add(newPromptInput);
//        qaCache.put(newestPrompt, promptInputList);
        qaCache.put(newestPrompt, promptInputs, flush);
        promptCache.put(newPromptInput, completionResults);
        if (needPersistent) {
            cachePersistence.addItem(new CacheItem(newPromptInput, chatCompletionResult));
        }
        if (flush) {
            log.debug("current cache size {}, putCache2: {}", promptCache.size(), promptInputs);
        }
    }

    private synchronized void putCache(String newestPrompt, PromptInput promptInputWithBoundaries,
                                       ChatCompletionResult chatCompletionResult, boolean needPersistent, boolean flush) {
        List<PromptInput> promptInputList = qaCache.get(newestPrompt);

        PromptInput lastPromptInput = PromptInputUtil.getLastPromptInput(promptInputWithBoundaries);
        List<ChatCompletionResult> completionResults = promptCache.get(lastPromptInput);
        if (promptInputWithBoundaries.getPromptList().size() == 1 && completionResults == null) {
           completionResults = promptCache.get(promptInputWithBoundaries);
        } else {
            putCache(promptInputWithBoundaries, lastPromptInput, chatCompletionResult, newestPrompt, needPersistent, flush);
            return;
        }

        if (promptInputList == null || promptInputList.isEmpty()) {
            promptInputList = new ArrayList<>();
        }

        if (completionResults == null || completionResults.isEmpty()) {
            completionResults = new ArrayList<>();
        }

        promptInputList.add(promptInputWithBoundaries);
        completionResults.add(chatCompletionResult);

        qaCache.put(newestPrompt, promptInputList, flush);
        promptCache.put(promptInputWithBoundaries, completionResults);
        if (needPersistent) {
            cachePersistence.addItem(new CacheItem(promptInputWithBoundaries, chatCompletionResult));
        }
        if (flush) {
            log.debug("current cache size {}, putCache1: {}", promptCache.size(), promptInputList);
        }
    }

    public PromptInput analyzeChatBoundaries(PromptInput promptInput) {
        List<String> questionList = promptInput.getPromptList();
        if (questionList.size() < 2) {
            return promptInput;
        }
//        String pattern = "[ \n\\.,;!\\?，。；！？#\\*：:-]";
        List<String> answerList = getRawAnswer(questionList);
//        questionList = questionList.stream().map(q->q.replaceAll(pattern, "")).collect(Collectors.toList());
//        answerList = answerList.stream().map(q->{
//            String s = q.replaceAll(pattern, "");
//            return s.substring(0, PromptCacheConfig.TRUNCATE_LENGTH);
//        }).collect(Collectors.toList());
//        int startIndex = 0;
//        String startQ = questionList.get(startIndex);
//        String startA = answerList.get(startIndex);
//        Set<String> startCoreSet = LCS.findLongestCommonSubstrings(startQ, startA, PromptCacheConfig.START_CORE_THRESHOLD);
//        Set<String> continuousSet = null;
//        for (int i = 1; i < questionList.size(); i++) {
//            String curQ = questionList.get(i);
//            String curA = answerList.get(i);
//            String lastA = answerList.get(i-1);
//            Set<String> sameCoreSet = LCS.findLongestCommonSubstrings(startQ, curA, PromptCacheConfig.ANSWER_CORE_THRESHOLD);
//            if(continuousSet == null) {
//                continuousSet = LCS.findLongestCommonSubstrings(curA, lastA, PromptCacheConfig.ANSWER_CORE_THRESHOLD);
//                Set<String> finalStartCoreSet = startCoreSet;
//                continuousSet = continuousSet.stream().filter(s->{
//                    long count = finalStartCoreSet.stream().filter(c -> c.contains(s)).count();
//                    return count > 0;
//                }).collect(Collectors.toSet());
//            } else {
//                Set<String> aaSet = LCS.findLongestCommonSubstrings(curA, lastA, PromptCacheConfig.ANSWER_CORE_THRESHOLD);
//                continuousSet = continuousSet.stream().filter(s->{
//                    long count = aaSet.stream().filter(c -> c.contains(s)).count();
//                    return count > 0;
//                }).collect(Collectors.toSet());
//            }
//            double sameRadio = LCS.getLcsRatio(startQ, sameCoreSet);
//            double conRadio = LCS.getLcsRatio(curA, continuousSet);
//            double radio = (double) PromptCacheConfig.ANSWER_CORE_THRESHOLD / curA.length();
//            if( sameRadio < LCS_RATIO_QUESTION && conRadio < radio) {
//                startIndex = i;
//                startQ = questionList.get(startIndex);
//                startA = answerList.get(startIndex);
//                startCoreSet = LCS.findLongestCommonSubstrings(startQ, startA, PromptCacheConfig.START_CORE_THRESHOLD);
//                continuousSet = null;
//            }
//        }
        List<QaPair> qaPairs = convert2QaPair(questionList, answerList);
        List<List<QaPair>> splitQaPairs = splitQaPairBySemantics(qaPairs);
        int startIndex = 0;
        if(!splitQaPairs.isEmpty() && !splitQaPairs.get(splitQaPairs.size() -1).isEmpty()) {
            startIndex = splitQaPairs.get(splitQaPairs.size() -1).get(0).getQIndex();
        }
        return PromptInput.builder()
                .parameter(promptInput.getParameter())
                .promptList(promptInput.getPromptList().subList(startIndex, promptInput.getPromptList().size()))
                .build();
    }



    public static List<Integer> analyzeChatBoundariesForIntent(ChatCompletionRequest chatCompletionRequest) {
        return theFinalRoundOfConversation(chatCompletionRequest.getMessages());
    }

    public static List<Integer> theFinalRoundOfConversation(List<ChatMessage> chatMessages) {
        List<QaPair> qaPairs = convert2QaPair(chatMessages, 30);
        if (qaPairs.isEmpty()) {
            return new ArrayList<>();
        }
        List<List<QaPair>> splitQaPairs = splitQaPairBySemantics(qaPairs);
        List<QaPair> lastQaPairs;
        if (splitQaPairs.isEmpty() || splitQaPairs.get(splitQaPairs.size() - 1).isEmpty()) {
            lastQaPairs = qaPairs;
        } else {
            lastQaPairs = splitQaPairs.get(splitQaPairs.size() - 1);
        }
        QaPair firstQaPair = lastQaPairs.get(0);
        QaPair lastQaPair = lastQaPairs.get(lastQaPairs.size() - 1);

        int startIndex = firstQaPair.getQIndex();
        int endIndex = lastQaPair.getAIndex();
        return Lists.newArrayList(startIndex, endIndex + 1);
    }


    private static List<QaPair> convert2QaPair(List<String> questionList, List<String> answerList) {
        List<QaPair> qaPairs = new ArrayList<>();
        for (int i = 0; i < questionList.size(); i++) {
            String aa = answerList.get(i).trim();
            aa = StrFilterUtil.filterPunctuations(aa);
            int min = Math.min(aa.length(), 50);
            aa = aa.substring(0, min);
            String qq = questionList.get(i).trim();
            qq = StrFilterUtil.filterPunctuations(qq);
            QaPair qa = QaPair.builder().a(aa).aIndex(i).q(qq).qIndex(i).build();
            qaPairs.add(qa);
        }
        return qaPairs;
    }

    private static List<QaPair> convert2QaPair(List<ChatMessage> chatMessages, int deep) {
        List<QaPair> qaPairs = new ArrayList<>();
        if (chatMessages == null || chatMessages.size() < 2 || deep <= 0) {
            return qaPairs;
        }

        int count = 0;
        int currentIndex = chatMessages.size() - 1;

        while (currentIndex > 0 && count < deep) {
            Integer assistantIndex = findValidAssistantIndex(chatMessages, currentIndex);
            if (assistantIndex == null) {
                break;
            }

            Integer userIndex = findValidUserIndex(chatMessages, assistantIndex - 1);
            if (userIndex == null) {
                currentIndex = assistantIndex - 1;
                continue;
            }

            ChatMessage assistantMsg = chatMessages.get(assistantIndex);
            ChatMessage userMsg = chatMessages.get(userIndex);

            String answer = assistantMsg.getContent().trim();
            answer = StrFilterUtil.filterPunctuations(answer);
            answer = answer.substring(0, Math.min(answer.length(), 50));

            String question = userMsg.getContent().trim();
            question = StrFilterUtil.filterPunctuations(question);

            QaPair qaPair = QaPair.builder()
                    .a(answer)
                    .aIndex(assistantIndex)
                    .q(question)
                    .qIndex(userIndex)
                    .build();
            qaPairs.add(qaPair);

            count++;
            currentIndex = userIndex - 1;
        }

        Collections.reverse(qaPairs);
        return qaPairs;
    }

    public static Integer findValidAssistantIndex(List<ChatMessage> messages, int startIndex) {
        for (int i = startIndex; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (LagiGlobal.LLM_ROLE_TOOL.equals(msg.getRole())
                    || msg.getTool_calls() != null
                    || msg.getContent() == null
                    || msg.getContent().trim().isEmpty()) {
                continue;
            }
            if (LagiGlobal.LLM_ROLE_ASSISTANT.equals(msg.getRole())) {
                return i;
            }
        }
        return null;
    }


    private static Integer findValidUserIndex(List<ChatMessage> messages, int startIndex) {
        for (int i = startIndex; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (LagiGlobal.LLM_ROLE_TOOL.equals(msg.getRole())
                    || msg.getTool_calls() != null
                    || msg.getContent() == null
                    || msg.getContent().trim().isEmpty()) {
                continue;
            }
            if (LagiGlobal.LLM_ROLE_USER.equals(msg.getRole())) {
                return i;
            }
        }
        return null;
    }

    private static List<List<QaPair>> splitQaPairBySemantics(List<QaPair> qaPairs) {
        Set<String> qaCore = new HashSet<>();
        List<List<QaPair>> dialogPairs = new ArrayList<>();
        List<QaPair> curDialog = new ArrayList<>();
        dialogPairs.add(curDialog);
        for (int i = 0; i < qaPairs.size(); i++) {
            QaPair qaPair = qaPairs.get(i);
            if(StoppingWordUtil.containsStoppingWorlds(qaPair.getQ())) {
                if(curDialog.isEmpty()) {
                    curDialog.add(qaPair);
                } else {
                    curDialog = new ArrayList<>();
                    curDialog.add(qaPair);
                    dialogPairs.add(curDialog);
                }
                qaCore = LCS.findLongestCommonSubstrings(qaPair.getQ(), qaPair.getA(), PromptCacheConfig.START_CORE_THRESHOLD);
                continue;
            }
            if(ContinueWordUtil.containsStoppingWorlds(qaPair.getQ())) {
                curDialog.add(qaPair);
                continue;
            }
            if(curDialog.isEmpty()) {
                curDialog.add(qaPair);
                qaCore = LCS.findLongestCommonSubstrings(qaPair.getQ(), qaPair.getA(), PromptCacheConfig.START_CORE_THRESHOLD);
                continue;
            }
            String lastQ = curDialog.get(0).getQ();
            String curA = qaPair.getA();
            Set<String> QAnCore = LCS.findLongestCommonSubstrings(lastQ, curA, PromptCacheConfig.ANSWER_CORE_THRESHOLD);
            Set<String> retainAll = setRetainAll(qaCore, QAnCore);
            double ratio = LCS.getLcsRatio(curA, retainAll);
            double threshold = (double) PromptCacheConfig.ANSWER_CORE_THRESHOLD /  qaPair.getA().length();
            if(ratio < threshold) {
                curDialog = new ArrayList<>();
                qaCore = LCS.findLongestCommonSubstrings(qaPair.getQ(), qaPair.getA(), PromptCacheConfig.START_CORE_THRESHOLD);
                curDialog.add(qaPair);
                dialogPairs.add(curDialog);
            } else {
                curDialog.add(qaPair);
            }
        }
        return dialogPairs;
    }

    private static  Set<String> setRetainAll(Set<String> tempCore, Set<String> core) {
        Set<String> temp = new HashSet<>();
        tempCore = tempCore.stream().map(RetainWordUtil::replace).collect(Collectors.toSet());
        core = core.stream().map(RetainWordUtil::replace).collect(Collectors.toSet());
        for(String tempCoreStr: tempCore) {
            for (String coreStr: core) {
                String longStr = tempCoreStr.length() > coreStr.length() ? tempCoreStr : coreStr;
                String shortStr = tempCoreStr.length() > coreStr.length() ? coreStr : tempCoreStr;
                if(longStr.contains(shortStr)) {
                    if(!RetainWordUtil.contains(coreStr)) {
                        temp.add(coreStr);
                    }
                }
            }
        }
        return temp;
    }


    private List<String> getRawAnswer(List<String> questionList) {
        List<ChatMessage> messages = new ArrayList<>();
        List<String> answerList = new ArrayList<>();
        for (String question : questionList) {
            messages.add(completionsService.getChatMessage(question, LagiGlobal.LLM_ROLE_USER));
            String answer = completions(new ArrayList<>(messages));
            messages.add(completionsService.getChatMessage(answer, LagiGlobal.LLM_ROLE_ASSISTANT));
            answerList.add(answer);
        }
        return answerList;
    }

    private ChatCompletionResult completionsWithContext(PromptInput promptInput) {
        String lastPrompt = PromptInputUtil.getNewestPrompt(promptInput);
        String text = lastPrompt;
        if(promptInput.getPromptList().size() > 1) {
            String firstPrompt = PromptInputUtil.getFirstPrompt(promptInput);
            text  = firstPrompt + "," +text;
        }
//        String text = String.join(";", promptInput.getPromptList());
        ChatCompletionRequest request = completionsService.getCompletionsRequest(
                promptInput.getParameter().getSystemPrompt(), lastPrompt, promptInput.getParameter().getCategory());
        List<IndexSearchData> indexSearchDataList = vectorStoreService.searchByContext(request);
        GetRagContext ragContext = completionsService.getRagContext(indexSearchDataList);
        String context = null;
        if(ragContext != null) {
            context = ragContext.getContext();
        }
        if (context != null) {
            completionsService.addVectorDBContext(request, context);
        }

        EnhanceChatCompletionRequest chatCompletionRequest = new EnhanceChatCompletionRequest();
        BeanUtil.copyProperties(request, chatCompletionRequest);
        chatCompletionRequest.setPriority(PriorityLock.LOW_PRIORITY);

        ChatCompletionResult result = completionsService.completions(chatCompletionRequest);
        CompletionUtil.populateContext(result, indexSearchDataList, context);
        return result;
    }

    private void delay(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
    }

    private String completions(List<ChatMessage> messages) {
        String answer = rawAnswerCache.get(messages);
        if (answer == null) {
            ChatCompletionRequest request = completionsService.getCompletionsRequest(messages);
            EnhanceChatCompletionRequest chatCompletionRequest = new EnhanceChatCompletionRequest();
            BeanUtil.copyProperties(request, chatCompletionRequest);
            chatCompletionRequest.setPriority(PriorityLock.LOW_PRIORITY);
            ChatCompletionResult result = completionsService.completions(chatCompletionRequest);
            answer = ChatCompletionUtil.getFirstAnswer(result);
            rawAnswerCache.put(messages, answer);
            delay(PromptCacheConfig.getPreDelay());
        }
        return answer;
    }


    public static void main(String[] args) {
//        ContextLoader.loadContext();
//        PromptInput promptInput = new PromptCacheTrigger(CompletionCache.getInstance()).analyzeChatBoundaries(null);
//        System.out.println(promptInput);
    }
}
