package ai.vector.impl;

import ai.common.pojo.IndexSearchData;
import ai.vector.VectorStoreService;
import com.google.common.collect.Lists;
import com.google.gson.JsonObject;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.GetLoadStateReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.DeleteResp;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;
import io.milvus.v2.service.vector.response.UpsertResp;
import ai.common.pojo.VectorStoreConfig;
import ai.config.ContextLoader;
import ai.embedding.Embeddings;
import ai.vector.pojo.IndexRecord;
import ai.vector.pojo.QueryCondition;
import ai.vector.pojo.UpsertRecord;
import lombok.NoArgsConstructor;

import java.util.*;
import java.util.stream.Collectors;

@NoArgsConstructor
public class MilvusVectorStore extends BaseVectorStore {
    private static final String DOCUMENT = "document";
    private MilvusClientV2 clientV2;
    private Embeddings embeddingFunction;

    public MilvusVectorStore(VectorStoreConfig config, Embeddings embeddingFunction) {
        this.embeddingFunction = embeddingFunction;
        this.config = config;
        try {
            ConnectConfig connectConfig = ConnectConfig.builder()
                    .uri(config.getUrl())
                    .token(config.getToken())
                    .build();
            this.clientV2 = new MilvusClientV2(connectConfig);
        }catch (Exception e){

        }
    }
    @Override
    public void upsert(List<UpsertRecord> upsertRecords) {
        upsert(upsertRecords, this.config.getDefaultCategory());
    }
    @Override
    public void upsert(List<UpsertRecord> upsertRecords, String category) {
        List<String> documents = new ArrayList<>();
        List<Map<String, String>> metadatas = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (UpsertRecord upsertRecord : upsertRecords) {
            documents.add(upsertRecord.getDocument());
            Map<String, String> metadata = upsertRecord.getMetadata();
            if (metadata == null) {
                metadata = new HashMap<>();
            }
            metadata.put(DOCUMENT, upsertRecord.getDocument());
            metadatas.add(metadata);
            ids.add(upsertRecord.getId());
        }
        List<List<Float>> embeddings = embeddingFunction.createEmbedding(documents);

        createCollection(category);

        List<JsonObject> data1 = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++){
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("id", ids.get(i));
            jsonObject.add("vector", new JsonObject());
            jsonObject.add("metadata", new JsonObject());
            data1.add(jsonObject);
        }

        UpsertReq upsertReq = UpsertReq.builder()
                .collectionName(category)
                .data(data1)
                .build();
        UpsertResp upsertResp = clientV2.upsert(upsertReq);

    }
    @Override
    public List<IndexRecord> query(QueryCondition queryCondition) {
        return query(queryCondition, this.config.getDefaultCategory());
    }

    @Override
    public List<IndexRecord> query(QueryCondition queryCondition, String category) {
        List<IndexRecord> result = new ArrayList<>();
        createCollection(category);
        if (queryCondition.getText() != null){
            List<List<Float>> embeddings = embeddingFunction.createEmbedding(Lists.newArrayList(queryCondition.getText()));
            List<BaseVector> collect = embeddings.stream().map(FloatVec::new).collect(Collectors.toList());
            Map<String,Object> map = new HashMap<>();
            map.put("radius",config.getSimilarityCutoff());
            SearchReq searchReq = SearchReq.builder()
                    .collectionName(category)
                    .data(collect)
                    .searchParams(map)
                    .topK(getConfig().getSimilarityTopK())
                    .outputFields(Lists.newArrayList("id","metadata","vector"))
                    .build();
            SearchResp searchResp = clientV2.search(searchReq);
            return searchRespToIndexRecord(result,searchResp);
        }else {
            String filter = "metadata['parent_id'] == '"+queryCondition.getWhere().get("parent_id")+"'";
            QueryReq queryReq = QueryReq.builder()
                    .collectionName(category)
                    .filter(filter)
                    .outputFields(Lists.newArrayList("id","metadata","vector"))
                    .build();
            QueryResp queryResp = clientV2.query(queryReq);
            return queryRespToIndexRecord(result,queryResp);
        }
    }

    private List<IndexRecord> queryRespToIndexRecord(List<IndexRecord> result, QueryResp queryResp) {
        for (int i = 0; i < queryResp.getQueryResults().size(); i++) {
            IndexRecord indexRecord = new IndexRecord();
            indexRecord.setId((String) queryResp.getQueryResults().get(i).getEntity().get("id"));
            Map<String,Object> metadata = (Map<String, Object>) queryResp.getQueryResults().get(i).getEntity().get("metadata");
            indexRecord.setDocument((String) metadata.get(DOCUMENT));
            indexRecord.setMetadata(metadata);
            result.add(indexRecord);
        }
        return result;
    }

    private List<IndexRecord> searchRespToIndexRecord(List<IndexRecord> result,SearchResp searchResp) {
        for (int i = 0; i < searchResp.getSearchResults().size(); i++) {
            for (int j = 0; j < searchResp.getSearchResults().get(i).size(); j++){
                IndexRecord indexRecord = new IndexRecord();
                Map<String,Object> metadata = (Map<String, Object>) searchResp.getSearchResults().get(i).get(j).getEntity().get("metadata");
                indexRecord.setId((String) searchResp.getSearchResults().get(i).get(j).getId());
                indexRecord.setDocument((String) metadata.get(DOCUMENT));
                indexRecord.setMetadata(metadata);
                indexRecord.setDistance(1-searchResp.getSearchResults().get(i).get(j).getScore());
                result.add(indexRecord);
            }
        }
        return result;
    }

    @Override
    public List<IndexRecord> fetch(List<String> ids) {
        return fetch(ids, this.config.getDefaultCategory());
    }

    @Override
    public List<IndexRecord> fetch(List<String> ids, String category) {
        List<IndexRecord> result = new ArrayList<>();
        QueryReq queryReq = QueryReq.builder()
                .collectionName(category)
                .ids(ids.stream().map(s->(Object) s).collect(Collectors.toList()))
                .outputFields(Lists.newArrayList("id","metadata","vector"))
                .build();
        QueryResp queryResp = clientV2.query(queryReq);
        return queryRespToIndexRecord(result,queryResp);
    }

    @Override
    public void delete(List<String> ids) {
        this.delete(ids, this.config.getDefaultCategory());
    }

    @Override
    public void delete(List<String> ids, String category) {
        try {
            DeleteReq deleteReq = DeleteReq.builder()
                    .collectionName(category)
                    .ids(ids.stream().map(s->(Object) s).collect(Collectors.toList()))
                    .build();
            DeleteResp deleteResp = clientV2.delete(deleteReq);
        }catch (Exception e){
        }

    }

    @Override
    public void deleteWhere(List<Map<String, String>> whereList) {
        deleteWhere(whereList, this.config.getDefaultCategory());
    }

    @Override
    public void deleteWhere(List<Map<String, String>> whereList, String category) {
        for (Map<String, String> map : whereList) {
            for (String key : map.keySet()) {
                String filter = "metadata['"+key+"'] == '"+map.get(key)+"'";
                DeleteReq deleteReq = DeleteReq.builder()
                        .collectionName(category)
                        .filter(filter)
                        .build();
                DeleteResp deleteResp = clientV2.delete(deleteReq);
            }
        }

    }

    @Override
    public void deleteCollection(String category) {
        try {
            DropCollectionReq dropCollectionRequest= DropCollectionReq.builder()
                    .collectionName(category)
                    .build();
            clientV2.dropCollection(dropCollectionRequest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public void createCollection(String CollectionName){
        boolean collectionExists = true;
        try {
            DescribeCollectionReq describeCollectionReq = DescribeCollectionReq.builder()
                    .collectionName(CollectionName)
                    .build();
            clientV2.describeCollection(describeCollectionReq);
        } catch (Exception e) {
            collectionExists = false;
        }
        if (!collectionExists){
            CreateCollectionReq quickSetupReq = CreateCollectionReq.builder()
                    .collectionName(CollectionName)
                    .dimension(1536)
                    .idType(DataType.VarChar)
                    .metricType("IP")
                    .build();
            clientV2.createCollection(quickSetupReq);
            GetLoadStateReq loadStateReq = GetLoadStateReq.builder()
                    .collectionName(CollectionName)
                    .build();
            boolean loadState = clientV2.getLoadState(loadStateReq);

        }

    }

    public static void main(String[] args) {
        ContextLoader.loadContext();
        VectorStoreService vectorStoreService = new VectorStoreService();
        List<IndexSearchData> search = vectorStoreService.search("办医师执业证书的整个流程包括哪些步骤", "medusa");
        search.stream().forEach(indexSearchData -> System.out.println(indexSearchData.getText()));

    }
}
