package ai.database.impl;

import ai.common.pojo.Backend;
import ai.config.ContextLoader;
import ai.database.pojo.SQLJdbc;
import ai.database.pojo.TableColumnInfo;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MysqlAdapter {
    private  String name;
    private  String driver;
    private  String url;
    private  String username;
    private  String password;
    public  String model;
    public MysqlAdapter(String databaseName,String storageName){
        init(storageName);
        if (databaseName!=null&&url!=null){
            String regex = "jdbc:mysql://([^/]+)";

            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(url);
            String hostPort = "";
            if (matcher.find()) {
                hostPort = matcher.group(1);
            } else {
                System.out.println("No host and port found.");
            }
            url = "jdbc:mysql://" + hostPort + "/" + databaseName + "?useUnicode=true&characterEncoding=utf-8&useSSL=false";
        }
       }

        public MysqlAdapter(String storageName){
            init(storageName);
        }

        private void init(String storageName){
            this.name = storageName;
            try {
                List<Backend> list = ContextLoader.configuration.getFunctions().getText2sql();
                Backend maxBackend = list.stream()
                        .filter(Backend::getEnable)
                        .max(Comparator.comparingInt(Backend::getPriority)) .orElseThrow(() -> new NoSuchElementException("No enabled backends found"));
                SQLJdbc database = ContextLoader.configuration.getStores().getDatabase().stream()
                        .filter(sqlJdbc -> sqlJdbc.getName().equals(this.name))
                        .findFirst()
                        .orElseThrow(() -> new NoSuchElementException("Database not found"));
                driver = database.getDriver();
                url = database.getJdbcUrl();
                username = database.getUsername();
                password = database.getPassword();
                model = maxBackend.getModel();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    /**
     * 打开连接
     */
    public Connection getCon() {
        Connection con = null;

        try {
            Class.forName(driver);
            con = DriverManager.getConnection(url,username,password);
        } catch (Exception e) {
            System.out.println("数据库连接失败:"+e);
        }
        return con;}

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
        List<T> list = new ArrayList<T>();
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
     *
     */
    public List<Map<String, Object>> select(String sql, Object... objs) {
        Connection con = null;
        PreparedStatement pre = null;
        ResultSet res = null;
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
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
                for (int i = 1; i <= res.getMetaData().getColumnCount(); i++) {
                    String columnName = rsmd.getColumnLabel(i);
                    Object columnValue = res.getObject(i);
                    rowMap.put(columnName, columnValue);
                }
                list.add(rowMap);
            }
        } catch (SQLException e) {
            //e.printStackTrace();
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
        ResultSet res = null;
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
            close(con, pre, res);
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
                String[] catalogs = table.split("[。.]");
                resultSet = metaData.getColumns(catalogs[0], null, table, null);

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
            }catch (Exception e){
                return null;
            } finally {
                close(con, null,resultSet);// 关闭连接
            }
        }
        return columnInfos;
    }

    public List<Map<String,Object>> sqlToValue(String sql) {
        List<Map<String,Object>> list = select(sql);
        return list.size() > 0 && list != null ? list : new ArrayList<>();
    }

}
