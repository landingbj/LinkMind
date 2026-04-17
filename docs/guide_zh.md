# 开发集成指南

本文面向准备把 LinkMind 接入到自己业务系统中的开发者。如果你只是想先把控制台或 HTTP API 跑起来，请优先阅读 [安装指南](install_zh.md) 和 [API 参考](API_zh.md)。

## 一、先选集成方式

LinkMind 常见的接入方式有两种：

| 方式 | 适用场景 |
| --- | --- |
| REST API | 你的系统可以调用外部服务，希望接入成本最低 |
| `lagi-core` | 你的项目是 Java 项目，希望在自己代码里直接调用 LinkMind 服务类 |

如果只是快速验证，建议先用 REST API；只有在你明确需要 Java 进程内调用时，再接 `lagi-core`。

## 二、准备 `lagi-core`

### 方式 A：从当前仓库安装到本地 Maven

在仓库根目录执行：

```bash
mvn clean install -pl lagi-core -am -DskipTests
```

这一步会把 `lagi-core` 以及项目依赖的内部制品一起安装到本地 Maven 仓库中。

### 方式 B：发布到团队内部制品库

如果团队已经在使用 Nexus、Artifactory 等内部 Maven 仓库，也可以在同样的构建结果基础上自行发布。

## 三、添加 Java 依赖

本地 `mvn install` 完成后，或你已经发布到内部仓库后，可以按当前版本添加依赖：

```xml
<dependency>
  <groupId>com.landingbj</groupId>
  <artifactId>lagi-core</artifactId>
  <version>1.2.3</version>
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

完整可运行示例已经在这里：

- [`lagi-core/src/test/java/ai/example/Demo.java`](../lagi-core/src/test/java/ai/example/Demo.java)

### 文本对话

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
        new ChatMessage("user", "请用一句话介绍 LinkMind。")
));

ChatCompletionResult result = service.completions(request);
String answer = result.getChoices().get(0).getMessage().getContent();
```

### 语音识别

```java
import ai.audio.service.AudioService;
import ai.common.pojo.AsrResult;
import ai.common.pojo.AudioRequestParam;

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

ImageGenerationService service = new ImageGenerationService();
ImageGenerationRequest request = new ImageGenerationRequest();
request.setPrompt("一个未来机场里的智能服务机器人");

ImageGenerationResult result = service.generations(request);
```

## 六、如果改走 HTTP 集成

如果你的应用不是 Java 项目，更推荐直接启动 LinkMind 服务并调用：

- 不再额外写版本前缀的原生路由，例如 `/chat/completions`、`/audio/speech2text`、`/audio/text2speech`、`/image/text2image`、`/sql/text2sql`、`/instruction/generate`、`/doc/doc2ext`、`/ocr/doc2ocr`
- 保留标准前缀的 OpenAI 兼容路由，例如 `/v1/chat/completions`、`/v1/models`、`/v1/embeddings`、`/v1/images/generations`、`/v1/rerank`

服务根地址示例：

- `http://localhost:8080`

如果 LinkMind 开启了鉴权，请在请求头中带上：

```http
Authorization: Bearer <你的-linkmind-api-key>
```

## 七、接下来建议继续看

- [配置参考](config_zh.md)
- [API 参考](API_zh.md)
- [教学演示](tutor_zh.md)
- [扩展开发文档](extend_zh.md)
