package ai.servlet.api;

import ai.account.CuihuaAccountService;
import ai.account.CuihuaUser;
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
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-user token usage API. The account is taken from an authenticated
 * LinkMind client token or LinkMind browser session; it is never accepted as
 * a query parameter.
 */
public class TokenStatisticsApiServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    private final TokenStatisticsDao tokenStatisticsDao = new TokenStatisticsDao();
    private final LinkMindClientTokenService clientTokenService = new LinkMindClientTokenService();
    private final CuihuaAccountService accountService = new CuihuaAccountService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");
        CuihuaUser user = resolveAuthenticatedUser(req);
        if (user == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            writeErrorJson(resp, "authentication required");
            return;
        }

        String uri = req.getRequestURI();
        try {
            if (uri.endsWith("/overview")) {
                writeOverview(req, resp, user.getUserId());
            } else if (uri.endsWith("/details")) {
                writeDetails(req, resp, user.getUserId());
            } else if (uri.endsWith("/sessions")) {
                writeSessions(req, resp, user.getUserId());
            } else if (uri.endsWith("/guard")) {
                writeGuard(resp, user.getUserId());
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                writeJson(resp, "{\"error\":\"not found\"}");
            }
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeErrorJson(resp, "invalid number: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeErrorJson(resp, e.getMessage());
        }
    }

    private void writeOverview(HttpServletRequest req, HttpServletResponse resp, String userId) throws IOException {
        String rangeParam = req.getParameter("range");
        TokenStatisticsRange range = TokenStatisticsRange.fromQueryParam(rangeParam);
        TokenStatisticsSummary summary = tokenStatisticsDao.summarize(range, userId);
        String echo = rangeParam == null || rangeParam.isEmpty() ? "today" : rangeParam.trim();
        TokenStatisticsOverview overview = TokenStatisticsOverview.builder()
                .range(echo)
                .totalTokens(summary.getTotalTokensConsumed())
                .totalSavedTokens(summary.getTotalSavedTokens())
                .dailyAvgTokens(summary.getDailyAvgTokensConsumed())
                .recordCount(summary.getRecordCount())
                .build();
        writeJson(resp, gson.toJson(overview));
    }

    private void writeGuard(HttpServletResponse resp, String userId) throws IOException {
        TokenStatisticsGuardInfo info = tokenStatisticsDao.guardInfo(userId);
        writeJson(resp, gson.toJson(info));
    }

    private void writeDetails(HttpServletRequest req, HttpServletResponse resp, String userId) throws IOException {
        TokenStatisticsRange range = TokenStatisticsRange.fromQueryParam(req.getParameter("range"));
        int page = parsePositiveInt(req.getParameter("page"), 1);
        int pageSize = parsePositiveInt(req.getParameter("pageSize"), 20);
        TokenStatisticsPageResult result = tokenStatisticsDao.queryDetails(range, page, pageSize, userId);
        writeJson(resp, gson.toJson(result));
    }

    private void writeSessions(HttpServletRequest req, HttpServletResponse resp, String userId) throws IOException {
        TokenStatisticsRange range = TokenStatisticsRange.fromQueryParam(req.getParameter("range"));
        int page = parsePositiveInt(req.getParameter("page"), 1);
        int pageSize = parsePositiveInt(req.getParameter("pageSize"), 20);
        Long startMs = parseNullableLong(req.getParameter("startMs"));
        Long endMs = parseNullableLong(req.getParameter("endMs"));
        TokenStatisticsSessionPageResult result = tokenStatisticsDao.querySessions(
                range, page, pageSize, startMs, endMs, userId);
        writeJson(resp, gson.toJson(result));
    }

    private CuihuaUser resolveAuthenticatedUser(HttpServletRequest req) {
        String key = ApikeyUtil.extractBearerToken(req.getHeader("Authorization"));
        if (key == null || key.isEmpty()) {
            key = optionalText(req.getHeader("x-api-key"));
        }
        LinkMindClientTokenService.AuthenticationResult token = clientTokenService.authenticate(key);
        if (token.isTokenMatched()) {
            return token.isAuthenticated() ? token.getUser() : null;
        }
        try {
            return accountService.resolveSession(readSessionCookie(req));
        } catch (SQLException e) {
            return null;
        }
    }

    private String readSessionCookie(HttpServletRequest req) {
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

    private static String optionalText(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static int parsePositiveInt(String raw, int defaultValue) {
        if (raw == null || raw.isEmpty()) {
            return defaultValue;
        }
        int value = Integer.parseInt(raw.trim());
        if (value < 1) {
            throw new IllegalArgumentException("must be >= 1");
        }
        return value;
    }

    private static Long parseNullableLong(String raw) {
        return raw == null || raw.isEmpty() ? null : Long.parseLong(raw.trim());
    }

    private static void writeJson(HttpServletResponse resp, String json) throws IOException {
        PrintWriter out = resp.getWriter();
        out.print(json);
        out.flush();
    }

    private void writeErrorJson(HttpServletResponse resp, String message) throws IOException {
        Map<String, String> error = new HashMap<String, String>();
        error.put("error", message);
        writeJson(resp, gson.toJson(error));
    }
}
