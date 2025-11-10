package ai.vector.impl;

import ai.common.pojo.VectorStoreConfig;
import ai.embedding.Embeddings;
import ai.utils.OkHttpUtil;
import ai.vector.pojo.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import tech.amikos.chromadb.Client;
import tech.amikos.chromadb.Collection;
import tech.amikos.chromadb.EmbeddingFunction;
import tech.amikos.chromadb.handler.ApiException;

import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.util.*;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Slf4j
public class ChromaVectorStore extends BaseVectorStore {
    private static final int TIMEOUT = 60 * 3;
    private final CustomEmbeddingFunction embeddingFunction;
    private final Map<String, String> colMetadata;
    private static final Gson gson = new Gson();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper.setSerializationInclusion(NON_NULL);
    }

    public static class CustomEmbeddingFunction implements EmbeddingFunction {
        private final Embeddings ef;

        public CustomEmbeddingFunction(Embeddings ef) {
            this.ef = ef;
        }

        @Override
        public List<List<Float>> createEmbedding(List<String> list) {
            return this.ef.createEmbedding(list);
        }

        @Override
        public List<List<Float>> createEmbedding(List<String> list, String s) {
            return null;
        }
    }

    public ChromaVectorStore(VectorStoreConfig config, Embeddings embeddingFunction) {
        this.config = config;
        this.embeddingFunction = new CustomEmbeddingFunction(embeddingFunction);
        colMetadata = new LinkedTreeMap<>();
        colMetadata.put("hnsw:space", config.getMetric());
        colMetadata.put("embedding_function", this.embeddingFunction.getClass().getName());
    }

    private Client getClient() {
        Client client = new Client(this.config.getUrl());
        client.setTimeout(TIMEOUT);
        return client;
    }

    private Collection getCollection(String category) {
        Client client = getClient();
        Collection collection = null;
        try {
            collection = client.createCollection(category, colMetadata, true, this.embeddingFunction);
        } catch (ApiException e) {
            log.error("Failed to create/get Chroma collection '{}': code={}, message={}, responseBody={}", 
                    category, e.getCode(), e.getMessage(), e.getResponseBody());
            try {
                Map<String, String> simpleMetadata = new LinkedTreeMap<>();
                simpleMetadata.put("hnsw:space", config.getMetric());
                collection = client.createCollection(category, simpleMetadata, true, this.embeddingFunction);
                log.info("Successfully created collection '{}' with simplified metadata", category);
            } catch (ApiException e2) {
                log.error("Failed to create collection '{}' with simplified metadata: code={}, message={}, responseBody={}", 
                        category, e2.getCode(), e2.getMessage(), e2.getResponseBody());
                try {
                    collection = client.getCollection(category, this.embeddingFunction);
                    log.info("Collection '{}' already exists, retrieved successfully", category);
                } catch (ApiException e3) {
                    log.error("Failed to get existing collection '{}': code={}, message={}, responseBody={}", 
                            category, e3.getCode(), e3.getMessage(), e3.getResponseBody());
                    throw new RuntimeException("Failed to create or get Chroma collection: " + category + ", responseBody: " + e3.getResponseBody(), e3);
                }
            }
        }
        return collection;
    }

    public void upsert(List<UpsertRecord> upsertRecords) {
        upsert(upsertRecords, this.config.getDefaultCategory());
    }

    public void upsert(List<UpsertRecord> upsertRecords, String category) {
        List<String> documents = new ArrayList<>();
        List<Map<String, String>> metadatas = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (UpsertRecord upsertRecord : upsertRecords) {
            documents.add(upsertRecord.getDocument());
            metadatas.add(upsertRecord.getMetadata());
            ids.add(upsertRecord.getId());
        }
        List<List<Float>> embeddings = this.embeddingFunction.createEmbedding(documents);
        Collection collection = getCollection(category);
        try {
            collection.upsert(embeddings, metadatas, documents, ids);
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
    }

    public List<IndexRecord> query(QueryCondition queryCondition) {
        List<IndexRecord> result = new ArrayList<>();
        String category = queryCondition.getCategory() != null ? queryCondition.getCategory() : this.config.getDefaultCategory();
        Collection collection = getCollection(category);
        if (queryCondition.getText() == null || queryCondition.getText().isEmpty()) {
            GetEmbedding getEmbedding = GetEmbedding.builder()
                    .category(category)
                    .where(queryCondition.getWhere())
                    .limit(queryCondition.getN())
                    .offset(0)
                    .whereDocument(queryCondition.getWhereDocument())
                    .build();
            return get(getEmbedding);
        }

        List<List<Float>> queryEmbeddings = this.embeddingFunction.createEmbedding(Collections.singletonList(queryCondition.getText()));
        ChromaQueryRequest queryEmbedding = ChromaQueryRequest.builder()
                .where(queryCondition.getWhere())
                .whereDocument(queryCondition.getWhereDocument())
                .queryEmbeddings(queryEmbeddings)
                .nResults(queryCondition.getN())
                .build();

        String url = this.config.getUrl() + "/api/v1/collections/" + collection.getId() + "/query";
        Collection.QueryResponse qr;
        try {
            String json = OkHttpUtil.post(url, objectMapper.writeValueAsString(queryEmbedding));
            qr = gson.fromJson(json, Collection.QueryResponse.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (qr == null) {
            return result;
        }
        for (int i = 0; i < qr.getDocuments().size(); i++) {
            for (int j = 0; j < qr.getDocuments().get(i).size(); j++) {
                IndexRecord indexRecord = IndexRecord.builder()
                        .document(qr.getDocuments().get(i).get(j))
                        .id(qr.getIds().get(i).get(j))
                        .metadata(qr.getMetadatas().get(i).get(j))
                        .distance(qr.getDistances().get(i).get(j))
                        .build();
                result.add(indexRecord);
            }
        }
        return result;
    }

    private List<IndexRecord> getIndexRecords(Collection.GetResult gr) {
        List<IndexRecord> result = new ArrayList<>();
        for (int i = 0; i < gr.getDocuments().size(); i++) {
            IndexRecord indexRecord = IndexRecord.builder()
                    .document(gr.getDocuments().get(i))
                    .id(gr.getIds().get(i))
                    .metadata(gr.getMetadatas() == null ? null : gr.getMetadatas().get(i))
                    .build();
            result.add(indexRecord);
        }
        return result;
    }

    private List<IndexRecord> getIndexRecords(GetResult gr) {
        List<IndexRecord> result = new ArrayList<>();
        for (int i = 0; i < gr.getIds().size(); i++) {
            IndexRecord indexRecord = IndexRecord.builder()
                    .id(gr.getIds().get(i))
                    .document(gr.getDocuments() == null ? null : gr.getDocuments().get(i))
                    .metadata(gr.getMetadatas() == null ? null : gr.getMetadatas().get(i))
                    .embeddings(gr.getEmbeddings() == null ? null : gr.getEmbeddings().get(i))
                    .build();
            result.add(indexRecord);
        }
        return result;
    }


    public List<IndexRecord> fetch(List<String> ids) {
        return fetch(ids, this.config.getDefaultCategory());
    }

    public List<IndexRecord> fetch(List<String> ids, String category) {
        Collection.GetResult gr;
        Collection collection = getCollection(category);
        try {
            gr = collection.get(ids, null, null);
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
        return getIndexRecords(gr);
    }


    public List<IndexRecord> fetch(int limit, int offset, String category) {
        Collection.GetResult gr;
        ChromaGetRequest chromaGetRequest = ChromaGetRequest.builder().limit(limit).offset(offset).build();
        Collection collection = getCollection(category);
        String url = this.config.getUrl() + "/api/v1/collections/" + collection.getId() + "/get";
        try {
            String json = OkHttpUtil.post(url, new Gson().toJson(chromaGetRequest));
            gr = gson.fromJson(json, Collection.GetResult.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return getIndexRecords(gr);
    }

    public List<IndexRecord> fetch(Map<String, String> where) {
        return fetch(where, this.config.getDefaultCategory());
    }

    public List<IndexRecord> fetch(Map<String, String> where, String category) {
        Collection.GetResult gr;
        Collection collection = getCollection(category);
        try {
            gr = collection.get(null, where, null);
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
        return getIndexRecords(gr);
    }


    public void delete(List<String> ids) {
        this.delete(ids, this.config.getDefaultCategory());
    }

    public void delete(List<String> ids, String category) {
        Collection collection = getCollection(category);
        try {
            collection.deleteWithIds(ids);
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleteWhere(List<Map<String, String>> whereList) {
        deleteWhere(whereList, this.config.getDefaultCategory());
    }

    @Override
    public void deleteWhere(List<Map<String, String>> whereList, String category) {
        Collection collection = getCollection(category);
        try {
            for (Map<String, String> where : whereList) {
                collection.deleteWhere(where);
            }
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleteCollection(String category) {
        try {
            Client client = getClient();
            for (VectorCollection vectorCollection : listCollections()) {
                if (vectorCollection.getCategory().equals(category)) {
                    client.deleteCollection(category);
                    break;
                }
            }
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<VectorCollection> listCollections() {
        List<VectorCollection> result = new ArrayList<>();
        try {
            Client client = getClient();
            List<Collection> collections = client.listCollections();
            for (Collection collection : collections) {
                VectorCollection vectorCollection = VectorCollection.builder()
                        .category(collection.getName())
                        .vectorCount(collection.count())
                        .build();
                result.add(vectorCollection);
            }
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    @Override
    public List<IndexRecord> get(GetEmbedding getEmbedding) {
        GetResult gr;
        String category = getEmbedding.getCategory() != null ? getEmbedding.getCategory() : this.config.getDefaultCategory();
        Collection collection = getCollection(category);
        String url = this.config.getUrl() + "/api/v1/collections/" + collection.getId() + "/get";
        try {
            String reqJson = objectMapper.writeValueAsString(getEmbedding);
            String json = OkHttpUtil.post(url, reqJson);
            gr = gson.fromJson(json, GetResult.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return getIndexRecords(gr);
    }

    @Override
    public void add(AddEmbedding addEmbedding) {
        String category = addEmbedding.getCategory() != null ? addEmbedding.getCategory() : this.config.getDefaultCategory();
        Collection collection = getCollection(category);
        String url = this.config.getUrl() + "/api/v1/collections/" + collection.getId() + "/add";

        ChromaAddRequest chromaAddRequest = ChromaAddRequest.builder()
                .ids(new ArrayList<>())
                .documents(new ArrayList<>())
                .metadatas(new ArrayList<>())
                .embeddings(new ArrayList<>())
                .build();

        for (AddEmbedding.AddEmbeddingData addEmbeddingData : addEmbedding.getData()) {
            if (addEmbeddingData.getId() == null || addEmbeddingData.getId().isEmpty()) {
                addEmbeddingData.setId(UUID.randomUUID().toString().replace("-", ""));
            }
            chromaAddRequest.getIds().add(addEmbeddingData.getId());
            chromaAddRequest.getDocuments().add(addEmbeddingData.getDocument());
            chromaAddRequest.getMetadatas().add(addEmbeddingData.getMetadata());
            if (addEmbeddingData.getEmbedding() == null || addEmbeddingData.getEmbedding().isEmpty()) {
                List<List<Float>> embeddings = this.embeddingFunction.createEmbedding(Collections.singletonList(addEmbeddingData.getDocument()));
                addEmbeddingData.setEmbedding(embeddings.get(0));
            }
            chromaAddRequest.getEmbeddings().add(addEmbeddingData.getEmbedding());
        }
        try {
            OkHttpUtil.post(url, objectMapper.writeValueAsString(chromaAddRequest));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void update(UpdateEmbedding updateEmbedding) {
        String category = updateEmbedding.getCategory() != null ? updateEmbedding.getCategory() : this.config.getDefaultCategory();
        Collection collection = getCollection(category);
        String url = this.config.getUrl() + "/api/v1/collections/" + collection.getId() + "/update";
        ChromaUpdateRequest chromaUpdateRequest = ChromaUpdateRequest.builder()
                .ids(new ArrayList<>())
                .documents(new ArrayList<>())
                .metadatas(new ArrayList<>())
                .embeddings(new ArrayList<>())
                .build();

        for (UpdateEmbedding.UpdateEmbeddingData updateEmbeddingData : updateEmbedding.getData()) {
            chromaUpdateRequest.getIds().add(updateEmbeddingData.getId());
            chromaUpdateRequest.getMetadatas().add(updateEmbeddingData.getMetadata());
            chromaUpdateRequest.getDocuments().add(updateEmbeddingData.getDocument());
            List<Float> embedding = updateEmbeddingData.getEmbedding();
            String document = updateEmbeddingData.getDocument();
            if (document != null && !document.trim().isEmpty() &&
                    (embedding == null || embedding.isEmpty())) {
                List<List<Float>> generatedEmbeddings = embeddingFunction.createEmbedding(Collections.singletonList(document));
                if (generatedEmbeddings != null && !generatedEmbeddings.isEmpty()) {
                    embedding = generatedEmbeddings.get(0);
                }
            }
            chromaUpdateRequest.getEmbeddings().add(embedding);
        }

        try {
            OkHttpUtil.post(url, objectMapper.writeValueAsString(chromaUpdateRequest));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void delete(DeleteEmbedding deleteEmbedding) {
        String category = deleteEmbedding.getCategory() != null ? deleteEmbedding.getCategory() : this.config.getDefaultCategory();
        Collection collection = getCollection(category);
        String url = this.config.getUrl() + "/api/v1/collections/" + collection.getId() + "/delete";
        try {
            OkHttpUtil.post(url, objectMapper.writeValueAsString(deleteEmbedding));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
