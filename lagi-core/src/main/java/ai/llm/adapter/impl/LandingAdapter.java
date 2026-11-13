package ai.llm.adapter.impl;


import ai.common.exception.RRException;
import ai.config.ConfigUtil;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.utils.LLMErrorConstants;
import ai.llm.utils.ExtraBodyUtil;
import ai.manager.LlmManager;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.utils.MultimodalTraceUtil;
import ai.openai.pojo.ExtraBody;
import io.reactivex.Observable;

public class LandingAdapter extends OpenAIStandardAdapter {
    private final String DEFAULT_BASE_URL = "https://ai.linkmind.top";

    @Override
    public ChatCompletionResult completions(ChatCompletionRequest chatCompletionRequest) {
        String callerModel = isFinalServer() ? callerModelName(chatCompletionRequest) : null;
        ILlmAdapter finalAdapter = resolveFinalAdapter(chatCompletionRequest);
        ChatCompletionResult result;
        if (finalAdapter != null) {
            result = finalAdapter.completions(chatCompletionRequest);
        } else {
            MultimodalTraceUtil.logRequest("landing-dispatch", chatCompletionRequest, getApiAddress());
            result = super.completions(chatCompletionRequest);
        }
        return restoreCallerModel(result, callerModel);
    }

    @Override
    public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest chatCompletionRequest) {
        String callerModel = isFinalServer() ? callerModelName(chatCompletionRequest) : null;
        ILlmAdapter finalAdapter = resolveFinalAdapter(chatCompletionRequest);
        Observable<ChatCompletionResult> result;
        if (finalAdapter != null) {
            result = finalAdapter.streamCompletions(chatCompletionRequest);
        } else {
            MultimodalTraceUtil.logRequest("landing-dispatch", chatCompletionRequest, getApiAddress());
            result = super.streamCompletions(chatCompletionRequest);
        }
        final String responseModel = callerModel;
        return result.map(chunk -> restoreCallerModel(chunk, responseModel));
    }

    private static String callerModelName(ChatCompletionRequest request) {
        if (request == null || request.getModel() == null) {
            return null;
        }
        String model = request.getModel().trim();
        return model.isEmpty() ? null : model;
    }

    private static ChatCompletionResult restoreCallerModel(ChatCompletionResult result, String callerModel) {
        if (result != null && callerModel != null) {
            result.setModel(callerModel);
        }
        return result;
    }

    /**
     * A box sends provider-prefixed cloud aliases back to the final LinkMind
     * server. At that point the request must leave through the configured
     * provider adapter, not call this server's HTTP endpoint again.
     */
    private ILlmAdapter resolveFinalAdapter(ChatCompletionRequest request) {
        if (!isFinalServer() || request == null || request.getModel() == null
                || request.getModel().indexOf('/') < 0) {
            return null;
        }
        normalizeModelNameIfSlashSeparated(request);
        ILlmAdapter target = LlmManager.getInstance().getAdapter(request.getModel());
        if (target == null || isLandingAdapter(target)) {
            throw new RRException(LLMErrorConstants.NO_AVAILABLE_MODEL,
                    "{\"error\":\"No configured upstream for model: " + request.getModel() + ".\"}");
        }

        // A first-hop key must not override the target provider's configured key.
        request.setSelectedBackendApiKey(null);
        return target;
    }

    private boolean isLandingAdapter(ILlmAdapter adapter) {
        if (adapter instanceof ProxyLlmAdapter) {
            adapter = ((ProxyLlmAdapter) adapter).getLlmAdapter();
        }
        return adapter instanceof LandingAdapter;
    }

    /**
     * If model is like {@code Alibaba/qwen3-max}, keep only the part after the slash ({@code qwen3-max}).
     */
    private void normalizeModelNameIfSlashSeparated(ChatCompletionRequest chatCompletionRequest) {
        if (chatCompletionRequest == null || chatCompletionRequest.getModel() == null) {
            return;
        }
        String model = chatCompletionRequest.getModel().trim();
        int slash = model.indexOf('/');
        if (slash >= 0 && slash < model.length() - 1) {
            String after = model.substring(slash + 1).trim();
            if (!after.isEmpty()) {
                chatCompletionRequest.setModel(after);
            }
        }
    }

    private boolean isFinalServer() {
        String baseUrl = ConfigUtil.getBaseUrl();
        return baseUrl != null && !baseUrl.trim().isEmpty() && getApiAddress().startsWith(baseUrl);
    }

    @Override
    public String getApiAddress() {
        if (apiAddress == null) {
            apiAddress = DEFAULT_BASE_URL + "/v1/chat/completions";
        }
        return apiAddress;
    }

    @Override
    public void setApiAddress(String apiAddress) {
        this.apiAddress = apiAddress;
    }

    @Override
    public boolean verify() {
        return true;
    }

    @Override
    public void setDefaultField(ChatCompletionRequest request) {
        ExtraBody extraBody;
        if (request.getExtraBody() == null && ConfigUtil.isCascadeProxyEnabled()) {
            extraBody = ExtraBodyUtil.getExtraBody();
        } else {
            extraBody = request.getExtraBody();
        }
        String userApiKey = request.getUserApiKey();
        super.setDefaultField(request);
        request.setExtraBody(extraBody);
        request.setUserApiKey(userApiKey);
    }

}
