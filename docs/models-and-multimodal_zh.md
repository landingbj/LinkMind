# 模型接入、路由与多模态

本页把模型 Provider、路由、调用接口和验证步骤放在一起。先完成一条聊天请求，再按需启用 Embedding、音频、图片、视频或文档处理；不同能力必须分别验证。

[Provider 与密钥池](#provider-drivers) · [路由](#routing-reference) · [Medusa](#medusa-cache) · [Chat Completions](#model-api-04) · [Responses](#model-api-02) · [Messages](#model-api-03) · [多模态配置](#function-backends) · [Embedding 与媒体 API](#model-api-19)

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

<a id="provider-fields"></a>

| 字段 | 说明 |
| --- | --- |
| `name` | 后续在 `functions.*.backend` 中引用的后端名 |
| `type` | 配置中展示的提供方标签 |
| `enable` | 是否启用该提供方 |
| `model` | 当前适配器负责的模型 ID 列表，通常为逗号分隔 |
| `driver` | 单适配器模式下的 Java 适配器类 |
| `drivers` | 一个后端暴露多种模态时使用的适配器列表 |
| `api_address` | 提供方接口地址，常见于 OpenAI 兼容或自建服务 |
| `endpoint` | 提供方专用基础地址 |
| `api_key` | 主 API Key |
| `api_keys` | 多 Key 池 |
| `key_route` | Key 池策略，支持 `polling` 或 `failover` |
| `app_id` / `app_key` | 提供方应用标识 |
| `secret_key` | 提供方密钥 |
| `access_key_id` / `access_key_secret` | 部分云厂商使用的密钥对 |
| `deployment` / `api_version` | 部署名与版本字段，常见于 Azure 类服务 |
| `alias` | 可选的模型别名映射 |
| `oss` | 当前适配器绑定的对象存储配置名 |

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

<a id="provider-drivers"></a>

### 单驱动、多驱动与模型密钥池

随仓库提供的配置包含 Qwen、DeepSeek、Landing、FastChat/Vicuna、OpenAI、Azure OpenAI、ERNIE、ChatGLM、Kimi、Baichuan、Spark、SenseChat、Gemini、Doubao 和 Claude 等示例。可调用的模型取决于实际启用的 Provider、路由与授权。[模型配置示例](../lagi-web/src/main/resources/model.yml)

#### 单适配器示例

```yaml
models:
  - name: deepseek
    type: DeepSeek
    enable: true
    model: deepseek-chat
    driver: ai.llm.adapter.impl.DeepSeekAdapter
    api_address: https://api.deepseek.com/chat/completions
    api_key: your-api-key
```

#### 多适配器示例

```yaml
models:
  - name: landing
    type: Landing
    enable: true
    drivers:
      - model: turing,qa,tree,proxy
        driver: ai.llm.adapter.impl.LandingAdapter
      - model: image
        driver: ai.image.adapter.impl.LandingImageAdapter
        oss: landing
      - model: landing-tts,landing-asr
        driver: ai.audio.adapter.impl.LandingAudioAdapter
      - model: video
        driver: ai.video.adapter.impl.LandingVideoAdapter
    api_key: your-api-key
```

#### API Key 池

如果你只有一个 Key，继续使用 `api_key` 即可：

```yaml
api_key: sk-your-single-key
```

如果你有多个 Key，可以用 `api_keys` + `key_route`：

```yaml
api_keys:
  - sk-key1
  - sk-key2
  - sk-key3
key_route: polling
```

也可以直接写成逗号分隔：

```yaml
api_keys: sk-key1,sk-key2,sk-key3
key_route: failover
```

当 `api_key` 和 `api_keys` 同时存在时，Key 池优先生效；单个 Key 也会按需并入 Key 池。

<a id="routing-reference"></a>

### 路由表达式与故障转移

先为表达式中出现的每个名字配置 `models` 和 `functions.chat.backends`。在 `routers.items` 注册语法后，在 `route` 中引用它。

```yaml
routers:
  enable: true
  items:
    - name: best
      rule: (|,&)

    - name: pass
      rule: (%)
```

常见字段：

| 字段 | 说明 |
| --- | --- |
| `enable` | 全局路由器开关 |
| `items[].name` | 在 `functions.chat.route` 或 worker 中引用的路由器名 |
| `items[].rule` | 该路由器支持的路由语法 |

常见用法：

- `A|B` 表示轮询
- `A,B` 表示故障转移
- `A&B` 表示并行
- `%` 表示通配符

#### 聊天路由与运行选项

```yaml
functions:
  chat:
    route: pass(landing)
    filter: sensitive,priority,stopping,continue
    handle: failover
    grace_time: 20
    enable_queue_handle: true
    maxgen: 3
    context_length: 4096
    token_charge: false
    enable_auth: false
    enable_policy: true
    backends:
      - backend: landing
        model: cascade
        enable: true
        stream: true
        protocol: completion
        priority: 350
```

常见字段：

| 字段 | 说明 |
| --- | --- |
| `route` | 选择或组合后端的路由表达式 |
| `filter` | 引用 `filters.items` 中定义的过滤器名称，逗号分隔 |
| `backends` | 候选对话后端列表 |
| `enable_queue_handle` | 是否启用排队处理 |
| `handle` | 超载或失败时的处理策略 |
| `grace_time` | 处理器使用的宽限时间 |
| `maxgen` | 最大生成分支数或重试数 |
| `context_length` | 编排时使用的上下文长度 |
| `token_charge` | 是否启用 token 计费统计 |
| `enable_auth` | 是否启用鉴权检查 |
| `enable_policy` | 是否启用内置策略检查 |

对话后端定义在 `functions.chat.backends` 中。

例如，四个后端都已启用时可写 `best((landing&qwen),(kimi|chatgpt))`，组合并行、轮询和故障转移。

#### 组合三个已配置的聊天后端

LinkMind 并不只支持一个聊天后端。你可以保留多个模型后端，再用路由规则统一调度。

示例：

```yaml
functions:
  chat:
    route: best((landing&qwen),kimi)
    backends:
      - backend: landing
        model: cascade
        enable: true
        stream: true
        priority: 350

      - backend: qwen
        model: qwen-plus
        enable: true
        stream: true
        priority: 100

      - backend: kimi
        model: moonshot-v1-8k
        enable: true
        stream: true
        priority: 90
```

常见路由规则：

- `A|B`：轮询
- `A,B`：故障转移
- `A&B`：并行

<a id="medusa-cache"></a>

### Medusa 缓存与预请求

```yaml
stores:
  medusa:
    enable: true
    algorithm: hash,graph,llm
    reason_model: deepseek-r1
    aheads: 1
    producer_thread_num: 1
    consumer_thread_num: 2
    core_pool_size: 5
    maximum_pool_size: 30
    cache_persistent_path: medusa_cache
    cache_persistent_batch_size: 2
    flush: false
    cache_hit_window: 16
    cache_hit_ratio: 0.3
    temperature_tolerance: 0.1
```

常见字段：

| 字段 | 说明 |
| --- | --- |
| `enable` | 是否启用 Medusa 缓存加速 |
| `cache` / `memory` | 是否启用缓存与内存加速 |
| `algorithm` | 缓存与推理策略组合 |
| `enableL2` / `enableReasonDiver` | 高级缓存与推理开关 |
| `reason_model` | 可选的推理模型 |
| `aheads` | 预请求数量 |
| `producer_thread_num` / `consumer_thread_num` | 生产者与消费者线程数 |
| `core_pool_size` / `maximum_pool_size` | Prompt 缓存写入执行器线程池大小 |
| `consumeDelay` / `preDelay` | 队列时序控制参数 |
| `lcsRatioPromptInput` / `similarityCutoff` / `qa_similarity_cutoff` / `dynamicSimilarity` | 缓存命中阈值相关参数 |
| `cache_persistent_path` | 持久化缓存目录 |
| `cache_persistent_batch_size` | 批量刷盘大小 |
| `flush` | 启动时是否重建缓存 |
| `cache_hit_window` / `cache_hit_ratio` | 滑动窗口与命中率阈值 |
| `temperature_tolerance` | 缓存匹配时的温度容忍度 |
| `inits` | 可选的预热提示词 |

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
| Anthropic Messages | `POST /v1/messages` | `model`、`max_tokens`、`messages`、`system`（当前版本） | Claude Code 等 |

Responses 也可通过 `POST /responses` 访问，与 `POST /v1/responses` 对应；Anthropic Messages 也可通过 `POST /messages` 访问，与 `POST /v1/messages` 对应。以下示例统一使用 `/v1/…` 路径。

Responses 的可选字段 `instructions` 用于设置系统指令；Anthropic Messages 的 `system` 用于设置系统提示内容，当前版本需显式提供，具体要求见下文。

<a id="model-api-04"></a>

### Chat Completions 请求与响应

`POST /v1/chat/completions` 用于统一调用聊天模型。下面集中说明请求参数、`messages` 内容块、工具定义以及返回结果。

快速跳转：[请求参数](#chat-completions-请求参数) · [工具定义](#toolsfunction-完整结构) · [完整工具参数表](#工具参数逐项参考) · [返回结果](#chat-completions-返回结果)。

#### Chat Completions 请求参数

| 字段 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Content-Type` | header | string | 是 | 使用 `application/json` |
| `Authorization` | header | string | 按部署鉴权要求 | `Bearer <linkmind-api-key>`；需要鉴权时提供有效的客户端 Key |
| 请求体 | body | object | 是 | 包含下面的请求字段 |
| `model` | body | string | 独立 API 调用应提供 | 当前已启用的模型名；已有控制台会话设置模型偏好时，服务端可补入该偏好 |
| `messages` | body | array of objects | 是 | 按对话顺序提交的消息列表；应至少包含一条有效消息 |
| `messages[].role` | body | string | 是 | 消息角色，常用 `system`、`user`、`assistant`；工具结果使用 `tool`，具体角色支持取决于后端 |
| `messages[].content` | body | string / array of objects / null | 按消息用途 | 普通文本或内容块数组；普通对话需提供内容，仅含工具调用的 assistant 消息可使用 `null` |
| `messages[].content[].type` | body | string | 使用内容块时 | 内容块类型；文本块使用 `text`，其他类型需要模型支持 |
| `messages[].content[].text` | body | string | 文本块必填 | `type: "text"` 内容块的文本 |
| `messages[].tool_calls` | body | array of objects | 否 | 上一轮 assistant 返回的工具调用；元素包含 `id`、`type`、`function.name` 和 JSON 字符串 `function.arguments` |
| `messages[].tool_call_id` | body | string | 回传工具结果时 | 与对应工具调用的 `id` 匹配，配合 `role: "tool"` 使用 |
| `stream` | body | boolean | 否 | `true` 请求 SSE 流；省略或为 `false` 时使用非流式响应 |
| `store` | body | boolean | 否 | 请求保存本次调用结果的标志；实际保存行为由所选后端决定 |
| `max_completion_tokens` | body | integer | 否 | 本次生成允许的最大输出 Token 数；具体支持由后端决定 |
| `max_tokens` | body | integer | 否 | 兼容使用该字段的后端；按后端要求选择输出上限字段 |
| `temperature` | body | number | 否 | 采样温度；当前请求对象默认值为 `1` |
| `top_p` | body | number | 否 | 核采样参数，支持范围由后端决定 |
| `presence_penalty` | body | number | 否 | 主题重复惩罚参数，支持范围由后端决定 |
| `frequency_penalty` | body | number | 否 | 词频重复惩罚参数，支持范围由后端决定 |
| `tools` | body | array of objects | 否 | 提供给模型的工具定义，完整结构见下文 |
| `tool_choice` | body | string | 否 | 工具选择策略；当前请求字段为字符串，使用后端接受的策略值 |
| `parallel_tool_calls` | body | boolean | 否 | 是否允许并行工具调用，需要后端支持 |
| `response_format` | body | object | 否 | 返回格式配置，包含 `type`；使用 JSON Schema 时还包含 `json_schema` |
| `response_format.type` | body | string | 指定返回格式时 | 后端支持的返回格式类型 |
| `response_format.json_schema` | body | object | 使用 Schema 格式时 | 包含 `name`、`schema` 和可选 `strict`；`schema` 为约束输出的 Schema 对象 |
| `response_format.json_schema.name` | body | string | 使用 Schema 格式时 | 输出 Schema 的名称 |
| `response_format.json_schema.schema` | body | object | 使用 Schema 格式时 | 输出内容的结构与约束 |
| `response_format.json_schema.strict` | body | boolean | 否 | 是否请求严格遵循 Schema；当前对象默认 `true`，需要后端支持 |
| `stream_options` | body | object | 否 | 流式响应选项，按后端支持的选项填写 |
| `logprobs` | body | boolean | 否 | 是否请求 Token 概率信息，需要后端支持 |
| `category` | body | string | 否 | LinkMind 的数据或知识分类 |
| `sessionId` / `session_id` | body | string | 否 | LinkMind 会话标识，按业务需要保持同一会话 |
| `extra_body` / `extraBody` | body | object | 否 | 类型化扩展对象，包含 `userId`（别名 `user_id`、`user`）和 `mateUrl`（别名 `mate_url`）等已声明字段 |
| `extra_body.userId` | body | string | 否 | 逻辑业务用户标识，供社交 Skill、频道接口及识别调用方的流程使用；HTTP 别名为 `user_id`、`user`，Java 使用 `setUserId(...)` |
| `extra_body.mateUrl` | body | string | 否 | 级联业务上下文中的节点地址；自动补齐时取本机基础地址，HTTP 别名为 `mate_url`；部署要求见[部署、安全与级联组网](deployment-and-security_zh.md) |
| `chat_template_kwargs` | body | object | 否 | 后端聊天模板参数；实际支持和效果由所选模型后端决定 |

`stream`、`store`、`max_completion_tokens` 和 `tools` 均为可选字段。接口识别某个字段不代表每个模型都实现了该选项；采样、工具、结构化输出和保存行为应以所选后端的支持为准。

普通文本请求体示例：

```json
{
  "model": "qwen-plus",
  "stream": false,
  "max_completion_tokens": 256,
  "messages": [
    {
      "role": "user",
      "content": "Hello, LinkMind."
    }
  ]
}
```

#### extra_body 业务上下文

`POST /chat/completions` 和 `POST /v1/chat/completions` 支持可选的 `extra_body` 对象，用于承载业务侧带外元数据。它是请求 JSON 的顶层字段，内部保存类型化业务上下文；调用方不需要为此改变标准 `messages` 结构。

下面保留旧文档的完整 HTTP 请求体示例。请求表达“查询当前业务用户订阅频道”的意图，能否执行对应操作取决于已配置的社交 Skill 和业务接口；传入用户标识本身不会启用这些能力：

```json
{
  "model": "qwen-plus",
  "stream": false,
  "messages": [
    {
      "role": "user",
      "content": "列出我已订阅的频道。"
    }
  ],
  "extra_body": {
    "user_id": "u_1001"
  }
}
```

将请求体保存为本地 `chat-with-context.json` 后提交；`model` 应使用当前部署可用的模型名称：

```bash
curl --request POST "http://localhost:8080/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <linkmind-api-key>" \
  --data-binary "@chat-with-context.json"
```

沿用上方请求参数表中的字段定义：

- `extra_body.user_id` 是可选的逻辑业务用户标识，供社交 Skill、频道接口及需要识别业务调用方的流程使用；Java 属性名为 `userId`。
- `extra_body.user` 是同一用户标识的兼容别名，`extra_body.userId` 也可接收。三者使用字符串类型，选择一种写法即可，不应同时传入互相冲突的值。这里的 `user` 位于 `extra_body` 内，不是顶层同名字段或 `messages[].role`。
- 顶层 `extraBody` 是 `extra_body` 的兼容写法。当前类型化对象声明了 `userId`、`mateUrl` 及其别名，未声明的自定义属性不会作为该对象的字段保留。
- 用户标识表达业务上下文，不能替代部署要求的 API Key、登录态或其他鉴权信息；响应仍按 Chat Completions 的结构处理。

省略 `extra_body`、显式传 `null`、传 `{}` 或只填写 `mate_url`，会影响运行时的用户补齐条件，不能都视为同一种“未传用户”。完整条件、Skill 内部传递及 Java 对象用法见[系统集成与二次开发](integration_zh.md#业务上下文的运行时传递)。

#### messages 内容块

`messages` 按顺序携带历史对话。普通文本可以直接写入 `content`；使用文本内容块时，结构如下：

```json
{
  "role": "user",
  "content": [
    {"type": "text", "text": "请概括这份材料。"}
  ]
}
```

角色说明和内容块是请求格式的一部分；内容块的实际可用类型取决于模型。接续工具调用时，保留 assistant 返回的 `tool_calls`，再追加带有匹配 `tool_call_id` 的工具结果消息。

#### tools.function 完整结构

每个工具通过 `tools[]` 声明。下表中的“使用条件”描述定义该层结构时的要求；具体工具参数是否必填，由其所属对象的 `required` 列表决定。

| 字段路径 | 类型 | 使用条件与含义 |
| --- | --- | --- |
| `tools` | array of objects | 可省略；提供时每个元素声明一个工具 |
| `tools[].type` | string | 函数工具填写 `function` |
| `tools[].function` | object | 函数工具的定义 |
| `tools[].function.name` | string | 函数工具需提供名称，用于匹配返回的工具调用 |
| `tools[].function.description` | string | 可选的函数用途说明 |
| `tools[].function.strict` | boolean | 可选；当前对象默认 `true`，严格 Schema 的支持取决于后端 |
| `tools[].function.parameters` | object | 函数参数的 JSON Schema；有参数的工具应提供 |
| `tools[].function.parameters.type` | string | 参数对象通常填写 `object` |
| `tools[].function.parameters.required` | array of strings | 参数对象的必填属性名列表；当前对象未设置时为空列表 |
| `tools[].function.parameters.properties` | object | 参数名到该参数 Schema 的映射 |
| `tools[].function.parameters.additionalProperties` | boolean / object | 是否允许未声明属性，或其 Schema；当前对象默认 `false` |

属性 Schema 可以继续嵌套 `properties`、`items`、`required`、`additionalProperties`、`patternProperties`，并声明 `enum`、数值上下限、长度限制等约束。下面演示调用方声明一个工具；模型返回 `tool_calls` 后，由调用方执行工具并回传结果。

```json
{
  "model": "qwen-plus",
  "messages": [
    {
      "role": "user",
      "content": "请读取说明文件的前三行。"
    }
  ],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "read_document",
        "description": "读取指定文件的文本行。",
        "strict": false,
        "parameters": {
          "type": "object",
          "properties": {
            "path": {
              "type": "string",
              "description": "文件路径"
            },
            "limit": {
              "type": "integer",
              "description": "读取的行数",
              "minimum": 1
            }
          },
          "required": [
            "path"
          ],
          "additionalProperties": false
        }
      }
    }
  ]
}
```

#### 工具参数逐项参考

以下完整列出原接口表中的具体工具参数及其嵌套节点。所有路径均相对于 `tools[].function.parameters`，例如 `properties.path.type` 的完整路径为 `tools[].function.parameters.properties.path.type`。

“节点类型”表示 Schema 字段自身的 JSON 类型。例如 `properties.path` 是一个 Schema 对象，实际参数 `path` 的类型由其 `type` 值定义。表中的参数来自不同工具，应按工具选用各自的参数；必填条件以对应层级的 `required` 为准。

原表未给出具体枚举成员、上下限或默认值时，这里仅保留约束字段及类型，实际值由调用方提供的工具 Schema 确定。声明参数名称本身不会提供对应工具的执行实现。

| Schema 路径 | 节点类型 | 含义与约束 |
| --- | --- | --- |
| `properties.path` | object | `path` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.path.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.path.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.offset` | object | `offset` 参数的 Schema；是否必填由所属对象的 `required` 决定；结果偏移量。 |
| `properties.offset.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.offset.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.limit` | object | `limit` 参数的 Schema；是否必填由所属对象的 `required` 决定；返回记录数上限。 |
| `properties.limit.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.limit.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.limit.minimum` | integer | 数值下限；具体阈值由工具 Schema 提供 |
| `properties.file_path` | object | `file_path` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.file_path.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.file_path.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.filePath` | object | `filePath` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.filePath.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.filePath.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.file` | object | `file` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.file.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.file.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.oldText` | object | `oldText` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.oldText.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.oldText.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.newText` | object | `newText` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.newText.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.newText.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.edits` | object | `edits` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.edits.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.edits.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.edits.items` | object | 数组中每个元素的 Schema |
| `properties.edits.items.additionalProperties` | boolean | 本层对象是否允许未声明的属性 |
| `properties.edits.items.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.edits.items.required` | [string] | 本层对象必须包含的属性名列表 |
| `properties.edits.items.properties` | object | 本层对象的属性 Schema 映射 |
| `properties.edits.items.properties.oldText` | object | `oldText` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.edits.items.properties.oldText.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.edits.items.properties.oldText.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.edits.items.properties.newText` | object | `newText` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.edits.items.properties.newText.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.edits.items.properties.newText.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.old_string` | object | `old_string` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.old_string.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.old_string.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.old_text` | object | `old_text` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.old_text.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.old_text.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.oldString` | object | `oldString` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.oldString.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.oldString.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.new_string` | object | `new_string` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.new_string.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.new_string.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.new_text` | object | `new_text` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.new_text.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.new_text.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.newString` | object | `newString` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.newString.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.newString.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.content` | object | `content` 参数的 Schema；是否必填由所属对象的 `required` 决定；消息内容。 |
| `properties.content.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.content.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.command` | object | `command` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.command.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.command.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.workdir` | object | `workdir` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.workdir.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.workdir.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.env` | object | `env` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.env.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.env.patternProperties` | object | 属性名正则表达式与对应 Schema 的映射 |
| `properties.env.patternProperties["^(.*)$"]` | object | 匹配该正则表达式的属性值 Schema |
| `properties.env.patternProperties["^(.*)$"].type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.yieldMs` | object | `yieldMs` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.yieldMs.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.yieldMs.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.background` | object | `background` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.background.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.background.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.timeout` | object | `timeout` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.timeout.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.timeout.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.timeout.minimum` | integer | 数值下限；具体阈值由工具 Schema 提供 |
| `properties.pty` | object | `pty` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.pty.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.pty.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.elevated` | object | `elevated` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.elevated.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.elevated.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.host` | object | `host` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.host.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.host.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.security` | object | `security` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.security.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.security.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.ask` | object | `ask` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.ask.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.ask.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.node` | object | `node` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.node.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.node.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.action` | object | `action` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.action.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.action.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.action.enum` | [string] | 允许值列表；具体成员由工具 Schema 提供 |
| `properties.sessionId` | object | `sessionId` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.sessionId.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.sessionId.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.data` | object | `data` 参数的 Schema；是否必填由所属对象的 `required` 决定；接口返回数据。 |
| `properties.data.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.data.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.keys` | object | `keys` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.keys.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.keys.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.keys.items` | object | 数组中每个元素的 Schema |
| `properties.keys.items.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.hex` | object | `hex` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.hex.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.hex.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.hex.items` | object | 数组中每个元素的 Schema |
| `properties.hex.items.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.literal` | object | `literal` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.literal.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.literal.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.text` | object | `text` 参数的 Schema；是否必填由所属对象的 `required` 决定；输入文本。 |
| `properties.text.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.text.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.bracketed` | object | `bracketed` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.bracketed.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.bracketed.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.eof` | object | `eof` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.eof.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.eof.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.gatewayUrl` | object | `gatewayUrl` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.gatewayUrl.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.gatewayToken` | object | `gatewayToken` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.gatewayToken.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.timeoutMs` | object | `timeoutMs` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.timeoutMs.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.includeDisabled` | object | `includeDisabled` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.includeDisabled.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.job` | object | `job` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.job.additionalProperties` | boolean | 本层对象是否允许未声明的属性 |
| `properties.job.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.job.properties` | object | 本层对象的属性 Schema 映射 |
| `properties.jobId` | object | `jobId` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.jobId.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.id` | object | `id` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.id.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.patch` | object | `patch` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.patch.additionalProperties` | boolean | 本层对象是否允许未声明的属性 |
| `properties.patch.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.patch.properties` | object | 本层对象的属性 Schema 映射 |
| `properties.mode` | object | `mode` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.mode.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.mode.enum` | [string] | 允许值列表；具体成员由工具 Schema 提供 |
| `properties.runMode` | object | `runMode` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.runMode.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.runMode.enum` | [string] | 允许值列表；具体成员由工具 Schema 提供 |
| `properties.contextMessages` | object | `contextMessages` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.contextMessages.minimum` | integer | 数值下限；具体阈值由工具 Schema 提供 |
| `properties.contextMessages.maximum` | integer | 数值上限；具体阈值由工具 Schema 提供 |
| `properties.contextMessages.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.kinds` | object | `kinds` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.kinds.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.kinds.items` | object | 数组中每个元素的 Schema |
| `properties.kinds.items.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.activeMinutes` | object | `activeMinutes` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.activeMinutes.minimum` | integer | 数值下限；具体阈值由工具 Schema 提供 |
| `properties.activeMinutes.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.messageLimit` | object | `messageLimit` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.messageLimit.minimum` | integer | 数值下限；具体阈值由工具 Schema 提供 |
| `properties.messageLimit.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.sessionKey` | object | `sessionKey` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.sessionKey.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.includeTools` | object | `includeTools` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.includeTools.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.label` | object | `label` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.label.minLength` | integer | 字符串最小长度；具体值由工具 Schema 提供 |
| `properties.label.maxLength` | integer | 字符串最大长度；具体值由工具 Schema 提供 |
| `properties.label.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.agentId` | object | `agentId` 参数的 Schema；是否必填由所属对象的 `required` 决定；目标 Agent 标识。 |
| `properties.agentId.minLength` | integer | 字符串最小长度；具体值由工具 Schema 提供 |
| `properties.agentId.maxLength` | integer | 字符串最大长度；具体值由工具 Schema 提供 |
| `properties.agentId.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.message` | object | `message` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.message.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.timeoutSeconds` | object | `timeoutSeconds` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.timeoutSeconds.minimum` | integer | 数值下限；具体阈值由工具 Schema 提供 |
| `properties.timeoutSeconds.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.task` | object | `task` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.task.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.runtime` | object | `runtime` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.runtime.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.runtime.enum` | [string] | 允许值列表；具体成员由工具 Schema 提供 |
| `properties.resumeSessionId` | object | `resumeSessionId` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.resumeSessionId.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.resumeSessionId.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.model` | object | `model` 参数的 Schema；是否必填由所属对象的 `required` 决定；调用时使用的模型名称。 |
| `properties.model.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.thinking` | object | `thinking` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.thinking.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.cwd` | object | `cwd` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.cwd.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.runTimeoutSeconds` | object | `runTimeoutSeconds` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.runTimeoutSeconds.minimum` | integer | 数值下限；具体阈值由工具 Schema 提供 |
| `properties.runTimeoutSeconds.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.thread` | object | `thread` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.thread.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.cleanup` | object | `cleanup` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.cleanup.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.cleanup.enum` | [string] | 允许值列表；具体成员由工具 Schema 提供 |
| `properties.sandbox` | object | `sandbox` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.sandbox.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.sandbox.enum` | [string] | 允许值列表；具体成员由工具 Schema 提供 |
| `properties.streamTo` | object | `streamTo` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.streamTo.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.streamTo.enum` | [string] | 允许值列表；具体成员由工具 Schema 提供 |
| `properties.attachments` | object | `attachments` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.attachments.maxItems` | integer | 数组元素数上限；具体值由工具 Schema 提供 |
| `properties.attachments.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.attachments.items` | object | 数组中每个元素的 Schema |
| `properties.attachments.items.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.attachments.items.required` | [string] | 本层对象必须包含的属性名列表 |
| `properties.attachments.items.properties` | object | 本层对象的属性 Schema 映射 |
| `properties.attachments.items.properties.name` | object | `name` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.attachments.items.properties.name.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.attachments.items.properties.content` | object | `content` 参数的 Schema；是否必填由所属对象的 `required` 决定；消息内容。 |
| `properties.attachments.items.properties.content.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.attachments.items.properties.encoding` | object | `encoding` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.attachments.items.properties.encoding.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.attachments.items.properties.encoding.enum` | [string] | 允许值列表；具体成员由工具 Schema 提供 |
| `properties.attachments.items.properties.mimeType` | object | `mimeType` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.attachments.items.properties.mimeType.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.attachAs` | object | `attachAs` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.attachAs.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.attachAs.properties` | object | 本层对象的属性 Schema 映射 |
| `properties.attachAs.properties.mountPath` | object | `mountPath` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.attachAs.properties.mountPath.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.target` | object | `target` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.target.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.recentMinutes` | object | `recentMinutes` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.recentMinutes.minimum` | integer | 数值下限；具体阈值由工具 Schema 提供 |
| `properties.recentMinutes.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.query` | object | `query` 参数的 Schema；是否必填由所属对象的 `required` 决定；查询条件。 |
| `properties.query.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.query.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.count` | object | `count` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.count.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.count.minimum` | integer | 数值下限；具体阈值由工具 Schema 提供 |
| `properties.count.maximum` | integer | 数值上限；具体阈值由工具 Schema 提供 |
| `properties.count.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.region` | object | `region` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.region.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.region.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.safeSearch` | object | `safeSearch` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.safeSearch.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.safeSearch.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.url` | object | `url` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.url.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.url.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.extractMode` | object | `extractMode` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.extractMode.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.extractMode.enum` | [string] | 允许值列表；具体成员由工具 Schema 提供 |
| `properties.extractMode.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.extractMode.default` | string | 该参数的默认值声明；原表未指定具体值 |
| `properties.maxChars` | object | `maxChars` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.maxChars.description` | string | 该参数或嵌套字段的说明文本 |
| `properties.maxChars.minimum` | integer | 数值下限；具体阈值由工具 Schema 提供 |
| `properties.maxChars.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.maxResults` | object | `maxResults` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.maxResults.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.minScore` | object | `minScore` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.minScore.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.from` | object | `from` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.from.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |
| `properties.lines` | object | `lines` 参数的 Schema；是否必填由所属对象的 `required` 决定 |
| `properties.lines.type` | string | 该 Schema 所描述值的类型；具体类型值由工具定义 |

#### Chat Completions 返回结果

非流式成功响应通常为 HTTP **200**，使用聊天完成对象。下面保留完整的普通文本响应示例，标识、时间戳、文本和用量均为示例值：

```json
{
  "id": "chatcmpl-example-001",
  "object": "chat.completion",
  "created": 1700000000,
  "model": "qwen-plus",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "Hello! How can I help you today?"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 12,
    "completion_tokens": 10,
    "total_tokens": 22
  }
}
```

| 字段路径 | 类型 | 含义与出现条件 |
| --- | --- | --- |
| `id` | string | 本次聊天完成的标识 |
| `object` | string | 对象类型；非流式聊天完成通常为 `chat.completion` |
| `created` | integer | 创建时间的 Unix 时间戳，单位为秒 |
| `model` | string | 本次使用的模型 |
| `choices` | array of objects | 模型返回的候选结果列表 |
| `choices[].index` | integer | 候选项索引 |
| `choices[].message` | object | 非流式 assistant 消息 |
| `choices[].message.role` | string | 返回消息的角色，正常回答为 `assistant` |
| `choices[].message.content` | string / null | 普通文本回答的内容；仅含工具调用的消息可能为 `null` |
| `choices[].message.reasoning_content` | string | 后端提供时的推理内容 |
| `choices[].message.tool_calls` | array of objects | 模型请求执行的工具调用，出现条件取决于模型输出 |
| `choices[].message.tool_calls[].id` | string | 工具调用标识，回传结果时作为 `tool_call_id` |
| `choices[].message.tool_calls[].type` | string | 工具类型，函数调用为 `function` |
| `choices[].message.tool_calls[].function` | object | 包含函数 `name` 和 JSON 字符串形式的 `arguments` |
| `choices[].message.tool_calls[].function.name` | string | 要调用的函数名称 |
| `choices[].message.tool_calls[].function.arguments` | string | 工具参数的 JSON 字符串，使用前需解析 |
| `choices[].finish_reason` | string / null | 结束原因，常见 `stop`、`length`、`tool_calls`；流式中间事件可能为 `null` |
| `choices[].delta` | object | 流式增量消息，按字段逐步拼接内容 |
| `choices[].logprobs` | object / null | 后端提供时的 Token 概率信息 |
| `usage` | object | 请求用量统计；是否返回及流式出现时机取决于后端 |
| `usage.prompt_tokens` | integer | 输入 Token 数 |
| `usage.completion_tokens` | integer | 生成的 Token 数 |
| `usage.total_tokens` | integer | 本次请求总 Token 数 |
| `usage.prompt_tokens_details.cached_tokens` | integer | 后端提供时的缓存输入 Token 数 |
| `usage.completion_tokens_details.reasoning_tokens` | integer | 后端提供时的推理 Token 数 |
| `usage.prompt_cache_hit_tokens` / `usage.prompt_cache_miss_tokens` | integer | 后端提供时的输入缓存命中和未命中统计 |
| `system_fingerprint` | string | 后端提供时的系统指纹 |

流式请求设置 `stream: true`，按 SSE 的 `data:` 事件读取 `choices[].delta`，并持续读取到 `data: [DONE]`。HTTP 200 或首个事件仅表示响应已开始，仍需检查后续错误及完成标记。

<a id="model-api-02"></a>

### OpenAI Responses 示例

```bash
curl http://localhost:8080/v1/responses \
  -H "Authorization: Bearer <linkmind-api-key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"qwen-plus","instructions":"你是一位技术助手，请简洁回答。","input":"用一句话说明路由的作用。"}'
```

<a id="model-api-03"></a>

### Anthropic Messages 请求与响应

Messages 接口可调用 LinkMind 中已启用的聊天模型，支持普通 JSON 响应和 SSE 流式响应。

请求使用 `Content-Type: application/json`。需要鉴权时，可传入 `x-api-key: <linkmind-api-key>`，也可使用 `Authorization: Bearer <linkmind-api-key>`；两者同时提供时，非空 `x-api-key` 优先。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `model` | string | 是 | 当前已启用的聊天模型名，可通过 `GET /v1/models` 查询 |
| `max_tokens` | integer | 是 | 最大输出 Token 数 |
| `messages` | array | 是 | 非空消息列表；每条消息包含 `role` 和 `content`，常用角色为 `user`、`assistant` |
| `system` | string / array | 当前版本是 | 系统提示内容，可以是文本或文本内容块数组，例如 `[{"type":"text","text":"请简洁回答。"}]` |
| `stream` | boolean | 否 | 默认 `false`；设为 `true` 返回 SSE 流式事件 |

**当前版本限制：**请求中省略 `system` 或将其设为 `null`，会返回 HTTP 400，错误信息为 `message content is required`。调用时请显式提供 `system`，以下示例已包含该字段。

请求示例：

```bash
curl http://localhost:8080/v1/messages \
  -H "Authorization: Bearer <linkmind-api-key>" \
  -H "anthropic-version: 2023-06-01" \
  -H "Content-Type: application/json" \
  -d '{"model":"qwen-plus","max_tokens":128,"system":"你是一位技术助手，请简洁回答。","messages":[{"role":"user","content":"你好"}]}'
```

#### 非流式响应

当 `stream` 为 `false` 或未提供时，响应为 Messages 格式的 JSON，主要字段如下：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 本次响应的标识 |
| `type` | string | 固定为 `"message"` |
| `role` | string | 固定为 `"assistant"` |
| `model` | string | 本次响应使用的模型 |
| `content` | array | 响应内容块；文本块的 `type` 为 `"text"`，文本位于该块的 `text` 字段 |
| `stop_reason` | string | 停止原因，例如正常结束 `end_turn`、达到输出上限 `max_tokens`、请求工具调用 `tool_use` |
| `usage` | object | Token 用量，包含输入 `input_tokens` 和输出 `output_tokens` |

读取文本时，遍历 `content`，提取其中 `type: "text"` 内容块的 `text`。

#### 流式响应

将 `stream` 设为 `true` 后，响应类型为 `text/event-stream`。客户端需要处理以下事件：

| 事件 | 含义 |
| --- | --- |
| `message_start` | 消息开始，提供消息标识、角色、模型等初始信息 |
| `content_block_start` | 一个内容块开始 |
| `content_block_delta` | 内容块的增量数据；文本增量位于 `delta.text`，工具参数增量位于 `delta.partial_json` |
| `content_block_stop` | 一个内容块结束 |
| `message_delta` | 消息级更新，包含停止原因和用量信息 |
| `message_stop` | 整条消息结束 |

按内容块的 `index` 汇集增量，并持续读取到 `message_stop`。客户端应使用 Messages 的内容块和事件格式解析响应；Chat Completions 的 `choices` 字段和 `data: [DONE]` 结束标记不适用于此接口。

Claude Code 的环境变量配置、验证与排查见[Claude Code 接入教程](best-practices/cli/claude-code.md#zh-cn)。

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
  -d '{"model":"text-embedding-v3","input":["LinkMind embedding verification"]}'
```

成功响应的 `data` 为二维向量数组；将它用于知识库前，应确认维度与目标向量库索引一致。完整请求、批量输入和返回字段见下方的 [Embedding 生成](#embedding-生成)。

<a id="media-endpoints"></a>

| 能力 | 功能块 / 接口 | 主要输入 |
| --- | --- | --- |
| 语音识别、语音合成 | `speech2text` / `text2speech`；`POST /audio/speech2text`、`GET /audio/text2speech` | 音频文件或文本 |
| 图片理解、生成、增强 | `image2text`、`text2image`、`image2enhance`；`POST /image/text2image`、`POST /image/image2text`、`POST /image/image2enhance`、`POST /image/image2ocr` | 图片 URL/文件或提示词 |
| 视频生成、追踪、增强 | `text2video`、`image2video`、`video2track`、`video2enhance`；图生视频：`POST /image/image2video`（兼容 `POST /image/generateVideo`）；视频追踪与增强：`POST /video/video2tracking`、`POST /video/video2enhance` | 提示词、图片或视频 |
| 文档 OCR、抽取、结构化 | `doc2ocr`、`doc2ext`、`doc2struct`；`POST /ocr/doc2ocr`、`POST /doc/doc2ext`、`POST /doc/doc2struct` | 文档文件 |
| 自然语言转 SQL | `text2sql`，`POST /sql/text2sql` | 数据问题与可访问的数据库结构 |

多模态后端条目通常包含 `backend`、`model`、`enable`、`priority`。先用小文件或短提示验证输入格式、上游权限与结果，再提高并发或接入生产文件。

<a id="function-backends"></a>

### 多模态与文档处理后端配置

下面将 18 种功能列在同一份 `functions` 示例中。选择需要的条目并把 `backend` 指向实际配置的 Provider。复制到已有 YAML 时，合并到现有的 `functions` 区块。`speech2clone.others` 填写音色/说话人 ID，`text2qa` 使用单个对象。

| 字段 | 说明 |
| --- | --- |
| `backend` | 在 `models` 里定义的后端名称 |
| `model` | 该后端负责的模型 ID |
| `enable` | 是否启用这一条能力配置 |
| `priority` | 选择优先级 |
| `stream` | 是否流式输出 |
| `protocol` | 对话协议，常见为 `completion` 或 `response` |
| `others` | 提供方专用补充值，例如 speaker ID |
| `filter` | 可选的过滤器覆盖 |
| `router` | 可选的路由覆盖 |
| `concurrency` | 可选并发限制 |

```yaml
functions:
  # 翻译
  translate:
    - backend: ernie
      model: translate
      enable: true
      priority: 10
  # 语音转文本
  speech2text:
    - backend: qwen
      model: asr
      enable: true
      priority: 10
  # 文本转语音
  text2speech:
    - backend: landing
      model: tts
      enable: true
      priority: 10
  # 语音克隆
  speech2clone:
    - backend: doubao
      model: openspeech
      enable: true
      priority: 10
      others: your-speaker-id
  # 文本生成图片
  text2image:
    - backend: spark
      model: tti
      enable: true
      priority: 10
  # 图片转文本
  image2text:
    - backend: ernie
      model: Fuyu-8B
      enable: true
      priority: 10
  # 图片 OCR
  image2ocr:
    - backend: qwen
      model: ocr
      enable: true
      priority: 10
  # 图片增强
  image2enhance:
    - backend: ernie
      model: enhance
      enable: true
      priority: 10
  # 文本生成视频
  text2video:
    - backend: landing
      model: video
      enable: true
      priority: 10
  # 图片生成视频
  image2video:
    - backend: qwen
      model: vision
      enable: true
      priority: 10
  # 视频跟踪
  video2track:
    - backend: landing
      model: video
      enable: true
      priority: 10
  # 视频增强
  video2enhance:
    - backend: qwen
      model: vision
      enable: true
      priority: 10
  # 文档 OCR
  doc2ocr:
    - backend: qwen
      model: qwen-vl-ocr
      enable: true
      priority: 15
  # 文档指令提取
  doc2instruct:
    - backend: landing
      model: cascade
      enable: true
      priority: 10
  # Text-to-SQL
  text2sql:
    - backend: landing
      model: cascade
      enable: true
      priority: 10
  # 文档内容抽取
  doc2ext:
    - backend: landing
      model: cascade
      enable: true
      priority: 10
  # 文档结构化
  doc2struct:
    - backend: landing
      model: cascade
      enable: true
      priority: 10
  # 文本生成问答
  text2qa:
    enable: true
    model: cascade
```

#### Embedding

```yaml
functions:
  embedding:
    - backend: qwen
      type: Qwen
      api_key: your-api-key
      model_name: text-embedding
      api_endpoint: https://your-endpoint.example.com
      secret_key: your-secret-key
      model_path: /path/to/local-embedding.model
```

常见字段：

| 字段 | 说明 |
| --- | --- |
| `backend` | 提供方名称 |
| `type` | 提供方标签 |
| `api_key` | API Key |
| `model_name` | Embedding 模型名 |
| `api_endpoint` | 远程接口地址 |
| `secret_key` | 可选密钥 |
| `model_path` | 本地 Embedding 模型路径 |

<a id="model-api-19"></a>

### Embedding 生成

`POST /v1/embeddings` 接收文本数组并返回向量列表。当前入口按字符串数组解析 `input`，单条文本也应写成 `["文本"]`，不能直接传单个字符串。

#### Embedding 请求参数与批量示例

下面保留旧文档的完整单条输入示例：

```json
{
  "model": "text-embedding",
  "input": [
    "Hello LinkMind"
  ]
}
```

| 字段 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 请求体 | body | object | 是 | JSON 请求对象 |
| `Content-Type` | header | string | 建议提供 | `application/json`，说明请求体内容类型 |
| `Authorization` | header | string | 按部署鉴权要求 | `Bearer <linkmind-api-key>`；启用鉴权时携带有效的客户端 Key |
| `input` | body | string[] | 是 | 待生成向量的文本列表，应提供非空数组和非空文本；单条输入也使用数组，批量输入在同一数组中放入多条文本 |
| `model` | body | string | 否 | 保留旧文档中的模型名称字段及示例值。当前接口不使用此字段选择后端或覆盖实际模型，实际使用部署中的 Embedding 配置 |

旧表将 `model` 标为必填并描述为调用模型；当前实现按部署配置创建 Embedding 后端，未使用请求中的 `model` 进行选择。仅改变请求里的模型名不会切换实际使用的后端或模型。排查模型、Key 或维度问题时，应检查生效的 Embedding 配置。

两条文本的批量请求示例：

```bash
curl --request POST "http://localhost:8080/v1/embeddings" \
  -H "Authorization: Bearer <linkmind-api-key>" \
  -H "Content-Type: application/json" \
  --data-raw '{"model":"text-embedding","input":["Hello LinkMind","How does model routing work?"]}'
```

批量条数和单条文本长度限制取决于所选后端；先用短文本和小批量验证。上方最小调用示例也已使用只有一个元素的 `input` 数组。

#### Embedding 完整响应与字段说明

正常响应使用 HTTP **200** 和 JSON 响应体。下面保留旧文档针对单条输入的完整响应示例；四个数值仅用于说明结构，实际向量维度由生效的模型决定：

```json
{
  "status": "success",
  "data": [
    [
      0.12,
      -0.03,
      0.44,
      0.08
    ]
  ]
}
```

| 字段 | 类型 | 出现条件与含义 |
| --- | --- | --- |
| `status` | string | 后端返回的向量列表非空时为 `success`，为空列表时为 `failed` |
| `data` | number[][] | 成功响应中的 Embedding 向量列表，外层为结果列表；当前失败分支不返回此字段 |
| `data[]` | number[] | 一条结果向量，其长度为实际维度；不能将示例中的四个数值视为固定维度 |
| `data[][]` | number | 向量分量，按后端结果返回，可包含正数、负数或零 |

后端返回空列表时，当前入口返回以下 JSON，通常仍为 HTTP **200**：

```json
{
  "status": "failed"
}
```

当前入口根据返回列表是否为空决定 `success` 或 `failed`，不在此处核验结果数量、每条向量是否非空或维度是否一致。因此应继续检查 `data` 的实际内容。请求解析、配置或上游调用异常不保证返回同一失败 JSON，应同时检查 HTTP 状态和日志。

#### 批量结果验证与排查

1. 先运行单条文本示例，确认 `status` 为 `success`，`data` 包含一条非空数值向量，并记录实际维度。
2. 再运行两条文本的批量示例，核对返回向量数量与输入条数一致、每条向量非空且维度一致。按所用后端的顺序约定核对输入与结果；必要时分别提交两条文本，对照结果确认对应关系。
3. 接入知识库前，确认实际 Embedding 模型和维度与目标向量集合一致；请求中的 `model` 字段不能用于切换配置来修正不匹配。
4. 若返回 `failed`、数量不符或空向量，检查输入数组、文本长度和批量限制、生效的 Embedding 后端及模型、Key 权限、上游连通性和服务日志。先用短文本缩小问题范围，再恢复目标批量。

<a id="model-api-06"></a>

### 语音识别（ASR）

`POST /audio/speech2text` 接收音频二进制流，返回识别文本。先配置可用的语音识别后端，再用内容已知的短音频验证。

#### ASR 请求参数与上传示例

| 字段 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `model` | query | string | 否 | 已注册的 ASR 后端标识；未传、为空或为 `default` 时，从已启用且 `priority > 0` 的后端中按优先级从高到低选择第一个；显式指定的标识必须能找到对应后端 |
| `format` | query | string | 否 | 上传音频的格式，例如 `wav`；入口用它作为保存音频的文件后缀，未传时使用 `wav`。实际可识别的格式由所选后端决定 |
| `Content-Type` | header | string | 建议设置 | 二进制上传可使用 `application/octet-stream` |
| `Authorization` | header | string | 按部署鉴权要求 | `Bearer <linkmind-api-key>`；启用鉴权时携带有效的客户端 Key |
| 请求体 | body | string(binary) | 是 | 音频文件的原始字节 |

将测试音频放在 `./examples/audio/sample.wav`，或将命令中的路径替换为实际文件路径。`model` 和 `format` 放在 URL 查询参数中，文件使用 `--data-binary` 上传：

```bash
curl --request POST \
  "http://localhost:8080/audio/speech2text?model=default&format=wav" \
  -H "Authorization: Bearer <linkmind-api-key>" \
  -H "Content-Type: application/octet-stream" \
  --data-binary @./examples/audio/sample.wav
```

要选择特定 ASR 后端，将 `model=default` 替换为部署中注册的后端标识。`format` 描述上传音频，不是识别结果的返回格式；填写后缀不会改变文件的实际编码，应与音频文件及后端支持的格式一致。

#### ASR 返回结果与验证

正常响应使用 HTTP **200**，响应体为 JSON。以下示例使用项目定义的 ASR 成功业务码 `20000000`：

```json
{
  "result": "Hello LinkMind",
  "status": 20000000
}
```

| 字段 | 类型 | 出现条件与含义 |
| --- | --- | --- |
| `result` | string | 成功识别时的文本内容；失败时可能没有此字段 |
| `status` | integer | 识别业务状态码；项目定义成功为 `20000000`、失败为 `40000000`，部分适配器可能返回上游状态码 |
| `task_id` | string | 后端提供时的识别任务标识 |
| `message` | string | 后端提供的状态说明或错误信息 |

HTTP 状态表示请求在 HTTP 层的处理结果，JSON 中的 `status` 表示识别业务结果。客户端应同时检查 HTTP 状态和业务状态码，并以识别结果确认本次调用是否成功。

验证时检查业务状态、`result` 是否包含预期文本，并与测试音频的实际内容核对。若返回 `null` 或没有有效结果，先检查 `model` 标识、后端注册情况，以及默认选择所需的 `enable` 和正数 `priority`；若返回失败状态，再结合 `message` 和日志检查后端配置、认证、网络、音频格式与采样参数。支持的编码、采样率和声道数以所选 ASR 后端为准。

<a id="model-api-07"></a>

### 文字转语音（TTS）

`GET /audio/text2speech` 根据查询参数合成语音，成功时直接返回音频二进制流。先配置可用的 TTS 后端，再用一段短文本验证。

#### TTS 请求参数与下载示例

| 字段 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `text` | query | string | 是 | 待合成的非空文本；中文、空格和特殊字符应进行 URL 编码 |
| `model` | query | string | 否 | 已注册的 TTS 后端标识；未传、为空或为 `default` 时，从已启用且 `priority > 0` 的后端中按优先级从高到低选择第一个 |
| `format` | query | string | 否 | 期望的合成音频格式；是否采用、支持哪些值以及省略时的格式由所选后端决定 |
| `Authorization` | header | string | 按部署鉴权要求 | `Bearer <linkmind-api-key>`；启用鉴权时携带有效的客户端 Key |

使用 `--get` 发起 GET 请求，使用 `--data-urlencode` 编码查询参数。下面的示例请求 `wav` 格式，将响应头保存到 `speech.headers`，响应内容保存到 `speech.audio`：

```bash
curl --get "http://localhost:8080/audio/text2speech" \
  -H "Authorization: Bearer <linkmind-api-key>" \
  --data-urlencode "text=你好，LinkMind。" \
  --data-urlencode "model=default" \
  --data-urlencode "format=wav" \
  --dump-header speech.headers \
  --output speech.audio
```

可将 `model=default` 替换为部署中注册的 TTS 后端标识，并按该后端支持的格式调整或省略 `format`。当前适配器对格式的处理存在差异：例如 Alibaba 适配器固定请求 `wav`；VolcEngine 适配器会读取 `format`，未指定时使用 `mp3`。实际音频格式应以所选后端输出为准。

#### TTS 返回结果与验证

成功时通常返回 HTTP **200**，响应体是音频文件的原始字节。当前公共入口将音频响应的 `Content-Type` 固定为 `audio/mpeg`，不会根据 `format` 动态调整；保存和播放时应确认实际音频编码，再选择合适的文件扩展名和播放器。

当后端返回项目定义的 TTS 失败业务码 `40000000` 时，接口返回 `application/json` 错误对象，例如：

```json
{
  "status": 40000000,
  "message": "Speech synthesis failed."
}
```

| 字段 | 类型 | 出现条件与含义 |
| --- | --- | --- |
| `status` | integer | 失败分支的业务状态码；上述分支使用 `40000000` |
| `message` | string | 后端提供时的状态说明或错误信息；示例文本不代表固定错误消息 |
| `task_id` | string | 后端提供时的合成任务标识 |
| `result` | string | 后端提供时的结果信息；失败时可能没有此字段 |

失败 JSON 也可能伴随 HTTP 200 返回。先检查 `speech.headers` 中的 HTTP 状态和 `Content-Type`：若为 JSON，读取响应内容中的业务状态和错误信息；确认收到有效音频后，再用支持其实际格式的播放器打开，并核对朗读内容是否与 `text` 一致。

若没有音频或播放失败，检查 `text` 的 URL 编码、TTS 后端标识及启用状态、默认选择所需的正数 `priority`、后端认证和格式支持。若日志显示音频下载失败，还需检查 LinkMind 服务访问生成音频存储地址的网络和权限；该分支可能返回 HTTP 500 或不完整的音频内容。

<a id="model-api-08"></a>

### 图片理解（看图说话）

`POST /image/image2text` 上传图片并返回图片描述、分类和图片结果地址，兼容路径为 `POST /image/imageToText`。实际输出能力由部署中的图片理解后端决定。

#### 图片理解请求与上传示例

| 字段 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 请求体 | body | object（multipart 表单） | 是 | 包含上传的图片文件 |
| `file` | form-data | string(binary) | 是 | 图片文件的二进制内容；提供带文件名和扩展名的图片 |
| `Content-Type` | header | string | 是，由客户端生成 | `multipart/form-data`，包含与请求体一致的 `boundary`；使用 cURL 的 `--form` 可自动生成 |
| `Authorization` | header | string | 按部署鉴权要求 | `Bearer <linkmind-api-key>`；启用鉴权时携带有效的客户端 Key |

准备一张内容已知的图片 `sample.png`，将路径替换为实际文件后执行：

```bash
curl --request POST "http://localhost:8080/image/image2text" \
  -H "Authorization: Bearer <linkmind-api-key>" \
  --form "file=@sample.png"
```

本示例上传一张图片，由 cURL 自动设置 multipart 请求头和边界。使用兼容路径时，只需将 URL 改为 `/image/imageToText`。

#### 旧参数兼容说明

以下保留旧文档中的字段及示例值。当前入口不读取这四个字段，也不会将它们传给图片理解后端；将它们放入表单或查询参数中均不会影响本接口的处理结果：

| 旧字段 | 示例值 | 当前支持情况 |
| --- | --- | --- |
| `model` | `default` | 不生效；当前入口根据部署配置选择可用的图片理解后端 |
| `emotion` | `default` | 不读取、不传递 |
| `text` | `Please describe the image.` | 不作为图片理解提示词传递 |
| `category` | `default` | 不读取、不传递 |

#### 图片理解返回结果与验证

正常响应通常使用 HTTP **200** 和 JSON 响应体。以下为完整响应结构示例，字段值和地址仅用于说明：

```json
{
  "status": "success",
  "classification": "cat",
  "caption": "A cat sitting on a sofa.",
  "samUrl": "https://example.com/segmented-image.png"
}
```

| 字段 | 类型 | 出现条件与含义 |
| --- | --- | --- |
| `status` | string | 调用状态，例如 `success`、`failed`；失败时其他字段可能缺失 |
| `classification` | string | 后端给出的图片类别；后端未提供类别时，当前入口补为空字符串 |
| `caption` | string | 图片识别或描述文本；当前入口会尝试将描述翻译为中文，实际文本以返回内容为准 |
| `samUrl` | string | 后端给出的图片结果或预览地址；后端返回 `null` 时，入口复制原图并补入 `static/img/split/<id>.png` 形式的相对预览路径 |

`samUrl` 可能指向分割结果，也可能指向原图副本，不能仅凭该字段存在就判断完成了图片分割。相对路径应按部署的应用根地址解析。

验证时检查业务状态，核对 `caption` 与图片实际内容是否相符，并检查返回的图片地址是否可访问。`classification` 为空字符串本身不代表调用失败。若预期得到分割结果，应进一步确认后端支持该能力以及实际返回图片的内容。

若返回空响应、`failed` 或 HTTP 错误，先检查请求是否为 multipart、是否包含有效的图片文件、文件扩展名和大小是否符合部署要求，再检查图片理解后端、认证、网络及日志。HTTP 200 本身不能替代对识别结果的检查。

<a id="model-api-09"></a>

### 图片生成（文生图）

`POST /image/text2image` 根据提示词生成图片。`POST /v1/images/generations` 使用同一生成处理逻辑，两种入口均接收 JSON 请求并返回 JSON 结果。先配置可用的图片生成后端，再验证生成结果。

#### 图片生成请求参数与示例

| 字段 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `Content-Type` | header | string | 是 | 使用 `application/json` |
| `Authorization` | header | string | 按部署鉴权要求 | `Bearer <linkmind-api-key>`；启用鉴权时携带有效的客户端 Key |
| 请求体 | body | object | 是 | 描述图片生成请求的 JSON 对象 |
| `prompt` | body | string | 是 | 待生成图片的文字描述，应提供非空提示词 |
| `model` | body | string | 否 | 已注册的图片生成后端标识；未提供或未匹配到该标识时，当前实现使用默认可用后端 |

完整的最小请求体：

```json
{
  "prompt": "A cat sitting on a sofa."
}
```

对应的 cURL 调用：

```bash
curl --request POST "http://localhost:8080/image/text2image" \
  -H "Authorization: Bearer <linkmind-api-key>" \
  -H "Content-Type: application/json" \
  -d '{"prompt":"A cat sitting on a sofa."}'
```

使用另一入口时，将 URL 改为 `http://localhost:8080/v1/images/generations`，请求体保持相同。若要指定后端，在 JSON 中增加 `model` 并填写实际注册标识。当前入口会尝试将 `prompt` 翻译为英文，翻译返回文本时使用该文本调用图片生成后端。

#### 图片生成返回结果与验证

正常响应通常使用 HTTP **200**，响应体为图片生成结果 JSON。以下保留完整的 URL 型响应示例；时间戳和图片地址为示例值，其中 `created` 使用秒值：

```json
{
  "created": 1700000000,
  "data": [
    {
      "url": "https://example.com/generated-image.png"
    }
  ]
}
```

| 字段 | 类型 | 出现条件与含义 |
| --- | --- | --- |
| `created` | integer | 图片生成结果的创建时间；当前不同后端的单位与填充方式不同，详见下文 |
| `data` | array of objects | 图片结果列表；成功生成时应包含有效的图片结果 |
| `data[].url` | string | 可选的生成图片地址；仅返回 Base64 内容的后端可能不提供该字段 |
| `data[].base64Image` | string | 可选的 Base64 图片内容；当前返回结构使用此字段名，可与 `url` 同时存在 |
| `dataType` | string | 后端提供时的结果类型提示，例如 `base64`；仍应检查每个 `data` 项中的实际图片内容 |

**时间戳说明：**`created` 描述图片生成结果的创建时间。当前 Landing 适配器使用 Unix 秒，StableDiffusion 适配器使用 Unix 毫秒，Spark 适配器未填写该字段时返回默认值 `0`；Baidu 和级联适配器沿用上游返回值。接口当前没有统一换算这些值，解析时应按所选后端处理，并将 `0` 视为未提供有效创建时间。

验证时先检查 HTTP 状态和 `data`。如果存在 `url`，确认地址可访问且返回有效图片；如果存在 `base64Image`，将其解码后确认能作为图片打开。最后核对图片内容是否符合提示词。仅返回 Base64 图片而没有 URL，也可以是有效的生成结果。

若返回 `null`、空结果或错误，检查图片生成后端是否已配置并启用、`model` 是否实际选中了预期后端、后端认证与额度、网络及服务端日志。若已有图片地址但无法读取，还需检查图片存储地址的访问权限和有效期。

<a id="model-api-10"></a>

### 图像增强

`POST /image/image2enhance` 上传图片并返回增强结果地址，兼容路径为 `POST /image/enhanceImage`。实际增强能力和效果取决于部署中配置的图像增强后端。

#### 图像增强请求与上传示例

| 字段 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 请求体 | body | object（multipart 表单） | 是 | 包含上传的图片文件 |
| `file` | form-data | string(binary) | 是 | 待增强图片的二进制内容；提供带文件名和扩展名的图片 |
| `Content-Type` | header | string | 是，由客户端生成 | `multipart/form-data`，包含与请求体一致的 `boundary`；cURL 的 `--form` 会自动生成 |
| `Authorization` | header | string | 按部署鉴权要求 | `Bearer <linkmind-api-key>`；启用鉴权时携带有效的客户端 Key |

准备一张待增强的图片 `sample.png`，将路径替换为实际文件后执行：

```bash
curl --request POST "http://localhost:8080/image/image2enhance" \
  -H "Authorization: Bearer <linkmind-api-key>" \
  --form "file=@sample.png"
```

本示例上传一张图片，由 cURL 自动生成 multipart 请求头和边界。使用兼容路径时，将 URL 改为 `/image/enhanceImage`。

#### 图像增强旧参数兼容说明

以下保留旧文档中的字段和示例值。当前入口不读取这四个字段，也不会将它们传给增强后端；在表单或查询参数中传入它们，均不会改变本接口的增强处理：

| 旧字段 | 示例值 | 当前支持情况 |
| --- | --- | --- |
| `model` | `default` | 不生效；增强后端由部署配置选择 |
| `emotion` | `default` | 不读取、不传递 |
| `text` | `Please enhance this image.` | 不作为增强指令传递 |
| `category` | `default` | 不读取、不传递 |

#### 图像增强返回结果与验证

正常响应通常使用 HTTP **200** 和 JSON 响应体。下面保留完整响应结构，地址按当前实现写为应用内的相对路径；文件名为示例，实际以返回值为准：

```json
{
  "status": "success",
  "enhanceImageUrl": "static/img/enhance/enhanced-image.png"
}
```

| 字段 | 类型 | 出现条件与含义 |
| --- | --- | --- |
| `status` | string | 接口处理状态，`success` 为成功标记，`failed` 为失败标记；仍需验证结果图片是否可读取 |
| `enhanceImageUrl` | string | 成功响应中的增强结果图片地址，当前正常结果使用 `static/img/enhance/...` 相对路径；失败时可能缺失 |

将相对路径追加到部署的应用根地址后访问。例如应用根地址为 `http://localhost:8080/`，上述结果的访问地址为 `http://localhost:8080/static/img/enhance/enhanced-image.png`。若部署带有上下文路径，应将该路径包含在应用根地址中。

验证时先检查业务状态，再访问 `enhanceImageUrl`，确认文件可下载、可作为图片打开，并与输入图片对照增强效果。`status: "success"` 或 HTTP 200 本身不能替代对结果文件的验证。

若返回空响应、`failed` 或 HTTP 错误，检查 multipart 请求、上传文件的扩展名和大小、增强后端配置、认证及日志。若返回了地址但图片无法读取，进一步检查静态资源访问路径、结果目录权限，以及服务端下载或保存增强图片时的错误。

<a id="model-api-11"></a>

### 图生视频

`POST /image/image2video` 上传图片并返回生成视频的地址，兼容路径为 `POST /image/generateVideo`。先在部署中配置可用的图生视频后端，再上传图片验证生成结果。

#### 图生视频请求与上传示例

| 字段 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 请求体 | body | object（multipart 表单） | 是 | 包含上传的图片文件 |
| `file` | form-data | string(binary) | 是 | 用于生成视频的图片二进制内容；提供带文件名和扩展名的图片 |
| `Content-Type` | header | string | 是，由客户端生成 | `multipart/form-data`，包含与请求体一致的 `boundary`；cURL 的 `--form` 会自动生成 |
| `Authorization` | header | string | 按部署鉴权要求 | `Bearer <linkmind-api-key>`；启用鉴权时携带有效的客户端 Key |

准备一张图片 `sample.png`，将路径替换为实际文件后执行：

```bash
curl --request POST "http://localhost:8080/image/image2video" \
  -H "Authorization: Bearer <linkmind-api-key>" \
  --form "file=@sample.png"
```

本示例上传一张图片，由 cURL 自动生成 multipart 请求头和边界。使用兼容路径时，将 URL 改为 `/image/generateVideo`。本接口返回结果 JSON，生成和结果文件下载在服务端请求处理过程中执行；应为该请求预留足够的等待时间。

#### 图生视频旧参数兼容说明

以下保留旧文档中的字段和示例值。当前入口不读取这四个字段，也不会将它们传给图生视频后端；在表单或查询参数中传入它们，均不会改变本接口的视频生成处理：

| 旧字段 | 示例值 | 当前支持情况 |
| --- | --- | --- |
| `model` | `default` | 不用于本次请求选择模型；需要配置可用的图生视频后端 |
| `emotion` | `default` | 不读取、不传递 |
| `text` | `Generate a short video from this image.` | 不作为视频生成提示词传递 |
| `category` | `default` | 不读取、不传递 |

#### 图生视频返回结果与验证

正常响应通常使用 HTTP **200** 和 JSON 响应体。下面保留完整响应结构，地址按当前实现写为应用内的相对路径；文件名为示例，实际以返回值为准：

```json
{
  "status": "success",
  "svdVideoUrl": "static/img/svd/generated-video.mp4"
}
```

| 字段 | 类型 | 出现条件与含义 |
| --- | --- | --- |
| `status` | string | 接口处理状态，`success` 为成功标记，`failed` 为失败标记；仍需验证结果视频是否可读取和播放 |
| `svdVideoUrl` | string | 成功响应中的生成视频地址，当前正常结果使用 `static/img/svd/...` 相对路径；失败时可能缺失 |

将相对路径追加到部署的应用根地址后访问。例如应用根地址为 `http://localhost:8080/`，上述结果的访问地址为 `http://localhost:8080/static/img/svd/generated-video.mp4`。若部署带有上下文路径，应将该路径包含在应用根地址中。

验证时先检查业务状态，再访问 `svdVideoUrl`，确认文件可下载、可作为视频播放，并检查生成内容是否符合预期。`status: "success"` 或 HTTP 200 本身不能替代对结果文件的验证。

若返回空响应、`failed` 或 HTTP 错误，检查 multipart 请求、图片的扩展名和大小、图生视频后端配置、认证及日志。若返回了地址但视频无法读取或播放，进一步检查静态资源访问路径、结果目录权限、服务端下载结果文件时的错误，以及文件是否为有效视频。

<a id="model-api-12"></a>

### 视频追踪

`POST /video/video2tracking` 上传视频并返回追踪结果视频的地址，兼容路径为 `POST /video/motInference`。先在部署中配置可用的视频追踪后端，再用一段内容已知的短视频验证。

#### 视频追踪请求与上传示例

| 字段 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 请求体 | body | object（multipart 表单） | 是 | 包含上传的视频文件 |
| `file` | form-data | string(binary) | 是 | 待追踪视频的二进制内容；提供带文件名和扩展名的视频 |
| `Content-Type` | header | string | 是，由客户端生成 | `multipart/form-data`，包含与请求体一致的 `boundary`；cURL 的 `--form` 会自动生成 |
| `Authorization` | header | string | 按部署鉴权要求 | `Bearer <linkmind-api-key>`；启用鉴权时携带有效的客户端 Key |

旧文档使用以下方式表示本地示例文件：

```yaml
file: file://./examples/video/sample.mp4
```

实际请求应通过 multipart 上传文件内容。将示例路径替换为实际视频文件后执行：

```bash
curl --request POST "http://localhost:8080/video/video2tracking" \
  -H "Authorization: Bearer <linkmind-api-key>" \
  --form "file=@./examples/video/sample.mp4"
```

`--form` 中的 `@` 表示读取本地文件并上传其内容；上述 `file://` 写法只是旧文档的文件示意，不能把它作为普通文本字段替代文件上传。使用兼容路径时，将 URL 改为 `/video/motInference`。本示例上传一个视频，由 cURL 自动生成 multipart 请求头和边界。

#### 视频追踪返回结果与验证

正常响应通常使用 HTTP **200** 和 JSON 响应体。下面保留旧响应的 `status`、`data`，并补充当前实现返回的 `type`；结果地址按当前实现写为应用内的相对路径，文件名为示例：

```json
{
  "status": "success",
  "data": "static/video/tracked-video.mp4",
  "type": "mot"
}
```

| 字段 | 类型 | 出现条件与含义 |
| --- | --- | --- |
| `status` | string | 接口处理状态，`success` 为成功标记，`failed` 为失败标记；仍需验证结果视频是否可读取和播放 |
| `data` | string | 成功响应中的追踪结果视频地址，当前正常结果使用 `static/video/...` 相对路径；失败时可能缺失 |
| `type` | string | 成功响应中的结果类型，当前视频追踪接口返回 `mot`；失败时可能缺失 |

将相对路径追加到部署的应用根地址后访问。例如应用根地址为 `http://localhost:8080/`，上述结果的访问地址为 `http://localhost:8080/static/video/tracked-video.mp4`。若部署带有上下文路径，应将该路径包含在应用根地址中。实际访问时使用返回的 `data` 值。

验证时先检查业务状态，再访问 `data`，确认文件可下载、可作为视频播放，并对照输入视频检查追踪结果。`status: "success"` 或 HTTP 200 本身不能替代对结果文件的验证。

当前实现中，追踪服务未返回结果时可返回 `{"status":"failed"}`，不包含 `data` 和 `type`；上传解析失败或其他异常也可能出现空响应或 HTTP 错误。排查时检查 multipart 请求、文件的扩展名和大小、视频追踪后端配置、认证及日志。若返回了地址但视频无法读取或播放，进一步检查静态资源访问路径、结果目录权限、服务端下载结果文件时的错误，以及文件是否为有效视频。

<a id="model-api-13"></a>

### 视频增强

`POST /video/video2enhance` 上传视频并返回增强结果视频的地址，旧文档中的 `POST /video/mmeditingInference` 是同一功能的兼容入口。先在部署中配置可用的视频增强后端，再用一段短视频验证；具体增强能力和效果取决于所用后端。

#### 视频增强请求与上传示例

| 字段 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 请求体 | body | object（multipart 表单） | 是 | 包含上传的视频文件 |
| `file` | form-data | string(binary) | 是 | 待增强视频的二进制内容；提供带文件名和扩展名的视频 |
| `Content-Type` | header | string | 是，由客户端生成 | `multipart/form-data`，包含与请求体一致的 `boundary`；cURL 的 `--form` 会自动生成 |
| `Authorization` | header | string | 按部署鉴权要求 | `Bearer <linkmind-api-key>`；启用鉴权时携带有效的客户端 Key |

旧文档使用以下方式表示本地示例文件：

```yaml
file: file://./examples/video/sample.mp4
```

实际请求应通过 multipart 上传文件内容。将示例路径替换为实际视频文件后执行：

```bash
curl --request POST "http://localhost:8080/video/video2enhance" \
  -H "Authorization: Bearer <linkmind-api-key>" \
  --form "file=@./examples/video/sample.mp4"
```

`--form` 中的 `@` 表示读取本地文件并上传其内容；上述 `file://` 写法只是旧文档的文件示意，不能把它作为普通文本字段替代文件上传。使用旧入口时，将 URL 改为 `/video/mmeditingInference`，上传参数与响应结构相同。本示例上传一个视频，由 cURL 自动生成 multipart 请求头和边界。

#### 视频增强返回结果与验证

正常响应通常使用 HTTP **200** 和 JSON 响应体。下面保留旧响应的 `status`、`data`，并补充当前实现返回的 `type`；结果地址按当前实现写为应用内的相对路径，文件名为示例：

```json
{
  "status": "success",
  "data": "static/video/enhanced-video.mp4",
  "type": "mmediting"
}
```

| 字段 | 类型 | 出现条件与含义 |
| --- | --- | --- |
| `status` | string | 接口处理状态，`success` 为成功标记，`failed` 为失败标记；仍需验证结果视频是否可读取和播放 |
| `data` | string | 成功响应中的增强结果视频地址，当前正常结果使用 `static/video/...` 相对路径；失败时可能缺失 |
| `type` | string | 成功响应中的结果类型，当前视频增强接口返回 `mmediting`；失败时可能缺失 |

将相对路径追加到部署的应用根地址后访问。例如应用根地址为 `http://localhost:8080/`，上述结果的访问地址为 `http://localhost:8080/static/video/enhanced-video.mp4`。若部署带有上下文路径，应将该路径包含在应用根地址中。实际访问时使用返回的 `data` 值。

验证时先检查业务状态，再访问 `data`，确认文件可下载、可作为视频播放，并对照输入视频检查增强效果。`status: "success"` 或 HTTP 200 本身不能替代对结果文件的验证。

当前实现中，增强服务未返回结果时可返回 `{"status":"failed"}`，不包含 `data` 和 `type`；上传解析失败或其他异常也可能出现空响应或 HTTP 错误。排查时检查 multipart 请求、文件的扩展名和大小、视频增强后端配置、认证及日志。若返回了地址但视频无法读取或播放，进一步检查静态资源访问路径、结果目录权限、服务端下载结果文件时的错误，以及文件是否为有效视频。

## 五、常见问题与下一步

- **聊天能用，Embedding 或 OCR 失败**：功能块没有配置、对应模型不支持该模态，或后端 Key 权限不同。
- **模型显示但请求失败**：显示可用不代表上游可用；按 Provider Key、模型 ID、余额、DNS/代理和日志顺序排查。
- **流式请求无完成标记**：记录首包、最后一包和连接关闭时间；同时检查 LinkMind 到上游的 SSE 日志，不要只看客户端页面。
- **要上传资料做问答**：继续阅读[知识库](knowledge-base_zh.md)，其中包含向量库、上传、检索和 RAG 验证。
- **要扩展新厂商协议或新模态**：阅读[更多功能接入与自定义扩展](extensions_zh.md)。
