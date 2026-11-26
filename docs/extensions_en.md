# More Integrations and Custom Extensions

Reuse configuration and existing compatible adapters first. Add Java only when the existing surface cannot express a provider, modality, store, or runtime behavior. This page defines extension boundaries, minimal configuration, implementation entry points, and verification order.

[Choose an extension](#extension-choice) · [Driver configuration](#custom-model-driver) · [LLMs](#adapter-6) · [Audio](#adapter-7) · [Images](#adapter-8) · [Video](#adapter-11) · [Vector stores](#vector-store-spi)

<a id="extension-choice"></a>

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

Implement an adapter when a provider requires custom request signing or its payload cannot be mapped by a compatible adapter. Add model IDs, endpoints, or credentials in configuration.

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

<a id="custom-model-driver"></a>

### Register a custom driver or multiple modality drivers

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

## 3. Implement a new adapter or store

An LLM adapter implements `ai.llm.adapter.ILlmAdapter`. Audio, image, video, and document capabilities implement their corresponding modality interfaces. A custom retrieval backend implements `ai.vector.VectorStore` or extends `ai.vector.impl.BaseVectorStore`, then registers in `stores.vector`.

Follow three boundaries:

1. Keep provider request/response transformation inside the adapter, not in every business caller.
2. Carry business data through typed fields such as `extra_body`; do not change standard chat-message structure.
3. Keep social and workflow state in Skills, Workers, and supporting Services so it cannot block standard completion, routing, or streaming.

For a vector store, validate connection/create, write, category query, update, delete, metadata filtering, and concurrency. The RAG call path is documented in [Knowledge Base](knowledge-base_en.md).

The following Java excerpts show the interfaces, annotations, and parameter mappings. Supply the actual provider SDK calls, result conversions, and error handling where the snippets use placeholders.

<a id="adapter-6"></a>

### Extend Large Language Model Adapters

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

The example below uses ArkService directly and shows where to convert responses and streamed events. The bundled [Doubao adapter](../lagi-core/src/main/java/ai/llm/adapter/impl/DoubaoAdapter.java) reuses OpenAIStandardAdapter.

```java
@LLM(modelNames = {"your_model1", "your_model2"})
public class CustomArkAdapter extends ModelService implements ILlmAdapter {
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

<a id="adapter-7"></a>

### Extend Speech-to-Text And Text-to-Speech

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

<a id="adapter-8"></a>

### Extend Text-to-Image

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

<a id="adapter-9"></a>

### Extend Image-to-Text

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

<a id="adapter-10"></a>

### Extend Image Enhancement

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

<a id="adapter-11"></a>

### Extend Text-to-Video

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

<a id="adapter-12"></a>

### Extend Image-to-Video

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

<a id="adapter-13"></a>

### Extend Video Tracking

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

<a id="adapter-14"></a>

### Extend Video Enhancement

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

<a id="adapter-business-context"></a>

### Keep business state outside the adapter

Decode `extra_body` into a typed request at the integration boundary. Keep channel, announcement, and task state in Skills, scripts, or services. The model adapter converts provider protocols. Applications can integrate channels, provider key pools, orders, and balances independently of model execution. See [secondary-development APIs](integration_en.md#secondary-services).

<a id="vector-store-spi"></a>

### Vector store interface, configuration, and CRUD examples

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
List<IndexRecord> fetch(int limit, int offset, String category);
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
    try {
        collection.upsert(embeddings, metadatas, documents, ids);
    } catch (ApiException e) {
        throw new RuntimeException(e);
    }
}
```

```java
public List<List<IndexRecord>> query(MultiQueryCondition queryCondition) {
    List<List<IndexRecord>> resultList = new ArrayList<>();
    String category = queryCondition.getCategory() != null ? queryCondition.getCategory() : this.config.getDefaultCategory();
    Collection collection = getCollection(category);
    if (queryCondition.getTexts() == null || queryCondition.getTexts().isEmpty()) {
        GetEmbedding getEmbedding = GetEmbedding.builder()
                .category(category)
                .where(queryCondition.getWhere())
                .limit(queryCondition.getN())
                .offset(0)
                .whereDocument(queryCondition.getWhereDocument())
                .build();
        resultList.add(get(getEmbedding));
        return resultList;
    }

    List<List<Float>> queryEmbeddings = this.embeddingFunction.createEmbedding(queryCondition.getTexts());
    ChromaQueryRequest queryEmbedding = ChromaQueryRequest.builder()
            .where(queryCondition.getWhere())
            .whereDocument(queryCondition.getWhereDocument())
            .queryEmbeddings(queryEmbeddings)
            .nResults(queryCondition.getN())
            .build();

    String url = this.config.getUrl() + "/api/v1/collections/" + collection.getId() + "/query";
    Collection.QueryResponse qr;
    try {
        String json = OkHttpUtil.post(url, objectMapper.writeValueAsString(queryEmbedding));
        qr = gson.fromJson(json, Collection.QueryResponse.class);
    } catch (IOException e) {
        throw new RuntimeException(e);
    }

    if (qr == null) {
        return resultList;
    }
    for (int i = 0; i < qr.getDocuments().size(); i++) {
        List<IndexRecord> result = new ArrayList<>();
        for (int j = 0; j < qr.getDocuments().get(i).size(); j++) {
            IndexRecord indexRecord = IndexRecord.builder()
                    .document(qr.getDocuments().get(i).get(j))
                    .id(qr.getIds().get(i).get(j))
                    .metadata(qr.getMetadatas().get(i).get(j))
                    .distance(qr.getDistances().get(i).get(j))
                    .build();
            result.add(indexRecord);
        }
        resultList.add(result);
    }
    return resultList;
}
```

```java
public void deleteWhere(List<Map<String, String>> whereList, String category) {
    Collection collection = getCollection(category);
    try {
        for (Map<String, String> where : whereList) {
            collection.deleteWhere(where);
        }
    } catch (ApiException e) {
        throw new RuntimeException(e);
    }
}
```

For a single query, call `query(QueryCondition, String category)`. Batch queries return one group of `IndexRecord` per input, with IDs, documents, metadata and distances. Test a write, query, and conditional deletion in one category, then check isolation with a second category. [Chroma implementation](../lagi-core/src/main/java/ai/vector/impl/ChromaVectorStore.java)

In a custom backend, delegate a single query to the batch implementation and handle an empty result:

```java
public List<IndexRecord> query(QueryCondition condition, String category) {
    MultiQueryCondition batch = MultiQueryCondition.builder()
            .category(category)
            .texts(Collections.singletonList(condition.getText()))
            .where(condition.getWhere())
            .whereDocument(condition.getWhereDocument())
            .n(condition.getN())
            .build();
    List<List<IndexRecord>> groups = query(batch);
    return groups.isEmpty() ? Collections.emptyList() : groups.get(0);
}
```

## 4. Extend the runtime and test in order

Register an Agent in `agents.items`, an MCP server in `mcps.servers`, and a Skill, Worker, or PnP under `skills`. Use `routers.items` for shared selection and `filters.items` for content governance. The unified call path and request fields are in [Agents, Tools, and Task Orchestration](agents-and-tools_en.md).

Recommended order: reuse a compatible adapter and validate configuration -> build the smallest adapter or store -> expose it through the relevant `functions.*` block -> test non-stream, stream, errors, timeouts, concurrency, and authentication separately -> then enable routing, billing, tools, and production traffic.

Every extension should include an example without real keys, one runnable verification request, expected output or log signature, failure checks, and unit or integration tests. Never put real provider keys, customer documents, or production URLs in examples.
