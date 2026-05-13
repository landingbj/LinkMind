package ai.agent.service;

import ai.agent.pojo.SocialChannelMessage;
import ai.agent.util.AgentSocialUtil;
import ai.common.utils.LRUCache;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatMessage;
import ai.openai.pojo.ExtraBody;
import lombok.Value;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * Singleton service that holds shared in-memory state for the social agent:
 *  - an LRU cache of system messages keyed by userId (populated by
 *    {@code AgentFilterImpl} during the {@code beforeModel} hook);
 *  - an LRU cache of per-channel thread-safe queues of
 *    {@link SocialChannelMessage} entries produced by
 *    {@code SocialChannelService#sendMessage} on successful inserts.
 */
public class AgentMessageQueueService {

    private static final int SYSTEM_MESSAGE_CACHE_CAPACITY = 1024;

    private static final int SENT_MESSAGE_QUEUE_CACHE_CAPACITY = 1024;

    private static final AgentMessageQueueService INSTANCE = new AgentMessageQueueService();

    private final LRUCache<String, CachedChatParams> systemMessageCache = new LRUCache<>(SYSTEM_MESSAGE_CACHE_CAPACITY);

    private final LRUCache<Long, Queue<SocialChannelMessage>> sentMessageQueueCache = new LRUCache<>(SENT_MESSAGE_QUEUE_CACHE_CAPACITY);

    private AgentMessageQueueService() {
    }

    public static AgentMessageQueueService getInstance() {
        return INSTANCE;
    }

    /**
     * Caches the system messages extracted from the given request along with
     * the request's {@code model}, {@code temperature}, and {@code apiKey},
     * keyed by {@code extraBody.userId}. No-op when the userId is missing/blank
     * or the messages list is null.
     */
    public void cacheSystemMessages(ChatCompletionRequest request, List<ChatMessage> systemMessages) {
        if (!AgentSocialUtil.isSocialChannelSkillEnabled()) {
            return;
        }
        if (request == null || systemMessages == null) {
            return;
        }
        String userId = extractUserId(request);
        if (isBlank(userId)) {
            return;
        }
        systemMessageCache.put(userId,
                new CachedChatParams(systemMessages, request.getModel(),
                        request.getTemperature(), request.getApiKey()));
    }

    public CachedChatParams getCachedChatParams(String userId) {
        if (isBlank(userId)) {
            return null;
        }
        return systemMessageCache.get(userId);
    }

    public List<ChatMessage> getCachedSystemMessages(String userId) {
        CachedChatParams params = getCachedChatParams(userId);
        return params == null ? null : params.getSystemMessages();
    }

    public LRUCache<String, CachedChatParams> getSystemMessageCache() {
        return systemMessageCache;
    }

    /**
     * Snapshot of the model invocation parameters extracted from a chat
     * completion request, used to faithfully reproduce a user's preferred
     * model/temperature/apiKey when the agent replies on their behalf later.
     */
    @Value
    public static class CachedChatParams {
        List<ChatMessage> systemMessages;
        String model;
        double temperature;
        String apiKey;
    }

    /**
     * Enqueue a successfully persisted social channel message into the queue
     * associated with its {@code channelId}. The queue is created lazily on
     * first use and stored in an LRU cache. Messages without a valid channelId
     * are ignored.
     */
    public void offerSentMessage(SocialChannelMessage message) {
        if (!AgentSocialUtil.isSocialChannelSkillEnabled()) {
            return;
        }
        if (message == null || message.getChannelId() == null) {
            return;
        }
        Queue<SocialChannelMessage> queue = getOrCreateChannelQueue(message.getChannelId());
        queue.offer(message);
    }

    /**
     * Returns the queue for the given channelId, or {@code null} if no queue
     * has been created (or it has expired from the cache).
     */
    public Queue<SocialChannelMessage> getSentMessageQueue(long channelId) {
        return sentMessageQueueCache.get(channelId);
    }

    public LRUCache<Long, Queue<SocialChannelMessage>> getSentMessageQueueCache() {
        return sentMessageQueueCache;
    }

    private Queue<SocialChannelMessage> getOrCreateChannelQueue(long channelId) {
        Queue<SocialChannelMessage> queue = sentMessageQueueCache.get(channelId);
        if (queue != null) {
            return queue;
        }
        synchronized (sentMessageQueueCache) {
            queue = sentMessageQueueCache.get(channelId);
            if (queue == null) {
                queue = new ConcurrentLinkedQueue<>();
                sentMessageQueueCache.put(channelId, queue);
            }
            return queue;
        }
    }

    private static String extractUserId(ChatCompletionRequest request) {
        ExtraBody extraBody = request.getExtraBody();
        if (extraBody == null) {
            return null;
        }
        return extraBody.getUserId();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
