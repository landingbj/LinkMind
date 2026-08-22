package ai.servlet.api;

import ai.account.CuihuaAccountService;
import ai.account.CuihuaUser;
import ai.account.LinkMindClientToken;
import ai.account.LinkMindClientTokenService;
import ai.llm.dao.TokenStatisticsDao;
import ai.llm.pojo.TokenStatisticsGuardInfo;
import ai.llm.pojo.TokenStatisticsOverview;
import ai.llm.pojo.TokenStatisticsPageResult;
import ai.llm.pojo.TokenStatisticsRange;
import ai.llm.pojo.TokenStatisticsSessionPageResult;
import ai.llm.pojo.TokenStatisticsSummary;
import ai.servlet.BaseServlet;
import ai.servlet.UserServlet;
import ai.utils.ApikeyUtil;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Backend-to-backend API used by Cuihua to manage LinkMind client API keys and
 * inspect a designated user's model token usage. Every operation requires an
 * active {@code system_admin} account, authenticated by a LinkMind client key
 * or a LinkMind management-console session.
 */
public class CuihuaIntegrationApiServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    private final LinkMindClientTokenService clientTokenService = new LinkMindClientTokenService();
    private final CuihuaAccountService accountService = new CuihuaAccountService();
    private final TokenStatisticsDao tokenStatisticsDao = new TokenStatisticsDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        handle(req, resp, HttpMethod.GET);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        handle(req, resp, HttpMethod.POST);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        handle(req, resp, HttpMethod.PUT);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        handle(req, resp, HttpMethod.DELETE);
    }

    private void handle(HttpServletRequest req, HttpServletResponse resp, HttpMethod method) throws IOException {
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");
        AdminAuthentication auth = authenticateSystemAdmin(req);
        if (auth.user == null) {
            writeIntegrationError(resp, auth.status, auth.message);
            return;
        }

        RequestTarget target = parseTarget(req);
        if (target == null) {
            writeIntegrationError(resp, HttpServletResponse.SC_NOT_FOUND, "unknown integration resource");
            return;
        }

        try {
            if (target.isClientApiKeyResource()) {
                handleClientApiKey(req, resp, method, target);
                return;
            }
            if (target.isTokenUsageResource() && method == HttpMethod.GET) {
                handleTokenUsage(req, resp, target);
                return;
            }
            writeIntegrationError(resp, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "method is not supported for this resource");
        } catch (IllegalArgumentException e) {
            writeIntegrationError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (SQLException e) {
            writeIntegrationError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "shared account database operation failed");
        }
    }

    private void handleClientApiKey(HttpServletRequest req, HttpServletResponse resp, HttpMethod method,
                                    RequestTarget target) throws IOException, SQLException {
        if (method == HttpMethod.GET && target.clientTokenId == null) {
            List<LinkMindClientToken> keys = clientTokenService.list(target.userId);
            writeSuccess(resp, keys);
            return;
        }
        if (method == HttpMethod.POST && target.clientTokenId == null) {
            CreateClientApiKeyRequest body = reqBodyToObj(req, CreateClientApiKeyRequest.class);
            LinkMindClientToken key = clientTokenService.issue(
                    target.userId, body == null ? null : body.name, body == null ? null : body.expiresAt);
            resp.setStatus(HttpServletResponse.SC_CREATED);
            writeSuccess(resp, key);
            return;
        }
        if (method == HttpMethod.PUT && target.clientTokenId != null) {
            UpdateClientApiKeyRequest body = reqBodyToObj(req, UpdateClientApiKeyRequest.class);
            LinkMindClientToken key = clientTokenService.update(target.clientTokenId, target.userId,
                    body == null ? null : body.name,
                    body == null ? null : body.enabled,
                    body == null ? null : body.expiresAt,
                    body != null && Boolean.TRUE.equals(body.clearExpiry));
            if (key == null) {
                writeIntegrationError(resp, HttpServletResponse.SC_NOT_FOUND, "client API key was not found");
                return;
            }
            writeSuccess(resp, key);
            return;
        }
        if (method == HttpMethod.DELETE && target.clientTokenId != null) {
            if (!clientTokenService.delete(target.clientTokenId, target.userId)) {
                writeIntegrationError(resp, HttpServletResponse.SC_NOT_FOUND, "client API key was not found");
                return;
            }
            Map<String, Object> data = new HashMap<String, Object>();
            data.put("id", target.clientTokenId);
            data.put("deleted", true);
            writeSuccess(resp, data);
            return;
        }
        writeIntegrationError(resp, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "method is not supported for client API keys");
    }

    private void handleTokenUsage(HttpServletRequest req, HttpServletResponse resp, RequestTarget target) throws IOException {
        String rangeParam = req.getParameter("range");
        TokenStatisticsRange range = TokenStatisticsRange.fromQueryParam(rangeParam);
        if ("overview".equals(target.usageView)) {
            TokenStatisticsSummary summary = tokenStatisticsDao.summarize(range, target.userId);
            String echo = rangeParam == null || rangeParam.trim().isEmpty() ? "today" : rangeParam.trim();
            TokenStatisticsOverview overview = TokenStatisticsOverview.builder()
                    .range(echo)
                    .totalTokens(summary.getTotalTokensConsumed())
                    .totalSavedTokens(summary.getTotalSavedTokens())
                    .dailyAvgTokens(summary.getDailyAvgTokensConsumed())
                    .recordCount(summary.getRecordCount())
                    .build();
            writeSuccess(resp, overview);
            return;
        }
        if ("details".equals(target.usageView)) {
            TokenStatisticsPageResult details = tokenStatisticsDao.queryDetails(range,
                    parsePositiveInt(req.getParameter("page"), 1),
                    parsePositiveInt(req.getParameter("pageSize"), 20), target.userId);
            writeSuccess(resp, details);
            return;
        }
        if ("sessions".equals(target.usageView)) {
            TokenStatisticsSessionPageResult sessions = tokenStatisticsDao.querySessions(range,
                    parsePositiveInt(req.getParameter("page"), 1),
                    parsePositiveInt(req.getParameter("pageSize"), 20),
                    parseNullableLong(req.getParameter("startMs")),
                    parseNullableLong(req.getParameter("endMs")), target.userId);
            writeSuccess(resp, sessions);
            return;
        }
        if ("guard".equals(target.usageView)) {
            TokenStatisticsGuardInfo guard = tokenStatisticsDao.guardInfo(target.userId);
            writeSuccess(resp, guard);
            return;
        }
        writeIntegrationError(resp, HttpServletResponse.SC_NOT_FOUND, "unknown token usage view");
    }

    private AdminAuthentication authenticateSystemAdmin(HttpServletRequest req) {
        String apiKey = ApikeyUtil.extractBearerToken(req.getHeader("Authorization"));
        if (isBlank(apiKey)) {
            apiKey = optionalText(req.getHeader("x-api-key"));
        }
        LinkMindClientTokenService.AuthenticationResult keyAuthentication = clientTokenService.authenticate(apiKey);
        if (keyAuthentication.isTokenMatched()) {
            if (!keyAuthentication.isAuthenticated()) {
                return AdminAuthentication.unauthorized("system administrator API key is invalid");
            }
            return accountService.isSystemAdmin(keyAuthentication.getUser())
                    ? AdminAuthentication.authorized(keyAuthentication.getUser())
                    : AdminAuthentication.forbidden("system_admin role is required");
        }
        try {
            CuihuaUser user = accountService.resolveSession(readSessionCookie(req));
            if (user == null) {
                return AdminAuthentication.unauthorized("system administrator authentication is required");
            }
            return accountService.isSystemAdmin(user)
                    ? AdminAuthentication.authorized(user)
                    : AdminAuthentication.forbidden("system_admin role is required");
        } catch (SQLException e) {
            return AdminAuthentication.unauthorized("system administrator authentication is unavailable");
        }
    }

    private RequestTarget parseTarget(HttpServletRequest req) {
        String path = req.getRequestURI();
        String contextPath = req.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        final String prefix = "/v1/integration/users/";
        if (!path.startsWith(prefix)) {
            return null;
        }
        String[] segments = path.substring(prefix.length()).split("/", -1);
        if (segments.length < 2 || isBlank(segments[0])) {
            return null;
        }
        String userId = decodePathSegment(segments[0]);
        if (userId == null) {
            return null;
        }
        if ("client-api-keys".equals(segments[1])) {
            if (segments.length == 2) {
                return RequestTarget.clientApiKeys(userId, null);
            }
            if (segments.length == 3) {
                try {
                    long id = Long.parseLong(segments[2]);
                    return id > 0 ? RequestTarget.clientApiKeys(userId, id) : null;
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        }
        if ("token-usage".equals(segments[1]) && segments.length == 3) {
            return RequestTarget.tokenUsage(userId, segments[2]);
        }
        return null;
    }

    private static String readSessionCookie(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (UserServlet.SESSION_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static String decodePathSegment(String segment) {
        try {
            return URLDecoder.decode(segment, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    private static int parsePositiveInt(String raw, int defaultValue) {
        if (isBlank(raw)) {
            return defaultValue;
        }
        int value = Integer.parseInt(raw.trim());
        if (value < 1) {
            throw new IllegalArgumentException("must be >= 1");
        }
        return value;
    }

    private static Long parseNullableLong(String raw) {
        return isBlank(raw) ? null : Long.parseLong(raw.trim());
    }

    private void writeSuccess(HttpServletResponse resp, Object data) throws IOException {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("status", "success");
        body.put("data", data);
        writeJson(resp, gson.toJson(body));
    }

    private void writeIntegrationError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        Map<String, String> error = new HashMap<String, String>();
        error.put("status", "failed");
        error.put("msg", message);
        writeJson(resp, gson.toJson(error));
    }

    private static void writeJson(HttpServletResponse resp, String json) throws IOException {
        PrintWriter writer = resp.getWriter();
        writer.print(json);
        writer.flush();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String optionalText(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private enum HttpMethod {
        GET, POST, PUT, DELETE
    }

    private static final class RequestTarget {
        private final String userId;
        private final Long clientTokenId;
        private final String usageView;

        private RequestTarget(String userId, Long clientTokenId, String usageView) {
            this.userId = userId;
            this.clientTokenId = clientTokenId;
            this.usageView = usageView;
        }

        private static RequestTarget clientApiKeys(String userId, Long clientTokenId) {
            return new RequestTarget(userId, clientTokenId, null);
        }

        private static RequestTarget tokenUsage(String userId, String usageView) {
            return new RequestTarget(userId, null, usageView);
        }

        private boolean isClientApiKeyResource() {
            return usageView == null;
        }

        private boolean isTokenUsageResource() {
            return usageView != null;
        }
    }

    private static final class AdminAuthentication {
        private final CuihuaUser user;
        private final int status;
        private final String message;

        private AdminAuthentication(CuihuaUser user, int status, String message) {
            this.user = user;
            this.status = status;
            this.message = message;
        }

        private static AdminAuthentication authorized(CuihuaUser user) {
            return new AdminAuthentication(user, HttpServletResponse.SC_OK, null);
        }

        private static AdminAuthentication unauthorized(String message) {
            return new AdminAuthentication(null, HttpServletResponse.SC_UNAUTHORIZED, message);
        }

        private static AdminAuthentication forbidden(String message) {
            return new AdminAuthentication(null, HttpServletResponse.SC_FORBIDDEN, message);
        }
    }

    private static final class CreateClientApiKeyRequest {
        private String name;
        private Long expiresAt;
    }

    private static final class UpdateClientApiKeyRequest {
        private String name;
        private Boolean enabled;
        private Long expiresAt;
        private Boolean clearExpiry;
    }
}
