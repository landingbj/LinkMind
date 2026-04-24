[简体中文](README_zh.md) | English

# LinkMind

LinkMind is enterprise-grade multimodal AI middleware for teams that need one stable layer between business systems, private knowledge, model providers, and agent runtimes. It focuses on fast onboarding, low-friction integration, routing and failover, RAG, multimodal APIs, and production governance.

## Introduction

The current codebase exposes a single middleware layer for chat, RAG, OCR, ASR/TTS, image and video workflows, text-to-SQL, embeddings, rerank, MCP access, skills, worker orchestration, and OpenAI-compatible APIs. It also includes runtime sync hooks for OpenClaw, Hermes Agent, and DeerFlow.

### Model And Runtime Ecosystem

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

Entries are grouped by type and listed alphabetically. These ecosystems are extensible; the [Extension Guide](docs/extend_en.md) covers writing new model, vector-store, and adapter integrations. For Chroma setup specifics, see the [Annex](docs/annex_en.md).

## Why LinkMind

- One middleware layer covers chat, OCR, ASR/TTS, image generation, image and video understanding, text-to-SQL, embeddings, rerank, and document pipelines.
- Multi-model routing, failover, and orchestration are configured centrally in `lagi.yml`, so business systems do not need provider-specific rewrites.
- RAG can connect directly to Chroma, Elasticsearch, Milvus, MySQL, Pinecone, SQLite, and graph-style augmentation paths.
- Medusa cache acceleration, token statistics, filters, and runtime governance are built for real production stability and cost control.
- OpenClaw, Hermes Agent, and DeerFlow integration hooks make it easier to join existing agent workflows instead of rebuilding them.

  <a href="docs/images/img_25.png">
    <img src="docs/images/img_25.png" alt="LinkMind runtime integration overview">
  </a>

## Get Started In Minutes

The following four methods are parallel options. Choose any one of them.

### Option 1. Official Installer

Prerequisite: install **JDK 8 or later** first. If you still need Java setup steps, jump to the [Installation Guide](docs/install_en.md#appendix-install-jdk-8).

- Windows PowerShell

  ```powershell
  iwr -useb https://cdn.linkmind.top/install.ps1 | iex
  ```

- macOS / Linux

  ```bash
  curl -fsSL https://cdn.linkmind.top/install.sh | bash
  ```

The installer supports two runtime choices:

| Mode | Use when |
| --- | --- |
| `Agent Mate` | You already use OpenClaw, Hermes Agent, or DeerFlow locally and want LinkMind to work as the shared middleware layer |
| <code>Agent&nbsp;Server</code> | You want a standalone LinkMind service first, or you are evaluating the web console and API directly |

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

More setup details are in the [Installation Guide](docs/install_en.md). For a guided first-run walkthrough, use the [Tutorial](docs/tutor_en.md).

After LinkMind is running, use the [Configuration Guide](docs/config_en.md) to enable providers, routes, filters, RAG, and other runtime settings in `lagi.yml`.

## API Surface

LinkMind exposes two route styles:

- Native LinkMind routes without extra version prefixes where the server already supports them, such as `/chat/completions`, `/audio/speech2text`, `/audio/text2speech`, `/image/text2image`, `/sql/text2sql`, `/instruction/generate`, `/doc/doc2ext`, and `/ocr/doc2ocr`
- OpenAI-compatible routes that intentionally keep the standard prefix, such as `/v1/chat/completions`, `/v1/models`, `/v1/embeddings`, `/v1/images/generations`, and `/v1/rerank`

One current exception is the vector administration namespace, which is still mapped in code as `/v1/vector/*`. Full endpoint documentation is in the [API Reference](docs/API_en.md).

## Agent Runtime Integration

- **OpenClaw**: LinkMind can inject itself as an OpenAI-compatible provider and can also load model selections back from OpenClaw into `lagi.yml`.
- **Hermes Agent**: LinkMind can import and export model settings through `~/.hermes/config.yaml` and `.env`.
- **DeerFlow**: LinkMind can import and export model settings through DeerFlow `config.yaml` and `.env`.

If you want the shortest evaluation path, start with `Agent Server`, verify the web console and API, then switch to `Agent Mate` when you are ready to connect LinkMind to your existing agent runtime stack.

## Core Features

### 1. RAG + Embedding

Turn private files, QA pairs, and structured knowledge into retrievable context through vector stores, embeddings, OCR, and document ingestion pipelines.

### 2. Medusa

Use built-in cache acceleration to shorten repeat response latency and improve runtime efficiency in real production traffic.

### 3. Airank

Route, rank, fail over, and orchestrate multiple models centrally through routers such as `best(...)` and `pass(...)` instead of hard-coding provider logic in each app.

### 4. Security Guardrails

Apply sensitive-word filtering, priority keywords, stopping keywords, continuation rules, and related runtime controls to keep output safer and conversations governable.

### 5. Graph

Augment retrieval and intent understanding with graph-style context so the middleware can make more stable decisions for complex enterprise knowledge scenarios.

### 6. Cascade Networking

Compose many LinkMind nodes into a larger router-like tree network. Each Agent Server manages its own concurrency and local data, while cascade links stitch distributed agents together and preserve physical and logical isolation for data and permissions.

### 7. OpenClaw Plugin

Connect LinkMind to the OpenClaw ecosystem as a plugin-friendly, OpenAI-compatible context and provider layer instead of wiring each model separately.

To integrate these capabilities into your application via `lagi-core` or REST APIs, see the [Integration Guide](docs/guide_en.md).

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
