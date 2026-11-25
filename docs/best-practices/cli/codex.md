# Codex 通过 LinkMind 调用模型

本页配置 Codex 使用 LinkMind 的 OpenAI Responses 或 Chat Completions 兼容接口。推荐 Responses 协议，Chat Completions 仅用于兼容已有配置。

## 一、先让 Codex 能访问 LinkMind

本机运行时使用 `http://localhost:8080/v1`。局域网内使用盒子或服务器时，替换为 `http://<LINKMIND_BOX_IP>:8080/v1`，并先在开发机验证：

```bash
curl http://<LINKMIND_BOX_IP>:8080/v1/models
```

返回的模型来自 `lagi.yml` 中已启用且已加入 `functions.chat.backends` 的聊天后端。另一台机器访问时，LinkMind 需监听可信局域网地址，防火墙只允许所需网段访问 `8080`；不要直接暴露到公网。

## 二、配置 Codex

在 Codex 的 `auth.json` 中写入 LinkMind 客户端 Key：

```json
{
  "OPENAI_API_KEY": "<LINKMIND_CLIENT_TOKEN>"
}
```

在 `config.toml` 中新增 Provider：

```toml
model_provider = "linkmind"
model = "Alibaba/qwen-plus"
preferred_auth_method = "apikey"

[model_providers.linkmind]
name = "LinkMind"
base_url = "http://<LINKMIND_BOX_IP>:8080/v1"
wire_api = "responses"
```

`wire_api = "responses"` 会请求 `POST /v1/responses`。如需兼容旧客户端设置为 `wire_api = "chat"`，则请求 `POST /v1/chat/completions`。`model` 必须替换为 LinkMind 实际已启用的模型名。

## 三、启动与验证

重启终端后运行：

```bash
codex
```

发送一条简短请求，并在 LinkMind **控制** 中核对同一时间的模型、路由和 Token 记录。没有调用记录时，先检查 Codex `base_url` 是否以 `/v1` 结束、API Key 是否启用、以及 `model` 是否出现在 `/v1/models` 返回中。

## 四、鉴权边界

`LINKMIND_API_KEY` 或 `--linkmind-api-key` 仅用于把 LinkMind 凭据同步给运行时，不等同于多租户 Token 池。需要为多人或多台开发机发放独立 Token 时，应先在 LinkMind 前部署带 TLS 的反向代理或 API 网关，并提供 Bearer Token 校验、访问控制、限流和审计。
