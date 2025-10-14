package ai.servlet.api;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import ai.common.ModelService;
import ai.common.exception.RRException;
import ai.common.pojo.Configuration;
import ai.common.pojo.IndexSearchData;
import ai.common.pojo.Medusa;
import ai.config.ContextLoader;
import ai.config.pojo.RAGFunction;
import ai.dto.ModelPreferenceDto;
import ai.embedding.EmbeddingFactory;
import ai.embedding.Embeddings;
import ai.embedding.pojo.OpenAIEmbeddingRequest;
import ai.llm.pojo.EnhanceChatCompletionRequest;
import ai.llm.pojo.GetRagContext;
import ai.llm.schedule.QueueSchedule;
import ai.llm.service.CompletionsService;
import ai.llm.service.LlmRouterDispatcher;
import ai.llm.utils.CompletionUtil;
import ai.medusa.MedusaService;
import ai.medusa.pojo.CacheItem;
import ai.medusa.pojo.PromptInput;
import ai.medusa.MedusaMonitor;
import ai.medusa.utils.PromptCacheTrigger;
import ai.medusa.utils.PromptInputUtil;
import ai.openai.pojo.ChatCompletionChoice;
import ai.router.pojo.LLmRequest;
import ai.servlet.BaseServlet;
import ai.utils.ClientIpAddressUtil;
import ai.vector.VectorDbService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.utils.MigrateGlobal;
import ai.utils.SensitiveWordUtil;
import ai.utils.qa.ChatCompletionUtil;
import ai.vector.VectorCacheLoader;
import ai.worker.DefaultWorker;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        } else if(method.equals("go")) {
            this.go(req, resp);
        } else if(method.equals("isMedusa")) {
            this.isMedusa(req, resp);
        } else if(method.equals("isRAG")) {
            this.isRAG(req, resp);
        }
    }

    private void isRAG(HttpServletRequest req, HttpServletResponse resp) throws IOException{
        resp.setHeader("Content-Type", "application/json;charset=utf-8");
        String enable = req.getParameter("RAG");
        Map<String, Object> map = new HashMap<>();
        map.put("status", "success");
        if (enable!=null&&!"".equals(enable)){
            if(enable.equals("true")){
                map.put("RAG", "RAG已开启");
                this.RAG_ENABLE = true;
            }else {
                this.RAG_ENABLE = false;
                map.put("RAG", "RAG已关闭");
            }
        }
        PrintWriter out = resp.getWriter();
        out.print(gson.toJson(map));
        out.flush();
        out.close();
    }

    private void isMedusa(HttpServletRequest req, HttpServletResponse resp) throws IOException{
        resp.setHeader("Content-Type", "application/json;charset=utf-8");
        String enable = req.getParameter("medusa");
        Map<String, Object> map = new HashMap<>();
        map.put("status", "success");
        if (enable!=null&&!"".equals(enable)){
            if(enable.equals("true")){
                map.put("medusa", "medusa已开启");
                this.MEDUSA_ENABLE = true;
            }else {
                this.MEDUSA_ENABLE = false;
                map.put("medusa", "medusa已关闭");
            }
        }
        PrintWriter out = resp.getWriter();
        out.print(gson.toJson(map));
        out.flush();
        out.close();

    }

    private void go(HttpServletRequest req, HttpServletResponse resp) throws IOException{
        resp.setContentType("application/json;charset=utf-8");
        LLmRequest lLmRequest = reqBodyToObj(req, LLmRequest.class);
        ChatCompletionResult work = defaultWorker.work(lLmRequest.getWorker(), lLmRequest);
        if(Boolean.FALSE.equals(lLmRequest.getStream())) {
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
                ChatCompletionResult filter = SensitiveWordUtil.filter(result);
                String msg = gson.toJson(filter);
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

    private void sensitiveWordStreamOutPrint( ChatCompletionRequest chatCompletionRequest, HttpServletResponse resp, PrintWriter out) {
        resp.setHeader("Content-Type", "text/event-stream;charset=utf-8");
        String id = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "");
        out.print("data: " + gson.toJson(CompletionUtil.generateStreamCompletionResult(id, SensitiveWordUtil.getRequestFilterMessage(), chatCompletionRequest.getModel())) + "\n\n");
        out.flush();
        out.print("data: " + gson.toJson(CompletionUtil.generateStreamCompletionResult(id, "", "content_filter", chatCompletionRequest.getModel())) + "\n\n");
        out.flush();
        out.print("data: " + "[DONE]" + "\n\n");
        out.flush();
        out.close();
    }

    private void completions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        PrintWriter out = resp.getWriter();
        HttpSession session = req.getSession();
        if (RAG_ENABLE == null){
            RAG_ENABLE = RAG_CONFIG.getEnable();
        }
        ChatCompletionRequest chatCompletionRequest = setCustomerModel(req, session);

        if (chatCompletionRequest.getStream() != null && chatCompletionRequest.getStream() &&
                SensitiveWordUtil.enableRequestFilter() && SensitiveWordUtil.filter(chatCompletionRequest)) {
            sensitiveWordStreamOutPrint(chatCompletionRequest, resp, out);
            return;
        }

        boolean isMultiModal = CompletionUtil.isMultiModal(chatCompletionRequest);

        ChatCompletionResult chatCompletionResult = null;

        List<IndexSearchData> indexSearchDataList = null;
        String SAMPLE_COMPLETION_RESULT_PATTERN = "{\"created\":0,\"choices\":[{\"index\":0,\"message\":{\"content\":\"%s\"}}]}";

        if (Boolean.TRUE.equals(RAG_ENABLE)) {
            ModelService modelService = (ModelService) LlmRouterDispatcher
                    .getRagAdapter(null).stream().findFirst().orElse(null);
            if(modelService != null  && RAG_CONFIG.getPriority() > modelService.getPriority()) {
                indexSearchDataList = vectorDbService.searchByContext(chatCompletionRequest);
                if(indexSearchDataList.isEmpty()) {
                    String s = String.format(SAMPLE_COMPLETION_RESULT_PATTERN, RAG_CONFIG.getDefaultText());
                    outPrintJson(resp, chatCompletionRequest, s);
                    return ;
                }
            }
        }

        if (MEDUSA_ENABLE==null){
            MEDUSA_ENABLE = MEDUSA_CONFIG.getEnable();
        }
        if(Boolean.TRUE.equals(MEDUSA_ENABLE)) {
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
        if (!isMultiModal) {
            if (chatCompletionRequest.getCategory() != null && Boolean.TRUE.equals(RAG_ENABLE)) {
                String lastMessage = ChatCompletionUtil.getLastMessage(chatCompletionRequest);
                String answer = VectorCacheLoader.get2L2(lastMessage);
                if(StrUtil.isNotBlank(answer)) {
                    outPrintJson(resp,  chatCompletionRequest,String.format(SAMPLE_COMPLETION_RESULT_PATTERN, answer));
                    return;
                }
                if(indexSearchDataList == null) {
                    indexSearchDataList = vectorDbService.searchByContext(chatCompletionRequest);
                }
                if (indexSearchDataList != null && !indexSearchDataList.isEmpty()) {
                    context = completionsService.getRagContext(indexSearchDataList);
                    String contextStr = CompletionUtil.truncate(context.getContext());
                    context.setContext(contextStr);
                    completionsService.addVectorDBContext(chatCompletionRequest, contextStr);
                    ChatMessage chatMessage = chatCompletionRequest.getMessages().get(chatCompletionRequest.getMessages().size() - 1);
                    chatCompletionRequest.setMessages(Lists.newArrayList(chatMessage));
                    hasTruncate = true;
                }
            } else {
                indexSearchDataList = null;
            }
            if(!hasTruncate) {
                List<ChatMessage> chatMessages = CompletionUtil.truncateChatMessages(chatCompletionRequest.getMessages());
                chatCompletionRequest.setMessages(chatMessages);
            }
        }

        EnhanceChatCompletionRequest enhance = EnhanceChatCompletionRequest.builder()
                .ip(ClientIpAddressUtil.getClientIpAddress(req))
                .build();
        BeanUtil.copyProperties(chatCompletionRequest, enhance);
        chatCompletionRequest = enhance;
        if (chatCompletionRequest.getStream() != null && chatCompletionRequest.getStream()) {
            try {
                Observable<ChatCompletionResult> result;
                if(enableQueueHandle) {
                    result = queueSchedule.streamSchedule(chatCompletionRequest, indexSearchDataList);
                } else {
                    result = completionsService.streamCompletions(chatCompletionRequest, indexSearchDataList);
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
                if(enableQueueHandle) {
                    result = queueSchedule.schedule(chatCompletionRequest, indexSearchDataList);
                } else {
                    result = completionsService.completions(chatCompletionRequest, indexSearchDataList);
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
            ChatMessage build = ChatMessage.builder().contextChunkIds(context.getChunkIds()).build();
            CopyOptions copyOption = CopyOptions.create(null, true);
            BeanUtil.copyProperties(message, build, copyOption);
            choice.setMessage(build);
        });
    }


    private void outPrintChatCompletion(HttpServletResponse resp, ChatCompletionRequest chatCompletionRequest, ChatCompletionResult chatCompletionResult) throws IOException {
        if(Boolean.TRUE.equals(chatCompletionRequest.getStream())) {
            streamOutPrint(resp, chatCompletionResult);
        } else {
            outPrint(resp, chatCompletionResult);
        }
    }

    private void outPrintJson(HttpServletResponse resp, ChatCompletionRequest chatCompletionRequest, String s) throws IOException {
        if(Boolean.TRUE.equals(chatCompletionRequest.getStream())) {
            streamOutPrint(resp, s);
        } else {
            outPrint(resp, s);
        }
    }


    private void outPrint(HttpServletResponse resp,  ChatCompletionResult chatCompletionResult) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        responsePrint(resp, toJson(chatCompletionResult));
    }

    private void outPrint(HttpServletResponse resp,  String json) throws IOException {
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


    private ChatCompletionRequest setCustomerModel(HttpServletRequest req, HttpSession session) throws IOException {
        ModelPreferenceDto preference = JSONUtil.toBean((String) session.getAttribute("preference"), ModelPreferenceDto.class) ;
        String json = requestToJson(req);
        logger.info(json);

        ChatCompletionRequest chatCompletionRequest = objectMapper.readValue(json, ChatCompletionRequest.class);
        if(chatCompletionRequest.getModel() == null
                && preference != null
                && preference.getLlm() != null) {
            chatCompletionRequest.setModel(preference.getLlm());
        }
        return chatCompletionRequest;
    }

    private static ChatCompletionRequest getCompletionRequest(ChatCompletionRequest chatCompletionRequest) {
        List<Integer> integers = PromptCacheTrigger.analyzeChatBoundariesForIntent(chatCompletionRequest);
        ChatCompletionRequest medusaRequest = null;
        if(!integers.isEmpty()) {
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
                    ChatCompletionResult filter = SensitiveWordUtil.filter(data, true);
                    if (filter != null && filter.getChoices() != null && !filter.getChoices().isEmpty() && filter.getChoices().get(0).getFinish_reason() != null ) {
                        ChatCompletionChoice chatCompletionChoice = filter.getChoices().get(0);
                        String finishReason = chatCompletionChoice.getFinish_reason();
                        chatCompletionChoice.setFinish_reason(null);
                        String msg = gson.toJson(filter);
                        outputChunk(out, msg);
                        if (SensitiveWordUtil.isBlocked(filter)) {
                            chatCompletionChoice.setFinish_reason("content_filter");
                        } else {
                            chatCompletionChoice.setFinish_reason(finishReason);
                        }
                        chatCompletionChoice.getDelta().setContent("");
                        msg = gson.toJson(filter);
                        outputChunk(out, msg);
                    } else {
                        String msg = gson.toJson(filter);
                        outputChunk(out, msg);
                    }
                    if (medusaMonitor != null && promptInput != null && filter != null) {
                        medusaMonitor.put(key, new CacheItem(promptInput, filter));
                    }
                },
                e -> {
                    logger.error("", e);
                    medusaMonitor.finish(key);
                },
                () -> {
                    if(lastResult[0] == null) {
                        return;
                    }
                    extracted(lastResult,indexSearchDataList,context, out);
                    out.flush();
                    out.close();
                    if (medusaMonitor != null && promptInput != null && lastResult[0] != null) {
                        medusaMonitor.put(key, new CacheItem(promptInput, lastResult[0]));
                        medusaMonitor.finish(key);
                    }
                }
        );
    }

    private void outputChunk(PrintWriter out, String msg) {
        logger.info("data: {}", msg);
        out.print("data: " + msg + "\n\n");
        out.flush();
    }

    private void extracted(ChatCompletionResult[] lastResult, List<IndexSearchData> indexSearchDataList, GetRagContext ragContext, PrintWriter out) {
        if (lastResult[0] != null && !lastResult[0].getChoices().isEmpty()
                && indexSearchDataList != null && !indexSearchDataList.isEmpty()) {
            List<String> imageList = new ArrayList<>();
            for (IndexSearchData indexSearchData : indexSearchDataList) {
                    List<String> strLsit = vectorDbService.getImageFiles(indexSearchData);
                if (strLsit!=null){
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
