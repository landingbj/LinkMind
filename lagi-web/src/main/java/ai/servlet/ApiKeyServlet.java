package ai.servlet;

import ai.config.ConfigUtil;
import ai.dto.ModelApiKey;
import ai.migrate.service.ApiKeyService;
import ai.utils.ApikeyUtil;
import ai.utils.OkHttpUtil;
import cn.hutool.core.util.StrUtil;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ApiKeyServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;
    protected Gson gson = new Gson();
    private final ApiKeyService apiKeyService = new ApiKeyService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);

        switch (method) {
            case "list":
                this.list(req, resp);
                break;
            case "get":
                this.get(req, resp);
                break;
            case "providers":
                this.providers(req, resp);
                break;
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);

        switch (method) {
            case "add":
                this.add(req, resp);
                break;
            case "delete":
                this.delete(req, resp);
                break;
            case "toggle":
                this.toggle(req, resp);
                break;
            case "invalidList":
                this.invalidList(req, resp);
                break;
        }
    }

    private void list(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            String userId = req.getParameter("userId");
            List<ModelApiKey> data = apiKeyService.listApiKeys(userId);
            result.put("status", "success");
            result.put("data", data);
            result.put("localApiKeyEditable", apiKeyService.isLocalApiKeyEditable());
        } catch (Exception e) {
            log.error("Failed to list API keys: {}", e.getMessage(), e);
            result.put("status", "failed");
            result.put("msg", e.getMessage());
        }
        responsePrint(resp, gson.toJson(result));
    }

    private void get(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        String modelName = req.getParameter("modelName");
        try {
            Map<String, String> data = apiKeyService.getApiKey(modelName);
            if (data == null) {
                result.put("status", "failed");
                result.put("msg", "Model not found");
            } else {
                data.put("api_key", maskApiKey(data.get("api_key")));
                result.put("status", "success");
                result.put("data", data);
            }
        } catch (Exception e) {
            log.error("Failed to get API key: {}", e.getMessage(), e);
            result.put("status", "failed");
            result.put("msg", e.getMessage());
        }
        responsePrint(resp, gson.toJson(result));
    }

    private void providers(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            List<String> data = apiKeyService.listProviders();
            result.put("status", "success");
            result.put("data", data);
            result.put("localApiKeyEditable", apiKeyService.isLocalApiKeyEditable());
        } catch (Exception e) {
            log.error("Failed to list providers: {}", e.getMessage(), e);
            result.put("status", "failed");
            result.put("msg", e.getMessage());
        }
        responsePrint(resp, gson.toJson(result));
    }

    private void add(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            AddApiKeyRequest body = reqBodyToObj(req, AddApiKeyRequest.class);
            apiKeyService.addApiKey(
                    body == null ? null : body.name,
                    body == null ? null : body.provider,
                    body == null ? null : body.apiKey,
                    body == null ? null : body.model,
                    body == null ? null : body.apiAddress,
                    body == null ? null : body.userId
            );
            result.put("status", "success");
            result.put("msg", "add success");
        } catch (Exception e) {
            log.error("Failed to add API key: {}", e.getMessage(), e);
            result.put("status", "failed");
            result.put("msg", e.getMessage());
        }
        responsePrint(resp, gson.toJson(result));
    }

    private void delete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            DeleteApiKeyRequest body = reqBodyToObj(req, DeleteApiKeyRequest.class);
            Long id = body == null ? null : body.id;
            String apiKey = body == null ? null : body.apiKey;
            String provider = body == null ? null : body.provider;
            String userId = body == null ? null : body.userId;
            apiKeyService.deleteApiKey(id, apiKey, provider, userId);
            result.put("status", "success");
            result.put("msg", "delete success");
        } catch (Exception e) {
            log.error("Failed to delete API key: {}", e.getMessage(), e);
            result.put("status", "failed");
            result.put("msg", e.getMessage());
        }
        responsePrint(resp, gson.toJson(result));
    }

    private void toggle(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        Map<String, Object> result = new HashMap<>();
        try {
            ToggleApiKeyRequest body = reqBodyToObj(req, ToggleApiKeyRequest.class);
            Long id = body == null ? null : body.id;
            String provider = body == null ? null : body.provider;
            String userId = body == null ? null : body.userId;
            boolean enabled = body != null && Boolean.TRUE.equals(body.enabled);
            apiKeyService.toggleApiKey(id, provider, enabled, userId);
            result.put("status", "success");
            result.put("msg", "toggle success");
        } catch (Exception e) {
            log.error("Failed to toggle API key: {}", e.getMessage(), e);
            result.put("status", "failed");
            result.put("msg", e.getMessage());
        }
        responsePrint(resp, gson.toJson(result));
    }

    private void invalidList(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=utf-8");
        if (shouldProxyToCascade()) {
            String cascadeApiAddress = ConfigUtil.CASCADE_API_ADDRESS.trim();
            String targetUrl = buildTargetUrl(cascadeApiAddress, req);
            try {
                String body = requestToJson(req);
                String result = OkHttpUtil.post(targetUrl, new HashMap<>(), new HashMap<>(), body);
                responsePrint(resp, result);
                return;
            } catch (Exception e) {
                Map<String, Object> result = new HashMap<>();
                result.put("status", "failed");
                result.put("msg", "proxy request failed: " + e.getMessage());
                responsePrint(resp, gson.toJson(result));
                return;
            }
        }
        Map<String, Object> result = new HashMap<>();
        try {
            InvalidApiKeyRequest body = reqBodyToObj(req, InvalidApiKeyRequest.class);
            List<String> apiKeys = body == null ? null : body.getApiKeys();
            if (apiKeys == null) {
                throw new IOException("apiKeys is required");
            }
            result.put("status", "success");
            result.put("data", ApikeyUtil.listInvalidApiKeys(apiKeys));
        } catch (Exception e) {
            log.error("Failed to list invalid API keys: {}", e.getMessage(), e);
            result.put("status", "failed");
            result.put("msg", e.getMessage());
        }
        responsePrint(resp, gson.toJson(result));
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

    private static class AddApiKeyRequest {
        Long id;
        String name;
        String provider;
        String apiKey;
        String model;
        String apiAddress;
        String userId;
    }

    private static class DeleteApiKeyRequest {
        Long id;
        String apiKey;
        String provider;
        String userId;
    }

    private static class ToggleApiKeyRequest {
        Long id;
        String provider;
        String userId;
        Boolean enabled;
    }

    private static class InvalidApiKeyRequest {
        List<String> apiKeys;
        List<String> apiKeyList;
        List<String> apikeys;

        List<String> getApiKeys() {
            if (apiKeys != null) {
                return apiKeys;
            }
            if (apiKeyList != null) {
                return apiKeyList;
            }
            return apikeys;
        }
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "";
        }
        int len = apiKey.length();
        if (len <= 4) {
            return "****";
        }
        if (len <= 8) {
            return apiKey.substring(0, 1) + "****" + apiKey.substring(len - 1);
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(len - 4);
    }
}
