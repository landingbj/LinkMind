package ai.llm.hook.impl;

import ai.annotation.Component;
import ai.annotation.ConditionalOnProperty;
import ai.annotation.Order;
import ai.common.utils.LRUCache;
import ai.config.ContextLoader;
import ai.config.pojo.SkillsConfig;
import ai.llm.hook.AfterModel;
import ai.llm.hook.BeforeModel;
import ai.llm.pojo.ModelContext;
import ai.openai.pojo.*;
import ai.pnps.skills.SkillLoader;
import ai.pnps.skills.SkillsAgent;
import ai.pnps.skills.util.OpenClawSkillUtil;
import ai.pnps.skills.pojo.SkillEntry;
import ai.pnps.skills.pojo.SkillsAgentResult;
import ai.pnps.skills.util.SkillUtil;
import ai.pnps.skills.util.SkillsAgentToolUtil;
import ai.pnps.skills.util.SkillsJsons;
import ai.utils.ExtraBodyUtil;
import ai.utils.LagiGlobal;
import ai.utils.qa.ChatCompletionUtil;
import cn.hutool.core.bean.BeanUtil;
import io.reactivex.Observable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Order(2)
@Component
@ConditionalOnProperty(name = "skills.enable", havingValue = "true")
public class OpenClawSkillInject implements BeforeModel, AfterModel {

    private static final int CACHE_SIZE = 100;
    private static final long TTL_HOURS = 1;

    private static final LRUCache<List<ChatMessage>, List<ChatMessage>> toolCallResultCache =
            new LRUCache<>(CACHE_SIZE, TTL_HOURS, TimeUnit.HOURS);

    private final List<SkillEntry> serverSkills;


    public OpenClawSkillInject() {
        SkillLoader skillLoader = new SkillLoader();
        List<SkillEntry> skills;
        if (ContextLoader.configuration != null) {
            SkillsConfig skillsConfig = ContextLoader.configuration.getSkills();
            List<String> configuredRoots = skillsConfig == null ? null : skillsConfig.getRoots();
            if (configuredRoots == null || configuredRoots.isEmpty()) {
                skills = Collections.emptyList();
            } else {
                skills = skillLoader.load(configuredRoots);
            }
            List<SkillEntry> configSkills = skillsConfig == null || skillsConfig.getSkills() == null
                    ? Collections.emptyList()
                    : skillsConfig.getSkills();
            configSkills.forEach(skill -> {
                if(skill.getRule() == null) {
                    if (skillsConfig != null && skillsConfig.getRule() != null) {
                        skill.setRule(skillsConfig.getRule());
                    } else {
                        skill.setRule("cli");
                    }
                }
            });
            this.serverSkills = retainSkill(configSkills, skills);
        } else {
            this.serverSkills = Collections.emptyList();
        }
    }

    @Override
    public ChatCompletionRequest beforeModel(ModelContext context) {
        ChatCompletionRequest request = context.getRequest();
        // only support COMPLETION
//        ((ModelService) context.getAdapter()).setProtocol(ResponseProtocolConstants.COMPLETION);
        context.setOriginalMessages(request.getMessages());
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return request;
        }
        //  Tool result injection
        List<ChatMessage> key = ChatCompletionUtil.getHistoryMessages(request.getMessages());
        List<ChatMessage> messages = new ArrayList<>(request.getMessages());
        if(toolCallResultCache.get(key) != null) {
            messages.addAll(toolCallResultCache.get(key));
            request.setMessages(messages);
        }
        List<ChatMessage> systemPrompt = ChatCompletionUtil.getSystemMessages(request.getMessages());
        if (systemPrompt.isEmpty()) {
            return request;
        }
        // no server skill
        if(serverSkills.isEmpty()) {
            return request;
        }
        // server skills injection
        ChatMessage systemMessage = systemPrompt.get(0);
        String content = systemMessage.getContent();
        List<SkillEntry> openClawSkills = OpenClawSkillUtil.skillExtractor(content);
        List<SkillEntry> finalSkills = mergeSkills(openClawSkills, serverSkills);
        context.setSkills(finalSkills);
        String systemPromptContent = OpenClawSkillUtil.replaceAvailableSkill(content, finalSkills);
        ExtraBody extraBody = ExtraBodyUtil.extractExtraBody(content);
        if (extraBody == null) {
            String extraBodyStr = ExtraBodyUtil.toExtraBodyString(request);
            systemPromptContent = systemPromptContent + extraBodyStr;
        }
        systemMessage.setContent(systemPromptContent);
        return context.getRequest();
    }


    private List<SkillEntry> retainSkill(List<SkillEntry> configSkills, List<SkillEntry> skills) {
        List<SkillEntry> retainSkills = new ArrayList<>();
        for (SkillEntry configSkill : configSkills) {
            for (SkillEntry skill : skills) {
                if (Objects.equals(skill.getName(), configSkill.getName())) {
                    skill.setRule(configSkill.getRule());
                    skill.setName(SkillsAgentToolUtil.getLandingbjSkillName(skill.getName()));
                    retainSkills.add(skill);
                    break;
                }
            }
        }
        return retainSkills;
    }


    private List<SkillEntry> mergeSkills(List<SkillEntry> openClawSkills, List<SkillEntry> configSkills) {
        if (openClawSkills == null) {
            openClawSkills = Collections.emptyList();
        }
        if (configSkills == null) {
            configSkills = Collections.emptyList();
        }
        Map<String, SkillEntry> configByName = new LinkedHashMap<>();
        for (SkillEntry c : configSkills) {
            if (c != null && c.getName() != null && !c.getName().trim().isEmpty()) {
                configByName.put(c.getName(), c);
            }
        }
        Set<String> openClawNames = new HashSet<>();
        for (SkillEntry o : openClawSkills) {
            if (o != null && o.getName() != null && !o.getName().trim().isEmpty()) {
                openClawNames.add(o.getName());
            }
        }
        List<SkillEntry> merged = new ArrayList<>();
        for (SkillEntry oc : openClawSkills) {
            if (oc == null || oc.getName() == null || oc.getName().trim().isEmpty()) {
                continue;
            }
            SkillEntry cfg = configByName.get(oc.getName());
            // only openClaw skill
            if (cfg == null) {
                merged.add(oc);
                oc.setRule("cli");
                continue;
            }
            // server skill
            String rule = normalizeMergeRule(cfg.getRule());
            if ("server".equals(rule)) {
                oc.setRule("server");
                merged.add(oc);
            } else if ("cli".equals(rule)) {
                oc.setRule("cli");
                merged.add(oc);
            }
        }
        for (SkillEntry cfg : configSkills) {
            if (cfg == null || cfg.getName() == null || cfg.getName().trim().isEmpty()) {
                continue;
            }
            if (!openClawNames.contains(cfg.getName())) {
                SkillEntry skillEntry = new SkillEntry();
                BeanUtil.copyProperties(cfg, skillEntry);
                skillEntry.setRule("server");
                merged.add(skillEntry);
            }
        }
        return merged;
    }

    private static String normalizeMergeRule(String rule) {
        if (rule == null || rule.trim().isEmpty()) {
            return "server";
        }
        return rule.trim().toLowerCase(Locale.ROOT);
    }


    @Override
    public ChatCompletionResult apply(ModelContext context) {
        ChatCompletionResult result = context.getResult();
        if (result == null || result.getChoices() == null || result.getChoices().isEmpty()) {
            return result;
        }
        ChatCompletionChoice choice = result.getChoices().get(0);
        ChatMessage assistantPayload = choice.getMessage() != null ? choice.getMessage() : choice.getDelta();
        List<ToolCall> toolCalls = assistantPayload == null ? null : assistantPayload.getTool_calls();
        if (toolCalls == null || toolCalls.isEmpty()) {
            return result;
        }
        List<SkillEntry> skills1 = context.getSkills();
        if (skills1 == null || skills1.isEmpty()) {
            return result;
        }
        List<ToolClassification> serverTool = new ArrayList<>();
        List<ToolClassification> cliTool = new ArrayList<>();
        for (ToolCall toolCall : toolCalls) {
            if (toolCall == null || toolCall.getFunction() == null || toolCall.getFunction().getName() == null) {
                continue;
            }
            String name = toolCall.getFunction().getName();
            try {
                if (SkillUtil.isReadTool(name)) {
                    classifyReadSkillTools(toolCall, skills1, serverTool, cliTool);
                } else if ("exec".equals(name)) {
                    classifyTools(toolCall, "workdir", skills1, serverTool, cliTool);
                } else {
                    cliTool.add(new ToolClassification(toolCall, "cli", null));
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to classify tool call in non-stream mode", e);
            }
        }
        if (!cliTool.isEmpty() && !serverTool.isEmpty()) {
            runAndCachedToolResult(context, serverTool);
            List<ToolCall> cliToolCalls = cliTool.stream()
                    .map(ToolClassification::getToolCall)
                    .collect(Collectors.toList());
            if (choice.getDelta() != null) {
                choice.getDelta().setTool_calls(cliToolCalls);
            }
            if (choice.getMessage() != null) {
                choice.getMessage().setTool_calls(cliToolCalls);
            }
            return result;
        }
        if (!serverTool.isEmpty()) {
            ChatCompletionRequest request = context.getRequest();
            List<ChatMessage> chatMessages = request.getMessages();
            if (chatMessages == null) {
                chatMessages = new ArrayList<>();
            }
            ChatMessage assistantMessage = choice.getMessage() != null ? choice.getMessage() : choice.getDelta();
            if (assistantMessage == null) {
                assistantMessage = new ChatMessage();
                assistantMessage.setRole(LagiGlobal.LLM_ROLE_ASSISTANT);
                assistantMessage.setTool_calls(toolCalls);
            }
            chatMessages.add(assistantMessage);
            request.setMessages(chatMessages);
            SkillsAgent skillsAgent = new SkillsAgent();
            SkillsAgentResult run = skillsAgent.run(request);
            return run.getOriginalResult();
        }
        return result;
    }

    @Override
    public Observable<ChatCompletionResult> stream(ModelContext context) {
        Observable<ChatCompletionResult> source = context.getStreamResult();
        return Observable.create(emitter -> {
            final boolean[] intercepted = {false};
            final List<ToolCall> mergedToolCalls = new ArrayList<>();
            final ToolCall[] currentToolCall = {null};
            final ChatCompletionResult[] lastToolChunk = {null};
            final io.reactivex.disposables.Disposable disposable = source.subscribe(
                    chunk -> {
                        if (intercepted[0]) {
                            return;
                        }
                        try {
                            if (chunk == null) {
                                return;
                            }
                            if (chunk.getChoices() == null || chunk.getChoices().isEmpty()) {
                                if (chunk.getUsage() != null) {
                                    emitter.onNext(chunk);
                                }
                                return;
                            }
                            List<ToolCall> toolCalls = getDeltaToolCalls(chunk);
                            if (toolCalls == null || toolCalls.isEmpty()) {
                                emitter.onNext(chunk);
                                return;
                            }

                            lastToolChunk[0] = chunk;
                            for (ToolCall partial : toolCalls) {
                                mergeToolCallPartial(currentToolCall, mergedToolCalls, partial);
                            }
                            emitter.onNext(emptyResult(chunk));
                        } catch (Exception e) {
                            emitter.onError(e);
                        }
                    },
                    emitter::onError,
                    () -> {
                        if (intercepted[0]) {
                            emitter.onComplete();
                            return;
                        }
                        try {
                            if (!mergedToolCalls.isEmpty() || currentToolCall[0] != null) {
                                handleMergedToolCallsOnComplete(
                                        context,
                                        emitter,
                                        intercepted,
                                        currentToolCall,
                                        mergedToolCalls,
                                        lastToolChunk[0]
                                );
                            }
                            if (!intercepted[0]) {
                                emitter.onComplete();
                            }
                        } catch (Exception e) {
                            emitter.onError(e);
                        }
                    }
            );
            emitter.setCancellable(disposable::dispose);
        });
    }

    private static List<ToolCall> getDeltaToolCalls(ChatCompletionResult chunk) {
        if (chunk == null || chunk.getChoices() == null || chunk.getChoices().isEmpty()) {
            return null;
        }
        ChatMessage delta = chunk.getChoices().get(0).getDelta();
        return delta == null ? null : delta.getTool_calls();
    }

    private void handleMergedToolCallsOnComplete(ModelContext context,
                                                 io.reactivex.ObservableEmitter<ChatCompletionResult> emitter,
                                                 boolean[] intercepted,
                                                 ToolCall[] currentToolCall,
                                                 List<ToolCall> mergedToolCalls,
                                                 ChatCompletionResult lastChunk) throws Exception {
        flushCurrentToolCall(currentToolCall, mergedToolCalls);
        if (mergedToolCalls.isEmpty()) {
            if (lastChunk != null) {
                emitter.onNext(lastChunk);
            }
            emitter.onComplete();
            return;
        }
        List<SkillEntry> skills1 = context.getSkills();
        if (skills1 == null || skills1.isEmpty()) {
            if (lastChunk != null) {
                ChatCompletionResult result = withToolCallsOnChunk(lastChunk, mergedToolCalls);
                emitter.onNext(result);
            }
            emitter.onComplete();
            return;
        }
        List<ToolClassification> serverTool = new ArrayList<>();
        List<ToolClassification> cliTool = new ArrayList<>();
        for (ToolCall toolCall : mergedToolCalls) {
            if (toolCall == null || toolCall.getFunction() == null || toolCall.getFunction().getName() == null) {
                continue;
            }
            String name = toolCall.getFunction().getName();
            if (SkillUtil.isReadTool(name)) {
                classifyReadSkillTools(toolCall , skills1, serverTool, cliTool);
            } else if ("exec".equals(name)) {
                classifyTools(toolCall , "workdir", skills1, serverTool, cliTool);
            } else {
                cliTool.add(new ToolClassification(toolCall, "cli", null));
            }
        }
        if (!cliTool.isEmpty() && !serverTool.isEmpty()) {
            runAndCachedToolResult(context, serverTool);
            if (lastChunk != null) {
                ChatCompletionResult out = withToolCallsOnChunk(lastChunk,
                        cliTool.stream().map(ToolClassification::getToolCall).collect(Collectors.toList())
                );
                emitter.onNext(out);
            }
        } else if (!serverTool.isEmpty()) {
            ChatCompletionRequest request = context.getRequest();
            List<ChatMessage> chatMessages = request.getMessages();
            if (chatMessages == null) {
                chatMessages = new ArrayList<>();
            }
            ChatMessage assistantMessage = buildAssistantToolMessage(lastChunk, mergedToolCalls);
            chatMessages.add(assistantMessage);
            request.setMessages(chatMessages);
            SkillsAgent skillsAgent = new SkillsAgent();
            Future<SkillsAgentResult> skillsAgentResultFuture = skillsAgent.runAsync(request);
            while (!skillsAgentResultFuture.isDone()) {
                try {
                    Thread.sleep(300);
                    emitter.onNext(emptyResult(lastChunk));
                } catch (InterruptedException ignored) {
                }
            }
            SkillsAgentResult run = skillsAgentResultFuture.get();
            ChatCompletionResult originalResult = run.getOriginalResult();
            if (originalResult != null) {
                intercepted[0] = true;
                ChatCompletionChoice firstChoice = originalResult.getChoices().get(0);
                firstChoice.setDelta(firstChoice.getMessage());
                firstChoice.setMessage(null);
                emitter.onNext(originalResult);
            } else if (lastChunk != null) {
                emitter.onNext(lastChunk);
            }
        } else {
            if (lastChunk != null) {
                emitter.onNext(withToolCallsOnChunk(lastChunk, mergedToolCalls));
            }
        }
        emitter.onComplete();
    }

    private void runAndCachedToolResult(ModelContext context, List<ToolClassification> serverTool) {
        SkillsAgent skillsAgent = new SkillsAgent();
        List<ToolCall> toolCalls = serverTool.stream().map(ToolClassification::getToolCall).collect(Collectors.toList());
        List<ChatMessage> tools = skillsAgent.runTools(toolCalls, context.getRequest());
        if (!tools.isEmpty()) {
            toolCallResultCache.put(context.getRequest().getMessages(), tools);
        }
    }

    private static ChatCompletionResult emptyResult(ChatCompletionResult src) {
        ChatCompletionResult result = new ChatCompletionResult();
        if(src != null) {
            result.setId(src.getId());
            result.setObject(src.getObject());
            result.setCreated(src.getCreated());
            result.setModel(src.getModel());
            result.setUsage(src.getUsage());
        }
        ChatCompletionChoice choice = new ChatCompletionChoice();
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setRole(LagiGlobal.LLM_ROLE_ASSISTANT);
        chatMessage.setContent("");
        choice.setDelta(chatMessage);
        result.setChoices(Collections.singletonList(choice));
        return result;
    }

    private static void mergeToolCallPartial(ToolCall[] currentToolCall, List<ToolCall> mergedToolCalls, ToolCall partial) {
        if (partial == null) {
            return;
        }
        boolean startsNewCall = partial.getId() != null && !partial.getId().isEmpty();
        if (startsNewCall) {
            flushCurrentToolCall(currentToolCall, mergedToolCalls);
            ToolCall next = new ToolCall();
            next.setId(partial.getId());
            next.setType(partial.getType());
            ToolCallFunction f = new ToolCallFunction();
            if (partial.getFunction() != null) {
                f.setName(partial.getFunction().getName());
                f.setArguments(defaultString(partial.getFunction().getArguments()));
            } else {
                f.setArguments("");
            }
            next.setFunction(f);
            currentToolCall[0] = next;
            return;
        }
        if (currentToolCall[0] == null) {
            return;
        }
        if (partial.getFunction() != null) {
            if (currentToolCall[0].getFunction() == null) {
                currentToolCall[0].setFunction(new ToolCallFunction());
            }
            if (partial.getFunction().getName() != null && !partial.getFunction().getName().isEmpty()) {
                currentToolCall[0].getFunction().setName(partial.getFunction().getName());
            }
            String existing = defaultString(currentToolCall[0].getFunction().getArguments());
            String append = defaultString(partial.getFunction().getArguments());
            currentToolCall[0].getFunction().setArguments(existing + append);
        }
    }

    private static void flushCurrentToolCall(ToolCall[] currentToolCall, List<ToolCall> mergedToolCalls) {
        if (currentToolCall[0] == null) {
            return;
        }
        if (currentToolCall[0].getFunction() != null
                && currentToolCall[0].getFunction().getArguments() == null) {
            currentToolCall[0].getFunction().setArguments("");
        }
        mergedToolCalls.add(currentToolCall[0]);
        currentToolCall[0] = null;
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }

    private static ChatCompletionResult withToolCallsOnChunk(ChatCompletionResult chunk, List<ToolCall> toolCalls) {
        ChatCompletionResult result = new ChatCompletionResult();
        result.setId(chunk.getId());
        result.setObject(chunk.getObject());
        result.setCreated(chunk.getCreated());
        result.setModel(chunk.getModel());
        result.setUsage(chunk.getUsage());
        ChatCompletionChoice choice = new ChatCompletionChoice();
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setTool_calls(toolCalls);
        choice.setDelta(chatMessage);
        result.setChoices(Collections.singletonList(choice));
        return result;
    }

    private static ChatMessage buildAssistantToolMessage(ChatCompletionResult chunk, List<ToolCall> mergedToolCalls) {
        ChatMessage assistantMessage = null;
        if (chunk != null && chunk.getChoices() != null && !chunk.getChoices().isEmpty()) {
            assistantMessage = chunk.getChoices().get(0).getMessage();
            if (assistantMessage == null) {
                assistantMessage = chunk.getChoices().get(0).getDelta();
            }
        }
        if (assistantMessage == null) {
            assistantMessage = new ChatMessage();
        }
        if (assistantMessage.getRole() == null || assistantMessage.getRole().isEmpty()) {
            assistantMessage.setRole(LagiGlobal.LLM_ROLE_ASSISTANT);
        }
        assistantMessage.setTool_calls(mergedToolCalls);
        return assistantMessage;
    }

    private void classifyReadSkillTools(ToolCall toolCall, List<SkillEntry> skills, List<ToolClassification> serverTool, List<ToolClassification> cliTool) throws IOException {
        String path = SkillsJsons.getArg(toolCall.getFunction().getArguments(), "path");
        String name = SkillsJsons.getArg(toolCall.getFunction().getArguments(), "name");
        if (path != null && !path.trim().isEmpty()) {
            classifyTools(toolCall, "path", skills, serverTool, cliTool);
        } else if (name != null && !name.trim().isEmpty()) {
            classifyToolsByName(toolCall, name, skills, serverTool, cliTool);
        } else {
            cliTool.add(new ToolClassification(toolCall, "cli", null));
        }
    }

    private void classifyToolsByName(ToolCall toolCall, String name, List<SkillEntry> skills, List<ToolClassification> serverTool, List<ToolClassification> cliTool) throws IOException {
        if (SkillsAgentToolUtil.isLandingbjSkillName(name)) {
            for (SkillEntry skillEntry : skills) {
                if (skillEntry.getName().equals(name)) {
                    serverTool.add(new ToolClassification(toolCall, "server", skillEntry));
                    return;
                }
            }
        } else {
            cliTool.add(new ToolClassification(toolCall, "cli", null));
        }
    }

    private void classifyTools(ToolCall toolCall, String argument, List<SkillEntry> skills1, List<ToolClassification> serverTool, List<ToolClassification> cliTool) throws IOException {
        String path = SkillsJsons.getArg(toolCall.getFunction().getArguments(), argument);
        if (path == null || path.trim().isEmpty()) {
            cliTool.add(new ToolClassification(toolCall, "cli", null));
            return;
        }
        String toolPath = Paths.get(path).toAbsolutePath().normalize().toString();
        for (SkillEntry skillEntry : skills1) {
            Path skillMdPath = Objects.equals(argument, "path") ?  skillEntry.getSkillMdPath() : skillEntry.getSkillDir();
            if(skillMdPath == null) {
                continue;
            }
            String skillPath = skillMdPath.toAbsolutePath().normalize().toString();
            boolean sameFile = skillPath.equals(toolPath);
            if(sameFile) {
                if("server".equals(skillEntry.getRule())) {
                    serverTool.add(new ToolClassification(toolCall, "server", skillEntry));
                    return;
                }
                else {
                    cliTool.add(new ToolClassification(toolCall, "cli", skillEntry));
                    return;
                }
            }
        }
        cliTool.add(new ToolClassification(toolCall, "cli", null));
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    static class ToolClassification{
        private ToolCall toolCall;
        private String classification;
        private SkillEntry skill;
    }



}
