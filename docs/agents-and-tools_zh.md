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

<a id="agent-registration"></a>

### Agent 与 MCP 注册字段

#### Agent

| 字段 | 说明 |
| --- | --- |
| `enable` / `items[].enable` | 全局 Agent 开关，或单个 Agent 的可选启用开关 |
| `name` | Agent 名称 |
| `driver` | Agent 实现类 |
| `token` / `api_key` | 提供方 token 或 API Key |
| `app_id` / `user_id` | 可选的提供方身份字段 |
| `wrong_case` | 请求不匹配时的兜底回复 |
| `endpoint` | 可选的自定义地址 |
| `mcps` | 可选的 MCP 依赖列表 |

#### MCP

| 字段 | 说明 |
| --- | --- |
| `enable` | 全局 MCP 开关 |
| `name` | MCP 服务名 |
| `url` | SSE 接口地址 |
| `key` | 可选鉴权 Key 字段 |
| `headers` | 自定义请求头 |
| `priority` | 优先级 |
| `servers[].enable` | 当前服务项是否启用 |
| `driver` | MCP 客户端实现，默认是 `ai.mcps.CommonSseMcpClient` |

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

`route: pass(%)` 允许请求以 `agentId` 指定目标；固定场景可写成 `pass(weather)`。上线前核对每个 Agent、MCP、Skill 的 Key、工作目录、网络范围和工具权限，默认禁止不必要的文件、命令和写操作。

<a id="skill-worker-settings"></a>

### Skill 目录、Worker 选择与 PnP 连接器

#### Skills

```yaml
skills:
  enable: true
  roots: ["classpath:skills"]
  workspace: "skills"
  rule: cli
  items:
    - name: extract_content_with_image
      description: 将本地文件拆分为文本与图片块
```

常见字段：

| 字段 | 说明 |
| --- | --- |
| `enable` | 全局技能开关 |
| `roots` | 技能来源根目录 |
| `workspace` | 技能运行使用的本地工作区 |
| `rule` | 技能执行优先策略，如 `cli`、`server`、`block` |
| `items` | 暴露给运行时的技能清单 |

#### Workers

```yaml
skills:
  workers:
    - name: appointedWorker
      route: pass(%)
      worker: ai.worker.DefaultAppointWorker
```

常见字段：

| 字段 | 说明 |
| --- | --- |
| `name` | Worker 名称 |
| `worker` | Worker 实现类 |
| `route` | 路由表达式 |
| `agent` / `agents` | 目标 Agent 或 Agent 集合 |
| `filter` | 可选过滤器覆盖 |

#### Pnps

```yaml
skills:
  pnps:
    - name: qq
      api_key: your-api-key
      driver: ai.pnps.social.QQPnp
```

常见字段：

| 字段 | 说明 |
| --- | --- |
| `name` | 自动化连接器名称 |
| `driver` | Pnp 实现类 |
| `api_key` | 连接器凭证 |

更多 Pnp 可以拆到 `pnp.yml`，再通过 `include_pnps` 引入。

`workers` 与 `pnps` 同属 `skills`。聊天和 Worker 共用[路由语法](models-and-multimodal_zh.md#routing-reference)，过滤规则见[安全策略](deployment-and-security_zh.md#filter-rules)。

<a id="worker-request"></a>

## 三、快速验证 Worker 调用

`POST /chat/go` 是 Agent / Worker 的统一调用入口，通过已配置的 Worker 执行编排请求。重启服务后，用一个低风险问题验证接入。启用鉴权时，携带 LinkMind API Key。

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

### Worker 请求字段

| 字段 | 位置 | 类型 | 必填 | 作用 |
| --- | --- | --- | --- | --- |
| `Content-Type` | header | string | 是 | 使用 `application/json` |
| `Authorization` | header | string | 按部署鉴权要求 | `Bearer <linkmind-api-key>`；需要鉴权时提供有效的客户端 Key |
| 请求体 | body | object | 是 | 包含下面的编排请求字段 |
| `model` | body | string | 否 | 可选模型名，随请求传入 Worker；是否采用由具体 Worker / Agent 实现决定 |
| `temperature` | body | number | 否 | 采样温度；当前请求对象默认 `1`，是否采用取决于 Worker / Agent 及其模型后端 |
| `max_tokens` | body | integer | 否 | 最大生成 Token 数，是否采用取决于 Worker / Agent 及其模型后端 |
| `category` | body | string | 否 | 数据或知识分类 |
| `messages` | body | array of objects | 是 | 按对话顺序提交的有效消息列表 |
| `messages[].role` | body | string | 每条消息应提供 | 消息角色，常用 `user`、`assistant` |
| `messages[].content` | body | string | 普通对话消息应提供 | `user` 消息为用户输入，`assistant` 消息为已有回复 |
| `worker` | body | string | 当前实现是 | 已注册的路由 Worker 名称，例如 `appointedWorker`；当前入口没有自动选择默认 Worker 的逻辑 |
| `agentId` | body | string | 按路由要求 | `route: pass(%)` 时提供目标 Agent 标识；固定路由如 `pass(weather)` 使用配置中的目标 |
| `stream` | body | boolean | 否，建议显式填写 | 只有 `false` 使用普通 JSON；`true` 或未提供时，当前入口进入 SSE 响应分支 |

验证时，先确认 `worker` 与配置中的名称一致，再按该 Worker 的路由填写 `agentId`。`model` 是传给执行链路的模型参数，目标 Agent 由路由和 `agentId` 确定。`temperature` 和 `max_tokens` 为可选字段，不要求为了通过入口解析而一律填写。

### Worker 响应与验收

`stream: false` 的成功响应采用聊天完成结构，正常情况下为 HTTP **200**。下面保留完整的 Worker 响应示例；标识、时间戳、模型、文本和用量为示例值，实际内容由执行的 Worker / Agent 返回：

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

读取 `choices[0].message.content` 获取回复。`id`、`object`、`created`、`model`、`choices`、`finish_reason` 和 `usage` 及其子字段的完整类型、含义与出现条件，见[共用返回字段说明](models-and-multimodal_zh.md#chat-completions-返回结果)。

当前 `/chat/go` 入口先取得 Worker 的完整结果，再将回复文本拆分为 SSE 事件。正常流式文本位于每个事件的 `choices[0].message.content`，按顺序拼接，并持续读取到 `data: [DONE]`；这不表示 Agent 内部正在逐 Token 推送。

验收同时检查客户端回复、LinkMind 控制记录、被选 Worker / Agent、预期的实际工具调用及用量记录。确认调用已到达配置的目标后，再接入正式业务。

## 四、调用方式选择与排查

- **业务系统调用 Worker/Agent**：使用 `/chat/go`，以 `worker` 和 `agentId` 表达编排目标。
- **外部 Agent 运行时只把 LinkMind 当模型网关**：使用 `/v1/chat/completions`、`/v1/responses` 或 `/v1/messages`，模型与路由配置见[模型接入、路由与多模态](models-and-multimodal_zh.md)。
- **MCP 连不上**：检查服务 URL、SSE/HTTP 传输方式、所需请求头和服务器侧日志；不要用聊天成功推断 MCP 已成功。
- **Worker 没走预期 Agent**：检查 Worker 的 `route`、请求的 `agentId`、路由表达式以及目标 Agent 的 `enable` 状态。
- **需要私有资料回答**：在 Worker 使用前先按[知识库](knowledge-base_zh.md)验证上传、检索和分类隔离。

<a id="client-recipes"></a>

## 五、第三方智能体与 CLI 实操

安装器可同步 OpenClaw、Hermes Agent、DeerFlow 和 OpenHuman 的客户端模型配置。LinkMind 仍从自己的 `lagi.yml` 加载后端与路由。同步后，在客户端和 LinkMind 控制页分别验证一次调用。

以下页面是可照着完成的 cookbook，而不是概览说明；其中保留了客户端界面和验证步骤截图：

- [OpenClaw](best-practices/agents/openclaw.md#zh-cn)
- [Hermes Agent](best-practices/agents/hermes-agent.md#zh-cn)
- [DeerFlow](best-practices/agents/deerflow.md#zh-cn)
- [OpenHuman](best-practices/agents/openhuman.md#zh-cn)
- [Codex](best-practices/cli/codex.md#zh-cn)
- [Claude Code](best-practices/cli/claude-code.md#zh-cn)
- [客户端兼容性](best-practices/cli/client-compatibility.md#zh-cn)

## 六、安全边界

Agent、MCP、Skill、Worker 与 PnP 扩大了执行面。生产环境应只启用可信来源、最小范围的 URL、Key、工作目录和网络访问；对有副作用的工具建立审批、审计或沙箱。部署、反向代理、TLS、鉴权和级联节点的安全基线见[部署、安全与级联组网](deployment-and-security_zh.md)。
