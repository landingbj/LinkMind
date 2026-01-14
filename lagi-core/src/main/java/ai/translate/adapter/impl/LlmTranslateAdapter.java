package ai.translate.adapter.impl;

import ai.common.ModelService;
import ai.llm.service.CompletionsService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.translate.adapter.TranslateAdapter;
import ai.translate.pojo.TranslateResponse;
import ai.translate.pojo.TranslateResult;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class LlmTranslateAdapter extends ModelService implements TranslateAdapter {

    private final CompletionsService completionsService =  new CompletionsService();


    private TranslateResponse getTransResult(String query, String to) {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setMessages(convert2ChatMessage(query, to));
        request.setMax_tokens(1024);
        request.setTemperature(0.8);
        ChatCompletionResult completions = completionsService.completions(request);
        return TranslateResponse.builder()
                .transResult(Lists.newArrayList(TranslateResult.builder()
                        .dst(completions.getChoices().get(0).getMessage().getContent())
                        .src(query)
                        .build()))
                .build();
    }

    private static List<ChatMessage> convert2ChatMessage(String query, String to) {
        List<ChatMessage> chatMessages = new ArrayList<>();
        ChatMessage sysChatMessage = new ChatMessage();
        ChatMessage userChatMessage = new ChatMessage();
        if(to.equals("en")) {
            String systemPrompt = "You are a professional translator, please translate the following text into English.";
            sysChatMessage.setRole("system");
            sysChatMessage.setContent(systemPrompt);

        } else {
            String systemPrompt = "You are a professional translator, please translate the following text into Chinese.";
            sysChatMessage.setRole("system");
            sysChatMessage.setContent(systemPrompt);
        }
        chatMessages.add(sysChatMessage);
        userChatMessage.setRole("user");
        userChatMessage.setContent(query);
        chatMessages.add(userChatMessage);
        return chatMessages;
    }

    @Override
    public String toEnglish(String text) {
        try {
            TranslateResponse en = getTransResult(text, "en");
            return en.getTransResult().get(0).getDst();
        } catch (Exception e) {
            log.error("to english error", e);
        }
        return null ;
    }


    @Override
    public String toChinese(String text) {
        try {
            TranslateResponse zh = getTransResult(text, "zh");
            return zh.getTransResult().get(0).getDst();
        } catch (Exception e) {
            log.error("to english error", e);
        }
        return null ;
    }

}
