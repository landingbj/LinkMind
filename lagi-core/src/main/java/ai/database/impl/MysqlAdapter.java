package ai.database.impl;

import ai.common.pojo.Backend;
import ai.config.ContextLoader;
import ai.config.GlobalConfigurations;
import ai.database.pojo.SQLJdbc;
import ai.database.pojo.TableColumnInfo;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.lang.reflect.Field;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MysqlAdapter {
    private static final Logger log = LoggerFactory.getLogger(MysqlAdapter.class);
    /**
     * 全局连接池缓存：避免每次 new MysqlAdapter 都创建一个新的 HikariPool，导致连接数爆炸（Too many connections）。
     * key = storageName（如 "mysql"）
     */
//    private static final Map<String, HikariDataSource> DATA_SOURCE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static final Map<String, MysqlAdapter> instances = new ConcurrentHashMap<>();


    private HikariDataSource toDataSource(SQLJdbc sqlJdbc) {
        HikariDataSource hikariDataSource = new HikariDataSource();
        hikariDataSource.setJdbcUrl(sqlJdbc.getJdbcUrl());
        hikariDataSource.setUsername(sqlJdbc.getUsername());
        hikariDataSource.setPassword(sqlJdbc.getPassword());
        hikariDataSource.setDriverClassName(sqlJdbc.getDriver());
        hikariDataSource.setMaximumPoolSize(sqlJdbc.getMaximumPoolSize());
        hikariDataSource.setIdleTimeout(sqlJdbc.getIdleTimeout());
        hikariDataSource.setMaxLifetime(sqlJdbc.getMaxLifetime());
        return hikariDataSource;
    }

    private MysqlAdapter(String storageName){
        GlobalConfigurations configuration = ContextLoader.configuration;
        if(configuration == null) {
            throw new RuntimeException("mysql 创建数据库需哟配置文件");
        }
        if(configuration.getStores() == null) {
            throw new RuntimeException("mysql 创建数据库需数据配置");
        }
        if(configuration.getStores().getDatabase() == null) {
            throw new RuntimeException("mysql 创建数据库需数据配置1");
        }
        List<SQLJdbc> mysql = configuration.getStores().getDatabase().stream().filter(database -> database.getName().equalsIgnoreCase(storageName)).collect(Collectors.toList());
        if(mysql.isEmpty()){
            throw new RuntimeException("需要数据默认mysql配置");
        }
        SQLJdbc sqlJdbc = mysql.get(0);
        this.name = sqlJdbc.getName();
        this.driver = sqlJdbc.getDriver();
        this.url = sqlJdbc.getJdbcUrl();
        this.username = sqlJdbc.getUsername();
        this.password = sqlJdbc.getPassword();
        this.maximumPoolSize = sqlJdbc.getMaximumPoolSize();
        this.idleTimeout = sqlJdbc.getIdleTimeout();
        this.maxLifetime = sqlJdbc.getMaxLifetime();
        this.dataSource = toDataSource(sqlJdbc);
    }

    /**
     * 替换 JDBC URL 中的数据库名称
     * MySQL JDBC URL 格式: jdbc:mysql://host:port/database?params
     * 
     * @param originalUrl 原始 JDBC URL
     * @param newDatabaseName 新的数据库名称
     * @return 替换后的 JDBC URL
     */
    private String replaceDatabaseInUrl(String originalUrl, String newDatabaseName) {
        if (originalUrl == null || originalUrl.isEmpty()) {
            throw new IllegalArgumentException("JDBC URL 不能为空");
        }
        if (newDatabaseName == null || newDatabaseName.isEmpty()) {
            throw new IllegalArgumentException("数据库名称不能为空");
        }
        
        // 使用正则表达式匹配并替换数据库名称部分
        // 匹配模式: jdbc:mysql://host:port/database?params 或 jdbc:mysql://host/database?params
        // 数据库名称在最后一个 / 之后，? 之前（如果有参数）或字符串结尾
        Pattern pattern = Pattern.compile("(jdbc:mysql://[^/]+/)([^?]*)(.*)");
        Matcher matcher = pattern.matcher(originalUrl);
        
        if (matcher.matches()) {
            String prefix = matcher.group(1);  // jdbc:mysql://host:port/
            String params = matcher.group(3);  // ?params 或空字符串
            return prefix + newDatabaseName + params;
        } else {
            // 如果 URL 格式不符合预期，尝试简单替换（向后兼容）
            log.warn("JDBC URL 格式不符合预期: {}, 使用简单替换方式", originalUrl);
            // 查找最后一个 / 的位置，替换其后的数据库名称
            int lastSlashIndex = originalUrl.lastIndexOf('/');
            if (lastSlashIndex >= 0) {
                int questionMarkIndex = originalUrl.indexOf('?', lastSlashIndex);
                if (questionMarkIndex >= 0) {
                    // 有参数的情况
                    return originalUrl.substring(0, lastSlashIndex + 1) + newDatabaseName + originalUrl.substring(questionMarkIndex);
                } else {
                    // 无参数的情况
                    return originalUrl.substring(0, lastSlashIndex + 1) + newDatabaseName;
                }
            } else {
                throw new IllegalArgumentException("无法解析 JDBC URL 格式: " + originalUrl);
            }
        }
    }

    private MysqlAdapter(String databaseName , String storageName){
        GlobalConfigurations configuration = ContextLoader.configuration;
        if(configuration == null) {
            throw new RuntimeException("mysql 创建数据库需哟配置文件");
        }
        if(configuration.getStores() == null) {
            throw new RuntimeException("mysql 创建数据库需数据配置");
        }
        if(configuration.getStores().getDatabase() == null) {
            throw new RuntimeException("mysql 创建数据库需数据配置1");
        }
        List<SQLJdbc> mysql = configuration.getStores().getDatabase().stream().filter(database -> database.getName().equalsIgnoreCase(storageName)).collect(Collectors.toList());
        if(mysql.isEmpty()){
            throw new RuntimeException("需要数据默认mysql配置");
        }
        SQLJdbc sqlJdbc = mysql.get(0);
        this.name = sqlJdbc.getName();
        this.driver = sqlJdbc.getDriver();
        // 使用新的方法替换数据库名称
        this.url = replaceDatabaseInUrl(sqlJdbc.getJdbcUrl(), databaseName);
        this.username = sqlJdbc.getUsername();
        this.password = sqlJdbc.getPassword();
        this.maximumPoolSize = sqlJdbc.getMaximumPoolSize();
        this.idleTimeout = sqlJdbc.getIdleTimeout();
        this.maxLifetime = sqlJdbc.getMaxLifetime();
        // 创建新的 SQLJdbc 对象，使用替换后的 URL
        SQLJdbc modifiedSqlJdbc = new SQLJdbc();
        modifiedSqlJdbc.setName(sqlJdbc.getName());
        modifiedSqlJdbc.setDriver(sqlJdbc.getDriver());
        modifiedSqlJdbc.setJdbcUrl(this.url);
        modifiedSqlJdbc.setUsername(sqlJdbc.getUsername());
        modifiedSqlJdbc.setPassword(sqlJdbc.getPassword());
        modifiedSqlJdbc.setMaximumPoolSize(sqlJdbc.getMaximumPoolSize());
        modifiedSqlJdbc.setIdleTimeout(sqlJdbc.getIdleTimeout());
        modifiedSqlJdbc.setMaxLifetime(sqlJdbc.getMaxLifetime());
        this.dataSource = toDataSource(modifiedSqlJdbc);
    }

    public static MysqlAdapter getInstance(String storageName){
        storageName = storageName.toLowerCase();
        MysqlAdapter aDefault = instances.get(storageName);
        if(aDefault != null) {
            return aDefault;
        }
        synchronized (MysqlAdapter.class) {
            aDefault = instances.get(storageName);
            if(aDefault != null) {
                return aDefault;
            }
            try {
                aDefault = new MysqlAdapter(storageName);
                instances.put(storageName, aDefault);
            } catch (Exception e) {
                log.error("mysql 创建数据库异常", e);
            }
        }
        return aDefault;
    }

    public static MysqlAdapter getInstance(String databaseName,String storageName){
        String key = (databaseName + storageName).toLowerCase();
        MysqlAdapter aDefault = instances.get(key);
        if(aDefault != null) {
            return aDefault;
        }
        synchronized (MysqlAdapter.class) {
            aDefault = instances.get(key);
            if(aDefault != null) {
                return aDefault;
            }
            try {
                aDefault = new MysqlAdapter(databaseName, storageName);
                instances.put(key, aDefault);
            } catch (Exception e) {
                log.error("mysql 创建数据库异常", e);
            }
        }
        return aDefault;
    }


    public static MysqlAdapter getInstance(){
        return getInstance("mysql");
    }

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
