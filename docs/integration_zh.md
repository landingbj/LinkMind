# 系统集成与二次开发

业务系统可通过 HTTP API、协议兼容客户端或 Java `lagi-core` 使用 LinkMind。应优先以这些边界接入，而不是让每个业务服务都直接调用模型厂商。

[Java 配置](#sdk-config) · [Java 多模态示例](#sdk-media) · [Python 与 Go](#http-clients) · [业务上下文](#typed-business-context) · [频道](#social-channel-api) · [密钥管理](#provider-keys) · [计费](#orders-and-balances)

## 一、HTTP 集成

| 需求 | 接口 |
| --- | --- |
| OpenAI 兼容聊天 | `POST /v1/chat/completions` |
| OpenAI Responses | `POST /v1/responses` |
| Anthropic 兼容聊天 | `POST /v1/messages` |
| Embedding | `POST /v1/embeddings` |
| 模型列表 | `GET /v1/models` |
| 图片生成 | `POST /v1/images/generations` |
| Rerank | `POST /v1/rerank` |

启用鉴权后使用 LinkMind API Key：

```bash
curl http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer <linkmind-api-key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"Alibaba/qwen-plus","messages":[{"role":"user","content":"请总结这段文本"}]}'
```

OpenAI 客户端的 base URL 使用以 `/v1` 结尾的地址。Claude Code 的 `ANTHROPIC_BASE_URL` 使用服务根地址，客户端会自行追加 `/v1/messages`。按客户端要求选择协议，不要在已有兼容接口时手写协议转换。

## 二、Java `lagi-core` 集成

将本地模块安装或发布到团队 Maven 仓库：

```bash
mvn clean install -pl lagi-core -am -DskipTests
```

随后加入匹配版本依赖，并通过 classpath 或 `-Dlinkmind.config=/path/to/lagi.yml` 提供 `lagi.yml`：

```xml
<dependency>
  <groupId>com.landingbj</groupId>
  <artifactId>lagi-core</artifactId>
  <version>1.2.4</version>
</dependency>
```

业务应用需要嵌入 LinkMind 对象和扩展点时使用核心库；服务需要独立部署时使用 HTTP。当前构建命令、JAR/WAR 产物和直接导入说明见[部署、安全与级联组网](deployment-and-security_zh.md)。

<a id="sdk-config"></a>

### Java 应用如何找到配置

`ContextLoader.loadContext()` 首先尝试 JVM 属性 `-Dlinkmind.config=/path/to/lagi.yml`；未加载配置时依次尝试 classpath 根目录的 `lagi.yml`、当前工作目录下的 `lagi-web/src/main/resources/lagi.yml` 和 `../lagi-web/src/main/resources/lagi.yml`。后两个位置供源码运行。独立应用可将文件放进 `src/main/resources`，或在 JVM 启动时显式指定外部文件。至少启用一个模型和一个聊天后端。

<a id="sdk-chat"></a>

### Java 示例

初始化一次上下文，再调用对应服务：

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
message.setContent("请用一句话介绍 LinkMind。");
ChatCompletionRequest request = new ChatCompletionRequest();
request.setModel("qwen-plus");
request.setStream(false);
request.setMessages(Collections.singletonList(message));
ChatCompletionResult result = new CompletionsService().completions(request);
if (result != null && result.getChoices() != null && !result.getChoices().isEmpty()) {
    System.out.println(result.getChoices().get(0).getMessage().getContent());
}
```

业务上下文通过类型化 `extra_body` 挂载，不修改聊天消息结构：

```java
import ai.openai.pojo.ExtraBody;

ExtraBody extraBody = new ExtraBody();
extraBody.setUserId("u_1001");
request.setExtraBody(extraBody);
```

`AudioService` 用于 ASR/TTS，`ImageGenerationService` 用于文生图。音频、图片和视频的可运行示例位于 [`lagi-core/src/test/java/ai/example/Demo.java`](../lagi-core/src/test/java/ai/example/Demo.java)。

<a id="sdk-media"></a>

### 在 Java 中调用音频、图片和视频服务

#### 语音识别

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

#### 文字转语音

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

#### 文生图

```java
import ai.config.ContextLoader;
import ai.common.pojo.ImageGenerationRequest;
import ai.common.pojo.ImageGenerationResult;
import ai.image.service.ImageGenerationService;

ContextLoader.loadContext();

ImageGenerationService service = new ImageGenerationService();
ImageGenerationRequest request = new ImageGenerationRequest();
request.setPrompt("一个未来机场里的智能服务机器人");

ImageGenerationResult result = service.generations(request);
```

#### 其他仍可直接复用的 Java 示例

当前代码里还保留了这些 Java 示例：

- 看图理解
- 图片增强
- 图生视频
- 视频追踪
- 视频增强

可直接参考 [`lagi-core/src/test/java/ai/example/Demo.java`](../lagi-core/src/test/java/ai/example/Demo.java) 中的现成写法。

<a id="secondary-services"></a>

## 三、二次开发边界

- OpenAI 兼容聊天请求支持通过 `extra_body` 携带类型化业务元数据。
- 社交交互接口位于 `/socialChannel/*`。[社交频道接口表](#社交频道接口)
- API Key 管理接口位于 `/apiKey/*`，额度接口位于 `/credit/*`。

业务元数据、社交状态、认证和计费应放在模型适配器主链路之外，避免自定义逻辑影响正常补全、流式输出、路由或级联行为。

业务系统可分别接入[社交频道](#social-channel-api)、[Provider 密钥管理](#provider-keys)和[订单及余额](#orders-and-balances)。Skill 通过频道服务订阅、读取或发布消息，适配器继续处理模型协议转换。

<a id="typed-business-context"></a>

### 业务上下文的运行时传递

HTTP 调用应发送请求 JSON 中的 `extra_body` 对象；完整请求、`user_id`/`user` 别名及参数说明集中在[模型接入、路由与多模态](models-and-multimodal_zh.md#extra_body-业务上下文)。Java 调用继续使用上面的 `ExtraBody` 示例，通过 `setUserId(...)` 设置用户标识，再调用 `request.setExtraBody(...)`。这是携带带外业务上下文的方式，不需要自行改造聊天消息结构。

#### Skill 内部载荷

当运行时注入 Skill 时，可能把类型化对象序列化为内部 `<extra_body>...</extra_body>` 片段，使业务上下文随工具执行链路传递。当前 Skill 注入流程启用 `skills.enable`，并需要已加载的可用服务端 Skill 和 system 消息；未进入这一流程时，不保证生成该片段。

当前实现会检查 system 内容中是否已有可解析的内部载荷；没有时，才把请求中已有的 `extra_body` 序列化追加进去。请求的整个对象为 `null` 时不生成片段，已有可解析片段也不会在这一步被请求对象覆盖。内部序列化使用对象属性名，例如 `userId`，无需与调用方使用的 HTTP 别名逐字相同。

调用方仍只提交标准 JSON `extra_body`，不需要手工拼接这些标签或把业务标识写入 system 提示。内部片段用于运行时传递，不构成独立的公开请求格式。Skill 的启用和配置见[智能体接入、工具与任务编排](agents-and-tools_zh.md)。

#### 级联下的最近用户补齐

旧文档描述了启用 `general.cascade_api_address` 后补齐最近登录用户的能力。当前实现位于 `LandingAdapter` 的默认字段处理阶段，触发条件需同时满足：

1. 本次调用实际执行到 `LandingAdapter.setDefaultField`；不能将该行为推广到所有模型适配器或所有 Skill 调用路径。
2. 请求中的整个 `ExtraBody` 对象为 `null`，包括未提供该字段或显式传 `null`；仅缺少其中的用户字段不满足此条件。
3. 显式配置的 `general.cascade_api_address` 经规范化后非空，运行时据此判定级联代理已启用。配置含义见[部署、安全与级联组网](deployment-and-security_zh.md)。
4. 运行时能够读到非空的最近用户标识。当前从 `saas` 数据源的 `social_temp` 表中读取键 `last_login_user_id` 对应的值，去除首尾空白后使用；无记录、空值或读取异常时，不生成补齐对象。

条件满足时，新对象的 `userId` 使用该最近用户标识，`mateUrl` 取本机运行时基础地址 `ConfigUtil.getBaseUrl()`。`mateUrl` 并非直接复制 `general.cascade_api_address`；本机地址是否可达仍由部署配置决定。

| 请求与运行状态 | 该补齐步骤的行为 |
| --- | --- |
| 已提供非空对象且包含用户标识 | 保留请求对象，不用最近用户替换它 |
| 提供 `{}` 或只包含 `mate_url` 等其他字段 | 对象已存在，不补齐缺失的用户字段 |
| 对象为 `null`，但级联代理未启用 | 不读取最近用户进行补齐 |
| 对象为 `null`，级联代理已启用，但最近用户记录不可用 | 补齐结果仍为 `null` |
| 对象为 `null`，级联代理已启用，最近用户记录有效 | 新建对象，填入最近用户及本机基础地址 |

这是指定适配器路径上的本地兜底行为。最近用户来自本地保存的记录，不等同于对当前 HTTP 请求完成身份认证；显式业务用户标识也不能替代 API Key 或登录态。需要确定调用方身份的业务集成应明确传入相应用户上下文，并按部署要求完成鉴权。

排查上下文缺失时，先核对实际 HTTP 请求或 Java 对象，再检查适配器路径、Skill 配置、级联配置以及最近用户记录。不要仅凭开启了级联或传入一个空对象，就认定后续每个工具调用都已获得用户身份。

<a id="service-responses"></a>

### 二次开发接口的响应约定

旧文档中的二次开发通用 JSON 包装约定为 `{"status":"success|failed","msg":"...","data":...}`。这是结构示意：`success|failed` 表示两种业务状态，`...` 表示具体提示或数据，并非实际返回这些占位文字。对采用该约定的业务接口，除接口另有说明外，按以下含义处理；字段是否出现及实际数据结构仍以具体接口文档为准。

#### 字段与示例

| 字段 | 类型 | 出现条件与含义 |
| --- | --- | --- |
| `status` | string | 采用该包装的接口通常用 `success` 表示其定义的成功或受理状态，用 `failed` 表示业务失败；是否已完成后台工作需看具体接口约定 |
| `msg` | string | 可选的提示或错误信息，成功和失败响应都可能包含，也可能省略；不能只根据是否有该字段或某段提示文字判断结果 |
| `data` | 按接口定义的 JSON 值 | 可选的业务结果，可能是对象、数组、字符串或其他 JSON 值；不能统一假定为对象，也不能默认所有接口都返回该字段 |

以下成功和失败示例用于展示通用包装形式：

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

现有接口也可能只返回 `{"status":"success"}` 或 `{"status":"failed"}`，或增加 `task_id`、`progress`、`files`、分页统计等顶层字段。这些响应应按各自接口说明读取，不能把 `msg`、`data` 都当作必填字段。对于声明 `data` 为 JSON 字符串的接口，才需要再次解析它；直接返回对象或数组时不做这一步。具体实例见[知识库接口](knowledge-base_zh.md)。

#### 适用范围与客户端处理

| 响应类型 | 解析约定 |
| --- | --- |
| 采用上述包装的二次开发业务接口 | 分别检查 HTTP 结果、业务 `status` 以及该接口定义的数据字段；业务失败可能仍使用 HTTP **200** |
| OpenAI 兼容 Chat Completions、Responses 和 Anthropic Messages | 使用各自协议的 JSON 结构与错误格式，不额外添加 `status/msg/data` 外层包装；协议内部同名字段按该协议解释 |
| SSE 流式响应 | 按具体接口的事件类型和事件数据逐条解析；不能把整个流作为一个普通 JSON 包装读取 |
| 音频、文件等二进制响应 | 按该接口约定处理内容类型、二进制内容及错误响应；不以此 JSON 包装替代文件内容 |

不能仅根据 URL 含 `/v1` 就推断响应结构；例如当前 Embedding 接口的包装应按[模型接入、路由与多模态](models-and-multimodal_zh.md)中的实际契约处理。具体接口的明确说明优先于这里的通用约定。

客户端可按以下顺序判断结果：

1. 检查 HTTP 状态，并结合接口契约和响应内容类型选择 JSON、事件流或二进制解析方式。非 JSON 错误正文不应强行套用包装字段。
2. 对采用业务包装的接口，继续检查 `status`；HTTP **200** 只说明 HTTP 层返回成功状态，`status: "failed"` 仍是业务失败。只有 HTTP 状态检查不足以完成业务判断。
3. 按接口定义读取可选的 `msg`、`data` 及其他字段，核对实际类型。字段缺失应按该接口的出现条件处理，不能自行视为成功或自动补成同一种空对象。
4. 若成功响应仅表示已受理、已启动后台工作或流程已返回，再按该接口提供的进度或结果核验方法确认完成状态。

下方 Python 与 Go 示例调用 Chat Completions，响应按兼容协议处理；上述业务包装检查适用于明确采用该包装的接口。

<a id="http-clients"></a>

### Python 与 Go 示例

如部署启用鉴权，先设置 `LINKMIND_API_KEY`。两个示例都发送非流式请求。Python 打印正文，Go 打印完整 JSON，并保留失败响应供排查。

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

### 社交频道接口

GET 请求使用 URL 查询参数，POST 请求使用 `application/json` 请求体。`userId` 是社交用户的字符串标识，`channelId` 是数值频道 ID。成功响应均含 `status: "success"`，各接口返回的业务字段见末列。业务校验失败时，读取 `status: "failed"` 和 `msg`。

| 方法 | 路径 | 必填输入 | 用途与调用条件 | 成功返回 |
| --- | --- | --- | --- | --- |
| GET | `/socialChannel/runningMode` | 无 | 查看当前 LinkMind 节点状态，以及互动频道和 API Key 是否启用级联代理。 | `runningMode: "linkmind"`、`cascadeProxyEnabled`、`menuLoginRequired`，以及兼容字段 `isMateMode: false` |
| POST | `/socialChannel/registerUser` | `userId`、`username` | 将业务用户登记为社交用户。重复登记时保留已有记录。 | `created`，布尔值，仅新建记录时为 `true` |
| POST | `/socialChannel/saveLastLoginUser` | `userId` | 在本机保存最近登录用户，供启用级联的本地调用补齐用户上下文。 | `status` |
| POST | `/socialChannel/createChannel` | `userId`、`name` | 已注册用户创建频道，并自动成为所有者和订阅者。可传 `description` 保存描述。`isPublic` 仅作兼容输入，当前新建频道均启用并进入公开列表。 | `channelId` |
| POST | `/socialChannel/subscribe` | `userId`、`channelId` | 已注册用户订阅一个已启用的频道。 | `msg` 为 `subscribed` |
| POST | `/socialChannel/unsubscribe` | `userId`、`channelId` | 取消订阅。频道所有者必须保留自己的订阅。 | `msg` 为 `unsubscribed` |
| GET | `/socialChannel/listMyChannels` | `userId` | 查看该用户已订阅且已启用的频道。 | `data` 为 `SocialChannel[]` |
| GET | `/socialChannel/listPublicChannels` | 无 | 浏览已启用的公开频道。可传 `limit` 限制条数，`lang` 选择名称和描述的显示语言。 | `data` 为 `SocialChannel[]` |
| GET | `/socialChannel/listOwnedChannels` | `userId` | 查看该用户拥有的频道，包括已禁用的频道。 | `data` 为 `SocialChannel[]` |
| GET | `/socialChannel/getChannel` | `channelId` | 读取指定频道的名称、描述、所有者等信息。频道须存在且已启用。 | `data` 为单个 `SocialChannel` |
| GET | `/socialChannel/listMessages` | `userId`、`channelId` | 订阅者读取频道消息。可传 `limit`、`beforeId`、`startTime`、`endTime`。 | `data` 为 `SocialChannelMessage[]` |
| POST | `/socialChannel/sendMessage` | `userId`、`content`，以及 `channelId` 或 `channelName` | 向已订阅且已启用的频道发送文本。正整数 `channelId` 优先，按名称发送须在已订阅频道中唯一匹配。 | `messageId` |
| POST | `/socialChannel/toggleChannel` | `userId`、`channelId`、`enabled` | 仅所有者可操作。`enabled` 是 JSON 布尔值。 | `msg` 为 `enabled` 或 `disabled` |
| POST | `/socialChannel/deleteChannel` | `userId`、`channelId` | 仅所有者可删除频道。 | `msg` 为 `deleted` |

#### 消息分页

`listMessages` 和 `listPublicChannels` 的 HTTP 参数 `limit` 默认是 `50`。零或负数按 `50` 处理，上限为 `200`。`listMessages` 返回的 `data` 按消息 `id` 从大到小排列。可用 `startTime`、`endTime` 限定时间范围，两端均包含，格式为 `yyyy-MM-dd HH:mm:ss`。

首次读取时省略 `beforeId`。例如 `u_1001` 读取已订阅频道 `17`，一页得到消息 ID `105`、`102`、`99`。向前翻页时，将本页最小的正整数 `data[].id` 作为 `beforeId`，并沿用频道、用户和时间范围。下面的请求只会返回 ID 小于 `99` 的消息。成功响应的 `data` 为空数组时，已读完该范围。

```http
GET /socialChannel/listMessages?userId=u_1001&channelId=17&limit=3&beforeId=99
```

#### 级联与 Skill 接入

`general.cascade_api_address` 留空时，当前节点处理频道请求。显式配置上级地址后，频道请求按原路径转发到上级。其中三个操作始终由本机处理。`runningMode` 报告本机状态，`saveLastLoginUser` 保存本机最近用户，`cascadeConfig` 通过 GET 读取、通过 POST 保存本机配置。`models[].api_address` 仅配置模型上游地址。可通过 `runningMode` 返回的 `cascadeProxyEnabled` 确认代理状态。[互动级联配置与验证](deployment-and-security_zh.md#四互动级联与网络验证)

内置 `social-channel` Skill 根据当前业务用户的订阅列表将频道名称解析为 ID，再通过 `listMessages` 读取消息或通过 `sendMessage` 发布内容。遇到重名时，使用订阅列表返回的 `data[].id` 选择频道。订阅和消息存储由频道服务负责，Skill 复用同一套 HTTP 接口。读取脚本默认请求 `20` 条消息。[Skill 配置](agents-and-tools_zh.md#二配置-skillworker路由与过滤器) · [social-channel 使用说明](../lagi-web/src/main/resources/skills/social-channel/SKILL.md)

<a id="provider-keys"></a>

### Provider 密钥管理接口

| 方法与路径 | 输入 | 成功结果 |
| --- | --- | --- |
| `GET /apiKey/list` | 可选查询参数 `userId`，用于合并 Landing 用户密钥 | `data` 为 `ModelApiKey[]`，另有 `localApiKeyEditable` |
| `GET /apiKey/get` | 查询参数 `modelName` | `data.name`、`data.provider`、脱敏的 `data.api_key`；适用时有 `data.api_address` |
| `GET /apiKey/providers` | 无 | `data` 为可管理的 Provider 名称数组，另有 `localApiKeyEditable` |
| `POST /apiKey/add` | 本地 Provider 传 `name`、`provider`、`apiKey`；可附 `apiAddress`。Landing 传显示名 `name`、`provider` 和 `userId`，密钥由服务端签发。请求也接受 `model` | `msg: add success` |
| `POST /apiKey/delete` | 本地传 `provider` 及 `apiKey` 或 `id`。Landing 传 `provider`、`userId` 和实际 `apiKey` | `msg: delete success` |
| `POST /apiKey/toggle` | `id`、`provider`、布尔值 `enabled`；Landing 另传 `userId`。显式传 `true` 或 `false` | `msg: toggle success` |

GET 使用查询参数，POST 使用 JSON，检查响应中的 `status`，失败时读取 `msg`。`get` 会脱敏，`list` 中的对象可能携带完整密钥，避免输出列表到日志。新增本地密钥先保存为停用，启用后再核对实际模型配置。Provider 可配置 `api_key` 或 `api_keys`。`general.local_api_key_editable: false` 限制本地管理，并将可管范围收窄至 Landing。选择 Landing 密钥仍可能同步当前模型配置。

<a id="orders-and-balances"></a>

### 预支付、订单明细与余额

| 路径 | 方法 | 必要输入 | 用途 | 成功返回 |
| --- | --- | --- | --- | --- |
| `/credit/prepay` | POST | `lagiUserId`、`fee` | 发起预支付订单。 | `outTradeNo`、`qrCode`、`mWebUrl`、`totalFee`、`result` |
| `/credit/getChargeDetail` | GET | `outTradeNo` | 按支付订单号读取一条计费记录。 | `seq`、`userId`、`amount`、`time`、`status` |
| `/credit/getChargeDetailByUserId` | GET | `userId` | 按用户查询计费记录列表。 | `data: ChargeDetail[]` |
| `/credit/getCreditUserBalance` | GET | `userId` | 查询用户当前余额。 | `data.userId`、`data.balance` |
| `/credit/getChargeDetailBySeq` | GET | `seq` | 按流水号读取一条计费记录。 | `data: ChargeDetail` |

`POST /credit/prepay` 接收 JSON，例如 `{"lagiUserId":"u_1001","fee":"10"}`。这两个输入都是字符串。其余四个 GET 使用 URL 查询参数。预支付返回订单对象，按订单号查询直接返回 `ChargeDetail`；按用户、余额和按流水号查询从 `data` 读取。创建订单后，用 `outTradeNo` 核对状态，再用同一用户的余额或明细核对到账。应用可独立替换计费服务并保留这些接口。

<a id="service-objects"></a>

### 频道、密钥与额度的数据对象

| 对象 | 关键字段 |
| --- | --- |
| `SocialChannel` | `id`、`name`、`description`、`ownerUserId`、`isPublic`、`enabled`、`createdAt` |
| `SocialChannelMessage` | `id`、`channelId`、`channelName`、`userId`、`userName`、`content`、`createdAt` |
| `ModelApiKey` | `id`、`name`、`provider`、`apiKey`、`apiAddress`、`createdTime`、`userId`、`status` |
| `CreditUserBalance` | `userId`、`balance` |
| `ChargeDetail` | `seq`、`userId`、`amount`、`time`、`status` |

## 四、集成验证

1. 调用一条短文本请求，并记录请求时间与模型。
2. 核对响应、LinkMind 运行记录和实际路由。
3. 流式、工具、文件和自定义元数据逐项增加验证。
4. 生产客户端还应验证异常处理、超时、鉴权与计费行为。

聊天、Responses、Messages、Embedding 与多模态请求字段见[模型接入、路由与多模态](models-and-multimodal_zh.md)；向量与资料入库字段见[知识库](knowledge-base_zh.md)。标准 API 或兼容适配器不足时，参见[更多功能接入与自定义扩展](extensions_zh.md)。
