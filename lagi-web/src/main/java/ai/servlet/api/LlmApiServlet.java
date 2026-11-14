package ai.servlet.api;

import ai.common.ModelService;
import ai.common.exception.RRException;
import ai.common.pojo.Configuration;
import ai.common.pojo.IndexSearchData;
import ai.common.pojo.Medusa;
import ai.config.ContextLoader;
import ai.config.pojo.LocalKeyConfig;
import ai.config.pojo.RAGFunction;
import ai.dto.ModelPreferenceDto;
import ai.embedding.EmbeddingFactory;
import ai.embedding.Embeddings;
import ai.embedding.pojo.OpenAIEmbeddingRequest;
import ai.llm.pojo.EnhanceChatCompletionRequest;
import ai.llm.pojo.GetRagContext;
import ai.llm.routing.BringYourOwnKeyRouter;
import ai.localkey.LocalBoxKey;
import ai.localkey.LocalBoxKeyService;
import ai.llm.schedule.QueueSchedule;
import ai.llm.service.CompletionsService;
import ai.llm.service.LlmRouterDispatcher;
import ai.llm.utils.CompletionUtil;
import ai.llm.utils.LLMErrorConstants;
import ai.llm.utils.ThinkTagContentFilter;
import ai.manager.LlmManager;
import ai.medusa.MedusaMonitor;
import ai.medusa.MedusaService;
import ai.medusa.pojo.CacheItem;
import ai.medusa.pojo.PromptInput;
import ai.medusa.utils.PromptInputUtil;
import ai.openai.pojo.*;
import ai.openai.utils.MultimodalTraceUtil;
import ai.router.pojo.LLmRequest;
import ai.servlet.BaseServlet;
import ai.utils.AiGlobal;
import ai.utils.ApikeyUtil;
import ai.utils.ClientIpAddressUtil;
import ai.utils.MigrateGlobal;
import ai.utils.ModelNameUtil;
import ai.utils.qa.ChatCompletionUtil;
import ai.vector.VectorCacheLoader;
import ai.vector.VectorDbService;
import ai.worker.DefaultWorker;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
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
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LocalBoxKeyService localBoxKeyService = new LocalBoxKeyService();
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
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setHeader("Content-Type", "application/json;charset=utf-8");
        String url = req.getRequestURI();
        if (url.endsWith("/v1/models") || url.endsWith("/models")) {
            Set<String> modelIds = resolveModelsForRequest(req, resp);
            if (modelIds == null) {
                return;
            }
            PrintWriter out = resp.getWriter();
            Map<String, Object> models = new LinkedHashMap<>();
            models.put("object", "list");
            List<Map<String, Object>> data = new ArrayList<>();
            for (String modelId : modelIds) {
                Map<String, Object> model = new LinkedHashMap<>();
                model.put("id", modelId);
                model.put("object", "model");
                model.put("created", 0);
                model.put("owned_by", "linkmind");
                data.add(model);
            }
            models.put("data", data);
            out.print(gson.toJson(models));
            out.flush();
            out.close();
        }
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
        } else if (method.equals("isMedusa")) {
            this.isMedusa(req, resp);
        } else if (method.equals("isRAG")) {
            this.isRAG(req, resp);
        }
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
                this.RAG_ENABLE = false;
                map.put("RAG", "RAG已关闭");
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
                this.MEDUSA_ENABLE = false;
                map.put("medusa", "medusa已关闭");
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
        ChatCompletionResult work = defaultWorker.work(lLmRequest.getWorker(), lLmRequest);
        if (work == null || work.getChoices() == null || work.getChoices().isEmpty()) {
            ChatCompletionResult error = LLMErrorConstants.errorResponse(lLmRequest,
                    LLMErrorConstants.NO_AVAILABLE_MODEL, "{\"error\":\"agent backend is not available.\"}");
            if (Boolean.FALSE.equals(lLmRequest.getStream())) {
                responsePrint(resp, toJson(error));
            } else {
                streamOutPrint(resp, error);
            }
            return;
        }
        if (Boolean.FALSE.equals(lLmRequest.getStream())) {
            responsePrint(resp, toJson(work));
            return;
        }
        String firstAnswer = ChatCompletionUtil.getFirstAnswer(work);
        convert2streamAndOutput(firstAnswer, resp, work);
    }


    private ChatCompletionResult convertResponse(String response) {
        String format = StrUtil.format("{\"created\":0,\"choices\":[{\"index\":0,\"message\":{\"content\":\"{}\"}}]}", response);
        return JSONUtil.toBean(format, ChatCompletionResult.class);
    }

    private void convert2streamAndOutput(String firstAnswer, HttpServletResponse resp, ChatCompletionResult chatCompletionResult) throws IOException {
        resp.setHeader("Content-Type", "text/event-stream;charset=utf-8");
        PrintWriter out = resp.getWriter();
        int length = 5;
        for (int i = 0; i < firstAnswer.length(); i += length) {
            int end = Math.min(i + length, firstAnswer.length());
            try {
                String substring = firstAnswer.substring(i, end);
                ChatCompletionResult result = convertResponse(substring);
                String msg = gson.toJson(result);
                out.print("data: " + msg + "\n\n");
                out.flush();
            } catch (Exception e) {
            }
            try {
                Thread.sleep(200);
            } catch (Exception ignored) {
                logger.error("produce stream", ignored);
            }
        }
        chatCompletionResult.getChoices().get(0).getMessage().setContent("");
        out.print("data: " + gson.toJson(chatCompletionResult) + "\n\n");
        out.flush();
        out.print("data: " + "[DONE]" + "\n\n");
        out.flush();
        out.close();
    }


    private void completions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession();
        ChatCompletionRequest chatCompletionRequest = setCustomerModel(req, session);
        executeCompletion(req, resp, chatCompletionRequest);
    }

    /**
     * Runs the normal Chat Completions pipeline for an already-normalized request.
     * Protocol adapters call this method so every public API surface shares routing,
     * RAG, queueing, cache, and backend-key selection behaviour.
     */
    protected void executeCompletion(HttpServletRequest req, HttpServletResponse resp,
                                     ChatCompletionRequest chatCompletionRequest) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        PrintWriter out = resp.getWriter();
        String apiKey = chatCompletionRequest.getApiKey();

        ExtraBody extraBody = chatCompletionRequest.getExtraBody();
        String mateUrl = extraBody != null ? extraBody.getMateUrl() : null;

        LocalBoxKey localKey = authenticateLocalKey(apiKey, resp, chatCompletionRequest);
        if (LocalBoxKeyService.isLocalKey(apiKey) && localKey == null) {
            return;
        }
        if (localKey != null) {
            if (!routeLocalKeyRequest(localKey, resp, chatCompletionRequest)) {
                return;
            }
        }

        // A LinkMind SaaS key is authoritative even when a client keeps an
        // unprefixed model name. Resolve it to the configured cloud alias
        // (qwen-plus -> Alibaba/qwen-plus) before considering local BYOK.
        boolean landingKey = localKey == null && ApikeyUtil.isLandingKey(apiKey);
        if (landingKey) {
            String cloudModel = BringYourOwnKeyRouter.resolveCloudModelAlias(chatCompletionRequest.getModel());
            if (cloudModel != null) {
                chatCompletionRequest.setModel(cloudModel);
            } else if (chatCompletionRequest.getModel() != null
                    && !chatCompletionRequest.getModel().contains("/")) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                ChatCompletionResult chatCompletionResult = LLMErrorConstants.errorResponse(chatCompletionRequest,
                        LLMErrorConstants.INVALID_REQUEST_ERROR,
                        "This LinkMind API key requires a configured cloud model alias for the requested model.");
                outPrintChatCompletion(resp, chatCompletionRequest, chatCompletionResult);
                return;
            }
            // Stage 2 is user-owned SaaS access: the submitted LinkMind key,
            // not the box's configured Landing key, is forwarded to cloud.
            // Stage 3 deliberately sets a different selected key below.
            chatCompletionRequest.setSelectedBackendApiKey(apiKey);
            chatCompletionRequest.setUseCallerProvidedApiKey(true);
        }

        BringYourOwnKeyRouter.Decision byok = localKey == null
                ? BringYourOwnKeyRouter.resolve(chatCompletionRequest.getModel(), apiKey)
                : BringYourOwnKeyRouter.Decision.notApplicable();
        if (byok.isDirect()) {
            // A direct-provider model keeps the caller's original key for this
            // request only. It is never written to lagi.yml or a key pool.
            chatCompletionRequest.setSelectedBackendApiKey(apiKey);
            chatCompletionRequest.setUseCallerProvidedApiKey(true);
        } else if (localKey == null && !landingKey && !ApikeyUtil.validateModelApiKey(apiKey)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            ChatCompletionResult chatCompletionResult = LLMErrorConstants.errorResponse(chatCompletionRequest,
                    LLMErrorConstants.UNAUTHORIZED_CODE, LLMErrorConstants.INVALID_API_KEY_MESSAGE);
            outPrintChatCompletion(resp, chatCompletionRequest, chatCompletionResult);
            ApikeyUtil.saveInvalidApiKey(apiKey);
            return;
        }

        boolean isMultiModal = CompletionUtil.isMultiModal(chatCompletionRequest);
        ChatCompletionResult chatCompletionResult;
        List<IndexSearchData> indexSearchDataList = null;
        String SAMPLE_COMPLETION_RESULT_PATTERN = "{\"created\":0,\"choices\":[{\"index\":0,\"message\":{\"content\":\"%s\"}}]}";

        if (RAG_ENABLE == null) {
            RAG_ENABLE = RAG_CONFIG.getEnable();
        }
        if (Boolean.TRUE.equals(RAG_ENABLE)) {
            ModelService modelService = (ModelService) LlmRouterDispatcher
                    .getRagAdapter(null).stream().findFirst().orElse(null);
            if (modelService != null && RAG_CONFIG.getPriority() > modelService.getPriority()) {
                indexSearchDataList = vectorDbService.searchByContext(chatCompletionRequest);
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
//        boolean hasTruncate = false;
        GetRagContext context = null;
        if (!isMultiModal) {
            if (chatCompletionRequest.getCategory() != null && Boolean.TRUE.equals(RAG_ENABLE)) {
                String lastMessage = ChatCompletionUtil.getLastMessage(chatCompletionRequest);
                String answer = VectorCacheLoader.get2L2(lastMessage);
                if (StrUtil.isNotBlank(answer)) {
                    outPrintJson(resp, chatCompletionRequest, String.format(SAMPLE_COMPLETION_RESULT_PATTERN, answer));
                    return;
                }
                if (indexSearchDataList == null) {
                    indexSearchDataList = vectorDbService.searchByContext(chatCompletionRequest);
                }
                if (indexSearchDataList != null && !indexSearchDataList.isEmpty()) {
                    context = completionsService.getRagContext(indexSearchDataList);
                    String contextStr = CompletionUtil.truncate(context.getContext());
                    context.setContext(contextStr);
                    completionsService.addVectorDBContext(chatCompletionRequest, contextStr);
                    ChatMessage chatMessage = chatCompletionRequest.getMessages().get(chatCompletionRequest.getMessages().size() - 1);
                    chatCompletionRequest.setMessages(Lists.newArrayList(chatMessage));
//                    hasTruncate = true;
                }
            } else {
                indexSearchDataList = null;
            }
//            if(!hasTruncate) {
//                List<ChatMessage> chatMessages = CompletionUtil.truncateChatMessages(chatCompletionRequest.getMessages());
//                chatCompletionRequest.setMessages(chatMessages);
//            }
        }

        EnhanceChatCompletionRequest enhance = EnhanceChatCompletionRequest.builder()
                .ip(ClientIpAddressUtil.getClientIpAddress(req))
                .build();
        BeanUtil.copyProperties(chatCompletionRequest, enhance);
        enhance.setTraceId(chatCompletionRequest.getTraceId());
        enhance.setKeyPoolSessionId(chatCompletionRequest.getKeyPoolSessionId());
        enhance.setSelectedBackendApiKey(chatCompletionRequest.getSelectedBackendApiKey());
        enhance.setUseCallerProvidedApiKey(chatCompletionRequest.getUseCallerProvidedApiKey());
        chatCompletionRequest.copyAccountAttributionTo(enhance);
        chatCompletionRequest = enhance;
        if (chatCompletionRequest.getStream() != null && chatCompletionRequest.getStream()) {
            try {
                Observable<ChatCompletionResult> result;
                if (enableQueueHandle) {
                    result = queueSchedule.streamSchedule(chatCompletionRequest, indexSearchDataList);
                } else {
                    result = completionsService.streamCompletions(chatCompletionRequest, indexSearchDataList);
                }
                resp.setHeader("Content-Type", "text/event-stream;charset=utf-8");
                streamOutPrint(medusaService.getPromptInput(chatCompletionRequest), result,
                        context, indexSearchDataList, out, chatCompletionRequest, apiKey);
            } catch (RRException e) {
                handleCompletionException(resp, chatCompletionRequest, mateUrl, e);
            }

        } else {
            try {
                ChatCompletionResult result;
                if (enableQueueHandle) {
                    result = queueSchedule.schedule(chatCompletionRequest, indexSearchDataList);
                } else {
                    result = completionsService.completions(chatCompletionRequest, indexSearchDataList);
                }
                if (context != null) {
                    CompletionUtil.populateContext(result, indexSearchDataList, context.getContext());
                    addChunkIds(result, context);
                }
                stripThinkBlocks(result);
                if (medusaMonitor != null) {
                    medusaMonitor.put(medusaService.getPromptInput(chatCompletionRequest), result);
                }
                responsePrint(resp, toJson(result));
            } catch (RRException e) {
                handleCompletionException(resp, chatCompletionRequest, mateUrl, e);
            }
        }
    }

    /**
     * Models are deliberately selected from configuration rather than a
     * hard-coded provider catalogue. A user-owned provider key sees direct
     * models, a SaaS key sees cloud models, and a local box key sees the union
     * of its local-first route names.
     */
    private Set<String> resolveModelsForRequest(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String apiKey = ApikeyUtil.extractBearerToken(req.getHeader("Authorization"));
        if (StrUtil.isBlank(apiKey)) {
            // Preserve the historical no-auth discovery response for existing
            // consoles and scripts. Key-aware filtering applies only when a
            // caller actually supplies a credential.
            return BringYourOwnKeyRouter.configuredModels();
        }
        LocalKeyConfig localConfig = localKeyConfig();
        if (LocalBoxKeyService.isLocalKey(apiKey)) {
            if (Boolean.FALSE.equals(localConfig.getEnable())) {
                writeModelListError(resp, HttpServletResponse.SC_NOT_FOUND, "local key service is disabled");
                return null;
            }
            try {
                LocalBoxKey localKey = localBoxKeyService.authenticate(apiKey);
                if (localKey == null) {
                    writeModelListError(resp, HttpServletResponse.SC_UNAUTHORIZED,
                            "The local LinkMind API key is invalid or revoked.");
                    return null;
                }
            } catch (Exception e) {
                logger.error("local box key lookup failed while listing models", e);
                writeModelListError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "Local box key storage is unavailable.");
                return null;
            }
            return BringYourOwnKeyRouter.configuredLocalKeyModels(localConfig.getCloudBackend());
        }
        if (ApikeyUtil.isLandingKey(apiKey)) {
            return BringYourOwnKeyRouter.configuredCloudModels(localConfig.getCloudBackend());
        }
        return BringYourOwnKeyRouter.configuredDirectModels();
    }

    private void writeModelListError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        Map<String, String> error = new LinkedHashMap<>();
        error.put("error", message);
        resp.getWriter().print(gson.toJson(error));
    }

    /**
     * Authenticates a box-issued lmk_ key. A non-lmk_ key returns null so the
     * existing SaaS/BYOK paths can continue. An invalid lmk_ request is
     * terminated at the edge and is never sent to a model provider.
     */
    private LocalBoxKey authenticateLocalKey(String apiKey, HttpServletResponse resp,
                                             ChatCompletionRequest request) throws IOException {
        if (!LocalBoxKeyService.isLocalKey(apiKey)) {
            return null;
        }
        if (Boolean.FALSE.equals(localKeyConfig().getEnable())) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            ChatCompletionResult error = LLMErrorConstants.errorResponse(request,
                    LLMErrorConstants.INVALID_REQUEST_ERROR, "Local box key service is disabled.");
            outPrintChatCompletion(resp, request, error);
            return null;
        }
        try {
            LocalBoxKey localKey = localBoxKeyService.authenticate(apiKey);
            if (localKey != null) {
                request.setAccountUserId(localKey.getSubjectId());
                request.setAccountLocalKeyId(localKey.getId());
                return localKey;
            }
        } catch (Exception e) {
            logger.error("local box key lookup failed", e);
            resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            ChatCompletionResult error = LLMErrorConstants.errorResponse(request,
                    LLMErrorConstants.SERVER_ERROR, "Local box key storage is unavailable.");
            outPrintChatCompletion(resp, request, error);
            return null;
        }
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        ChatCompletionResult error = LLMErrorConstants.errorResponse(request,
                LLMErrorConstants.INVALID_AUTHENTICATION_ERROR, "The local LinkMind API key is invalid or revoked.");
        outPrintChatCompletion(resp, request, error);
        return null;
    }

    /**
     * Implements local-key routing: configured direct provider credentials
     * win; otherwise the request is forwarded through the configured Landing
     * backend with its box-owned credential.
     */
    private boolean routeLocalKeyRequest(LocalBoxKey localKey, HttpServletResponse resp,
                                         ChatCompletionRequest request) throws IOException {
        String requestedModel = request.getModel();
        if (BringYourOwnKeyRouter.hasConfiguredDirectBackend(requestedModel)) {
            request.setRouteSource("direct_provider");
            return true;
        }

        String cloudBackend = localKeyConfig().getCloudBackend();
        String cloudModel = BringYourOwnKeyRouter.resolveCloudModelAlias(requestedModel, cloudBackend);
        String cloudApiKey = BringYourOwnKeyRouter.resolveConfiguredCloudApiKey(cloudModel, cloudBackend);
        if (cloudModel != null && cloudApiKey != null) {
            request.setModel(cloudModel);
            request.setSelectedBackendApiKey(cloudApiKey);
            request.setUseCallerProvidedApiKey(true);
            request.setRouteSource("cloud_landing");
            return true;
        }

        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        ChatCompletionResult error = LLMErrorConstants.errorResponse(request,
                LLMErrorConstants.NO_AVAILABLE_MODEL,
                "The requested model has no configured local provider or cloud Landing route.");
        outPrintChatCompletion(resp, request, error);
        return false;
    }

    private LocalKeyConfig localKeyConfig() {
        if (ContextLoader.configuration == null || ContextLoader.configuration.getGeneral() == null
                || ContextLoader.configuration.getGeneral().getLocalKey() == null) {
            return new LocalKeyConfig();
        }
        return ContextLoader.configuration.getGeneral().getLocalKey();
    }

    private void handleCompletionException(HttpServletResponse resp, ChatCompletionRequest chatCompletionRequest,
                                           String mateUrl, RRException e) throws IOException {
        if (mateUrl == null) {
            resp.setStatus(LLMErrorConstants.toHttpStatus(e.getCode()));
            resp.setHeader("X-LinkMind-Error-Code", String.valueOf(e.getCode()));
            responsePrint(resp, e.getMsg());
            return;
        }
        ExtraBody extraBody = chatCompletionRequest.getExtraBody();
        if (extraBody == null) {
            extraBody = new ExtraBody();
            chatCompletionRequest.setExtraBody(extraBody);
        }
        extraBody.setMateUrl(mateUrl);
        ChatCompletionResult chatCompletionResult = LLMErrorConstants.errorResponse(chatCompletionRequest, e.getCode(), e.getMsg());
        outPrintChatCompletion(resp, chatCompletionRequest, chatCompletionResult);
    }

    private void addChunkIds(ChatCompletionResult result, GetRagContext context) {
        result.getChoices().forEach(choice -> {
            ChatMessage message = choice.getMessage();
            ChatMessage build = ChatMessage.builder().contextChunkIds(context.getChunkIds()).build();
            CopyOptions copyOption = CopyOptions.create(null, true);
            BeanUtil.copyProperties(message, build, copyOption);
            choice.setMessage(build);
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
        out.flush();
        out.close();
    }


    protected ChatCompletionRequest setCustomerModel(HttpServletRequest req, HttpSession session) throws IOException {
        ModelPreferenceDto preference = JSONUtil.toBean((String) session.getAttribute("preference"), ModelPreferenceDto.class);
        String json = requestToJson(req);
//        System.out.println("ChatCompletionRequest json: " + json);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        ChatCompletionRequest chatCompletionRequest = objectMapper.readValue(json, ChatCompletionRequest.class);
        // Servlet worker threads are reused across callers. Carry the stable
        // HTTP-session identity only when the caller did not provide its own
        // conversation ID, so the key pool can safely stay sticky.
        if (StrUtil.isBlank(chatCompletionRequest.getSessionId())) {
            chatCompletionRequest.setKeyPoolSessionId(session.getId());
        }
        if (chatCompletionRequest.getModel() == null
                && preference != null
                && preference.getLlm() != null) {
            chatCompletionRequest.setModel(preference.getLlm());
        }
        if (chatCompletionRequest.getModel() != null && chatCompletionRequest.getModel().equals(AiGlobal.DEFAULT_MODEL_ID)) {
            chatCompletionRequest.setModel(resolveDefaultChatModel());
        }
        chatCompletionRequest.setModel(ModelNameUtil.normalizeOpenAiCompatibleModelName(chatCompletionRequest.getModel()));
        String apikey = ApikeyUtil.extractBearerToken(req.getHeader("Authorization"));
        chatCompletionRequest.setApiKey(apikey);
        if (chatCompletionRequest.getUserApiKey() == null) {
            chatCompletionRequest.setUserApiKey(apikey);
        }
        MultimodalTraceUtil.logRequest("api-received", chatCompletionRequest, null);
        return chatCompletionRequest;
    }

    protected String resolveDefaultChatModel() {
        try {
            if (ContextLoader.configuration == null) {
                ContextLoader.loadContext();
            }
            if (ContextLoader.configuration == null
                    || ContextLoader.configuration.getFunctions() == null
                    || ContextLoader.configuration.getFunctions().getChat() == null) {
                return null;
            }
            return normalizeModelName(ContextLoader.configuration.getFunctions().getChat().getConsoleDefaultModel());
        } catch (Exception e) {
            logger.warn("Failed to resolve default chat model", e);
            return null;
        }
    }

    private String normalizeModelName(String model) {
        String normalized = ModelNameUtil.normalizeOpenAiCompatibleModelName(model);
        return normalized == null || normalized.trim().isEmpty() ? null : normalized;
    }

    private void streamOutPrint(PromptInput promptInput, Observable<ChatCompletionResult> observable,
                                GetRagContext context, List<IndexSearchData> indexSearchDataList,
                                PrintWriter out, ChatCompletionRequest chatCompletionRequest, String apiKey) {
        final ChatCompletionResult[] lastResult = {null};
        final Map<Integer, ThinkTagContentFilter> thinkFilters = new HashMap<>();
        final StreamCompletionTerminator terminator = new StreamCompletionTerminator();
        String key = UUID.randomUUID().toString();
        observable.subscribe(
                data -> {
                    lastResult[0] = data;
//                    ChatCompletionResult filter = SensitiveWordUtil.filter(data, true);
//                    SecurityFilterImpl had filter
                    ChatCompletionResult filter = data;
                    if (filter == null) {
                        return;
                    }
                    stripThinkBlocks(filter, thinkFilters);
                    terminator.observe(filter);
                    if (filter.getChoices() != null && !filter.getChoices().isEmpty() && filter.getChoices().get(0).getFinish_reason() != null) {
                        ChatCompletionChoice chatCompletionChoice = filter.getChoices().get(0);
                        String finishReason = chatCompletionChoice.getFinish_reason();
                        chatCompletionChoice.setFinish_reason(null);
                        String msg = gson.toJson(filter);
                        outputChunk(out, msg);
                        chatCompletionChoice.setFinish_reason(finishReason);
                        chatCompletionChoice.getDelta().setContent("");
//                        chatCompletionChoice.getMessage().setContent("");
                        msg = gson.toJson(filter);
                        outputChunk(out, msg);
                    } else {
                        String msg = gson.toJson(filter);
                        outputChunk(out, msg);
                    }
                    if (medusaMonitor != null && promptInput != null) {
                        medusaMonitor.put(key, new CacheItem(promptInput, filter));
                    }
                },
                e -> {
                    logger.error("Error in stream completion", e);
                    try {
                        if (medusaMonitor != null) {
                            medusaMonitor.finish(key);
                        }
                    } catch (Exception ex) {
                        logger.error("Error in medusaMonitor.finish", ex);
                    }
                    try {
                        out.print("data: " + "[DONE]" + "\n\n");
                        out.flush();
                        out.close();
                    } catch (Exception ex) {
                        logger.error("Error closing stream", ex);
                    }
                },
                () -> {
                    if (lastResult[0] == null) {
                        return;
                    }
                    extracted(lastResult, indexSearchDataList, context, out);
                    ChatCompletionResult terminal = terminator.missingFinishChunk();
                    if (terminal != null) {
                        outputChunk(out, gson.toJson(terminal));
                    }
                    out.print("data: [DONE]\n\n");
                    out.flush();
                    out.close();
                    if (medusaMonitor != null && promptInput != null && lastResult[0] != null) {
                        medusaMonitor.put(key, new CacheItem(promptInput, lastResult[0]));
                        medusaMonitor.finish(key);
                    }
                }
        );
    }

    private void stripThinkBlocks(ChatCompletionResult result) {
        if (result == null || result.getChoices() == null) {
            return;
        }
        for (ChatCompletionChoice choice : result.getChoices()) {
            if (choice == null) {
                continue;
            }
            ThinkTagContentFilter filter = new ThinkTagContentFilter();
            ChatMessage message = choice.getMessage();
            ChatMessage delta = choice.getDelta();
            if (message != null && message.getContent() != null) {
                message.setContent(filter.filter(message.getContent()));
            } else if (delta != null && delta.getContent() != null) {
                delta.setContent(filter.filter(delta.getContent()));
            }
        }
    }

    private void stripThinkBlocks(ChatCompletionResult result, Map<Integer, ThinkTagContentFilter> filters) {
        if (result == null || result.getChoices() == null) {
            return;
        }
        for (ChatCompletionChoice choice : result.getChoices()) {
            if (choice == null) {
                continue;
            }
            ThinkTagContentFilter filter = filters.computeIfAbsent(choice.getIndex(), ignored -> new ThinkTagContentFilter());
            ChatMessage delta = choice.getDelta();
            ChatMessage message = choice.getMessage();
            if (delta != null && delta.getContent() != null) {
                String visible = filter.filter(delta.getContent());
                delta.setContent(visible);
                if (message != null && message.getContent() != null) {
                    message.setContent(visible);
                }
            } else if (message != null && message.getContent() != null) {
                message.setContent(filter.filter(message.getContent()));
            }
        }
    }

    private void outputChunk(PrintWriter out, String msg) {
        out.print("data: " + msg + "\n\n");
        out.flush();
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
                    lastResult[0].getChoices().get(i).setMessage(message);

                }
            }

            out.print("data: " + gson.toJson(lastResult[0]) + "\n\n");
            out.flush();
        }
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
