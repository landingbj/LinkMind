# OpenHuman 接入 LinkMind

本页说明 OpenHuman 桌面端如何使用 LinkMind Provider。OpenHuman 没有单独的专有接口，实际通过 LinkMind 的 OpenAI Chat Completions 兼容接口完成调用。

## 一、准备 OpenHuman 与 LinkMind

1. 安装 OpenHuman 官方桌面版，并至少启动一次，使其工作区和用户配置生成。
2. 启动 LinkMind，确认 `http://localhost:8080` 可访问。
3. 在 LinkMind 中启用目标聊天模型，创建给 OpenHuman 使用的 API Key，并先完成一条短消息验证。

安装器或 LinkMind 可按以下顺序发现 OpenHuman 配置：

1. 显式提供的 `--openhuman-path=`。
2. `OPENHUMAN_WORKSPACE`。
3. `~/.openhuman/active_user.toml` 指向的用户配置。
4. `~/.openhuman/users/local/config.toml`。

## 二、配置 LinkMind Provider

在 OpenHuman `config.toml` 中写入或确认下面的 Provider 与工作负载配置：

```toml
chat_provider = "linkmind:Alibaba/qwen3.6-plus"
reasoning_provider = "linkmind:Alibaba/qwen3.6-plus"
agentic_provider = "linkmind:Alibaba/qwen3.6-plus"
coding_provider = "linkmind:Alibaba/qwen3.6-plus"
memory_provider = "linkmind:Alibaba/qwen3.6-plus"

[[cloud_providers]]
id = "p_linkmind_linkmind"
slug = "linkmind"
label = "LinkMind"
endpoint = "http://127.0.0.1:8080/v1"
auth_style = "bearer"
default_model = "Alibaba/qwen3.6-plus"
```

将模型名替换为 LinkMind 已启用模型。OpenHuman 与 LinkMind 不在同一主机时，必须把 `endpoint` 的回环地址替换为 OpenHuman 可访问的服务地址。

## 三、配置鉴权并启动

在运行安装器或启动 LinkMind 前提供 `LINKMIND_API_KEY`。集成流程会在 OpenHuman 的 `auth-profiles.json` 中启用 `provider:linkmind`；存在桌面端 keychain 文件时，会将同一 Token 写入该文件。不要将真实 Token 提交到仓库或放入截图。

配置修改后，完全退出并重新打开 OpenHuman，以重新加载 `config.toml` 和鉴权配置。

## 四、验证与排查

在 OpenHuman 中发送一条短消息。接入成功时：

1. OpenHuman 收到模型回复。
2. LinkMind 收到一条 `POST /v1/chat/completions` 调用。
3. LinkMind 控制页出现对应模型、路由、Token 或用量记录。

若失败，先在 LinkMind 控制台验证同模型；再检查 `endpoint` 是否可达、`auth_style` 是否为 `bearer`、模型 ID 是否一致，以及重启前的 OpenHuman 是否仍在使用旧配置。
