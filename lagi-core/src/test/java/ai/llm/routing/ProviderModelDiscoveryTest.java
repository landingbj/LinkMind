package ai.llm.routing;

import ai.common.ModelService;
import ai.common.pojo.Backend;
import ai.config.ConfigUtil;
import ai.config.pojo.ModelFunctions;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.adapter.impl.*;
import ai.manager.Html2ContentManager;
import ai.manager.LlmManager;
import ai.manager.MultimodalAIManager;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static ai.llm.routing.BringYourOwnKeyRouter.*;
import static org.junit.jupiter.api.Assertions.*;

class ProviderModelDiscoveryTest {
    private HttpServer server;
    private String base;
    private final List<String> requests = Collections.synchronizedList(new ArrayList<>());
    private final YAMLMapper yaml = new YAMLMapper();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::respond);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
        LlmManager.getInstance().clear();
        Html2ContentManager.getInstance().clear();
        ConfigUtil.APP_CONTAINER_URL = null;
    }

    private void respond(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        requests.add(path + " " + exchange.getRequestHeaders().getFirst("Authorization"));
        String body = "{\"object\":\"list\",\"data\":[{\"id\":\"shared\"},{\"id\":\"new\"},{\"id\":\"org/native\"}]}";
        int status = 200;
        if (path.startsWith("/dead/")) {
            status = 503;
        } else if (path.startsWith("/box/")) {
            body = "{\"data\":[{\"id\":\"A/shared\"},{\"id\":\"B/cloud-only\"}]}";
        } else if (path.startsWith("/claude/")) {
            assertEquals("configured-claude", exchange.getRequestHeaders().getFirst("x-api-key"));
            assertEquals("2023-06-01", exchange.getRequestHeaders().getFirst("anthropic-version"));
            body = exchange.getRequestURI().getQuery() == null
                    ? "{\"data\":[{\"id\":\"first\"}],\"has_more\":true,\"last_id\":\"first\"}"
                    : "{\"data\":[{\"id\":\"second\"}],\"has_more\":false}";
            if (exchange.getRequestURI().getQuery() != null) assertEquals("after_id=first", exchange.getRequestURI().getQuery());
        } else if (path.startsWith("/native/")) {
            assertEquals("configured-native", exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            body = exchange.getRequestURI().getQuery() == null
                    ? "{\"models\":[{\"name\":\"models/one\"}],\"nextPageToken\":\"two\"}"
                    : "{\"models\":[{\"name\":\"models/two\"}]}";
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private Backend provider(String name, String type, String models, Class<?> driver) {
        Backend backend = new Backend();
        backend.setName(name);
        backend.setType(type);
        backend.setEnable(true);
        backend.setApiKey("configured-" + name);
        backend.setApiAddress(base + "/" + name + "/v1/chat/completions");
        backend.setDriver(driver.getName());
        backend.setModel(models);
        return backend;
    }

    private void load(List<Backend> providers, String... rules) throws IOException {
        StringBuilder config = new StringBuilder("chat:\n  backends:\n");
        for (String rule : rules) config.append("    - ").append(rule).append("\n");
        MultimodalAIManager.register(providers, yaml.readValue(config.toString(), ModelFunctions.class));
    }

    @Test
    void mergesInPlatformOrderAndUsesTheSameAllowlistAndRawIdsForRouting() throws IOException {
        Backend a = provider("a", "A", "manual,shared", QwenCompatibleAdapter.class);
        Backend b = provider("b", "B", null, QwenCompatibleAdapter.class);
        load(Arrays.asList(a, b), "{backend: b, enable: true, model: obsolete, models: [shared, B/org/native]}",
                "{backend: a, enable: true}");
        assertEquals(Arrays.asList("A/manual", "A/shared", "A/new", "A/org/native", "B/shared", "B/org/native"),
                new ArrayList<>(configuredLocalKeyModels(null)));
        Map<String, ILlmAdapter> routes = routes(Scope.LOCAL, null);
        assertEquals("a", ((ModelService) find(routes, "shared")).getBackend());
        assertEquals("b", ((ModelService) find(routes, "B/shared")).getBackend());
        assertEquals("org/native", ((ModelService) find(routes, "B/org/native")).getModel());
        assertEquals("a", ((ModelService) find(routes, "org/native")).getBackend());
        assertNull(find(routes, "B/new"));
        assertNull(find(routes, "C/shared"));
        assertEquals(Arrays.asList("/a/v1/models Bearer configured-a", "/b/v1/models Bearer configured-b"), requests);
        load(Arrays.asList(a, b), "{backend: a, enable: true, model: manual}", "{backend: b, enable: true, models: []}");
        assertEquals(Collections.singleton("A/manual"), configuredLocalKeyModels(null));
        assertEquals(2, requests.size());
    }

    @Test
    void failureFallsBackPerPlatformAndMissingCredentialsDoNotProbe() throws IOException {
        Backend dead = provider("dead", "Old", "manual", OpenAIStandardAdapter.class);
        Backend noKey = provider("empty", "NoKey", "existing", QwenCompatibleAdapter.class);
        noKey.setApiKey("your-api-key");
        Backend good = provider("good", "Good", null, OpenAIStandardAdapter.class);
        good.setApiKey(null);
        good.setApiKeys(Arrays.asList("your-api-key", "pool-key"));
        load(Arrays.asList(dead, noKey, good), "{backend: dead, enable: true}", "{backend: empty, enable: true}",
                "{backend: good, enable: true, models: [new]}");
        assertEquals(Arrays.asList("Old/manual", "Good/new"), new ArrayList<>(configuredLocalKeyModels(null)));
        assertEquals(Arrays.asList("/dead/v1/models Bearer configured-dead", "/good/v1/models Bearer pool-key"), requests);
    }

    @Test
    void remoteLandingKeepsIdsAndLocalKeysPreferDirectOnlyForTheSamePublicId() throws IOException {
        Backend box = provider("box", "Landing", null, LandingAdapter.class);
        Backend direct = provider("a", "A", null, OpenAIStandardAdapter.class);
        load(Arrays.asList(box, direct), "{backend: a, enable: true}", "{backend: box, enable: true}");
        Map<String, ILlmAdapter> local = routes(Scope.LOCAL, "box");
        assertEquals(Arrays.asList("A/shared", "B/cloud-only", "A/new", "A/org/native"), new ArrayList<>(local.keySet()));
        assertFalse(isLanding(find(local, "A/shared")));
        assertTrue(isLanding(find(local, "cloud-only")));
        assertTrue(isLanding(find(routes(Scope.CLOUD, "box"), "A/shared")));
    }

    @Test
    void terminalPublishesTheIntersectionWithoutContactingItself() throws IOException {
        ConfigUtil.APP_CONTAINER_URL = base + "/terminal";
        Backend terminal = provider("terminal", "Landing", "Other/shared", LandingAdapter.class);
        Backend direct = provider("a", "A", "manual", OpenAIStandardAdapter.class);
        load(Arrays.asList(terminal, direct), "{backend: terminal, enable: true, models: [A/shared, A/new, Other/shared]}",
                "{backend: a, enable: true, models: [shared, manual]}");
        assertEquals(Collections.singleton("A/shared"), configuredCloudModels("terminal"));
        assertTrue(isLanding(find(routes(Scope.CLOUD, "terminal"), "shared")));
        assertNotNull(upstream("A/shared"));
        assertNull(upstream("Other/shared"));
        assertEquals(Collections.singletonList("/a/v1/models Bearer configured-a"), requests);
    }

    @Test
    void wrapperWithoutStaticModelsUsesItsChatDriverAndItsFunctionCredential() throws IOException {
        Backend wrapper = provider("box", "Landing", null, ai.wrapper.impl.LandingAdapter.class);
        wrapper.setApiKey(null);
        load(Collections.singletonList(wrapper), "{backend: box, enable: true, apiKey: rule-key}");
        assertEquals(Arrays.asList("A/shared", "B/cloud-only"), new ArrayList<>(configuredCloudModels("box")));
        assertEquals(Collections.singletonList("/box/v1/models Bearer rule-key"), requests);
    }

    @Test
    void hotReloadOnlyReadsTheStartupSnapshotEvenForANewEndpoint() throws IOException {
        Backend a = provider("a", "A", "manual", OpenAIStandardAdapter.class);
        Backend b = provider("b", "B", "static", OpenAIStandardAdapter.class);
        load(Collections.singletonList(a), "{backend: a, enable: true}");
        ModelFunctions rules = yaml.readValue("chat:\n  backends:\n    - {backend: a, enable: true}\n    - {backend: b, enable: true}", ModelFunctions.class);
        MultimodalAIManager.register(Arrays.asList(a, b), rules, false);
        assertTrue(configuredLocalKeyModels(null).containsAll(Arrays.asList("A/new", "A/manual", "B/static")));
        assertFalse(configuredLocalKeyModels(null).contains("B/new"));
        assertEquals(Collections.singletonList("/a/v1/models Bearer configured-a"), requests);
    }

    @Test
    void aPlaceholderInThePlatformTemplateNeverTriggersDiscovery() throws IOException {
        Backend provider = provider("a", "A", "manual", QwenCompatibleAdapter.class);
        provider.setApiKey("yourAppId:yourSecret");
        load(Collections.singletonList(provider), "{backend: a, enable: true}");
        assertTrue(configuredLocalKeyModels(null).isEmpty());
        assertTrue(requests.isEmpty());
    }

    @Test
    void boxKeepsStaticModelsForAdaptersWithSigningCredentials() throws IOException {
        Backend signed = provider("sense", "SenseTime", "sense-chat", SenseChatAdapter.class);
        signed.setApiKey(null);
        signed.setAccessKeyId("test-access-id");
        signed.setAccessKeySecret("test-access-secret");
        load(Collections.singletonList(signed), "{backend: sense, enable: true}");

        assertEquals(Collections.singleton("SenseTime/sense-chat"), configuredLocalKeyModels(null));
        assertNotNull(upstream("SenseTime/sense-chat"));
        assertTrue(requests.isEmpty());
    }

    @Test
    void anthropicUsesItsNativeKeyAndCursor() throws IOException {
        Backend claude = provider("claude", "Anthropic", null, ClaudeAdapter.class);
        load(Collections.singletonList(claude), "{backend: claude, enable: true}");
        assertEquals(Arrays.asList("Anthropic/first", "Anthropic/second"), new ArrayList<>(configuredLocalKeyModels(null)));
        assertEquals(2, requests.size());
    }

    @Test
    void azurePreservesAnExplicitDeploymentAndRoutesNewIdsIndependently() throws IOException {
        Backend azure = provider("azure", "Microsoft", "manual", GPTAzureAdapter.class);
        azure.setEndpoint(base + "/azure/");
        azure.setApiVersion("2024-10-21");
        azure.setDeployment("my-existing-deployment");
        load(Collections.singletonList(azure), "{backend: azure, enable: true}");
        Map<String, ILlmAdapter> routes = routes(Scope.LOCAL, null);
        assertEquals("my-existing-deployment", ((ModelService) find(routes, "Microsoft/manual")).getDeployment());
        assertEquals("new", ((ModelService) find(routes, "Microsoft/new")).getDeployment());
        assertEquals(Collections.singletonList("/azure/openai/v1/models null"), requests);
    }

    @Test
    void followsNativePaginationAndRemovesOnlyTheGeminiResourcePrefix() throws IOException {
        Backend nativeConfig = provider("native", "Google", null, GeminiAdapter.class);
        load(Collections.singletonList(nativeConfig), "{backend: native, enable: true}");
        assertEquals(Arrays.asList("Google/one", "Google/two"), new ArrayList<>(configuredLocalKeyModels(null)));
        assertEquals(2, requests.size());
    }
}
