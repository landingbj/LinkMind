package ai.agent.customer;

import ai.agent.Agent;
import ai.agent.customer.pojo.*;
import ai.agent.customer.prompt.Prompt;
import ai.agent.customer.tools.AbstractTool;
import ai.agent.customer.tools.ToolManager;
import ai.agent.customer.tools.ToolUtils;
import ai.common.utils.LRUCache;
import ai.config.ContextLoader;
import ai.config.pojo.AgentConfig;
import ai.llm.service.CompletionsService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import io.reactivex.Observable;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class CustomerAgent extends Agent<ChatCompletionRequest, ChatCompletionResult> {
    private static final Integer maxTryTimes = 3;
    private final CompletionsService completionsService = new CompletionsService();
    private final Gson gson = new Gson();
    protected List<ToolInfo> toolInfoList;

    private static final LRUCache<ToolLlmRequest, ChatCompletionResult> callLLmCache = new LRUCache<>(1000);

    public CustomerAgent(AgentConfig agentConfig) {
        this.agentConfig = agentConfig;
        toolInfoList = new ArrayList<>();
    }

    public CustomerAgent() {
        toolInfoList = new ArrayList<>();
    }

    public CustomerAgent(List<ToolInfo> toolInfoList) {
        this.toolInfoList = toolInfoList;
    }

    private ChatCompletionResult callLLm(ToolLlmRequest toolLlmRequest) {
        ChatCompletionResult result = callLLmCache.get(toolLlmRequest);
        if (result != null) {
            return result;
        }
        return callLLm(toolLlmRequest.getPrompt(), toolLlmRequest.getHistory(), toolLlmRequest.getUserMsg());
    }

    private ChatCompletionResult callLLm(String prompt, List<List<String>> history, String userMsg) {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(ContextLoader.configuration.getAgentGeneralConfiguration().getModel());
        List<ChatMessage> chatMessages = new ArrayList<>();
        ChatMessage systemMessage = new ChatMessage();
        systemMessage.setContent(prompt);
        systemMessage.setRole("system");
        chatMessages.add(systemMessage);
        for (int i = 0; i < history.size(); i++) {
            ChatMessage userMessage = new ChatMessage();
            userMessage.setRole("user");
            userMessage.setContent(history.get(i).get(0));

            ChatMessage assistantMessage = new ChatMessage();
            assistantMessage.setRole("assistant");
            assistantMessage.setContent(history.get(i).get(1));

            chatMessages.add(userMessage);
            chatMessages.add(assistantMessage);
        }
        ChatMessage userMessage = new ChatMessage();
        userMessage.setRole("user");
        userMessage.setContent(userMsg);
        chatMessages.add(userMessage);
        request.setMax_tokens(2048);
        request.setTemperature(0);
        request.setMessages(chatMessages);
        request.setStream(false);
        return completionsService.completions(request);
    }

    private String genPrompt(String question, String agent_scratch) {
        return Prompt.genPrompt(ToolUtils.genToolPrompt(toolInfoList), question, agent_scratch);
    }

    @Override
    public ChatCompletionResult communicate(ChatCompletionRequest data) {
        int count = maxTryTimes;
        String finalAnswer = null;
        List<String> imageUrl = null;
        List<String> fileUrl = null;
        String question = data.getMessages().get(data.getMessages().size() - 1).getContent();
        StringBuilder agent_scratch = new StringBuilder();
//        String user_msg = "你只能完成工具描述相关的目标,对于工具描述无关的目标,请直接认为任务完成且“不”给出任何解答建议。 如果工具调用失败也请直接完成告知调用失败。";
        String user_msg = "决定使用哪个工具。对于工具描述无关的目标或工具调用失败直接调用完成工具, 请不要根据你所掌握的事实知识解答， 而是告知用户为什么没有完成目标。 ";
        String assistant_msg = "";
        List<List<String>> history = new ArrayList<>();
        Set<String> toolNames = toolInfoList.stream().map(ToolInfo::getName).collect(Collectors.toSet());
        while (count-- > 0) {
            String prompt = genPrompt(question, agent_scratch.toString());

            ToolLlmRequest toolLlmRequest = new ToolLlmRequest();
            toolLlmRequest.setPrompt(prompt);
            toolLlmRequest.setHistory(new ArrayList<>(history));
            toolLlmRequest.setUserMsg(user_msg);

            long start = System.currentTimeMillis();
            ChatCompletionResult result = callLLm(toolLlmRequest);
            log.info("customer agent call llm time: {}", (System.currentTimeMillis() - start));
            String answer = result.getChoices().get(0).getMessage().getContent();
            answer = extractJson(answer);

            ResponseTemplate responseTemplate;
            try {
                responseTemplate = gson.fromJson(answer, ResponseTemplate.class);
            } catch (Exception e) {
                agent_scratch.append("\nobservation: 返回的结果不符合要求的json格式");
                continue;
            }
            Action action = responseTemplate.getAction();
            if (action == null) {
                continue;
            }

            if (!callLLmCache.containsKey(toolLlmRequest)) {
                callLLmCache.put(toolLlmRequest, result);
            }

            if ("finish".equals(action.getName())) {
                finalAnswer = action.getArgs().get("result").toString();
                imageUrl = (List<String>) action.getArgs().get("imageUrl");
                fileUrl = (List<String>) action.getArgs().get("fileUrl");
                break;
            }
            String observation = responseTemplate.getThoughts().getSpeak();
            Map<String, Object> args = action.getArgs();
            String call_result = null;
            try {
                if (toolNames.contains(action.getName())) {
                    AbstractTool func = ToolManager.getInstance().getTool(action.getName());
                    call_result = func.apply(args);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            assistant_msg = parseThoughts(responseTemplate.getThoughts());
            agent_scratch.append(StrUtil.format("\nobservation: {} \nexecute action results: {}", observation, call_result == null ? "查询失败" : "查询成功"));
            call_result = call_result == null ? "查询失败" : "查询成功结果如下:" + call_result;
            user_msg = call_result + "\n" + user_msg;
            history.add(Lists.newArrayList(user_msg, assistant_msg));
        }
        if (finalAnswer == null) {
            return null;
        }
        String format = StrUtil.format("{\"created\":0,\"choices\":[{\"index\":0,\"message\":{\"content\":\"{}\"}}]}", finalAnswer);
        ChatCompletionResult chatCompletionResult = gson.fromJson(format, ChatCompletionResult.class);
        ChatMessage message = chatCompletionResult.getChoices().get(0).getMessage();
        if (!CollectionUtil.isEmpty(fileUrl)) {
            message.setFilepath(fileUrl);
        }
        if (!CollectionUtil.isEmpty(imageUrl)) {
            message.setImageList(imageUrl);
        }
        return chatCompletionResult;
    }

    @Override
    public Observable<ChatCompletionResult> stream(ChatCompletionRequest data) {
        throw new UnsupportedOperationException("streaming is not supported");
    }

    @Override
    public boolean canStream() {
        return false;
    }

    private String parseThoughts(Thoughts thoughts) {
        return StrUtil.format("plan: {}\n reasoning:{}\n criticism: {}\n observation:{}",
                thoughts.getPlain(),
                thoughts.getReasoning(),
                thoughts.getCriticism(),
                thoughts.getSpeak());
    }

    @Override
    public void connect() {

    }

    @Override
    public void terminate() {

    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public void send(ChatCompletionRequest request) {

    }

    @Override
    public ChatCompletionResult receive() {
        return null;
    }


    public static String extractJson(String input) {
        Pattern pattern = Pattern.compile("\\{.*\\}", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }
}
