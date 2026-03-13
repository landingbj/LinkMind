package ai.llm.responses;

import ai.common.ModelService;
import ai.common.exception.RRException;
import ai.llm.adapter.impl.GPTAdapter;
import ai.llm.adapter.impl.GPTAzureAdapter;
import ai.llm.adapter.impl.OpenAIStandardAdapter;
import ai.llm.adapter.impl.QwenAdapter;
import ai.llm.utils.LLMErrorConstants;
import ai.wrapper.impl.AlibabaAdapter;
import cn.hutool.core.util.StrUtil;

public final class ResponseProtocolUtil {

    private ResponseProtocolUtil() {
    }

    public static String normalize(String protocol) {
        if (StrUtil.isBlank(protocol)) {
            return ResponseProtocolConstants.COMPLETION;
        }
        String normalized = protocol.trim().toLowerCase();
        if (ResponseProtocolConstants.COMPLETION.equals(normalized) || ResponseProtocolConstants.RESPONSE.equals(normalized)) {
            return normalized;
        }
        throw invalidRequest("unsupported protocol: " + protocol);
    }

    public static boolean isResponseProtocol(ModelService modelService) {
        return modelService != null && ResponseProtocolConstants.RESPONSE.equals(normalize(modelService.getProtocol()));
    }

    public static boolean supportsResponses(String driver) {
        return StrUtil.equals(driver, GPTAdapter.class.getName())
                || StrUtil.equals(driver, OpenAIStandardAdapter.class.getName())
                || StrUtil.equals(driver, GPTAzureAdapter.class.getName())
                || StrUtil.equals(driver, QwenAdapter.class.getName())
                || StrUtil.equals(driver, AlibabaAdapter.class.getName());
    }

    public static boolean supportsResponses(ModelService modelService) {
        return modelService instanceof GPTAdapter
                || modelService instanceof OpenAIStandardAdapter
                || modelService instanceof GPTAzureAdapter
                || modelService instanceof QwenAdapter;
    }

    public static RRException invalidRequest(String message) {
        return new RRException(LLMErrorConstants.INVALID_REQUEST_ERROR,
                String.format("{\"error\":\"%s\"}", message.replace("\"", "\\\"")));
    }
}
