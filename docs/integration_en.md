# System Integration and Secondary Development

LinkMind can be consumed through HTTP APIs, protocol-compatible clients, or the Java `lagi-core` library. Use one of these boundaries instead of calling a provider directly from every business service.

## 1. HTTP integration

| Requirement | Endpoint |
| --- | --- |
| OpenAI-compatible chat | `POST /v1/chat/completions` |
| OpenAI Responses | `POST /v1/responses` |
| Anthropic-compatible chat | `POST /v1/messages` |
| Embedding | `POST /v1/embeddings` |
| Model list | `GET /v1/models` |
| Image generation | `POST /v1/images/generations` |
| Rerank | `POST /v1/rerank` |

Use a LinkMind API key when authentication is enabled:

```bash
curl http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer <linkmind-api-key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"Alibaba/qwen-plus","messages":[{"role":"user","content":"Summarize this text"}]}'
```

OpenAI clients use a base URL ending in `/v1`. Claude Code uses `ANTHROPIC_BASE_URL` as the service root and appends `/v1/messages` itself. Select the protocol that the client expects; do not translate requests by hand when a compatible route is available.

## 2. Java `lagi-core` integration

Install the local module, or publish it to an internal Maven repository:

```bash
mvn clean install -pl lagi-core -am -DskipTests
```

Then add the matching dependency and provide `lagi.yml` through the classpath or `-Dlinkmind.config=/path/to/lagi.yml`:

```xml
<dependency>
  <groupId>com.landingbj</groupId>
  <artifactId>lagi-core</artifactId>
  <version>1.2.4</version>
</dependency>
```

Use the core library when the business application must embed LinkMind objects and extension points. Use HTTP when services should remain independently deployable. For the current build commands, generated JAR/WAR artifacts, and direct import notes, see [Deployment and Security](deployment-and-security_en.md).

### Java examples

Initialize the context once, then call the relevant service:

```java
import ai.config.ContextLoader;
import ai.llm.service.CompletionsService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatMessage;
import java.util.Collections;

ContextLoader.loadContext();
ChatMessage message = new ChatMessage();
message.setRole("user");
message.setContent("Summarize LinkMind in one sentence.");
ChatCompletionRequest request = new ChatCompletionRequest();
request.setModel("qwen-plus");
request.setStream(false);
request.setMessages(Collections.singletonList(message));
new CompletionsService().completions(request);
```

Attach business context through the typed `extra_body` rather than changing the chat-message schema:

```java
import ai.openai.pojo.ExtraBody;

ExtraBody extraBody = new ExtraBody();
extraBody.setUserId("u_1001");
request.setExtraBody(extraBody);
```

`AudioService` supplies ASR and TTS, while `ImageGenerationService` supplies image generation. Current runnable examples for these and image/video flows are in [`lagi-core/src/test/java/ai/example/Demo.java`](../lagi-core/src/test/java/ai/example/Demo.java).

## 3. Secondary-development boundaries

- OpenAI-compatible chat requests accept optional `extra_body` for typed business metadata.
- Social interactions are exposed under `/socialChannel/*`.
- Account, API-key, and credit operations are exposed under `/user/*`, `/apiKey/*`, and `/credit/*`.

Keep business metadata, social state, authentication, and charging outside the core model-adapter path. This prevents custom application behavior from altering normal completion, streaming, routing, or cascade behavior.

### Python and Go examples

```python
import requests

response = requests.post(
    "http://localhost:8080/v1/chat/completions",
    headers={"Content-Type": "application/json"},
    json={"model": "qwen-plus", "messages": [{"role": "user", "content": "Hello"}]},
    timeout=60,
)
response.raise_for_status()
print(response.json())
```

```go
package main

import (
    "bytes"
    "net/http"
)

func main() {
    body := []byte(`{"model":"qwen-plus","messages":[{"role":"user","content":"Hello"}]}`)
    request, _ := http.NewRequest("POST", "http://localhost:8080/v1/chat/completions", bytes.NewBuffer(body))
    request.Header.Set("Content-Type", "application/json")
    response, err := http.DefaultClient.Do(request)
    _ = response
    _ = err
}
```

## 4. Validate an integration

1. Call a short text request and retain the request time and model.
2. Confirm the response, LinkMind runtime record, and expected route.
3. Add streaming, tools, files, or custom metadata one capability at a time.
4. For a production client, verify error handling, timeouts, authentication, and billing behavior.

See [Models, Routing, and Multimodal](models-and-multimodal_en.md) for chat, Responses, Messages, embedding, and multimodal request fields; see [Knowledge Base](knowledge-base_en.md) for vectors and ingestion. Use [Extensions](extensions_en.md) when a standard API or compatible adapter is insufficient.
