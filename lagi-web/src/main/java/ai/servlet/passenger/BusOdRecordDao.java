package ai.servlet.passenger;

import java.sql.*;
import java.time.LocalDateTime;
/**
 * 入库操作（当前不使用，链路最终只需发送Kafka）。
 *
 */
public class BusOdRecordDao {

	public void save(BusOdRecord record) throws SQLException {
		String sql = "INSERT INTO ads.bus_od_record (date, timestamp_begin, timestamp_end, bus_no, camera_no, " +
				"line_id, direction, station_id_on, station_name_on, station_id_off, " +
				"station_name_off, passenger_vector, up_count, down_count, gps_lat, gps_lng, " +
				"counting_image, passenger_position, full_load_rate, feature_description, " +
				"total_count, ticket_count, data_source, created_at, bus_id) " +
				"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
		try (Connection conn = DriverManager.getConnection(Config.getDbUrl(), Config.getDbUser(), Config.getDbPassword());
			 PreparedStatement pstmt = conn.prepareStatement(sql)) {

			pstmt.setDate(1, Date.valueOf(record.getDate()));
			pstmt.setTimestamp(2, Timestamp.valueOf(record.getTimestampBegin()));
			pstmt.setTimestamp(3, record.getTimestampEnd() != null ? Timestamp.valueOf(record.getTimestampEnd()) : null);
			pstmt.setString(4, record.getBusNo());
			pstmt.setString(5, record.getCameraNo());
			pstmt.setString(6, record.getLineId());
			pstmt.setString(7, record.getDirection());
			pstmt.setString(8, record.getStationIdOn());
			pstmt.setString(9, record.getStationNameOn());
			pstmt.setString(10, record.getStationIdOff());
			pstmt.setString(11, record.getStationNameOff());
			pstmt.setString(12, record.getPassengerVector());
			pstmt.setObject(13, record.getUpCount(), java.sql.Types.INTEGER);
			pstmt.setObject(14, record.getDownCount(), java.sql.Types.INTEGER);
			pstmt.setBigDecimal(15, record.getGpsLat());
			pstmt.setBigDecimal(16, record.getGpsLng());
			pstmt.setString(17, record.getCountingImage());
			pstmt.setString(18, record.getPassengerPosition());
			pstmt.setBigDecimal(19, record.getFullLoadRate());
			pstmt.setString(20, record.getFeatureDescription());
			pstmt.setObject(21, record.getTotalCount(), java.sql.Types.INTEGER);
			pstmt.setObject(22, record.getTicketCount(), java.sql.Types.INTEGER);
			pstmt.setString(23, record.getDataSource());
			pstmt.setTimestamp(24, Timestamp.valueOf(LocalDateTime.now()));
			pstmt.setLong(25, record.getBusId() != null ? record.getBusId() : 0L);

			pstmt.executeUpdate();
		}
	}
}
