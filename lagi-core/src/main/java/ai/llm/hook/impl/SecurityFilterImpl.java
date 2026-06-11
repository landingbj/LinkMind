package ai.llm.hook.impl;

import ai.annotation.Component;
import ai.annotation.Order;
import ai.annotation.Value;
import ai.llm.hook.AfterModel;
import ai.llm.hook.BeforeModel;
import ai.llm.pojo.ModelContext;
import ai.openai.pojo.ChatCompletionChoice;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.utils.LagiGlobal;
import ai.utils.SensitiveWordUtil;
import io.reactivex.Observable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

@Order(1)
@Component
public class SecurityFilterImpl implements BeforeModel, AfterModel {

    private static final String INPUT_BLOCKED_MESSAGE = "该问题触发安全过滤规则，已停止生成回答。请调整提问内容后重试。";

    @Value("${filters[0].filter_window_length:1}")
    private Integer queueCapacity;

    @Override
    public ChatCompletionRequest beforeModel(ModelContext context) {
        if (context == null || context.getRequest() == null) {
            return null;
        }
        ChatCompletionRequest request = context.getRequest();
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return request;
        }
        ChatMessage last = request.getMessages().get(request.getMessages().size() - 1);
        if (last == null || last.getContent() == null) {
            return request;
        }
        String content = last.getContent();
        boolean blocked = SensitiveWordUtil.isInputBlocked(content);
        last.setContent(SensitiveWordUtil.filter(content, SensitiveWordUtil.INPUT_RULE_TYPE));
        if (blocked) {
            request.setEnableAfter(false);
            request.setLocalCompletionResult(buildInputBlockedResult(request));
        }
        return request;
    }

    @Override
    public ChatCompletionResult apply(ModelContext context) {
        if (context == null) {
            return null;
        }
        return SensitiveWordUtil.filter4ChatCompletionResult(context.getResult());
    }

    @Override
    public Observable<ChatCompletionResult> stream(ModelContext context) {
        if (context == null || context.getStreamResult() == null) {
            return Observable.empty();
        }
        Observable<ChatCompletionResult> source = context.getStreamResult();
        return Observable.create(emitter -> {
            int capacity = normalizedQueueCapacity();
            Queue<ChatCompletionResult> cacheQueue = new ArrayBlockingQueue<>(capacity);
            List<String> contents = new ArrayList<>();
            AtomicBoolean streamSensitiveRecorded = new AtomicBoolean(false);

            source.subscribe(
                    chunk -> {
                        try {
                            if (!isContentChunk(chunk)) {
                                flushQueue(cacheQueue, emitter::onNext);
                                emitter.onNext(chunk);
                                return;
                            }

                            ChatMessage delta = chunk.getChoices().get(0).getDelta();
                            String content = delta.getContent();
                            contents.add(content);
                            String totalContent = String.join("", contents);
                            String nullOrReplaceContent = SensitiveWordUtil.getNullOrReplaceContent(totalContent);
                            if (nullOrReplaceContent != null) {
                                if (streamSensitiveRecorded.compareAndSet(false, true)) {
                                    SensitiveWordUtil.recordOutputStreamFilter(totalContent);
                                }
                                rewriteCachedChunks(cacheQueue, nullOrReplaceContent);
                                for (int i = Math.max(0, contents.size() - cacheQueue.size()); i < contents.size(); i++) {
                                    contents.set(i, nullOrReplaceContent);
                                }
                                delta.setContent(nullOrReplaceContent);
                            }

                            if (cacheQueue.size() < capacity) {
                                cacheQueue.offer(chunk);
                            } else {
                                ChatCompletionResult toEmit = cacheQueue.poll();
                                cacheQueue.offer(chunk);
                                if (toEmit != null) {
                                    emitter.onNext(toEmit);
                                }
                            }
                        } catch (Exception e) {
                            emitter.onError(e);
                        }
                    },
                    emitter::onError,
                    () -> {
                        flushQueue(cacheQueue, emitter::onNext);
                        emitter.onComplete();
                    }
            );

            emitter.setCancellable(() -> {
                cacheQueue.clear();
                contents.clear();
            });
        });
    }

    private int normalizedQueueCapacity() {
        if (queueCapacity == null || queueCapacity < 1) {
            return 1;
        }
        return queueCapacity;
    }

    private ChatCompletionResult buildInputBlockedResult(ChatCompletionRequest request) {
        ChatCompletionResult result = new ChatCompletionResult();
        result.setId("chatcmpl-local-filter-" + UUID.randomUUID());
        result.setObject(Boolean.TRUE.equals(request.getStream()) ? "chat.completion.chunk" : "chat.completion");
        result.setCreated(System.currentTimeMillis() / 1000);
        result.setModel(request.getModel());

        ChatMessage message = ChatMessage.builder()
                .role(LagiGlobal.LLM_ROLE_ASSISTANT)
                .content(INPUT_BLOCKED_MESSAGE)
                .build();

        ChatCompletionChoice choice = new ChatCompletionChoice();
        choice.setIndex(0);
        choice.setFinish_reason("content_filter");
        if (Boolean.TRUE.equals(request.getStream())) {
            choice.setDelta(message);
        } else {
            choice.setMessage(message);
        }
        result.setChoices(Collections.singletonList(choice));
        return result;
    }

    private boolean isContentChunk(ChatCompletionResult chunk) {
        if (chunk == null || chunk.getChoices() == null || chunk.getChoices().isEmpty()) {
            return false;
        }
        ChatCompletionChoice choice = chunk.getChoices().get(0);
        return choice != null
                && choice.getFinish_reason() == null
                && choice.getDelta() != null
                && choice.getDelta().getContent() != null;
    }

    private interface ChunkEmitter {
        void emit(ChatCompletionResult chunk);
    }

    private void flushQueue(Queue<ChatCompletionResult> cacheQueue, ChunkEmitter emitter) {
        while (!cacheQueue.isEmpty()) {
            ChatCompletionResult remaining = cacheQueue.poll();
            if (remaining != null) {
                emitter.emit(remaining);
            }
        }
    }

    private void rewriteCachedChunks(Queue<ChatCompletionResult> cacheQueue, String content) {
        int size = cacheQueue.size();
        for (int i = 0; i < size; i++) {
            ChatCompletionResult temp = cacheQueue.poll();
            if (temp != null && isContentChunk(temp)) {
                temp.getChoices().get(0).getDelta().setContent(content);
            }
            cacheQueue.offer(temp);
        }
    }
}
