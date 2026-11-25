# Compatible Model Provider

## 接入 / Setup

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

Verify a short non-streaming request, streaming output, and route fallback separately. See [Models and Multimodal](../../models-and-multimodal_en.md) / [模型接入、路由与多模态](../../models-and-multimodal_zh.md).
