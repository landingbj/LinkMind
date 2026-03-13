package ai.llm.responses;

import cn.hutool.core.util.StrUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class QwenResponseProtocolUtil {
    public static final String DEFAULT_RESPONSES_API_ADDRESS =
            "https://dashscope.aliyuncs.com/api/v2/apps/protocols/compatible-mode/v1/responses";
    private static final Set<String> SUPPORTED_MODELS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "qwen-plus",
            "qwen-flash",
            "qwen3.5-plus",
            "qwen3.5-flash",
            "qwen3-max",
            "qwen3-coder-plus",
            "qwen3-coder-flash",
            "qwen3.5-14b-instruct-2507",
            "qwen3.5-32b-instruct-2507",
            "qwen3.5-72b-instruct-2507",
            "qwen3.5-235b-a22b-thinking-2507"
    )));

    private QwenResponseProtocolUtil() {
    }

    public static boolean supportsResponsesModel(String model) {
        return StrUtil.isNotBlank(model) && SUPPORTED_MODELS.contains(model.trim());
    }

    public static boolean canResolveResponsesApiAddress(String apiAddress) {
        if (StrUtil.isBlank(apiAddress)) {
            return true;
        }
        return apiAddress.endsWith("/responses")
                || apiAddress.endsWith("/compatible-mode/v1")
                || apiAddress.endsWith("/chat/completions");
    }

    public static String resolveResponsesApiAddress(String apiAddress) {
        if (StrUtil.isBlank(apiAddress)) {
            return DEFAULT_RESPONSES_API_ADDRESS;
        }
        if (apiAddress.endsWith("/responses")) {
            return apiAddress;
        }
        if (apiAddress.endsWith("/compatible-mode/v1")) {
            return apiAddress + "/responses";
        }
        if (apiAddress.endsWith("/chat/completions")) {
            return apiAddress.substring(0, apiAddress.length() - "/chat/completions".length()) + "/responses";
        }
        throw ResponseProtocolUtil.invalidRequest("qwen api_address must end with /compatible-mode/v1, /chat/completions or /responses");
    }
}
