# Client Compatibility

## 协议选择 / Protocol selection

| Client type | LinkMind endpoint |
| --- | --- |
| OpenAI-compatible clients, including Codex | Base URL ending in `/v1`; `POST /v1/chat/completions` or `/v1/responses` |
| Anthropic-compatible clients, including Claude Code | Service root; `POST /v1/messages` is appended by the client when applicable |
| Gemini CLI or a Google-compatible client | Requires the client to support a configurable compatible endpoint and matching Google request contract |
| DeepSeek Harness or other clients | Choose the supported protocol before configuring the base URL |

先核对客户端实际请求的协议和 URL 拼接行为，再填写 LinkMind 地址。A model name appearing in a client does not prove that the upstream route is configured. Verify one short request and the LinkMind runtime record before enabling tools or long contexts.
