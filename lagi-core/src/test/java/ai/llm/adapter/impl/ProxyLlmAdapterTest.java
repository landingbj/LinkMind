package ai.llm.adapter.impl;

import ai.common.ModelService;
import ai.llm.adapter.ILlmAdapter;
import ai.openai.pojo.ChatCompletionChoice;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import io.reactivex.Observable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ProxyLlmAdapterTest {

    @Test
    void completionsReturnsLocalResultWithoutCallingDelegate() {
        CountingAdapter delegate = new CountingAdapter();
        ProxyLlmAdapter proxy = new ProxyLlmAdapter(delegate);
        ChatCompletionResult local = messageResult("blocked");
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setLocalCompletionResult(local);

        ChatCompletionResult result = proxy.completions(request);

        assertSame(local, result);
        assertEquals(0, delegate.completionCalls);
    }

    @Test
    void streamReturnsLocalResultWithoutCallingDelegate() {
        CountingAdapter delegate = new CountingAdapter();
        ProxyLlmAdapter proxy = new ProxyLlmAdapter(delegate);
        ChatCompletionResult local = deltaResult("blocked");
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setLocalCompletionResult(local);

        ChatCompletionResult result = proxy.streamCompletions(request).blockingFirst();

        assertSame(local, result);
        assertEquals(0, delegate.streamCalls);
    }

    @Test
    void pollingKeyPoolReusesKeyForTheSameSession() {
        CountingAdapter delegate = new CountingAdapter();
        delegate.setApiKeys(Arrays.asList("key-1", "key-2"));
        delegate.setKeyRoute("polling");
        delegate.setApiKey("configured-default-key");
        ProxyLlmAdapter proxy = new ProxyLlmAdapter(delegate);

        ChatCompletionRequest first = new ChatCompletionRequest();
        first.setSessionId("conversation-1");
        ChatCompletionRequest second = new ChatCompletionRequest();
        second.setSessionId("conversation-1");

        proxy.completions(first);
        proxy.completions(second);

        assertEquals(Arrays.asList("key-1", "key-1"), delegate.completionKeys);
        assertEquals("configured-default-key", delegate.getApiKey());
    }

    @Test
    void pollingKeyPoolReusesKeyForStreamingRequestsInTheSameSession() {
        CountingAdapter delegate = new CountingAdapter();
        delegate.setApiKeys(Arrays.asList("key-1", "key-2"));
        delegate.setKeyRoute("polling");
        ProxyLlmAdapter proxy = new ProxyLlmAdapter(delegate);

        ChatCompletionRequest first = new ChatCompletionRequest();
        first.setSessionId("conversation-1");
        ChatCompletionRequest second = new ChatCompletionRequest();
        second.setSessionId("conversation-1");

        proxy.streamCompletions(first).blockingFirst();
        proxy.streamCompletions(second).blockingFirst();

        assertEquals(Arrays.asList("key-1", "key-1"), delegate.streamKeys);
    }

    @Test
    void pollingKeyPoolStillRoundRobinsWithoutSessionIdentity() {
        CountingAdapter delegate = new CountingAdapter();
        delegate.setApiKeys(Arrays.asList("key-1", "key-2"));
        delegate.setKeyRoute("polling");
        ProxyLlmAdapter proxy = new ProxyLlmAdapter(delegate);

        proxy.completions(new ChatCompletionRequest());
        proxy.completions(new ChatCompletionRequest());

        assertEquals(Arrays.asList("key-1", "key-2"), delegate.completionKeys);
    }

    private static ChatCompletionResult messageResult(String content) {
        ChatCompletionChoice choice = new ChatCompletionChoice();
        choice.setMessage(ChatMessage.builder().content(content).build());
        ChatCompletionResult result = new ChatCompletionResult();
        result.setChoices(Collections.singletonList(choice));
        return result;
    }

    private static ChatCompletionResult deltaResult(String content) {
        ChatCompletionChoice choice = new ChatCompletionChoice();
        choice.setDelta(ChatMessage.builder().content(content).build());
        ChatCompletionResult result = new ChatCompletionResult();
        result.setChoices(Collections.singletonList(choice));
        return result;
    }

    private static class CountingAdapter extends ModelService implements ILlmAdapter {
        int completionCalls;
        int streamCalls;
        List<String> completionKeys = new ArrayList<>();
        List<String> streamKeys = new ArrayList<>();

        @Override
        public ChatCompletionResult completions(ChatCompletionRequest request) {
            completionCalls++;
            completionKeys.add(getApiKey(request));
            return messageResult("delegate");
        }

        @Override
        public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest chatCompletionRequest) {
            streamCalls++;
            streamKeys.add(getApiKey(chatCompletionRequest));
            return Observable.just(deltaResult("delegate"));
        }
    }
}
