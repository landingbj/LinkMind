package ai.database.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SQLJdbc {
    private String name;
    @JsonProperty("jdbc_url")
    private String jdbcUrl;
    @JsonProperty("driver")
    private String driver;
    private String username;
    private String password;
    @JsonProperty("maximum_pool_size")
    private Integer maximumPoolSize;
    @JsonProperty("idle_timeout")
    private Long idleTimeout;
    @JsonProperty("max_lifetime")
    private Long maxLifetime;
}
