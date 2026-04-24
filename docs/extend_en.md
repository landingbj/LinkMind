# Extension Guide

This guide explains how to extend model adapters, multimodal capabilities, vector stores, and runtime integrations. The safest path is to reuse an existing compatible adapter first, and only write Java code when you truly need a new protocol, modality, or storage backend.

## 1. Decide What Kind of Extension You Need

Use configuration only when:

- your provider is OpenAI-compatible
- your provider is Qwen-compatible
- you only need to add a new model ID, endpoint, or credential set

Write a new adapter when:

- the request or response shape is custom
- the provider needs special signing logic
- you are adding a new modality or storage backend

## 2. Reuse Existing Compatibility Adapters First

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

## 3. Extend Model And Multimodal Adapters

## 3.1 Add Configuration First

The current config shape still centers on `models` and `functions`. A minimal custom provider example looks like this:

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

Use the multi-driver shape when one provider exposes multiple modalities under one logical backend:

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

## 3.2 Extend Large Language Model Adapters

LLM adapters implement `ai.llm.adapter.ILlmAdapter`:

```java
public interface ILlmAdapter {
    ChatCompletionResult completions(ChatCompletionRequest request);
    Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest request);
}
```

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

Existing style example:

```java
@LLM(modelNames = {"your_model1", "your_model2"})
public class DoubaoAdapter extends ModelService implements ILlmAdapter {
    @Override
    public ChatCompletionResult completions(ChatCompletionRequest request) {
        ArkService service = ArkService.builder()
                .apiKey(apiKey)
                .baseUrl("https://ark.cn-beijing.volces.com/api/v3/")
                .build();
        // Build provider request, invoke remote API, then convert back.
        return new ChatCompletionResult();
    }

    @Override
    public Observable<ChatCompletionResult> streamCompletions(ChatCompletionRequest request) {
        return Observable.create(emitter -> {
            // Stream provider chunks and convert them into LinkMind chunks.
            emitter.onComplete();
        });
    }
}
```

## 3.3 Extend Speech-to-Text And Text-to-Speech

Audio adapters implement `ai.audio.adapter.IAudioAdapter`:

```java
public interface IAudioAdapter {
    AsrResult asr(File audio, AudioRequestParam param);
    TTSResult tts(TTSRequestParam param);
}
```

Speech-to-text skeleton:

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

ASR example fragment:

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

TTS example fragment:

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

## 3.4 Extend Text-to-Image

Image generation adapters implement `ai.image.adapter.IImageGenerationAdapter`:

```java
public interface IImageGenerationAdapter {
    ImageGenerationResult generations(ImageGenerationRequest request);
}
```

Skeleton:

```java
@ImgGen(modelNames = "your-image-model")
public class YourImageAdapter extends ModelService implements IImageGenerationAdapter {
    @Override
    public ImageGenerationResult generations(ImageGenerationRequest request) {
        return null;
    }
}
```

Example fragment:

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

## 3.5 Extend Image-to-Text

Image understanding adapters implement `ai.image.adapter.IImage2TextAdapter`:

```java
public interface IImage2TextAdapter {
    ImageToTextResponse toText(FileRequest param);
}
```

Example fragment:

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

## 3.6 Extend Image Enhancement

Image enhancement adapters implement `ai.image.adapter.ImageEnhanceAdapter`:

```java
public interface ImageEnhanceAdapter {
    ImageEnhanceResult enhance(ImageEnhanceRequest imageEnhanceRequest);
}
```

Example fragment:

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

## 3.7 Extend Text-to-Video

Text-to-video adapters implement `ai.video.adapter.Text2VideoAdapter`:

```java
public interface Text2VideoAdapter {
    VideoJobResponse toVideo(ImageGenerationRequest request);
}
```

Example fragment:

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

## 3.8 Extend Image-to-Video

Image-to-video adapters implement `ai.video.adapter.Image2VideoAdapter`:

```java
public interface Image2VideoAdapter {
    VideoJobResponse image2Video(VideoGeneratorRequest videoGeneratorRequest);
}
```

Example fragment:

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

## 3.9 Extend Video Tracking

Video tracking adapters implement `ai.video.adapter.Video2trackAdapter`:

```java
public interface Video2trackAdapter {
    VideoJobResponse track(String videoUrl);
}
```

Example fragment:

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

## 3.10 Extend Video Enhancement

Video enhancement adapters implement `ai.video.adapter.Video2EnhanceAdapter`:

```java
public interface Video2EnhanceAdapter {
    VideoJobResponse enhance(VideoEnhanceRequest videoEnhanceRequest);
}
```

Example fragment:

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

## 4. Extend Vector Stores

Use `stores.vector` as the configuration key.

Configuration example:

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

Custom vector backends implement `ai.vector.VectorStore` or extend `ai.vector.impl.BaseVectorStore`.

The current interface includes:

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

Reference implementations already exist in the repository:

- `ai.vector.impl.ChromaVectorStore`
- `ai.vector.impl.SqliteVectorStore`
- `lagi-extension/src/main/java/ai/vector/impl/MilvusVectorStore.java`
- `lagi-extension/src/main/java/ai/vector/impl/PineconeVectorStore.java`

CRUD example fragments:

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
    // Convert provider response into IndexRecord list.
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

## 5. Extend Agents, Skills, Workers, And MCP

### Agents

Add a new agent in `agents.items` or `agent.yml`:

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

### Skills And Workers

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

## 6. Runtime Ecosystem Integrations

The current codebase also includes sync services for:

- OpenClaw
- Hermes Agent
- DeerFlow

These are runtime integration helpers, not replacements for your LinkMind YAML. Treat them as ecosystem bridges that can import or export configuration where needed.

## 7. Practical Extension Order

For most teams, the safest order is:

1. Add config only with an existing compatible adapter.
2. If that is not enough, add a new Java adapter for the modality you need.
3. Only after the adapter works, expose it through `functions.*`.
4. If retrieval is involved, add or swap the vector backend in `stores.vector`.

Following this order usually keeps both the implementation and the documentation much simpler.
