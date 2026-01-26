//package ai.llm.adapter.impl;
//
//import ai.annotation.LLM;
//import ai.common.ModelService;
//import ai.common.exception.RRException;
//import ai.llm.adapter.ILlmAdapter;
//import ai.llm.pojo.LlmApiResponse;
//import ai.llm.utils.LLMErrorConstants;
//import ai.llm.utils.OpenAiApiUtil;
//import ai.llm.utils.convert.SenseConvert;
//import ai.openai.pojo.*;
//import cn.hutool.core.text.StrFormatter;
//import com.auth0.jwt.JWT;
//import com.auth0.jwt.algorithms.Algorithm;
//import io.reactivex.Observable;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.util.*;
//
//@LLM(modelNames = {"SenseChat-Turbo", "SenseChat-FunctionCall", "SenseChat-5", "SenseChat-128K", "SenseChat-32K"})
//public class SenseChatAdapter extends ModelService implements ILlmAdapter {
//    private static final Logger logger = LoggerFactory.getLogger(SenseChatAdapter.class);
//    private static final String COMPLETIONS_URL = "https://api.sensenova.cn/compatible-mode/v1/chat/completions";
//    private static final int HTTP_TIMEOUT = 15 * 1000;
//
//    @Override
//    public boolean verify() {
//        if (getAccessKeySecret() == null || getAccessKeySecret().startsWith("you")) {
//            return false;
//        }
//        return getAccessKeyId() != null && !getAccessKeyId().startsWith("you");
//    }
//
//    @Override
//    public ChatCompletionResult completions(ChatCompletionRequest chatCompletionRequest) {
//        LlmApiResponse llmApiResponse = llmCompletions(chatCompletionRequest);
//        if (llmApiResponse.getCode() != 200) {
//            logger.error("SenseChatAdapter api code:{}  error:{} ", llmApiResponse.getCode(), llmApiResponse.getMsg());
//            throw new RRException(llmApiResponse.getCode(), llmApiResponse.getMsg());
//        }
//        return llmApiResponse.getData();
//    }
//
//    @Override
//    public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest chatCompletionRequest) {
//        LlmApiResponse llmApiResponse = llmCompletions(chatCompletionRequest);
//        if (llmApiResponse.getCode() != 200) {
//            logger.error("SenseChatAdapter stream  api code:{}  error:{} ", llmApiResponse.getCode(), llmApiResponse.getMsg());
//            throw new RRException(llmApiResponse.getCode(), llmApiResponse.getMsg());
//        }
//        return llmApiResponse.getStreamData();
//    }
//
//    public LlmApiResponse llmCompletions(ChatCompletionRequest chatCompletionRequest) {
//        setDefaultModel(chatCompletionRequest);
//        String token = sign(getAccessKeyId(), getAccessKeySecret());
//        LlmApiResponse completions;
//        if (chatCompletionRequest.getStream()) {
//            completions = OpenAiApiUtil.streamCompletions(token,
//                    COMPLETIONS_URL,
//                    HTTP_TIMEOUT,
//                    chatCompletionRequest,
//                    SenseConvert::convertStreamLine2ChatCompletionResult,
//                    SenseConvert::convertByResponse);
//        } else {
//            completions = OpenAiApiUtil.completions(token,
//                    COMPLETIONS_URL,
//                    HTTP_TIMEOUT,
//                    chatCompletionRequest,
//                    SenseConvert::convert2ChatCompletionResult,
//                    SenseConvert::convertByResponse);
//        }
//        return completions;
//    }
//
//    private void setDefaultModel(ChatCompletionRequest request) {
//        if (request.getModel() == null) {
//            request.setModel(getModel());
//        }
//    }
//
//    private String sign(String ak, String sk) {
//        try {
//            Date expiredAt = new Date(System.currentTimeMillis() + 1800 * 1000);
//            Date notBefore = new Date(System.currentTimeMillis() - 5 * 1000);
//            Algorithm algo = Algorithm.HMAC256(sk);
//            Map<String, Object> header = new HashMap<String, Object>();
//            header.put("alg", "HS256");
//            return JWT.create()
//                    .withIssuer(ak)
//                    .withHeader(header)
//                    .withExpiresAt(expiredAt)
//                    .withNotBefore(notBefore)
//                    .sign(algo);
//        } catch (Exception e) {
//            String msg = StrFormatter.format("{\"error\":\"{}\"}", e.getMessage());
//            Integer code = LLMErrorConstants.INVALID_AUTHENTICATION_ERROR;
//            logger.error("SenseChat api code {},  error {}", code, msg);
//            throw new RRException(code, msg);
//        }
//    }
//}
