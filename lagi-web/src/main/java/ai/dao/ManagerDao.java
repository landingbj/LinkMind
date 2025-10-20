package ai.dao;

import ai.agent.dto.ManagerModel;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.utils.LlmAdapterFactory;
import ai.migrate.db.Conn;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ManagerDao {

    // Add ManagerModel
    public int addManagerModel(ManagerModel managerModel) {
        String sql = "INSERT INTO model_manager ( user_id, model_name, online, api_key, model_type, endpoint, status) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = new Conn();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, managerModel.getUserId());
            pstmt.setString(2, managerModel.getModelName());
            pstmt.setInt(3, managerModel.getOnline());
            pstmt.setString(4, managerModel.getApiKey());
            pstmt.setString(5, managerModel.getModelType());
            pstmt.setString(6, managerModel.getEndpoint());
            pstmt.setInt(7, managerModel.getStatus());
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // Delete ManagerModel
    public int delManagerModel(Integer id) {
        String sql = "DELETE FROM model_manager WHERE id = ?";
        try (Connection conn = new Conn();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // Update ManagerModel
//    public int updateManagerModel(ManagerModel managerModel) {
//        String sql = "UPDATE model_manager SET user_id = ?,  model_name = ?, online = ?, api_key = ?, model_type = ?, endpoint = ?, status = ? WHERE id = ?";
//        try (Connection conn = new Conn();
//             PreparedStatement pstmt = conn.prepareStatement(sql)) {
//            pstmt.setString(1, managerModel.getUserId());
//            pstmt.setString(2, managerModel.getModelName());
//            pstmt.setInt(3, managerModel.getOnline());
//            pstmt.setString(4, managerModel.getApiKey());
//            pstmt.setString(5, managerModel.getModelType());
//            pstmt.setString(6, managerModel.getEndpoint());
//            pstmt.setInt(7, managerModel.getStatus());
//            pstmt.setInt(8, managerModel.getId());
//            return pstmt.executeUpdate();
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
//        return 0;
//    }

    public int updateManagerModel(ManagerModel managerModel) {
        String sql = getUpdateSql(managerModel);
        try (Connection conn = new Conn();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            int index = 1;
            if(managerModel.getUserId() != null) {
                pstmt.setString(index++, managerModel.getUserId());
            }
            if(managerModel.getModelName() != null) {
                pstmt.setString(index++, managerModel.getModelName());
            }
            if(managerModel.getOnline() != null) {
                pstmt.setInt(index++, managerModel.getOnline());
            }
            if(managerModel.getApiKey() != null) {
                pstmt.setString(index++, managerModel.getApiKey());
            }
            if(managerModel.getModelType() != null) {
                pstmt.setString(index++, managerModel.getModelType());
            }
            if(managerModel.getEndpoint() != null) {
                pstmt.setString(index++, managerModel.getEndpoint());
            }
            if(managerModel.getStatus() != null) {
                pstmt.setInt(index++, managerModel.getStatus());
            }
            if(managerModel.getId() != null) {
                pstmt.setInt(index, managerModel.getId());
            }
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private String getUpdateSql(ManagerModel managerModel) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("UPDATE model_manager SET ");
        boolean hasLast = false;
        if(managerModel.getUserId() != null) {
            sqlBuilder.append(" user_id = ?");
            hasLast = true;
        }
        if(managerModel.getModelName() != null) {
            if(hasLast) {
                sqlBuilder.append(", ");
            }
            sqlBuilder.append(" model_name = ?");
            hasLast = true;
        }
        if (managerModel.getOnline() != null) {
            if(hasLast) {
                sqlBuilder.append(", ");
            }
            sqlBuilder.append(" online = ? ");
            hasLast = true;
        }
        if(managerModel.getApiKey() != null) {
            if(hasLast) {
                sqlBuilder.append(", ");
            }
            sqlBuilder.append(" api_key = ? ");
            hasLast = true;
        }
        if (managerModel.getModelType() != null) {
            if(hasLast) {
                sqlBuilder.append(", ");
            }
            sqlBuilder.append(" model_type = ? ");
            hasLast = true;
        }
        if(managerModel.getEndpoint() != null) {
            if(hasLast) {
                sqlBuilder.append(", ");
            }
            sqlBuilder.append(" endpoint = ? ");
            hasLast = true;
        }
        if(managerModel.getStatus() != null) {
            if(hasLast) {
                sqlBuilder.append(", ");
            }
            sqlBuilder.append(" status = ? ");
        }
        sqlBuilder.append(" WHERE id = ?");
        return sqlBuilder.toString();
    }

    // Get all ManagerModels
    public List<ManagerModel> getManagerModels(String userId) {
        String sql = "SELECT * FROM model_manager WHERE user_id = ?";
        List<ManagerModel> managerModels = new ArrayList<>();
        try (Connection conn = new Conn();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ) {
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                ManagerModel managerModel = new ManagerModel();
                managerModel.setId(rs.getInt("id"));
                managerModel.setUserId(rs.getString("user_id"));
                managerModel.setModelName(rs.getString("model_name"));
                managerModel.setOnline(rs.getInt("online"));
                managerModel.setApiKey(rs.getString("api_key"));
                managerModel.setModelType(rs.getString("model_type"));
                managerModel.setEndpoint(rs.getString("endpoint"));
                managerModel.setStatus(rs.getInt("status"));
                managerModels.add(managerModel);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return managerModels;
    }


    public List<ManagerModel> getManagerModels(String userId, Integer status) {
        String sql = "SELECT * FROM model_manager WHERE user_id = ? and status = ?";
        List<ManagerModel> managerModels = new ArrayList<>();
        try (Connection conn = new Conn();
             PreparedStatement pstmt = conn.prepareStatement(sql);
        ) {
            pstmt.setString(1, userId);
            pstmt.setInt(2, status);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                ManagerModel managerModel = new ManagerModel();
                managerModel.setId(rs.getInt("id"));
                managerModel.setUserId(rs.getString("user_id"));
                managerModel.setModelName(rs.getString("model_name"));
                managerModel.setOnline(rs.getInt("online"));
                managerModel.setApiKey(rs.getString("api_key"));
                managerModel.setModelType(rs.getString("model_type"));
                managerModel.setEndpoint(rs.getString("endpoint"));
                managerModel.setStatus(rs.getInt("status"));
                managerModels.add(managerModel);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return managerModels;
    }

    public List<ILlmAdapter> getUserLlmAdapters(String userId) {
        // TODO 2025/3/4  support invoke remote service
        ManagerDao managerDao = new ManagerDao();
        List<ManagerModel> managerModels = managerDao.getManagerModels(userId, 1);
        return managerModels.stream().map(m -> {
            return LlmAdapterFactory.getLlmAdapter(m.getModelType(), m.getModelName(), 999, m.getApiKey(), m.getEndpoint());
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }
    
}
