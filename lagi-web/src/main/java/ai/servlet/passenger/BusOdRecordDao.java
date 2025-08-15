package ai.servlet.passenger;

import java.sql.*;
import java.time.LocalDateTime;

public class BusOdRecordDao {

    public void save(BusOdRecord record) throws SQLException {
        String sql = "INSERT INTO ads.bus_od_record (date, timestamp_begin, timestamp_end, bus_no, camera_no, " +
                "line_id, direction, trip_no, station_id_on, station_name_on, station_id_off, " +
                "station_name_off, passenger_vector, up_count, down_count, gps_lat, gps_lng, " +
                "counting_image, passenger_position, full_load_rate, feature_description, " +
                "total_count, ticket_count, data_source, created_at, bus_id) " +  // 添加bus_id
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(Config.getDbUrl(), Config.getDbUser(), Config.getDbPassword());
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDate(1, Date.valueOf(record.getDate()));
            pstmt.setTimestamp(2, Timestamp.valueOf(record.getTimestampBegin()));
            pstmt.setTimestamp(3, record.getTimestampEnd() != null ? Timestamp.valueOf(record.getTimestampEnd()) : null);
            pstmt.setString(4, record.getBusNo());
            pstmt.setString(5, record.getCameraNo());
            pstmt.setString(6, record.getLineId());
            pstmt.setString(7, record.getDirection());
            pstmt.setString(8, record.getTripNo());
            pstmt.setString(9, record.getStationIdOn());
            pstmt.setString(10, record.getStationNameOn());
            pstmt.setString(11, record.getStationIdOff());
            pstmt.setString(12, record.getStationNameOff());
            pstmt.setString(13, record.getPassengerVector());
            pstmt.setObject(14, record.getUpCount(), java.sql.Types.INTEGER);
            pstmt.setObject(15, record.getDownCount(), java.sql.Types.INTEGER);
            pstmt.setBigDecimal(16, record.getGpsLat());
            pstmt.setBigDecimal(17, record.getGpsLng());
            pstmt.setString(18, record.getCountingImage());
            pstmt.setString(19, record.getPassengerPosition());
            pstmt.setBigDecimal(20, record.getFullLoadRate());
            pstmt.setString(21, record.getFeatureDescription());
            pstmt.setObject(22, record.getTotalCount(), java.sql.Types.INTEGER);
            pstmt.setObject(23, record.getTicketCount(), java.sql.Types.INTEGER);
            pstmt.setString(24, record.getDataSource());
            pstmt.setTimestamp(25, Timestamp.valueOf(LocalDateTime.now()));
            pstmt.setLong(26, record.getBusId() != null ? record.getBusId() : 0L);  // 新增bus_id

            pstmt.executeUpdate();
        }
    }
}