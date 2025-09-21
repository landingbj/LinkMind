package ai.servlet.api;

import ai.agent.Agent;
import ai.agent.chat.rag.LocalRagAgent;
import ai.common.ModelService;
import ai.common.exception.RRException;
import ai.common.pojo.*;
import ai.config.ContextLoader;
import ai.config.pojo.AgentConfig;
import ai.config.pojo.RAGFunction;
import ai.dto.DeductExpensesRequest;
import ai.dto.ModelPreferenceDto;
import ai.embedding.EmbeddingFactory;
import ai.embedding.Embeddings;
import ai.embedding.pojo.OpenAIEmbeddingRequest;
import ai.intent.IntentGlobal;
import ai.intent.pojo.IntentRouteResult;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.pojo.ChatCompletionResultWithSource;
import ai.llm.pojo.EnhanceChatCompletionRequest;
import ai.llm.pojo.GetRagContext;
import ai.llm.schedule.QueueSchedule;
import ai.llm.service.CompletionsService;
import ai.llm.service.FreezingService;
import ai.llm.service.LlmRouterDispatcher;
import ai.llm.utils.CompletionUtil;
import ai.llm.utils.PriorityLock;
import ai.llm.utils.SummaryUtil;
import ai.manager.AgentManager;
import ai.manager.LlmManager;
import ai.medusa.MedusaMonitor;
import ai.medusa.MedusaService;
import ai.medusa.pojo.CacheItem;
import ai.medusa.pojo.PromptInput;
import ai.medusa.utils.PromptCacheTrigger;
import ai.medusa.utils.PromptInputUtil;
import ai.migrate.dao.UserModelPreferenceDao;
import ai.migrate.service.AgentService;
import ai.migrate.service.TraceService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.router.pojo.LLmRequest;
import ai.servlet.BaseServlet;
import ai.servlet.dto.LagiAgentExpenseListResponse;
import ai.servlet.dto.LagiAgentListResponse;
import ai.servlet.dto.LagiAgentResponse;
import ai.servlet.dto.PaidLagiAgent;
import ai.sevice.KnowledgeBaseService;
import ai.utils.*;
import ai.utils.qa.ChatCompletionUtil;
import ai.vector.VectorCacheLoader;
import ai.vector.VectorDbService;
import ai.worker.DefaultWorker;
import ai.worker.pojo.IntentResponse;
import ai.worker.skillMap.SkillMapUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public class LlmApiServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    private static final Configuration config = MigrateGlobal.config;
    private final CompletionsService completionsService = new CompletionsService();
    private final VectorDbService vectorDbService = new VectorDbService(config);
    private final Logger logger = LoggerFactory.getLogger(LlmApiServlet.class);
    private final MedusaService medusaService = new MedusaService();
    private final RAGFunction RAG_CONFIG = ContextLoader.configuration.getStores().getRag();
    private static final Medusa MEDUSA_CONFIG = ContextLoader.configuration.getStores().getMedusa();
    private Boolean RAG_ENABLE = null;
    private Boolean MEDUSA_ENABLE = null;
    private final Boolean enableQueueHandle = ContextLoader.configuration.getFunctions().getChat().getEnableQueueHandle();
    private final QueueSchedule queueSchedule = enableQueueHandle ? new QueueSchedule() : null;
    private final DefaultWorker defaultWorker = new DefaultWorker();
    private final AgentService agentService = new AgentService();
    private final TraceService traceService = new TraceService();
    private static final LRUCache<String, Pair<Integer, Agent<ChatCompletionRequest, ChatCompletionResult>>> agentLRUCache = IntentGlobal.AGENT_LRU_CACHE;
    private static MedusaMonitor medusaMonitor;

    static {
        if (MEDUSA_CONFIG.getEnable()) {
            medusaMonitor = MedusaMonitor.getInstance();
        }
        VectorCacheLoader.load();
    }

    @Override
    public void init() throws ServletException {
        medusaService.init();
        super.init();
    }


    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setHeader("Content-Type", "application/json;charset=utf-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);
        if (method.equals("completions")) {
            this.completions(req, resp);
        } else if (method.equals("embeddings")) {
            this.embeddings(req, resp);
        } else if (method.equals("go")) {
            this.go(req, resp);
        } else if (method.equals("stream")) {
            this.goStream(req, resp);
        } else if (method.equals("solid")) {
            this.goSolid(req, resp);
        } else if (method.equals("isMedusa")) {
            this.isMedusa(req, resp);
        } else if (method.equals("isRAG")) {
            this.isRAG(req, resp);
        } else {
            responsePrint(resp, "method not found");
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setHeader("Content-Type", "application/json;charset=utf-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);
        if (method.equals("getSession")) {
            this.getSession(req, resp);
        }
    }

    private void getSession(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String sessionId = UUID.randomUUID().toString();
        agentLRUCache.put(sessionId, null);
        responsePrint(resp, "{\"sessionId\": \"" + sessionId + "+\"}");
    }


    private void isRAG(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setHeader("Content-Type", "application/json;charset=utf-8");
        String enable = req.getParameter("RAG");
        Map<String, Object> map = new HashMap<>();
        map.put("status", "success");
        if (enable != null && !"".equals(enable)) {
            if (enable.equals("true")) {
                map.put("RAG", "RAG已开启");
                this.RAG_ENABLE = true;
            } else {
                map.put("RAG", "RAG已关闭");
                this.RAG_ENABLE = false;
            }
        }
        PrintWriter out = resp.getWriter();
        out.print(gson.toJson(map));
        out.flush();
        out.close();
    }

    private void isMedusa(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setHeader("Content-Type", "application/json;charset=utf-8");
        String enable = req.getParameter("medusa");
        Map<String, Object> map = new HashMap<>();
        map.put("status", "success");
        if (enable != null && !"".equals(enable)) {
            if (enable.equals("true")) {
                map.put("medusa", "medusa已开启");
                this.MEDUSA_ENABLE = true;
            } else {
                map.put("medusa", "medusa已关闭");
                this.MEDUSA_ENABLE = false;
            }
        }
        PrintWriter out = resp.getWriter();
        out.print(gson.toJson(map));
        out.flush();
        out.close();

    }

    private void go(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        LLmRequest lLmRequest = reqBodyToObj(req, LLmRequest.class);
        List<Agent<ChatCompletionRequest, ChatCompletionResult>> agents;
        LagiAgentExpenseListResponse paidAgentByUser = agentService.getPaidAgentByUser(lLmRequest.getUserId(), "1", "1000");
        Map<Integer, Boolean> haveABalance = paidAgentByUser.getData().stream().collect(Collectors.toMap(AgentConfig::getId, agentConfig -> {
            BigDecimal balance = agentConfig.getBalance();
            BigDecimal pricePerReq = agentConfig.getPricePerReq();
            // 使用compareTo代替doubleValue比较，避免精度问题
            return balance.compareTo(pricePerReq) >= 0;
        }));
        Map<Integer, PaidLagiAgent> paidLagiAgentMap = paidAgentByUser.getData().stream().collect(Collectors.toMap(AgentConfig::getId, agentConfig -> agentConfig));

        ChatCompletionResult work = null;
        if (lLmRequest.getAgentId() == null) {
            LagiAgentListResponse lagiAgentList = agentService.getLagiAgentList(null, 1, 1000, "true");
            List<AgentConfig> agentConfigs = lagiAgentList.getData();
            agents = convert2AgentList(agentConfigs, haveABalance);
            work = defaultWorker.work(lLmRequest.getWorker(), agents, lLmRequest);
            if (work != null) {
                deductExpense(work, lLmRequest, agentConfigs);
            }
        } else {
            LagiAgentResponse lagiAgent = agentService.getLagiAgent(null, String.valueOf(lLmRequest.getAgentId()));
            AgentConfig agentConfig = lagiAgent.getData();
            // system agent
            if (agentConfig == null) {
                work = defaultWorker.work(lLmRequest.getWorker(), Collections.emptyList(), lLmRequest);
            } else
            // user  agents
            {
                Boolean isFeeRequired = agentConfig.getIsFeeRequired();
                List<AgentConfig> agentConfigs = Lists.newArrayList(lagiAgent.getData());
                agents = convert2AgentList(agentConfigs, haveABalance);
                if (Boolean.TRUE.equals(isFeeRequired)) {
                    boolean isPreDuctExpense = preDuctExpense(paidLagiAgentMap.get(agentConfig.getId()));
                    if (isPreDuctExpense) {
                        work = defaultWorker.work(lLmRequest.getWorker(), agents, lLmRequest);
                        if (work != null) {
                            deductExpense(work, lLmRequest, agentConfigs);
                        }
                    } else {
                        work = noExpenseResult(lagiAgent);
                    }
                } else {
                    work = defaultWorker.work(lLmRequest.getWorker(), agents, lLmRequest);
                }
            }
        }
        if (work == null) {
            throw new RRException("调用接口失败,  未获取有效结果");
        }
        if (Boolean.FALSE.equals(lLmRequest.getStream())) {
            responsePrint(resp, toJson(work));
        }
        String firstAnswer = ChatCompletionUtil.getFirstAnswer(work);
        convert2streamAndOutput(firstAnswer, req, resp, work);
        if (work instanceof ChatCompletionResultWithSource) {
            ChatCompletionResultWithSource resultWithSource = (ChatCompletionResultWithSource) work;
            if (lLmRequest.getAgentId() != null) {
                resultWithSource.setSourceId(lLmRequest.getAgentId());
                try {
                    if (lLmRequest.getSessionId() != null && AgentManager.getInstance().get(lLmRequest.getAgentId()) != null) {
                        agentLRUCache.put(lLmRequest.getSessionId(), new Pair<>(lLmRequest.getMessages().size() - 1, (Agent<ChatCompletionRequest, ChatCompletionResult>) AgentManager.getInstance().get(lLmRequest.getAgentId())));
                    }
                } catch (Exception ignored) {
                }
            }
            traceService.syncAddAgentTrace(resultWithSource);
        }
    }

    private ChatCompletionResult noExpenseResult(LagiAgentResponse lagiAgent) {
        String format = StrUtil.format("{\"source\":\"{}\",  \"created\":0,\"choices\":[{\"index\":0,\"message\":{\"content\":\"您在{}账户余额不足\"}}]}", lagiAgent.getData().getName(), lagiAgent.getData().getName());
        return gson.fromJson(format, ChatCompletionResultWithSource.class);
    }

    private boolean preDuctExpense(PaidLagiAgent agentConfig) {
        if (agentConfig == null) {
            return false;
        }
        SafeDeductionTool.createAccount(agentConfig.getUserId() + agentConfig.getId(), agentConfig.getBalance().doubleValue());
        return SafeDeductionTool.deduct(agentConfig.getUserId() + agentConfig.getId(), agentConfig.getPricePerReq().doubleValue());
    }

    private void deductExpense(ChatCompletionResult work, LLmRequest lLmRequest, List<AgentConfig> agentConfigs) {
        if (work == null || agentConfigs == null) {
            return;
        }
        agentConfigs = agentConfigs.stream().filter(Objects::nonNull).collect(Collectors.toList());
        if (agentConfigs.isEmpty()) {
            return;
        }
        if (work instanceof ChatCompletionResultWithSource) {
            // 使用 (existingValue, replacementValue) -> existingValue 来解决冲突
            Map<Integer, Boolean> feedMap = agentConfigs.stream()
                    .collect(Collectors.toMap(
                            AgentConfig::getId,
                            AgentConfig::getIsFeeRequired,
                            (existingValue, replacementValue) -> existingValue
                    ));
            Integer sourceId = ((ChatCompletionResultWithSource) work).getSourceId();
            if (feedMap.containsKey(sourceId) && feedMap.get(sourceId)) {
                deductExpense(sourceId, lLmRequest.getUserId());
                SafeDeductionTool.removeAccount(lLmRequest.getUserId() + sourceId);
            }
        }
    }

    private void deductExpense(Integer agentId, String userId) {
        DeductExpensesRequest deductExpensesRequest = new DeductExpensesRequest();
        deductExpensesRequest.setAgentId(agentId);
        deductExpensesRequest.setUserId(userId);
        try {
            Response response = agentService.deductExpenses(deductExpensesRequest);
            if ("success".equals(response.getStatus())) {
                logger.info("user {} agent {} deductExpense success", userId, agentId);
            }
        } catch (Exception e) {
            logger.error("deductExpense error", e);
        }
    }

    private static List<Agent<ChatCompletionRequest, ChatCompletionResult>> convert2AgentList(List<AgentConfig> agentConfigs, Map<Integer, Boolean> haveABalance) {
        return SkillMapUtil.convert2AgentList(agentConfigs, haveABalance);
    }


    private ChatCompletionResultWithSource convertResponse(String source, String response) {
        String format = StrUtil.format("{\"source\":\"{}\",  \"created\":0,\"choices\":[{\"index\":0,\"message\":{\"content\":\"{}\"}}]}", source, response);
        return new Gson().fromJson(format, ChatCompletionResultWithSource.class);
    }

    private void convert2streamAndOutput(String firstAnswer, HttpServletRequest req, HttpServletResponse resp, ChatCompletionResult chatCompletionResult) throws IOException {
        resp.setHeader("Content-Type", "text/event-stream;charset=utf-8");
        PrintWriter out = resp.getWriter();
        int length = 5;
        String source = "";
        if (chatCompletionResult instanceof ChatCompletionResultWithSource) {
            source = ((ChatCompletionResultWithSource) chatCompletionResult).getSource();
        }
        firstAnswer = firstAnswer.replaceAll("\n", "   \n");
        for (int i = 0; i < firstAnswer.length(); i += length) {
            int end = Math.min(i + length, firstAnswer.length());
            try {
                String substring = firstAnswer.substring(i, end);
                ChatCompletionResult result = convertResponse(source, substring);
                ChatCompletionResult filter = SensitiveWordUtil.filter(result);
                String msg = gson.toJson(filter);
                out.print("data: " + msg + "\n\n");
                out.flush();
            } catch (Exception e) {
            }
            try {
                Thread.sleep(20);
            } catch (Exception ignored) {
                logger.error("produce stream", ignored);
            }
        }
        loaderWebPic2Local(req, chatCompletionResult);
        chatCompletionResult.getChoices().get(0).getMessage().setContent("");
        out.print("data: " + gson.toJson(chatCompletionResult) + "\n\n");


        out.flush();
        out.print("data: " + "[DONE]" + "\n\n");
        out.flush();
        out.close();
    }

    private void loaderWebPic2Local(HttpServletRequest req, ChatCompletionResult chatCompletionResult) {
        try {
            List<String> imageList = chatCompletionResult.getChoices().get(0).getMessage().getImageList();
            if (imageList != null && !imageList.isEmpty()) {
                String rootPath = req.getRealPath("");
                String filePath = rootPath + "static/images/";
                File file = new File(filePath);
                if (!file.exists()) {
                    file.mkdirs();
                }
                imageList = imageList.stream().map(image -> {
                    if (image.contains("@")) {
                        image = image.split("@")[0];
                    }
                    WhisperResponse whisperResponse = DownloadUtils.downloadFile(image, "jpeg", filePath);
                    return "static/images/" + whisperResponse.getMsg();
                }).collect(Collectors.toList());
                chatCompletionResult.getChoices().get(0).getMessage().setImageList(imageList);
            }
        } catch (Exception ignored) {

        }
    }

    private List<Agent<ChatCompletionRequest, ChatCompletionResult>> getAllAgents(LLmRequest llmRequest, String uri) throws IOException {
        return agentService.getAllAgents(llmRequest, uri);
    }

    public List<ILlmAdapter> getUserLlmAdapters(String userId) {
        return agentService.getUserLlmAdapters(userId);
    }

    private static final LRUCache<LLmRequest, IntentResponse> intentKeywordCache = IntentGlobal.INTENT_KEYWORD_CACHE;

    private void goStream(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setHeader("Content-Type", "text/event-stream;charset=utf-8");
        LLmRequest llmRequest = reqBodyToObj(req, LLmRequest.class);
        PrintWriter out = resp.getWriter();
        String uri = req.getScheme() + "://" + req.getServerName() + ":" + req.getServerPort();
        List<Agent<ChatCompletionRequest, ChatCompletionResult>> allAgents = getAllAgents(llmRequest, uri);
        if (invokeAppointedAgent(req, resp, llmRequest, allAgents)) return;
        IntentRouteResult intentRouteResult = llmRequest.getIntent();
//        boolean think = Boolean.TRUE.equals(llmRequest.getThink());
//        boolean think = true;
//        if (think) {
//            out.println("data: " + gson.toJson(convertResponse("", "<think>")) + "\n\n");
//            out.flush();
//        }
//        if (intentRouteResult!=null&&intentRouteResult.getInvoke() != null) {
//            String invoke = intentRouteResult.getInvoke();
//            SummaryUtil.setInvoke(llmRequest, invoke);
//            if (think) {
//                out.println("data: " + gson.toJson(convertResponse("", "用户的目标：" + invoke + "\n\n")) + "\n\n");
//                out.flush();
//            }
//            logger.info("Summary: {}", invoke);
//        }
        List<Integer> agents = intentRouteResult==null?null:intentRouteResult.getAgents();
        List<Agent<ChatCompletionRequest, ChatCompletionResult>> pickAgentList = agentService.getAgentsById(agents, allAgents);
        Agent<ChatCompletionRequest, ChatCompletionResult> outputAgent = null;

        if (pickAgentList == null || pickAgentList.isEmpty()) {
            outputAgent = SkillMapUtil.getHighestPriorityLlm();
        } else if (intentRouteResult.getAllSolid()) {
            outputAgent = pickAgentList.get(0);
        } else {
            outputAgent = getFirstStreamAgent(pickAgentList);
        }

        if(outputAgent == null) {
            outputAgent = SkillMapUtil.getHighestPriorityLlm();
        }
        IntentResponse intentDetect = getIntentResponse(llmRequest);

//        if (think) {
//            if(outputAgent instanceof LocalRagAgent) {
//                out.println("data: " + gson.toJson(convertResponse("", "为用户选用大模型：LLM" +   "\n\n")) + "\n\n");
//            } else {
//                out.println("data: " + gson.toJson(convertResponse("", "为用户选用智能体：" + outputAgent.getAgentConfig().getName() + "\n\n")) + "\n\n");
//            }
//            out.flush();
//            out.println("data: " + gson.toJson(convertResponse("", "</think>")) + "\n\n");
//            out.flush();
//        }

        if (outputAgent instanceof LocalRagAgent) {
            LocalRagAgent ragAgent = (LocalRagAgent) outputAgent;
            ragAgent.getAgentConfig().setEndpoint(uri);
            llmRequest.setModel(ragAgent.getAgentConfig().getName());
        }

        final ChatCompletionResultWithSource[] resultWithSource = {null};
        if (!outputAgent.canStream()) {
            llmRequest.setStream(false);
            ChatCompletionResult result = outputAgent.communicate(llmRequest);
            ChatCompletionResultWithSource chatCompletionResultWithSource = new ChatCompletionResultWithSource(outputAgent.getAgentConfig().getName(), outputAgent.getAgentConfig().getId());
            BeanUtil.copyProperties(result, chatCompletionResultWithSource);
            String answer = ChatCompletionUtil.getFirstAnswer(result);
            convert2streamAndOutput(answer, req, resp, chatCompletionResultWithSource);
            resultWithSource[0] = chatCompletionResultWithSource;
            recordLastOutputAgent(llmRequest.getIntent().getContinuedIndex(), outputAgent, llmRequest);
            agentRunFinishCallBack(resultWithSource, llmRequest, allAgents, outputAgent, intentDetect);
        } else {
            llmRequest.setStream(true);
            Observable<ChatCompletionResult> result = outputAgent.stream(llmRequest);
            if (result == null) {
                throw new RRException("调用接口失败, 未获取有效结果");
            }
            Agent<ChatCompletionRequest, ChatCompletionResult> finalOutputAgent = outputAgent;
            if (finalOutputAgent instanceof LocalRagAgent) {
                List<ILlmAdapter> collect = LlmManager.getInstance().getAdapters().stream().filter(a -> a != null && FreezingService.notFreezingAdapter(a)).collect(Collectors.toList());
                if (!collect.isEmpty()) {
                    ModelService modelService = (ModelService) collect.get(0);
                    finalOutputAgent.getAgentConfig().setName(modelService.getModel());
                }
            }
            result.subscribe(
                    data -> {
                        ChatCompletionResult chatCompletionResult = SensitiveWordUtil.filter(data);
                        ChatCompletionResultWithSource chatCompletionResultWithSource;
                        if (StrUtil.isNotBlank(chatCompletionResult.getModel())) {
                            chatCompletionResultWithSource = new ChatCompletionResultWithSource(chatCompletionResult.getModel(), finalOutputAgent.getAgentConfig().getId());
                        } else {
                            Integer AgentId = SkillMapUtil.getNameByID(finalOutputAgent.getAgentConfig().getName());
                            chatCompletionResultWithSource = new ChatCompletionResultWithSource(finalOutputAgent.getAgentConfig().getName(), AgentId!=null?AgentId:finalOutputAgent.getAgentConfig().getId());
                        }
                        BeanUtil.copyProperties(chatCompletionResult, chatCompletionResultWithSource);
                        String msg = gson.toJson(chatCompletionResultWithSource);
                        out.print("data: " + msg + "\n\n");
                        out.flush();
                        if (resultWithSource[0] == null) {
                            resultWithSource[0] = chatCompletionResultWithSource;
                        } else {
                            try {
                                String content = resultWithSource[0].getChoices().get(0).getMessage().getContent();
                                String content1 = chatCompletionResult.getChoices().get(0).getMessage().getContent();
                                resultWithSource[0].getChoices().get(0).getMessage().setContent(content + content1);
                            } catch (Exception ignored) {
                            }
                        }
                    },
                    e -> {
                        logger.error("", e);
                    },
                    () -> {
                        out.print("data: " + "[DONE]" + "\n\n");
                        out.flush();
                        out.close();
                        recordLastOutputAgent(llmRequest.getIntent().getContinuedIndex(), finalOutputAgent, llmRequest);
                        agentRunFinishCallBack(resultWithSource, llmRequest, allAgents, finalOutputAgent, intentDetect);
                    }
            );
        }
    }

    private static IntentResponse getIntentResponse(LLmRequest llmRequest) {
        IntentResponse intentDetect = intentKeywordCache.get(llmRequest);
        if (intentDetect == null) {
            String userInput = ChatCompletionUtil.getLastMessage(llmRequest);
            intentDetect = SkillMapUtil.intentDetect(userInput);
        }
        return intentDetect;
    }

    private void recordLastOutputAgent(int continuedIndex, Agent<ChatCompletionRequest, ChatCompletionResult> outputAgent, LLmRequest llmRequest) {
        Pair<Integer, Agent<ChatCompletionRequest, ChatCompletionResult>> integerAgentPair = new Pair<>();
        integerAgentPair.setPA(continuedIndex);
        integerAgentPair.setPB(outputAgent);
        agentLRUCache.put(llmRequest.getSessionId(), integerAgentPair);
    }

    private boolean invokeAppointedAgent(HttpServletRequest req, HttpServletResponse resp, LLmRequest llmRequest, List<Agent<ChatCompletionRequest, ChatCompletionResult>> allAgents) throws IOException {
        if (llmRequest.getAgentId() != null) {
            ChatCompletionResult work = defaultWorker.work("appointedWorker", allAgents, llmRequest);
            if (work != null) {
                convert2streamAndOutput(work.getChoices().get(0).getMessage().getContent(), req, resp, work);
                try {
                    agentLRUCache.put(llmRequest.getSessionId(), new Pair<>(llmRequest.getMessages().size() - 1, (Agent<ChatCompletionRequest, ChatCompletionResult>) AgentManager.getInstance().get(llmRequest.getAgentId())));
                } catch (Exception ignored) {
                }
                deductExpense(work, llmRequest, allAgents.stream().map(Agent::getAgentConfig).collect(Collectors.toList()));
                return true;
            }
        }
        return false;
    }

    private void agentRunFinishCallBack(ChatCompletionResultWithSource[] resultWithSource, LLmRequest llmRequest, List<Agent<ChatCompletionRequest, ChatCompletionResult>> allAgents, Agent<ChatCompletionRequest, ChatCompletionResult> outputAgent, IntentResponse intentDetect) {
        //更新得分
        SkillMapUtil.updateScore(intentDetect, llmRequest, outputAgent, resultWithSource[0]);
        deductExpense(resultWithSource[0], llmRequest, allAgents.stream().map(Agent::getAgentConfig).collect(Collectors.toList()));
        if (resultWithSource[0] != null) {
            if (outputAgent instanceof LocalRagAgent) {
                traceService.syncAddLlmTrace(resultWithSource[0]);
            } else {
                traceService.syncAddAgentTrace(resultWithSource[0]);
            }
        }
        List<Integer> agents;
        if(llmRequest.getIntent().getAgents() != null) {
            agents = llmRequest.getIntent().getAgents();
        } else {
            agents = Collections.emptyList();
        }
        List<Agent<ChatCompletionRequest, ChatCompletionResult>> pickedAgents = allAgents.stream()
                .filter(agent -> (agent instanceof LocalRagAgent) || agents.contains(agent.getAgentConfig().getId()))
                .collect(Collectors.toList());
        scorePickAgents(outputAgent, pickedAgents, intentDetect, llmRequest);
        scoreNoBalanceAgents(intentDetect, llmRequest);
    }

    private void scoreNoBalanceAgents(IntentResponse intentDetect, LLmRequest llmRequest) {
        // TODO 2025/6/16 为支付的agent 评分
    }

    private void scorePickAgents(Agent<ChatCompletionRequest, ChatCompletionResult> outputAgent, List<Agent<ChatCompletionRequest, ChatCompletionResult>> pickAgentList, IntentResponse intentDetect, LLmRequest llmRequest) {
        if (pickAgentList != null) {
            pickAgentList = pickAgentList.stream().filter(agent ->
                            !Objects.equals(outputAgent.getAgentConfig().getId(), agent.getAgentConfig().getId())
            ).collect(Collectors.toList());
            if (intentDetect != null) {
                SkillMapUtil.asyncScoreAgents(intentDetect, llmRequest, pickAgentList);
            } else {
                SkillMapUtil.asyncScoreAgents(llmRequest, pickAgentList);
            }
        }
    }

    private static Agent<ChatCompletionRequest, ChatCompletionResult> getFirstStreamAgent(
            List<Agent<ChatCompletionRequest, ChatCompletionResult>> skillMapAgentList) {
        Agent<ChatCompletionRequest, ChatCompletionResult> outputAgent = null;
        for (Agent<ChatCompletionRequest, ChatCompletionResult> agent : skillMapAgentList) {
            if (agent.canStream()) {
                outputAgent = agent;
                break;
            }
        }
        return outputAgent;
    }

    private boolean isAllCanNotStream(List<Agent<ChatCompletionRequest, ChatCompletionResult>> allAgents) {
        return allAgents.stream().noneMatch(Agent::canStream);
    }

    private void goSolid(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        String uri = req.getScheme() + "://" + req.getServerName() + ":" + req.getServerPort();

        LLmRequest llmRequest = reqBodyToObj(req, LLmRequest.class);
        List<Agent<ChatCompletionRequest, ChatCompletionResult>> allAgents = getAllAgents(llmRequest, uri);

        List<Agent<ChatCompletionRequest, ChatCompletionResult>> pickAgentList = agentService.getAgentsById(llmRequest.getIntent().getAgents(), allAgents);
        if (pickAgentList == null || pickAgentList.isEmpty()) {
            throw new RRException("agent not found");
        }
        Agent<ChatCompletionRequest, ChatCompletionResult> outputAgent = pickAgentList.get(0);

        if (outputAgent == null || outputAgent.canStream()) {
            throw new RRException("agent not found");
        }
        IntentResponse intentDetect = getIntentResponse(llmRequest);
        final ChatCompletionResultWithSource[] resultWithSource = {null};
        llmRequest.setStream(false);
        ChatCompletionResult result = outputAgent.communicate(llmRequest);
        ChatCompletionResultWithSource chatCompletionResultWithSource = new ChatCompletionResultWithSource(outputAgent.getAgentConfig().getName(), outputAgent.getAgentConfig().getId());
        BeanUtil.copyProperties(result, chatCompletionResultWithSource);
        responsePrint(resp, toJson(chatCompletionResultWithSource));
        resultWithSource[0] = chatCompletionResultWithSource;
        recordLastOutputAgent(llmRequest.getIntent().getContinuedIndex(), outputAgent, llmRequest);
        agentRunFinishCallBack(resultWithSource, llmRequest, allAgents, outputAgent, intentDetect);
    }

    private void completions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        PrintWriter out = resp.getWriter();
        HttpSession session = req.getSession();
        if (RAG_ENABLE == null) {
            RAG_ENABLE = RAG_CONFIG.getEnable();
        }
        EnhanceChatCompletionRequest chatCompletionRequest = setCustomerModel(req, session);
        chatCompletionRequest.setPriority(PriorityLock.HIGH_PRIORITY);
        ChatCompletionResult chatCompletionResult = null;

        List<IndexSearchData> indexSearchDataList = null;
        String SAMPLE_COMPLETION_RESULT_PATTERN = "{\"created\":0,\"source\":%s,\"choices\":[{\"index\":0,\"message\":{\"content\":\"%s\"}}]}";

        if (Boolean.TRUE.equals(RAG_ENABLE) && (!Boolean.FALSE.equals(chatCompletionRequest.getRag()))) {
            ModelService modelService = (ModelService) LlmRouterDispatcher
                    .getRagAdapter(null).stream().findFirst().orElse(null);
            if (modelService != null && RAG_CONFIG.getPriority() > modelService.getPriority()) {
                try {
                    indexSearchDataList = vectorDbService.searchByContext(chatCompletionRequest);
                } catch (Exception e) {
                    logger.error("vector search failed : ", e);
                }
                if (indexSearchDataList.isEmpty()) {
                    String s = String.format(SAMPLE_COMPLETION_RESULT_PATTERN, RAG_CONFIG.getDefaultText());
                    outPrintJson(resp, chatCompletionRequest, s);
                    return;
                }
            }
        }

        if (MEDUSA_ENABLE == null) {
            MEDUSA_ENABLE = MEDUSA_CONFIG.getEnable();
        }
        if (Boolean.TRUE.equals(MEDUSA_ENABLE)) {
            PromptInput promptInput = medusaService.getPromptInput(chatCompletionRequest);
            chatCompletionResult = medusaService.locate(promptInput);
            if (chatCompletionResult != null) {
                outPrintChatCompletion(resp, chatCompletionRequest, chatCompletionResult);
                logger.info("Cache hit: {}", PromptInputUtil.getNewestPrompt(promptInput));
                promptInput.getMedusaMetadata().setCacheHit(true);
                medusaService.triggerCachePutAndDiversify(promptInput, true);
                return;
            } else {
                medusaService.triggerCachePutAndDiversify(promptInput, true);
            }
        }
        boolean hasTruncate = false;
        GetRagContext context = null;
        String userId = chatCompletionRequest.getUserId() == null ? req.getHeader("userId") : chatCompletionRequest.getUserId();
        List<ILlmAdapter> userLlmAdapters = getUserLlmAdapters(userId);
        if(!userLlmAdapters.isEmpty()) {
            chatCompletionRequest.setModel( ((ModelService) userLlmAdapters.get(0)).getModel());
        }
        long count = userLlmAdapters.stream().map(adapter -> ((ModelService) adapter).getModel())
                .filter(model -> Objects.equals(model, chatCompletionRequest.getModel())).count();
        Integer maxContext = null;
        Integer maxMsg = null;
        if (count > 0L) {
            maxContext = 1024 * 3;
            maxMsg = 1024 * 4;
        }
        if (chatCompletionRequest.getCategory() != null && Boolean.TRUE.equals(RAG_ENABLE) && (!Boolean.FALSE.equals(chatCompletionRequest.getRag()))) {
            String lastMessage = ChatCompletionUtil.getLastMessage(chatCompletionRequest);
            String answer = VectorCacheLoader.get2L2(lastMessage);
            if (StrUtil.isNotBlank(answer)) {
                outPrintJson(resp, chatCompletionRequest, String.format(SAMPLE_COMPLETION_RESULT_PATTERN, chatCompletionRequest.getModel(), answer));
                return;
            }
            if (indexSearchDataList == null) {
                try {
                    KnowledgeBaseService knowledgeBaseService = new KnowledgeBaseService();
                    KnowledgeBase latestPublicKnowledgeBase = knowledgeBaseService.getLatestPublicKnowledgeBase(userId, knowledgeBaseService.getRegion());
                    chatCompletionRequest.setKnowledgeBase(latestPublicKnowledgeBase);
                    // TODO 2025/7/7 RAG查询: 添加用户自定义参数影响查询: indexSearchDataList = vectorDbService.searchByContext(chatCompletionRequest, userSelectedParams);
                    indexSearchDataList = vectorDbService.searchByContext(chatCompletionRequest);
                } catch (Exception e) {
                    logger.error("vector search failed : ", e);
                }
            }
            if (indexSearchDataList != null && !indexSearchDataList.isEmpty()) {
                context = completionsService.getRagContext(indexSearchDataList);
                String contextStr = CompletionUtil.truncate(context.getContext(), maxContext);
                context.setContext(contextStr);
                completionsService.addVectorDBContext(chatCompletionRequest, contextStr);
                ChatMessage chatMessage = chatCompletionRequest.getMessages().get(chatCompletionRequest.getMessages().size() - 1);
                chatCompletionRequest.setMessages(Lists.newArrayList(chatMessage));
                hasTruncate = true;
            }
        } else {
            indexSearchDataList = null;
        }
        if (!hasTruncate) {
            List<ChatMessage> chatMessages = CompletionUtil.truncateChatMessages(chatCompletionRequest.getMessages(), maxMsg);
            chatCompletionRequest.setMessages(chatMessages);
        }

        if (chatCompletionRequest.getStream() != null && chatCompletionRequest.getStream()) {
            try {
                Observable<ChatCompletionResult> result;
                if (enableQueueHandle) {
                    result = queueSchedule.streamSchedule(chatCompletionRequest, indexSearchDataList);
                } else {
                    result = completionsService.streamCompletions(chatCompletionRequest, userLlmAdapters, indexSearchDataList);
                }
                resp.setHeader("Content-Type", "text/event-stream;charset=utf-8");
                streamOutPrint(medusaService.getPromptInput(chatCompletionRequest), result, context, indexSearchDataList, out);
            } catch (RRException e) {
                resp.setStatus(e.getCode());
                responsePrint(resp, e.getMsg());
            }

        } else {
            try {
                ChatCompletionResult result;
                if (enableQueueHandle) {
                    result = queueSchedule.schedule(chatCompletionRequest, indexSearchDataList);
                } else {
                    result = completionsService.completions(chatCompletionRequest, userLlmAdapters, indexSearchDataList);
                }
                if (context != null) {
                    CompletionUtil.populateContext(result, indexSearchDataList, context.getContext());
                    addChunkIds(result, context);
                }
                if (medusaMonitor != null) {
                    medusaMonitor.put(medusaService.getPromptInput(chatCompletionRequest), result);
                }
                responsePrint(resp, toJson(result));
            } catch (RRException e) {
                resp.setStatus(e.getCode());
                responsePrint(resp, e.getMsg());
            }
        }
    }

    private void addChunkIds(ChatCompletionResult result, GetRagContext context) {
        result.getChoices().forEach(choice -> {
            ChatMessage message = choice.getMessage();
            message.setContextChunkIds(context.getChunkIds());
        });
    }

    private void outPrintChatCompletion(HttpServletResponse resp, ChatCompletionRequest chatCompletionRequest, ChatCompletionResult chatCompletionResult) throws IOException {
        if (Boolean.TRUE.equals(chatCompletionRequest.getStream())) {
            streamOutPrint(resp, chatCompletionResult);
        } else {
            outPrint(resp, chatCompletionResult);
        }
    }

    private void outPrintJson(HttpServletResponse resp, ChatCompletionRequest chatCompletionRequest, String s) throws IOException {
        if (Boolean.TRUE.equals(chatCompletionRequest.getStream())) {
            streamOutPrint(resp, s);
        } else {
            outPrint(resp, s);
        }
    }

    private void outPrint(HttpServletResponse resp, ChatCompletionResult chatCompletionResult) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        responsePrint(resp, toJson(chatCompletionResult));
    }

    private void outPrint(HttpServletResponse resp, String json) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        responsePrint(resp, json);
    }

    private void streamOutPrint(HttpServletResponse resp, ChatCompletionResult chatCompletionResult) throws IOException {
        resp.setHeader("Content-Type", "text/event-stream;charset=utf-8");
        PrintWriter out = resp.getWriter();
        out.print("data: " + toJson(chatCompletionResult) + "\n\n");
        out.flush();
        out.print("data: " + "[DONE]" + "\n\n");
        out.flush();
        out.close();
    }

    private void streamOutPrint(HttpServletResponse resp, String json) throws IOException {
        resp.setHeader("Content-Type", "text/event-stream;charset=utf-8");
        PrintWriter out = resp.getWriter();
        out.print("data: " + json + "\n\n");
        out.flush();
        out.print("data: " + "[DONE]" + "\n\n");
        out.close();
    }

    private EnhanceChatCompletionRequest setCustomerModel(HttpServletRequest req, HttpSession session) throws IOException {
        EnhanceChatCompletionRequest chatCompletionRequest = reqBodyToObj(req, EnhanceChatCompletionRequest.class);
        if(chatCompletionRequest.getUserContext() != null) {
            String finger = (String) chatCompletionRequest.getUserContext();
            UserModelPreferenceDao userModelPreferenceDao = new  UserModelPreferenceDao();
            ModelPreferenceDto preference = userModelPreferenceDao.getUserModelPreference( finger);
            if(preference != null) {
                chatCompletionRequest.setModel(preference.getLlm());
                chatCompletionRequest.setUserContext(null);
            }
        }

        String clientIpAddress = ClientIpAddressUtil.getClientIpAddress(req);
        chatCompletionRequest.setIp(clientIpAddress);
        return chatCompletionRequest;
    }

    private static ChatCompletionRequest getCompletionRequest(ChatCompletionRequest chatCompletionRequest) {
        List<Integer> integers = PromptCacheTrigger.analyzeChatBoundariesForIntent(chatCompletionRequest);
        ChatCompletionRequest medusaRequest = null;
        if (!integers.isEmpty()) {
            Integer i = integers.get(0);
            List<ChatMessage> chatMessages = chatCompletionRequest.getMessages().subList(i, chatCompletionRequest.getMessages().size());
            medusaRequest = new ChatCompletionRequest();
            medusaRequest.setTemperature(chatCompletionRequest.getTemperature());
            medusaRequest.setMax_tokens(chatCompletionRequest.getMax_tokens());
            medusaRequest.setCategory(chatCompletionRequest.getCategory());
            medusaRequest.setMessages(chatMessages);
        } else {
            medusaRequest = chatCompletionRequest;
        }
        return medusaRequest;
    }

    private void streamOutPrint(PromptInput promptInput, Observable<ChatCompletionResult> observable, GetRagContext context, List<IndexSearchData> indexSearchDataList, PrintWriter out) {
        final ChatCompletionResult[] lastResult = {null};
        String key = UUID.randomUUID().toString();
        observable.subscribe(
                data -> {
                    lastResult[0] = data;
                    ChatCompletionResult filter = SensitiveWordUtil.filter(data);
                    String msg = gson.toJson(filter);
                    out.print("data: " + msg + "\n\n");
                    out.flush();
                    if (medusaMonitor != null && promptInput != null && filter != null) {
                        medusaMonitor.put(key, new CacheItem(promptInput, filter));
                    }
                },
                e -> {
                    logger.error("", e);
                    medusaMonitor.finish(key);
                },
                () -> {
                    if (lastResult[0] == null) {
                        return;
                    }
                    extracted(lastResult, indexSearchDataList, context, out);
                    out.flush();
                    out.close();
                    if (medusaMonitor != null && promptInput != null && lastResult[0] != null) {
                        medusaMonitor.put(key, new CacheItem(promptInput, lastResult[0]));
                        medusaMonitor.finish(key);
                    }
                }
        );
    }

    private void extracted(ChatCompletionResult[] lastResult, List<IndexSearchData> indexSearchDataList, GetRagContext ragContext, PrintWriter out) {
        if (lastResult[0] != null && !lastResult[0].getChoices().isEmpty()
                && indexSearchDataList != null && !indexSearchDataList.isEmpty()) {
            List<String> imageList = new ArrayList<>();
            for (IndexSearchData indexSearchData : indexSearchDataList) {
                List<String> strLsit = vectorDbService.getImageFiles(indexSearchData);
                if (strLsit != null) {
                    imageList.addAll(strLsit);
                }
            }
            imageList = Optional.ofNullable(imageList).map(list -> list.stream().distinct().collect(Collectors.toList())).orElse(new ArrayList<>());
            List<String> filePaths = ragContext.getFilePaths().stream().distinct().collect(Collectors.toList());
            List<String> filenames = ragContext.getFilenames().stream().distinct().collect(Collectors.toList());
            List<String> chunkIds = ragContext.getChunkIds().stream().distinct().collect(Collectors.toList());

            for (int j = 0; j < lastResult.length; j++) {
                for (int i = 0; i < lastResult[j].getChoices().size(); i++) {
                    ChatMessage message = new ChatMessage();
                    message.setFilename(filenames);
                    message.setFilepath(filePaths);
                    message.setContext(ragContext.getContext());
                    message.setContextChunkIds(chunkIds);
                    message.setImageList(imageList);
                    message.setContent("");
                    lastResult[j].getChoices().get(i).setMessage(message);
                }
            }

            out.print("data: " + gson.toJson(lastResult[0]) + "\n\n");
            out.flush();
        }
        out.print("data: " + "[DONE]" + "\n\n");
    }

    private void embeddings(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        OpenAIEmbeddingRequest request = gson.fromJson(requestToJson(req), OpenAIEmbeddingRequest.class);
        Embeddings embeddings = EmbeddingFactory.getEmbedding(config.getLLM().getEmbedding());
        List<List<Float>> embeddingDataList = embeddings.createEmbedding(request.getInput());
        Map<String, Object> result = new HashMap<>();
        if (embeddingDataList.isEmpty()) {
            result.put("status", "failed");
        } else {
            result.put("status", "success");
            result.put("data", embeddingDataList);
        }
        responsePrint(resp, toJson(result));
    }
}
