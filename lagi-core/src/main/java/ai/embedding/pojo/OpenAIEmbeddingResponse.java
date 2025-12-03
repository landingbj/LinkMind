package ai.embedding.pojo;

import lombok.Data;

import java.util.List;

@Data
public class OpenAIEmbeddingResponse {
    private String object;
    private List<EmbeddingData> data;
    private String model;
    private Usage usage;

    @Data
    public static class EmbeddingData {
        private String object;
        private List<Float> embedding;
        private int index;
    }

    @Data
    public static class Usage {
        private int prompt_tokens;
        private int total_tokens;
    }
}
