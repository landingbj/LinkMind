# Chroma Knowledge Base

## 启动与验证 / Setup

```bash
pip install chromadb
chroma run --host 0.0.0.0 --port 8000
```

```bash
docker run -d --name chroma -p 8000:8000 chromadb/chroma
```

Configure `stores.vector` with `ai.vector.impl.ChromaVectorStore`, point `url` at `http://localhost:8000` when both services share a host, and select the same store name in `stores.rag.vector`. Enable an embedding backend before ingestion.

Upload a small document with one distinctive fact, query it through `/v1/vector/query`, then ask the chat model about the same fact. Full configuration and API steps are in [Knowledge Base](../../knowledge-base_en.md) / [知识库](../../knowledge-base_zh.md).
