# 配置指南

本文档以当前代码中的 `lagi-web/src/main/resources/lagi.yml` 及其引用的 YAML 文件为准。如果你看到其他旧文档、截图或历史博客里的字段和这里不一致，请以代码里的实际配置结构为准。

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
    route: best((landing&qwen),(kimi|chatgpt))
    filter: sensitive,priority,stopping,continue
    backends:
      - backend: qwen
        model: qwen-plus
        enable: true
        stream: true
        protocol: completion
        priority: 200
```

## 配置是怎么拼起来的

LinkMind 现在采用“主配置 + 按需拆分”的方式：

| 配置项 | 作用 |
| --- | --- |
| `lagi.yml` | 主配置入口 |
| `include_models: model.yml` | 补充模型定义 |
| `include_stores: store.yml` | 补充存储配置 |
| `include_agents: agent.yml` | 补充 Agent 定义 |
| `include_mcps: mcp.yml` | 补充 MCP 服务 |
| `include_pnps: pnp.yml` | 补充自动化连接器 |

如果你通过 `-Dlinkmind.config=/path/to/lagi.yml` 启动，那么这个自定义文件会成为实际生效的主配置。

## 顶层结构总览

| 区块 | 负责内容 |
| --- | --- |
| `system_title` | 前端显示的系统标题 |
| `models` | 模型提供方与适配器定义 |
| `stores` | 向量库、RAG、Medusa 缓存 |
| `functions` | 对话与多模态能力编排 |
| `agents` | Agent 配置 |
| `mcps` | MCP 服务注册 |
| `skills` | 技能目录、工作区、技能清单 |
| `routers` | 路由表达式，如 `best(...)`、`pass(...)` |
| `filters` | 敏感词、优先级、会话控制等过滤规则 |

有一个容易踩坑的点：当前默认 `lagi.yml` 里，`workers` 和 `pnps` 是挂在 `skills` 下面配置的，而不是单独顶层。

## 模型配置

`models` 中的每一项表示一个后端模型族：

```yaml
- name: deepseek
  type: DeepSeek
  enable: true
  model: deepseek-chat
  driver: ai.llm.adapter.impl.DeepSeekAdapter
  api_address: https://api.deepseek.com/chat/completions
  api_key: your-api-key
```

常见字段含义：

| 字段 | 说明 |
| --- | --- |
| `name` | 后续在 `functions.*.backend` 中引用的后端名 |
| `type` | 提供方标签 |
| `enable` | 是否启用 |
| `model` | 当前适配器负责的模型 ID 列表 |
| `driver` | Java 适配器类 |
| `api_key` / `app_id` / `secret_key` / `endpoint` / `api_address` | 提供方相关凭证与地址 |
| `api_keys` | 多 Key 池，支持 YAML 数组或逗号分隔（见下方说明） |
| `key_route` | Key 池调度策略：`polling`（轮询）或 `failover`（故障转移） |
| `alias` | 某些提供方可用的模型别名映射 |

默认配置和代码里已经覆盖了 Qwen、DeepSeek、Landing、FastChat/Vicuna、OpenAI、Azure OpenAI、ERNIE、ChatGLM、Kimi、Baichuan、Spark、SenseChat、Gemini、Doubao、Claude 等提供方。

### API Key 池（多 Key 轮询 / 故障转移）

如果你只有一个 Key，继续使用 `api_key` 即可：

```yaml
api_key: sk-your-single-key
```

当你拥有多个 Key 时，可以使用 `api_keys` + `key_route` 开启 Key 池：

**写法 1：YAML 数组**

```yaml
api_keys:
  - sk-key1
  - sk-key2
  - sk-key3
key_route: polling
```

**写法 2：逗号分隔**

```yaml
api_keys: sk-key1,sk-key2,sk-key3
key_route: polling
```

两种写法效果完全一致。`key_route` 支持两种策略：

| 策略 | 行为 |
| --- | --- |
| `polling` | 每次请求轮询使用下一个 Key（Round-Robin） |
| `failover` | 优先使用第一个 Key；如果请求失败则自动切换到下一个 |

> **注意**：`api_keys` 和 `api_key` 同时存在时，Key 池优先生效。

另外，代码里还有几类适合接入兼容服务的通用适配器：

- `ai.llm.adapter.impl.OpenAIStandardAdapter`
- `ai.llm.adapter.impl.OpenRouterAdapter`
- `ai.llm.adapter.impl.QwenCompatibleAdapter`

## 存储配置

### 向量库

当前实际字段是 `stores.vector`，不是旧文档里的 `stores.vectors`。

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

默认实现是 `ChromaVectorStore`。仓库里还包含 `SqliteVectorStore`，并在 `lagi-extension` 里提供了 Milvus、Pinecone 的扩展示例。

### RAG

```yaml
stores:
  rag:
    vector: chroma
    term: elastic
    graph: landing
    enable: false
    priority: 10
    track: true
    html: true
    default: "Please give prompt more precisely"
```

`vector` 指向已定义的向量库。`term` 和 `graph` 是可选的补充检索通道。`default` 用于在开启 RAG 但没有检索到可用上下文时返回兜底提示。

### Medusa

```yaml
stores:
  medusa:
    enable: false
    algorithm: hash,graph,llm
    aheads: 1
    producer_thread_num: 1
    consumer_thread_num: 2
    flush: false
    cache_hit_window: 16
    cache_hit_ratio: 0.3
    temperature_tolerance: 0.1
```

Medusa 是内置的缓存与加速层。只有在你明确希望启动时重建缓存时，才把 `flush` 设为 `true`。

## 功能配置

### Embedding

```yaml
functions:
  embedding:
    - backend: qwen
      type: Qwen
      api_key: your-api-key
```

### 对话编排

当前对话能力使用 `route + backends` 结构：

```yaml
functions:
  chat:
    route: best((landing&qwen),(kimi|chatgpt))
    filter: sensitive,priority,stopping,continue
    backends:
      - backend: landing
        model: cascade
        enable: true
        stream: true
        protocol: completion
        priority: 350
```

和旧文档相比，最关键的变化是：

- 对话模型列表现在在 `functions.chat.backends`
- 路由规则现在在 `functions.chat.route`
- `functions.chat.filter` 里填的是 `filters.items` 中定义的过滤器名称

### 多模态与任务能力

当前默认配置里已经包含这些功能块：

- `translate`
- `speech2text`
- `text2speech`
- `speech2clone`
- `text2image`
- `image2text`
- `image2enhance`
- `text2video`
- `image2video`
- `video2track`
- `video2enhance`
- `doc2ocr`
- `doc2instruct`
- `text2sql`
- `doc2ext`
- `doc2struct`
- `text2qa`

这些能力块的配置方式基本一致，都是按后端定义 `backend`、`model`、`enable`、`priority`。

## Agents、MCP、Skills、Workers 与 Filters

### Agents

内联 Agent 写在 `agents.items` 中，大量 Agent 可以放到 `agent.yml`，再通过 `include_agents: agent.yml` 引入。

```yaml
agents:
  enable: true
  items:
    - name: weather
      token: your-token
      driver: ai.agent.customer.WeatherAgent
```

### MCP 服务

```yaml
mcps:
  enable: true
  servers:
    - name: amap_mcp
      url: https://mcp.amap.com/sse?key=your-key
```

### Skills 与自动化

```yaml
skills:
  enable: false
  roots: ["classpath:skills"]
  workspace: "skills"
  rule: cli
  items:
    - name: extract_content_with_image
      description: 拆分本地文件为文本与图片块
  workers:
    - name: appointedWorker
      route: pass(%)
      worker: ai.worker.DefaultAppointWorker
  pnps:
    - name: qq
      api_key: your-api-key
      driver: ai.pnps.social.QQPnp
```

### 路由器

默认路由器包括：

- `best`：支持 `|`、`,`、`&` 组合表达式
- `pass`：把请求交给指定的 Agent 或 Worker

### 过滤器

过滤器定义在 `filters.items`，并通过 `functions.chat.filter` 按名称引用。默认示例包含：

- `sensitive`
- `priority`
- `stopping`
- `continue`

## 运行时同步能力

当前代码里还内置了以下配置同步服务：

- OpenClaw
- Hermes Agent
- DeerFlow

这些同步逻辑主要用于安装或启动阶段与外部运行时对齐配置，但 LinkMind 自身运行时仍然以本地 YAML 配置为主。
