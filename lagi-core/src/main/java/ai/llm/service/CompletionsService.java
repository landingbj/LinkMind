/*
 * This program is commercial software; you can only redistribute it and/or modify
 * it under the WARRANTY of Beijing Landing Technologies Co. Ltd.
 *
 * You should have received a copy license along with this program;
 * If not, write to Beijing Landing Technologies, service@landingbj.com.
 */

package ai.llm.service;

import java.util.*;
import java.util.stream.Collectors;

import ai.common.ModelService;
import ai.common.exception.RRException;
import ai.common.pojo.IndexSearchData;
import ai.config.ContextLoader;
import ai.config.pojo.Policy;
import ai.llm.adapter.ILlmAdapter;
import ai.common.pojo.Backend;
import ai.llm.pojo.EnhanceChatCompletionRequest;
import ai.llm.pojo.GetRagContext;
import ai.llm.utils.LLMErrorConstants;
import ai.llm.utils.PolicyConstants;
import ai.llm.utils.PollingScheduler;
import ai.llm.utils.PriorityLock;
import ai.manager.AIManager;
import ai.manager.LlmManager;
import ai.mr.IMapper;
import ai.mr.mapper.llm.*;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.router.Route;
import ai.router.Routers;
import ai.router.pojo.RouteCompletionResult;
import ai.utils.LagiGlobal;
import ai.utils.SensitiveWordUtil;
import ai.utils.qa.ChatCompletionUtil;
import cn.hutool.core.bean.BeanUtil;
import io.reactivex.Observable;
import lombok.extern.slf4j.Slf4j;
import weixin.tools.TulingThread;

@Slf4j
public class CompletionsService implements ChatCompletion {
    private static TulingThread tulingProcessor = null;
    private static final double DEFAULT_TEMPERATURE = 0.8;
    private static final int DEFAULT_MAX_TOKENS = 1024;
    private final AIManager<ILlmAdapter> llmAdapterAIManager;

    static {
        if (tulingProcessor == null) {
            tulingProcessor = new TulingThread();
            tulingProcessor.setDaemon(true);
            tulingProcessor.start();
        }
    }

    public CompletionsService() {
        this.llmAdapterAIManager = LlmManager.getInstance();
    }

    public CompletionsService(AIManager<ILlmAdapter> llmAdapterAIManager) {
        this.llmAdapterAIManager = llmAdapterAIManager;
    }

    public static Policy getPolicy() {
        Policy policy = BeanUtil.copyProperties(ContextLoader.configuration.getFunctions().getChat(), Policy.class);
        return policy;
    }

    public static String getRoute() {
        return ContextLoader.configuration.getFunctions().getChat().getRoute();
    }

    public ChatCompletionResult completions(ChatCompletionRequest chatCompletionRequest, List<ILlmAdapter> userAdapters, List<IndexSearchData> indexSearchDataList) {
        // The execution model is specified
        RRException r = new RRException(LLMErrorConstants.OTHER_ERROR, "{\"error\":\"backend is not enabled.\"}");
        if (chatCompletionRequest.getModel() != null) {
            ILlmAdapter appointAdapter = llmAdapterAIManager.getAdapter(chatCompletionRequest.getModel());
            if(appointAdapter == null) {
                Map<String, ILlmAdapter> userAdapterMap = userAdapters.stream().collect(Collectors.toMap(a -> ((ModelService) a).getModel(), a -> a));
                appointAdapter = userAdapterMap.get(chatCompletionRequest.getModel());
            }
            if (appointAdapter != null && notFreezingAdapter(appointAdapter)) {
                try {
                    ChatCompletionResult result = SensitiveWordUtil.filter(appointAdapter.completions(chatCompletionRequest));
                    unfreezeAdapter(appointAdapter);
                    return result;
                } catch (RRException e) {
                    freezingAdapterByErrorCode(appointAdapter, e.getCode());
                    throw e;
                }
            }
        }
        chatCompletionRequest.setModel(null);
        String routeRule = getRoute();
        Route route = Routers.getInstance().getRoute(routeRule);
        RouteCompletionResult result = route.invokeLlm(chatCompletionRequest);
        if (result != null) {
            return result.getResult();
        }
        throw r;
    }

    public ChatCompletionResult completions(ILlmAdapter adapter, ChatCompletionRequest chatCompletionRequest) {
        if (adapter != null) {
            return adapter.completions(chatCompletionRequest);
        }
        return null;
    }

    public ChatCompletionResult completions(ChatCompletionRequest chatCompletionRequest) {
        return completions(chatCompletionRequest, Collections.emptyList(), null);
    }


    public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest chatCompletionRequest) {
        return streamCompletions(chatCompletionRequest, Collections.emptyList(), null);
    }

    public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest chatCompletionRequest,  List<ILlmAdapter> userAdapters, List<IndexSearchData> indexSearchDataList) {
        RRException r = new RRException(LLMErrorConstants.NO_AVAILABLE_MODEL, "{\"error\":\"Stream backend is not enabled.\"}");
        if (chatCompletionRequest.getModel() != null) {
            ILlmAdapter adapter = llmAdapterAIManager.getAdapter(chatCompletionRequest.getModel());
            if(adapter == null) {
                Map<String, ILlmAdapter> userAdapterMap = userAdapters.stream().collect(Collectors.toMap(a -> ((ModelService) a).getModel(), a -> a));
                adapter = userAdapterMap.get(chatCompletionRequest.getModel());
            }
            if (adapter != null && notFreezingAdapter(adapter)) {
                try {
                    Observable<ChatCompletionResult> result = adapter.streamCompletions(chatCompletionRequest);
                    unfreezeAdapter(adapter);
                    return result;
                } catch (RRException e) {
                    freezingAdapterByErrorCode(adapter, e.getCode());
                }
            }
        }

        chatCompletionRequest.setModel(null);
        List<ILlmAdapter> adapters = getLlmAdapters(userAdapters, indexSearchDataList);
        String handle = getPolicy().getHandle();
        if (!PolicyConstants.POLLING.equals(handle)) {
            for (ILlmAdapter adapter : adapters) {
                EnhanceChatCompletionRequest copy = new EnhanceChatCompletionRequest();
                BeanUtil.copyProperties(chatCompletionRequest, copy);
                if (adapter != null) {
                    try {
                        Observable<ChatCompletionResult> result = adapter.streamCompletions(copy);
                        unfreezeAdapter(adapter);
                        return result;
                    } catch (RRException e) {
                        freezingAdapterByErrorCode(adapter, e.getCode());
                        r = e;
                    }
                }
            }
        } else {
            return pollingGetStreamChatCompletionResult(chatCompletionRequest, adapters);
        }

        throw r;
    }

    private Observable<ChatCompletionResult> pollingGetStreamChatCompletionResult(ChatCompletionRequest chatCompletionRequest, List<ILlmAdapter> ragAdapters) {
        RRException r = new RRException(LLMErrorConstants.NO_AVAILABLE_MODEL, "{\"error\":\"failover -> backend is not enabled.\"}");

        if (chatCompletionRequest instanceof EnhanceChatCompletionRequest) {
            EnhanceChatCompletionRequest enhanceChatCompletionRequest = (EnhanceChatCompletionRequest) chatCompletionRequest;
            int hash = enhanceChatCompletionRequest.getIp().hashCode();
            while (!ragAdapters.isEmpty()) {
                int index = Math.abs(hash) % ragAdapters.size();
                ILlmAdapter adapter = ragAdapters.get(index);
                if (adapter != null && notFreezingAdapter(adapter)) {
                    ChatCompletionRequest copy = new ChatCompletionRequest();
                    BeanUtil.copyProperties(chatCompletionRequest, copy);
                    try {
                        Observable<ChatCompletionResult> chatCompletionResultObservable = adapter.streamCompletions(copy);
                        unfreezeAdapter(adapter);
                        return chatCompletionResultObservable;
                    } catch (RRException e) {
                        freezingAdapterByErrorCode(adapter, e.getCode());
                        r = e;
                    }
                }
                ragAdapters.remove(index);
            }
        } else {
            List<String> models = ragAdapters.stream().map(adapter -> {
                ModelService modelService = (ModelService) adapter;
                return modelService.getModel();
            }).collect(Collectors.toList());
            while (!models.isEmpty()) {
                String model = PollingScheduler.schedule(models);
                ILlmAdapter appointAdapter = llmAdapterAIManager.getAdapter(model);
                if (appointAdapter != null && notFreezingAdapter(appointAdapter)) {
                    ChatCompletionRequest copy = new ChatCompletionRequest();
                    BeanUtil.copyProperties(chatCompletionRequest, copy);
                    try {
                        Observable<ChatCompletionResult> result = appointAdapter.streamCompletions(copy);
                        unfreezeAdapter(appointAdapter);
                        return result;
                    } catch (RRException e) {
                        freezingAdapterByErrorCode(appointAdapter, e.getCode());
                        r = e;
                    }
                }
                models.remove(model);
            }
        }

        throw r;
    }

    public List<ILlmAdapter> getLlmAdapters(List<ILlmAdapter> userAdapters, List<IndexSearchData> indexSearchDataList) {
        // no effect backend
        List<ILlmAdapter> adapters = new ArrayList<>(userAdapters);
        if (indexSearchDataList != null && !indexSearchDataList.isEmpty()) {
            adapters.addAll(LlmRouterDispatcher.getRagAdapter(llmAdapterAIManager, indexSearchDataList.get(0).getText()));
        } else {
            adapters.addAll( llmAdapterAIManager.getAdapters());
        }
        List<ILlmAdapter> notFreezingAdapters =
                adapters.stream().filter(adapter -> adapter != null && notFreezingAdapter(adapter)).collect(Collectors.toList());

        if (!notFreezingAdapters.isEmpty()) {
            adapters = notFreezingAdapters;
        }
        if (adapters.isEmpty()) {
            throw new RRException(LLMErrorConstants.NO_AVAILABLE_MODEL, "{\"error\" : \"no available model\"}");
        }
        return adapters;
    }


    public Observable<ChatCompletionResult> streamCompletions(ILlmAdapter adapter, ChatCompletionRequest chatCompletionRequest) {
        if (adapter != null) {
            return adapter.streamCompletions(chatCompletionRequest);
        }
        throw new RuntimeException("Stream backend is not enabled.");
    }

    public static boolean notFreezingAdapter(ILlmAdapter adapter) {
        return FreezingService.notFreezingAdapter(adapter);
    }

    public static void freezingAdapterByErrorCode(ILlmAdapter adapter, int errorCode) {
        FreezingService.freezingAdapterByErrorCode(adapter, errorCode);
    }

    public static void freezingAdapter(ILlmAdapter adapter) {
        FreezingService.freezingAdapter(adapter);
    }

    public static void unfreezeAdapter(ILlmAdapter adapter) {
        FreezingService.unfreezeAdapter(adapter);
    }


    private IMapper getMapper(Backend backendConfig, ILlmAdapter adapter) {
        return new UniversalMapper(adapter);
    }

    public void addVectorDBContext(ChatCompletionRequest request, String context) {
        String lastMessage = ChatCompletionUtil.getLastMessage(request);
        String prompt = "以下是背景信息：\n--------------------\n%s\n--------------------\n" +
                "根据上下文信息而非先前知识，回答以下这个问题，回答只基于上下文信息，不要随意扩展和发散内容，不要出现上下文里没有的信息: %s";
        prompt = String.format(prompt, context, lastMessage);
        ChatCompletionUtil.setLastMessage(request, prompt);
    }

    public GetRagContext getRagContext(List<IndexSearchData> indexSearchDataList) {
        if (indexSearchDataList == null || indexSearchDataList.isEmpty()) {
            return null;
        }
        List<String> filePaths = new ArrayList<>();
        List<String> filenames = new ArrayList<>();
        List<String> chunkIds = new ArrayList<>();
        String context = indexSearchDataList.get(0).getText();
        if (indexSearchDataList.get(0).getFilepath() != null && indexSearchDataList.get(0).getFilename() != null) {
            filePaths.addAll(indexSearchDataList.get(0).getFilepath());
            filenames.addAll(indexSearchDataList.get(0).getFilename());
            chunkIds.add(indexSearchDataList.get(0).getId());
        }
        double firstDistance = indexSearchDataList.get(0).getDistance();
        double lastDistance = firstDistance;
        List<Double> diffList = new ArrayList<>();
        for (int i = 1; i < indexSearchDataList.size(); i++) {
            if (i == 1) {
                IndexSearchData data = indexSearchDataList.get(i);
                double diff = data.getDistance() - firstDistance;
                double threshold = diff / firstDistance;
                if (threshold < 0.25) {
                    if (data.getFilepath() != null && data.getFilename() != null) {
                        filePaths.addAll(data.getFilepath());
                        filenames.addAll(data.getFilename());
                        chunkIds.add(data.getId());
                    }
                    context += "\n" + data.getText();
                    lastDistance = data.getDistance();
                    diffList.add(diff);
                } else {
                    break;
                }
            } else if (i == 2) {
                IndexSearchData data = indexSearchDataList.get(i);
                double diff = data.getDistance() - lastDistance;
                if (diff < diffList.get(0) * 0.618) {
                    if (data.getFilepath() != null && data.getFilename() != null) {
                        filePaths.addAll(data.getFilepath());
                        filenames.addAll(data.getFilename());
                        chunkIds.add(data.getId());
                    }
                    context += "\n" + data.getText();
                    lastDistance = data.getDistance();
                    diffList.add(diff);
                } else {
                    break;
                }
            } else {
                IndexSearchData data = indexSearchDataList.get(i);
                double diff = data.getDistance() - lastDistance;
                double average = diffList.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                if (diff < average) {
                    context += "\n" + data.getText();
                    lastDistance = data.getDistance();
                    diffList.add(diff);
                    if (data.getFilepath() != null && data.getFilename() != null) {
                        filePaths.addAll(data.getFilepath());
                        filenames.addAll(data.getFilename());
                        chunkIds.add(data.getId());
                    }
                } else {
                    break;
                }
            }
        }
        return GetRagContext.builder()
                .filenames(filenames)
                .filePaths(filePaths)
                .context(context)
                .chunkIds(chunkIds)
                .build();
    }

    public ChatMessage getChatMessage(String question, String role) {
        ChatMessage message = new ChatMessage();
        message.setRole(role);
        message.setContent(question);
        return message;
    }

    public ChatCompletionRequest getCompletionsRequest(String prompt) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(getChatMessage(prompt, LagiGlobal.LLM_ROLE_USER));
        return getCompletionsRequest(messages);
    }


    public ChatCompletionRequest getCompletionsRequestByPrompts(List<String> prompts) {
        List<ChatMessage> messages = new ArrayList<>();
        prompts.forEach(prompt->{
            messages.add(getChatMessage(prompt, LagiGlobal.LLM_ROLE_USER));
            messages.add(getChatMessage(" ", LagiGlobal.LLM_ROLE_ASSISTANT));
        });
        if (messages.size() > 1) {
            messages.remove(messages.size() - 1);
        }
        return getCompletionsRequest(messages);
    }

    public ChatCompletionRequest getCompletionsRequest(String systemPrompt, String prompt, String category) {
        List<ChatMessage> messages = new ArrayList<>();
        if (systemPrompt != null) {
            messages.add(getChatMessage(systemPrompt, LagiGlobal.LLM_ROLE_SYSTEM));
        }
        messages.add(getChatMessage(prompt, LagiGlobal.LLM_ROLE_USER));
        return getCompletionsRequest(messages, category);
    }

    public ChatCompletionRequest getCompletionsRequest(String systemPrompt, String prompt, String category, int maxTokens) {
        List<ChatMessage> messages = new ArrayList<>();
        if (systemPrompt != null) {
            messages.add(getChatMessage(systemPrompt, LagiGlobal.LLM_ROLE_SYSTEM));
        }
        messages.add(getChatMessage(prompt, LagiGlobal.LLM_ROLE_USER));
        return getCompletionsRequest(messages, DEFAULT_TEMPERATURE, maxTokens,category);
    }

    public ChatCompletionRequest getCompletionsRequest(List<ChatMessage> messages) {
        return getCompletionsRequest(messages, null);
    }

    public ChatCompletionRequest getCompletionsRequest(List<ChatMessage> messages, String category) {
        return getCompletionsRequest(messages, DEFAULT_TEMPERATURE, DEFAULT_MAX_TOKENS, category);
    }

    public ChatCompletionRequest getCompletionsRequest(List<ChatMessage> messages, double temperature, int maxTokens, String category) {
        EnhanceChatCompletionRequest chatCompletionRequest = new EnhanceChatCompletionRequest();
        chatCompletionRequest.setPriority(PriorityLock.LOW_PRIORITY);
        chatCompletionRequest.setTemperature(temperature);
        chatCompletionRequest.setStream(false);
        chatCompletionRequest.setMax_tokens(maxTokens);
        chatCompletionRequest.setMessages(messages);
        chatCompletionRequest.setCategory(category);
        return chatCompletionRequest;
    }
}
