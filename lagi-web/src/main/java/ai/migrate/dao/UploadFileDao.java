package ai.migrate.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import ai.common.pojo.UserRagSetting;
import ai.dao.IConn;
import ai.index.BaseIndex;
import ai.migrate.db.Conn;
import ai.common.pojo.UploadFile;

public class UploadFileDao {
    public int deleteUploadFile(String category) throws SQLException {
        IConn conn = new Conn();
        int result = -1;
        String sql = "DELETE FROM lagi_upload_file WHERE category = ?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, category);
        result = ps.executeUpdate();
        BaseIndex.closeConnection(ps);
        return result;
    }

    public int deleteUploadFile(String fileId, Conn conn) throws SQLException {
        int result = -1;
        String sql = "DELETE FROM lagi_upload_file WHERE file_id = ?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, fileId);
        result = ps.executeUpdate();
        BaseIndex.closeConnection(ps);
        return result;
    }

    public int addUploadFile(UploadFile entity) throws SQLException {
        IConn conn = new Conn();
        int result = -1;
        String sql = "INSERT INTO lagi_upload_file (file_id, filename, filepath, category, create_time, user_id, knowledge_base_id) VALUES (?, ?, ?, ?, ?, ?, ?)";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, entity.getFileId());
        ps.setString(2, entity.getFilename());
        ps.setString(3, entity.getFilepath());
        ps.setString(4, entity.getCategory());
        ps.setLong(5, entity.getCreateTime());
        ps.setString(6, entity.getUserId());
        if (entity.getKnowledgeBaseId() == null) {
            ps.setNull(7, java.sql.Types.NULL);
        } else {
            ps.setLong(7, entity.getKnowledgeBaseId());
        }
        result = ps.executeUpdate();
        BaseIndex.closeConnection(ps, conn);
        return result;
    }
    
    public UploadFile getUploadFileList(String fileId, Conn conn) throws SQLException {
        UploadFile result = null;
        String sql = "select file_id, filename, filepath, category, create_time from lagi_upload_file where file_id = ?";
        PreparedStatement ps = null;
        ResultSet rs = null;
        ps = conn.prepareStatement(sql);
        ps.setString(1, fileId);
        rs = ps.executeQuery();
        while (rs.next()) {
            result = new UploadFile();
            result.setFileId(rs.getString(1));
            result.setFilename(rs.getString(2));
            result.setFilepath(rs.getString(3));
            result.setCategory(rs.getString(4));
            result.setCreateTime(rs.getLong(5));
        }
        BaseIndex.closeConnection(rs, ps);
        return result;
    }
    
    public List<UploadFile> getUploadFileList(int pageNumber, int pageSize) throws SQLException {
        IConn conn = new Conn();
        List<UploadFile> result = new ArrayList<>();
        String sql = "select file_id, filename, filepath, category, create_time from lagi_upload_file limit ?,?";
        PreparedStatement ps = null;
        ResultSet rs = null;
        ps = conn.prepareStatement(sql);
        ps.setInt(1, (pageNumber - 1) * pageSize);
        ps.setInt(2, pageSize);
        rs = ps.executeQuery();
        while (rs.next()) {
            UploadFile entity = new UploadFile();
            entity.setFileId(rs.getString(1));
            entity.setFilename(rs.getString(2));
            entity.setFilepath(rs.getString(3));
            entity.setCategory(rs.getString(4));
            entity.setCreateTime(rs.getLong(5));
            result.add(entity);
        }
        BaseIndex.closeConnection(rs, ps, conn);
        return result;
    }

    public List<UploadFile> getUploadFileList(int pageNumber, int pageSize, String category) throws SQLException {
        IConn conn = new Conn();
        List<UploadFile> result = new ArrayList<>();
        String sql = "select file_id, filename, filepath, category, create_time from lagi_upload_file where category=? limit ?,?";
        PreparedStatement ps = null;
        ResultSet rs = null;
        ps = conn.prepareStatement(sql);
        ps.setString(1, category);
        ps.setInt(2, (pageNumber - 1) * pageSize);
        ps.setInt(3, pageSize);
        rs = ps.executeQuery();
        while (rs.next()) {
            UploadFile entity = new UploadFile();
            entity.setFileId(rs.getString(1));
            entity.setFilename(rs.getString(2));
            entity.setFilepath(rs.getString(3));
            entity.setCategory(rs.getString(4));
            entity.setCreateTime(rs.getLong(5));
            result.add(entity);
        }
        BaseIndex.closeConnection(rs, ps, conn);
        return result;
    }


    public List<UploadFile> getUploadFileList(int pageNumber, int pageSize, String category, String userId, String knowledgeBaseId) throws SQLException {
        IConn conn = new Conn();
        List<UploadFile> result = new ArrayList<>();
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("select file_id, filename, filepath, category, create_time, user_id from lagi_upload_file where 1=1 ");
        if(category != null) {
            stringBuilder.append("and  category=? ");
        }
        if(userId != null) {
            stringBuilder.append("and  user_id=? ");
        }
        if(knowledgeBaseId != null) {
            stringBuilder.append("and  knowledge_base_id=? ");
        }
        stringBuilder.append("limit ?,?");
        String sql = stringBuilder.toString();
        PreparedStatement ps = null;
        ResultSet rs = null;
        ps = conn.prepareStatement(sql);
        int i = 1;
        if(category != null) {
            ps.setString(i++, category);
        }
        if(userId != null) {
            ps.setString(i++, userId);
        }
        if(knowledgeBaseId != null) {
            ps.setString(i++, knowledgeBaseId);
        }
        ps.setInt(i++, (pageNumber - 1) * pageSize);
        ps.setInt(i, pageSize);
        rs = ps.executeQuery();
        while (rs.next()) {
            UploadFile entity = new UploadFile();
            entity.setFileId(rs.getString(1));
            entity.setFilename(rs.getString(2));
            entity.setFilepath(rs.getString(3));
            entity.setCategory(rs.getString(4));
            entity.setCreateTime(rs.getLong(5));
            entity.setUserId(rs.getString(6));
            result.add(entity);
        }
        BaseIndex.closeConnection(rs, ps, conn);
        return result;
    }


    public int getTotalRow() throws SQLException {
        Conn conn = new Conn();
        int result = -1;
        String sql = "SELECT count(*) FROM lagi_upload_file";
        PreparedStatement ps = null;
        ResultSet rs = null;
        ps = conn.prepareStatement(sql);
        rs = ps.executeQuery();
        while (rs.next()) {
            result = rs.getInt(1);
        }
        BaseIndex.closeConnection(rs, ps, conn);
        return result;
    }
    
    public int getTotalRow(String category, String userId, String knowledgeBaseId) throws SQLException {
        Conn conn = new Conn();
        int result = -1;
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("SELECT count(*) FROM lagi_upload_file where 1=1 ");
        if(category != null) {
            stringBuilder.append("and  category=? ");
        }
        if(userId != null) {
            stringBuilder.append("and  user_id=? ");
        }
        if(knowledgeBaseId != null) {
            stringBuilder.append("and  knowledge_base_id=? ");
        }
        String sql = stringBuilder.toString();
        PreparedStatement ps = null;
        ResultSet rs = null;
        ps = conn.prepareStatement(sql);
        int i = 1;
        if(category != null) {
            ps.setString(i++, category);
        }
        if(userId != null) {
            ps.setString(i++, userId);
        }
        if(knowledgeBaseId != null) {
            ps.setString(i++, knowledgeBaseId);
        }
        rs = ps.executeQuery();
        while (rs.next()) {
            result = rs.getInt(1);
        }
        BaseIndex.closeConnection(rs, ps, conn);
        return result;
    }

    public List<UserRagSetting> getTextBlockSize(String category, String userId) throws SQLException {
        List<UserRagSetting> result = new ArrayList<>();
        IConn conn = new Conn();
        String sql = "select id, user_id, file_type, category, chunk_size, temperature from user_rag_settings where category = ? and user_id = ?";
        PreparedStatement ps = null;
        ResultSet rs = null;
        ps = conn.prepareStatement(sql);
        ps.setString(1, category);
        ps.setString(2, userId);
        rs = ps.executeQuery();
        while (rs.next()) {
            UserRagSetting userRagSetting = new UserRagSetting();
            userRagSetting.setId(rs.getInt(1));
            userRagSetting.setUserId(rs.getString(2));
            userRagSetting.setFileType(rs.getString(3));
            userRagSetting.setCategory(rs.getString(4));
            userRagSetting.setChunkSize(rs.getInt(5));
            userRagSetting.setTemperature(rs.getDouble(6));
            result.add(userRagSetting);
        }
        BaseIndex.closeConnection(rs, ps, conn);
        return result;
    }

    public int addTextBlockSize(UserRagSetting entity) throws SQLException {
        IConn conn = new Conn();
        int result = -1;
        String sql = "INSERT INTO user_rag_settings (user_id, file_type, category, chunk_size, temperature) VALUES (?, ?, ?, ?, ?)";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, entity.getFileType());
        ps.setString(2, entity.getCategory());
        ps.setInt(3, entity.getChunkSize());
        ps.setDouble(4, entity.getTemperature());
        result = ps.executeUpdate();
        BaseIndex.closeConnection(ps, conn);
        return result;
    }

    public int updateTextBlockSize(UserRagSetting entity) throws SQLException {
        int result = -1;
        IConn conn = new Conn();
        if (exists(entity)) {
            // 记录存在，执行更新操作
            String sqlUpdate = "UPDATE user_rag_settings SET chunk_size = ?, temperature = ? WHERE user_id = ? AND file_type = ? AND category = ?";
            PreparedStatement psUpdate = conn.prepareStatement(sqlUpdate);
            if (entity.getChunkSize() != null){
                psUpdate.setInt(1, entity.getChunkSize());
            }else {
                psUpdate.setInt(1, 512);
            }
            if (entity.getTemperature() != null){
                psUpdate.setDouble(2, entity.getTemperature());
            }else {
                psUpdate.setDouble(2, 0.8);
            }
            psUpdate.setString(3, entity.getUserId());
            psUpdate.setString(4, entity.getFileType());
            psUpdate.setString(5, entity.getCategory());

            result = psUpdate.executeUpdate();
            BaseIndex.closeConnection(psUpdate, conn);
            return result;
        } else {
            String sql = "INSERT INTO user_rag_settings (user_id, file_type, category, chunk_size, temperature) VALUES (?, ?, ?, ?, ?)";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, entity.getUserId());
            ps.setString(2, entity.getFileType());
            ps.setString(3, entity.getCategory());
            if (entity.getChunkSize() != null){
                ps.setInt(4, entity.getChunkSize());
            }else {
                ps.setInt(4, 512);
            }
            if (entity.getTemperature() != null){
                ps.setDouble(5, entity.getTemperature());
            }else {
                ps.setDouble(5, 0.8);
            }

            result = ps.executeUpdate();
            BaseIndex.closeConnection(ps, conn);
            return result;
        }

    }

    public int deleteTextBlockSize(UserRagSetting entity) throws SQLException {
        int result = -1;
        IConn conn = new Conn();
        StringBuilder sql = new StringBuilder();
        sql.append("DELETE FROM user_rag_settings WHERE user_id = ?  AND category = ?  ");
        boolean hasFileType = false;
        if(entity.getFileType() != null) {
            sql.append("AND file_type = ? ");
            hasFileType = true;
        }
        PreparedStatement ps = conn.prepareStatement(sql.toString());
        ps.setString(1, entity.getUserId());
        ps.setString(2, entity.getCategory());
        if (hasFileType){
            ps.setString(3, entity.getFileType());
        }
        result = ps.executeUpdate();
        BaseIndex.closeConnection(ps, conn);
        return result;
    }

    private boolean exists(UserRagSetting entity) throws SQLException {
        IConn conn = new Conn();
        String sql = "SELECT COUNT(*) FROM user_rag_settings WHERE category = ? AND user_id = ? AND file_type = ?";
        PreparedStatement ps = null;
        ResultSet rs = null;
        boolean result = false;

        try {
            ps = conn.prepareStatement(sql);
            ps.setString(1, entity.getCategory());
            ps.setString(2, entity.getUserId());
            ps.setString(3, entity.getFileType());
            rs = ps.executeQuery();

            if (rs.next()) {
                int count = rs.getInt(1);
                result = count > 0;
            }
        } finally {
            BaseIndex.closeConnection(rs, ps, conn);
        }

        return result;
    }

}
