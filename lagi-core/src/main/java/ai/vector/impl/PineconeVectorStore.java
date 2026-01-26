//package ai.vector.impl;
//
//import ai.embedding.Embeddings;
//import ai.common.pojo.VectorStoreConfig;
//import ai.vector.VectorStore;
//import ai.vector.pojo.QueryCondition;
//import ai.vector.pojo.IndexRecord;
//import ai.vector.pojo.UpsertRecord;
//import ai.vector.pojo.VectorCollection;
//import com.google.protobuf.Struct;
//import com.google.protobuf.Value;
//import io.pinecone.PineconeClient;
//import io.pinecone.PineconeClientConfig;
//import io.pinecone.PineconeConnection;
//import io.pinecone.PineconeConnectionConfig;
//import io.pinecone.exceptions.PineconeException;
//import io.pinecone.proto.*;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//public class PineconeVectorStore implements VectorStore {
//    private static final String DOCUMENT = "document";
//    private PineconeClientConfig pineconeClientConfig;
//    private PineconeClient pineconeClient;
//    private Embeddings embeddingFunction;
//    private VectorStoreConfig config;
//
//    public PineconeVectorStore(VectorStoreConfig config, Embeddings embeddingFunction) {
//        this.embeddingFunction = embeddingFunction;
//        this.config = config;
//        pineconeClientConfig = new PineconeClientConfig()
//                .withApiKey(config.getApiKey())
//                .withEnvironment(config.getEnvironment())
//                .withProjectName(config.getProjectName());
//        pineconeClient = new PineconeClient(pineconeClientConfig);
//    }
//
//    private Struct generateMetadataStruct(Map<String, String> metadata) {
//        Struct.Builder builder = Struct.newBuilder();
//        for (Map.Entry<String, String> entry : metadata.entrySet()) {
//            builder.putFields(entry.getKey(), Value.newBuilder().setStringValue(entry.getValue()).build());
//        }
//        return builder.build();
//    }
//
//    private Struct generateWhereStruct(Map<String, String> where) {
//        Struct.Builder builder = Struct.newBuilder();
//        for (Map.Entry<String, String> entry : where.entrySet()) {
//            builder.putFields(entry.getKey(), Value.newBuilder().setStringValue(entry.getValue()).build());
//        }
//        return builder.build();
//    }
//
//    public void upsert(List<UpsertRecord> upsertRecords) {
//        upsert(upsertRecords, this.config.getDefaultCategory());
//    }
//
//    public void upsert(List<UpsertRecord> upsertRecords, String category) {
//        List<String> documents = new ArrayList<>();
//        List<Map<String, String>> metadatas = new ArrayList<>();
//        List<String> ids = new ArrayList<>();
//        for (UpsertRecord upsertRecord : upsertRecords) {
//            documents.add(upsertRecord.getDocument());
//            Map<String, String> metadata = upsertRecord.getMetadata();
//            if (metadata == null) {
//                metadata = new HashMap<>();
//            }
//            metadata.put(DOCUMENT, upsertRecord.getDocument());
//            metadatas.add(metadata);
//            ids.add(upsertRecord.getId());
//        }
//        PineconeConnectionConfig connectionConfig = new PineconeConnectionConfig()
//                .withIndexName(this.config.getIndexName());
//        try (PineconeConnection connection = pineconeClient.connect(connectionConfig)) {
//            List<List<Float>> embeddings = embeddingFunction.createEmbedding(documents);
//            UpsertRequest.Builder builder = UpsertRequest.newBuilder()
//                    .setNamespace(category);
//            for (int i = 0; i < ids.size(); i++) {
//                Struct metadataStrut = generateMetadataStruct(metadatas.get(i));
//                Vector vector = Vector.newBuilder()
//                        .setId(ids.get(i))
//                        .addAllValues(embeddings.get(i))
//                        .setMetadata(metadataStrut)
//                        .build();
//                builder.addVectors(vector);
//            }
//            UpsertRequest upsertRequest = builder.build();
//            UpsertResponse upsertResponse = connection.getBlockingStub().upsert(upsertRequest);
//        } catch (PineconeException e) {
//            e.printStackTrace();
//        }
//    }
//
//    public List<IndexRecord> query(QueryCondition queryCondition) {
//        return query(queryCondition, this.config.getDefaultCategory());
//    }
//
//    public List<IndexRecord> query(QueryCondition queryCondition, String category) {
//        String text = queryCondition.getText();
//        Integer n = queryCondition.getN();
//        Map<String, String> where = queryCondition.getWhere();
//        Struct whereStruct = generateWhereStruct(where);
//        List<IndexRecord> result = new ArrayList<>();
//        PineconeConnectionConfig connectionConfig = new PineconeConnectionConfig()
//                .withIndexName(this.config.getIndexName());
//        try (PineconeConnection connection = pineconeClient.connect(connectionConfig)) {
//            Iterable<Float> iterable = embeddingFunction.createEmbedding(text);
//            QueryRequest queryRequest = QueryRequest.newBuilder()
//                    .addAllVector(iterable)
//                    .setNamespace(category)
//                    .setTopK(n)
//                    .setIncludeMetadata(true)
//                    .setFilter(whereStruct)
//                    .build();
//            result = query(connection, queryRequest);
//        } catch (PineconeException e) {
//            e.printStackTrace();
//        }
//        return result;
//    }
//
//    public List<IndexRecord> fetch(List<String> ids) {
//        return fetch(ids, this.config.getDefaultCategory());
//    }
//
//    public List<IndexRecord> fetch(List<String> ids, String category) {
//        List<IndexRecord> result = new ArrayList<>();
//        PineconeConnectionConfig connectionConfig = new PineconeConnectionConfig()
//                .withIndexName(this.config.getIndexName());
//        try (PineconeConnection connection = pineconeClient.connect(connectionConfig)) {
//            for (String id : ids) {
//                QueryRequest queryRequest = QueryRequest.newBuilder()
//                        .setId(id)
//                        .setNamespace(category)
//                        .setIncludeMetadata(true)
//                        .setTopK(1)
//                        .build();
//                result.addAll(query(connection, queryRequest));
//            }
//        } catch (PineconeException e) {
//            e.printStackTrace();
//        }
//        return result;
//    }
//
//    public List<IndexRecord> fetch(Map<String, String> where) {
//        // TODO: Implement this method
//        return null;
//    }
//
//    public List<IndexRecord> fetch(Map<String, String> where, String category) {
//        // TODO: Implement this method
//        return null;
//    }
//
//    @Override
//    public void delete(List<String> ids) {
//        this.delete(ids, this.config.getDefaultCategory());
//    }
//
//    @Override
//    public void delete(List<String> ids, String category) {
//        PineconeConnectionConfig connectionConfig = new PineconeConnectionConfig()
//                .withIndexName(this.config.getIndexName());
//        try (PineconeConnection connection = pineconeClient.connect(connectionConfig)) {
//            DeleteRequest deleteRequest = DeleteRequest.newBuilder()
//                    .setNamespace(category)
//                    .addAllIds(ids)
//                    .build();
//            connection.getBlockingStub().delete(deleteRequest);
//        } catch (PineconeException e) {
//            e.printStackTrace();
//        }
//    }
//
//    @Override
//    public void deleteWhere(List<Map<String, String>> where) {
//        deleteWhere(where, this.config.getDefaultCategory());
//    }
//
//    @Override
//    public void deleteWhere(List<Map<String, String>> whereList, String category) {
//        PineconeConnectionConfig connectionConfig = new PineconeConnectionConfig()
//                .withIndexName(this.config.getIndexName());
//        try (PineconeConnection connection = pineconeClient.connect(connectionConfig)) {
//            for (Map<String, String> where : whereList) {
//                Struct whereStruct = generateWhereStruct(where);
//                DeleteRequest deleteRequest = DeleteRequest.newBuilder()
//                        .setNamespace(category)
//                        .setFilter(whereStruct)
//                        .build();
//                connection.getBlockingStub().delete(deleteRequest);
//            }
//        } catch (PineconeException e) {
//            e.printStackTrace();
//        }
//    }
//
//    private List<IndexRecord> query(PineconeConnection connection, QueryRequest queryRequest) {
//        List<IndexRecord> result = new ArrayList<>();
//        QueryResponse queryResponse = connection.getBlockingStub().query(queryRequest);
//        for (int i = 0; i < queryResponse.getMatchesCount(); i++) {
//            Map<String, Value> fieldsMap = queryResponse.getMatches(i).getMetadata().getFieldsMap();
//            String document = fieldsMap.get(DOCUMENT).getStringValue();
//            String id = queryResponse.getMatches(i).getId();
//            Map<String, Object> metadata = new HashMap<>();
//            for (Map.Entry<String, Value> entry : fieldsMap.entrySet()) {
//                if (entry.getKey().equals(DOCUMENT)) {
//                    continue;
//                }
//                metadata.put(entry.getKey(), entry.getValue().getStringValue());
//            }
//            Float distance = queryResponse.getMatches(i).getScore();
//
//            IndexRecord indexRecord = IndexRecord.newBuilder()
//                    .withDocument(document)
//                    .withId(id)
//                    .withMetadata(metadata)
//                    .withDistance(distance)
//                    .build();
//            result.add(indexRecord);
//        }
//        return result;
//    }
//
//    @Override
//    public void deleteCollection(String category) {
//        PineconeConnectionConfig connectionConfig = new PineconeConnectionConfig()
//                .withIndexName(this.config.getIndexName());
//        try (PineconeConnection connection = pineconeClient.connect(connectionConfig)) {
//            DeleteRequest deleteRequest = DeleteRequest.newBuilder()
//                    .setNamespace(category)
//                    .setDeleteAll(true)
//                    .build();
//            connection.getBlockingStub().delete(deleteRequest);
//        } catch (PineconeException e) {
//            e.printStackTrace();
//        }
//    }
//
//    @Override
//    public List<VectorCollection> listCollections() {
//        List<VectorCollection> result = new ArrayList<>();
//        PineconeConnectionConfig connectionConfig = new PineconeConnectionConfig()
//                .withIndexName(this.config.getIndexName());
//        try (PineconeConnection connection = pineconeClient.connect(connectionConfig)) {
//            DescribeIndexStatsResponse describeIndexStatsResponse = connection.getBlockingStub().describeIndexStats(DescribeIndexStatsRequest.newBuilder().build());
//            for (Map.Entry<String, NamespaceSummary> entry : describeIndexStatsResponse.getNamespacesMap().entrySet()) {
//                if (entry.getKey().isEmpty()) {
//                    continue;
//                }
//                int count = entry.getValue().getVectorCount();
//                VectorCollection vectorCollection = VectorCollection.builder()
//                        .category(entry.getKey())
//                        .vectorCount(count)
//                        .build();
//                result.add(vectorCollection);
//            }
//        } catch (PineconeException e) {
//            e.printStackTrace();
//        }
//        return result;
//    }
//}
