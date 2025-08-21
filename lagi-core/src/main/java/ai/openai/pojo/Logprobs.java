package ai.openai.pojo;

import lombok.Data;

import java.util.List;

@Data
public class Logprobs {
    private List<TokenInfo> content;
    private List<TokenInfo> refusal;

    @Data
    public static class TokenInfo {
        private List<Integer> bytes;
        private Double logprob;
        private String token;
        private List<TopLogprob> top_logprobs;
    }

    @Data
    public static class TopLogprob {
        private List<Integer> bytes;
        private Double logprob;
        private String token;
    }
}


