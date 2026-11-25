# 快速开始

本页用于完成首次安装与第一条完整调用，是 LinkMind 的统一入口；各功能页会把该功能的配置、接口和验证方式放在一起。

## 一、前置条件

- 运行 JAR 或从源码构建时需要 JDK 8 或更高版本。
- 准备模型厂商的 API Key 和模型 ID。
- 只有选择容器镜像时才需要 Docker。

可使用官方安装器、预打包 JAR、Docker 镜像或源码构建。最快方式是官方安装器：

```powershell
iwr -useb https://cdn.linkmind.top/install.ps1 | iex
```

```bash
curl -fsSL https://cdn.linkmind.top/install.sh | bash
```

所有安装方式、端口、自定义配置目录和各平台 Java 安装说明见[部署、安全与级联组网](deployment-and-security_zh.md)。

## 二、启动并打开控制台

使用预打包应用时执行：

```powershell
java -jar LinkMind.jar
```

打开 `http://localhost:8080`。在 **设置 -> API 密钥** 中新增或确认已有可用 Provider Key；再进入 **聊天 -> 大模型 -> 智能问答**，选择已启用模型并发送一条短消息。

[Web 控制台](index_zh.md)说明各页面入口，以及页面设置与运行时 YAML 的边界。

## 三、完成第一条 API 调用

启用聊天后端后，可调用 OpenAI 兼容接口：

```bash
curl http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer <linkmind-api-key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"Alibaba/qwen-plus","messages":[{"role":"user","content":"你好"}]}'
```

只有同时收到模型回复、并在 LinkMind 中看到对应运行记录，才算调用成功。模型配置、路由、流式输出和多模态接口请继续查看[模型接入、路由与多模态](models-and-multimodal_zh.md)。

## 四、按功能继续查阅

| 目标 | 下一篇 |
| --- | --- |
| 接入模型、路由、图片、音频或视频 | [模型接入、路由与多模态](models-and-multimodal_zh.md) |
| 上传资料并搭建知识库 | [知识库](knowledge-base_zh.md) |
| 接入 Agent、MCP、Skill 或 Worker | [智能体、工具与任务编排](agents-and-tools_zh.md) |
| 用 REST 或 `lagi-core` 集成业务系统 | [系统集成与二次开发](integration_zh.md) |
| 部署、加固或级联节点 | [部署、安全与级联组网](deployment-and-security_zh.md) |
| 新增 Provider、适配器、存储或运行时扩展 | [更多功能接入与自定义扩展](extensions_zh.md) |
