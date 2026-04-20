package ai.llm.adapter.impl;

import ai.common.ModelService;
import ai.common.exception.RRException;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.hook.HookService;
import ai.llm.pojo.EnhanceChatCompletionRequest;
import ai.llm.pojo.ModelContext;
import ai.llm.utils.PriorityLock;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import cn.hutool.core.bean.BeanUtil;
import io.reactivex.Observable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;



@Slf4j
public class ProxyLlmAdapter extends ModelService implements ILlmAdapter {

    @Getter
    private final ILlmAdapter llmAdapter;


    private PriorityLock priorityLock = null;
    private final HookService hookService = HookService.getInstance();

    public ProxyLlmAdapter(ILlmAdapter llmAdapter) {
        this.llmAdapter = llmAdapter;
        if(llmAdapter instanceof ModelService) {
            ModelService modelService = (ModelService) llmAdapter;
            BeanUtil.copyProperties(modelService, this);
            if(modelService.getConcurrency() != null) {
                this.priorityLock = new PriorityLock(modelService.getConcurrency());
            }
        }
    }


    private Observable<ChatCompletionResult> applyStreamHookOnce(ChatCompletionRequest request, ModelContext context) {
        if(Boolean.TRUE.equals(request.getEnableAfter())) {
            request.setEnableAfter(null);
            return hookService != null ? hookService.streamApply(context) : context.getStreamResult();
        }
        return context.getStreamResult();
    }

    private ChatCompletionResult applyAfterHookOnce(ChatCompletionRequest request, ModelContext context, ChatCompletionResult completions) {
        if(Boolean.TRUE.equals(request.getEnableAfter())) {
            request.setEnableAfter(null);
            context.setResult(completions);
            return hookService != null ? hookService.AfterModel(context) : completions;
        }
        return completions;
    }

    @Override
    public ChatCompletionResult completions(ChatCompletionRequest request) {
        // Prevent stack overflow caused by calling large models within the hook function
        ModelContext context = ModelContext.builder().request(request).adapter(this.llmAdapter).build();
        if(Boolean.TRUE.equals(request.getEnableHook())) {
            request.setEnableHook(null);
            if (hookService != null) {
                ChatCompletionRequest afterHook = hookService.beforeModel(context);
                if (afterHook != null) {
                    request = afterHook;
                }
            }
        }
        if(!(request instanceof EnhanceChatCompletionRequest)) {
            ChatCompletionResult completions = delegateCompletions(request);
            return applyAfterHookOnce(request, context, completions);
        }
        if(this.priorityLock == null) {
            ChatCompletionResult completions = delegateCompletions(request);
            return applyAfterHookOnce(request, context, completions);
        }
        Integer priority = ((EnhanceChatCompletionRequest) request).getPriority();
        if(priority == null) {
            ChatCompletionResult completions = delegateCompletions(request);
            return applyAfterHookOnce(request, context, completions);
        }
        try {
            this.priorityLock.lock(priority);
            ((EnhanceChatCompletionRequest) request).setPriority(null);
            ChatCompletionResult completions = delegateCompletions(request);
            return applyAfterHookOnce(request, context, completions);
        } finally {
            this.priorityLock.unlock(priority);
        }
    }




    @Override
    public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest chatCompletionRequest) {
        // Prevent stack overflow caused by calling large models within the hook function
        ModelContext context = ModelContext.builder().request(chatCompletionRequest).adapter(this.llmAdapter).build();
        if(Boolean.TRUE.equals(chatCompletionRequest.getEnableHook())) {
            chatCompletionRequest.setEnableHook(null);
            if (hookService != null) {
                ChatCompletionRequest afterHook = hookService.beforeModel(context);
                if (afterHook != null) {
                    chatCompletionRequest = afterHook;
                }
            }
        }
        if(!(chatCompletionRequest instanceof EnhanceChatCompletionRequest)) {
            Observable<ChatCompletionResult> chatCompletionResultObservable = delegateStreamCompletions(chatCompletionRequest);
            context.setStreamResult(chatCompletionResultObservable);
            return applyStreamHookOnce(chatCompletionRequest, context);
        }
        if(this.priorityLock == null) {
            Observable<ChatCompletionResult> chatCompletionResultObservable = delegateStreamCompletions(chatCompletionRequest);
            context.setStreamResult(chatCompletionResultObservable);
            return applyStreamHookOnce(chatCompletionRequest, context);
        }
        Integer priority = ((EnhanceChatCompletionRequest) chatCompletionRequest).getPriority();
        if(priority == null) {
            Observable<ChatCompletionResult> chatCompletionResultObservable = delegateStreamCompletions(chatCompletionRequest);
            context.setStreamResult(chatCompletionResultObservable);
            return applyStreamHookOnce(chatCompletionRequest, context);
        }
        Observable<ChatCompletionResult> completions = null;
        try {
            this.priorityLock.lock(priority);
            ((EnhanceChatCompletionRequest) chatCompletionRequest).setPriority(null);
            completions = delegateStreamCompletions(chatCompletionRequest);
            context.setStreamResult(completions);
            completions = applyStreamHookOnce(chatCompletionRequest, context);
            if(completions != null) {
                return completions.doFinally(() -> {
                    this.priorityLock.unlock(priority);
                });
            } else {
                return null;
            }
        } finally {
            if(completions == null) {
                this.priorityLock.unlock(priority);
            }
        }
    }

    private ChatCompletionResult delegateCompletions(ChatCompletionRequest request) {
        ModelService inner = (ModelService) llmAdapter;
        if (!inner.hasKeyPool()) {
            return llmAdapter.completions(request);
        }
        if ("polling".equals(inner.getKeyRoute())) {
            inner.setApiKey(inner.selectNextKey());
            return llmAdapter.completions(request);
        }
        List<String> keys = inner.getApiKeys();
        RRException lastError = null;
        for (int i = 0; i < keys.size(); i++) {
            inner.setApiKey(keys.get(i));
            try {
                return llmAdapter.completions(request);
            } catch (RRException e) {
                lastError = e;
                log.warn("API key [{}] failed, trying next ({}/{})", i, i + 1, keys.size());
            }
        }
        throw lastError;
    }

    private Observable<ChatCompletionResult> delegateStreamCompletions(ChatCompletionRequest request) {
        ModelService inner = (ModelService) llmAdapter;
        if (!inner.hasKeyPool()) {
            return llmAdapter.streamCompletions(request);
        }
        if ("polling".equals(inner.getKeyRoute())) {
            inner.setApiKey(inner.selectNextKey());
            return llmAdapter.streamCompletions(request);
        }
        List<String> keys = inner.getApiKeys();
        RRException lastError = null;
        for (int i = 0; i < keys.size(); i++) {
            inner.setApiKey(keys.get(i));
            try {
                return llmAdapter.streamCompletions(request);
            } catch (RRException e) {
                lastError = e;
                log.warn("Stream API key [{}] failed, trying next ({}/{})", i, i + 1, keys.size());
            }
        }
        throw lastError;
    }
}
