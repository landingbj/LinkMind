//package ai.embedding.impl;
//
//import ai.common.pojo.EmbeddingConfig;
//import ai.embedding.Embeddings;
//import ai.embedding.pojo.OpenAIEmbeddingRequest;
//import ai.embedding.pojo.OpenAIEmbeddingResponse;
//import com.auth0.jwt.JWT;
//import com.auth0.jwt.algorithms.Algorithm;
//import com.google.gson.Gson;
//import okhttp3.*;
//
//import java.io.IOException;
//import java.util.*;
//
//public class SenseChatEmbeddings  implements Embeddings {
//    private final Gson gson = new Gson();
//    private String token;
//    private String apiKey;
//    private String secretKey;
//    private String modelName;
//    private String apiEndpoint;
//
//    public SenseChatEmbeddings(EmbeddingConfig config) {
//        this.apiKey = config.getApi_key();
//        this.secretKey = config.getSecret_key();
//        this.token = sign(apiKey,secretKey);
//        this.modelName = config.getModel_name();
//        this.apiEndpoint = config.getApi_endpoint();
//    }
//
//    @Override
//    public List<List<Float>> createEmbedding(List<String> docs) {
//        List<List<Float>> result = new ArrayList<>();
//        try {
//            OkHttpClient client = new OkHttpClient();
//            MediaType JSON = MediaType.get("application/json; charset=utf-8");
//            OpenAIEmbeddingRequest openAIEmbeddingRequest = new OpenAIEmbeddingRequest();
//            openAIEmbeddingRequest.setInput(docs);
//            openAIEmbeddingRequest.setModel(this.modelName);
//            String jsonBody = gson.toJson(openAIEmbeddingRequest);
//            RequestBody body = RequestBody.create(jsonBody, JSON);
//            Request request = new Request.Builder()
//                    .url(apiEndpoint)
//                    .addHeader("Authorization", "Bearer " + token)
//                    .post(body)
//                    .build();
//            try (Response response = client.newCall(request).execute()) {
//                if (!response.isSuccessful()) {
//                    throw new RuntimeException("Unexpected code " + response);
//                }
//                OpenAIEmbeddingResponse openAIEmbeddingResponse = gson.fromJson(response.body().string(), OpenAIEmbeddingResponse.class);
//                for (OpenAIEmbeddingResponse.EmbeddingData data : openAIEmbeddingResponse.getData()) {
//                    result.add(data.getEmbedding());
//                }
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        return result;
//    }
//
//    @Override
//    public List<Float> createEmbedding(String doc) {
//        List<String> docs = new ArrayList<>();
//        docs.add(doc);
//        List<List<Float>> result = createEmbedding(docs);
//        if (!result.isEmpty()) {
//            return result.get(0);
//        }
//        return null;
//    }
//    static String sign(String ak,String sk) {
//        try {
//            Date expiredAt = new Date(System.currentTimeMillis() + 1800*1000);
//            Date notBefore = new Date(System.currentTimeMillis() - 5*1000);
//            Algorithm algo = Algorithm.HMAC256(sk);
//            Map<String, Object> header = new HashMap<String, Object>();
//            header.put("alg", "HS256");
//            return JWT.create()
//                    .withIssuer(ak)
//                    .withHeader(header)
//                    .withExpiresAt(expiredAt)
//                    .withNotBefore(notBefore)
//                    .sign(algo);
//        } catch (Exception e) {
//            e.printStackTrace();
//            return null;
//        }
//    }
//}
