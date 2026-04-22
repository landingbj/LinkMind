# Tutorial

This walkthrough is the shortest practical path from zero to a working LinkMind environment.

## 1. Start LinkMind

Use any one of the four options from the [Installation Guide](install_en.md):

- Official Installer
- Download Packaged Jar
- With Docker Image
- Build from Source

When the server is ready, open:

- `http://localhost:8080`

## 2. Create Your First Usable Configuration

Before testing anything, make sure at least one real model key is configured.

The simplest path is:

1. Sign in to the web console
2. Open the model or API-key settings page
3. Fill in one provider key
4. Enable one chat backend in `lagi.yml`

A minimal chat example looks like this:

```yaml
models:
  - name: qwen
    type: Alibaba
    enable: true
    model: qwen-plus,qwen-max
    driver: ai.wrapper.impl.AlibabaAdapter
    api_key: your-api-key
    # For multiple keys, use a key pool instead:
    # api_keys: sk-key1,sk-key2,sk-key3
    # key_route: polling  # polling (round-robin) or failover

functions:
  chat:
    route: pass(qwen)
    backends:
      - backend: qwen
        model: qwen-plus
        enable: true
        stream: true
        priority: 100

routers:
  enable: true
  items:
    - name: pass
      rule: (%)
```

## 3. Verify Chat In The Console

Return to the chat page and send a simple prompt such as:

- `Introduce LinkMind in one paragraph.`

If you get a normal answer, your first provider configuration is working.

## 4. Verify The HTTP API

### Native LinkMind Route

```bash
curl http://localhost:8080/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "qwen-plus",
    "stream": false,
    "messages": [
      {"role": "user", "content": "List three core LinkMind capabilities."}
    ]
  }'
```

### OpenAI-Compatible Route

```bash
curl http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "qwen-plus",
    "stream": false,
    "messages": [
      {"role": "user", "content": "List three core LinkMind capabilities."}
    ]
  }'
```

If auth is enabled, add:

```http
Authorization: Bearer <your-linkmind-api-key>
```

## 5. Enable RAG

If you want answers grounded in your own data:

1. Start Chroma
2. Point `stores.vector[*].url` at Chroma
3. Enable `stores.rag`
4. Configure an embedding backend
5. Ingest data through the console or vector APIs

Chroma quick start:

```bash
pip install chromadb
mkdir db_data
chroma run --path db_data
```

Then set:

```yaml
stores:
  vector:
    - name: chroma
      driver: ai.vector.impl.ChromaVectorStore
      url: http://localhost:8000

  rag:
    vector: chroma
    enable: true
```

## 6. Try The Multimodal Endpoints

The current server includes these common workflows:

- `POST /audio/speech2text`
- `GET /audio/text2speech`
- `POST /image/text2image`
- `POST /image/image2ocr`
- `POST /ocr/doc2ocr`
- `POST /doc/doc2ext`
- `POST /doc/doc2struct`
- `POST /sql/text2sql`

Use the [API Reference](API_en.md) for request examples.

## 7. Optional: Connect An Agent Runtime

If your local workflow already uses OpenClaw, Hermes Agent, or DeerFlow:

1. Reinstall or restart LinkMind in `Agent Mate` mode
2. Verify that the runtime config path is correct
3. Let LinkMind act as the shared middleware layer instead of wiring every business app directly to each model provider

## 8. Next Steps

- Tune models, routes, filters, and RAG: [Configuration Reference](config_en.md)
- Integrate with your own service: [Integration Guide](guide_en.md)
- Extend models or vector stores: [Extension Guide](extend_en.md)
