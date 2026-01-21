package ai.database.impl;

import ai.common.pojo.Backend;
import ai.config.ContextLoader;
import ai.database.pojo.SQLJdbc;
import ai.database.pojo.TableColumnInfo;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;


import java.lang.reflect.Field;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MysqlAdapter {
    /**
     * 全局连接池缓存：避免每次 new MysqlAdapter 都创建一个新的 HikariPool，导致连接数爆炸（Too many connections）。
     * key = storageName（如 "mysql"）
     */
    private static final Map<String, HikariDataSource> DATA_SOURCE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private HikariDataSource dataSource;
    private  String name;
    private  String driver;
    private  String url;
    private  String username;
    private  String password;
    public  String model;
    private Integer maximumPoolSize;
    private Long idleTimeout;
    private Long maxLifetime;

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
                // 复用全局连接池（同一个 storageName 只创建一次）
                this.dataSource = DATA_SOURCE_CACHE.computeIfAbsent(this.name, key -> {
                    try {
                        List<Backend> list = ContextLoader.configuration.getFunctions().getText2sql();
                        Backend maxBackend = list.stream()
                                .filter(Backend::getEnable)
                                .max(Comparator.comparingInt(Backend::getPriority))
                                .orElseThrow(() -> new NoSuchElementException("No enabled backends found"));
                        SQLJdbc database = ContextLoader.configuration.getStores().getDatabase().stream()
                                .filter(sqlJdbc -> sqlJdbc.getName().equals(key))
                                .findFirst()
                                .orElseThrow(() -> new NoSuchElementException("Database not found: " + key));

                        driver = database.getDriver();
                        url = database.getJdbcUrl();
                        username = database.getUsername();
                        password = database.getPassword();
                        model = maxBackend.getModel();
                        maximumPoolSize = database.getMaximumPoolSize();
                        idleTimeout = database.getIdleTimeout();
                        maxLifetime = database.getMaxLifetime();

                        HikariConfig config = new HikariConfig();
                        config.setJdbcUrl(url);
                        config.setUsername(username);
                        config.setPassword(password);
                        config.setDriverClassName(driver);
                        // 连接池配置
                        config.setMaximumPoolSize(maximumPoolSize != null ? maximumPoolSize : 10);
                        config.setMinimumIdle(1);
                        config.setConnectionTimeout(30000);
                        if (idleTimeout != null) {
                            config.setIdleTimeout(idleTimeout);
                        }
                        if (maxLifetime != null) {
                            config.setMaxLifetime(maxLifetime);
                        }
                        return new HikariDataSource(config);
                    } catch (Exception e) {
                        // computeIfAbsent 里抛异常会导致缓存不写入，便于下次重试
                        throw new RuntimeException("初始化数据库连接池失败: " + key, e);
                    }
                });
            } catch (Exception e) {
                // 保持兼容：不直接抛出到上层，但要确保 dataSource 为 null 时不 NPE
                e.printStackTrace();
                this.dataSource = null;
            }
        }

    /**
     * 打开连接
     */
//    public Connection getCon() {
//        Connection con = null;
//
//        try {
//            Class.forName(driver);
//            con = DriverManager.getConnection(url,username,password);
//        } catch (Exception e) {
//            System.out.println("数据库连接失败:"+e);
//        }
//        return con;}
    public Connection getCon() {
        try {
            if (dataSource == null) {
                return null;
            }
            return dataSource.getConnection();
        } catch (SQLException e) {
            System.out.println("数据库连接失败:"+e);
            return null;
        }
    }

    /**
     * 关闭连接
     */
//    public void close(Connection con) {
//        try {
//            if (con != null)
//                con.close();
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
//    }
    public void close() {
        // 兼容旧调用：不主动关闭全局连接池，避免影响其他线程/请求
        // 如需关闭请在应用退出时统一处理（或实现显式 shutdown 方法）
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
            if (con == null) {
                return list;
            }
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
