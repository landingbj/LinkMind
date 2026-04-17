[简体中文](README_zh.md) | English

# LinkMind

LinkMind is enterprise-grade multimodal AI middleware for teams that need one stable layer between business systems, private knowledge, model providers, and agent runtimes. It focuses on fast onboarding, low-friction integration, routing and failover, RAG, multimodal APIs, and production governance.

## Online Demo

- Public demo: [https://lagi.landingbj.com](https://lagi.landingbj.com/)
- Local console after startup: `http://localhost:8080`

## Introduction

The current codebase exposes a single middleware layer for chat, RAG, OCR, ASR/TTS, image and video workflows, text-to-SQL, embeddings, rerank, MCP access, skills, worker orchestration, and OpenAI-compatible APIs. It also includes runtime sync hooks for OpenClaw, Hermes Agent, and DeerFlow.

### Model And Runtime Ecosystem

<div style="display:flex;flex-wrap:wrap;gap:8px 10px;margin:12px 0 20px;">
  <span style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #d0d7de;border-radius:999px;"><img src="docs/images/logo/model/img_1.png" width="18" height="18" alt="Landing"><span>Landing</span></span>
  <span style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #d0d7de;border-radius:999px;"><img src="docs/images/logo/model/img_2.jpeg" width="18" height="18" alt="FastChat / Vicuna"><span>FastChat / Vicuna</span></span>
  <span style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #d0d7de;border-radius:999px;"><img src="docs/images/logo/model/img_4.jpeg" width="18" height="18" alt="OpenAI"><span>OpenAI</span></span>
  <span style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #d0d7de;border-radius:999px;"><img src="docs/images/logo/model/img_3.jpeg" width="18" height="18" alt="Azure OpenAI"><span>Azure OpenAI</span></span>
  <span style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #d0d7de;border-radius:999px;"><img src="docs/images/logo/model/img_12.webp" width="18" height="18" alt="Gemini"><span>Gemini</span></span>
  <span style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #d0d7de;border-radius:999px;"><img src="docs/images/logo/model/img_5.png" width="18" height="18" alt="Qwen"><span>Qwen</span></span>
  <span style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #d0d7de;border-radius:999px;"><img src="docs/images/logo/model/img_6.png" width="18" height="18" alt="ERNIE"><span>ERNIE</span></span>
  <span style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #d0d7de;border-radius:999px;"><img src="docs/images/logo/model/img_7.jpg" width="18" height="18" alt="ChatGLM"><span>ChatGLM</span></span>
  <span style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #d0d7de;border-radius:999px;"><img src="docs/images/logo/model/img_8.png" width="18" height="18" alt="Moonshot / Kimi"><span>Moonshot / Kimi</span></span>
  <span style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #d0d7de;border-radius:999px;"><img src="docs/images/logo/model/img_9.jpeg" width="18" height="18" alt="Baichuan"><span>Baichuan</span></span>
  <span style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #d0d7de;border-radius:999px;"><img src="docs/images/logo/model/img_10.jpeg" width="18" height="18" alt="iFLYTEK Spark"><span>iFLYTEK Spark</span></span>
  <span style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #d0d7de;border-radius:999px;"><img src="docs/images/logo/model/img_11.png" width="18" height="18" alt="SenseChat"><span>SenseChat</span></span>
  <span style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #d0d7de;border-radius:999px;"><img src="docs/images/logo/model/img_13.png" width="18" height="18" alt="Doubao"><span>Doubao</span></span>
  <span style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #d0d7de;border-radius:999px;"><img src="docs/images/logo/model/img_14.jpeg" width="18" height="18" alt="DeepSeek"><span>DeepSeek</span></span>
  <span style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #d0d7de;border-radius:999px;"><img src="docs/images/logo/model/img_15.webp" width="18" height="18" alt="Claude"><span>Claude</span></span>
  <span style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #d0d7de;border-radius:999px;"><img src="docs/images/logo/model/img_16.jpg" width="18" height="18" alt="MiniMax"><span>MiniMax</span></span>
  <span style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #d0d7de;border-radius:999px;"><img src="docs/images/logo/img_4.jpeg" width="18" height="18" alt="Hunyuan"><span>Hunyuan</span></span>
  <span style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #d0d7de;border-radius:999px;"><span style="display:inline-flex;align-items:center;justify-content:center;width:18px;height:18px;border-radius:6px;background:#111827;color:#fff;font-size:10px;font-weight:700;">X</span><span>Grok</span></span>
  <span style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #d0d7de;border-radius:999px;"><span style="display:inline-flex;align-items:center;justify-content:center;width:18px;height:18px;border-radius:6px;background:#0f766e;color:#fff;font-size:10px;font-weight:700;">OR</span><span>OpenRouter</span></span>
  <span style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #d0d7de;border-radius:999px;"><span style="display:inline-flex;align-items:center;justify-content:center;width:18px;height:18px;border-radius:6px;background:#7c3aed;color:#fff;font-size:10px;font-weight:700;">SF</span><span>StepFun</span></span>
  <span style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #d0d7de;border-radius:999px;"><span style="display:inline-flex;align-items:center;justify-content:center;width:18px;height:18px;border-radius:6px;background:#ea580c;color:#fff;font-size:10px;font-weight:700;">XM</span><span>Xiaomi</span></span>
  <span style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #d0d7de;border-radius:999px;"><span style="display:inline-flex;align-items:center;justify-content:center;width:18px;height:18px;border-radius:6px;background:#334155;color:#fff;font-size:10px;font-weight:700;">OA</span><span>OpenAI-compatible</span></span>
</div>

<div style="display:flex;flex-wrap:wrap;gap:8px 10px;margin:0 0 20px;">
  <span style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #d0d7de;border-radius:999px;"><span style="display:inline-flex;align-items:center;justify-content:center;width:18px;height:18px;border-radius:6px;background:#0f172a;color:#fff;font-size:10px;font-weight:700;">OC</span><span>OpenClaw</span></span>
  <span style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #d0d7de;border-radius:999px;"><span style="display:inline-flex;align-items:center;justify-content:center;width:18px;height:18px;border-radius:6px;background:#6d28d9;color:#fff;font-size:10px;font-weight:700;">HA</span><span>Hermes Agent</span></span>
  <span style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #d0d7de;border-radius:999px;"><span style="display:inline-flex;align-items:center;justify-content:center;width:18px;height:18px;border-radius:6px;background:#166534;color:#fff;font-size:10px;font-weight:700;">DF</span><span>DeerFlow</span></span>
  <span style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #d0d7de;border-radius:999px;"><img src="docs/images/logo/img_1.png" width="18" height="18" alt="Coze"><span>Coze</span></span>
  <span style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #d0d7de;border-radius:999px;"><img src="docs/images/logo/img_2.png" width="18" height="18" alt="Wenxin Agents"><span>Wenxin Agents</span></span>
  <span style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #d0d7de;border-radius:999px;"><img src="docs/images/logo/img_3.png" width="18" height="18" alt="Zhipu Agents"><span>Zhipu Agents</span></span>
  <span style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #d0d7de;border-radius:999px;"><img src="docs/images/logo/img_4.jpeg" width="18" height="18" alt="Hunyuan Agents"><span>Hunyuan Agents</span></span>
</div>

<div style="display:flex;flex-wrap:wrap;gap:8px 10px;margin:0 0 20px;">
  <span style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #d0d7de;border-radius:999px;"><img src="docs/images/logo/img_4.png" width="18" height="18" alt="Chroma"><span>Chroma</span></span>
  <span style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #d0d7de;border-radius:999px;"><img src="docs/images/logo/img_5.png" width="18" height="18" alt="Elasticsearch"><span>Elasticsearch</span></span>
  <span style="display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #d0d7de;border-radius:999px;"><img src="docs/images/logo/img_6.png" width="18" height="18" alt="MySQL"><span>MySQL</span></span>
</div>

## Why LinkMind

- One middleware layer for chat, OCR, ASR/TTS, image generation, image and video understanding, text-to-SQL, embeddings, rerank, and document pipelines.
- Multi-model routing and failover are configured centrally in `lagi.yml`, so business systems do not need provider-specific rewrites.
- RAG support is built around vector stores and document ingestion, with optional Chroma, Elasticsearch, MySQL, and graph-style augmentation.
- Medusa caching, token statistics endpoints, filters, and runtime governance target real production cost and stability concerns.
- Agent-runtime sync is built in for OpenClaw, Hermes Agent, and DeerFlow, which makes local AI workspace integration easier.

## Get Started In Minutes

### 1. Official installer

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

### 2. Run the packaged JAR

```powershell
java -jar LinkMind.jar
```

On first run, LinkMind creates `config/`, `data/`, and the default `lagi.yml`. Then open `http://localhost:8080`.

### 3. Build from source

```bash
mvn clean package -pl lagi-web -am -DskipTests -U
```

Current packaging generates:

- `lagi-web/target/LinkMind.jar`
- `lagi-web/target/ROOT.war`

More setup details are in the [Installation Guide](docs/install_en.md).

## Documentation Map

| Goal | English | 中文 |
| --- | --- | --- |
| Fast local startup checklist | [QuickStart](QuickStart.md) | [QuickStart](QuickStart.md) |
| Install and run LinkMind | [Installation Guide](docs/install_en.md) | [安装指南](docs/install_zh.md) |
| Configure models, routing, RAG, skills, and filters | [Configuration Reference](docs/config_en.md) | [配置参考](docs/config_zh.md) |
| Call native and OpenAI-compatible APIs | [API Reference](docs/API_en.md) | [API 参考](docs/API_zh.md) |
| Integrate `lagi-core` or REST APIs into your app | [Integration Guide](docs/guide_en.md) | [开发集成指南](docs/guide_zh.md) |
| Follow a first-run walkthrough | [Tutorial](docs/tutor_en.md) | [教学演示](docs/tutor_zh.md) |
| Extend models, vector stores, and adapters | [Extension Guide](docs/extend_en.md) | [扩展开发文档](docs/extend_zh.md) |
| Chroma setup and packaging appendix | [Annex](docs/annex_en.md) | [附件](docs/annex_zh.md) |

## API Surface

LinkMind exposes two route styles:

- Native LinkMind routes without extra version prefixes where the server already supports them, such as `/chat/completions`, `/audio/speech2text`, `/audio/text2speech`, `/image/text2image`, `/sql/text2sql`, `/instruction/generate`, `/doc/doc2ext`, and `/ocr/doc2ocr`
- OpenAI-compatible routes that intentionally keep the standard prefix, such as `/v1/chat/completions`, `/v1/models`, `/v1/embeddings`, `/v1/images/generations`, and `/v1/rerank`

One current exception is the vector administration namespace, which is still mapped in code as `/v1/vector/*`.

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

## Build And Packaging Notes

- Root project version: `1.2.3`
- Modules: `lagi-web`, `lagi-core`, `lagi-extension`
- Default runtime entry point: `ai.starter.Application`

## License

This project is distributed under the [LICENSE](LICENSE).
