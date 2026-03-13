package ai.common.pojo;

import lombok.*;

import java.util.List;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Data
public class Backend {
    private String backend;
    private String name;
    private List<Driver> drivers;
    private String type;
    private Boolean enable;
    private Integer priority;
    private String model;
    private String deployment;
    private String apiVersion;
    private String driver;
    private String endpoint;
    private String apiAddress;
    private String appId;
    private String apiKey;
    private String secretKey;
    private String appKey;
    private String accessKeyId;
    private String accessKeySecret;
    private String securityKey;
    private Boolean stream;
    private String oss;
    private String accessToken;
    private String others;
    private String alias;
    private Boolean cacheEnable;
    private String cacheDir;
    private String router;
    private Boolean dependingOnTheContext;
    private String filter;
    private Integer concurrency;
    private String protocol;
    protected Boolean function;
}
