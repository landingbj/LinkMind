package ai.servlet.api;

import ai.common.pojo.Configuration;
import ai.embedding.EmbeddingFactory;
import ai.embedding.Embeddings;
import ai.embedding.pojo.OpenAIEmbeddingRequest;
import ai.embedding.pojo.OpenAIEmbeddingResponse;
import ai.servlet.BaseServlet;
import ai.utils.MigrateGlobal;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EmbeddingApiServlet extends BaseServlet {
    private static final Configuration config = MigrateGlobal.config;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setHeader("Content-Type", "application/json;charset=utf-8");
        String url = req.getRequestURI();
        String method = url.substring(url.lastIndexOf("/") + 1);
        if (method.equals("embeddings")) {
            this.embeddings(req, resp);
        }
    }

    private void embeddings(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        long startedAt = System.nanoTime();
        OpenAIEmbeddingRequest request = gson.fromJson(requestToJson(req), OpenAIEmbeddingRequest.class);
        int inputCount = request.getInput() == null ? 0 : request.getInput().size();
        String model = request.getModel();
        log.info("embedding.request.start model={} input_count={}", model, inputCount);
        try {
            Embeddings embeddings = EmbeddingFactory.getEmbedding(config.getLLM().getEmbedding());
            List<List<Float>> embeddingDataList = embeddings.createEmbedding(request.getInput());
            OpenAIEmbeddingResponse response = new OpenAIEmbeddingResponse();
            response.setObject("list");
            response.setModel(model);

            List<OpenAIEmbeddingResponse.EmbeddingData> dataList = new ArrayList<>();
            for (int i = 0; i < embeddingDataList.size(); i++) {
                OpenAIEmbeddingResponse.EmbeddingData embeddingData = new OpenAIEmbeddingResponse.EmbeddingData();
                embeddingData.setObject("embedding");
                embeddingData.setEmbedding(embeddingDataList.get(i));
                embeddingData.setIndex(i);
                dataList.add(embeddingData);
            }
            response.setData(dataList);
            responsePrint(resp, toJson(response));
            log.info("embedding.request.complete model={} input_count={} output_count={} duration_ms={}",
                    model, inputCount, embeddingDataList.size(), (System.nanoTime() - startedAt) / 1_000_000L);
        } catch (RuntimeException e) {
            log.error("embedding.request.failed model={} input_count={} duration_ms={}",
                    model, inputCount, (System.nanoTime() - startedAt) / 1_000_000L, e);
            throw e;
        }
    }
}
