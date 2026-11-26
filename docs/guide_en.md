# Getting Started

Use this page to install LinkMind on a new machine, start it, verify the console, and make the first API call. Then move to the model, knowledge-base, or Agent page for the target capability; each feature page contains its own configuration, call, and verification steps.

[First configuration](#first-configuration) · [Included files](#config-layout) · [Native chat](#native-chat) · [Nine business scenarios](#business-scenarios)

## 1. Install and start

LinkMind requires JDK 8 or later. Docker is required only when using Docker. These methods are alternatives; do not run all of them:

```powershell
iwr -useb https://cdn.linkmind.top/install.ps1 | iex
```

```bash
curl -fsSL https://cdn.linkmind.top/install.sh | bash
java -jar LinkMind.jar
```

The installer and a first JAR start prepare default `config/`, `config/lagi.yml`, and `data/`. For Docker, WAR, private directories, reverse proxy, or production runtime options, use [Deployment, Security, and Cascade Networking](deployment-and-security_en.md).

<a id="first-configuration"></a>

## 2. Complete a minimal model configuration

For the standalone JAR, edit `lagi.yml` in the configuration directory. Pass `--config=/path/to/config` to select a directory. When embedding the Java library, use `-Dlinkmind.config=/path/to/lagi.yml` to select a file.

The example enables one chat backend and shows where to configure embeddings, RAG and Medusa later. Replace the placeholders with credentials authorized for the model. Supply `access_key_id` and `access_key_secret` for features that require cloud credentials. For a chat-only first run you can omit embedding and the cloud access credentials.

```yaml
system_title: LinkMind

models:
  - name: qwen
    type: Alibaba
    enable: true
    model: qwen-plus,qwen-max,asr,vision,ocr
    driver: ai.wrapper.impl.AlibabaAdapter
    api_key: your-api-key
    # For multiple credentials, replace the line above with the pool below.
    # api_keys: key-one,key-two
    # key_route: polling  # polling / failover
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

routers:
  enable: true
  items:
    - name: pass
      rule: (%)
```

Restart the service and open `http://localhost:8080`. When account service is enabled, sign in first. In **Settings -> API Keys**, create or confirm a LinkMind API key; then select the enabled model in **Chat -> LLM -> Intelligent Q&A** and send a short message.

<a id="config-layout"></a>

### Main configuration, included files, and top-level keys

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

## 3. Make the first API call

Copy a LinkMind API key from the console and run:

```bash
curl http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer <linkmind-api-key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"qwen-plus","messages":[{"role":"user","content":"Hello, LinkMind."}],"stream":false,"max_completion_tokens":128}'
```

Success requires `choices[0].message.content` in the response and a model call at the same time in **Control**. A reachable console, HTTP 200, or a model shown in a list does not independently prove the upstream model is usable.

<a id="native-chat"></a>

### Call the native LinkMind chat route

For plain chat, send the same message to `POST /chat/completions`. Read the answer from `choices[0].message.content`.

```bash
curl http://localhost:8080/chat/completions \
  -H "Authorization: Bearer <linkmind-api-key>" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "qwen-plus",
    "stream": false,
    "messages": [
      {"role": "user", "content": "List three core LinkMind capabilities."}
    ]
  }'
```

## 4. Continue by capability

| Goal | Page |
| --- | --- |
| Models, routing, streaming, and multimodal | [Models, Routing, and Multimodal](models-and-multimodal_en.md) |
| Upload documents, configure SQLite/Chroma, and verify RAG | [Knowledge Base](knowledge-base_en.md) |
| Agents, MCP, Skills, or Workers | [Agents, Tools, and Task Orchestration](agents-and-tools_en.md) |
| REST or `lagi-core` business integration | [System Integration and Secondary Development](integration_en.md) |
| Deployment, hardening, cascade, or incident checks | [Deployment, Security, and Cascade Networking](deployment-and-security_en.md) |
| New provider, adapter, or store | [More Integrations and Custom Extensions](extensions_en.md) |

<a id="business-scenarios"></a>

## 5. Select, deploy, and verify a business scenario

<a id="scenario-selection"></a>

### Choose a Deployment Shape First

| Current business situation | Recommended shape | Main goal |
| --- | --- | --- |
| Multiple agents or business lines need shared AI capabilities | [LinkMind team AI hub](#scenario-team-hub) | Unified Skills, Tools, model routing, safety, and observability |
| The team has private documents, databases, or policy knowledge | [Knowledge Base Server](#scenario-enterprise-knowledge) | Use RAG so answers are grounded in enterprise knowledge |
| The team already uses OpenClaw, Hermes Agent, DeerFlow, or another agent workspace | [LinkMind Agent frontend integration](#scenario-agent-frontend) | Transparent enhancement, token reduction, and safety boundaries |
| Local appliance or edge GPU capacity is limited and repeated questions are common | [Medusa Accelerator](#scenario-medusa) | Reuse cache, reduce repeated inference, and lower GPU load |
| Finance, healthcare, government, or other high-compliance environments | [Security Gateway](#scenario-security-gateway) | Unified filters, permissions, audit, and AI guardrails |
| Many departments and systems share AI capability | [AI Aggregation Platform](#scenario-aggregation-platform) | Unified access, orchestration, governance, observability, and cost accounting |
| An existing RAG platform only needs a shared embedding service | [Embedding Server](#scenario-embedding-server) | Unified Embedding API with replaceable models |
| Multi-region, multi-team, or group-level organizations need a shared proxy exit | [Proxy + GEO + Cascade](#scenario-regional-cascade) | Distributed topology, layered governance, and regional access |
| The team needs a capacity or stability baseline before launch, scaling, or version comparison | [Benchmark Load Test](#scenario-benchmark) | Fixed concurrency, run count, model, and dataset with repeatable result logs |

These capabilities can be combined. A common path is to start with single-node LinkMind or an existing Agent frontend integration for POC, use Benchmark load testing to establish a capacity baseline, then grow into a LinkMind team AI hub + Security Gateway + Knowledge Base Server as a full enterprise AI platform.

The scenarios below include work for both LinkMind and the deployment operator. Implement per-person or tenant credentials, quotas, and audit policies in the business platform or API gateway. Correlate gateway, LinkMind, and billing records for the same request.

<a id="scenario-deployment-steps"></a>

### Standard Business Integration Steps

1. Define the entry point: Web/App/API, internal OA/CRM/ERP, support system, developer tool, or existing agent workspace.
2. Define the data boundary: which requests may leave the network, which knowledge must stay private, and which fields require masking, filtering, or audit.
3. Choose deployment: use the installer, packaged JAR, or Docker for pilot; expand to single-node, cluster, gateway, or GEO/Cascade topology for production.
4. Configure models and routes: enable providers, routing rules, failover, filters, RAG, and required Skills in `lagi.yml`.
5. Connect business calls: select native LinkMind routes, OpenAI Chat Completions at `/v1/chat/completions`, OpenAI Responses at `/v1/responses`, or Anthropic Messages at `/v1/messages` to match the client protocol instead of calling model-provider APIs directly.
6. Add governance: enable API keys, user identity, filters, blacklists/whitelists, token statistics, and request tracing.
7. Pilot narrowly: validate answer quality, retrieval hit rate, token consumption, latency, error rate, and safety behavior on one business line.
8. Load test: use the Benchmark scripts under `tools/benchmark` with fixed concurrency, run count, model, and test data; keep logs as a repeatable baseline.
9. Scale out: reuse proven routes, policies, knowledge bases, and Skills across more business lines or LinkMind nodes.

A lightweight pilot can start from 8 CPU cores, 32 GB memory, and 200 GB storage. Prepare GPU servers when local model inference is a core requirement. If the pilot mainly calls cloud model APIs, local GPU is usually not required.

<a id="scenario-team-hub"></a>

### Scenario 1: Team AI Hub

LinkMind is useful when multiple agents, business lines, or teams need one shared AI capability base. Business agents no longer manage model keys, tool registration, safety rules, and observability on their own; they call LinkMind.

Business usage:

- Multiple agents share the same Skills, Tools, model routes, and safety policies.
- Business teams focus on task orchestration while LinkMind owns common capabilities.
- Platform teams observe token usage, request traces, and error rates by business line.

Setup:

1. Start LinkMind with the [Installation Guide](deployment-and-security_en.md); a single node is enough for an initial pilot.
2. Enable at least one chat model, routing rules, and required Skills in the [Configuration Reference](models-and-multimodal_en.md).
3. Assign the LinkMind endpoint and API key to each agent.
4. Let agents call `/chat/completions` or `/v1/chat/completions`.
5. For production, add a gateway, load balancing, and multiple nodes to avoid single-node bottlenecks.

<a id="scenario-enterprise-knowledge"></a>

### Scenario 2: Knowledge Base Server

Knowledge Base Server is for enterprises that already have policy documents, product materials, FAQs, tickets, databases, or industry knowledge and need model answers grounded in internal knowledge.

Business usage:

- Support, sales, operations, engineering support, or internal assistants send user questions to LinkMind.
- LinkMind retrieves relevant context from vector stores, databases, or document indexes.
- Retrieved context is injected into the model path so answers are grounded in enterprise knowledge.
- New materials can be incrementally added so the knowledge base evolves with the business.

Setup:

1. Prepare knowledge sources such as PDF, Word, HTML, FAQ, MySQL, Elasticsearch, Chroma, Milvus, or another vector store.
2. Configure vector storage, embedding models, and retrieval flow with the [knowledge-base settings](knowledge-base_en.md#retrieval-settings) and [Configuration Reference](models-and-multimodal_en.md).
3. Use document processing, OCR, and embedding capabilities to chunk, vectorize, and index content.
4. Let business systems call the chat API while LinkMind handles retrieval augmentation.
5. Add review, update, and rollback workflows for high-value knowledge bases before production use.

<a id="scenario-agent-frontend"></a>

### Scenario 3: Existing Agent Frontend Integration

This scenario is for teams that already use OpenClaw, Hermes Agent, DeerFlow, or another agent workspace. LinkMind does not replace the current system; it joins as a compatible provider or sidecar middleware. Clients that support OpenAI Chat Completions / Responses can use the corresponding endpoints, while clients that support Anthropic Messages can use `/v1/messages` to access the shared model-call capabilities.

Business usage:

- Existing agents keep their original workflows while model calls pass through LinkMind.
- LinkMind handles token reduction, context trimming, structured reuse, and on-demand recall.
- Input/output filters, blacklisted Skills, permission policies, and model routing apply in the middleware layer.
- Teams can connect one agent path first, validate the effect, then expand gradually.

Setup:

1. Choose optional Agent frontend integration during installation, then follow the [Agent Runtime Integration Cookbook](agents-and-tools_en.md#client-recipes) for OpenClaw, Hermes Agent, DeerFlow, or OpenHuman setup and verification.
2. Select the LinkMind Chat Completions, Responses, or Messages endpoint supported by the existing agent; the client protocol setting must match the endpoint.
3. Configure the base URL, model name, and authentication as required by the client. OpenAI clients typically use a base URL ending in `/v1`; Claude Code uses the service root for `ANTHROPIC_BASE_URL` and appends `/v1/messages`. See the [client setup recipes](agents-and-tools_en.md#client-recipes).
4. Enable filters, token statistics, and routing failover.
5. Compare token consumption, latency, failure rate, and safety interceptions before and after the change.

<a id="scenario-medusa"></a>

### Scenario 4: Medusa Accelerator

Medusa is for local appliances, edge nodes, local LLM deployments, and highly repetitive Q&A traffic. It uses hit detection, read-ahead cache, and result write-back to reduce repeated inference and make limited GPU resources serve more traffic.

Business usage:

- High-frequency FAQs, support questions, policy inquiries, and internal assistants try cache first.
- Missed requests call the local model, RAG path, or upstream model.
- Results are written back so similar future requests respond faster.

Setup:

1. Deploy LinkMind on the local or edge node and connect local or cloud models.
2. Enable Medusa cache capability and the required RAG path.
3. Put high-frequency, reusable, low-risk questions into the cache strategy first.
4. Watch cache hit rate, GPU load, average response time, and answer consistency.

<a id="scenario-security-gateway"></a>

### Scenario 5: Security Gateway

Security Gateway is for external AI products, high-compliance industries, and deployments with many plugins or Skills. It moves permissions, filters, audit, and callable capability boundaries into LinkMind.

Business usage:

- External requests pass through LinkMind before they reach models, tools, or private data.
- Filters apply bidirectional input/output checks to reduce prompt injection, sensitive data leakage, and unsafe responses.
- Blacklists/whitelists and vulnerable Skill checks isolate risky components.
- Audit and token statistics support compliance review, cost accounting, and incident tracing.

Setup:

1. Route business systems, web entry points, or API Gateway traffic to LinkMind.
2. Configure `filters`, routing policies, permission policies, and available model scope in `lagi.yml`.
3. Enable API keys, user identity, and required audit fields.
4. Split keys, quotas, and callable capabilities by department, application, or tenant.
5. Roll safety rules out gradually to avoid blocking core business traffic by mistake.

<a id="scenario-aggregation-platform"></a>

### Scenario 6: AI Aggregation Platform

An AI Aggregation Platform is useful when many systems, departments, models, and multimodal capabilities are shared across an enterprise. LinkMind becomes the central AI platform with standard APIs, unified configuration, orchestration, and observability.

Business usage:

- OA, CRM, ERP, support systems, knowledge bases, developer tools, and mobile apps all access LinkMind.
- Text, speech, images, OCR, document processing, and text-to-SQL capabilities come from one platform.
- Token and cost can be counted by department, project, system, or agent.
- Adding a model requires platform configuration changes, not rewrites in every business system.

Setup:

1. Deploy LinkMind with Docker, packaged JAR, or a source build.
2. For production, place it behind a shared gateway and scale nodes according to concurrency.
3. Configure multiple providers, RAG, Skills, filters, and routing rules.
4. Assign each business system its own API key, quota, and allowed capability scope.
5. Establish release workflows for model changes, policy changes, and knowledge-base updates.

<a id="scenario-embedding-server"></a>

### Scenario 7: Embedding Server

Embedding Server is for teams that already have a RAG platform, search platform, or vector store but need one shared vectorization service. LinkMind can act as an OpenAI-compatible embedding layer.

Business usage:

- Document chunks, query text, and structured fields are sent to LinkMind for embeddings.
- LinkMind routes by language, domain, or task type to the right embedding model.
- The upper knowledge platform does not need to know how vector models are replaced or upgraded.

Setup:

1. Enable embedding models and providers in `lagi.yml`.
2. Let the current RAG or search system call `/v1/embeddings`.
3. Keep vector dimension, model version, and index rebuild strategy traceable.
4. Before upgrading models, run a small evaluation and decide whether a full index rebuild is needed.

<a id="scenario-regional-cascade"></a>

### Scenario 8: Proxy + GEO + Cascade

Proxy, GEO, and Cascade are for multi-region, multi-organization, group-level, or cross-network environments. LinkMind is not a simple API relay; it is an intelligent proxy layer with routing, safety, cache, billing, and audit.

Business usage:

- Multiple systems share one model exit point instead of each team holding upstream model keys.
- Regional users connect to nearby LinkMind nodes to reduce latency.
- Parent nodes distribute policies while child nodes serve local business systems and agents.
- Serial nodes can separate responsibilities such as security, routing, and cache.

Setup:

1. Start with one LinkMind proxy node in a single region for model exit, filtering, and cost statistics.
2. Deploy multiple LinkMind nodes across regions and route requests by network, compliance, and latency.
3. For large organizations, use parent-child cascade: headquarters manages policies, regional nodes handle local traffic.
4. At each layer, define cross-region data rules, log retention, key management, and failover behavior.

<a id="scenario-benchmark"></a>

### Scenario 9: Benchmark Load Test

Benchmark load testing is useful for POC acceptance, pre-launch capacity checks, before/after version comparisons, cache or routing strategy validation, and diagnosing latency, failure rate, or token throughput under concurrency. The goal is not to claim production capacity from a single run; it is to keep scripts, test data, parameters, and results as repeatable evidence.

Business usage:

- Fix concurrency, run count, model, request parameters, and the test question pool to create a comparable baseline.
- Compare response behavior across versions, models, cache strategies, or deployment sizes.
- Watch status codes, total latency, tokens/second, failures, timeouts, and stack traces instead of relying only on averages.
- Keep scripts, test data, and run logs together under `tools/benchmark` so delivery, engineering, and operations can review the same evidence.

Setup:

1. Start LinkMind and confirm that `http://127.0.0.1:8080/chat/completions` or the target environment endpoint is reachable.
2. Read [benchmark instructions](../tools/benchmark/README.md), then adjust `concurrent_testing.py` as needed: endpoint, `API_KEY`, `MODEL`, `MAX_THREADS`, `FETCH_TIMES`, and request body parameters.
3. Run the script from `tools/benchmark` and redirect console output to a log file.
4. Record environment, version, model, concurrency, run count, cache state, and test data scope for every run so warmup effects, environment drift, or load-generator bottlenecks are not mistaken for service behavior.
5. Keep at least one before/after pair with the same test setup before launch. For capacity conclusions, also check server CPU/memory/IO, downstream dependencies, p95/p99, error rate, and load-generator resources.

<a id="scenario-launch-checks"></a>

### Production Checklist

- Business entry points, user identity, caller systems, and API key management are defined.
- At least one stable model provider is enabled, with backup models or failover rules when needed.
- RAG scenarios have clear knowledge sources, update frequency, permission boundaries, and vector-store strategy.
- Filters, blacklists/whitelists, audit fields, and content policies are enabled according to business risk.
- Token statistics, cost attribution, error rate, latency, and cache hit rate have been verified.
- Benchmark scenarios keep script parameters, test data, run logs, and environment notes, and do not replace capacity conclusions with one-run averages.
- A phased rollout plan exists by business line, department, or agent.
- Responsibility boundaries for model keys, private data, logs, and cache storage are clear.
