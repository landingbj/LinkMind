package ai.agent.service;

import ai.agent.pojo.SocialChannelMessage;
import ai.agent.util.AgentSocialUtil;
import ai.llm.hook.impl.TokenChargeImpl;
import ai.llm.hook.impl.TokenStatisticsImpl;
import ai.llm.service.CompletionsService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.utils.AiGlobal;
import ai.utils.ResourceUtil;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Background worker that turns queued social channel messages into LLM
 * replies for subscribed users.
 *
 * <p>For each channel that has new messages in
 * {@link AgentMessageQueueService}, it polls up to {@link #MAX_DRAINED_MESSAGES}
 * entries, fetches the latest {@link #CONTEXT_MESSAGE_LIMIT} messages of that
 * channel from the database as context, and iterates over every userId in
 * {@link AgentMessageQueueService#getSystemMessageCache()}. When a user is
 * subscribed to the channel and is not the author of the most recent message,
 * an LLM completion is requested (prepended with the cached system messages)
 * and the response is posted back through
 * {@link SocialChannelService#sendMessage(String, long, String)}.
 */
public class AgentSocialService {

    private static final Logger log = LoggerFactory.getLogger(AgentSocialService.class);

    private static final int DEFAULT_THREADS = 1;
    private static final long DEFAULT_INTERVAL_SECONDS = 5L;
    private static final int MAX_DRAINED_MESSAGES = 5;
    private static final int CONTEXT_MESSAGE_LIMIT = 20;
    private static final int REPLY_MAX_TOKENS = 4096;
    /**
     * The per-channel cap on consecutive agent-authored replies is computed as
     * {@code baseConsecutiveAgentReplies + extraRepliesPerOnlineUser * onlineUsers},
     * where {@code onlineUsers} is the number of users in
     * {@link AgentMessageQueueService#getSystemMessageCache()} who are
     * subscribed to the channel. The counter is reset whenever the newest
     * message is authored by a non-agent (human) user.
     */
    private static final int DEFAULT_BASE_CONSECUTIVE_AGENT_REPLIES = 5;
    private static final int DEFAULT_EXTRA_REPLIES_PER_ONLINE_USER = 100;
    private static final int ABSOLUTE_MAX_CONSECUTIVE_AGENT_REPLIES = 1000;
    /** Default size of the pool that runs LLM reply generations in parallel. */
    private static final int DEFAULT_REPLY_GENERATION_THREADS = 1;
    /** Hard cap on how long a single reply generation may block the dispatch loop. */
    private static final long REPLY_GENERATION_TIMEOUT_SECONDS = 120L;
    /** Default size of the pool that runs per-channel dispatch in parallel. */
    private static final int DEFAULT_CHANNEL_DISPATCH_THREADS = 4;

    private static final String REPLY_PROMPT_RESOURCE = "/prompts/agent_social_reply.md";
    private static final String REPLY_PROMPT_TEMPLATE = loadReplyPromptTemplate();

    private static final AgentSocialService INSTANCE = new AgentSocialService();

    private final SocialChannelService socialChannelService = new SocialChannelService();
    private final AgentMessageQueueService queueService = AgentMessageQueueService.getInstance();
    private final ConcurrentMap<Long, ChannelSession> sessions = new ConcurrentHashMap<>();

    private volatile int baseConsecutiveAgentReplies = DEFAULT_BASE_CONSECUTIVE_AGENT_REPLIES;
    private volatile int extraRepliesPerOnlineUser = DEFAULT_EXTRA_REPLIES_PER_ONLINE_USER;
    private ScheduledExecutorService executor;
    // Pool used to run per-user generateReply calls in parallel. Lazily
    // created on first dispatch (or by start()) so manual runOnce()
    // invocations in tests also benefit from concurrency.
    private volatile ExecutorService replyExecutor;
    private volatile int replyGenerationThreads = DEFAULT_REPLY_GENERATION_THREADS;
    // Pool used to dispatch processChannel() calls in parallel. Kept
    // separate from replyExecutor to avoid deadlock: a channel task waits
    // on reply tasks it submits, so they must come from a different pool.
    private volatile ExecutorService channelExecutor;
    private volatile int channelDispatchThreads = DEFAULT_CHANNEL_DISPATCH_THREADS;
    // Prevents the same channel from being processed concurrently by two
    // overlapping runOnce passes. A pass that finds its channel already
    // in-flight skips it; the next scheduled pass will pick it back up.
    private final Set<Long> inFlightChannels = ConcurrentHashMap.newKeySet();
    private volatile boolean running = false;

    private AgentSocialService() {
    }

    public static AgentSocialService getInstance() {
        return INSTANCE;
    }

    public synchronized void start() {
        start(DEFAULT_THREADS, DEFAULT_INTERVAL_SECONDS);
    }

    public synchronized void start(int threads, long intervalSeconds) {
        if (!AgentSocialUtil.isSocialChannelSkillEnabled()) {
            return;
        }
        if (running) {
            log.info("AgentSocialService already running, skip start request (threads={}, intervalSeconds={})",
                    threads, intervalSeconds);
            return;
        }
        int poolSize = Math.max(1, threads);
        long interval = intervalSeconds <= 0 ? DEFAULT_INTERVAL_SECONDS : intervalSeconds;
        executor = Executors.newScheduledThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "agent-social-worker");
            t.setDaemon(true);
            return t;
        });
        for (int i = 0; i < poolSize; i++) {
            executor.scheduleWithFixedDelay(this::runOnce, interval, interval, TimeUnit.SECONDS);
        }
        ensureReplyExecutor();
        ensureChannelExecutor();
        running = true;
        log.info("AgentSocialService started, poolSize={}, intervalSeconds={}, replyGenerationThreads={}, channelDispatchThreads={}",
                poolSize, interval, replyGenerationThreads, channelDispatchThreads);
    }

    public synchronized void stop() {
        if (!running) {
            log.info("AgentSocialService stop requested but service is not running");
            return;
        }
        running = false;
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        if (replyExecutor != null) {
            replyExecutor.shutdownNow();
            replyExecutor = null;
        }
        if (channelExecutor != null) {
            channelExecutor.shutdownNow();
            channelExecutor = null;
        }
        inFlightChannels.clear();
        log.info("AgentSocialService stopped");
    }

    /**
     * Size of the pool used to issue {@link #generateReply} calls in
     * parallel. Changing this after {@link #start()} replaces the existing
     * pool on the next dispatch pass.
     */
    public synchronized void setReplyGenerationThreads(int threads) {
        int sanitized = Math.max(1, threads);
        if (sanitized == replyGenerationThreads && replyExecutor != null) {
            return;
        }
        replyGenerationThreads = sanitized;
        if (replyExecutor != null) {
            replyExecutor.shutdownNow();
            replyExecutor = null;
        }
        log.info("AgentSocialService replyGenerationThreads set to {}", sanitized);
    }

    public int getReplyGenerationThreads() {
        return replyGenerationThreads;
    }

    private ExecutorService ensureReplyExecutor() {
        ExecutorService local = replyExecutor;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (replyExecutor == null) {
                final AtomicInteger seq = new AtomicInteger();
                ThreadFactory factory = r -> {
                    Thread t = new Thread(r, "agent-social-reply-" + seq.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                };
                replyExecutor = Executors.newFixedThreadPool(Math.max(1, replyGenerationThreads), factory);
            }
            return replyExecutor;
        }
    }

    /**
     * Size of the pool that runs {@link #processChannel(long)} calls in
     * parallel. Must stay distinct from the reply pool so the per-channel
     * task can safely block on reply tasks it submits.
     */
    public synchronized void setChannelDispatchThreads(int threads) {
        int sanitized = Math.max(1, threads);
        if (sanitized == channelDispatchThreads && channelExecutor != null) {
            return;
        }
        channelDispatchThreads = sanitized;
        if (channelExecutor != null) {
            channelExecutor.shutdownNow();
            channelExecutor = null;
        }
        log.info("AgentSocialService channelDispatchThreads set to {}", sanitized);
    }

    public int getChannelDispatchThreads() {
        return channelDispatchThreads;
    }

    private ExecutorService ensureChannelExecutor() {
        ExecutorService local = channelExecutor;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (channelExecutor == null) {
                final AtomicInteger seq = new AtomicInteger();
                ThreadFactory factory = r -> {
                    Thread t = new Thread(r, "agent-social-channel-" + seq.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                };
                channelExecutor = Executors.newFixedThreadPool(Math.max(1, channelDispatchThreads), factory);
            }
            return channelExecutor;
        }
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * Base term of the per-channel reply cap (cap = base + extraPerOnlineUser
     * * onlineUsers). Any positive value is accepted; non-positive values are
     * ignored.
     */
    public void setBaseConsecutiveAgentReplies(int base) {
        if (base <= 0) {
            return;
        }
        this.baseConsecutiveAgentReplies = base;
        log.info("AgentSocialService baseConsecutiveAgentReplies set to {}", base);
    }

    public int getBaseConsecutiveAgentReplies() {
        return baseConsecutiveAgentReplies;
    }

    /**
     * Extra reply budget contributed by each online (subscribed and recently
     * active) agent user. Non-negative values are accepted; setting 0 keeps
     * the cap independent of the channel population.
     */
    public void setExtraRepliesPerOnlineUser(int extra) {
        if (extra < 0) {
            return;
        }
        this.extraRepliesPerOnlineUser = extra;
        log.info("AgentSocialService extraRepliesPerOnlineUser set to {}", extra);
    }

    public int getExtraRepliesPerOnlineUser() {
        return extraRepliesPerOnlineUser;
    }

    /**
     * Computes the effective reply cap for a channel given the current number
     * of online (subscribed agent) users in that channel.
     */
    private int effectiveMaxReplies(int onlineUsers) {
        long cap = (long) baseConsecutiveAgentReplies
                + (long) extraRepliesPerOnlineUser * Math.max(0, onlineUsers);
        if (cap > ABSOLUTE_MAX_CONSECUTIVE_AGENT_REPLIES) {
            cap = ABSOLUTE_MAX_CONSECUTIVE_AGENT_REPLIES;
        }
        return (int) cap;
    }

    // ----------------------------- session control -----------------------------

    /**
     * Stop the agent session for a specific channel. While stopped, the worker
     * will continue to drain the channel's queue (to avoid unbounded memory
     * growth) but will not generate or send any replies for that channel.
     */
    public void stopChannel(long channelId) {
        ChannelSession session = getOrCreateSession(channelId);
        session.enabled = false;
        session.manuallyStopped = true;
        log.info("AgentSocialService channel {} session stopped (manual)", channelId);
    }

    /**
     * Restart the agent session for a specific channel: clears the
     * consecutive-reply counter, clears any manual-stop flag, and re-enables
     * dispatch.
     */
    public void restartChannel(long channelId) {
        ChannelSession session = getOrCreateSession(channelId);
        session.enabled = true;
        session.manuallyStopped = false;
        session.consecutiveAgentReplies.set(0);
        log.info("AgentSocialService channel {} session restarted", channelId);
    }

    public boolean isChannelActive(long channelId) {
        ChannelSession session = sessions.get(channelId);
        return session == null || session.enabled;
    }

    public int getChannelConsecutiveReplies(long channelId) {
        ChannelSession session = sessions.get(channelId);
        return session == null ? 0 : session.consecutiveAgentReplies.get();
    }

    /**
     * Stop dispatch on every known channel. New channels remain active until
     * individually stopped.
     */
    public void stopAllChannels() {
        for (Long channelId : new ArrayList<>(sessions.keySet())) {
            stopChannel(channelId);
        }
    }

    private ChannelSession getOrCreateSession(long channelId) {
        ChannelSession existing = sessions.get(channelId);
        if (existing != null) {
            return existing;
        }
        ChannelSession created = new ChannelSession();
        ChannelSession prior = sessions.putIfAbsent(channelId, created);
        return prior != null ? prior : created;
    }

    /**
     * Single worker pass: iterate every channel queue and dispatch replies.
     * Exposed for tests / manual triggering.
     */
    public void runOnce() {
        try {
            List<Long> channelIds = queueService.getSentMessageQueueCache().keys();
            if (channelIds == null || channelIds.isEmpty()) {
                return;
            }
            ExecutorService pool = ensureChannelExecutor();
            List<Future<?>> futures = new ArrayList<>(channelIds.size());
            for (final Long channelId : channelIds) {
                if (channelId == null) {
                    continue;
                }
                // Skip if this channel is already being processed by an
                // overlapping pass. The next runOnce will pick it back up.
                if (!inFlightChannels.add(channelId)) {
                    log.info("AgentSocialService channel {} already in-flight, skip duplicate dispatch", channelId);
                    continue;
                }
                try {
                    futures.add(pool.submit(() -> {
                        try {
                            processChannel(channelId);
                        } catch (Exception e) {
                            log.warn("AgentSocialService process channel {} failed", channelId, e);
                        } finally {
                            inFlightChannels.remove(channelId);
                        }
                    }));
                } catch (Exception e) {
                    inFlightChannels.remove(channelId);
                    log.warn("AgentSocialService failed to submit channel {} task", channelId, e);
                }
            }
            // Wait for this pass's channel tasks to complete so successive
            // scheduled passes serialize naturally and don't pile up.
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    log.warn("AgentSocialService channel task failed", e.getCause() != null ? e.getCause() : e);
                } catch (Exception e) {
                    log.warn("AgentSocialService channel task interrupted", e);
                    f.cancel(true);
                }
            }
        } catch (Exception e) {
            log.warn("AgentSocialService runOnce failed", e);
        }
    }

    private void processChannel(long channelId) throws Exception {
        Queue<SocialChannelMessage> queue = queueService.getSentMessageQueue(channelId);
        if (queue == null || queue.isEmpty()) {
            return;
        }
        List<SocialChannelMessage> drained = new ArrayList<>(MAX_DRAINED_MESSAGES);
        for (int i = 0; i < MAX_DRAINED_MESSAGES; i++) {
            SocialChannelMessage m = queue.poll();
            if (m == null) {
                break;
            }
            drained.add(m);
        }
        if (drained.isEmpty()) {
            return;
        }
        log.info("AgentSocialService channel {} drained {} pending message(s)", channelId, drained.size());

        // Fetch the latest CONTEXT_MESSAGE_LIMIT messages as conversation context.
        List<SocialChannelMessage> latest = socialChannelService.listLatestMessages(channelId, CONTEXT_MESSAGE_LIMIT, null);
        if (latest == null || latest.isEmpty()) {
            log.info("AgentSocialService channel {} has no context messages, skip dispatch", channelId);
            return;
        }
        // listMessages returns DESC by id; the first entry is the newest.
        SocialChannelMessage newest = latest.get(0);
        String newestAuthor = newest.getUserId();
        if (newestAuthor == null) {
            log.info("AgentSocialService channel {} newest message has no author, skip dispatch", channelId);
            return;
        }
        log.info("AgentSocialService channel {} loaded {} context message(s), newestAuthor={}",
                channelId, latest.size(), newestAuthor);

        List<String> userIds = queueService.getSystemMessageCache().keys();
        Set<String> agentUserIds = new HashSet<>();
        if (userIds != null) {
            for (String id : userIds) {
                if (id != null) {
                    agentUserIds.add(id);
                }
            }
        }

        // "Online" users in this channel: agent-driven users (in
        // systemMessageCache) that are subscribed to this channel. The reply
        // cap scales with this number so larger / more active channels get a
        // proportionally larger reply budget.
        List<String> onlineSubscribers = new ArrayList<>();
        for (String userId : agentUserIds) {
            try {
                if (socialChannelService.isSubscribed(userId, channelId)) {
                    onlineSubscribers.add(userId);
                }
            } catch (Exception e) {
                log.warn("AgentSocialService subscription check failed for user {} on channel {}", userId, channelId, e);
            }
        }
        int effectiveCap = effectiveMaxReplies(onlineSubscribers.size());

        ChannelSession session = getOrCreateSession(channelId);
        boolean humanTrigger = false;
        String humanTriggerAuthor = null;
        for (SocialChannelMessage m : drained) {
            if (!m.isAgentAutoSent()) {
                humanTrigger = true;
                humanTriggerAuthor = m.getUserId();
                break;
            }
        }
        // A manually sent message in this batch breaks the agent reply loop:
        // reset the counter, and auto-revive sessions stopped only by the cap.
        // Sessions that an operator manually stopped stay stopped.
        if (humanTrigger) {
            int prior = session.consecutiveAgentReplies.getAndSet(0);
            if (prior > 0) {
                log.info("AgentSocialService channel {} human trigger from {} detected, resetting counter (was {})",
                        channelId, humanTriggerAuthor, prior);
            }
            if (!session.endedUsers.isEmpty()) {
                log.info("AgentSocialService channel {} human trigger from {} detected, clearing endedUsers ({})",
                        channelId, humanTriggerAuthor, session.endedUsers);
                session.endedUsers.clear();
            }
            if (!session.enabled && !session.manuallyStopped) {
                session.enabled = true;
                log.info("AgentSocialService channel {} auto-revived after human trigger", channelId);
            }
        }

        if (!session.enabled) {
            log.info("AgentSocialService channel {} session is stopped ({}), skip dispatch",
                    channelId, session.manuallyStopped ? "manual" : "cap");
            return;
        }

        log.info("AgentSocialService channel {} dispatching to {} online subscriber(s), consecutiveAgentReplies={}/{} (base={}, perUser={})",
                channelId, onlineSubscribers.size(),
                session.consecutiveAgentReplies.get(), effectiveCap,
                baseConsecutiveAgentReplies, extraRepliesPerOnlineUser);

        // Submit one task per candidate user that performs the full
        // generate -> cap-check -> send -> state-update pipeline inside the
        // reply pool. ChannelSession state is mutated under atomic / concurrent
        // primitives so no external synchronization is required.
        ExecutorService pool = ensureReplyExecutor();
        final long dispatchChannelId = channelId;
        final int cap = effectiveCap;
        final ChannelSession sess = session;
        List<Future<?>> futures = new ArrayList<>(onlineSubscribers.size());
        for (final String userId : onlineSubscribers) {
            if (userId.equals(newestAuthor)) {
                continue;
            }
            if (sess.endedUsers.contains(userId)) {
                log.info("AgentSocialService channel {} user {} already ended this session, skip until human message",
                        dispatchChannelId, userId);
                continue;
            }
            try {
                futures.add(pool.submit(() -> dispatchOne(dispatchChannelId, userId, sess, cap, latest)));
            } catch (Exception e) {
                log.warn("AgentSocialService failed to submit reply task for user {} on channel {}", userId, dispatchChannelId, e);
            }
        }

        // Wait for all reply tasks of this channel pass to finish so the
        // scheduler doesn't pile up overlapping dispatches for the same
        // channel. Per-task failures are already logged inside dispatchOne.
        for (Future<?> f : futures) {
            try {
                f.get(REPLY_GENERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                log.warn("AgentSocialService reply task on channel {} failed", channelId, e.getCause() != null ? e.getCause() : e);
            } catch (Exception e) {
                log.warn("AgentSocialService reply task on channel {} timed out or interrupted", channelId, e);
                f.cancel(true);
            }
        }
    }

    /**
     * Full pipeline for a single candidate user: re-check session state,
     * call the LLM, enforce the cap atomically, send the reply, and update
     * {@link ChannelSession#endedUsers}. Designed to run inside the reply
     * pool.
     */
    private void dispatchOne(long channelId, String userId, ChannelSession session, int effectiveCap, List<SocialChannelMessage> latest) {
        if (!session.enabled || session.endedUsers.contains(userId)) {
            return;
        }
        ReplyResult result;
        try {
            log.info("AgentSocialService generating reply for user {} on channel {}", userId, channelId);
            AgentMessageQueueService.CachedChatParams params = queueService.getCachedChatParams(userId);
            result = generateReply(userId, params, latest);
        } catch (Exception e) {
            log.warn("AgentSocialService reply for user {} on channel {} failed", userId, channelId, e);
            return;
        }
        if (result == null) {
            log.info("AgentSocialService no reply result for user {} on channel {}, skip send", userId, channelId);
            return;
        }
        String reply = result.reply;
        boolean hasContent = reply != null && !reply.trim().isEmpty();
        if (hasContent) {
            // Atomically reserve a slot in the cap before sending; release it
            // on failure so transient errors don't waste the budget.
            int reserved;
            while (true) {
                int cur = session.consecutiveAgentReplies.get();
                if (cur >= effectiveCap) {
                    if (session.enabled) {
                        session.enabled = false;
                        log.warn("AgentSocialService channel {} reached max consecutive agent replies ({}), session auto-stopped before sending for user {}",
                                channelId, effectiveCap, userId);
                    }
                    reserved = -1;
                    break;
                }
                if (session.consecutiveAgentReplies.compareAndSet(cur, cur + 1)) {
                    reserved = cur + 1;
                    break;
                }
            }
            if (reserved < 0) {
                return;
            }
            try {
                socialChannelService.sendMessage(userId, channelId, reply, true);
                log.info("AgentSocialService sent reply for user {} on channel {} (length={}, end={}, consecutiveAgentReplies={}/{})",
                        userId, channelId, reply.length(), result.end,
                        reserved, effectiveCap);
            } catch (Exception e) {
                session.consecutiveAgentReplies.decrementAndGet();
                log.warn("AgentSocialService send reply for user {} on channel {} failed, refunded cap slot", userId, channelId, e);
                return;
            }
        } else {
            log.info("AgentSocialService empty reply produced for user {} on channel {} (end={}), skip send",
                    userId, channelId, result.end);
        }
        if (result.end && session.endedUsers.add(userId)) {
            log.info("AgentSocialService channel {} user {} marked as ended; will resume only after a human message",
                    channelId, userId);
        }
    }

    /**
     * Per-channel session state controlling agent dispatch and bounding the
     * reply chain length. Mutated only from the worker thread, with the
     * exception of {@link #enabled}, which is also flipped by the public
     * {@link #stopChannel(long)} / {@link #restartChannel(long)} APIs. The
     * field is declared {@code volatile} so those writes publish safely.
     */
    private static final class ChannelSession {
        volatile boolean enabled = true;
        // True only when an operator stopped the session via stopChannel(...).
        // A session that was auto-stopped because it reached the reply cap
        // leaves this false, allowing a subsequent human message to revive it.
        volatile boolean manuallyStopped = false;
        // Atomic counter so concurrent reply tasks can CAS against the cap
        // without an external lock.
        final AtomicInteger consecutiveAgentReplies = new AtomicInteger(0);
        // Agent users that have self-declared the conversation finished
        // (end=true in their reply JSON). Backed by a concurrent set so
        // reply tasks can read/write it in parallel.
        final Set<String> endedUsers = ConcurrentHashMap.newKeySet();
    }

    /**
     * Parsed structure returned by the reply prompt: free-form reply text
     * plus a self-reported "end" flag.
     */
    private static final class ReplyResult {
        final String reply;
        final boolean end;

        ReplyResult(String reply, boolean end) {
            this.reply = reply;
            this.end = end;
        }
    }

    private ReplyResult generateReply(String replyUserId, AgentMessageQueueService.CachedChatParams params, List<SocialChannelMessage> latestDesc) {
        // latestDesc is newest-first; flip to chronological order for the LLM.
        List<SocialChannelMessage> chronological = new ArrayList<>(latestDesc);
        Collections.reverse(chronological);

        List<ChatMessage> systemMessages = params == null ? null : params.getSystemMessages();
        List<ChatMessage> messages = new ArrayList<>();
        if (systemMessages != null) {
            for (ChatMessage sys : systemMessages) {
                if (sys != null) {
                    messages.add(sys);
                }
            }
        }
        // Collapse the whole chronological transcript into one user-role
        // message: lines authored by the replying user are tagged as "Me",
        // others use their display name. The model then picks the single most
        // appropriate next reply from "Me" based on the full context.
        StringBuilder transcript = new StringBuilder();
        for (SocialChannelMessage m : chronological) {
            if (m == null || m.getContent() == null) {
                continue;
            }
            String text = m.getContent().trim();
            if (text.isEmpty()) {
                continue;
            }
            boolean self = replyUserId.equals(m.getUserId());
            String author;
            if (self) {
                author = "Me";
            } else {
                author = m.getUserName() != null && !m.getUserName().isEmpty()
                        ? m.getUserName()
                        : (m.getUserId() != null ? m.getUserId() : "user");
            }
            if (transcript.length() > 0) {
                transcript.append('\n');
            }
            transcript.append(author).append(": ").append(text);
        }
        if (transcript.length() == 0) {
            return null;
        }

        String prompt = String.format(REPLY_PROMPT_TEMPLATE, transcript.toString());

        ChatMessage userMessage = new ChatMessage();
        userMessage.setRole("user");
        userMessage.setContent(prompt);
        messages.add(userMessage);

        String model = params != null ? params.getModel() : null;
        if (model != null && model.equals(AiGlobal.DEFAULT_MODEL_ID)) {
            model = null;
        }
        double temperature = params != null ? params.getTemperature() : 0.7;

        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setStream(false);
        request.setTemperature(temperature);
        request.setMax_tokens(REPLY_MAX_TOKENS);
        request.setModel(model);
        if (params != null && params.getApiKey() != null) {
            request.setApiKey(params.getApiKey());
            // Sticky billing key for TokenChargeImpl: downstream adapters
            // rewrite apiKey, userApiKey survives the round-trip.
            request.setUserApiKey(params.getApiKey());
        }
        request.setMessages(Lists.newArrayList(messages));
        // Avoid feedback loop: AgentFilterImpl re-caches system messages keyed
        // by userId, so skip BeforeModel hooks on these synthetic requests.
        // AfterModel hooks (notably TokenStatisticsImpl / TokenChargeImpl) are still allowed to
        // run so the synthetic call is billed against the caller's apiKey.
        request.setEnableHook(false);
        request.setEnableAfter(true);
        request.enableOnlyHook(TokenStatisticsImpl.class);
        request.enableOnlyHook(TokenChargeImpl.class);

        try {
            log.info("AgentSocialService requesting completion for user {} (model={}, temperature={}, messages={})",
                    replyUserId, model, temperature, messages.size());
            CompletionsService completionsService = new CompletionsService();
            ChatCompletionResult result = completionsService.completions(request);
            if (result == null || result.getChoices() == null || result.getChoices().isEmpty()) {
                log.info("AgentSocialService completion returned no choices for user {}", replyUserId);
                return null;
            }
            ChatMessage out = result.getChoices().get(0).getMessage();
            if (out == null) {
                log.info("AgentSocialService completion choice has no message for user {}", replyUserId);
                return null;
            }
            String content = out.getContent();
            log.info("AgentSocialService completion succeeded for user {} reply {}", replyUserId, new Gson().toJson(out));
            return parseReplyResult(content);
        } catch (Exception e) {
            log.warn("AgentSocialService completion failed for user {}", replyUserId, e);
            return null;
        }
    }

    /**
     * Parse the model output into a {@link ReplyResult}. The prompt asks for
     * strict JSON {@code {"reply": "...", "end": true|false}}, but defensively
     * handle code-fenced JSON and plain-text fallbacks (treated as reply with
     * {@code end=false}).
     */
    private ReplyResult parseReplyResult(String content) {
        if (content == null) {
            return null;
        }
        String text = content.trim();
        if (text.isEmpty()) {
            return new ReplyResult("", false);
        }
        String json = stripCodeFence(text);
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            String candidate = json.substring(start, end + 1);
            try {
                JsonElement parsed = new JsonParser().parse(candidate);
                if (parsed != null && parsed.isJsonObject()) {
                    JsonObject obj = parsed.getAsJsonObject();
                    String reply = null;
                    if (obj.has("reply") && !obj.get("reply").isJsonNull()) {
                        reply = obj.get("reply").getAsString();
                    }
                    boolean endFlag = false;
                    if (obj.has("end") && !obj.get("end").isJsonNull()) {
                        JsonElement endEl = obj.get("end");
                        try {
                            endFlag = endEl.getAsBoolean();
                        } catch (Exception ignore) {
                            String s = endEl.getAsString();
                            endFlag = "true".equalsIgnoreCase(s) || "1".equals(s);
                        }
                    }
                    return new ReplyResult(reply == null ? "" : reply.trim(), endFlag);
                }
            } catch (Exception e) {
                log.warn("AgentSocialService failed to parse reply JSON, falling back to raw text: {}", e.getMessage());
            }
        }
        // Fallback: treat the entire output as plain reply text.
        return new ReplyResult(text, false);
    }

    private String stripCodeFence(String text) {
        String s = text.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline > 0) {
                s = s.substring(firstNewline + 1);
            } else {
                s = s.substring(3);
            }
            if (s.endsWith("```")) {
                s = s.substring(0, s.length() - 3);
            }
            s = s.trim();
        }
        return s;
    }

    private static String loadReplyPromptTemplate() {
        String template = ResourceUtil.loadAsString(REPLY_PROMPT_RESOURCE);
        if (template == null || template.trim().isEmpty()) {
            throw new IllegalStateException("Reply prompt resource is missing or empty: " + REPLY_PROMPT_RESOURCE);
        }
        return template;
    }
}
