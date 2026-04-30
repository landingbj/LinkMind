package ai.llm.utils;

import ai.openai.pojo.ChatCompletionChoice;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.openai.pojo.ExtraBody;
import ai.openai.pojo.PromptTokensDetails;
import ai.openai.pojo.Usage;
import ai.utils.LagiGlobal;

import java.util.Collections;
import java.util.UUID;

public class LLMErrorConstants {

    /**
     * 请求参数不合法
     */
    public static final Integer INVALID_REQUEST_ERROR = 600;

    /**
     * 授权错误
     */
    public static final Integer INVALID_AUTHENTICATION_ERROR = 601;
    /**
     * 权限被拒绝
     */
    public static final Integer PERMISSION_DENIED_ERROR = 602;

    /**
     * 资源不存在
     */
    public static final Integer RESOURCE_NOT_FOUND_ERROR = 603;

    /**
     * 访问频率限制
     */
    public static final Integer RATE_LIMIT_REACHED_ERROR = 604;

    /**
     * 模型内部错误
     */
    public static final Integer SERVER_ERROR = 605;

    /**
     *  其他错误
     */
    public static final Integer OTHER_ERROR = 606;

    /**
     *  超时
     */
    public static final Integer TIME_OUT = 607;

    /**
     *  没有可用的模型
     */
    public static final Integer NO_AVAILABLE_MODEL = 608;

    /**
     * 内容安全拦截，禁止继续 fallback 到其他模型
     */
    public static final Integer CONTENT_SAFETY_BLOCKED = 609;

    public static final Integer UNAUTHORIZED_CODE = 401;

    public static final String UNAUTHORIZED_MESSAGE = "Your API key has insufficient balance. Please recharge before continuing.";

    public static boolean isContentSafetyBlocked(Integer errorCode) {
        return CONTENT_SAFETY_BLOCKED.equals(errorCode);
    }


    public static ChatCompletionResult errorResponse(ChatCompletionRequest chatCompletionRequest) {
        return errorResponse(chatCompletionRequest, UNAUTHORIZED_CODE, UNAUTHORIZED_MESSAGE);
    }

    public static ChatCompletionResult errorResponse(ChatCompletionRequest chatCompletionRequest, int code, String message) {
        String model = chatCompletionRequest.getModel();
        boolean stream = chatCompletionRequest.getStream();
        ExtraBody extraBody = chatCompletionRequest.getExtraBody();
        String mateUrl = extraBody != null ? extraBody.getMateUrl() : null;
        ChatCompletionResult result = new ChatCompletionResult();
        result.setId(UUID.randomUUID().toString());
        result.setObject(stream ? "chat.completion.chunk" : "chat.completion");
        result.setCreated(System.currentTimeMillis() / 1000);
        result.setModel(model);

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setRole(LagiGlobal.LLM_ROLE_ASSISTANT);
        chatMessage.setContent(buildApiKeyErrorContent(mateUrl, Integer.toString(code), message));

        ChatCompletionChoice choice = new ChatCompletionChoice();
        choice.setIndex(0);
        choice.setFinish_reason("stop");
        if (stream) {
            choice.setDelta(chatMessage);
        } else {
            choice.setMessage(chatMessage);
        }
        result.setChoices(Collections.singletonList(choice));

        Usage usage = new Usage();
        PromptTokensDetails promptTokensDetails = new PromptTokensDetails();
        usage.setPrompt_tokens_details(promptTokensDetails);
        result.setUsage(usage);
        return result;
    }

    private static String buildApiKeyErrorContent(String mateUrl, String code, String message) {
        String errorContent = "An error occurred while calling the large language model.\n\n"
                + "Error code: " + safeString(code) + "\n"
                + "Error message: " + safeString(message) + "\n";
        if (safeString(mateUrl).isEmpty()) {
            return errorContent;
        }
        return errorContent + "\n"
                + "You can log in to **[" + mateUrl + "](" + mateUrl + ")** to recharge, manage configuration, and perform other operations.\n";
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }
}
