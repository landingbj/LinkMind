# Integration Development Guide

This guide is for developers who want to integrate LinkMind into their own applications. If you only need to run the web console or call the HTTP APIs, read the [Installation Guide](install_en.md) and [API Reference](API_en.md) first.

## 1. Choose an Integration Mode

LinkMind supports two common integration styles:

| Mode | Use when |
| --- | --- |
| REST API | Your application can call an external service and you want the lowest integration cost |
| `lagi-core` | Your project is Java-based and you want to call LinkMind services directly inside your own code |

If you are evaluating quickly, start with the REST API. Move to `lagi-core` only when you specifically need in-process Java integration.

## 2. Prepare `lagi-core`

### Option A: Install Locally from This Repository

From the repository root, run:

```bash
mvn clean install -pl lagi-core -am -DskipTests
```

This installs `lagi-core` and the internal supporting artifacts that the project expects into your local Maven repository.

### Option B: Publish to Your Own Artifact Repository

If your team already uses Nexus, Artifactory, or another internal Maven registry, publish `lagi-core` there after the same build.

## 3. Add the Java Dependency

After `mvn install` or an internal publish, add the current module version:

```xml
<dependency>
  <groupId>com.landingbj</groupId>
  <artifactId>lagi-core</artifactId>
  <version>1.2.3</version>
</dependency>
```

## 4. Provide a Configuration File

LinkMind loads `lagi.yml` from one of these locations:

- A classpath resource named `lagi.yml`
- An explicit system property: `-Dlinkmind.config=/path/to/lagi.yml`
- The repository resource defaults when you run directly inside the source tree

For application integration, the simplest choices are:

- Put `lagi.yml` on your runtime classpath
- Or set `-Dlinkmind.config` and keep the file outside your package

Use the [Configuration Reference](config_en.md) to enable at least one model and one chat backend before calling services.

## 5. Common Java Service Calls

Complete runnable samples are already in:

- [`lagi-core/src/test/java/ai/example/Demo.java`](../lagi-core/src/test/java/ai/example/Demo.java)

### Text Chat

```java
import ai.llm.service.CompletionsService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;

import java.util.Collections;

CompletionsService service = new CompletionsService();

ChatCompletionRequest request = new ChatCompletionRequest();
request.setModel("qwen-plus");
request.setStream(false);
request.setMessages(Collections.singletonList(
        new ChatMessage("user", "Summarize LinkMind in one sentence.")
));

ChatCompletionResult result = service.completions(request);
String answer = result.getChoices().get(0).getMessage().getContent();
```

### Speech Recognition

```java
import ai.audio.service.AudioService;
import ai.common.pojo.AsrResult;
import ai.common.pojo.AudioRequestParam;

AudioService service = new AudioService();
AudioRequestParam param = new AudioRequestParam();
param.setFormat("wav");

AsrResult result = service.asr("D:/audio/demo.wav", param);
```

### Text to Speech

```java
import ai.audio.service.AudioService;
import ai.common.pojo.TTSRequestParam;
import ai.common.pojo.TTSResult;

AudioService service = new AudioService();
TTSRequestParam request = new TTSRequestParam();
request.setText("Hello from LinkMind.");

TTSResult result = service.tts(request);
```

### Image Generation

```java
import ai.common.pojo.ImageGenerationRequest;
import ai.common.pojo.ImageGenerationResult;
import ai.image.service.ImageGenerationService;

ImageGenerationService service = new ImageGenerationService();
ImageGenerationRequest request = new ImageGenerationRequest();
request.setPrompt("A futuristic airport assistant robot");

ImageGenerationResult result = service.generations(request);
```

## 6. Integrate Through HTTP Instead

If your application is not Java, run LinkMind as a service and call:

- Native routes such as `/chat/completions`, `/audio/speech2text`, `/audio/text2speech`, `/image/text2image`, `/sql/text2sql`, `/instruction/generate`, `/doc/doc2ext`, and `/ocr/doc2ocr`
- OpenAI-compatible routes such as `/v1/chat/completions`, `/v1/models`, `/v1/embeddings`, `/v1/images/generations`, and `/v1/rerank`

Service root example:

- `http://localhost:8080`

If LinkMind auth is enabled, send:

```http
Authorization: Bearer <your-linkmind-api-key>
```

## 7. What to Read Next

- [Configuration Reference](config_en.md)
- [API Reference](API_en.md)
- [Tutorial](tutor_en.md)
- [Extension Guide](extend_en.md)
