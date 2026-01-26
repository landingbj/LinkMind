//package ai.embedding.impl;
//
//import ai.common.pojo.EmbeddingConfig;
//import ai.embedding.Embeddings;
//import com.zhipu.oapi.ClientV4;
//import com.zhipu.oapi.service.v4.embedding.EmbeddingApiResponse;
//import com.zhipu.oapi.service.v4.embedding.EmbeddingRequest;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.stream.Collectors;
//
//public class ZhipuEmbeddings implements Embeddings {
//    private String apiKey;
//    private String modelName;
//    private final ClientV4 client;
//
//    public ZhipuEmbeddings(EmbeddingConfig config) {
//        this.apiKey = config.getApi_key();
//        this.modelName = config.getModel_name();
//        client = new ClientV4.Builder(this.apiKey).build();
//    }
//
//    @Override
//    public List<List<Float>> createEmbedding(List<String> docs) {
//        List<List<Float>> result = new ArrayList<>();
//        for (String doc : docs) {
//            result.add(createEmbedding(doc));
//        }
//        return result;
//    }
//
//    @Override
//    public List<Float> createEmbedding(String doc) {
//        EmbeddingRequest embeddingRequest = new EmbeddingRequest();
//        embeddingRequest.setInput(doc);
//        embeddingRequest.setModel(this.modelName);
//        EmbeddingApiResponse apiResponse = client.invokeEmbeddingsApi(embeddingRequest);
//        List<Double> doubleEmbedding = apiResponse.getData().getData().get(0).getEmbedding();
//        return doubleEmbedding.stream()
//                .map(Double::floatValue)
//                .collect(Collectors.toList());
//    }
//}
