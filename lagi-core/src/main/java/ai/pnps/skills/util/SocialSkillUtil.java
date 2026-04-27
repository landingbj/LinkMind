package ai.pnps.skills.util;

import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatMessage;
import ai.openai.pojo.ExtraBody;
import ai.utils.ExtraBodyUtil;
import ai.utils.qa.ChatCompletionUtil;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders the social-channel SKILL.md template by replacing the
 * {{USER_ID}} and {{SUBSCRIBED_CHANNELS_JSON}} placeholders with the
 * caller's userId (read from request.extra_body.user_id) and a JSON
 * snapshot of the channels they have subscribed to.
 */
public final class SocialSkillUtil {

    private static final Gson GSON = new Gson();
    private static final String PLACEHOLDER_USER_ID = "{{USER_ID}}";
    private static final String PLACEHOLDER_CHANNELS = "{{SUBSCRIBED_CHANNELS_JSON}}";

    private SocialSkillUtil() {
    }

    public static String generateSkill(String skillTemplate, ChatCompletionRequest request) {
        if (skillTemplate == null) {
            return null;
        }
        List<ChatMessage> systemPrompt = ChatCompletionUtil.getSystemMessages(request.getMessages());
        if (!systemPrompt.isEmpty()) {
            ChatMessage systemMessage = systemPrompt.get(0);
            String content = systemMessage.getContent();
            ExtraBody extraBody = ExtraBodyUtil.extractExtraBody(content);
            request.setExtraBody(extraBody);
        }
        String userId = resolveUserId(request);
        List<Map<String, Object>> channels = loadSubscribedChannels(userId);
        String channelsJson = GSON.toJson(channels);
        return skillTemplate
                .replace(PLACEHOLDER_USER_ID, userId == null ? "" : userId)
                .replace(PLACEHOLDER_CHANNELS, channelsJson);
    }

    private static String resolveUserId(ChatCompletionRequest request) {
        if (request == null) {
            return null;
        }
        ExtraBody extra = request.getExtraBody();
        if (extra == null) {
            return null;
        }
        String userId = extra.getUserId();
        if (userId == null || userId.trim().isEmpty()) {
            return null;
        }
        return userId.trim();
    }

    private static List<Map<String, Object>> loadSubscribedChannels(String userId) {
        List<Map<String, Object>> channels = new ArrayList<Map<String, Object>>();
        if (userId == null || userId.isEmpty()) {
            return channels;
        }
        try {
            Class<?> daoClass = Class.forName("ai.migrate.dao.SocialChannelDao");
            Object dao = daoClass.getDeclaredConstructor().newInstance();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> result = (List<Map<String, Object>>) daoClass
                    .getMethod("loadSubscribedChannels", String.class)
                    .invoke(dao, userId);
            if (result != null) {
                channels = result;
            }
        } catch (Exception ignored) {
            // DAO module may not be present or DB unavailable; return what we have.
        }
        return channels;
    }
}
