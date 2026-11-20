package ai.llm.routing;

import ai.common.ModelService;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.adapter.impl.LandingAdapter;
import ai.llm.adapter.impl.ProxyLlmAdapter;
import ai.manager.LlmManager;
import cn.hutool.core.util.StrUtil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** The box lists and selects named models from the same ordered catalogue. */
public final class BringYourOwnKeyRouter {
    public enum Scope { LOCAL, CLOUD, UPSTREAM }

    private BringYourOwnKeyRouter() {}

    public static String publicId(ModelService adapter, String model) {
        if (!(adapter instanceof ILlmAdapter) || StrUtil.isBlank(adapter.getType()) || isLanding((ILlmAdapter) adapter)) return model;
        return adapter.getType().trim() + "/" + model;
    }

    public static Map<String, ILlmAdapter> routes(Scope scope, String cloudBackend) {
        Map<String, ILlmAdapter> result = new LinkedHashMap<>();
        for (ILlmAdapter adapter : LlmManager.getInstance().getAllAdapters()) {
            ModelService service = (ModelService) adapter;
            if (Boolean.FALSE.equals(service.getEnable()) || StrUtil.isBlank(service.getModel())) continue;
            boolean cloud = isLanding(adapter);
            if (cloud && StrUtil.isNotBlank(cloudBackend) && !cloudBackend.equals(service.getBackend())) continue;
            switch (scope) {
                case CLOUD:
                    if (!cloud) continue;
                    break;
                case LOCAL:
                    if (cloud && configuredKey(service) == null) continue;
                    break;
                case UPSTREAM:
                    if (cloud) continue;
                    break;
            }
            String id = publicId(service, service.getModel());
            ILlmAdapter first = result.putIfAbsent(id, adapter);
            if (scope == Scope.LOCAL && first != null && isLanding(first) && !cloud) {
                // Replacing the value preserves the original YAML position of this public ID.
                result.put(id, adapter);
            }
        }
        return result;
    }

    public static ILlmAdapter find(Map<String, ILlmAdapter> routes, String model) {
        if (StrUtil.isBlank(model)) return null;
        int separator = model.indexOf('/');
        if (separator >= 0) {
            ILlmAdapter exact = routes.get(model);
            if (exact != null) return exact;
            // Normalization in legacy OpenAI-compatible clients can change the case of the provider.
            for (Map.Entry<String, ILlmAdapter> entry : routes.entrySet()) {
                String id = entry.getKey();
                if (id.indexOf('/') == separator && id.regionMatches(true, 0, model, 0, separator)
                        && id.substring(separator).equals(model.substring(separator))) return entry.getValue();
            }
            // A native ID may itself contain slashes (e.g. org/model). Never reinterpret a known provider.
            String prefix = model.substring(0, separator);
            for (ILlmAdapter registered : LlmManager.getInstance().getAllAdapters()) {
                ModelService service = (ModelService) registered;
                String id = publicId(service, service.getModel());
                if (id != null && id.indexOf('/') == separator && id.regionMatches(true, 0, prefix, 0, separator)) {
                    return null;
                }
            }
        }
        for (Map.Entry<String, ILlmAdapter> entry : routes.entrySet()) {
            String id = entry.getKey();
            int slash = id.indexOf('/');
            if (model.equals(id) || (slash >= 0 && model.equals(id.substring(slash + 1)))) return entry.getValue();
        }
        return null;
    }

    public static ILlmAdapter upstream(String model) {
        // A terminal hop must resolve the complete public ID. Dropping its provider could change accounts.
        return routes(Scope.UPSTREAM, null).get(model);
    }

    public static Set<String> configuredCloudModels(String backend) {
        return routes(Scope.CLOUD, backend).keySet();
    }

    public static Set<String> configuredLocalKeyModels(String backend) {
        return routes(Scope.LOCAL, backend).keySet();
    }

    public static boolean isLanding(ILlmAdapter adapter) {
        return unwrap(adapter) instanceof LandingAdapter;
    }

    private static ILlmAdapter unwrap(ILlmAdapter adapter) {
        return adapter instanceof ProxyLlmAdapter ? ((ProxyLlmAdapter) adapter).getLlmAdapter() : adapter;
    }

    public static String configuredKey(ModelService service) {
        if (hasUsableKey(service.getApiKey())) return service.getApiKey().trim();
        if (service.getApiKeys() != null) {
            for (String key : service.getApiKeys()) if (hasUsableKey(key)) return key.trim();
        }
        return null;
    }

    public static boolean hasUsableKey(String key) {
        return StrUtil.isNotBlank(key) && !key.trim().regionMatches(true, 0, "your", 0, 4)
                && !key.trim().startsWith("${");
    }
}
