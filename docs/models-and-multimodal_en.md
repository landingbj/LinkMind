# Models, Routing, and Multimodal

This page keeps provider configuration, routing, API calls, and verification in one place. Prove one chat request first, then enable embeddings, audio, image, video, or document processing as needed. Each capability must be verified independently.

## 1. Configure a provider and chat route

Declare an upstream in `models`, then expose a chat model through `functions.chat.backends` in `lagi.yml`. `name` is LinkMind's internal backend name; `model` is the model ID used by callers or the provider. They are not interchangeable.

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

| Field | Purpose |
| --- | --- |
| `name` / `backend` | Internal backend name and reference |
| `type` / `driver` | Provider label and adapter implementation |
| `api_address` / `endpoint` | Upstream address for a self-hosted or compatible provider |
| `api_key` / `api_keys` | One key or a key pool |
| `key_route` | Key-pool policy: `polling` or `failover` |
| `enable` / `priority` | Enablement and backend priority |
| `stream` / `protocol` | Streaming and compatible protocol, commonly `completion` |

Inject real keys through environment variables or controlled configuration. Never put them in source, screenshots, or support bundles. An OpenAI-compatible upstream can reuse `ai.llm.adapter.impl.OpenAIStandardAdapter`; other compatible endpoints can use `OpenRouterAdapter` or `QwenCompatibleAdapter` as appropriate.

For failover, keep the caller-facing model ID stable and select backends in the route:

```yaml
functions:
  chat:
    route: best((qwen|deepseek),openai)
    handle: failover
    grace_time: 20
    context_length: 4096
```

`pass(name)` pins a backend. `best(...)` chooses from candidates. Confirm that every candidate works independently before enabling routing and failover.

## 2. Quickly verify chat and the model list

After restarting the service, list the exposed models and make one short non-streaming request. A protected deployment requires `Authorization: Bearer <linkmind-api-key>`.

```bash
curl http://localhost:8080/v1/models \
  -H "Authorization: Bearer <linkmind-api-key>"

curl http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer <linkmind-api-key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"qwen-plus","messages":[{"role":"user","content":"Describe LinkMind in one sentence."}],"stream":false,"max_completion_tokens":128}'
```

Success means that the response contains `choices[0].message.content` and the **Control** view records the same model and time. If a model is absent from `/v1/models`, check `models.enable`, the chat backend `enable`, the backend name, and the model ID. If upstream calls fail, check the key, balance, network, and provider address.

## 3. Chat, Responses, and Messages APIs

| Protocol | Endpoint | Minimum request fields | Typical client |
| --- | --- | --- | --- |
| OpenAI Chat Completions | `POST /v1/chat/completions` | `model`, `messages` | OpenAI SDK and most agents |
| OpenAI Responses | `POST /v1/responses` | `model`, `input` | Responses SDK |
| Anthropic Messages | `POST /v1/messages` | `model`, `max_tokens`, `messages` | Claude Code and similar clients |

For Chat Completions, `messages` is an ordered list of `role`/`content` messages. `stream` controls SSE, `max_completion_tokens` limits generated tokens, and `temperature`, `tools`, `store`, and typed `extra_body` are optional. A streaming client must continue reading through `data: [DONE]`; an HTTP 200 or first SSE event alone is not success.

Responses example:

```bash
curl http://localhost:8080/v1/responses \
  -H "Authorization: Bearer <linkmind-api-key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"qwen-plus","input":"Explain the purpose of routing in one sentence."}'
```

Anthropic Messages example:

```bash
curl http://localhost:8080/v1/messages \
  -H "Authorization: Bearer <linkmind-api-key>" \
  -H "anthropic-version: 2023-06-01" \
  -H "Content-Type: application/json" \
  -d '{"model":"qwen-plus","max_tokens":128,"messages":[{"role":"user","content":"Hello"}]}'
```

The public protocol stays at LinkMind. Provider-specific protocol and model mapping belong only in `lagi.yml`, not in every client.

## 4. Embeddings and multimodal capabilities

Every capability needs an enabled backend. This is a minimal embedding configuration; replace `model_name` with an embedding model supported by the chosen provider.

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

The successful response contains an embedding array. Before using it for a knowledge base, confirm that its dimension matches the target vector index.

| Capability | Function block / endpoint | Main input |
| --- | --- | --- |
| Speech recognition and TTS | `speech2text` / `text2speech`; `POST /audio/speech2text`, `GET /audio/text2speech` | Audio file or text |
| Image understanding, generation, enhancement | `image2text`, `text2image`, `image2enhance`; `/image/*` | Image URL/file or prompt |
| Video generation, tracking, enhancement | `text2video`, `image2video`, `video2track`, `video2enhance`; `/video/*` | Prompt, image, or video |
| Document OCR, extraction, structuring | `doc2ocr`, `doc2ext`, `doc2struct`; `/ocr/doc2ocr`, `/doc/*` | Document file |

Multimodal backend entries normally include `backend`, `model`, `enable`, and `priority`. Test small supported input first, then increase concurrency or connect production documents.

## 5. Troubleshooting and next steps

- **Chat works but embeddings or OCR fail:** the function block may be missing, the selected model may not support the modality, or its key may have different permissions.
- **The model is shown but calls fail:** visibility is not upstream availability. Check provider key, model ID, balance, DNS/proxy, and logs in that order.
- **A stream has no completion marker:** record first event, last event, and connection-close times; inspect LinkMind-to-upstream SSE logs as well as the client.
- **To upload documents for Q&A:** continue to [Knowledge Base](knowledge-base_en.md), which includes storage, ingestion, retrieval, and RAG verification.
- **To add a new provider protocol or modality:** see [More Integrations and Custom Extensions](extensions_en.md).
