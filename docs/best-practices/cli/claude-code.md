# Claude Code 通过 LinkMind 调用模型

[中文](#zh-cn) · [English](#english)

<a id="zh-cn"></a>

Claude Code 使用 Anthropic Messages 协议，LinkMind 将该请求和流式事件转入内部聊天链路。本页只需配置 Claude Code 与 LinkMind 的连接，无需转换为 OpenAI 请求。

## 一、准备 LinkMind

先确认 LinkMind 中已有可用聊天模型、路由和客户端 API Key。客户端访问地址必须是服务根地址，不带 `/v1`；Claude Code 会自行追加 `/v1/messages`。

局域网地址示例：`http://linkmind.example.net:8080`。先从客户端机器验证 LinkMind 可访问，再进行 Claude Code 配置。

## 二、设置环境变量

macOS / Linux：

```bash
export ANTHROPIC_AUTH_TOKEN="<LINKMIND_CLIENT_TOKEN>"
export ANTHROPIC_BASE_URL=http://linkmind.example.net:8080
export ANTHROPIC_MODEL="<ENABLED_LINKMIND_MODEL>"
```

PowerShell：

```powershell
$env:ANTHROPIC_AUTH_TOKEN = "<LINKMIND_CLIENT_TOKEN>"
$env:ANTHROPIC_BASE_URL = "http://linkmind.example.net:8080"
$env:ANTHROPIC_MODEL = "<ENABLED_LINKMIND_MODEL>"
```

不要把 `ANTHROPIC_BASE_URL` 写成带 `/v1` 的地址，否则客户端会拼出错误路径。Messages 接口可接受 `x-api-key` 或 Bearer 鉴权；实际客户端 Key 与模型名应替换为部署中的值。

## 三、验证与排查

启动 Claude Code 后发送一条短消息。成功时，LinkMind 收到 `POST /v1/messages`，并在 **控制** 中显示对应调用、模型和 Token 记录。

失败时依次检查：服务根地址是否可达；模型是否已启用；Key 是否有效；LinkMind 路由是否支持流式；以及客户端是否误将 `/v1` 重复拼接。

需要公开给外部使用时，先按[部署、安全与级联组网](../../deployment-and-security_zh.md)配置 TLS、网络隔离和网关鉴权。

<a id="english"></a>

## English

Start with a working LinkMind chat backend and client key. Point `ANTHROPIC_BASE_URL` at the server root. Claude Code appends `/v1/messages`. Use these environment variables in the shell running the client:

```bash
export ANTHROPIC_AUTH_TOKEN="<LINKMIND_CLIENT_TOKEN>"
export ANTHROPIC_BASE_URL=http://linkmind.example.net:8080
export ANTHROPIC_MODEL="<ENABLED_LINKMIND_MODEL>"
```

PowerShell:

```powershell
$env:ANTHROPIC_AUTH_TOKEN = "<LINKMIND_CLIENT_TOKEN>"
$env:ANTHROPIC_BASE_URL = "http://linkmind.example.net:8080"
$env:ANTHROPIC_MODEL = "<ENABLED_LINKMIND_MODEL>"
```

Replace the placeholders and host. Send a short message in Claude Code. Check the reply and the corresponding model and token record in LinkMind **Control**. For errors check the URL (including a duplicated `/v1`), key, model, and streaming route. LinkMind accepts `x-api-key` or Bearer for Messages.
