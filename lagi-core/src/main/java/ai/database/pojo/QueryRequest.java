package ai.database.pojo;

import java.util.List;

public class QueryRequest {
    private SQLJdbc databaseConfig;
    private String sql;
    private List<Object> parameters;

    // getters and setters
    public SQLJdbc getDatabaseConfig() {
        return databaseConfig;
    }

    public void setDatabaseConfig(SQLJdbc databaseConfig) {
        this.databaseConfig = databaseConfig;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public List<Object> getParameters() {
        return parameters;
    }

    public void setParameters(List<Object> parameters) {
        this.parameters = parameters;
    }
}
