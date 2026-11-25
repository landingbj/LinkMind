# 知识库

本页将文档检索和 RAG 所需的配置、入库接口、验证步骤放在一起。

## 一、选择并启动向量库

新部署优先使用 SQLite：它与 LinkMind 同机运行时不需要额外服务，适合盒子、单机和小规模资料库。已有 Chroma、Milvus 等服务时再选择对应存储。

SQLite 最小配置：

```yaml
stores:
  vector:
    - name: sqlite
      driver: ai.vector.impl.SqliteVectorStore
      default_category: default
      similarity_top_k: 10
      similarity_cutoff: 0.5
      parent_depth: 1
      child_depth: 1
      path: data/vector.db
```

`path` 使用 LinkMind 进程可写的目录；升级和迁移前应备份该数据库文件。下面的 Chroma 是可选的独立向量库方案：

Chroma 是最小的本地方案，可用 Python 或 Docker 启动：

```bash
pip install chromadb
chroma run --host 0.0.0.0 --port 8000
```

```bash
docker run -d --name chroma -p 8000:8000 chromadb/chroma
```

## 二、配置存储、Embedding 与 RAG

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
    priority: 10
    track: true
    html: true
    default: "Please give prompt more precisely"
    enable_excel_to_md: true

functions:
  embedding:
    - backend: qwen
      type: Qwen
      api_key: your-api-key
      model_name: text-embedding
```

`stores.rag.vector` 必须与向量库的 `name` 一致。向量库地址应填写 LinkMind 服务可访问的主机，并非一定是浏览器所在主机。`similarity_top_k` 与 `similarity_cutoff` 控制检索数量和阈值，`enable_excel_to_md` 会在入库前规范化表格内容。

## 三、入库与维护接口

| 任务 | 接口 |
| --- | --- |
| 生成 Embedding | `POST /v1/embeddings` |
| 新增、更新、读取或删除向量 | `POST /v1/vector/add`、`/update`、`/get`、`/delete` |
| 查询向量 | `POST /v1/vector/query` |
| 获取集合列表 | `GET /v1/vector/listCollections` |
| 抽取、OCR 或结构化文档 | `POST /doc/doc2ext`、`/ocr/doc2ocr`、`/doc/doc2struct` |
| 上传私训资料或问答对 | `POST /training/upload`、`POST /uploadFile/pairing` |

每个知识域使用明确的 collection/category，并保存来源元数据和接口返回的文档 ID，便于重复更新和删除。

### 1. 写入或更新向量

`POST /v1/vector/add` 将文本写入指定 collection；`/update`、`/get`、`/delete` 分别用于更新、读取、删除。新增时提供 collection/category、文本和元数据；更新或删除时使用接口返回或读取到的稳定 ID：

```json
{
  "category": "property-manual",
  "data": [
    {
      "document": "物业报修应在两个工作日内完成首次响应。",
      "metadata": {"source": "manual.pdf", "page": "3"}
    }
  ]
}
```

### 2. 查询检索结果

`POST /v1/vector/query`：

```json
{
  "category": "property-manual",
  "text": "报修后多久响应？",
  "n": 5
}
```

`category` 对应知识分类或 collection，`text` 是检索问题，`n` 是期望返回的近邻数量。实际召回还受 `similarity_top_k`、`similarity_cutoff` 和元数据过滤条件影响。

### 3. 上传文件或问答对

`POST /training/upload?category=property-manual` 使用 `multipart/form-data`，文件字段为 `fileToUpload`。问答对使用 `POST /uploadFile/pairing`：

```json
{
  "category": "property-manual",
  "level": "user",
  "data": [
    {
      "instruction": ["物业报修多久响应？"],
      "output": "应在两个工作日内完成首次响应。"
    }
  ]
}
```

使用 `GET /uploadFile/getUploadFileList` 查看已上传文件，使用 `POST /uploadFile/deleteFile` 按文件 ID 删除，并同步清理相关向量。

## 四、验证问答链路

1. 上传或写入一份包含明显事实的短文档。
2. 确认文档处理和 Embedding 请求均已完成。
3. 通过 `POST /v1/vector/query` 直接查询对应集合。
4. 用聊天接口询问该明确事实。
5. 确认回答与已配置的追踪或引用信息。

上传成功但回答未命中资料时，检查 collection/category、Embedding 模型、向量库可达性、检索阈值，以及聊天链路是否启用 RAG。

## 五、相关页面

- [模型接入、路由与多模态](models-and-multimodal_zh.md)：配置 Embedding 与文档处理后端。
- [更多功能接入与自定义扩展](extensions_zh.md)：实现自定义向量库。
