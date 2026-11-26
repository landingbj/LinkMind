简体中文 | [English](README.md)

# <img src="docs/images/logo/LinkMind%20logo%281%29.png" alt="LinkMind logo" width="42"> LinkMind

LinkMind 是面向企业场景的多模态 AI 中间件，用来把业务系统、私有知识、模型厂商和 Agent 运行时统一接到一层可治理、可扩展、可上线的能力层里。它优先解决的是企业真正落地时最常见的几个问题：上手慢、接入碎、成本高、稳定性差。

## 项目简介

当前代码已经覆盖统一聊天入口、RAG、OCR、ASR/TTS、图片与视频能力、文档处理、Text-to-SQL、Embedding、Rerank、MCP、Skills、Worker 编排，以及兼容 OpenAI Chat Completions / Responses 和 Anthropic Messages 的接口。同时，项目还内置了 OpenClaw、Hermes Agent、DeerFlow、OpenHuman 的配置同步能力，便于接入现有 Agent 工作流。



## 为什么选 LinkMind

- 一层中间件同时覆盖聊天、OCR、ASR/TTS、图片生成、图像与视频理解、Text-to-SQL、Embedding、Rerank、文档处理等能力。
- 多模型路由、故障切换和编排统一配置在 `lagi.yml` 中，业务系统不用为不同厂商重复改接口。
- RAG 可以直接接到 Chroma、Elasticsearch、Milvus、MySQL、Pinecone、SQLite 等检索组件，也能继续扩展图谱类增强能力。
- Medusa 缓存加速、Token 统计、过滤器和运行时治理，都是面向真实生产场景的稳定性与成本问题设计的。
- OpenClaw、Hermes Agent、DeerFlow、OpenHuman 的接入钩子已经内置，适合在现有 Agent 工作流里逐步落地。

  <a href="docs/images/img_24.png">
    <img src="docs/images/img_24.png" alt="LinkMind 运行时集成示意图">
  </a>

## 几分钟上手

下面 4 种方式是并列选项，任选其一即可。

### 选项1：官方安装脚本快速安装

前置要求为 **JDK 8 或以上版本**。按操作系统查看 [JDK 安装与验证](docs/deployment-and-security_zh.md#install-jdk)。

- Windows PowerShell

  ```powershell
  iwr -useb https://cdn.linkmind.top/install.ps1 | iex
  ```

- macOS / Linux

  ```bash
  curl -fsSL https://cdn.linkmind.top/install.sh | bash
  ```

LinkMind 采用统一运行形态：可作为团队 AI 枢纽部署在硬件盒子、服务器、个人电脑或 Docker 中；若本机已有 OpenClaw、Hermes Agent、DeerFlow、OpenHuman 等前端，可按需接入并保留配置同步能力。

### 选项2：下载并运行 JAR 包

预打包资源：

- 应用文件：`LinkMind.jar`，[点击这里下载](https://cdn.linkmind.top/installer/LinkMind.jar)
- 核心库文件：`lagi-core-1.2.0-jar-with-dependencies.jar`，[点击这里下载](https://ai.linkmind.top/lagi/lib/lagi-core-1.2.0-jar-with-dependencies.jar)

```powershell
java -jar LinkMind.jar
```

首次启动会自动生成 `config/`、`data/` 和默认的 `lagi.yml`，随后访问 `http://localhost:8080` 即可。

### 选项3：使用 Docker 镜像

镜像名称：`landingbj/linkmind`

```bash
docker pull landingbj/linkmind
docker run -d -p 8080:8080 landingbj/linkmind
```

启动后访问 `http://localhost:8080`。

### 选项4：从源码编译

```bash
mvn clean package -pl lagi-web -am -DskipTests -U
```

当前打包结果为：

- `lagi-web/target/LinkMind.jar`
- `lagi-web/target/ROOT.war`

首次控制台核验与 API 调用请继续阅读[快速开始](docs/guide_zh.md)；详细部署方式见[部署、安全与级联组网](docs/deployment-and-security_zh.md)。

启动 LinkMind 后，可按功能查看[模型接入、路由与多模态](docs/models-and-multimodal_zh.md)、[知识库](docs/knowledge-base_zh.md)、[智能体接入、工具与任务编排](docs/agents-and-tools_zh.md)，在 `lagi.yml` 中启用相应运行配置。

按部署形态选择安装、容器化、反向代理或级联方式，请查看[部署、安全与级联组网](docs/deployment-and-security_zh.md)。

按业务目标比较团队 AI 枢纽、知识库、已有 Agent 前端、Medusa、安全网关、聚合中台、Embedding、跨区域级联和压测，见[九种业务场景的搭建与验收](docs/guide_zh.md#business-scenarios)。

## 接口风格

LinkMind 提供原生路由和协议兼容路由：

- LinkMind 原生路由，例如 `/chat/completions`、`/audio/speech2text`、`/audio/text2speech`、`/image/text2image`、`/sql/text2sql`、`/instruction/generate`、`/doc/doc2ext`、`/ocr/doc2ocr`
- OpenAI 兼容路由，包括 Chat Completions `/v1/chat/completions`、Responses `/v1/responses`，以及 `/v1/models`、`/v1/embeddings`、`/v1/images/generations`、`/v1/rerank`
- Anthropic Messages 兼容路由：`/v1/messages`

当前有一个仍按代码映射保留在 `/v1` 命名空间下的例外：向量管理接口 `/v1/vector/*`。模型与多模态接口见[模型接入、路由与多模态](docs/models-and-multimodal_zh.md)，向量、上传与检索接口见[知识库](docs/knowledge-base_zh.md)。

## 二次开发接口

LinkMind 将二次开发能力放在清晰的面向对象边界之后，便于在不改动主流程的前提下扩展业务能力。

- `通讯层与带外数据`：OpenAI 兼容请求支持 `extra_body`，运行时通过专门的工具类完成编码、解码和当前用户注入，不把业务解析逻辑塞进 Adapter 主链路，因此不会影响既有补全、流式输出和级联调用。
- `Skill 层社交数据`：社交交互数据封装在 Skill 与 Service 内部，并通过 `/socialChannel/*` 对外暴露。如果不启用社交 Skill，原有聊天流程和 Token 路径保持不变。
- 业务方可通过 `/apiKey/*` 管理 API Key 池，通过 `/credit/*` 接入计费流程。两组接口都支持在保留接口协议的前提下替换后端实现。

这种分层方式让二开具备两个直接优势：对现有代码零侵入，以及按需启用。你可以只接入自己需要的接口，而不必重写整套运行时。

## Agent 运行时集成

- **OpenClaw**：可以把 LinkMind 注入为 OpenAI 兼容 Provider，也可以把 OpenClaw 的模型选择反向同步回 `lagi.yml`
- **Hermes Agent**：可以通过 `~/.hermes/config.yaml` 和 `.env` 导入导出模型配置
- **DeerFlow**：可以通过 DeerFlow 的 `config.yaml` 和 `.env` 导入导出模型配置
- **OpenHuman**：可以把带 Bearer 鉴权的 LinkMind 写入 OpenHuman `config.toml` 的 `linkmind` provider，绑定 `provider:linkmind` 鉴权 profile，并在 OpenHuman 桌面端 keychain 文件可用时写入 LinkMind token；也可以把支持的 OpenAI 兼容 provider 反向导入 `lagi.yml`

首次评估可直接启动 LinkMind 跑通控制台与 API；需要时再接入现有 Agent 前端，不需要切换产品版本。

## 核心功能

### 1. RAG + Embedding

把私有文档、问答对和结构化知识转成可检索上下文，通过向量库、Embedding、OCR 与文档处理流水线支撑真实知识库问答。

### 2. Medusa

通过内置缓存加速层缩短重复请求的响应时间，让模型运行在真实业务流量下更稳、更快。

### 3. Airank（路由与编排）

利用 `best(...)`、`pass(...)` 等路由规则统一完成多模型选择、排序、故障切换与编排，而不是把厂商逻辑散落在各个业务系统里。

### 4. 安全与合规

安全围栏、策略检查、数据处理、访问控制、模型提供方治理与部署责任边界见[部署、安全与级联组网](docs/deployment-and-security_zh.md)。

### 5. Graph

在向量检索之外补充图谱类上下文增强和意图理解能力，适合更复杂的企业知识与推理场景。

### 6. Token Grid（级联网络）

Token Grid 把多个 LinkMind 节点连接成类似“国家电网”的智能网络。在这套网络里，多级架构中的各级 LinkMind 节点并非仅仅是中转站。它更像梯级水电站，每一层都可以挂载自己的“机模”，包括本地模型、厂商路由、工具、技能、私有知识库和策略控制。全网所有节点既是上游能力的消费者，也是向下游输出算力与领域能力的提供者，同时通过级联链路保障数据和权限的物理与逻辑隔离。

<a href="docs/images/token_grid_zh.png">
  <img src="docs/images/token_grid_zh.png" alt="Token Grid 级联网络概念图">
</a>

### 7. Agent 框架支持

将 LinkMind 作为各类 Agent 框架共享的模型、上下文与治理层接入，而不是在每个 Agent 应用中分别配置模型。运行时提供兼容 OpenAI Chat Completions / Responses 和 Anthropic Messages 的接口，采用这些协议的框架可通过统一 Provider 地址接入。

- **内置配置同步**：OpenClaw、Hermes Agent、DeerFlow、OpenHuman 可导入或导出已支持的模型与 Provider 配置。
- **框架无关接入**：其他 Agent 框架可通过兼容接口使用 `lagi.yml` 的模型路由、MCP、Skills、RAG 与策略控制，无需依赖专用插件。
- **统一执行入口**：Worker 将本地业务 Agent 和已集成的外部 Agent 能力注册、路由到同一个调用入口。

如需将这些能力通过 `lagi-core` 或 REST API 接入到业务系统，请继续阅读[系统集成与二次开发](docs/integration_zh.md)。

### 8. Worker 编排

通过 Worker 统一组织已注册 Agent 的调用，支持指定目标 Agent，或结合路由策略选择合适的执行者。可复用本地业务 Agent 和已接入的云端 Agent 平台能力，适用于多业务助手统一接入、领域问答和任务分发等场景。

在 `lagi.yml` 的 `skills.workers` 中配置 Worker 实现与路由，业务系统通过 `POST /chat/go` 发起调用。配置方式见[智能体接入、工具与任务编排](docs/agents-and-tools_zh.md)，自定义扩展见[更多功能接入与自定义扩展](docs/extensions_zh.md)。

## 模型与运行时生态

**模型与提供方**

<table>
  <tr>
    <td><img src="docs/images/logo/model/img_3.jpeg" width="18" alt="Azure OpenAI"> Azure OpenAI</td>
    <td><img src="docs/images/logo/model/img_9.jpeg" width="18" alt="Baichuan"> Baichuan</td>
    <td><img src="docs/images/logo/model/img_7.jpg" width="18" alt="ChatGLM"> ChatGLM</td>
    <td><img src="docs/images/logo/model/img_15.webp" width="18" alt="Claude"> Claude</td>
  </tr>
  <tr>
    <td><img src="docs/images/logo/model/img_14.jpeg" width="18" alt="DeepSeek"> DeepSeek</td>
    <td><img src="docs/images/logo/model/img_13.png" width="18" alt="Doubao"> Doubao</td>
    <td><img src="docs/images/logo/model/img_6.png" width="18" alt="ERNIE"> ERNIE</td>
    <td><img src="docs/images/logo/model/img_2.jpeg" width="18" alt="FastChat / Vicuna"> FastChat / Vicuna</td>
  </tr>
  <tr>
    <td><img src="docs/images/logo/model/img_12.webp" width="18" alt="Gemini"> Gemini</td>
    <td><img src="docs/images/logo/model/img_17.png" width="18" alt="Grok"> Grok</td>
    <td><img src="docs/images/logo/img_4.jpeg" width="18" alt="Hunyuan"> Hunyuan</td>
    <td><img src="docs/images/logo/model/img_10.jpeg" width="18" alt="iFLYTEK Spark"> iFLYTEK Spark</td>
  </tr>
  <tr>
    <td><img src="docs/images/logo/model/img_16.jpg" width="18" alt="MiniMax"> MiniMax</td>
    <td><img src="docs/images/logo/model/img_8.png" width="18" alt="Moonshot / Kimi"> Moonshot / Kimi</td>
    <td><img src="docs/images/logo/model/img_4.jpeg" width="18" alt="OpenAI"> OpenAI</td>
    <td><img src="docs/images/logo/model/img_18.webp" width="18" alt="OpenRouter"> OpenRouter</td>
  </tr>
  <tr>
    <td><img src="docs/images/logo/model/img_5.png" width="18" alt="Qwen"> Qwen</td>
    <td><img src="docs/images/logo/model/img_11.png" width="18" alt="SenseChat"> SenseChat</td>
    <td><img src="docs/images/logo/model/img_19.png" width="18" alt="StepFun"> StepFun</td>
    <td><img src="docs/images/logo/model/img_20.png" width="18" alt="Xiaomi"> Xiaomi</td>
  </tr>
</table>

**本地 Agent 框架**

<table>
  <tr>
    <td><img src="docs/images/logo/img_23.jpg" width="18" alt="DeerFlow"> DeerFlow</td>
    <td><img src="docs/images/logo/img_22.png" width="18" alt="Hermes Agent"> Hermes Agent</td>
    <td><img src="docs/images/logo/OpenHuman_logo.png" width="18" alt="Hermes Agent">OpenHuman</td>
    <td><img src="docs/images/logo/img_21.jpg" width="18" alt="OpenClaw"> OpenClaw</td>
  </tr>
</table>

**云端 Agent 平台**

<table>
  <tr>
    <td><img src="docs/images/logo/img_1.png" width="18" alt="Coze"> Coze</td>
    <td><img src="docs/images/logo/img_4.jpeg" width="18" alt="Hunyuan Agents"> Hunyuan Agents（混元智能体）</td>
    <td><img src="docs/images/logo/img_2.png" width="18" alt="Wenxin Agents"> Wenxin Agents（文心智能体）</td>
    <td><img src="docs/images/logo/img_3.png" width="18" alt="Zhipu Agents"> Zhipu Agents（智谱智能体）</td>
  </tr>
</table>

**Data & Retrieval（数据与检索）**

<table>
  <tr>
    <td><img src="docs/images/logo/img_4.png" width="18" alt="Chroma"> Chroma</td>
    <td><img src="docs/images/logo/img_5.png" width="18" alt="Elasticsearch"> Elasticsearch</td>
    <td><img src="docs/images/logo/img_26.png" width="18" alt="Milvus"> Milvus</td>
  </tr>
  <tr>
    <td><img src="docs/images/logo/img_6.png" width="18" alt="MySQL"> MySQL</td>
    <td><img src="docs/images/logo/img_27.png" width="18" alt="Pinecone"> Pinecone</td>
    <td><img src="docs/images/logo/img_28.png" width="18" alt="SQLite"> SQLite</td>
  </tr>
</table>

以上条目按类型分组，并按英文名称字母顺序排列。新增模型、向量库、适配器或二次开发接口的方式见[更多功能接入与自定义扩展](docs/extensions_zh.md)。Chroma 的具体安装与补充说明见[知识库](docs/knowledge-base_zh.md)。

## Agentic Social（智能体社交）

Agentic Social 为智能体提供结构化的上下文交换、用户意图传递、协作信号和社交交互数据通道，而不是把这些逻辑硬塞进主聊天链路。社交数据封装在 Skill 与 Service 内部，并通过 `/socialChannel/*` 对外暴露，团队可以按需启用智能体之间、用户与智能体之间的协作能力。

<table width="100%">
  <tr>
    <td valign="top" width="50%">
      <h2>License</h2>
      <p>本项目遵循 <a href="LICENSE">LICENSE</a>。</p>
    </td>
    <td valign="top" width="50%">
      <h2>演示参看</h2>
      <p>演示地址：<a href="https://linkmind.landingbj.com/">https://linkmind.landingbj.com/</a></p>
    </td>
  </tr>
</table>

## 详细文档导航

文档已按功能组织：每个功能页汇总所需配置、接口入口和验证步骤。

| 文档 | 内容 |
| --- | --- |
| [快速开始](docs/guide_zh.md) | 首次安装、控制台核验与第一条 API 调用 |
| [Web 控制台](docs/index_zh.md) | 页面入口、运行记录与常见检查项 |
| [模型接入、路由与多模态](docs/models-and-multimodal_zh.md) | Provider、路由、聊天、Embedding、音频、图片、视频与文档接口 |
| [知识库](docs/knowledge-base_zh.md) | 向量存储、RAG 配置、入库与检索验证 |
| [智能体接入、工具与任务编排](docs/agents-and-tools_zh.md) | Agent、MCP、Skill、Worker 与运行时客户端 |
| [系统集成与二次开发](docs/integration_zh.md) | REST、Java `lagi-core`、兼容协议与扩展边界 |
| [部署、安全与级联组网](docs/deployment-and-security_zh.md) | 安装器、JAR、Docker、WAR、加固与级联配置 |
| [更多功能接入与自定义扩展](docs/extensions_zh.md) | 兼容 Provider、适配器、存储与运行时扩展 |

[九种场景的选型与上线检查](docs/guide_zh.md#business-scenarios) · [并发压测脚本与结果解读](tools/benchmark/README.md)

### 最佳实践

| 分类 | 实操 |
| --- | --- |
| 第三方智能体客户端 | [OpenClaw](docs/best-practices/agents/openclaw.md#zh-cn)、[Hermes Agent](docs/best-practices/agents/hermes-agent.md#zh-cn)、[DeerFlow](docs/best-practices/agents/deerflow.md#zh-cn)、[OpenHuman](docs/best-practices/agents/openhuman.md#zh-cn) |
| 第三方命令行工具 | [Codex](docs/best-practices/cli/codex.md#zh-cn)、[Claude Code](docs/best-practices/cli/claude-code.md#zh-cn)、[客户端兼容性](docs/best-practices/cli/client-compatibility.md#zh-cn) |
| 第三方服务与开发环境 | [Chroma 知识库](docs/best-practices/integrations/chroma-knowledge-base.md#zh-cn)、[兼容模型 Provider](docs/best-practices/integrations/compatible-model-provider.md)、[MCP 服务](docs/best-practices/integrations/connect-mcp-server.md)、[Java IDE](docs/best-practices/integrations/java-ide.md#zh-cn)、[Tomcat WAR](docs/best-practices/integrations/deploy-war-to-tomcat.md#zh-cn) |

### Agent 项目延伸阅读（英文 PDF）

1. [从模型调用到可靠的单 Agent](docs/best-practices/01-reliable-single-agent.pdf)
2. [无需重新训练的私有知识接入](docs/best-practices/02-private-knowledge-and-rag.pdf)
3. [通过 Skills、Tools 与 MCP 接入私有能力](docs/best-practices/03-skills-tools-and-mcp.pdf)
4. [安全围栏、权限与审计](docs/best-practices/04-agent-security-and-audit.pdf)
5. [路由、编排与多 Agent 协作](docs/best-practices/05-routing-and-multi-agent-orchestration.pdf)
6. [共享人类上下文中的智能体社交](docs/best-practices/06-agentic-social.pdf)
