package ai.llm.adapter.impl;

import ai.common.ModelService;
import ai.config.ConfigUtil;
import ai.llm.adapter.ILlmAdapter;
import ai.manager.LlmManager;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import io.reactivex.Observable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LandingAdapterTest {

    @AfterEach
    void resetManager() {
        LlmManager.getInstance().clear();
        ConfigUtil.setAppHost(null);
        ConfigUtil.setAppPort(0);
        ConfigUtil.APP_CONTAINER_URL = null;
    }

    @Test
    void finalServerDelegatesPrefixedModelToRegisteredProvider() {
        RecordingAdapter claude = new RecordingAdapter();
        claude.setApiKey("nexrouter-key");
        LlmManager.getInstance().register("claude-opus-4-8", claude);

        LandingAdapter landing = finalServerAdapter();
        ChatCompletionRequest request = request("anthropic/claude-opus-4-8");
        request.setSelectedBackendApiKey("box-key");

        ChatCompletionResult result = landing.completions(request);

        assertEquals(1, claude.completionCalls);
        assertEquals("claude-opus-4-8", claude.lastModel);
        assertEquals("nexrouter-key", claude.lastApiKey);
        assertEquals("anthropic/claude-opus-4-8", result.getModel());
    }

    @Test
    void finalServerDelegatesPrefixedStreamingModelToRegisteredProvider() {
        RecordingAdapter gemini = new RecordingAdapter();
        gemini.setApiKey("nexrouter-key");
        LlmManager.getInstance().register("gemini-3.8-flash", gemini);

        LandingAdapter landing = finalServerAdapter();
        ChatCompletionResult result = landing.streamCompletions(request("google/gemini-3.8-flash")).blockingFirst();

        assertEquals(1, gemini.streamCalls);
        assertEquals("gemini-3.8-flash", gemini.lastModel);
        assertEquals("nexrouter-key", gemini.lastApiKey);
        assertEquals("google/gemini-3.8-flash", result.getModel());
    }

    private LandingAdapter finalServerAdapter() {
        ConfigUtil.setAppHost(null);
        ConfigUtil.APP_CONTAINER_URL = "http://localhost:25007";
        LandingAdapter adapter = new LandingAdapter();
        adapter.setApiAddress("http://localhost:25007/v1/chat/completions");
        return adapter;
    }

    private ChatCompletionRequest request(String model) {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(model);
        return request;
    }

    private static class RecordingAdapter extends ModelService implements ILlmAdapter {
        int completionCalls;
        int streamCalls;
        String lastModel;
        String lastApiKey;

        @Override
        public ChatCompletionResult completions(ChatCompletionRequest request) {
            completionCalls++;
            lastModel = request.getModel();
            lastApiKey = getApiKey(request);
            ChatCompletionResult result = new ChatCompletionResult();
            result.setModel("upstream-" + request.getModel());
            return result;
        }

        @Override
        public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest request) {
            streamCalls++;
            lastModel = request.getModel();
            lastApiKey = getApiKey(request);
            ChatCompletionResult result = new ChatCompletionResult();
            result.setModel("upstream-" + request.getModel());
            return Observable.just(result);
        }
    }
}
