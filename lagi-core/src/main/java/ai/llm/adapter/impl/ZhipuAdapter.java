//package ai.llm.adapter.impl;
//
//import ai.annotation.LLM;
//import ai.common.ModelService;
//import ai.common.utils.MappingIterable;
//import ai.llm.adapter.ILlmAdapter;
//import ai.llm.utils.convert.ZhiPuConvert;
//import ai.openai.pojo.ChatCompletionChoice;
//import ai.openai.pojo.ChatCompletionRequest;
//import ai.openai.pojo.ChatCompletionResult;
//import ai.openai.pojo.ChatMessage;
//import cn.hutool.core.bean.BeanUtil;
//import com.zhipu.oapi.ClientV4;
//import com.zhipu.oapi.Constants;
//import com.zhipu.oapi.service.v4.model.ChatTool;
//import com.zhipu.oapi.service.v4.model.ModelApiResponse;
//import com.zhipu.oapi.service.v4.model.ModelData;
//import io.reactivex.Observable;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Optional;
//import java.util.stream.Collectors;
//
//@LLM(modelNames = { "glm-3-turbo","glm-4", "glm-4v"})
//public class ZhipuAdapter extends ModelService implements ILlmAdapter {
//
//
//
//    @Override
//    public ChatCompletionResult completions(ChatCompletionRequest chatCompletionRequest) {
//        ClientV4 client = new ClientV4.Builder(getApiKey()).build();
//        ModelApiResponse invokeModelApiResp = client.invokeModelApi(convertRequest(chatCompletionRequest));
//        int code = invokeModelApiResp.getCode();
//        if(code != 200) {
//            throw ZhiPuConvert.convert2RRException(invokeModelApiResp);
//        }
//        return convertResponse(invokeModelApiResp.getData());
//    }
//
//    @Override
//    public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest chatCompletionRequest) {
//        ClientV4 client = new ClientV4.Builder(getApiKey()).build();
//        ModelApiResponse sseModelApiResp = client.invokeModelApi(convertRequest(chatCompletionRequest));
//        int code = sseModelApiResp.getCode();
//        if(code != 200) {
//            throw ZhiPuConvert.convert2RRException(sseModelApiResp);
//        }
//        Iterable<ModelData> resultIterable = sseModelApiResp.getFlowable().blockingIterable();
//        Iterable<ChatCompletionResult> iterable = new MappingIterable<>(resultIterable, this::convertResponse);
//        return Observable.fromIterable(iterable);
//    }
//
//
//    private com.zhipu.oapi.service.v4.model.ChatCompletionRequest convertRequest(ChatCompletionRequest request) {
//        List<com.zhipu.oapi.service.v4.model.ChatMessage> messages = new ArrayList<>();
//        for (ChatMessage chatMessage : request.getMessages()) {
//            com.zhipu.oapi.service.v4.model.ChatMessage msg =
//                    new com.zhipu.oapi.service.v4.model.ChatMessage(chatMessage.getRole(), chatMessage.getContent());
//            messages.add(msg);
//        }
//
//        boolean stream = Optional.ofNullable(request.getStream()).orElse(false);
//        String model = Optional.ofNullable(request.getModel()).orElse(getModel());
//
//        String invokeMethod = Constants.invokeMethod;
//        if (stream) {
//            invokeMethod = Constants.invokeMethodSse;
//        }
//        List<ChatTool> tools = null;
//
//        if(request.getTools() != null &&  (!request.getTools().isEmpty())) {
//            tools = request.getTools().stream().map(tool -> {
//                ChatTool chatTool = new ChatTool();
//                BeanUtil.copyProperties(tool, chatTool);
//                return chatTool;
//            }).collect(Collectors.toList());
//        }
//
//        return com.zhipu.oapi.service.v4.model.ChatCompletionRequest.builder()
//                .model(model)
//                .maxTokens(request.getMax_tokens())
//                .temperature((float) request.getTemperature())
//                .stream(stream)
//                .invokeMethod(invokeMethod)
//                .messages(messages)
//                .tools(tools)
//                .toolChoice(request.getTool_choice())
//                .build();
//    }
//
//    private ChatCompletionResult convertResponse(ModelData modelData) {
//        ChatCompletionResult result = new ChatCompletionResult();
//        result.setId(modelData.getId());
//        result.setCreated(modelData.getCreated());
//        List<ChatCompletionChoice> choices = new ArrayList<>();
//        for (int i = 0; i < modelData.getChoices().size(); i++) {
//            ChatCompletionChoice choice = new ChatCompletionChoice();
//            ChatMessage chatMessage = new ChatMessage();
//            com.zhipu.oapi.service.v4.model.Delta delta = modelData.getChoices().get(i).getDelta();
//            if (delta != null) {
//                chatMessage.setContent(delta.getContent());
//                chatMessage.setRole(delta.getRole());
//            } else {
//                com.zhipu.oapi.service.v4.model.ChatMessage message = modelData.getChoices().get(i).getMessage();
//                chatMessage.setContent(message.getContent().toString());
//                chatMessage.setRole(message.getRole());
//            }
//            choice.setIndex(i);
//            choice.setMessage(chatMessage);
//            choices.add(choice);
//        }
//        result.setChoices(choices);
//        return result;
//    }
//}
