package ai.account;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/** Issues and verifies LinkMind client tokens mapped to Cuihua user IDs. */
public class LinkMindClientTokenService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;
    private final CuihuaAccountDao accountDao = new CuihuaAccountDao();

    public AuthenticationResult authenticate(String token) {
        if (isBlank(token)) {
            return AuthenticationResult.notMatched();
        }
        try {
            LinkMindClientToken clientToken = accountDao.findClientToken(CuihuaAccountDao.sha256(token.trim()));
            if (clientToken == null) {
                return AuthenticationResult.notMatched();
            }
            if (!clientToken.isEnabled()) {
                return AuthenticationResult.rejected("token is disabled");
            }
            if (clientToken.getExpiresAt() != null && clientToken.getExpiresAt() <= System.currentTimeMillis()) {
                return AuthenticationResult.rejected("token has expired");
            }
            CuihuaUser user = accountDao.findActiveUserById(clientToken.getUserId());
            if (user == null) {
                return AuthenticationResult.rejected("user is unavailable");
            }
            accountDao.touchClientToken(clientToken.getId());
            return AuthenticationResult.authenticated(user, clientToken);
        } catch (SQLException e) {
            return AuthenticationResult.rejected("token validation failed");
        }
    }

    public LinkMindClientToken issue(String userId, String name, Long expiresAt) throws SQLException {
        if (isBlank(userId)) {
            throw new IllegalArgumentException("userId is required");
        }
        if (expiresAt != null && expiresAt <= System.currentTimeMillis()) {
            throw new IllegalArgumentException("expiresAt must be in the future");
        }
        CuihuaUser user = accountDao.findActiveUserById(userId);
        if (user == null) {
            throw new IllegalArgumentException("active Cuihua user was not found");
        }
        String token = newToken();
        LinkMindClientToken clientToken = LinkMindClientToken.builder()
                .userId(user.getUserId())
                .tokenPrefix(token.substring(0, Math.min(12, token.length())))
                .name(name == null ? "" : name.trim())
                .enabled(true)
                .createdAt(System.currentTimeMillis())
                .expiresAt(expiresAt)
                .token(token)
                .build();
        accountDao.insertClientToken(clientToken, CuihuaAccountDao.sha256(token));
        return clientToken;
    }

    public List<LinkMindClientToken> list(String userId) throws SQLException {
        if (isBlank(userId)) {
            return Collections.emptyList();
        }
        return accountDao.listClientTokens(userId);
    }

    public boolean revoke(long id, String userId) throws SQLException {
        return accountDao.revokeClientToken(id, userId);
    }

    /** Updates metadata only; the original plaintext token can never be recovered. */
    public LinkMindClientToken update(long id, String userId, String name, Boolean enabled,
                                      Long expiresAt, boolean clearExpiry) throws SQLException {
        if (id <= 0 || isBlank(userId)) {
            throw new IllegalArgumentException("id and userId are required");
        }
        LinkMindClientToken current = accountDao.findClientToken(id, userId);
        if (current == null) {
            return null;
        }
        if (expiresAt != null && expiresAt <= System.currentTimeMillis()) {
            throw new IllegalArgumentException("expiresAt must be in the future");
        }
        if (name != null) {
            current.setName(name.trim());
        }
        if (enabled != null) {
            current.setEnabled(enabled);
        }
        if (clearExpiry) {
            current.setExpiresAt(null);
        } else if (expiresAt != null) {
            current.setExpiresAt(expiresAt);
        }
        return accountDao.updateClientToken(current) ? current : null;
    }

    /** Permanently removes a client token. Existing usage records are retained. */
    public boolean delete(long id, String userId) throws SQLException {
        return accountDao.deleteClientToken(id, userId);
    }

    public boolean isIntegrationAvailable() {
        return accountDao.ensureSchema();
    }

    private static String newToken() {
        byte[] raw = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(raw);
        return "lmk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class AuthenticationResult {
        private final boolean tokenMatched;
        private final boolean authenticated;
        private final String reason;
        private final CuihuaUser user;
        private final LinkMindClientToken clientToken;

        private AuthenticationResult(boolean tokenMatched, boolean authenticated, String reason,
                                     CuihuaUser user, LinkMindClientToken clientToken) {
            this.tokenMatched = tokenMatched;
            this.authenticated = authenticated;
            this.reason = reason;
            this.user = user;
            this.clientToken = clientToken;
        }

        public static AuthenticationResult notMatched() {
            return new AuthenticationResult(false, false, null, null, null);
        }

        public static AuthenticationResult rejected(String reason) {
            return new AuthenticationResult(true, false, reason, null, null);
        }

        public static AuthenticationResult authenticated(CuihuaUser user, LinkMindClientToken clientToken) {
            return new AuthenticationResult(true, true, null, user, clientToken);
        }

        public boolean isTokenMatched() {
            return tokenMatched;
        }

        public boolean isAuthenticated() {
            return authenticated;
        }

        public String getReason() {
            return reason;
        }

        public CuihuaUser getUser() {
            return user;
        }

        public LinkMindClientToken getClientToken() {
            return clientToken;
        }
    }
}
