package ai.migrate.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class HikariDS {
    private static HikariConfig landingbjConfig;
    private static HikariDataSource landingbjDS;
    private static final String LANDINGBJ_CONFIG_PATH = "/hikari-saas.properties";

    static {
        landingbjConfig = new HikariConfig(LANDINGBJ_CONFIG_PATH);
        landingbjDS = new HikariDataSource(landingbjConfig);
//        initializeDatabase("/init.sql");
    }

    private HikariDS() {
    }

    public static Connection getConnection(String conname) throws SQLException {
        Connection conn = null;
        if (conname.equals(landingbjConfig.getPoolName())) {
            conn = landingbjDS.getConnection();
        }
        return conn;
    }

    public static DataSource getDataSource(String conname) throws SQLException {
        DataSource ds = null;
        if (conname.equals(landingbjConfig.getPoolName())) {
            ds = landingbjDS;
        }
        return ds;
    }

    private static void initializeDatabase(String filePath) {
        try (Connection conn = getConnection(landingbjConfig.getPoolName());
             Statement statement = conn.createStatement();
             InputStream is = HikariDS.class.getResourceAsStream(filePath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is));) {
            String line;
            StringBuilder sqlBuilder = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                if (line.endsWith(";")) {
                    sqlBuilder.append(line);
                    try {
                        statement.execute(sqlBuilder.toString());
                        sqlBuilder.setLength(0);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    sqlBuilder.append(line);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
