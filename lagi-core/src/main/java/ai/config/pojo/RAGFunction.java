package ai.config.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Data
public class RAGFunction {
    private String  vector;
    private String term;
    private String graph;
    private Boolean enable;
    private Integer priority;
    @JsonProperty("default")
    private String defaultText;
    private Boolean track;
    private Boolean html;
    @JsonProperty("cache_size")
    private Integer cacheSize;
    @JsonProperty("preload_cache")
    private Boolean preloadCache;
    @JsonProperty("preload_cache_category")
    private String preloadCacheCategory;
}
