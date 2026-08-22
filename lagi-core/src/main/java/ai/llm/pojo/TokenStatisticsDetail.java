package ai.llm.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenStatisticsDetail {
    private long id;
    /** Record time (epoch millis). */
    private long createdAt;
    private long promptTokens;
    private long completionTokens;
    private long totalTokens;
    private long savedTokens;
    private String provider;
    private String model;
    private String sessionId;
    private String userId;
}
