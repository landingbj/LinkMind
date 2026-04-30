package ai.llm.adapter.impl;


import ai.common.db.HikariDS;
import ai.config.ConfigUtil;
import ai.llm.utils.ExtraBodyUtil;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ExtraBody;
import ai.utils.ApikeyUtil;
import io.reactivex.Observable;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class LandingAdapter extends OpenAIStandardAdapter {
    private final String DEFAULT_BASE_URL = "https://lagi.saasai.top";

    @Override
    public ChatCompletionResult completions(ChatCompletionRequest chatCompletionRequest) {
        if (isFinalServer()) {
            normalizeModelNameIfSlashSeparated(chatCompletionRequest);
            if (ApikeyUtil.isLandingKey(chatCompletionRequest.getApiKey())) {
                chatCompletionRequest.setApiKey(ApikeyUtil.getModelKey(chatCompletionRequest.getModel()));
            }
        }
        return super.completions(chatCompletionRequest);
    }

    @Override
    public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest chatCompletionRequest) {
        if (isFinalServer()) {
            normalizeModelNameIfSlashSeparated(chatCompletionRequest);
            if (ApikeyUtil.isLandingKey(chatCompletionRequest.getApiKey())) {
                chatCompletionRequest.setApiKey(ApikeyUtil.getModelKey(chatCompletionRequest.getModel()));
            }
        }
        return super.streamCompletions(chatCompletionRequest);
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
        return getApiAddress().startsWith(ConfigUtil.getBaseUrl()) || getApiAddress().startsWith(DEFAULT_BASE_URL);
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
        super.setDefaultField(request);
        request.setExtraBody(extraBody);
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
