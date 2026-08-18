package ai.vector.impl;

import ai.common.db.Conn;
import ai.common.db.SqliteRetry;
import ai.common.pojo.VectorStoreConfig;
import ai.embedding.Embeddings;
import ai.utils.AiGlobal;
import ai.vector.pojo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class SqliteVectorStore extends BaseVectorStore {
    private static final Logger logger = LoggerFactory.getLogger(SqliteVectorStore.class);

    private static final String SQLITE_VEC_RESOURCE_DIR = "sqlite/sqlite-vec";

    private final Embeddings embeddingFunction;
    private final String connName;
    private final String extensionPath;
    private final Map<Connection, Boolean> loadedConnections = Collections.synchronizedMap(new WeakHashMap<>());

    public SqliteVectorStore(VectorStoreConfig config, Embeddings embeddingFunction) {
        this.config = config;
        this.embeddingFunction = embeddingFunction;
        this.connName = AiGlobal.DEFAULT_DB;
        this.extensionPath = resolveSqliteVecExtensionPath();
    }

    /**
     * Returns the relative path under sqlite-vec for the current OS and arch.
     * Structure: {os}/{arch}/vec0.{ext}, e.g. windows/x86_64/vec0.dll, linux/aarch64/vec0.so.
     */
    private static String getSqliteVecResourcePath() {
        String os = getNormalizedOs();
        String arch = getNormalizedArch();
        String ext = os.equals("windows") ? ".dll" : (os.equals("macos") ? ".dylib" : ".so");
        return os + "/" + arch + "/vec0" + ext;
    }

    private static String getNormalizedOs() {
        String name = System.getProperty("os.name", "").toLowerCase();
        if (name.contains("win")) {
            return "windows";
        }
        if (name.contains("mac")) {
            return "macos";
        }
        return "linux";
    }

    private static String getNormalizedArch() {
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (arch.equals("amd64") || arch.equals("x86_64")) {
            return "x86_64";
        }
        if (arch.equals("aarch64") || arch.equals("arm64")) {
            return "aarch64";
        }
        return "x86_64";
    }

    /**
     * Resolves the sqlite-vec extension to a file path, loading from classpath
     * (sqlite/sqlite-vec/{os}/{arch}/vec0.{ext}). Extracts to a temp file when loaded from a JAR.
     */
    private static String resolveSqliteVecExtensionPath() {
        String relativePath = getSqliteVecResourcePath();
        String resourcePath = SQLITE_VEC_RESOURCE_DIR + "/" + relativePath;
        String simpleFilename = "vec0" + relativePath.substring(relativePath.lastIndexOf('.'));
        ClassLoader cl = SqliteVectorStore.class.getClassLoader();
        if (cl == null) {
            cl = ClassLoader.getSystemClassLoader();
        }
        java.net.URL url = cl.getResource(resourcePath);
        if (url == null && Thread.currentThread().getContextClassLoader() != null) {
            ClassLoader ctx = Thread.currentThread().getContextClassLoader();
            url = ctx.getResource(resourcePath);
            if (url != null) {
                cl = ctx;
            }
        }
        if (url == null) {
            logger.warn("sqlite-vec extension not found: {} (os={}, arch={}). Ensure {} is on the classpath.",
                    resourcePath, getNormalizedOs(), getNormalizedArch(), SQLITE_VEC_RESOURCE_DIR);
            return "vec0";
        }
        try {
            if ("file".equals(url.getProtocol())) {
                String path = java.net.URLDecoder.decode(url.getPath(), "UTF-8");
                if (getNormalizedOs().equals("windows") && path.length() > 2 && path.startsWith("/") && path.charAt(2) == ':') {
                    path = path.substring(1);
                }
                return Paths.get(path).normalize().toAbsolutePath().toString();
            }
            Path tempDir = Files.createTempDirectory("sqlite-vec");
            tempDir.toFile().deleteOnExit();
            Path temp = tempDir.resolve(simpleFilename);
            try (InputStream in = cl.getResourceAsStream(resourcePath)) {
                if (in != null) {
                    Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    temp.toFile().deleteOnExit();
                    return temp.toAbsolutePath().normalize().toString();
                }
            }
        } catch (Exception e) {
            logger.error("Failed to resolve sqlite-vec extension path for {}: {}", resourcePath, e.getMessage());
        }
        return "vec0";
    }

    private static String escapeExtensionPathForSql(String path) {
        if (path == null) return "";
        return path.replace("'", "''");
    }

    // ==================== Connection & Extension ====================

    private Conn getConn() {
        Conn conn = new Conn(connName);
        loadExtension(conn);
        enableForeignKeys(conn);
        return conn;
    }

    private void loadExtension(Conn conn) {
        try {
            Connection rawConn = conn.unwrap(Connection.class);
            if (loadedConnections.containsKey(rawConn)) {
                return;
            }
            String pathForSql = escapeExtensionPathForSql(extensionPath);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT load_extension('" + pathForSql + "')");
            }
            loadedConnections.put(rawConn, Boolean.TRUE);
        } catch (SQLException e) {
            logger.error("Failed to load extension (path={}): {}", extensionPath, e.getMessage());
        }
    }

    private void enableForeignKeys(Conn conn) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException e) {
            logger.debug("Failed to enable foreign keys: {}", e.getMessage());
        }
    }

    // ==================== Table Naming ====================

    private String vecTableName(String category) {
        return "vec_" + category.replaceAll("[^a-zA-Z0-9_$]", "_");
    }

    private String metadataTableName(String category) {
        return vecTableName(category) + "_metadatas";
    }

    // ==================== Collection Management ====================

    private boolean collectionExists(Conn conn, String vecTable) throws SQLException {
        try (ResultSet rs = conn.executeQuery(
                "SELECT 1 FROM sqlite_master WHERE name='" + vecTable.replace("'", "''") + "'")) {
            return rs.next();
        }
    }

    @FunctionalInterface
    private interface TransactionAction {
        void run(Conn conn) throws SQLException;
    }

    private void runWriteTransaction(TransactionAction action) {
        try {
            SqliteRetry.run(() -> {
                Conn conn = getConn();
                try {
                    conn.setAutoCommit(false);
                    action.run(conn);
                    conn.commit();
                } catch (SQLException e) {
                    try {
                        conn.rollback();
                    } catch (SQLException ex) {
                        logger.error("Rollback failed", ex);
                    }
                    throw e;
                } finally {
                    conn.close();
                }
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void ensureCollection(Conn conn, String category, int dimension) throws SQLException {
        String vecTable = vecTableName(category);
        if (collectionExists(conn, vecTable)) return;

        String metaTable = metadataTableName(category);
        String metric = config.getMetric() != null ? config.getMetric() : "cosine";

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE VIRTUAL TABLE " + vecTable + " USING vec0(" +
                    "id TEXT PRIMARY KEY, " +
                    "embeddings float[" + dimension + "] distance_metric=" + metric + ", " +
                    "document TEXT)");
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS " + metaTable + " (" +
                    "id TEXT NOT NULL, " +
                    "key TEXT NOT NULL, " +
                    "value TEXT, " +
                    "PRIMARY KEY (id, key), " +
                    "FOREIGN KEY (id) REFERENCES " + vecTable + "_rowids(id) ON DELETE CASCADE)");
        }
        ensureMetadataLookupIndex(conn, metaTable);
    }

    // ==================== Byte Conversion ====================

    private byte[] floatListToBlob(List<Float> floats) {
        ByteBuffer buffer = ByteBuffer.allocate(floats.size() * 4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (float f : floats) {
            buffer.putFloat(f);
        }
        return buffer.array();
    }

    private List<Float> blobToFloatList(byte[] blob) {
        ByteBuffer buffer = ByteBuffer.wrap(blob);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        List<Float> result = new ArrayList<>();
        while (buffer.hasRemaining()) {
            result.add(buffer.getFloat());
        }
        return result;
    }

    // ==================== Metadata Operations ====================

    private Map<String, Object> fetchMetadata(Conn conn, String metaTable, String id) throws SQLException {
        Map<String, Object> metadata = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT key, value FROM " + metaTable + " WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    metadata.put(rs.getString("key"), rs.getString("value"));
                }
            }
        }
        return metadata;
    }

    private void insertMetadata(Conn conn, String metaTable, String id, Map<String, ?> metadata) throws SQLException {
        if (metadata == null || metadata.isEmpty()) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO " + metaTable + "(id, key, value) VALUES (?, ?, ?)")) {
            for (Map.Entry<String, ?> entry : metadata.entrySet()) {
                ps.setString(1, id);
                ps.setString(2, entry.getKey());
                ps.setString(3, entry.getValue() != null ? String.valueOf(entry.getValue()) : null);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void ensureMetadataLookupIndex(Conn conn, String metaTable) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE INDEX IF NOT EXISTS " + metaTable + "_key_value_idx ON " +
                    metaTable + "(key, value, id)");
        }
    }

    private void deleteMetadata(Conn conn, String metaTable, String id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM " + metaTable + " WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    // ==================== Where Condition Matching ====================

    @SuppressWarnings("unchecked")
    private boolean matchesWhere(Map<String, Object> where, Map<String, Object> metadata) {
        if (where == null || where.isEmpty()) return true;
        if (metadata == null) metadata = Collections.emptyMap();

        for (Map.Entry<String, Object> entry : where.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if ("$and".equals(key)) {
                List<Map<String, Object>> conditions = (List<Map<String, Object>>) value;
                for (Map<String, Object> condition : conditions) {
                    if (!matchesWhere(condition, metadata)) return false;
                }
            } else if ("$or".equals(key)) {
                List<Map<String, Object>> conditions = (List<Map<String, Object>>) value;
                boolean anyMatch = false;
                for (Map<String, Object> condition : conditions) {
                    if (matchesWhere(condition, metadata)) {
                        anyMatch = true;
                        break;
                    }
                }
                if (!anyMatch) return false;
            } else {
                String metaValue = metadata.get(key) != null ? String.valueOf(metadata.get(key)) : null;
                if (value instanceof Map) {
                    Map<String, Object> ops = (Map<String, Object>) value;
                    for (Map.Entry<String, Object> op : ops.entrySet()) {
                        if (!applyOperator(op.getKey(), metaValue, op.getValue())) return false;
                    }
                } else {
                    String expected = value != null ? String.valueOf(value) : null;
                    if (!Objects.equals(expected, metaValue)) return false;
                }
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean applyOperator(String operator, String actual, Object expected) {
        if (actual == null && !"$ne".equals(operator)) return false;
        String expectedStr = expected != null ? String.valueOf(expected) : null;

        switch (operator) {
            case "$eq":
                return Objects.equals(actual, expectedStr);
            case "$ne":
                return !Objects.equals(actual, expectedStr);
            case "$gt":
            case "$gte":
            case "$lt":
            case "$lte":
                return compareValues(operator, actual, expectedStr);
            case "$in":
                if (expected instanceof List) {
                    return ((List<Object>) expected).stream()
                            .anyMatch(e -> Objects.equals(actual, String.valueOf(e)));
                }
                return false;
            case "$nin":
                if (expected instanceof List) {
                    return ((List<Object>) expected).stream()
                            .noneMatch(e -> Objects.equals(actual, String.valueOf(e)));
                }
                return true;
            default:
                return true;
        }
    }

    private boolean compareValues(String operator, String actual, String expected) {
        if (actual == null || expected == null) return false;
        try {
            double a = Double.parseDouble(actual);
            double e = Double.parseDouble(expected);
            switch (operator) {
                case "$gt":  return a > e;
                case "$gte": return a >= e;
                case "$lt":  return a < e;
                case "$lte": return a <= e;
                default:     return false;
            }
        } catch (NumberFormatException ex) {
            int cmp = actual.compareTo(expected);
            switch (operator) {
                case "$gt":  return cmp > 0;
                case "$gte": return cmp >= 0;
                case "$lt":  return cmp < 0;
                case "$lte": return cmp <= 0;
                default:     return false;
            }
        }
    }

    private boolean matchesWhereDocument(Map<String, Object> whereDocument, String document) {
        if (whereDocument == null || whereDocument.isEmpty()) return true;
        if (document == null) document = "";
        for (Map.Entry<String, Object> entry : whereDocument.entrySet()) {
            String value = String.valueOf(entry.getValue());
            if ("$contains".equals(entry.getKey())) {
                if (!document.contains(value)) return false;
            } else if ("$not_contains".equals(entry.getKey())) {
                if (document.contains(value)) return false;
            }
        }
        return true;
    }

    /**
     * Find record IDs matching all key-value pairs via INTERSECT.
     * Returns null when no filter is applied (as opposed to empty list meaning no matches).
     */
    private List<String> findIdsByMetadata(Conn conn, String metaTable, Map<String, String> where) throws SQLException {
        if (where == null || where.isEmpty()) return null;

        StringBuilder sql = new StringBuilder();
        List<String> params = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, String> entry : where.entrySet()) {
            if (i > 0) sql.append(" INTERSECT ");
            sql.append("SELECT id FROM ").append(metaTable).append(" WHERE key = ? AND value = ?");
            params.add(entry.getKey());
            params.add(entry.getValue());
            i++;
        }

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int j = 0; j < params.size(); j++) {
                ps.setString(j + 1, params.get(j));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<String> ids = new ArrayList<>();
                while (rs.next()) ids.add(rs.getString("id"));
                return ids;
            }
        }
    }

    /**
     * Converts the common $and metadata filter shape into indexed SQL lookups.
     * Returns null when a condition needs the generic in-memory matcher.
     */
    @SuppressWarnings("unchecked")
    private List<String> findIdsByMetadataWhere(Conn conn, String metaTable, Map<String, Object> where)
            throws SQLException {
        if (where == null || where.size() != 1 || !(where.get("$and") instanceof List)) {
            return null;
        }
        List<Map<String, Object>> conditions = (List<Map<String, Object>>) where.get("$and");
        if (conditions.isEmpty()) {
            return null;
        }

        StringBuilder sql = new StringBuilder();
        List<Object> parameters = new ArrayList<>();
        for (int index = 0; index < conditions.size(); index++) {
            Map<String, Object> condition = conditions.get(index);
            if (condition == null || condition.size() != 1) {
                return null;
            }
            Map.Entry<String, Object> entry = condition.entrySet().iterator().next();
            String key = entry.getKey();
            Object value = entry.getValue();
            List<?> values;
            if (value instanceof Map) {
                Map<String, Object> operators = (Map<String, Object>) value;
                if (operators.size() != 1 || !(operators.get("$in") instanceof List)) {
                    return null;
                }
                values = (List<?>) operators.get("$in");
            } else {
                values = Collections.singletonList(value);
            }
            if (values.isEmpty()) {
                return Collections.emptyList();
            }
            if (index > 0) {
                sql.append(" INTERSECT ");
            }
            sql.append("SELECT id FROM ").append(metaTable).append(" WHERE key = ? AND value IN (");
            sql.append(values.stream().map(item -> "?").collect(Collectors.joining(",")));
            sql.append(")");
            parameters.add(key);
            parameters.addAll(values);
        }

        List<String> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int index = 0; index < parameters.size(); index++) {
                ps.setString(index + 1, String.valueOf(parameters.get(index)));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getString("id"));
                }
            }
        }
        return ids;
    }

    private List<IndexRecord> fetchRecordsByIds(Conn conn, String vecTable, String metaTable, List<String> ids) throws SQLException {
        if (ids.isEmpty()) return new ArrayList<>();
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT id, document FROM " + vecTable + " WHERE id IN (" + placeholders + ")";
        List<IndexRecord> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setString(i + 1, ids.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    results.add(IndexRecord.builder()
                            .id(id)
                            .document(rs.getString("document"))
                            .metadata(fetchMetadata(conn, metaTable, id))
                            .build());
                }
            }
        }
        return results;
    }

    private IndexRecord buildIndexRecord(ResultSet rs, Conn conn, String metaTable,
                                         boolean includeEmbeddings) throws SQLException {
        String id = rs.getString("id");
        IndexRecord.IndexRecordBuilder builder = IndexRecord.builder()
                .id(id)
                .document(rs.getString("document"))
                .metadata(fetchMetadata(conn, metaTable, id));
        if (includeEmbeddings) {
            byte[] blob = rs.getBytes("embeddings");
            if (blob != null) builder.embeddings(blobToFloatList(blob));
        }
        return builder.build();
    }

    private List<IndexRecord> applyPagination(List<IndexRecord> records, Integer limit, Integer offset) {
        int off = offset != null ? offset : 0;
        int lim = limit != null ? limit : records.size();
        if (off >= records.size()) return new ArrayList<>();
        return new ArrayList<>(records.subList(off, Math.min(off + lim, records.size())));
    }

    // ==================== VectorStore Interface ====================

    @Override
    public void upsert(List<UpsertRecord> upsertRecords) {
        upsert(upsertRecords, config.getDefaultCategory());
    }

    @Override
    public void upsert(List<UpsertRecord> upsertRecords, String category) {
        if (upsertRecords == null || upsertRecords.isEmpty()) return;

        List<String> documents = upsertRecords.stream()
                .map(UpsertRecord::getDocument)
                .collect(Collectors.toList());
        List<List<Float>> embeddings = embeddingFunction.createEmbedding(documents);

        runWriteTransaction(conn -> {
            ensureCollection(conn, category, embeddings.get(0).size());

            String vecTable = vecTableName(category);
            String metaTable = metadataTableName(category);

            for (int i = 0; i < upsertRecords.size(); i++) {
                UpsertRecord record = upsertRecords.get(i);
                String id = record.getId();

                deleteMetadata(conn, metaTable, id);
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM " + vecTable + " WHERE id = ?")) {
                    ps.setString(1, id);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO " + vecTable + "(id, embeddings, document) VALUES (?, ?, ?)")) {
                    ps.setString(1, id);
                    ps.setBytes(2, floatListToBlob(embeddings.get(i)));
                    ps.setString(3, record.getDocument());
                    ps.executeUpdate();
                }

                insertMetadata(conn, metaTable, id, record.getMetadata());
            }
        });
    }

    @Override
    public List<IndexRecord> query(QueryCondition queryCondition) {
        String category = queryCondition.getCategory() != null
                ? queryCondition.getCategory() : config.getDefaultCategory();
        int n = queryCondition.getN() != null ? queryCondition.getN() : 10;

        if (queryCondition.getText() == null || queryCondition.getText().isEmpty()) {
            return get(GetEmbedding.builder()
                    .category(category)
                    .where(queryCondition.getWhere())
                    .whereDocument(queryCondition.getWhereDocument())
                    .limit(n)
                    .offset(0)
                    .build());
        }

        List<Float> queryEmbedding = embeddingFunction.createEmbedding(queryCondition.getText());
        boolean hasFilter = hasWhereFilter(queryCondition.getWhere())
                || hasWhereFilter(queryCondition.getWhereDocument());
        int fetchN = hasFilter ? n * 5 : n;

        Conn conn = getConn();
        try {
            String vecTable = vecTableName(category);
            String metaTable = metadataTableName(category);
            if (!collectionExists(conn, vecTable)) return new ArrayList<>();

            String sql = "SELECT id, document, distance FROM " + vecTable +
                    " WHERE embeddings MATCH ? AND k = ? ORDER BY distance";
            List<IndexRecord> results = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setBytes(1, floatListToBlob(queryEmbedding));
                ps.setInt(2, fetchN);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next() && results.size() < n) {
                        String id = rs.getString("id");
                        String document = rs.getString("document");
                        float distance = rs.getFloat("distance");
                        Map<String, Object> metadata = fetchMetadata(conn, metaTable, id);

                        if (matchesWhere(queryCondition.getWhere(), metadata) &&
                                matchesWhereDocument(queryCondition.getWhereDocument(), document)) {
                            results.add(IndexRecord.builder()
                                    .id(id)
                                    .document(document)
                                    .distance(distance)
                                    .metadata(metadata)
                                    .build());
                        }
                    }
                }
            }
            return results;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            conn.close();
        }
    }

    @Override
    public List<IndexRecord> query(QueryCondition queryCondition, String category) {
        return query(QueryCondition.builder()
                .category(category)
                .text(queryCondition.getText())
                .where(queryCondition.getWhere())
                .whereDocument(queryCondition.getWhereDocument())
                .n(queryCondition.getN())
                .build());
    }

    @Override
    public List<List<IndexRecord>> query(MultiQueryCondition queryCondition) {
        List<List<IndexRecord>> resultList = new ArrayList<>();
        String category = queryCondition.getCategory() != null
                ? queryCondition.getCategory() : config.getDefaultCategory();

        if (queryCondition.getTexts() == null || queryCondition.getTexts().isEmpty()) {
            resultList.add(get(GetEmbedding.builder()
                    .category(category)
                    .where(queryCondition.getWhere())
                    .whereDocument(queryCondition.getWhereDocument())
                    .limit(queryCondition.getN())
                    .offset(0)
                    .build()));
            return resultList;
        }

        for (String text : queryCondition.getTexts()) {
            resultList.add(query(QueryCondition.builder()
                    .category(category)
                    .text(text)
                    .where(queryCondition.getWhere())
                    .whereDocument(queryCondition.getWhereDocument())
                    .n(queryCondition.getN())
                    .build()));
        }
        return resultList;
    }

    @Override
    public List<IndexRecord> fetch(List<String> ids) {
        return fetch(ids, config.getDefaultCategory());
    }

    @Override
    public List<IndexRecord> fetch(List<String> ids, String category) {
        if (ids == null || ids.isEmpty()) return new ArrayList<>();
        Conn conn = getConn();
        try {
            String vecTable = vecTableName(category);
            String metaTable = metadataTableName(category);
            if (!collectionExists(conn, vecTable)) return new ArrayList<>();
            return fetchRecordsByIds(conn, vecTable, metaTable, ids);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            conn.close();
        }
    }

    @Override
    public List<IndexRecord> fetch(int limit, int offset, String category) {
        Conn conn = getConn();
        try {
            String vecTable = vecTableName(category);
            String metaTable = metadataTableName(category);
            if (!collectionExists(conn, vecTable)) return new ArrayList<>();

            String sql = "SELECT id, document FROM " + vecTable + " LIMIT ? OFFSET ?";
            List<IndexRecord> results = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, limit);
                ps.setInt(2, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String id = rs.getString("id");
                        results.add(IndexRecord.builder()
                                .id(id)
                                .document(rs.getString("document"))
                                .metadata(fetchMetadata(conn, metaTable, id))
                                .build());
                    }
                }
            }
            return results;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            conn.close();
        }
    }

    @Override
    public List<IndexRecord> fetch(Map<String, String> where) {
        return fetch(where, config.getDefaultCategory());
    }

    @Override
    public List<IndexRecord> fetch(Map<String, String> where, String category) {
        if (where == null || where.isEmpty()) return new ArrayList<>();
        Conn conn = getConn();
        try {
            String vecTable = vecTableName(category);
            String metaTable = metadataTableName(category);
            if (!collectionExists(conn, vecTable)) return new ArrayList<>();

            List<String> matchedIds = findIdsByMetadata(conn, metaTable, where);
            if (matchedIds == null || matchedIds.isEmpty()) return new ArrayList<>();
            return fetchRecordsByIds(conn, vecTable, metaTable, matchedIds);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            conn.close();
        }
    }

    @Override
    public void delete(List<String> ids) {
        delete(ids, config.getDefaultCategory());
    }

    @Override
    public void delete(List<String> ids, String category) {
        if (ids == null || ids.isEmpty()) return;
        runWriteTransaction(conn -> {
            String vecTable = vecTableName(category);
            String metaTable = metadataTableName(category);
            if (!collectionExists(conn, vecTable)) return;

            for (String id : ids) {
                deleteMetadata(conn, metaTable, id);
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM " + vecTable + " WHERE id = ?")) {
                    ps.setString(1, id);
                    ps.executeUpdate();
                }
            }
        });
    }

    @Override
    public void deleteWhere(List<Map<String, String>> whereList) {
        deleteWhere(whereList, config.getDefaultCategory());
    }

    @Override
    public void deleteWhere(List<Map<String, String>> whereList, String category) {
        if (whereList == null || whereList.isEmpty()) return;
        runWriteTransaction(conn -> {
            String vecTable = vecTableName(category);
            String metaTable = metadataTableName(category);
            if (!collectionExists(conn, vecTable)) return;

            for (Map<String, String> where : whereList) {
                List<String> ids = findIdsByMetadata(conn, metaTable, where);
                if (ids != null && !ids.isEmpty()) {
                    for (String id : ids) {
                        deleteMetadata(conn, metaTable, id);
                        try (PreparedStatement ps = conn.prepareStatement(
                                "DELETE FROM " + vecTable + " WHERE id = ?")) {
                            ps.setString(1, id);
                            ps.executeUpdate();
                        }
                    }
                }
            }
        });
    }

    @Override
    public void deleteCollection(String category) {
        runWriteTransaction(conn -> {
            String vecTable = vecTableName(category);
            String metaTable = metadataTableName(category);
            if (!collectionExists(conn, vecTable)) return;

            conn.executeUpdate("DROP TABLE IF EXISTS " + metaTable);
            conn.executeUpdate("DROP TABLE IF EXISTS " + vecTable);
        });
    }

    @Override
    public List<VectorCollection> listCollections() {
        Conn conn = getConn();
        try {
            List<VectorCollection> result = new ArrayList<>();
            try (ResultSet rs = conn.executeQuery(
                    "SELECT name, sql FROM sqlite_master WHERE type='table' AND name LIKE 'vec_%' AND sql LIKE '%vec0%'")) {
                while (rs.next()) {
                    String tableName = rs.getString("name");
                    String category = tableName.substring(4);
                    int count = 0;
                    try (ResultSet countRs = conn.executeQuery("SELECT count(*) FROM " + tableName)) {
                        if (countRs.next()) count = countRs.getInt(1);
                    }
                    result.add(VectorCollection.builder()
                            .category(category)
                            .vectorCount(count)
                            .build());
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            conn.close();
        }
    }

    @Override
    public List<IndexRecord> get(GetEmbedding getEmbedding) {
        String category = getEmbedding.getCategory() != null
                ? getEmbedding.getCategory() : config.getDefaultCategory();
        Conn conn = getConn();
        try {
            String vecTable = vecTableName(category);
            String metaTable = metadataTableName(category);
            if (!collectionExists(conn, vecTable)) return new ArrayList<>();

            boolean includeEmbeddings = getEmbedding.getInclude() != null &&
                    getEmbedding.getInclude().contains("embeddings");
            String selectCols = includeEmbeddings ? "id, document, embeddings" : "id, document";

            boolean hasFilter = hasWhereFilter(getEmbedding.getWhere())
                    || hasWhereFilter(getEmbedding.getWhereDocument());

            if ((getEmbedding.getIds() == null || getEmbedding.getIds().isEmpty()) &&
                    hasWhereFilter(getEmbedding.getWhere())) {
                ensureMetadataLookupIndex(conn, metaTable);
                List<String> matchedIds = findIdsByMetadataWhere(conn, metaTable, getEmbedding.getWhere());
                if (matchedIds != null) {
                    List<IndexRecord> records = fetchRecordsByIds(conn, vecTable, metaTable, matchedIds);
                    records = records.stream()
                            .filter(record -> matchesWhere(getEmbedding.getWhere(), record.getMetadata()) &&
                                    matchesWhereDocument(getEmbedding.getWhereDocument(), record.getDocument()))
                            .collect(Collectors.toList());
                    return applyPagination(records, getEmbedding.getLimit(), getEmbedding.getOffset());
                }
            }

            if (getEmbedding.getIds() != null && !getEmbedding.getIds().isEmpty()) {
                String placeholders = getEmbedding.getIds().stream()
                        .map(id -> "?").collect(Collectors.joining(","));
                String sql = "SELECT " + selectCols + " FROM " + vecTable +
                        " WHERE id IN (" + placeholders + ")";
                List<IndexRecord> results = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    for (int i = 0; i < getEmbedding.getIds().size(); i++) {
                        ps.setString(i + 1, getEmbedding.getIds().get(i));
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            IndexRecord record = buildIndexRecord(rs, conn, metaTable, includeEmbeddings);
                            if (matchesWhere(getEmbedding.getWhere(), record.getMetadata()) &&
                                    matchesWhereDocument(getEmbedding.getWhereDocument(), record.getDocument())) {
                                results.add(record);
                            }
                        }
                    }
                }
                return applyPagination(results, getEmbedding.getLimit(), getEmbedding.getOffset());
            }

            String sql;
            if (hasFilter) {
                sql = "SELECT " + selectCols + " FROM " + vecTable;
            } else {
                sql = "SELECT " + selectCols + " FROM " + vecTable + " LIMIT ? OFFSET ?";
            }

            List<IndexRecord> results = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                if (!hasFilter) {
                    ps.setInt(1, getEmbedding.getLimit() != null ? getEmbedding.getLimit() : Integer.MAX_VALUE);
                    ps.setInt(2, getEmbedding.getOffset() != null ? getEmbedding.getOffset() : 0);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        IndexRecord record = buildIndexRecord(rs, conn, metaTable, includeEmbeddings);
                        if (matchesWhere(getEmbedding.getWhere(), record.getMetadata()) &&
                                matchesWhereDocument(getEmbedding.getWhereDocument(), record.getDocument())) {
                            results.add(record);
                        }
                    }
                }
            }

            if (hasFilter) {
                return applyPagination(results, getEmbedding.getLimit(), getEmbedding.getOffset());
            }
            return results;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            conn.close();
        }
    }

    @Override
    public void add(AddEmbedding addEmbedding) {
        if (addEmbedding == null || addEmbedding.getData() == null || addEmbedding.getData().isEmpty()) return;
        String category = addEmbedding.getCategory() != null
                ? addEmbedding.getCategory() : config.getDefaultCategory();

        runWriteTransaction(conn -> {

            for (AddEmbedding.AddEmbeddingData data : addEmbedding.getData()) {
                if (data.getId() == null || data.getId().isEmpty()) {
                    data.setId(UUID.randomUUID().toString().replace("-", ""));
                }
                if (data.getEmbedding() == null || data.getEmbedding().isEmpty()) {
                    List<List<Float>> embs = embeddingFunction.createEmbedding(
                            Collections.singletonList(data.getDocument()));
                    data.setEmbedding(embs.get(0));
                }
            }

            int dimension = addEmbedding.getData().get(0).getEmbedding().size();
            ensureCollection(conn, category, dimension);

            String vecTable = vecTableName(category);
            String metaTable = metadataTableName(category);

            for (AddEmbedding.AddEmbeddingData data : addEmbedding.getData()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO " + vecTable + "(id, embeddings, document) VALUES (?, ?, ?)")) {
                    ps.setString(1, data.getId());
                    ps.setBytes(2, floatListToBlob(data.getEmbedding()));
                    ps.setString(3, data.getDocument());
                    ps.executeUpdate();
                }
                insertMetadata(conn, metaTable, data.getId(), data.getMetadata());
            }
        });
    }

    @Override
    public void update(UpdateEmbedding updateEmbedding) {
        if (updateEmbedding == null || updateEmbedding.getData() == null || updateEmbedding.getData().isEmpty()) return;
        String category = updateEmbedding.getCategory() != null
                ? updateEmbedding.getCategory() : config.getDefaultCategory();

        runWriteTransaction(conn -> {
            String vecTable = vecTableName(category);
            String metaTable = metadataTableName(category);
            if (!collectionExists(conn, vecTable)) return;

            for (UpdateEmbedding.UpdateEmbeddingData data : updateEmbedding.getData()) {
                List<Float> embedding = data.getEmbedding();
                if (data.getDocument() != null && !data.getDocument().trim().isEmpty()
                        && (embedding == null || embedding.isEmpty())) {
                    List<List<Float>> embs = embeddingFunction.createEmbedding(
                            Collections.singletonList(data.getDocument()));
                    if (embs != null && !embs.isEmpty()) embedding = embs.get(0);
                }

                // vec0 does not support UPDATE directly; fetch, delete, and re-insert
                IndexRecord existing = null;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, document, embeddings FROM " + vecTable + " WHERE id = ?")) {
                    ps.setString(1, data.getId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            existing = IndexRecord.builder()
                                    .id(rs.getString("id"))
                                    .document(rs.getString("document"))
                                    .embeddings(blobToFloatList(rs.getBytes("embeddings")))
                                    .metadata(fetchMetadata(conn, metaTable, rs.getString("id")))
                                    .build();
                        }
                    }
                }

                if (existing == null) continue;

                String newDocument = data.getDocument() != null ? data.getDocument() : existing.getDocument();
                List<Float> newEmbedding = (embedding != null && !embedding.isEmpty())
                        ? embedding : existing.getEmbeddings();

                deleteMetadata(conn, metaTable, data.getId());
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM " + vecTable + " WHERE id = ?")) {
                    ps.setString(1, data.getId());
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO " + vecTable + "(id, embeddings, document) VALUES (?, ?, ?)")) {
                    ps.setString(1, data.getId());
                    ps.setBytes(2, floatListToBlob(newEmbedding));
                    ps.setString(3, newDocument);
                    ps.executeUpdate();
                }

                Map<String, Object> mergedMetadata = existing.getMetadata() != null
                        ? new LinkedHashMap<>(existing.getMetadata()) : new LinkedHashMap<>();
                if (data.getMetadata() != null) mergedMetadata.putAll(data.getMetadata());
                insertMetadata(conn, metaTable, data.getId(), mergedMetadata);
            }
        });
    }

    @Override
    public void delete(DeleteEmbedding deleteEmbedding) {
        if (deleteEmbedding == null) return;
        String category = deleteEmbedding.getCategory() != null
                ? deleteEmbedding.getCategory() : config.getDefaultCategory();

        runWriteTransaction(conn -> {
            String vecTable = vecTableName(category);
            String metaTable = metadataTableName(category);
            if (!collectionExists(conn, vecTable)) return;

            Set<String> idsToDelete = new LinkedHashSet<>();

            if (deleteEmbedding.getIds() != null) {
                idsToDelete.addAll(deleteEmbedding.getIds());
            }

            if (hasWhereFilter(deleteEmbedding.getWhere()) || hasWhereFilter(deleteEmbedding.getWhereDocument())) {
                try (ResultSet rs = conn.executeQuery("SELECT id, document FROM " + vecTable)) {
                    while (rs.next()) {
                        String id = rs.getString("id");
                        String document = rs.getString("document");
                        Map<String, Object> metadata = fetchMetadata(conn, metaTable, id);
                        if (matchesWhere(deleteEmbedding.getWhere(), metadata) &&
                                matchesWhereDocument(deleteEmbedding.getWhereDocument(), document)) {
                            idsToDelete.add(id);
                        }
                    }
                }
            }

            for (String id : idsToDelete) {
                deleteMetadata(conn, metaTable, id);
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM " + vecTable + " WHERE id = ?")) {
                    ps.setString(1, id);
                    ps.executeUpdate();
                }
            }
        });
    }

    private boolean hasWhereFilter(Map<String, ?> where) {
        return where != null && !where.isEmpty();
    }
}
