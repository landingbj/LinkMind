package ai.llm.utils;

import ai.config.ContextLoader;
import ai.llm.service.CompletionsService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.utils.LagiGlobal;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


@Slf4j
public class ContextUtil {
    private static final CompletionsService completionsService = new CompletionsService();
    private static final String agentGeneralModel = ContextLoader.configuration.getAgentGeneralConfiguration().getModel();

    public static final String SUMMARY_PROMPT = "你是一名对话分析助手。你的任务是判断在以下对话中，**最后一句话**是否和之前出现过的某一句话属于同一轮会话（即语义上是直接延续或回应）。\n" +
            "### 判断标准\n" +
            "1. **同一轮会话**：最后一句和前文某一句在语义、主题或问答关系上有直接延续、补充或回应关系。\n" +
            "2. **不同轮会话**：最后一句话引入了新的主题、问题，或与之前内容无明显联系。\n" +
            "3. 注意代词、省略语和口语化承接（如“那”，“对”，“所以”），这类词往往意味着延续前文。\n" +
            "## 输入和输出示例\n" +
            "**输入**\n" +
            "```\n" +
            "对话:  \n" +
            "A: 你今天吃了吗？  \n" +
            "B: 吃了，在公司楼下。  \n" +
            "A: 好吃吗？  \n" +
            "B: 还行吧。  \n" +
            "A: 那明天还去吗？  \n" +
            "\n" +
            "最后一句: 那明天还去吗？\n" +
            "```\n" +
            "**输出**\n" +
            "```\n" +
            "是\n" +
            "```\n" +
            "### 输入\n" +
            "对话内容：\n" +
            "```\n" +
            "{conversation}\n" +
            "```\n" +
            "待判断的最后一句话：\n" +
            "```\n" +
            "{last_sentence}\n" +
            "```\n" +
            "### 输出\n" +
            "只需输出：`是` 或 `否`，不要输出其它内容。";

    public static boolean checkLastMsgContinuity(ChatCompletionRequest request) {
        List<ChatMessage> allMessages = request.getMessages();
        List<ChatMessage> lastMessages = allMessages.subList(Math.max(0, allMessages.size() - 5), allMessages.size());

        String conversation = lastMessages.stream()
                .map(msg -> {
                    String prefix;
                    if ("user".equalsIgnoreCase(msg.getRole())) {
                        prefix = "A: ";
                    } else if ("assistant".equalsIgnoreCase(msg.getRole())) {
                        prefix = "B: ";
                    } else {
                        prefix = "";
                    }
                    return prefix + msg.getContent();
                })
                .collect(Collectors.joining("\n"));
        String lastSentence = lastMessages.get(lastMessages.size() - 1).getContent();
        String prompt = SUMMARY_PROMPT.replace("{conversation}", conversation).replace("{last_sentence}", lastSentence);
        ChatCompletionRequest chatCompletionRequest = getChatCompletionRequest(prompt);

        try {
            ChatCompletionResult summaryResult = completionsService.completions(chatCompletionRequest);
            String summary = summaryResult.getChoices().get(0).getMessage().getContent();
            return !summary.contains("否");
        } catch (Exception e) {
            log.error("ContextUtil checkLastMsgContinuity error", e);
        }
        return false;
    }


    private static ChatCompletionRequest getChatCompletionRequest(String prompt) {
        ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest();
        chatCompletionRequest.setTemperature(0.3);
        chatCompletionRequest.setStream(false);
        chatCompletionRequest.setMax_tokens(4);
        List<ChatMessage> messages = new ArrayList<>();
        ChatMessage message = new ChatMessage();
        message.setRole(LagiGlobal.LLM_ROLE_USER);
        message.setContent(prompt);
        messages.add(message);
        chatCompletionRequest.setMessages(messages);
        chatCompletionRequest.setModel(agentGeneralModel);
        return chatCompletionRequest;
    }
}
