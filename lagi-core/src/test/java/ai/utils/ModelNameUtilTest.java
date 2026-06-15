package ai.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModelNameUtilTest {

    @Test
    void preservesAlibabaProviderPrefix() {
        assertEquals("Alibaba/qwen3.6-plus",
                ModelNameUtil.normalizeOpenAiCompatibleModelName("Alibaba/qwen3.6-plus"));
        assertEquals("Alibaba/qwen3.6-plus",
                ModelNameUtil.normalizeOpenAiCompatibleModelName("  alibaba/qwen3.6-plus  "));
    }

    @Test
    void leavesOtherModelNamesUnchanged() {
        assertEquals("DeepSeek/deepseek-v4-flash",
                ModelNameUtil.normalizeOpenAiCompatibleModelName("DeepSeek/deepseek-v4-flash"));
        assertEquals("qwen3.6-plus",
                ModelNameUtil.normalizeOpenAiCompatibleModelName("qwen3.6-plus"));
        assertNull(ModelNameUtil.normalizeOpenAiCompatibleModelName(null));
    }

    @Test
    void resolvesBillingModelFromProviderResponseWhenRequestHasProviderPrefix() {
        assertEquals("qwen3.6-plus",
                ModelNameUtil.resolveBillingModelName("Alibaba/qwen3.6-plus", "qwen3.6-plus"));
        assertEquals("qwen3.6-plus",
                ModelNameUtil.resolveBillingModelName("Alibaba/qwen3.6-plus", null));
        assertEquals("qwen3.6-plus",
                ModelNameUtil.resolveBillingModelName("qwen3.6-plus", "qwen3.6-plus"));
        assertEquals("qwen3.6-plus",
                ModelNameUtil.resolveBillingModelName(null, "qwen3.6-plus"));
    }
}
