# 更多功能接入与自定义扩展

优先复用配置与现有兼容适配器；只有现有能力不能表达 Provider、模态、存储或运行时行为时，才新增 Java 代码。本页给出扩展边界、最小配置、实现入口和验证顺序。

## 一、先选择正确的扩展边界

| 需求 | 优先做法 |
| --- | --- |
| 新模型 ID、Key 池、上游地址或路由 | 修改 `models` 与 `functions.*` |
| OpenAI、Qwen 等兼容 Provider | 复用现有兼容适配器 |
| 新厂商请求/响应协议 | 实现 LLM 适配器 |
| 新语音、图片、视频或文档能力 | 实现相应模态适配器 |
| 新检索后端 | 实现 `VectorStore` 或继承 `BaseVectorStore` |
| 新工具或任务行为 | 新增 Agent、MCP、Skill 或 Worker |

调用方保持 LinkMind 的稳定地址与协议；厂商差异、鉴权和字段转换留在适配器与配置中。

## 二、接入兼容模型 Provider 并验证

OpenAI 兼容服务通常无需编写新代码：

```yaml
models:
  - name: custom-openai
    type: OpenAI-Compatible
    enable: true
    model: my-model
    driver: ai.llm.adapter.impl.OpenAIStandardAdapter
    api_address: https://your-endpoint.example.com/chat/completions
    api_key: ${CUSTOM_PROVIDER_KEY}

functions:
  chat:
    route: pass(custom-openai)
    backends:
      - backend: custom-openai
        model: my-model
        enable: true
        stream: true
        protocol: completion
        priority: 100
```

重启后先调 `GET /v1/models`，再以 `POST /v1/chat/completions` 发一条非流式短消息，最后用 `stream:true` 验证 SSE 包与 `[DONE]`。不要在未验证上游错误结构、超时、工具调用和流式结束行为前接入生产流量。

## 三、实现新适配器或存储

大语言模型适配器实现 `ai.llm.adapter.ILlmAdapter`；音频、图片、视频和文档能力实现对应模态接口；自定义检索后端实现 `ai.vector.VectorStore` 或继承 `ai.vector.impl.BaseVectorStore`，随后注册到 `stores.vector`。

实现时应遵循三个边界：

1. 厂商请求与响应转换只放在适配器内，避免泄漏到业务调用方。
2. 业务数据通过 `extra_body` 等类型化字段传递，不改变标准聊天消息结构。
3. 社交或流程状态放入 Skill、Worker 和 Service 辅助层，不阻塞标准补全、路由和流式链路。

向量库至少验证建库/连接、写入、按 `category` 查询、更新、删除、元数据过滤和并发边界。具体 RAG 调用路径见[知识库](knowledge-base_zh.md)。

## 四、扩展运行时与测试顺序

在 `agents.items` 注册 Agent，在 `mcps.servers` 注册 MCP 服务，在 `skills` 下注册 Skill、Worker 或 PnP；共享选择策略使用 `routers.items`，内容治理使用 `filters.items`。统一调用入口和请求字段见[智能体接入、工具与任务编排](agents-and-tools_zh.md)。

推荐顺序是：复用兼容适配器并做配置验证 -> 编写最小适配器或存储实现 -> 对外启用对应 `functions.*` -> 分别测试非流式、流式、错误、超时、并发和鉴权 -> 最后启用路由、计费、工具和生产流量。

每个扩展应同时提供：无真实 Key 的示例配置、一条可执行验证请求、预期输出或日志特征、失败时的排查点，以及单元或集成测试。不要把真实 Provider Key、客户文档或生产 URL 写进示例。
