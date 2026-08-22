package ai.servlet;

import ai.account.CuihuaAccountService;
import ai.account.CuihuaUser;
import ai.account.LinkMindClientToken;
import ai.account.LinkMindClientTokenService;
import ai.common.pojo.Configuration;
import ai.common.pojo.VectorStoreConfig;
import ai.migrate.service.UserService;
import ai.servlet.dto.LoginRequest;
import ai.servlet.dto.LoginResponse;
import ai.servlet.dto.RegisterResponse;
import ai.utils.MigrateGlobal;
import ai.utils.ValidateCodeCreator;
import ai.vector.VectorStoreService;
import cn.hutool.core.util.StrUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LinkMind account endpoints backed by the shared Cuihua {@code users} table.
 * Cuihua remains the only writer of account and password data.
 */
public class UserServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;
    public static final String SESSION_COOKIE_NAME = "linkmind-session";
    private static final String LEGACY_COOKIE_NAME = "lagi-auth";
    private static final int COOKIE_MAX_AGE = 60 * 60 * 24 * 7;
    private static final long SESSION_LIFETIME_MILLIS = COOKIE_MAX_AGE * 1000L;

    protected Gson gson = new Gson();
    private final UserService userService = new UserService();
    private final CuihuaAccountService accountService = new CuihuaAccountService();
    private final LinkMindClientTokenService clientTokenService = new LinkMindClientTokenService();
    private static final Configuration config = MigrateGlobal.config;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String method = lastPathSegment(req);
        if ("getRandomCategory".equals(method)) {
            getRandomCategory(req, resp);
        } else if ("getDefaultTitle".equals(method)) {
            getDefaultTitle(resp);
        } else if ("getCaptcha".equals(method)) {
            getCaptcha(req, resp);
        } else if ("tokens".equals(method)) {
            listTokens(req, resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String method = lastPathSegment(req);
        if ("login".equals(method)) {
            login(req, resp);
        } else if ("register".equals(method)) {
            register(resp);
        } else if ("authLoginCookie".equals(method)) {
            authLoginCookie(req, resp);
        } else if ("logout".equals(method)) {
            logout(req, resp);
        } else if ("issueToken".equals(method)) {
            issueToken(req, resp);
        } else if ("revokeToken".equals(method)) {
            revokeToken(req, resp);
        }
    }

    private void login(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        LoginResponse response;
        try {
            LoginRequest request = reqBodyToObj(req, LoginRequest.class);
            HttpSession servletSession = req.getSession(true);
            String captcha = request == null ? null : request.getCaptcha();
            String expectedCaptcha = (String) servletSession.getAttribute("captcha");
            if (expectedCaptcha == null || !expectedCaptcha.equalsIgnoreCase(captcha)) {
                response = failedLogin("invalid captcha");
            } else if (!accountService.isIntegrationAvailable()) {
                response = failedLogin("shared Cuihua user database is unavailable");
            } else {
                CuihuaAccountService.LoginResult login = accountService.loginSystemAdmin(
                        request == null ? null : request.getUsername(),
                        request == null ? null : request.getPassword(),
                        SESSION_LIFETIME_MILLIS);
                if (login == null) {
                    response = failedLogin("LinkMind access requires an active system_admin account");
                } else {
                    response = successLogin(login.getUser());
                    addLoginCookies(req, resp, login.getUser(), login.getSessionToken());
                }
            }
        } catch (SQLException e) {
            response = failedLogin("shared account service is unavailable");
        } catch (Exception e) {
            response = failedLogin("login failed");
        }
        responsePrint(resp, gson.toJson(response));
    }

    /** Accounts are created and maintained by Cuihua, never by LinkMind. */
    private void register(HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        RegisterResponse response = new RegisterResponse();
        response.setStatus("failed");
        response.setMsg("accounts are managed by Cuihua; please register there");
        responsePrint(resp, gson.toJson(response));
    }

    /** Validates the opaque server-side LinkMind session. It never handles a password. */
    private void authLoginCookie(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        LoginResponse response;
        try {
            String token = readSessionCookie(req);
            if (StrUtil.isBlank(token)) {
                JsonObject body = reqBodyToObj(req, JsonObject.class);
                if (body != null && body.has("cookieValue") && !body.get("cookieValue").isJsonNull()) {
                    token = body.get("cookieValue").getAsString();
                }
            }
            CuihuaUser user = accountService.resolveSession(token);
            if (user == null || !accountService.isSystemAdmin(user)) {
                if (user != null) {
                    accountService.revokeSession(token);
                }
                removeCookies(req, resp);
                response = failedLogin("session is invalid, expired, or no longer has system_admin access");
            } else {
                response = successLogin(user);
                addLoginCookies(req, resp, user, token);
            }
        } catch (Exception e) {
            removeCookies(req, resp);
            response = failedLogin("session validation failed");
        }
        responsePrint(resp, gson.toJson(response));
    }

    private void logout(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        try {
            accountService.revokeSession(readSessionCookie(req));
        } catch (SQLException ignored) {
            // Clearing the browser cookie still prevents normal use if storage is temporarily unavailable.
        }
        removeCookies(req, resp);
        Map<String, Object> response = new HashMap<String, Object>();
        response.put("status", "success");
        responsePrint(resp, gson.toJson(response));
    }

    /** Lists only the current Cuihua user's LinkMind client tokens. */
    private void listTokens(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        CuihuaUser user = currentUser(req);
        if (user == null) {
            writeUnauthorized(resp);
            return;
        }
        try {
            List<LinkMindClientToken> tokens = clientTokenService.list(user.getUserId());
            Map<String, Object> response = new HashMap<String, Object>();
            response.put("status", "success");
            response.put("data", tokens);
            responsePrint(resp, gson.toJson(response));
        } catch (SQLException e) {
            writeFailure(resp, "list client tokens failed");
        }
    }

    /** Issues a token once; the plaintext value is returned only by this endpoint. */
    private void issueToken(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        CuihuaUser user = currentUser(req);
        if (user == null) {
            writeUnauthorized(resp);
            return;
        }
        try {
            IssueTokenRequest body = reqBodyToObj(req, IssueTokenRequest.class);
            LinkMindClientToken token = clientTokenService.issue(
                    user.getUserId(), body == null ? null : body.name, body == null ? null : body.expiresAt);
            Map<String, Object> response = new HashMap<String, Object>();
            response.put("status", "success");
            response.put("data", token);
            responsePrint(resp, gson.toJson(response));
        } catch (IllegalArgumentException e) {
            writeFailure(resp, e.getMessage());
        } catch (SQLException e) {
            writeFailure(resp, "issue client token failed");
        }
    }

    private void revokeToken(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        CuihuaUser user = currentUser(req);
        if (user == null) {
            writeUnauthorized(resp);
            return;
        }
        try {
            RevokeTokenRequest body = reqBodyToObj(req, RevokeTokenRequest.class);
            boolean revoked = body != null && clientTokenService.revoke(body.id, user.getUserId());
            if (!revoked) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                writeFailure(resp, "client token was not found");
                return;
            }
            Map<String, Object> response = new HashMap<String, Object>();
            response.put("status", "success");
            responsePrint(resp, gson.toJson(response));
        } catch (SQLException e) {
            writeFailure(resp, "revoke client token failed");
        }
    }

    private CuihuaUser currentUser(HttpServletRequest req) {
        try {
            return accountService.resolveSession(readSessionCookie(req));
        } catch (SQLException e) {
            return null;
        }
    }

    private void addLoginCookies(HttpServletRequest req, HttpServletResponse resp, CuihuaUser user, String sessionToken) {
        addCookie(resp, SESSION_COOKIE_NAME, sessionToken, true, req.isSecure());
        addCookie(resp, "userId", user.getUserId(), false, req.isSecure());
        expireCookie(resp, LEGACY_COOKIE_NAME, false, req.isSecure());
    }

    private void addCookie(HttpServletResponse resp, String name, String value, boolean httpOnly, boolean secure) {
        Cookie cookie = new Cookie(name, value);
        cookie.setMaxAge(COOKIE_MAX_AGE);
        cookie.setPath("/");
        cookie.setHttpOnly(httpOnly);
        cookie.setSecure(secure);
        resp.addCookie(cookie);
    }

    private void expireCookie(HttpServletResponse resp, String name, boolean httpOnly, boolean secure) {
        Cookie cookie = new Cookie(name, "");
        cookie.setMaxAge(0);
        cookie.setPath("/");
        cookie.setHttpOnly(httpOnly);
        cookie.setSecure(secure);
        resp.addCookie(cookie);
    }

    private void removeCookies(HttpServletRequest req, HttpServletResponse resp) {
        boolean secure = req.isSecure();
        expireCookie(resp, SESSION_COOKIE_NAME, true, secure);
        expireCookie(resp, LEGACY_COOKIE_NAME, false, secure);
        expireCookie(resp, "userId", false, secure);
    }

    private String readSessionCookie(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (SESSION_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private LoginResponse successLogin(CuihuaUser user) {
        LoginResponse response = new LoginResponse();
        response.setStatus("success");
        LoginResponse.Data data = new LoginResponse.Data();
        data.setUsername(user.getUsername());
        data.setUserId(user.getUserId());
        response.setData(data);
        return response;
    }

    private LoginResponse failedLogin(String message) {
        LoginResponse response = new LoginResponse();
        response.setStatus("failed");
        response.setMsg(message);
        return response;
    }

    private void writeUnauthorized(HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        writeFailure(resp, "authentication required");
    }

    private void writeFailure(HttpServletResponse resp, String message) throws IOException {
        Map<String, Object> response = new HashMap<String, Object>();
        response.put("status", "failed");
        response.put("msg", message);
        responsePrint(resp, gson.toJson(response));
    }

    private void getRandomCategory(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        String currentCategory = req.getParameter("currentCategory");
        String userId = req.getParameter("userId");
        JsonObject data = new JsonObject();
        String category;
        VectorStoreConfig vectorStoreConfig = new VectorStoreService().getVectorStoreConfig();
        if (vectorStoreConfig == null) {
            category = null;
        } else {
            category = vectorStoreConfig.getDefaultCategory();
        }
        if (category == null) {
            category = currentCategory == null || currentCategory.isEmpty()
                    ? userService.getRandomCategory() : currentCategory;
        }
        if (StrUtil.isNotBlank(userId)) {
            category = category + "_" + userId;
        }
        data.addProperty("category", category);
        Map<String, Object> response = new HashMap<String, Object>();
        if (category != null) {
            response.put("status", "success");
            response.put("data", data);
        } else {
            response.put("status", "failed");
        }
        responsePrint(resp, gson.toJson(response));
    }

    private void getDefaultTitle(HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> response = new HashMap<String, Object>();
        response.put("status", "success");
        response.put("data", config.getSystemTitle());
        responsePrint(resp, gson.toJson(response));
    }

    private void getCaptcha(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("image/jpeg");
        int width = parsePositiveInt(req.getParameter("width"), 60);
        int height = parsePositiveInt(req.getParameter("height"), 20);
        int charNum = parsePositiveInt(req.getParameter("charNum"), 4);
        int fontSize = parsePositiveInt(req.getParameter("fontSize"), 18);
        if (req.getParameter("width") == null) {
            width = charNum * 15;
        }
        String code = ValidateCodeCreator.randomCode(charNum);
        HttpSession session = req.getSession();
        session.setMaxInactiveInterval(30 * 60);
        session.setAttribute("captcha", code);
        BufferedImage image = ValidateCodeCreator.create(code, width, height, fontSize);
        ImageIO.write(image, "JPEG", resp.getOutputStream());
    }

    private int parsePositiveInt(String raw, int defaultValue) {
        if (raw == null || raw.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String lastPathSegment(HttpServletRequest req) {
        String url = req.getRequestURI();
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private static class IssueTokenRequest {
        String name;
        Long expiresAt;
    }

    private static class RevokeTokenRequest {
        long id;
    }
}
