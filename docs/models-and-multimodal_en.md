# Models, Routing, and Multimodal

This page keeps provider configuration, routing, API calls, and verification in one place. Prove one chat request first, then enable embeddings, audio, image, video, or document processing as needed. Each capability must be verified independently.

[Providers and keys](#provider-drivers) · [Routing](#routing-reference) · [Medusa](#medusa-cache) · [Chat Completions](#model-api-04) · [Responses](#model-api-02) · [Messages](#model-api-03) · [Multimodal settings](#function-backends) · [Embedding and media APIs](#model-api-19)

## 1. Configure a provider and chat route

Declare an upstream in `models`, then expose a chat model through `functions.chat.backends` in `lagi.yml`. `name` is LinkMind's internal backend name; `model` is the model ID used by callers or the provider. They are not interchangeable.

```yaml
models:
  - name: qwen
    type: Alibaba
    enable: true
    model: qwen-plus,text-embedding-v3
    driver: ai.wrapper.impl.AlibabaAdapter
    api_key: ${QWEN_API_KEY}

functions:
  chat:
    route: pass(qwen)
    filter: sensitive,priority,stopping,continue
    backends:
      - backend: qwen
        model: qwen-plus
        enable: true
        stream: true
        protocol: completion
        priority: 100
```

<a id="provider-fields"></a>

| Field | Meaning |
| --- | --- |
| `name` | Backend name referenced later by `functions.*.backend` |
| `type` | Provider label shown in config or UI |
| `enable` | Whether the provider is enabled |
| `model` | Comma-separated model IDs handled by the adapter |
| `driver` | Java adapter class for a single-driver backend |
| `drivers` | Optional multi-driver list when one backend exposes multiple modalities |
| `api_address` | Provider endpoint, often for OpenAI-compatible or self-hosted services |
| `endpoint` | Provider-specific base endpoint |
| `api_key` | Primary API key |
| `api_keys` | Multi-key pool |
| `key_route` | Key-pool strategy: `polling` or `failover` |
| `app_id` / `app_key` | Provider-specific app identity |
| `secret_key` | Provider secret |
| `access_key_id` / `access_key_secret` | Cloud credential pair used by some vendors |
| `deployment` / `api_version` | Deployment-specific fields, especially for Azure-like providers |
| `alias` | Optional model alias mapping |
| `oss` | Object-storage profile used by the adapter |

Inject real keys through environment variables or controlled configuration. Never put them in source, screenshots, or support bundles. An OpenAI-compatible upstream can reuse `ai.llm.adapter.impl.OpenAIStandardAdapter`; other compatible endpoints can use `OpenRouterAdapter` or `QwenCompatibleAdapter` as appropriate.

For failover, keep the caller-facing model ID stable and select backends in the route:

```yaml
functions:
  chat:
    route: best((qwen|deepseek),openai)
    handle: failover
    grace_time: 20
    context_length: 4096
```

`pass(name)` pins a backend. `best(...)` chooses from candidates. Confirm that every candidate works independently before enabling routing and failover.

<a id="provider-drivers"></a>

### Single drivers, multiple drivers, and API key pools

The bundled profiles include Qwen, DeepSeek, Landing, FastChat/Vicuna, OpenAI, Azure OpenAI, ERNIE, ChatGLM, Kimi, Baichuan, Spark, SenseChat, Gemini, Doubao, and Claude. The models you can call depend on enabled providers, routes, and credentials. [Bundled model profiles](../lagi-web/src/main/resources/model.yml)

#### Single-Driver Example

```yaml
models:
  - name: deepseek
    type: DeepSeek
    enable: true
    model: deepseek-chat
    driver: ai.llm.adapter.impl.DeepSeekAdapter
    api_address: https://api.deepseek.com/chat/completions
    api_key: your-api-key
```

#### Multi-Driver Example

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

#### API Key Pool

If you only have a single key, keep using `api_key`:

```yaml
api_key: sk-your-single-key
```

When you have multiple keys, use `api_keys` plus `key_route`:

```yaml
api_keys:
  - sk-key1
  - sk-key2
  - sk-key3
key_route: polling
```

You can also use comma-separated syntax:

```yaml
api_keys: sk-key1,sk-key2,sk-key3
key_route: failover
```

When both `api_key` and `api_keys` exist, the key pool takes priority and the single key is merged into the pool if needed.

<a id="routing-reference"></a>

### Routing expressions and failover

Configure every named provider in `models` and `functions.chat.backends`. Register the routing syntax under `routers.items` and use it in `route`.

```yaml
routers:
  enable: true
  items:
    - name: best
      rule: (|,&)

    - name: pass
      rule: (%)
```

Common fields:

| Field | Meaning |
| --- | --- |
| `enable` | Global router switch |
| `items[].name` | Router name referenced from `functions.chat.route` or workers |
| `items[].rule` | Route grammar supported by that router |

Use:

- `A|B` for polling
- `A,B` for failover
- `A&B` for parallel
- `%` as a wildcard

#### Chat routes and runtime options

```yaml
functions:
  chat:
    route: pass(landing)
    filter: sensitive,priority,stopping,continue
    handle: failover
    grace_time: 20
    enable_queue_handle: true
    maxgen: 3
    context_length: 4096
    token_charge: false
    enable_auth: false
    enable_policy: true
    backends:
      - backend: landing
        model: cascade
        enable: true
        stream: true
        protocol: completion
        priority: 350
```

Common fields:

| Field | Meaning |
| --- | --- |
| `route` | Router expression used to choose or combine backends |
| `filter` | Comma-separated filter names from `filters.items` |
| `backends` | Candidate chat backends |
| `enable_queue_handle` | Whether queue handling is enabled |
| `handle` | Overflow or fallback strategy |
| `grace_time` | Grace period used by the handler |
| `maxgen` | Maximum number of generated branches or retries |
| `context_length` | Conversation context length used by orchestration |
| `token_charge` | Whether token accounting is enabled |
| `enable_auth` | Whether auth checks are enabled |
| `enable_policy` | Whether built-in policy checks are enabled |

Declare chat backends under `functions.chat.backends`.

With all four backends enabled, `best((landing&qwen),(kimi|chatgpt))` combines parallel calls, round robin, and failover.

#### Combine three configured chat backends

LinkMind is not limited to one chat backend. You can keep multiple providers enabled and decide how they are used.

Example:

```yaml
functions:
  chat:
    route: best((landing&qwen),kimi)
    backends:
      - backend: landing
        model: cascade
        enable: true
        stream: true
        priority: 350

      - backend: qwen
        model: qwen-plus
        enable: true
        stream: true
        priority: 100

      - backend: kimi
        model: moonshot-v1-8k
        enable: true
        stream: true
        priority: 90
```

Useful route ideas:

- `A|B`: polling
- `A,B`: failover
- `A&B`: parallel

<a id="medusa-cache"></a>

### Medusa caching and prefetch

```yaml
stores:
  medusa:
    enable: true
    algorithm: hash,graph,llm
    reason_model: deepseek-r1
    aheads: 1
    producer_thread_num: 1
    consumer_thread_num: 2
    core_pool_size: 5
    maximum_pool_size: 30
    cache_persistent_path: medusa_cache
    cache_persistent_batch_size: 2
    flush: false
    cache_hit_window: 16
    cache_hit_ratio: 0.3
    temperature_tolerance: 0.1
```

Common fields:

| Field | Meaning |
| --- | --- |
| `enable` | Whether Medusa cache acceleration is enabled |
| `cache` / `memory` | Whether cache and in-memory acceleration are enabled |
| `algorithm` | Cache and reasoning strategies |
| `enableL2` / `enableReasonDiver` | Optional advanced cache and reasoning toggles |
| `reason_model` | Optional reasoning model |
| `aheads` | Number of ahead-of-time requests |
| `producer_thread_num` / `consumer_thread_num` | Worker thread counts |
| `core_pool_size` / `maximum_pool_size` | Prompt cache write executor thread pool size |
| `consumeDelay` / `preDelay` | Optional queue timing controls |
| `lcsRatioPromptInput` / `similarityCutoff` / `qa_similarity_cutoff` / `dynamicSimilarity` | Cache-match thresholds |
| `cache_persistent_path` | Persistent cache directory |
| `cache_persistent_batch_size` | Flush batch size |
| `flush` | Rebuild cache state on startup |
| `cache_hit_window` / `cache_hit_ratio` | Sliding window and hit-ratio thresholds |
| `temperature_tolerance` | Temperature tolerance during cache match |
| `inits` | Optional warm-up prompts |

## 2. Quickly verify chat and the model list

After restarting the service, list the exposed models and make one short non-streaming request. A protected deployment requires `Authorization: Bearer <linkmind-api-key>`.

```bash
curl http://localhost:8080/v1/models \
  -H "Authorization: Bearer <linkmind-api-key>"

curl http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer <linkmind-api-key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"qwen-plus","messages":[{"role":"user","content":"Describe LinkMind in one sentence."}],"stream":false,"max_completion_tokens":128}'
```

Success means that the response contains `choices[0].message.content` and the **Control** view records the same model and time. If a model is absent from `/v1/models`, check `models.enable`, the chat backend `enable`, the backend name, and the model ID. If upstream calls fail, check the key, balance, network, and provider address.

## 3. Chat, Responses, and Messages APIs

| Protocol | Endpoint | Minimum request fields | Typical client |
| --- | --- | --- | --- |
| OpenAI Chat Completions | `POST /v1/chat/completions` | `model`, `messages` | OpenAI SDK and most agents |
| OpenAI Responses | `POST /v1/responses` | `model`, `input` | Responses SDK |
| Anthropic Messages | `POST /v1/messages` | `model`, `max_tokens`, `messages`, `system` (current version) | Claude Code and similar clients |

Responses is also available at `POST /responses`, an alias of `POST /v1/responses`. Anthropic Messages is also available at `POST /messages`, an alias of `POST /v1/messages`. The examples below consistently use the `/v1/…` paths.

The optional Responses field `instructions` sets system instructions. The Anthropic Messages field `system` sets the system prompt and must be supplied explicitly in the current version; see the requirements below.

<a id="model-api-04"></a>

### Chat Completions requests and responses

`POST /v1/chat/completions` is the unified entrypoint for chat models. This section covers request fields, `messages` content blocks, tool definitions, and responses.

Jump to [request fields](#chat-completions-request-fields), [tool definitions](#complete-toolsfunction-structure), the [complete tool parameter table](#complete-tool-parameter-reference), or [responses](#chat-completions-response).

#### Chat Completions request fields

| Field | Location | Type | Required | Description |
| --- | --- | --- | --- | --- |
| `Content-Type` | header | string | Yes | Use `application/json` |
| `Authorization` | header | string | As required by deployment authentication | `Bearer <linkmind-api-key>`; provide a valid client key when authentication is required |
| Request body | body | object | Yes | Contains the request fields below |
| `model` | body | string | Supply for standalone API calls | An enabled model name; an existing console session with a model preference can supply that preference |
| `messages` | body | array of objects | Yes | Messages in conversation order; supply at least one valid message |
| `messages[].role` | body | string | Yes | Common roles are `system`, `user`, and `assistant`; tool results use `tool`. Supported roles depend on the backend |
| `messages[].content` | body | string / array of objects / null | Depends on message purpose | Plain text or content blocks; normal conversation messages need content, while an assistant message containing only tool calls can use `null` |
| `messages[].content[].type` | body | string | When using content blocks | Block type; text blocks use `text`, and other types require model support |
| `messages[].content[].text` | body | string | For text blocks | Text within a `type: "text"` content block |
| `messages[].tool_calls` | body | array of objects | No | Tool calls from the preceding assistant turn; entries include `id`, `type`, `function.name`, and JSON-string `function.arguments` |
| `messages[].tool_call_id` | body | string | When returning a tool result | Matches the relevant tool call `id`; use with `role: "tool"` |
| `stream` | body | boolean | No | `true` requests SSE; omission or `false` uses a non-streaming response |
| `store` | body | boolean | No | Requests storage of this result; actual storage behavior depends on the selected backend |
| `max_completion_tokens` | body | integer | No | Maximum output tokens for this generation; support depends on the backend |
| `max_tokens` | body | integer | No | Compatibility field for backends using this output limit; choose the limit field required by the backend |
| `temperature` | body | number | No | Sampling temperature; the current request object defaults to `1` |
| `top_p` | body | number | No | Nucleus sampling parameter; supported values depend on the backend |
| `presence_penalty` | body | number | No | Presence penalty; supported values depend on the backend |
| `frequency_penalty` | body | number | No | Frequency penalty; supported values depend on the backend |
| `tools` | body | array of objects | No | Tool definitions supplied to the model; the complete structure is documented below |
| `tool_choice` | body | string | No | Tool selection policy; the current request field is a string, so use a policy value accepted by the backend |
| `parallel_tool_calls` | body | boolean | No | Whether parallel tool calls are allowed; requires backend support |
| `response_format` | body | object | No | Output format with `type`, plus `json_schema` when using a JSON Schema format |
| `response_format.type` | body | string | When choosing an output format | An output format type supported by the backend |
| `response_format.json_schema` | body | object | When using a Schema format | Contains `name`, `schema`, and optional `strict`; `schema` constrains the output |
| `response_format.json_schema.name` | body | string | When using a Schema format | Name of the output Schema |
| `response_format.json_schema.schema` | body | object | When using a Schema format | Output structure and constraints |
| `response_format.json_schema.strict` | body | boolean | No | Requests strict Schema adherence; the current object defaults to `true`, and backend support is required |
| `stream_options` | body | object | No | Streaming options supported by the backend |
| `logprobs` | body | boolean | No | Requests token probability information; requires backend support |
| `category` | body | string | No | LinkMind data or knowledge category |
| `sessionId` / `session_id` | body | string | No | LinkMind session identifier; keep it consistent when a business conversation needs the same session |
| `extra_body` / `extraBody` | body | object | No | Typed extensions with declared fields such as `userId` (aliases `user_id`, `user`) and `mateUrl` (alias `mate_url`) |
| `extra_body.userId` | body | string | No | Logical business-user identifier for social skills, channel APIs, and user-aware workflows; HTTP aliases are `user_id` and `user`, while Java uses `setUserId(...)` |
| `extra_body.mateUrl` | body | string | No | Node address in cascade business context; automatic population uses the local base URL. HTTP alias: `mate_url`. See [Deployment, Security, and Cascades](deployment-and-security_en.md) |
| `chat_template_kwargs` | body | object | No | Backend chat-template options; support and effects depend on the selected model backend |

`stream`, `store`, `max_completion_tokens`, and `tools` are optional. Recognition of a field by the endpoint does not mean that every model implements it; sampling, tools, structured output, and storage depend on the selected backend.

Plain-text request body:

```json
{
  "model": "qwen-plus",
  "stream": false,
  "max_completion_tokens": 256,
  "messages": [
    {
      "role": "user",
      "content": "Hello, LinkMind."
    }
  ]
}
```

#### extra_body business context

`POST /chat/completions` and `POST /v1/chat/completions` accept an optional `extra_body` object for business-side metadata. It is a top-level field in the request JSON containing typed business context; callers do not need to change the standard `messages` structure.

This complete HTTP request body from the previous documentation expresses an intent to query the business user's channel subscriptions. Executing that operation depends on configured social skills and business endpoints; supplying a user identifier alone does not enable them:

```json
{
  "model": "qwen-plus",
  "stream": false,
  "messages": [
    {
      "role": "user",
      "content": "List my subscribed channels."
    }
  ],
  "extra_body": {
    "user_id": "u_1001"
  }
}
```

Save the body locally as `chat-with-context.json` and submit it. Use a model name available in the current deployment:

```bash
curl --request POST "http://localhost:8080/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <linkmind-api-key>" \
  --data-binary "@chat-with-context.json"
```

Use the field definitions in the request table above:

- `extra_body.user_id` is an optional logical business-user identifier used by social skills, channel APIs, and workflows that need to identify the business caller. Its Java property is `userId`.
- `extra_body.user` is a compatibility alias for the same identifier; `extra_body.userId` is also accepted. All three use strings. Choose one spelling rather than supplying conflicting values. This `user` is inside `extra_body`, not a same-named top-level field or `messages[].role`.
- Top-level `extraBody` is a compatibility spelling of `extra_body`. The current typed object declares `userId`, `mateUrl`, and their aliases; undeclared custom properties are not retained as fields of this object.
- The identifier carries business context and does not replace API keys, login state, or other authentication required by the deployment. The response continues to use the Chat Completions structure.

Omitting `extra_body`, sending explicit `null`, sending `{}`, or supplying only `mate_url` affects runtime user-population conditions. These are not all equivalent to an absent user. For exact conditions, internal skill transport, and Java object usage, see [System Integration and Secondary Development](integration_en.md#runtime-propagation-of-business-context).

#### messages content blocks

`messages` carries the conversation in order. Plain text can be assigned directly to `content`. A text content block uses this structure:

```json
{
  "role": "user",
  "content": [
    {"type": "text", "text": "Summarize this material."}
  ]
}
```

Roles and content blocks are part of the request format; supported block types depend on the model. To continue a tool interaction, retain the assistant's `tool_calls` and append tool result messages with matching `tool_call_id` values.

#### Complete tools.function structure

Declare each tool through `tools[]`. The conditions below describe the requirements when defining that structure; whether a particular tool argument is mandatory is determined by its containing object's `required` list.

| Field path | Type | Condition and meaning |
| --- | --- | --- |
| `tools` | array of objects | Optional; each supplied entry declares a tool |
| `tools[].type` | string | Use `function` for a function tool |
| `tools[].function` | object | Function tool definition |
| `tools[].function.name` | string | A function tool needs a name to match returned tool calls |
| `tools[].function.description` | string | Optional description of the function's purpose |
| `tools[].function.strict` | boolean | Optional; the current object defaults to `true`, while strict Schema support depends on the backend |
| `tools[].function.parameters` | object | JSON Schema for arguments; provide it for tools that take arguments |
| `tools[].function.parameters.type` | string | Usually `object` for an argument object |
| `tools[].function.parameters.required` | array of strings | Required property names for the argument object; the current object defaults to an empty list |
| `tools[].function.parameters.properties` | object | Maps argument names to their Schemas |
| `tools[].function.parameters.additionalProperties` | boolean / object | Whether undeclared properties are allowed, or their Schema; the current object defaults to `false` |

Property Schemas may nest `properties`, `items`, `required`, `additionalProperties`, and `patternProperties`, and declare `enum`, numeric bounds, length limits, and other constraints. This example declares a caller-provided tool; after the model returns `tool_calls`, the caller executes the tool and returns its result.

```json
{
  "model": "qwen-plus",
  "messages": [
    {
      "role": "user",
      "content": "Read the first three lines of the guide."
    }
  ],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "read_document",
        "description": "Read text lines from a specified file.",
        "strict": false,
        "parameters": {
          "type": "object",
          "properties": {
            "path": {
              "type": "string",
              "description": "File path"
            },
            "limit": {
              "type": "integer",
              "description": "Number of lines to read",
              "minimum": 1
            }
          },
          "required": [
            "path"
          ],
          "additionalProperties": false
        }
      }
    }
  ]
}
```

#### Complete tool parameter reference

The following table retains every concrete tool parameter and nested node from the previous API table. All paths are relative to `tools[].function.parameters`; for example, `properties.path.type` means `tools[].function.parameters.properties.path.type`.

“Node type” is the JSON type of the Schema field itself. For example, `properties.path` is a Schema object; the actual argument type of `path` is defined by its `type` value. These parameters belong to different tools; select the relevant parameters for each tool and use the appropriate object's `required` list for mandatory arguments.

Where the previous table did not specify enum members, numeric bounds, or defaults, only the constraint field and its type are retained here. Actual values come from the caller's tool Schema. Declaring a parameter name does not provide the corresponding tool implementation.

| Schema path | Node type | Meaning and constraints |
| --- | --- | --- |
| `properties.path` | object | Schema for argument `path`; its containing object's `required` list determines whether it is mandatory |
| `properties.path.description` | string | Description of this argument or nested field |
| `properties.path.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.offset` | object | Schema for argument `offset`; its containing object's `required` list determines whether it is mandatory; Result offset. |
| `properties.offset.description` | string | Description of this argument or nested field |
| `properties.offset.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.limit` | object | Schema for argument `limit`; its containing object's `required` list determines whether it is mandatory; Maximum number of records to return. |
| `properties.limit.description` | string | Description of this argument or nested field |
| `properties.limit.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.limit.minimum` | integer | Numeric lower bound; the tool Schema supplies the threshold |
| `properties.file_path` | object | Schema for argument `file_path`; its containing object's `required` list determines whether it is mandatory |
| `properties.file_path.description` | string | Description of this argument or nested field |
| `properties.file_path.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.filePath` | object | Schema for argument `filePath`; its containing object's `required` list determines whether it is mandatory |
| `properties.filePath.description` | string | Description of this argument or nested field |
| `properties.filePath.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.file` | object | Schema for argument `file`; its containing object's `required` list determines whether it is mandatory |
| `properties.file.description` | string | Description of this argument or nested field |
| `properties.file.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.oldText` | object | Schema for argument `oldText`; its containing object's `required` list determines whether it is mandatory |
| `properties.oldText.description` | string | Description of this argument or nested field |
| `properties.oldText.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.newText` | object | Schema for argument `newText`; its containing object's `required` list determines whether it is mandatory |
| `properties.newText.description` | string | Description of this argument or nested field |
| `properties.newText.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.edits` | object | Schema for argument `edits`; its containing object's `required` list determines whether it is mandatory |
| `properties.edits.description` | string | Description of this argument or nested field |
| `properties.edits.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.edits.items` | object | Schema for each array item |
| `properties.edits.items.additionalProperties` | boolean | Whether this object allows undeclared properties |
| `properties.edits.items.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.edits.items.required` | [string] | Names of properties required in this object |
| `properties.edits.items.properties` | object | Property Schemas for this object |
| `properties.edits.items.properties.oldText` | object | Schema for argument `oldText`; its containing object's `required` list determines whether it is mandatory |
| `properties.edits.items.properties.oldText.description` | string | Description of this argument or nested field |
| `properties.edits.items.properties.oldText.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.edits.items.properties.newText` | object | Schema for argument `newText`; its containing object's `required` list determines whether it is mandatory |
| `properties.edits.items.properties.newText.description` | string | Description of this argument or nested field |
| `properties.edits.items.properties.newText.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.old_string` | object | Schema for argument `old_string`; its containing object's `required` list determines whether it is mandatory |
| `properties.old_string.description` | string | Description of this argument or nested field |
| `properties.old_string.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.old_text` | object | Schema for argument `old_text`; its containing object's `required` list determines whether it is mandatory |
| `properties.old_text.description` | string | Description of this argument or nested field |
| `properties.old_text.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.oldString` | object | Schema for argument `oldString`; its containing object's `required` list determines whether it is mandatory |
| `properties.oldString.description` | string | Description of this argument or nested field |
| `properties.oldString.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.new_string` | object | Schema for argument `new_string`; its containing object's `required` list determines whether it is mandatory |
| `properties.new_string.description` | string | Description of this argument or nested field |
| `properties.new_string.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.new_text` | object | Schema for argument `new_text`; its containing object's `required` list determines whether it is mandatory |
| `properties.new_text.description` | string | Description of this argument or nested field |
| `properties.new_text.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.newString` | object | Schema for argument `newString`; its containing object's `required` list determines whether it is mandatory |
| `properties.newString.description` | string | Description of this argument or nested field |
| `properties.newString.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.content` | object | Schema for argument `content`; its containing object's `required` list determines whether it is mandatory; Message content. |
| `properties.content.description` | string | Description of this argument or nested field |
| `properties.content.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.command` | object | Schema for argument `command`; its containing object's `required` list determines whether it is mandatory |
| `properties.command.description` | string | Description of this argument or nested field |
| `properties.command.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.workdir` | object | Schema for argument `workdir`; its containing object's `required` list determines whether it is mandatory |
| `properties.workdir.description` | string | Description of this argument or nested field |
| `properties.workdir.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.env` | object | Schema for argument `env`; its containing object's `required` list determines whether it is mandatory |
| `properties.env.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.env.patternProperties` | object | Mapping of property-name regular expressions to Schemas |
| `properties.env.patternProperties["^(.*)$"]` | object | Schema for values whose property names match this regular expression |
| `properties.env.patternProperties["^(.*)$"].type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.yieldMs` | object | Schema for argument `yieldMs`; its containing object's `required` list determines whether it is mandatory |
| `properties.yieldMs.description` | string | Description of this argument or nested field |
| `properties.yieldMs.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.background` | object | Schema for argument `background`; its containing object's `required` list determines whether it is mandatory |
| `properties.background.description` | string | Description of this argument or nested field |
| `properties.background.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.timeout` | object | Schema for argument `timeout`; its containing object's `required` list determines whether it is mandatory |
| `properties.timeout.description` | string | Description of this argument or nested field |
| `properties.timeout.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.timeout.minimum` | integer | Numeric lower bound; the tool Schema supplies the threshold |
| `properties.pty` | object | Schema for argument `pty`; its containing object's `required` list determines whether it is mandatory |
| `properties.pty.description` | string | Description of this argument or nested field |
| `properties.pty.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.elevated` | object | Schema for argument `elevated`; its containing object's `required` list determines whether it is mandatory |
| `properties.elevated.description` | string | Description of this argument or nested field |
| `properties.elevated.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.host` | object | Schema for argument `host`; its containing object's `required` list determines whether it is mandatory |
| `properties.host.description` | string | Description of this argument or nested field |
| `properties.host.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.security` | object | Schema for argument `security`; its containing object's `required` list determines whether it is mandatory |
| `properties.security.description` | string | Description of this argument or nested field |
| `properties.security.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.ask` | object | Schema for argument `ask`; its containing object's `required` list determines whether it is mandatory |
| `properties.ask.description` | string | Description of this argument or nested field |
| `properties.ask.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.node` | object | Schema for argument `node`; its containing object's `required` list determines whether it is mandatory |
| `properties.node.description` | string | Description of this argument or nested field |
| `properties.node.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.action` | object | Schema for argument `action`; its containing object's `required` list determines whether it is mandatory |
| `properties.action.description` | string | Description of this argument or nested field |
| `properties.action.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.action.enum` | [string] | Allowed values; actual members are supplied by the tool Schema |
| `properties.sessionId` | object | Schema for argument `sessionId`; its containing object's `required` list determines whether it is mandatory |
| `properties.sessionId.description` | string | Description of this argument or nested field |
| `properties.sessionId.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.data` | object | Schema for argument `data`; its containing object's `required` list determines whether it is mandatory; API response data. |
| `properties.data.description` | string | Description of this argument or nested field |
| `properties.data.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.keys` | object | Schema for argument `keys`; its containing object's `required` list determines whether it is mandatory |
| `properties.keys.description` | string | Description of this argument or nested field |
| `properties.keys.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.keys.items` | object | Schema for each array item |
| `properties.keys.items.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.hex` | object | Schema for argument `hex`; its containing object's `required` list determines whether it is mandatory |
| `properties.hex.description` | string | Description of this argument or nested field |
| `properties.hex.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.hex.items` | object | Schema for each array item |
| `properties.hex.items.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.literal` | object | Schema for argument `literal`; its containing object's `required` list determines whether it is mandatory |
| `properties.literal.description` | string | Description of this argument or nested field |
| `properties.literal.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.text` | object | Schema for argument `text`; its containing object's `required` list determines whether it is mandatory; Input text. |
| `properties.text.description` | string | Description of this argument or nested field |
| `properties.text.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.bracketed` | object | Schema for argument `bracketed`; its containing object's `required` list determines whether it is mandatory |
| `properties.bracketed.description` | string | Description of this argument or nested field |
| `properties.bracketed.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.eof` | object | Schema for argument `eof`; its containing object's `required` list determines whether it is mandatory |
| `properties.eof.description` | string | Description of this argument or nested field |
| `properties.eof.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.gatewayUrl` | object | Schema for argument `gatewayUrl`; its containing object's `required` list determines whether it is mandatory |
| `properties.gatewayUrl.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.gatewayToken` | object | Schema for argument `gatewayToken`; its containing object's `required` list determines whether it is mandatory |
| `properties.gatewayToken.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.timeoutMs` | object | Schema for argument `timeoutMs`; its containing object's `required` list determines whether it is mandatory |
| `properties.timeoutMs.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.includeDisabled` | object | Schema for argument `includeDisabled`; its containing object's `required` list determines whether it is mandatory |
| `properties.includeDisabled.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.job` | object | Schema for argument `job`; its containing object's `required` list determines whether it is mandatory |
| `properties.job.additionalProperties` | boolean | Whether this object allows undeclared properties |
| `properties.job.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.job.properties` | object | Property Schemas for this object |
| `properties.jobId` | object | Schema for argument `jobId`; its containing object's `required` list determines whether it is mandatory |
| `properties.jobId.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.id` | object | Schema for argument `id`; its containing object's `required` list determines whether it is mandatory |
| `properties.id.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.patch` | object | Schema for argument `patch`; its containing object's `required` list determines whether it is mandatory |
| `properties.patch.additionalProperties` | boolean | Whether this object allows undeclared properties |
| `properties.patch.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.patch.properties` | object | Property Schemas for this object |
| `properties.mode` | object | Schema for argument `mode`; its containing object's `required` list determines whether it is mandatory |
| `properties.mode.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.mode.enum` | [string] | Allowed values; actual members are supplied by the tool Schema |
| `properties.runMode` | object | Schema for argument `runMode`; its containing object's `required` list determines whether it is mandatory |
| `properties.runMode.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.runMode.enum` | [string] | Allowed values; actual members are supplied by the tool Schema |
| `properties.contextMessages` | object | Schema for argument `contextMessages`; its containing object's `required` list determines whether it is mandatory |
| `properties.contextMessages.minimum` | integer | Numeric lower bound; the tool Schema supplies the threshold |
| `properties.contextMessages.maximum` | integer | Numeric upper bound; the tool Schema supplies the threshold |
| `properties.contextMessages.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.kinds` | object | Schema for argument `kinds`; its containing object's `required` list determines whether it is mandatory |
| `properties.kinds.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.kinds.items` | object | Schema for each array item |
| `properties.kinds.items.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.activeMinutes` | object | Schema for argument `activeMinutes`; its containing object's `required` list determines whether it is mandatory |
| `properties.activeMinutes.minimum` | integer | Numeric lower bound; the tool Schema supplies the threshold |
| `properties.activeMinutes.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.messageLimit` | object | Schema for argument `messageLimit`; its containing object's `required` list determines whether it is mandatory |
| `properties.messageLimit.minimum` | integer | Numeric lower bound; the tool Schema supplies the threshold |
| `properties.messageLimit.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.sessionKey` | object | Schema for argument `sessionKey`; its containing object's `required` list determines whether it is mandatory |
| `properties.sessionKey.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.includeTools` | object | Schema for argument `includeTools`; its containing object's `required` list determines whether it is mandatory |
| `properties.includeTools.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.label` | object | Schema for argument `label`; its containing object's `required` list determines whether it is mandatory |
| `properties.label.minLength` | integer | Minimum string length; the tool Schema supplies the value |
| `properties.label.maxLength` | integer | Maximum string length; the tool Schema supplies the value |
| `properties.label.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.agentId` | object | Schema for argument `agentId`; its containing object's `required` list determines whether it is mandatory; Target agent identifier. |
| `properties.agentId.minLength` | integer | Minimum string length; the tool Schema supplies the value |
| `properties.agentId.maxLength` | integer | Maximum string length; the tool Schema supplies the value |
| `properties.agentId.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.message` | object | Schema for argument `message`; its containing object's `required` list determines whether it is mandatory |
| `properties.message.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.timeoutSeconds` | object | Schema for argument `timeoutSeconds`; its containing object's `required` list determines whether it is mandatory |
| `properties.timeoutSeconds.minimum` | integer | Numeric lower bound; the tool Schema supplies the threshold |
| `properties.timeoutSeconds.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.task` | object | Schema for argument `task`; its containing object's `required` list determines whether it is mandatory |
| `properties.task.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.runtime` | object | Schema for argument `runtime`; its containing object's `required` list determines whether it is mandatory |
| `properties.runtime.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.runtime.enum` | [string] | Allowed values; actual members are supplied by the tool Schema |
| `properties.resumeSessionId` | object | Schema for argument `resumeSessionId`; its containing object's `required` list determines whether it is mandatory |
| `properties.resumeSessionId.description` | string | Description of this argument or nested field |
| `properties.resumeSessionId.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.model` | object | Schema for argument `model`; its containing object's `required` list determines whether it is mandatory; Model name used for the call. |
| `properties.model.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.thinking` | object | Schema for argument `thinking`; its containing object's `required` list determines whether it is mandatory |
| `properties.thinking.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.cwd` | object | Schema for argument `cwd`; its containing object's `required` list determines whether it is mandatory |
| `properties.cwd.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.runTimeoutSeconds` | object | Schema for argument `runTimeoutSeconds`; its containing object's `required` list determines whether it is mandatory |
| `properties.runTimeoutSeconds.minimum` | integer | Numeric lower bound; the tool Schema supplies the threshold |
| `properties.runTimeoutSeconds.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.thread` | object | Schema for argument `thread`; its containing object's `required` list determines whether it is mandatory |
| `properties.thread.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.cleanup` | object | Schema for argument `cleanup`; its containing object's `required` list determines whether it is mandatory |
| `properties.cleanup.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.cleanup.enum` | [string] | Allowed values; actual members are supplied by the tool Schema |
| `properties.sandbox` | object | Schema for argument `sandbox`; its containing object's `required` list determines whether it is mandatory |
| `properties.sandbox.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.sandbox.enum` | [string] | Allowed values; actual members are supplied by the tool Schema |
| `properties.streamTo` | object | Schema for argument `streamTo`; its containing object's `required` list determines whether it is mandatory |
| `properties.streamTo.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.streamTo.enum` | [string] | Allowed values; actual members are supplied by the tool Schema |
| `properties.attachments` | object | Schema for argument `attachments`; its containing object's `required` list determines whether it is mandatory |
| `properties.attachments.maxItems` | integer | Maximum number of array items; the tool Schema supplies the value |
| `properties.attachments.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.attachments.items` | object | Schema for each array item |
| `properties.attachments.items.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.attachments.items.required` | [string] | Names of properties required in this object |
| `properties.attachments.items.properties` | object | Property Schemas for this object |
| `properties.attachments.items.properties.name` | object | Schema for argument `name`; its containing object's `required` list determines whether it is mandatory |
| `properties.attachments.items.properties.name.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.attachments.items.properties.content` | object | Schema for argument `content`; its containing object's `required` list determines whether it is mandatory; Message content. |
| `properties.attachments.items.properties.content.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.attachments.items.properties.encoding` | object | Schema for argument `encoding`; its containing object's `required` list determines whether it is mandatory |
| `properties.attachments.items.properties.encoding.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.attachments.items.properties.encoding.enum` | [string] | Allowed values; actual members are supplied by the tool Schema |
| `properties.attachments.items.properties.mimeType` | object | Schema for argument `mimeType`; its containing object's `required` list determines whether it is mandatory |
| `properties.attachments.items.properties.mimeType.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.attachAs` | object | Schema for argument `attachAs`; its containing object's `required` list determines whether it is mandatory |
| `properties.attachAs.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.attachAs.properties` | object | Property Schemas for this object |
| `properties.attachAs.properties.mountPath` | object | Schema for argument `mountPath`; its containing object's `required` list determines whether it is mandatory |
| `properties.attachAs.properties.mountPath.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.target` | object | Schema for argument `target`; its containing object's `required` list determines whether it is mandatory |
| `properties.target.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.recentMinutes` | object | Schema for argument `recentMinutes`; its containing object's `required` list determines whether it is mandatory |
| `properties.recentMinutes.minimum` | integer | Numeric lower bound; the tool Schema supplies the threshold |
| `properties.recentMinutes.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.query` | object | Schema for argument `query`; its containing object's `required` list determines whether it is mandatory; Query conditions. |
| `properties.query.description` | string | Description of this argument or nested field |
| `properties.query.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.count` | object | Schema for argument `count`; its containing object's `required` list determines whether it is mandatory |
| `properties.count.description` | string | Description of this argument or nested field |
| `properties.count.minimum` | integer | Numeric lower bound; the tool Schema supplies the threshold |
| `properties.count.maximum` | integer | Numeric upper bound; the tool Schema supplies the threshold |
| `properties.count.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.region` | object | Schema for argument `region`; its containing object's `required` list determines whether it is mandatory |
| `properties.region.description` | string | Description of this argument or nested field |
| `properties.region.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.safeSearch` | object | Schema for argument `safeSearch`; its containing object's `required` list determines whether it is mandatory |
| `properties.safeSearch.description` | string | Description of this argument or nested field |
| `properties.safeSearch.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.url` | object | Schema for argument `url`; its containing object's `required` list determines whether it is mandatory |
| `properties.url.description` | string | Description of this argument or nested field |
| `properties.url.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.extractMode` | object | Schema for argument `extractMode`; its containing object's `required` list determines whether it is mandatory |
| `properties.extractMode.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.extractMode.enum` | [string] | Allowed values; actual members are supplied by the tool Schema |
| `properties.extractMode.description` | string | Description of this argument or nested field |
| `properties.extractMode.default` | string | Declared default for this argument; the previous table did not specify its value |
| `properties.maxChars` | object | Schema for argument `maxChars`; its containing object's `required` list determines whether it is mandatory |
| `properties.maxChars.description` | string | Description of this argument or nested field |
| `properties.maxChars.minimum` | integer | Numeric lower bound; the tool Schema supplies the threshold |
| `properties.maxChars.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.maxResults` | object | Schema for argument `maxResults`; its containing object's `required` list determines whether it is mandatory |
| `properties.maxResults.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.minScore` | object | Schema for argument `minScore`; its containing object's `required` list determines whether it is mandatory |
| `properties.minScore.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.from` | object | Schema for argument `from`; its containing object's `required` list determines whether it is mandatory |
| `properties.from.type` | string | Type of the value described by this Schema; the tool defines the actual type value |
| `properties.lines` | object | Schema for argument `lines`; its containing object's `required` list determines whether it is mandatory |
| `properties.lines.type` | string | Type of the value described by this Schema; the tool defines the actual type value |

#### Chat Completions response

A successful non-streaming response normally uses HTTP **200** and a chat completion object. This is the complete plain-text response example; identifiers, timestamp, text, and usage are illustrative values:

```json
{
  "id": "chatcmpl-example-001",
  "object": "chat.completion",
  "created": 1700000000,
  "model": "qwen-plus",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "Hello! How can I help you today?"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 12,
    "completion_tokens": 10,
    "total_tokens": 22
  }
}
```

| Field path | Type | Meaning and availability |
| --- | --- | --- |
| `id` | string | Identifier for this chat completion |
| `object` | string | Object type; normally `chat.completion` for a non-streaming completion |
| `created` | integer | Creation time as a Unix timestamp in seconds |
| `model` | string | Model used for the request |
| `choices` | array of objects | Returned candidate results |
| `choices[].index` | integer | Candidate index |
| `choices[].message` | object | Non-streaming assistant message |
| `choices[].message.role` | string | Returned message role; normally `assistant` |
| `choices[].message.content` | string / null | Content of a plain-text answer; a tool-call-only message may contain `null` |
| `choices[].message.reasoning_content` | string | Reasoning content, when supplied by the backend |
| `choices[].message.tool_calls` | array of objects | Requested tool calls, when present in the model output |
| `choices[].message.tool_calls[].id` | string | Tool call identifier, used as `tool_call_id` when returning the result |
| `choices[].message.tool_calls[].type` | string | Tool type; `function` for function calls |
| `choices[].message.tool_calls[].function` | object | Contains function `name` and JSON-string `arguments` |
| `choices[].message.tool_calls[].function.name` | string | Name of the function to invoke |
| `choices[].message.tool_calls[].function.arguments` | string | JSON-encoded tool arguments; parse them before use |
| `choices[].finish_reason` | string / null | Stop reason, commonly `stop`, `length`, or `tool_calls`; intermediate streaming events may use `null` |
| `choices[].delta` | object | Streaming message delta; accumulate the fields as they arrive |
| `choices[].logprobs` | object / null | Token probability information, when supplied by the backend |
| `usage` | object | Request usage statistics; availability and timing in a stream depend on the backend |
| `usage.prompt_tokens` | integer | Input tokens |
| `usage.completion_tokens` | integer | Generated tokens |
| `usage.total_tokens` | integer | Total tokens for the request |
| `usage.prompt_tokens_details.cached_tokens` | integer | Cached input tokens, when supplied by the backend |
| `usage.completion_tokens_details.reasoning_tokens` | integer | Reasoning tokens, when supplied by the backend |
| `usage.prompt_cache_hit_tokens` / `usage.prompt_cache_miss_tokens` | integer | Input cache hit and miss counts, when supplied by the backend |
| `system_fingerprint` | string | System fingerprint, when supplied by the backend |

For streaming, set `stream: true`, read `choices[].delta` from SSE `data:` events, and continue through `data: [DONE]`. HTTP 200 or the first event indicates that the response has started; still check subsequent errors and the completion marker.

<a id="model-api-02"></a>

### OpenAI Responses example

```bash
curl http://localhost:8080/v1/responses \
  -H "Authorization: Bearer <linkmind-api-key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"qwen-plus","instructions":"You are a technical assistant. Answer concisely.","input":"Explain the purpose of routing in one sentence."}'
```

<a id="model-api-03"></a>

### Anthropic Messages requests and responses

The Messages endpoint calls enabled chat models in LinkMind and supports regular JSON responses and SSE streaming.

Use `Content-Type: application/json`. When authentication is required, send `x-api-key: <linkmind-api-key>` or `Authorization: Bearer <linkmind-api-key>`. If both are supplied, a non-empty `x-api-key` takes precedence.

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `model` | string | Yes | A currently enabled chat model name, available from `GET /v1/models` |
| `max_tokens` | integer | Yes | Maximum number of output tokens |
| `messages` | array | Yes | A non-empty message list; each message contains `role` and `content`, typically with the `user` or `assistant` role |
| `system` | string / array | Yes in the current version | System instructions as text or an array of text content blocks, such as `[{"type":"text","text":"Answer concisely."}]` |
| `stream` | boolean | No | Defaults to `false`; set to `true` for SSE streaming |

**Current version limitation:** omitting `system` or setting it to `null` returns HTTP 400 with the error message `message content is required`. Supply `system` explicitly, as shown in the example below.

Request example:

```bash
curl http://localhost:8080/v1/messages \
  -H "Authorization: Bearer <linkmind-api-key>" \
  -H "anthropic-version: 2023-06-01" \
  -H "Content-Type: application/json" \
  -d '{"model":"qwen-plus","max_tokens":128,"system":"You are a technical assistant. Answer concisely.","messages":[{"role":"user","content":"Hello"}]}'
```

#### Non-streaming response

When `stream` is `false` or omitted, the endpoint returns Messages-format JSON with the following main fields:

| Field | Type | Description |
| --- | --- | --- |
| `id` | string | Identifier for this response |
| `type` | string | Always `"message"` |
| `role` | string | Always `"assistant"` |
| `model` | string | Model used for this response |
| `content` | array | Response content blocks; a text block has `type: "text"` and its text is in the `text` field |
| `stop_reason` | string | Stop reason, such as normal completion (`end_turn`), output limit (`max_tokens`), or a tool call (`tool_use`) |
| `usage` | object | Token usage, including `input_tokens` and `output_tokens` |

To read the text, iterate over `content` and extract `text` from blocks with `type: "text"`.

#### Streaming response

With `stream: true`, the response uses `text/event-stream`. Clients need to handle these events:

| Event | Meaning |
| --- | --- |
| `message_start` | Starts the message with initial metadata such as its identifier, role, and model |
| `content_block_start` | Starts a content block |
| `content_block_delta` | Adds content to a block; text deltas are in `delta.text`, and tool argument deltas are in `delta.partial_json` |
| `content_block_stop` | Ends a content block |
| `message_delta` | Updates message-level information, including the stop reason and usage |
| `message_stop` | Ends the entire message |

Collect deltas by content block `index` and continue reading until `message_stop`. Parse responses using Messages content blocks and events; the Chat Completions `choices` field and `data: [DONE]` terminator do not apply to this endpoint.

For Claude Code environment variables, verification, and troubleshooting, see the [Claude Code integration guide](best-practices/cli/claude-code.md#english).

The public protocol stays at LinkMind. Provider-specific protocol and model mapping belong only in `lagi.yml`, not in every client.

## 4. Embeddings and multimodal capabilities

Every capability needs an enabled backend. This is a minimal embedding configuration; replace `model_name` with an embedding model supported by the chosen provider.

```yaml
functions:
  embedding:
    - backend: qwen
      type: Qwen
      api_key: ${QWEN_API_KEY}
      model_name: text-embedding-v3
      enable: true
      priority: 100
```

```bash
curl http://localhost:8080/v1/embeddings \
  -H "Authorization: Bearer <linkmind-api-key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"text-embedding-v3","input":["LinkMind embedding verification"]}'
```

The successful response contains a two-dimensional vector array in `data`. Before using it for a knowledge base, confirm that its dimension matches the target vector index. See [Embedding generation](#embedding-generation) below for complete requests, batch input, and response fields.

<a id="media-endpoints"></a>

| Capability | Function block / endpoint | Main input |
| --- | --- | --- |
| Speech recognition and TTS | `speech2text` / `text2speech`; `POST /audio/speech2text`, `GET /audio/text2speech` | Audio file or text |
| Image understanding, generation, enhancement | `image2text`, `text2image`, `image2enhance`; `POST /image/text2image`, `POST /image/image2text`, `POST /image/image2enhance`, `POST /image/image2ocr` | Image URL/file or prompt |
| Video generation, tracking, enhancement | `text2video`, `image2video`, `video2track`, `video2enhance`; image-to-video: `POST /image/image2video` (alias `POST /image/generateVideo`); video tracking and enhancement: `POST /video/video2tracking`, `POST /video/video2enhance` | Prompt, image, or video |
| Document OCR, extraction, structuring | `doc2ocr`, `doc2ext`, `doc2struct`; `POST /ocr/doc2ocr`, `POST /doc/doc2ext`, `POST /doc/doc2struct` | Document file |
| Natural language to SQL | `text2sql`, `POST /sql/text2sql` | Data question and accessible database schema |

Multimodal backend entries normally include `backend`, `model`, `enable`, and `priority`. Test small supported input first, then increase concurrency or connect production documents.

<a id="function-backends"></a>

### Multimodal and document backend configuration

The sample groups 18 functions under one `functions` key. Select the entries you use, point each `backend` at a configured provider, and merge them into the existing mapping. Set `speech2clone.others` to the speaker ID. `text2qa` takes a single object.

| Field | Meaning |
| --- | --- |
| `backend` | Name of the model backend defined under `models` |
| `model` | Model ID handled by that backend |
| `enable` | Whether this function backend is active |
| `priority` | Selection priority |
| `stream` | Streaming flag where applicable |
| `protocol` | Completion or response protocol for chat |
| `others` | Extra provider-specific value, such as a speaker ID |
| `filter` | Optional filter override |
| `router` | Optional router override |
| `concurrency` | Optional concurrency limit |

```yaml
functions:
  # Translate
  translate:
    - backend: ernie
      model: translate
      enable: true
      priority: 10
  # Speech To Text
  speech2text:
    - backend: qwen
      model: asr
      enable: true
      priority: 10
  # Text To Speech
  text2speech:
    - backend: landing
      model: tts
      enable: true
      priority: 10
  # Speech Clone
  speech2clone:
    - backend: doubao
      model: openspeech
      enable: true
      priority: 10
      others: your-speaker-id
  # Text To Image
  text2image:
    - backend: spark
      model: tti
      enable: true
      priority: 10
  # Image To Text
  image2text:
    - backend: ernie
      model: Fuyu-8B
      enable: true
      priority: 10
  # Image OCR
  image2ocr:
    - backend: qwen
      model: ocr
      enable: true
      priority: 10
  # Image Enhancement
  image2enhance:
    - backend: ernie
      model: enhance
      enable: true
      priority: 10
  # Text To Video
  text2video:
    - backend: landing
      model: video
      enable: true
      priority: 10
  # Image To Video
  image2video:
    - backend: qwen
      model: vision
      enable: true
      priority: 10
  # Video Tracking
  video2track:
    - backend: landing
      model: video
      enable: true
      priority: 10
  # Video Enhancement
  video2enhance:
    - backend: qwen
      model: vision
      enable: true
      priority: 10
  # Document OCR
  doc2ocr:
    - backend: qwen
      model: qwen-vl-ocr
      enable: true
      priority: 15
  # Document Instruction Extraction
  doc2instruct:
    - backend: landing
      model: cascade
      enable: true
      priority: 10
  # Text To SQL
  text2sql:
    - backend: landing
      model: cascade
      enable: true
      priority: 10
  # Document Content Extraction
  doc2ext:
    - backend: landing
      model: cascade
      enable: true
      priority: 10
  # Document Structuring
  doc2struct:
    - backend: landing
      model: cascade
      enable: true
      priority: 10
  # Text To QA
  text2qa:
    enable: true
    model: cascade
```

#### Embedding

```yaml
functions:
  embedding:
    - backend: qwen
      type: Qwen
      api_key: your-api-key
      model_name: text-embedding
      api_endpoint: https://your-endpoint.example.com
      secret_key: your-secret-key
      model_path: /path/to/local-embedding.model
```

Common fields:

| Field | Meaning |
| --- | --- |
| `backend` | Provider name |
| `type` | Provider label |
| `api_key` | API key |
| `model_name` | Embedding model name |
| `api_endpoint` | Remote endpoint |
| `secret_key` | Optional provider secret |
| `model_path` | Local embedding model path |

<a id="model-api-19"></a>

### Embedding generation

`POST /v1/embeddings` accepts an array of texts and returns a list of vectors. The current endpoint parses `input` as a string array. Even one text must be supplied as `["text"]`, rather than as a single string.

#### Embedding request parameters and batch example

This complete single-input example is retained from the previous documentation:

```json
{
  "model": "text-embedding",
  "input": [
    "Hello LinkMind"
  ]
}
```

| Field | Location | Type | Required | Description |
| --- | --- | --- | --- | --- |
| Request body | body | object | Yes | JSON request object |
| `Content-Type` | header | string | Recommended | `application/json`, identifying the request body content type |
| `Authorization` | header | string | As required by deployment authentication | `Bearer <linkmind-api-key>`; supply a valid client key when authentication is enabled |
| `input` | body | string[] | Yes | Texts to embed. Supply a non-empty array of non-empty texts; use an array for one text as well, and place multiple texts in the same array for batch input |
| `model` | body | string | No | Retains the model-name field and example value from the previous documentation. The current endpoint does not use it to select a backend or override the actual model; it uses the deployment's embedding configuration |

The previous table marked `model` as required and described it as the model used for the call. The current implementation creates the embedding backend from deployment configuration without using the request's `model` for selection. Changing only that request field does not switch the actual backend or model. Check the effective embedding configuration when investigating model, key, or dimension issues.

Example batch request with two texts:

```bash
curl --request POST "http://localhost:8080/v1/embeddings" \
  -H "Authorization: Bearer <linkmind-api-key>" \
  -H "Content-Type: application/json" \
  --data-raw '{"model":"text-embedding","input":["Hello LinkMind","How does model routing work?"]}'
```

Batch-size and per-text length limits depend on the selected backend. Start with short texts and small batches. The minimal call above also uses a one-element `input` array.

#### Complete embedding response and field descriptions

A normal response uses HTTP **200** with a JSON body. This complete single-input response example is retained from the previous documentation. Its four numbers illustrate the structure; the effective model determines the actual vector dimension:

```json
{
  "status": "success",
  "data": [
    [
      0.12,
      -0.03,
      0.44,
      0.08
    ]
  ]
}
```

| Field | Type | Meaning and availability |
| --- | --- | --- |
| `status` | string | `success` when the backend returns a non-empty vector list, or `failed` when it returns an empty list |
| `data` | number[][] | Embedding vectors in a successful response; the outer array is the result list. The current failure branch omits this field |
| `data[]` | number[] | One result vector, whose length is its actual dimension. The four-number example does not define a fixed dimension |
| `data[][]` | number | A vector component returned by the backend; values may be positive, negative, or zero |

When the backend returns an empty list, the current endpoint returns this JSON, usually still with HTTP **200**:

```json
{
  "status": "failed"
}
```

The endpoint decides between `success` and `failed` based on whether the returned list is empty. It does not check the result count, whether each vector is non-empty, or whether dimensions are consistent at this point. Inspect the actual `data` contents. Request parsing, configuration, or upstream-call exceptions do not guarantee the same failure JSON; inspect the HTTP status and logs as well.

#### Batch verification and troubleshooting

1. Run the single-text example first. Confirm `status: "success"`, one non-empty numeric vector in `data`, and its actual dimension.
2. Run the two-text batch example. Check that the vector count matches the input count and that all vectors are non-empty with equal dimensions. Verify input-to-result mapping according to the selected backend's ordering contract; if needed, submit each text separately and compare the results.
3. Before connecting a knowledge base, confirm that the actual embedding model and dimensions match the target vector collection. The request's `model` field cannot switch configuration to resolve a mismatch.
4. For `failed`, count mismatches, or empty vectors, check the input array, text-length and batch limits, effective embedding backend and model, key permissions, upstream connectivity, and server logs. Isolate the issue with short texts before restoring the intended batch size.

<a id="model-api-06"></a>

### Speech recognition (ASR)

`POST /audio/speech2text` accepts an audio binary stream and returns recognized text. Configure an available ASR backend, then verify it with a short recording whose content is known.

#### ASR request fields and upload example

| Field | Location | Type | Required | Description |
| --- | --- | --- | --- | --- |
| `model` | query | string | No | Registered ASR backend identifier; if omitted, empty, or `default`, the service selects the first enabled backend with `priority > 0` in descending priority order. An explicit identifier must resolve to a registered backend |
| `format` | query | string | No | Format of the uploaded audio, such as `wav`; the endpoint uses it as the saved audio file extension and uses `wav` when omitted. Recognized formats depend on the selected backend |
| `Content-Type` | header | string | Recommended | Use `application/octet-stream` for binary uploads |
| `Authorization` | header | string | As required by deployment authentication | `Bearer <linkmind-api-key>`; supply a valid client key when authentication is enabled |
| Request body | body | string(binary) | Yes | Raw audio file bytes |

Place the test audio at `./examples/audio/sample.wav`, or replace that path with the actual file path. Put `model` and `format` in the URL query string and upload the file with `--data-binary`:

```bash
curl --request POST \
  "http://localhost:8080/audio/speech2text?model=default&format=wav" \
  -H "Authorization: Bearer <linkmind-api-key>" \
  -H "Content-Type: application/octet-stream" \
  --data-binary @./examples/audio/sample.wav
```

To select a particular ASR backend, replace `model=default` with its registered identifier in your deployment. `format` describes the uploaded audio, not the recognition response format. An extension does not change the file's encoding; it should match the actual audio and a format supported by the backend.

#### ASR response and verification

A normal response uses HTTP **200** with a JSON body. This example uses the project's ASR success code, `20000000`:

```json
{
  "result": "Hello LinkMind",
  "status": 20000000
}
```

| Field | Type | Meaning and availability |
| --- | --- | --- |
| `result` | string | Recognized text on success; it may be absent on failure |
| `status` | integer | Recognition business status; the project defines `20000000` for success and `40000000` for failure. Some adapters may return upstream status codes |
| `task_id` | string | Recognition task identifier, when supplied by the backend |
| `message` | string | Status description or error message supplied by the backend |

The HTTP status describes HTTP-level handling; JSON `status` describes the recognition result. Check both statuses and use the recognized text to confirm that the call succeeded.

Verify the business status and that `result` contains the expected text, then compare it with the recording. For `null` responses or missing results, check the `model` identifier, backend registration, and the `enable` and positive `priority` required for default selection. For failure statuses, use `message` and logs to check backend configuration, authentication, network access, audio format, and sampling parameters. Supported codecs, sample rates, and channel counts depend on the selected ASR backend.

<a id="model-api-07"></a>

### Text to speech (TTS)

`GET /audio/text2speech` synthesizes speech from query parameters and returns audio bytes on success. Configure an available TTS backend, then verify it with a short text.

#### TTS request fields and download example

| Field | Location | Type | Required | Description |
| --- | --- | --- | --- | --- |
| `text` | query | string | Yes | Non-empty text to synthesize; URL-encode Chinese characters, spaces, and special characters |
| `model` | query | string | No | Registered TTS backend identifier; if omitted, empty, or `default`, the service selects the first enabled backend with `priority > 0` in descending priority order |
| `format` | query | string | No | Requested output audio format; whether it is honored, supported values, and the default depend on the selected backend |
| `Authorization` | header | string | As required by deployment authentication | `Bearer <linkmind-api-key>`; supply a valid client key when authentication is enabled |

Use `--get` for a GET request and `--data-urlencode` to encode query parameters. This example requests `wav`, saves response headers to `speech.headers`, and saves the response body to `speech.audio`:

```bash
curl --get "http://localhost:8080/audio/text2speech" \
  -H "Authorization: Bearer <linkmind-api-key>" \
  --data-urlencode "text=Hello, LinkMind." \
  --data-urlencode "model=default" \
  --data-urlencode "format=wav" \
  --dump-header speech.headers \
  --output speech.audio
```

Replace `model=default` with a registered TTS backend identifier when needed, and adjust or omit `format` according to that backend's support. Current adapters handle formats differently: the Alibaba adapter requests `wav`, while the VolcEngine adapter reads `format` and defaults to `mp3` when it is unspecified. The selected backend determines the actual audio format.

#### TTS response and verification

Successful responses normally use HTTP **200** with raw audio bytes in the body. The current public endpoint sets the audio response `Content-Type` to `audio/mpeg` without adjusting it to `format`. Confirm the actual audio encoding before choosing a file extension and player.

When the backend returns the project's TTS failure code `40000000`, the endpoint returns an `application/json` error object, for example:

```json
{
  "status": 40000000,
  "message": "Speech synthesis failed."
}
```

| Field | Type | Meaning and availability |
| --- | --- | --- |
| `status` | integer | Business status in the failure branch; the branch above uses `40000000` |
| `message` | string | Backend status description or error message, when supplied; the example is not a fixed error message |
| `task_id` | string | Synthesis task identifier, when supplied by the backend |
| `result` | string | Result information, when supplied by the backend; it may be absent on failure |

A failure JSON response may also use HTTP 200. First inspect the HTTP status and `Content-Type` in `speech.headers`. For JSON, read the business status and error information in the body. After confirming valid audio, open it in a player supporting its actual format and check that the spoken content matches `text`.

For missing or unplayable audio, check URL encoding of `text`, the TTS backend identifier and enabled state, a positive `priority` for default selection, backend authentication, and format support. If logs report a failure to download generated audio, also check network access and permissions from the LinkMind service to the audio storage address; this branch may return HTTP 500 or incomplete audio.

<a id="model-api-08"></a>

### Image understanding

`POST /image/image2text` accepts an uploaded image and returns a description, classification, and image result address. `POST /image/imageToText` is a compatible alias. Actual output capabilities depend on the image understanding backend configured in the deployment.

#### Image understanding request and upload example

| Field | Location | Type | Required | Description |
| --- | --- | --- | --- | --- |
| Request body | body | object (multipart form) | Yes | Contains the uploaded image file |
| `file` | form-data | string(binary) | Yes | Image file bytes; supply an image with a filename and extension |
| `Content-Type` | header | string | Yes, generated by the client | `multipart/form-data` with a `boundary` matching the body; cURL's `--form` generates it automatically |
| `Authorization` | header | string | As required by deployment authentication | `Bearer <linkmind-api-key>`; supply a valid client key when authentication is enabled |

Prepare an image with known content as `sample.png`, or replace the path with your actual image:

```bash
curl --request POST "http://localhost:8080/image/image2text" \
  -H "Authorization: Bearer <linkmind-api-key>" \
  --form "file=@sample.png"
```

This example uploads one image and lets cURL generate the multipart header and boundary. To use the compatible alias, change the URL path to `/image/imageToText`.

#### Legacy parameter compatibility

The following fields and example values are retained from the previous documentation. The current endpoint neither reads these four fields nor passes them to the image understanding backend; including them as form fields or query parameters does not affect this endpoint's processing:

| Legacy field | Example value | Current support |
| --- | --- | --- |
| `model` | `default` | No effect; the endpoint selects an available image understanding backend from the deployment configuration |
| `emotion` | `default` | Not read or forwarded |
| `text` | `Please describe the image.` | Not forwarded as an image understanding prompt |
| `category` | `default` | Not read or forwarded |

#### Image understanding response and verification

A normal response usually uses HTTP **200** with a JSON body. This complete response example uses illustrative field values and an example address:

```json
{
  "status": "success",
  "classification": "cat",
  "caption": "A cat sitting on a sofa.",
  "samUrl": "https://example.com/segmented-image.png"
}
```

| Field | Type | Meaning and availability |
| --- | --- | --- |
| `status` | string | Call status, such as `success` or `failed`; other fields may be absent on failure |
| `classification` | string | Image category from the backend; the current endpoint fills in an empty string when no category is supplied |
| `caption` | string | Image recognition or description text; the endpoint attempts to translate it into Chinese, so use the text actually returned |
| `samUrl` | string | Image result or preview address from the backend; when it is `null`, the endpoint copies the original image and supplies a relative preview path such as `static/img/split/<id>.png` |

`samUrl` may point to a segmentation result or a copy of the original image. Its presence alone does not establish that segmentation occurred. Resolve relative paths against the deployment's application root URL.

Check the business status, compare `caption` with the image content, and verify that the returned image address is accessible. An empty `classification` string alone does not indicate failure. When segmentation is expected, also verify backend support and inspect the returned image content.

For an empty response, `failed` status, or an HTTP error, first check that the request is multipart and contains a valid image file whose extension and size meet deployment requirements. Then check the image understanding backend, authentication, network access, and logs. HTTP 200 alone does not replace checking the recognition result.

<a id="model-api-09"></a>

### Image generation (text to image)

`POST /image/text2image` generates images from a prompt. `POST /v1/images/generations` uses the same generation handler; both endpoints accept JSON requests and return JSON results. Configure an available image generation backend before verifying the generated output.

#### Image generation request fields and example

| Field | Location | Type | Required | Description |
| --- | --- | --- | --- | --- |
| `Content-Type` | header | string | Yes | Use `application/json` |
| `Authorization` | header | string | As required by deployment authentication | `Bearer <linkmind-api-key>`; supply a valid client key when authentication is enabled |
| Request body | body | object | Yes | JSON object describing the image generation request |
| `prompt` | body | string | Yes | Description of the image to generate; supply a non-empty prompt |
| `model` | body | string | No | Registered image generation backend identifier; if omitted or unmatched, the current implementation uses a default available backend |

Complete minimal request body:

```json
{
  "prompt": "A cat sitting on a sofa."
}
```

Corresponding cURL request:

```bash
curl --request POST "http://localhost:8080/image/text2image" \
  -H "Authorization: Bearer <linkmind-api-key>" \
  -H "Content-Type: application/json" \
  -d '{"prompt":"A cat sitting on a sofa."}'
```

To use the other endpoint, change the URL to `http://localhost:8080/v1/images/generations` and keep the same body. To select a backend, add `model` to the JSON with its registered identifier. The current endpoint attempts to translate `prompt` into English and uses the returned translation when one is available.

#### Image generation response and verification

A normal response usually uses HTTP **200** with an image generation result as JSON. This is the complete URL-based response example; its timestamp and image address are illustrative, with `created` expressed in seconds:

```json
{
  "created": 1700000000,
  "data": [
    {
      "url": "https://example.com/generated-image.png"
    }
  ]
}
```

| Field | Type | Meaning and availability |
| --- | --- | --- |
| `created` | integer | Creation time of the image generation result; units and population differ between current backends, as described below |
| `data` | array of objects | Image result list; successful generation should include usable image data |
| `data[].url` | string | Optional generated image address; backends returning only Base64 content may omit it |
| `data[].base64Image` | string | Optional Base64 image content; this is the field name used by the current result structure and it may coexist with `url` |
| `dataType` | string | Result type hint supplied by the backend, such as `base64`; still inspect the actual image content in each `data` entry |

**Timestamp behavior:** `created` describes creation of the image generation result. The current Landing adapter uses Unix seconds, StableDiffusion uses Unix milliseconds, and Spark leaves the field at its default `0` when it is not populated. Baidu and cascade adapters retain the upstream value. The endpoint does not normalize these values; interpret them according to the selected backend and treat `0` as an unavailable creation time.

Check the HTTP status and `data` first. For a `url`, verify that the address is accessible and returns a valid image. For `base64Image`, decode the content and verify that it opens as an image. Finally, compare the generated content with the prompt. A Base64-only response without a URL can also be a valid result.

For `null`, empty results, or errors, check that the image generation backend is configured and enabled, that `model` selected the intended backend, and that backend authentication, quota, network access, and service logs show no failures. If a generated image URL cannot be read, also check storage access permissions and URL expiry.

<a id="model-api-10"></a>

### Image enhancement

`POST /image/image2enhance` accepts an uploaded image and returns its enhanced result address. `POST /image/enhanceImage` is a compatible alias. Actual enhancement capabilities and effects depend on the backend configured in the deployment.

#### Image enhancement request and upload example

| Field | Location | Type | Required | Description |
| --- | --- | --- | --- | --- |
| Request body | body | object (multipart form) | Yes | Contains the uploaded image file |
| `file` | form-data | string(binary) | Yes | Image bytes to enhance; supply an image with a filename and extension |
| `Content-Type` | header | string | Yes, generated by the client | `multipart/form-data` with a `boundary` matching the body; cURL's `--form` generates it automatically |
| `Authorization` | header | string | As required by deployment authentication | `Bearer <linkmind-api-key>`; supply a valid client key when authentication is enabled |

Prepare an image to enhance as `sample.png`, or replace the path with your actual file:

```bash
curl --request POST "http://localhost:8080/image/image2enhance" \
  -H "Authorization: Bearer <linkmind-api-key>" \
  --form "file=@sample.png"
```

This example uploads one image and lets cURL generate the multipart header and boundary. To use the compatible alias, change the URL path to `/image/enhanceImage`.

#### Image enhancement legacy parameter compatibility

The following fields and example values are retained from the previous documentation. The current endpoint neither reads these four fields nor passes them to the enhancement backend; supplying them as form fields or query parameters does not change this endpoint's enhancement processing:

| Legacy field | Example value | Current support |
| --- | --- | --- |
| `model` | `default` | No effect; the deployment configuration selects the enhancement backend |
| `emotion` | `default` | Not read or forwarded |
| `text` | `Please enhance this image.` | Not forwarded as an enhancement instruction |
| `category` | `default` | Not read or forwarded |

#### Image enhancement response and verification

A normal response usually uses HTTP **200** with a JSON body. This complete response example uses an application-relative address to match the current implementation. The filename is illustrative; use the actual returned value:

```json
{
  "status": "success",
  "enhanceImageUrl": "static/img/enhance/enhanced-image.png"
}
```

| Field | Type | Meaning and availability |
| --- | --- | --- |
| `status` | string | Processing status: `success` marks success and `failed` marks failure; still verify that the result image can be read |
| `enhanceImageUrl` | string | Enhanced image address in a successful response; normal results currently use a relative path under `static/img/enhance/`. It may be absent on failure |

Append the relative path to the deployment's application root URL. For an application root of `http://localhost:8080/`, the example result is accessed at `http://localhost:8080/static/img/enhance/enhanced-image.png`. Include any deployment context path in the application root URL.

Check the business status, then access `enhanceImageUrl`. Confirm that the file downloads, opens as an image, and shows the expected enhancement compared with the input. Neither `status: "success"` nor HTTP 200 replaces verification of the result file.

For an empty response, `failed` status, or an HTTP error, check the multipart request, file extension and size, enhancement backend configuration, authentication, and logs. If an address is returned but the image cannot be read, also check static resource paths, result directory permissions, and server errors while downloading or saving the enhanced image.

<a id="model-api-11"></a>

### Image-to-video

`POST /image/image2video` accepts an uploaded image and returns the generated video address. `POST /image/generateVideo` is a compatible alias. Configure an available image-to-video backend in the deployment, then upload an image to verify the result.

#### Image-to-video request and upload example

| Field | Location | Type | Required | Description |
| --- | --- | --- | --- | --- |
| Request body | body | object (multipart form) | Yes | Contains the uploaded image file |
| `file` | form-data | string(binary) | Yes | Image bytes used to generate a video; supply an image with a filename and extension |
| `Content-Type` | header | string | Yes, generated by the client | `multipart/form-data` with a `boundary` matching the body; cURL's `--form` generates it automatically |
| `Authorization` | header | string | As required by deployment authentication | `Bearer <linkmind-api-key>`; supply a valid client key when authentication is enabled |

Prepare an image as `sample.png`, or replace the path with your actual file:

```bash
curl --request POST "http://localhost:8080/image/image2video" \
  -H "Authorization: Bearer <linkmind-api-key>" \
  --form "file=@sample.png"
```

This example uploads one image and lets cURL generate the multipart header and boundary. To use the compatible alias, change the URL path to `/image/generateVideo`. The endpoint returns result JSON; generation and result file download occur during server-side request handling, so allow enough time for the request to finish.

#### Image-to-video legacy parameter compatibility

The following fields and example values are retained from the previous documentation. The current endpoint neither reads these four fields nor passes them to the image-to-video backend; supplying them as form fields or query parameters does not change this endpoint's video generation processing:

| Legacy field | Example value | Current support |
| --- | --- | --- |
| `model` | `default` | Not used to select a model for this request; configure an available image-to-video backend |
| `emotion` | `default` | Not read or forwarded |
| `text` | `Generate a short video from this image.` | Not forwarded as a video generation prompt |
| `category` | `default` | Not read or forwarded |

#### Image-to-video response and verification

A normal response usually uses HTTP **200** with a JSON body. This complete response example uses an application-relative address to match the current implementation. The filename is illustrative; use the actual returned value:

```json
{
  "status": "success",
  "svdVideoUrl": "static/img/svd/generated-video.mp4"
}
```

| Field | Type | Meaning and availability |
| --- | --- | --- |
| `status` | string | Processing status: `success` marks success and `failed` marks failure; still verify that the result video can be read and played |
| `svdVideoUrl` | string | Generated video address in a successful response; normal results currently use a relative path under `static/img/svd/`. It may be absent on failure |

Append the relative path to the deployment's application root URL. For an application root of `http://localhost:8080/`, the example result is accessed at `http://localhost:8080/static/img/svd/generated-video.mp4`. Include any deployment context path in the application root URL.

Check the business status, then access `svdVideoUrl`. Confirm that the file downloads, plays as a video, and contains the expected generated content. Neither `status: "success"` nor HTTP 200 replaces verification of the result file.

For an empty response, `failed` status, or an HTTP error, check the multipart request, image extension and size, image-to-video backend configuration, authentication, and logs. If an address is returned but the video cannot be read or played, also check static resource paths, result directory permissions, server errors while downloading the result file, and whether the file is a valid video.

<a id="model-api-12"></a>

### Video tracking

`POST /video/video2tracking` accepts an uploaded video and returns the tracked video result address. `POST /video/motInference` is a compatible alias. Configure an available video tracking backend in the deployment, then verify it with a short video whose content is known.

#### Video tracking request and upload example

| Field | Location | Type | Required | Description |
| --- | --- | --- | --- | --- |
| Request body | body | object (multipart form) | Yes | Contains the uploaded video file |
| `file` | form-data | string(binary) | Yes | Video bytes to process for tracking; supply a video with a filename and extension |
| `Content-Type` | header | string | Yes, generated by the client | `multipart/form-data` with a `boundary` matching the body; cURL's `--form` generates it automatically |
| `Authorization` | header | string | As required by deployment authentication | `Bearer <linkmind-api-key>`; supply a valid client key when authentication is enabled |

The previous documentation represented the local example file as follows:

```yaml
file: file://./examples/video/sample.mp4
```

The actual request must upload the file contents as multipart data. Replace the example path with your actual video file:

```bash
curl --request POST "http://localhost:8080/video/video2tracking" \
  -H "Authorization: Bearer <linkmind-api-key>" \
  --form "file=@./examples/video/sample.mp4"
```

The `@` in `--form` reads and uploads the local file contents. The `file://` notation above is only the previous documentation's file reference; sending it as a plain text field does not replace a file upload. To use the compatible alias, change the URL path to `/video/motInference`. This example uploads one video and lets cURL generate the multipart header and boundary.

#### Video tracking response and verification

A normal response usually uses HTTP **200** with a JSON body. This example retains the previous response's `status` and `data` fields and adds the `type` returned by the current implementation. The result address is application-relative to match the implementation, and the filename is illustrative:

```json
{
  "status": "success",
  "data": "static/video/tracked-video.mp4",
  "type": "mot"
}
```

| Field | Type | Meaning and availability |
| --- | --- | --- |
| `status` | string | Processing status: `success` marks success and `failed` marks failure; still verify that the result video can be read and played |
| `data` | string | Tracked video result address in a successful response; normal results currently use a relative path under `static/video/`. It may be absent on failure |
| `type` | string | Result type in a successful response; the video tracking endpoint currently returns `mot`. It may be absent on failure |

Append the relative path to the deployment's application root URL. For an application root of `http://localhost:8080/`, the example result is accessed at `http://localhost:8080/static/video/tracked-video.mp4`. Include any deployment context path in the application root URL. Use the actual returned `data` value when accessing the result.

Check the business status, then access `data`. Confirm that the file downloads, plays as a video, and contains the expected tracking result compared with the input video. Neither `status: "success"` nor HTTP 200 replaces verification of the result file.

In the current implementation, the endpoint can return `{"status":"failed"}` without `data` or `type` when the tracking service returns no result. Upload parsing failures or other exceptions can also produce an empty response or an HTTP error. Check the multipart request, file extension and size, video tracking backend configuration, authentication, and logs. If an address is returned but the video cannot be read or played, also check static resource paths, result directory permissions, server errors while downloading the result file, and whether the file is a valid video.

<a id="model-api-13"></a>

### Video enhancement

`POST /video/video2enhance` accepts an uploaded video and returns the enhanced video result address. `POST /video/mmeditingInference`, used in the previous documentation, is a compatible endpoint for the same operation. Configure an available video enhancement backend in the deployment, then verify it with a short video; enhancement capabilities and effects depend on the selected backend.

#### Video enhancement request and upload example

| Field | Location | Type | Required | Description |
| --- | --- | --- | --- | --- |
| Request body | body | object (multipart form) | Yes | Contains the uploaded video file |
| `file` | form-data | string(binary) | Yes | Video bytes to enhance; supply a video with a filename and extension |
| `Content-Type` | header | string | Yes, generated by the client | `multipart/form-data` with a `boundary` matching the body; cURL's `--form` generates it automatically |
| `Authorization` | header | string | As required by deployment authentication | `Bearer <linkmind-api-key>`; supply a valid client key when authentication is enabled |

The previous documentation represented the local example file as follows:

```yaml
file: file://./examples/video/sample.mp4
```

The actual request must upload the file contents as multipart data. Replace the example path with your actual video file:

```bash
curl --request POST "http://localhost:8080/video/video2enhance" \
  -H "Authorization: Bearer <linkmind-api-key>" \
  --form "file=@./examples/video/sample.mp4"
```

The `@` in `--form` reads and uploads the local file contents. The `file://` notation above is only the previous documentation's file reference; sending it as a plain text field does not replace a file upload. To use the previous endpoint, change the URL path to `/video/mmeditingInference`; the upload parameters and response structure are the same. This example uploads one video and lets cURL generate the multipart header and boundary.

#### Video enhancement response and verification

A normal response usually uses HTTP **200** with a JSON body. This example retains the previous response's `status` and `data` fields and adds the `type` returned by the current implementation. The result address is application-relative to match the implementation, and the filename is illustrative:

```json
{
  "status": "success",
  "data": "static/video/enhanced-video.mp4",
  "type": "mmediting"
}
```

| Field | Type | Meaning and availability |
| --- | --- | --- |
| `status` | string | Processing status: `success` marks success and `failed` marks failure; still verify that the result video can be read and played |
| `data` | string | Enhanced video result address in a successful response; normal results currently use a relative path under `static/video/`. It may be absent on failure |
| `type` | string | Result type in a successful response; the video enhancement endpoint currently returns `mmediting`. It may be absent on failure |

Append the relative path to the deployment's application root URL. For an application root of `http://localhost:8080/`, the example result is accessed at `http://localhost:8080/static/video/enhanced-video.mp4`. Include any deployment context path in the application root URL. Use the actual returned `data` value when accessing the result.

Check the business status, then access `data`. Confirm that the file downloads, plays as a video, and shows the expected enhancement compared with the input video. Neither `status: "success"` nor HTTP 200 replaces verification of the result file.

In the current implementation, the endpoint can return `{"status":"failed"}` without `data` or `type` when the enhancement service returns no result. Upload parsing failures or other exceptions can also produce an empty response or an HTTP error. Check the multipart request, file extension and size, video enhancement backend configuration, authentication, and logs. If an address is returned but the video cannot be read or played, also check static resource paths, result directory permissions, server errors while downloading the result file, and whether the file is a valid video.

## 5. Troubleshooting and next steps

- **Chat works but embeddings or OCR fail:** the function block may be missing, the selected model may not support the modality, or its key may have different permissions.
- **The model is shown but calls fail:** visibility is not upstream availability. Check provider key, model ID, balance, DNS/proxy, and logs in that order.
- **A stream has no completion marker:** record first event, last event, and connection-close times; inspect LinkMind-to-upstream SSE logs as well as the client.
- **To upload documents for Q&A:** continue to [Knowledge Base](knowledge-base_en.md), which includes storage, ingestion, retrieval, and RAG verification.
- **To add a new provider protocol or modality:** see [More Integrations and Custom Extensions](extensions_en.md).
