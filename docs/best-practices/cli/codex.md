# Codex with LinkMind

## 配置 / Setup

Configure Codex to use LinkMind's OpenAI-compatible endpoint: set the base URL to `http://localhost:8080/v1`, select an enabled LinkMind model, and provide a LinkMind API key when authentication is enabled.

For a LAN or remote deployment, replace `localhost` with the reachable LinkMind host and expose only the required trusted-network port. Start with a short prompt, then verify the response and LinkMind call record.

Codex expects an OpenAI-compatible Chat Completions or Responses contract. The exact request schema and LinkMind route setup are documented in [System Integration and Secondary Development](../../integration_en.md) / [系统集成与二次开发](../../integration_zh.md).
