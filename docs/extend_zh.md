# 扩展指南

本文档只保留和当前代码结构一致的扩展方式。大多数情况下，优先复用现成兼容适配器会比直接写 Java 代码更快，也更稳。

## 1. 先判断你需要哪种扩展

只改配置就够的场景：

- 你的服务是 OpenAI 兼容接口
- 你的服务是 Qwen 兼容接口
- 你只是新增模型 ID、地址或凭证

需要写新适配器的场景：

- 请求或响应协议不是通用兼容格式
- 提供方有特殊签名逻辑
- 你要接入新的模态能力或新的向量存储

## 2. 优先复用现成兼容适配器

当前代码库里已经有几类通用适配器：

- `ai.llm.adapter.impl.OpenAIStandardAdapter`
- `ai.llm.adapter.impl.OpenRouterAdapter`
- `ai.llm.adapter.impl.QwenCompatibleAdapter`

很多时候你只需要补一条模型配置，再把它接到 `functions.chat.backends` 里即可。

```yaml
models:
  - name: custom-openai
    type: OpenAI-Compatible
    enable: true
    model: my-model
    driver: ai.llm.adapter.impl.OpenAIStandardAdapter
    api_address: https://your-endpoint.example.com/chat/completions
    api_key: your-api-key
    # 多 Key 池写法：api_keys: sk-key1,sk-key2  key_route: polling

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

## 3. 新增大模型适配器

### 第一步：实现适配器

大模型适配器需要实现 `ai.llm.adapter.ILlmAdapter`：

```java
public interface ILlmAdapter {
    ChatCompletionResult completions(ChatCompletionRequest request);
    Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest request);
}
```

实际落地时，类通常需要：

- 继承 `ModelService`
- 实现 `ILlmAdapter`
- 使用 `@LLM(modelNames = {...})` 注解声明负责的模型

最小骨架如下：

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

### 第二步：在 YAML 里注册

```yaml
models:
  - name: your-provider
    type: YourProvider
    enable: true
    model: your-model
    driver: ai.llm.adapter.impl.YourAdapter
    api_key: your-api-key
    # 多 Key 池写法：api_keys: sk-key1,sk-key2  key_route: polling

functions:
    route: pass(%)
    backends:
      - backend: your-provider
        model: your-model
        enable: true
        stream: true
        protocol: completion
        priority: 100
```

这里有个和旧文档不同的重要点：当前对话后端写在 `functions.chat.backends` 下，不再是旧版的扁平 `functions.chat` 列表。

## 4. 新增音频或图像适配器

### 音频适配器

音频适配器需要实现 `ai.audio.adapter.IAudioAdapter`：

```java
public interface IAudioAdapter {
    AsrResult asr(File audio, AudioRequestParam param);
    TTSResult tts(TTSRequestParam param);
}
```

其中：

- 语音识别使用 `@ASR(...)`
- 语音合成使用 `@TTS(...)`

接入后，再通过配置把它挂到功能块中：

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

### 图像生成适配器

图像生成适配器需要实现 `ai.image.adapter.IImageGenerationAdapter`：

```java
public interface IImageGenerationAdapter {
    ImageGenerationResult generations(ImageGenerationRequest request);
}
```

使用 `@ImgGen(modelNames = "...")` 注册后，再通过 `functions.text2image` 绑定即可。

## 5. 新增向量存储

如果你要扩展新的向量后端，可以实现 `ai.vector.VectorStore`，也可以直接继承 `ai.vector.impl.BaseVectorStore`。

代码库里已经有现成参考实现：

- `ai.vector.impl.ChromaVectorStore`
- `ai.vector.impl.SqliteVectorStore`
- `lagi-extension/src/main/java/ai/vector/impl/MilvusVectorStore.java`
- `lagi-extension/src/main/java/ai/vector/impl/PineconeVectorStore.java`

当前注册位置在 `stores.vector`：

```yaml
stores:
  vector:
    - name: milvus
      driver: ai.vector.impl.MilvusVectorStore
      default_category: default
      url: http://localhost:19530
      token: your-token
```

之后再把 `stores.rag.vector` 指向这个后端名称即可。

## 6. 扩展 Agents、Skills、Workers 与 MCP

### Agents

新增 Agent 可写在 `agents.items` 或 `agent.yml`：

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

### Skills 与 Workers

当前默认配置里，`workers` 和 `pnps` 是挂在 `skills` 下面的：

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

如果你的扩展依赖路由逻辑，可以在 `routers.items` 中新增或复用规则，再在 Worker 中引用。

## 7. 运行时生态集成

当前代码中还内置了以下配置同步能力：

- OpenClaw
- Hermes Agent
- DeerFlow

它们更像是运行时生态桥接能力，而不是替代 LinkMind 自身 YAML 的配置来源。实际运行时仍然以 LinkMind 本地配置为主。

## 8. 更稳妥的扩展顺序

对大多数团队来说，建议按这个顺序推进：

1. 先尝试只用现成兼容适配器加配置接入。
2. 如果不够，再新增对应模态的 Java 适配器。
3. 适配器跑通后，再挂到 `functions.*`。
4. 如果涉及检索，再补 `stores.vector` 和 `stores.rag`。

这样做通常最容易保证实现和文档都保持简单、可维护。
