# Integration Development Guide

This guide is for developers who want to integrate LinkMind into their own applications. If you only need to run the web console or call the HTTP APIs manually, read the [Installation Guide](install_en.md) and [API Reference](API_en.md) first.

## 1. Choose an Integration Mode

LinkMind supports three practical integration styles:

| Mode | Use when |
| --- | --- |
| REST API | Your application can call an external service and you want the lowest integration cost |
| Docker service | You want a clean shared runtime for local teams, CI, or mixed-language projects |
| `lagi-core` | Your project is Java-based and you want to call LinkMind services directly inside your own code |

If you are evaluating quickly, start with REST or Docker. Move to `lagi-core` only when you specifically need in-process Java integration.

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
  <version>1.2.4</version>
</dependency>
```

## 4. Provide a Configuration File

LinkMind loads `lagi.yml` from one of these locations:

- a classpath resource named `lagi.yml`
- an explicit system property: `-Dlinkmind.config=/path/to/lagi.yml`
- the repository resource defaults when you run directly inside the source tree

For application integration, the simplest choices are:

- put `lagi.yml` on your runtime classpath
- or set `-Dlinkmind.config` and keep the file outside your package

Use the [Configuration Reference](config_en.md) to enable at least one model and one chat backend before calling services.

## 5. Common Java Service Calls

Before you call service classes, initialize the context once:

```java
import ai.config.ContextLoader;

ContextLoader.loadContext();
```

Complete runnable samples are already in:

- [`lagi-core/src/test/java/ai/example/Demo.java`](../lagi-core/src/test/java/ai/example/Demo.java)

### Text Chat

```java
import ai.llm.service.CompletionsService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
import ai.openai.pojo.ChatMessage;

import java.util.Collections;

ContextLoader.loadContext();

CompletionsService service = new CompletionsService();

ChatMessage message = new ChatMessage();
message.setRole("user");
message.setContent("Summarize LinkMind in one sentence.");

ChatCompletionRequest request = new ChatCompletionRequest();
request.setModel("qwen-plus");
request.setStream(false);
request.setMessages(Collections.singletonList(message));

ChatCompletionResult result = service.completions(request);
String answer = result.getChoices().get(0).getMessage().getContent();
```

### Carry Secondary-Development Context Only When You Need It

Secondary-development metadata is intentionally isolated from the standard chat surface. When your business flow needs caller identity or other out-of-band data, attach it through `extra_body` instead of changing the message schema:

```java
import ai.openai.pojo.ExtraBody;

ExtraBody extraBody = new ExtraBody();
extraBody.setUserId("u_1001");
request.setExtraBody(extraBody);
```

This keeps the request object OpenAI-compatible while giving skills, social features, and other server-side extensions a typed place to read business context. If you run LinkMind through `LandingAdapter` in `Agent Mate` mode, the current login user can also be injected automatically, so existing call sites do not need invasive rewrites.

### Speech Recognition

```java
import ai.audio.service.AudioService;
import ai.common.pojo.AsrResult;
import ai.common.pojo.AudioRequestParam;

ContextLoader.loadContext();

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

ContextLoader.loadContext();

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

ContextLoader.loadContext();

ImageGenerationService service = new ImageGenerationService();
ImageGenerationRequest request = new ImageGenerationRequest();
request.setPrompt("A futuristic airport assistant robot");

ImageGenerationResult result = service.generations(request);
```

### More Java Examples Still Available

The current codebase also includes Java samples for:

- image-to-text
- image enhancement
- image-to-video
- video tracking
- video enhancement

See [`lagi-core/src/test/java/ai/example/Demo.java`](../lagi-core/src/test/java/ai/example/Demo.java) for the latest runnable shapes.

## 6. Integrate Through HTTP / REST

If your application is not Java, the cleanest path is to run LinkMind as a service and call its HTTP endpoints.

### Common Routes

- Native routes such as `/chat/completions`, `/audio/speech2text`, `/audio/text2speech`, `/image/text2image`, `/sql/text2sql`, `/instruction/generate`, `/doc/doc2ext`, and `/ocr/doc2ocr`
- OpenAI-compatible routes such as `/v1/chat/completions`, `/v1/models`, `/v1/embeddings`, `/v1/images/generations`, and `/v1/rerank`

Service root example:

- `http://localhost:8080`

### cURL Example

```bash
curl http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "qwen-plus",
    "stream": false,
    "messages": [
      {"role": "user", "content": "Summarize LinkMind in one sentence."}
    ]
  }'
```

### Python Example

```python
import requests

resp = requests.post(
    "http://localhost:8080/v1/chat/completions",
    headers={"Content-Type": "application/json"},
    json={
        "model": "qwen-plus",
        "stream": False,
        "messages": [
            {"role": "user", "content": "Summarize LinkMind in one sentence."}
        ],
    },
    timeout=60,
)

resp.raise_for_status()
print(resp.json())
```

### Go Example

```go
package main

import (
	"bytes"
	"fmt"
	"io"
	"net/http"
)

func main() {
	body := []byte(`{
	  "model": "qwen-plus",
	  "stream": false,
	  "messages": [
	    {"role": "user", "content": "Summarize LinkMind in one sentence."}
	  ]
	}`)

	req, _ := http.NewRequest("POST", "http://localhost:8080/v1/chat/completions", bytes.NewBuffer(body))
	req.Header.Set("Content-Type", "application/json")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		panic(err)
	}
	defer resp.Body.Close()

	data, _ := io.ReadAll(resp.Body)
	fmt.Println(string(data))
}
```

If LinkMind auth is enabled, send:

```http
Authorization: Bearer <your-linkmind-api-key>
```

For route-by-route request details, use the [API Reference](API_en.md).

### Secondary-Development Interface Groups

LinkMind exposes secondary-development contracts as separate interface groups, so you can adopt only the layers your project actually needs:

- `Out-of-band request context`: `POST /chat/completions` and `POST /v1/chat/completions` accept `extra_body`, which is the preferred contract for user identity and other business-side metadata.
- `Social skill and channel APIs`: `/socialChannel/*` provides channel registration, subscription, message listing, and message publishing. Skill-side social features consume these routes instead of writing directly into the chat adapter path.
- `User, API-key, and billing APIs`: `/user/*`, `/apiKey/*`, and `/credit/*` separate account, credential-pool, and charging workflows from model routing. This is the safest replacement point when you need to plug LinkMind into an existing SSO, user center, or charging platform.

Because these interfaces are separated by responsibility, most teams can keep the current chat and multimodal flows unchanged and only swap the business module they actually own.

## 7. Integrate Through Docker

Docker is useful when multiple applications in different languages all need the same LinkMind runtime.

### Start the Official Image

```bash
docker pull landingbj/linkmind
docker run -d --name linkmind -p 8080:8080 landingbj/linkmind
```

### Typical Integration Pattern

1. Run LinkMind in Docker as the shared AI middleware service.
2. Point your Python, Go, Java, Node.js, or other application to `http://localhost:8080`.
3. Call either the native LinkMind routes or the OpenAI-compatible `/v1/...` routes.
4. Keep your business application logic separate from model-provider details.

This pattern is especially useful for local development, internal demos, CI smoke tests, and multi-language teams.

## 8. What to Read Next

- [Configuration Reference](config_en.md)
- [API Reference](API_en.md)
- [Tutorial](tutor_en.md)
- [Extension Guide](extend_en.md)
