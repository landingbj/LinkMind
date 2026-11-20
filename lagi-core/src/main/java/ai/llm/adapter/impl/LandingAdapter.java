package ai.llm.adapter.impl;


import ai.common.exception.RRException;
import ai.config.ConfigUtil;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.utils.LLMErrorConstants;
import ai.llm.utils.ExtraBodyUtil;
import ai.llm.routing.BringYourOwnKeyRouter;
import ai.common.ModelService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.utils.MultimodalTraceUtil;
import ai.openai.pojo.ExtraBody;
import io.reactivex.Observable;
import okhttp3.HttpUrl;

import java.net.URI;
import java.net.URISyntaxException;

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
        if (!isFinalServer() || request == null) {
            return null;
        }
        ILlmAdapter target = BringYourOwnKeyRouter.upstream(request.getModel());
        if (target == null) {
            throw new RRException(LLMErrorConstants.NO_AVAILABLE_MODEL,
                    "{\"error\":\"No configured upstream for model: " + request.getModel() + ".\"}");
        }

        // A first-hop key must not override the target provider's configured key.
        request.setSelectedBackendApiKey(null);
        request.setModel(((ModelService) target).getModel());
        return target;
    }

    public boolean isFinalServer() {
        HttpUrl destination = HttpUrl.parse(ConfigUtil.normalizeCascadeApiAddress(getApiAddress()));
        return destination != null && (sameService(destination, ConfigUtil.getBaseUrl())
                || sameService(destination, ConfigUtil.APP_CONTAINER_URL));
    }

    private static boolean sameService(HttpUrl destination, String address) {
        if (address == null || address.trim().isEmpty()) return false;
        HttpUrl local = HttpUrl.parse(ConfigUtil.normalizeCascadeApiAddress(address));
        if (local == null || !destination.scheme().equals(local.scheme()) || destination.port() != local.port()
                || !destination.encodedPath().equals(local.encodedPath())) return false;
        return destination.host().equals(local.host()) || (loopback(destination.host()) && loopback(local.host()));
    }

    private static boolean loopback(String host) {
        return "localhost".equals(host) || "127.0.0.1".equals(host) || "::1".equals(host);
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
        String originalUrl = extractOrigin(apiAddress);
        ConfigUtil.setCascadeApiAddress(originalUrl);
    }

    @Override
    public boolean verify() {
        return true;
    }

    @Override
    public void setDefaultField(ChatCompletionRequest request) {
        ExtraBody extraBody;
        if (ConfigUtil.getRunningMode().equals(ConfigUtil.MODE_MATE)) {
            extraBody = ExtraBodyUtil.getExtraBody();
        } else {
            extraBody = request.getExtraBody();
        }
        String userApiKey = request.getUserApiKey();
        super.setDefaultField(request);
        request.setExtraBody(extraBody);
        request.setUserApiKey(userApiKey);
    }

    private String extractOrigin(String apiAddress) {
        if (apiAddress == null) {
            return null;
        }
        String trimmed = apiAddress.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        try {
            URI uri = new URI(trimmed);
            if (uri.getScheme() != null && uri.getRawAuthority() != null) {
                return uri.getScheme() + "://" + uri.getRawAuthority();
            }
        } catch (URISyntaxException ignored) {
            // Fall back to a simple string split when the value is not a strict URI.
        }

        int schemeIdx = trimmed.indexOf("://");
        if (schemeIdx >= 0) {
            int pathStart = trimmed.indexOf('/', schemeIdx + 3);
            if (pathStart >= 0) {
                return trimmed.substring(0, pathStart);
            }
        }
        return trimmed;
    }
}
