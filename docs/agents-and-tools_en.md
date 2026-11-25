# Agents, Tools, and Task Orchestration

Use this page to configure LinkMind Agents, MCP servers, Skills, Workers, and third-party Agent runtimes.

## 1. Configure Agents and MCP servers

```yaml
agents:
  enable: true
  items:
    - name: weather
      driver: ai.agent.customer.WeatherAgent
      token: your-token

mcps:
  enable: true
  servers:
    - name: amap_mcp
      url: https://mcp.example.com/sse
      priority: 10
      enable: true
```

Move a large Agent or MCP catalog into `agent.yml` or `mcp.yml` and load it with `include_agents` or `include_mcps`. An MCP server entry can also supply `key`, `headers`, and a custom client `driver`.

## 2. Configure Skills and Workers

Workers and PnPs are nested under `skills` in the current configuration:

```yaml
skills:
  enable: true
  roots: ["classpath:skills"]
  workspace: "skills"
  rule: cli
  workers:
    - name: appointedWorker
      route: pass(%)
      worker: ai.worker.DefaultAppointWorker
  pnps:
    - name: qq
      api_key: your-api-key
      driver: ai.pnps.social.QQPnp
```

Use the Worker `route`, `agent`, or `agents` fields to select the target execution path. Add routers under `routers.items` when a Worker needs reusable selection logic. Filters stay under `filters.items` and can be referenced from chat or a Worker.

## 3. Call and verify

- Use `POST /chat/go` to invoke a configured Worker.
- Use OpenAI-compatible chat routes when an Agent runtime is configured as a client of LinkMind.
- Verify the client answer, the LinkMind call record, the selected route, and usage records where billing is enabled.

API contracts, user sessions, API-key operations, credits, and social-channel APIs are grouped in [API Reference](API_en.md#secondary-development-interfaces).

## 4. Agent runtime recipes

Client-specific installation and configuration are kept in Best Practices:

- [OpenClaw](best-practices/agents/openclaw.md)
- [Hermes Agent](best-practices/agents/hermes-agent.md)
- [DeerFlow](best-practices/agents/deerflow.md)
- [OpenHuman](best-practices/agents/openhuman.md)
- [Codex](best-practices/cli/codex.md)
- [Claude Code and compatible clients](best-practices/cli/claude-code.md)

## 5. Safety boundary

An enabled Agent, MCP server, or Skill can access external capabilities. Restrict keys, URLs, tool permissions, workspace paths, and network exposure to the minimum required. See [Deployment and Security](deployment-and-security_en.md) before enabling a new tool in production.
