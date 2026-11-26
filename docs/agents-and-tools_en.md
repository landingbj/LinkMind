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

<a id="agent-registration"></a>

### Agent and MCP registration fields

#### Agent

| Field | Meaning |
| --- | --- |
| `enable` / `items[].enable` | Global agent switch or optional per-agent switch |
| `name` | Agent name |
| `driver` | Agent implementation class |
| `token` / `api_key` | Provider token or API key |
| `app_id` / `user_id` | Optional provider identity |
| `wrong_case` | Fallback reply for unsupported or wrong requests |
| `endpoint` | Optional custom endpoint |
| `mcps` | Optional MCP dependency list |

#### MCP

| Field | Meaning |
| --- | --- |
| `enable` | Global MCP switch |
| `name` | MCP server name |
| `url` | SSE endpoint |
| `key` | Optional auth key field |
| `headers` | Optional custom request headers |
| `priority` | Selection priority |
| `servers[].enable` | Whether this server entry is active |
| `driver` | MCP client implementation, defaulting to `ai.mcps.CommonSseMcpClient` |

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
  enable: true
  items:
    - name: pass
      rule: (%)

filters:
  enable: true
  items:
    - name: sensitive
      groups: []
```

`route: pass(%)` lets a request select its target through `agentId`; a fixed case can use `pass(weather)`. Before production, review every Agent, MCP server, and Skill key, workspace, network range, and tool permission. File, command, and write access should be off unless required.

<a id="skill-worker-settings"></a>

### Skill directories, Worker selection, and PnP connectors

#### Skills

```yaml
skills:
  enable: true
  roots: ["classpath:skills"]
  workspace: "skills"
  rule: cli
  items:
    - name: extract_content_with_image
      description: Split local files into text and image chunks
```

Common fields:

| Field | Meaning |
| --- | --- |
| `enable` | Global skills switch |
| `roots` | Skill source roots |
| `workspace` | Local working directory for skill execution |
| `rule` | Skill execution preference such as `cli`, `server`, or `block` |
| `items` | Skill catalog entries exposed to the runtime |

#### Workers

```yaml
skills:
  workers:
    - name: appointedWorker
      route: pass(%)
      worker: ai.worker.DefaultAppointWorker
```

Common fields:

| Field | Meaning |
| --- | --- |
| `name` | Worker name |
| `worker` | Worker implementation class |
| `route` | Router expression |
| `agent` / `agents` | Target agent or agent set |
| `filter` | Optional filter override |

#### Pnps

```yaml
skills:
  pnps:
    - name: qq
      api_key: your-api-key
      driver: ai.pnps.social.QQPnp
```

Common fields:

| Field | Meaning |
| --- | --- |
| `name` | Automation connector name |
| `driver` | Pnp implementation class |
| `api_key` | Connector credential |

You can split additional Pnp definitions into `pnp.yml` and load them with `include_pnps`.

Both `workers` and `pnps` belong in `skills`. Chat and Workers use the same [routing syntax](models-and-multimodal_en.md#routing-reference). See [security policies](deployment-and-security_en.md#filter-rules) for filter rules.

<a id="worker-request"></a>

## 3. Quickly verify a Worker call

`POST /chat/go` is the unified Agent / Worker entrypoint for executing an orchestrated request through a configured Worker. Restart the service, then verify the integration with a low-risk request. Send a LinkMind API key when authentication is enabled.

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

### Worker request fields

| Field | Location | Type | Required | Purpose |
| --- | --- | --- | --- | --- |
| `Content-Type` | header | string | Yes | Use `application/json` |
| `Authorization` | header | string | As required by deployment authentication | `Bearer <linkmind-api-key>`; provide a valid client key when authentication is required |
| Request body | body | object | Yes | Contains the orchestration fields below |
| `model` | body | string | No | Optional model name passed to the Worker; whether it is used depends on the specific Worker / Agent implementation |
| `temperature` | body | number | No | Sampling temperature; the current request object defaults to `1`. Its use depends on the Worker / Agent and its model backend |
| `max_tokens` | body | integer | No | Maximum generated tokens; its use depends on the Worker / Agent and its model backend |
| `category` | body | string | No | Data or knowledge category |
| `messages` | body | array of objects | Yes | Valid messages in conversation order |
| `messages[].role` | body | string | Supply for each message | Message role, commonly `user` or `assistant` |
| `messages[].content` | body | string | Supply for ordinary conversation messages | User input for a `user` message or a previous reply for an `assistant` message |
| `worker` | body | string | Yes in the current implementation | Registered routing Worker name, such as `appointedWorker`; the current entrypoint does not automatically select a default Worker |
| `agentId` | body | string | Depends on the route | Supply the target Agent identifier for `route: pass(%)`; a fixed route such as `pass(weather)` uses the configured target |
| `stream` | body | boolean | No; explicit values recommended | Only `false` selects ordinary JSON; `true` or an omitted field takes the SSE response branch in the current entrypoint |

For verification, first match `worker` to its configured name, then supply `agentId` according to that Worker's route. `model` is a model parameter passed to the execution chain; the route and `agentId` determine the target Agent. `temperature` and `max_tokens` are optional and are not mandatory for request parsing.

### Worker response and verification

A successful `stream: false` response uses a chat completion object, normally with HTTP **200**. This complete Worker response example uses illustrative identifiers, timestamp, model, text, and usage; actual content comes from the executing Worker / Agent:

```json
{
  "id": "chatcmpl-worker-001",
  "object": "chat.completion",
  "created": 1700000000,
  "model": "qwen-plus",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "LinkMind can help route and execute your request."
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 14,
    "completion_tokens": 2,
    "total_tokens": 16
  }
}
```

Read `choices[0].message.content` for the reply. For the complete types, meanings, and availability of `id`, `object`, `created`, `model`, `choices`, `finish_reason`, `usage`, and their nested fields, see the [shared response field reference](models-and-multimodal_en.md#chat-completions-response).

The current `/chat/go` entrypoint obtains the Worker's complete result before splitting the reply text into SSE events. Normal streamed text appears in `choices[0].message.content` in each event; concatenate it in order and continue through `data: [DONE]`. This does not indicate token-by-token streaming inside the Agent.

Verify the client reply, LinkMind control record, selected Worker / Agent, expected tool execution, and usage record together. Confirm that the request reached the configured target before connecting the business workflow.

## 4. Choose the call path and troubleshoot

- **A business system calls a Worker or Agent:** use `/chat/go`; use `worker` and `agentId` to express the orchestration target.
- **An external Agent runtime uses LinkMind only as a model gateway:** use `/v1/chat/completions`, `/v1/responses`, or `/v1/messages`; see [Models, Routing, and Multimodal](models-and-multimodal_en.md) for model and route configuration.
- **An MCP server will not connect:** check its URL, SSE/HTTP transport, required headers, and server-side logs. A successful chat does not prove MCP connectivity.
- **The Worker selected the wrong Agent:** inspect the Worker `route`, request `agentId`, route expression, and target Agent `enable` state.
- **The Agent needs private documents:** validate upload, retrieval, and category isolation through [Knowledge Base](knowledge-base_en.md) first.

<a id="client-recipes"></a>

## 5. Third-party Agent and CLI recipes

The installer can synchronize client model settings for OpenClaw, Hermes Agent, DeerFlow, and OpenHuman. LinkMind still loads its backends and routes from its own `lagi.yml`. Verify one request in both the client and the LinkMind control page after synchronizing.

These pages are runnable cookbooks rather than summaries. They retain client UI and verification screenshots:

- [OpenClaw](best-practices/agents/openclaw.md#english)
- [Hermes Agent](best-practices/agents/hermes-agent.md#english)
- [DeerFlow](best-practices/agents/deerflow.md#english)
- [OpenHuman](best-practices/agents/openhuman.md#english)
- [Codex](best-practices/cli/codex.md#english)
- [Claude Code](best-practices/cli/claude-code.md#english)
- [Client compatibility](best-practices/cli/client-compatibility.md#english)

## 6. Security boundary

Agents, MCP servers, Skills, Workers, and PnPs expand the execution surface. In production, enable only trusted sources and the smallest possible URL, key, workspace, and network scope; establish approval, audit, or sandboxing for side-effecting tools. For deployment, reverse proxy, TLS, authentication, and cascade-node controls, see [Deployment, Security, and Cascade Networking](deployment-and-security_en.md).
