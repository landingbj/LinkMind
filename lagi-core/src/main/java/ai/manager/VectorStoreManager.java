package ai.manager;

import ai.common.ModelService;
import ai.common.pojo.EmbeddingConfig;
import ai.common.pojo.VectorStoreConfig;
import ai.config.ContextLoader;
import ai.config.pojo.RAGFunction;
import ai.embedding.EmbeddingFactory;
import ai.embedding.Embeddings;
import ai.utils.LagiGlobal;
import ai.vector.VectorStore;
import ai.vector.impl.BaseVectorStore;
import ai.vector.impl.ProxyVectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class VectorStoreManager {

    private final Logger log = LoggerFactory.getLogger(VectorStoreManager.class);

    private final Map<String, VectorStore> aiMap = new ConcurrentHashMap<>();

    private static final VectorStoreManager INSTANCE = new VectorStoreManager();

    public static VectorStoreManager getInstance() {
        return INSTANCE;
    }

    private VectorStoreManager(){}

    public void register(List<VectorStoreConfig> vectorStoreConfigs, RAGFunction ragFunction, List<EmbeddingConfig> embeddings) {
        aiMap.clear();
        LagiGlobal.RAG_ENABLE = false;
        if (vectorStoreConfigs == null || vectorStoreConfigs.isEmpty() || ragFunction == null || embeddings == null || embeddings.isEmpty()) {
            return;
        }
        Map<String, VectorStoreConfig> vectorMap = vectorStoreConfigs.stream().collect(Collectors.toMap(VectorStoreConfig::getName, vectorStoreConfig -> vectorStoreConfig));
        if(Boolean.TRUE.equals(ragFunction.getEnable())) {
            VectorStoreConfig vectorStoreConfig = vectorMap.get(ragFunction.getVector());
            Optional.ofNullable(vectorStoreConfig).ifPresent(v -> {
                try {
                    String name = v.getName();
                    String driver = v.getDriver();
                    Class<?> clazz = ContextLoader.getClass(driver);
                    Constructor<?> constructor = clazz.getConstructor(VectorStoreConfig.class, Embeddings.class);
                    BaseVectorStore vs = (BaseVectorStore) constructor.newInstance(v, EmbeddingFactory.getEmbedding(embeddings.get(0)));
                    register(name, vs);
                    LagiGlobal.RAG_ENABLE = true;
                } catch (Exception e) {
                    log.error("registerVectorStore ({})error", v.getName());
                }}
            );
        }
    }

    public void register(String key, VectorStore adapter) {
        if(adapter instanceof BaseVectorStore) {
            BaseVectorStore baseVectorStore = (BaseVectorStore) adapter;
            Integer concurrency = baseVectorStore.getConfig().getConcurrency();
            if(concurrency != null && concurrency > 0) {
                adapter = new ProxyVectorStore(baseVectorStore);
            }
        }
        VectorStore previous = aiMap.put(key, adapter);
        if (previous != null) {
            log.debug("VectorStore {} ({}) replaced on re-register", key, adapter.getClass().getName());
        }
    }


    public VectorStore getAdapter(String key) {
        return aiMap.get(key);
    }

    public VectorStore getAdapter() {
        Iterator<VectorStore> it = aiMap.values().iterator();
        return it.hasNext() ? it.next() : null;
    }

    public List<VectorStore> getAdapters() {
        return getDefaultSortedAdapter(aiMap);
    }

    private static <T> List<T> getSortedAdapter(Map<String, T> map, Comparator<? super T> comparator) {
        return map.values().stream().sorted(comparator).collect(Collectors.toList());
    }

    private static <T> List<T> getDefaultSortedAdapter(Map<String, T> map) {
        return getSortedAdapter(map, (m1, m2)->{
            if(!(m1 instanceof ModelService)) {
                return 0;
            }
            ModelService ms1 = (ModelService)m1;
            ModelService ms2 = (ModelService)m2;
            if(ms1.getPriority() != null) {
                return ms1.getPriority().compareTo(ms2.getPriority()) * -1;
            }
            return 1;
        });
    }

}


