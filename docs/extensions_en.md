# More Integrations and Custom Extensions

Use configuration first. Add Java code only when the provider, modality, storage, or runtime behavior cannot be represented by an existing compatible adapter.

## 1. Choose the extension boundary

| Need | Preferred approach |
| --- | --- |
| OpenAI- or Qwen-compatible provider | Add a model config using an existing compatible adapter |
| New model ID, endpoint, or key pool | Add or adjust `models` and `functions.*` |
| New provider request/response protocol | Implement a model adapter |
| New audio, image, or video capability | Implement the matching modality adapter |
| New retrieval backend | Implement `VectorStore` or extend `BaseVectorStore` |
| New tool or task behavior | Add an Agent, MCP server, Skill, or Worker |

## 2. Add a compatible provider

```yaml
models:
  - name: custom-openai
    type: OpenAI-Compatible
    enable: true
    model: my-model
    driver: ai.llm.adapter.impl.OpenAIStandardAdapter
    api_address: https://your-endpoint.example.com/chat/completions
    api_key: your-api-key

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

Verify a short non-streaming request first, then streaming and route fallback. The same configuration pattern applies to a new model ID behind an existing provider.

## 3. Add an adapter or vector store

LLM adapters implement `ai.llm.adapter.ILlmAdapter`. Audio, image, and video adapters implement their corresponding modality interfaces. A custom retrieval backend implements `ai.vector.VectorStore` or extends `ai.vector.impl.BaseVectorStore`, then registers under `stores.vector`.

Keep provider conversion inside the adapter. Keep business-specific metadata in typed extensions such as `extra_body`, and keep social or workflow state in Skills and service helpers. This preserves the standard completion and streaming path.

## 4. Extend the runtime

Add Agents in `agents.items`, MCP servers in `mcps.servers`, and Skills/Workers under `skills`. Reuse `routers.items` and `filters.items` where selection and policy need to be shared. See [Agents and Tools](agents-and-tools_en.md) for configuration and verification.

## 5. Safe extension order

1. Reuse a compatible adapter with configuration only.
2. Add the smallest adapter or store implementation when necessary.
3. Expose it through the appropriate `functions.*` block.
4. Add routing, access control, observability, and tests before production traffic.

The previous detailed implementation examples remain in [Legacy Extension Guide](extend_en.md) while existing integrations transition to this feature-oriented navigation.
