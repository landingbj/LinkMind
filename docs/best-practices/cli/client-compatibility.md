# DeepSeek Harness 与 Gemini CLI 兼容性

[中文](#zh-cn) · [English](#english)

<a id="zh-cn"></a>

本页用于在配置前确认客户端协议，避免“地址可达但请求格式不匹配”。LinkMind 当前直接支持 OpenAI Chat Completions、OpenAI Responses 和 Anthropic Messages。

## 一、协议对应关系

| 客户端或协议 | LinkMind 地址与接口 | 当前状态 |
| --- | --- | --- |
| OpenAI Chat Completions | base URL 以 `/v1` 结尾；`POST /v1/chat/completions` | 可直接接入 |
| OpenAI Responses | base URL 以 `/v1` 结尾；`POST /v1/responses` | 可直接接入 |
| Anthropic Messages | 服务根地址；`POST /v1/messages` | 可直接接入 |
| DeepSeek Harness | 选择 `openai-completions`、`openai-responses` 或 `anthropic-messages` 之一 | 可直接接入，协议必须一致 |
| Gemini CLI | `POST /v1beta/models/{model}:generateContent` | LinkMind 尚未提供 Gemini 协议转换层 |

<a id="cli-gateway-zh"></a>

### 从开发机连接局域网网关

在示例中，用开发机可以访问的 LinkMind 主机替换 `linkmind.example.net`。OpenAI 客户端的 `LINKMIND_BASE_URL` 以 `/v1` 结尾，Anthropic 客户端使用服务根地址。可用接口如下。

```text
GET  /v1/models
POST /v1/chat/completions
POST /v1/responses
POST /v1/messages
```

在服务器的安装目录启动 LinkMind。Windows 示例：

```powershell
Set-Location "$env:USERPROFILE\LinkMind"
java -jar .\LinkMind.jar --host=0.0.0.0 --port=8080
```

只向所需可信网段开放 8080，再从客户端验证。全程使用 `lagi.yml` 与 `functions.chat.backends` 中已启用的模型。

```bash
curl http://linkmind.example.net:8080/v1/models
```

`--linkmind-api-key` 和 `LINKMIND_API_KEY` 用于把客户端凭证同步给外部运行时。Messages 接受 `x-api-key` 或 Bearer，Responses 与 Chat Completions 使用 Bearer。多人或多开发机需要独立身份、配额与追踪时，在入口网关配置 TLS、凭证校验、访问权限、限流与审计。Provider 端的密钥轮询和故障转移用于访问上游。

## 二、DeepSeek Harness 接入步骤

1. 先确定 Harness 配置中的协议类型。
2. `openai-completions` 或 `openai-responses` 使用 `http://linkmind.example.net:8080/v1`。
3. `anthropic-messages` 使用 `http://linkmind.example.net:8080` 服务根地址。
4. 设置 LinkMind API Key 和一个已启用模型。
5. 先发短请求，再确认 LinkMind 的运行记录。

协议和 URL 必须成对选择；例如 Anthropic 请求不能发送到一个已包含 `/v1` 的地址后再让客户端追加 `/v1/messages`。

## 三、Gemini CLI 当前限制

Gemini CLI 会请求 `GOOGLE_GEMINI_BASE_URL/v1beta/models/...:generateContent`。即使设置：

```bash
export GEMINI_API_KEY="<LINKMIND_CLIENT_TOKEN>"
export GOOGLE_GEMINI_BASE_URL=http://linkmind.example.net:8080
```

LinkMind 仍不能直接处理 Gemini `generateContent` 协议。要支持 Gemini CLI，需要增加 Gemini 请求/响应与流式事件到 LinkMind 内部聊天协议的转换层；在该层完成前，应选用 OpenAI 或 Anthropic 兼容客户端。

## 四、统一验证

无论哪个客户端，均先查看 `/v1/models` 识别已启用模型，再发一条短请求。客户端有回复且 LinkMind **控制** 出现对应记录，才算协议、鉴权和模型路由均已对齐。

<a id="english"></a>

## English

Replace `linkmind.example.net` with a LinkMind host reachable from the development machine. For OpenAI clients, `LINKMIND_BASE_URL` ends in `/v1`. Anthropic clients use the service root. Available routes:

```text
GET  /v1/models
POST /v1/chat/completions
POST /v1/responses
POST /v1/messages
```

On the server, start the JAR from its installation directory. For example, on Windows:

```powershell
Set-Location "$env:USERPROFILE\LinkMind"
java -jar .\LinkMind.jar --host=0.0.0.0 --port=8080
```

Restrict port 8080 to the required trusted subnets. Verify connectivity from the client:

```bash
curl http://linkmind.example.net:8080/v1/models
```

Choose a chat model enabled in both `lagi.yml` and `functions.chat.backends`. DeepSeek Harness can use `openai-completions`, `openai-responses`, or `anthropic-messages`. Match the endpoint to the selected protocol and verify one reply against a LinkMind control record. For Codex choose Responses. For Claude Code choose Messages.

Gemini CLI sends `POST /v1beta/models/{model}:generateContent`. Setting the following changes its destination, but the request and streaming event format still require a Gemini translation layer. LinkMind exposes the OpenAI and Anthropic routes listed above.

```bash
export GEMINI_API_KEY="<LINKMIND_CLIENT_TOKEN>"
export GOOGLE_GEMINI_BASE_URL=http://linkmind.example.net:8080
```

`--linkmind-api-key` and `LINKMIND_API_KEY` synchronize a client credential to external runtimes. Messages accepts `x-api-key` or Bearer. Responses and Chat Completions use Bearer. For separate identities, limits, and audit trails per person or machine, configure TLS, credential validation, access controls, rate limits, and audit logs at the gateway. Provider key rotation and failover handle calls to the upstream provider.
