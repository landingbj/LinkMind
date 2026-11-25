# Models, Routing, and Multimodal

Use this page to connect a model provider, choose a route, and call chat, embedding, audio, image, or video capabilities.

## 1. Configure a provider and chat route

Define the provider under `models`, then expose it through `functions.chat.backends`:

```yaml
models:
  - name: qwen
    type: Alibaba
    enable: true
    model: qwen-plus,asr,vision,ocr
    driver: ai.wrapper.impl.AlibabaAdapter
    api_key: your-api-key

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

`name` is the backend reference used by functions, `model` is the provider model ID, and `driver` selects the adapter. Use `api_keys` with `key_route: polling` or `failover` for a key pool. Existing compatible adapters include `OpenAIStandardAdapter`, `OpenRouterAdapter`, and `QwenCompatibleAdapter`.

## 2. Routing and resilience

Use `pass(...)` for a direct or fallback route and `best(...)` where the configured router should select from candidates. Keep model-specific logic in `lagi.yml`; callers should retain one stable LinkMind endpoint. The chat block also controls filters, queue handling, retries, context length, and token accounting.

```yaml
functions:
  chat:
    route: best((qwen|deepseek),openai)
    handle: failover
    grace_time: 20
    context_length: 4096
```

## 3. Call chat APIs

| Protocol | Endpoint | Required body fields |
| --- | --- | --- |
| OpenAI Chat Completions | `POST /v1/chat/completions` | `model`, `messages` |
| OpenAI Responses | `POST /v1/responses` | `model`, `input` |
| Anthropic Messages | `POST /v1/messages` | `model`, `max_tokens`, `messages` |
| LinkMind worker call | `POST /chat/go` | Worker-specific request body |

Use `Authorization: Bearer <linkmind-api-key>` when the deployment enables API authentication. See [API Reference](API_en.md#text) for complete request and stream schemas.

## 4. Enable embeddings and multimodal functions

Each function uses an enabled model backend. Example embedding configuration:

```yaml
functions:
  embedding:
    - backend: qwen
      type: Qwen
      api_key: your-api-key
      model_name: text-embedding
```

Common function blocks are `speech2text`, `text2speech`, `text2image`, `image2text`, `image2ocr`, `image2enhance`, `text2video`, `image2video`, `video2track`, `video2enhance`, `doc2ocr`, `doc2ext`, and `doc2struct`. Each backend item normally declares `backend`, `model`, `enable`, and `priority`.

| Capability | Endpoint |
| --- | --- |
| Embeddings | `POST /v1/embeddings` |
| Speech recognition / TTS | `POST /audio/speech2text`, `GET /audio/text2speech` |
| Image understanding / generation / enhancement | `POST /image/image2text`, `/image/text2image`, `/image/image2enhance` |
| Video generation / tracking / enhancement | `POST /image/image2video`, `/video/video2tracking`, `/video/mmeditingInference` |
| Document OCR / extraction / structuring | `POST /ocr/doc2ocr`, `/doc/doc2ext`, `/doc/doc2struct` |

Test every capability with a small supported input after saving configuration. A successful text chat does not verify audio, image, video, OCR, or embedding backends.

## 5. Related pages

- [Knowledge Base](knowledge-base_en.md): embeddings and vector retrieval in RAG.
- [Extensions](extensions_en.md): add a provider adapter or modality implementation.
- [API Reference](API_en.md): full endpoint parameters and response schemas.
