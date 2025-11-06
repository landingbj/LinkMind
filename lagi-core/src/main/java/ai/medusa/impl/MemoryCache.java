package ai.medusa.impl;

import ai.common.utils.LRUCache;
import ai.llm.service.CompletionsService;
import ai.medusa.pojo.PromptInput;
import ai.medusa.utils.PromptInputUtil;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.utils.LagiGlobal;
import ai.utils.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MemoryCache {
    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(MemoryCache.class);
    private final CompletionsService completionsService = new CompletionsService();
    private static final MemoryCache INSTANCE = new MemoryCache();
    private static final ExecutorService MEMORY_EXECUTOR = new ThreadPoolExecutor(
            5,
            10,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            runnable -> {
                Thread thread = new Thread(runnable);
                thread.setName("memory-cache-worker-" + thread.getId());
                thread.setDaemon(true);
                return thread;
            }
    );

    private final LRUCache<PromptInput, String> cache;

    private MemoryCache() {
        this.cache = new LRUCache<>(10000);
    }

    public static MemoryCache getInstance() {
        return INSTANCE;
    }

    public String get(PromptInput promptInput) {
        PromptInput lastPromptInput = PromptInputUtil.getLastPromptInput(promptInput);
        return cache.get(lastPromptInput);
    }

    public void computeAndCacheSummary(PromptInput promptInput, ChatCompletionRequest originalRequest) {
        if (promptInput == null || originalRequest == null) {
            return;
        }
        MEMORY_EXECUTOR.submit(() -> {
            try {
                String conversationText = buildConversationText(
                        promptInput.getPromptList(),
                        promptInput.getAssistantPrompts(),
                        true);
                if (StrUtil.isBlank(conversationText)) {
                    return;
                }
                String summary = summarizeConversation(conversationText, originalRequest);
                if (StrUtil.isBlank(summary)) {
                    return;
                }
                cache.put(promptInput, summary);
            } catch (Exception e) {
                logger.warn("Failed to compute conversation summary asynchronously.", e);
            }
        });
    }

    public void remove(PromptInput promptInput) {
        cache.remove(promptInput);
    }

    public String buildConversationText(List<String> prompts, List<String> assistantPrompts, boolean includeAssistantMessages) {
        if (prompts == null || prompts.isEmpty()) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        if (includeAssistantMessages && assistantPrompts != null && !assistantPrompts.isEmpty()) {
            for (int i = 0; i < prompts.size() && i < assistantPrompts.size(); i++) {
                builder.append("用户消息")
                        .append(i + 1)
                        .append("：")
                        .append(StrUtil.nullToEmpty(prompts.get(i)))
                        .append("\n")
                        .append("模型回复消息")
                        .append(i + 1)
                        .append("：")
                        .append(StrUtil.nullToEmpty(assistantPrompts.get(i)))
                        .append("\n");
            }
        } else {
            for (int i = 0; i < prompts.size(); i++) {
                builder.append("用户消息")
                        .append(i + 1)
                        .append("：")
                        .append(StrUtil.nullToEmpty(prompts.get(i)))
                        .append("\n");
            }
        }
        return builder.toString().trim();
    }

    private String summarizeConversation(String conversation, ChatCompletionRequest originalRequest) {
        String promptTemplate = ResourceUtil.loadAsString("/prompts/medusa_memory_summary.md");
        if (StrUtil.isBlank(promptTemplate)) {
            logger.warn("Failed to load medusa memory summary prompt template.");
            return null;
        }

        String prompt = promptTemplate.replace("${{CONVERSATION}}", conversation);

        List<ChatMessage> summaryMessages = new ArrayList<>();
        summaryMessages.add(ChatMessage.builder()
                .role(LagiGlobal.LLM_ROLE_USER)
                .content(prompt)
                .build());

        ChatCompletionRequest summaryRequest = completionsService.getCompletionsRequest(
                summaryMessages,
                0.1,
                4096,
                null);
        summaryRequest.setModel(originalRequest.getModel());

        try {
            ChatCompletionResult result = completionsService.completions(summaryRequest);
            if (result == null || result.getChoices() == null || result.getChoices().isEmpty()) {
                return null;
            }
            ChatMessage message = result.getChoices().get(0).getMessage();
            if (message == null || StrUtil.isBlank(message.getContent())) {
                return null;
            }
//            logger.info("Summary conversation: {}", message.getContent());
            return message.getContent().trim();
        } catch (Exception e) {
            if (logger != null) {
                logger.warn("Failed to summarize conversation history.", e);
            }
            return null;
        }
    }
}
