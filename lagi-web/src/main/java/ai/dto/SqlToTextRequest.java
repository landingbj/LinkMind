package ai.dto;
import lombok.*;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SqlToTextRequest {
    private String sql;
    private String demand;
    private String table;
    private String databaseName;//弃用
    private String storage;
}
