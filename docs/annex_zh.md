# 附件

本页整理了 RAG 配套环境、IDE 配置以及其他补充说明。

## 一、RAG 的 Chroma 快速搭建

如果你想在本地启用 RAG，Chroma 仍然是最快上手的向量库起点。

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

Chroma 启动后，请至少检查 `lagi.yml` 中这些配置项：

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

建议顺序：

1. 启动 Chroma。
2. 把 `stores.vector[*].url` 指向你的 Chroma 服务。
3. 打开 `stores.rag`。
4. 配置一个可用的 Embedding 后端。
5. 再上传或写入知识内容。

## 三、快速集成补充说明

### 直接导入 JAR

如果你只是想在现有 Java 项目里复用 LinkMind 的能力：

1. 下载 `lagi-core-*-jar-with-dependencies.jar`。
2. 放进项目的 `lib` 目录。
3. 提供外部 `lagi.yml`，或放到运行时 `resources` 中。
4. 按原有方式构建并启动项目。

运行时外部配置会覆盖 JAR 内置的默认配置。

### 在 Eclipse 中接入

1. 打开 `File > Import...`。
2. 选择 `General > Existing Projects into Workspace`。
3. 选择项目目录并完成导入。
4. 打开项目 `Properties > Java Build Path > Libraries`。
5. 加入 LinkMind 相关 JAR，或加入打好的 `lagi-core` 依赖。
6. 执行 `Build Project`。

### 在 IntelliJ IDEA 中接入

1. 打开 `File > Open`。
2. 选择你的项目目录。
3. 通过 Maven 依赖或本地 JAR 的方式加入 LinkMind。
4. 重新同步项目。
5. 执行一次构建，确认类都能正确解析。

### IDE 常见问题

- 如果 JAR 无法识别，先确认文件真实存在，且加入到了正确的模块。
- 如果类仍然提示缺失，尝试刷新 Maven 或重建 IDE 索引。
- 如果运行行为和预期不一致，优先确认当前到底加载的是哪一份 `lagi.yml`。

## 四、构建产物说明

当前仓库打包后会产出：

- `lagi-web/target/LinkMind.jar`
- `lagi-web/target/ROOT.war`

运行打包后的 JAR 时，还会自动生成：

- `config/lagi.yml`
- `data/`

如果你要做容器化部署，请直接使用[安装指南](install_zh.md)中的官方 Docker 镜像，不再建议在这里自行封装镜像。

## 五、文档与知识处理能力

当前服务端支持这些和文档知识相关的能力：

- `POST /doc/doc2ext`：抽取正文内容
- `POST /doc/doc2struct`：把文档转为结构化 Markdown
- `POST /ocr/doc2ocr`：对 PDF 和图片做 OCR
- `POST /v1/vector/upsert` 及相关向量管理接口：写入、维护知识库内容

## 六、相关文档

- [安装指南](install_zh.md)
- [配置参考](config_zh.md)
- [API 参考](API_zh.md)
- [教学演示](tutor_zh.md)
