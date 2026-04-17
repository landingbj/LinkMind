# Configuration Guide

This document reflects the current configuration structure in `lagi-web/src/main/resources/lagi.yml` and the included YAML files it references. If a field here conflicts with older screenshots or examples, treat the codebase configuration as the source of truth.

## Start with the smallest working setup

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

## How configuration is composed

LinkMind uses one main file plus optional include files:

| Key | Purpose |
| --- | --- |
| `lagi.yml` | Main runtime configuration |
| `include_models: model.yml` | Extra model definitions |
| `include_stores: store.yml` | Extra storage definitions |
| `include_agents: agent.yml` | Extra agent definitions |
| `include_mcps: mcp.yml` | Extra MCP servers |
| `include_pnps: pnp.yml` | Extra automation connectors |

If you start LinkMind with `-Dlinkmind.config=/path/to/lagi.yml`, the custom file becomes the main entry point.

## Top-level sections

| Section | What it controls |
| --- | --- |
| `system_title` | Product name shown in the UI |
| `models` | Provider and adapter definitions |
| `stores` | Vector store, RAG, and Medusa cache |
| `functions` | Routing for chat and multimodal capabilities |
| `agents` | Built-in or custom agents |
| `mcps` | MCP server registry |
| `skills` | Skill roots, workspace, published skills |
| `routers` | Route expressions such as `best(...)` and `pass(...)` |
| `filters` | Sensitive-word, priority, and session filters |

One implementation detail worth noting: `workers` and `pnps` are currently configured under `skills` in the default `lagi.yml`.

## Models

Each entry under `models` defines one backend family:

```yaml
- name: deepseek
  type: DeepSeek
  enable: true
  model: deepseek-chat
  driver: ai.llm.adapter.impl.DeepSeekAdapter
  api_address: https://api.deepseek.com/chat/completions
  api_key: your-api-key
```

Common fields:

| Field | Meaning |
| --- | --- |
| `name` | Backend name referenced later by `functions.*.backend` |
| `type` | Provider label shown in config and UI |
| `enable` | Whether the provider is enabled |
| `model` | Comma-separated model IDs handled by the adapter |
| `driver` | Java adapter class |
| `api_key` / `app_id` / `secret_key` / `endpoint` / `api_address` | Provider-specific credentials and endpoints |
| `alias` | Optional model-to-endpoint alias mapping for some providers |

The default config already includes providers such as Qwen, DeepSeek, Landing, FastChat/Vicuna, OpenAI, Azure OpenAI, ERNIE, ChatGLM, Kimi, Baichuan, Spark, SenseChat, Gemini, Doubao, and Claude.

The codebase also contains generic compatibility adapters that are useful for custom providers:

- `ai.llm.adapter.impl.OpenAIStandardAdapter`
- `ai.llm.adapter.impl.OpenRouterAdapter`
- `ai.llm.adapter.impl.QwenCompatibleAdapter`

## Stores

### Vector store

The current key is `stores.vector`, not `stores.vectors`.

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

`ChromaVectorStore` is the default implementation. The repository also contains `SqliteVectorStore`, plus extension examples for Milvus and Pinecone in `lagi-extension`.

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

Use `vector` to point at the configured vector backend. `term` and `graph` are optional extra retrieval channels. `default` is the fallback reply when RAG is enabled but no usable context is found.

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

Medusa is the built-in cache and acceleration layer. Keep `flush: true` only when you intentionally want to rebuild cache state on startup.

## Functions

### Embedding

```yaml
functions:
  embedding:
    - backend: qwen
      type: Qwen
      api_key: your-api-key
```

### Chat

Current chat routing uses `route` plus `backends`:

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

Important differences from older docs:

- Chat backends are now under `functions.chat.backends`.
- Selection logic lives in `functions.chat.route`.
- Filter names in `functions.chat.filter` should match entries in `filters.items`.

### Multimodal and task functions

The default config exposes the following function blocks:

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

Each block is configured as one or more backends with `backend`, `model`, `enable`, and `priority`.

## Agents, MCP, skills, workers, and filters

### Agents

Inline agents live under `agents.items`, and `include_agents: agent.yml` can load a larger catalog.

```yaml
agents:
  enable: true
  items:
    - name: weather
      token: your-token
      driver: ai.agent.customer.WeatherAgent
```

### MCP servers

```yaml
mcps:
  enable: true
  servers:
    - name: amap_mcp
      url: https://mcp.amap.com/sse?key=your-key
```

### Skills and worker automation

```yaml
skills:
  enable: false
  roots: ["classpath:skills"]
  workspace: "skills"
  rule: cli
  items:
    - name: extract_content_with_image
      description: Split local files into text and image chunks
  workers:
    - name: appointedWorker
      route: pass(%)
      worker: ai.worker.DefaultAppointWorker
  pnps:
    - name: qq
      api_key: your-api-key
      driver: ai.pnps.social.QQPnp
```

### Routers

The default router config defines:

- `best`: supports `|`, `,`, and `&` combinations
- `pass`: forwards to the explicitly named agent or worker

### Filters

Filters are configured under `filters.items` and are referenced by name from `functions.chat.filter`. The shipped examples cover:

- `sensitive`
- `priority`
- `stopping`
- `continue`

## Runtime sync integrations

The current codebase also contains configuration sync services for:

- OpenClaw
- Hermes Agent
- DeerFlow

These integrations help synchronize model/runtime settings during install or startup, but the core LinkMind runtime still reads from your local YAML configuration first.
