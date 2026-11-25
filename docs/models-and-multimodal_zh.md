# 模型接入、路由与多模态

本页把模型 Provider、路由、调用接口和验证步骤放在一起。先完成一条聊天请求，再按需启用 Embedding、音频、图片、视频或文档处理；不同能力必须分别验证。

## 一、配置 Provider 与聊天路由

在 `lagi.yml` 的 `models` 中声明上游，在 `functions.chat.backends` 中公开聊天模型。`name` 是 LinkMind 内部后端名，`model` 是调用方传入或厂商使用的模型 ID，二者不要混淆。

```yaml
models:
  - name: qwen
    type: Alibaba
    enable: true
    model: qwen-plus,text-embedding-v3
    driver: ai.wrapper.impl.AlibabaAdapter
    api_key: ${QWEN_API_KEY}

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

常用字段如下。真实 Key 应由环境变量或受控配置注入，不能进入仓库、截图和支持包。

| 字段 | 用途 |
| --- | --- |
| `name` / `backend` | 后端的内部名称与引用关系 |
| `type` / `driver` | Provider 标签与适配器实现 |
| `api_address` / `endpoint` | 自建或兼容 Provider 的上游地址 |
| `api_key` / `api_keys` | 单 Key 或 Key 池 |
| `key_route` | Key 池策略：`polling` 或 `failover` |
| `enable` / `priority` | 是否启用及后端优先级 |
| `stream` / `protocol` | 流式输出及兼容协议，常用 `completion` |

OpenAI 兼容 Provider 可复用 `ai.llm.adapter.impl.OpenAIStandardAdapter`；其他兼容入口可按协议选用 `OpenRouterAdapter` 或 `QwenCompatibleAdapter`。多个模型需要容灾时，保留调用方稳定的模型 ID，在路由中选择后端：

```yaml
functions:
  chat:
    route: best((qwen|deepseek),openai)
    handle: failover
    grace_time: 20
    context_length: 4096
```

`pass(name)` 固定走一个后端，`best(...)` 在候选后端中选择。先确认每个候选后端都可单独调用，再启用路由与故障切换。

## 二、快速验证聊天与模型列表

重启服务后，先列出当前可用模型，再进行一个非流式短请求。启用鉴权的部署需要 `Authorization: Bearer <linkmind-api-key>`。

```bash
curl http://localhost:8080/v1/models \
  -H "Authorization: Bearer <linkmind-api-key>"

curl http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer <linkmind-api-key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"qwen-plus","messages":[{"role":"user","content":"用一句话介绍 LinkMind。"}],"stream":false,"max_completion_tokens":128}'
```

成功条件是 HTTP 响应中有 `choices[0].message.content`，且控制台 **控制** 页面记录了同一时间、同一模型的调用。若模型不在 `/v1/models` 中，检查 `models.enable`、聊天后端的 `enable`、后端名和模型 ID；若上游失败，再检查 Key、额度、网络和 Provider 地址。

## 三、聊天、Responses 与 Messages 接口

| 协议 | 地址 | 最小必填字段 | 适用客户端 |
| --- | --- | --- | --- |
| OpenAI Chat Completions | `POST /v1/chat/completions` | `model`、`messages` | OpenAI SDK、多数 Agent |
| OpenAI Responses | `POST /v1/responses` | `model`、`input` | Responses SDK |
| Anthropic Messages | `POST /v1/messages` | `model`、`max_tokens`、`messages` | Claude Code 等 |

Chat Completions 中，`messages` 是按顺序传递的 `role`/`content` 消息；`stream` 控制 SSE 流；`max_completion_tokens` 限制本次最大输出；可选 `temperature`、`tools`、`store` 和类型化 `extra_body`。流式响应应持续读取到 `data: [DONE]`，不能只以收到第一包或 HTTP 200 判定成功。

Responses 示例：

```bash
curl http://localhost:8080/v1/responses \
  -H "Authorization: Bearer <linkmind-api-key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"qwen-plus","input":"用一句话说明路由的作用。"}'
```

Anthropic Messages 示例：

```bash
curl http://localhost:8080/v1/messages \
  -H "Authorization: Bearer <linkmind-api-key>" \
  -H "anthropic-version: 2023-06-01" \
  -H "Content-Type: application/json" \
  -d '{"model":"qwen-plus","max_tokens":128,"messages":[{"role":"user","content":"你好"}]}'
```

协议地址固定在 LinkMind；不同厂商的上游协议和模型映射只保留在 `lagi.yml`，不要由业务客户端自行适配。

## 四、Embedding 与多模态能力

每项能力必须引用已启用的后端。以下是 Embedding 的最小配置；`model_name` 必须替换为所选 Provider 支持的嵌入模型。

```yaml
functions:
  embedding:
    - backend: qwen
      type: Qwen
      api_key: ${QWEN_API_KEY}
      model_name: text-embedding-v3
      enable: true
      priority: 100
```

```bash
curl http://localhost:8080/v1/embeddings \
  -H "Authorization: Bearer <linkmind-api-key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"text-embedding-v3","input":"LinkMind embedding verification"}'
```

成功响应包含向量数组；将它用于知识库前，应确认维度与目标向量库索引一致。

| 能力 | 功能块 / 接口 | 主要输入 |
| --- | --- | --- |
| 语音识别、语音合成 | `speech2text` / `text2speech`；`POST /audio/speech2text`、`GET /audio/text2speech` | 音频文件或文本 |
| 图片理解、生成、增强 | `image2text`、`text2image`、`image2enhance`；`/image/*` | 图片 URL/文件或提示词 |
| 视频生成、追踪、增强 | `text2video`、`image2video`、`video2track`、`video2enhance`；`/video/*` | 提示词、图片或视频 |
| 文档 OCR、抽取、结构化 | `doc2ocr`、`doc2ext`、`doc2struct`；`/ocr/doc2ocr`、`/doc/*` | 文档文件 |

多模态后端条目通常包含 `backend`、`model`、`enable`、`priority`。先用小文件或短提示验证输入格式、上游权限与结果，再提高并发或接入生产文件。

## 五、常见问题与下一步

- **聊天能用，Embedding 或 OCR 失败**：功能块没有配置、对应模型不支持该模态，或后端 Key 权限不同。
- **模型显示但请求失败**：显示可用不代表上游可用；按 Provider Key、模型 ID、余额、DNS/代理和日志顺序排查。
- **流式请求无完成标记**：记录首包、最后一包和连接关闭时间；同时检查 LinkMind 到上游的 SSE 日志，不要只看客户端页面。
- **要上传资料做问答**：继续阅读[知识库](knowledge-base_zh.md)，其中包含向量库、上传、检索和 RAG 验证。
- **要扩展新厂商协议或新模态**：阅读[更多功能接入与自定义扩展](extensions_zh.md)。
