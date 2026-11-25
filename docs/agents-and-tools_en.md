# Agents, Tools, and Task Orchestration

This page explains how to register Agents, MCP servers, Skills, Workers, and routes in LinkMind, then verify them through the unified Worker API. Illustrated, step-by-step third-party client recipes live in [Best Practices](#5-third-party-agent-and-cli-recipes).

## 1. Configure Agents and MCP servers

Declare callable Agents and MCP servers in `lagi.yml`. A large catalog can be split into `agent.yml` and `mcp.yml`, then loaded with `include_agents` and `include_mcps`. Treat these split files as part of the same configuration change and backup set.

```yaml
agents:
  enable: true
  items:
    - name: weather
      driver: ai.agent.customer.WeatherAgent
      token: ${WEATHER_TOKEN}

mcps:
  enable: true
  servers:
    - name: amap_mcp
      url: https://mcp.example.com/sse
      headers:
        Authorization: Bearer ${MCP_TOKEN}
      priority: 10
      enable: true
```

`name` must be unique in this deployment. `driver` identifies a local implementation. Give MCP `url`, `headers`, `key`, and custom `driver` only the values the target service needs. Verify a read-only, side-effect-free tool first; grant write, command, or external-system access later.

## 2. Configure Skills, Workers, routes, and filters

Workers and PnPs are nested under `skills`, not at the top level. A Worker chooses an execution target through `route`, `agent`, or `agents`; reusable routes live in `routers.items`, and chat or Workers may refer to filters.

```yaml
skills:
  enable: true
  roots: ["classpath:skills"]
  workspace: skills
  rule: cli
  workers:
    - name: appointedWorker
      route: pass(%)
      worker: ai.worker.DefaultAppointWorker

routers:
  items:
    - name: support-route
      expression: pass(weather)

filters:
  enable: true
  items:
    - name: sensitive
      groups: []
```

`route: pass(%)` lets a request select its target through `agentId`; a fixed case can use `pass(weather)`. Before production, review every Agent, MCP server, and Skill key, workspace, network range, and tool permission. File, command, and write access should be off unless required.

## 3. Quickly verify a Worker call

Restart the service, then use `POST /chat/go` for a low-risk request. Send a LinkMind API key when authentication is enabled.

```bash
curl http://localhost:8080/chat/go \
  -H "Authorization: Bearer <linkmind-api-key>" \
  -H "Content-Type: application/json" \
  -d '{
    "category":"default",
    "worker":"appointedWorker",
    "agentId":"weather",
    "stream":false,
    "temperature":0.2,
    "max_tokens":128,
    "messages":[{"role":"user","content":"Confirm that you received this test request."}]
  }'
```

| Field | Meaning |
| --- | --- |
| `messages` | Required chat messages; each has `role` and `content` |
| `worker` | Optional Worker name; an omitted value follows deployment defaults |
| `agentId` | Optional explicit Agent identifier, useful with `pass(%)` |
| `category` | Optional data or knowledge category |
| `stream` | Whether to return SSE events |
| `temperature` / `max_tokens` | Sampling temperature and maximum generated tokens |

A successful non-stream response uses the chat-completion shape with `id`, `model`, `choices[0].message.content`, and `usage`. A stream must be read through `data: [DONE]`. Verify the client response, LinkMind control record, selected Worker/Agent, actual tool execution, and usage record together.

## 4. Choose the call path and troubleshoot

- **A business system calls a Worker or Agent:** use `/chat/go`; use `worker` and `agentId` to express the orchestration target.
- **An external Agent runtime uses LinkMind only as a model gateway:** use `/v1/chat/completions`, `/v1/responses`, or `/v1/messages`; see [Models, Routing, and Multimodal](models-and-multimodal_en.md) for model and route configuration.
- **An MCP server will not connect:** check its URL, SSE/HTTP transport, required headers, and server-side logs. A successful chat does not prove MCP connectivity.
- **The Worker selected the wrong Agent:** inspect the Worker `route`, request `agentId`, route expression, and target Agent `enable` state.
- **The Agent needs private documents:** validate upload, retrieval, and category isolation through [Knowledge Base](knowledge-base_en.md) first.

## 5. Third-party Agent and CLI recipes

These pages are runnable cookbooks rather than summaries. They retain client UI and verification screenshots:

- [OpenClaw](best-practices/agents/openclaw.md)
- [Hermes Agent](best-practices/agents/hermes-agent.md)
- [DeerFlow](best-practices/agents/deerflow.md)
- [OpenHuman](best-practices/agents/openhuman.md)
- [Codex](best-practices/cli/codex.md)
- [Claude Code](best-practices/cli/claude-code.md)
- [Client compatibility](best-practices/cli/client-compatibility.md)

## 6. Security boundary

Agents, MCP servers, Skills, Workers, and PnPs expand the execution surface. In production, enable only trusted sources and the smallest possible URL, key, workspace, and network scope; establish approval, audit, or sandboxing for side-effecting tools. For deployment, reverse proxy, TLS, authentication, and cascade-node controls, see [Deployment, Security, and Cascade Networking](deployment-and-security_en.md).
