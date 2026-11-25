# Hermes Agent with LinkMind

## 配置 / Setup

Verify the client with `where.exe hermes`, `hermes --version`, and `hermes status`. Configure a custom provider in `config.yaml`:

```yaml
model:
  default: Alibaba/qwen-plus
  provider: custom
  base_url: http://localhost:8080/v1
  api_key: <your-linkmind-api-key>
  api_mode: chat_completions
  context_length: 64000
```

重启 Hermes 会话并发送测试消息。若 Hermes 在 WSL，`localhost` 必须替换为 WSL 可访问的 Windows 主机地址。完成条件为 Hermes 收到回复、LinkMind 记录请求、所选路由和 Key 已启用。

For LinkMind-side model and route configuration, see [Models and Multimodal](../../models-and-multimodal_en.md) / [模型接入、路由与多模态](../../models-and-multimodal_zh.md).
