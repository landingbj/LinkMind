# Codex 通过 LinkMind 调用模型

[中文](#zh-cn) · [English](#english)

<a id="zh-cn"></a>

Codex 通过 OpenAI Responses 接口连接 LinkMind。以下示例使用当前 Codex 配置支持的 `wire_api = "responses"`。

## 一、先让 Codex 能访问 LinkMind

本机运行时使用 `http://localhost:8080/v1`。局域网内使用LinkMind 服务器时，替换为 `http://linkmind.example.net:8080/v1`，并先在开发机验证：

```bash
curl http://linkmind.example.net:8080/v1/models
```

返回的模型来自 `lagi.yml` 中已启用且已加入 `functions.chat.backends` 的聊天后端。另一台机器访问时，LinkMind 需监听可信局域网地址，防火墙只允许所需网段访问 `8080`；不要直接暴露到公网。

## 二、配置 Codex

在启动 Codex 的终端环境中设置 `LINKMIND_API_KEY`，供下面的自定义 Provider 使用。如果已有客户端通过 `auth.json` 读取 OpenAI 形式的 Key，其凭证格式如下：

```json
{
  "OPENAI_API_KEY": "<LINKMIND_CLIENT_TOKEN>"
}
```

在 `config.toml` 中新增 Provider：

```toml
model_provider = "linkmind"
model = "Alibaba/qwen-plus"

[model_providers.linkmind]
name = "LinkMind"
base_url = "http://linkmind.example.net:8080/v1"
wire_api = "responses"
env_key = "LINKMIND_API_KEY"
```

当前 Codex 配置使用 `wire_api = "responses"`，请求 `POST /v1/responses`。旧版配置中的 `wire_api = "chat"` 仅适用于明确支持该值的旧客户端，对应 `POST /v1/chat/completions`。`model` 应取自当前部署的 `/v1/models`。[Codex 配置参考](https://developers.openai.com/codex/config-reference)

## 三、启动与验证

重启终端后运行：

```bash
codex
```

发送一条简短请求，并在 LinkMind **控制** 中核对同一时间的模型、路由和 Token 记录。没有调用记录时，先检查 Codex `base_url` 是否以 `/v1` 结束、API Key 是否启用、以及 `model` 是否出现在 `/v1/models` 返回中。

## 四、鉴权边界

`LINKMIND_API_KEY` 或 `--linkmind-api-key` 仅用于把 LinkMind 凭据同步给运行时，不等同于多租户 Token 池。需要为多人或多台开发机发放独立 Token 时，应先在 LinkMind 前部署带 TLS 的反向代理或 API 网关，并提供 Bearer Token 校验、访问控制、限流和审计。

<a id="english"></a>

## English

Use `http://localhost:8080/v1` on the same host, or a reachable LinkMind server on a trusted network. Get an enabled chat model with `GET /v1/models`. Set `LINKMIND_API_KEY` in the shell running Codex. Add the provider to `~/.codex/config.toml`:

```toml
model_provider = "linkmind"
model = "Alibaba/qwen-plus"

[model_providers.linkmind]
name = "LinkMind"
base_url = "http://linkmind.example.net:8080/v1"
wire_api = "responses"
env_key = "LINKMIND_API_KEY"
```

Replace the host and model, reopen the shell, and run `codex`. Send a short message and match its timestamp, model, route and token record in LinkMind **Control**. The current Codex provider uses `wire_api = "responses"` and calls `POST /v1/responses`. Older clients that explicitly accept `wire_api = "chat"` use `POST /v1/chat/completions`. Verify the supported setting for the installed client. [Codex configuration reference](https://developers.openai.com/codex/config-reference)

For installations that read OpenAI-style credentials from `auth.json`, the stored shape is:

```json
{
  "OPENAI_API_KEY": "<LINKMIND_CLIENT_TOKEN>"
}
```

The provider above reads the named environment variable. See [client authentication and LAN access](client-compatibility.md#english) for firewall, gateway, and per-client token controls.
