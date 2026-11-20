package ai.common.pojo;

import ai.router.utils.RouteGlobal;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.*;

import java.util.Objects;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> models;

    public boolean allowsChatModel(String upstreamId, String publicId) {
        if (models != null) {
            return models.stream().filter(Objects::nonNull).flatMap(value -> Arrays.stream(value.split(","))).map(String::trim)
                    .anyMatch(id -> id.equals(upstreamId) || id.equals(publicId));
        }
        return model == null || model.trim().isEmpty()
                || model.trim().equals(upstreamId) || model.trim().equals(publicId);
    }
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
    private Boolean stream = true;
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
    private String protocol = "completion";
    protected Boolean function;
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> apiKeys;
    private String keyRoute = RouteGlobal.POLLING;

    @JsonSetter("api_keys")
    public void setApiKeys(List<String> apiKeys) {
        if (apiKeys != null && apiKeys.size() == 1 && apiKeys.get(0).contains(",")) {
            this.apiKeys = Arrays.stream(apiKeys.get(0).split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        } else {
            this.apiKeys = apiKeys;
        }
        if (apiKey != null && !apiKey.isEmpty() && !apiKey.startsWith("your")) {
            if (this.apiKeys != null && !this.apiKeys.contains(apiKey)) {
                this.apiKeys.add(0, apiKey);
            }
        }
    }
}
