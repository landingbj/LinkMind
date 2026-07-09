package ai.openai.pojo;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
public class ChatCompletionRequest {
    @JsonAlias({"session_id"})
    private String sessionId;
    @JsonAlias({"extra_body"})
    private ExtraBody extraBody;
    private String model;
    private double temperature = 1;
    private Integer max_tokens;
    private Integer max_completion_tokens;
    private String category;
    private List<ChatMessage> messages;
    private Boolean stream;
    private List<Tool> tools;
    private String tool_choice;
    private Boolean parallel_tool_calls;
    private Double presence_penalty;
    private Double frequency_penalty;
    private Double top_p;
    private ResponseFormat response_format;
    private Map<String, Object> stream_options;
    private Boolean logprobs;
    private transient Boolean enableHook = true;
    private transient Boolean enableAfter = true;
    private transient Boolean preserveInputMessages;
    @JsonIgnore
    private transient ChatCompletionResult localCompletionResult;
    /**
     * Optional per-hook allowlist consulted by {@code HookService} for both
     * {@code BeforeModel} and {@code AfterModel} dispatch. When this set is
     * {@code null}, every registered hook is allowed (subject to the global
     * {@link #enableHook} / {@link #enableAfter} switches). When non-null,
     * only hooks whose concrete class is assignable to one of the entries
     * may run; everything else is skipped. Matching uses
     * {@code isAssignableFrom}, so a superclass or interface entry covers
     * all of its implementations (e.g. adding {@code AfterModel.class}
     * enables every AfterModel hook).
     */
    private transient Set<Class<?>> enabledHooks;
    private Boolean store;
    private String apiKey;
    private String userApiKey;
    @JsonAlias({"chat_template_kwargs"})
    private Map<String, Object> chat_template_kwargs;

    /** Add a hook class (or supertype) to the per-request allowlist. */
    public ChatCompletionRequest enableOnlyHook(Class<?> hookClass) {
        if (hookClass == null) {
            return this;
        }
        if (enabledHooks == null) {
            enabledHooks = new HashSet<>();
        }
        enabledHooks.add(hookClass);
        return this;
    }

    /**
     * @return {@code true} when no allowlist is configured, or when
     * {@code hookClass} matches one of the allowlist entries (by
     * {@code isAssignableFrom}).
     */
    public boolean isHookEnabled(Class<?> hookClass) {
        if (hookClass == null || enabledHooks == null) {
            return true;
        }
        for (Class<?> allowed : enabledHooks) {
            if (allowed != null && allowed.isAssignableFrom(hookClass)) {
                return true;
            }
        }
        return false;
    }
}
