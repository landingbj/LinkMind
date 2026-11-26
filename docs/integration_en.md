# System Integration and Secondary Development

LinkMind can be consumed through HTTP APIs, protocol-compatible clients, or the Java `lagi-core` library. Use one of these boundaries instead of calling a provider directly from every business service.

[Java configuration](#sdk-config) · [Java media examples](#sdk-media) · [Python and Go](#http-clients) · [Business context](#typed-business-context) · [Channels](#social-channel-api) · [Key management](#provider-keys) · [Billing](#orders-and-balances)

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

<a id="sdk-config"></a>

### How an embedded application finds its configuration

`ContextLoader.loadContext()` first tries the file selected by `-Dlinkmind.config=/path/to/lagi.yml`. If no configuration was loaded it tries `lagi.yml` at the classpath root, then `lagi-web/src/main/resources/lagi.yml` and `../lagi-web/src/main/resources/lagi.yml` relative to the working directory. The last two paths support source checkouts. An embedded application can package the resource under `src/main/resources` or supply an external file to the JVM. Enable at least one model and chat backend.

<a id="sdk-chat"></a>

### Java examples

Initialize the context once, then call the relevant service:

```java
import ai.config.ContextLoader;
import ai.llm.service.CompletionsService;
import ai.openai.pojo.ChatCompletionRequest;
import ai.openai.pojo.ChatCompletionResult;
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
ChatCompletionResult result = new CompletionsService().completions(request);
if (result != null && result.getChoices() != null && !result.getChoices().isEmpty()) {
    System.out.println(result.getChoices().get(0).getMessage().getContent());
}
```

Attach business context through the typed `extra_body` rather than changing the chat-message schema:

```java
import ai.openai.pojo.ExtraBody;

ExtraBody extraBody = new ExtraBody();
extraBody.setUserId("u_1001");
request.setExtraBody(extraBody);
```

`AudioService` supplies ASR and TTS, while `ImageGenerationService` supplies image generation. Current runnable examples for these and image/video flows are in [`lagi-core/src/test/java/ai/example/Demo.java`](../lagi-core/src/test/java/ai/example/Demo.java).

<a id="sdk-media"></a>

### Call audio, image, and video services in Java

#### Speech Recognition

```java
import ai.config.ContextLoader;
import ai.audio.service.AudioService;
import ai.common.pojo.AsrResult;
import ai.common.pojo.AudioRequestParam;

ContextLoader.loadContext();

AudioService service = new AudioService();
AudioRequestParam param = new AudioRequestParam();
param.setFormat("wav");

AsrResult result = service.asr("D:/audio/demo.wav", param);
```

#### Text to Speech

```java
import ai.config.ContextLoader;
import ai.audio.service.AudioService;
import ai.common.pojo.TTSRequestParam;
import ai.common.pojo.TTSResult;

ContextLoader.loadContext();

AudioService service = new AudioService();
TTSRequestParam request = new TTSRequestParam();
request.setText("Hello from LinkMind.");

TTSResult result = service.tts(request);
```

#### Image Generation

```java
import ai.config.ContextLoader;
import ai.common.pojo.ImageGenerationRequest;
import ai.common.pojo.ImageGenerationResult;
import ai.image.service.ImageGenerationService;

ContextLoader.loadContext();

ImageGenerationService service = new ImageGenerationService();
ImageGenerationRequest request = new ImageGenerationRequest();
request.setPrompt("A futuristic airport assistant robot");

ImageGenerationResult result = service.generations(request);
```

#### More Java Examples Still Available

The current codebase also includes Java samples for:

- image-to-text
- image enhancement
- image-to-video
- video tracking
- video enhancement

See [`lagi-core/src/test/java/ai/example/Demo.java`](../lagi-core/src/test/java/ai/example/Demo.java) for the latest runnable shapes.

<a id="secondary-services"></a>

## 3. Secondary-development boundaries

- OpenAI-compatible chat requests accept optional `extra_body` for typed business metadata.
- Social interactions are exposed under `/socialChannel/*`. [Social channel API reference](#social-channel-apis)
- API key management uses `/apiKey/*`. Credit operations use `/credit/*`.

Keep business metadata, social state, authentication, and charging outside the core model-adapter path. This prevents custom application behavior from altering normal completion, streaming, routing, or cascade behavior.

Applications can integrate [social channels](#social-channel-api), [provider key management](#provider-keys), and [orders and balances](#orders-and-balances) independently. Skills subscribe, read, and publish through the channel service. Model adapters handle provider protocol conversion.

<a id="typed-business-context"></a>

### Runtime propagation of business context

HTTP callers should send an `extra_body` object in the request JSON. The complete request, `user_id`/`user` aliases, and field definitions are documented in [Models, Routing, and Multimodal](models-and-multimodal_en.md#extra_body-business-context). Java callers continue to use the `ExtraBody` example above: set the user identifier with `setUserId(...)`, then call `request.setExtraBody(...)`. This carries out-of-band business context without requiring callers to redesign the chat-message structure.

#### Internal skill payload

When the runtime injects skills, it may serialize the typed object into an internal `<extra_body>...</extra_body>` block so business context travels with tool execution. The current skill-injection flow requires `skills.enable`, loaded and available server skills, and a system message. Calls that do not enter this flow are not guaranteed to produce such a block.

The current implementation checks the system content for an existing parseable internal payload. Only when none is found does it append a serialization of the request's existing `extra_body`. A `null` object produces no block, and an existing parseable block is not overwritten from the request at this step. Internal serialization uses object property names such as `userId`; these need not match the caller's HTTP alias spelling exactly.

Clients still send normal JSON `extra_body`. They do not need to construct these tags or insert business identifiers into system prompts. The block is an internal runtime transport mechanism, not a separate public request format. See [Agents, Tools, and Task Orchestration](agents-and-tools_en.md) for skill enablement and configuration.

#### Populating the most recent user in cascade mode

The previous documentation described populating the most recently logged-in user when `general.cascade_api_address` is enabled. The current implementation lives in `LandingAdapter` default-field processing and requires all of these conditions:

1. The call actually reaches `LandingAdapter.setDefaultField`; this behavior does not apply to every model adapter or every skill-execution path.
2. The entire request `ExtraBody` object is `null`, including an omitted field or explicit `null`. A missing user field inside an existing object is insufficient.
3. Explicitly configured `general.cascade_api_address` is nonempty after normalization, which is how the runtime determines that cascade proxying is enabled. See [Deployment, Security, and Cascades](deployment-and-security_en.md) for the configuration meaning.
4. A nonempty most-recent-user identifier can be read. The current implementation reads the value for key `last_login_user_id` from `social_temp` in the `saas` data source and trims whitespace. A missing record, empty value, or read error produces no populated object.

When all conditions hold, the new object's `userId` comes from that record, and `mateUrl` comes from the local runtime base URL returned by `ConfigUtil.getBaseUrl()`. It does not directly copy `general.cascade_api_address` into `mateUrl`; reachability of the local address depends on deployment configuration.

| Request and runtime state | Behavior of this population step |
| --- | --- |
| A non-null object with a user identifier is supplied | Preserves the request object rather than replacing it with the most recent user |
| `{}` or an object containing only other fields such as `mate_url` is supplied | The object already exists, so the missing user field is not populated |
| The object is `null` and cascade proxying is disabled | Does not look up the most recent user for this fallback |
| The object is `null`, cascade proxying is enabled, and the user record is unavailable | The result remains `null` |
| The object is `null`, cascade proxying is enabled, and the user record is valid | Creates an object with the most recent user and the local base URL |

This is a local fallback on a specific adapter path. The user comes from a locally stored record and does not establish authentication of the current HTTP request. An explicit business-user identifier also does not replace an API key or login state. Integrations that need a definite caller identity should supply the corresponding user context and authenticate as required by the deployment.

To investigate missing context, first inspect the actual HTTP request or Java object, then check the adapter path, skill configuration, cascade configuration, and most-recent-user record. Enabling cascade mode or supplying an empty object alone does not establish that every later tool call has received a user identity.

<a id="service-responses"></a>

### Response conventions for secondary-development endpoints

The previous documentation defined the general JSON envelope for secondary development as `{"status":"success|failed","msg":"...","data":...}`. This is a structural illustration: `success|failed` stands for two possible business states, and `...` stands for the actual message or data; these placeholders are not literal response values. For business endpoints using this convention, apply the meanings below unless the endpoint specifies otherwise. Field presence and the actual data schema remain defined by each endpoint.

#### Fields and examples

| Field | Type | Meaning and availability |
| --- | --- | --- |
| `status` | string | Endpoints using this envelope normally use `success` for their defined successful or accepted state and `failed` for business failure. Whether background work has finished depends on the endpoint contract |
| `msg` | string | Optional information or error message. It may appear in either successful or failed responses, or be omitted. Its presence or a particular message text alone does not establish the outcome |
| `data` | JSON value defined by the endpoint | Optional business result, which may be an object, array, string, or another JSON value. Do not assume it is always an object or that every endpoint returns it |

These success and failure examples illustrate the general envelope:

```json
{
  "status": "success",
  "msg": "ok",
  "data": []
}
```

```json
{
  "status": "failed",
  "msg": "Unable to complete the request."
}
```

Existing endpoints may also return only `{"status":"success"}` or `{"status":"failed"}`, or add top-level fields such as `task_id`, `progress`, `files`, and pagination totals. Read these according to the endpoint's documentation; neither `msg` nor `data` is universally required. Parse `data` a second time only when the endpoint declares it to be a JSON string; an object or array returned directly needs no second parse. See the [knowledge-base endpoints](knowledge-base_en.md) for concrete examples.

#### Scope and client handling

| Response type | Parsing convention |
| --- | --- |
| Secondary-development business endpoints using this envelope | Check the HTTP result, business `status`, and the endpoint's data fields separately. A business failure can still use HTTP **200** |
| OpenAI-compatible Chat Completions and Responses, and Anthropic Messages | Use each protocol's JSON structure and error format without adding an outer `status/msg/data` envelope. Interpret any same-named fields inside the protocol according to that protocol |
| SSE streaming responses | Parse event types and event data according to the endpoint; do not treat the entire stream as one ordinary JSON envelope |
| Binary responses such as audio and files | Handle content types, binary content, and error responses according to the endpoint contract. Do not substitute this JSON envelope for file contents |

A `/v1` URL alone does not determine the response schema. For example, follow the current embedding envelope documented in [Models, Routing, and Multimodal](models-and-multimodal_en.md). Explicit endpoint documentation takes precedence over this general convention.

Clients can evaluate results in this order:

1. Check the HTTP status and use the endpoint contract and response content type to choose JSON, event-stream, or binary parsing. Do not force non-JSON error bodies into envelope fields.
2. For business-envelope endpoints, also check `status`. HTTP **200** represents a successful HTTP status, while `status: "failed"` still represents business failure. An HTTP-status check alone does not establish the business outcome.
3. Read optional `msg`, `data`, and additional fields according to the endpoint definition and check their actual types. Handle missing fields according to their documented conditions; do not automatically interpret absence as success or replace every missing result with the same empty object.
4. If a successful response only acknowledges acceptance, starts background work, or reports that a processing path returned, use the endpoint's progress or result-verification method to establish completion.

The Python and Go examples below call Chat Completions and use its compatible protocol response. The business-envelope checks above apply to endpoints that explicitly use that envelope.

<a id="http-clients"></a>

### Python and Go examples

Set `LINKMIND_API_KEY` when the deployment requires authentication. Both examples send non-streaming requests. Python prints the answer. Go prints the full JSON and reports the response body on HTTP errors.

```python
import os
import requests

headers = {"Content-Type": "application/json"}
token = os.getenv("LINKMIND_API_KEY")
if token:
    headers["Authorization"] = f"Bearer {token}"

response = requests.post(
    "http://localhost:8080/v1/chat/completions",
    headers=headers,
    json={
        "model": "qwen-plus",
        "messages": [{"role": "user", "content": "Hello"}],
        "stream": False,
    },
    timeout=60,
)
response.raise_for_status()
print(response.json()["choices"][0]["message"]["content"])
```

```go
package main

import (
    "bytes"
    "fmt"
    "io"
    "log"
    "net/http"
    "os"
    "time"
)

func main() {
    body := []byte(`{"model":"qwen-plus","messages":[{"role":"user","content":"Hello"}],"stream":false}`)
    request, err := http.NewRequest(http.MethodPost, "http://localhost:8080/v1/chat/completions", bytes.NewReader(body))
    if err != nil {
        log.Fatal(err)
    }
    request.Header.Set("Content-Type", "application/json")
    if token := os.Getenv("LINKMIND_API_KEY"); token != "" {
        request.Header.Set("Authorization", "Bearer "+token)
    }
    client := &http.Client{Timeout: 60 * time.Second}
    response, err := client.Do(request)
    if err != nil {
        log.Fatal(err)
    }
    defer response.Body.Close()
    data, err := io.ReadAll(response.Body)
    if err != nil {
        log.Fatal(err)
    }
    if response.StatusCode < 200 || response.StatusCode >= 300 {
        log.Fatalf("LinkMind HTTP %s: %s", response.Status, data)
    }
    fmt.Println(string(data))
}
```

<a id="social-channel-api"></a>

### Social channel APIs

Send GET parameters in the URL query string and POST parameters in an `application/json` body. `userId` is a string identifying the social user, and `channelId` is a numeric channel ID. Successful responses contain `status: "success"`. The last column lists the result fields. A business validation error returns `status: "failed"` and `msg`.

| Method | Route | Required input | Purpose and conditions | Success payload |
| --- | --- | --- | --- | --- |
| GET | `/socialChannel/runningMode` | None | Inspect the current LinkMind node and whether it proxies interaction-channel and API key requests to a cascade node. | `runningMode: "linkmind"`, `cascadeProxyEnabled`, `menuLoginRequired`, and the compatibility field `isMateMode: false` |
| POST | `/socialChannel/registerUser` | `userId`, `username` | Register an application user in the social service. Repeat registrations preserve the existing record. | Boolean `created`, `true` only for a newly inserted record |
| POST | `/socialChannel/saveLastLoginUser` | `userId` | Save the most recent login user on this node for local calls that populate user context when cascade proxying is enabled. | `status` |
| POST | `/socialChannel/createChannel` | `userId`, `name` | A registered user creates a channel and automatically becomes its owner and a subscriber. Optional `description` sets the description. `isPublic` is accepted for compatibility. Newly created channels are enabled and appear in the public list. | `channelId` |
| POST | `/socialChannel/subscribe` | `userId`, `channelId` | Subscribe a registered user to an enabled channel. | `msg` is `subscribed` |
| POST | `/socialChannel/unsubscribe` | `userId`, `channelId` | Remove a subscription. Owners must retain their own subscription. | `msg` is `unsubscribed` |
| GET | `/socialChannel/listMyChannels` | `userId` | List the enabled channels to which the user subscribes. | `data` is a `SocialChannel[]` |
| GET | `/socialChannel/listPublicChannels` | None | Discover enabled public channels. Optional `limit` sets the result count and `lang` selects the display language for names and descriptions. | `data` is a `SocialChannel[]` |
| GET | `/socialChannel/listOwnedChannels` | `userId` | List the user's own channels, including disabled ones. | `data` is a `SocialChannel[]` |
| GET | `/socialChannel/getChannel` | `channelId` | Read channel metadata such as name, description, and owner. The channel must exist and be enabled. | `data` is one `SocialChannel` |
| GET | `/socialChannel/listMessages` | `userId`, `channelId` | Read messages as a subscriber. Accepts optional `limit`, `beforeId`, `startTime`, and `endTime`. | `data` is a `SocialChannelMessage[]` |
| POST | `/socialChannel/sendMessage` | `userId`, `content`, and either `channelId` or `channelName` | Publish text to an enabled channel the user subscribes to. A positive `channelId` takes precedence. A name must resolve to one subscribed channel. | `messageId` |
| POST | `/socialChannel/toggleChannel` | `userId`, `channelId`, `enabled` | Only the owner can enable or disable the channel. `enabled` is a JSON boolean. | `msg` is `enabled` or `disabled` |
| POST | `/socialChannel/deleteChannel` | `userId`, `channelId` | Only the owner may delete the channel. | `msg` is `deleted` |

#### Paging through messages

For direct HTTP calls to `listMessages` and `listPublicChannels`, `limit` defaults to `50`. Zero and negative values also use `50`, and the maximum is `200`. `listMessages` returns `data` in descending message `id` order. Optional `startTime` and `endTime` bound the time range inclusively and use the format `yyyy-MM-dd HH:mm:ss`.

Omit `beforeId` on the first request. Suppose `u_1001` reads subscribed channel `17` and receives message IDs `105`, `102`, and `99`. To fetch the preceding page, set `beforeId` to the smallest positive `data[].id` on the current page and keep the same channel, user, and time range. This request returns messages with IDs below `99`. An empty `data` array in a successful response marks the end of that range.

```http
GET /socialChannel/listMessages?userId=u_1001&channelId=17&limit=3&beforeId=99
```

#### Cascade routing and skill integration

With `general.cascade_api_address` empty, the current node handles channel requests. Setting an upstream address explicitly forwards channel requests to the same route on that node. Three operations always run locally. `runningMode` reports local node status, `saveLastLoginUser` stores the local last-login user, and `cascadeConfig` reads the local configuration on GET and saves it on POST. `models[].api_address` configures the model upstream only. Check `cascadeProxyEnabled` in the `runningMode` response to confirm the proxy state. [Interaction cascade configuration and verification](deployment-and-security_en.md#4-interaction-cascade-and-network-verification)

The bundled `social-channel` skill resolves a channel name against the current business user's subscriptions, then calls `listMessages` to read or `sendMessage` to publish. If names coincide, select the channel by `data[].id` from the subscription list. The channel service stores subscriptions and messages, so the skill can use the same HTTP endpoints as other clients. Its read script requests `20` messages by default. [Skill configuration](agents-and-tools_en.md#2-configure-skills-workers-routes-and-filters) · [social-channel usage](../lagi-web/src/main/resources/skills/social-channel/SKILL.md)

<a id="provider-keys"></a>

### Provider key management APIs

| Method and path | Inputs | Success result |
| --- | --- | --- |
| `GET /apiKey/list` | Optional query `userId` includes Landing user keys | `data` is `ModelApiKey[]`, plus `localApiKeyEditable` |
| `GET /apiKey/get` | Query `modelName` | `data.name`, `data.provider`, masked `data.api_key`, and `data.api_address` where applicable |
| `GET /apiKey/providers` | None | `data` is an array of manageable provider names, plus `localApiKeyEditable` |
| `POST /apiKey/add` | Local provider: `name`, `provider`, `apiKey` and optional `apiAddress`. Landing: display `name`, `provider` and `userId`. The service issues the key. The request also accepts `model` | `msg: add success` |
| `POST /apiKey/delete` | Local: `provider` and either `apiKey` or `id`. Landing: `provider`, `userId` and the actual `apiKey` | `msg: delete success` |
| `POST /apiKey/toggle` | `id`, `provider`, and a JSON boolean `enabled`. Also pass `userId` for Landing. Send `true` or `false` explicitly | `msg: toggle success` |

Use query strings for GET and JSON bodies for POST. Check `status` and read `msg` on failure. The `get` response masks the key. Objects from `list` may contain full keys, so keep them out of logs. New local keys are stored disabled. Enable the desired key and verify the effective model configuration. A provider accepts `api_key` or `api_keys`. With `general.local_api_key_editable: false`, management is limited to Landing. Selecting a Landing key can still synchronize the current model configuration.

<a id="orders-and-balances"></a>

### Prepayment, charge details, and balances

| Route | Method | Required input | Purpose | Success payload |
| --- | --- | --- | --- | --- |
| `/credit/prepay` | POST | `lagiUserId`, `fee` | Start a prepay order. | `outTradeNo`, `qrCode`, `mWebUrl`, `totalFee`, `result` |
| `/credit/getChargeDetail` | GET | `outTradeNo` | Read one charge record by payment order number. | `seq`, `userId`, `amount`, `time`, `status` |
| `/credit/getChargeDetailByUserId` | GET | `userId` | List charge records of one user. | `data: ChargeDetail[]` |
| `/credit/getCreditUserBalance` | GET | `userId` | Read the current balance of one user. | `data.userId`, `data.balance` |
| `/credit/getChargeDetailBySeq` | GET | `seq` | Read one charge record by sequence number. | `data: ChargeDetail` |

`POST /credit/prepay` accepts JSON, for example `{"lagiUserId":"u_1001","fee":"10"}`. Both inputs are strings. The four GET routes use query parameters. Prepay returns an order object. A lookup by trade number returns `ChargeDetail` directly. For the user list, balance, or sequence lookup, read `data`. After placing an order, check its status by `outTradeNo` and reconcile the credit against the same user’s balance or history. Applications can replace the billing service behind these contracts.

<a id="service-objects"></a>

### Channel, key, and credit data objects

| Object | Key fields |
| --- | --- |
| `SocialChannel` | `id`, `name`, `description`, `ownerUserId`, `isPublic`, `enabled`, `createdAt` |
| `SocialChannelMessage` | `id`, `channelId`, `channelName`, `userId`, `userName`, `content`, `createdAt` |
| `ModelApiKey` | `id`, `name`, `provider`, `apiKey`, `apiAddress`, `createdTime`, `userId`, `status` |
| `CreditUserBalance` | `userId`, `balance` |
| `ChargeDetail` | `seq`, `userId`, `amount`, `time`, `status` |

## 4. Validate an integration

1. Call a short text request and retain the request time and model.
2. Confirm the response, LinkMind runtime record, and expected route.
3. Add streaming, tools, files, or custom metadata one capability at a time.
4. For a production client, verify error handling, timeouts, authentication, and billing behavior.

See [Models, Routing, and Multimodal](models-and-multimodal_en.md) for chat, Responses, Messages, embedding, and multimodal request fields; see [Knowledge Base](knowledge-base_en.md) for vectors and ingestion. Use [Extensions](extensions_en.md) when a standard API or compatible adapter is insufficient.
