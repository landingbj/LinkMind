package ai.common.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Medusa {
    private Boolean enable;
    private Boolean cache = true;
    private Boolean memory = false;
    private String algorithm;
    private Boolean enableL2;
    private Boolean enableReasonDiver;
    private Long consumeDelay;
    private Long preDelay;
    private Double lcsRatioPromptInput;
    private Double similarityCutoff;
    private String inits;
    @JsonProperty("reason_model")
    private String reasonModel;
    @JsonProperty("producer_thread_num")
    private Integer producerThreadNum;
    @JsonProperty("consumer_thread_num")
    private Integer consumerThreadNum;
    @JsonProperty("aheads")
    private Integer aheads;
    private Integer priority;
    @JsonProperty("qa_similarity_cutoff")
    private Double qaSimilarityCutoff;
    @JsonProperty("cache_persistent_path")
    private String cachePersistentPath;
    @JsonProperty("cache_persistent_batch_size")
    private Integer cachePersistentBatchSize;
    @JsonProperty("flush")
    private Boolean flush;
    @JsonProperty("cache_hit_window")
    private Integer cacheHitWindow;
    @JsonProperty("cache_hit_ratio")
    private Double cacheHitRatio;
    @JsonProperty("dynamic_similarity")
    private Boolean dynamicSimilarity;
    @JsonProperty("temperature_tolerance")
    private Double temperatureTolerance;
}
