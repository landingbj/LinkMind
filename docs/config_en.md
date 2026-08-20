# Configuration Guide

This document reflects the current configuration structure in `lagi-web/src/main/resources/lagi.yml` and the configuration classes that load it. When older screenshots, blogs, or copied snippets conflict with this page, treat the current code and YAML structure as the source of truth.

## Start With The Smallest Working Setup

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

## How Configuration Is Composed

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

## Top-Level Sections

| Section | What it controls |
| --- | --- |
| `system_title` | Product name shown in the UI |
| `models` | Provider and adapter definitions |
| `stores` | Vector store, object storage, term store, relational database, RAG, and Medusa |
| `functions` | Chat and multimodal capability orchestration |
| `agents` | Built-in or custom agents |
| `mcps` | MCP server registry |
| `skills` | Skill roots, workspace, workers, and pnps |
| `routers` | Route expressions such as `best(...)` and `pass(...)` |
| `filters` | Sensitive-word, priority, and session filters |

One easy-to-miss detail: in the current default `lagi.yml`, `workers` and `pnps` are nested under `skills`, not declared as standalone top-level blocks.

## Models

### Single-Driver Example

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

### Multi-Driver Example

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

### Common Fields

| Field | Meaning |
| --- | --- |
| `name` | Backend name referenced later by `functions.*.backend` |
| `type` | Provider label shown in config or UI |
| `enable` | Whether the provider is enabled |
| `model` | Comma-separated model IDs handled by the adapter |
| `driver` | Java adapter class for a single-driver backend |
| `drivers` | Optional multi-driver list when one backend exposes multiple modalities |
| `api_address` | Provider endpoint, often for OpenAI-compatible or self-hosted services |
| `endpoint` | Provider-specific base endpoint |
| `api_key` | Primary API key |
| `api_keys` | Multi-key pool |
| `key_route` | Key-pool strategy: `polling` or `failover` |
| `app_id` / `app_key` | Provider-specific app identity |
| `secret_key` | Provider secret |
| `access_key_id` / `access_key_secret` | Cloud credential pair used by some vendors |
| `deployment` / `api_version` | Deployment-specific fields, especially for Azure-like providers |
| `alias` | Optional model alias mapping |
| `oss` | Object-storage profile used by the adapter |

The default config already includes providers such as Qwen, DeepSeek, Landing, FastChat/Vicuna, OpenAI, Azure OpenAI, ERNIE, ChatGLM, Kimi, Baichuan, Spark, SenseChat, Gemini, Doubao, and Claude.

### API Key Pool

If you only have a single key, keep using `api_key`:

```yaml
api_key: sk-your-single-key
```

When you have multiple keys, use `api_keys` plus `key_route`:

```yaml
api_keys:
  - sk-key1
  - sk-key2
  - sk-key3
key_route: polling
```

You can also use comma-separated syntax:

```yaml
api_keys: sk-key1,sk-key2,sk-key3
key_route: failover
```

When both `api_key` and `api_keys` exist, the key pool takes priority and the single key is merged into the pool if needed.

### Compatibility Adapters Worth Reusing

- `ai.llm.adapter.impl.OpenAIStandardAdapter`
- `ai.llm.adapter.impl.OpenRouterAdapter`
- `ai.llm.adapter.impl.QwenCompatibleAdapter`

## Stores

### Vector Store

Current key: `stores.vector`

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

Common fields:

| Field | Meaning |
| --- | --- |
| `name` | Vector backend name |
| `driver` | Java vector store implementation |
| `type` | Optional vector vendor label |
| `default_category` | Default collection or category name |
| `url` | Remote vector endpoint |
| `metric` | Similarity metric, usually cosine |
| `similarity_top_k` | Number of nearest results |
| `similarity_cutoff` | Minimum similarity threshold |
| `parent_depth` / `child_depth` | Parent-child retrieval expansion depth |
| `api_key` / `token` | Optional auth fields |
| `index_name` / `environment` / `project_name` | Vendor-specific vector fields |
| `concurrency` | Optional request concurrency |

### Object Storage (OSS)

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

Common fields:

| Field | Meaning |
| --- | --- |
| `name` | Storage profile name |
| `driver` | OSS implementation class |
| `endpoint` | Object storage endpoint |
| `access_key_id` / `access_key_secret` | Cloud credentials |
| `bucket_name` | Bucket used by the adapter |
| `enable` | Whether this OSS profile is enabled |

### Full-Text / Term Store

```yaml
stores:
  term:
    - name: elastic
      driver: ai.bigdata.impl.ElasticSearchAdapter
      host: localhost
      port: 9200
      enable: false
```

Common fields:

| Field | Meaning |
| --- | --- |
| `name` | Term-search backend name |
| `driver` | Full-text adapter class |
| `host` | Hostname |
| `port` | Service port |
| `username` / `password` | Optional auth |
| `enable` | Whether the backend is active |

### Relational Database

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

Common fields:

| Field | Meaning |
| --- | --- |
| `name` | Database profile name |
| `jdbc_url` | JDBC connection string |
| `driver` | JDBC driver class |
| `username` / `password` | Login credentials |
| `maximum_pool_size` | Hikari max pool size |
| `idle_timeout` | Connection idle timeout |
| `max_lifetime` | Maximum connection lifetime |

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

Common fields:

| Field | Meaning |
| --- | --- |
| `vector` | Vector backend name to use |
| `term` | Optional term-search backend |
| `graph` | Optional graph-style backend |
| `enable` | Whether RAG is enabled |
| `priority` | Priority when RAG competes with direct model calls |
| `track` | Whether to keep document trace information |
| `html` | Whether HTML-style content handling is enabled |
| `default` | Fallback reply when no context is found |
| `cache_size` | Optional RAG cache size |
| `preload_cache` | Whether to preload cache |
| `preload_cache_category` | Category used during preload |
| `enable_excel_to_md` | Whether spreadsheet content should be normalized into Markdown |

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
    core_pool_size: 5
    maximum_pool_size: 30
    cache_persistent_path: medusa_cache
    cache_persistent_batch_size: 2
    flush: false
    cache_hit_window: 16
    cache_hit_ratio: 0.3
    temperature_tolerance: 0.1
```

Common fields:

| Field | Meaning |
| --- | --- |
| `enable` | Whether Medusa cache acceleration is enabled |
| `cache` / `memory` | Whether cache and in-memory acceleration are enabled |
| `algorithm` | Cache and reasoning strategies |
| `enableL2` / `enableReasonDiver` | Optional advanced cache and reasoning toggles |
| `reason_model` | Optional reasoning model |
| `aheads` | Number of ahead-of-time requests |
| `producer_thread_num` / `consumer_thread_num` | Worker thread counts |
| `core_pool_size` / `maximum_pool_size` | Prompt cache write executor thread pool size |
| `consumeDelay` / `preDelay` | Optional queue timing controls |
| `lcsRatioPromptInput` / `similarityCutoff` / `qa_similarity_cutoff` / `dynamicSimilarity` | Cache-match thresholds |
| `cache_persistent_path` | Persistent cache directory |
| `cache_persistent_batch_size` | Flush batch size |
| `flush` | Rebuild cache state on startup |
| `cache_hit_window` / `cache_hit_ratio` | Sliding window and hit-ratio thresholds |
| `temperature_tolerance` | Temperature tolerance during cache match |
| `inits` | Optional warm-up prompts |

## Functions

### Shared Backend Item Fields

Most function arrays reuse the same backend item shape:

| Field | Meaning |
| --- | --- |
| `backend` | Name of the model backend defined under `models` |
| `model` | Model ID handled by that backend |
| `enable` | Whether this function backend is active |
| `priority` | Selection priority |
| `stream` | Streaming flag where applicable |
| `protocol` | Completion or response protocol for chat |
| `others` | Extra provider-specific value, such as a speaker ID |
| `filter` | Optional filter override |
| `router` | Optional router override |
| `concurrency` | Optional concurrency limit |

Unless noted otherwise, the modules below use arrays of these backend items.

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

Common fields:

| Field | Meaning |
| --- | --- |
| `backend` | Provider name |
| `type` | Provider label |
| `api_key` | API key |
| `model_name` | Embedding model name |
| `api_endpoint` | Remote endpoint |
| `secret_key` | Optional provider secret |
| `model_path` | Local embedding model path |

### Chat

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

Common fields:

| Field | Meaning |
| --- | --- |
| `route` | Router expression used to choose or combine backends |
| `filter` | Comma-separated filter names from `filters.items` |
| `backends` | Candidate chat backends |
| `enable_queue_handle` | Whether queue handling is enabled |
| `handle` | Overflow or fallback strategy |
| `grace_time` | Grace period used by the handler |
| `maxgen` | Maximum number of generated branches or retries |
| `context_length` | Conversation context length used by orchestration |
| `token_charge` | Whether token accounting is enabled |
| `enable_auth` | Whether auth checks are enabled |
| `enable_policy` | Whether built-in policy checks are enabled |

Declare chat backends under `functions.chat.backends`.

### Translate

```yaml
functions:
  translate:
    - backend: ernie
      model: translate
      enable: true
      priority: 10
```

### Speech To Text

```yaml
functions:
  speech2text:
    - backend: qwen
      model: asr
      enable: true
      priority: 10
```

### Text To Speech

```yaml
functions:
  text2speech:
    - backend: landing
      model: tts
      enable: true
      priority: 10
```

### Speech Clone

```yaml
functions:
  speech2clone:
    - backend: doubao
      model: openspeech
      enable: true
      priority: 10
      others: your-speaker-id
```

### Text To Image

```yaml
functions:
  text2image:
    - backend: spark
      model: tti
      enable: true
      priority: 10
```

### Image To Text

```yaml
functions:
  image2text:
    - backend: ernie
      model: Fuyu-8B
      enable: true
      priority: 10
```

### Image OCR

```yaml
functions:
  image2ocr:
    - backend: qwen
      model: ocr
      enable: true
      priority: 10
```

### Image Enhancement

```yaml
functions:
  image2enhance:
    - backend: ernie
      model: enhance
      enable: true
      priority: 10
```

### Text To Video

```yaml
functions:
  text2video:
    - backend: landing
      model: video
      enable: true
      priority: 10
```

### Image To Video

```yaml
functions:
  image2video:
    - backend: qwen
      model: vision
      enable: true
      priority: 10
```

### Video Tracking

```yaml
functions:
  video2track:
    - backend: landing
      model: video
      enable: true
      priority: 10
```

### Video Enhancement

```yaml
functions:
  video2enhance:
    - backend: qwen
      model: vision
      enable: true
      priority: 10
```

### Document OCR

```yaml
functions:
  doc2ocr:
    - backend: qwen
      model: qwen-vl-ocr
      enable: true
      priority: 15
```

### Document Instruction Extraction

```yaml
functions:
  doc2instruct:
    - backend: landing
      model: cascade
      enable: true
      priority: 10
```

### Text To SQL

```yaml
functions:
  text2sql:
    - backend: landing
      model: cascade
      enable: true
      priority: 10
```

### Document Content Extraction

```yaml
functions:
  doc2ext:
    - backend: landing
      model: cascade
      enable: true
      priority: 10
```

### Document Structuring

```yaml
functions:
  doc2struct:
    - backend: landing
      model: cascade
      enable: true
      priority: 10
```

### Text To QA

`text2qa` is currently a single backend object rather than an array:

```yaml
functions:
  text2qa:
    enable: true
    model: cascade
```

## Agents, MCP, Skills, Workers, Pnps, Routers, And Filters

### Agents

```yaml
agents:
  enable: true
  items:
    - name: weather
      token: your-token
      driver: ai.agent.customer.WeatherAgent
```

Common fields:

| Field | Meaning |
| --- | --- |
| `enable` / `items[].enable` | Global agent switch or optional per-agent switch |
| `name` | Agent name |
| `driver` | Agent implementation class |
| `token` / `api_key` | Provider token or API key |
| `app_id` / `user_id` | Optional provider identity |
| `wrong_case` | Fallback reply for unsupported or wrong requests |
| `endpoint` | Optional custom endpoint |
| `mcps` | Optional MCP dependency list |

Large agent lists can be moved to `agent.yml` and loaded with `include_agents`.

### MCP Servers

```yaml
mcps:
  enable: true
  servers:
    - name: amap_mcp
      url: https://mcp.amap.com/sse?key=your-key
      priority: 10
      enable: true
```

Common fields:

| Field | Meaning |
| --- | --- |
| `enable` | Global MCP switch |
| `name` | MCP server name |
| `url` | SSE endpoint |
| `key` | Optional auth key field |
| `headers` | Optional custom request headers |
| `priority` | Selection priority |
| `servers[].enable` | Whether this server entry is active |
| `driver` | MCP client implementation, defaulting to `ai.mcps.CommonSseMcpClient` |

You can also split MCP definitions into `mcp.yml` and load them with `include_mcps`.

### Skills

```yaml
skills:
  enable: true
  roots: ["classpath:skills"]
  workspace: "skills"
  rule: cli
  items:
    - name: extract_content_with_image
      description: Split local files into text and image chunks
```

Common fields:

| Field | Meaning |
| --- | --- |
| `enable` | Global skills switch |
| `roots` | Skill source roots |
| `workspace` | Local working directory for skill execution |
| `rule` | Skill execution preference such as `cli`, `server`, or `block` |
| `items` | Skill catalog entries exposed to the runtime |

### Workers

```yaml
skills:
  workers:
    - name: appointedWorker
      route: pass(%)
      worker: ai.worker.DefaultAppointWorker
```

Common fields:

| Field | Meaning |
| --- | --- |
| `name` | Worker name |
| `worker` | Worker implementation class |
| `route` | Router expression |
| `agent` / `agents` | Target agent or agent set |
| `filter` | Optional filter override |

### Pnps

```yaml
skills:
  pnps:
    - name: qq
      api_key: your-api-key
      driver: ai.pnps.social.QQPnp
```

Common fields:

| Field | Meaning |
| --- | --- |
| `name` | Automation connector name |
| `driver` | Pnp implementation class |
| `api_key` | Connector credential |

You can split additional Pnp definitions into `pnp.yml` and load them with `include_pnps`.

### Routers

```yaml
routers:
  enable: true
  items:
    - name: best
      rule: (|,&)

    - name: pass
      rule: (%)
```

Common fields:

| Field | Meaning |
| --- | --- |
| `enable` | Global router switch |
| `items[].name` | Router name referenced from `functions.chat.route` or workers |
| `items[].rule` | Route grammar supported by that router |

Use:

- `A|B` for polling
- `A,B` for failover
- `A&B` for parallel
- `%` as a wildcard

### Filters

Sensitive-word example:

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

Priority example:

```yaml
filters:
  items:
    - name: priority
      rules: >-
        car,weather,
        social*security
```

Stopping example:

```yaml
filters:
  items:
    - name: stopping
      rules: >-
        bye,
        start*
```

Continuation example:

```yaml
filters:
  items:
    - name: continue
      rules: >-
        about,next,then
```

Common fields:

| Field | Meaning |
| --- | --- |
| `enable` | Global filter switch |
| `items[].name` | Filter name referenced from `functions.chat.filter` |
| `items[].groups[].level` | Action level for sensitive-word groups, such as `mask`, `erase`, or `block` |
| `items[].groups[].rules` | Comma-separated or regex-style rules for grouped sensitive filters |
| `items[].rules` | Keyword list for non-grouped filters such as priority, stopping, or continuation |

`functions.chat.filter` should reference these filter names as a comma-separated list.

## Runtime Sync Integrations

The current codebase also contains configuration sync services for:

- OpenClaw
- Hermes Agent
- DeerFlow
- OpenHuman

These integrations help synchronize model and runtime settings during install or startup, but the core LinkMind runtime still reads from your local YAML configuration first.

OpenHuman sync writes a `linkmind` entry into `[[cloud_providers]]` with `endpoint = "http://127.0.0.1:<port>/v1"` and `auth_style = "bearer"`, activates `provider:linkmind` in OpenHuman `auth-profiles.json`, stores the LinkMind token in OpenHuman's desktop keychain file when `LINKMIND_API_KEY` or `linkmind.apiKey` is available, then points empty/default/OpenHuman LLM workload routes to `linkmind:Alibaba/qwen3.6-plus`. This includes `chat_provider`, `reasoning_provider`, `agentic_provider`, `coding_provider`, `memory_provider`, `heartbeat_provider`, `learning_provider`, and `subconscious_provider`; embedding routes are left unchanged. On import, LinkMind reads supported OpenAI-compatible OpenHuman providers and skips OpenHuman cloud, LinkMind itself, and Anthropic-only routes.

## Using the Box as a CLI Model Gateway

A LinkMind box can be the shared model egress point for a trusted LAN. Its currently exposed compatibility surface is OpenAI Chat Completions:

```text
GET  http://<LINKMIND_BOX_IP>:8080/v1/models
POST http://<LINKMIND_BOX_IP>:8080/v1/chat/completions
```

In this section, `LINKMIND_BASE_URL` means `http://<LINKMIND_BOX_IP>:8080/v1`. Replace `<LINKMIND_BOX_IP>` with the box LAN address, for example `192.168.1.50`; do not keep `BASE_URL` or a third-party host from a reference tutorial.

### Make the box reachable on the LAN

LinkMind binds to `localhost` by default. To allow a separate development machine to reach the box, start it on the box with:

```powershell
cd C:\Users\Hello\LinkMind
java -jar .\LinkMind.jar --host=0.0.0.0 --port=8080
```

Expose port `8080` only to a trusted LAN and restrict it in Windows Firewall to the required subnets. Do not publish this port directly to the Internet.

Verify the connection from a development machine:

```bash
curl http://<LINKMIND_BOX_IP>:8080/v1/models
```

The request `model` must be enabled in the box `lagi.yml` and registered in `functions.chat.backends`. `/v1/models` returns the enabled chat models dynamically.

### Codex

Codex can use either OpenAI Responses or Chat Completions. Responses is recommended because it is the native Codex protocol; Chat Completions remains available for compatibility.

`auth.json`:

```json
{
  "OPENAI_API_KEY": "<LINKMIND_CLIENT_TOKEN>"
}
```

`config.toml`:

```toml
model_provider = "linkmind"
model = "gpt-5.4"
preferred_auth_method = "apikey"

[model_providers.linkmind]
name = "LinkMind Box"
base_url = "http://<LINKMIND_BOX_IP>:8080/v1"
wire_api = "responses"
```

Restart the terminal and run:

```bash
codex
```

This configuration calls `POST /v1/responses`. To retain the legacy integration, set `wire_api = "chat"`, which calls `POST /v1/chat/completions`.

### Claude Code and Gemini CLI

Claude Code can connect directly through the Anthropic Messages compatibility endpoint:

```bash
# Claude Code sends POST <ANTHROPIC_BASE_URL>/v1/messages
export ANTHROPIC_AUTH_TOKEN=<LINKMIND_CLIENT_TOKEN>
export ANTHROPIC_BASE_URL=http://<LINKMIND_BOX_IP>:8080
export ANTHROPIC_MODEL=<ENABLED_LINKMIND_MODEL>

# Gemini CLI sends requests to <GOOGLE_GEMINI_BASE_URL>/v1beta/models/...
export GEMINI_API_KEY=<LINKMIND_CLIENT_TOKEN>
export GOOGLE_GEMINI_BASE_URL=http://<LINKMIND_BOX_IP>:8080
```

Claude Code uses `POST /v1/messages`; LinkMind converts Messages requests and streaming events to the internal chat pipeline. Gemini CLI uses the Gemini `generateContent` protocol, which is not exposed by LinkMind.

| Client | Required endpoint | Current status |
| --- | --- | --- |
| Codex | `POST /v1/responses` or `POST /v1/chat/completions` | Directly supported; use `wire_api = "responses"` |
| Claude Code | `POST /v1/messages` | Directly supported |
| Gemini CLI | `POST /v1beta/models/{model}:generateContent` | Requires a Gemini protocol translation layer |

DeepSeek Harness can select `openai-completions`, `openai-responses`, or `anthropic-messages` and point all three at the same LinkMind host. Select the endpoint that matches its configured protocol.

### Authentication and the Token-Pool Boundary

`--linkmind-api-key` and `LINKMIND_API_KEY` currently synchronize LinkMind credentials to external runtimes. The Messages endpoint accepts `x-api-key` (or Bearer), while Responses and Chat Completions accept Bearer tokens. They use the existing LinkMind request-key validation behaviour; this is not a tenant-aware token-pool policy.

Before issuing distinct tokens to people or development machines, deploy a TLS-enabled reverse proxy or API gateway in front of the box and enforce Bearer-token validation, access control, rate limiting, and audit logging. Only then should `<LINKMIND_CLIENT_TOKEN>` be treated as a real client credential for the CLI tools.
