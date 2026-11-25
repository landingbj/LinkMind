# More Integrations and Custom Extensions

Reuse configuration and existing compatible adapters first. Add Java only when the existing surface cannot express a provider, modality, store, or runtime behavior. This page defines extension boundaries, minimal configuration, implementation entry points, and verification order.

## 1. Choose the correct extension boundary

| Need | Preferred approach |
| --- | --- |
| New model ID, key pool, upstream address, or route | Change `models` and `functions.*` |
| OpenAI, Qwen, or similar compatible provider | Reuse a compatible adapter |
| New provider request/response protocol | Implement an LLM adapter |
| New speech, image, video, or document capability | Implement the matching modality adapter |
| New retrieval backend | Implement `VectorStore` or extend `BaseVectorStore` |
| New tool or task behavior | Add an Agent, MCP server, Skill, or Worker |

Callers should retain LinkMind's stable endpoint and protocol. Provider differences, credentials, and field mapping belong in the adapter and configuration.

## 2. Add a compatible provider and verify it

An OpenAI-compatible service normally needs no new Java code:

```yaml
models:
  - name: custom-openai
    type: OpenAI-Compatible
    enable: true
    model: my-model
    driver: ai.llm.adapter.impl.OpenAIStandardAdapter
    api_address: https://your-endpoint.example.com/chat/completions
    api_key: ${CUSTOM_PROVIDER_KEY}

functions:
  chat:
    route: pass(custom-openai)
    backends:
      - backend: custom-openai
        model: my-model
        enable: true
        stream: true
        protocol: completion
        priority: 100
```

After restart, call `GET /v1/models`, send one short non-streaming request to `POST /v1/chat/completions`, then validate SSE events and `[DONE]` with `stream:true`. Do not send production traffic before checking upstream error shapes, timeout, tool calls, and streaming completion behavior.

## 3. Implement a new adapter or store

An LLM adapter implements `ai.llm.adapter.ILlmAdapter`. Audio, image, video, and document capabilities implement their corresponding modality interfaces. A custom retrieval backend implements `ai.vector.VectorStore` or extends `ai.vector.impl.BaseVectorStore`, then registers in `stores.vector`.

Follow three boundaries:

1. Keep provider request/response transformation inside the adapter, not in every business caller.
2. Carry business data through typed fields such as `extra_body`; do not change standard chat-message structure.
3. Keep social and workflow state in Skills, Workers, and supporting Services so it cannot block standard completion, routing, or streaming.

For a vector store, validate connection/create, write, category query, update, delete, metadata filtering, and concurrency. The RAG call path is documented in [Knowledge Base](knowledge-base_en.md).

## 4. Extend the runtime and test in order

Register an Agent in `agents.items`, an MCP server in `mcps.servers`, and a Skill, Worker, or PnP under `skills`. Use `routers.items` for shared selection and `filters.items` for content governance. The unified call path and request fields are in [Agents, Tools, and Task Orchestration](agents-and-tools_en.md).

Recommended order: reuse a compatible adapter and validate configuration -> build the smallest adapter or store -> expose it through the relevant `functions.*` block -> test non-stream, stream, errors, timeouts, concurrency, and authentication separately -> then enable routing, billing, tools, and production traffic.

Every extension should include an example without real keys, one runnable verification request, expected output or log signature, failure checks, and unit or integration tests. Never put real provider keys, customer documents, or production URLs in examples.
