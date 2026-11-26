[简体中文](README_zh.md) | English

# <img src="docs/images/logo/LinkMind%20logo%281%29.png" alt="LinkMind logo" width="42"> LinkMind

LinkMind is enterprise-grade multimodal AI middleware for teams that need one stable layer between business systems, private knowledge, model providers, and agent runtimes. It focuses on fast onboarding, low-friction integration, routing and failover, RAG, multimodal APIs, and production governance.

## Introduction

The current codebase exposes a single middleware layer for chat, RAG, OCR, ASR/TTS, image and video workflows, text-to-SQL, embeddings, rerank, MCP access, skills, worker orchestration, and APIs compatible with OpenAI Chat Completions / Responses and Anthropic Messages. It also includes runtime sync hooks for OpenClaw, Hermes Agent, DeerFlow, and OpenHuman.



## Why LinkMind

- One middleware layer covers chat, OCR, ASR/TTS, image generation, image and video understanding, text-to-SQL, embeddings, rerank, and document pipelines.
- Multi-model routing, failover, and orchestration are configured centrally in `lagi.yml`, so business systems do not need provider-specific rewrites.
- RAG can connect directly to Chroma, Elasticsearch, Milvus, MySQL, Pinecone, SQLite, and graph-style augmentation paths.
- Medusa cache acceleration, token statistics, filters, and runtime governance are built for real production stability and cost control.
- OpenClaw, Hermes Agent, DeerFlow, and OpenHuman integration hooks make it easier to join existing agent workflows instead of rebuilding them.

  <a href="docs/images/img_25.png">
    <img src="docs/images/img_25.png" alt="LinkMind runtime integration overview">
  </a>

## Get Started In Minutes

The following four methods are parallel options. Choose any one of them.

### Option 1. Official Installer

Install **JDK 8 or later**. Follow the [JDK setup and verification steps for your OS](docs/deployment-and-security_en.md#install-jdk).

- Windows PowerShell

  ```powershell
  iwr -useb https://cdn.linkmind.top/install.ps1 | iex
  ```

- macOS / Linux

  ```bash
  curl -fsSL https://cdn.linkmind.top/install.sh | bash
  ```

LinkMind uses one unified runtime: deploy it as a team AI hub on an appliance, server, personal computer, or Docker. When OpenClaw, Hermes Agent, DeerFlow, OpenHuman, or another supported frontend already exists locally, connect it as an optional integration while retaining configuration synchronization.

### Option 2. Download Packaged Jar

Packaged downloads:

- Application package: `LinkMind.jar` ([Download](https://cdn.linkmind.top/installer/LinkMind.jar))
- Core library: `lagi-core-1.2.0-jar-with-dependencies.jar` ([Download](https://ai.linkmind.top/lagi/lib/lagi-core-1.2.0-jar-with-dependencies.jar))

```powershell
java -jar LinkMind.jar
```

On first run, LinkMind creates `config/`, `data/`, and the default `lagi.yml`. Then open `http://localhost:8080`.

### Option 3. With Docker Image

Prebuilt image: `landingbj/linkmind`

```bash
docker pull landingbj/linkmind
docker run -d -p 8080:8080 landingbj/linkmind
```

Then open `http://localhost:8080`.

### Option 4. Build from Source

```bash
mvn clean package -pl lagi-web -am -DskipTests -U
```

Current packaging generates:

- `lagi-web/target/LinkMind.jar`
- `lagi-web/target/ROOT.war`

Continue with [Getting Started](docs/guide_en.md) for the first console check and API call. Detailed deployment options are in [Deployment, Security, and Cascade Networking](docs/deployment-and-security_en.md).

After LinkMind is running, use [Models, Routing, and Multimodal](docs/models-and-multimodal_en.md), [Knowledge Base](docs/knowledge-base_en.md), and [Agents, Tools, and Task Orchestration](docs/agents-and-tools_en.md) to enable runtime settings in `lagi.yml` by capability.

For deployment shape, containerization, reverse proxy, or cascade networking, see [Deployment, Security, and Cascade Networking](docs/deployment-and-security_en.md).

For a team AI hub, knowledge base, existing agent frontend, Medusa, security gateway, aggregation platform, embeddings, regional cascade, or benchmark, see [setup and acceptance checks for nine scenarios](docs/guide_en.md#business-scenarios).

## API Surface

LinkMind exposes native routes and protocol-compatible routes:

- Native LinkMind routes without extra version prefixes where the server already supports them, such as `/chat/completions`, `/audio/speech2text`, `/audio/text2speech`, `/image/text2image`, `/sql/text2sql`, `/instruction/generate`, `/doc/doc2ext`, and `/ocr/doc2ocr`
- OpenAI-compatible routes, including Chat Completions at `/v1/chat/completions`, Responses at `/v1/responses`, plus `/v1/models`, `/v1/embeddings`, `/v1/images/generations`, and `/v1/rerank`
- Anthropic Messages-compatible route: `/v1/messages`

One current exception is the vector administration namespace, which is still mapped in code as `/v1/vector/*`. Model and multimodal APIs are documented in [Models, Routing, and Multimodal](docs/models-and-multimodal_en.md); vector, upload, and retrieval APIs are in [Knowledge Base](docs/knowledge-base_en.md).

## Customization API

LinkMind keeps secondary-development entry points behind object-oriented boundaries so teams can extend behavior without rewriting the main request flow.

- `Communication and out-of-band data`: OpenAI-compatible chat requests accept `extra_body`, and runtime helpers handle encoding, decoding, and current-user injection without pushing business parsing into the adapter itself. Existing completion, streaming, and cascade flows remain unchanged.
- `Skill-level social data`: social interaction data is encapsulated in skills and service classes, then exposed through `/socialChannel/*`. If you do not enable social skills, the original chat and token flow stays intact.
- Use `/apiKey/*` to manage API key pools and `/credit/*` to integrate billing. You can replace the backing service while preserving the HTTP contract.

This split gives two practical benefits for secondary development: zero-invasive integration with the current codebase, and opt-in adoption so you only enable the interfaces your deployment actually needs.

## Agent Runtime Integration

- **OpenClaw**: LinkMind can inject itself as an OpenAI-compatible provider and can also load model selections back from OpenClaw into `lagi.yml`.
- **Hermes Agent**: LinkMind can import and export model settings through `~/.hermes/config.yaml` and `.env`.
- **DeerFlow**: LinkMind can import and export model settings through DeerFlow `config.yaml` and `.env`.
- **OpenHuman**: LinkMind can add a local Bearer-auth `linkmind` provider to OpenHuman `config.toml`, bind the `provider:linkmind` auth profile, store the token in OpenHuman's desktop keychain file when available, and import supported OpenAI-compatible providers back into `lagi.yml`.

For the shortest evaluation path, start LinkMind directly and verify the web console and API. Connect an existing Agent frontend when needed; no product-version switch is required.

## Core Features

### 1. RAG + Embedding

Turn private files, QA pairs, and structured knowledge into retrievable context through vector stores, embeddings, OCR, and document ingestion pipelines.

### 2. Medusa

Use built-in cache acceleration to shorten repeat response latency and improve runtime efficiency in real production traffic.

### 3. Airank

Route, rank, fail over, and orchestrate multiple models centrally through routers such as `best(...)` and `pass(...)` instead of hard-coding provider logic in each app.

### 4. Security & Safety

Guardrails, policy checks, data handling, access control, provider governance, and deployment responsibilities are documented in [Deployment, Security, and Cascade Networking](docs/deployment-and-security_en.md).

### 5. Graph

Augment retrieval and intent understanding with graph-style context so the middleware can make more stable decisions for complex enterprise knowledge scenarios.

### 6. Token Grid (Cascade Network)

Token Grid connects many LinkMind nodes into a national-power-grid-like intelligence network. In this topology, LinkMind nodes in a multi-level architecture are not passive relay stations. Like cascade hydropower stations, every layer can mount its own "machine-model" capacity, including local models, provider routes, tools, skills, private knowledge, and policy controls. Every node in the grid is both a consumer of upstream capabilities and a provider of compute and domain capability to downstream nodes, while cascade links preserve physical and logical isolation for data and permissions.

<a href="docs/images/token_grid_en.png">
  <img src="docs/images/token_grid_en.png" alt="Token Grid cascade network concept">
</a>

### 7. Agent Framework Support

Use LinkMind as the shared model, context, and governance layer for agent frameworks rather than configuring each model separately in every agent application. The runtime exposes OpenAI Chat Completions / Responses and Anthropic Messages-compatible APIs, so frameworks that use these protocols can connect through a common provider endpoint.

- **Built-in configuration synchronization**: OpenClaw, Hermes Agent, DeerFlow, and OpenHuman can import or export supported model and provider settings.
- **Framework-neutral integration**: Other agent frameworks can use the compatible APIs, `lagi.yml` model routing, MCP, Skills, RAG, and policy controls without requiring a framework-specific plugin.
- **Unified execution**: Workers register and route local business agents and integrated external agent capabilities through one invocation surface.

To integrate these capabilities into your application via `lagi-core` or REST APIs, see [System Integration and Secondary Development](docs/integration_en.md).

### 8. Worker Orchestration

Workers provide a unified way to invoke registered agents, supporting explicit agent selection or routing policies that choose a suitable executor. Reuse local business agents and integrated cloud agent platform capabilities for specialized assistants, domain question answering, and task dispatch.

Configure worker implementations and routes under `skills.workers` in `lagi.yml`, then invoke them through `POST /chat/go`. See [Agents, Tools, and Task Orchestration](docs/agents-and-tools_en.md) for setup and [More Integrations and Custom Extensions](docs/extensions_en.md) for custom implementations.


## Model And Runtime Ecosystem

**Models & Providers**

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

**Local Agent Frameworks**

<table>
  <tr>
    <td><img src="docs/images/logo/img_23.jpg" width="18" alt="DeerFlow"> DeerFlow</td>
    <td><img src="docs/images/logo/img_22.png" width="18" alt="Hermes Agent"> Hermes Agent</td>
    <td><img src="docs/images/logo/OpenHuman_logo.png" width="18" alt="Hermes Agent">OpenHuman</td>
    <td><img src="docs/images/logo/img_21.jpg" width="18" alt="OpenClaw"> OpenClaw</td>
  </tr>
</table>

**Cloud Agent Platforms**

<table>
  <tr>
    <td><img src="docs/images/logo/img_1.png" width="18" alt="Coze"> Coze</td>
    <td><img src="docs/images/logo/img_4.jpeg" width="18" alt="Hunyuan Agents"> Hunyuan Agents</td>
    <td><img src="docs/images/logo/img_2.png" width="18" alt="Wenxin Agents"> Wenxin Agents</td>
    <td><img src="docs/images/logo/img_3.png" width="18" alt="Zhipu Agents"> Zhipu Agents</td>
  </tr>
</table>

**Data & Retrieval**

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

Entries are grouped by type and listed alphabetically. These ecosystems are extensible; [More Integrations and Custom Extensions](docs/extensions_en.md) covers new model, vector-store, adapter, and secondary-development integrations. For Chroma setup specifics, see [Knowledge Base](docs/knowledge-base_en.md).


## Agentic Social

Agentic Social gives agents a structured way to exchange context, user intent, collaboration signals, and social interaction data without forcing that logic into the main chat pipeline. Social data is encapsulated in skills and service classes, then exposed through `/socialChannel/*`, so teams can add agent-to-agent and user-to-agent collaboration features only when their deployment needs them.

<table width="100%">
  <tr>
    <td valign="top" width="50%">
      <h2>License</h2>
      <p>This project is distributed under the <a href="LICENSE">LICENSE</a>.</p>
    </td>
    <td valign="top" width="50%">
      <h2>Online Demo</h2>
        Public&nbsp;demo:&nbsp;<a href="https://linkmind.landingbj.com/">https://linkmind.landingbj.com/</a>
    </td>
  </tr>
</table>

## Documentation

The documentation is organized by capability. Each feature page brings together the required configuration, API entry points, and verification steps.

| Document | Contents |
| --- | --- |
| [Getting Started](docs/guide_en.md) | First installation, console check, and first API call |
| [LinkMind Console](docs/index_en.md) | Console entries, usage records, and troubleshooting checks |
| [Models, Routing, and Multimodal](docs/models-and-multimodal_en.md) | Providers, routes, chat, embeddings, audio, image, video, and document APIs |
| [Knowledge Base](docs/knowledge-base_en.md) | Vector storage, RAG configuration, ingestion, and retrieval verification |
| [Agents, Tools, and Task Orchestration](docs/agents-and-tools_en.md) | Agents, MCP, Skills, Workers, and runtime clients |
| [System Integration and Secondary Development](docs/integration_en.md) | REST, Java `lagi-core`, compatible protocols, and extension boundaries |
| [Deployment, Security, and Cascade Networking](docs/deployment-and-security_en.md) | Installer, JAR, Docker, WAR, hardening, and cascade configuration |
| [More Integrations and Custom Extensions](docs/extensions_en.md) | Compatible providers, adapters, stores, and runtime extensions |

[Nine business scenarios and launch checks](docs/guide_en.md#business-scenarios) · [Load-test scripts and result interpretation](tools/benchmark/README.md)

### Best Practices

| Topic | Recipe |
| --- | --- |
| Agent clients | [OpenClaw](docs/best-practices/agents/openclaw.md#english), [Hermes Agent](docs/best-practices/agents/hermes-agent.md#english), [DeerFlow](docs/best-practices/agents/deerflow.md#english), [OpenHuman](docs/best-practices/agents/openhuman.md#english) |
| CLI clients | [Codex](docs/best-practices/cli/codex.md#english), [Claude Code](docs/best-practices/cli/claude-code.md#english), [Client compatibility](docs/best-practices/cli/client-compatibility.md#english) |
| Service and development integrations | [Chroma knowledge base](docs/best-practices/integrations/chroma-knowledge-base.md#english), [Compatible model provider](docs/best-practices/integrations/compatible-model-provider.md), [MCP server](docs/best-practices/integrations/connect-mcp-server.md), [Java IDE](docs/best-practices/integrations/java-ide.md#english), [Tomcat WAR](docs/best-practices/integrations/deploy-war-to-tomcat.md#english) |

### AI Agent Project Reading (English PDFs)

1. [From Model Call to Reliable Single Agent](docs/best-practices/01-reliable-single-agent.pdf)
2. [Injecting Private Knowledge without Retraining](docs/best-practices/02-private-knowledge-and-rag.pdf)
3. [Injecting Private Capabilities with Skills, Tools, and MCP](docs/best-practices/03-skills-tools-and-mcp.pdf)
4. [Safety by Design: Guardrails, Permissions, and Audit](docs/best-practices/04-agent-security-and-audit.pdf)
5. [Routing, Orchestration, and Multi-Agent Teamwork](docs/best-practices/05-routing-and-multi-agent-orchestration.pdf)
6. [Agentic Social: Agents in Shared Human Contexts](docs/best-practices/06-agentic-social.pdf)
