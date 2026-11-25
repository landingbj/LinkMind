# Compatible Model Provider

Use this recipe when an upstream exposes an OpenAI-compatible chat endpoint but is not already covered by a dedicated LinkMind adapter. It keeps the client-facing LinkMind protocol unchanged.

## 1. Add the Provider and route

For an OpenAI-compatible provider, define a model backend with `ai.llm.adapter.impl.OpenAIStandardAdapter`, its `api_address`, API key, and model ID. Then add the backend under `functions.chat.backends` and select it with `route`.

```yaml
models:
  - name: custom-openai
    type: OpenAI-Compatible
    enable: true
    model: my-model
    driver: ai.llm.adapter.impl.OpenAIStandardAdapter
    api_address: https://your-endpoint.example.com/chat/completions
    api_key: your-api-key
```

Complete the chat backend in the same file:

```yaml
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

Use a secret-management mechanism or environment variable for the real key. `api_address` must be the upstream's actual Chat Completions endpoint, not merely its marketing or console URL.

## 2. Verify the compatibility contract

Restart LinkMind, call `GET /v1/models`, and send a short non-streaming `POST /v1/chat/completions` request with `model: my-model`. Then repeat with `stream: true` and confirm the client receives complete SSE output through `[DONE]`.

Verify a provider error, timeout, tool call if used, and route fallback separately before production. A 200 response from the upstream does not by itself prove that its error shape, usage fields, and stream completion are compatible. See [Models and Multimodal](../../models-and-multimodal_en.md) / [模型接入、路由与多模态](../../models-and-multimodal_zh.md).
