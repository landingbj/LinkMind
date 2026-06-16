package ai.medusa.impl;

import ai.common.pojo.IndexSearchData;
import ai.medusa.utils.PromptCacheConfig;
import ai.medusa.pojo.PromptInput;
import ai.utils.LRUCache;
import ai.vector.VectorStoreService;
import ai.vector.pojo.UpsertRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class QaCache {
    private static final Logger logger = LoggerFactory.getLogger(QaCache.class);
    private static final LRUCache<String, List<PromptInput>> cache;
    private static final String MEDUSA_CATEGORY = PromptCacheConfig.MEDUSA_CATEGORY;
    private static final int QA_SIMILARITY_TOP_K = PromptCacheConfig.QA_SIMILARITY_TOP_K;

    private static final VectorStoreService vectorStoreService = new VectorStoreService();

    static {
        cache = new LRUCache<>(PromptCacheConfig.COMPLETION_CACHE_SIZE);
        if (PromptCacheConfig.MEDUSA_FLUSH) {
            try {
                vectorStoreService.deleteCollection(MEDUSA_CATEGORY);
            } catch (Exception ignored) {
            }
        }
    }

    public List<PromptInput> get(String key) {
        return cache.get(key);
    }

    public void put(String key, List<PromptInput> value) {
        put(key, value, true);
    }

    public void put(String key, List<PromptInput> value, boolean flush) {
        if (flush) {
            try {
                Map<String, String> metadata = new HashMap<>();
                metadata.put("category", MEDUSA_CATEGORY);
                UpsertRecord upsertRecord = UpsertRecord.newBuilder().withDocument(key).withMetadata(metadata).build();
                vectorStoreService.upsertCustomVectors(Collections.singletonList(upsertRecord), MEDUSA_CATEGORY);
            } catch (Exception ignored) {
            }
        }
        cache.put(key, value);
    }

    public int size() {
        return cache.size();
    }

    public String getPromptInVectorDb(String key, double similarityCutoff) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("category", MEDUSA_CATEGORY);
        List<IndexSearchData> indexSearchDataList = new ArrayList<>();
        try {
            indexSearchDataList = vectorStoreService.search(key, QA_SIMILARITY_TOP_K,
                    similarityCutoff, metadata, MEDUSA_CATEGORY);
        } catch (Exception ignored) {
        }
        if (indexSearchDataList.isEmpty()) {
            return null;
        }
        IndexSearchData indexSearchData = indexSearchDataList.get(0);
//        logger.info("Prompt in vector db index search data: {}", indexSearchData);
        return indexSearchData.getText();
    }
}
