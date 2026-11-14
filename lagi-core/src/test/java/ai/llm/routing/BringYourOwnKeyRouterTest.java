package ai.llm.routing;

import ai.llm.adapter.impl.DeepSeekAdapter;
import ai.llm.adapter.impl.ErnieAdapter;
import ai.llm.adapter.impl.LandingAdapter;
import ai.llm.adapter.impl.ProxyLlmAdapter;
import ai.llm.adapter.impl.QwenCompatibleAdapter;
import ai.llm.adapter.impl.ZhipuAdapter;
import ai.manager.LlmManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BringYourOwnKeyRouterTest {

    @Test
    void qwenAllowsItsExplicitWorkspaceKey() {
        BringYourOwnKeyRouter.Decision decision = BringYourOwnKeyRouter.resolve(
                "qwen-plus", "sk-ws-example", new QwenCompatibleAdapter());

        assertTrue(decision.isDirect());
    }

    @Test
    void qwenAllowsGenericSkKeyBecauseTheModelSelectsTheProvider() {
        BringYourOwnKeyRouter.Decision decision = BringYourOwnKeyRouter.resolve(
                "qwen-plus", "sk-a-generic-provider-key", new QwenCompatibleAdapter());

        assertTrue(decision.isDirect());
    }

    @Test
    void modelStillSelectsQwenRouteWhenKeyLooksLikeAnotherProvider() {
        BringYourOwnKeyRouter.Decision decision = BringYourOwnKeyRouter.resolve(
                "qwen-plus", "bce-v3/example", new QwenCompatibleAdapter());

        assertTrue(decision.isDirect());
    }

    @Test
    void zhipuAllowsItsSignedKey() {
        BringYourOwnKeyRouter.Decision decision = BringYourOwnKeyRouter.resolve(
                "glm-5", "f507240a75a9470db9d5787ebfca0e07.signature", new ZhipuAdapter());

        assertTrue(decision.isDirect());
    }

    @Test
    void modelStillSelectsZhipuRouteWhenKeyLooksLikeAnotherProvider() {
        BringYourOwnKeyRouter.Decision decision = BringYourOwnKeyRouter.resolve(
                "glm-5", "sk-ws-example", new ZhipuAdapter());

        assertTrue(decision.isDirect());
    }

    @Test
    void baiduAcceptsBceKey() {
        BringYourOwnKeyRouter.Decision decision = BringYourOwnKeyRouter.resolve(
                "ernie-5.0", "bce-v3/example", new ErnieAdapter());

        assertTrue(decision.isDirect());
    }

    @Test
    void directProviderModelIsSupportedButUnconfiguredProxyIsNot() {
        BringYourOwnKeyRouter.Decision decision = BringYourOwnKeyRouter.resolve(
                "deepseek-v4-pro", "sk-example", new DeepSeekAdapter());

        assertTrue(decision.isDirect());
    }

    @Test
    void cloudAliasIsFoundFromConfiguredLandingModels() {
        LlmManager manager = LlmManager.getInstance();
        manager.clear();
        LandingAdapter landing = new LandingAdapter();
        landing.setModel("Alibaba/qwen-plus");
        manager.register("Alibaba/qwen-plus", landing);

        try {
            org.junit.jupiter.api.Assertions.assertEquals("Alibaba/qwen-plus",
                    BringYourOwnKeyRouter.resolveCloudModelAlias("qwen-plus"));
        } finally {
            manager.clear();
        }
    }

    @Test
    void configuredCloudModelNeverTreatsCallerKeyAsByok() {
        LlmManager manager = LlmManager.getInstance();
        manager.clear();
        LandingAdapter landing = new LandingAdapter();
        landing.setModel("Alibaba/qwen-plus");
        manager.register("Alibaba/qwen-plus", landing);

        try {
            assertTrue(BringYourOwnKeyRouter.isConfiguredCloudModel("Alibaba/qwen-plus"));
            assertFalse(BringYourOwnKeyRouter.resolve("Alibaba/qwen-plus", "sk-unknown").isDirect());
        } finally {
            manager.clear();
        }
    }

    @Test
    void vendorPrefixedModelNeverTreatsCallerKeyAsByok() {
        assertFalse(BringYourOwnKeyRouter.resolve(
                "Alibaba/qwen-plus", "sk-unknown", new QwenCompatibleAdapter()).isDirect());
    }

    @Test
    void localKeyRoutingUsesConfiguredDirectBackendBeforeCloudFallback() {
        LlmManager manager = LlmManager.getInstance();
        manager.clear();
        QwenCompatibleAdapter qwen = new QwenCompatibleAdapter();
        qwen.setModel("qwen-plus");
        qwen.setApiKey("local-alibaba-key");
        LandingAdapter landing = new LandingAdapter();
        landing.setModel("Alibaba/qwen-plus");
        landing.setBackend("landing");
        landing.setApiKey("box-landing-key");
        manager.register("qwen-plus", qwen);
        manager.register("Alibaba/qwen-plus", landing);

        try {
            assertTrue(BringYourOwnKeyRouter.hasConfiguredDirectBackend("qwen-plus"));
            assertEquals("Alibaba/qwen-plus",
                    BringYourOwnKeyRouter.resolveCloudModelAlias("qwen-plus"));
            assertEquals("box-landing-key",
                    BringYourOwnKeyRouter.resolveConfiguredCloudApiKey("Alibaba/qwen-plus"));
        } finally {
            manager.clear();
        }
    }

    @Test
    void cloudBackendSelectionIsConfigurationDriven() {
        LlmManager manager = LlmManager.getInstance();
        manager.clear();
        LandingAdapter landingA = new LandingAdapter();
        landingA.setModel("Alibaba/qwen-plus");
        landingA.setBackend("landing-a");
        landingA.setApiKey("key-a");
        LandingAdapter landingB = new LandingAdapter();
        landingB.setModel("Alibaba/qwen-max");
        landingB.setBackend("landing-b");
        landingB.setApiKey("key-b");
        manager.register("Alibaba/qwen-plus", landingA);
        manager.register("Alibaba/qwen-max", landingB);

        try {
            assertEquals("Alibaba/qwen-plus",
                    BringYourOwnKeyRouter.resolveCloudModelAlias("qwen-plus", "landing-a"));
            assertFalse(BringYourOwnKeyRouter.configuredCloudModels("landing-a")
                    .contains("Alibaba/qwen-max"));
            assertEquals("key-a", BringYourOwnKeyRouter.resolveConfiguredCloudApiKey(
                    "Alibaba/qwen-plus", "landing-a"));
            assertEquals(null, BringYourOwnKeyRouter.resolveConfiguredCloudApiKey(
                    "Alibaba/qwen-plus", "landing-b"));
        } finally {
            manager.clear();
        }
    }
}
