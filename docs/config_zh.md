# 配置指南

本文档以当前代码中的 `lagi-web/src/main/resources/lagi.yml` 以及实际加载这些配置的 Java 类为准。如果你看到旧截图、旧博客或历史示例与本文不一致，请以当前代码和 YAML 结构为准。

## 先从最小可用配置开始

```yaml
system_title: LinkMind

models:
  - name: qwen
    type: Alibaba
    enable: true
    model: qwen-plus,asr,vision,ocr
    driver: ai.wrapper.impl.AlibabaAdapter
    api_key: your-api-key
    access_key_id: your-access-key-id
    access_key_secret: your-access-key-secret

stores:
  vector:
    - name: chroma
      driver: ai.vector.impl.ChromaVectorStore
      default_category: default
      similarity_top_k: 10
      similarity_cutoff: 0.5
      parent_depth: 1
      child_depth: 1
      url: http://localhost:8000
  rag:
    vector: chroma
    enable: false
    priority: 10
    track: true
    html: true
    default: "Please give prompt more precisely"
  medusa:
    enable: false

functions:
  embedding:
    - backend: qwen
      type: Qwen
      api_key: your-api-key

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

## 配置是怎么拼起来的

LinkMind 采用“主配置 + 按需拆分”的方式：

| 配置项 | 作用 |
| --- | --- |
| `lagi.yml` | 主运行配置 |
| `include_models: model.yml` | 补充模型定义 |
| `include_stores: store.yml` | 补充存储配置 |
| `include_agents: agent.yml` | 补充 Agent 定义 |
| `include_mcps: mcp.yml` | 补充 MCP 服务 |
| `include_pnps: pnp.yml` | 补充自动化连接器 |

如果你通过 `-Dlinkmind.config=/path/to/lagi.yml` 启动 LinkMind，这个自定义文件会成为真正的主入口配置。

## 顶层结构总览

| 区块 | 控制内容 |
| --- | --- |
| `system_title` | 前端展示的系统标题 |
| `models` | 模型提供方与适配器定义 |
| `stores` | 向量库、对象存储、词项检索、关系型数据库、RAG 与 Medusa |
| `functions` | 对话与多模态能力编排 |
| `agents` | 内置或自定义 Agent |
| `mcps` | MCP 服务注册 |
| `skills` | 技能根目录、工作区、workers 与 pnps |
| `routers` | 路由表达式，如 `best(...)`、`pass(...)` |
| `filters` | 敏感词、优先级、会话控制等过滤器 |

有一个很容易忽略的点：当前默认 `lagi.yml` 里，`workers` 和 `pnps` 是挂在 `skills` 下面的，不是单独的顶层块。

## 模型配置

### 单适配器示例

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

### 多适配器示例

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

### 常见字段

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

默认配置和代码已经覆盖 Qwen、DeepSeek、Landing、FastChat/Vicuna、OpenAI、Azure OpenAI、ERNIE、ChatGLM、Kimi、Baichuan、Spark、SenseChat、Gemini、Doubao、Claude 等提供方。

### API Key 池

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

### 值得复用的兼容适配器

- `ai.llm.adapter.impl.OpenAIStandardAdapter`
- `ai.llm.adapter.impl.OpenRouterAdapter`
- `ai.llm.adapter.impl.QwenCompatibleAdapter`

## 存储配置

### 向量库

配置字段使用 `stores.vector`。

```yaml
stores:
  vector:
    - name: chroma
      driver: ai.vector.impl.ChromaVectorStore
      default_category: default
      similarity_top_k: 10
      similarity_cutoff: 0.5
      parent_depth: 1
      child_depth: 1
      url: http://localhost:8000
```

常见字段：

| 字段 | 说明 |
| --- | --- |
| `name` | 向量后端名称 |
| `driver` | Java 向量库实现类 |
| `type` | 可选的向量服务类型标签 |
| `default_category` | 默认集合或分类名 |
| `url` | 远程向量服务地址 |
| `metric` | 相似度度量方式，通常为 cosine |
| `similarity_top_k` | 返回的近邻数量 |
| `similarity_cutoff` | 最低相似度阈值 |
| `parent_depth` / `child_depth` | 父子分块扩展检索深度 |
| `api_key` / `token` | 可选鉴权字段 |
| `index_name` / `environment` / `project_name` | 厂商相关扩展字段 |
| `concurrency` | 可选并发数限制 |

### 对象存储（OSS）

```yaml
stores:
  oss:
    - name: landing
      driver: ai.oss.impl.LandingOSS
      bucket_name: lagi
      enable: true

    - name: alibaba
      driver: ai.oss.impl.AlibabaOSS
      endpoint: oss-cn-hangzhou.aliyuncs.com
      access_key_id: your-access-key-id
      access_key_secret: your-access-key-secret
      bucket_name: your-bucket
      enable: true
```

常见字段：

| 字段 | 说明 |
| --- | --- |
| `name` | 存储配置名 |
| `driver` | OSS 实现类 |
| `endpoint` | 对象存储服务地址 |
| `access_key_id` / `access_key_secret` | 云存储密钥对 |
| `bucket_name` | 当前使用的桶名 |
| `enable` | 是否启用该 OSS 配置 |

### 全文 / 词项检索

```yaml
stores:
  term:
    - name: elastic
      driver: ai.bigdata.impl.ElasticSearchAdapter
      host: localhost
      port: 9200
      enable: false
```

常见字段：

| 字段 | 说明 |
| --- | --- |
| `name` | 词项检索后端名 |
| `driver` | 全文检索适配器类 |
| `host` | 主机地址 |
| `port` | 服务端口 |
| `username` / `password` | 可选认证信息 |
| `enable` | 是否启用该后端 |

### 关系型数据库

```yaml
stores:
  database:
    - name: mysql
      jdbc_url: jdbc:mysql://127.0.0.1:3306/demo
      driver: com.mysql.cj.jdbc.Driver
      username: root
      password: your-password
      maximum_pool_size: 20
      idle_timeout: 0
      max_lifetime: 2877700
```

常见字段：

| 字段 | 说明 |
| --- | --- |
| `name` | 数据库配置名 |
| `jdbc_url` | JDBC 连接串 |
| `driver` | JDBC 驱动类 |
| `username` / `password` | 登录账号密码 |
| `maximum_pool_size` | Hikari 连接池最大连接数 |
| `idle_timeout` | 空闲连接超时时间 |
| `max_lifetime` | 连接最大生命周期 |

### RAG

```yaml
stores:
  rag:
    vector: chroma
    term: elastic
    graph: landing
    enable: true
    priority: 10
    track: true
    html: true
    default: "Please give prompt more precisely"
    cache_size: 1000
    preload_cache: false
    preload_cache_category: default
    enable_excel_to_md: true
```

常见字段：

| 字段 | 说明 |
| --- | --- |
| `vector` | 使用哪个向量后端 |
| `term` | 可选的词项检索后端 |
| `graph` | 可选的图式检索后端 |
| `enable` | 是否启用 RAG |
| `priority` | 当 RAG 与直接模型调用竞争时的优先级 |
| `track` | 是否保留文档追踪信息 |
| `html` | 是否启用 HTML 内容处理 |
| `default` | 没检索到上下文时的兜底回复 |
| `cache_size` | 可选的 RAG 缓存大小 |
| `preload_cache` | 是否预加载缓存 |
| `preload_cache_category` | 预加载时使用的分类 |
| `enable_excel_to_md` | 是否把表格内容归一化为 Markdown |

### Medusa

```yaml
stores:
  medusa:
    enable: true
    algorithm: hash,graph,llm
    reason_model: deepseek-r1
    aheads: 1
    producer_thread_num: 1
    consumer_thread_num: 2
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
| `consumeDelay` / `preDelay` | 队列时序控制参数 |
| `lcsRatioPromptInput` / `similarityCutoff` / `qa_similarity_cutoff` / `dynamicSimilarity` | 缓存命中阈值相关参数 |
| `cache_persistent_path` | 持久化缓存目录 |
| `cache_persistent_batch_size` | 批量刷盘大小 |
| `flush` | 启动时是否重建缓存 |
| `cache_hit_window` / `cache_hit_ratio` | 滑动窗口与命中率阈值 |
| `temperature_tolerance` | 缓存匹配时的温度容忍度 |
| `inits` | 可选的预热提示词 |

## 功能配置

### 公共后端字段

绝大多数功能块都会复用同一类后端项结构：

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

除非特别说明，下方这些功能块都使用这一类后端数组结构。

### Embedding

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

### 对话编排

```yaml
functions:
  chat:
    route: best((landing&qwen),(kimi|chatgpt))
    filter: sensitive,priority,stopping,continue
    handle: failover
    grace_time: 20
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

### 翻译

```yaml
functions:
  translate:
    - backend: ernie
      model: translate
      enable: true
      priority: 10
```

### 语音转文本

```yaml
functions:
  speech2text:
    - backend: qwen
      model: asr
      enable: true
      priority: 10
```

### 文本转语音

```yaml
functions:
  text2speech:
    - backend: landing
      model: tts
      enable: true
      priority: 10
```

### 语音克隆

```yaml
functions:
  speech2clone:
    - backend: doubao
      model: openspeech
      enable: true
      priority: 10
      others: your-speaker-id
```

### 文本生成图片

```yaml
functions:
  text2image:
    - backend: spark
      model: tti
      enable: true
      priority: 10
```

### 图片转文本

```yaml
functions:
  image2text:
    - backend: ernie
      model: Fuyu-8B
      enable: true
      priority: 10
```

### 图片 OCR

```yaml
functions:
  image2ocr:
    - backend: qwen
      model: ocr
      enable: true
      priority: 10
```

### 图片增强

```yaml
functions:
  image2enhance:
    - backend: ernie
      model: enhance
      enable: true
      priority: 10
```

### 文本生成视频

```yaml
functions:
  text2video:
    - backend: landing
      model: video
      enable: true
      priority: 10
```

### 图片生成视频

```yaml
functions:
  image2video:
    - backend: qwen
      model: vision
      enable: true
      priority: 10
```

### 视频跟踪

```yaml
functions:
  video2track:
    - backend: landing
      model: video
      enable: true
      priority: 10
```

### 视频增强

```yaml
functions:
  video2enhance:
    - backend: qwen
      model: vision
      enable: true
      priority: 10
```

### 文档 OCR

```yaml
functions:
  doc2ocr:
    - backend: qwen
      model: qwen-vl-ocr
      enable: true
      priority: 15
```

### 文档指令提取

```yaml
functions:
  doc2instruct:
    - backend: landing
      model: cascade
      enable: true
      priority: 10
```

### Text-to-SQL

```yaml
functions:
  text2sql:
    - backend: landing
      model: cascade
      enable: true
      priority: 10
```

### 文档内容抽取

```yaml
functions:
  doc2ext:
    - backend: landing
      model: cascade
      enable: true
      priority: 10
```

### 文档结构化

```yaml
functions:
  doc2struct:
    - backend: landing
      model: cascade
      enable: true
      priority: 10
```

### 文本生成问答

`text2qa` 当前不是数组，而是单个后端对象：

```yaml
functions:
  text2qa:
    enable: true
    model: cascade
```

## Agents、MCP、Skills、Workers、Pnps、Routers 与 Filters

### Agents

```yaml
agents:
  enable: true
  items:
    - name: weather
      token: your-token
      driver: ai.agent.customer.WeatherAgent
```

常见字段：

| 字段 | 说明 |
| --- | --- |
| `enable` / `items[].enable` | 全局 Agent 开关，或单个 Agent 的可选启用开关 |
| `name` | Agent 名称 |
| `driver` | Agent 实现类 |
| `token` / `api_key` | 提供方 token 或 API Key |
| `app_id` / `user_id` | 可选的提供方身份字段 |
| `wrong_case` | 请求不匹配时的兜底回复 |
| `endpoint` | 可选的自定义地址 |
| `mcps` | 可选的 MCP 依赖列表 |

如果 Agent 很多，可以拆到 `agent.yml`，再通过 `include_agents` 引入。

### MCP 服务

```yaml
mcps:
  enable: true
  servers:
    - name: amap_mcp
      url: https://mcp.amap.com/sse?key=your-key
      priority: 10
      enable: true
```

常见字段：

| 字段 | 说明 |
| --- | --- |
| `enable` | 全局 MCP 开关 |
| `name` | MCP 服务名 |
| `url` | SSE 接口地址 |
| `key` | 可选鉴权 Key 字段 |
| `headers` | 自定义请求头 |
| `priority` | 优先级 |
| `servers[].enable` | 当前服务项是否启用 |
| `driver` | MCP 客户端实现，默认是 `ai.mcps.CommonSseMcpClient` |

如果需要，也可以拆到 `mcp.yml`，再通过 `include_mcps` 引入。

### Skills

```yaml
skills:
  enable: true
  roots: ["classpath:skills"]
  workspace: "skills"
  rule: cli
  items:
    - name: extract_content_with_image
      description: 将本地文件拆分为文本与图片块
```

常见字段：

| 字段 | 说明 |
| --- | --- |
| `enable` | 全局技能开关 |
| `roots` | 技能来源根目录 |
| `workspace` | 技能运行使用的本地工作区 |
| `rule` | 技能执行优先策略，如 `cli`、`server`、`block` |
| `items` | 暴露给运行时的技能清单 |

### Workers

```yaml
skills:
  workers:
    - name: appointedWorker
      route: pass(%)
      worker: ai.worker.DefaultAppointWorker
```

常见字段：

| 字段 | 说明 |
| --- | --- |
| `name` | Worker 名称 |
| `worker` | Worker 实现类 |
| `route` | 路由表达式 |
| `agent` / `agents` | 目标 Agent 或 Agent 集合 |
| `filter` | 可选过滤器覆盖 |

### Pnps

```yaml
skills:
  pnps:
    - name: qq
      api_key: your-api-key
      driver: ai.pnps.social.QQPnp
```

常见字段：

| 字段 | 说明 |
| --- | --- |
| `name` | 自动化连接器名称 |
| `driver` | Pnp 实现类 |
| `api_key` | 连接器凭证 |

更多 Pnp 可以拆到 `pnp.yml`，再通过 `include_pnps` 引入。

### 路由器

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

### 过滤器

敏感词过滤示例：

```yaml
filters:
  enable: true
  items:
    - name: sensitive
      groups:
        - level: mask
          rules: >-
            openai,FLG
        - level: erase
          rules: >-
            your context
        - level: block
          rules: >-
            shit,CNM
```

优先级过滤示例：

```yaml
filters:
  items:
    - name: priority
      rules: >-
        car,weather,
        social*security
```

停止词过滤示例：

```yaml
filters:
  items:
    - name: stopping
      rules: >-
        bye,
        start*
```

续聊过滤示例：

```yaml
filters:
  items:
    - name: continue
      rules: >-
        about,next,then
```

常见字段：

| 字段 | 说明 |
| --- | --- |
| `enable` | 全局过滤器开关 |
| `items[].name` | 在 `functions.chat.filter` 中引用的过滤器名 |
| `items[].groups[].level` | 敏感词分组动作级别，如 `mask`、`erase`、`block` |
| `items[].groups[].rules` | 分组敏感词规则，可写逗号分隔关键词或正则风格规则 |
| `items[].rules` | 非分组过滤器的关键词列表，如优先级、停止词、续聊词 |

`functions.chat.filter` 里填写的应该就是这些过滤器名称，多个值用逗号分隔。

## 运行时同步集成

当前代码里还内置了以下配置同步服务：

- OpenClaw
- Hermes Agent
- DeerFlow

这些同步逻辑主要用于安装或启动阶段对齐外部运行时配置，但 LinkMind 真正运行时仍然优先读取本地 YAML 配置。
