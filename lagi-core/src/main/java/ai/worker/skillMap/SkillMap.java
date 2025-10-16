package ai.worker.skillMap;

import ai.agent.Agent;
import ai.common.exception.RRException;
import ai.common.utils.LRUCache;
import ai.common.utils.ThreadPoolManager;
import ai.config.pojo.AgentConfig;
import ai.database.DatabaseGlobal;
import ai.keywords.SubstitutionUtil;
import ai.learn.questionAnswer.KShingle;
import ai.manager.DatabaseManager;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.utils.JsonExtractor;
import ai.utils.LlmUtil;
import ai.utils.qa.ChatCompletionUtil;
import ai.worker.pojo.AgentIntentScore;
import ai.worker.pojo.IntentResponse;
import ai.worker.pojo.ScoreResponse;
import ai.worker.skillMap.db.AgentInfoDao;
import ai.worker.skillMap.db.AgentScoreDao;
import ai.worker.skillMap.prompt.SkillMapPrompt;
import cn.hutool.core.util.StrUtil;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class SkillMap {


    private static final ExecutorService executorService;

    private static final LRUCache<String, List<AgentIntentScore>> cachedSkillMap = new LRUCache<>(1000, 5L, TimeUnit.MINUTES);

    private static final ConcurrentHashMap<String, ThreadLocal<Object>> lockMap = new ConcurrentHashMap<>();

    private static final int MAX_RAG_AGENT_ID = -10000;
    private static final int MIN_RAG_AGENT_ID = -20000;

    private static final int maxTry = 3;

    private final AgentScoreDao agentScoreDao = new AgentScoreDao();

    private final AgentInfoDao agentInfoDao = new AgentInfoDao();

    public static Object getLockObject(String key) {
        return lockMap.computeIfAbsent(key, k -> ThreadLocal.withInitial(Object::new)).get();
    }

    public static void removeLockObject(String key) {
        lockMap.remove(key);
    }


    private final Gson gson = new Gson();


    static {
        ThreadPoolManager.registerExecutor("skill-map");
        executorService= ThreadPoolManager.getExecutor("skill-map");
    }

    public List<AgentIntentScore> getAgentScore(List<String> keywords) {
        List<AgentIntentScore> agentIntentScores = new ArrayList<>();
        for (String keyword : keywords) {
            List<AgentIntentScore> agentScore = getAgentScore(keyword);
            agentIntentScores.addAll(agentScore);
        }
        return agentIntentScores;
    }

    private List<AgentIntentScore> getAgentScore(String keyword) {
        List<AgentIntentScore> agentIntentScoresByKeyword = cachedSkillMap.get(keyword);
        if (agentIntentScoresByKeyword != null && !agentIntentScoresByKeyword.isEmpty()) {
            return agentIntentScoresByKeyword;
        } else {
            List<AgentIntentScore>  fromSQLite = Collections.emptyList();
            synchronized (getLockObject(keyword)) {
                try {
                    fromSQLite = agentScoreDao.getAgentScore(keyword);
                    cachedSkillMap.put(keyword, fromSQLite);
                } catch (Exception ignored) {
                }
            }
            removeLockObject(keyword);
            return fromSQLite;
        }
    }

    public List<Agent<ChatCompletionRequest, ChatCompletionResult>> rankAgentByIntentKeyword(
            List<Agent<ChatCompletionRequest, ChatCompletionResult>> agentList,
            IntentResponse intentResponse, double edge) {
        if (intentResponse == null || intentResponse.getKeywords() == null || intentResponse.getKeywords().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> keywords = intentResponse.getKeywords();
        List<AgentIntentScore> agentScores = getAgentIntentScoreByIntentKeyword(keywords);

        if (agentScores == null || agentScores.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Integer, AgentIntentScore> scoreMap = agentScores.stream()
                .collect(Collectors.toMap(AgentIntentScore::getAgentId, a -> a));
        List<Agent<ChatCompletionRequest, ChatCompletionResult>> highScoreAgents = agentList.stream()
                .filter(agent -> {
                    if (!agent.getAgentConfig().getCanOutPut()) {
                        return false;
                    }
                    AgentIntentScore agentIntentScore = scoreMap.get(agent.getAgentConfig().getId());
                    return agentIntentScore != null && agentIntentScore.getScore() > edge;
                })
                .sorted(Comparator
                        .comparing((Agent<ChatCompletionRequest, ChatCompletionResult> agent) ->
                                scoreMap.get(agent.getAgentConfig().getId()).getScore())
                        .reversed())
                .collect(Collectors.toList());
        return highScoreAgents;
    }

    public List<Agent<ChatCompletionRequest, ChatCompletionResult>> rankAgentByIntentKeyword(
            List<Agent<ChatCompletionRequest, ChatCompletionResult>> agentList,
            String question, double edge) {
        IntentResponse intentResponse = getSafeIntentResponse(question);
        return rankAgentByIntentKeyword(agentList, intentResponse, edge);
    }

    public List<Agent<ChatCompletionRequest, ChatCompletionResult>> filterAgentByIntentKeyword(
            List<Agent<ChatCompletionRequest, ChatCompletionResult>> agentList,
            String question, Double edge) {
        if (edge == null || edge < 0) {
            throw new IllegalArgumentException("Edge must be a non-negative number");
        }

        IntentResponse intentResponse = getSafeIntentResponse(question);
        if (intentResponse == null || intentResponse.getKeywords() == null || intentResponse.getKeywords().isEmpty()) {
            return agentList;
        }

        List<String> keywords = intentResponse.getKeywords();
        List<AgentIntentScore> agentScores = getAgentIntentScoreByIntentKeyword(keywords);
        Set<Integer> agentIdsByKeyword = getSafeAgentIdsByKeyword(keywords);

        if (agentScores == null || agentScores.isEmpty()) {
            return filterAgentsNotInSet(agentList, agentIdsByKeyword);
        }

        Map<Integer, AgentIntentScore> scoreMap = agentScores.stream()
                .collect(Collectors.toMap(AgentIntentScore::getAgentId, a -> a));

        List<Agent<ChatCompletionRequest, ChatCompletionResult>> highScoreAgents = agentList.stream()
                .filter(agent -> {
                    AgentIntentScore agentIntentScore = scoreMap.get(agent.getAgentConfig().getId());
                    return agentIntentScore != null && agentIntentScore.getScore() > edge;
                })
                .collect(Collectors.toList());

        if (highScoreAgents.isEmpty()) {
            return filterAgentsNotInSet(agentList, agentIdsByKeyword);
        }

        List<Agent<ChatCompletionRequest, ChatCompletionResult>> notTryAgents = filterAgentsNotInSet(agentList, agentIdsByKeyword);
        highScoreAgents.addAll(notTryAgents);
        return highScoreAgents;
    }

    public IntentResponse getSafeIntentResponse(String question) {
        try {
            return intentDetect(question);
        } catch (Exception e) {
            return null;
        }
    }

    private Set<Integer> getSafeAgentIdsByKeyword(List<String> keywords) {
        try {
            return agentScoreDao.getAgentIdsByKeyword(keywords);
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    private List<Agent<ChatCompletionRequest, ChatCompletionResult>> filterAgentsNotInSet(
            List<Agent<ChatCompletionRequest, ChatCompletionResult>> agentList, Set<Integer> agentIdsByKeyword) {
        return agentList.stream()
                .filter(agent -> !agentIdsByKeyword.contains(agent.getAgentConfig().getId()))
                .collect(Collectors.toList());
    }

    public List<AgentIntentScore> getAgentIntentScoreByIntentKeyword(List<String> keywords) {
        if(keywords == null || keywords.isEmpty()) {
            return Collections.emptyList();
        }
        List<AgentIntentScore> agentIntentScores = getAgentScore(keywords);
        return combineAgentScore(agentIntentScores, keywords.size());
    }


    private static List<AgentIntentScore> combineAgentScore(List<AgentIntentScore> agentIntentScores, int size) {
        Map<String, AgentIntentScore> agentScoreMap = new HashMap<>();
        for (AgentIntentScore agentIntentScore : agentIntentScores) {
            String agentName = agentIntentScore.getAgentName();
            AgentIntentScore cached = agentScoreMap.get(agentName);
            if(cached == null) {
                agentScoreMap.put(agentName, AgentIntentScore.builder().agentId(agentIntentScore.getAgentId())
                        .agentName(agentIntentScore.getAgentName()).score(agentIntentScore.getScore()).build());
            } else {
                cached.setScore(cached.getScore() + agentIntentScore.getScore());
            }
        }
        agentIntentScores = agentScoreMap.values().stream()
                .peek(a -> a.setScore(a.getScore()/ size))
                .filter(a -> a.getScore() > 6)
                .sorted(Comparator.comparingDouble(AgentIntentScore::getScore).reversed())
                .collect(Collectors.toList());
        return agentIntentScores;
    }

    // 获取 agent 评分
    public AgentIntentScore agentIntentScore(Integer agentId, List<String> keywords) {
        if(keywords == null || keywords.isEmpty()) {
            return null;
        }
        Integer count = agentScoreDao.countAgentKeywords(agentId, keywords);
        if(count < keywords.size()) {
            return null;
        }
        double score = agentScoreDao.getTotalScoreByAgentIdAndKeywords(agentId, keywords);
        score = score / keywords.size();
        return AgentIntentScore.builder().agentId(agentId).score(score).build();
    }

    public void saveAgentScore(AgentConfig agentConfig, String question, String answer) {
        executorService.submit(()->{
            try {
                List<String> keywords = intentDetect(question).getKeywords();
                for (String keyword : keywords) {
                    List<AgentIntentScore> agentIntentScores = getAgentScore(keyword);
                    if(agentIntentScores == null || agentIntentScores.isEmpty()) {
                        save(agentConfig, question, answer, keyword);
                    } else {
                        boolean present = agentIntentScores.stream().anyMatch(a -> a.getAgentId().equals(agentConfig.getId()));
                        if(!present) {
                            save(agentConfig, question, answer, keyword);
                        }
                    }
                    agentScoreDao.insertAgentKeywordLog(agentConfig.getId(), keyword);
                }
            } catch (Exception e) {

            }

        });
    }

    public void saveAgentScore(AgentConfig agentConfig, List<String> keywords, Double score) {
        try {
            for (String keyword : keywords) {
                List<AgentIntentScore> agentIntentScores = getAgentScore(keyword);
                if(agentIntentScores == null || agentIntentScores.isEmpty()) {
                    save(agentConfig, keyword, score);
                } else {
                    boolean present = agentIntentScores.stream().anyMatch(a -> a.getAgentId().equals(agentConfig.getId()));
                    if(!present) {
                        save(agentConfig, keyword, score);
                    }
                }
                agentScoreDao.insertAgentKeywordLog(agentConfig.getId(), keyword);
            }
        } catch (Exception e) {

        }
    }

    public void updateScore(AgentConfig agentConfig, List<String> keywords, Double score) {
        try {
            for (String keyword : keywords) {
                List<AgentIntentScore> agentIntentScores = getAgentScore(keyword);
                if(agentIntentScores == null || agentIntentScores.isEmpty()) {
                    save(agentConfig, keyword, score);
                } else {
                    boolean present = agentIntentScores.stream().anyMatch(a -> a.getAgentId().equals(agentConfig.getId()));
                    if(!present) {
                        save(agentConfig, keyword, score);
                    }
                }
            }
        } catch (Exception e) {

        }
    }

    private void save(AgentConfig agentConfig, String keyword, double score) {
        AgentIntentScore agentScore = AgentIntentScore.builder()
                .agentId(agentConfig.getId()).agentName(agentConfig.getName()).keyword(keyword).score(score)
                .build();
        saveAgentScore(agentScore);
        log.info("save agent score: {}", agentScore);
    }

    private void save(AgentConfig agentConfig, String question, String answer, String keyword) {
        Double score = scoring(question, answer);
        save(agentConfig, keyword, score);
    }

    private void saveAgentScore(AgentIntentScore agentScore) {
        String keyword = agentScore.getKeyword();
        try {
            agentScoreDao.saveOrUpdateScore(agentScore.getAgentId(), agentScore.getAgentName(), keyword, agentScore.getQuestion(), agentScore.getScore());
//            agentScoreDao.saveScore(agentScore.getAgentId(), agentScore.getAgentName(), keyword, agentScore.getQuestion(), agentScore.getScore());
        } catch (Exception ignored) {
        }
        removeCached(keyword);
    }

    private static void removeCached(String keyword) {
        cachedSkillMap.remove(keyword);
    }

    public double getSimilarity(String question, String answer) {
        KShingle kShingle = new KShingle();
        double[] similarity = kShingle.similarity(question, answer, 2);
        return similarity[0];
    }

    public IntentResponse intentDetect(ChatCompletionRequest chatCompletionRequest) {
        String question = chatCompletionRequest.getMessages().get(chatCompletionRequest.getMessages().size() - 1).getContent();
        return intentDetect(question);
    }

    public IntentResponse intentDetect(String question) {
        if (DatabaseManager.isDbExists(DatabaseGlobal.AI_DB)) {
            return nodeIntentDetect(question);
        }
        return llmIntentDetect(question);
    }

    public IntentResponse nodeIntentDetect(String question) {
        List<String> keywords = SubstitutionUtil.lookupNodeStr(question);
        if (keywords.isEmpty()) {
            return null;
        }
        IntentResponse intentResponse = IntentResponse.builder().keywords(keywords).build();
        return intentResponse;
    }

    public IntentResponse llmIntentDetect(String question) {
        for (int i = 0;i < maxTry; i++) {
            try {
                ChatCompletionResult chatCompletionResult = LlmUtil.callLLm(StrUtil.format(SkillMapPrompt.KEYWORD_PROMPT_TEMPLATE, question),
                        Collections.emptyList(),
                        SkillMapPrompt.KEYWORD_USER_PROMPT);
                String json = JsonExtractor.extractJson(ChatCompletionUtil.getFirstAnswer(chatCompletionResult));
                return gson.fromJson(json, IntentResponse.class);
            } catch (Exception e) {
            }
        }
        return null;
    }

    public Double scoring(String question, String answer) {
        for (int i = 0;i < maxTry; i++) {
            try {
                ChatCompletionResult chatCompletionResult = LlmUtil.callLLm(StrUtil.format(SkillMapPrompt.SCORE_PROMPT_TEMPLATE, question, answer),
                        Collections.emptyList(),
                        SkillMapPrompt.SCORE_USER_PROMPT);
                String firstAnswer = ChatCompletionUtil.getFirstAnswer(chatCompletionResult);
                String json = JsonExtractor.extractJson(firstAnswer);
                ScoreResponse scoreResponse = gson.fromJson(json, ScoreResponse.class);
                Double score = scoreResponse.getScore();
                if(score > 0) {
                    double similarity = getSimilarity(question, answer);
                    score = Math.min(10, score * 0.7  + (similarity * 0.3 * 10)) ;
                }
                return score;
            } catch (Exception e) {
            }
        }
        return null;
    }

    @Builder
    @ToString
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static
    class PickAgent{
        private Integer id;
        private String describe;
        private String reason;
    }

    public List<PickAgent> pickAgent(String question, List<PickAgent> pickAgents) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("[\n");
        for (PickAgent pickAgent : pickAgents) {
            stringBuilder.append(StrUtil.format("{\"id\": {}, \"describe\": \"{}\"},\n", pickAgent.getId(), pickAgent.getDescribe()));
        }
        stringBuilder.append("{\"id\": 0, \"describe\": \"{通用功能智能体,能处理所有问题但专业度不高}\"},\n");
        stringBuilder.append("]\n");
        String agents = stringBuilder.toString();
        for (int i = 0;i < maxTry; i++) {
            try {
                List<Integer> ids = pickAgents.stream().map(PickAgent::getId).collect(Collectors.toList());
//                ChatCompletionResult chatCompletionResult = LlmUtil.callLLm(StrUtil.format(SkillMapPrompt.AGENT_PICK_SYSTEM_PROMPT, gson.toJson(ids), agents), Collections.emptyList(), question);
                ChatCompletionResult chatCompletionResult = LlmUtil.callLLm(StrUtil.format(SkillMapPrompt.AGENTS_PICK_SYSTEM_PROMPT, agents), Collections.emptyList(), question);
                String firstAnswer = ChatCompletionUtil.getFirstAnswer(chatCompletionResult);
                String json = JsonExtractor.extractJson(firstAnswer);
                Type typeResponse = new TypeToken<List<PickAgent>>() {}.getType();
                List<PickAgent> pickAgent = gson.fromJson(json, typeResponse);
                if(pickAgent == null) {
                    return Collections.emptyList();
                }
                pickAgent = pickAgent.stream().filter(p-> p.getId() != 0).collect(Collectors.toList());
                return pickAgent;
            } catch (Exception e) {
            }
        }
        return Collections.emptyList();
    }


    public Map<String, Integer> getRagAgentIdName() {
        Map<String, Integer> agentMap = new HashMap<>();
        List<AgentIntentScore> agentList = agentScoreDao.getAgentIdName();
        for (AgentIntentScore agent : agentList) {
            if (agent.getAgentId() < MAX_RAG_AGENT_ID && agent.getAgentId() > MIN_RAG_AGENT_ID) {
                agentMap.put(agent.getAgentName(), agent.getAgentId());
            }
        }
        return agentMap;
    }

    public int getRandomRagAgentId() {
        Random random = new Random();
        return random.nextInt(MAX_RAG_AGENT_ID - MIN_RAG_AGENT_ID) + MIN_RAG_AGENT_ID;
    }

    public void manualScoring(String question, Integer agentId, String agentName, Double score) {
        IntentResponse safeIntentResponse = getSafeIntentResponse(question);
        if(safeIntentResponse == null) {
            throw new RRException("未识别到意图");
        }
        List<String> keywords = safeIntentResponse.getKeywords();
        if(keywords == null) {
            throw new RRException("未识别到关键词");
        }
        keywords.forEach(keyword -> {
            agentScoreDao.saveOrUpdateScore(agentId, agentName, keyword, question, score);
            cachedSkillMap.remove(keyword);
        });
    }
    public Integer getNameByID(String name) {
        return agentScoreDao.getNameByID( name);
    }

}
