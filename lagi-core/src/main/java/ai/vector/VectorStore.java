package ai.vector;

import ai.vector.pojo.*;

import java.util.List;
import java.util.Map;

public interface VectorStore {
    void upsert(List<UpsertRecord> upsertRecords);

    void upsert(List<UpsertRecord> upsertRecords, String category);

    List<IndexRecord> query(QueryCondition queryCondition);

    List<List<IndexRecord>> query(MultiQueryCondition queryCondition);

    List<IndexRecord> query(QueryCondition queryCondition, String category);

    List<IndexRecord> fetch(List<String> ids);

    List<IndexRecord> fetch(List<String> ids, String category);

    List<IndexRecord> fetch(Map<String, String> where);

    List<IndexRecord> fetch(Map<String, String> where, String category);

    List<IndexRecord> fetch(int limit, int offset, String category);

    void delete(List<String> ids);

    void delete(List<String> ids, String category);

    void deleteWhere(List<Map<String, String>> where);

    void deleteWhere(List<Map<String, String>> whereList, String category);

    void deleteCollection(String category);

    List<VectorCollection> listCollections();

    List<IndexRecord> get(GetEmbedding getEmbedding);

    void add(AddEmbedding addEmbedding);

    void update(UpdateEmbedding updateEmbedding);

    void delete(DeleteEmbedding deleteEmbedding);
}
