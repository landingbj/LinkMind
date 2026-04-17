# 附件

本页放的是一些对安装和落地有帮助、但不应该打断主上手路径的补充材料。

## 一、RAG 的 Chroma 快速搭建

如果你准备在本地启用 RAG，Chroma 是当前最容易起步的向量库方案。

### Python 方式

```bash
pip install chromadb
mkdir db_data
chroma run --path db_data
```

默认本地地址：

- `http://localhost:8000`

### Docker 方式

```bash
docker run -d \
  --name chromadb \
  -p 8000:8000 \
  -v /mydata/docker/local/chroma/data:/study/ai/chroma \
  -e IS_PERSISTENT=TRUE \
  -e ANONYMIZED_TELEMETRY=TRUE \
  chromadb/chroma:latest
```

## 二、最小 RAG 配置检查单

Chroma 跑起来后，请在 `lagi.yml` 里确认这些项：

```yaml
stores:
  vector:
    - name: chroma
      driver: ai.vector.impl.ChromaVectorStore
      url: http://localhost:8000

  rag:
    vector: chroma
    enable: true

functions:
  embedding:
    - backend: qwen
      type: Qwen
      api_key: your-api-key
```

推荐顺序：

1. 启动 Chroma
2. 把 `stores.vector[*].url` 指向你的 Chroma 地址
3. 打开 `stores.rag`
4. 配置一个可用的 Embedding 后端
5. 开始上传或写入知识内容

## 三、构建产物说明

当前仓库构建后会生成：

- `lagi-web/target/LinkMind.jar`
- `lagi-web/target/ROOT.war`

运行打包后的 JAR 时，会在运行目录自动生成：

- `config/lagi.yml`
- `data/`

## 四、文档与知识处理能力

当前服务端已经支持这些相关能力：

- `POST /doc/doc2ext` 文档内容抽取
- `POST /doc/doc2struct` 文档结构化输出为 Markdown
- `POST /ocr/doc2ocr` 对 PDF 和图片做 OCR
- `POST /v1/vector/upsert` 以及相关向量管理接口，用于知识写入和维护

## 五、相关文档

- [安装指南](install_zh.md)
- [配置参考](config_zh.md)
- [API 参考](API_zh.md)
- [教学演示](tutor_zh.md)
