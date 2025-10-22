package ai.servlet.dto;

import lombok.Data;
import java.util.List;

@Data
public class DatasetListResponse {
    private int total;
    private List<DatasetInfo> datasets;

    @Data
    public static class DatasetInfo {
        private String name;
        private String path;
        private String created_at;
        private int sample_count;
        private String description;
        private String user_id;
    }
}
