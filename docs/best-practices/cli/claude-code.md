# Claude Code with LinkMind

## 配置 / Setup

Claude Code connects through LinkMind's Anthropic-compatible Messages route. Set `ANTHROPIC_BASE_URL` to the LinkMind service root, not a URL ending in `/v1`; Claude Code appends `/v1/messages` itself. Provide the LinkMind API key through the client authentication setting.

```bash
ANTHROPIC_BASE_URL=http://localhost:8080
```

Use a short request to verify the response and LinkMind record. When LinkMind is remote, replace `localhost` with its reachable address. Model, route, and authentication configuration remains on the LinkMind side; see [Models and Multimodal](../../models-and-multimodal_en.md) / [模型接入、路由与多模态](../../models-and-multimodal_zh.md).
