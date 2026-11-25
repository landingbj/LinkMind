# Web 控制台

Web 控制台用于验证部署、创建 LinkMind API Key、观察调用与管理当前部署的账号、额度和过滤规则。它不替代 `lagi.yml` 中的 Provider、路由、RAG 或 Agent 运行配置。

## 一、首次登录与配置边界

1. 启动 LinkMind 后访问 `http://localhost:8080`；账号服务启用时先注册或登录。
2. 在 **设置 -> API 密钥** 创建或确认用于客户端的 LinkMind API Key。
3. 在 **聊天** 选择启用的模型，发送一条短消息。
4. 打开 **控制**，确认模型、时间、路由和用量记录。

页面中的 Key、余额或模型列表是当前控制面数据，不能替代一次真实调用。Provider 原厂 Key、模型路由与多模态开关仍在[模型接入、路由与多模态](models-and-multimodal_zh.md)中配置。

## 二、页面入口与可验证事项

| 页面 | 用途 | 应同时核对 |
| --- | --- | --- |
| **聊天** | 模型、Agent 与多模态的交互验证 | 模型回复、所选路由和后端日志 |
| **聊天 -> 编排** | 文档、知识库或流程验证 | 上传状态、分类、检索结果与引用 |
| **控制** | 调用、Token、用量、过滤与错误记录 | 请求时间、模型、请求 ID、首包/末包 |
| **设置** | 登录、LinkMind API Key、Provider、过滤规则与额度管理 | 权限范围、有效期、余额与保存状态 |
| **互动** | 发布、订阅或查看级联频道 | `general.cascade_api_address` 与目标节点可达性 |

## 三、API Key、用量和额度

LinkMind API Key 是调用 LinkMind 的凭据；Provider 原厂 Key 用于 LinkMind 调上游模型，二者用途不同。将客户端 Key 放在 `Authorization: Bearer <linkmind-api-key>` 中；Provider Key 只保留在受控部署配置或密钥系统中。

使用量、余额或费用变化应以同一请求的模型、时间、Token 记录和计费服务返回为准。页面没有刷新时先重新获取对应余额/账单接口并检查浏览器网络请求、服务日志和账单服务状态；不要凭支付页面成功或前端缓存推断已入账。

## 四、常见问题排查

- **登录失败**：检查账号服务、数据库连接、反向代理 Cookie/HTTPS 和服务日志。
- **模型列表为空或聊天失败**：检查 `models.enable`、聊天后端、路由、Provider Key/额度和上游网络；操作步骤见[模型接入、路由与多模态](models-and-multimodal_zh.md)。
- **上传后问答没有引用资料**：检查文件处理、Embedding、向量库、`category` 与 RAG 开关；按[知识库](knowledge-base_zh.md)逐段验证。
- **Agent/工具未按预期执行**：检查 Worker/Agent/MCP 启用状态、路由、工具权限和 `/chat/go` 记录；见[智能体接入、工具与任务编排](agents-and-tools_zh.md)。
- **页面请求报错**：保留发生时间、请求 ID、页面路径与脱敏错误，结合控制记录和服务端日志排查，避免提交真实 Key 或客户数据。
