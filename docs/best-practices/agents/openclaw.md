# OpenClaw with LinkMind

## 安装与接入 / Setup

在 PowerShell 检查 `node -v` 与 `git -v`，首次安装可执行：

```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
iwr -useb https://openclaw.ai/install.ps1 | iex
openclaw --version
openclaw gateway start
```

Open `http://127.0.0.1:18789`, select LinkMind as the middleware, choose `linkmind-Pro` or a model synchronized by the installer, and send a test message. LinkMind and OpenClaw on the same host use `http://localhost:8080/v1`; otherwise use a reachable LinkMind address.

验收时同时确认 OpenClaw 收到回复，以及 LinkMind 出现对应调用记录。模型和 API Key 配置见[智能体、工具与任务编排](../../agents-and-tools_zh.md) / [Agents and Tools](../../agents-and-tools_en.md).
