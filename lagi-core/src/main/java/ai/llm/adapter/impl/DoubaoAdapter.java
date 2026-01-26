//package ai.llm.adapter.impl;
//
//import ai.annotation.LLM;
//import ai.common.ModelService;
//import ai.common.exception.RRException;
//import ai.llm.adapter.ILlmAdapter;
//import ai.llm.utils.convert.DouBaoConvert;
//import ai.openai.pojo.ChatCompletionRequest;
//import ai.openai.pojo.ChatCompletionResult;
//import cn.hutool.core.bean.BeanUtil;
//import cn.hutool.json.JSONUtil;
//import com.volcengine.ark.runtime.exception.ArkHttpException;
//import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionChunk;
//import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
//import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
//import com.volcengine.ark.runtime.service.ArkService;
//import io.reactivex.Flowable;
//import io.reactivex.Observable;
//import org.jetbrains.annotations.NotNull;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.List;
//import java.util.Map;
//import java.util.stream.Collectors;
//
//@LLM(modelNames = {"doubao-pro-32k,doubao-pro-4k"})
//public class DoubaoAdapter  extends ModelService implements ILlmAdapter {
//
//
//    private static final Logger log = LoggerFactory.getLogger(DoubaoAdapter.class);
//
//    private String getModelEndpoint(String model) {
//        Map<String, String> collect = Arrays.stream(alias.split(",")).collect(Collectors.toMap(a -> a.split("=")[0], a -> a.split("=")[1]));
//        return collect.get(model);
//    }
//    @Override
//    public ChatCompletionResult completions(ChatCompletionRequest request) {
//        ArkService service = ArkService.builder().apiKey(apiKey).baseUrl("https://ark.cn-beijing.volces.com/api/v3/").build();
//        List<ChatMessage> messages = convertChatMessageList(request);
//        com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest chatCompletionRequest = com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest.builder()
//                .model(getModelEndpoint(model))
//                .messages(messages)
//                .build();
//        try {
//            com.volcengine.ark.runtime.model.completion.chat.ChatCompletionResult chatCompletion = service.createChatCompletion(chatCompletionRequest);
//            ChatCompletionResult result = new ChatCompletionResult();
//            BeanUtil.copyProperties(chatCompletion, result);
//            return result;
//        } catch (ArkHttpException e) {
//            log.error(e.toString());
//            RRException exception = new RRException(DouBaoConvert.convertByInt(e.statusCode), e.getMessage());
//            log.error("doubao api error code : {}, error: {}", exception.getCode(), exception.getMsg());
//            throw exception;
//        }
//    }
//
//    @Override
//    public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest request) {
//        ArkService service = ArkService.builder().apiKey(apiKey).baseUrl("https://ark.cn-beijing.volces.com/api/v3/").build();
//        List<ChatMessage> messages = convertChatMessageList(request);
//        com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest streamChatCompletionRequest = com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest.builder()
//                .model(getModelEndpoint(model))
//                .messages(messages)
//                .build();
//
//        Flowable<ChatCompletionChunk> flowable = service.streamChatCompletion(streamChatCompletionRequest);
//        Observable<ChatCompletionResult> iterable = Observable.create(observableEmitter -> {
//                try {
//                    flowable.blockingForEach(chatCompletionChunk -> {
//                        System.out.println(JSONUtil.toJsonStr(chatCompletionChunk));
//                        if (chatCompletionChunk.getChoices().size()>0){
//                            observableEmitter.onNext(convertResponse(chatCompletionChunk));
//                            if (chatCompletionChunk.getChoices().get(0).getMessage().getContent().equals("")){
//                                service.shutdownExecutor();
//                            }
//                        }
//                    });
//                }catch (ArkHttpException e) {
//                    RRException exception = new RRException(DouBaoConvert.convertByInt(e.statusCode), e.getMessage());
//                    log.error("doubao api error code : {}, error: {}", exception.getCode(), exception.getMsg());
//                } finally {
//                    observableEmitter.onComplete();
//                }
//            });
//        return iterable;
//    }
//
//    private static @NotNull List<ChatMessage> convertChatMessageList(ChatCompletionRequest request) {
//        List<ChatMessage> messages = new ArrayList<>();
//        ChatMessage systemMessage = ChatMessage.builder().role(ChatMessageRole.SYSTEM).content("你是豆包人工智能助手").build();
//        List<ChatMessage> chatMessages = request.getMessages().stream()
//                .map(chatMessage -> ChatMessage.builder()
//                        .role(ChatMessageRole.valueOf(chatMessage.getRole().toUpperCase()))
//                        .content(chatMessage.getContent())
//                        .build())
//                .collect(Collectors.toList());
//        messages.add(systemMessage);
//        messages.addAll(chatMessages);
//        return messages;
//    }
//
//    private ChatCompletionResult convertResponse(ChatCompletionChunk chatCompletionChunk) {
//        ChatCompletionResult chatCompletionResult = new ChatCompletionResult();
//        if (chatCompletionResult!=null) {
//            BeanUtil.copyProperties(chatCompletionChunk, chatCompletionResult);
//        }
//        return chatCompletionResult;
//    }
//}
