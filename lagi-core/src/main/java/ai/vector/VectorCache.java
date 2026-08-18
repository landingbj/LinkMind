package ai.vector;

import ai.common.pojo.IndexSearchData;
import ai.common.utils.LRUCache;
import ai.config.ContextLoader;
import ai.config.pojo.RAGFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class VectorCache {
    private static final Logger logger = LoggerFactory.getLogger(VectorCache.class);
    private static final long DEFAULT_CACHE_EXPIRE_SECONDS = TimeUnit.DAYS.toSeconds(30);
    private static final RAGFunction RAG_CONFIG = ContextLoader.configuration.getStores().getRag();

    private static final VectorCache INSTANCE = new VectorCache();
    private static final LRUCache<String, List<IndexSearchData>> vectorLinkCache;
    private static final LRUCache<String, IndexSearchData> parentElementCache;
    private static final LRUCache<String, List<IndexSearchData>> childElementCache;
    private static int CACHE_SIZE;
    private static long CACHE_EXPIRE_SECONDS;

    static {
        CACHE_SIZE = VectorStoreConstant.VECTOR_CACHE_SIZE;
        if (RAG_CONFIG.getCacheSize() != null) {
            CACHE_SIZE = RAG_CONFIG.getCacheSize();
        }
        CACHE_EXPIRE_SECONDS = DEFAULT_CACHE_EXPIRE_SECONDS;
        if (RAG_CONFIG.getCacheExpireSeconds() != null) {
            if (RAG_CONFIG.getCacheExpireSeconds() > 0) {
                CACHE_EXPIRE_SECONDS = RAG_CONFIG.getCacheExpireSeconds();
            } else {
                logger.warn("Invalid rag.cache_expire_seconds={}, using default {} seconds",
                        RAG_CONFIG.getCacheExpireSeconds(), DEFAULT_CACHE_EXPIRE_SECONDS);
            }
        }
        vectorLinkCache = new LRUCache<>(CACHE_SIZE, CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        parentElementCache = new LRUCache<>(CACHE_SIZE, CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        childElementCache = new LRUCache<>(CACHE_SIZE, CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        logger.info("Vector cache initialized: capacity={}, ttlSeconds={}", CACHE_SIZE, CACHE_EXPIRE_SECONDS);
    }

    private VectorCache() {
    }

    public static VectorCache getInstance() {
        return INSTANCE;
    }

    public List<IndexSearchData> getFromVectorLinkCache(String id) {
        return get(vectorLinkCache, "vectorLink", id);
    }

    public void putToVectorLinkCache(String id, List<IndexSearchData> extendedIndexSearchData) {
        vectorLinkCache.put(id, extendedIndexSearchData);
        logger.debug("Vector cache put: cacheType=vectorLink, id={}, size={}", id, vectorLinkCache.size());
    }

    public void removeFromVectorLinkCache(String id) {
        vectorLinkCache.remove(id);
        logger.info("Vector cache invalidated: cacheType=vectorLink, id={}", id);
    }

    public IndexSearchData getFromParentElementCache(String id) {
        return get(parentElementCache, "parentElement", id);
    }

    public boolean isParentCacheFull() {
        return parentElementCache.size() >= CACHE_SIZE;
    }

    public void putToParentElementCache(String id, IndexSearchData extendedIndexSearchData) {
        parentElementCache.put(id, extendedIndexSearchData);
        logger.debug("Vector cache put: cacheType=parentElement, id={}, size={}", id, parentElementCache.size());
    }

    public void removeFromParentElementCache(String id) {
        parentElementCache.remove(id);
        logger.info("Vector cache invalidated: cacheType=parentElement, id={}", id);
    }

    public List<IndexSearchData> getFromChildElementCache(String id) {
        return get(childElementCache, "childElement", id);
    }

    public void putToChildElementCache(String id, List<IndexSearchData> extendedIndexSearchData) {
        childElementCache.put(id, extendedIndexSearchData);
        logger.debug("Vector cache put: cacheType=childElement, id={}, size={}", id, childElementCache.size());
    }

    public void removeFromChildElementCache(String id) {
        childElementCache.remove(id);
        logger.info("Vector cache invalidated: cacheType=childElement, id={}", id);
    }

    public boolean isChildCacheFull() {
        return childElementCache.size() >= CACHE_SIZE;
    }

    public void removeFromAllCache(String id) {
        removeFromVectorLinkCache(id);
        removeFromParentElementCache(id);
        removeFromChildElementCache(id);
    }

    public void logStats(String reason) {
        logger.info("Vector cache stats: reason={}, vectorLink={}, parentElement={}, childElement={}", reason,
                formatStats(vectorLinkCache.getStats()), formatStats(parentElementCache.getStats()),
                formatStats(childElementCache.getStats()));
    }

    private <T> T get(LRUCache<String, T> cache, String cacheType, String id) {
        T value = cache.get(id);
        logger.debug("Vector cache access: cacheType={}, id={}, result={}, size={}",
                cacheType, id, value == null ? "miss" : "hit", cache.size());
        return value;
    }

    private String formatStats(LRUCache.CacheStats stats) {
        return String.format("hits=%d,misses=%d,puts=%d,expired=%d,capacityEvictions=%d,explicitRemovals=%d,size=%d",
                stats.getHits(), stats.getMisses(), stats.getPuts(), stats.getExpirations(),
                stats.getCapacityEvictions(), stats.getExplicitRemovals(), stats.getSize());
    }
}
