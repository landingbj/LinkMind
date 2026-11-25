# 智能体接入、工具与任务编排

本页说明如何在 LinkMind 中注册 Agent、MCP、Skill、Worker 与路由，并用统一的 Worker 接口完成验证。第三方客户端的逐步安装图文教程在[最佳实践](#五第三方智能体与-cli-实操)中单独维护。

## 一、配置 Agent 与 MCP 服务

把可调用的 Agent 和 MCP 服务写入 `lagi.yml`。较大的目录可拆为 `agent.yml`、`mcp.yml`，主配置用 `include_agents`、`include_mcps` 引入；拆分文件仍应与主配置一并纳入变更和备份。

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

`name` 必须在当前部署内唯一；`driver` 指向本地实现；MCP 的 `url`、`headers`、`key` 和自定义 `driver` 只配置为目标服务实际要求的值。先用只读或无副作用的工具验证连接，随后才开放写入、命令执行或外部系统权限。

## 二、配置 Skill、Worker、路由与过滤器

当前配置中 Worker 和 PnP 位于 `skills` 下，而不是顶层。Worker 通过 `route`、`agent` 或 `agents` 选择执行目标；可复用路由在 `routers.items` 声明，聊天和 Worker 可引用过滤器。

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

`route: pass(%)` 允许请求以 `agentId` 指定目标；固定场景可写成 `pass(weather)`。上线前核对每个 Agent、MCP、Skill 的 Key、工作目录、网络范围和工具权限，默认禁止不必要的文件、命令和写操作。

## 三、快速验证 Worker 调用

重启服务后，用 `POST /chat/go` 调一个低风险问题。启用鉴权时，携带 LinkMind API Key。

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
    "messages":[{"role":"user","content":"请确认你已收到测试请求。"}]
  }'
```

| 字段 | 说明 |
| --- | --- |
| `messages` | 必填的对话消息，元素包含 `role` 和 `content` |
| `worker` | 可选的 Worker 名称；不填时由部署默认行为决定 |
| `agentId` | 可选的指定 Agent 标识，适用于 `pass(%)` |
| `category` | 可选的数据或知识分类 |
| `stream` | 是否按 SSE 流式返回 |
| `temperature` / `max_tokens` | 采样温度与最大生成 Token |

非流式成功响应采用聊天完成结构，包含 `id`、`model`、`choices[0].message.content` 和 `usage`。流式时必须读到 `data: [DONE]`。验收同时检查：客户端回复、LinkMind 控制记录、被选 Worker/Agent、实际工具调用及用量记录。

## 四、调用方式选择与排查

- **业务系统调用 Worker/Agent**：使用 `/chat/go`，以 `worker` 和 `agentId` 表达编排目标。
- **外部 Agent 运行时只把 LinkMind 当模型网关**：使用 `/v1/chat/completions`、`/v1/responses` 或 `/v1/messages`，模型与路由配置见[模型接入、路由与多模态](models-and-multimodal_zh.md)。
- **MCP 连不上**：检查服务 URL、SSE/HTTP 传输方式、所需请求头和服务器侧日志；不要用聊天成功推断 MCP 已成功。
- **Worker 没走预期 Agent**：检查 Worker 的 `route`、请求的 `agentId`、路由表达式以及目标 Agent 的 `enable` 状态。
- **需要私有资料回答**：在 Worker 使用前先按[知识库](knowledge-base_zh.md)验证上传、检索和分类隔离。

## 五、第三方智能体与 CLI 实操

以下页面是可照着完成的 cookbook，而不是概览说明；其中保留了客户端界面和验证步骤截图：

- [OpenClaw](best-practices/agents/openclaw.md)
- [Hermes Agent](best-practices/agents/hermes-agent.md)
- [DeerFlow](best-practices/agents/deerflow.md)
- [OpenHuman](best-practices/agents/openhuman.md)
- [Codex](best-practices/cli/codex.md)
- [Claude Code](best-practices/cli/claude-code.md)
- [客户端兼容性](best-practices/cli/client-compatibility.md)

## 六、安全边界

Agent、MCP、Skill、Worker 与 PnP 扩大了执行面。生产环境应只启用可信来源、最小范围的 URL、Key、工作目录和网络访问；对有副作用的工具建立审批、审计或沙箱。部署、反向代理、TLS、鉴权和级联节点的安全基线见[部署、安全与级联组网](deployment-and-security_zh.md)。
