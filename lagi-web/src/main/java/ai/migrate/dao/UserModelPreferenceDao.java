package ai.migrate.dao;

import ai.dto.ModelPreferenceDto;
import ai.migrate.db.Conn;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserModelPreferenceDao {

    private final Logger log = LoggerFactory.getLogger(UserModelPreferenceDao.class);
    private static final String DB_URL = "jdbc:sqlite:preference2.db";
    private static HikariDataSource dataSource;

    static {
        String sql = "CREATE TABLE IF NOT EXISTS lagi_user_preference (\n" +
                "    finger TEXT PRIMARY KEY,\n" +
                "    userId TEXT,\n" +
                "    llm TEXT,\n" +
                "    tts TEXT,\n" +
                "    asr TEXT,\n" +
                "    img2Text TEXT,\n" +
                "    imgGen TEXT,\n" +
                "    imgEnhance TEXT,\n" +
                "    img2Video TEXT,\n" +
                "    text2Video TEXT,\n" +
                "    videoEnhance TEXT,\n" +
                "    videoTrack TEXT\n" +
                ")";

        try {
            initializeDataSource();
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        } catch (SQLException e) {
            e.printStackTrace();
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

    public ModelPreferenceDto getUserModelPreference(String finger)  {
        String sqlCheck = "SELECT finger, llm, tts, asr, img2Text, imgGen, imgEnhance, img2Video, text2Video, videoEnhance, videoTrack  FROM lagi_user_preference WHERE finger = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlCheck)) {
            ps.setString(1, finger);
            ResultSet rs = ps.executeQuery();
            ModelPreferenceDto modelPreferenceDto = new ModelPreferenceDto();
            modelPreferenceDto.setFinger(rs.getString(1));
            modelPreferenceDto.setLlm(rs.getString(2));
            modelPreferenceDto.setTts(rs.getString(3));
            modelPreferenceDto.setAsr(rs.getString(4));
            modelPreferenceDto.setImg2Text(rs.getString(5));
            modelPreferenceDto.setImgGen(rs.getString(6));
            modelPreferenceDto.setImgEnhance(rs.getString(7));
            modelPreferenceDto.setImg2Video(rs.getString(8));
            modelPreferenceDto.setText2Video(rs.getString(9));
            modelPreferenceDto.setVideoEnhance(rs.getString(10));
            modelPreferenceDto.setVideoTrack(rs.getString(11));
            return modelPreferenceDto;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // 根据finger查询所有记录
    public List<ModelPreferenceDto> findByFinger(String finger) {
        List<ModelPreferenceDto> result = new ArrayList<>();
        String sql = "SELECT * FROM lagi_user_preference WHERE finger = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, finger);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                result.add(ModelPreferenceDto.builder()
                        .finger(rs.getString("finger"))
                        .userId(rs.getString("userId"))
                        .llm(rs.getString("llm"))
                        .tts(rs.getString("tts"))
                        .asr(rs.getString("asr"))
                        .img2Text(rs.getString("img2Text"))
                        .imgGen(rs.getString("imgGen"))
                        .imgEnhance(rs.getString("imgEnhance"))
                        .img2Video(rs.getString("img2Video"))
                        .text2Video(rs.getString("text2Video"))
                        .videoEnhance(rs.getString("videoEnhance"))
                        .videoTrack(rs.getString("videoTrack"))
                        .build());
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    // 根据finger删除数据
    public int deleteByFinger(String finger) {
        String sql = "DELETE FROM lagi_user_preference WHERE finger = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, finger);
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // 保存或更新数据，只更新非空字段
    public int saveOrUpdate(ModelPreferenceDto dto) {
        if (exists(dto.getFinger())) {
            return updateNonNullFields(dto);
        } else {
           return insert(dto);
        }
    }

    // 检查记录是否存在
    private boolean exists(String finger) {
        String sql = "SELECT COUNT(*) FROM lagi_user_preference WHERE finger = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, finger);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // 插入新记录
    private int insert(ModelPreferenceDto dto) {
        String sql = "INSERT INTO lagi_user_preference (finger, userId, llm, tts, asr, img2Text, " +
                "imgGen, imgEnhance, img2Video, text2Video, videoEnhance, videoTrack) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, dto.getFinger());
            pstmt.setString(2, dto.getUserId());
            pstmt.setString(3, dto.getLlm());
            pstmt.setString(4, dto.getTts());
            pstmt.setString(5, dto.getAsr());
            pstmt.setString(6, dto.getImg2Text());
            pstmt.setString(7, dto.getImgGen());
            pstmt.setString(8, dto.getImgEnhance());
            pstmt.setString(9, dto.getImg2Video());
            pstmt.setString(10, dto.getText2Video());
            pstmt.setString(11, dto.getVideoEnhance());
            pstmt.setString(12, dto.getVideoTrack());

           return  pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    // 更新非空字段
    private int updateNonNullFields(ModelPreferenceDto dto) {
        StringBuilder sqlBuilder = new StringBuilder("UPDATE lagi_user_preference SET ");
        List<Object> params = new ArrayList<>();

        if (dto.getUserId() != null) {
            sqlBuilder.append("userId = ?, ");
            params.add(dto.getUserId());
        }
        if (dto.getLlm() != null) {
            sqlBuilder.append("llm = ?, ");
            params.add(dto.getLlm());
        }
        if (dto.getTts() != null) {
            sqlBuilder.append("tts = ?, ");
            params.add(dto.getTts());
        }
        if (dto.getAsr() != null) {
            sqlBuilder.append("asr = ?, ");
            params.add(dto.getAsr());
        }
        if (dto.getImg2Text() != null) {
            sqlBuilder.append("img2Text = ?, ");
            params.add(dto.getImg2Text());
        }
        if (dto.getImgGen() != null) {
            sqlBuilder.append("imgGen = ?, ");
            params.add(dto.getImgGen());
        }
        if (dto.getImgEnhance() != null) {
            sqlBuilder.append("imgEnhance = ?, ");
            params.add(dto.getImgEnhance());
        }
        if (dto.getImg2Video() != null) {
            sqlBuilder.append("img2Video = ?, ");
            params.add(dto.getImg2Video());
        }
        if (dto.getText2Video() != null) {
            sqlBuilder.append("text2Video = ?, ");
            params.add(dto.getText2Video());
        }
        if (dto.getVideoEnhance() != null) {
            sqlBuilder.append("videoEnhance = ?, ");
            params.add(dto.getVideoEnhance());
        }
        if (dto.getVideoTrack() != null) {
            sqlBuilder.append("videoTrack = ?, ");
            params.add(dto.getVideoTrack());
        }

        // 移除最后一个逗号
        if (params.size() > 0) {
            sqlBuilder.delete(sqlBuilder.length() - 2, sqlBuilder.length());
        }

        sqlBuilder.append(" WHERE finger = ?");
        params.add(dto.getFinger());

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sqlBuilder.toString())) {

            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }

            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }
    public static void main(String[] args) {
        ModelPreferenceDto dto = ModelPreferenceDto.builder().finger("aaa").llm("gpt").asr("asr").build();
        UserModelPreferenceDao userModelPreferenceDao = new UserModelPreferenceDao();
        userModelPreferenceDao.saveOrUpdate(dto);
    }
}
