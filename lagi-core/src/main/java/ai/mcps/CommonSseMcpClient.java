package ai.mcps;

import ai.common.pojo.McpBackend;
import ai.mcps.client.McpClient;
import ai.mcps.client.McpSyncClient;
import ai.mcps.client.transport.HttpClientSseClientTransport;
import ai.mcps.spec.McpSchema.*;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
public class CommonSseMcpClient implements SyncMcpClient{

    private final McpSyncClient client;
    private final String name;

    public CommonSseMcpClient(McpBackend mcpBackend){
        this.name = mcpBackend.getName();
        HttpClientSseClientTransport httpClientSseClientTransport = new HttpClientSseClientTransport(mcpBackend.getUrl());
        this.client = McpClient.sync(httpClientSseClientTransport)
                .requestTimeout(Duration.ofSeconds(120))
                .capabilities(ClientCapabilities.builder()
                        .roots(true)
                        .sampling().build())
                .sampling(request -> new CreateMessageResult())
                .toolsChangeConsumer(tools -> Mono.fromRunnable(() -> {
                    log.info("Tools updated: {}", tools);
                }))
                .resourcesChangeConsumer(resources -> Mono.fromRunnable(() -> {
                    log.info("Resources updated: {}", resources);
                }))
                .promptsChangeConsumer(prompts -> Mono.fromRunnable(() -> {
                    log.info("Prompts updated: {}", prompts);
                }))
                .build();
    }

    @Override
    public String getName() {
        return name;
    }

    public InitializeResult initialize() {
        return this.client.initialize();
    }

    @Override
    public void rootsListChangedNotification() {
        this.client.rootsListChangedNotification();
    }

    @Override
    public void addRoot(Root root) {
        this.client.addRoot(root);
    }

    @Override
    public void removeRoot(String rootUri) {
        this.client.removeRoot(rootUri);
    }

    @Override
    public Object ping() {
        return this.client.ping();
    }

    @Override
    public CallToolResult callTool(CallToolRequest callToolRequest) {
        return this.client.callTool(callToolRequest);
    }

    @Override
    public ListToolsResult listTools() {
        return this.client.listTools();
    }

    @Override
    public ListToolsResult listTools(String cursor) {
        return this.client.listTools(cursor);
    }

    @Override
    public ListResourcesResult listResources(String cursor) {
        return this.client.listResources(cursor);
    }

    @Override
    public ListResourcesResult listResources() {
        return this.client.listResources();
    }

    @Override
    public ReadResourceResult readResource(Resource resource) {
        return this.client.readResource(resource);
    }

    @Override
    public ReadResourceResult readResource(ReadResourceRequest readResourceRequest) {
        return this.client.readResource(readResourceRequest);
    }

    @Override
    public ListResourceTemplatesResult listResourceTemplates(String cursor) {
        return this.client.listResourceTemplates(cursor);
    }

    @Override
    public ListResourceTemplatesResult listResourceTemplates() {
        return this.client.listResourceTemplates();
    }

    @Override
    public void subscribeResource(SubscribeRequest subscribeRequest) {
        this.client.subscribeResource(subscribeRequest);
    }

    @Override
    public void unsubscribeResource(UnsubscribeRequest unsubscribeRequest) {
        this.client.unsubscribeResource(unsubscribeRequest);
    }

    @Override
    public ListPromptsResult listPrompts(String cursor) {
        return this.client.listPrompts(cursor);
    }

    @Override
    public ListPromptsResult listPrompts() {
        return this.client.listPrompts();
    }

    @Override
    public GetPromptResult getPrompt(GetPromptRequest getPromptRequest) {
        return this.client.getPrompt(getPromptRequest);
    }

    @Override
    public void setLoggingLevel(LoggingLevel loggingLevel) {
        this.client.setLoggingLevel(loggingLevel);
    }


    @Override
    public void close() throws Exception {
        if(this.client != null) {
            this.client.close();
        }
    }


    public static void main(String[] args) throws Exception {
//        McpBackend build = McpBackend.builder().url("https://mcp.amap.com/sse?key=").build();
//        McpBackend build = McpBackend.builder().url("http://appbuilder.baidu.com/v2/ai_search/mcp/sse?api_key=").build();
        McpBackend build = McpBackend.builder().url("http://localhost:8082/mcp/sse").build();
        try (CommonSseMcpClient commonSseMcpClient = new CommonSseMcpClient(build)){
            commonSseMcpClient.initialize();
            commonSseMcpClient.setLoggingLevel(LoggingLevel.DEBUG);
            ListToolsResult listToolsResult = commonSseMcpClient.listTools();
            for (Tool tool : listToolsResult.getTools()) {
//                if ("maps_weather".equals(tool.getName())) {
//                    CallToolRequest callToolRequest = new CallToolRequest();
//                    callToolRequest.setName(tool.getName());
//                    Map<String, Object> params = new HashMap<>();
//                    params.put("city", "武汉");
//                    callToolRequest.setArguments(params);
//                    CallToolResult callToolResult = commonSseMcpClient.callTool(callToolRequest);
//                    System.out.println(callToolResult);
//                }
                System.out.println(tool);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
