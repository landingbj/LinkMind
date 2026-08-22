package ai.account;

import lombok.Builder;
import lombok.Data;

/**
 * Read-only projection of the account owned by the Cuihua application.
 * LinkMind never creates or updates rows in the shared {@code users} table.
 */
@Data
@Builder
public class CuihuaUser {
    private String userId;
    private String username;
    private String email;
    private String displayName;
    private String passwordHash;
    private String role;
    private String status;
    private String lockedUntil;
    private long createdAt;

    public boolean isSystemAdmin() {
        return role != null && "system_admin".equalsIgnoreCase(role.trim());
    }
}
