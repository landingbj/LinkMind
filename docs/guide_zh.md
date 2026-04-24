# 开发集成指南

本文面向准备把 LinkMind 接入到自己业务系统中的开发者。如果你只是想先把控制台或 HTTP API 跑起来，请优先阅读 [安装指南](install_zh.md) 和 [API 参考](API_zh.md)。

## 一、先选集成方式

LinkMind 常见的接入方式有三种：

| 方式 | 适用场景 |
| --- | --- |
| REST API | 你的系统可以调用外部服务，希望接入成本最低 |
| Docker 服务 | 你希望给本地团队、测试环境或多语言项目提供一个统一运行时 |
| `lagi-core` | 你的项目是 Java 项目，希望在自己代码里直接调用 LinkMind 服务类 |

如果只是快速验证，建议先用 REST API 或 Docker；只有在你明确需要 Java 进程内调用时，再接 `lagi-core`。

## 二、准备 `lagi-core`

### 方式 A：从当前仓库安装到本地 Maven

在仓库根目录执行：

```bash
mvn clean install -pl lagi-core -am -DskipTests
```

这一步会把 `lagi-core` 以及项目依赖的内部制品一起安装到本地 Maven 仓库中。

### 方式 B：发布到团队内部制品库

如果团队已经在使用 Nexus、Artifactory 等内部 Maven 仓库，也可以基于同样的构建结果自行发布。

## 三、添加 Java 依赖

本地 `mvn install` 完成后，或你已经发布到内部仓库后，可以按当前版本添加依赖：

```xml
<dependency>
  <groupId>com.landingbj</groupId>
  <artifactId>lagi-core</artifactId>
  <version>1.2.4</version>
</dependency>
```

## 四、提供配置文件

LinkMind 会按以下方式查找 `lagi.yml`：

- 运行时 classpath 中名为 `lagi.yml` 的资源
- 显式系统属性：`-Dlinkmind.config=/path/to/lagi.yml`
- 在源码仓库内运行时，会回退到仓库自带资源路径

对业务集成来说，最简单的两种做法是：

- 直接把 `lagi.yml` 放进你的运行时 classpath
- 或者通过 `-Dlinkmind.config` 指向外部配置文件

在真正调用服务前，请先按 [配置参考](config_zh.md) 至少启用一个模型和一个聊天后端。

## 五、常见 Java 调用方式

在调用服务类之前，先初始化一次上下文：

```java
import ai.config.ContextLoader;

ContextLoader.loadContext();
```

完整可运行示例已经在这里：

- [`lagi-core/src/test/java/ai/example/Demo.java`](../lagi-core/src/test/java/ai/example/Demo.java)

### 文本对话

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
message.setContent("请用一句话介绍 LinkMind。");

ChatCompletionRequest request = new ChatCompletionRequest();
request.setModel("qwen-plus");
request.setStream(false);
request.setMessages(Collections.singletonList(message));

ChatCompletionResult result = service.completions(request);
String answer = result.getChoices().get(0).getMessage().getContent();
```

### 语音识别

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

### 文字转语音

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

### 文生图

```java
import ai.common.pojo.ImageGenerationRequest;
import ai.common.pojo.ImageGenerationResult;
import ai.image.service.ImageGenerationService;

ContextLoader.loadContext();

ImageGenerationService service = new ImageGenerationService();
ImageGenerationRequest request = new ImageGenerationRequest();
request.setPrompt("一个未来机场里的智能服务机器人");

ImageGenerationResult result = service.generations(request);
```

### 其他仍可直接复用的 Java 示例

当前代码里还保留了这些 Java 示例：

- 看图理解
- 图片增强
- 图生视频
- 视频追踪
- 视频增强

可直接参考 [`lagi-core/src/test/java/ai/example/Demo.java`](../lagi-core/src/test/java/ai/example/Demo.java) 中的现成写法。

## 六、如果改走 HTTP / REST 集成

如果你的应用不是 Java 项目，最直接的方式就是把 LinkMind 当成服务来调用。

### 常见路由

- LinkMind 原生路由，例如 `/chat/completions`、`/audio/speech2text`、`/audio/text2speech`、`/image/text2image`、`/sql/text2sql`、`/instruction/generate`、`/doc/doc2ext`、`/ocr/doc2ocr`
- OpenAI 兼容路由，例如 `/v1/chat/completions`、`/v1/models`、`/v1/embeddings`、`/v1/images/generations`、`/v1/rerank`

服务根地址示例：

- `http://localhost:8080`

### cURL 示例

```bash
curl http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "qwen-plus",
    "stream": false,
    "messages": [
      {"role": "user", "content": "请用一句话介绍 LinkMind。"}
    ]
  }'
```

### Python 示例

```python
import requests

resp = requests.post(
    "http://localhost:8080/v1/chat/completions",
    headers={"Content-Type": "application/json"},
    json={
        "model": "qwen-plus",
        "stream": False,
        "messages": [
            {"role": "user", "content": "请用一句话介绍 LinkMind。"}
        ],
    },
    timeout=60,
)

resp.raise_for_status()
print(resp.json())
```

### Go 示例

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
	    {"role": "user", "content": "请用一句话介绍 LinkMind。"}
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

如果 LinkMind 开启了鉴权，请在请求头中带上：

```http
Authorization: Bearer <你的-linkmind-api-key>
```

每个接口的参数与返回体细节，请继续查看 [API 参考](API_zh.md)。

## 七、如果改走 Docker 集成

当你的 Python、Go、Java、Node.js 等多个系统都要共用同一套 AI 中间件运行时，Docker 是很合适的接法。

### 启动官方镜像

```bash
docker pull landingbj/linkmind
docker run -d --name linkmind -p 8080:8080 landingbj/linkmind
```

### 典型接入方式

1. 用 Docker 启动 LinkMind，作为统一的 AI 中间件服务。
2. 让业务应用统一访问 `http://localhost:8080`。
3. 按需要调用 LinkMind 原生路由或 OpenAI 兼容的 `/v1/...` 路由。
4. 让业务系统保持和具体模型厂商解耦。

这种方式特别适合本地开发、内部演示、CI 冒烟测试，以及多语言团队共用一套服务。

## 八、接下来建议继续看

- [配置参考](config_zh.md)
- [API 参考](API_zh.md)
- [教学演示](tutor_zh.md)
- [扩展开发文档](extend_zh.md)
