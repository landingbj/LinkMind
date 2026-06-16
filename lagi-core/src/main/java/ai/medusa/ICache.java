package ai.medusa;


import ai.medusa.pojo.PromptInput;
import ai.medusa.utils.PromptCacheConfig;
import ai.medusa.utils.PromptCacheTrigger;
import ai.medusa.utils.PromptPool;
import ai.openai.pojo.ChatCompletionResult;

public interface ICache<K, V> {
    V get(K key);

    void put(K key, V value);

    void put(K key, V value, boolean needPersistent, boolean flush);

    void put(K key);

    void syncPut(PromptInput promptInput, ChatCompletionResult chatCompletionResult);

    void syncPut(PromptInput promptInput, ChatCompletionResult chatCompletionResult, boolean needPersistent, boolean flush);

    int size();

    V locate(K key);

    PromptPool getPromptPool();

    void startProcessingPrompt();
}
