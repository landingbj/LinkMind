package ai.database.pojo;

import java.util.Map;

public class UpdateRequest {
    private SQLJdbc databaseConfig;
    private String operationType; // "insert" or "update"
    private String tableName;
    private Map<String, Object> data;
    private Map<String, Object> where;

    // getters and setters
    public SQLJdbc getDatabaseConfig() {
        return databaseConfig;
    }

    public void setDatabaseConfig(SQLJdbc databaseConfig) {
        this.databaseConfig = databaseConfig;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public Map<String, Object> getWhere() {
        return where;
    }

    public void setWhere(Map<String, Object> where) {
        this.where = where;
    }
}
