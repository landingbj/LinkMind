package ai.vector.impl;

import ai.common.pojo.VectorStoreConfig;
import ai.vector.VectorStore;
import ai.vector.pojo.*;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class BaseVectorStore implements VectorStore {
    protected VectorStoreConfig config;

    @Override
    public void upsert(List<UpsertRecord> upsertRecords) {
    }

    @Override
    public void upsert(List<UpsertRecord> upsertRecords, String category) {
    }

    @Override
    public List<IndexRecord> query(QueryCondition queryCondition) {
        return null;
    }

    @Override
    public List<IndexRecord> query(QueryCondition queryCondition, String category) {
        return null;
    }

    @Override
    public List<List<IndexRecord>> query(MultiQueryCondition queryCondition) {
        return null;
    }

    @Override
    public List<IndexRecord> fetch(List<String> ids) {
        return null;
    }

    @Override
    public List<IndexRecord> fetch(Map<String, String> where) {
        return null;
    }

    @Override
    public List<IndexRecord> fetch(Map<String, String> where, String category) {
        return null;
    }

    @Override
    public List<IndexRecord> fetch(List<String> ids, String category) {
        return null;
    }

    @Override
    public void delete(List<String> ids) {
    }

    @Override
    public void delete(List<String> ids, String category) {
    }

    @Override
    public void deleteWhere(List<Map<String, String>> where) {
    }

    @Override
    public void deleteWhere(List<Map<String, String>> whereList, String category) {
    }

    @Override
    public void deleteCollection(String category) {
    }

    @Override
    public List<VectorCollection> listCollections() {
        return null;
    }

    @Override
    public List<IndexRecord> fetch(int limit, int offset, String category) {
        return null;
    }

    @Override
    public List<IndexRecord> get(GetEmbedding getEmbedding) {
        return null;
    }

    @Override
    public void add(AddEmbedding addEmbedding) {
    }

    @Override
    public void update(UpdateEmbedding updateEmbedding) {
    }

    @Override
    public void delete(DeleteEmbedding deleteEmbedding) {
    }
}
