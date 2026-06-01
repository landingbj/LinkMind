# LinkMind Business Scenarios Guide

This guide is for product, architecture, and delivery teams that want to put LinkMind into real business systems. It explains how LinkMind is used in enterprise scenarios, which deployment shape to choose, and how to start a practical pilot.

If the service is not running yet, start with the [Installation Guide](install_en.md). If you are already integrating business systems, use this document together with the [Integration Guide](guide_en.md), [Configuration Reference](config_en.md), and [API Reference](API_en.md).

## 1. Where LinkMind Fits

LinkMind is not another standalone model. It is a unified middleware layer between business systems, private data, private capabilities, agent runtimes, and model providers.

```text
Business and interaction layer
Web / App / API / OA / CRM / ERP / Agent / OpenClaw
        |
        v
LinkMind middleware layer
Routing / RAG / Skills / Safety filters / Cache acceleration / Token governance / Observability
        |
        v
Models, data, and tools
LLM / VLM / MLLM / ASR / TTS / Embedding / Vector stores / Private databases / Tools
```

The core idea is to keep model switching, failover, safety policies, RAG retrieval, token reduction, cost accounting, and capability reuse inside LinkMind. Business applications can call a stable LinkMind API or its OpenAI-compatible endpoints instead of binding themselves to each provider.

## 2. Choose a Deployment Shape First

| Current business situation | Recommended shape | Main goal |
| --- | --- | --- |
| Multiple agents or business lines need shared AI capabilities | Agent Server | Unified Skills, Tools, model routing, safety, and observability |
| The team has private documents, databases, or policy knowledge | Knowledge Base Server | Use RAG so answers are grounded in enterprise knowledge |
| The team already uses OpenClaw, Hermes Agent, DeerFlow, or another agent workspace | Agent Mate | Transparent enhancement, token reduction, and safety boundaries |
| Local appliance or edge GPU capacity is limited and repeated questions are common | Medusa Accelerator | Reuse cache, reduce repeated inference, and lower GPU load |
| Finance, healthcare, government, or other high-compliance environments | Security Gateway | Unified filters, permissions, audit, and AI guardrails |
| Many departments and systems share AI capability | AI Aggregation Platform | Unified access, orchestration, governance, observability, and cost accounting |
| An existing RAG platform only needs a shared embedding service | Embedding Server | Unified Embedding API with replaceable models |
| Multi-region, multi-team, or group-level organizations need a shared proxy exit | Proxy + GEO + Cascade | Distributed topology, layered governance, and regional access |
| The team needs a capacity or stability baseline before launch, scaling, or version comparison | Benchmark Load Test | Fixed concurrency, run count, model, and dataset with repeatable result logs |

These shapes can be combined. A common path is to start with Agent Mate or a single-node Agent Server for POC, use Benchmark load testing to establish a capacity baseline, then grow into Agent Server + Security Gateway + Knowledge Base Server as a full enterprise AI platform.

## 3. Standard Business Integration Steps

1. Define the entry point: Web/App/API, internal OA/CRM/ERP, support system, developer tool, or existing agent workspace.
2. Define the data boundary: which requests may leave the network, which knowledge must stay private, and which fields require masking, filtering, or audit.
3. Choose deployment: use the installer, packaged JAR, or Docker for pilot; expand to single-node, cluster, gateway, or GEO/Cascade topology for production.
4. Configure models and routes: enable providers, routing rules, failover, filters, RAG, and required Skills in `lagi.yml`.
5. Connect business calls: call native LinkMind routes or OpenAI-compatible `/v1/...` routes instead of model-provider APIs directly.
6. Add governance: enable API keys, user identity, filters, blacklists/whitelists, token statistics, and request tracing.
7. Pilot narrowly: validate answer quality, retrieval hit rate, token consumption, latency, error rate, and safety behavior on one business line.
8. Load test: use the Benchmark scripts under `tools/benchmark` with fixed concurrency, run count, model, and test data; keep logs as a repeatable baseline.
9. Scale out: reuse proven routes, policies, knowledge bases, and Skills across more business lines or LinkMind nodes.

A lightweight pilot can start from 8 CPU cores, 32 GB memory, and 200 GB storage. Prepare GPU servers when local model inference is a core requirement. If the pilot mainly calls cloud model APIs, local GPU is usually not required.

## 4. Typical Scenarios

### Scenario 1: Agent Server

Agent Server is useful when multiple agents, business lines, or teams need one shared AI capability base. Business agents no longer manage model keys, tool registration, safety rules, and observability on their own; they call LinkMind.

Business usage:

- Multiple agents share the same Skills, Tools, model routes, and safety policies.
- Business teams focus on task orchestration while LinkMind owns common capabilities.
- Platform teams observe token usage, request traces, and error rates by business line.

Setup:

1. Start LinkMind with the [Installation Guide](install_en.md); a single node is enough for an initial pilot.
2. Enable at least one chat model, routing rules, and required Skills in the [Configuration Reference](config_en.md).
3. Assign the LinkMind endpoint and API key to each agent.
4. Let agents call `/chat/completions` or `/v1/chat/completions`.
5. For production, add a gateway, load balancing, and multiple nodes to avoid single-node bottlenecks.

### Scenario 2: Knowledge Base Server

Knowledge Base Server is for enterprises that already have policy documents, product materials, FAQs, tickets, databases, or industry knowledge and need model answers grounded in internal knowledge.

Business usage:

- Support, sales, operations, engineering support, or internal assistants send user questions to LinkMind.
- LinkMind retrieves relevant context from vector stores, databases, or document indexes.
- Retrieved context is injected into the model path so answers are grounded in enterprise knowledge.
- New materials can be incrementally added so the knowledge base evolves with the business.

Setup:

1. Prepare knowledge sources such as PDF, Word, HTML, FAQ, MySQL, Elasticsearch, Chroma, Milvus, or another vector store.
2. Configure vector storage, embedding models, and retrieval flow with the [Annex](annex_en.md) and [Configuration Reference](config_en.md).
3. Use document processing, OCR, and embedding capabilities to chunk, vectorize, and index content.
4. Let business systems call the chat API while LinkMind handles retrieval augmentation.
5. Add review, update, and rollback workflows for high-value knowledge bases before production use.

### Scenario 3: Agent Mate

Agent Mate is for teams that already use OpenClaw, Hermes Agent, DeerFlow, or another agent workspace. LinkMind does not replace the current system; it joins as an OpenAI-compatible provider or sidecar middleware.

Business usage:

- Existing agents keep their original workflows while model calls pass through LinkMind.
- LinkMind handles token reduction, context trimming, structured reuse, and on-demand recall.
- Input/output filters, blacklisted Skills, permission policies, and model routing apply in the middleware layer.
- Teams can connect one agent path first, validate the effect, then expand gradually.

Setup:

1. Choose `Agent Mate` during installation, or synchronize OpenClaw, Hermes Agent, or DeerFlow model settings through the [Configuration Reference](config_en.md).
2. Change the existing agent model provider endpoint to LinkMind's OpenAI-compatible endpoint.
3. Keep agent code unchanged and only replace base URL, model name, and key.
4. Enable filters, token statistics, and routing failover.
5. Compare token consumption, latency, failure rate, and safety interceptions before and after the change.

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

### Scenario 9: Benchmark Load Test

Benchmark load testing is useful for POC acceptance, pre-launch capacity checks, before/after version comparisons, cache or routing strategy validation, and diagnosing latency, failure rate, or token throughput under concurrency. The goal is not to claim production capacity from a single run; it is to keep scripts, test data, parameters, and results as repeatable evidence.

Business usage:

- Fix concurrency, run count, model, request parameters, and the test question pool to create a comparable baseline.
- Compare response behavior across versions, models, cache strategies, or deployment sizes.
- Watch status codes, total latency, tokens/second, failures, timeouts, and stack traces instead of relying only on averages.
- Keep scripts, test data, and run logs together under `tools/benchmark` so delivery, engineering, and operations can review the same evidence.

Setup:

1. Start LinkMind and confirm that `http://127.0.0.1:8080/chat/completions` or the target environment endpoint is reachable.
2. Read `tools/benchmark/README.md`, then adjust `concurrent_testing.py` as needed: endpoint, `API_KEY`, `MODEL`, `MAX_THREADS`, `FETCH_TIMES`, and request body parameters.
3. Run the script from `tools/benchmark` and redirect console output to a log file.
4. Record environment, version, model, concurrency, run count, cache state, and test data scope for every run so warmup effects, environment drift, or load-generator bottlenecks are not mistaken for service behavior.
5. Keep at least one before/after pair with the same test setup before launch. For capacity conclusions, also check server CPU/memory/IO, downstream dependencies, p95/p99, error rate, and load-generator resources.

## 5. Production Checklist

- Business entry points, user identity, caller systems, and API key management are defined.
- At least one stable model provider is enabled, with backup models or failover rules when needed.
- RAG scenarios have clear knowledge sources, update frequency, permission boundaries, and vector-store strategy.
- Filters, blacklists/whitelists, audit fields, and content policies are enabled according to business risk.
- Token statistics, cost attribution, error rate, latency, and cache hit rate have been verified.
- Benchmark scenarios keep script parameters, test data, run logs, and environment notes, and do not replace capacity conclusions with one-run averages.
- A phased rollout plan exists by business line, department, or agent.
- Responsibility boundaries for model keys, private data, logs, and cache storage are clear.

## 6. What to Read Next

- [Installation Guide](install_en.md)
- [Integration Guide](guide_en.md)
- [Configuration Reference](config_en.md)
- [API Reference](API_en.md)
- [Security & Safety](security_en.md)
- [Annex](annex_en.md)
