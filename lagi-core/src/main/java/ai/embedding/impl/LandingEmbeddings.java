package ai.embedding.impl;

import ai.embedding.pojo.LagiEmbeddingResponse;
import ai.utils.ApikeyUtil;
import ai.embedding.EmbeddingConstant;
import ai.embedding.Embeddings;
import ai.embedding.pojo.OpenAIEmbeddingRequest;
import ai.embedding.pojo.OpenAIEmbeddingResponse;
import ai.common.pojo.EmbeddingConfig;
import com.google.common.cache.Cache;
import com.google.gson.Gson;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LandingEmbeddings implements Embeddings {
    private final Gson gson = new Gson();
    private static final Cache<List<String>, List<List<Float>>> cache = EmbeddingConstant.getEmbeddingCache();
    private final String apiKey;
    private static final String apiEndpoint = "https://ai.linkmind.top/v1/embeddings";

    public LandingEmbeddings(EmbeddingConfig config) {
        this.apiKey = config.getApi_key();
    }

    @Override
    public List<List<Float>> createEmbedding(List<String> docs) {
        if (!ApikeyUtil.isApiKeyValid(apiKey)) {
            throw new RuntimeException("Invalid landing embedding API key");
        }
        List<List<Float>> result = new ArrayList<>();
        try {
            OkHttpClient client = new OkHttpClient();
            MediaType JSON = MediaType.get("application/json; charset=utf-8");
            OpenAIEmbeddingRequest openAIEmbeddingRequest = new OpenAIEmbeddingRequest();
            openAIEmbeddingRequest.setInput(docs);
            String jsonBody = gson.toJson(openAIEmbeddingRequest);
            RequestBody body = RequestBody.create(jsonBody, JSON);
            Request request = new Request.Builder()
                    .url(apiEndpoint)
                    .post(body)
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Unexpected code " + response);
                }
                LagiEmbeddingResponse embeddingResponse = gson.fromJson(response.body().string(), LagiEmbeddingResponse.class);
                if (embeddingResponse != null && "success".equals(embeddingResponse.getStatus())) {
                    result = embeddingResponse.getData();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    @Override
    public List<Float> createEmbedding(String doc) {
        List<String> docs = Collections.singletonList(doc);
        List<List<Float>> result = createEmbedding(docs);
        if (!result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }
}
