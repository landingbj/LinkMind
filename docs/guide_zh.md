# 快速开始

本页用于在新机器完成首次安装、启动、控制台核验与第一条 API 调用。完成本页后，再按目标进入模型、知识库或 Agent 页面；每个功能页都包含自己的配置、调用和验证步骤。

## 一、安装并启动

运行 LinkMind 需要 JDK 8 或更高版本；使用 Docker 时需要 Docker。以下安装方式是并列选择，不需要全部执行：

```powershell
iwr -useb https://cdn.linkmind.top/install.ps1 | iex
```

```bash
curl -fsSL https://cdn.linkmind.top/install.sh | bash
java -jar LinkMind.jar
```

安装器和 JAR 首次启动会准备默认 `config/`、`config/lagi.yml` 与 `data/`。需要 Docker、WAR、私有目录、反向代理或生产运行参数时，直接查看[部署、安全与级联组网](deployment-and-security_zh.md)，不要在本页之外继续寻找单独的安装手册。

## 二、完成最小模型配置

编辑 `config/lagi.yml` 或由 `-Dlinkmind.config` 指定的主配置，至少配置一个 Provider 和一个聊天后端。用环境变量或安全配置系统提供真实 Key：

```yaml
models:
  - name: qwen
    type: Alibaba
    enable: true
    model: qwen-plus
    driver: ai.wrapper.impl.AlibabaAdapter
    api_key: ${QWEN_API_KEY}

functions:
  chat:
    route: pass(qwen)
    backends:
      - backend: qwen
        model: qwen-plus
        enable: true
        stream: true
        protocol: completion
        priority: 100
```

重启服务后打开 `http://localhost:8080`。如部署启用了账号服务，先登录；在 **设置 -> API 密钥** 创建或确认 LinkMind API Key，在 **聊天 -> 大模型 -> 智能问答** 选择已启用模型并发送一句短消息。

## 三、进行第一条 API 调用

从控制台复制 LinkMind API Key 后，执行：

```bash
curl http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer <linkmind-api-key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"qwen-plus","messages":[{"role":"user","content":"你好，LinkMind。"}],"stream":false,"max_completion_tokens":128}'
```

成功条件是返回 `choices[0].message.content`，且 **控制** 页面出现同一时间的模型调用。浏览器能打开控制台、HTTP 返回 200、或模型出现在下拉框中，都不能单独证明上游模型已可用。

## 四、按功能继续

| 目标 | 页面 |
| --- | --- |
| 接入模型、路由、流式与多模态 | [模型接入、路由与多模态](models-and-multimodal_zh.md) |
| 上传资料、配置 SQLite/Chroma、验证 RAG | [知识库](knowledge-base_zh.md) |
| 接入 Agent、MCP、Skill 或 Worker | [智能体接入、工具与任务编排](agents-and-tools_zh.md) |
| 用 REST 或 `lagi-core` 集成业务系统 | [系统集成与二次开发](integration_zh.md) |
| 部署、加固、级联或故障排查 | [部署、安全与级联组网](deployment-and-security_zh.md) |
| 新增 Provider、适配器或存储 | [更多功能接入与自定义扩展](extensions_zh.md) |
