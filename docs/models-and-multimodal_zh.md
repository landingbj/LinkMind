# 模型接入、路由与多模态

本页用于接入模型提供方、选择路由，并调用聊天、Embedding、音频、图片和视频能力。

## 一、配置 Provider 与聊天路由

先在 `models` 定义 Provider，再通过 `functions.chat.backends` 对外启用：

```yaml
models:
  - name: qwen
    type: Alibaba
    enable: true
    model: qwen-plus,asr,vision,ocr
    driver: ai.wrapper.impl.AlibabaAdapter
    api_key: your-api-key

functions:
  chat:
    route: pass(qwen)
    filter: sensitive,priority,stopping,continue
    backends:
      - backend: qwen
        model: qwen-plus
        enable: true
        stream: true
        protocol: completion
        priority: 100
```

`name` 是各功能块引用的后端名，`model` 是厂商模型 ID，`driver` 选择适配器。多 Key 使用 `api_keys`，并通过 `key_route: polling` 或 `failover` 选择策略。可复用的兼容适配器包括 `OpenAIStandardAdapter`、`OpenRouterAdapter` 和 `QwenCompatibleAdapter`。

## 二、路由与稳定性

直接选择或故障切换可使用 `pass(...)`；由路由器在多个候选后端中选择时使用 `best(...)`。厂商差异应留在 `lagi.yml` 中，调用方只需使用稳定的 LinkMind 地址。聊天块还控制过滤器、排队处理、重试、上下文长度与 Token 计费。

```yaml
functions:
  chat:
    route: best((qwen|deepseek),openai)
    handle: failover
    grace_time: 20
    context_length: 4096
```

## 三、调用聊天接口

| 协议 | 接口 | 必填请求字段 |
| --- | --- | --- |
| OpenAI Chat Completions | `POST /v1/chat/completions` | `model`、`messages` |
| OpenAI Responses | `POST /v1/responses` | `model`、`input` |
| Anthropic Messages | `POST /v1/messages` | `model`、`max_tokens`、`messages` |
| LinkMind Worker 调用 | `POST /chat/go` | 由 Worker 决定的请求体 |

部署启用 API 鉴权时，传递 `Authorization: Bearer <linkmind-api-key>`。完整请求体和流式返回结构见[API 参考](API_zh.md#文本)。

## 四、启用 Embedding 与多模态功能

每项功能都引用一个已启用的模型后端。Embedding 最小示例：

```yaml
functions:
  embedding:
    - backend: qwen
      type: Qwen
      api_key: your-api-key
      model_name: text-embedding
```

常见功能块包括 `speech2text`、`text2speech`、`text2image`、`image2text`、`image2ocr`、`image2enhance`、`text2video`、`image2video`、`video2track`、`video2enhance`、`doc2ocr`、`doc2ext`、`doc2struct`。后端条目通常声明 `backend`、`model`、`enable`、`priority`。

| 能力 | 接口 |
| --- | --- |
| Embedding | `POST /v1/embeddings` |
| 语音识别 / TTS | `POST /audio/speech2text`、`GET /audio/text2speech` |
| 图片理解 / 生成 / 增强 | `POST /image/image2text`、`/image/text2image`、`/image/image2enhance` |
| 视频生成 / 追踪 / 增强 | `POST /image/image2video`、`/video/video2tracking`、`/video/mmeditingInference` |
| 文档 OCR / 抽取 / 结构化 | `POST /ocr/doc2ocr`、`/doc/doc2ext`、`/doc/doc2struct` |

保存配置后，请针对每项能力用小型且受支持的输入单独验证。文本聊天成功不代表音频、图片、视频、OCR 或 Embedding 后端可用。

## 五、相关页面

- [知识库](knowledge-base_zh.md)：RAG 中的 Embedding 与向量检索。
- [更多功能接入与自定义扩展](extensions_zh.md)：新增 Provider 适配器或模态实现。
- [API 参考](API_zh.md)：完整接口参数和返回结构。
