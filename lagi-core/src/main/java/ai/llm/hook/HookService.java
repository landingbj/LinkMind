package ai.llm.hook;

import ai.llm.pojo.ModelContext;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.utils.BeanManageUtil;
import io.reactivex.Observable;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HookService {

    private static final Logger log = LoggerFactory.getLogger(HookService.class);

    @Getter
    private static final HookService instance = new HookService();

    private HookService() {
    }

    public ChatCompletionRequest beforeModel(ModelContext context) {
        ChatCompletionRequest request = context.getRequest();
        if (request == null || BeanManageUtil.getBeansByType(BeforeModel.class) == null) {
            return request;
        }
        for (BeforeModel beforeModel : BeanManageUtil.getBeansByType(BeforeModel.class)) {
            try {
                ChatCompletionRequest next = beforeModel.beforeModel(context);
                if (next != null) {
                    request = next;
                }
            } catch (Exception e) {
                log.error("", e);
            }
        }
        return request;
    }

    public ChatCompletionResult AfterModel(ModelContext context) {
        ChatCompletionResult result = context.getResult();
        if (result == null || BeanManageUtil.getBeansByType(AfterModel.class) == null) {
            return result;
        }
        context.setResult(result);
        for (AfterModel afterModel : BeanManageUtil.getBeansByType(AfterModel.class)) {
            try {
                ChatCompletionResult next = afterModel.apply(context);
                if (next != null) {
                    result = next;
                    context.setResult(result);
                }
            } catch (Exception e) {
                log.error("HookService afterModel error", e);
            }
        }
        return result;
    }

    public Observable<ChatCompletionResult> streamApply(ModelContext context) {
        Observable<ChatCompletionResult> result = context.getStreamResult();
        if (result == null || BeanManageUtil.getBeansByType(AfterModel.class) == null) {
            return result;
        }
        context.setStreamResult(result);
        for (AfterModel afterModel : BeanManageUtil.getBeansByType(AfterModel.class)) {
            try {
                Observable<ChatCompletionResult> next = afterModel.stream(context);
                if (next != null) {
                    result = next;
                    context.setStreamResult(result);
                }
            } catch (Exception e) {
                log.error("HookService afterModel error", e);
            }
        }
        return result;
    }

}
