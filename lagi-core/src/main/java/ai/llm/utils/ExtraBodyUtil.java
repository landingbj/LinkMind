package ai.llm.utils;

import ai.common.db.HikariDS;
import ai.openai.pojo.ExtraBody;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class ExtraBodyUtil {
    public static ExtraBody getExtraBody() {
        String userId = getUserId();
        if (userId == null) {
            return null;
        }
        ExtraBody extraBody = new ExtraBody();
        extraBody.setUserId(userId);
        return extraBody;
    }

    public static String getUserId() {
        String sql = "SELECT v FROM social_temp WHERE k = ? LIMIT 1";
        try (Connection conn = HikariDS.getConnection("saas");
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "last_login_user_id");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String userId = rs.getString("v");
                    if (userId != null && !userId.trim().isEmpty()) {
                        return userId.trim();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
