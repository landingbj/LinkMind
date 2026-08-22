package ai.account;

import lombok.Builder;
import lombok.Data;

/** Metadata for a LinkMind client token. The plaintext token is only present when it is first issued. */
@Data
@Builder
public class LinkMindClientToken {
    private long id;
    private String userId;
    private String tokenPrefix;
    private String name;
    private boolean enabled;
    private long createdAt;
    private Long expiresAt;
    private Long lastUsedAt;
    private String token;
}
