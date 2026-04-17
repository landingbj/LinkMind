# API Guide

This page focuses on the routes that are actually exposed by the current `web.xml` and servlet implementations.

## Base URL and routing rules

Assume the default local deployment:

```text
http://localhost:8080
```

Route rules used in this document:

- Prefer the native LinkMind routes without a version prefix when the code exposes them directly.
- Keep `/v1/...` for OpenAI-compatible endpoints such as `/v1/chat/completions`, `/v1/models`, `/v1/embeddings`, `/v1/images/generations`, and `/v1/rerank`.
- Keep `/v1/vector/*` because vector administration is still mapped that way in the current servlet config.
- If API key protection is enabled in your deployment, send the corresponding auth header or request key required by your environment.

## Most-used endpoints

| Capability | Method | Route |
| --- | --- | --- |
| Native chat completions | `POST` | `/chat/completions` |
| OpenAI-compatible chat completions | `POST` | `/v1/chat/completions` |
| Agent or worker execution | `POST` | `/chat/go` |
| Speech to text | `POST` | `/audio/speech2text` |
| Text to speech | `GET` | `/audio/text2speech` |
| Text to image | `POST` | `/image/text2image` |
| OpenAI-compatible image generation | `POST` | `/v1/images/generations` |
| Image OCR | `POST` | `/image/image2ocr` |
| Document OCR | `POST` | `/ocr/doc2ocr` |
| Document content extraction | `POST` | `/doc/doc2ext` |
| Document structuring / markdown conversion | `POST` | `/doc/doc2struct` |
| Instruction extraction | `POST` | `/instruction/generate` |
| Text to SQL | `POST` | `/sql/text2sql` |
| SQL to text | `POST` | `/sql/sql2text` |
| Embeddings | `POST` | `/embeddings` or `/v1/embeddings` |
| Rerank | `POST` | `/rerank` or `/v1/rerank` |
| OpenAI-compatible models list | `GET` | `/v1/models` |
| Vector admin | `GET` / `POST` | `/v1/vector/*` |

## 1. Chat completions

Native route:

```bash
curl http://localhost:8080/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "qwen-plus",
    "temperature": 0.7,
    "max_tokens": 512,
    "category": "default",
    "stream": false,
    "messages": [
      {
        "role": "user",
        "content": "Summarize what LinkMind is used for."
      }
    ]
  }'
```

OpenAI-compatible route:

```bash
curl http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "qwen-plus",
    "stream": true,
    "messages": [
      {
        "role": "user",
        "content": "Write a short welcome message."
      }
    ]
  }'
```

Useful request fields from the current request object:

| Field | Notes |
| --- | --- |
| `model` | Model ID configured in `functions.chat.backends` |
| `messages` | Standard chat message list |
| `stream` | `true` for SSE streaming |
| `temperature` | Sampling temperature |
| `max_tokens` | Response token limit |
| `category` | RAG category for knowledge retrieval |
| `tools`, `tool_choice`, `parallel_tool_calls` | Tool-calling compatible fields |
| `response_format` | Structured output request |
| `session_id` | Accepted as an alias for `sessionId` |

## 2. Agent and worker execution

`/chat/go` is the entry point for invoking workers or routed agents.

```bash
curl http://localhost:8080/chat/go \
  -H "Content-Type: application/json" \
  -d '{
    "worker": "appointedWorker",
    "agentId": "weather",
    "stream": false,
    "messages": [
      {
        "role": "user",
        "content": "What is the weather in Beijing today?"
      }
    ]
  }'
```

Common fields:

- `worker`: worker name configured under `skills.workers`
- `agentId`: target agent name when the worker route expects one
- `messages`: conversation payload
- `stream`: whether to stream the answer

## 3. Audio APIs

### Speech to text

`POST /audio/speech2text`

Send raw audio bytes in the request body. Optional request parameters are parsed into `AudioRequestParam`, including `model`, `format`, `sample_rate`, `vocabulary_id`, and `customization_id`.

```bash
curl -X POST "http://localhost:8080/audio/speech2text?model=asr&format=wav" \
  -H "Content-Type: application/octet-stream" \
  --data-binary "@demo.wav"
```

### Text to speech

`GET /audio/text2speech`

Important query fields in the current implementation include `text`, `model`, `format`, `sample_rate`, `voice`, `volume`, `speech_rate`, `pitch_rate`, `emotion`, and `source`.

```bash
curl "http://localhost:8080/audio/text2speech?model=landing-tts&text=Hello%20LinkMind"
```

When synthesis succeeds, the response body is audio data with `Content-Type: audio/mpeg`.

## 4. Image APIs

### Text to image

Native:

```bash
curl http://localhost:8080/image/text2image \
  -H "Content-Type: application/json" \
  -d '{
    "model": "tti",
    "prompt": "A clean product illustration of an AI control tower",
    "size": "1024x1024"
  }'
```

OpenAI-compatible:

```bash
curl http://localhost:8080/v1/images/generations \
  -H "Content-Type: application/json" \
  -d '{
    "model": "tti",
    "prompt": "A clean product illustration of an AI control tower",
    "size": "1024x1024"
  }'
```

### Image OCR

`POST /image/image2ocr`

Upload one or more image files as `multipart/form-data`.

```bash
curl http://localhost:8080/image/image2ocr \
  -F "file=@page.png"
```

## 5. Document APIs

### Document OCR

`POST /ocr/doc2ocr`

The current implementation accepts uploaded files and an optional `lang` form field. PDFs are processed one at a time.

```bash
curl http://localhost:8080/ocr/doc2ocr \
  -F "file=@contract.pdf" \
  -F "lang=chn,eng"
```

### Extract document content

`POST /doc/doc2ext`

```bash
curl http://localhost:8080/doc/doc2ext \
  -F "file=@manual.pdf"
```

### Convert document to structured markdown

`POST /doc/doc2struct`

```bash
curl http://localhost:8080/doc/doc2struct \
  -F "file=@manual.pdf"
```

### Generate instructions from files

`POST /instruction/generate`

```bash
curl http://localhost:8080/instruction/generate \
  -F "file=@faq.docx"
```

## 6. SQL APIs

### Text to SQL

`POST /sql/text2sql`

```bash
curl http://localhost:8080/sql/text2sql \
  -H "Content-Type: application/json" \
  -d '{
    "demand": "Count orders created this week",
    "tables": "orders",
    "storage": "mysql"
  }'
```

### SQL to text

`POST /sql/sql2text`

```bash
curl http://localhost:8080/sql/sql2text \
  -H "Content-Type: application/json" \
  -d '{
    "demand": "Explain the result in plain English",
    "sql": "SELECT COUNT(*) FROM orders",
    "tables": "orders",
    "storage": "mysql"
  }'
```

## 7. Embeddings and rerank

### Embeddings

Both routes are available:

- `POST /embeddings`
- `POST /v1/embeddings`

```bash
curl http://localhost:8080/embeddings \
  -H "Content-Type: application/json" \
  -d '{
    "model": "text-embedding",
    "input": ["LinkMind", "RAG middleware"]
  }'
```

### Rerank

Both routes are available:

- `POST /rerank`
- `POST /v1/rerank`

```bash
curl http://localhost:8080/rerank \
  -H "Content-Type: application/json" \
  -d '{
    "model": "rerank-model",
    "query": "enterprise knowledge base",
    "documents": [
      "Internal FAQ",
      "Model integration guide",
      "Random meeting notes"
    ]
  }'
```

## 8. Vector administration

Vector management currently stays under `/v1/vector/*`.

Common routes in the current servlet:

| Method | Route | Purpose |
| --- | --- | --- |
| `POST` | `/v1/vector/upsert` | Add or update documents |
| `POST` | `/v1/vector/query` | Vector query |
| `POST` | `/v1/vector/get` | Fetch by IDs or conditions |
| `POST` | `/v1/vector/search` | Search by conversation context |
| `POST` | `/v1/vector/searchByMetadata` | Search with metadata filter |
| `POST` | `/v1/vector/deleteById` | Delete by IDs |
| `POST` | `/v1/vector/deleteByMetadata` | Delete by metadata |
| `POST` | `/v1/vector/deleteCollection` | Drop a category / collection |
| `GET` | `/v1/vector/listCollections` | List collections |
| `POST` | `/v1/vector/updateTextBlockSize` | Update RAG chunk size settings |
| `GET` | `/v1/vector/getTextBlockSize` | Read RAG chunk size settings |
| `POST` | `/v1/vector/resetBlockSize` | Reset chunk size settings |

Example upsert:

```bash
curl http://localhost:8080/v1/vector/upsert \
  -H "Content-Type: application/json" \
  -d '{
    "category": "product-docs",
    "isContextLinked": true,
    "data": [
      {
        "id": "intro-001",
        "document": "LinkMind provides unified AI middleware capabilities.",
        "metadata": {
          "source": "README",
          "lang": "en"
        }
      }
    ]
  }'
```

## 9. Compatibility notes

- `GET /v1/models` exists for OpenAI-compatible clients, but the current servlet returns a minimal compatibility-style list rather than a full provider inventory.
- `/chat/isRAG` and `/chat/isMedusa` are present in the servlet for runtime toggling and inspection, but they are operational helpers rather than the main public integration surface.
- `/v1/openclaw/context/*` exists for OpenClaw-related context APIs and should be treated as a specialized integration route.
