# OpenHuman with LinkMind

## 安装与配置 / Setup

Install the official OpenHuman desktop release and launch it once. Configure or let LinkMind locate its `config.toml` through `OPENHUMAN_WORKSPACE`, `~/.openhuman/active_user.toml`, or the local user configuration.

```toml
[[cloud_providers]]
id = "p_linkmind_linkmind"
slug = "linkmind"
label = "LinkMind"
endpoint = "http://127.0.0.1:8080/v1"
auth_style = "bearer"
default_model = "Alibaba/qwen3.6-plus"
```

修改后重启 OpenHuman，发送一条消息，并确认 LinkMind 收到 `/v1/chat/completions` 请求。The provider endpoint must be reachable from OpenHuman; replace loopback when the services are on different hosts.
