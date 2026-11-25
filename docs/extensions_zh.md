# 更多功能接入与自定义扩展

优先只改配置；仅当现有兼容适配器无法表达 Provider、模态、存储或运行时行为时，再新增 Java 代码。

## 一、选择扩展边界

| 需求 | 优先方式 |
| --- | --- |
| OpenAI 或 Qwen 兼容 Provider | 使用现有兼容适配器新增模型配置 |
| 新模型 ID、上游地址或 Key 池 | 调整 `models` 与 `functions.*` |
| 新 Provider 请求/返回协议 | 实现模型适配器 |
| 新音频、图片或视频能力 | 实现对应模态适配器 |
| 新检索后端 | 实现 `VectorStore` 或继承 `BaseVectorStore` |
| 新工具或任务行为 | 新增 Agent、MCP 服务、Skill 或 Worker |

## 二、接入兼容 Provider

```yaml
models:
  - name: custom-openai
    type: OpenAI-Compatible
    enable: true
    model: my-model
    driver: ai.llm.adapter.impl.OpenAIStandardAdapter
    api_address: https://your-endpoint.example.com/chat/completions
    api_key: your-api-key

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

先验证一条短的非流式请求，再验证流式输出和路由故障切换。已有 Provider 下新增模型 ID 也采用同一配置模式。

## 三、新增适配器或向量库

大语言模型适配器实现 `ai.llm.adapter.ILlmAdapter`；音频、图片和视频适配器实现对应的模态接口。自定义检索后端实现 `ai.vector.VectorStore` 或继承 `ai.vector.impl.BaseVectorStore`，再注册到 `stores.vector`。

厂商协议转换放在适配器内；业务元数据放在 `extra_body` 等类型化扩展中；社交或流程状态放在 Skill 与 Service 辅助层，以保持标准补全和流式链路稳定。

## 四、扩展运行时

在 `agents.items` 中新增 Agent，在 `mcps.servers` 中新增 MCP 服务，在 `skills` 下新增 Skill/Worker。需要共享选择或策略时复用 `routers.items`、`filters.items`。配置和验证见[智能体、工具与任务编排](agents-and-tools_zh.md)。

## 五、稳妥的扩展顺序

1. 先复用兼容适配器，只做配置扩展。
2. 确有必要时再增加最小适配器或存储实现。
3. 通过对应 `functions.*` 功能块对外暴露。
4. 上生产流量前补齐路由、访问控制、可观测性和测试。

旧版更细的实现示例仍保留在[旧版扩展开发文档](extend_zh.md)，现有接入会逐步切换到本页的按功能导航。
