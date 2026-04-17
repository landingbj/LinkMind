# Extension Guide

This guide describes the extension points that match the current codebase. The fastest path is usually to reuse an existing compatible adapter first, and only write Java code when you truly need a new protocol or storage backend.

## 1. Decide what kind of extension you need

Use configuration only when:

- your provider is OpenAI-compatible
- your provider is Qwen-compatible
- you only need to add a new model ID, endpoint, or credential set

Write a new adapter when:

- the request or response shape is custom
- the provider needs special signing logic
- you are adding a new modality or storage backend

## 2. Reuse existing compatibility adapters first

The current codebase already contains several general-purpose adapters:

- `ai.llm.adapter.impl.OpenAIStandardAdapter`
- `ai.llm.adapter.impl.OpenRouterAdapter`
- `ai.llm.adapter.impl.QwenCompatibleAdapter`

In many integrations, you only need to add a model row and then reference it from `functions.chat.backends`.

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
    route: pass(%)
    backends:
      - backend: custom-openai
        model: my-model
        enable: true
        stream: true
        protocol: completion
        priority: 100
```

## 3. Add a new LLM adapter

### Step 1: implement the adapter

LLM adapters implement `ai.llm.adapter.ILlmAdapter`:

```java
public interface ILlmAdapter {
    ChatCompletionResult completions(ChatCompletionRequest request);
    Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest request);
}
```

In practice, the adapter class should:

- extend `ModelService`
- implement `ILlmAdapter`
- be annotated with `@LLM(modelNames = {...})`

Minimal shape:

```java
@LLM(modelNames = {"your-model"})
public class YourAdapter extends ModelService implements ILlmAdapter {
    @Override
    public ChatCompletionResult completions(ChatCompletionRequest request) {
        return null;
    }

    @Override
    public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest request) {
        return null;
    }
}
```

### Step 2: register it in YAML

```yaml
models:
  - name: your-provider
    type: YourProvider
    enable: true
    model: your-model
    driver: ai.llm.adapter.impl.YourAdapter
    api_key: your-api-key

functions:
  chat:
    route: pass(%)
    backends:
      - backend: your-provider
        model: your-model
        enable: true
        stream: true
        protocol: completion
        priority: 100
```

The important current detail is that chat backends live under `functions.chat.backends`, not under the older flat `functions.chat` list.

## 4. Add audio or image adapters

### Audio

Audio adapters implement `ai.audio.adapter.IAudioAdapter`:

```java
public interface IAudioAdapter {
    AsrResult asr(File audio, AudioRequestParam param);
    TTSResult tts(TTSRequestParam param);
}
```

Use:

- `@ASR(...)` for speech recognition
- `@TTS(...)` for synthesis

Then bind the adapter through normal config:

```yaml
functions:
  speech2text:
    - backend: your-audio
      model: asr
      enable: true
      priority: 10

  text2speech:
    - backend: your-audio
      model: tts
      enable: true
      priority: 10
```

### Image generation

Image adapters implement `ai.image.adapter.IImageGenerationAdapter`:

```java
public interface IImageGenerationAdapter {
    ImageGenerationResult generations(ImageGenerationRequest request);
}
```

Register the class with `@ImgGen(modelNames = "...")`, then wire it into `functions.text2image`.

## 5. Add a vector store

For custom vector backends, implement `ai.vector.VectorStore` or extend `ai.vector.impl.BaseVectorStore`.

The repository already gives you working references:

- `ai.vector.impl.ChromaVectorStore`
- `ai.vector.impl.SqliteVectorStore`
- `lagi-extension/src/main/java/ai/vector/impl/MilvusVectorStore.java`
- `lagi-extension/src/main/java/ai/vector/impl/PineconeVectorStore.java`

Current registration happens in `stores.vector`:

```yaml
stores:
  vector:
    - name: milvus
      driver: ai.vector.impl.MilvusVectorStore
      default_category: default
      url: http://localhost:19530
      token: your-token
```

Then point `stores.rag.vector` at that backend name.

## 6. Extend agents, skills, workers, and MCP

### Agents

Add a new agent in `agents.items` or `agent.yml`:

```yaml
agents:
  enable: true
  items:
    - name: your-agent
      driver: ai.agent.customer.YourAgent
```

### MCP

```yaml
mcps:
  enable: true
  servers:
    - name: your_mcp
      url: https://your-mcp.example.com/sse
```

### Skills and workers

In the current default config, workers and pnps are nested under `skills`:

```yaml
skills:
  enable: true
  roots: ["classpath:skills"]
  workspace: "skills"
  workers:
    - name: appointedWorker
      route: pass(%)
      worker: ai.worker.DefaultAppointWorker
  pnps:
    - name: qq
      api_key: your-api-key
      driver: ai.pnps.social.QQPnp
```

If your extension needs routing, define or reuse a router under `routers.items`, then call it from the worker definition.

## 7. Runtime ecosystem integrations

The current codebase also includes sync services for:

- OpenClaw
- Hermes Agent
- DeerFlow

These are runtime integration helpers, not replacements for your LinkMind YAML. Treat them as ecosystem bridges that can import or export configuration where needed.

## 8. Practical extension order

For most teams, the safest order is:

1. Add config only with an existing compatible adapter.
2. If that is not enough, add a new Java adapter for the modality you need.
3. Only after the adapter works, expose it through `functions.*`.
4. If retrieval is involved, add or swap the vector backend in `stores.vector`.

Following this order usually keeps both the implementation and the documentation much simpler.
