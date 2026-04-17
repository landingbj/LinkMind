# API 指南

本文档只保留当前 `web.xml` 和各个 Servlet 中实际存在的接口，重点放在最常用、最容易上手的能力上。

## 基础地址与路由规则

默认本地部署可按下面地址理解：

```text
http://localhost:8080
```

本文档遵循以下路由规则：

- 代码已经直接暴露原生路由的能力，优先使用无版本前缀的 LinkMind 原生路径。
- OpenAI 兼容接口保留标准 `/v1/...` 前缀，例如 `/v1/chat/completions`、`/v1/models`、`/v1/embeddings`、`/v1/images/generations`、`/v1/rerank`。
- 向量管理接口当前仍然实际映射在 `/v1/vector/*` 下，这部分前缀需要保留。
- 如果你的部署开启了鉴权，请按你所在环境要求附带对应的认证头或请求密钥。

## 常用接口总览

| 能力 | 方法 | 路径 |
| --- | --- | --- |
| 原生对话补全 | `POST` | `/chat/completions` |
| OpenAI 兼容对话补全 | `POST` | `/v1/chat/completions` |
| Agent / Worker 调用 | `POST` | `/chat/go` |
| 语音转文本 | `POST` | `/audio/speech2text` |
| 文本转语音 | `GET` | `/audio/text2speech` |
| 文生图 | `POST` | `/image/text2image` |
| OpenAI 兼容文生图 | `POST` | `/v1/images/generations` |
| 图片 OCR | `POST` | `/image/image2ocr` |
| 文档 OCR | `POST` | `/ocr/doc2ocr` |
| 文档内容抽取 | `POST` | `/doc/doc2ext` |
| 文档结构化 / Markdown 转换 | `POST` | `/doc/doc2struct` |
| 指令抽取 | `POST` | `/instruction/generate` |
| Text-to-SQL | `POST` | `/sql/text2sql` |
| SQL-to-Text | `POST` | `/sql/sql2text` |
| Embedding | `POST` | `/embeddings` 或 `/v1/embeddings` |
| Rerank | `POST` | `/rerank` 或 `/v1/rerank` |
| OpenAI 兼容模型列表 | `GET` | `/v1/models` |
| 向量管理 | `GET` / `POST` | `/v1/vector/*` |

## 1. 对话补全

原生路由：

```bash
curl http://localhost:8080/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "qwen-plus",
    "temperature": 0.7,
    "max_tokens": 512,
    "category": "default",
    "stream": false,
    "messages": [
      {
        "role": "user",
        "content": "请用一句话介绍 LinkMind。"
      }
    ]
  }'
```

OpenAI 兼容路由：

```bash
curl http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "qwen-plus",
    "stream": true,
    "messages": [
      {
        "role": "user",
        "content": "写一段简短欢迎语。"
      }
    ]
  }'
```

当前请求对象里常用字段包括：

| 字段 | 说明 |
| --- | --- |
| `model` | 在 `functions.chat.backends` 中配置的模型 ID |
| `messages` | 标准聊天消息列表 |
| `stream` | 是否使用 SSE 流式输出 |
| `temperature` | 采样温度 |
| `max_tokens` | 输出 token 上限 |
| `category` | RAG 检索时使用的知识分类 |
| `tools`、`tool_choice`、`parallel_tool_calls` | 工具调用兼容字段 |
| `response_format` | 结构化输出要求 |
| `session_id` | 当前代码接受它作为 `sessionId` 的别名 |

## 2. Agent 与 Worker 调用

`/chat/go` 是 Worker / Agent 的统一入口。

```bash
curl http://localhost:8080/chat/go \
  -H "Content-Type: application/json" \
  -d '{
    "worker": "appointedWorker",
    "agentId": "weather",
    "stream": false,
    "messages": [
      {
        "role": "user",
        "content": "帮我查一下北京今天的天气。"
      }
    ]
  }'
```

常用字段：

- `worker`：对应 `skills.workers` 中定义的 Worker 名称
- `agentId`：当 Worker 路由需要指定 Agent 时使用
- `messages`：对话内容
- `stream`：是否流式返回

## 3. 音频接口

### 语音转文本

`POST /audio/speech2text`

请求体直接传音频二进制。当前实现会把部分请求参数解析为 `AudioRequestParam`，例如 `model`、`format`、`sample_rate`、`vocabulary_id`、`customization_id`。

```bash
curl -X POST "http://localhost:8080/audio/speech2text?model=asr&format=wav" \
  -H "Content-Type: application/octet-stream" \
  --data-binary "@demo.wav"
```

### 文本转语音

`GET /audio/text2speech`

当前实现支持的常见查询参数包括 `text`、`model`、`format`、`sample_rate`、`voice`、`volume`、`speech_rate`、`pitch_rate`、`emotion`、`source`。

```bash
curl "http://localhost:8080/audio/text2speech?model=landing-tts&text=你好%20LinkMind"
```

成功时响应体为音频流，`Content-Type` 为 `audio/mpeg`。

## 4. 图像接口

### 文生图

原生路由：

```bash
curl http://localhost:8080/image/text2image \
  -H "Content-Type: application/json" \
  -d '{
    "model": "tti",
    "prompt": "一个简洁的 AI 控制塔产品插画",
    "size": "1024x1024"
  }'
```

OpenAI 兼容路由：

```bash
curl http://localhost:8080/v1/images/generations \
  -H "Content-Type: application/json" \
  -d '{
    "model": "tti",
    "prompt": "一个简洁的 AI 控制塔产品插画",
    "size": "1024x1024"
  }'
```

### 图片 OCR

`POST /image/image2ocr`

按 `multipart/form-data` 上传一个或多个图片文件即可。

```bash
curl http://localhost:8080/image/image2ocr \
  -F "file=@page.png"
```

## 5. 文档接口

### 文档 OCR

`POST /ocr/doc2ocr`

当前实现支持上传文件，并可选附带 `lang` 表单字段。PDF 目前一次只允许处理一个文件。

```bash
curl http://localhost:8080/ocr/doc2ocr \
  -F "file=@contract.pdf" \
  -F "lang=chn,eng"
```

### 文档内容抽取

`POST /doc/doc2ext`

```bash
curl http://localhost:8080/doc/doc2ext \
  -F "file=@manual.pdf"
```

### 文档结构化 / Markdown 转换

`POST /doc/doc2struct`

```bash
curl http://localhost:8080/doc/doc2struct \
  -F "file=@manual.pdf"
```

### 指令抽取

`POST /instruction/generate`

```bash
curl http://localhost:8080/instruction/generate \
  -F "file=@faq.docx"
```

## 6. SQL 接口

### Text-to-SQL

`POST /sql/text2sql`

```bash
curl http://localhost:8080/sql/text2sql \
  -H "Content-Type: application/json" \
  -d '{
    "demand": "统计本周新增订单数",
    "tables": "orders",
    "storage": "mysql"
  }'
```

### SQL-to-Text

`POST /sql/sql2text`

```bash
curl http://localhost:8080/sql/sql2text \
  -H "Content-Type: application/json" \
  -d '{
    "demand": "请用自然语言解释查询结果",
    "sql": "SELECT COUNT(*) FROM orders",
    "tables": "orders",
    "storage": "mysql"
  }'
```

## 7. Embedding 与 Rerank

### Embedding

当前两个入口都可用：

- `POST /embeddings`
- `POST /v1/embeddings`

```bash
curl http://localhost:8080/embeddings \
  -H "Content-Type: application/json" \
  -d '{
    "model": "text-embedding",
    "input": ["LinkMind", "RAG middleware"]
  }'
```

### Rerank

当前两个入口都可用：

- `POST /rerank`
- `POST /v1/rerank`

```bash
curl http://localhost:8080/rerank \
  -H "Content-Type: application/json" \
  -d '{
    "model": "rerank-model",
    "query": "企业知识库",
    "documents": [
      "内部 FAQ",
      "模型接入指南",
      "零散会议纪要"
    ]
  }'
```

## 8. 向量管理接口

向量管理目前仍然统一挂在 `/v1/vector/*` 下。

当前 Servlet 中常见路由如下：

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| `POST` | `/v1/vector/upsert` | 新增或更新文档 |
| `POST` | `/v1/vector/query` | 向量检索 |
| `POST` | `/v1/vector/get` | 按 ID 或条件获取数据 |
| `POST` | `/v1/vector/search` | 按对话上下文检索 |
| `POST` | `/v1/vector/searchByMetadata` | 按元数据过滤检索 |
| `POST` | `/v1/vector/deleteById` | 按 ID 删除 |
| `POST` | `/v1/vector/deleteByMetadata` | 按元数据删除 |
| `POST` | `/v1/vector/deleteCollection` | 删除分类 / 集合 |
| `GET` | `/v1/vector/listCollections` | 查看集合列表 |
| `POST` | `/v1/vector/updateTextBlockSize` | 更新 RAG 分块大小 |
| `GET` | `/v1/vector/getTextBlockSize` | 查询 RAG 分块大小 |
| `POST` | `/v1/vector/resetBlockSize` | 重置 RAG 分块大小 |

`upsert` 示例：

```bash
curl http://localhost:8080/v1/vector/upsert \
  -H "Content-Type: application/json" \
  -d '{
    "category": "product-docs",
    "isContextLinked": true,
    "data": [
      {
        "id": "intro-001",
        "document": "LinkMind provides unified AI middleware capabilities.",
        "metadata": {
          "source": "README",
          "lang": "en"
        }
      }
    ]
  }'
```

## 9. 兼容性说明

- `GET /v1/models` 主要用于 OpenAI 兼容客户端探测，当前返回的是兼容风格的最小模型列表，并不是完整后端清单。
- `/chat/isRAG` 与 `/chat/isMedusa` 存在于当前 Servlet 中，更适合作为运行期开关或排查接口，而不是主接入面。
- `/v1/openclaw/context/*` 也是当前代码中存在的专用接口，用于 OpenClaw 相关上下文能力。
