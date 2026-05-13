package ai.llm.service;

import ai.openai.pojo.Usage;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TokenUsageQueue {
    private static final TokenUsageQueue INSTANCE = new TokenUsageQueue();
    private final BlockingQueue<TokenUsageRecord> queue = new LinkedBlockingQueue<TokenUsageRecord>();

    private TokenUsageQueue() {
    }

    public static TokenUsageQueue getInstance() {
        return INSTANCE;
    }

    public boolean enqueue(String apiKey, String modelName, Usage usage) {
        if (usage == null) {
            return false;
        }
        if (modelName == null || modelName.trim().isEmpty()) {
            return false;
        }
        // offer is non-blocking; it returns immediately.
        return queue.offer(new TokenUsageRecord(apiKey, modelName.trim(), usage));
    }

    public TokenUsageRecord poll() {
        return queue.poll();
    }

    public TokenUsageRecord take() throws InterruptedException {
        return queue.take();
    }

    public static class TokenUsageRecord {
        private final String apiKey;
        private final String modelName;
        private final Usage usage;

        public TokenUsageRecord(String apiKey, String modelName, Usage usage) {
            this.apiKey = apiKey;
            this.modelName = modelName;
            this.usage = usage;
        }

        public String getApiKey() {
            return apiKey;
        }

        public String getModelName() {
            return modelName;
        }

        public Usage getUsage() {
            return usage;
        }
    }
}
