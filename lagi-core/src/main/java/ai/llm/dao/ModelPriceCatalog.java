package ai.llm.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Built-in reference prices for the models exposed by the SaaS catalogue.
 *
 * <p>Prices are the vendors' normal online, pay-as-you-go text prices per
 * million tokens, checked on 2026-08-25. All amounts are persisted in CNY.
 * USD source prices are converted at the fixed reference rate maintained by
 * {@link ModelPriceDao#CNY_PER_USD}. The current price table deliberately
 * only has input and output columns.  It therefore does not represent cache
 * hits, long-context tiers, batch discounts, peak/off-peak rates, or
 * audio/image/video pricing.  The corresponding source URLs are maintained in
 * {@code docs/tokenlink_local_user_key_plan_and_api_zh.md}.</p>
 *
 * <p>Rows use {@code effective_at = 0} so a later operator-maintained row for
 * the same provider and model always takes precedence. Unknown models are
 * priced separately by {@link ModelPriceDao} using a documented similar-model
 * estimate, so usage records always have a CNY amount.</p>
 */
final class ModelPriceCatalog {
    static final long BUILT_IN_EFFECTIVE_AT = 0L;

    private static final List<Price> DEFAULT_PRICES = createDefaults();

    private ModelPriceCatalog() {
    }

    static void seedDefaults(Connection connection) throws SQLException {
        String sql = "INSERT INTO linkmind_model_prices(provider, model, input_price_per_million, "
                + "output_price_per_million, currency, effective_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(provider, model, effective_at) DO UPDATE SET "
                + "input_price_per_million=excluded.input_price_per_million, "
                + "output_price_per_million=excluded.output_price_per_million, "
                + "currency=excluded.currency, updated_at=excluded.updated_at";
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (Price price : DEFAULT_PRICES) {
                ps.setString(1, price.provider);
                ps.setString(2, price.model);
                ps.setBigDecimal(3, price.inputPricePerMillion);
                ps.setBigDecimal(4, price.outputPricePerMillion);
                ps.setString(5, price.currency);
                ps.setLong(6, BUILT_IN_EFFECTIVE_AT);
                ps.setLong(7, now);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static List<Price> createDefaults() {
        List<Price> prices = new ArrayList<>();

        add(prices, "Alibaba", "CNY", "0.15", "1.5",
                "qwen-flash", "qwen-flash-2025-07-28");
        add(prices, "Alibaba", "CNY", "0.5", "2",
                "qwen-long", "qwen-long-2025-01-25");
        add(prices, "Alibaba", "CNY", "2.4", "9.6", "qwen-max");
        add(prices, "Alibaba", "CNY", "0.8", "2",
                "qwen-plus", "qwen-plus-latest", "qwen-plus-2024-12-20", "qwen-plus-2025-01-12",
                "qwen-plus-2025-01-25", "qwen-plus-2025-04-28", "qwen-plus-2025-07-14",
                "qwen-plus-2025-07-28", "qwen-plus-2025-09-11", "qwen-plus-2025-12-01");
        add(prices, "Alibaba", "CNY", "0.6", "3", "qwen-turbo");
        add(prices, "Alibaba", "CNY", "0.6", "2.4", "qwen2.5-omni-7b");
        add(prices, "Alibaba", "CNY", "2", "20", "qwen3-235b-a22b", "qwen3-235b-a22b-thinking-2507");
        add(prices, "Alibaba", "CNY", "2", "8", "qwen3-235b-a22b-instruct-2507");
        add(prices, "Alibaba", "CNY", "0.75", "3", "qwen3-30b-a3b-instruct-2507");
        add(prices, "Alibaba", "CNY", "0.75", "7.5", "qwen3-30b-a3b-thinking-2507");
        add(prices, "Alibaba", "CNY", "1.5", "6", "qwen3-coder-30b-a3b-instruct");
        add(prices, "Alibaba", "CNY", "6", "24", "qwen3-coder-480b-a35b-instruct");
        add(prices, "Alibaba", "CNY", "1", "4", "qwen3-coder-next", "qwen3-next-80b-a3b-instruct");
        add(prices, "Alibaba", "CNY", "2.5", "10", "qwen3-max", "qwen3-max-2026-01-23");
        add(prices, "Alibaba", "CNY", "6", "24", "qwen3-max-2025-09-23", "qwen3-max-preview");
        add(prices, "Alibaba", "CNY", "1", "10", "qwen3-next-80b-a3b-thinking");
        add(prices, "Alibaba", "CNY", "2", "8", "qwen3-vl-235b-a22b-instruct", "qwen3-vl-32b-instruct");
        add(prices, "Alibaba", "CNY", "2", "20", "qwen3-vl-235b-a22b-thinking", "qwen3-vl-32b-thinking");
        add(prices, "Alibaba", "CNY", "0.75", "3", "qwen3-vl-30b-a3b-instruct");
        add(prices, "Alibaba", "CNY", "0.75", "7.5", "qwen3-vl-30b-a3b-thinking");
        add(prices, "Alibaba", "CNY", "0.5", "2", "qwen3-vl-8b-instruct");
        add(prices, "Alibaba", "CNY", "0.5", "5", "qwen3-vl-8b-thinking");
        add(prices, "Alibaba", "CNY", "0.8", "6.4", "qwen3.5-122b-a10b");
        add(prices, "Alibaba", "CNY", "0.6", "4.8", "qwen3.5-27b");
        add(prices, "Alibaba", "CNY", "0.4", "3.2", "qwen3.5-35b-a3b");
        add(prices, "Alibaba", "CNY", "1.2", "7.2", "qwen3.5-397b-a17b", "qwen3.6-flash");
        add(prices, "Alibaba", "CNY", "0.2", "2", "qwen3.5-flash", "qwen3.5-flash-2026-02-23");
        add(prices, "Alibaba", "CNY", "0.8", "4.8", "qwen3.5-plus", "qwen3.5-plus-2026-02-15");
        add(prices, "Alibaba", "CNY", "2.2", "13.3", "qwen3.5-omni-flash", "qwen3.5-omni-flash-2026-03-15");
        add(prices, "Alibaba", "CNY", "7", "40", "qwen3.5-omni-plus", "qwen3.5-omni-plus-2026-03-15");
        add(prices, "Alibaba", "CNY", "2", "12", "qwen3.6-plus", "qwen3.6-plus-2026-04-02");
        add(prices, "Alibaba", "CNY", "12", "36", "qwen3.7-max");
        add(prices, "Alibaba", "CNY", "2", "8", "qwen3.7-plus");

        add(prices, "anthropic", "USD", "1", "5", "claude-haiku-4.5");
        add(prices, "anthropic", "USD", "3", "15", "claude-sonnet-4.5", "claude-sonnet-4.6");
        add(prices, "anthropic", "USD", "5", "25", "claude-opus-4.5", "claude-opus-4.6");

        add(prices, "google", "USD", "0.5", "3", "gemini-3-flash-preview");
        add(prices, "google", "USD", "0.25", "1.5", "gemini-3.1-flash-lite");
        add(prices, "google", "USD", "2", "12", "gemini-3.1-pro-preview");

        // Keep box estimates aligned with the active SaaS price catalogue.
        add(prices, "DeepSeek", "CNY", "1", "2", "deepseek-v4-flash");
        add(prices, "DeepSeek", "CNY", "3", "6", "deepseek-v4-pro");

        add(prices, "moonshot", "CNY", "4", "21", "kimi-k2.5");
        add(prices, "moonshot", "CNY", "2", "10", "moonshot-v1-8k", "moonshot-v1-8k-vision-preview");
        add(prices, "moonshot", "CNY", "5", "20", "moonshot-v1-32k", "moonshot-v1-32k-vision-preview");
        add(prices, "moonshot", "CNY", "10", "30", "moonshot-v1-128k", "moonshot-v1-128k-vision-preview");

        add(prices, "zai", "USD", "0.2", "1.1", "glm-4.5-air");
        add(prices, "zai", "USD", "0.6", "2.2", "glm-4.7");
        add(prices, "zai", "USD", "0", "0", "glm-4.7-flash");
        add(prices, "zai", "USD", "0.07", "0.4", "glm-4.7-flashx");
        add(prices, "zai", "USD", "1", "3.2", "glm-5");
        add(prices, "zai", "USD", "1.2", "4", "glm-5-turbo");
        add(prices, "zai", "USD", "1.4", "4.4", "glm-5.1");

        add(prices, "minimax", "CNY", "2.1", "8.4", "MiniMax-M2.5");
        add(prices, "minimax", "CNY", "4.2", "16.8", "MiniMax-M2.5-highspeed");

        add(prices, "ByteDance", "CNY", "3.2", "16", "doubao-seed-2-0-code-preview-260215", "doubao-seed-2-0-pro-260215");
        add(prices, "ByteDance", "CNY", "0.6", "3.6", "doubao-seed-2-0-lite-260215");
        add(prices, "ByteDance", "CNY", "0.2", "2", "doubao-seed-2-0-mini-260215");

        add(prices, "BAICHUAN", "CNY", "2", "20", "Baichuan-M2");
        add(prices, "BAICHUAN", "CNY", "10", "30", "Baichuan-M2-Plus", "Baichuan-M3");
        add(prices, "BAICHUAN", "CNY", "5", "9", "Baichuan-M3-Plus");
        // These legacy Baichuan prices are published as a single per-token rate, so input and output use the same value.
        add(prices, "BAICHUAN", "CNY", "10", "10", "Baichuan2-53B");
        add(prices, "BAICHUAN", "CNY", "8", "8", "Baichuan2-Turbo");
        add(prices, "BAICHUAN", "CNY", "12", "12", "Baichuan3-Turbo");
        add(prices, "BAICHUAN", "CNY", "24", "24", "Baichuan3-Turbo-128k");
        add(prices, "BAICHUAN", "CNY", "100", "100", "Baichuan4");
        add(prices, "BAICHUAN", "CNY", "0.98", "0.98", "Baichuan4-Air");
        add(prices, "BAICHUAN", "CNY", "15", "15", "Baichuan4-Turbo");

        add(prices, "StepFun", "CNY", "0.7", "2.1", "step-3.5-flash");

        add(prices, "openai", "USD", "2.5", "15", "gpt-5.4");
        add(prices, "openai", "USD", "0.75", "4.5", "gpt-5.4-mini");
        add(prices, "openai", "USD", "0.2", "1.25", "gpt-5.4-nano");
        add(prices, "openai", "USD", "5", "30", "gpt-5.5");

        return Collections.unmodifiableList(prices);
    }

    private static void add(List<Price> prices, String provider, String currency, String input, String output,
                            String... models) {
        for (String model : models) {
            prices.add(new Price(provider, model, input, output, currency));
        }
    }

    private static final class Price {
        private final String provider;
        private final String model;
        private final BigDecimal inputPricePerMillion;
        private final BigDecimal outputPricePerMillion;
        private final String currency;

        private Price(String provider, String model, String input, String output, String currency) {
            this.provider = provider;
            this.model = model;
            this.inputPricePerMillion = ModelPriceDao.toCny(new BigDecimal(input), currency);
            this.outputPricePerMillion = ModelPriceDao.toCny(new BigDecimal(output), currency);
            this.currency = ModelPriceDao.CNY;
        }
    }
}
