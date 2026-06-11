package ai.llm.adapter.impl;

import ai.common.ModelService;
import ai.llm.adapter.ILlmAdapter;
import ai.openai.pojo.ChatCompletionChoice;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import io.reactivex.Observable;
import org.junit.jupiter.api.Test;

import java.util.Collections;

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

        @Override
        public ChatCompletionResult completions(ChatCompletionRequest request) {
            completionCalls++;
            return messageResult("delegate");
        }

        @Override
        public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest chatCompletionRequest) {
            streamCalls++;
            return Observable.just(deltaResult("delegate"));
        }
    }
}
