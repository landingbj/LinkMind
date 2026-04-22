简体中文 | [English](README.md)

# LinkMind

LinkMind 是面向企业场景的多模态 AI 中间件，用来把业务系统、私有知识、模型厂商和 Agent 运行时统一接到一层可治理、可扩展、可上线的能力层里。它优先解决的是企业真正落地时最常见的几个问题：上手慢、接入碎、成本高、稳定性差。

## 项目简介

当前代码已经覆盖统一聊天入口、RAG、OCR、ASR/TTS、图片与视频能力、文档处理、Text-to-SQL、Embedding、Rerank、MCP、Skills、Worker 编排，以及 OpenAI 兼容接口。同时，项目还内置了 OpenClaw、Hermes Agent、DeerFlow 的配置同步能力，便于接入现有 Agent 工作流。

### 模型与运行时生态

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


以上条目按类型分组，并按英文名称字母顺序排列。新增模型、向量库或适配器的方式见[扩展开发文档](docs/extend_zh.md)。Chroma 的具体安装与补充说明见[附件](docs/annex_zh.md)。

## 为什么选 LinkMind

- 一层中间件同时覆盖聊天、OCR、ASR/TTS、图片生成、图像与视频理解、Text-to-SQL、Embedding、Rerank、文档处理等能力。

- 多模型路由与故障切换统一配置在 `lagi.yml`（所有配置项详见[配置参考](docs/config_zh.md)），业务侧不用为不同厂商重复改接口。

- RAG 可以直接接到 Chroma、Elasticsearch、Milvus、MySQL、Pinecone、SQLite 等数据与检索组件，并继续扩展图谱类增强能力。

- Medusa 缓存、Token 统计、过滤器和运行时治理，都是面向真实生产环境的成本与稳定性问题设计的。

- OpenClaw、Hermes Agent、DeerFlow 的配置同步能力已经在当前代码里就位，适合逐步接入现有 Agent 工作流。

  <a href="docs/images/img_24.png">
    <img src="docs/images/img_24.png" alt="LinkMind 运行时集成示意图">
  </a>

## 几分钟上手

下面 4 种方式是并列选项，任选其一即可。

### 选项1：官方安装脚本快速安装

前置要求：安装 **JDK 8 或以上版本**。

- Windows PowerShell

  ```powershell
  iwr -useb https://ai.linkmind.top/install.ps1 | iex
  ```

- macOS / Linux

  ```bash
  curl -fsSL https://ai.linkmind.top/install.sh | bash
  ```

安装器支持两种运行模式：

| 模式 | 适用场景 |
| --- | --- |
| `Agent Mate` | 本机已经在使用 OpenClaw、Hermes Agent、DeerFlow，希望 LinkMind 作为统一中间层接入 |
| `Agent Server` | 先单独启动 LinkMind，直接体验控制台和 API，或做独立部署评估 |

### 选项2：下载并运行 JAR 包

预打包资源：

- 应用文件：`LinkMind.jar`，[点击这里下载](https://ai.linkmind.top/installer/LinkMind.jar)
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

更完整的安装说明见 [安装指南](docs/install_zh.md)。如需跟着示例一步步跑通，请参考[教学演示](docs/tutor_zh.md)。

## 接口风格

LinkMind 当前同时暴露两套路由风格：

- 已支持无额外版本前缀的 LinkMind 原生路由，例如 `/chat/completions`、`/audio/speech2text`、`/audio/text2speech`、`/image/text2image`、`/sql/text2sql`、`/instruction/generate`、`/doc/doc2ext`、`/ocr/doc2ocr`
- 需要保留标准前缀的 OpenAI 兼容路由，例如 `/v1/chat/completions`、`/v1/models`、`/v1/embeddings`、`/v1/images/generations`、`/v1/rerank`

当前有一个仍按代码映射保留在 `/v1` 命名空间下的例外：向量管理接口 `/v1/vector/*`。完整的接口文档见 [API 参考](docs/API_zh.md)。

## Agent 运行时集成

- **OpenClaw**：可以把 LinkMind 注入为 OpenAI 兼容 Provider，也可以把 OpenClaw 的模型选择反向同步回 `lagi.yml`
- **Hermes Agent**：可以通过 `~/.hermes/config.yaml` 和 `.env` 导入导出模型配置
- **DeerFlow**：可以通过 DeerFlow 的 `config.yaml` 和 `.env` 导入导出模型配置

如果你只是首次评估，建议先用 `Agent Server` 跑通控制台与 API；确认稳定后，再切到 `Agent Mate` 接入现有 Agent 运行时。

## 核心能力

- 基于 `best(...)` 与 `pass(...)` 的统一聊天路由
- OpenAI 兼容聊天与 Embedding 接口
- 面向知识库的 RAG、文档抽取、OCR 和向量更新
- ASR、TTS、文生图、图像 OCR、看图、图生视频、视频追踪、视频增强等多模态能力
- Text-to-SQL、SQL-to-Text、指令集生成、MCP 接入与 Worker 编排
- 敏感词、优先级词、停止词、续聊词等过滤器
- Skills 运行时、MCP 服务配置与 Token 使用观测

如需将上述能力通过 `lagi-core` 或 REST API 集成到业务系统，请参考[开发集成指南](docs/guide_zh.md)。

<table>
  <tr>
    <td valign="top" width="50%">
      <h2>License</h2>
      <p>本项目遵循 <a href="LICENSE">LICENSE</a>。</p>
    </td>
    <td valign="top" width="50%">
      <h2>演示参看</h2>
      <ul>
        <li>公网体验地址：<a href="https://linkmind.landingbj.com/">https://linkmind.landingbj.com/</a></li>
        <li>本地启动后的控制台地址：<code>http://localhost:8080</code></li>
      </ul>
    </td>
  </tr>
</table>
