# Chroma Knowledge Base

This recipe uses Chroma only when an independent vector service is required. For a new appliance or a single host, prefer the SQLite path in [Knowledge Base](../../knowledge-base_en.md) / [知识库](../../knowledge-base_zh.md), which has no separate service to operate.

## 1. Start Chroma

```bash
pip install chromadb
chroma run --host 0.0.0.0 --port 8000
```

```bash
docker run -d --name chroma -p 8000:8000 chromadb/chroma
```

Confirm that `8000` is reachable from the LinkMind process, rather than only from the browser. When Chroma runs on another host, replace `localhost` with that host's reachable address and restrict the port to the LinkMind network.

## 2. Configure LinkMind RAG

Add the store, RAG selection, and an enabled embedding backend to `lagi.yml`:

```yaml
stores:
  vector:
    - name: chroma
      driver: ai.vector.impl.ChromaVectorStore
      default_category: default
      similarity_top_k: 10
      similarity_cutoff: 0.5
      url: http://localhost:8000
  rag:
    vector: chroma
    enable: true
    track: true

functions:
  embedding:
    - backend: qwen
      type: Qwen
      model_name: text-embedding-v3
      api_key: ${QWEN_API_KEY}
      enable: true
```

`stores.rag.vector` must exactly match the store `name`. Restart LinkMind after the change, then verify the embedding endpoint before uploading documents.

## 3. Ingest and verify

Upload a small document with one distinctive fact, query it through `/v1/vector/query`, then ask the chat model about the same fact. A useful verification sequence is: upload -> wait for document processing and embedding -> direct vector query using the same `category` -> chat question -> verify trace/citation if enabled.

If upload succeeds but retrieval is empty, check the category, embedding model, Chroma reachability, collection name, and similarity cutoff in that order. Full configuration and API fields are in [Knowledge Base](../../knowledge-base_en.md) / [知识库](../../knowledge-base_zh.md).
