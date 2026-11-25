# Connect an MCP Server

This recipe registers an external MCP server with the smallest possible permission set, validates one harmless tool, and only then exposes it to users or Workers.

## 1. Register the server

Add the server under `mcps.servers` and keep it disabled until connectivity and permissions are checked:

```yaml
mcps:
  enable: true
  servers:
    - name: your_mcp
      url: https://your-mcp.example.com/sse
      headers:
        Authorization: Bearer ${MCP_TOKEN}
      priority: 10
      enable: true
```

Add `key` or `headers` only when the server requires them. The LinkMind host must reach the MCP URL; a browser that can open the URL is not sufficient proof. Keep the server disabled until endpoint, transport, and authentication are verified.

## 2. Bind and test a Worker

Enable the server, restart LinkMind, and invoke an Agent/Worker that is allowed to use it. Start with a read-only operation such as a status or search query. Capture the request time and then confirm the LinkMind control record, selected Worker, MCP server log, and audit data.

If the tool is absent or fails, check the server URL, SSE versus HTTP transport, authorization header, TLS trust, server-side tool list, and the Worker route. Restrict tool permissions, network access, credentials, workspace, and command execution before exposing the tool to users. Configuration and Worker composition are described in [Agents and Tools](../../agents-and-tools_en.md) / [智能体、工具与任务编排](../../agents-and-tools_zh.md).
