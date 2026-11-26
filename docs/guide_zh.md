# 快速开始

本页用于在新机器完成首次安装、启动、控制台核验与第一条 API 调用。完成本页后，再按目标进入模型、知识库或 Agent 页面；每个功能页都包含自己的配置、调用和验证步骤。

[第一份配置](#first-configuration) · [拆分文件](#config-layout) · [原生聊天](#native-chat) · [九个业务场景](#business-scenarios)

## 一、安装并启动

运行 LinkMind 需要 JDK 8 或更高版本；使用 Docker 时需要 Docker。以下安装方式是并列选择，不需要全部执行：

```powershell
iwr -useb https://cdn.linkmind.top/install.ps1 | iex
```

```bash
curl -fsSL https://cdn.linkmind.top/install.sh | bash
java -jar LinkMind.jar
```

安装器和 JAR 首次启动会准备默认 `config/`、`config/lagi.yml` 与 `data/`。需要 Docker、WAR、私有目录、反向代理或生产运行参数时，直接查看[部署、安全与级联组网](deployment-and-security_zh.md)。

<a id="first-configuration"></a>

## 二、完成最小模型配置

运行独立 JAR 时编辑配置目录里的 `lagi.yml`。使用 `--config=/path/to/config` 指定目录。嵌入 Java 应用时可用 `-Dlinkmind.config=/path/to/lagi.yml` 指定文件。

下例启用一个聊天后端，保留可选的 Embedding、RAG 和 Medusa 配置供后续启用。用已开通模型的厂商凭证替换占位符。`access_key_id` 和 `access_key_secret` 用于需要云访问凭证的能力。首次只验证聊天，可先省略 Embedding 与云访问凭证。

```yaml
system_title: LinkMind

models:
  - name: qwen
    type: Alibaba
    enable: true
    model: qwen-plus,qwen-max,asr,vision,ocr
    driver: ai.wrapper.impl.AlibabaAdapter
    api_key: your-api-key
    # 多个凭证可改用下面的密钥池，去掉上一行。
    # api_keys: key-one,key-two
    # key_route: polling  # polling / failover
    access_key_id: your-access-key-id
    access_key_secret: your-access-key-secret

stores:
  vector:
    - name: chroma
      driver: ai.vector.impl.ChromaVectorStore
      default_category: default
      similarity_top_k: 10
      similarity_cutoff: 0.5
      parent_depth: 1
      child_depth: 1
      url: http://localhost:8000
  rag:
    vector: chroma
    enable: false
    priority: 10
    track: true
    html: true
    default: "Please give prompt more precisely"
  medusa:
    enable: false

functions:
  embedding:
    - backend: qwen
      type: Qwen
      api_key: your-api-key

  chat:
    route: pass(qwen)
    filter: sensitive,priority,stopping,continue
    backends:
      - backend: qwen
        model: qwen-plus
        enable: true
        stream: true
        protocol: completion
        priority: 100

routers:
  enable: true
  items:
    - name: pass
      rule: (%)
```

重启服务后打开 `http://localhost:8080`。如部署启用了账号服务，先登录；在 **设置 -> API 密钥** 创建或确认 LinkMind API Key，在 **聊天 -> 大模型 -> 智能问答** 选择已启用模型并发送一句短消息。

<a id="config-layout"></a>

### 主配置、拆分文件和顶层字段

LinkMind 采用“主配置 + 按需拆分”的方式：

| 配置项 | 作用 |
| --- | --- |
| `lagi.yml` | 主运行配置 |
| `include_models: model.yml` | 补充模型定义 |
| `include_stores: store.yml` | 补充存储配置 |
| `include_agents: agent.yml` | 补充 Agent 定义 |
| `include_mcps: mcp.yml` | 补充 MCP 服务 |
| `include_pnps: pnp.yml` | 补充自动化连接器 |

如果你通过 `-Dlinkmind.config=/path/to/lagi.yml` 启动 LinkMind，这个自定义文件会成为真正的主入口配置。

| 区块 | 控制内容 |
| --- | --- |
| `system_title` | 前端展示的系统标题 |
| `models` | 模型提供方与适配器定义 |
| `stores` | 向量库、对象存储、词项检索、关系型数据库、RAG 与 Medusa |
| `functions` | 对话与多模态能力编排 |
| `agents` | 内置或自定义 Agent |
| `mcps` | MCP 服务注册 |
| `skills` | 技能根目录、工作区、workers 与 pnps |
| `routers` | 路由表达式，如 `best(...)`、`pass(...)` |
| `filters` | 敏感词、优先级、会话控制等过滤器 |

## 三、进行第一条 API 调用

从控制台复制 LinkMind API Key 后，执行：

```bash
curl http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer <linkmind-api-key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"qwen-plus","messages":[{"role":"user","content":"你好，LinkMind。"}],"stream":false,"max_completion_tokens":128}'
```

成功条件是返回 `choices[0].message.content`，且 **控制** 页面出现同一时间的模型调用。浏览器能打开控制台、HTTP 返回 200、或模型出现在下拉框中，都不能单独证明上游模型已可用。

<a id="native-chat"></a>

### 调用 LinkMind 原生聊天路由

普通文本对话也可调用 `POST /chat/completions`。响应中的 `choices[0].message.content` 是本次回答。

```bash
curl http://localhost:8080/chat/completions \
  -H "Authorization: Bearer <linkmind-api-key>" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "qwen-plus",
    "stream": false,
    "messages": [
      {"role": "user", "content": "列出 LinkMind 的三个核心能力。"}
    ]
  }'
```

## 四、按功能继续

| 目标 | 页面 |
| --- | --- |
| 接入模型、路由、流式与多模态 | [模型接入、路由与多模态](models-and-multimodal_zh.md) |
| 上传资料、配置 SQLite/Chroma、验证 RAG | [知识库](knowledge-base_zh.md) |
| 接入 Agent、MCP、Skill 或 Worker | [智能体接入、工具与任务编排](agents-and-tools_zh.md) |
| 用 REST 或 `lagi-core` 集成业务系统 | [系统集成与二次开发](integration_zh.md) |
| 部署、加固、级联或故障排查 | [部署、安全与级联组网](deployment-and-security_zh.md) |
| 新增 Provider、适配器或存储 | [更多功能接入与自定义扩展](extensions_zh.md) |

<a id="business-scenarios"></a>

## 五、按业务场景选型、搭建与验收

<a id="scenario-selection"></a>

### 先选落地形态

| 当前业务状况 | 优先形态 | 核心目标 |
| --- | --- | --- |
| 已有多个 Agent 或多条业务线，需要共用能力 | [LinkMind 团队 AI 枢纽](#scenario-team-hub) | 统一 Skill、Tool、模型路由、安全和观测 |
| 有私有文档、数据库或制度知识，需要企业知识问答 | [Knowledge Base Server](#scenario-enterprise-knowledge) | 通过 RAG 让回答基于企业真实知识 |
| 已有 OpenClaw、Hermes Agent、DeerFlow 或其他 Agent 工作台 | [LinkMind Agent 前端集成](#scenario-agent-frontend) | 透明接入，降低 Token 消耗，补安全边界 |
| 本地一体机或边缘节点 GPU 资源有限，重复问答多 | [Medusa 加速器](#scenario-medusa) | 缓存复用，减少重复推理，降低 GPU 负载 |
| 金融、医疗、政务等高合规场景 | [Security Gateway](#scenario-security-gateway) | 统一过滤、权限、审计和电子围栏 |
| 多部门、多系统共用 AI 能力 | [AI 聚合中台](#scenario-aggregation-platform) | 统一接入、编排、治理、观测和成本核算 |
| 已有 RAG 平台，只缺统一向量化入口 | [Embedding Server](#scenario-embedding-server) | 统一 Embedding API，模型可替换 |
| 多区域、多团队、集团型组织需要统一代理出口 | [Proxy + GEO + Cascade](#scenario-regional-cascade) | 分布式组网、分层治理、跨区域接入 |
| 准备上线、扩容或版本对比，需要容量与稳定性基线 | [Benchmark 压力测试](#scenario-benchmark) | 固定并发、轮次、模型和数据集，沉淀可复跑结果 |

这些能力可以组合使用。常见路径是先用单节点 LinkMind 或已有 Agent 前端集成做 POC，用 Benchmark 压力测试建立容量基线，再逐步扩展为 LinkMind 团队 AI 枢纽 + Security Gateway + Knowledge Base Server 的完整企业 AI 中台。

以下场景列出中间件和部署方共同完成的上线工作。按人或租户分配独立凭证、配额与审计时，由业务平台或 API 网关实施这些策略；可用同一请求标识核对网关、LinkMind 和计费服务的记录。

<a id="scenario-deployment-steps"></a>

### 业务接入的标准步骤

1. 明确入口：先确定 LinkMind 服务的是 Web/App/API、内部 OA/CRM/ERP、客服系统、研发工具，还是已有 Agent 工作台。
2. 明确数据边界：梳理哪些请求可以出网，哪些知识必须私有化，哪些字段需要脱敏、过滤或审计。
3. 选择部署方式：试点可从官方安装脚本、JAR 或 Docker 开始；生产可按单节点、集群、网关或 GEO/Cascade 拓扑扩展。
4. 配置模型与路由：在 `lagi.yml` 中启用模型提供方、路由规则、故障切换、过滤器、RAG 和需要的 Skills。
5. 接入业务调用：按客户端协议选择 LinkMind 原生路由、OpenAI Chat Completions `/v1/chat/completions`、OpenAI Responses `/v1/responses`，或 Anthropic Messages `/v1/messages`，不直接绑定具体模型厂商。
6. 加上治理：启用 API Key、用户体系、过滤器、黑白名单、Token 统计和调用链路观测。
7. 小范围试点：选择一条业务线验证响应质量、检索命中率、Token 消耗、延迟、错误率和安全策略效果。
8. 压力测试：用 `tools/benchmark` 下的 Benchmark 脚本固定并发、轮次、模型和测试数据，保存运行日志并形成可复跑基线。
9. 扩展上线：把验证过的路由、策略、知识库和 Skills 推广到更多业务线或更多 LinkMind 节点。

试点资源可以按轻量规格开始：最低可参考 8 核 CPU、32 GB 内存、200 GB 存储；如果本地模型推理是核心诉求，再准备 GPU 服务器。若主要对接云端模型 API，POC 阶段通常不需要本地 GPU。

<a id="scenario-team-hub"></a>

### 场景 1：团队 AI 枢纽

LinkMind 适合多个 Agent、多个业务线或多个团队共用一套 AI 能力底座的情况。业务 Agent 不再各自维护模型 Key、工具注册、安全规则和观测逻辑，而是统一调用 LinkMind。

业务怎么用：

- 多个 Agent 共用同一批 Skills、Tools、模型路由和安全策略。
- 业务线只负责自己的任务编排，把通用能力交给 LinkMind 托管。
- 平台团队通过 Token 统计、调用链和错误率观察各业务线使用情况。

怎么架设：

1. 用 [安装指南](deployment-and-security_zh.md) 启动 LinkMind，试点阶段可以先跑单节点。
2. 在 [配置参考](models-and-multimodal_zh.md) 中启用至少一个聊天模型、路由规则和需要的 Skills。
3. 给各 Agent 分配 LinkMind 入口地址和 API Key。
4. 让 Agent 统一调用 `/chat/completions` 或 `/v1/chat/completions`。
5. 生产环境再接入网关、负载均衡和多节点部署，避免单点瓶颈。

<a id="scenario-enterprise-knowledge"></a>

### 场景 2：Knowledge Base Server

Knowledge Base Server 适合企业已经拥有制度文档、产品资料、FAQ、工单、数据库或行业知识，希望模型回答基于企业自身知识，而不是只依赖通用参数。

业务怎么用：

- 客服、销售、运营、研发支持等系统把用户问题交给 LinkMind。
- LinkMind 先从向量库、数据库或文档索引中检索相关上下文。
- 检索结果随请求注入模型链路，模型基于企业知识作答。
- 新资料上线后增量更新知识库，答案随业务持续迭代。

怎么架设：

1. 准备知识来源，例如 PDF、Word、HTML、FAQ、MySQL、Elasticsearch、Chroma、Milvus 或其他向量库。
2. 按 [知识库配置](knowledge-base_zh.md#retrieval-settings) 和 [配置参考](models-and-multimodal_zh.md) 配置向量存储、Embedding 模型和检索链路。
3. 通过文档处理、OCR、Embedding 等能力把资料切分、向量化并写入检索组件。
4. 让业务系统调用聊天接口，由 LinkMind 自动完成检索增强。
5. 对高价值知识库设置更新、审核和回滚流程，避免未确认内容直接进入生产问答。

<a id="scenario-agent-frontend"></a>

### 场景 3：已有 Agent 前端接入

该场景适合已有 OpenClaw、Hermes Agent、DeerFlow 或其他 Agent 工作台的团队。LinkMind 不替代现有系统，而是作为兼容 Provider 或旁路中间层接入：支持 OpenAI Chat Completions / Responses 的客户端可使用对应入口，支持 Anthropic Messages 的客户端可使用 `/v1/messages`，由 LinkMind 统一增强模型调用。

业务怎么用：

- 原 Agent 继续按原流程工作，模型调用经过 LinkMind。
- LinkMind 在中间层做 Token 节流、上下文裁剪、结构化复用和按需召回。
- 输入输出过滤、黑名单 Skill、权限策略和模型路由统一生效。
- 适合先接一条 Agent 链路验证，再推广到团队。

怎么架设：

1. 安装时按需选择接入 Agent 前端；OpenClaw、Hermes Agent、DeerFlow、OpenHuman 的安装、配置和验证请按[Agent 客户端接入实操](agents-and-tools_zh.md#client-recipes)执行。
2. 按现有 Agent 支持的协议选择 LinkMind 的 Chat Completions、Responses 或 Messages 入口；客户端的协议设置必须与入口一致。
3. 按客户端要求配置 base URL、模型名和鉴权：OpenAI 客户端通常使用带 `/v1` 的 base URL；Claude Code 的 `ANTHROPIC_BASE_URL` 使用服务根地址，由客户端追加 `/v1/messages`。按客户端选择[对应接入教程](agents-and-tools_zh.md#client-recipes)。
4. 开启过滤器、Token 统计和路由故障切换。
5. 对比接入前后的 Token 消耗、延迟、失败率和安全拦截结果。

<a id="scenario-medusa"></a>

### 场景 4：Medusa 加速器

Medusa 适合本地一体机、边缘计算节点、本地大模型部署和高重复率问答。它通过命中判断、预读缓存和结果回写减少重复推理，让有限 GPU 资源承担更多业务流量。

业务怎么用：

- 高频 FAQ、客服问询、政策咨询和内部助手先尝试命中缓存。
- 未命中时再调用本地模型、RAG 或上游模型。
- 结果回写缓存，后续相似请求可以更快响应。

怎么架设：

1. 在本地或边缘节点部署 LinkMind，并接入本地模型或云端模型。
2. 开启 Medusa 缓存能力和必要的 RAG 链路。
3. 根据业务把高频、可复用、低风险的问题优先纳入缓存策略。
4. 观察缓存命中率、GPU 负载、平均响应时间和回答一致性。

<a id="scenario-security-gateway"></a>

### 场景 5：Security Gateway

Security Gateway 适合外部用户直接接入 AI、行业合规要求高、插件或 Skills 较多的部署环境。它把权限、过滤、审计和可调用能力边界前置到 LinkMind。

业务怎么用：

- 所有外部请求先经过 LinkMind，再进入模型、工具和私有数据。
- Filters 做输入输出双向过滤，降低提示词注入、敏感信息泄露和不当输出风险。
- 黑白名单与漏洞 Skill 检测限制高风险组件调用。
- 审计和 Token 统计支撑合规检查、成本核算和异常追踪。

怎么架设：

1. 让业务系统、Web 入口或 API Gateway 统一转发到 LinkMind。
2. 在 `lagi.yml` 中配置 `filters`、路由策略、权限策略和可用模型范围。
3. 启用 API Key、用户身份和必要的审计字段。
4. 按部门、应用或租户拆分 Key、配额和调用策略。
5. 对安全规则做灰度验证，避免误拦核心业务请求。

<a id="scenario-aggregation-platform"></a>

### 场景 6：AI 聚合中台

AI 聚合中台适合企业内多个系统、多个部门、多个模型和多模态能力共用的场景。LinkMind 作为统一 AI 中台，提供标准 API、统一配置、统一编排和统一观测。

业务怎么用：

- OA、CRM、ERP、客服、知识库、研发工具和移动端统一访问 LinkMind。
- 文本、语音、图像、OCR、文档处理、Text-to-SQL 等能力从同一平台输出。
- 按部门、项目、系统或 Agent 统计 Token 和成本。
- 新模型接入只改中台配置，不要求每个业务系统重写。

怎么架设：

1. 以 Docker、JAR 或源码构建方式部署 LinkMind。
2. 生产环境建议放在统一网关后，并根据并发量扩展多节点。
3. 在配置中接入多个模型提供方、RAG、Skills、过滤器和路由规则。
4. 给每个业务系统分配独立 API Key、配额和可用能力范围。
5. 建立模型变更、策略变更和知识库更新的发布流程。

<a id="scenario-embedding-server"></a>

### 场景 7：Embedding Server

Embedding Server 适合已经有 RAG 平台、搜索平台或向量库，但缺少统一向量化入口的团队。LinkMind 可以单独作为兼容 OpenAI 的 Embedding 服务层。

业务怎么用：

- 文档切片、查询文本和结构化字段统一发给 LinkMind 生成向量。
- LinkMind 按语言、领域、任务类型路由到合适的 Embedding 模型。
- 上层知识平台不用关心底层向量模型的替换和升级。

怎么架设：

1. 在 `lagi.yml` 中启用 Embedding 模型和对应 Provider。
2. 让现有 RAG 或搜索系统调用 `/v1/embeddings`。
3. 保持向量维度、模型版本和索引重建策略可追踪。
4. 模型升级时先做小样本评估，再决定是否重建全量索引。

<a id="scenario-regional-cascade"></a>

### 场景 8：Proxy + GEO + Cascade

Proxy、GEO 和 Cascade 适合多区域、多组织、集团型客户或跨网络环境。LinkMind 不只是 API 转发，而是带有路由、安全、缓存、计费和审计能力的智能代理层。

业务怎么用：

- 多个系统共享统一模型出口，避免各团队直接持有上游模型 Key。
- 不同区域用户就近访问本地 LinkMind 节点，降低延迟。
- 父节点统一下发策略，子节点服务本地业务和 Agent。
- 串联节点可以分层承担安全、路由、缓存等不同职责。

怎么架设：

1. 单区域先部署一个 LinkMind 代理节点，承接模型出口、过滤和成本统计。
2. 多区域部署多个 LinkMind 节点，按网络、合规和延迟策略路由请求。
3. 大型组织可采用上下级级联：总部节点管理策略，区域节点处理本地业务。
4. 在每层明确数据是否可跨区、日志保留周期、Key 管理和故障切换规则。

<a id="scenario-benchmark"></a>

### 场景 9：Benchmark 压力测试

Benchmark 压力测试适合 POC 验收、上线前容量验证、版本升级前后对比、缓存/路由策略调整后复测，以及排查高并发下的延迟、失败率和 Token 生成速度变化。它的目标不是一次跑出“生产容量结论”，而是把脚本、测试数据、参数和运行结果沉淀成可复跑证据。

业务怎么用：

- 固定并发数、执行轮次、模型、请求参数和测试问题池，建立同口径基线。
- 对比不同版本、不同模型、不同缓存策略或不同部署规格下的响应差异。
- 关注状态码、总耗时、tokens/秒、失败、超时和错误堆栈，避免只看平均值。
- 将脚本、测试数据和运行日志统一放在 `tools/benchmark` 目录，方便交付、研发和运维共同复盘。

怎么架设：

1. 先启动 LinkMind，并确认 `http://127.0.0.1:8080/chat/completions` 或目标环境接口可访问。
2. 查看 [压测工具说明](../tools/benchmark/README.md)，按需调整 `concurrent_testing.py` 中的接口地址、`API_KEY`、`MODEL`、`MAX_THREADS`、`FETCH_TIMES` 和请求体参数。
3. 从 `tools/benchmark` 目录运行脚本，并把控制台输出重定向为日志文件。
4. 每次测试记录环境、版本、模型、并发、轮次、缓存状态和测试数据范围，避免把预热、环境漂移或压测端瓶颈误判为服务端变化。
5. 上线前至少保留优化前后两份同口径结果；如需容量结论，还要结合服务端 CPU/内存/IO、下游依赖、p95/p99、错误率和压测机资源一起判断。

<a id="scenario-launch-checks"></a>

### 上线检查清单

- 已确认业务入口、用户身份、调用方系统和 API Key 管理方式。
- 已启用至少一个稳定模型 Provider，并配置备用模型或故障切换规则。
- RAG 场景已确认知识来源、更新频率、权限边界和向量库策略。
- 已按业务风险启用 Filters、黑白名单、审计字段和必要的内容策略。
- 已验证 Token 统计、成本归因、错误率、延迟和缓存命中率。
- Benchmark 场景已保留脚本参数、测试数据、运行日志和环境说明，且未用单次平均值替代容量结论。
- 已准备灰度方案，能按业务线、部门或 Agent 分批放量。
- 已明确模型 Key、私有数据、日志和缓存的存储责任边界。
