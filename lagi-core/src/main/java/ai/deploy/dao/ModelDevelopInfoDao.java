package ai.deploy.dao;

import ai.deploy.pojo.DeployInfo;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ModelDevelopInfoDao {
    private static final Logger log = LoggerFactory.getLogger(ModelDevelopInfoDao.class);
    private static final String DB_URL = "jdbc:sqlite:saas.db";
    private static HikariDataSource dataSource;

    static {
        try {
            initializeDataSource();
            // 创建数据库表
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(
                         "CREATE TABLE IF NOT EXISTS model_develop_info (\n" +
                         "  id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                         "  user_id varchar(64),\n" +
                         "  model_path varchar(200),\n" +
                         "  template varchar(64),\n" +
                         "  adapter_path varchar(200),\n" +
                         "  finetuning_type varchar(64),\n" +
                         "  port varchar(20),\n" +
                         "  running INTEGER,\n" +
                         "  inference_id varchar(64),\n" +
                         "  api_address varchar(200)\n" +
                         ");")) {
                pstmt.executeUpdate();
            }
            log.info("ModelDevelopInfoDao initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize ModelDevelopInfoDao", e);
        }
    }

    private synchronized static void initializeDataSource() {
        if (dataSource == null) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(DB_URL);
            config.setDriverClassName("org.sqlite.JDBC");
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            config.setLeakDetectionThreshold(60000);
            dataSource = new HikariDataSource(config);
        }
    }

    private static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            initializeDataSource();
        }
        return dataSource.getConnection();
    }

    // 插入数据
    public int insert(DeployInfo modelDevelopInfo) {
        String sql = "INSERT INTO model_develop_info (user_id, model_path, template, adapter_path, finetuning_type, port, running, inference_id, api_address) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?,?)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, modelDevelopInfo.getUserId());
            pstmt.setString(2, modelDevelopInfo.getModelPath());
            pstmt.setString(3, modelDevelopInfo.getTemplate());
            pstmt.setString(4, modelDevelopInfo.getAdapterPath());
            pstmt.setString(5, modelDevelopInfo.getFinetuningType());
            pstmt.setString(6, modelDevelopInfo.getPort());
            pstmt.setInt(7, modelDevelopInfo.getRunning());
            pstmt.setString(8, modelDevelopInfo.getInferenceId());
            pstmt.setString(8, modelDevelopInfo.getApiAddress());

            pstmt.executeUpdate();

            // 获取生成的主键
            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1); // 返回生成的 id
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    // 更新数据
    public int update(DeployInfo modelDevelopInfo) {
        String sql = "UPDATE model_develop_info SET " +
                "user_id = ?, model_path = ?, template = ?, adapter_path = ?, finetuning_type = ?, port = ?, running = ? , inference_id = ?, api_address = ? " +
                " WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, modelDevelopInfo.getUserId());
            pstmt.setString(2, modelDevelopInfo.getModelPath());
            pstmt.setString(3, modelDevelopInfo.getTemplate());
            pstmt.setString(4, modelDevelopInfo.getAdapterPath());
            pstmt.setString(5, modelDevelopInfo.getFinetuningType());
            pstmt.setString(6, modelDevelopInfo.getPort());
            pstmt.setInt(7, modelDevelopInfo.getRunning());
            pstmt.setString(8, modelDevelopInfo.getInferenceId());
            pstmt.setString(9, modelDevelopInfo.getApiAddress());
            pstmt.setInt(10, modelDevelopInfo.getId());
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // 删除数据
    public int delete(int id) {
        String sql = "DELETE FROM model_develop_info WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // 查询所有数据
    public List<DeployInfo> findAll() {
        String sql = "SELECT * FROM model_develop_info";
        List<DeployInfo> modelDevelopInfos = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                DeployInfo modelDevelopInfo = new DeployInfo();
                modelDevelopInfo.setId(rs.getInt("id"));
                modelDevelopInfo.setUserId(rs.getString("user_id"));
                modelDevelopInfo.setModelPath(rs.getString("model_path"));
                modelDevelopInfo.setTemplate(rs.getString("template"));
                modelDevelopInfo.setAdapterPath(rs.getString("adapter_path"));
                modelDevelopInfo.setFinetuningType(rs.getString("finetuning_type"));
                modelDevelopInfo.setPort(rs.getString("port"));
                modelDevelopInfo.setRunning(rs.getInt("running"));
                modelDevelopInfo.setInferenceId(rs.getString("inference_id"));
                modelDevelopInfo.setApiAddress(rs.getString("api_address"));
                modelDevelopInfos.add(modelDevelopInfo);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return modelDevelopInfos;
    }

    // 根据ID查询数据
    public List<DeployInfo> findByUserId(String  userId) {
        String sql = "SELECT * FROM model_develop_info WHERE user_id = ?";
        List<DeployInfo> modelDevelopInfos = new ArrayList<>();
        try (Connection conn = getConnection();
              PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                DeployInfo modelDevelopInfo = new DeployInfo();
                modelDevelopInfo.setId(rs.getInt("id"));
                modelDevelopInfo.setUserId(rs.getString("user_id"));
                modelDevelopInfo.setModelPath(rs.getString("model_path"));
                modelDevelopInfo.setTemplate(rs.getString("template"));
                modelDevelopInfo.setAdapterPath(rs.getString("adapter_path"));
                modelDevelopInfo.setFinetuningType(rs.getString("finetuning_type"));
                modelDevelopInfo.setPort(rs.getString("port"));
                modelDevelopInfo.setRunning(rs.getInt("running"));
                modelDevelopInfo.setInferenceId(rs.getString("inference_id"));
                modelDevelopInfo.setApiAddress(rs.getString("api_address"));
                modelDevelopInfos.add(modelDevelopInfo);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return modelDevelopInfos;
    }

    public DeployInfo findById(Integer id) {
        String sql = "SELECT * FROM model_develop_info WHERE id = ?";
        DeployInfo modelDevelopInfo = null;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                modelDevelopInfo = new DeployInfo();
                modelDevelopInfo.setId(rs.getInt("id"));
                modelDevelopInfo.setUserId(rs.getString("user_id"));
                modelDevelopInfo.setModelPath(rs.getString("model_path"));
                modelDevelopInfo.setTemplate(rs.getString("template"));
                modelDevelopInfo.setAdapterPath(rs.getString("adapter_path"));
                modelDevelopInfo.setFinetuningType(rs.getString("finetuning_type"));
                modelDevelopInfo.setPort(rs.getString("port"));
                modelDevelopInfo.setInferenceId(rs.getString("inference_id"));
                modelDevelopInfo.setApiAddress(rs.getString("api_address"));
                modelDevelopInfo.setRunning(rs.getInt("running"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return modelDevelopInfo;
    }

    public int countPort(String port) {
        String sql = "SELECT count(1) FROM model_develop_info WHERE port = ? and running=1";
        int count = 0;
        try (Connection conn = getConnection();
              PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, port);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                count = rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return count;
    }


    public List<String> runningPort(String port) {
        String sql = "SELECT port FROM model_develop_info WHERE port = ? and running= 1";
        List<String> ports = new ArrayList<>();
        try (Connection conn = getConnection();
              PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, port);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                ports.add(rs.getString("port"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return ports;
    }
}
