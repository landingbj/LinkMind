# 扩展指南

本页说明如何扩展模型适配器、多模态能力、向量存储、运行时集成以及二次开发接口。更稳妥的做法是优先复用兼容适配器，再在确实需要时编写新的 Java 扩展代码或业务扩展层。

## 1. 先判断你需要哪种扩展

以下情况优先只改配置：

- 目标厂商本身兼容 OpenAI 接口
- 目标厂商本身兼容 Qwen 风格接口
- 你只是要增加新的模型 ID、端点地址或一组新的凭证

以下情况才建议编写新适配器：

- 请求或响应结构与现有适配器差异较大
- 厂商需要特殊签名逻辑
- 你要新增新的模态能力或新的存储后端

## 2. 优先复用现成兼容适配器

当前代码里已经有几个通用兼容适配器：

- `ai.llm.adapter.impl.OpenAIStandardAdapter`
- `ai.llm.adapter.impl.OpenRouterAdapter`
- `ai.llm.adapter.impl.QwenCompatibleAdapter`

很多场景里，你只需要新增一个模型配置，再在 `functions.chat.backends` 中引用它即可。

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

## 3. 扩展模型与多模态适配器

## 3.1 先补配置

当前配置结构依然以 `models` 和 `functions` 为中心。一个最小的自定义模型配置可以写成：

```yaml
models:
  - name: your-model-name
    type: your-model-type
    enable: true
    model: model-version
    driver: ai.llm.adapter.impl.YourAdapter
    api_key: your-api-key
    secret_key: your-secret-key
    api_address: your-api-address

functions:
  chat:
    route: pass(%)
    backends:
      - backend: your-model-name
        model: model-version
        enable: true
        stream: true
        priority: 100
```

当一个逻辑后端同时承载多种模态能力时，可以使用多驱动写法：

```yaml
models:
  - name: landing
    type: Landing
    enable: true
    drivers:
      - model: turing,qa,tree,proxy
        driver: ai.llm.adapter.impl.LandingAdapter
      - model: image
        driver: ai.image.adapter.impl.LandingImageAdapter
        oss: landing
      - model: landing-tts,landing-asr
        driver: ai.audio.adapter.impl.LandingAudioAdapter
      - model: video
        driver: ai.video.adapter.impl.LandingVideoAdapter
    api_key: your-api-key
```

## 3.2 扩展大语言模型适配器

大语言模型适配器实现 `ai.llm.adapter.ILlmAdapter`：

```java
public interface ILlmAdapter {
    ChatCompletionResult completions(ChatCompletionRequest request);
    Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest request);
}
```

最小骨架：

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

现有风格示例：

```java
@LLM(modelNames = {"your_model1", "your_model2"})
public class DoubaoAdapter extends ModelService implements ILlmAdapter {
    @Override
    public ChatCompletionResult completions(ChatCompletionRequest request) {
        ArkService service = ArkService.builder()
                .apiKey(apiKey)
                .baseUrl("https://ark.cn-beijing.volces.com/api/v3/")
                .build();
        // 组装厂商请求，调用远端接口，再转换回 LinkMind 结果。
        return new ChatCompletionResult();
    }

    @Override
    public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest request) {
        return Observable.create(emitter -> {
            // 流式消费厂商返回，再转换成 LinkMind 的 chunk。
            emitter.onComplete();
        });
    }
}
```

## 3.3 扩展语音识别与文字转语音

音频适配器实现 `ai.audio.adapter.IAudioAdapter`：

```java
public interface IAudioAdapter {
    AsrResult asr(File audio, AudioRequestParam param);
    TTSResult tts(TTSRequestParam param);
}
```

语音识别 / TTS 骨架：

```java
@ASR(company = "your-company-name", modelNames = "your-asr-model")
public class YourAudioAdapter extends ModelService implements IAudioAdapter {
    @Override
    public AsrResult asr(File audio, AudioRequestParam param) {
        return null;
    }

    @Override
    public TTSResult tts(TTSRequestParam param) {
        return null;
    }
}
```

ASR 示例片段：

```java
@ASR(company = "alibaba", modelNames = "asr")
public class YourAudioAdapter extends ModelService implements IAudioAdapter {
    @Override
    public AsrResult asr(File audio, AudioRequestParam param) {
        AlibabaAsrService asrService = new AlibabaAsrService(
                getAppKey(),
                getAccessKeyId(),
                getAccessKeySecret()
        );
        return gson.fromJson(asrService.asr(audio), AsrResult.class);
    }

    @Override
    public TTSResult tts(TTSRequestParam param) {
        return null;
    }
}
```

TTS 示例片段：

```java
@TTS(company = "alibaba", modelNames = "tts")
public class YourAudioAdapter extends ModelService implements IAudioAdapter {
    @Override
    public AsrResult asr(File audio, AudioRequestParam param) {
        return null;
    }

    @Override
    public TTSResult tts(TTSRequestParam param) {
        AlibabaTtsService ttsService = new AlibabaTtsService(
                getAppKey(),
                getAccessKeyId(),
                getAccessKeySecret()
        );
        Request request = ttsService.getRequest(param);
        return new TTSResult();
    }
}
```

## 3.4 扩展文生图

图像生成适配器实现 `ai.image.adapter.IImageGenerationAdapter`：

```java
public interface IImageGenerationAdapter {
    ImageGenerationResult generations(ImageGenerationRequest request);
}
```

骨架：

```java
@ImgGen(modelNames = "your-image-model")
public class YourImageAdapter extends ModelService implements IImageGenerationAdapter {
    @Override
    public ImageGenerationResult generations(ImageGenerationRequest request) {
        return null;
    }
}
```

示例片段：

```java
@ImgGen(modelNames = "tti")
public class SparkImageAdapter extends ModelService implements IImageGenerationAdapter {
    @Override
    public ImageGenerationResult generations(ImageGenerationRequest request) {
        String authUrl = getAuthUrl(apiUrl, apiKey, secretKey);
        SparkGenImgRequest sparkRequest = convert2SparkGenImageRequest(request);
        String post = doPostJson(authUrl, null, JSONUtil.toJsonStr(sparkRequest));
        SparkGenImgResponse bean = JSONUtil.toBean(post, SparkGenImgResponse.class);
        return convert2ImageGenerationResult(bean);
    }
}
```

## 3.5 扩展图片转文字

图像理解适配器实现 `ai.image.adapter.IImage2TextAdapter`：

```java
public interface IImage2TextAdapter {
    ImageToTextResponse toText(FileRequest param);
}
```

示例片段：

```java
@Img2Text(modelNames = "Fuyu-8B")
public class YourVisionAdapter extends ModelService implements IImage2TextAdapter {
    @Override
    public ImageToTextResponse toText(FileRequest param) {
        Image2TextRequest request = convertImage2TextRequest(param);
        Image2TextResponse response = buildQianfan().image2Text(request);
        return ImageToTextResponse.success(response.getResult());
    }
}
```

## 3.6 扩展图片增强

图片增强适配器实现 `ai.image.adapter.ImageEnhanceAdapter`：

```java
public interface ImageEnhanceAdapter {
    ImageEnhanceResult enhance(ImageEnhanceRequest imageEnhanceRequest);
}
```

示例片段：

```java
@ImgEnhance(modelNames = "enhance")
public class YourEnhanceAdapter extends ModelService implements ImageEnhanceAdapter {
    @Override
    public ImageEnhanceResult enhance(ImageEnhanceRequest request) {
        String url = "https://aip.baidubce.com/rest/2.0/image-process/v1/image_definition_enhance";
        return ImageEnhanceResult.builder().type("base64").data("...").build();
    }
}
```

## 3.7 扩展文本生成视频

文本生成视频适配器实现 `ai.video.adapter.Text2VideoAdapter`：

```java
public interface Text2VideoAdapter {
    VideoJobResponse toVideo(ImageGenerationRequest request);
}
```

示例片段：

```java
@Text2Video(modelNames = "video")
public class YourVideoAdapter extends ModelService implements Text2VideoAdapter {
    @Override
    public VideoJobResponse toVideo(ImageGenerationRequest request) {
        ImageGenerationResult generations = generations(request);
        if (generations != null) {
            String url = generations.getData().get(0).getUrl();
            return VideoJobResponse.builder().data(url).build();
        }
        return null;
    }
}
```

## 3.8 扩展图片生成视频

图生视频适配器实现 `ai.video.adapter.Image2VideoAdapter`：

```java
public interface Image2VideoAdapter {
    VideoJobResponse image2Video(VideoGeneratorRequest videoGeneratorRequest);
}
```

示例片段：

```java
@Img2Video(modelNames = "video")
public class YourVideoAdapter extends ModelService implements Image2VideoAdapter {
    @Override
    public VideoJobResponse image2Video(VideoGeneratorRequest request) {
        GenerateVideoResponse response = client.generateVideo(convert2GenerateVideoRequest(request));
        return VideoJobResponse.builder().jobId(response.getBody().getRequestId()).build();
    }
}
```

## 3.9 扩展视频追踪

视频追踪适配器实现 `ai.video.adapter.Video2trackAdapter`：

```java
public interface Video2trackAdapter {
    VideoJobResponse track(String videoUrl);
}
```

示例片段：

```java
@VideoTrack(modelNames = "video")
public class YourTrackingAdapter extends ModelService implements Video2trackAdapter {
    @Override
    public VideoJobResponse track(String videoUrl) {
        String url = universalOSS.upload("mmtracking/" + new File(videoUrl).getName(), new File(videoUrl));
        return VideoJobResponse.builder().data(url).build();
    }
}
```

## 3.10 扩展视频增强

视频增强适配器实现 `ai.video.adapter.Video2EnhanceAdapter`：

```java
public interface Video2EnhanceAdapter {
    VideoJobResponse enhance(VideoEnhanceRequest videoEnhanceRequest);
}
```

示例片段：

```java
@VideoEnhance(modelNames = "vision")
public class YourVideoEnhanceAdapter extends ModelService implements Video2EnhanceAdapter {
    @Override
    public VideoJobResponse enhance(VideoEnhanceRequest request) {
        EnhanceVideoQualityResponse response = client.enhanceVideoQuality(
                convert2EnhanceVideoQualityRequest(request)
        );
        return wait2Result(response.getBody().getRequestId());
    }
}
```

## 3.11 设计面向对象的二次开发接口边界

当你为业务场景扩展 LinkMind 时，建议把二次开发边界保持为清晰的面向对象分层，而不是把业务判断散落到主链路里：

- `带外请求数据`：把非标准业务字段放在 `extra_body` 这类类型化请求扩展中，并把编码、解码逻辑集中在工具类里处理。Adapter 只负责读取类型化对象，而不是解析零散业务字符串。
- `Skill 层交互状态`：把社交、公告、流程协同等状态收敛在 Skill、脚本和 Service 辅助类内部，这样可以复用现有工具调用与聊天链路，而不改动基础补全流程。
- `认证、API Key 与计费`：把账号体系、凭证池和收费逻辑放在 `/user/*`、`/apiKey/*`、`/credit/*` 这类 Service 或 Servlet 边界之后。对接企业 SSO、用户中心或计费系统时，优先替换后端实现，保持 HTTP 协议稳定。

这种分层方式能够让现有调用方保持零侵入，也让各个二开模块都可以按需启用。只需要路由、RAG 或 Token 优化的部署，可以完全不打开社交或计费扩展，而不影响标准运行时。

## 4. 扩展向量库

配置键名使用 `stores.vector`。

配置示例：

```yaml
stores:
  vector:
    - name: chroma
      driver: ai.vector.impl.ChromaVectorStore
      default_category: default
      similarity_top_k: 10
      similarity_cutoff: 0.5
      parent_depth: 1
      child_depth: 1
      url: http://127.0.0.1:8000

  rag:
    vector: chroma
    term: elastic
    graph: landing
    enable: true
    priority: 10
    default: "Please give prompt more precisely"
```

自定义向量后端实现 `ai.vector.VectorStore`，或者继承 `ai.vector.impl.BaseVectorStore`。

当前接口包括：

```java
void upsert(List<UpsertRecord> upsertRecords);
void upsert(List<UpsertRecord> upsertRecords, String category);
List<IndexRecord> query(QueryCondition queryCondition);
List<List<IndexRecord>> query(MultiQueryCondition queryCondition);
List<IndexRecord> query(QueryCondition queryCondition, String category);
List<IndexRecord> fetch(List<String> ids);
List<IndexRecord> fetch(List<String> ids, String category);
List<IndexRecord> fetch(Map<String, String> where);
List<IndexRecord> fetch(Map<String, String> where, String category);
void delete(List<String> ids);
void delete(List<String> ids, String category);
void deleteWhere(List<Map<String, String>> whereList);
void deleteWhere(List<Map<String, String>> whereList, String category);
void deleteCollection(String category);
List<VectorCollection> listCollections();
List<IndexRecord> get(GetEmbedding getEmbedding);
void add(AddEmbedding addEmbedding);
void update(UpdateEmbedding updateEmbedding);
void delete(DeleteEmbedding deleteEmbedding);
```

仓库里已经有可参考的实现：

- `ai.vector.impl.ChromaVectorStore`
- `ai.vector.impl.SqliteVectorStore`
- `lagi-extension/src/main/java/ai/vector/impl/MilvusVectorStore.java`
- `lagi-extension/src/main/java/ai/vector/impl/PineconeVectorStore.java`

CRUD 示例片段：

```java
public void upsert(List<UpsertRecord> upsertRecords, String category) {
    List<String> documents = new ArrayList<>();
    List<Map<String, String>> metadatas = new ArrayList<>();
    List<String> ids = new ArrayList<>();
    for (UpsertRecord upsertRecord : upsertRecords) {
        documents.add(upsertRecord.getDocument());
        metadatas.add(upsertRecord.getMetadata());
        ids.add(upsertRecord.getId());
    }
    List<List<Float>> embeddings = this.embeddingFunction.createEmbedding(documents);
    Collection collection = getCollection(category);
    collection.upsert(embeddings, metadatas, documents, ids);
}
```

```java
public List<IndexRecord> query(QueryCondition queryCondition, String category) {
    List<IndexRecord> result = new ArrayList<>();
    Collection collection = getCollection(category);
    Collection.QueryResponse qr = collection.query(
            Collections.singletonList(queryCondition.getText()),
            queryCondition.getN(),
            queryCondition.getWhere(),
            null,
            null
    );
    // 将厂商结果转换成 IndexRecord 列表。
    return result;
}
```

```java
public void deleteWhere(List<Map<String, String>> whereList, String category) {
    Collection collection = getCollection(category);
    for (Map<String, String> where : whereList) {
        collection.deleteWhere(where);
    }
}
```

## 5. 扩展 Agents、Skills、Workers 与 MCP

### Agents

在 `agents.items` 或 `agent.yml` 里新增：

```yaml
agents:
  enable: true
  items:
    - name: your-agent
      driver: ai.agent.customer.YourAgent
      token: your-token
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

当前默认配置里，workers 和 pnps 仍然挂在 `skills` 下：

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

如果你的扩展需要路由规则，请先在 `routers.items` 中定义，再在 worker 配置里引用。

## 6. 运行时生态集成

当前代码还包含这些运行时同步服务：

- OpenClaw
- Hermes Agent
- DeerFlow

它们是运行时生态桥接能力，不替代你自己的 LinkMind YAML 主配置。

## 7. 更稳妥的扩展顺序

对大多数团队来说，最稳妥的顺序是：

1. 先用现成兼容适配器，只做配置扩展。
2. 如果配置不够，再写新的 Java 适配器。
3. 适配器跑通后，再把它接入 `functions.*`。
4. 如果涉及检索，再去扩展 `stores.vector`。

按这个顺序推进，代码、配置和文档都会更容易维护。
