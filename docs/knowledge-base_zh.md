# 知识库

本页将文档检索和 RAG 所需的配置、入库接口、验证步骤放在一起。

## 一、启动向量库

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

每个知识域使用明确的 collection/category，并保存来源元数据和文档 ID，便于重复更新和删除。完整请求结构见[API 参考](API_zh.md#rag)。

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
- [旧版 Chroma 补充说明](annex_zh.md)：额外的 Java 与 Chroma 集成细节。
