package ai.medusa;

import ai.medusa.pojo.CacheItem;
import ai.medusa.pojo.PromptInput;
import ai.openai.pojo.ChatCompletionResult;
import ai.utils.qa.ChatCompletionUtil;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MedusaMonitor {
    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(MedusaMonitor.class);
    private static final MedusaMonitor instance = new MedusaMonitor();
    private static final Map<String, CacheItem> monitorMap = new ConcurrentHashMap<>();
    private static final Map<String, CacheItem> reasonMonitorMap = new ConcurrentHashMap<>();
    private final MedusaService medusaService = new MedusaService();
    private final LinkedBlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();

    private static final Pattern THINK_TAG_PATTERN = Pattern.compile("(.*?)</think>", Pattern.DOTALL);

    private MedusaMonitor() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            while (true) {
                try {
                    Runnable task = taskQueue.take();
                    task.run();
                } catch (Exception e) {
                    logger.error("Error processing task in MedusaMonitor", e);
                }
            }
        });
    }

    public static MedusaMonitor getInstance() {
        return instance;
    }

    public void put(String key, CacheItem cacheItem) {
        taskQueue.offer(() -> {
            CacheItem result = monitorMap.get(key);
            if (result == null) {
                result = cacheItem;
            } else {
                ChatCompletionResult lastCompletionResult = result.getChatCompletionResult();
                ChatCompletionResult completionResult = cacheItem.getChatCompletionResult();
                String lastContent = getStreamContent(lastCompletionResult);
                String content = getStreamContent(completionResult);
                if (content != null) {
                    if (lastContent != null) {
                        content = lastContent + content;
                    }
                    completionResult.getChoices().get(0).getDelta().setContent(content);
                    result.setChatCompletionResult(completionResult);
                }
            }
            monitorMap.put(key, result);
            if (reasonMonitorMap.containsKey(key)) {
                return;
            }
            String reasonContent = extractReasonContent(cacheItem.getChatCompletionResult());
            if (reasonContent != null && !reasonContent.isEmpty()) {
                PromptInput promptInput = cacheItem.getPromptInput();
                promptInput.getMedusaMetadata().setReasoningContent(reasonContent);
                medusaService.triggerCachePutAndDiversify(promptInput);
                reasonMonitorMap.put(key, cacheItem);
            }
        });
    }

    private String extractReasonContent(ChatCompletionResult chatCompletionResult) {
        String content = getContent(chatCompletionResult);
        if (content != null) {
            Matcher matcher = THINK_TAG_PATTERN.matcher(content);
            if (matcher.find()) {
                return matcher.group(1).trim().replace("<think>", "").trim();
            } else {
                return null;
            }
        }
        return null;
    }

    public void finish(String key) {
        taskQueue.offer(() -> {
            CacheItem cacheItem = monitorMap.remove(key);
            if (cacheItem == null) {
                return;
            }
            if (!reasonMonitorMap.containsKey(key)) {
                String content = getStreamContent(cacheItem.getChatCompletionResult());
                if (content.trim().startsWith("<think>")) {
                    PromptInput promptInput = cacheItem.getPromptInput();
                    promptInput.getMedusaMetadata().setReasoningContent(content.replace("<think>", "").trim());
                    medusaService.triggerCachePut(promptInput);
                    reasonMonitorMap.put(key, cacheItem);
                }
            }
            reasonMonitorMap.remove(key);
            PromptInput promptInput = cacheItem.getPromptInput();
            ChatCompletionResult chatCompletionResult = cacheItem.getChatCompletionResult();
            if (promptInput != null && chatCompletionResult != null) {
                medusaService.put(promptInput, chatCompletionResult);
            }
        });
    }

    public void put(PromptInput promptInput, ChatCompletionResult completionResult) {
        taskQueue.offer(() -> {
            if (promptInput == null || completionResult == null) {
                return;
            }
            String reasonContent = extractReasonContent(completionResult);
            if (reasonContent == null) {
                reasonContent = getContent(completionResult);
                if (reasonContent.startsWith("<think>")) {
                    promptInput.getMedusaMetadata().setReasoningContent(reasonContent.replace("<think>", ""));
                    medusaService.triggerCachePutAndDiversify(promptInput);
                }
            } else {
                promptInput.getMedusaMetadata().setReasoningContent(reasonContent);
                medusaService.triggerCachePutAndDiversify(promptInput);
            }
            medusaService.put(promptInput, completionResult);
        });
    }

    private String getContent(ChatCompletionResult completionResult) {
        String content = null;
        if (completionResult.getChoices() != null && !completionResult.getChoices().isEmpty() &&
                completionResult.getChoices().get(0).getMessage() != null) {
            content = ChatCompletionUtil.getFirstAnswer(completionResult).trim();
        }
        return content;
    }

    private String getStreamContent(ChatCompletionResult completionResult) {
        String content = null;
        if (completionResult.getChoices() != null && !completionResult.getChoices().isEmpty() &&
                completionResult.getChoices().get(0).getDelta() != null && completionResult.getChoices().get(0).getDelta().getContent() != null) {
            content = ChatCompletionUtil.getFirstDelta(completionResult).trim();
        }
        return content;
    }
}