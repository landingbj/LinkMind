package ai.pnps.skills;

import ai.common.exception.RRException;
import ai.llm.service.CompletionsService;
import ai.openai.pojo.ChatCompletionChoice;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;
import ai.openai.pojo.Tool;
import ai.openai.pojo.ToolCall;
import ai.pnps.skills.filesystem.Filesystem;
import ai.pnps.skills.filesystem.impl.LocalFileSystemImpl;
import ai.pnps.skills.pojo.*;
import ai.pnps.skills.util.SkillsAgentToolUtil;
import ai.utils.LagiGlobal;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * Java openclaw-like skill workflow:
 * load catalog -> select one skill -> read SKILL.md -> plan -> execute script -> summarize.
 */
public class SkillsAgent {
    private static final int SKILL_MD_MAX_CHARS = 24_000;
    private static final long DEFAULT_SCRIPT_TIMEOUT_SECONDS = 120L;
    private static final int MAX_TOOL_LOOP_TURNS = 10;

    private final SkillLoader loader;
    private final Filesystem fs;

    public SkillsAgent() {
        this(new SkillLoader(), new LocalFileSystemImpl(), false);
    }

    public SkillsAgent(SkillLoader loader,
                       Filesystem filesystem, boolean debug) {
        this.loader = Objects.requireNonNull(loader, "loader");
        Filesystem fs1 = Objects.requireNonNull(filesystem, "filesystem");
        fs = (Filesystem) Proxy.newProxyInstance(fs1.getClass().getClassLoader(), new Class[]{Filesystem.class}, (proxy, method, args) -> {
            String name = method.getName();
            if (debug) {
                System.out.println("===== fs : " + name + " arguments:\t" + Arrays.toString(args) + " =====");
            }
            Object invoke = method.invoke(fs1, args);
            if (debug) {
                System.out.println("===== fs : " + name  + " result:\t"+ invoke + " =====");
            }
            return invoke;
        });
    }

    public List<ChatMessage> runTools(List<ToolCall> toolCalls, ChatCompletionRequest request) {
        List<ChatMessage> tools = new ArrayList<>();
        for (ToolCall toolCall : toolCalls) {
            String name = toolCall.getFunction().getName();
            ChatMessage toolMessage = new ChatMessage();
            toolMessage.setRole(LagiGlobal.LLM_ROLE_TOOL);
            toolMessage.setTool_call_id(toolCall.getId());
            Map<String, String> args = SkillsAgentToolUtil.parseArguments(
                    SkillsAgentToolUtil.toolArgumentsJson(toolCall));
            if("read".equals(name)) {
                String content = SkillsAgentToolUtil.readFromToolArgs(fs.read(args), args, request);
                toolMessage.setContent(content);
                tools.add(toolMessage);
            } else if("exec".equals(name)) {
                toolMessage.setContent(fs.exec(args, DEFAULT_SCRIPT_TIMEOUT_SECONDS));
                tools.add(toolMessage);
            }
        }
        return tools;
    }

    public Future<SkillsAgentResult> runAsync(ChatCompletionRequest request) {
        return CompletableFuture.supplyAsync(() -> run(request));
    }


    public SkillsAgentResult run(ChatCompletionRequest request) {
        Objects.requireNonNull(request, "request");
        update2innerRequest(request);
        List<ChatMessage> chatMessages = request.getMessages();
        if (chatMessages == null) {
            throw new IllegalArgumentException("request.messages required");
        }
        CompletionsService completionsService = new CompletionsService();
        int llmRounds = 0;
        ChatCompletionResult lastResult = null;
        while (true) {
            if (chatMessages.isEmpty()) {
                return new SkillsAgentResult(false, null, "messages is empty", null, null, null);
            }
            ChatMessage last = chatMessages.get(chatMessages.size() - 1);
            String lastRole = last.getRole();
            if (LagiGlobal.LLM_ROLE_ASSISTANT.equals(lastRole)
                    && last.getTool_calls() != null
                    && !last.getTool_calls().isEmpty()) {
                for (ToolCall toolCall : last.getTool_calls()) {
                    String name = SkillsAgentToolUtil.toolCallFunctionName(toolCall);
                    Map<String, String> args = SkillsAgentToolUtil.parseArguments(
                            SkillsAgentToolUtil.toolArgumentsJson(toolCall));
                    String toolResult;
                    if ("read".equals(name)) {
                        toolResult = SkillsAgentToolUtil.readFromToolArgs(fs.read(args), args, request);
                    } else if ("exec".equals(name)) {
                        toolResult = fs.exec(args, DEFAULT_SCRIPT_TIMEOUT_SECONDS);
                    } else if ("write".equals(name)) {
                        toolResult = fs.write(args);
                    } else if ("edit".equals(name)) {
                        toolResult = fs.edit(args);
                    } else {
                        toolResult = "unsupported tool: " + name;
                    }
                    chatMessages.add(ChatMessage.builder()
                            .role(LagiGlobal.LLM_ROLE_TOOL)
                            .content(toolResult)
                            .tool_call_id(toolCall.getId())
                            .build());
                }
                continue;
            }
            if (LagiGlobal.LLM_ROLE_ASSISTANT.equals(lastRole)) {
                String reply = emptyToFallback(last.getContent(), "Process completed, yet the model failed to return the final text.");
                return new SkillsAgentResult(
                        true,
                        null,
                        reply,
                        last.getReasoning_content(),
                        null,
                        buildResult(reply, lastResult));
            }

            if (llmRounds >= MAX_TOOL_LOOP_TURNS) {
                String fallback = "The maximum rounds of tool/model invocation have been reached, and the final response was not finished.";
                return new SkillsAgentResult(true , null,
                        fallback, null, null, buildResult(fallback, lastResult));
            }

            try {

                lastResult = completionsService.completions(request);
                if (lastResult == null
                        || lastResult.getChoices() == null
                        || lastResult.getChoices().isEmpty()
                        || lastResult.getChoices().get(0) == null) {
                    return new SkillsAgentResult(false, null,
                            "The model returned an empty result.", null, null, lastResult);
                }
                ChatCompletionChoice choice = lastResult.getChoices().get(0);
                if(choice.getMessage().getTool_calls() == null || choice.getMessage().getTool_calls().isEmpty()) {
                    return new SkillsAgentResult(false, null,
                            choice.getMessage().getContent(), null, null, lastResult);
                }
                chatMessages.add(choice.getMessage());
                llmRounds++;
            } catch (RRException e) {
                String err = e.getMsg() != null ? e.getMsg() : e.getMessage();
                String s = emptyToFallback(err, "Model invocation failed.");
                ChatCompletionResult result = buildResult(s, lastResult);
                return new SkillsAgentResult(false, null,s
                       , null, null, result);
            } catch (Exception e) {
                String s = emptyToFallback(e.getMessage(), "Abnormal model invocation.");
                return new SkillsAgentResult(false, null,
                        s, null, null, buildResult(s, lastResult));
            }
        }
    }

    private ChatCompletionResult buildResult(String content, ChatCompletionResult lastResult) {
        ChatCompletionResult result = new ChatCompletionResult();
        ChatCompletionChoice choice = new ChatCompletionChoice();
        ChatMessage build = ChatMessage.builder()
                .role(LagiGlobal.LLM_ROLE_ASSISTANT)
                .content(content)
                .build();
        choice.setMessage(build);
        result.setChoices(Collections.singletonList(choice));
        if(lastResult != null) {
            result.setCreated(lastResult.getCreated());
            result.setId(lastResult.getId());
            result.setObject(lastResult.getObject());
            result.setModel(lastResult.getModel());
            result.setUsage(lastResult.getUsage());
        }
        return result;
    }

    private void update2innerRequest(ChatCompletionRequest request) {
        request.setStream(false);
        request.setStream_options(null);
        List<Tool> tools = request.getTools();
        if(tools != null) {
            List<Tool> cleanTools = tools.stream().filter(tool -> {
                String n = tool.getFunction().getName();
                return "read".equals(n) || "exec".equals(n) || "write".equals(n) || "edit".equals(n);
            }).collect(Collectors.toList());
            request.setTools(cleanTools);
        }
    }

    private String emptyToFallback(String v, String fallback) {
        if (v == null || v.trim().isEmpty()) {
            return fallback;
        }
        return v;
    }
}
