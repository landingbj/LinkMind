[简体中文](README_zh.md) | English

# LinkMind

LinkMind is enterprise-grade multimodal AI middleware for teams that need one stable layer between business systems, private knowledge, model providers, and agent runtimes. It focuses on fast onboarding, low-friction integration, routing and failover, RAG, multimodal APIs, and production governance.

## Online Demo

- Public demo: [https://lagi.landingbj.com](https://lagi.landingbj.com/)
- Local console after startup: `http://localhost:8080`

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

- One middleware layer for chat, OCR, ASR/TTS, image generation, image and video understanding, text-to-SQL, embeddings, rerank, and document pipelines.

- Multi-model routing and failover are configured centrally in `lagi.yml` (see the [Configuration Reference](docs/config_en.md) for all options), so business systems do not need provider-specific rewrites.

- RAG support is built around document ingestion plus Chroma, Elasticsearch, Milvus, MySQL, Pinecone, and SQLite-backed retrieval paths, with room for graph-style augmentation.

- Medusa caching, token statistics endpoints, filters, and runtime governance target real production cost and stability concerns.

- Agent-runtime sync is built in for OpenClaw, Hermes Agent, and DeerFlow, which makes local AI workspace integration easier.

  <img src="docs/images/img_25.png">

## Get Started In Minutes

The following four methods are parallel options; you can choose any one of them.

### Option 1. Official Installer

Prerequisite: install **JDK 8 or later**.

- Windows PowerShell

  ```powershell
  iwr -useb https://downloads.landingbj.com/install.ps1 | iex
  ```

- macOS / Linux

  ```bash
  curl -fsSL https://downloads.landingbj.com/install.sh | bash
  ```

The installer supports two runtime choices:

| Mode | Use when |
| --- | --- |
| `Agent Mate` | You already use OpenClaw, Hermes Agent, or DeerFlow locally and want LinkMind to work as the shared middleware layer |
| `Agent Server` | You want a standalone LinkMind service first, or you are evaluating the web console and API directly |

### Option 2. Download Packaged Jar

Packaged downloads:

- Application package: `LinkMind.jar` ([Download](https://downloads.landingbj.com/lagi/installer/LinkMind.jar))
- Core library: `lagi-core-1.2.0-jar-with-dependencies.jar` ([Download](https://downloads.landingbj.com/lagi/lib/lagi-core-1.2.0-jar-with-dependencies.jar))

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

More setup details are in the [Installation Guide](docs/install_en.md). For a guided first-run walkthrough, try the [Tutorial](docs/tutor_en.md).

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

## Core Capabilities

- Unified chat routing with `best(...)` and `pass(...)` router rules
- OpenAI-compatible chat and embedding endpoints
- RAG with vector search, document extraction, OCR, and knowledge-base updates
- Multimodal APIs for ASR, TTS, image generation, image OCR, image understanding, image-to-video, video tracking, and video enhancement
- Text-to-SQL, SQL-to-text, instruction generation, MCP access, and worker orchestration
- Filters for sensitive content, priority words, stopping words, and conversation continuation
- Skills runtime, MCP server configuration, and token usage observability

To integrate these capabilities into your application via `lagi-core` or REST APIs, see the [Integration Guide](docs/guide_en.md).

## Build And Packaging Notes

- Root project version: `1.2.3`
- Modules: `lagi-web`, `lagi-core`, `lagi-extension`
- Default runtime entry point: `ai.starter.Application`

## License

This project is distributed under the [LICENSE](LICENSE).
