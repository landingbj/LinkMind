package ai.llm.routing;

import ai.llm.adapter.ILlmAdapter;
import ai.llm.adapter.impl.BaichuanAdapter;
import ai.llm.adapter.impl.DeepSeekAdapter;
import ai.llm.adapter.impl.DoubaoAdapter;
import ai.llm.adapter.impl.ErnieAdapter;
import ai.llm.adapter.impl.LandingAdapter;
import ai.llm.adapter.impl.MiniMaxAdapter;
import ai.llm.adapter.impl.MoonshotAdapter;
import ai.llm.adapter.impl.ProxyLlmAdapter;
import ai.llm.adapter.impl.QwenAdapter;
import ai.llm.adapter.impl.QwenCompatibleAdapter;
import ai.llm.adapter.impl.StepFunAdapter;
import ai.llm.adapter.impl.XiaoMiAdapter;
import ai.llm.adapter.impl.ZhipuAdapter;
import ai.common.ModelService;
import ai.manager.LlmManager;
import cn.hutool.core.util.StrUtil;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Decides whether an inbound API key can be used as a one-request upstream
 * credential. Routing is intentionally model-led: a prefixed model such as
 * {@code Alibaba/qwen-plus} is registered to Landing, while {@code qwen-plus}
 * is registered to the Alibaba direct adapter. API-key text never selects the
 * provider because most providers issue generic {@code sk-...} keys.
 */
public final class BringYourOwnKeyRouter {

    private BringYourOwnKeyRouter() {
    }

    public static Decision resolve(String model, String apiKey) {
        ILlmAdapter adapter = model == null ? null : LlmManager.getInstance().getAdapter(model);
        return resolve(model, apiKey, adapter);
    }

    /**
     * A configured cloud model belongs to LinkMind SaaS and must never treat
     * the caller's credential as an upstream provider credential.
     */
    public static boolean isConfiguredCloudModel(String model) {
        return !StrUtil.isBlank(model) && configuredCloudModels(null).contains(model);
    }

    /**
     * Finds the cloud-model alias for an unprefixed direct model. For example,
     * with both models configured, {@code qwen-plus} resolves to
     * {@code Alibaba/qwen-plus}. The alias is configuration-driven rather than
     * hard-coded so the public cloud catalogue remains the source of truth.
     */
    public static String resolveCloudModelAlias(String model) {
        return resolveCloudModelAlias(model, null);
    }

    /**
     * Same as {@link #resolveCloudModelAlias(String)}, constrained to the
     * configured cloud backend when one is supplied. This prevents a box with
     * more than one Landing-compatible backend from accidentally using the
     * wrong account or endpoint.
     */
    public static String resolveCloudModelAlias(String model, String cloudBackend) {
        if (StrUtil.isBlank(model) || model.indexOf('/') >= 0) {
            return null;
        }
        for (ILlmAdapter adapter : LlmManager.getInstance().getAllAdapters()) {
            if (!(adapter instanceof ModelService) || !isConfiguredLandingAdapter(adapter, cloudBackend)) {
                continue;
            }
            String cloudModel = ((ModelService) adapter).getModel();
            if (StrUtil.isBlank(cloudModel)) {
                continue;
            }
            int slash = cloudModel.indexOf('/');
            if (slash > 0 && slash < cloudModel.length() - 1
                    && model.equals(cloudModel.substring(slash + 1))) {
                return cloudModel;
            }
        }
        return null;
    }

    /**
     * Returns the configured box-to-cloud credential for a Landing model.
     * This is used exclusively for local {@code lmk_} requests; a caller's
     * local key must never become the credential sent to the cloud service.
     */
    public static String resolveConfiguredCloudApiKey(String cloudModel) {
        return resolveConfiguredCloudApiKey(cloudModel, null);
    }

    /** Returns the cloud credential only from the requested configured backend. */
    public static String resolveConfiguredCloudApiKey(String cloudModel, String cloudBackend) {
        ILlmAdapter adapter = cloudModel == null ? null : LlmManager.getInstance().getAdapter(cloudModel);
        if (!(adapter instanceof ModelService) || !isConfiguredLandingAdapter(adapter, cloudBackend)) {
            return null;
        }
        ModelService modelService = (ModelService) adapter;
        String apiKey = usableConfiguredKey(modelService.getApiKey());
        if (apiKey != null) {
            return apiKey;
        }
        if (modelService.getApiKeys() != null) {
            for (String candidate : modelService.getApiKeys()) {
                apiKey = usableConfiguredKey(candidate);
                if (apiKey != null) {
                    return apiKey;
                }
            }
        }
        return null;
    }

    /** True only when a direct provider model has a usable configured backend credential. */
    public static boolean hasConfiguredDirectBackend(String model) {
        ILlmAdapter adapter = model == null ? null : LlmManager.getInstance().getAdapter(model);
        if (!(adapter instanceof ModelService) || !isDirectProviderAdapter(adapter)) {
            return false;
        }
        ModelService modelService = (ModelService) adapter;
        if (usableConfiguredKey(modelService.getApiKey()) != null) {
            return true;
        }
        if (modelService.getApiKeys() != null) {
            for (String candidate : modelService.getApiKeys()) {
                if (usableConfiguredKey(candidate) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Cloud catalogue shown to a LinkMind SaaS key. */
    public static Set<String> configuredCloudModels(String cloudBackend) {
        Set<String> models = new LinkedHashSet<String>();
        for (ILlmAdapter adapter : LlmManager.getInstance().getAllAdapters()) {
            if (!(adapter instanceof ModelService) || !isConfiguredLandingAdapter(adapter, cloudBackend)) {
                continue;
            }
            addModels(models, ((ModelService) adapter).getModel());
        }
        return models;
    }

    /** Direct-provider catalogue shown to a caller using its own provider key. */
    public static Set<String> configuredDirectModels() {
        Set<String> models = new LinkedHashSet<String>();
        for (ILlmAdapter adapter : LlmManager.getInstance().getAllAdapters()) {
            if (adapter instanceof ModelService && isDirectProviderAdapter(adapter)) {
                addModels(models, ((ModelService) adapter).getModel());
            }
        }
        return models;
    }

    /** All enabled registered models, retained for legacy unauthenticated discovery calls. */
    public static Set<String> configuredModels() {
        Set<String> models = new LinkedHashSet<String>();
        for (ILlmAdapter adapter : LlmManager.getInstance().getAllAdapters()) {
            if (adapter instanceof ModelService && Boolean.TRUE.equals(((ModelService) adapter).getEnable())) {
                addModels(models, ((ModelService) adapter).getModel());
            }
        }
        return models;
    }

    /**
     * Local keys can use directly configured models and the unprefixed names
     * of cloud models. Direct wins later in the request path.
     */
    public static Set<String> configuredLocalKeyModels(String cloudBackend) {
        Set<String> models = configuredDirectModels();
        for (String cloudModel : configuredCloudModels(cloudBackend)) {
            int slash = cloudModel.indexOf('/');
            if (slash > 0 && slash < cloudModel.length() - 1) {
                models.add(cloudModel.substring(slash + 1));
            }
        }
        return models;
    }

    /**
     * Visible for unit tests. The adapter is the model selected by the normal
     * LinkMind routing table, not an adapter selected from the API-key format.
     */
    public static Decision resolve(String model, String apiKey, ILlmAdapter adapter) {
        if (StrUtil.isBlank(model) || StrUtil.isBlank(apiKey) || adapter == null
                || isConfiguredCloudModel(model) || model.indexOf('/') >= 0) {
            return Decision.notApplicable();
        }

        return isDirectProviderAdapter(adapter) ? Decision.direct() : Decision.notApplicable();
    }

    /**
     * Whether this adapter can accept a caller-owned provider credential.
     * Box configurations intentionally omit customer provider keys, so these
     * adapters must still be registered for the stage-1 BYOK route.
     */
    public static boolean isDirectProviderAdapter(ILlmAdapter adapter) {
        ILlmAdapter target = adapter;
        if (target instanceof ProxyLlmAdapter) {
            target = ((ProxyLlmAdapter) target).getLlmAdapter();
        }
        if (target instanceof QwenCompatibleAdapter || target instanceof QwenAdapter) {
            return true;
        }
        if (target instanceof DeepSeekAdapter) {
            return true;
        }
        if (target instanceof ErnieAdapter) {
            return true;
        }
        if (target instanceof ZhipuAdapter) {
            return true;
        }
        if (target instanceof MoonshotAdapter) {
            return true;
        }
        if (target instanceof DoubaoAdapter) {
            return true;
        }
        if (target instanceof MiniMaxAdapter) {
            return true;
        }
        if (target instanceof XiaoMiAdapter) {
            return true;
        }
        if (target instanceof BaichuanAdapter) {
            return true;
        }
        if (target instanceof StepFunAdapter) {
            return true;
        }
        return false;
    }

    private static boolean isLandingAdapter(ILlmAdapter adapter) {
        ILlmAdapter target = adapter;
        if (target instanceof ProxyLlmAdapter) {
            target = ((ProxyLlmAdapter) target).getLlmAdapter();
        }
        return target instanceof LandingAdapter;
    }

    private static boolean isConfiguredLandingAdapter(ILlmAdapter adapter, String cloudBackend) {
        if (!isLandingAdapter(adapter) || StrUtil.isBlank(cloudBackend)) {
            return isLandingAdapter(adapter);
        }
        return adapter instanceof ModelService
                && cloudBackend.trim().equals(((ModelService) adapter).getBackend());
    }

    private static void addModels(Set<String> target, String configuredModels) {
        if (StrUtil.isBlank(configuredModels)) {
            return;
        }
        for (String model : configuredModels.split(",")) {
            if (!StrUtil.isBlank(model)) {
                target.add(model.trim());
            }
        }
    }

    private static String usableConfiguredKey(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.startsWith("your-") ? null : normalized;
    }

    public static final class Decision {
        private final boolean direct;
        private Decision(boolean direct) {
            this.direct = direct;
        }

        private static Decision direct() {
            return new Decision(true);
        }

        public static Decision notApplicable() {
            return new Decision(false);
        }

        public boolean isDirect() {
            return direct;
        }

    }
}
