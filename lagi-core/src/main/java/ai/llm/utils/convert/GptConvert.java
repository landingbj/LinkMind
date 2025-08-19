package ai.llm.utils.convert;

import ai.llm.utils.LLMErrorConstants;
import ai.openai.pojo.ChatCompletionResult;
import com.google.gson.Gson;
import okhttp3.Response;

public class GptConvert {

    private static final Gson gson = new Gson();

//    https://platform.openai.com/docs/guides/error-codes/api-errors
    public static int convert(Object object) {
        if(object instanceof  Integer) {
            return convertByInt((int) object);
        }
        if(object instanceof Response) {
            return convertByResponse((Response) object);
        }
        return LLMErrorConstants.OTHER_ERROR;
    }

    public static int convertByInt(int errorCode) {
        if(errorCode == 401) {
            return LLMErrorConstants.INVALID_AUTHENTICATION_ERROR;
        }
        if(errorCode == 403) {
            return LLMErrorConstants.PERMISSION_DENIED_ERROR;
        }
        if(errorCode == 429) {
            return LLMErrorConstants.RATE_LIMIT_REACHED_ERROR;
        }
        if(errorCode == 500) {
            return LLMErrorConstants.SERVER_ERROR;
        }
        if(errorCode == 503) {
            return LLMErrorConstants.OTHER_ERROR;
        }
        return LLMErrorConstants.OTHER_ERROR;
    }

    public static int convertByResponse(Response response) {
        return convert(response.code());
    }

    public static ChatCompletionResult convert2ChatCompletionResult(String body) {
        return gson.fromJson(body, ChatCompletionResult.class);
    }

    public static ChatCompletionResult convertSteamLine2ChatCompletionResult(String body) {
        if (body.equals("[DONE]")) {
            return null;
        }
        ChatCompletionResult result = gson.fromJson(body, ChatCompletionResult.class);
//        result.getChoices().forEach(choice -> {
//            choice.setMessage(choice.getDelta());
//            choice.setDelta(null);
//        });
        return result;
    }
}
