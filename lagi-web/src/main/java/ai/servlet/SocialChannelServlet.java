package ai.servlet;

import ai.sevice.SocialChannelService;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class SocialChannelServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;
    protected Gson gson = new Gson();
    private final SocialChannelService socialChannelService = new SocialChannelService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);

        switch (method) {
            case "listMyChannels":
                this.listMyChannels(req, resp);
                break;
            case "listPublicChannels":
                this.listPublicChannels(req, resp);
                break;
            case "listMessages":
                this.listMessages(req, resp);
                break;
            case "getChannel":
                this.getChannel(req, resp);
                break;
            case "getUser":
                this.getUser(req, resp);
                break;
            default:
                break;
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
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
            default:
                break;
        }
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

    private void getUser(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<String, Object>();
        try {
            String userId = req.getParameter("userId");
            result.put("status", "success");
            result.put("data", socialChannelService.getUser(userId));
        } catch (Exception e) {
            log.error("getUser: {}", e.getMessage(), e);
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
            result.put("status", "success");
            result.put("data", socialChannelService.listPublicChannels(limit));
        } catch (Exception e) {
            log.error("listPublicChannels: {}", e.getMessage(), e);
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

    private static class SendMessageBody {
        String userId;
        Long channelId;
        String channelName;
        String content;
    }
}
