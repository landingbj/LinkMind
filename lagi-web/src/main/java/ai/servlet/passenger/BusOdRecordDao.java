package ai.servlet.passenger;

import java.sql.*;

public class BusOdRecordDao {
    public void save(BusOdRecord record) throws SQLException {
        String sql = "INSERT INTO ads.bus_od_record (id, date, timestamp_begin, timestamp_end, bus_no, camera_no, " +
                     "line_id, direction, trip_no, station_id_on, station_name_on, station_id_off, " +
                     "station_name_off, passenger_vector, up_count, down_count, gps_lat, gps_lng, " +
                     "counting_image, passenger_position, full_load_rate, feature_description, " +
                     "total_count, ticket_count, data_source, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(Config.getDbUrl(), Config.getDbUser(), Config.getDbPassword());
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, record.getId());
            pstmt.setDate(2, Date.valueOf(record.getDate()));
            pstmt.setTimestamp(3, Timestamp.valueOf(record.getTimestampBegin()));
            pstmt.setTimestamp(4, Timestamp.valueOf(record.getTimestampEnd()));
            pstmt.setString(5, record.getBusNo());
            pstmt.setString(6, record.getCameraNo());
            pstmt.setString(7, record.getLineId());
            pstmt.setString(8, record.getDirection());
            pstmt.setString(9, record.getTripNo());
            pstmt.setString(10, record.getStationIdOn());
            pstmt.setString(11, record.getStationNameOn());
            pstmt.setString(12, record.getStationIdOff());
            pstmt.setString(13, record.getStationNameOff());
            pstmt.setString(14, record.getPassengerVector());
            pstmt.setObject(15, record.getUpCount(), Types.INTEGER);
            pstmt.setObject(16, record.getDownCount(), Types.INTEGER);
            pstmt.setBigDecimal(17, record.getGpsLat());
            pstmt.setBigDecimal(18, record.getGpsLng());
            pstmt.setString(19, record.getCountingImage());
            pstmt.setString(20, record.getPassengerPosition());
            pstmt.setBigDecimal(21, record.getFullLoadRate());
            pstmt.setString(22, record.getFeatureDescription());
            pstmt.setObject(23, record.getTotalCount(), Types.INTEGER);
            pstmt.setObject(24, record.getTicketCount(), Types.INTEGER);
            pstmt.setString(25, record.getDataSource());
            pstmt.setTimestamp(26, Timestamp.valueOf(record.getCreatedAt()));
            
            pstmt.executeUpdate();
        }
    }
}