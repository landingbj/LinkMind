# Chroma Knowledge Base

[中文](#zh-cn) · [English](#english)

<a id="english"></a>

This recipe uses Chroma only when an independent vector service is required. For a new appliance or a single host, prefer the SQLite path in [Knowledge Base](../../knowledge-base_en.md) / [知识库](../../knowledge-base_zh.md), which has no separate service to operate.

<a id="chroma-persistence"></a>

## 1. Start Chroma with persistent storage

The LinkMind Chroma adapter calls the v1 HTTP API. This recipe pins Chroma 0.5.23, which serves that API. Use either Python or Docker. In both examples, `db_data` is a persistent directory on the host.

### Python

```bash
python -m pip install "chromadb==0.5.23"
mkdir -p db_data
chroma run --path "$PWD/db_data" --host 127.0.0.1 --port 8000
```

### Docker

```bash
mkdir -p db_data
docker run -d --name linkmind-chroma \
  -p 127.0.0.1:8000:8000 \
  -v "$PWD/db_data:/chroma/chroma" \
  -e IS_PERSISTENT=TRUE \
  -e ANONYMIZED_TELEMETRY=FALSE \
  chromadb/chroma:0.5.23
```

`IS_PERSISTENT=TRUE` enables disk-backed data; the volume maps the database directory used by this image. `ANONYMIZED_TELEMETRY=FALSE` disables anonymized telemetry. The example binds to loopback. When LinkMind runs on another machine or in another container, configure a reachable private address and restrict access to the LinkMind network. Confirm connectivity from the LinkMind process. [Chroma 0.5.23 Docker Compose](https://github.com/chroma-core/chroma/blob/0.5.23/docker-compose.yml) · [Chroma 0.5.23 configuration](https://github.com/chroma-core/chroma/blob/0.5.23/chromadb/config.py)

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

For a persistence check, record a known document ID, category, and a distinctive phrase. Restart Chroma using the same directory or volume (for Docker use `docker restart linkmind-chroma`). Query the same category and confirm the ID and text before testing chat again.

<a id="zh-cn"></a>

## 中文

本项目的 Chroma 适配器使用 v1 HTTP API。下例固定使用提供该接口的 Chroma 0.5.23。单机可直接使用内置 SQLite；需要独立向量服务时，在 Python 和 Docker 中选择一种启动方式。两种方式中的 `db_data` 都是持久化目录。

### Python

```bash
python -m pip install "chromadb==0.5.23"
mkdir -p db_data
chroma run --path "$PWD/db_data" --host 127.0.0.1 --port 8000
```

### Docker

```bash
mkdir -p db_data
docker run -d --name linkmind-chroma \
  -p 127.0.0.1:8000:8000 \
  -v "$PWD/db_data:/chroma/chroma" \
  -e IS_PERSISTENT=TRUE \
  -e ANONYMIZED_TELEMETRY=FALSE \
  chromadb/chroma:0.5.23
```

`IS_PERSISTENT=TRUE` 开启落盘，卷挂载到该版本镜像实际写入的 `/chroma/chroma`。`ANONYMIZED_TELEMETRY=FALSE` 关闭匿名遥测。示例只监听回环地址。跨机器或容器使用时，在受控网络中配置可达的私有地址，并从 LinkMind 所在进程验证连通性。[Chroma 0.5.23 Docker Compose](https://github.com/chroma-core/chroma/blob/0.5.23/docker-compose.yml) · [Chroma 0.5.23 configuration](https://github.com/chroma-core/chroma/blob/0.5.23/chromadb/config.py)

### 配置并验证 RAG

在 `lagi.yml` 中配置向量库、RAG 及已启用的 Embedding 后端，将凭证和模型替换为部署中的实际值：

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

`stores.rag.vector` 对应 `stores.vector[].name`。重启 LinkMind 后先验证 Embedding，再通过控制台或向量 API 上传含独特事实的小文档。记录 `category` 和结果 ID，在同一分类调用 `/v1/vector/query`，然后聊天提问并核对来源。用同一数据目录重启 Chroma（Docker 可用 `docker restart linkmind-chroma`），再次验证相同 ID 和内容。结果为空时逐项检查分类、Embedding 模型、Chroma 地址、集合名和相似度阈值。[知识库接口与字段](../../knowledge-base_zh.md)
