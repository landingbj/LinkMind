# 更多功能接入与自定义扩展

优先复用配置与现有兼容适配器；只有现有能力不能表达 Provider、模态、存储或运行时行为时，才新增 Java 代码。本页给出扩展边界、最小配置、实现入口和验证顺序。

[选择扩展方式](#extension-choice) · [驱动配置](#custom-model-driver) · [语言模型](#adapter-6) · [音频](#adapter-7) · [图片](#adapter-8) · [视频](#adapter-11) · [向量库](#vector-store-spi)

<a id="extension-choice"></a>

## 一、先选择正确的扩展边界

| 需求 | 优先做法 |
| --- | --- |
| 新模型 ID、Key 池、上游地址或路由 | 修改 `models` 与 `functions.*` |
| OpenAI、Qwen 等兼容 Provider | 复用现有兼容适配器 |
| 新厂商请求/响应协议 | 实现 LLM 适配器 |
| 新语音、图片、视频或文档能力 | 实现相应模态适配器 |
| 新检索后端 | 实现 `VectorStore` 或继承 `BaseVectorStore` |
| 新工具或任务行为 | 新增 Agent、MCP、Skill 或 Worker |

调用方保持 LinkMind 的稳定地址与协议；厂商差异、鉴权和字段转换留在适配器与配置中。

厂商要求特殊签名，或请求和响应字段无法由兼容适配器转换时，需要编写适配器。新增模型 ID、端点或凭证可直接改配置。

## 二、接入兼容模型 Provider 并验证

OpenAI 兼容服务通常无需编写新代码：

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

重启后先调 `GET /v1/models`，再以 `POST /v1/chat/completions` 发一条非流式短消息，最后用 `stream:true` 验证 SSE 包与 `[DONE]`。不要在未验证上游错误结构、超时、工具调用和流式结束行为前接入生产流量。

<a id="custom-model-driver"></a>

### 注册自定义驱动与多模态驱动

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

## 三、实现新适配器或存储

大语言模型适配器实现 `ai.llm.adapter.ILlmAdapter`；音频、图片、视频和文档能力实现对应模态接口；自定义检索后端实现 `ai.vector.VectorStore` 或继承 `ai.vector.impl.BaseVectorStore`，随后注册到 `stores.vector`。

实现时应遵循三个边界：

1. 厂商请求与响应转换只放在适配器内，避免泄漏到业务调用方。
2. 业务数据通过 `extra_body` 等类型化字段传递，不改变标准聊天消息结构。
3. 社交或流程状态放入 Skill、Worker 和 Service 辅助层，不阻塞标准补全、路由和流式链路。

向量库至少验证建库/连接、写入、按 `category` 查询、更新、删除、元数据过滤和并发边界。具体 RAG 调用路径见[知识库](knowledge-base_zh.md)。

以下 Java 片段展示接口、注解和参数映射。实现类中的省略号或占位返回值需要换成实际厂商 SDK 调用、结果转换与错误处理。

<a id="adapter-6"></a>

### 扩展大语言模型适配器

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

下例展示直接使用 ArkService 时，如何将普通结果和流式事件映射回 LinkMind。当前内建的[豆包适配器](../lagi-core/src/main/java/ai/llm/adapter/impl/DoubaoAdapter.java)复用 OpenAIStandardAdapter。

```java
@LLM(modelNames = {"your_model1", "your_model2"})
public class CustomArkAdapter extends ModelService implements ILlmAdapter {
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

<a id="adapter-7"></a>

### 扩展语音识别与文字转语音

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

<a id="adapter-8"></a>

### 扩展文生图

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

<a id="adapter-9"></a>

### 扩展图片转文字

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

<a id="adapter-10"></a>

### 扩展图片增强

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

<a id="adapter-11"></a>

### 扩展文本生成视频

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

<a id="adapter-12"></a>

### 扩展图片生成视频

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

<a id="adapter-13"></a>

### 扩展视频追踪

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

<a id="adapter-14"></a>

### 扩展视频增强

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

<a id="adapter-business-context"></a>

### 将业务状态留在适配器之外

在接入层解码 `extra_body` 并传入类型化对象。由 Skill、脚本和 Service 保存频道、公告或任务状态。模型适配器负责厂商协议转换。应用可独立接入社交频道、Provider 密钥池、订单和余额服务，接口调用与模型执行分别验证。见[二次开发接口](integration_zh.md#secondary-services)。

<a id="vector-store-spi"></a>

### 向量库接口、配置与读写示例

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

单条查询可用 `query(QueryCondition, String category)`。批量实现为每个输入返回一组 `IndexRecord`，同时保留 ID、原文、元数据与距离。用同一分类执行写入、检索和条件删除，再用另一分类验证隔离。[Chroma 实现](../lagi-core/src/main/java/ai/vector/impl/ChromaVectorStore.java)

自定义后端可将单条查询委托给批量实现，并处理空结果：

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

## 四、扩展运行时与测试顺序

在 `agents.items` 注册 Agent，在 `mcps.servers` 注册 MCP 服务，在 `skills` 下注册 Skill、Worker 或 PnP；共享选择策略使用 `routers.items`，内容治理使用 `filters.items`。统一调用入口和请求字段见[智能体接入、工具与任务编排](agents-and-tools_zh.md)。

推荐顺序是：复用兼容适配器并做配置验证 -> 编写最小适配器或存储实现 -> 对外启用对应 `functions.*` -> 分别测试非流式、流式、错误、超时、并发和鉴权 -> 最后启用路由、计费、工具和生产流量。

每个扩展应同时提供：无真实 Key 的示例配置、一条可执行验证请求、预期输出或日志特征、失败时的排查点，以及单元或集成测试。不要把真实 Provider Key、客户文档或生产 URL 写进示例。
