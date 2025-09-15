package ai.database.impl;

import ai.database.pojo.TableColumnInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SqliteAdapter {
    private String url;
    public String model;
    private static final Logger log = LoggerFactory.getLogger(SqliteAdapter.class);

    public SqliteAdapter() {
        init();
    }

    private void init() {
        String tomcatPath = System.getProperty("user.dir");
        String dbPath = tomcatPath + "/saas.db";
        url = "jdbc:sqlite:" + dbPath;
        Connection conn = null;
        Statement stmt = null;
        try {
            conn = getCon(url);
            stmt = conn.createStatement();
            DatabaseMetaData dbm = conn.getMetaData();
            ResultSet tables = dbm.getTables(null, null, "table_info", null);
            if (!tables.next()) {
                String createTableInfo = "CREATE TABLE table_info (" +
                        " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        " file_id TEXT NOT NULL," +
                        " table_name TEXT NOT NULL," +
                        " description TEXT" +
                        ");";
                stmt.executeUpdate(createTableInfo);
            }

            tables = dbm.getTables(null, null, "detailed_data", null);
            if (!tables.next()) {
                StringBuilder createDetailedData = new StringBuilder("CREATE TABLE detailed_data (" +
                        " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        " table_info_id INTEGER NOT NULL,");
                for (int i = 1; i <= 36; i++) {
                    createDetailedData.append(" field").append(i).append(" TEXT,");
                }
                createDetailedData.append(" FOREIGN KEY (table_info_id) REFERENCES table_info(id) ON DELETE CASCADE" +
                        ");");
                stmt.executeUpdate(createDetailedData.toString());
            }
            log.info("Tables created successfully or already exist.");
        } catch (SQLException e) {
            log.error("An error occurred while checking or creating tables.", e);
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public Connection getCon() {
        return getCon(url);
    }

    /**
     * 打开连接
     */
    public Connection getCon(String url) {
        Connection con = null;
        try {
            Class.forName("org.sqlite.JDBC");
            con = DriverManager.getConnection(url);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return con;
    }

    /**
     * 关闭连接
     */
    public void close(Connection con) {
        try {
            if (con != null)
                con.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void close(PreparedStatement pre) {
        try {
            if (pre != null)
                pre.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void close(ResultSet res) {
        try {
            if (res != null)
                res.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void close(Connection con, PreparedStatement pre, ResultSet res) {
        try {
            if (res != null)
                res.close();
            if (pre != null)
                pre.close();
            if (con != null)
                con.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void close(Connection con, PreparedStatement pre) {
        try {
            if (pre != null)
                pre.close();
            if (con != null)
                con.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * 查询
     *
     * @param <T>
     */
    public <T> List<T> select(Class<T> claz, String sql, Object... objs) {
        Connection con = null;
        PreparedStatement pre = null;
        ResultSet res = null;
        List<T> list = new ArrayList<>();
        try {
            con = getCon();
            pre = con.prepareStatement(sql);
            for (int i = 0; i < objs.length; i++) {
                pre.setObject(i + 1, objs[i]);
            }
            res = pre.executeQuery();
            while (res.next()) {
                T t = claz.newInstance();
                Field[] fields = claz.getDeclaredFields();
                for (Field f : fields) {
                    f.setAccessible(true);
                    f.set(t, res.getObject(f.getName()));
                }
                list.add(t);
            }
        } catch (SQLException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        } finally {
            close(con, pre, res);
        }
        return list;
    }

    /**
     * 无实体类的通用查询
     */
    public List<Map<String, Object>> select(String sql, Object... objs) {
        Connection con = null;
        PreparedStatement pre = null;
        ResultSet res = null;
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            con = getCon();
            pre = con.prepareStatement(sql);
            for (int i = 0; i < objs.length; i++) {
                pre.setObject(i + 1, objs[i]);
            }
            res = pre.executeQuery();
            ResultSetMetaData rsmd = res.getMetaData();
            while (res.next()) {
                Map<String, Object> rowMap = new HashMap<>();
                for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                    String columnName = rsmd.getColumnLabel(i);
                    Object columnValue = res.getObject(i);
                    rowMap.put(columnName, columnValue);
                }
                list.add(rowMap);
            }
        } catch (SQLException e) {
            Map<String, Object> rowMap = new HashMap<>();
            rowMap.put("error", e.getMessage());
            list.add(rowMap);
            return list;
        } finally {
            close(con, pre, res);
        }
        return list;
    }

    /**
     * 增删改
     *
     * @param sql
     * @param objs
     */
    public int executeUpdate(String sql, Object... objs) {
        Connection con = null;
        PreparedStatement pre = null;
        try {
            con = getCon();
            pre = con.prepareStatement(sql);
            for (int i = 0; i < objs.length; i++) {
                pre.setObject(i + 1, objs[i]);
            }
            return pre.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            close(con, pre);
        }
        return 0;
    }

    /**
     * 返id增
     *
     * @param sql
     * @param objs
     */
    public int executeUpdateGeneratedKeys(String sql, Object... objs) {
        Connection con = null;
        PreparedStatement pre = null;
        try {
            con = getCon();
            pre = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < objs.length; i++) {
                pre.setObject(i + 1, objs[i]);
            }
            int rowsAffected = pre.executeUpdate();
            int newCid = 0;
            ResultSet generatedKeys = pre.getGeneratedKeys();
            if (rowsAffected > 0) {
                if (generatedKeys.next()) {
                    newCid = generatedKeys.getInt(1);
                }
            }
            return newCid;
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            close(con, pre);
        }
        return 0;
    }

    /**
     * 聚合查询
     *
     * @param sql
     * @param objs
     */
    public int selectCount(String sql, Object... objs) {
        Connection con = null;
        PreparedStatement pre = null;
        ResultSet res = null;
        try {
            con = getCon();
            pre = con.prepareStatement(sql);
            for (int i = 0; i < objs.length; i++) {
                pre.setObject(i + 1, objs[i]);
            }
            res = pre.executeQuery();
            if (res.next()) {
                return res.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            close(con, pre, res);
        }
        return 0;
    }

    /**
     * 获取列信息
     *
     * @param tableName
     */
    public List<TableColumnInfo> getTableColumnInfo(String tableName) {
        String[] tableNames = tableName.split("[,，]");
        ResultSet resultSet = null;
        Connection con = null;
        List<TableColumnInfo> columnInfos = new ArrayList<>();
        for (String table : tableNames) {
            try {
                con = getCon();
                DatabaseMetaData metaData = con.getMetaData();
                resultSet = metaData.getColumns(null, null, table, null);
                while (resultSet.next()) {
                    String columnName = resultSet.getString("COLUMN_NAME");
                    String columnType = resultSet.getString("TYPE_NAME");
                    int columnSize = resultSet.getInt("COLUMN_SIZE");
                    String columnRemark = resultSet.getString("REMARKS");
                    String tableType = resultSet.getString("TABLE_NAME");
                    TableColumnInfo columnInfo = new TableColumnInfo(
                            table,
                            tableType,
                            columnName,
                            columnType,
                            columnSize,
                            columnRemark
                    );
                    columnInfos.add(columnInfo);
                }
            } catch (Exception e) {
                return null;
            } finally {
                close(con, null, resultSet);
            }
        }
        return columnInfos;
    }

    public List<Map<String, Object>> sqlToValue(String sql) {
        List<Map<String, Object>> list = select(sql);
        return list.size() > 0 && list != null ? list : new ArrayList<>();
    }
}
