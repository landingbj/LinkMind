# 系统集成与二次开发

业务系统可通过 HTTP API、协议兼容客户端或 Java `lagi-core` 使用 LinkMind。应优先以这些边界接入，而不是让每个业务服务都直接调用模型厂商。

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

### Java 示例

初始化一次上下文，再调用对应服务：

```java
import ai.config.ContextLoader;
import ai.llm.service.CompletionsService;
import ai.openai.pojo.ChatCompletionRequest;
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
new CompletionsService().completions(request);
```

业务上下文通过类型化 `extra_body` 挂载，不修改聊天消息结构：

```java
import ai.openai.pojo.ExtraBody;

ExtraBody extraBody = new ExtraBody();
extraBody.setUserId("u_1001");
request.setExtraBody(extraBody);
```

`AudioService` 用于 ASR/TTS，`ImageGenerationService` 用于文生图。音频、图片和视频的可运行示例位于 [`lagi-core/src/test/java/ai/example/Demo.java`](../lagi-core/src/test/java/ai/example/Demo.java)。

## 三、二次开发边界

- OpenAI 兼容聊天请求支持通过 `extra_body` 携带类型化业务元数据。
- 社交交互接口位于 `/socialChannel/*`。
- 账号、API Key、额度接口位于 `/user/*`、`/apiKey/*`、`/credit/*`。

业务元数据、社交状态、认证和计费应放在模型适配器主链路之外，避免自定义逻辑影响正常补全、流式输出、路由或级联行为。

### Python 与 Go 示例

```python
import requests

response = requests.post(
    "http://localhost:8080/v1/chat/completions",
    headers={"Content-Type": "application/json"},
    json={"model": "qwen-plus", "messages": [{"role": "user", "content": "你好"}]},
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
    body := []byte(`{"model":"qwen-plus","messages":[{"role":"user","content":"你好"}]}`)
    request, _ := http.NewRequest("POST", "http://localhost:8080/v1/chat/completions", bytes.NewBuffer(body))
    request.Header.Set("Content-Type", "application/json")
    response, err := http.DefaultClient.Do(request)
    _ = response
    _ = err
}
```

## 四、集成验证

1. 调用一条短文本请求，并记录请求时间与模型。
2. 核对响应、LinkMind 运行记录和实际路由。
3. 流式、工具、文件和自定义元数据逐项增加验证。
4. 生产客户端还应验证异常处理、超时、鉴权与计费行为。

聊天、Responses、Messages、Embedding 与多模态请求字段见[模型接入、路由与多模态](models-and-multimodal_zh.md)；向量与资料入库字段见[知识库](knowledge-base_zh.md)。标准 API 或兼容适配器不足时，参见[更多功能接入与自定义扩展](extensions_zh.md)。
