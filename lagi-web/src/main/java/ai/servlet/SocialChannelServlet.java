package ai.servlet;

import ai.config.ConfigUtil;
import ai.migrate.service.CascadeConfigService;
import ai.sevice.SocialChannelService;
import ai.utils.OkHttpUtil;
import cn.hutool.core.util.StrUtil;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class SocialChannelServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;
    protected Gson gson = new Gson();
    private final SocialChannelService socialChannelService = new SocialChannelService();
    private final CascadeConfigService cascadeConfigService = new CascadeConfigService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        if (tryProxyToCascade(req, resp, "GET")) {
            return;
        }
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);

        switch (method) {
            case "runningMode":
                this.runningMode(resp);
                break;
            case "cascadeConfig":
                this.getCascadeConfig(resp);
                break;
            case "listMyChannels":
                this.listMyChannels(req, resp);
                break;
            case "listPublicChannels":
                this.listPublicChannels(req, resp);
                break;
            case "listMessages":
                this.listMessages(req, resp);
                break;
            case "listOwnedChannels":
                this.listOwnedChannels(req, resp);
                break;
            case "getChannel":
                this.getChannel(req, resp);
                break;
            default:
                break;
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        if (tryProxyToCascade(req, resp, "POST")) {
            return;
        }
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);

        switch (method) {
            case "registerUser":
                this.registerUser(req, resp);
                break;
            case "createChannel":
                this.createChannel(req, resp);
                break;
            case "subscribe":
                this.subscribe(req, resp);
                break;
            case "unsubscribe":
                this.unsubscribe(req, resp);
                break;
            case "sendMessage":
                this.sendMessage(req, resp);
                break;
            case "toggleChannel":
                this.toggleChannel(req, resp);
                break;
            case "deleteChannel":
                this.deleteChannel(req, resp);
                break;
            case "saveLastLoginUser":
                this.saveLastLoginUser(req, resp);
                break;
            case "cascadeConfig":
                this.saveCascadeConfig(req, resp);
                break;
            case "translateChannel":
                this.translateChannel(req, resp);
                break;
            default:
                break;
        }
    }

    private boolean tryProxyToCascade(HttpServletRequest req, HttpServletResponse resp, String requestMethod) throws IOException {
        String requestUri = req.getRequestURI();
        String method = requestUri.substring(requestUri.lastIndexOf("/") + 1);
        if ("saveLastLoginUser".equals(method)) {
            return false;
        }
        if ("runningMode".equals(method)) {
            return false;
        }
        if ("cascadeConfig".equals(method)) {
            return false;
        }
        if (!shouldProxyToCascade()) {
            return false;
        }
        String cascadeApiAddress = ConfigUtil.CASCADE_API_ADDRESS.trim();
        String targetUrl = buildTargetUrl(cascadeApiAddress, req);
        resp.setContentType("application/json;charset=utf-8");
        try {
            String result;
            if ("GET".equalsIgnoreCase(requestMethod)) {
                result = OkHttpUtil.get(targetUrl, getQueryParams(req));
            } else {
                String body = requestToJson(req);
                result = OkHttpUtil.post(targetUrl, new HashMap<String, String>(), getQueryParams(req), body);
            }
            responsePrint(resp, result);
            return true;
        } catch (Exception e) {
            log.error("proxy social channel request failed, targetUrl={}", targetUrl, e);
            Map<String, Object> result = new HashMap<String, Object>();
            result.put("status", "failed");
            result.put("msg", "proxy request failed: " + e.getMessage());
            responsePrint(resp, gson.toJson(result));
            return true;
        }
    }

    private boolean shouldProxyToCascade() {
        return ConfigUtil.MODE_MATE.equalsIgnoreCase(ConfigUtil.getRunningMode())
                && StrUtil.isNotBlank(ConfigUtil.CASCADE_API_ADDRESS);
    }

    private String buildTargetUrl(String cascadeApiAddress, HttpServletRequest req) {
        String requestUri = req.getRequestURI();
        if (cascadeApiAddress.endsWith("/") && requestUri.startsWith("/")) {
            return cascadeApiAddress.substring(0, cascadeApiAddress.length() - 1) + requestUri;
        }
        if (!cascadeApiAddress.endsWith("/") && !requestUri.startsWith("/")) {
            return cascadeApiAddress + "/" + requestUri;
        }
        return cascadeApiAddress + requestUri;
    }

    private Map<String, String> getQueryParams(HttpServletRequest req) {
        Map<String, String> params = new HashMap<String, String>();
        Enumeration<String> parameterNames = req.getParameterNames();
        while (parameterNames.hasMoreElements()) {
            String name = parameterNames.nextElement();
            params.put(name, req.getParameter(name));
        }
        return params;
    }

    private void runningMode(HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<String, Object>();
        try {
            String runningMode = ConfigUtil.getRunningMode();
            result.put("status", "success");
            result.put("runningMode", runningMode);
            result.put("isMateMode", "mate".equals(runningMode));
        } catch (Exception e) {
            log.error("runningMode: {}", e.getMessage(), e);
            result.put("status", "failed");
            result.put("msg", e.getMessage());
        }
        responsePrint(resp, gson.toJson(result));
    }

    private void getCascadeConfig(HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<String, Object>();
        try {
            result.put("status", "success");
            result.put("data", cascadeConfigService.getCascadeConfig());
        } catch (Exception e) {
            log.error("get cascade config failed", e);
            result.put("status", "failed");
            result.put("msg", e.getMessage());
        }
        responsePrint(resp, gson.toJson(result));
    }

    private void saveCascadeConfig(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<String, Object>();
        try {
            CascadeConfigRequest body = reqBodyToObj(req, CascadeConfigRequest.class);
            String serverAddress = body == null ? null : body.serverAddress;
            result.put("status", "success");
            result.put("data", cascadeConfigService.saveCascadeConfig(serverAddress));
        } catch (Exception e) {
            log.error("save cascade config failed", e);
            result.put("status", "failed");
            result.put("msg", e.getMessage());
        }
        responsePrint(resp, gson.toJson(result));
    }

    private void registerUser(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<String, Object>();
        try {
            RegisterUserBody body = reqBodyToObj(req, RegisterUserBody.class);
            if (body == null) {
                throw new IOException("userId and username are required");
            }
            boolean created = socialChannelService.registerUser(body.userId, body.username);
            result.put("status", "success");
            result.put("created", created);
        } catch (Exception e) {
            log.error("registerUser: {}", e.getMessage(), e);
            result.put("status", "failed");
            result.put("msg", e.getMessage());
        }
        responsePrint(resp, gson.toJson(result));
    }

    private void saveLastLoginUser(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            LastLoginUserBody body = reqBodyToObj(req, LastLoginUserBody.class);
            if (body == null) {
                throw new IOException("userId is required");
            }
            socialChannelService.saveLastLoginUser(body.userId);
            result.put("status", "success");
        } catch (Exception e) {
            log.error("saveLastLoginUser: {}", e.getMessage(), e);
            result.put("status", "failed");
            result.put("msg", e.getMessage());
        }
        responsePrint(resp, gson.toJson(result));
    }

    private void createChannel(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<String, Object>();
        try {
            CreateChannelBody body = reqBodyToObj(req, CreateChannelBody.class);
            long channelId = socialChannelService.createChannel(
                    body == null ? null : body.userId,
                    body == null ? null : body.name,
                    body == null ? null : body.description,
                    body == null ? null : body.isPublic
            );
            result.put("status", "success");
            result.put("channelId", channelId);
        } catch (Exception e) {
            log.error("createChannel: {}", e.getMessage(), e);
            result.put("status", "failed");
            result.put("msg", e.getMessage());
        }
        responsePrint(resp, gson.toJson(result));
    }

    private void subscribe(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<String, Object>();
        try {
            UserChannelBody body = reqBodyToObj(req, UserChannelBody.class);
            if (body == null || body.channelId == null) {
                throw new IOException("userId and channelId are required");
            }
            socialChannelService.subscribe(body.userId, body.channelId);
            result.put("status", "success");
            result.put("msg", "subscribed");
        } catch (Exception e) {
            log.error("subscribe: {}", e.getMessage(), e);
            result.put("status", "failed");
            result.put("msg", e.getMessage());
        }
        responsePrint(resp, gson.toJson(result));
    }

    private void unsubscribe(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<String, Object>();
        try {
            UserChannelBody body = reqBodyToObj(req, UserChannelBody.class);
            if (body == null || body.channelId == null) {
                throw new IOException("userId and channelId are required");
            }
            socialChannelService.unsubscribe(body.userId, body.channelId);
            result.put("status", "success");
            result.put("msg", "unsubscribed");
        } catch (Exception e) {
            log.error("unsubscribe: {}", e.getMessage(), e);
            result.put("status", "failed");
            result.put("msg", e.getMessage());
        }
        responsePrint(resp, gson.toJson(result));
    }

    private void sendMessage(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<String, Object>();
        try {
            SendMessageBody body = reqBodyToObj(req, SendMessageBody.class);
            if (body == null) {
                throw new IOException("userId, content and (channelId or channelName) are required");
            }
            if (body.channelId == null && (body.channelName == null || body.channelName.trim().isEmpty())) {
                throw new IOException("channelId or channelName is required");
            }
            long messageId = socialChannelService.sendMessage(
                    body.userId,
                    body.channelId,
                    body.channelName,
                    body.content
            );
            result.put("status", "success");
            result.put("messageId", messageId);
        } catch (Exception e) {
            log.error("sendMessage: {}", e.getMessage(), e);
            result.put("status", "failed");
            result.put("msg", e.getMessage());
        }
        responsePrint(resp, gson.toJson(result));
    }

    private void listMyChannels(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<String, Object>();
        try {
            String userId = req.getParameter("userId");
            result.put("status", "success");
            result.put("data", socialChannelService.listMyChannels(userId));
        } catch (Exception e) {
            log.error("listMyChannels: {}", e.getMessage(), e);
            result.put("status", "failed");
            result.put("msg", e.getMessage());
        }
        responsePrint(resp, gson.toJson(result));
    }

    private void listPublicChannels(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<String, Object>();
        try {
            String limitStr = req.getParameter("limit");
            int limit = 50;
            if (limitStr != null && !limitStr.trim().isEmpty()) {
                limit = Integer.parseInt(limitStr.trim());
            }
            String lang = req.getParameter("lang");
            result.put("status", "success");
            result.put("data", socialChannelService.listPublicChannels(limit, lang));
        } catch (Exception e) {
            log.error("listPublicChannels: {}", e.getMessage(), e);
            result.put("status", "failed");
            result.put("msg", e.getMessage());
        }
        responsePrint(resp, gson.toJson(result));
    }

    private void listOwnedChannels(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<String, Object>();
        try {
            String userId = req.getParameter("userId");
            result.put("status", "success");
            result.put("data", socialChannelService.listOwnedChannels(userId));
        } catch (Exception e) {
            log.error("listOwnedChannels: {}", e.getMessage(), e);
            result.put("status", "failed");
            result.put("msg", e.getMessage());
        }
        responsePrint(resp, gson.toJson(result));
    }

    private void listMessages(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<String, Object>();
        try {
            String userId = req.getParameter("userId");
            String channelIdStr = req.getParameter("channelId");
            if (channelIdStr == null || channelIdStr.trim().isEmpty()) {
                throw new IOException("channelId is required");
            }
            long channelId = Long.parseLong(channelIdStr.trim());
            String limitStr = req.getParameter("limit");
            int limit = 50;
            if (limitStr != null && !limitStr.trim().isEmpty()) {
                limit = Integer.parseInt(limitStr.trim());
            }
            Long beforeId = null;
            String beforeStr = req.getParameter("beforeId");
            if (beforeStr != null && !beforeStr.trim().isEmpty()) {
                beforeId = Long.parseLong(beforeStr.trim());
            }
            result.put("status", "success");
            result.put("data", socialChannelService.listMessages(userId, channelId, limit, beforeId));
        } catch (Exception e) {
            log.error("listMessages: {}", e.getMessage(), e);
            result.put("status", "failed");
            result.put("msg", e.getMessage());
        }
        responsePrint(resp, gson.toJson(result));
    }

    private void getChannel(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<String, Object>();
        try {
            String userId = req.getParameter("userId");
            String channelIdStr = req.getParameter("channelId");
            if (channelIdStr == null || channelIdStr.trim().isEmpty()) {
                throw new IOException("channelId is required");
            }
            long channelId = Long.parseLong(channelIdStr.trim());
            result.put("status", "success");
            result.put("data", socialChannelService.getChannel(userId, channelId));
        } catch (Exception e) {
            log.error("getChannel: {}", e.getMessage(), e);
            result.put("status", "failed");
            result.put("msg", e.getMessage());
        }
        responsePrint(resp, gson.toJson(result));
    }

    private void toggleChannel(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<String, Object>();
        try {
            ToggleChannelBody body = reqBodyToObj(req, ToggleChannelBody.class);
            if (body == null || body.channelId == null || body.enabled == null) {
                throw new IOException("userId, channelId and enabled are required");
            }
            socialChannelService.toggleChannel(body.userId, body.channelId, body.enabled);
            result.put("status", "success");
            result.put("msg", body.enabled ? "enabled" : "disabled");
        } catch (Exception e) {
            log.error("toggleChannel: {}", e.getMessage(), e);
            result.put("status", "failed");
            result.put("msg", e.getMessage());
        }
        responsePrint(resp, gson.toJson(result));
    }

    private void translateChannel(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<String, Object>();
        try {
            TranslateChannelBody body = reqBodyToObj(req, TranslateChannelBody.class);
            if (body == null || body.channelId == null) {
                throw new IOException("channelId is required");
            }
            if (body.lang == null || body.lang.trim().isEmpty()) {
                throw new IOException("lang is required");
            }
            result.put("status", "success");
            result.put("data", socialChannelService.translateChannel(body.channelId, body.lang));
        } catch (Exception e) {
            log.error("translateChannel: {}", e.getMessage(), e);
            result.put("status", "failed");
            result.put("msg", e.getMessage());
        }
        responsePrint(resp, gson.toJson(result));
    }

    private void deleteChannel(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<String, Object>();
        try {
            UserChannelBody body = reqBodyToObj(req, UserChannelBody.class);
            if (body == null || body.channelId == null) {
                throw new IOException("userId and channelId are required");
            }
            socialChannelService.deleteChannel(body.userId, body.channelId);
            result.put("status", "success");
            result.put("msg", "deleted");
        } catch (Exception e) {
            log.error("deleteChannel: {}", e.getMessage(), e);
            result.put("status", "failed");
            result.put("msg", e.getMessage());
        }
        responsePrint(resp, gson.toJson(result));
    }

    private static class RegisterUserBody {
        String userId;
        String username;
    }

    private static class CreateChannelBody {
        String userId;
        String name;
        String description;
        Boolean isPublic;
    }

    private static class UserChannelBody {
        String userId;
        Long channelId;
    }

    private static class ToggleChannelBody {
        String userId;
        Long channelId;
        Boolean enabled;
    }

    private static class SendMessageBody {
        String userId;
        Long channelId;
        String channelName;
        String content;
    }

    private static class LastLoginUserBody {
        String userId;
    }

    private static class CascadeConfigRequest {
        String serverAddress;
    }

    private static class TranslateChannelBody {
        Long channelId;
        String lang;
    }
}
