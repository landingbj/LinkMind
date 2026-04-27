package ai.utils;

import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ExtraBody;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class ExtraBodyUtil {
    private static final Gson GSON = new Gson();
    private static final String EXTRA_BODY_START = "<extra_body>";
    private static final String EXTRA_BODY_END = "</extra_body>";

    public static String toExtraBodyString(ChatCompletionRequest request) {
        if (request == null || request.getExtraBody() == null) {
            return "";
        }
        String json = GSON.toJson(request.getExtraBody());
        return "\n" + EXTRA_BODY_START + json + EXTRA_BODY_END + "\n";
    }

    public static ExtraBody extractExtraBody(String content) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }
        int start = content.indexOf(EXTRA_BODY_START);
        if (start < 0) {
            return null;
        }
        int jsonStart = start + EXTRA_BODY_START.length();
        int end = content.indexOf(EXTRA_BODY_END, jsonStart);
        if (end < 0) {
            return null;
        }
        String json = content.substring(jsonStart, end).trim();
        if (json.isEmpty()) {
            return null;
        }
        try {
            return GSON.fromJson(json, ExtraBody.class);
        } catch (JsonSyntaxException ignored) {
            return null;
        }
    }
}
