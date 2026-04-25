package ai.pnps.skills.util;

import ai.common.db.HikariDS;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ExtraBody;
import com.google.gson.Gson;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
        String sql = "SELECT c.id, c.name, c.description, c.is_public "
                + "FROM social_channels c "
                + "INNER JOIN social_channel_subscriptions s ON s.channel_id = c.id "
                + "WHERE s.user_id = ? "
                + "ORDER BY c.name ASC";
        try (Connection conn = HikariDS.getConnection("saas");
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> ch = new LinkedHashMap<String, Object>();
                    ch.put("channelId", rs.getLong("id"));
                    ch.put("channelName", rs.getString("name"));
                    ch.put("description", rs.getString("description"));
                    ch.put("isPublic", rs.getInt("is_public") == 1);
                    channels.add(ch);
                }
            }
        } catch (Exception ignored) {
            // Tables may not exist yet or DB unavailable; return what we have.
        }
        return channels;
    }
}
