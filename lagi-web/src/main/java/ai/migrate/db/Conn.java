/*
 * This program is commercial software; you can only redistribute it and/or modify
 * it under the WARRANTY of Beijing Landing Technologies Co. Ltd.
 *
 * You should have received a copy license along with this program;
 * If not, write to Beijing Landing Technologies, service@landingbj.com.
 */

/*
 * Conn.java
 * Copyright (C) 2020 Beijing Landing Technologies, China
 */

/**
 * 
 */

package ai.migrate.db;

import java.io.Serializable;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import ai.core.AiGlobalDB;
import ai.dao.IConn;
import ai.utils.MigrateGlobal;

public class Conn implements Serializable, IConn {
	private static boolean _DEBUG_1 = false;
	private static boolean _DEBUG_2 = false;

	static {
		if (MigrateGlobal._DEBUG_LEVEL >= 2) {
			_DEBUG_2 = true;
		}
		if (MigrateGlobal._DEBUG_LEVEL >= 1) {
			_DEBUG_1 = true;
		}
	}

	/**
     * 
     */
	private static final long serialVersionUID = 1L;
	private Connection con = null;
	private String connName;
	private static int defaultDBPool = AiGlobalDB.DEFAULT_DB_POOL;
	public static String defaultDB = MigrateGlobal.DEFAULT_DB;
	private boolean closed;

	private static ThreadLocal<AtomicInteger> tracker = new ThreadLocal<AtomicInteger>() {
		@Override
		protected AtomicInteger initialValue() {
			return new AtomicInteger(0);
		}
	};

	private static ConcurrentMap<String, ThreadLocal<Connection>> connMap = new ConcurrentHashMap<String, ThreadLocal<Connection>>();

	public Conn() {
		this(defaultDB);
	}

	public Conn(String connname) {
		connMap.putIfAbsent(connname, new ConnectionThreadLocal(connname));
		this.con = connMap.get(connname).get();
		this.connName = connname;
		this.closed = false;
		tracker.get().incrementAndGet();
	}

	public <T> T unwrap(Class<T> iface) throws SQLException {
		return con.unwrap(iface);
	}

	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		return con.isWrapperFor(iface);
	}

	public Statement createStatement() throws SQLException {
		return con.createStatement();
	}

	public PreparedStatement prepareStatement(String sql) throws SQLException {
		return con.prepareStatement(sql);
	}

	public CallableStatement prepareCall(String sql) throws SQLException {
		return con.prepareCall(sql);
	}

	public String nativeSQL(String sql) throws SQLException {
		return con.nativeSQL(sql);
	}

	public void setAutoCommit(boolean autoCommit) throws SQLException {
		con.setAutoCommit(autoCommit);
	}

	public boolean getAutoCommit() throws SQLException {
		return con.getAutoCommit();
	}

	public void commit() throws SQLException {
		con.commit();
	}

	public void rollback() throws SQLException {
		con.rollback();
	}
	
	@Override
	public void reset()	{
		// TODO: add implementation
		throw new UnsupportedOperationException("Currently unsupported");
	}

	public void close() {
		try {
			closed = true;
			if (tracker.get().decrementAndGet() <= 0) {
				if (!con.getAutoCommit()) {
					con.commit();
				}
				con.close();
				connMap.get(this.connName).remove();
				tracker.get().set(0);
			} else {
				if (!con.getAutoCommit()) {
					con.setAutoCommit(true);
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			try {
				if (!con.getAutoCommit()) {
					con.rollback();
				}
			} catch (SQLException e1) {
				e1.printStackTrace();
			}
		}
	}

	public boolean isClosed() {
		return closed;
	}

	public DatabaseMetaData getMetaData() throws SQLException {
		return con.getMetaData();
	}

	public void setReadOnly(boolean readOnly) throws SQLException {
		con.setReadOnly(readOnly);
	}

	public boolean isReadOnly() throws SQLException {
		return con.isReadOnly();
	}

	public void setCatalog(String catalog) throws SQLException {
		con.setCatalog(catalog);
	}

	public String getCatalog() throws SQLException {
		return con.getCatalog();
	}

	public void setTransactionIsolation(int level) throws SQLException {
		con.setTransactionIsolation(level);
	}

	public int getTransactionIsolation() throws SQLException {
		return con.getTransactionIsolation();
	}

	public SQLWarning getWarnings() throws SQLException {
		return con.getWarnings();
	}

	public void clearWarnings() throws SQLException {
		con.clearWarnings();
	}

	public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
		return con.createStatement(resultSetType, resultSetConcurrency);
	}

	public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
		return con.prepareStatement(sql, resultSetType, resultSetConcurrency);
	}

	public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
		return con.prepareCall(sql, resultSetType, resultSetConcurrency);
	}

	public Map<String, Class<?>> getTypeMap() throws SQLException {
		return con.getTypeMap();
	}

	public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
		con.setTypeMap(map);
	}

	public void setHoldability(int holdability) throws SQLException {
		con.setHoldability(holdability);
	}

	public int getHoldability() throws SQLException {
		return con.getHoldability();
	}

	public Savepoint setSavepoint() throws SQLException {
		return con.setSavepoint();
	}

	public Savepoint setSavepoint(String name) throws SQLException {
		return con.setSavepoint(name);
	}

	public void rollback(Savepoint savepoint) throws SQLException {
		con.rollback(savepoint);
	}

	public void releaseSavepoint(Savepoint savepoint) throws SQLException {
		con.releaseSavepoint(savepoint);
	}

	public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
		return con.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
	}

	public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
		return con.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
	}

	public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
		return con.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
	}

	public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
		return con.prepareStatement(sql, autoGeneratedKeys);
	}

	public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
		return con.prepareStatement(sql, columnIndexes);
	}

	public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
		return con.prepareStatement(sql, columnNames);
	}

	public Clob createClob() throws SQLException {
		return con.createClob();
	}

	public Blob createBlob() throws SQLException {
		return con.createBlob();
	}

	public NClob createNClob() throws SQLException {
		return con.createNClob();
	}

	public SQLXML createSQLXML() throws SQLException {
		return con.createSQLXML();
	}

	public boolean isValid(int timeout) throws SQLException {
		return con.isValid(timeout);
	}

	public void setClientInfo(String name, String value) throws SQLClientInfoException {
		con.setClientInfo(name, value);
	}

	public void setClientInfo(Properties properties) throws SQLClientInfoException {
		con.setClientInfo(properties);
	}

	public String getClientInfo(String name) throws SQLException {
		return con.getClientInfo(name);
	}

	public Properties getClientInfo() throws SQLException {
		return con.getClientInfo();
	}

	public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
		return con.createArrayOf(typeName, elements);
	}

	public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
		return con.createStruct(typeName, attributes);
	}

	public void setSchema(String schema) throws SQLException {
		con.setSchema(schema);
	}

	public String getSchema() throws SQLException {
		return con.getSchema();
	}

	public void abort(Executor executor) throws SQLException {
		con.abort(executor);
	}

	public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
		con.setNetworkTimeout(executor, milliseconds);
	}

	public int getNetworkTimeout() throws SQLException {
		return con.getNetworkTimeout();
	}

	@Override
	public ResultSet executeQuery(String sql) throws SQLException {
		Statement stmt = null;
		stmt = con.createStatement();
		return stmt.executeQuery(sql);

	}

	@Override
	public int executeUpdate(String sql) throws SQLException {
		Statement stmt = null;
		stmt = con.createStatement();
		return stmt.executeUpdate(sql);

	}

	private static Connection initUseproxool(String dbDriver, String connStr) {
	    Logger logger = LoggerFactory.getLogger(Conn.class);
		try {
			Class.forName(dbDriver);
		} catch (java.lang.ClassNotFoundException e) {
			logger.error("警告:Class not found exception occur. Message is:");
			logger.error(e.getMessage());
		}

		try {
			return DriverManager.getConnection(connStr);
		} catch (SQLException e) {
			logger.error("SQL Exception occur. Message is:");
			logger.error(e.getMessage());
		}
		return null;
	}

	private static Connection initUseHikari(String conname) {
		Logger logger = LoggerFactory.getLogger(Conn.class);
		try {
			return HikariDS.getConnection(conname);
		} catch (SQLException e) {
			logger.error("SQL Exception occur. Message is:");
			logger.error(e.getMessage());
		}
		return null;
	}

//	private static Connection initWithMysqlDriver() {
//		try {
//			Class.forName("com.mysql.jdbc.Driver");
//			return DriverManager.getConnection("jdbc:mysql://localhost:3306/ai?useUnicode=true&characterEncoding=UTF-8&zeroDateTimeBehavior=convertToNull&autoReconnect=true", "root", "dir13652");
//		} catch (SQLException | ClassNotFoundException e) {
//			e.printStackTrace();
//		}
//		return null;
//	}

	private static Connection initConnection(String conname) {
		if (_DEBUG_2) {
			defaultDBPool = AiGlobalDB.NO_DB_POOL;
		}
		Connection result = null;
		if (defaultDBPool == AiGlobalDB.PROXOOL_DB_POOL) {
			String dbDriver = AiGlobalDB.DEFAULT_DB_DRIVER;
			String connStr = "proxool." + conname;
			result = initUseproxool(dbDriver, connStr);
		} else if (defaultDBPool == AiGlobalDB.HIKARICP_DB_POOL) {
			result = initUseHikari(conname);
		}
		else {
			result = initUseHikari(conname);
//			result = initWithMysqlDriver();
		}
		return result;
	}

	private static class ConnectionThreadLocal extends ThreadLocal<Connection> {

		private String connName;

		public ConnectionThreadLocal(String connName) {
			super();
			this.connName = connName;
		}

		@Override
		protected Connection initialValue() {
			return initConnection(connName);
		}

	}
}
