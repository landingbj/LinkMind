# Connect an MCP Server

## 接入 / Setup

Add the server under `mcps.servers` and keep it disabled until connectivity and permissions are checked:

```yaml
mcps:
  enable: true
  servers:
    - name: your_mcp
      url: https://your-mcp.example.com/sse
      priority: 10
      enable: true
```

Add `key` or `headers` only when the server requires them. Test a single harmless tool call, then confirm the LinkMind call record and audit data. Restrict tool permissions, network access, and credentials before exposing the tool to users. Configuration and Worker composition are described in [Agents and Tools](../../agents-and-tools_en.md) / [智能体、工具与任务编排](../../agents-and-tools_zh.md).
