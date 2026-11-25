# 智能体接入、工具与任务编排

本页用于配置 LinkMind Agent、MCP 服务、Skill、Worker 与第三方 Agent 运行时。

## 一、配置 Agent 与 MCP 服务

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

较大的 Agent 或 MCP 目录可拆入 `agent.yml`、`mcp.yml`，并通过 `include_agents`、`include_mcps` 加载。MCP 条目还可配置 `key`、`headers` 和自定义 `driver`。

## 二、配置 Skill 与 Worker

当前配置中，Worker 和 PnP 均位于 `skills` 下：

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

使用 Worker 的 `route`、`agent` 或 `agents` 字段选择执行目标。需要复用选择逻辑时，在 `routers.items` 定义路由；过滤器位于 `filters.items`，可被聊天或 Worker 引用。

## 三、调用与验证

- 调用已配置 Worker 使用 `POST /chat/go`。
- Agent 运行时作为 LinkMind 客户端时，使用 OpenAI 兼容聊天接口。
- 同时核对客户端回复、LinkMind 调用记录、实际路由与启用计费后的用量记录。

用户会话、API Key、额度、社交频道等接口约定见[API 参考](API_zh.md#二次开发接口)。

## 四、Agent 运行时实操

各客户端的安装和配置移至最佳实践目录：

- [OpenClaw](best-practices/agents/openclaw.md)
- [Hermes Agent](best-practices/agents/hermes-agent.md)
- [DeerFlow](best-practices/agents/deerflow.md)
- [OpenHuman](best-practices/agents/openhuman.md)
- [Codex](best-practices/cli/codex.md)
- [Claude Code 与兼容客户端](best-practices/cli/claude-code.md)

## 五、安全边界

启用的 Agent、MCP 服务或 Skill 可能访问外部能力。生产环境应只授予必要的 Key、URL、工具权限、工作目录和网络访问范围；上线前请先阅读[部署、安全与级联组网](deployment-and-security_zh.md)。
