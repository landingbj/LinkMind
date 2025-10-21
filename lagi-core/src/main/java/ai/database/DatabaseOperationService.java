package ai.database;

import ai.database.pojo.QueryRequest;
import ai.database.pojo.SQLJdbc;
import ai.database.pojo.UpdateRequest;
import ai.workflow.exception.WorkflowException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * 数据库操作服务类
 */
public class DatabaseOperationService {

    private final Map<String, DataSource> dataSourceCache = new HashMap<>();

    /**
     * 执行查询操作
     */
    public Object query(QueryRequest request) {
        DataSource dataSource = getDataSource(request.getDatabaseConfig());

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(request.getSql())) {

            // 设置查询参数
            setParameters(statement, request.getParameters());

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSetToList(resultSet);
            }
        } catch (SQLException e) {
            throw new WorkflowException("数据库查询执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行更新/插入操作
     */
    public Object update(UpdateRequest request) {
        DataSource dataSource = getDataSource(request.getDatabaseConfig());

        try (Connection connection = dataSource.getConnection()) {
            String sql = buildSql(request);

            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                // 设置参数
                setUpdateParameters(statement, request);

                int affectedRows = statement.executeUpdate();

                // 如果是插入操作且有自动生成的键，返回生成的键
                if ("insert".equals(request.getOperationType())) {
                    try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            long id = generatedKeys.getLong(1);
                            Map<String, Object> result = new HashMap<>();
                            result.put("affectedRows", affectedRows);
                            result.put("generatedId", id);
                            return result;
                        }
                    }
                }

                Map<String, Object> result = new HashMap<>();
                result.put("affectedRows", affectedRows);
                return result;
            }
        } catch (SQLException e) {
            throw new WorkflowException("数据库更新执行失败: " + e.getMessage(), e);
        }
    }

    private String buildSql(UpdateRequest request) {
        if ("insert".equals(request.getOperationType())) {
            return buildInsertSql(request.getTableName(), request.getData());
        } else if ("update".equals(request.getOperationType())) {
            return buildUpdateSql(request.getTableName(), request.getData().keySet(),
                    request.getWhere() != null ? request.getWhere().keySet() : Collections.emptySet());
        } else {
            throw new WorkflowException("不支持的操作类型: " + request.getOperationType());
        }
    }

    private String buildInsertSql(String tableName, Map<String, Object> data) {
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(tableName);
        sql.append(" (");

        boolean first = true;
        for (String column : data.keySet()) {
            if (!first) {
                sql.append(", ");
            }
            sql.append(column);
            first = false;
        }

        sql.append(") VALUES (");

        first = true;
        for (int i = 0; i < data.size(); i++) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("?");
            first = false;
        }

        sql.append(")");
        return sql.toString();
    }
    private String buildUpdateSql(String tableName, Set<String> updateColumns, Set<String> whereColumns) {
        StringBuilder sql = new StringBuilder("UPDATE ");
        sql.append(tableName);
        sql.append(" SET ");

        boolean first = true;
        for (String column : updateColumns) {
            if (!first) {
                sql.append(", ");
            }
            sql.append(column);
            sql.append(" = ?");
            first = false;
        }

        if (!whereColumns.isEmpty()) {
            sql.append(" WHERE ");
            first = true;
            for (String column : whereColumns) {
                if (!first) {
                    sql.append(" AND ");
                }
                sql.append(column);
                sql.append(" = ?");
                first = false;
            }
        }

        return sql.toString();
    }

    private void setUpdateParameters(PreparedStatement statement, UpdateRequest request) throws SQLException {
        int index = 1;

        // 设置更新数据参数 - 按照构建SQL时的相同顺序
        for (Object value : request.getData().values()) {
            statement.setObject(index++, value);
        }

        // 设置WHERE条件参数
        if (request.getWhere() != null) {
            for (Object value : request.getWhere().values()) {
                statement.setObject(index++, value);
            }
        }
    }


    private DataSource getDataSource(SQLJdbc config) {
        String key = config.getName() + ":" + config.getJdbcUrl();

        return dataSourceCache.computeIfAbsent(key, k -> createDataSource(config));
    }

    private DataSource createDataSource(SQLJdbc config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getJdbcUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setDriverClassName(config.getDriver());

        if (config.getMaximumPoolSize() != null) {
            hikariConfig.setMaximumPoolSize(config.getMaximumPoolSize());
        }

        if (config.getIdleTimeout() != null) {
            hikariConfig.setIdleTimeout(config.getIdleTimeout());
        }

        if (config.getMaxLifetime() != null) {
            hikariConfig.setMaxLifetime(config.getMaxLifetime());
        }

        return new HikariDataSource(hikariConfig);
    }

    private void setParameters(PreparedStatement statement, List<Object> parameters) throws SQLException {
        if (parameters != null) {
            int index = 1;
            for (Object value : parameters) {
                statement.setObject(index++, value);
            }
        }
    }

    private List<Map<String, Object>> resultSetToList(ResultSet resultSet) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();

        while (resultSet.next()) {
            Map<String, Object> row = new HashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(metaData.getColumnLabel(i), resultSet.getObject(i));
            }
            list.add(row);
        }

        return list;
    }
}
