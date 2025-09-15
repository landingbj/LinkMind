package ai.embedding.impl;

import ai.common.pojo.EmbeddingConfig;
import ai.embedding.EmbeddingGlobal;
import ai.embedding.Embeddings;
import ai.embedding.EmbeddingsUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LinkMindEmbeddings implements Embeddings {

    public LinkMindEmbeddings(EmbeddingConfig config) {
        if (!EmbeddingGlobal.EMBEDDING_WEIGHT.equals(config.getModel_path())) {
            EmbeddingGlobal.EMBEDDING_WEIGHT = config.getModel_path();
        }
    }

    @Override
    public List<List<Float>> createEmbedding(List<String> docs) {
        List<List<Float>> result = new ArrayList<>();
        for (String text : docs) {
            List<Double> vector = EmbeddingsUtil.embeddings(text);
            result.add(convert(vector));
        }
        return result;
    }

    @Override
    public List<Float> createEmbedding(String doc) {
        List<String> docs = Collections.singletonList(doc);
        List<List<Float>> result = createEmbedding(docs);
        if (!result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }

    private List<Float> convert(List<Double> doubleList) {
        if (doubleList == null) {
            return new ArrayList<>();
        }

        List<Float> floatList = new ArrayList<>(doubleList.size());
        for (Double d : doubleList) {
            if (d != null) {
                floatList.add(d.floatValue());
            } else {
                floatList.add(null);
            }
        }
        return floatList;
    }
}
