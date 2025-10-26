package ai.agent.mcp;

import ai.agent.Agent;
import ai.common.pojo.McpBackend;
import ai.config.ContextLoader;
import ai.config.pojo.AgentConfig;
import ai.llm.pojo.EnhanceChatCompletionRequest;
import ai.llm.service.CompletionsService;
import ai.manager.McpManager;
import ai.mcps.CommonSseMcpClient;
import ai.mcps.SyncMcpClient;
import ai.mcps.spec.McpSchema;
import ai.openai.pojo.*;
import cn.hutool.core.bean.BeanUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class IMcpAgent extends Agent<ChatCompletionRequest, ChatCompletionResult> {


    private static final Logger log = LoggerFactory.getLogger(IMcpAgent.class);

    protected final CompletionsService completionsService;

    private List<String> mcps = new ArrayList<>();

    public IMcpAgent(String mcps) {
        String[] mcp = mcps.split(",");
        this.mcps = Arrays.stream(mcp).collect(Collectors.toList());
        this.completionsService = new CompletionsService();
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

    @Override
    public ChatCompletionResult communicate(ChatCompletionRequest data) {
        EnhanceChatCompletionRequest chatCompletionRequest = new EnhanceChatCompletionRequest();
        BeanUtil.copyProperties(data, chatCompletionRequest);
        chatCompletionRequest.setStream(false);
        List<SyncMcpClient> mcpClients = mcps.stream().map(mcp->{
                    McpBackend build = McpBackend.builder().url(mcp).build();
                    try {
                        return new CommonSseMcpClient(build);
                    } catch (Exception e) {
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        List<Tool> tools = new ArrayList<>();
        Map<String, SyncMcpClient> toolNameSyncMcpClientHashMap = new HashMap<>();
        for (SyncMcpClient mcpClient : mcpClients) {
            try {
                mcpClient.initialize();
                // TODO 2025/4/28 use cursor to get tools
                McpSchema.ListToolsResult listToolsResult = mcpClient.listTools();
                List<Tool> tools1 = convert2FunctionCallTools(listToolsResult);
                if(listToolsResult.getNextCursor() != null) {
                    while (listToolsResult.getNextCursor() != null) {
                        listToolsResult = mcpClient.listTools(listToolsResult.getNextCursor());
                        List<Tool> temp = convert2FunctionCallTools(listToolsResult);
                        tools1.addAll(temp);
                    }
                }
                for (Tool tool : tools1) {
                    toolNameSyncMcpClientHashMap.put(tool.getFunction().getName(), mcpClient);
                }
                tools.addAll(tools1);
            } catch (Exception e) {
                log.error("get mcpClient error ", e);
            }
        }
        chatCompletionRequest.setTools(tools);
        String model = ContextLoader.configuration.getAgentGeneralConfiguration().getModel();
        chatCompletionRequest.setModel(model);
        ChatCompletionResult result = completionsService.completions(chatCompletionRequest);
        ChatMessage assistantMessage = result.getChoices().get(0).getMessage();
        List<ToolCall> functionCalls = assistantMessage.getTool_calls();
        List<ChatMessage> chatMessages = chatCompletionRequest.getMessages();
        chatMessages.add(assistantMessage);
        while (functionCalls != null && !functionCalls.isEmpty()) {
            List<ToolCall> loopFunctionCalls =  new ArrayList<>(functionCalls);
            List<ToolCall> nextFunctionCalls =  new ArrayList<>();
            for (ToolCall functionCall: loopFunctionCalls) {
                SyncMcpClient syncMcpClient = toolNameSyncMcpClientHashMap.get(functionCall.getFunction().getName());
                McpSchema.CallToolRequest callToolRequest = convertFunctionToolCall2McpToolCall(functionCall);
                if(callToolRequest == null) {
                    continue;
                }
                McpSchema.CallToolResult callToolResult = syncMcpClient.callTool(callToolRequest);
                ChatMessage toolChatMessage = new ChatMessage();
                toolChatMessage.setRole("tool");
                toolChatMessage.setTool_call_id(functionCall.getId());
                toolChatMessage.setContent(callToolResult.getContent().get(0).toString());
                chatMessages.add(toolChatMessage);
                ChatCompletionResult temp = completionsService.completions(chatCompletionRequest);
                assistantMessage = temp.getChoices().get(0).getMessage();
                chatMessages.add(assistantMessage);
                if (assistantMessage.getTool_calls() != null) {
                    nextFunctionCalls.addAll(assistantMessage.getTool_calls());
                }
                if(temp.getChoices().get(0).getMessage().getContent() != null) {
                    result.getChoices().get(0).setMessage(assistantMessage);
                }
            }
            functionCalls = nextFunctionCalls;
        }

        mcpClients.forEach(mcpClient -> {
            try {
                mcpClient.close();
            } catch (Exception ignored) {
            }
        });

        return result;
    }


    public McpSchema.CallToolRequest convertFunctionToolCall2McpToolCall(ToolCall toolCall) {
        Gson gson = new Gson();
        McpSchema.CallToolRequest callToolRequest = new McpSchema.CallToolRequest();
        callToolRequest.setName(toolCall.getFunction().getName());
        Map<String, Object> o = gson.fromJson(toolCall.getFunction().getArguments(), new TypeToken<Map<String, Object>>() {
        }.getType());
        callToolRequest.setArguments(o);
        return callToolRequest;
    }

    public List<Tool> convert2FunctionCallTools(McpSchema.ListToolsResult listToolsResult) {
        List<Tool> res = new ArrayList<>();
        List<McpSchema.Tool> tools = listToolsResult.getTools();
        for (McpSchema.Tool tool : tools) {
            String name = tool.getName();
            String description = tool.getDescription();
            McpSchema.JsonSchema inputSchema = tool.getInputSchema();
            Tool functionCallTool = new Tool();
            functionCallTool.setType("function");
            Function function = new Function();
            function.setName(name);
            function.setDescription(description);
            Parameters parameters = new Parameters();
            BeanUtil.copyProperties(inputSchema, parameters);
            function.setParameters(parameters);
            functionCallTool.setFunction(function);
            res.add(functionCallTool);
        }
        return res;
    }

    @Override
    public Observable<ChatCompletionResult> stream(ChatCompletionRequest data) {
        throw new UnsupportedOperationException("streaming is not supported");
    }

    @Override
    public boolean canStream() {
        return false;
    }


}
