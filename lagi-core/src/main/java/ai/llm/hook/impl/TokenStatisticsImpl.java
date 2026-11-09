package ai.llm.hook.impl;

import ai.annotation.Component;
import ai.annotation.Order;
import ai.common.ModelService;
import ai.llm.dao.TokenStatisticsDao;
import ai.llm.hook.AfterModel;
import ai.llm.pojo.ModelContext;
import ai.utils.ModelNameUtil;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.Usage;
import io.reactivex.Observable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AfterModel hook that persists every LLM call's token usage to the local
 * {@link TokenStatisticsDao} for in-process analytics / dashboards. This
 * hook is purely a recorder; SaaS-side metering / billing is handled
 * separately by {@link TokenChargeImpl}.
 */
@Order(3)
@Component
public class TokenStatisticsImpl implements AfterModel {

    private static final ExecutorService ASYNC_SAVE =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "llm-token-statistics");
                t.setDaemon(true);
                return t;
            });

    private final TokenStatisticsDao tokenStatisticsDao = new TokenStatisticsDao();

    @Override
    public ChatCompletionResult apply(ModelContext context) {
        ChatCompletionResult result = context.getResult();
        if (result != null && result.getUsage() != null) {
            recordUsage(context, result.getUsage());
        }
        return result;
    }

    @Override
    public Observable<ChatCompletionResult> stream(ModelContext context) {
        AtomicBoolean recorded = new AtomicBoolean(false);
        return context.getStreamResult().doOnNext(chunk -> {
            if (recorded.get()) {
                return;
            }
            if (chunk != null && chunk.getUsage() != null) {
                Usage u = chunk.getUsage();
                if (u.getTotal_tokens() > 0) {
                    recorded.set(true);
                    recordUsage(context, u);
                }
            }
        });
    }

    private void recordUsage(ModelContext context, Usage usage) {
        long total = usage.getTotal_tokens();
        if (total <= 0) {
            return;
        }
        long saved = TokenStatisticsDao.computeSavedTokens(total);
        usage.setSaved_tokens(saved);
        long prompt = usage.getPrompt_tokens();
        long completion = usage.getCompletion_tokens();
        String provider = resolveProvider(context);
        String model = resolveModel(context);
        String sessionId = resolveSessionId(context);
        String userId = resolveAccountUserId(context);
        Long userApiKeyId = resolveAccountUserApiKeyId(context);
        ASYNC_SAVE.execute(() -> tokenStatisticsDao.insert(
                prompt, completion, total, saved, provider, model, sessionId, userId, userApiKeyId));
    }

    private String resolveModel(ModelContext context) {
        if (context == null) {
            return null;
        }
        String requested = context.getBillingModel();
        if (requested == null && context.getRequest() != null) {
            requested = context.getRequest().getModel();
        }
        return ModelNameUtil.resolveBillingModelName(requested, null);
    }

    private String resolveProvider(ModelContext context) {
        if (context == null || context.getAdapter() == null) {
            return null;
        }
        if (context.getAdapter() instanceof ModelService) {
            String type = ((ModelService) context.getAdapter()).getType();
            if (type != null && !type.trim().isEmpty()) {
                return type.trim();
            }
        }
        String name = context.getAdapter().getClass().getSimpleName();
        if (name == null || name.isEmpty()) {
            return null;
        }
        if (name.endsWith("Adapter") && name.length() > "Adapter".length()) {
            return name.substring(0, name.length() - "Adapter".length());
        }
        return name;
    }

    private String resolveSessionId(ModelContext context) {
        if (context == null || context.getRequest() == null) {
            return null;
        }
        return context.getRequest().getSessionId();
    }

    private String resolveAccountUserId(ModelContext context) {
        if (context == null || context.getRequest() == null) {
            return null;
        }
        return context.getRequest().getAccountUserId();
    }

    private Long resolveAccountUserApiKeyId(ModelContext context) {
        if (context == null || context.getRequest() == null) {
            return null;
        }
        return context.getRequest().getAccountUserApiKeyId();
    }
}
