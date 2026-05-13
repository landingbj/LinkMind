package ai.llm.hook.impl;

import ai.annotation.Component;
import ai.annotation.ConditionalOnProperty;
import ai.annotation.Order;
import ai.llm.hook.AfterModel;
import ai.llm.pojo.ModelContext;
import ai.llm.service.TokenUsageService;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.Usage;
import io.reactivex.Observable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AfterModel hook that forwards every LLM call's token usage to
 * {@link TokenUsageService} so the caller's apiKey is metered / billed by
 * the SaaS backend. Pure billing — local analytics is handled separately
 * by {@link TokenStatisticsImpl}.
 *
 * <p>The caller's apiKey is read from {@code ModelContext#getUserApiKey()},
 * which is the sticky copy captured at the public entry point before any
 * adapter / route rewrites {@code request.apiKey}.
 */
@Order(4)
@Component
@ConditionalOnProperty(name = "functions.chat.token_charge", havingValue = "true")
public class TokenChargeImpl implements AfterModel {

    private final TokenUsageService tokenUsageService = TokenUsageService.getInstance();

    @Override
    public ChatCompletionResult apply(ModelContext context) {
        ChatCompletionResult result = context.getResult();
        if (result != null && result.getUsage() != null) {
            charge(context, result);
        }
        return result;
    }

    @Override
    public Observable<ChatCompletionResult> stream(ModelContext context) {
        AtomicBoolean charged = new AtomicBoolean(false);
        return context.getStreamResult().doOnNext(chunk -> {
            if (charged.get()) {
                return;
            }
            if (chunk != null && chunk.getUsage() != null) {
                Usage u = chunk.getUsage();
                if (u.getTotal_tokens() > 0) {
                    charged.set(true);
                    charge(context, chunk);
                }
            }
        });
    }

    private void charge(ModelContext context, ChatCompletionResult result) {
        if (context == null) {
            return;
        }
        String apiKey = context.getUserApiKey();
        tokenUsageService.recordUsage(apiKey, context.getRequest(), result);
    }
}
