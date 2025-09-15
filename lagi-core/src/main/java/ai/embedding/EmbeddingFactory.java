package ai.embedding;

import ai.embedding.impl.*;
import ai.common.pojo.EmbeddingConfig;
import ai.utils.LagiGlobal;

public class EmbeddingFactory {
    public static Embeddings getEmbedding(EmbeddingConfig config) {
        String type = config.getType();
        if (EmbeddingConstant.EMBEDDING_TYPE_OPENAI.equalsIgnoreCase(type)) {
            return new OpenAIEmbeddings(config);
        } else if (EmbeddingConstant.EMBEDDING_TYPE_ERNIE.equalsIgnoreCase(type)) {
            return new ErnieEmbeddings(config);
        } else if (EmbeddingConstant.EMBEDDING_TYPE_QWEN.equalsIgnoreCase(type)) {
            return new QwenEmbeddings(config);
        } else if (EmbeddingConstant.EMBEDDING_TYPE_VICUNA.equalsIgnoreCase(type)) {
            return new VicunaEmbeddings(config);
        } else if (EmbeddingConstant.EMBEDDING_TYPE_LANDING.equalsIgnoreCase(type) && config.getModel_path() == null) {
            return new LandingEmbeddings(config);
        } else if (EmbeddingConstant.EMBEDDING_TYPE_ZHIPU.equalsIgnoreCase(type)) {
            return new ZhipuEmbeddings(config);
        } else if (EmbeddingConstant.EMBEDDING_TYPE_BAICHUAN.equalsIgnoreCase(type)) {
            return new BaichuanEmbeddings(config);
        } else if (EmbeddingConstant.EMBEDDING_TYPE_SENSECHAT.equalsIgnoreCase(type)){
            return new SenseChatEmbeddings(config);
        } else if (EmbeddingConstant.EMBEDDING_TYPE_LANDING.equalsIgnoreCase(type) && config.getModel_path() != null) {
            return new LinkMindEmbeddings(config);
        }

        throw new IllegalArgumentException("Invalid type: " + type);
    }
}