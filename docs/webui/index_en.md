# Web UI overview

English | [中文](index_zh.md)

The Web UI is a lightweight console for validating a LinkMind deployment: try an enabled model or agent, upload material, and inspect basic runtime information. It is not the primary way to define system behavior. Configure providers, routing, RAG, agents, MCP, Skills, Workers, and security policies in the [Configuration Guide](../config_en.md).

## First-run check

1. Start the service following the [Installation Guide](../install_en.md), then open `http://localhost:8080` or the address supplied by the administrator.
2. Sign in when the deployment requires it. Under **Settings → API Keys**, confirm that a provider key and model are configured; key management and account services depend on the deployment.
3. Select **Chat → LLM → Smart Q&A**, choose an available model, and send a short text request.
4. Confirm both the response and the related entry under **Control**. A visible model name or saved key alone does not prove that the upstream service is reachable.

## Main entry points

| Need | Console entry | Source of truth |
| --- | --- | --- |
| Test a model, agent, or multimodal request | **Chat** | Provider, route, agent, and tool configuration: [Configuration Guide](../config_en.md) |
| Upload material or verify an answer source | **Chat → Orchestration** | RAG and storage configuration: [Configuration Guide](../config_en.md) |
| Inspect usage, calls, and filter records | **Control** | Runtime records are operational evidence, not a replacement for billing or audit systems |
| Configure an interaction server or subscriptions | **Interactions** | Deployment mode and server configuration |
| Manage UI login, keys, filters, or credits | **Settings** | [Security & Safety](../security_en.md) and the configured account service |

Available menus, models, balances, and permissions are deployment-specific. In Agent Server mode, **Interactions → Publish** is available; in Agent Mate mode, configure an upstream server and subscribe to channels instead. The Web UI uses a browser session and does not by itself grant administrator access to backend APIs.

For endpoint parameters, use the [API Reference](../API_en.md). For application integration or Agent framework integration, use the [Integration Guide](../guide_en.md) and [Extension Guide](../extend_en.md). See [Web UI troubleshooting](troubleshooting_en.md) when the console does not behave as expected.
